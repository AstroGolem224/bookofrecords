package com.claymachinegames.bookofrecords.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claymachinegames.bookofrecords.R
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
        val brush = Brush.horizontalGradient(0f to Bor.waveCold, 1f to Bor.violet)
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

// ---------- Idle-Screen: feste 412-dp-Komposition ----------

const val ReferencePxPerDp = 2.067961f
val BebasNeue = FontFamily(Font(R.font.bebas_neue))
val BarlowSemiCondensed = FontFamily(Font(R.font.barlow_semi_condensed_medium))

private val IdleBarHalfHeightsPx = floatArrayOf(
    12f, 37f, 31f, 41f, 54f, 38f, 24f, 29f, 35f, 34f, 22f, 15f, 33f, 24f,
    53f, 69f, 68f, 52f, 26f, 25f, 77f, 82f, 75f, 48f, 38f, 53f, 69f, 52f,
    37f, 27f, 44f, 32f, 26f, 18f, 14f, 24f, 34f, 21f, 25f, 32f, 56f, 47f,
    32f, 52f, 39f, 28f, 48f, 77f, 61f, 41f, 50f, 76f, 59f, 44f, 24f, 17f,
    33f, 24f, 15f, 38f, 54f, 43f, 22f, 34f, 24f, 21f, 16f, 11f, 22f, 34f,
    36f, 30f, 69f, 51f, 36f, 61f, 78f, 49f, 28f, 54f, 55f, 34f, 36f, 27f,
    18f, 27f, 30f, 44f, 62f, 78f, 49f, 39f, 40f, 61f, 70f, 27f, 24f, 26f,
    25f, 38f, 25f, 24f, 18f, 16f,
)

@Composable
fun IdleStartScreen(
    hasAudioPermission: Boolean,
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
    onRecord: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Schmale Viewports skalieren uniform; oberhalb 412 dp bleibt die Komposition zentriert.
        val scale = minOf(maxWidth.value / 412f, 1f)
        val compositionWidth = 412.dp * scale
        val dockHeight = 121.4f * scale
        val dockY = minOf(
            719.5f * scale,
            (maxHeight.value - dockHeight).coerceAtLeast(451.7f * scale),
        )

        // Ambient-Hintergrund kommt global aus MainActivity (liegt unter allen Screens)
        Box(
            Modifier.width(compositionWidth).fillMaxHeight().align(Alignment.TopCenter),
        ) {
            WaveLogo(
                scale = scale,
                modifier = Modifier.absoluteOffset(27.6.dp * scale, 97.7.dp * scale),
            )
            Text(
                "BOOK OF\nRECORDS",
                color = Bor.idleTextPrimary,
                fontFamily = BebasNeue,
                fontSize = 38.7.sp * scale,
                lineHeight = 50.3.sp * scale,
                letterSpacing = 1.3545.sp * scale,
                style = TextStyle.Default,
                modifier = Modifier
                    .absoluteOffset(130.1.dp * scale, 96.2.dp * scale)
                    .size(207.9.dp * scale, 103.dp * scale),
            )
            Text(
                "AUDIO RECORDER",
                color = Bor.idleTextSecondary,
                fontFamily = BarlowSemiCondensed,
                fontSize = 12.1.sp * scale,
                lineHeight = 15.sp * scale,
                letterSpacing = 5.082.sp * scale,
                maxLines = 1,
                modifier = Modifier
                    .absoluteOffset(131.dp * scale, 214.2.dp * scale)
                    .size(164.4.dp * scale, 15.dp * scale),
            )
            SettingsControl(
                scale = scale,
                onClick = onOpenSettings,
                modifier = Modifier.absoluteOffset(340.4.dp * scale, 85.6.dp * scale),
            )
            GlassSurface(
                scale = scale,
                radiusDp = 18.9f,
                modifier = Modifier
                    .absoluteOffset(22.2.dp * scale, 265.dp * scale)
                    .size(368.5.dp * scale, 177.dp * scale),
            )
            HeroWaveform(
                scale = scale,
                modifier = Modifier
                    .absoluteOffset(30.dp * scale, 313.4.dp * scale)
                    .size(353.dp * scale, 78.8.dp * scale),
            )
            Playhead(
                scale = scale,
                modifier = Modifier
                    .absoluteOffset(202.6.dp * scale, 290.1.dp * scale)
                    .size(5.8.dp * scale, 126.7.dp * scale),
            )
            ActionDock(
                scale = scale,
                hasAudioPermission = hasAudioPermission,
                onOpenLibrary = onOpenLibrary,
                onRecord = onRecord,
                onHide = onHide,
                modifier = Modifier
                    .absoluteOffset(33.8.dp * scale, dockY.dp)
                    .size(343.8.dp * scale, dockHeight.dp),
            )
        }
    }
}

