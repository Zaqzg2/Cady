package com.cady.cadysalesapp.ui.reports

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cady.cadysalesapp.ui.common.CadyAppBar
import com.cady.cadysalesapp.ui.common.CadyCard
import com.cady.cadysalesapp.ui.common.HeroCard
import com.cady.cadysalesapp.ui.common.SectionTitle
import com.cady.cadysalesapp.ui.common.StatTile
import com.cady.cadysalesapp.ui.common.animatedMoneyText
import com.cady.cadysalesapp.ui.common.formatCompact
import com.cady.cadysalesapp.ui.common.formatMoney
import com.cady.cadysalesapp.ui.manager.RepFilterRow
import com.cady.cadysalesapp.ui.theme.cady
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val dayFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd")

/**
 * The reports page as a dashboard. From the top: the period (chips, and arrows / a sideways swipe on the big
 * card to step to the previous or next one), the net-sales hero with its comparison against the period before,
 * four KPI tiles, the daily trend, how sales were paid (cash / credit), and the rankings — best customers, best
 * sellers, biggest debtors and, for a manager, the reps. Every number is computed from the same records, with
 * the same definitions, as the report page it replaces (the definitions are at the bottom).
 */
@Composable
fun ReportsScreen(
    bottomBar: @Composable () -> Unit = {},
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val data = state.dashboard
    val summary = data.summary
    val previous = data.previous
    val scope = rememberCoroutineScope()

    // Swiping the hero: a finger moving right pulls the NEWER period in, moving left the OLDER one — the
    // time axis runs in the reading direction of an Arabic screen (older at the right, newer at the left).
    var pull by remember { mutableFloatStateOf(0f) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { CadyAppBar(title = { Text("لوحة التقارير") }) },
        bottomBar = bottomBar,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReportPeriod.entries.forEach { option ->
                    FilterChip(
                        selected = state.period == option,
                        onClick = { viewModel.selectPeriod(option) },
                        label = { Text(option.label) },
                    )
                }
            }

            if (state.isManager) {
                RepFilterRow(reps = state.reps, selectedId = state.repId, onSelect = viewModel::selectRep)
            }

            if (state.period == ReportPeriod.CUSTOM) {
                val from = state.customFrom ?: LocalDate.now()
                val to = state.customTo ?: from
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { pickDay(context, from, viewModel::setCustomFrom) },
                        modifier = Modifier.weight(1f),
                    ) { Text("من: ${from.format(dayFormat)}") }
                    OutlinedButton(
                        onClick = { pickDay(context, to, viewModel::setCustomTo) },
                        modifier = Modifier.weight(1f),
                    ) { Text("إلى: ${to.format(dayFormat)}") }
                }
            }

            RangeNavigator(
                label = state.rangeLabel,
                canStep = state.canStepBack,
                canGoNewer = state.canStepForward,
                onOlder = { viewModel.stepPeriod(1) },
                onNewer = { viewModel.stepPeriod(-1) },
            )

            // ---- Hero: net sales -------------------------------------------------------------------------
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = pull * 0.35f
                        alpha = 1f - (abs(pull) / 1200f).coerceIn(0f, 0.35f)
                    }
                    .pointerInput(state.canStepBack, state.canStepForward) {
                        if (state.canStepBack) {
                            detectHorizontalDragGestures(
                                onDragStart = { pull = 0f },
                                onDragEnd = {
                                    val travelled = pull
                                    if (travelled > 110f) viewModel.stepPeriod(-1)
                                    if (travelled < -110f) viewModel.stepPeriod(1)
                                    scope.launch {
                                        animate(travelled, 0f, animationSpec = spring(dampingRatio = 0.75f)) { value, _ -> pull = value }
                                    }
                                },
                                onDragCancel = {
                                    val travelled = pull
                                    scope.launch {
                                        animate(travelled, 0f, animationSpec = spring(dampingRatio = 0.75f)) { value, _ -> pull = value }
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    pull += dragAmount
                                },
                            )
                        }
                    },
            ) {
                HeroCard {
                    Text(
                        "صافي المبيعات",
                        style = MaterialTheme.typography.labelLarge,
                        color = LocalContentColor.current.copy(alpha = 0.85f),
                    )
                    Text(
                        animatedMoneyText(summary.netSales),
                        style = MaterialTheme.typography.displaySmall,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(6.dp))
                    DeltaChip(
                        current = summary.netSales,
                        previous = previous?.netSales,
                        label = state.compareLabel,
                        onHero = true,
                    )
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = LocalContentColor.current.copy(alpha = 0.2f))
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        HeroFigure("متوسط الفاتورة", if (summary.salesCount > 0) formatCompact(summary.salesTotal / summary.salesCount) else "—")
                        HeroFigure("نسبة المرتجعات", if (summary.salesTotal > 0.005) percentOf(summary.returnsTotal, summary.salesTotal) else "—")
                        HeroFigure("نسبة التحصيل", if (summary.salesTotal > 0.005) percentOf(summary.collectionsTotal, summary.salesTotal) else "—")
                    }
                }
            }

            // ---- KPI tiles ------------------------------------------------------------------------------
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    label = "المبيعات",
                    value = formatMoney(summary.salesTotal),
                    caption = "${summary.salesCount} فاتورة",
                    icon = Icons.Filled.Payments,
                    tint = MaterialTheme.cady.sale,
                    modifier = Modifier.weight(1f),
                    footer = { DeltaChip(summary.salesTotal, previous?.salesTotal, "") },
                )
                StatTile(
                    label = "المرتجعات",
                    value = formatMoney(summary.returnsTotal),
                    caption = "${summary.returnsCount} فاتورة",
                    icon = Icons.Filled.Replay,
                    tint = MaterialTheme.cady.returns,
                    modifier = Modifier.weight(1f),
                    footer = { DeltaChip(summary.returnsTotal, previous?.returnsTotal, "", higherIsBetter = false) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    label = "التحصيلات",
                    value = formatMoney(summary.collectionsTotal),
                    caption = "${summary.collectionsCount} سند",
                    icon = Icons.Filled.Receipt,
                    tint = MaterialTheme.cady.receipt,
                    modifier = Modifier.weight(1f),
                    footer = { DeltaChip(summary.collectionsTotal, previous?.collectionsTotal, "") },
                )
                StatTile(
                    label = "مديونية العملاء",
                    value = formatMoney(data.debt.total),
                    caption = "${data.debt.debtors} مدين من ${data.debt.customers}",
                    icon = Icons.Filled.Savings,
                    tint = MaterialTheme.cady.debt,
                    valueColor = if (data.debt.total > 0.005) MaterialTheme.cady.debt else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }

            // ---- Trend ----------------------------------------------------------------------------------
            TrendCard(data)

            // ---- How sales were paid --------------------------------------------------------------------
            PaymentSplitCard(cash = data.cashSales, credit = data.creditSales)

            // ---- Rankings -------------------------------------------------------------------------------
            if (data.topCustomers.isNotEmpty()) {
                RankingCard("أفضل العملاء", "حسب صافي المشتريات في الفترة", data.topCustomers, MaterialTheme.cady.sale)
            }
            if (data.topProducts.isNotEmpty()) {
                RankingCard("الأكثر مبيعًا", "حسب قيمة المبيع في الفترة", data.topProducts, MaterialTheme.cady.receipt)
            }
            if (data.topDebtors.isNotEmpty()) {
                RankingCard("أعلى المديونيات", "الرصيد الحالي، لكل الفترات", data.topDebtors, MaterialTheme.cady.debt)
            }
            if (state.isManager && data.repSales.isNotEmpty()) {
                RankingCard("أداء المندوبين", "صافي المبيعات في الفترة", data.repSales, MaterialTheme.colorScheme.primary)
            }

            DefinitionsCard(isManager = state.isManager)
        }
    }
}

