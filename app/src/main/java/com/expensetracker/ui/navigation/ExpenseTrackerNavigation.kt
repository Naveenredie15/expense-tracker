package com.expensetracker.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.expensetracker.ui.screen.home.HomeScreen
import com.expensetracker.ui.screen.transactions.TransactionsScreen
import com.expensetracker.ui.screen.categories.CategoriesScreen
import com.expensetracker.ui.screen.insights.InsightsScreen
import com.expensetracker.ui.screen.profile.ProfileScreen
import com.expensetracker.ui.screen.profile.AddAccountScreen
import com.expensetracker.ui.screen.recurring.RecurringExpensesScreen
import com.expensetracker.ui.screen.unrecognized.UnrecognizedSmsScreen
import com.expensetracker.ui.screen.formats.SmsFormatsScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Filled.Home)
    object Transactions : Screen("transactions", "Transactions", Icons.Filled.List)
    object Categories : Screen("categories", "Categories", Icons.Filled.Category)
    object Insights : Screen("insights", "Insights", Icons.Filled.Analytics)
    object Profile : Screen("profile", "Profile", Icons.Filled.Person)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseTrackerNavigation(
    hasSmsPermission: Boolean,
    onRequestPermissions: () -> Unit
) {
    val navController = rememberNavController()
    val items = listOf(
        Screen.Home,
        Screen.Transactions,
        Screen.Insights,
        Screen.Profile
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            // Always land on the tab's ROOT, even from a nested detail
                            // screen (e.g. Categories opened from Profile). We intentionally
                            // do NOT save/restore state here — otherwise restoreState would
                            // bring the nested detail screen back instead of the tab root.
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id)
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    hasSmsPermission = hasSmsPermission,
                    onRequestPermissions = onRequestPermissions
                )
            }
            composable(Screen.Transactions.route) {
                TransactionsScreen()
            }
            composable(Screen.Categories.route) {
                CategoriesScreen()
            }
            composable(Screen.Insights.route) {
                InsightsScreen()
            }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    onAddAccount = { navController.navigate("add_account") },
                    onEditAccount = { id -> navController.navigate("edit_account/$id") },
                    onOpenCategories = { navController.navigate(Screen.Categories.route) },
                    onOpenRecurring = { navController.navigate("recurring_expenses") },
                    onOpenUnrecognized = { navController.navigate("unrecognized_sms") },
                    onOpenFormats = { navController.navigate("sms_formats") }
                )
            }
            composable("add_account") {
                AddAccountScreen(
                    onDone = { navController.popBackStack() },
                    // After adding, jump straight to tagging the account's SMS formats.
                    onAccountAdded = {
                        navController.popBackStack()
                        navController.navigate("unrecognized_sms")
                    }
                )
            }
            composable("recurring_expenses") {
                RecurringExpensesScreen(onBack = { navController.popBackStack() })
            }
            composable("unrecognized_sms") {
                UnrecognizedSmsScreen(onBack = { navController.popBackStack() })
            }
            composable("sms_formats") {
                SmsFormatsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                "edit_account/{accountId}",
                arguments = listOf(navArgument("accountId") { type = NavType.StringType })
            ) { entry ->
                AddAccountScreen(
                    onDone = { navController.popBackStack() },
                    accountId = entry.arguments?.getString("accountId")
                )
            }
        }
    }
} 