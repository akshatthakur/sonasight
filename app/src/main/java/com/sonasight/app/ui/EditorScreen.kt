package com.sonasight.app.ui

import android.graphics.Bitmap
import android.net.Uri
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.exifinterface.media.ExifInterface
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.sonasight.app.Adjust
import com.sonasight.app.Aspect
import com.sonasight.app.FilmGlEffect
import com.sonasight.app.FilmProcessor
import com.sonasight.app.FilmStock
import com.sonasight.app.Films
import com.sonasight.app.Imaging
import com.sonasight.app.Look
import com.sonasight.app.LookRef
import com.sonasight.app.Media
import com.sonasight.app.Overlay
import com.sonasight.app.Prefs
import com.sonasight.app.Recipe
import com.sonasight.app.ShotInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

private enum class Tab(val label: String, val jp: String) {
    FILM("FILM", "フィルム"), TUNE("TUNE", "調整"), CROP("CROP", "切抜"), TRIM("TRIM", "尺"),
}

/** Crop shapes in the editor: free, the picture's own, then the fixed ratios. */
private val RATIOS: List<Ratio> = listOf(Ratio.Free, Ratio.Original) + Aspect.entries.map { Ratio.Fixed(it) }

private fun Ratio.label() = when (this) {
    Ratio.Free -> "FREE"
    Ratio.Original -> "ORIGINAL"
    is Ratio.Fixed -> aspect.label
}