/** Ambient-Hintergrund aus der Startscreen-Spec (Basis-Verlauf + drei Radial-Glows).
 *  Selbstständig skalierend — app-weit unter allen Screens nutzbar (außer HideScreen). */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val scale = minOf(size.width / (412.dp.toPx()), 1f)
        val left = (size.width - 412.dp.toPx() * scale) / 2f
        val px = { baselinePx: Float -> baselinePx / ReferencePxPerDp * scale.dp.toPx() }
        drawRect(Brush.verticalGradient(listOf(Bor.idleBgBase, Bor.idleBgEnd)))
        drawRect(Brush.radialGradient(
            colors = listOf(Color(0x7A003B5A), Color.Transparent),
            center = Offset(left + px(-90f), px(1260f)),
            radius = px(680f),
        ))
        drawRect(Brush.radialGradient(
            colors = listOf(Color(0x663B0A70), Color.Transparent),
            center = Offset(left + px(920f), px(1220f)),
            radius = px(720f),
        ))
        drawRect(Brush.radialGradient(
            colors = listOf(Color(0x3D2A0749), Color.Transparent),
            center = Offset(left + px(835f), px(375f)),
            radius = px(320f),
        ))
    }
}

/** Markenpfad aus dem 184×157-px-Original. */
@Composable
fun WaveLogo(scale: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.size(89.dp * scale, 75.9.dp * scale)) {
        val sx = size.width / 184f
        val sy = size.height / 157f
        val path = Path().apply {
            moveTo(0f, 91f * sy)
            lineTo(38f * sx, 91f * sy)
            cubicTo(46f * sx, 91f * sy, 44f * sx, 50f * sy, 52f * sx, 50f * sy)
            cubicTo(61f * sx, 50f * sy, 59f * sx, 121f * sy, 68f * sx, 121f * sy)
            cubicTo(78f * sx, 121f * sy, 76f * sx, 0f, 86f * sx, 0f)
            cubicTo(96f * sx, 0f, 95f * sx, 154f * sy, 105f * sx, 154f * sy)
            cubicTo(115f * sx, 154f * sy, 113f * sx, 52f * sy, 123f * sx, 52f * sy)
            cubicTo(132f * sx, 52f * sy, 130f * sx, 91f * sy, 140f * sx, 91f * sy)
            lineTo(184f * sx, 91f * sy)
        }
        drawPath(
            path = path,
            brush = Brush.horizontalGradient(listOf(Bor.idleCyan, Bor.idleViolet)),
            style = Stroke(9f * sx, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
fun GlassSurface(
    scale: Float,
    radiusDp: Float,
    modifier: Modifier = Modifier,
    borderStart: Color = Bor.idleCyan,
    borderEnd: Color = Bor.idleViolet,
    content: @Composable () -> Unit = {},
) {
    val radius = radiusDp.dp * scale
    Box(
        modifier.shadow(
            elevation = (34f / ReferencePxPerDp).dp * scale,
            shape = RoundedCornerShape(radius),
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.52f),
            spotColor = Color.Black.copy(alpha = 0.52f),
        ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val corner = CornerRadius(radius.toPx())
            val glowStroke = Stroke(5.dp.toPx() * scale)
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(
                    borderStart.copy(alpha = 0.18f),
                    Color.Transparent,
                    borderEnd.copy(alpha = 0.22f),
                )),
                cornerRadius = corner,
                style = glowStroke,
            )
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Bor.idleGlass.copy(alpha = 0.80f),
                        Bor.idleGlassEnd.copy(alpha = 0.72f),
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
                cornerRadius = corner,
            )
            drawRoundRect(
                brush = Brush.linearGradient(listOf(borderStart, borderEnd)),
                cornerRadius = corner,
                style = Stroke(1.dp.toPx() * scale),
            )
        }
        content()
    }
}

