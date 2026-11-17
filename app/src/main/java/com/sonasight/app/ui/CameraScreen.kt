package com.sonasight.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.util.Size
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.Surface
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.text.BasicText
import com.sonasight.app.Aspect
import com.sonasight.app.CameraRig
import com.sonasight.app.Films
import com.sonasight.app.Look
import com.sonasight.app.Mode
import com.sonasight.app.Overlay
import com.sonasight.app.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

// Palette: black, greys, white, and sona gold.
val Ink = Color(0xFF000000)
val Coal = Color(0xFF0E0E0E)
val Slate = Color(0xFF1C1C1C)
val Ash = Color(0xFF6E6E6E)
val Mist = Color(0xFFB4B4B4)
val Bone = Color(0xFFF2F2F0)
val Gold = Color(0xFFC9A55A)

private val Label = TextStyle(
    color = Mist, fontSize = 10.sp, letterSpacing = 2.2.sp,
    fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
)
private val Numeral = TextStyle(color = Bone, fontSize = 12.sp, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)

private val LENSES = listOf(24, 28, 35, 50)

@SuppressLint("ClickableViewAccessibility")
@Composable
fun CameraScreen(rig: CameraRig, prefs: Prefs) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(prefs.mode) }
    var sceneId by remember { mutableStateOf(prefs.scene) }
    var stockId by remember { mutableStateOf(prefs.stock) }
    var lens by remember { mutableIntStateOf(prefs.lens) }
    var aspect by remember { mutableStateOf(prefs.aspect) }
    var overlay by remember { mutableStateOf(prefs.overlay) }
    var turn by remember { mutableIntStateOf(prefs.turn) }
    var ev by remember { mutableFloatStateOf(prefs.ev) }
    var level by remember { mutableStateOf(prefs.level) }
    var front by remember { mutableStateOf(prefs.front) }
    var shots by remember { mutableIntStateOf(prefs.shots) }
    var ready by remember { mutableStateOf(false) }
    var thumb by remember { mutableStateOf<Bitmap?>(null) }
    var lastUri by remember { mutableStateOf<Uri?>(null) }
    var recording by remember { mutableStateOf(false) }
    var recMs by remember { mutableLongStateOf(0L) }
    var developing by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var focusAt by remember { mutableStateOf<Offset?>(null) }
    val focusAnim = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    val gravityAngle = remember { mutableFloatStateOf(0f) }

    val stock = Films.stock(stockId)
    val scene = Films.scene(sceneId)

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // live look
    LaunchedEffect(stockId, sceneId) { rig.processor.look = Look.of(stock, scene) }

    LaunchedEffect(Unit) {
        rig.start(previewView, mode, front) {
            ready = true
            rig.setFocal(lens)
            rig.setEv(ev)
        }
    }
    LaunchedEffect(mode, front) {
        if (ready) {
            rig.bind(previewView, mode, front)
            rig.setFocal(lens)
            rig.setEv(ev)
        }
    }
    LaunchedEffect(lens) { if (ready) rig.setFocal(lens) }
    LaunchedEffect(ev) { if (ready) rig.setEv(ev) }

    // gravity: device rotation for captures, and the level line
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var rot = Surface.ROTATION_0
        var wasLevel = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val x = e.values[0]
                val y = e.values[1]
                val z = e.values[2]
                if (abs(z) > 8.5f) return // lying flat: keep last values
                val angle = Math.toDegrees(atan2(x, y).toDouble()).toFloat()
                gravityAngle.floatValue = angle
                val near = (angle / 90f).roundToInt() * 90
                if (abs(angle - near) < 30f) {
                    val r = when (((near % 360) + 360) % 360) {
                        90 -> Surface.ROTATION_90
                        180 -> Surface.ROTATION_180
                        270 -> Surface.ROTATION_270
                        else -> Surface.ROTATION_0
                    }
                    if (r != rot) { rot = r; rig.setRotation(r) }
                }
                val isLevel = abs(angle - near) < 0.8f
                if (isLevel && !wasLevel && prefs.level) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                wasLevel = isLevel
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }

    fun applyScene(id: String) {
        val s = Films.scene(id)
        sceneId = id; stockId = s.stockId; lens = s.lens; aspect = s.aspect; overlay = s.overlay; ev = s.ev
        prefs.scene = id; prefs.stock = s.stockId; prefs.lens = s.lens; prefs.aspect = s.aspect
        prefs.overlay = s.overlay; prefs.ev = s.ev
    }

    fun shutter() {
        if (!ready) return
        if (mode == Mode.PHOTO) {
            if (developing) return
            developing = true
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            scope.launch { flash.snapTo(1f); flash.animateTo(0f, tween(260)) }
            rig.takePhoto(Look.of(stock, scene), aspect, front) { uri, bmp ->
                developing = false
                if (uri != null) {
                    lastUri = uri; thumb = bmp; shots += 1; prefs.shots = shots
                }
            }
        } else if (!recording) {
            recording = true
            recMs = 0
            rig.startRecording(onTick = { recMs = it }) { uri ->
                recording = false
                if (uri != null) {
                    lastUri = uri; shots += 1; prefs.shots = shots
                    scope.launch {
                        thumb = withContext(Dispatchers.IO) {
                            runCatching { context.contentResolver.loadThumbnail(uri, Size(320, 320), null) }.getOrNull()
                        }
                    }
                }
            }
        } else {
            rig.stopRecording()
        }
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {

            // ---- top bar ----
            Row(
                Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText("SONASIGHT", style = Label.copy(color = Gold, letterSpacing = 3.sp))
                Spacer(Modifier.weight(1f))
                if (recording) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Gold))
                    Spacer(Modifier.width(8.dp))
                    BasicText(clock(recMs), style = Numeral.copy(color = Gold))
                } else {
                    BasicText("No. " + shots.toString().padStart(4, '0'), style = Numeral.copy(color = Ash))
                }
                Spacer(Modifier.weight(1f))
                if (rig.hasFront && !recording) {
                    TextKey("FLIP") { front = !front; prefs.front = front }
                }
            }

            // ---- viewfinder ----
            BoxWithConstraints(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                val sensorRatio = if (mode == Mode.PHOTO) 3f / 4f else 9f / 16f
                Box(
                    Modifier.aspectRatio(sensorRatio, matchHeightConstraintsFirst = maxWidth / maxHeight > sensorRatio)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Coal),
                ) {
                    AndroidView(
                        factory = {
                            previewView.setOnTouchListener { v, ev2 ->
                                if (ev2.action == MotionEvent.ACTION_UP) {
                                    rig.focus(previewView, ev2.x, ev2.y)
                                    focusAt = Offset(ev2.x, ev2.y)
                                    scope.launch { focusAnim.snapTo(1f); focusAnim.animateTo(0f, tween(1400)) }
                                    v.performClick()
                                }
                                true
                            }
                            previewView
                        },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Canvas(Modifier.fillMaxSize()) {
                        val full = Rect(0f, 0f, size.width, size.height)
                        val frame = if (mode == Mode.PHOTO) fitRect(full, aspect.short.toFloat() / aspect.long) else full
                        val hair = 1.dp.toPx()
                        // mask outside the frame
                        if (frame != full) {
                            val p = Path().apply {
                                fillType = PathFillType.EvenOdd
                                addRect(full); addRect(frame)
                            }
                            drawPath(p, Ink.copy(alpha = 0.78f))
                            drawRect(Mist.copy(alpha = 0.35f), frame.topLeft, frame.size, style = Stroke(hair))
                        }
                        val guide = Gold.copy(alpha = 0.72f)
                        when (overlay) {
                            Overlay.PHI -> phiGrid(frame, guide, hair)
                            Overlay.SPIRAL -> goldenSpiral(frame, turn, guide, hair)
                            Overlay.TRIANGLES -> goldenTriangles(frame, turn, guide, hair)
                            Overlay.NONE -> Unit
                        }
                        // level line, read here so only this canvas redraws on sensor updates
                        if (level) {
                            val a = gravityAngle.floatValue
                            val near = (a / 90f).roundToInt() * 90f
                            val tilt = a - near
                            val isLevel = abs(tilt) < 0.8f
                            val len = minOf(frame.width, frame.height) * 0.42f
                            val c = frame.center
                            rotate(if (isLevel) near else a, c) {
                                val col = if (isLevel) Gold else Bone.copy(alpha = 0.55f)
                                drawLine(col, Offset(c.x - len, c.y), Offset(c.x - len * 0.18f, c.y), hair * 1.2f)
                                drawLine(col, Offset(c.x + len * 0.18f, c.y), Offset(c.x + len, c.y), hair * 1.2f)
                            }
                            drawCircle(Bone.copy(alpha = 0.5f), hair * 1.6f, c)
                        }
                        // focus brackets
                        val f = focusAt
                        val fa = focusAnim.value
                        if (f != null && fa > 0f) {
                            val s = 28.dp.toPx()
                            val k = 8.dp.toPx()
                            val col = Gold.copy(alpha = fa)
                            for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
                                val corner = Offset(f.x + sx * s, f.y + sy * s)
                                drawLine(col, corner, Offset(corner.x - sx * k, corner.y), hair * 1.4f)
                                drawLine(col, corner, Offset(corner.x, corner.y - sy * k), hair * 1.4f)
                            }
                        }
                        if (flash.value > 0f) drawRect(Ink.copy(alpha = flash.value * 0.9f))
                    }

                    // film tag and readout
                    Row(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.clip(RoundedCornerShape(2.dp))
                                .background(Ink.copy(alpha = 0.55f))
                                .border(1.dp, Gold.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                                .clickable { picker = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            BasicText("${stock.name}  ${stock.iso}", style = Label.copy(color = Bone))
                        }
                        Spacer(Modifier.weight(1f))
                        val evText = if (ev == 0f) "±0" else (if (ev > 0) "+" else "−") + "%.1f".format(abs(ev))
                        BasicText(
                            "EV $evText   ${lens}MM" + if (mode == Mode.PHOTO) "   ${aspect.label}" else "   16:9",
                            style = Numeral.copy(color = Mist, fontSize = 10.sp),
                            modifier = Modifier.background(Ink.copy(alpha = 0.45f)).padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            // ---- framing row ----
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextKey(overlay.label, active = overlay != Overlay.NONE) {
                    overlay = Overlay.entries[(overlay.ordinal + 1) % Overlay.entries.size]; prefs.overlay = overlay
                }
                if (overlay == Overlay.SPIRAL || overlay == Overlay.TRIANGLES) {
                    TextKey("TURN") { turn = (turn + 1) % 4; prefs.turn = turn }
                }
                TextKey("LEVEL", active = level) { level = !level; prefs.level = level }
                Spacer(Modifier.weight(1f))
                if (mode == Mode.PHOTO) {
                    TextKey(aspect.label, active = true) {
                        aspect = Aspect.entries[(aspect.ordinal + 1) % Aspect.entries.size]; prefs.aspect = aspect
                    }
                }
            }

            // ---- lens + exposure ----
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LENSES.forEach { mm ->
                    TextKey(mm.toString(), active = lens == mm, numeric = true) { lens = mm; prefs.lens = mm }
                }
                Spacer(Modifier.weight(1f))
                EvRuler(ev) { ev = it; prefs.ev = it }
            }

            // ---- scene dial ----
            SceneDial(sceneId) { applyScene(it) }

            // ---- shutter row ----
            Row(
                Modifier.fillMaxWidth().height(110.dp).padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(2.dp)).background(Slate)
                        .border(1.dp, Ash.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                        .clickable(enabled = lastUri != null) {
                            lastUri?.let { u ->
                                val i = Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                runCatching { context.startActivity(i) }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    thumb?.let {
                        Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
                Spacer(Modifier.weight(1f))
                Shutter(mode, recording, developing) { shutter() }
                Spacer(Modifier.weight(1f))
                ModeSwitch(mode, enabled = !recording) { m -> mode = m; prefs.mode = m }
            }
        }

        if (picker) {
            StockPicker(stockId, onPick = { stockId = it; prefs.stock = it; picker = false }, onClose = { picker = false })
        }
    }
}

private fun clock(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d".format(s / 60, s % 60)
}

@Composable
private fun TextKey(text: String, active: Boolean = false, numeric: Boolean = false, onClick: () -> Unit) {
    val base = if (numeric) Numeral.copy(fontSize = 12.sp) else Label
    Box(
        Modifier.clip(RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = base.copy(color = if (active) Gold else Mist))
    }
}

/** Draggable exposure ruler, -2 to +2 EV in thirds. */
@Composable
private fun EvRuler(ev: Float, onChange: (Float) -> Unit) {
    val view = LocalView.current
    var drag by remember { mutableFloatStateOf(0f) }
    var start by remember { mutableFloatStateOf(ev) }
    Canvas(
        Modifier.width(150.dp).height(34.dp)
            .pointerInput(Unit) {
                val step = 12.dp.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f; start = ev },
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        drag += dx
                        val thirds = (-drag / step).roundToInt()
                        val v = ((start * 3f).roundToInt() + thirds).coerceIn(-6, 6) / 3f
                        if (v != ev) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onChange(v)
                        }
                    },
                )
            },
    ) {
        val step = 12.dp.toPx()
        val cx = size.width / 2
        val cy = size.height / 2
        val offset = -ev * 3f * step
        for (i in -6..6) {
            val x = cx + i * step + offset
            if (x < 0 || x > size.width) continue
            val major = i % 3 == 0
            val h = if (major) 10.dp.toPx() else 5.dp.toPx()
            drawLine(if (i == 0) Bone else Ash, Offset(x, cy - h / 2), Offset(x, cy + h / 2), 1.dp.toPx())
        }
        // fixed gold index
        val p = Path().apply {
            moveTo(cx - 4.dp.toPx(), 0f); lineTo(cx + 4.dp.toPx(), 0f); lineTo(cx, 5.dp.toPx()); close()
        }
        drawPath(p, Gold)
        drawLine(Gold, Offset(cx, 5.dp.toPx()), Offset(cx, size.height - 4.dp.toPx()), 1.dp.toPx())
    }
}

