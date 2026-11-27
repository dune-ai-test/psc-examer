package com.padanam.psc.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.padanam.psc.AppContainer
import com.padanam.psc.data.model.ScanMode
import com.padanam.psc.ui.components.TopSafeArea
import com.padanam.psc.ui.theme.AppType
import com.padanam.psc.ui.theme.Danger
import com.padanam.psc.ui.theme.DarkBackground
import com.padanam.psc.ui.theme.DarkSurface
import com.padanam.psc.ui.theme.Indigo
import com.padanam.psc.ui.theme.OnDark
import com.padanam.psc.util.ImageUtils
import com.padanam.psc.util.PreparedImage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MODE_SINGLE = "single"
private const val MODE_MULTI = "multi"
private const val MODE_BATCH = "batch"
private const val MAX_PAGES = 8

/** Keeps the captured-page strip across rotation and process death. */
private val CapturedImagesSaver = listSaver<List<PreparedImage>, String>(
    save = { images ->
        images.flatMap { listOf(it.path, it.displayName, it.sizeBytes.toString(), it.source) }
    },
    restore = { saved ->
        saved.chunked(4).mapNotNull { parts ->
            if (parts.size < 4) {
                null
            } else {
                PreparedImage(
                    path = parts[0],
                    displayName = parts[1],
                    sizeBytes = parts[2].toLongOrNull() ?: 0L,
                    source = parts[3]
                )
            }
        }
    }
)

/**
 * Camera-first capture screen. The viewfinder is a live CameraX preview with
 * the mockup's corner brackets and sweeping scan line drawn on top. SINGLE
 * captures one page, MULTI collects up to eight pages for one set, and BATCH
 * opens the system photo picker for a bulk import.
 */
