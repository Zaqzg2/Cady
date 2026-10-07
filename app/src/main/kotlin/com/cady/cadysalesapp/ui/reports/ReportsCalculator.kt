package com.cady.cadysalesapp.ui.reports

import com.cady.cadysalesapp.data.local.dao.InvoiceProductLine
import com.cady.cadysalesapp.data.local.entity.CustomerEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceEntity
import com.cady.cadysalesapp.data.local.entity.InvoiceKind
import com.cady.cadysalesapp.data.local.entity.PaymentMode
import com.cady.cadysalesapp.data.local.entity.ReceiptEntity
import com.cady.cadysalesapp.domain.computeCustomerBalances
import com.cady.cadysalesapp.domain.computeInvoiceTotals
import com.cady.cadysalesapp.ui.common.formatQuantity
import com.cady.cadysalesapp.ui.common.monthName
import com.cady.cadysalesapp.ui.common.weekdayName
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class ReportPeriod(val label: String) {
    TODAY("اليوم"),
    LAST_7_DAYS("آخر 7 أيام"),
    THIS_MONTH("هذا الشهر"),
    THIS_YEAR("هذه السنة"),
    ALL("الكل"),
    CUSTOM("فترة محددة"),
}

data class PeriodSummary(
    val salesTotal: Double = 0.0,
    val salesCount: Int = 0,
    val returnsTotal: Double = 0.0,
    val returnsCount: Int = 0,
    val collectionsTotal: Double = 0.0,
    val collectionsCount: Int = 0,
) {
    /** Sales minus returns — what was really sold in the period. */
    val netSales: Double get() = salesTotal - returnsTotal
}

data class DebtSummary(
    /** The sum of every customer's balance, as of now (not limited to the period). */
    val total: Double = 0.0,
    /** Customers who owe something (balance above zero). */
    val debtors: Int = 0,
    val customers: Int = 0,
)

/** Everything the report reads, already narrowed to what this account may see. */
internal class ReportInput(
    val invoices: List<InvoiceEntity>,
    val receipts: List<ReceiptEntity>,
    val customers: List<CustomerEntity>,
    /** Invoice id → the sum of its lines (price × quantity). */
    val subTotals: Map<String, Double>,
    /** One row per (invoice, product), for the "top products" ranking. */
    val productLines: List<InvoiceProductLine> = emptyList(),
)

/**
 * [start, end) of the period, or null for "all time". A custom range includes both of its days.
 *
 * [offset] steps back in time by whole periods (0 = the current one): 1 is yesterday / the previous 7 days /
 * last month / last year. The dashboard's swipe and arrows move it; "all time" and a custom range ignore it.
 */
internal fun periodRange(
    period: ReportPeriod,
    customFrom: LocalDate?,
    customTo: LocalDate?,
    offset: Int = 0,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): Pair<Instant, Instant>? {
    fun start(day: LocalDate): Instant = day.atStartOfDay(zone).toInstant()
    val back = offset.coerceAtLeast(0).toLong()
    return when (period) {
        ReportPeriod.TODAY -> {
            val day = today.minusDays(back)
            start(day) to start(day.plusDays(1))
        }
        ReportPeriod.LAST_7_DAYS -> {
            val last = today.minusDays(7 * back)
            start(last.minusDays(6)) to start(last.plusDays(1))
        }
        ReportPeriod.THIS_MONTH -> {
            val first = today.withDayOfMonth(1).minusMonths(back)
            start(first) to start(first.plusMonths(1))
        }
        ReportPeriod.THIS_YEAR -> {
            val first = today.withDayOfYear(1).minusYears(back)
            start(first) to start(first.plusYears(1))
        }
        ReportPeriod.ALL -> null
        ReportPeriod.CUSTOM -> {
            val a = customFrom ?: today
            val b = customTo ?: a
            val first = if (a.isAfter(b)) b else a
            val last = if (a.isAfter(b)) a else b
            start(first) to start(last.plusDays(1))
        }
    }
}

/**
 * The period to compare [periodRange] with: the one right before it. A month or year still in progress is
 * compared with the same number of days of the previous one (month-to-date vs month-to-date), not with its
 * full length, which would make every ongoing month look like a collapse.
 */