@Composable
private fun SettingsControl(scale: Float, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(maxOf(48f, 47.4f * scale).dp)
            .semantics { contentDescription = "Einstellungen" }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.TopStart,
    ) {
        Box(
            Modifier.size(47.4.dp * scale),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Bor.idleControl.copy(alpha = 0.84f))
                drawCircle(
                    brush = Brush.linearGradient(listOf(Bor.idleCyan, Bor.idleViolet)),
                    style = Stroke(1.dp.toPx() * scale),
                )
            }
            Icon(
                Icons.Filled.Settings,
                contentDescription = null,
                tint = Bor.idleIcon.copy(alpha = 0.9f),
                modifier = Modifier.size(21.8.dp * scale),
            )
        }
    }
}

@Composable
private fun HeroWaveform(scale: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val centerY = (352.5f - 313.4f).dp.toPx() * scale
        val step = 3.385.dp.toPx() * scale
        val firstCenter = 0.95.dp.toPx() * scale
        val brush = Brush.horizontalGradient(
            0f to Bor.idleCyan,
            0.26f to Color(0xFF22D3EE),
            0.48f to Color(0xFFC9D4FF),
            0.70f to Color(0xFFA855F7),
            1f to Bor.idleViolet,
            startX = 0f,
            endX = size.width,
        )
        IdleBarHalfHeightsPx.forEachIndexed { index, halfHeightPx ->
            val x = firstCenter + index * step
            val halfHeight = halfHeightPx / ReferencePxPerDp * scale.dp.toPx()
            drawLine(
                brush,
                Offset(x, centerY - halfHeight),
                Offset(x, centerY + halfHeight),
                strokeWidth = 1.7.dp.toPx() * scale,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun Playhead(scale: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val x = size.width / 2f
        val top = (606f / ReferencePxPerDp - 290.1f).dp.toPx() * scale
        val bottom = (857f / ReferencePxPerDp - 290.1f).dp.toPx() * scale
        drawLine(
            Bor.idleAmber.copy(alpha = 0.45f),
            Offset(x, top),
            Offset(x, bottom),
            strokeWidth = 5.8.dp.toPx() * scale,
            cap = StrokeCap.Round,
        )
        drawLine(
            Bor.idleAmber,
            Offset(x, top),
            Offset(x, bottom),
            strokeWidth = 1.45.dp.toPx() * scale,
            cap = StrokeCap.Round,
        )
        val endRadius = 2.42.dp.toPx() * scale
        drawCircle(Bor.idleAmberHi, endRadius, Offset(x, top))
        drawCircle(Bor.idleAmberHi, endRadius, Offset(x, bottom))
    }
}

@Composable
private fun ActionDock(
    scale: Float,
    hasAudioPermission: Boolean,
    onOpenLibrary: () -> Unit,
    onRecord: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(scale = scale, radiusDp = 22.2f, modifier = modifier) {
        DockActionControl(
            label = "BIBLIOTHEK",
            description = "Bibliothek öffnen",
            scale = scale,
            onClick = onOpenLibrary,
            borderStart = Bor.idleCyan.copy(alpha = 0.58f),
            borderEnd = Bor.idleOutline.copy(alpha = 0.55f),
            icon = { LibraryIcon(scale = scale) },
            modifier = Modifier
                .absoluteOffset(19.4.dp * scale, 23.3.dp * scale)
                .size(75.dp * scale, 76.9.dp * scale),
        )
        RecordControl(
            enabled = hasAudioPermission,
            scale = scale,
            onClick = onRecord,
            modifier = Modifier
                .absoluteOffset(117.1.dp * scale, 4.9.dp * scale)
                .size(109.8.dp * scale),
        )
        DockActionControl(
            label = "HIDE",
            description = "Bildschirm verstecken",
            scale = scale,
            onClick = onHide,
            borderStart = Bor.idleOutline.copy(alpha = 0.55f),
            borderEnd = Bor.idleViolet.copy(alpha = 0.58f),
            icon = { HideIcon(scale = scale) },
            modifier = Modifier
                .absoluteOffset(248.1.dp * scale, 23.3.dp * scale)
                .size(77.9.dp * scale, 76.9.dp * scale),
        )
    }
}

@Composable
private fun DockActionControl(
    label: String,
    description: String,
    scale: Float,
    onClick: () -> Unit,
    borderStart: Color,
    borderEnd: Color,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    GlassSurface(
        scale = scale,
        radiusDp = 16.9f,
        modifier = modifier
            .semantics { contentDescription = description }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        borderStart = borderStart,
        borderEnd = borderEnd,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(top = 13.dp * scale),
        ) {
            icon()
            Text(
                label,
                color = Bor.idleDockLabel,
                fontFamily = BarlowSemiCondensed,
                fontSize = 10.2.sp * scale,
                lineHeight = 13.1.sp * scale,
                letterSpacing = 0.816.sp * scale,
                maxLines = 1,
                modifier = Modifier.padding(top = 7.dp * scale),
            )
        }
    }
}

@Composable
private fun RecordControl(
    enabled: Boolean,
    scale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = if (pressed) tween(90) else spring(
            dampingRatio = 1f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "idleRecordPress",
    )
    val amber = if (pressed) Color(0xFFD18300) else Bor.idleAmber
    Box(
        modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .semantics { contentDescription = "Aufnahme starten" }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val contentAlpha = if (enabled) 1f else 0.38f
            val center = center
            if (enabled) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Bor.idleAmber.copy(alpha = 0.20f), Color.Transparent),
                        center = center,
                        radius = 33.8.dp.toPx() * scale,
                    ),
                    radius = 33.8.dp.toPx() * scale,
                    center = center,
                )
            }
            drawCircle(
                Bor.idleControl.copy(alpha = 0.82f),
                radius = 50.3.dp.toPx() * scale,
                center = center,
            )
            drawCircle(
                Bor.idleOutline.copy(alpha = 0.65f * contentAlpha),
                radius = 50.3.dp.toPx() * scale,
                center = center,
                style = Stroke(1.dp.toPx() * scale),
            )
            drawCircle(
                amber.copy(alpha = contentAlpha),
                radius = 45.95.dp.toPx() * scale,
                center = center,
                style = Stroke(2.dp.toPx() * scale),
            )
            val innerRadius = 17.9.dp.toPx() * scale
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Bor.idleAmberHi.copy(alpha = contentAlpha),
                        amber.copy(alpha = contentAlpha),
                    ),
                    center = Offset(
                        center.x - innerRadius * 0.35f,
                        center.y - innerRadius * 0.35f,
                    ),
                    radius = innerRadius * 1.4f,
                ),
                radius = innerRadius,
                center = center,
            )
        }
    }
}

