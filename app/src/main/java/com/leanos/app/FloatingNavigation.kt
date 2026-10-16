package com.leanos.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Translucent, layered glass treatment without blurring labels or requiring newer APIs. */
@Composable
fun FloatingNavigation(tabs: List<Destination>, current: String, onSelect: (Destination) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val light = colors.background.red > .5f
    val glass = if (light) Color.White.copy(alpha = .87f) else Color(0xFF17212C).copy(alpha = .91f)
    val rim = if (light) Color.White else Color.White.copy(alpha = .22f)
    val shape = RoundedCornerShape(30.dp)
    Box(
        Modifier.fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(colors.background.copy(alpha = 0f), colors.background.copy(alpha = .9f))
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.widthIn(max = 460.dp)
                .fillMaxWidth()
                .shadow(
                    20.dp,
                    shape,
                    ambientColor = Color.Black.copy(alpha = .25f),
                    spotColor = Color.Black.copy(alpha = .35f),
                )
                .clip(shape)
                .background(glass)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = if (light) .5f else .07f),
                            Color.Transparent,
                            colors.secondary.copy(alpha = .05f),
                        )
                    )
                )
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(rim, colors.outlineVariant.copy(alpha = .35f))
                        ),
                    ),
                    shape,
                )
                .selectableGroup()
                .padding(7.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val selected = current == tab.route || (current == "budget" && tab.route == "home")
                val emphasis by
                    animateFloatAsState(if (selected) 1f else 0f, spring(), label = "tab emphasis")
                val tint by
                    animateColorAsState(
                        if (selected) colors.primary else colors.onSurfaceVariant,
                        label = "tab tint",
                    )
                Column(
                    Modifier.weight(1f)
                        .heightIn(min = 58.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(colors.primary.copy(alpha = .12f * emphasis))
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onSelect(tab) },
                        )
                        .padding(horizontal = 1.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = null,
                        tint = tint,
                        modifier =
                            Modifier.size(23.dp).graphicsLayer {
                                scaleX = 1f + .06f * emphasis
                                scaleY = 1f + .06f * emphasis
                                translationY = -emphasis
                            },
                    )
                    Text(
                        tab.name,
                        color = tint,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
