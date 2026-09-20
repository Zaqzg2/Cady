package com.cady.cadysalesapp.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

private data class TabItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    TabItem(CadyDestination.Home.route, "الرئيسية", Icons.Filled.Home),
    TabItem(CadyDestination.Customers.route, "العملاء", Icons.Filled.Group),
    TabItem(CadyDestination.Products.route, "المنتجات", Icons.Filled.Inventory2),
    TabItem(CadyDestination.Reports.route, "التقارير", Icons.Filled.Assessment),
)

/**
 * This was the missing piece: Home/Customers/Products/Reports each existed as
 * a real screen but nothing let a person move between them — Home's four
 * quick-action cards only ever led to Invoice/Receipt. Settings deliberately
 * stays off this bar (reachable via Home's top-right icon instead), matching
 * the current app's "swipe-only settings" pattern from the review doc.
 */
@Composable
fun CadyBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationBar {
        tabs.forEach { tab ->
            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(CadyDestination.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
            )
        }
    }
}
