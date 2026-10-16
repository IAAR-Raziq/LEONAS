package com.leanos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by vm.theme.collectAsStateWithLifecycle()
            LeanosTheme(theme == "Light" || (theme == "System" && !isSystemInDarkTheme())) {
                LeanosApp(vm)
            }
        }
    }
}

data class Destination(val route: String, val name: String, val icon: ImageVector)

@Composable
fun LeanosApp(vm: MainViewModel) {
    val records by vm.records.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val loadError by vm.loadError.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.message.value = null
        }
    }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route ?: "home"
    val tabs =
        listOf(
            Destination("home", "Home", Icons.Outlined.Home),
            Destination("track", "Track", Icons.Outlined.CheckCircle),
            Destination("meals", "Meals", Icons.Outlined.Restaurant),
            Destination("progress", "Progress", Icons.Outlined.Insights),
            Destination("profile", "Profile", Icons.Outlined.Person),
        )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (records?.profile != null)
                NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.name) },
                            label = { Text(tab.name) },
                        )
                    }
                }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val data = records
            if (loadError != null) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(loadError!!)
                    Button(onClick = vm::reload) { Text("Retry opening records") }
                }
            } else if (data == null) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (data.profile == null) ProfileEditor(vm, Profile(), true) {}
            else
                NavHost(nav, startDestination = "home") {
                    composable("home") { HomeScreen(data, vm, { nav.navigate(it) }) }
                    composable("track") { TrackScreen(data, vm) }
                    composable("meals") { MealsScreen(data, vm) }
                    composable("progress") { ProgressScreen(data, vm) }
                    composable("profile") { ProfileScreen(data, vm) }
                    composable("budget") { BudgetScreen(data, vm) { nav.popBackStack() } }
                    composable("scanner") { ScannerScreen(vm) { nav.popBackStack() } }
                }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp))
        }
    }
}