internal fun previousRange(
    period: ReportPeriod,
    customFrom: LocalDate?,
    customTo: LocalDate?,
    offset: Int = 0,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): Pair<Instant, Instant>? {
    fun start(day: LocalDate): Instant = day.atStartOfDay(zone).toInstant()
    val back = offset.coerceAtLeast(0)
    return when (period) {
        ReportPeriod.TODAY, ReportPeriod.LAST_7_DAYS -> periodRange(period, customFrom, customTo, back + 1, today, zone)
        ReportPeriod.THIS_MONTH -> {
            val first = today.withDayOfMonth(1).minusMonths(back.toLong())
            val previousFirst = first.minusMonths(1)
            val sameLength = previousFirst.plusDays(today.dayOfMonth.toLong())
            val end = if (back == 0 && sameLength.isBefore(first)) sameLength else first
            start(previousFirst) to start(end)
        }
        ReportPeriod.THIS_YEAR -> {
            val first = today.withDayOfYear(1).minusYears(back.toLong())
            val previousFirst = first.minusYears(1)
            val sameLength = previousFirst.plusDays(today.dayOfYear.toLong())
            val end = if (back == 0 && sameLength.isBefore(first)) sameLength else first
            start(previousFirst) to start(end)
        }
        ReportPeriod.ALL -> null
        ReportPeriod.CUSTOM -> {
            val current = periodRange(period, customFrom, customTo, 0, today, zone) ?: return null
            val firstDay = current.first.atZone(zone).toLocalDate()
            val endDay = current.second.atZone(zone).toLocalDate()
            val days = ChronoUnit.DAYS.between(firstDay, endDay)
            start(firstDay.minusDays(days)) to start(firstDay)
        }
    }
}

private fun labelDate(day: LocalDate, today: LocalDate): String =
    "${day.dayOfMonth} ${monthName(day.monthValue)}" + if (day.year != today.year) " ${day.year}" else ""

/** What the dashboard calls the selected period: "اليوم", "أمس", "أكتوبر 2026", "1 أكتوبر – 7 أكتوبر"… */
internal fun periodLabel(
    period: ReportPeriod,
    customFrom: LocalDate?,
    customTo: LocalDate?,
    offset: Int = 0,
    today: LocalDate = LocalDate.now(),
): String = when (period) {
    ReportPeriod.TODAY -> when (offset) {
        0 -> "اليوم"
        1 -> "أمس"
        else -> labelDate(today.minusDays(offset.toLong()), today)
    }
    ReportPeriod.LAST_7_DAYS -> if (offset == 0) {
        "آخر 7 أيام"
    } else {
        val last = today.minusDays(7L * offset)
        "${labelDate(last.minusDays(6), today)} – ${labelDate(last, today)}"
    }
    ReportPeriod.THIS_MONTH -> {
        val first = today.withDayOfMonth(1).minusMonths(offset.toLong())
        if (offset == 0) "هذا الشهر · ${monthName(first.monthValue)}" else "${monthName(first.monthValue)} ${first.year}"
    }
    ReportPeriod.THIS_YEAR -> if (offset == 0) "هذه السنة · ${today.year}" else "سنة ${today.year - offset}"
    ReportPeriod.ALL -> "كل الفترات"
    ReportPeriod.CUSTOM -> {
        val a = customFrom ?: today
        val b = customTo ?: a
        val first = if (a.isAfter(b)) b else a
        val last = if (a.isAfter(b)) a else b
        if (first == last) labelDate(first, today) else "${labelDate(first, today)} – ${labelDate(last, today)}"
    }
}

/** "… عن أمس" — the words after the percentage on the hero's trend chip. */
internal fun compareLabel(period: ReportPeriod, offset: Int): String = when (period) {
    ReportPeriod.TODAY -> if (offset == 0) "عن أمس" else "عن اليوم السابق"
    ReportPeriod.LAST_7_DAYS -> "عن الأسبوع السابق"
    ReportPeriod.THIS_MONTH -> "عن الشهر السابق"
    ReportPeriod.THIS_YEAR -> "عن السنة السابقة"
    ReportPeriod.CUSTOM -> "عن الفترة السابقة"
    ReportPeriod.ALL -> ""
}

