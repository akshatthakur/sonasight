package com.sonasight.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.util.Size
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.sonasight.app.Adjust
import com.sonasight.app.Aspect
import com.sonasight.app.CameraRig
import com.sonasight.app.Films
import com.sonasight.app.LocationTracker
import com.sonasight.app.Look
import com.sonasight.app.Media
import com.sonasight.app.Mode
import com.sonasight.app.Overlay
import com.sonasight.app.Prefs
import com.sonasight.app.Recipe
import com.sonasight.app.ShotInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

private val LENSES = listOf(24, 28, 35, 50)

private enum class Sheet { NONE, FILM, TUNE, MENU }

@SuppressLint("ClickableViewAccessibility")
@Composable
fun CameraScreen(
    rig: CameraRig,
    prefs: Prefs,
    shutterPresses: State<Int>,
    requestedMode: MutableState<Mode?>,
    onEdit: (Uri) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(requestedMode.value ?: prefs.mode) }
    var sceneId by remember { mutableStateOf(prefs.scene) }
    var stockId by remember { mutableStateOf(prefs.stock) }
    var recipeId by remember { mutableStateOf(prefs.recipe) }
    var adjust by remember { mutableStateOf(prefs.adjust) }
    var recipes by remember { mutableStateOf(prefs.recipes) }
    var lens by remember { mutableIntStateOf(prefs.lens) }
    var zoom by remember { mutableFloatStateOf(prefs.lens / 24f) }
    var zoomMin by remember { mutableFloatStateOf(1f) }
    var zoomMax by remember { mutableFloatStateOf(1f) }
    var aspect by remember { mutableStateOf(prefs.aspect) }
    var overlay by remember { mutableStateOf(prefs.overlay) }
    var turn by remember { mutableIntStateOf(prefs.turn) }
    var ev by remember { mutableFloatStateOf(prefs.ev) }
    var level by remember { mutableStateOf(prefs.level) }
    var front by remember { mutableStateOf(prefs.front) }
    var shots by remember { mutableIntStateOf(prefs.shots) }
    var dateStamp by remember { mutableStateOf(prefs.dateStamp) }
    var timer by remember { mutableIntStateOf(prefs.timer) }
    var locationOn by remember { mutableStateOf(prefs.location) }
    var zebra by remember { mutableStateOf(prefs.zebra) }
    var rollSize by remember { mutableIntStateOf(prefs.rollSize) }
    var rollCount by remember { mutableIntStateOf(Media.rollCount(context)) }
    var torch by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var countdownJob by remember { mutableStateOf<Job?>(null) }
    var ready by remember { mutableStateOf(false) }
    var thumb by remember { mutableStateOf<Bitmap?>(null) }
    var lastUri by remember { mutableStateOf<Uri?>(null) }
    var recording by remember { mutableStateOf(false) }
    var recMs by remember { mutableLongStateOf(0L) }
    var developing by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(Sheet.NONE) }
    var notice by remember { mutableStateOf<String?>(null) }
    var focusAt by remember { mutableStateOf<Offset?>(null) }
    var stampKey by remember { mutableIntStateOf(0) }
    val focusAnim = remember { Animatable(0f) }
    val blink = remember { Animatable(0f) }
    val gravityAngle = remember { mutableFloatStateOf(0f) }
    val tracker = remember { LocationTracker(context) }

    val stock = Films.stock(stockId)
    val scene = Films.scene(sceneId)
    val recipe = recipes.firstOrNull { it.id == recipeId }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    fun say(text: String) {
        notice = text
        scope.launch { delay(2200); if (notice == text) notice = null }
    }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onEdit(uri)
    }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        val ok = r.values.any { it }
        locationOn = ok; prefs.location = ok
        if (!ok) say("LOCATION NOT ALLOWED")
    }

    // live look and viewfinder warnings
    LaunchedEffect(stockId, sceneId, adjust) { rig.processor.look = Look.of(stock, scene, adjust) }
    LaunchedEffect(zebra) { rig.processor.zebra = zebra }

    DisposableEffect(Unit) {
        rig.onZoom = { r, mn, mx -> zoom = r; zoomMin = mn; zoomMax = mx }
        rig.start(previewView, mode, front) {
            ready = true
            rig.setFocal(lens)
            rig.setEv(ev)
        }
        onDispose {
            rig.onZoom = null
            rig.unbind()
            tracker.stop()
        }
    }
    LaunchedEffect(mode, front) {
        if (ready) {
            rig.bind(previewView, mode, front)
            rig.setFocal(lens)
            rig.setEv(ev)
            torch = false
        }
    }
    LaunchedEffect(ev) { if (ready) rig.setEv(ev) }
    LaunchedEffect(torch) { rig.setTorch(torch) }
    LaunchedEffect(locationOn) { if (locationOn) tracker.start() else tracker.stop() }
    LaunchedEffect(requestedMode.value) {
        requestedMode.value?.let { if (!recording) { mode = it; prefs.mode = it } }
        requestedMode.value = null
    }

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
                if (abs(z) > 8.5f) return // lying flat: keep the last values
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
        sceneId = id; stockId = s.stockId; aspect = s.aspect; overlay = s.overlay; ev = s.ev
        recipeId = ""; adjust = Adjust.NONE
        lens = s.lens; rig.setFocal(s.lens)
        prefs.scene = id; prefs.stock = s.stockId; prefs.lens = s.lens; prefs.aspect = s.aspect
        prefs.overlay = s.overlay; prefs.ev = s.ev; prefs.recipe = ""; prefs.adjust = Adjust.NONE
    }

    fun developRoll() {
        if (rollCount == 0) return
        val n = rollCount
        scope.launch {
            developing = true
            val uri = withContext(Dispatchers.IO) { Media.developRoll(context) }
            developing = false
            rollCount = 0
            if (uri != null) {
                lastUri = uri
                thumb = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.loadThumbnail(uri, Size(320, 320), null) }.getOrNull()
                }
            }
            say("DEVELOPED  $n  FRAMES")
        }
    }

    fun fire() {
        if (mode == Mode.PHOTO) {
            if (developing) return
            developing = true
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            scope.launch { blink.snapTo(1f); blink.animateTo(0f, tween(260)) }
            val toRoll = rollSize > 0
            val info = ShotInfo(
                film = recipe?.name ?: "${stock.name} ${stock.iso}",
                scene = scene.name,
                focalMm = (zoom * 24f).roundToInt(),
                location = if (locationOn) tracker.last else null,
            )
            rig.takePhoto(Look.of(stock, scene, adjust), aspect, front, dateStamp, info, toRoll) { ok, uri, bmp ->
                developing = false
                if (!ok) {
                    say("COULDN'T SAVE")
                } else {
                    shots += 1; prefs.shots = shots
                    stampKey += 1
                    if (toRoll) {
                        rollCount += 1
                        if (rollCount >= rollSize) developRoll()
                    } else {
                        lastUri = uri; thumb = bmp
                    }
                }
            }
        } else if (!recording) {
            recording = true
            recMs = 0
            rig.startRecording(if (locationOn) tracker.last else null, onTick = { recMs = it }) { uri ->
                recording = false
                if (uri != null) {
                    lastUri = uri; shots += 1; prefs.shots = shots
                    scope.launch {
                        thumb = withContext(Dispatchers.IO) {
                            runCatching { context.contentResolver.loadThumbnail(uri, Size(320, 320), null) }.getOrNull()
                        }
                    }
                } else say("COULDN'T SAVE")
            }
        } else {
            rig.stopRecording()
        }
    }

    fun shutter() {
        if (!ready) return
        // a running countdown: pressing again cancels it
        if (countdownJob != null) {
            countdownJob?.cancel(); countdownJob = null; countdown = 0
            return
        }
        if (timer > 0 && !recording) {
            countdownJob = scope.launch {
                for (i in timer downTo 1) {
                    countdown = i
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    delay(1000)
                }
                countdown = 0
                countdownJob = null
                fire()
            }
        } else fire()
    }

    // volume keys
    var seenPresses by remember { mutableIntStateOf(shutterPresses.value) }
    LaunchedEffect(shutterPresses.value) {
        if (shutterPresses.value != seenPresses) {
            seenPresses = shutterPresses.value
            if (sheet == Sheet.NONE) shutter()
        }
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {

            // ---- top bar ----
            Row(
                Modifier.fillMaxWidth().height(48.dp).padding(start = 18.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    recording -> {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Gold))
                        Spacer(Modifier.width(8.dp))
                        BasicText(clock(recMs), style = Numeral.copy(color = Gold))
                    }
                    rollSize > 0 && mode == Mode.PHOTO ->
                        BasicText(
                            rollCount.toString().padStart(2, '0') + " / $rollSize",
                            style = Numeral.copy(color = Gold),
                        )
                    else -> BasicText("No. " + shots.toString().padStart(4, '0'), style = Numeral.copy(color = Ash))
                }
                Spacer(Modifier.weight(1f))
                TextKey(if (timer == 0) "TIMER" else "${timer}S", active = timer > 0) {
                    timer = when (timer) { 0 -> 3; 3 -> 10; else -> 0 }; prefs.timer = timer
                }
                if (ready && rig.hasTorch) TextKey("TORCH", active = torch) { torch = !torch }
                if (rig.hasFront && !recording) TextKey("FLIP") { front = !front; prefs.front = front }
                TextKey("•••") { sheet = Sheet.MENU }
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
                            var scaling = false
                            val pinch = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                                    scaling = true
                                    return true
                                }

                                override fun onScale(detector: ScaleGestureDetector): Boolean {
                                    rig.setZoomRatio(rig.zoomRatio() * detector.scaleFactor)
                                    return true
                                }
                            })
                            previewView.setOnTouchListener { v, e ->
                                pinch.onTouchEvent(e)
                                when (e.actionMasked) {
                                    MotionEvent.ACTION_DOWN -> scaling = false
                                    MotionEvent.ACTION_UP -> if (!scaling) {
                                        rig.focus(previewView, e.x, e.y)
                                        focusAt = Offset(e.x, e.y)
                                        scope.launch { focusAnim.snapTo(1f); focusAnim.animateTo(0f, tween(1400)) }
                                        v.performClick()
                                    } else {
                                        prefs.lens = (rig.zoomRatio() * 24f).roundToInt()
                                    }
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
                        if (level) {
                            val a = gravityAngle.floatValue
                            val near = (a / 90f).roundToInt() * 90f
                            val isLevel = abs(a - near) < 0.8f
                            val len = minOf(frame.width, frame.height) * 0.42f
                            val c = frame.center
                            rotate(if (isLevel) near else a, c) {
                                val col = if (isLevel) Gold else Bone.copy(alpha = 0.55f)
                                drawLine(col, Offset(c.x - len, c.y), Offset(c.x - len * 0.18f, c.y), hair * 1.2f)
                                drawLine(col, Offset(c.x + len * 0.18f, c.y), Offset(c.x + len, c.y), hair * 1.2f)
                            }
                            drawCircle(Bone.copy(alpha = 0.5f), hair * 1.6f, c)
                        }
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
                        if (blink.value > 0f) drawRect(Ink.copy(alpha = blink.value * 0.9f))
                    }

                    HankoStamp(stampKey, Modifier.align(Alignment.TopEnd).padding(14.dp))
                    if (countdown > 0) {
                        BasicText(
                            countdown.toString(),
                            style = TextStyle(color = Gold, fontSize = 96.sp, fontFamily = FontFamily.Serif),
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    notice?.let {
                        BasicText(
                            it, style = Label.copy(color = Bone, letterSpacing = 3.sp),
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp)
                                .background(Ink.copy(alpha = 0.7f)).padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }

                    // film tag and readout
                    Row(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cartouche(Modifier.clickable { sheet = Sheet.FILM }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (stock.category == "MEISHO") {
                                    VerticalText(stock.iso, color = Gold, size = 11f)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Column {
                                    BasicText(recipe?.name?.uppercase() ?: stock.name, style = Label.copy(color = Bone))
                                    if (stock.category != "MEISHO" || recipe != null) {
                                        Spacer(Modifier.height(2.dp))
                                        BasicText(
                                            if (recipe != null) "${stock.name} ${stock.iso}" else stock.iso,
                                            style = Numeral.copy(color = Gold, fontSize = 9.sp),
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        val evText = if (ev == 0f) "±0" else signed(ev)
                        val mm = (zoom * 24f).roundToInt()
                        BasicText(
                            "EV $evText   ${mm}MM" + if (mode == Mode.PHOTO) "   ${aspect.label}" else "   16:9",
                            style = Numeral.copy(color = Mist, fontSize = 10.sp),
                            modifier = Modifier.background(Ink.copy(alpha = 0.45f)).padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            // ---- framing row ----
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
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
                TextKey("TUNE", active = adjust != Adjust.NONE) { sheet = Sheet.TUNE }
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
                val wide = (zoomMin * 24f).roundToInt()
                val lenses = if (zoomMin < 0.95f) listOf(wide) + LENSES else LENSES
                val current = zoom * 24f
                lenses.forEach { mm ->
                    if (mm / 24f <= zoomMax + 0.01f || mm <= 24) {
                        TextKey(mm.toString(), active = abs(current - mm) < 0.8f, numeric = true) {
                            lens = mm; prefs.lens = mm
                            rig.setZoomRatio(mm / 24f)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                EvRuler(ev) { ev = it; prefs.ev = it }
            }

            SceneDial(sceneId) { applyScene(it) }

            // ---- shutter row ----
            Row(
                Modifier.fillMaxWidth().height(104.dp).padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(2.dp)).background(Slate)
                        .border(1.dp, Ash.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                        .clickable(enabled = !recording) {
                            val u = lastUri
                            if (u != null) onEdit(u)
                            else pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val t = thumb
                    if (rollSize > 0 && mode == Mode.PHOTO) {
                        // a roll hides its frames until it's developed
                        BasicText(rollCount.toString(), style = Numeral.copy(color = Gold, fontSize = 14.sp))
                    } else if (t != null) {
                        Image(t.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        BasicText("EDIT", style = Label.copy(color = Ash, fontSize = 8.sp))
                    }
                }
                Spacer(Modifier.weight(1f))
                Shutter(mode, recording, developing || countdown > 0) { shutter() }
                Spacer(Modifier.weight(1f))
                ModeSwitch(mode, enabled = !recording && countdown == 0) { m -> mode = m; prefs.mode = m }
            }
        }

        when (sheet) {
            Sheet.FILM -> FilmPicker(
                selectedStock = stockId,
                selectedRecipe = recipeId,
                recipes = recipes,
                onStock = {
                    stockId = it; prefs.stock = it
                    if (recipeId.isNotEmpty()) { adjust = Adjust.NONE; prefs.adjust = Adjust.NONE }
                    recipeId = ""; prefs.recipe = ""
                    sheet = Sheet.NONE
                },
                onRecipe = { r ->
                    stockId = r.stockId; adjust = r.adjust; recipeId = r.id
                    prefs.stock = r.stockId; prefs.adjust = r.adjust; prefs.recipe = r.id
                    sheet = Sheet.NONE
                },
                onDeleteRecipe = { r ->
                    recipes = recipes.filter { it.id != r.id }; prefs.recipes = recipes
                    if (recipeId == r.id) { recipeId = ""; prefs.recipe = "" }
                },
                onClose = { sheet = Sheet.NONE },
            )
            Sheet.TUNE -> TuneSheet(
                adjust = adjust,
                showExposure = false,
                onChange = { adjust = it; prefs.adjust = it },
                onSave = { name ->
                    val r = Recipe(UUID.randomUUID().toString(), name, stockId, adjust)
                    recipes = recipes + r; prefs.recipes = recipes
                    recipeId = r.id; prefs.recipe = r.id
                    say("SAVED  ${name.uppercase()}")
                },
                onClose = { sheet = Sheet.NONE },
            )
            Sheet.MENU -> MenuSheet(
                dateStamp = dateStamp,
                rollSize = rollSize,
                rollCount = rollCount,
                location = locationOn,
                zebra = zebra,
                onDate = { dateStamp = !dateStamp; prefs.dateStamp = dateStamp },
                onRoll = {
                    val next = when (rollSize) { 0 -> 24; 24 -> 36; else -> 0 }
                    // changing rolls develops whatever is on the current one
                    if (rollCount > 0 && next != rollSize) developRoll()
                    rollSize = next; prefs.rollSize = next
                },
                onDevelop = { developRoll() },
                onLocation = {
                    if (locationOn) { locationOn = false; prefs.location = false }
                    else if (tracker.allowed()) { locationOn = true; prefs.location = true }
                    else askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                },
                onZebra = { zebra = !zebra; prefs.zebra = zebra },
                onEdit = {
                    sheet = Sheet.NONE
                    pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                },
                onClose = { sheet = Sheet.NONE },
            )
            Sheet.NONE -> Unit
        }
    }
}

private fun clock(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d".format(s / 60, s % 60)
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
        val p = Path().apply {
            moveTo(cx - 4.dp.toPx(), 0f); lineTo(cx + 4.dp.toPx(), 0f); lineTo(cx, 5.dp.toPx()); close()
        }
        drawPath(p, Gold)
        drawLine(Gold, Offset(cx, 5.dp.toPx()), Offset(cx, size.height - 4.dp.toPx()), 1.dp.toPx())
    }
}

@Composable
fun SceneDial(selected: String, onPick: (String) -> Unit) {
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
    Column(Modifier.width(78.dp), horizontalAlignment = Alignment.Start) {
        for (m in Mode.entries) {
            val on = m == mode
            Row(
                Modifier.clickable(enabled = enabled) { onChange(m) }.padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(4.dp).clip(CircleShape).background(if (on) Gold else Color.Transparent))
                Spacer(Modifier.width(6.dp))
                Bilingual(
                    if (m == Mode.PHOTO) "STILL" else "MOTION", if (m == Mode.PHOTO) "静止" else "動画",
                    color = if (on) Bone else Ash, size = 9f,
                )
            }
        }
    }
}

/** Full-screen dark sheet that closes when you tap outside its content. */
@Composable
fun Scrim(onClose: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Ink.copy(alpha = 0.94f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }
    ) {
        Seigaiha(alpha = 0.05f)
        Bokashi(height = 160.dp, strength = 0.14f)
        Box(Modifier.fillMaxSize().systemBarsPadding()) { content() }
    }
}

@Composable
fun FilmPicker(
    selectedStock: String,
    selectedRecipe: String,
    recipes: List<Recipe>,
    onStock: (String) -> Unit,
    onRecipe: (Recipe) -> Unit,
    onDeleteRecipe: (Recipe) -> Unit,
    onClose: () -> Unit,
) {
    var confirm by remember { mutableStateOf<String?>(null) }
    Scrim(onClose) {
        LazyColumn(Modifier.fillMaxSize().padding(top = 24.dp)) {
            if (recipes.isNotEmpty()) {
                item { SectionLabel("your recipes", "レシピ", Modifier.padding(horizontal = 24.dp, vertical = 10.dp)) }
                items(recipes, key = { it.id }) { r ->
                    val asking = confirm == r.id
                    val st = Films.stock(r.stockId)
                    PickerRow(
                        title = if (asking) "TAP TO DELETE" else r.name.uppercase(),
                        badge = if (asking) "" else st.iso,
                        note = if (asking) "or long-press again to keep it" else "${st.name} ${st.iso}, tuned",
                        on = r.id == selectedRecipe,
                        onTap = { if (asking) { onDeleteRecipe(r); confirm = null } else onRecipe(r) },
                        onLong = { confirm = if (asking) null else r.id },
                    )
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
            item { SectionLabel("film", "フィルム", Modifier.padding(horizontal = 24.dp, vertical = 10.dp)) }
            items(Films.stocks, key = { it.id }) { s ->
                PickerRow(
                    title = s.name, badge = s.iso, note = s.note,
                    on = selectedRecipe.isEmpty() && s.id == selectedStock,
                    onTap = { onStock(s.id) }, onLong = null,
                )
            }
            item {
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 26.dp, bottom = 10.dp)) {
                    SectionLabel("meisho", "名所")
                    Spacer(Modifier.height(4.dp))
                    BasicText("looks named after places in Japan", style = Body)
                }
            }
            items(Films.meisho, key = { it.id }) { s ->
                PickerRow(
                    title = s.name, badge = s.iso, note = s.note,
                    on = selectedRecipe.isEmpty() && s.id == selectedStock,
                    onTap = { onStock(s.id) }, onLong = null,
                )
            }
            if (recipes.isNotEmpty()) {
                item { BasicText("long-press a recipe to delete it", style = Body, modifier = Modifier.padding(24.dp)) }
            }
        }
    }
}

@Composable
private fun PickerRow(title: String, badge: String, note: String, on: Boolean, onTap: () -> Unit, onLong: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .background(if (on) Gold.copy(alpha = 0.06f) else Coal.copy(alpha = 0.6f))
            .border(1.dp, if (on) Gold else Slate, RoundedCornerShape(2.dp))
            .pointerInput(title, onLong != null) {
                detectTapGestures(onTap = { onTap() }, onLongPress = { onLong?.invoke() })
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                BasicText(
                    title,
                    style = TextStyle(color = if (on) Bone else Mist, fontSize = 18.sp, letterSpacing = 3.sp, fontFamily = FontFamily.Serif),
                )
                Spacer(Modifier.width(10.dp))
                BasicText(badge, style = Numeral.copy(color = Gold, fontSize = 11.sp))
            }
            Spacer(Modifier.height(4.dp))
            BasicText(note, style = Body)
        }
    }
}

/** Sliders over the film, with recipe saving. Shared by the camera and the editor. */
@Composable
fun TuneSheet(
    adjust: Adjust,
    showExposure: Boolean,
    onChange: (Adjust) -> Unit,
    onSave: (String) -> Unit,
    onClose: () -> Unit,
) {
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    Box(
        Modifier.fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
    ) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Ink.copy(alpha = 0.96f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .navigationBarsPadding(),
        ) {
            Box {
                Bokashi(height = 48.dp, strength = 0.12f)
                SectionLabel("tune", "調整", Modifier.padding(start = 24.dp, top = 14.dp))
            }
            TuneSliders(adjust, showExposure, onChange)
            Spacer(Modifier.height(6.dp))
            Spacer(Modifier.height(8.dp))
            RecipeBar(naming, name, { name = it.take(24) }, onReset = { onChange(Adjust.NONE) }, onStartNaming = { naming = true },
                onSave = { onSave(name.trim()); naming = false; name = "" }, onDone = onClose)
        }
    }
}

@Composable
fun RecipeBar(
    naming: Boolean,
    name: String,
    onName: (String) -> Unit,
    onReset: () -> Unit,
    onStartNaming: () -> Unit,
    onSave: () -> Unit,
    onDone: (() -> Unit)?,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (naming) {
            Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                if (name.isEmpty()) BasicText("name this recipe", style = Body)
                BasicTextField(
                    value = name,
                    onValueChange = onName,
                    singleLine = true,
                    textStyle = Numeral.copy(color = Bone, fontSize = 14.sp),
                    cursorBrush = SolidColor(Gold),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            TextKey("SAVE", active = name.isNotBlank()) { if (name.isNotBlank()) onSave() }
        } else {
            TextKey("RESET") { onReset() }
            Spacer(Modifier.weight(1f))
            TextKey("SAVE AS RECIPE", active = true) { onStartNaming() }
            if (onDone != null) TextKey("DONE") { onDone() }
        }
    }
}

@Composable
fun TuneSliders(adjust: Adjust, showExposure: Boolean, onChange: (Adjust) -> Unit) {
    FineSlider("STRENGTH", adjust.strength, 0f..1f, 1f, ::pct) { onChange(adjust.copy(strength = it)) }
    if (showExposure) FineSlider("EXPOSURE", adjust.exposure, -2f..2f, 0f, { signed(it) }) { onChange(adjust.copy(exposure = it)) }
    FineSlider("WARMTH", adjust.warmth, -1f..1f, 0f, { signed(it, 2) }) { onChange(adjust.copy(warmth = it)) }
    FineSlider("GRAIN", adjust.grain, 0f..2f, 1f, ::pct) { onChange(adjust.copy(grain = it)) }
    FineSlider("GLOW", adjust.glow, 0f..1.5f, 0f, ::pct) { onChange(adjust.copy(glow = it)) }
    FineSlider("VIGNETTE", adjust.vignette, 0f..0.6f, 0f, ::pct) { onChange(adjust.copy(vignette = it)) }
}

@Composable
private fun MenuSheet(
    dateStamp: Boolean,
    rollSize: Int,
    rollCount: Int,
    location: Boolean,
    zebra: Boolean,
    onDate: () -> Unit,
    onRoll: () -> Unit,
    onDevelop: () -> Unit,
    onLocation: () -> Unit,
    onZebra: () -> Unit,
    onEdit: () -> Unit,
    onClose: () -> Unit,
) {
    Scrim(onClose) {
        Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
            SectionLabel("settings", "設定", Modifier.padding(horizontal = 24.dp, vertical = 10.dp))
            MenuRow("DATE STAMP", "日付", if (dateStamp) "ON" else "OFF", "a gold date in the corner of stills", onDate)
            MenuRow(
                "ROLL", "フィルム", if (rollSize == 0) "OFF" else "$rollSize EXP",
                if (rollSize == 0) "shoot a roll of 24 or 36, seen only once it's developed"
                else "$rollCount of $rollSize shot. the roll develops when it's full",
                onRoll,
            )
            if (rollCount > 0) MenuRow("DEVELOP NOW", "現像", "$rollCount", "send what's on the roll to your gallery", onDevelop)
            MenuRow("LOCATION", "位置", if (location) "ON" else "OFF", "save where each photo and video was taken", onLocation)
            MenuRow("HIGHLIGHT WARNING", "白飛び", if (zebra) "ON" else "OFF", "gold stripes where the brightest parts lose detail", onZebra)
            MenuRow("EDIT", "編集", "", "open a photo or video from your gallery", onEdit)
            Spacer(Modifier.weight(1f))
            BasicText(
                "the volume keys work as a shutter too.",
                style = Body, modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
            )
        }
    }
}

@Composable
private fun MenuRow(title: String, jp: String, value: String, note: String, onTap: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onTap() }.padding(horizontal = 28.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Bilingual(title, jp, color = Bone, size = 12f)
            Spacer(Modifier.height(4.dp))
            BasicText(note, style = Body)
        }
        BasicText(value, style = Numeral.copy(color = Gold, fontSize = 11.sp))
    }
}

@Composable
fun PermissionScreen(onAsk: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.Center) {
        Seigaiha(alpha = 0.06f)
        Bokashi(height = 220.dp, strength = 0.16f, modifier = Modifier.align(Alignment.TopCenter))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Cartouche(filled = Ink) {
                BasicText("SONASIGHT", style = Label.copy(color = Gold, fontSize = 13.sp, letterSpacing = 5.sp))
            }
            Spacer(Modifier.height(18.dp))
            BasicText(
                "Camera access is needed to shoot.\nMicrophone is used for sound in video.",
                style = TextStyle(
                    color = Mist, fontSize = 13.sp, fontFamily = FontFamily.SansSerif,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                ),
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
