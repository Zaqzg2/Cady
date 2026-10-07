package com.cady.cadysalesapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.cady.cadysalesapp.MainActivity
import com.cady.cadysalesapp.R
import com.cady.cadysalesapp.ui.QuickLaunch

/**
 * The home-screen widget: three buttons — فاتورة بيع, سند قبض, عميل نقدي. Each one only starts
 * MainActivity with a [QuickLaunch] action; the app (not the widget) decides what to open, so a
 * locked app asks for its PIN first and a signed-out one asks for the login first.
 *
 * The widget shows no data of its own, so it never needs refreshing (updatePeriodMillis = 0).
 */
class QuickActionsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_actions)
            views.setOnClickPendingIntent(R.id.widget_new_invoice, launch(context, QuickLaunch.ACTION_NEW_INVOICE, 1))
            views.setOnClickPendingIntent(R.id.widget_new_receipt, launch(context, QuickLaunch.ACTION_NEW_RECEIPT, 2))
            views.setOnClickPendingIntent(R.id.widget_cash_sale, launch(context, QuickLaunch.ACTION_NEW_CASH_SALE, 3))
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    private fun launch(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            // SINGLE_TOP (not CLEAR_TOP): an app that is already open receives the request in onNewIntent and
            // keeps whatever screen — or half-written invoice — is underneath; CLEAR_TOP would rebuild it.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