@Composable
private fun SceneDial(selected: String, onPick: (String) -> Unit) {
    val state = rememberLazyListState()
    val index = Films.scenes.indexOfFirst { it.id == selected }.coerceAtLeast(0)
    LaunchedEffect(Unit) { state.scrollToItem((index - 1).coerceAtLeast(0)) }
    LazyRow(
        state = state,
        modifier = Modifier.fillMaxWidth().height(46.dp),
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(Films.scenes, key = { it.id }) { s ->
            val on = s.id == selected
            Column(
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                ) { onPick(s.id) }.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(4.dp).clip(CircleShape).background(if (on) Gold else Color.Transparent))
                Spacer(Modifier.height(6.dp))
                BasicText(s.name, style = Label.copy(color = if (on) Bone else Ash, fontSize = 11.sp))
            }
        }
    }
}

@Composable
private fun Shutter(mode: Mode, recording: Boolean, busy: Boolean, onPress: () -> Unit) {
    val press = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    Box(
        Modifier.size(76.dp).scale(press.value)
            .clip(CircleShape)
            .border(1.5.dp, if (recording) Gold else Bone, CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                scope.launch { press.snapTo(0.92f); press.animateTo(1f, tween(180)) }
                onPress()
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            mode == Mode.VIDEO && recording ->
                Box(Modifier.size(26.dp).clip(RoundedCornerShape(3.dp)).background(Gold))
            mode == Mode.VIDEO ->
                Box(Modifier.size(58.dp).clip(CircleShape).background(Slate).border(1.dp, Gold, CircleShape))
            else ->
                Box(Modifier.size(62.dp).clip(CircleShape).background(if (busy) Ash else Bone))
        }
    }
}