/**
 * The two numbers sets the Reports screen shows:
 *  - the period summary: sales, returns and collections whose date falls in [range];
 *  - the customers' total debt, with the same formula as every customer's own balance
 *    (opening balance + sales − returns − receipts), over all time.
 * [repId] narrows everything to one rep's records (a manager's filter); null = all of [input].
 */
internal fun buildReport(input: ReportInput, repId: String?, range: Pair<Instant, Instant>?): Pair<PeriodSummary, DebtSummary> {
    val invoices = if (repId == null) input.invoices else input.invoices.filter { it.ownerUid == repId }
    val receipts = if (repId == null) input.receipts else input.receipts.filter { it.ownerUid == repId }
    val customers = if (repId == null) input.customers else input.customers.filter { it.ownerUid == repId }

    fun inRange(at: Instant): Boolean = range == null || (!at.isBefore(range.first) && at.isBefore(range.second))

    var salesTotal = 0.0
    var salesCount = 0
    var returnsTotal = 0.0
    var returnsCount = 0
    // Per customer: sales − returns, then minus receipts — all time.
    val owed = HashMap<String, Double>()

    for (invoice in invoices) {
        val total = computeInvoiceTotals(invoice, input.subTotals[invoice.id] ?: 0.0).grandTotal
        val signed = if (invoice.kind == InvoiceKind.SALE_RETURN) -total else total
        owed[invoice.customerId] = (owed[invoice.customerId] ?: 0.0) + signed
        if (inRange(invoice.date)) {
            if (invoice.kind == InvoiceKind.SALE_RETURN) {
                returnsTotal += total
                returnsCount++
            } else {
                salesTotal += total
                salesCount++
            }
        }
    }

    var collectionsTotal = 0.0
    var collectionsCount = 0
    for (receipt in receipts) {
        owed[receipt.customerId] = (owed[receipt.customerId] ?: 0.0) - receipt.amount
        if (inRange(receipt.date)) {
            collectionsTotal += receipt.amount
            collectionsCount++
        }
    }

    var debtTotal = 0.0
    var debtors = 0
    for (customer in customers) {
        val balance = customer.openingBalance + (owed[customer.id] ?: 0.0)
        debtTotal += balance
        if (balance > 0.005) debtors++
    }

    return Pair(
        PeriodSummary(salesTotal, salesCount, returnsTotal, returnsCount, collectionsTotal, collectionsCount),
        DebtSummary(debtTotal, debtors, customers.size),
    )
}

// ---------------------------------------------------------------------------------------------------------
// Dashboard
// ---------------------------------------------------------------------------------------------------------

/** One line of a ranking: a customer, a product or a rep, and what it adds up to. */
data class RankedItem(
    val id: String,
    val name: String,
    val value: Double,
    val caption: String? = null,
)

/** One bar of the trend chart — a day, or a month in long periods. */
data class TrendPoint(
    /** The short text under the bar ("6", "10"). */
    val label: String,
    /** The full name shown when the bar is tapped ("الاثنين 5 أكتوبر"). */
    val title: String,
    val sales: Double,
    val returns: Double,
    val collections: Double,
    /** False for context bars outside the selected period (a one-day period is drawn among the 7 days around it). */
    val inPeriod: Boolean,
)

/** Everything the dashboard draws, computed in one pass from the same records as the old report. */
data class DashboardData(
    val summary: PeriodSummary = PeriodSummary(),
    /** The period right before the selected one (null for "all time"), for the ▲/▼ comparison. */
    val previous: PeriodSummary? = null,
    val debt: DebtSummary = DebtSummary(),
    val cashSales: Double = 0.0,
    val creditSales: Double = 0.0,
    val trend: List<TrendPoint> = emptyList(),
    val trendCaption: String = "",
    val topCustomers: List<RankedItem> = emptyList(),
    val topProducts: List<RankedItem> = emptyList(),
    val topDebtors: List<RankedItem> = emptyList(),
    val repSales: List<RankedItem> = emptyList(),
)

