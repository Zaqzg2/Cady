package com.cady.cadysalesapp.data.printing

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Direct port of the Flutter project's native/BluetoothPrinterBridge.kt — same
 * android.bluetooth logic verified working on real hardware (see that file's
 * own comments: print_bluetooth_thermal always reported a successful connect()
 * but writeBytes() failed immediately; this raw-socket + fallback-channel
 * approach was written specifically to fix that). Only the MethodChannel
 * plumbing is gone: every call site here is plain Kotlin now, not Flutter
 * crossing a platform-channel boundary, so onMethodCall/MethodChannel.Result
 * become ordinary suspend functions instead.
 */
@Singleton
class BluetoothPrinterBridge @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "CadyBtPrinter"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val adapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter
    }

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    data class PairedDevice(val name: String, val mac: String)

    suspend fun pairedDevices(): List<PairedDevice> = withContext(Dispatchers.IO) {
        val bonded = try { adapter?.bondedDevices } catch (e: SecurityException) { null } ?: emptySet()
        bonded.map { d -> PairedDevice(name = safeName(d) ?: d.address, mac = d.address) }
    }

    private fun safeName(d: BluetoothDevice): String? = try {
        d.name
    } catch (e: SecurityException) {
        null
    }

    suspend fun connect(mac: String): Boolean = withContext(Dispatchers.IO) {
        disconnectInternal()
        val device = try { adapter?.getRemoteDevice(mac) } catch (e: Exception) { null } ?: return@withContext false
        try { adapter?.cancelDiscovery() } catch (_: Exception) {}

        var newSocket = openSocket(device, useFallbackChannel = false)
        try {
            newSocket?.connect()
        } catch (e: IOException) {
            Log.w(TAG, "Standard socket failed (${e.message}), trying raw channel 1...")
            try { newSocket?.close() } catch (_: Exception) {}
            newSocket = openSocket(device, useFallbackChannel = true)
            try {
                newSocket?.connect()
            } catch (e2: IOException) {
                Log.w(TAG, "Fallback connect also failed: ${e2.message}")
                try { newSocket?.close() } catch (_: Exception) {}
                return@withContext false
            }
        }

        if (newSocket == null || !newSocket.isConnected) return@withContext false
        socket = newSocket
        outputStream = newSocket.outputStream
        true
    }

    private fun openSocket(device: BluetoothDevice, useFallbackChannel: Boolean): BluetoothSocket? = try {
        if (!useFallbackChannel) {
            device.createRfcommSocketToServiceRecord(SPP_UUID)
        } else {
            // Known workaround for cheap thermal printers with an unreliable SDP
            // implementation — raw RFCOMM channel 1 via reflection, bypassing
            // service discovery entirely. Exactly the pattern that produces
            // "connect succeeds, write silently fails" when skipped.
            device.javaClass
                .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                .invoke(device, 1) as BluetoothSocket
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not create socket (fallback=$useFallbackChannel): ${e.message}")
        null
    }

    suspend fun isConnected(): Boolean = withContext(Dispatchers.IO) { socket?.isConnected == true }

    suspend fun writeBytes(bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val out = outputStream ?: return@withContext false
        try {
            out.write(bytes)
            out.flush()
            true
        } catch (e: IOException) {
            Log.w(TAG, "Write failed: ${e.message}")
            // The socket is actually dead — invalidate it immediately rather than
            // leaving it looking "connected" for the next call (the exact root
            // cause of "shows connected but printing fails" this class exists to fix).
            try { socket?.close() } catch (_: Exception) {}
            socket = null
            outputStream = null
            false
        }
    }

    suspend fun disconnect(): Boolean = withContext(Dispatchers.IO) { disconnectInternal() }

    private fun disconnectInternal(): Boolean = try {
        outputStream?.close()
        socket?.close()
        true
    } catch (e: Exception) {
        false
    } finally {
        outputStream = null
        socket = null
    }
}
