/*
 * Copyright (c) 2021 Bernat Borrás Paronella. MIT License.
 * Adapted from alorma/Compose-Settings SettingsMenuLink.kt.
 * Full notice and original source: vendor/ui/compose-settings/.
 */
package com.finisdiscago.leximvp.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsMenuLink(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    subtitle: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    colors: ListItemColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    shape: Shape = RoundedCornerShape(16.dp),
    semanticProperties: SemanticsPropertyReceiver.() -> Unit = {},
    onClick: () -> Unit
) {
    // Keep the upstream slot/click/semantics contract; inline its tile scaffold
    // so the baseline does not need a Compose Multiplatform toolchain upgrade.
    Surface(shape = shape) {
        ListItem(
            modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
                .semantics(properties = semanticProperties).then(modifier),
            headlineContent = title, supportingContent = subtitle,
            leadingContent = icon, trailingContent = action, colors = colors
        )
    }
}