/**
 * The dashboard's numbers. [repId] narrows to one rep's records (a manager's filter); [includeReps] adds the
 * per-rep leaderboard (only meaningful for a manager looking at everyone). Definitions are exactly those of
 * [buildReport]: sales and returns are totals after discount, collections are receipts, debt is
 * opening balance + sales − returns − receipts over all time.
 */
internal fun buildDashboard(
    input: ReportInput,
    repId: String?,
    range: Pair<Instant, Instant>?,
    previousRange: Pair<Instant, Instant>?,
    repNames: Map<String, String>,
    includeReps: Boolean,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): DashboardData {
    val (summary, debt) = buildReport(input, repId, range)
    val previous = if (previousRange != null) buildReport(input, repId, previousRange).first else null

    val invoices = if (repId == null) input.invoices else input.invoices.filter { it.ownerUid == repId }
    val receipts = if (repId == null) input.receipts else input.receipts.filter { it.ownerUid == repId }
    val customers = if (repId == null) input.customers else input.customers.filter { it.ownerUid == repId }

    fun inRange(at: Instant): Boolean = range == null || (!at.isBefore(range.first) && at.isBefore(range.second))

    var cashSales = 0.0
    var creditSales = 0.0
    val customerNet = HashMap<String, Double>()
    val customerName = HashMap<String, String>()
    val repNet = HashMap<String, Double>()
    val saleIds = HashSet<String>()
    for (invoice in invoices) {
        if (!inRange(invoice.date)) continue
        val total = computeInvoiceTotals(invoice, input.subTotals[invoice.id] ?: 0.0).grandTotal
        val signed = if (invoice.kind == InvoiceKind.SALE_RETURN) -total else total
        customerNet[invoice.customerId] = (customerNet[invoice.customerId] ?: 0.0) + signed
        customerName[invoice.customerId] = invoice.customerName
        repNet[invoice.ownerUid] = (repNet[invoice.ownerUid] ?: 0.0) + signed
        if (invoice.kind == InvoiceKind.SALE) {
            saleIds += invoice.id
            if (invoice.paymentMode == PaymentMode.CASH) cashSales += total else creditSales += total
        }
    }

    val topCustomers = customerNet.entries
        .filter { it.value > 0.005 }
        .sortedByDescending { it.value }
        .take(5)
        .map { RankedItem(it.key, customerName[it.key] ?: "—", it.value) }

    // Products: what the period's sale invoices sold, summed per product (returns are not subtracted — this is
    // a "what moves" ranking, and a return line carries no sale price of its own to net against).
    val productAmount = HashMap<String, Double>()
    val productQuantity = HashMap<String, Double>()
    val productName = HashMap<String, String>()
    for (line in input.productLines) {
        if (line.invoiceId !in saleIds) continue
        productAmount[line.productId] = (productAmount[line.productId] ?: 0.0) + line.amount
        productQuantity[line.productId] = (productQuantity[line.productId] ?: 0.0) + line.quantity
        // A line sold on offer is named "منتج (عرض 10%)": the ranking wants the product, not the offer.
        productName.getOrPut(line.productId) { line.productName.substringBefore(" (عرض").trim() }
    }
    val topProducts = productAmount.entries
        .filter { it.value > 0.005 }
        .sortedByDescending { it.value }
        .take(5)
        .map {
            RankedItem(
                id = it.key,
                name = productName[it.key] ?: "—",
                value = it.value,
                caption = "× ${formatQuantity(productQuantity[it.key] ?: 0.0)}",
            )
        }

    val balances = computeCustomerBalances(customers, invoices, receipts, input.subTotals)
    val topDebtors = customers
        .filter { (balances[it.id] ?: 0.0) > 0.005 }
        .sortedByDescending { balances[it.id] ?: 0.0 }
        .take(5)
        .map { RankedItem(it.id, it.name, balances[it.id] ?: 0.0) }

    val repSales = if (includeReps && repId == null) {
        repNet.entries
            .filter { kotlin.math.abs(it.value) > 0.005 }
            .sortedByDescending { it.value }
            .map { RankedItem(it.key, repNames[it.key] ?: "—", it.value) }
    } else {
        emptyList()
    }

    val (trend, trendCaption) = buildTrend(invoices, receipts, input.subTotals, range, today, zone)

    return DashboardData(
        summary = summary,
        previous = previous,
        debt = debt,
        cashSales = cashSales,
        creditSales = creditSales,
        trend = trend,
        trendCaption = trendCaption,
        topCustomers = topCustomers,
        topProducts = topProducts,
        topDebtors = topDebtors,
        repSales = repSales,
    )
}

