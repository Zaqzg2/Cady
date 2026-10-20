package com.cady.cadysalesapp.data.weborders

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cady.cadysalesapp.MainActivity
import com.cady.cadysalesapp.R
import com.cady.cadysalesapp.ui.QuickLaunch
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one "new orders from the website" notification. It is posted from two places — the live listener while the
 * app is open and the background check every ~15 minutes — and both go through [announce], which stays silent
 * for orders that were already announced. There is a single notification (the same id), always showing the
 * whole picture: how many orders are still waiting, newest first.
 */
@Singleton
class WebOrdersNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: WebOrdersAlertSettings,
) {
    /** The alerts are switched on AND the system lets this app post notifications (Android 13+ asks first). */
    suspend fun canNotify(): Boolean =
        settings.alertsEnabled.first() && NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * [window] is every order the caller just read, newest first. Rings only when an order that is still NEW was
     * not announced before; when nothing is waiting any more the notification is taken away.
     */
    suspend fun announce(window: List<WebOrder>) {
        val (waiting, handled) = window.partition { it.status == WebOrderStatus.NEW }
        if (waiting.isEmpty()) {
            cancel()
            return
        }
        if (!canNotify()) return // nothing is marked as announced, so it still rings once the alerts can
        val fresh = settings.claimUnannounced(
            waitingIds = waiting.mapTo(HashSet()) { it.id },
            handledIds = handled.mapTo(HashSet()) { it.id },
        )
        if (fresh.isEmpty()) return
        post(waiting)
    }

    fun cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /** Created early (Application start) so the channel already exists when Android 13 asks for permission. */
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "طلبات الموقع", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "تنبيه عند وصول طلب جديد من صفحة البيع على الإنترنت"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    // The permission is checked in canNotify() (areNotificationsEnabled is false without it on Android 13+),
    // and a revocation in the gap between the two is caught below.
    @SuppressLint("MissingPermission")
    private fun post(waiting: List<WebOrder>) {
        ensureChannel()
        val newest = waiting.first()

        val open = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                action = QuickLaunch.ACTION_OPEN_WEB_ORDERS
                // Same as the widget's: an app that is already open receives it in onNewIntent.
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_web_order)
            .setColor(BRAND_RED)
            .setContentTitle(titleFor(waiting.size))
            .setContentText(summaryLine(newest))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setNumber(waiting.size)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL) // before Android 8 the channel does not exist; this gives the sound

        if (waiting.size > 1) {
            val style = NotificationCompat.InboxStyle()
            waiting.take(MAX_LINES).forEach { style.addLine(summaryLine(it)) }
            if (waiting.size > MAX_LINES) style.setSummaryText("و${waiting.size - MAX_LINES} أخرى")
            builder.setStyle(style)
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(detailText(newest)))
        }

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // The permission was taken away just now; the next check simply finds it off.
        }
    }

    private fun titleFor(count: Int): String = when (count) {
        1 -> "طلب جديد من الموقع"
        2 -> "طلبان جديدان من الموقع"
        in 3..10 -> "$count طلبات جديدة من الموقع"
        else -> "$count طلبًا جديدًا من الموقع"
    }

    /** "أحمد — لمون × 2 • رمان × 1" */
    private fun summaryLine(order: WebOrder): String {
        val items = order.itemsText()
        return if (items.isEmpty()) order.customerName else "${order.customerName} — $items"
    }

    private fun detailText(order: WebOrder): String = buildString {
        append(summaryLine(order))
        order.phone?.let { append("\n").append(it) }
        order.address?.let { append("\n").append(order.addressLabel).append(": ").append(it) }
        order.note?.let { append("\n").append(it) }
        order.ref?.let { append("\nرقم الطلب: ").append(it) }
    }

    private companion object {
        const val CHANNEL_ID = "web_orders"
        const val NOTIFICATION_ID = 4101
        const val REQUEST_CODE = 4101
        const val MAX_LINES = 5
        const val BRAND_RED = 0xFFC62828.toInt()
    }
}
