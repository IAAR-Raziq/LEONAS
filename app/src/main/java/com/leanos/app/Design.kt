package com.leanos.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

val Lime = Color(0xFFC4F86A)
val Mint = Color(0xFF64E5C0)

@Composable
fun LeanosTheme(light: Boolean, content: @Composable () -> Unit) {
    val colors =
        if (light)
            lightColorScheme(
                primary = Color(0xFF416A05),
                secondary = Color(0xFF006C55),
                background = Color(0xFFF5F7F0),
                surface = Color.White,
                surfaceVariant = Color(0xFFE8EDE2),
            )
        else
            darkColorScheme(
                primary = Lime,
                onPrimary = Color(0xFF142000),
                secondary = Mint,
                background = Color(0xFF0C1118),
                surface = Color(0xFF151D27),
                surfaceVariant = Color(0xFF202C38),
                onSurface = Color(0xFFF1F4EE),
                onSurfaceVariant = Color(0xFF9CABA9),
                outlineVariant = Color(0xFF2A3742),
            )
    MaterialTheme(
        colorScheme = colors,
        typography =
            Typography(
                headlineLarge =
                    MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp,
                    ),
                titleLarge =
                    MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            ),
        content = content,
    )
}

fun Double.fmt(digits: Int = 1) = String.format(Locale.US, "%.${digits}f", this)

fun money(value: Double) = "RM ${value.fmt(2)}"

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border =
            androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f),
            ),
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun Heading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        subtitle?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun Label(text: String) {
    Text(
        text.uppercase(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
        letterSpacing = 1.5.sp,
    )
}

@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Label(label)
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun Ring(value: Float, title: String, detail: String, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState(value.coerceIn(0f, 1f), label = "progress")
    val base = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    Box(modifier.size(130.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(5.dp)) {
            drawArc(base, -90f, 360f, false, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
            if (fraction > 0)
                drawArc(
                    accent,
                    -90f,
                    360 * fraction,
                    false,
                    style = Stroke(10.dp.toPx(), cap = StrokeCap.Round),
                )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun Empty(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}

@Composable
fun Trend(points: List<Pair<String, Double>>, unit: String) {
    var selected by remember(points) { mutableIntStateOf((points.size - 1).coerceAtLeast(0)) }
    if (points.isEmpty()) {
        Empty("Your chart begins with your first saved record.")
        return
    }
    val accent = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val min = points.minOf { it.second }
    val max = points.maxOf { it.second }
    val range = (max - min).coerceAtLeast(1.0)
    Text(
        "${points[selected].second.fmt()} $unit · ${points[selected].first}",
        style = MaterialTheme.typography.titleMedium,
        color = accent,
    )
    Canvas(
        Modifier.fillMaxWidth().height(140.dp).pointerInput(points) {
            detectTapGestures {
                selected =
                    ((it.x / size.width) * (points.size - 1)).toInt().coerceIn(0, points.lastIndex)
            }
        }
    ) {
        val pad = 12.dp.toPx()
        val width = size.width - 2 * pad
        val height = size.height - 2 * pad
        repeat(3) { n ->
            val y = pad + n * height / 2
            drawLine(grid, Offset(pad, y), Offset(size.width - pad, y), 1f)
        }
        fun pos(i: Int) =
            Offset(
                pad + (if (points.size == 1) width / 2 else i * width / (points.size - 1)),
                pad + height - (points[i].second - min).toFloat() / range.toFloat() * height,
            )
        if (points.size > 1) {
            val path =
                Path().apply {
                    val first = pos(0)
                    moveTo(first.x, first.y)
                    (1..points.lastIndex).forEach {
                        val p = pos(it)
                        lineTo(p.x, p.y)
                    }
                }
            drawPath(path, accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
        points.indices.forEach {
            drawCircle(accent, if (it == selected) 6.dp.toPx() else 3.dp.toPx(), pos(it))
        }
    }
    Text(
        "${points.first().first}  →  ${points.last().first} · Tap to inspect",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
