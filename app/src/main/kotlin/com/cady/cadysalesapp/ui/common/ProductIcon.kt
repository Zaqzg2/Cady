package com.cady.cadysalesapp.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp

/** The product tile used on the invoice lines, the product picker, the products list and the offers list:
    the product's own photo when it has one, otherwise the default box icon. (Named "Circle" for history;
    since the Phase 9 refresh it is a softly rounded square, which suits photos of boxes and bottles better.) */
@Composable
fun ProductIconCircle(imagePath: String?, name: String, size: Dp, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(scheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (imagePath.isNullOrBlank()) {
            Icon(
                imageVector = Icons.Filled.Inventory2,
                contentDescription = null,
                tint = scheme.onPrimaryContainer,
                modifier = Modifier.size(size * 0.5f),
            )
        } else {
            LocalFileImage(path = imagePath, contentDescription = name, modifier = Modifier.fillMaxSize())
        }
    }
}
