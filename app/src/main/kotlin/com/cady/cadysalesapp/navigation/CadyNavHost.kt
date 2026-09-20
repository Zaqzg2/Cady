package com.cady.cadysalesapp.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cady.cadysalesapp.ui.customerdetail.CustomerDetailScreen
import com.cady.cadysalesapp.ui.customers.CustomersScreen
import com.cady.cadysalesapp.ui.home.HomeScreen
import com.cady.cadysalesapp.ui.invoice.InvoiceScreen
import com.cady.cadysalesapp.ui.lock.LockScreen
import com.cady.cadysalesapp.ui.login.LoginScreen
import com.cady.cadysalesapp.ui.products.ProductsScreen
import com.cady.cadysalesapp.ui.receipt.ReceiptScreen
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
) {
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
                    // TODO(Phase 2+): branch to CadyDestination.ManagerRoot for a
                    // manager account instead of Home once ManagerRootNav exists —
                    // AccountRepository.currentUser already carries the role needed
                    // to make that call, this just isn't wired up yet.
                    navController.navigate(CadyDestination.Home.route) {
                        popUpTo(CadyDestination.Login.route) { inclusive = true }
                    }
                },
                onCreateFirstManagerClick = { navController.navigate(CadyDestination.SetupManager.route) },
            )
        }
        composable(CadyDestination.Lock.route) {
            LockScreen(onUnlocked = { navController.popBackStack() })
        }

        composable(CadyDestination.Home.route) {
            HomeScreen(
                onNewSale = { navController.navigate(CadyDestination.Invoice.createRoute()) },
                onNewReturn = { navController.navigate(CadyDestination.Invoice.createRoute()) },
                onNewReceipt = { navController.navigate(CadyDestination.Receipt.createRoute()) },
                onNewCashCustomerSale = { navController.navigate(CadyDestination.Invoice.createRoute()) },
                onSettingsClick = { navController.navigate(CadyDestination.SettingsHub.route) },
                bottomBar = { CadyBottomBar(navController) },
            )
        }
        composable(CadyDestination.Customers.route) {
            CustomersScreen(
                onCustomerClick = { id -> navController.navigate(CadyDestination.CustomerDetail.createRoute(id)) },
                bottomBar = { CadyBottomBar(navController) },
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
        composable(CadyDestination.Products.route) { ProductsScreen(bottomBar = { CadyBottomBar(navController) }) }
        composable(CadyDestination.Reports.route) { PlaceholderScreen("التقارير") }
        composable(
            CadyDestination.Invoice.route,
            arguments = listOf(
                navArgument("invoiceId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("customerId") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) {
            InvoiceScreen(onSaved = { navController.popBackStack() }, onBack = { navController.popBackStack() })
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
        composable(CadyDestination.DocumentsList.route) { PlaceholderScreen("سجل المستندات") }
        composable(
            CadyDestination.PdfPreview.route,
            arguments = listOf(
                navArgument("docType") { type = NavType.StringType },
                navArgument("docId") { type = NavType.StringType },
            ),
        ) {
            com.cady.cadysalesapp.ui.pdfpreview.PdfPreviewScreen(onBack = { navController.popBackStack() })
        }

        composable(CadyDestination.SettingsHub.route) { PlaceholderScreen("الإعدادات") }
        composable(CadyDestination.SettingsCompany.route) { PlaceholderScreen("بيانات الشركة") }
        composable(CadyDestination.SettingsPrinting.route) { PlaceholderScreen("إعدادات الطباعة") }
        composable(CadyDestination.SettingsAppearance.route) { PlaceholderScreen("المظهر") }
        composable(CadyDestination.SettingsPrivacy.route) { PlaceholderScreen("الخصوصية") }
        composable(CadyDestination.SettingsData.route) { PlaceholderScreen("حجم البيانات") }

        composable(CadyDestination.Sync.route) { PlaceholderScreen("المزامنة") }
        composable(CadyDestination.SyncPendingPreview.route) { PlaceholderScreen("معاينة المعلّق") }
        composable(CadyDestination.SyncOutboxInbox.route) { PlaceholderScreen("الصادر والوارد") }
        composable(CadyDestination.BackupManagement.route) { PlaceholderScreen("النسخ الاحتياطي") }

        composable(CadyDestination.ManagerDashboard.route) { PlaceholderScreen("لوحة تحكم المدير") }
        composable(CadyDestination.ManagerUsers.route) { PlaceholderScreen("المندوبون") }
        composable(CadyDestination.ManagerSyncHub.route) { PlaceholderScreen("مركز المزامنة") }
        composable(CadyDestination.ManagerLiveActivity.route) { PlaceholderScreen("النشاط المباشر") }
        composable(CadyDestination.ManagerImport.route) { PlaceholderScreen("استيراد من مندوب") }
        composable(CadyDestination.ManagerExport.route) { PlaceholderScreen("إنشاء تحديث") }
        composable(CadyDestination.ManagerSyncLog.route) { PlaceholderScreen("سجل المزامنة") }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Scaffold { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(title)
        }
    }
}
