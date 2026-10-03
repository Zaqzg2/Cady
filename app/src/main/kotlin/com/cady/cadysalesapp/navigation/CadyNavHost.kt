package com.cady.cadysalesapp.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import com.cady.cadysalesapp.data.local.entity.UserRole
import com.cady.cadysalesapp.ui.IncomingFileViewModel
import com.cady.cadysalesapp.ui.customerdetail.CustomerDetailScreen
import com.cady.cadysalesapp.ui.customers.CustomersScreen
import com.cady.cadysalesapp.ui.home.HomeScreen
import com.cady.cadysalesapp.ui.invoice.InvoiceScreen
import com.cady.cadysalesapp.ui.lock.LockScreen
import com.cady.cadysalesapp.ui.login.LoginScreen
import com.cady.cadysalesapp.ui.manager.ManagerDashboardScreen
import com.cady.cadysalesapp.ui.manager.ManagerExportScreen
import com.cady.cadysalesapp.ui.manager.ManagerImportScreen
import com.cady.cadysalesapp.ui.manager.ManagerLiveActivityScreen
import com.cady.cadysalesapp.ui.manager.ManagerSyncHubScreen
import com.cady.cadysalesapp.ui.manager.ManagerUsersScreen
import com.cady.cadysalesapp.ui.products.ProductsScreen
import com.cady.cadysalesapp.ui.receipt.ReceiptScreen
import com.cady.cadysalesapp.ui.session.SessionViewModel
import com.cady.cadysalesapp.ui.setupmanager.SetupManagerScreen

/**
 * Every route from CadyDestinations wired to a placeholder screen so the app
 * shell already builds and runs end to end. Each placeholder() call below gets
 * swapped for the real screen composable as that phase (see the review doc's
 * "ترتيب البناء المقترح") lands. Phase 1 (identity) is the first to land for real:
 * Login, SetupManager, and Lock below are now the actual screens, not stubs.
 */
