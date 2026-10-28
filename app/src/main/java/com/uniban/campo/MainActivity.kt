package com.uniban.campo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.uniban.campo.data.repo.ServiceLocator
import com.uniban.campo.ui.estimate.EstimateScreen
import com.uniban.campo.ui.estimate.EstimateViewModel
import com.uniban.campo.ui.home.HomeScreen
import com.uniban.campo.ui.home.HomeViewModel
import com.uniban.campo.ui.history.HistoryScreen
import com.uniban.campo.ui.history.HistoryViewModel
import com.uniban.campo.ui.login.LoginScreen
import com.uniban.campo.ui.parcels.ParcelsScreen
import com.uniban.campo.ui.parcels.ParcelsViewModel
import com.uniban.campo.ui.theme.GreenPrimary
import com.uniban.campo.ui.theme.SuccessBg
import com.uniban.campo.ui.theme.SurfaceCard
import com.uniban.campo.ui.theme.TextSecondary
import com.uniban.campo.ui.theme.UnibanCampoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ServiceLocator.init(this)
        setContent {
            UnibanCampoTheme {
                AppRoot()
            }
        }
    }
}

private data class NavTab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    NavTab("home", "Inicio", Icons.Outlined.Home),
    NavTab("estimate", "Estimar", Icons.Outlined.Spa),
    NavTab("history", "Historial", Icons.AutoMirrored.Outlined.List),
    NavTab("parcels", "Parcelas", Icons.Outlined.Map)
)

@Composable
fun AppRoot() {
    val session = ServiceLocator.sessionManager
    var loggedIn by rememberSaveable { mutableStateOf(session.isLoggedIn) }

    if (!loggedIn) {
        LoginScreen(onLoggedIn = { email ->
            session.login(email)
            loggedIn = true
        })
    } else {
        MainScaffold(onLogout = { loggedIn = false })
    }
}

@Composable
private fun MainScaffold(onLogout: () -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "home"

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceCard,
                tonalElevation = 0.dp
            ) {
                tabs.forEach { tab ->
                    val selected = currentRoute == tab.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                navController.navigate(tab.route) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(tab.icon, contentDescription = tab.label)
                        },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = GreenPrimary,
                            selectedTextColor = GreenPrimary,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = SuccessBg
                        )
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") {
                val vm: HomeViewModel = viewModel()
                HomeScreen(
                    viewModel = vm,
                    onNavigate = { route -> navController.navigate(route) { launchSingleTop = true } },
                    onLogout = onLogout
                )
            }
            composable("estimate") {
                val vm: EstimateViewModel = viewModel()
                EstimateScreen(viewModel = vm)
            }
            composable("history") {
                val vm: HistoryViewModel = viewModel()
                HistoryScreen(viewModel = vm)
            }
            composable("parcels") {
                val vm: ParcelsViewModel = viewModel()
                ParcelsScreen(viewModel = vm)
            }
        }
    }
}