/** ‹ the period's name › — and what the sideways swipe on the big card does. */
@Composable
private fun RangeNavigator(
    label: String,
    canStep: Boolean,
    canGoNewer: Boolean,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // In the right-to-left layout the first child sits on the right: that is the OLDER side.
        if (canStep) {
            FilledTonalIconButton(onClick = onOlder) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "الفترة السابقة")
            }
        } else {
            Spacer(Modifier.size(40.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (canStep) {
                Text(
                    "اسحب البطاقة الكبيرة يمينًا أو يسارًا للتنقل",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (canStep) {
            FilledTonalIconButton(onClick = onNewer, enabled = canGoNewer) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "الفترة التالية")
            }
        } else {
            Spacer(Modifier.size(40.dp))
        }
    }
}

@Composable
private fun HeroFigure(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = LocalContentColor.current.copy(alpha = 0.8f))
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

private fun percentOf(part: Double, whole: Double): String {
    val percent = part / whole * 100.0
    return String.format(Locale.US, "%.0f", percent) + "%"
}

/** Sales per day with tap-to-inspect: the bar chosen shows its sales, returns and collections above the chart. */
@Composable
private fun TrendCard(data: DashboardData) {
    CadyCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(
                text = "منحنى المبيعات",
                trailing = {
                    if (data.trendCaption.isNotEmpty()) {
                        Text(
                            data.trendCaption,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
            if (data.trend.isEmpty()) {
                Text(
                    "لا توجد بيانات في هذه الفترة.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                var selected by remember(data.trend) {
                    mutableIntStateOf(data.trend.indexOfLast { it.inPeriod }.coerceAtLeast(0))
                }
                val point = data.trend[selected.coerceIn(0, data.trend.lastIndex)]
                Column(modifier = Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(point.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TrendFigure("مبيعات", point.sales, MaterialTheme.cady.sale)
                        TrendFigure("مرتجعات", point.returns, MaterialTheme.cady.returns)
                        TrendFigure("تحصيلات", point.collections, MaterialTheme.cady.receipt)
                    }
                }
                TrendBars(points = data.trend, selectedIndex = selected, onSelect = { selected = it })
            }
        }
    }
}

@Composable
private fun TrendFigure(label: String, value: Double, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        Text(formatMoney(value), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Cash vs credit sales as a ring, with each side's amount and share beside it. */
@Composable
private fun PaymentSplitCard(cash: Double, credit: Double) {
    val total = cash + credit
    CadyCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("البيع نقدًا وآجلًا")
            if (total <= 0.005) {
                Text(
                    "لا توجد مبيعات في هذه الفترة.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    DonutChart(
                        slices = listOf(
                            DonutSlice(cash, MaterialTheme.cady.success),
                            DonutSlice(credit, MaterialTheme.cady.warning),
                        ),
                        modifier = Modifier.size(132.dp),
                        strokeWidth = 16.dp,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                percentOf(cash, total),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                "نقدًا",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        LegendRow("نقدًا", cash, percentOf(cash, total), MaterialTheme.cady.success)
                        LegendRow("آجل", credit, percentOf(credit, total), MaterialTheme.cady.warning)
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendRow(label: String, value: Double, percent: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                "$label · $percent",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(formatMoney(value), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun RankingCard(title: String, subtitle: String, items: List<RankedItem>, color: Color) {
    CadyCard {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RankedBars(items = items, color = color)
        }
    }
}

/** The definitions the old page printed under its numbers — kept, but folded away until asked for. */
@Composable
private fun DefinitionsCard(isManager: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    CadyCard(modifier = Modifier.animateContentSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("كيف تُحسب هذه الأرقام؟", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val style = MaterialTheme.typography.bodySmall
                    val color = MaterialTheme.colorScheme.onSurfaceVariant
                    Text(
                        "المبيعات = مجموع الفواتير بعد الخصم · المرتجعات = مجموع فواتير المرتجع بعد الخصم · " +
                            "التحصيلات = مجموع سندات القبض (نقدًا وتحويلًا) · صافي المبيعات = المبيعات − المرتجعات. " +
                            "الفاتورة الآجلة تُحسب ضمن المبيعات، وتخفّض التحصيلات مديونية العميل.",
                        style = style, color = color,
                    )
                    Text(
                        "نسبة المرتجعات = المرتجعات ÷ المبيعات · نسبة التحصيل = التحصيلات ÷ المبيعات · " +
                            "متوسط الفاتورة = المبيعات ÷ عدد فواتير البيع. الأسهم تقارن الفترة بالتي قبلها مباشرة؛ " +
                            "والشهر أو السنة الجارية تُقارَن بنفس عدد الأيام من السابقة.",
                        style = style, color = color,
                    )
                    Text(
                        "المديونية لكل الفترات: الرصيد الافتتاحي + المبيعات − المرتجعات − التحصيلات، كما في كشف كل عميل.",
                        style = style, color = color,
                    )
                    if (isManager) {
                        Text(
                            "الأرقام من البيانات الموجودة على هذا الجهاز — ما سحبه من Firebase واستورده من ملفات المندوبين. " +
                                "ما لم يصل بعد لا يظهر هنا.",
                            style = style, color = color,
                        )
                    }
                }
            }
        }
    }
}

/** The system date dialog, as on the receipt screen. */
private fun pickDay(context: Context, initial: LocalDate, onPicked: (LocalDate) -> Unit) {
    DatePickerDialog(
        context,
        { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day)) },
        initial.year, initial.monthValue - 1, initial.dayOfMonth,
    ).show()
}
