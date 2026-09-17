package com.cady.cadysalesapp.navigation

/**
 * Plain string routes (not the newer @Serializable type-safe routes) — deliberately,
 * to avoid pulling in the kotlinx-serialization plugin/dependency just for this.
 * Worth revisiting once the screen count below actually exists and the win from
 * type safety outweighs the extra moving part.
 *
 * One route per screen in the review doc's inventory (section 1), grouped the same way.
 */
sealed class CadyDestination(val route: String) {

    // Auth / onboarding
    data object SetupManager : CadyDestination("setup_manager")
    data object Login : CadyDestination("login")
    data object Lock : CadyDestination("lock")

    // Rep core
    data object Home : CadyDestination("home")
    data object Customers : CadyDestination("customers")
    data object CustomerDetail : CadyDestination("customers/{customerId}") {
        fun createRoute(customerId: String) = "customers/$customerId"
    }
    data object Products : CadyDestination("products")
    data object Reports : CadyDestination("reports")
    data object Invoice : CadyDestination("invoice?invoiceId={invoiceId}&customerId={customerId}") {
        fun createRoute(invoiceId: String? = null, customerId: String? = null) =
            "invoice?invoiceId=${invoiceId.orEmpty()}&customerId=${customerId.orEmpty()}"
    }
    data object Receipt : CadyDestination("receipt?receiptId={receiptId}&customerId={customerId}") {
        fun createRoute(receiptId: String? = null, customerId: String? = null) =
            "receipt?receiptId=${receiptId.orEmpty()}&customerId=${customerId.orEmpty()}"
    }
    data object DocumentsList : CadyDestination("documents")
    data object PdfPreview : CadyDestination("pdf_preview/{docId}") {
        fun createRoute(docId: String) = "pdf_preview/$docId"
    }

    // Settings
    data object SettingsHub : CadyDestination("settings")
    data object SettingsCompany : CadyDestination("settings/company")
    data object SettingsPrinting : CadyDestination("settings/printing")
    data object SettingsAppearance : CadyDestination("settings/appearance")
    data object SettingsPrivacy : CadyDestination("settings/privacy")
    data object SettingsData : CadyDestination("settings/data")

    // Sync / backup (rep side)
    data object Sync : CadyDestination("sync")
    data object SyncPendingPreview : CadyDestination("sync/pending_preview")
    data object SyncOutboxInbox : CadyDestination("sync/outbox_inbox")
    data object BackupManagement : CadyDestination("backup")

    // Manager
    data object ManagerRoot : CadyDestination("manager")
    data object ManagerDashboard : CadyDestination("manager/dashboard")
    data object ManagerUsers : CadyDestination("manager/users")
    data object ManagerSyncHub : CadyDestination("manager/sync_hub")
    data object ManagerLiveActivity : CadyDestination("manager/live_activity?repId={repId}") {
        fun createRoute(repId: String? = null) = "manager/live_activity?repId=${repId.orEmpty()}"
    }
    data object ManagerImport : CadyDestination("manager/import")
    data object ManagerExport : CadyDestination("manager/export")
    data object ManagerSyncLog : CadyDestination("manager/sync_log")
}
