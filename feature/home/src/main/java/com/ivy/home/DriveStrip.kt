package com.ivy.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.driveexport.DriveExportState
import com.ivy.ui.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun DriveStrip(
    state: DriveExportState,
    onSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val failed = state.lastError != null && !state.running
    val color = when {
        state.running -> UI.colors.primary
        failed -> UI.colors.red
        else -> UI.colors.gray
    }
    val label = when {
        state.running -> R.string.drive_strip_syncing
        failed -> R.string.drive_strip_failed
        state.lastSuccess == null -> R.string.drive_export_pending
        else -> R.string.drive_strip_saved
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(UI.shapes.r4)
            .background(UI.colors.medium)
            .clickable(enabled = !state.running, onClick = onSync)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.running) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = color,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                modifier = Modifier.size(18.dp),
                painter = painterResource(R.drawable.ic_vue_weather_cloud),
                contentDescription = null,
                tint = color,
            )
        }

        Spacer(Modifier.width(10.dp))

        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(label),
            style = UI.typo.b2.style(color = color, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
        )

        state.lastSuccess?.let { lastSuccess ->
            Text(
                text = lastSuccess.driveTime(),
                style = UI.typo.nB2.style(color = UI.colors.gray, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
internal fun DrivePullIndicator(
    progress: Float,
    refreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(UI.colors.primary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        if (refreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = UI.colors.primary,
                strokeWidth = 2.5.dp,
                strokeCap = StrokeCap.Round,
            )
        } else {
            CircularProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.size(22.dp),
                color = UI.colors.primary,
                strokeWidth = 2.5.dp,
                strokeCap = StrokeCap.Round,
            )
        }
    }
}

@Composable
internal fun DrivePullReveal(
    state: DriveExportState,
    progress: Float,
    refreshing: Boolean,
    heightPx: Float,
    modifier: Modifier = Modifier,
) {
    val label = when {
        refreshing || state.running -> R.string.drive_strip_syncing
        progress >= 1f -> R.string.drive_strip_release
        else -> R.string.drive_strip_pull
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(with(LocalDensity.current) { heightPx.toDp() })
            .clipToBounds(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DrivePullIndicator(progress = progress, refreshing = refreshing || state.running)

        Spacer(Modifier.height(8.dp))

        Text(
            text = listOfNotNull(stringResource(label), state.lastSuccess?.driveTime())
                .joinToString(" · "),
            style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
        )
    }
}

private fun Instant.driveTime(): String {
    val time = atZone(ZoneId.systemDefault())
    val pattern = if (time.toLocalDate() == LocalDate.now()) "HH.mm" else "d MMM"
    return DateTimeFormatter.ofPattern(pattern).format(time)
}
