package com.leanos.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ProfileScreen(data: Records, vm: MainViewModel) {
    val p = data.profile!!
    val theme by vm.theme.collectAsStateWithLifecycle()
    val savedEndpoint by vm.endpoint.collectAsStateWithLifecycle()
    var editor by remember { mutableStateOf(false) }
    var endpoint by rememberSaveable(savedEndpoint) { mutableStateOf(savedEndpoint) }
    // Do not save credentials in SavedState or log them.
    var token by remember { mutableStateOf("") }
    var configured by remember { mutableStateOf(vm.vault.configured()) }
    var erase by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var forget by remember { mutableStateOf(false) }
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) {
            it?.let(vm::export)
        }
    val import =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            restoreUri = it
        }
    Page {
        Heading("Made for ${p.name}.", "Offline first. Yours by design.")
        Panel {
            Label("PERSONAL PROFILE")
            Text(
                "${p.age} years · ${p.height.fmt(0)} cm · ${p.country}",
                style = MaterialTheme.typography.titleLarge,
            )
            Text("${p.startingWeight.fmt()} → ${p.goalWeight.fmt()} kg")
            Text("${p.meals}\n${p.cooking}")
            Text("${money(p.budget)} / 30 days · ${p.stepTarget} steps / day")
            Button(onClick = { editor = true }) { Text("Edit profile, goals & targets") }
        }
        Panel {
            Text("Appearance", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Dark", "Light", "System").forEach { value ->
                    FilterChip(
                        theme == value,
                        { vm.run(null) { vm.preferences.theme(value) } },
                        label = { Text(value) },
                    )
                }
            }
            Text(
                "Units: kilograms, centimetres, millilitres · Currency: MYR",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Panel {
            Text("AI food scanner", style = MaterialTheme.typography.titleLarge)
            Text(
                "Optional internet feature. Only the photo you submit is sent to your backend and Google Gemini. Estimates need your review. No Gemini key is stored in this app.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                endpoint,
                { endpoint = it },
                label = { Text("Your HTTPS backend URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                token,
                { token = it },
                label = {
                    Text(
                        if (configured) "Replace client token (optional)"
                        else "Backend client access token"
                    )
                },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text(
                if (configured) "Client token secured with Android Keystore."
                else "Deploy backend/ first; see README for secure token setup.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = {
                    vm.run("Scanner settings saved") {
                        vm.preferences.endpoint(endpoint)
                        if (token.isNotBlank()) vm.vault.save(token)
                        token = ""
                        configured = vm.vault.configured()
                    }
                }
            ) {
                Text("Save scanner settings")
            }
            if (configured) TextButton(onClick = { forget = true }) { Text("Remove scanner token") }
        }
        Panel {
            Text("Your data", style = MaterialTheme.typography.titleLarge)
            Text(
                "Backups contain personal health records in readable JSON. Keep them private. Import replaces local records after validating the whole backup.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { export.launch("leanos-${today()}.json") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Export backup")
            }
            OutlinedButton(
                onClick = { import.launch(arrayOf("application/json", "text/plain")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Import backup")
            }
            Text(
                "Demo data: none. All charts and totals use your records.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { erase = true }) {
                Text("Delete all personal data", color = MaterialTheme.colorScheme.error)
            }
        }
        Text(
            "LEANOS 1.0\nYour body. Your progress. Your rules.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (editor)
        Dialog(
            onDismissRequest = { editor = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.safeDrawingPadding()) {
                    ProfileEditor(vm, p.copy(currentWeight = data.currentWeight()), false) {
                        editor = false
                    }
                }
            }
        }
    if (erase)
        Confirm(
            "Delete all personal data?",
            "This removes profile, meals, habits, weights, expenses, preferences and scanner credentials. Export a backup first if you want to keep your history.",
            { erase = false },
        ) {
            vm.run("Personal data deleted", done = { erase = false }) { vm.erasePersonalData() }
        }
    if (forget)
        Confirm(
            "Remove scanner access?",
            "Offline features remain available.",
            { forget = false },
        ) {
            vm.run(
                "Scanner token removed",
                done = {
                    forget = false
                    configured = false
                },
            ) {
                vm.vault.save("")
            }
        }
    restoreUri?.let { uri ->
        Confirm(
            "Replace your local records?",
            "The backup will be checked before any data is replaced. Export your current data first if needed.",
            { restoreUri = null },
        ) {
            restoreUri = null
            vm.import(uri)
        }
    }
}
