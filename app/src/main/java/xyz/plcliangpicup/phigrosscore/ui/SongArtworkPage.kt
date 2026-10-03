package xyz.plcliangpicup.phigrosscore.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.plcliangpicup.phigrosscore.data.SongScoreResult

@Composable
internal fun SongArtworkPage(song: SongScoreResult, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val visibility = remember { MutableTransitionState(false).apply { targetState = true } }
    var artwork by remember(song.songId) { mutableStateOf<Drawable?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val close = { visibility.targetState = false }
    LaunchedEffect(visibility.isIdle, visibility.currentState, visibility.targetState) {
        if (visibility.isIdle && !visibility.currentState && !visibility.targetState) onDismiss()
    }
    val save: () -> Unit = {
        if (!saving && artwork != null) {
            val drawable = artwork!!
            saving = true
            scope.launch {
                val message = try {
                    withContext(Dispatchers.IO) {
                        val temporary = File.createTempFile("artwork-", ".png", context.cacheDir)
                        try {
                            temporary.outputStream().use { output ->
                                check(drawable.toBitmap().compress(Bitmap.CompressFormat.PNG, 100, output))
                            }
                            saveB30ImageToGallery(context, temporary, "Phi-Artwork-${System.currentTimeMillis()}.png")
                        } finally {
                            temporary.delete()
                        }
                    }
                    "已保存到相册"
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    "保存失败，请重试"
                } finally {
                    saving = false
                }
                snackbar.showSnackbar(message)
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) save() else scope.launch { snackbar.showSnackbar("允许存储权限后可保存至相册") }
    }
    val request = remember(song.songId, attempt) {
        ImageRequest.Builder(context)
            .data(fullIllustrationUrl(song.songId))
            .size(Size.ORIGINAL)
            .allowHardware(false)
            .memoryCacheKey("illustration-original-${song.songId}")
            .diskCacheKey("illustration-full-${song.songId}")
            .placeholderMemoryCacheKey("illustration-preview-v2-${song.songId}")
            .crossfade(240)
            .build()
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
    )) {
        AnimatedVisibility(
            visibleState = visibility,
            enter = fadeIn(appTween(220)) + scaleIn(appTween(280), initialScale = .94f),
            exit = fadeOut(appTween(160)) + scaleOut(appTween(180), targetScale = .97f),
        ) {
            Column(Modifier.fillMaxSize().background(Color(0xFF0B0D12)).safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = close) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回单曲", tint = Color.White)
                    }
                    Text(song.songName, color = Color.White, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
                Box(Modifier.fillMaxWidth().weight(1f).clipToBounds(), contentAlignment = Alignment.Center) {
                    key(attempt) {
                    AsyncImage(
                        model = request,
                        contentDescription = "${song.songName} 完整曲绘",
                        contentScale = ContentScale.Fit,
                        onSuccess = { artwork = it.result.drawable; failed = false },
                        onError = { artwork = null; failed = true },
                        modifier = Modifier.fillMaxSize()
                            .pointerInput(song.songId) {
                                detectTapGestures(onDoubleTap = {
                                    scope.launch { scale.animateTo(1f, appTween(180)) }
                                    scope.launch { offsetX.animateTo(0f, appTween(180)) }
                                    scope.launch { offsetY.animateTo(0f, appTween(180)) }
                                })
                            }
                            .pointerInput(song.songId) {
                                detectTransformGestures { centroid, pan, zoom, _ ->
                                    val oldScale = scale.value
                                    val next = (oldScale * zoom).coerceIn(1f, 6f)
                                    val ratio = next / oldScale
                                    val x = ((offsetX.value - (centroid.x - size.width / 2f)) * ratio +
                                        centroid.x - size.width / 2f + pan.x)
                                        .coerceIn(-size.width * (next - 1f) / 2f, size.width * (next - 1f) / 2f)
                                    val y = ((offsetY.value - (centroid.y - size.height / 2f)) * ratio +
                                        centroid.y - size.height / 2f + pan.y)
                                        .coerceIn(-size.height * (next - 1f) / 2f, size.height * (next - 1f) / 2f)
                                    scope.launch { scale.snapTo(next); offsetX.snapTo(x); offsetY.snapTo(y) }
                                }
                            }
                            .graphicsLayer {
                                scaleX = scale.value; scaleY = scale.value
                                translationX = offsetX.value; translationY = offsetY.value
                            },
                    )
                    }
                    if (failed) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("曲绘加载失败", color = Color.White)
                            TextButton(onClick = { failed = false; attempt++ }) { Text("重新加载") }
                        }
                    } else if (artwork == null) {
                        CircularProgressIndicator(Modifier.size(32.dp), color = Color.White)
                    }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(context,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                                permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            } else save()
                        },
                        enabled = artwork != null && !saving,
                        modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                    ) {
                        if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (saving) "正在保存" else "保存至相册")
                    }
                    SnackbarHost(snackbar)
                }
            }
        }
    }
}