@OptIn(UnstableApi::class)
@Composable
fun EditorScreen(
    uri: Uri,
    processor: FilmProcessor,
    prefs: Prefs,
    replace: Boolean = false,
    onSaved: ((Uri) -> Unit)? = null,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isVideo = remember(uri) { context.contentResolver.getType(uri)?.startsWith("video") == true }

    var stockId by remember { mutableStateOf(prefs.stock) }
    var sceneId by remember { mutableStateOf<String?>(null) }
    var recipeId by remember { mutableStateOf("") }
    var adjust by remember { mutableStateOf(Adjust.NONE) }
    var recipes by remember { mutableStateOf(prefs.recipes) }
    var ratio by remember { mutableStateOf<Ratio>(Ratio.Free) }
    var turned by remember { mutableStateOf(false) }
    var cropBox by remember { mutableStateOf(NRect.FULL) }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var overlay by remember { mutableStateOf(Overlay.PHI) }
    var turn by remember { mutableIntStateOf(0) }
    var tab by remember { mutableStateOf(Tab.FILM) }
    var comparing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<String?>(null) }
    var naming by remember { mutableStateOf(false) }
    var stampKey by remember { mutableIntStateOf(0) }
    var recipeName by remember { mutableStateOf("") }

    // photo state
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var straight by remember { mutableFloatStateOf(0f) }
    var savedLevel by remember { mutableStateOf<Float?>(null) }
    var developed by remember { mutableStateOf<Bitmap?>(null) }
    // video state
    var mediaW by remember { mutableIntStateOf(0) }
    var mediaH by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var startMs by remember { mutableLongStateOf(0L) }
    var endMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }

    val stock = Films.stock(stockId)
    val scene = sceneId?.let { Films.scene(it) }
    val look = Look.of(stock, scene, adjust)
    val shown = if (comparing) Look.of(stock, scene, adjust.copy(strength = 0f)) else look

    fun say(t: String) {
        notice = t
        scope.launch { delay(2400); if (notice == t) notice = null }
    }

    // ---- photo: decode a preview-sized copy and re-develop it whenever the look changes ----
    LaunchedEffect(uri) {
        if (!isVideo) {
            val bmp = withContext(Dispatchers.IO) { runCatching { Imaging.decode(context, uri, 1600) }.getOrNull() }
            if (bmp == null) { say("COULDN'T OPEN"); return@LaunchedEffect }
            original = Imaging.fitForGl(bmp, processor.maxTextureSize)
            savedLevel = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { Media.savedStraighten(ExifInterface(it)) } }.getOrNull()
            }
        }
    }
    // straightening makes a new source; the frame size follows it
    LaunchedEffect(original, straight) {
        val o = original ?: return@LaunchedEffect
        val st = withContext(Dispatchers.Default) { Imaging.straighten(o, straight) }
        source = st
        mediaW = st.width; mediaH = st.height
    }
    LaunchedEffect(source, stockId, sceneId, adjust) {
        val src = source ?: return@LaunchedEffect
        delay(16)
        val l = Look.of(Films.stock(stockId), sceneId?.let { Films.scene(it) }, adjust)
        developed = withContext(Dispatchers.Default) { processor.renderStill(src, l) }
    }

    // ---- video: a looping player with the film effect ----
    val lookRef = remember { LookRef(look) }
    lookRef.look = shown
    val player = remember(uri) {
        if (!isVideo) null else ExoPlayer.Builder(context).build().apply {
            setVideoEffects(listOf<Effect>(FilmGlEffect(lookRef)))
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        val p = player
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val rotated = videoSize.unappliedRotationDegrees % 180 != 0
                    val w = (videoSize.width * videoSize.pixelWidthHeightRatio).roundToInt()
                    mediaW = if (rotated) videoSize.height else w
                    mediaH = if (rotated) w else videoSize.height
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && durationMs == 0L && p != null && p.duration > 0) {
                    durationMs = p.duration; endMs = p.duration
                }
            }
        }
        p?.addListener(listener)
        onDispose { p?.removeListener(listener); p?.release() }
    }
    // keep playback inside the trim range, and refresh a paused frame when the look changes
    LaunchedEffect(player) {
        val p = player ?: return@LaunchedEffect
        while (true) {
            positionMs = p.currentPosition
            if (endMs > 0 && (positionMs >= endMs || positionMs < startMs)) p.seekTo(startMs)
            delay(50)
        }
    }
    LaunchedEffect(stockId, sceneId, adjust, comparing) { player?.let { if (!it.isPlaying) it.seekTo(it.currentPosition) } }

    fun cropTarget() = CropMath.target(ratio, mediaW.coerceAtLeast(1), mediaH.coerceAtLeast(1), turned)

    // a new shape starts from the biggest crop that fits
    LaunchedEffect(ratio, turned, mediaW > 0) {
        if (mediaW > 0) cropBox = CropMath.fit(mediaW, mediaH, cropTarget())
    }

    fun saved(where: Uri, message: String) {
        saving = false
        stampKey += 1
        say(message)
        onSaved?.let { done -> scope.launch { delay(900); done(where) } }
    }

    /** Puts a finished file where it belongs: over the original, or as a copy in the gallery. */
    fun copy(file: File, note: String) {
        scope.launch {
            val out = withContext(Dispatchers.IO) {
                runCatching { if (isVideo) Media.publishVideo(context, file) else Media.publishImage(context, file) }.getOrNull()
            }
            if (out != null) saved(out, note) else { saving = false; say("COULDN'T SAVE") }
        }
    }

    fun overwrite(file: File) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { Media.overwrite(context, uri, file) }.isSuccess }
            if (ok) saved(uri, "ORIGINAL REPLACED") else copy(file, "SAVED AS A COPY INSTEAD")
        }
    }

    val askWrite = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val f = pendingFile ?: return@rememberLauncherForActivityResult
        pendingFile = null
        if (res.resultCode == android.app.Activity.RESULT_OK) overwrite(f)
        else { f.delete(); saving = false; say("ORIGINAL KEPT, NOTHING SAVED") }
    }

    fun deliver(file: File) {
        if (!replace) {
            copy(file, if (isVideo) "SAVED TO MOVIES / SONASIGHT" else "SAVED TO PICTURES / SONASIGHT")
            return
        }
        scope.launch {
            val err = withContext(Dispatchers.IO) {
                try { Media.overwrite(context, uri, file); null } catch (e: Exception) { e }
            }
            when {
                err == null -> saved(uri, "ORIGINAL REPLACED")
                err is SecurityException -> {
                    // the gallery's file needs your OK before it can be changed
                    val sender = Media.writeRequest(context, uri, err)
                    if (sender != null) {
                        pendingFile = file
                        askWrite.launch(IntentSenderRequest.Builder(sender).build())
                    } else {
                        copy(file, "GALLERY SAID NO, SAVED A COPY")
                    }
                }
                else -> copy(file, "SAVED AS A COPY INSTEAD")
            }
        }
    }

    fun pickStock(s: FilmStock) {
        stockId = s.id; sceneId = null
        if (recipeId.isNotEmpty()) adjust = Adjust.NONE
        recipeId = ""
    }

    fun save() {
        if (saving) return
        saving = true
        progress = 0
        if (!isVideo) {
            val l = look
            val box = cropBox; val tilt = straight
            scope.launch {
                val file = withContext(Dispatchers.IO) {
                    runCatching {
                        var full = Imaging.decode(context, uri, processor.maxTextureSize)
                        if (tilt != 0f) {
                            val st = Imaging.straighten(full, tilt)
                            if (st !== full) full.recycle()
                            full = st
                        }
                        if (!box.isFull) full = Imaging.cropNormalized(full, box.l, box.t, box.r, box.b)
                        full = Imaging.fitForGl(full, processor.maxTextureSize)
                        val out = processor.renderStill(full, l)
                        full.recycle()
                        val base = context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
                        val info = ShotInfo(film = recipes.firstOrNull { it.id == recipeId }?.name ?: "${stock.name} ${stock.iso}", scene = scene?.name, focalMm = null, location = null)
                        val f = Media.writeJpeg(context, out, info, base = base)
                        out.recycle()
                        f
                    }.getOrNull()
                }
                if (file != null) deliver(file) else { saving = false; say("COULDN'T SAVE") }
            }
        } else {
            exportVideo(
                context = context, uri = uri, look = look, startMs = startMs,
                endMs = if (endMs > 0) endMs else durationMs,
                crop = if (cropBox.isFull) null else android.graphics.RectF(cropBox.l, cropBox.t, cropBox.r, cropBox.b),
                onProgress = { progress = it },
                onDone = { file -> if (file != null) deliver(file) else { saving = false; say("COULDN'T SAVE") } },
            )
        }
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            // top bar
            Row(
                Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextKey("BACK") { onClose() }
                Spacer(Modifier.weight(1f))
                Bilingual(if (isVideo) "EDIT MOTION" else "EDIT STILL", "編集", color = Ash, size = 10f)
                Spacer(Modifier.weight(1f))
                if (saving) {
                    BasicText(if (isVideo) "$progress%" else "…", style = Numeral.copy(color = Gold), modifier = Modifier.padding(horizontal = 12.dp))
                } else {
                    TextKey(if (replace) "SAVE" else "SAVE COPY", active = true) { save() }
                }
            }

            // preview
            BoxWithConstraints(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (mediaW > 0 && mediaH > 0) {
                    val ratio = mediaW.toFloat() / mediaH
                    Box(
                        Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = maxWidth / maxHeight > ratio)
                            .pointerInput(tab) {
                                if (tab == Tab.CROP) {
                                    // corners resize, edges resize when free, inside moves, two fingers zoom
                                    awaitEachGesture {
                                        val down = awaitFirstDown()
                                        val vw = size.width.toFloat()
                                        val vh = size.height.toFloat()
                                        var grab = CropMath.grab(cropBox, down.position, vw, vh, 30.dp.toPx(), ratio == Ratio.Free)
                                        var pinched = false
                                        do {
                                            val ev = awaitPointerEvent()
                                            val fingers = ev.changes.count { it.pressed }
                                            if (fingers >= 2) {
                                                pinched = true
                                                val z = ev.calculateZoom()
                                                val pan = ev.calculatePan()
                                                var c = CropMath.zoom(cropBox, z, mediaW, mediaH, cropTarget())
                                                c = CropMath.move(c, pan.x / vw, pan.y / vh)
                                                cropBox = c
                                            } else if (fingers == 1 && !pinched) {
                                                val ch = ev.changes.first { it.pressed }
                                                val d = ch.positionChange()
                                                val p = Offset((ch.position.x / vw).coerceIn(0f, 1f), (ch.position.y / vh).coerceIn(0f, 1f))
                                                cropBox = when (val g = grab) {
                                                    is Grab.Corner -> CropMath.corner(cropBox, g.i, p, mediaW, mediaH, cropTarget())
                                                    is Grab.Edge -> CropMath.edge(cropBox, g.i, p)
                                                    Grab.Inside -> CropMath.move(cropBox, d.x / vw, d.y / vh)
                                                }
                                            }
                                            ev.changes.forEach { if (it.positionChange() != Offset.Zero) it.consume() }
                                        } while (ev.changes.any { it.pressed })
                                        grab = Grab.Inside
                                    }
                                } else {
                                    detectTapGestures(onPress = {
                                        comparing = true
                                        tryAwaitRelease()
                                        comparing = false
                                    })
                                }
                            },
                    ) {
                        if (player != null) {
                            AndroidView(
                                factory = { ctx -> SurfaceView(ctx).also { player.setVideoSurfaceView(it) } },
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            val img = if (comparing) source else developed ?: source
                            img?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
                        }
                        Canvas(Modifier.fillMaxSize()) {
                            val r = cropBox
                            val frame = Rect(r.l * size.width, r.t * size.height, r.r * size.width, r.b * size.height)
                            val full = Rect(0f, 0f, size.width, size.height)
                            val hair = 1.dp.toPx()
                            if (!r.isFull || tab == Tab.CROP) {
                                val p = Path().apply { fillType = PathFillType.EvenOdd; addRect(full); addRect(frame) }
                                drawPath(p, Ink.copy(alpha = if (tab == Tab.CROP) 0.6f else 0.85f))
                                drawRect(Mist.copy(alpha = 0.6f), frame.topLeft, frame.size, style = Stroke(hair))
                            }
                            if (tab == Tab.CROP) {
                                // gold corner handles, and edge marks when the shape is free
                                val arm = 18.dp.toPx()
                                val th = 3.dp.toPx()
                                val corners = listOf(
                                    Triple(frame.topLeft, 1f, 1f), Triple(frame.topRight, -1f, 1f),
                                    Triple(frame.bottomRight, -1f, -1f), Triple(frame.bottomLeft, 1f, -1f),
                                )
                                for ((o, sx, sy) in corners) {
                                    drawLine(Gold, o, Offset(o.x + sx * arm, o.y), th)
                                    drawLine(Gold, o, Offset(o.x, o.y + sy * arm), th)
                                }
                                if (ratio == Ratio.Free) {
                                    val m = 10.dp.toPx()
                                    val c = frame.center
                                    drawLine(Gold, Offset(c.x - m, frame.top), Offset(c.x + m, frame.top), th)
                                    drawLine(Gold, Offset(c.x - m, frame.bottom), Offset(c.x + m, frame.bottom), th)
                                    drawLine(Gold, Offset(frame.left, c.y - m), Offset(frame.left, c.y + m), th)
                                    drawLine(Gold, Offset(frame.right, c.y - m), Offset(frame.right, c.y + m), th)
                                }
                                val guide = Gold.copy(alpha = 0.8f)
                                when (overlay) {
                                    Overlay.PHI -> phiGrid(frame, guide, hair)
                                    Overlay.SPIRAL -> goldenSpiral(frame, turn, guide, hair)
                                    Overlay.TRIANGLES -> goldenTriangles(frame, turn, guide, hair)
                                    Overlay.NONE -> Unit
                                }
                            }
                        }
                        if (comparing) {
                            BasicText(
                                "ORIGINAL", style = Label.copy(color = Bone),
                                modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                                    .background(Ink.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                HankoStamp(stampKey, Modifier.align(Alignment.TopEnd).padding(14.dp))
                notice?.let {
                    BasicText(
                        it, style = Label.copy(color = Bone, letterSpacing = 3.sp),
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
                            .background(Ink.copy(alpha = 0.75f)).padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }

            // tool area
            Column(
                Modifier.fillMaxWidth().height(272.dp)
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Gold.copy(alpha = 0.07f), Color.Transparent)))
                    .padding(top = 8.dp),
            ) {
                when (tab) {
                    Tab.FILM -> {
                        SectionLabel("scene", "場面", Modifier.padding(start = 24.dp, top = 4.dp))
                        SceneDial(sceneId ?: "") { id ->
                            val s = Films.scene(id)
                            sceneId = id; stockId = s.stockId; recipeId = ""; adjust = Adjust.NONE
                        }
                        Spacer(Modifier.height(10.dp))
                        SectionLabel("film", "フィルム", Modifier.padding(start = 24.dp))
                        FilmStrip(
                            recipes = recipes,
                            selectedStock = stockId,
                            selectedRecipe = recipeId,
                            onStock = { pickStock(it) },
                            onRecipe = { r -> stockId = r.stockId; adjust = r.adjust; recipeId = r.id; sceneId = null },
                        )
                        Spacer(Modifier.weight(1f))
                        BasicText(
                            "press and hold the picture to see the original",
                            style = Body, modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                        )
                    }
                    Tab.TUNE -> {
                        TuneSliders(adjust, showExposure = true) { adjust = it }
                        Spacer(Modifier.weight(1f))
                        RecipeBar(
                            naming = naming, name = recipeName, onName = { recipeName = it.take(24) },
                            onReset = { adjust = Adjust.NONE }, onStartNaming = { naming = true },
                            onSave = {
                                val r = Recipe(UUID.randomUUID().toString(), recipeName.trim(), stockId, adjust)
                                recipes = recipes + r; prefs.recipes = recipes; recipeId = r.id
                                naming = false; recipeName = ""
                                say("SAVED  ${r.name.uppercase()}")
                            },
                            onDone = null,
                        )
                    }
                    Tab.CROP -> {
                        LazyRow(
                            Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            items(RATIOS) { a ->
                                val label = if (a is Ratio.Fixed) {
                                    val landscape = (mediaW >= mediaH) != turned
                                    a.aspect.label(upright = !landscape)
                                } else a.label()
                                TextKey(label, active = a == ratio) { ratio = a }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            val canTurn = ratio is Ratio.Fixed && (ratio as Ratio.Fixed).aspect != Aspect.R1_1
                            if (canTurn) TextKey("ROTATE FRAME", active = turned) { turned = !turned }
                            TextKey("RESET") { cropBox = CropMath.fit(mediaW, mediaH, cropTarget()); if (ratio == Ratio.Free) cropBox = NRect.FULL }
                            Spacer(Modifier.weight(1f))
                            TextKey(overlay.label, active = overlay != Overlay.NONE) {
                                overlay = Overlay.entries[(overlay.ordinal + 1) % Overlay.entries.size]
                            }
                            if (overlay == Overlay.SPIRAL || overlay == Overlay.TRIANGLES) {
                                TextKey("TURN") { turn = (turn + 1) % 4 }
                            }
                        }
                        if (!isVideo) {
                            FineSlider("STRAIGHTEN", straight, -10f..10f, 0f, { signed(it, 1) + "°" }) { straight = it }
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                                val saved = savedLevel
                                if (saved != null) {
                                    TextKey("AUTO LEVEL", active = kotlin.math.abs(straight - saved) < 0.05f) {
                                        straight = saved.coerceIn(-10f, 10f)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        BasicText(
                            "drag a corner to resize, drag inside to move, pinch to zoom",
                            style = Body, modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                        )
                    }
                    Tab.TRIM -> {
                        TrimBar(
                            durationMs = durationMs, startMs = startMs, endMs = endMs, positionMs = positionMs,
                            onStart = { startMs = it.coerceIn(0L, endMs - 300L); player?.seekTo(startMs) },
                            onEnd = { endMs = it.coerceIn(startMs + 300L, durationMs); player?.seekTo((endMs - 1500L).coerceAtLeast(startMs)) },
                        )
                        Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                            BasicText(clockMs(startMs), style = Numeral.copy(color = Mist, fontSize = 11.sp))
                            Spacer(Modifier.weight(1f))
                            BasicText(clockMs(endMs - startMs), style = Numeral.copy(color = Gold, fontSize = 11.sp))
                            Spacer(Modifier.weight(1f))
                            BasicText(clockMs(endMs), style = Numeral.copy(color = Mist, fontSize = 11.sp))
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            TextKey(if (playing) "PAUSE" else "PLAY", active = true) {
                                playing = !playing
                                player?.playWhenReady = playing
                            }
                        }
                    }
                }
            }

            // tabs
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Tab.entries.filter { isVideo || it != Tab.TRIM }.forEach { t ->
                    Column(
                        Modifier.clickable { tab = t }.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(4.dp).clip(CircleShape).background(if (t == tab) Gold else Color.Transparent))
                        Spacer(Modifier.height(5.dp))
                        Bilingual(t.label, t.jp, color = if (t == tab) Bone else Ash, size = 10f)
                    }
                }
            }
        }
    }
}

private fun clockMs(ms: Long): String {
    val t = ms.coerceAtLeast(0L)
    val s = t / 1000
    return "%d:%02d.%d".format(s / 60, s % 60, (t % 1000) / 100)
}

/** Horizontal strip of recipes, films and MEISHO films. */
@Composable
private fun FilmStrip(
    recipes: List<Recipe>,
    selectedStock: String,
    selectedRecipe: String,
    onStock: (FilmStock) -> Unit,
    onRecipe: (Recipe) -> Unit,
) {
    LazyRow(
        Modifier.fillMaxWidth().height(64.dp),
        contentPadding = PaddingValues(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(recipes, key = { "r" + it.id }) { r ->
            StripItem(r.name.uppercase(), "RECIPE", r.id == selectedRecipe) { onRecipe(r) }
        }
        if (recipes.isNotEmpty()) item { StripDivider() }
        items(Films.stocks, key = { "s" + it.id }) { s ->
            StripItem(s.name, s.iso, selectedRecipe.isEmpty() && s.id == selectedStock) { onStock(s) }
        }
        item { StripDivider("MEISHO") }
        items(Films.meisho, key = { "m" + it.id }) { s ->
            StripItem(s.name, s.iso, selectedRecipe.isEmpty() && s.id == selectedStock) { onStock(s) }
        }
    }
}

@Composable
private fun StripItem(name: String, badge: String, on: Boolean, onTap: () -> Unit) {
    Column(
        Modifier.clickable { onTap() }.padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(name, style = Label.copy(color = if (on) Bone else Mist, fontSize = 11.sp))
        Spacer(Modifier.height(3.dp))
        BasicText(badge, style = Numeral.copy(color = if (on) Gold else Ash, fontSize = 10.sp))
        Spacer(Modifier.height(4.dp))
        Box(Modifier.width(18.dp).height(1.dp).background(if (on) Gold else Color.Transparent))
    }
}

@Composable
private fun StripDivider(label: String? = null) {
    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(1.dp).height(28.dp).background(Slate))
        if (label != null) {
            Spacer(Modifier.width(10.dp))
            BasicText("// " + label.lowercase() + "  名所", style = Numeral.copy(color = Gold, fontSize = 10.sp))
        }
    }
}

/** Two gold handles on a hairline for the in and out points. */
@Composable
private fun TrimBar(
    durationMs: Long,
    startMs: Long,
    endMs: Long,
    positionMs: Long,
    onStart: (Long) -> Unit,
    onEnd: (Long) -> Unit,
) {
    var dragging by remember { mutableIntStateOf(0) } // -1 start, 1 end
    Canvas(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 28.dp)
            .pointerInput(durationMs) {
                if (durationMs <= 0) return@pointerInput
                fun at(x: Float) = ((x / size.width).coerceIn(0f, 1f) * durationMs).toLong()
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        val sx = startMs.toFloat() / durationMs * size.width
                        val ex = endMs.toFloat() / durationMs * size.width
                        dragging = if (kotlin.math.abs(o.x - sx) < kotlin.math.abs(o.x - ex)) -1 else 1
                    },
                    onDragEnd = { dragging = 0 },
                    onHorizontalDrag = { ch, _ ->
                        ch.consume()
                        if (dragging < 0) onStart(at(ch.position.x)) else onEnd(at(ch.position.x))
                    },
                )
            },
    ) {
        if (durationMs <= 0) return@Canvas
        val y = size.height / 2
        val sx = startMs.toFloat() / durationMs * size.width
        val ex = endMs.toFloat() / durationMs * size.width
        val px = positionMs.toFloat() / durationMs * size.width
        drawLine(Ash, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        drawLine(Gold, Offset(sx, y), Offset(ex, y), 2.dp.toPx())
        for (x in floatArrayOf(sx, ex)) {
            drawLine(Gold, Offset(x, y - 14.dp.toPx()), Offset(x, y + 14.dp.toPx()), 2.dp.toPx())
        }
        drawCircle(Bone, 3.dp.toPx(), Offset(px, y))
    }
}

