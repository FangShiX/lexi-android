/*
 * Copyright 2020-2025 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0.
 * Adapted from Jetsnack Home.kt; license and upstream source: vendor/ui/jetsnack/.
 */
package com.finisdiscago.leximvp.core.ui

import androidx.annotation.FloatRange
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

internal data class LexiTab(val route: String, val label: String, val icon: Int, val selectedIcon: Int)

@Composable
internal fun LexiBottomBar(tabs: List<LexiTab>, selectedRoute: String, pending: Int, onSelect: (LexiTab) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tabs.forEach { tab ->
                val selected = selectedRoute == tab.route
                val progress by animateFloatAsState(if (selected) 1f else 0f, spring(stiffness = 450f), label = "tabWidth")
                val tint by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, label = "tabTint")
                LexiBottomNavigationItem(
                    selected = selected, onSelected = { onSelect(tab) }, animSpec = spring(stiffness = 450f),
                    modifier = Modifier.weight(1f + progress).height(48.dp).clip(RoundedCornerShape(24.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                        .testTag("navigation_${tab.route}").semantics { contentDescription = tab.label },
                    icon = { BadgedBox(badge = {
                        if (tab.route == "learn" && pending > 0) Badge { Text(if (pending > 99) "99+" else pending.toString()) }
                    }) { Icon(painterResource(if (selected) tab.selectedIcon else tab.icon), null, tint = tint, modifier = Modifier.size(22.dp)) } },
                    text = { Text(tab.label, color = tint, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
                )
            }
        }
    }
}

@Composable
fun LexiBottomNavigationItem(
    icon: @Composable BoxScope.() -> Unit,
    text: @Composable BoxScope.() -> Unit,
    selected: Boolean,
    onSelected: () -> Unit,
    animSpec: AnimationSpec<Float>,
    modifier: Modifier = Modifier,
) {
    // Animate the icon/text positions within the item based on selection
    val animationProgress by animateFloatAsState(
        if (selected) 1f else 0f, animSpec,
        label = "animation progress",
    )
    LexiBottomNavItemLayout(
        icon = icon,
        text = text,
        animationProgress = animationProgress,
        modifier = modifier
            .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.Tab, onClick = onSelected)
            .wrapContentSize(),
    )
}

@Composable
private fun LexiBottomNavItemLayout(
    icon: @Composable BoxScope.() -> Unit,
    text: @Composable BoxScope.() -> Unit,
    @FloatRange(from = 0.0, to = 1.0) animationProgress: Float,
    modifier: Modifier = Modifier,
) {
    Layout(
        modifier = modifier,
        content = {
            Box(
                modifier = Modifier
                    .layoutId("icon")
                    .padding(horizontal = TextIconSpacing),
                content = icon,
            )
            val scale = lerp(0.6f, 1f, animationProgress)
            Box(
                modifier = Modifier
                    .layoutId("text")
                    .padding(horizontal = TextIconSpacing)
                    .graphicsLayer {
                        alpha = animationProgress
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = BottomNavLabelTransformOrigin
                    },
                content = text,
            )
        },
    ) { measurables, constraints ->
        val iconPlaceable = measurables.first { it.layoutId == "icon" }.measure(constraints)
        val textPlaceable = measurables.first { it.layoutId == "text" }.measure(constraints)

        placeTextAndIcon(
            textPlaceable,
            iconPlaceable,
            constraints.maxWidth,
            constraints.maxHeight,
            animationProgress,
        )
    }
}

private fun MeasureScope.placeTextAndIcon(
    textPlaceable: Placeable,
    iconPlaceable: Placeable,
    width: Int,
    height: Int,
    @FloatRange(from = 0.0, to = 1.0) animationProgress: Float,
): MeasureResult {
    val iconY = (height - iconPlaceable.height) / 2
    val textY = (height - textPlaceable.height) / 2

    val textWidth = textPlaceable.width * animationProgress
    val iconX = (width - textWidth - iconPlaceable.width) / 2
    val textX = iconX + iconPlaceable.width

    return layout(width, height) {
        iconPlaceable.placeRelative(iconX.toInt(), iconY)
        if (animationProgress != 0f) {
            textPlaceable.placeRelative(textX.toInt(), textY)
        }
    }
}


private val TextIconSpacing = 6.dp
private val BottomNavLabelTransformOrigin = TransformOrigin(0f, 0.5f)