/** Bibliotheks-Icon: drei Buchrücken als Outline. */
@Composable
fun LibraryIcon(scale: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.size(23.dp * scale, 21.dp * scale)) {
        val stroke = Stroke(1.45.dp.toPx() * scale)
        val width = 4.7.dp.toPx() * scale
        val gap = 2.8.dp.toPx() * scale
        var x = (size.width - 3f * width - 2f * gap) / 2f
        repeat(3) { index ->
            val inset = if (index == 2) 2.6.dp.toPx() * scale else 0f
            drawRoundRect(
                Bor.idleIcon.copy(alpha = 0.9f),
                topLeft = Offset(x, inset),
                size = Size(width, size.height - inset),
                cornerRadius = CornerRadius(1.3.dp.toPx() * scale),
                style = stroke,
            )
            x += width + gap
        }
    }
}

/** Durchgestrichenes Auge. */
@Composable
fun HideIcon(scale: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.size(26.dp * scale, 21.dp * scale)) {
        val color = Bor.idleIcon.copy(alpha = 0.9f)
        val strokeWidth = 1.55.dp.toPx() * scale
        val eye = Path().apply {
            moveTo(1.dp.toPx() * scale, size.height / 2f)
            cubicTo(
                size.width * 0.25f, size.height * 0.12f,
                size.width * 0.75f, size.height * 0.12f,
                size.width - 1.dp.toPx() * scale, size.height / 2f,
            )
            cubicTo(
                size.width * 0.75f, size.height * 0.88f,
                size.width * 0.25f, size.height * 0.88f,
                1.dp.toPx() * scale, size.height / 2f,
            )
        }
        drawPath(eye, color, style = Stroke(strokeWidth, cap = StrokeCap.Round))
        drawCircle(
            color,
            radius = 2.3.dp.toPx() * scale,
            center = center,
            style = Stroke(strokeWidth),
        )
        drawLine(
            color,
            Offset(size.width * 0.12f, size.height * 0.92f),
            Offset(size.width * 0.88f, size.height * 0.08f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}
