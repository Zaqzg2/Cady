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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cady.cadysalesapp.ui.lock.LockScreen
import com.cady.cadysalesapp.ui.login.LoginScreen
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

        composable(CadyDestination.Home.route) { PlaceholderScreen("الرئيسية") }
        composable(CadyDestination.Customers.route) { PlaceholderScreen("العملاء") }
        composable(CadyDestination.CustomerDetail.route) { backStackEntry ->
            val customerId = backStackEntry.arguments?.getString("customerId").orEmpty()
            PlaceholderScreen("تفاصيل العميل: $customerId")
        }
        composable(CadyDestination.Products.route) { PlaceholderScreen("المنتجات") }
        composable(CadyDestination.Reports.route) { PlaceholderScreen("التقارير") }
        composable(CadyDestination.Invoice.route) { PlaceholderScreen("فاتورة") }
        composable(CadyDestination.Receipt.route) { PlaceholderScreen("سند قبض") }
        composable(CadyDestination.DocumentsList.route) { PlaceholderScreen("سجل المستندات") }
        composable(CadyDestination.PdfPreview.route) { PlaceholderScreen("معاينة PDF") }

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
