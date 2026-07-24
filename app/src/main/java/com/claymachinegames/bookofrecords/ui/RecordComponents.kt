package com.claymachinegames.bookofrecords.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claymachinegames.bookofrecords.domain.MeterZone
import com.claymachinegames.bookofrecords.domain.dbTickFraction
import com.claymachinegames.bookofrecords.domain.meterZone
import kotlin.math.max

/** Balken-Waveform aus Level-Historie; neueste rechts, am Playhead verankert. */
@Composable
fun LiveWaveform(levels: List<Float>, modifier: Modifier = Modifier, onWidthPx: (Int) -> Unit = {}) {
    Canvas(modifier = modifier.fillMaxWidth().height(80.dp).onSizeChanged { onWidthPx(it.width) }) {
        val barW = 2.dp.toPx()
        val step = barW + 1.dp.toPx()
        val midY = size.height / 2f
        val brush = Brush.horizontalGradient(0f to Bor.waveCold, 1f to Bor.accent)
        val playheadX = size.width - 2.dp.toPx()
        if (levels.isEmpty()) {
            // Idle: ruhende Grundlinie statt Playhead
            drawLine(Bor.borderSubtle, Offset(0f, midY), Offset(size.width, midY),
                strokeWidth = 1.dp.toPx())
            return@Canvas
        }
        levels.asReversed().forEachIndexed { i, level ->
            val x = playheadX - step * (i + 1)
            if (x < 0) return@forEachIndexed
            val h = max(1.dp.toPx(), level * (size.height / 2f - 2.dp.toPx()))
            drawLine(brush, Offset(x, midY - h), Offset(x, midY + h),
                strokeWidth = barW, cap = StrokeCap.Round)
        }
        drawLine(Bor.accent, Offset(playheadX, 6.dp.toPx()), Offset(playheadX, size.height),
            strokeWidth = 1.5f.dp.toPx())
        drawCircle(Bor.accent, radius = 3.dp.toPx(), center = Offset(playheadX, 4.dp.toPx()))
    }
}

private fun zoneColor(zone: MeterZone): Color = when (zone) {
    MeterZone.GREEN -> Bor.levelGreen
    MeterZone.YELLOW -> Bor.levelYellow
    MeterZone.AMBER -> Bor.levelAmber
    MeterZone.ORANGE -> Bor.levelOrange
}

/** Segmentiertes Mono-Pegelmeter. dB-Skala separat via [DbScale]. */
@Composable
fun DbMeter(level: Float, modifier: Modifier = Modifier) {
    val segments = 32
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.fillMaxWidth().height(14.dp)) {
        repeat(segments) { i ->
            val frac = (i + 1f) / segments
            val color = if (frac <= level) zoneColor(meterZone(frac)) else Bor.borderSubtle
            Box(Modifier.weight(1f).height(14.dp).background(color))
        }
    }
}

/** dB-Beschriftung, log-korrekt positioniert; −60 entfällt (Kollision), −36 links geclampt. */
@Composable
fun DbScale(modifier: Modifier = Modifier) {
    var widthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Box(modifier = modifier.fillMaxWidth().height(16.dp).onSizeChanged { widthPx = it.width }) {
        listOf(-36, -24, -12).forEach { db ->
            val xDp = with(density) { (widthPx * dbTickFraction(db)).toDp() }
            Text("$db",
                color = Bor.textMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                modifier = Modifier.align(Alignment.CenterStart)
                    .padding(start = (xDp - 8.dp).coerceAtLeast(0.dp)))
        }
        // 0-dB-Tick liegt bei Fraction 1.0 → rechtsbündig, damit "dB" nie abgeschnitten wird
        Text("0 dB", color = Bor.textMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
            modifier = Modifier.align(Alignment.CenterEnd))
    }
}

/** Pulsierender REC-Punkt; Animation lebt nur hier, statisch bei Pause. */
@Composable
fun PulsingRecDot(paused: Boolean, modifier: Modifier = Modifier) {
    val alpha = if (paused) 1f else {
        val transition = rememberInfiniteTransition(label = "recPulse")
        val a by transition.animateFloat(0.4f, 1f,
            infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "recPulseAlpha")
        a
    }
    Box(modifier.size(12.dp).alpha(alpha)
        .background(if (paused) Bor.textMuted else Bor.accent, CircleShape))
}

/** Stop · Pause/Resume · Marker im Referenz-Layout. Icons selbst gezeichnet
 *  (material-icons-extended nicht als Dependency — Quadrat/Balken/Bookmark sind trivial). */
@Composable
fun RecordButtonRow(
    paused: Boolean,
    onStop: () -> Unit,
    onPauseResume: () -> Unit,
    onMarker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onStop,
            modifier = Modifier.size(64.dp).background(Bor.surface, CircleShape)) {
            Box(Modifier.size(18.dp).background(Bor.textPrimary, RoundedCornerShape(3.dp)))
        }
        // Mockup: amberfarbener Pause-Ring; Record-Punkt (fortsetzen) bleibt rot
        IconButton(onClick = onPauseResume,
            modifier = Modifier.size(96.dp)
                .background(Bor.surface, CircleShape)
                .border(3.dp, Bor.amber, CircleShape)) {
            if (paused) Box(Modifier.size(28.dp).background(Bor.accent, CircleShape))
            else Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.width(8.dp).height(28.dp)
                    .background(Bor.amber, RoundedCornerShape(2.dp)))
                Box(Modifier.width(8.dp).height(28.dp)
                    .background(Bor.amber, RoundedCornerShape(2.dp)))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onMarker, enabled = !paused,
                modifier = Modifier.size(64.dp).background(Bor.surface, CircleShape)) {
                BookmarkIcon(color = if (paused) Bor.textMuted else Bor.textPrimary)
            }
            Text("MARK", color = if (paused) Bor.textMuted else Bor.textSecondary,
                fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun BookmarkIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val w = size.width * 0.7f
        val left = (size.width - w) / 2f
        val path = Path().apply {
            moveTo(left, 0f)
            lineTo(left + w, 0f)
            lineTo(left + w, size.height)
            lineTo(size.width / 2f, size.height * 0.72f)
            lineTo(left, size.height)
            close()
        }
        drawPath(path, color)
    }
}

@Preview(backgroundColor = 0xFF0C0C0E, showBackground = true)
@Composable
private fun WaveformPreview() {
    LiveWaveform(levels = List(80) { (it % 20) / 20f })
}

@Preview(backgroundColor = 0xFF0C0C0E, showBackground = true)
@Composable
private fun DbMeterPreview() {
    Column { DbMeter(level = 0.7f); DbScale() }
}

@Preview(backgroundColor = 0xFF0C0C0E, showBackground = true)
@Composable
private fun ButtonRowPreview() {
    RecordButtonRow(paused = false, onStop = {}, onPauseResume = {}, onMarker = {})
}