/** Exports a trimmed, cropped, film-developed copy of a video to a temp file. */
@OptIn(UnstableApi::class)
private fun exportVideo(
    context: android.content.Context,
    uri: Uri,
    look: Look,
    startMs: Long,
    endMs: Long,
    crop: android.graphics.RectF?,
    onProgress: (Int) -> Unit,
    onDone: (File?) -> Unit,
) {
    val out = File(context.cacheDir, Media.stamp() + ".mp4")
    val handler = android.os.Handler(android.os.Looper.getMainLooper())
    var finished = false
    lateinit var transformer: Transformer
    transformer = Transformer.Builder(context)
        .setVideoMimeType(MimeTypes.VIDEO_H264)
        .addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                finished = true
                onDone(out)
            }

            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                finished = true
                out.delete()
                onDone(null)
            }
        })
        .build()

    val clip = MediaItem.ClippingConfiguration.Builder()
        .setStartPositionMs(startMs)
        .setEndPositionMs(endMs)
        .build()
    val item = MediaItem.Builder().setUri(uri).setClippingConfiguration(clip).build()
    val effects = ArrayList<Effect>()
    if (crop != null) {
        // Crop takes normalised device coordinates: -1..1, with y pointing up
        effects += Crop(crop.left * 2f - 1f, crop.right * 2f - 1f, 1f - crop.bottom * 2f, 1f - crop.top * 2f)
    }
    effects += FilmGlEffect(LookRef(look))
    val edited = EditedMediaItem.Builder(item).setEffects(Effects(listOf(), effects)).build()
    transformer.start(edited, out.absolutePath)

    val holder = ProgressHolder()
    handler.post(object : Runnable {
        override fun run() {
            if (finished) return
            if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress)
            handler.postDelayed(this, 250)
        }
    })
}
