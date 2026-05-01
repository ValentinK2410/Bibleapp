package com.example.bible.ui.travel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.R

/**
 * Компактная нижняя панель действий по карте: квадратные кнопки со скруглением,
 * как на референсе — без занятия половины экрана списками и переключателями.
 */
@Composable
fun TravelMapFloatingToolbar(
    modifier: Modifier = Modifier,
    visible: Boolean,
    polygonSelected: Boolean,
    markerModeActive: Boolean,
    routePickActive: Boolean,
    circleModeActive: Boolean,
    shareMapPointPickActive: Boolean,
    enabled: Boolean,
    onPolygonClick: () -> Unit,
    onMarkersClick: () -> Unit,
    onCircleClick: () -> Unit,
    onRouteClick: () -> Unit,
    onShareMapPointClick: () -> Unit,
    onShareClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible && enabled,
        modifier = modifier,
        enter = fadeIn(
            animationSpec = tween(durationMillis = 280, easing = LinearOutSlowInEasing),
        ),
        exit = fadeOut(
            animationSpec = tween(durationMillis = 380, easing = LinearOutSlowInEasing),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TravelMapSquareToolButton(
                selected = polygonSelected,
                onClick = onPolygonClick,
                badgePlus = true,
                contentDescription = stringResource(R.string.travel_fab_polygon_cd),
                iconVector = Icons.Default.Polyline,
                iconTint = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(14.dp))
            TravelMapSquareToolButton(
                selected = markerModeActive,
                onClick = onMarkersClick,
                badgePlus = true,
                contentDescription = stringResource(R.string.travel_fab_marker_cd),
                iconVector = Icons.Default.LocationOn,
                iconTint = Color(0xFFD32F2F),
            )
            Spacer(Modifier.weight(1f))
            TravelMapSquareToolButton(
                selected = circleModeActive,
                onClick = onCircleClick,
                badgePlus = true,
                contentDescription = stringResource(R.string.travel_fab_circle_cd),
                iconVector = Icons.Default.Circle,
                iconTint = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(14.dp))
            TravelMapSquareToolButton(
                selected = routePickActive,
                onClick = onRouteClick,
                badgePlus = false,
                contentDescription = stringResource(R.string.travel_fab_route_cd),
                iconVector = Icons.Default.CenterFocusStrong,
                iconTint = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(14.dp))
            TravelMapSquareToolButton(
                selected = shareMapPointPickActive,
                onClick = onShareMapPointClick,
                badgePlus = false,
                contentDescription = stringResource(R.string.travel_fab_share_map_point_cd),
                iconVector = Icons.Default.AddLocationAlt,
                iconTint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(14.dp))
            TravelMapSquareToolButton(
                selected = false,
                onClick = onShareClick,
                badgePlus = false,
                contentDescription = stringResource(R.string.travel_fab_share_cd),
                iconVector = Icons.Default.Share,
                iconTint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TravelMapSquareToolButton(
    selected: Boolean,
    onClick: () -> Unit,
    badgePlus: Boolean,
    contentDescription: String,
    iconVector: ImageVector,
    iconTint: Color,
) {
    val bg = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(shape)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                shape = shape,
            )
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (badgePlus) {
            Text(
                "+",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 4.dp, top = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
        }
        Icon(
            imageVector = iconVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(26.dp),
            tint = iconTint,
        )
    }
}
