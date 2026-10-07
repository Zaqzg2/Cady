package com.cady.cadysalesapp.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

private data class TabItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

private val tabs = listOf(
    TabItem(CadyDestination.Home.route, "الرئيسية", Icons.Outlined.Home, Icons.Filled.Home),
    TabItem(CadyDestination.Customers.route, "العملاء", Icons.Outlined.Group, Icons.Filled.Group),
    TabItem(CadyDestination.Products.route, "المنتجات", Icons.Outlined.Inventory2, Icons.Filled.Inventory2),
    // The reports page is a dashboard now, so it wears the dashboard icon; the name stays the one people know.
    TabItem(CadyDestination.Reports.route, "التقارير", Icons.Outlined.Dashboard, Icons.Filled.Dashboard),
)

/** A fifth tab that only a manager account gets — the manager's screens live in the same app. */
private val managerTab =
    TabItem(
        CadyDestination.ManagerDashboard.route,
        "المدير",
        Icons.Outlined.AdminPanelSettings,
        Icons.Filled.AdminPanelSettings,
    )

/**
 * This was the missing piece: Home/Customers/Products/Reports each existed as
 * a real screen but nothing let a person move between them — Home's four
 * quick-action cards only ever led to Invoice/Receipt. Settings deliberately
 * stays off this bar (reachable via Home's top-right icon instead), matching
 * the current app's "swipe-only settings" pattern from the review doc.
 *
 * Phase 9: outlined icons that fill in when selected (and pop slightly), a soft pill behind the selected
 * tab, a hairline above the bar instead of a tinted slab, and a light haptic tick on every change of tab.
 */
@Composable
fun CadyBottomBar(navController: NavHostController, isManager: Boolean = false) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val visibleTabs = if (isManager) tabs + managerTab else tabs
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme

    Box {
        NavigationBar(
            containerColor = scheme.surfaceContainerLowest,
            tonalElevation = 0.dp,
        ) {
            visibleTabs.forEach { tab ->
                val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                val iconScale by animateFloatAsState(
                    targetValue = if (selected) 1.12f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "tabIconScale",
                )
                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        navController.navigate(tab.route) {
                            popUpTo(CadyDestination.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.icon,
                            contentDescription = tab.label,
                            modifier = Modifier.graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            },
                        )
                    },
                    label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = scheme.onPrimaryContainer,
                        selectedTextColor = scheme.primary,
                        indicatorColor = scheme.primaryContainer,
                        unselectedIconColor = scheme.onSurfaceVariant,
                        unselectedTextColor = scheme.onSurfaceVariant,
                    ),
                )
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(scheme.outlineVariant))
    }
}
