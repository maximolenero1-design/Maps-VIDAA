package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SquareFoot
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun MapControls(
    isMeasuring: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onMyLocationClick: () -> Unit,
    onToggleMeasure: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.End
    ) {
        // Measurement Tool Toggle
        SmallFloatingActionButton(
            onClick = onToggleMeasure,
            shape = CircleShape,
            containerColor = if (isMeasuring) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surface,
            contentColor = if (isMeasuring) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurface,
            elevation = FloatingActionButtonDefaults.elevation(4.dp),
            modifier = Modifier
                .size(48.dp)
                .testTag("measure_tool_button")
        ) {
            Icon(
                imageVector = Icons.Default.SquareFoot,
                contentDescription = "Medir distancia"
            )
        }

        // My Location Button
        FloatingActionButton(
            onClick = onMyLocationClick,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            elevation = FloatingActionButtonDefaults.elevation(4.dp),
            modifier = Modifier
                .size(52.dp)
                .testTag("my_location_fab")
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "Mi ubicación",
                modifier = Modifier.size(24.dp)
            )
        }

        // Zoom In Button
        SmallFloatingActionButton(
            onClick = onZoomIn,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            elevation = FloatingActionButtonDefaults.elevation(4.dp),
            modifier = Modifier
                .size(48.dp)
                .testTag("zoom_in_button")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Acercar mapa"
            )
        }

        // Zoom Out Button
        SmallFloatingActionButton(
            onClick = onZoomOut,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            elevation = FloatingActionButtonDefaults.elevation(4.dp),
            modifier = Modifier
                .size(48.dp)
                .testTag("zoom_out_button")
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Alejar mapa"
            )
        }
    }
}
