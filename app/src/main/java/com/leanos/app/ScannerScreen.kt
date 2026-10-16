package com.leanos.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ScannerScreen(vm: MainViewModel, back: () -> Unit) {
    val context = LocalContext.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    val analysis by vm.analysis.collectAsStateWithLifecycle()
    var uriText by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraText by rememberSaveable { mutableStateOf<String?>(null) }
    var label by rememberSaveable { mutableStateOf(false) }
    var editor by remember { mutableStateOf(false) }
    var revised by remember(analysis) { mutableStateOf(analysis) }
    var componentIndex by remember { mutableStateOf<Int?>(null) }
    var portionsConfirmed by remember(analysis) { mutableStateOf(false) }
    val crop =
        rememberLauncherForActivityResult(CropImageContract()) { result ->
            if (result.isSuccessful) {
                uriText = result.uriContent?.toString()
                vm.analysis.value = null
            } else if (result.error != null)
                vm.message.value = "Unable to crop this image. Try selecting another photo."
        }
    fun cropUri(uri: Uri) {
        crop.launch(CropImageContractOptions(uri, CropImageOptions()))
    }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
            it?.let { selected ->
                uriText = selected.toString()
                vm.analysis.value = null
            }
        }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok) {
                uriText = cameraText
                vm.analysis.value = null
            }
        }
    val bitmap by
        produceState<android.graphics.Bitmap?>(null, uriText) {
            value = null
            uriText?.let { text ->
                value =
                    withContext(Dispatchers.IO) {
                        try {
                            decodePhoto(context, Uri.parse(text), 1200)
                        } catch (_: Exception) {
                            null
                        }
                    }
            }
        }
    Page {
        TextButton(onClick = back, enabled = !busy) { Text("← Home") }
        Heading("What's on your plate?", "Malaysian favourites. Thoughtful estimates.")
        Text(
            "An internet connection and configured backend are required. Photos are sent only when you tap Analyze. Hidden oil, sauces, sugar and portion sizes make estimates uncertain.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    try {
                        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                        val file = File.createTempFile("food-", ".jpg", dir)
                        val uri =
                            FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.files",
                                file,
                            )
                        cameraText = uri.toString()
                        camera.launch(uri)
                    } catch (_: Exception) {
                        vm.message.value = "No camera app available. Choose a photo instead."
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Camera / retake")
            }
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Choose photo")
            }
        }
        bitmap?.let {
            Image(
                it.asImageBitmap(),
                contentDescription = "Selected food image",
                modifier = Modifier.fillMaxWidth().height(250.dp),
                contentScale = ContentScale.Fit,
            )
        }
        uriText?.let { value ->
            TextButton(onClick = { cropUri(Uri.parse(value)) }, enabled = !busy) {
                Text("Crop & rotate")
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Switch(
                    label,
                    {
                        label = it
                        vm.analysis.value = null
                    },
                    enabled = !busy,
                )
                Spacer(Modifier.width(12.dp))
                Text("Scan a nutrition label")
            }
            Button(
                onClick = { vm.scan(Uri.parse(value), label) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (busy) "Analyzing…" else "Analyze photo")
            }
        }
        if (uriText == null)
            Panel {
                Empty(
                    "Photograph food in good light, include the whole plate, and avoid faces or personal details. For packaged foods, capture a clear nutrition label."
                )
            }
        revised?.let { a ->
            Panel {
                Label("${a.confidence} CONFIDENCE · ${a.source.replace('_',' ')}")
                Text(a.name, style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${a.components.sumOf { it.caloriesMin }.fmt(0)}–${a.components.sumOf { it.caloriesMax }.fmt(0)} kcal estimated",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                a.components.forEachIndexed { index, c ->
                    HorizontalDivider()
                    Text(c.name, style = MaterialTheme.typography.titleMedium)
                    Text("${c.serving} · ${c.gramsMin.fmt(0)}–${c.gramsMax.fmt(0)} g estimated")
                    Text(
                        "${c.ingredients.joinToString()}\n${c.caloriesMin.fmt(0)}–${c.caloriesMax.fmt(0)} kcal · P ${c.protein.fmt()} · C ${c.carbs.fmt()} · F ${c.fat.fmt()} · Fibre ${c.fibre.fmt()} g"
                    )
                    TextButton(onClick = { componentIndex = index }) {
                        Text("Edit ingredients & portion")
                    }
                }
                Text(
                    "Method: ${a.cookingMethod}\nSauces / toppings: ${a.sauces.joinToString().ifBlank { "Not identified" }}"
                )
                Text(a.uncertainty, color = MaterialTheme.colorScheme.secondary)
                Text(
                    "Confirm the serving size and ingredients. Edit nutrition, portions and meal cost before saving. No food database source is claimed."
                )
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(portionsConfirmed, { portionsConfirmed = it })
                    Text(
                        "I have reviewed the portions and estimates",
                        modifier = Modifier.weight(1f),
                    )
                }
                Button(
                    onClick = { editor = true },
                    enabled = portionsConfirmed,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Review portions & save…")
                }
                TextButton(onClick = { vm.analysis.value = null }) { Text("Discard analysis") }
            }
        }
    }
    if (editor)
        revised?.let { a ->
            MealEditor(
                vm,
                a.meal(),
                onSaved = {
                    editor = false
                    vm.analysis.value = null
                    uriText = null
                },
            ) {
                editor = false
            }
        }
    componentIndex?.let { index ->
        revised?.let { a ->
            ComponentEditor(
                vm,
                a,
                index,
                {
                    revised = it
                    portionsConfirmed = false
                },
            ) {
                componentIndex = null
            }
        }
    }
}
