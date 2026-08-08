package com.qwertimer.forge.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.qwertimer.forge.ui.food.DiaryScreen
import com.qwertimer.forge.ui.home.HomeScreen
import com.qwertimer.forge.ui.scan.ScanScreen
import com.qwertimer.forge.ui.settings.SettingsScreen
import com.qwertimer.forge.ui.stats.StatsScreen
import com.qwertimer.forge.ui.workout.WorkoutScreen

enum class TopLevel(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Today", Icons.Default.Home),
    FOOD("food", "Food", Icons.Default.Restaurant),
    TRAIN("train", "Train", Icons.Default.FitnessCenter),
    STATS("stats", "Stats", Icons.Default.BarChart),
    SETTINGS("settings", "Settings", Icons.Default.Settings),
}

private const val ROUTE_SCAN = "scan"

@Composable
fun ForgeNavHost(startOnWorkout: Boolean = false) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // The scanner is a full-screen task; a bottom bar underneath it is just clutter.
    val showBottomBar = currentDestination?.route != ROUTE_SCAN

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevel.entries.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (startOnWorkout) TopLevel.TRAIN.route else TopLevel.HOME.route,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(TopLevel.HOME.route) {
                HomeScreen(
                    onScanClick = { navController.navigate(ROUTE_SCAN) },
                    onTrainClick = { navController.navigate(TopLevel.TRAIN.route) },
                    modifier = Modifier.padding(padding),
                )
            }
            composable(TopLevel.FOOD.route) {
                DiaryScreen(
                    onScanClick = { navController.navigate(ROUTE_SCAN) },
                    modifier = Modifier.padding(padding),
                )
            }
            composable(TopLevel.TRAIN.route) {
                WorkoutScreen(modifier = Modifier.padding(padding))
            }
            composable(TopLevel.STATS.route) {
                StatsScreen(modifier = Modifier.padding(padding))
            }
            composable(TopLevel.SETTINGS.route) {
                SettingsScreen(modifier = Modifier.padding(padding))
            }
            composable(ROUTE_SCAN) {
                ScanScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