/**
 * The bars: daily for a period of up to 31 days, monthly (the last 12) for anything longer. A one-day period
 * is drawn among the 7 days around it — a single bar says nothing — with the selected day highlighted.
 */
private fun buildTrend(
    invoices: List<InvoiceEntity>,
    receipts: List<ReceiptEntity>,
    subTotals: Map<String, Double>,
    range: Pair<Instant, Instant>?,
    today: LocalDate,
    zone: ZoneId,
): Pair<List<TrendPoint>, String> {
    class Bucket(val label: String, val title: String, val inPeriod: Boolean) {
        var sales = 0.0
        var returns = 0.0
        var collections = 0.0
    }

    val periodStart: LocalDate
    val periodEnd: LocalDate // exclusive
    if (range != null) {
        periodStart = range.first.atZone(zone).toLocalDate()
        periodEnd = range.second.atZone(zone).toLocalDate()
    } else {
        val earliest = (invoices.map { it.date } + receipts.map { it.date }).minOrNull()
            ?: return Pair(emptyList(), "")
        periodStart = earliest.atZone(zone).toLocalDate()
        periodEnd = today.plusDays(1)
    }
    val days = ChronoUnit.DAYS.between(periodStart, periodEnd).toInt().coerceAtLeast(1)

    val buckets: List<Bucket>
    val indexOf: (LocalDate) -> Int
    val caption: String
    if (days <= 31) {
        val windowStart = if (days <= 1) periodEnd.minusDays(7) else periodStart
        val windowDays = if (days <= 1) 7 else days
        buckets = (0 until windowDays).map { i ->
            val day = windowStart.plusDays(i.toLong())
            Bucket(
                label = day.dayOfMonth.toString(),
                title = "${weekdayName(day)} ${day.dayOfMonth} ${monthName(day.monthValue)}",
                inPeriod = !day.isBefore(periodStart) && day.isBefore(periodEnd),
            )
        }
        indexOf = { date -> ChronoUnit.DAYS.between(windowStart, date).toInt() }
        caption = if (days <= 1) "آخر 7 أيام" else "يوميًا"
    } else {
        val lastMonth = YearMonth.from(periodEnd.minusDays(1))
        val naturalFirst = YearMonth.from(periodStart)
        val firstMonth = if (ChronoUnit.MONTHS.between(naturalFirst, lastMonth) >= 12) lastMonth.minusMonths(11) else naturalFirst
        val count = (ChronoUnit.MONTHS.between(firstMonth, lastMonth) + 1).toInt()
        buckets = (0 until count).map { i ->
            val month = firstMonth.plusMonths(i.toLong())
            Bucket(
                label = month.monthValue.toString(),
                title = "${monthName(month.monthValue)} ${month.year}",
                inPeriod = true,
            )
        }
        indexOf = { date -> ChronoUnit.MONTHS.between(firstMonth, YearMonth.from(date)).toInt() }
        caption = "شهريًا"
    }

    for (invoice in invoices) {
        val index = indexOf(invoice.date.atZone(zone).toLocalDate())
        if (index !in buckets.indices) continue
        val total = computeInvoiceTotals(invoice, subTotals[invoice.id] ?: 0.0).grandTotal
        if (invoice.kind == InvoiceKind.SALE_RETURN) buckets[index].returns += total else buckets[index].sales += total
    }
    for (receipt in receipts) {
        val index = indexOf(receipt.date.atZone(zone).toLocalDate())
        if (index !in buckets.indices) continue
        buckets[index].collections += receipt.amount
    }
    return Pair(
        buckets.map { TrendPoint(it.label, it.title, it.sales, it.returns, it.collections, it.inPeriod) },
        caption,
    )
}
