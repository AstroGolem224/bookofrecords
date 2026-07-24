package com.claymachinegames.bookofrecords.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claymachinegames.bookofrecords.domain.formatMs
import com.claymachinegames.bookofrecords.record.RecState
import com.claymachinegames.bookofrecords.record.RecorderService
import com.claymachinegames.bookofrecords.record.RecorderState
import kotlinx.coroutines.delay

@Composable
fun RecordScreen(
    hasAudioPermission: Boolean,
    waveHistory: List<Float>,
    onWaveWidthPx: (Int) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
    onHide: () -> Unit,
) {
    val context = LocalContext.current
    val state by RecorderState.state.collectAsState()
    fun send(action: String) {
        val intent = RecorderService.intent(context, action)
        // Nur START braucht startForegroundService; alle anderen Actions per startService,
        // sonst entsteht eine startForeground-Verpflichtung, die ein Idle-Service nicht einlöst
        if (action == RecorderService.ACTION_START) context.startForegroundService(intent)
        else context.startService(intent)
    }

    when (val s = state) {
        is RecState.Idle -> IdleStartScreen(
            hasAudioPermission = hasAudioPermission,
            onOpenSettings = onOpenSettings,
            onOpenLibrary = onOpenLibrary,
            onRecord = { send(RecorderService.ACTION_START) },
            onHide = onHide,
        )
        is RecState.Recording -> {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val contentWidth = minOf(maxWidth - 44.dp, 368.dp).coerceAtLeast(0.dp)
                var titleText by remember(s.baseName) { mutableStateOf(s.title) }
                LaunchedEffect(titleText) {
                    if (titleText != s.title) {
                        delay(400)   // debounce → Service
                        context.startService(
                            RecorderService.intent(context, RecorderService.ACTION_SET_TITLE)
                                .putExtra(RecorderService.EXTRA_TITLE, titleText))
                    }
                }
                // Dispose (Detail/Settings-Route zerstört den Pager) darf den Titel im
                // 400ms-Debounce-Fenster nicht verlieren → letzten Stand flushen
                val latestTitle by rememberUpdatedState(titleText)
                DisposableEffect(s.baseName) {
                    onDispose {
                        // nur flushen solange DIESE Aufnahme noch läuft — sonst würde der
                        // startService einen bereits gestoppten Service wiederbeleben
                        val cur = RecorderState.state.value
                        if (cur is RecState.Recording && cur.baseName == s.baseName
                            && cur.title != latestTitle) {
                            context.startService(
                                RecorderService.intent(context, RecorderService.ACTION_SET_TITLE)
                                    .putExtra(RecorderService.EXTRA_TITLE, latestTitle))
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .width(contentWidth)
                        .fillMaxSize()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RecHeader(paused = s.paused, onHide = onHide)
                    Spacer(Modifier.height(8.dp))
                    TitleField(text = titleText, onChange = { titleText = it })
                    Spacer(Modifier.height(8.dp))

                    GlassSurface(
                        scale = 1f,
                        radiusDp = 19f,
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        borderStart = Bor.idleCyan.copy(alpha = .72f),
                        borderEnd = Bor.idleViolet.copy(alpha = .72f),
                    ) {
                        // Historie lebt in App() und bleibt über Pager-Ausflüge erhalten.
                        Box(
                            Modifier.fillMaxSize().padding(12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            LiveWaveform(
                                levels = waveHistory,
                                onWidthPx = onWaveWidthPx,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    Text(
                        formatMs(s.elapsedMs),
                        color = Bor.idleTextPrimary,
                        fontFamily = BebasNeue,
                        fontSize = 44.sp,
                        lineHeight = 48.sp,
                        letterSpacing = 1.54.sp,
                        modifier = Modifier.alpha(if (s.paused) .6f else 1f),
                    )
                    Spacer(Modifier.height(8.dp))

                    GlassSurface(
                        scale = 1f,
                        radiusDp = 16f,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        borderStart = Bor.idleCyan.copy(alpha = .42f),
                        borderEnd = Bor.idleViolet.copy(alpha = .42f),
                    ) {
                        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 7.dp)) {
                            DbMeter(level = s.level)
                            Spacer(Modifier.height(2.dp))
                            DbScale()
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    GlassSurface(
                        scale = 1f,
                        radiusDp = 18f,
                        modifier = Modifier.width(210.dp).height(32.dp),
                        borderStart = Bor.idleCyan.copy(alpha = .42f),
                        borderEnd = Bor.idleViolet.copy(alpha = .42f),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "M4A · 96 kbps · mono",
                                color = Bor.idleDockLabel,
                                fontFamily = BarlowSemiCondensed,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                letterSpacing = 1.82.sp,
                                maxLines = 1,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    RecordButtonRow(
                        paused = s.paused,
                        onStop = {
                            // Titel mitschicken: schlägt 400ms-Debounce bei schnellem Stop
                            context.startService(
                                RecorderService.intent(context, RecorderService.ACTION_STOP)
                                    .putExtra(RecorderService.EXTRA_TITLE, titleText))
                        },
                        onPauseResume = {
                            send(if (s.paused) RecorderService.ACTION_RESUME
                                 else RecorderService.ACTION_PAUSE)
                        },
                        onMarker = { send(RecorderService.ACTION_MARKER) },
                    )
                    Spacer(Modifier.height(8.dp))

                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(s.markers.asReversed()) { m ->
                            Row(
                                Modifier.fillMaxWidth().height(42.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    formatMs(m.timeMs),
                                    color = Bor.idleDockLabel,
                                    fontFamily = BarlowSemiCondensed,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    style = TextStyle(fontFeatureSettings = "tnum"),
                                    modifier = Modifier.width(58.dp),
                                )
                                Text(
                                    m.label.ifEmpty { "(unbenannt)" },
                                    color = Bor.idleTextPrimary,
                                    fontFamily = BarlowSemiCondensed,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            HorizontalDivider(color = Bor.idleOutline.copy(alpha = .24f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecHeader(paused: Boolean, onHide: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        PulsingRecDot(paused = paused)
        Spacer(Modifier.width(8.dp))
        Text(
            if (paused) "PAUSE" else "REC",
            color = if (paused) Bor.idleDockLabel else Bor.idleAmber,
            fontFamily = BarlowSemiCondensed,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.weight(1f))
        HidePill(onClick = onHide)
    }
}

@Composable
private fun TitleField(text: String, onChange: (String) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    GlassSurface(
        scale = 1f,
        radiusDp = 18f,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        borderStart = Bor.idleCyan.copy(alpha = if (focused) .90f else .42f),
        borderEnd = Bor.idleViolet.copy(alpha = if (focused) .90f else .42f),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (focused) Modifier.background(
                        Bor.idleCyan.copy(alpha = .10f),
                        RoundedCornerShape(18.dp),
                    ) else Modifier
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = text,
                onValueChange = onChange,
                singleLine = true,
                interactionSource = interactionSource,
                cursorBrush = SolidColor(Bor.idleCyan),
                textStyle = TextStyle(
                    color = Bor.idleTextPrimary,
                    fontFamily = BarlowSemiCondensed,
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    letterSpacing = .36.sp,
                ),
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Text(
                            "Titel…",
                            color = Bor.idleTextSecondary,
                            fontFamily = BarlowSemiCondensed,
                            fontSize = 18.sp,
                        )
                    }
                    inner()
                },
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun HidePill(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) .96f else 1f,
        animationSpec = if (pressed) tween(90) else spring(
            dampingRatio = 1f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "recordHidePress",
    )
    Box(
        Modifier
            .size(width = 72.dp, height = 48.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .semantics { contentDescription = "Bildschirm verstecken" }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            scale = 1f,
            radiusDp = 18f,
            modifier = Modifier.size(width = 68.dp, height = 36.dp),
            borderStart = Bor.idleCyan.copy(alpha = if (pressed) .36f else .42f),
            borderEnd = Bor.idleViolet.copy(alpha = if (pressed) .36f else .42f),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "HIDE",
                    color = Bor.idleAmber,
                    fontFamily = BarlowSemiCondensed,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 1.2.sp,
                )
            }
        }
    }
}
