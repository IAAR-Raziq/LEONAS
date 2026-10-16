package com.leanos.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MealPhoto(selection: String, modifier: Modifier = Modifier) {
    if (selection.isEmpty()) return
    val context = LocalContext.current
    val view = LocalView.current
    var visible by remember(selection) { mutableStateOf(false) }
    var failed by remember(selection) { mutableStateOf(false) }
    val bitmap by
        produceState<android.graphics.Bitmap?>(null, selection, visible) {
            value = null
            if (visible) {
                value =
                    withContext(Dispatchers.IO) {
                        runCatching {
                                val uri =
                                    if (isPhotoName(selection))
                                        Uri.fromFile(
                                            File(context.filesDir, "meal_photos/$selection")
                                        )
                                    else Uri.parse(selection)
                                decodePhoto(context, uri, 640)
                            }
                            .getOrNull()
                    }
                failed = value == null
            }
        }
    Box(
        modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(20.dp))
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                visible =
                    bounds.bottom > 0 &&
                        bounds.top < view.height &&
                        bounds.right > 0 &&
                        bounds.left < view.width
            },
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                it.asImageBitmap(),
                "Meal photo",
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
            ?: Text(
                if (failed) "Photo unavailable. Replace it in Edit." else "Meal photo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }
}

@Composable
fun MealPhotoPicker(selection: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val gallery =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let {
                onChange(it.toString())
                error = null
            }
        }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok)
                pending?.let {
                    onChange(it)
                    error = null
                }
        }
    Panel {
        SectionTitle("Meal photo", "Optional · saved privately on this device")
        MealPhoto(selection)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    gallery.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Gallery")
            }
            OutlinedButton(
                onClick = {
                    try {
                        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                        val file = File.createTempFile("meal-", ".jpg", dir)
                        val uri =
                            FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.files",
                                file,
                            )
                        pending = uri.toString()
                        camera.launch(uri)
                    } catch (_: Exception) {
                        error = "Camera unavailable. Choose a gallery photo instead."
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Camera")
            }
        }
        if (selection.isNotEmpty()) TextButton(onClick = { onChange("") }) { Text("Remove photo") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