@Composable
private fun ModeSwitch(mode: Mode, enabled: Boolean, onChange: (Mode) -> Unit) {
    Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        for (m in Mode.entries) {
            val on = m == mode
            Row(
                Modifier.clickable(enabled = enabled) { onChange(m) }.padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(4.dp).clip(CircleShape).background(if (on) Gold else Color.Transparent))
                Spacer(Modifier.width(6.dp))
                BasicText(if (m == Mode.PHOTO) "STILL" else "MOTION", style = Label.copy(color = if (on) Bone else Ash, fontSize = 9.sp))
            }
        }
    }
}

@Composable
private fun StockPicker(selected: String, onPick: (String) -> Unit, onClose: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Ink.copy(alpha = 0.94f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }
            .systemBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
            BasicText("FILM", style = Label.copy(color = Gold, letterSpacing = 4.sp), modifier = Modifier.padding(horizontal = 28.dp))
            Spacer(Modifier.height(16.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(Films.stocks) { i, s ->
                    val on = s.id == selected
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(s.id) }.padding(horizontal = 28.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(2.dp).height(34.dp).background(if (on) Gold else Slate))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                BasicText(
                                    s.name,
                                    style = TextStyle(
                                        color = if (on) Bone else Mist, fontSize = 18.sp, letterSpacing = 3.sp,
                                        fontFamily = FontFamily.Serif,
                                    ),
                                )
                                Spacer(Modifier.width(10.dp))
                                BasicText(s.iso, style = Numeral.copy(color = Gold, fontSize = 11.sp))
                            }
                            Spacer(Modifier.height(4.dp))
                            BasicText(s.note, style = TextStyle(color = Ash, fontSize = 12.sp, fontFamily = FontFamily.SansSerif))
                        }
                        BasicText((i + 1).toString().padStart(2, '0'), style = Numeral.copy(color = Ash))
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionScreen(onAsk: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Ink).systemBarsPadding(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText("SONASIGHT", style = Label.copy(color = Gold, fontSize = 13.sp, letterSpacing = 5.sp))
            Spacer(Modifier.height(18.dp))
            BasicText(
                "Camera access is needed to shoot.\nMicrophone is used for sound in video.",
                style = TextStyle(color = Mist, fontSize = 13.sp, fontFamily = FontFamily.SansSerif, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            )
            Spacer(Modifier.height(28.dp))
            Box(
                Modifier.border(1.dp, Gold, RoundedCornerShape(2.dp)).clickable { onAsk() }
                    .padding(horizontal = 26.dp, vertical = 12.dp),
            ) {
                BasicText("ALLOW", style = Label.copy(color = Gold, letterSpacing = 3.sp))
            }
        }
    }
}