@Composable
fun CadyNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = CadyDestination.Login.route,
    sessionViewModel: SessionViewModel = hiltViewModel(),
) {
    // One app for both roles: the signed-in account's role decides which screens exist.
    val currentUser by sessionViewModel.currentUser.collectAsState()
    val isManager = currentUser?.role == UserRole.MANAGER

    // A sync file shared into Cady from another app: once the person is signed in (they are on
    // Home), bring them to the screen that asks before importing anything — a manager's import
    // screen (a rep's file, previewed first), or a rep's sync screen (the manager's update).
    val incomingViewModel: IncomingFileViewModel = hiltViewModel()
    val incomingFile by incomingViewModel.pending.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    LaunchedEffect(incomingFile, currentRoute, isManager) {
        if (incomingFile != null && currentRoute == CadyDestination.Home.route) {
            val target = if (isManager) CadyDestination.ManagerImport.route else CadyDestination.Sync.route
            navController.navigate(target) { launchSingleTop = true }
        }
    }

    // Signed out from anywhere (Settings → تسجيل الخروج, or "forgot PIN" on the lock):
    // back to Login with an empty back stack, so Back can't return into the old session.
    LaunchedEffect(currentUser, currentRoute) {
        val route = currentRoute ?: return@LaunchedEffect
        if (currentUser == null &&
            route != CadyDestination.Login.route &&
            route != CadyDestination.SetupManager.route
        ) {
            navController.navigate(CadyDestination.Login.route) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // The manager's screens are not for reps: a rep account that somehow lands on one
    // (restored state, an old link) is sent back Home. Real protection stays in the
    // Firestore rules; this only keeps the screens themselves out of reach.
    LaunchedEffect(currentUser, isManager, currentRoute) {
        val route = currentRoute ?: return@LaunchedEffect
        if (currentUser != null && !isManager && route.startsWith("manager")) {
            navController.navigate(CadyDestination.Home.route) {
                popUpTo(CadyDestination.Home.route)
                launchSingleTop = true
            }
        }
    }

    // Deliberately reads the role inside its own composable: capturing `isManager` here would
    // make the NavHost rebuild its whole graph every time the signed-in account changes.
    val bottomBar: @Composable () -> Unit = { RoleBottomBar(navController, sessionViewModel) }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(CadyDestination.SetupManager.route) {
            SetupManagerScreen(
                onManagerCreated = {
                    navController.navigate(CadyDestination.Home.route) {
                        popUpTo(CadyDestination.Login.route) { inclusive = true }
                    }
                },
                onGoToLoginClick = { navController.popBackStack() },
            )
        }
        composable(CadyDestination.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    // Same Home for both roles — a manager account simply gets the extra
                    // manager screens (see isManager above); there is no separate manager app.
                    navController.navigate(CadyDestination.Home.route) {
                        popUpTo(CadyDestination.Login.route) { inclusive = true }
                    }
                },
                onCreateFirstManagerClick = { navController.navigate(CadyDestination.SetupManager.route) },
            )
        }
        composable(CadyDestination.Lock.route) {
            // Kept only so the route resolves; the real lock is the overlay in AppRoot,
            // which covers whatever screen is showing without touching the back stack.
            LockScreen(onUnlocked = { navController.popBackStack() })
        }

        composable(CadyDestination.Home.route) {
            val user by sessionViewModel.currentUser.collectAsState()
            HomeScreen(
                onNewSale = { navController.navigate(CadyDestination.Invoice.createRoute()) },
                onNewReturn = { navController.navigate(CadyDestination.Invoice.createRoute()) },
                onNewReceipt = { navController.navigate(CadyDestination.Receipt.createRoute()) },
                onNewCashCustomerSale = { navController.navigate(CadyDestination.Invoice.createRoute()) },
                onSettingsClick = { navController.navigate(CadyDestination.SettingsHub.route) },
                onViewAllDocuments = { navController.navigate(CadyDestination.DocumentsList.route) },
                onDocumentClick = { type, id -> navController.navigate(CadyDestination.PdfPreview.createRoute(type, id)) },
                isManager = user?.role == UserRole.MANAGER,
                onManagerClick = { navController.navigate(CadyDestination.ManagerDashboard.route) },
                bottomBar = bottomBar,
            )
        }
        composable(CadyDestination.Customers.route) {
            CustomersScreen(
                onCustomerClick = { id -> navController.navigate(CadyDestination.CustomerDetail.createRoute(id)) },
                bottomBar = bottomBar,
            )
        }
        composable(CadyDestination.CustomerDetail.route) { backStackEntry ->
            val context = androidx.compose.ui.platform.LocalContext.current
            val customerId = backStackEntry.arguments?.getString("customerId").orEmpty()
            CustomerDetailScreen(
                onNewInvoice = { _ ->
                    // TODO(Phase 3): pass the sale/return kind through once
                    // InvoiceScreen actually reads it — CadyDestination.Invoice
                    // doesn't carry a kind argument yet.
                    navController.navigate(CadyDestination.Invoice.createRoute(customerId = customerId))
                },
                onNewReceipt = {
                    navController.navigate(CadyDestination.Receipt.createRoute(customerId = customerId))
                },
                onPreviewStatement = {
                    navController.navigate(CadyDestination.PdfPreview.createRoute("statement", customerId))
                },
                onPrintStatement = {
                    navController.navigate(CadyDestination.PdfPreview.createRoute("statement", customerId))
                },
                onDocumentClick = { type, id ->
                    navController.navigate(CadyDestination.PdfPreview.createRoute(type, id))
                },
                onCallClick = { phone ->
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$phone"))
                    )
                },
                onWhatsAppClick = { phone ->
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://wa.me/$phone"),
                        )
                    )
                },
                onMapClick = { /* TODO(Phase 2 polish): geocode the address, launch geo: intent */ },
            )
        }
        composable(CadyDestination.Products.route) {
            val user by sessionViewModel.currentUser.collectAsState()
            ProductsScreen(canEdit = user?.role == UserRole.MANAGER, bottomBar = bottomBar)
        }
        composable(CadyDestination.Reports.route) { PlaceholderScreen("التقارير", bottomBar = bottomBar) }
        composable(
            CadyDestination.Invoice.route,
            arguments = listOf(
                navArgument("invoiceId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("customerId") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) {
            InvoiceScreen(
                onSaved = { invoiceId ->
                    navController.navigate(CadyDestination.PdfPreview.createRoute("invoice", invoiceId)) {
                        popUpTo(CadyDestination.Invoice.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            CadyDestination.Receipt.route,
            arguments = listOf(
                navArgument("receiptId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("customerId") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) {
            ReceiptScreen(onSaved = { navController.popBackStack() }, onBack = { navController.popBackStack() })
        }
        composable(CadyDestination.DocumentsList.route) {
            com.cady.cadysalesapp.ui.documentslist.DocumentsListScreen(
                onDocumentClick = { type, id -> navController.navigate(CadyDestination.PdfPreview.createRoute(type, id)) },
            )
        }
        composable(
            CadyDestination.PdfPreview.route,
            arguments = listOf(
                navArgument("docType") { type = NavType.StringType },
                navArgument("docId") { type = NavType.StringType },
            ),
        ) {
            com.cady.cadysalesapp.ui.pdfpreview.PdfPreviewScreen(onBack = { navController.popBackStack() })
        }

        composable(CadyDestination.SettingsHub.route) {
            val user by sessionViewModel.currentUser.collectAsState()
            com.cady.cadysalesapp.ui.settings.SettingsHubScreen(
                accountName = user?.displayName,
                isManager = user?.role == UserRole.MANAGER,
                onCompanyClick = { navController.navigate(CadyDestination.SettingsCompany.route) },
                onPrintingClick = { navController.navigate(CadyDestination.SettingsPrinting.route) },
                onAppearanceClick = { navController.navigate(CadyDestination.SettingsAppearance.route) },
                onPrivacyClick = { navController.navigate(CadyDestination.SettingsPrivacy.route) },
                onDataClick = { navController.navigate(CadyDestination.SettingsData.route) },
                onSyncClick = {
                    val target = if (user?.role == UserRole.MANAGER) CadyDestination.ManagerSyncHub.route else CadyDestination.Sync.route
                    navController.navigate(target)
                },
                onBackupClick = { navController.navigate(CadyDestination.BackupManagement.route) },
                onManagerClick = { navController.navigate(CadyDestination.ManagerDashboard.route) },
                onLogoutClick = { sessionViewModel.logout() },
            )
        }
        composable(CadyDestination.SettingsCompany.route) { com.cady.cadysalesapp.ui.settings.SettingsCompanyScreen() }
        composable(CadyDestination.SettingsPrinting.route) { com.cady.cadysalesapp.ui.settings.SettingsPrintingScreen() }
        composable(CadyDestination.SettingsAppearance.route) { com.cady.cadysalesapp.ui.settings.SettingsAppearanceScreen() }
        composable(CadyDestination.SettingsPrivacy.route) { com.cady.cadysalesapp.ui.settings.SettingsPrivacyScreen() }
        composable(CadyDestination.SettingsData.route) { com.cady.cadysalesapp.ui.settings.SettingsDataScreen() }

        composable(CadyDestination.Sync.route) {
            com.cady.cadysalesapp.ui.sync.SyncScreen(
                onBack = { navController.popBackStack() },
                onOpenPendingPreview = { navController.navigate(CadyDestination.SyncPendingPreview.route) },
                onOpenLog = { navController.navigate(CadyDestination.SyncOutboxInbox.route) },
            )
        }
        composable(CadyDestination.SyncPendingPreview.route) {
            com.cady.cadysalesapp.ui.sync.SyncPendingPreviewScreen(onBack = { navController.popBackStack() })
        }
        composable(CadyDestination.SyncOutboxInbox.route) {
            com.cady.cadysalesapp.ui.sync.SyncOutboxInboxScreen(onBack = { navController.popBackStack() })
        }
        composable(CadyDestination.BackupManagement.route) {
            com.cady.cadysalesapp.ui.backup.BackupManagementScreen(onBack = { navController.popBackStack() })
        }

        composable(CadyDestination.ManagerDashboard.route) {
            val user by sessionViewModel.currentUser.collectAsState()
            ManagerDashboardScreen(
                displayName = user?.displayName.orEmpty(),
                onUsersClick = { navController.navigate(CadyDestination.ManagerUsers.route) },
                onSyncHubClick = { navController.navigate(CadyDestination.ManagerSyncHub.route) },
                onLiveActivityClick = { navController.navigate(CadyDestination.ManagerLiveActivity.createRoute()) },
                onImportClick = { navController.navigate(CadyDestination.ManagerImport.route) },
                onExportClick = { navController.navigate(CadyDestination.ManagerExport.route) },
                onSyncLogClick = { navController.navigate(CadyDestination.ManagerSyncLog.route) },
                bottomBar = bottomBar,
            )
        }
        composable(CadyDestination.ManagerUsers.route) {
            ManagerUsersScreen(
                onBack = { navController.popBackStack() },
                onRepActivity = { repId -> navController.navigate(CadyDestination.ManagerLiveActivity.createRoute(repId)) },
            )
        }
        composable(CadyDestination.ManagerSyncHub.route) {
            ManagerSyncHubScreen(
                onBack = { navController.popBackStack() },
                onImport = { navController.navigate(CadyDestination.ManagerImport.route) },
                onExport = { navController.navigate(CadyDestination.ManagerExport.route) },
                onLiveActivity = { navController.navigate(CadyDestination.ManagerLiveActivity.createRoute()) },
                onLog = { navController.navigate(CadyDestination.ManagerSyncLog.route) },
                onUsers = { navController.navigate(CadyDestination.ManagerUsers.route) },
                // This device's own Firebase sync (push/pull of the manager's data) is the rep-side screen.
                onDeviceSync = { navController.navigate(CadyDestination.Sync.route) },
            )
        }
        composable(
            CadyDestination.ManagerLiveActivity.route,
            arguments = listOf(navArgument("repId") { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) {
            ManagerLiveActivityScreen(
                onBack = { navController.popBackStack() },
                onOpenDocument = { type, id -> navController.navigate(CadyDestination.PdfPreview.createRoute(type, id)) },
            )
        }
        composable(CadyDestination.ManagerImport.route) {
            ManagerImportScreen(onBack = { navController.popBackStack() })
        }
        composable(CadyDestination.ManagerExport.route) {
            ManagerExportScreen(onBack = { navController.popBackStack() })
        }
        composable(CadyDestination.ManagerSyncLog.route) {
            // The same log screen as the rep's, under the manager's title and without the rep-only
            // "waiting for the manager's confirmation" line.
            com.cady.cadysalesapp.ui.sync.SyncOutboxInboxScreen(
                onBack = { navController.popBackStack() },
                title = "سجل المزامنة",
                showAckStatus = false,
            )
        }
    }
}

/** The bottom bar for whoever is signed in — managers get the extra tab. */
@Composable
private fun RoleBottomBar(navController: NavHostController, sessionViewModel: SessionViewModel) {
    val user by sessionViewModel.currentUser.collectAsState()
    CadyBottomBar(navController, isManager = user?.role == UserRole.MANAGER)
}

@Composable
private fun PlaceholderScreen(
    title: String,
    onBack: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
) {
    Scaffold(
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                        }
                    },
                )
            }
        },
        bottomBar = bottomBar,
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (onBack != null) "$title — قيد البناء" else title)
        }
    }
}