@Composable
fun ScanCameraScreen(
    container: AppContainer,
    openGalleryOnStart: Boolean,
    initialMode: String = ScanMode.SET,
    onClose: () -> Unit,
    onImagesReady: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var mode by rememberSaveable { mutableStateOf(MODE_SINGLE) }
    var scanMode by rememberSaveable { mutableStateOf(ScanMode.normalize(initialMode)) }
    var captured by rememberSaveable(stateSaver = CapturedImagesSaver) {
        mutableStateOf<List<PreparedImage>>(emptyList())
    }
    var flashOn by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var fatalCameraError by remember { mutableStateOf<String?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var askedGallery by rememberSaveable { mutableStateOf(false) }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    fun finish(images: List<PreparedImage>) {
        if (images.isEmpty() || submitted) return
        submitted = true
        container.scanSession.prepare(images, scanMode)
        onImagesReady()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) {
            cameraError = "Camera permission is needed to photograph a question."
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PAGES)
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val hadCaptured = captured.isNotEmpty()
        scope.launch {
            importing = true
            cameraError = null
            val imported = withContext(Dispatchers.IO) {
                ImageUtils.importFromUris(context, uris)
            }
            importing = false
            if (imported.isEmpty()) {
                cameraError = "Those photos could not be opened. Try different images."
                return@launch
            }
            val combined = (captured + imported).take(MAX_PAGES)
            captured = combined
            if (!hadCaptured) finish(combined)
        }
    }

    fun launchGallery() {
        runCatching {
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }.onFailure {
            cameraError = "No gallery app was found on this device."
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(openGalleryOnStart) {
        if (openGalleryOnStart && !askedGallery) {
            askedGallery = true
            delay(280)
            launchGallery()
        }
    }

    // Returning from a cancelled analysis can leave paths whose cache copies
    // were deleted, so drop dead entries before the strip is shown again.
    LaunchedEffect(Unit) {
        if (captured.isEmpty()) return@LaunchedEffect
        val alive = withContext(Dispatchers.IO) {
            captured.filter { image -> runCatching { File(image.path).exists() }.getOrDefault(false) }
        }
        if (alive.size != captured.size) captured = alive
    }

    DisposableEffect(hasPermission, lensFacing) {
        if (!hasPermission) return@DisposableEffect onDispose { }
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        val executor = ContextCompat.getMainExecutor(context)

        future.addListener({
            if (disposed) return@addListener
            runCatching {
                val resolved = future.get()
                provider = resolved
                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(
                        if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                    )
                    .build()
                val selector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()
                resolved.unbindAll()
                val bound = resolved.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    capture
                )
                imageCapture = capture
                camera = bound
                cameraError = null
                fatalCameraError = null
                runCatching { bound.cameraControl.enableTorch(flashOn) }
            }.onFailure {
                imageCapture = null
                camera = null
                fatalCameraError = "The camera could not start. Import photos from your gallery instead."
            }
        }, executor)

        onDispose {
            disposed = true
            imageCapture = null
            camera = null
            runCatching { provider?.unbindAll() }
        }
    }

    LaunchedEffect(flashOn, camera) {
        imageCapture?.flashMode =
            if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        runCatching { camera?.cameraControl?.enableTorch(flashOn) }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!submitted) {
                captured.forEach { image -> runCatching { File(image.path).delete() } }
            }
        }
    }

    fun capturePhoto() {
        if (capturing || importing || submitted) return
        if (captured.size >= MAX_PAGES) {
            cameraError = "That is already $MAX_PAGES pages. Analyse them or remove one first."
            return
        }
        val capture = imageCapture
        if (capture == null) {
            cameraError = "The camera is not ready yet. Try again in a moment."
            return
        }
        val file = ImageUtils.newCaptureFile(context)
        capturing = true
        cameraError = null
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    capturing = false
                    val prepared = PreparedImage(
                        path = file.absolutePath,
                        displayName = file.name,
                        sizeBytes = file.length(),
                        source = "scan"
                    )
                    val next = (captured + prepared).take(MAX_PAGES)
                    captured = next
                    if (mode != MODE_MULTI) finish(next)
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    runCatching { file.delete() }
                    cameraError = exception.message ?: "The photo could not be saved. Try again."
                }
            }
        )
    }

    fun removeCaptured(image: PreparedImage) {
        captured = captured.filterNot { it.path == image.path }
        runCatching { File(image.path).delete() }
    }

    val hint = when (mode) {
        MODE_MULTI -> "Page ${(captured.size + 1).coerceAtMost(MAX_PAGES)} of up to $MAX_PAGES"
        MODE_BATCH -> "Batch import · choose up to $MAX_PAGES photos"
        else -> if (scanMode == ScanMode.DECK) "Question and answer in frame" else "All options in frame"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        TopSafeArea()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .height(40.dp)
        ) {
            CameraCircleButton(
                icon = Icons.Rounded.Close,
                contentDescription = "Close scanner",
                onClick = onClose,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            Text(
                if (scanMode == ScanMode.DECK) "SCAN DECK CARDS" else "SCAN A QUESTION",
                style = AppType.Eyebrow.copy(letterSpacing = 1.2.sp),
                color = OnDark.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.Center)
            )
            CameraCircleButton(
                icon = if (flashOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                contentDescription = if (flashOn) "Turn flash off" else "Turn flash on",
                onClick = { flashOn = !flashOn },
                active = flashOn,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(OnDark.copy(alpha = 0.08f))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            ScanTypeChip(
                text = "Question set",
                selected = scanMode == ScanMode.SET,
                onClick = { scanMode = ScanMode.SET },
                modifier = Modifier.weight(1f)
            )
            ScanTypeChip(
                text = "Deck cards",
                selected = scanMode == ScanMode.DECK,
                onClick = { scanMode = ScanMode.DECK },
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp)
                .padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(Indigo)
                    )
                    Text(
                        hint,
                        style = AppType.BodySmall,
                        color = OnDark.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(OnDark.copy(alpha = 0.1f))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (hasPermission && fatalCameraError == null) Indigo else Danger
                            )
                    )
                    Text(
                        if (!hasPermission || fatalCameraError != null) "PAUSED" else "AUTO",
                        style = AppType.MonoChip.copy(fontSize = 8.sp, letterSpacing = 0.9.sp),
                        color = OnDark.copy(alpha = 0.7f)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(DarkSurface)
            ) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                ViewfinderOverlay(active = hasPermission && fatalCameraError == null)

                if (!hasPermission || fatalCameraError != null) {
                    CameraFallback(
                        title = if (!hasPermission) "Camera access needed" else "Camera unavailable",
                        body = fatalCameraError
                            ?: "Allow camera access to photograph a question sheet. You can also import photos from your gallery.",
                        allowCamera = if (!hasPermission) {
                            { permissionLauncher.launch(Manifest.permission.CAMERA) }
                        } else {
                            null
                        },
                        onImport = { launchGallery() }
                    )
                }

                if (importing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(DarkBackground.copy(alpha = 0.72f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                color = OnDark,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                "Opening photos…",
                                style = AppType.BodySmall,
                                color = OnDark
                            )
                        }
                    }
                }

                if (captured.size > 1) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(14.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(DarkBackground.copy(alpha = 0.72f))
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Layers,
                            null,
                            tint = OnDark,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            "${captured.size} PAGES",
                            style = AppType.MonoChip.copy(fontSize = 7.5.sp, letterSpacing = 0.7.sp),
                            color = OnDark
                        )
                    }
                }

                if (capturing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(DarkBackground.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = OnDark,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }

            if (captured.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(captured, key = { it.path }) { image ->
                        CapturedThumb(
                            image = image,
                            onRemove = { removeCaptured(image) }
                        )
                    }
                    if (captured.size < MAX_PAGES) {
                        item {
                            Box(
                                modifier = Modifier
                                    .size(54.dp, 66.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(OnDark.copy(alpha = 0.06f))
                                    .border(
                                        1.dp,
                                        OnDark.copy(alpha = 0.14f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { launchGallery() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Image,
                                    contentDescription = "Add from gallery",
                                    tint = OnDark.copy(alpha = 0.65f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            ModeBar(
                mode = mode,
                onSelect = { next ->
                    mode = next
                    if (next == MODE_BATCH && captured.isEmpty()) launchGallery()
                }
            )

            if (captured.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(OnDark)
                        .clickable(enabled = !importing && !capturing) {
                            finish(captured)
                        }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Rounded.PhotoCamera,
                        null,
                        tint = DarkBackground,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Analyse ${captured.size} page${if (captured.size == 1) "" else "s"}",
                        style = AppType.Button.copy(fontSize = 13.5.sp),
                        color = DarkBackground
                    )
                }
            }

            if (cameraError != null && hasPermission) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(OnDark.copy(alpha = 0.07f))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.ErrorOutline,
                        null,
                        tint = Color(0xFFF5B33F),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        cameraError ?: "",
                        style = AppType.BodyTiny,
                        color = OnDark.copy(alpha = 0.72f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 38.dp)
                .padding(top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CameraCircleButton(
                icon = Icons.Rounded.Image,
                contentDescription = "Import from gallery",
                onClick = { launchGallery() },
                size = 46.dp,
                square = true
            )

            ShutterButton(
                count = if (mode == MODE_MULTI) captured.size else 0,
                enabled = !capturing && !importing && !submitted,
                onClick = { capturePhoto() }
            )

            CameraCircleButton(
                icon = Icons.Rounded.Refresh,
                contentDescription = "Switch camera",
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                },
                size = 46.dp,
                square = true
            )
        }

        Text(
            "Keep the page flat · English and Malayalam are read together",
            style = AppType.MonoSmall.copy(letterSpacing = 0.2.sp),
            color = OnDark.copy(alpha = 0.35f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun ScanTypeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) OnDark else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = AppType.Button.copy(fontSize = 11.5.sp),
            color = if (selected) DarkBackground else OnDark.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun CameraCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    square: Boolean = false,
    active: Boolean = false
) {
    val shape = if (square) RoundedCornerShape(12.dp) else CircleShape
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(
                if (active) OnDark.copy(alpha = 0.22f) else OnDark.copy(alpha = 0.1f)
            )
            .border(
                1.dp,
                if (active) OnDark.copy(alpha = 0.35f) else OnDark.copy(alpha = 0.16f),
                shape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription,
            tint = if (active) OnDark else OnDark.copy(alpha = 0.78f),
            modifier = Modifier.size(size * 0.4f)
        )
    }
}

@Composable
private fun ViewfinderOverlay(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "scan")
    val sweep by transition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanline"
    )
    val accent = Indigo

    Canvas(modifier = Modifier.fillMaxSize()) {
        val inset = 12.dp.toPx()
        val arm = 22.dp.toPx()
        val stroke = 2.dp.toPx()
        val corner = OnDark.copy(alpha = 0.85f)
        val left = inset
        val top = inset
        val right = size.width - inset
        val bottom = size.height - inset

        // Four L-shaped corner brackets, exactly like the mockup.
        drawLine(corner, Offset(left, top), Offset(left + arm, top), stroke, StrokeCap.Round)
        drawLine(corner, Offset(left, top), Offset(left, top + arm), stroke, StrokeCap.Round)
        drawLine(corner, Offset(right, top), Offset(right - arm, top), stroke, StrokeCap.Round)
        drawLine(corner, Offset(right, top), Offset(right, top + arm), stroke, StrokeCap.Round)
        drawLine(corner, Offset(left, bottom), Offset(left + arm, bottom), stroke, StrokeCap.Round)
        drawLine(corner, Offset(left, bottom), Offset(left, bottom - arm), stroke, StrokeCap.Round)
        drawLine(corner, Offset(right, bottom), Offset(right - arm, bottom), stroke, StrokeCap.Round)
        drawLine(corner, Offset(right, bottom), Offset(right, bottom - arm), stroke, StrokeCap.Round)

        // Rule-of-thirds guides, kept very faint so the page stays readable.
        val guide = OnDark.copy(alpha = 0.08f)
        drawLine(guide, Offset(size.width / 3f, top), Offset(size.width / 3f, bottom), 1.dp.toPx())
        drawLine(guide, Offset(size.width * 2f / 3f, top), Offset(size.width * 2f / 3f, bottom), 1.dp.toPx())
        drawLine(guide, Offset(left, size.height / 3f), Offset(right, size.height / 3f), 1.dp.toPx())
        drawLine(guide, Offset(left, size.height * 2f / 3f), Offset(right, size.height * 2f / 3f), 1.dp.toPx())

        if (active) {
            val y = top + (bottom - top) * sweep
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, accent.copy(alpha = 0.18f), Color.Transparent),
                    startY = y - 20.dp.toPx(),
                    endY = y + 20.dp.toPx()
                ),
                topLeft = Offset(left, (y - 20.dp.toPx()).coerceAtLeast(top)),
                size = androidx.compose.ui.geometry.Size(
                    width = right - left,
                    height = 40.dp.toPx()
                )
            )
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, accent, Color(0xFF9C90FF), accent, Color.Transparent)
                ),
                start = Offset(left + 6.dp.toPx(), y),
                end = Offset(right - 6.dp.toPx(), y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun CameraFallback(
    title: String,
    body: String,
    allowCamera: (() -> Unit)?,
    onImport: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(OnDark.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.PhotoCamera,
                    null,
                    tint = OnDark,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                title,
                style = AppType.SectionTitle,
                color = OnDark,
                textAlign = TextAlign.Center
            )
            Text(
                body,
                style = AppType.BodySmall,
                color = OnDark.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            if (allowCamera != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(13.dp))
                        .background(OnDark)
                        .clickable(onClick = allowCamera)
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Allow camera",
                        style = AppType.ButtonSmall,
                        color = DarkBackground
                    )
                }
            }
            Text(
                "Import photos instead",
                style = AppType.LabelSmallStrong,
                color = OnDark.copy(alpha = 0.75f),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onImport)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun ModeBar(
    mode: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Top
    ) {
        listOf(
            MODE_SINGLE to "SINGLE",
            MODE_MULTI to "MULTI",
            MODE_BATCH to "BATCH"
        ).forEach { (value, label) ->
            val selected = mode == value
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    label,
                    style = AppType.MonoChip.copy(
                        fontSize = 9.sp,
                        letterSpacing = 0.9.sp
                    ),
                    color = if (selected) OnDark else OnDark.copy(alpha = 0.4f)
                )
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .width(if (selected) 22.dp else 0.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Indigo)
                )
            }
        }
    }
}

@Composable
private fun ShutterButton(
    count: Int,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(78.dp)
            .clip(CircleShape)
            .background(OnDark.copy(alpha = 0.15f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(if (enabled) OnDark else OnDark.copy(alpha = 0.55f))
        )
        if (count > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(23.dp)
                    .clip(CircleShape)
                    .background(Indigo),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$count",
                    style = AppType.MonoValue.copy(fontSize = 9.sp),
                    color = OnDark
                )
            }
        }
    }
}

@Composable
private fun CapturedThumb(
    image: PreparedImage,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(54.dp, 66.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
    ) {
        AsyncImage(
            model = File(image.path),
            contentDescription = image.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(17.dp)
                .clip(CircleShape)
                .background(DarkBackground.copy(alpha = 0.78f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Remove page",
                tint = OnDark,
                modifier = Modifier.size(10.dp)
            )
        }
    }
}
