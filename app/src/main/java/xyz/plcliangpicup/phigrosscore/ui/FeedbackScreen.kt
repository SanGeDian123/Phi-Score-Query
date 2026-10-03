package xyz.plcliangpicup.phigrosscore.ui

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.plcliangpicup.phigrosscore.data.*
import java.io.ByteArrayOutputStream
import java.util.UUID

object FeedbackNavigation {
    val destination = MutableStateFlow<String?>(null)
    val unread = MutableStateFlow(0)
}

class FeedbackViewModel(private val repository: AppRepository) : ViewModel() {
    var title by mutableStateOf("")
    var body by mutableStateOf("")
    var images by mutableStateOf<List<ByteArray>>(emptyList())
    var items by mutableStateOf<List<Feedback>>(emptyList())
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var more by mutableStateOf(false)
    var selected by mutableStateOf<Feedback?>(null)
    var attachments by mutableStateOf<List<ByteArray>>(emptyList())
    private var submissionId = UUID.randomUUID().toString()
    private var submittedDraft: FeedbackDraft? = null
    private var detailJob: kotlinx.coroutines.Job? = null
    fun leaveDetail() {
        detailJob?.cancel()
        selected = null; attachments = emptyList(); error = null
    }
    fun applyUpdates(updates: List<Feedback>) {
        val byId = updates.associateBy { it.id }
        items = items.map { byId[it.id]?.takeIf { update -> update.revision > it.revision } ?: it }
        val current = selected ?: return
        val updated = byId[current.id] ?: return
        if (updated.revision > current.revision) {
            selected = updated
            viewModelScope.launch { acknowledge(updated) }
        }
    }
    private suspend fun acknowledge(item: Feedback) {
        try {
            if (repository.feedbackRead(item.id, item.revision)) {
                items = items.map { if (it.id == item.id) it.copy(readRevision = maxOf(it.readRevision, item.revision)) else it }
                selected = selected?.let { if (it.id == item.id) it.copy(readRevision = maxOf(it.readRevision, item.revision)) else it }
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { /* Keep unread on the server if acknowledgement fails. */ }
    }
    fun clear() {
        viewModelScope.coroutineContext.cancelChildren()
        title=""; body=""; images=emptyList(); items=emptyList(); selected=null; attachments=emptyList()
        error=null; busy=false; more=false; submittedDraft=null; submissionId=UUID.randomUUID().toString()
    }
    fun load(append: Boolean = false) {
        if (busy) return
        busy = true; error = null
        viewModelScope.launch {
            try {
                val response = repository.feedbackList(if (append) items.size else 0)
                // A live event can arrive while the list request is in flight.
                val current = items.associateBy { it.id }
                val next = response.map { incoming -> current[incoming.id]?.takeIf { it.revision > incoming.revision } ?: incoming }
                items = if (append) (items + next).distinctBy { it.id } else next
                more = next.size == 30
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "加载失败，请重试" }
            finally { busy = false }
        }
    }
    fun addImages(context: Context, uris: List<Uri>) {
        if (busy) return
        busy = true; error = null
        viewModelScope.launch {
            try { images = images + withContext(Dispatchers.IO) { uris.take(3-images.size).map { compressFeedbackImage(context,it) } } }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "无法读取图片，请重新选择" }
            finally { busy = false }
        }
    }
    fun submit(onSuccess: () -> Unit) {
        if (busy) return
        if (title.isBlank() || body.isBlank()) {
            error = when {
                title.isBlank() && body.isBlank() -> "请填写标题和正文"
                title.isBlank() -> "请填写标题"
                else -> "请填写正文"
            }
            return
        }
        busy = true; error = null
        var draft = FeedbackDraft(submissionId,title.trim(),body.trim(),images.map { Base64.encodeToString(it,Base64.NO_WRAP) })
        if (submittedDraft != null && submittedDraft != draft) {
            submissionId=UUID.randomUUID().toString(); draft=draft.copy(id=submissionId)
        }
        submittedDraft=draft
        viewModelScope.launch {
            try {
                repository.createFeedback(draft)
                title = ""; body = ""; images = emptyList(); submissionId = UUID.randomUUID().toString()
                busy = false; onSuccess()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "提交失败，内容已保留，请重试" }
            finally { busy = false }
        }
    }
    fun open(item: Feedback) {
        if (busy) return
        selected = item; attachments = emptyList(); busy = true; error = null
        detailJob = viewModelScope.launch {
            try {
                acknowledge(item)
                attachments = (0 until item.imageCount).map { repository.feedbackImage(item.id,it) }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "加载失败，请重试" }
            finally { busy = false }
        }
    }
}

private fun compressFeedbackImage(context: Context, uri: Uri): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds=true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取图片" }
    var sample = 1
    while (maxOf(bounds.outWidth,bounds.outHeight)/sample > 1800) sample *= 2
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize=sample })
    } ?: error("无法读取图片")
    try {
        for (quality in listOf(88,75,60,45)) {
            val bytes = ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG,quality,out); out.toByteArray() }
            if (bytes.size <= 1024*1024) return bytes
        }
        error("图片较大，请裁剪后重新添加")
    } finally { bitmap.recycle() }
}
@Composable
fun FeedbackHost(repository: AppRepository, isLoggedIn: Boolean) {
    val destination by FeedbackNavigation.destination.collectAsState()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val vm: FeedbackViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = FeedbackViewModel(repository) as T
    })
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var renderedDestination by remember { mutableStateOf("mine") }
    var discard by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<ByteArray?>(null) }
    var createFromMine by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(3)) {
        vm.addImages(context, it)
    }

    // Keep the connection while the signed-in app process is alive, including normal backgrounding.
    // Android may still suspend/terminate this process; WorkManager remains the recovery path.
    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) {
            vm.clear()
            FeedbackNavigation.destination.value = null
            createFromMine = false
            preview = null
            discard = false
            snackbar.currentSnackbarData?.dismiss()
            FeedbackNavigation.unread.value = 0
            FeedbackNotificationManager.clearInAppUnread(context)
            SuggestionNotificationManager.clearInAppUnread(context)
            FeedbackNotificationManager.resetInApp()
            return@LaunchedEffect
        }
        var cursor: String? = null
        var fallbackUntil = 0L
        var retryDelay = 1_000L
        while (currentCoroutineContext().isActive) {
            try {
                val fallback = android.os.SystemClock.elapsedRealtime() < fallbackUntil
                val updates = if (fallback) repository.feedbackList(notifications = true) else {
                    val response = repository.feedbackUpdates(cursor)
                    cursor = response.cursor
                    response.items
                }
                currentCoroutineContext().ensureActive()
                FeedbackNavigation.unread.value = updates.size
                val newItems = FeedbackNotificationManager.publish(context, updates)
                vm.applyUpdates(updates)
                if (newItems.isNotEmpty() && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    // Showing a snackbar must not hold up the next live request.
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        val message = if (newItems.size == 1) "${newItems.first().title} · ${newItems.first().statusLabel}" else "${newItems.size} 条反馈有新进展"
                        if (snackbar.showSnackbar(message, actionLabel = "查看", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                            vm.leaveDetail()
                            FeedbackNavigation.destination.value = "mine"
                            vm.load()
                        }
                    }
                }
                retryDelay = 1_000L
                if (fallback) delay(3_000)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                cursor = null
                if (e is ApiException && (e.statusCode == 404 || e.statusCode == 405)) {
                    fallbackUntil = android.os.SystemClock.elapsedRealtime() + 60_000
                } else {
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(15_000)
                }
            }
        }
    }
    LaunchedEffect(destination) {
        if (destination != null) {
            renderedDestination = destination!!
            vm.error = null
            if (destination == "mine") vm.load()
        } else {
            createFromMine = false
        }
    }
    fun close() {
        when {
            vm.selected != null -> vm.leaveDetail()
            vm.busy -> Unit
            renderedDestination == "create" && (vm.title.isNotBlank() || vm.body.isNotBlank() || vm.images.isNotEmpty()) -> discard = true
            renderedDestination == "create" && createFromMine -> FeedbackNavigation.destination.value = "mine"
            else -> FeedbackNavigation.destination.value = null
        }
    }
    BackHandler(enabled = destination != null) { close() }
    AnimatedVisibility(
        visible = destination != null && isLoggedIn,
        enter = fadeIn(appTween(220)) + slideInHorizontally(appTween(280)) { it / 7 },
        exit = fadeOut(appTween(150)) + slideOutHorizontally(appTween(220)) { it / 9 },
        modifier = Modifier.fillMaxSize(),
    ) {
        Surface(Modifier.fillMaxSize(), color = AppBackground) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(), contentAlignment = Alignment.TopCenter) {
                val detail = vm.selected
                val screen = when { detail != null -> "detail"; else -> destination ?: renderedDestination }
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        val forward = targetState == "detail" || targetState == "create"
                        (fadeIn(appTween(220)) + slideInHorizontally(appTween(280)) { if (forward) it / 7 else -it / 7 }) togetherWith
                            (fadeOut(appTween(150)) + slideOutHorizontally(appTween(220)) { if (forward) -it / 9 else it / 9 })
                    },
                    label = "feedback-page",
                    modifier = Modifier.widthIn(max = AppReadableMaxWidth).fillMaxSize(),
                ) { page ->
                    FeatureScaffold(
                        title = when (page) { "create" -> "提交反馈"; "detail" -> "反馈详情"; else -> "我的反馈" },
                        onBack = { close() },
                        actions = {
                            if (page == "mine") IconButton(onClick = { vm.load() }, enabled = !vm.busy) { Icon(Icons.Default.Refresh, "刷新反馈", Modifier.size(20.dp)) }
                        },
                    ) {
                        when (page) {
                            "create" -> FeedbackEditor(vm, onAddImage = {
                                picker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }, onPreview = { preview = it }, onSubmit = {
                                vm.submit {
                                    createFromMine = false
                                    FeedbackNavigation.destination.value = "mine"
                                    FeedbackNotificationManager.schedule(context)
                                    scope.launch { snackbar.showSnackbar("反馈已提交") }
                                    if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            })
                            "detail" -> {
                                // Retain the outgoing detail for the duration of its exit transition.
                                var displayed by remember { mutableStateOf(detail) }
                                if (detail != null) displayed = detail
                                displayed?.let { FeedbackDetail(it, vm, onPreview = { bytes -> preview = bytes }) }
                            }
                            else -> FeedbackList(vm, onCreate = {
                                createFromMine = true
                                FeedbackNavigation.destination.value = "create"
                            })
                        }
                    }
                }
            }
        }
    }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.BottomCenter) {
        SnackbarHost(snackbar, modifier = Modifier.widthIn(max = AppReadableMaxWidth).padding(16.dp))
    }
    if (discard) AlertDialog(
        onDismissRequest = { discard = false },
        title = { Text("暂时离开？") },
        text = { Text("已填写的内容会保留，可返回继续编辑。") },
        confirmButton = { TextButton(onClick = {
            discard = false
            FeedbackNavigation.destination.value = if (createFromMine) "mine" else null
        }) { Text("离开") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } },
    )
    preview?.let { bytes ->
        Dialog(onDismissRequest = { preview = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { visible = true }
            val progress by animateFloatAsState(if (visible) 1f else 0f, appTween(220), label = "feedback-image-preview")
            Surface(Modifier.padding(20.dp).widthIn(max = AppReadableMaxWidth).graphicsLayer {
                alpha = progress; scaleX = .96f + .04f * progress; scaleY = scaleX
            }, shape = RoundedCornerShape(20.dp), color = AppSurface) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(start = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("图片预览", Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { preview = null }) { Icon(Icons.Default.Close, "关闭") }
                    }
                    AsyncImage(bytes, "反馈图片", Modifier.fillMaxWidth().heightIn(max = 560.dp), contentScale = ContentScale.Fit)
                }
            }
        }
    }
}

@Composable
private fun FeedbackEditor(vm: FeedbackViewModel, onAddImage: () -> Unit, onPreview: (ByteArray) -> Unit, onSubmit: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(AppPageHorizontalPadding, 18.dp, AppPageHorizontalPadding, 28.dp), verticalArrangement = Arrangement.spacedBy(AppSectionSpacing)) {
        item {
            FeedbackGroup {
                BasicTextField(
                    value = vm.title, onValueChange = { if (it.length <= 80) vm.title = it }, enabled = !vm.busy,
                    singleLine = true, textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(AppAccent), modifier = Modifier.fillMaxWidth(),
                    decorationBox = { field -> Box { if (vm.title.isEmpty()) Text("标题", color = AppTextMuted, style = MaterialTheme.typography.titleMedium); field() } },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .38f))
                BasicTextField(
                    value = vm.body, onValueChange = { if (it.length <= 4000) vm.body = it }, enabled = !vm.busy,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface, lineHeight = 23.sp),
                    cursorBrush = SolidColor(AppAccent), modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                    decorationBox = { field -> Box { if (vm.body.isEmpty()) Text("描述遇到的问题或您的建议…", color = AppTextMuted, style = MaterialTheme.typography.bodyMedium); field() } },
                )
                Text("${vm.body.length} / 4000", modifier = Modifier.align(Alignment.End), color = AppTextMuted, fontSize = 11.sp)
            }
        }
        item {
            FeedbackGroup {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("添加图片", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${vm.images.size} / 3", color = AppTextMuted, fontSize = 12.sp)
                }
                AnimatedContent(vm.images, transitionSpec = {
                    (fadeIn(appTween(180)) + scaleIn(appTween(220), initialScale = .98f)) togetherWith fadeOut(appTween(120))
                }, label = "feedback-attachments") { images ->
                    Row(Modifier.fillMaxWidth().animateContentSize(appTween(220)), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        images.forEachIndexed { index, bytes ->
                            Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(14.dp))) {
                                AsyncImage(bytes, "附件 ${index + 1}，点击预览", Modifier.fillMaxSize().clickable { onPreview(bytes) }, contentScale = ContentScale.Crop)
                                Surface(Modifier.align(Alignment.TopEnd).padding(2.dp), shape = RoundedCornerShape(50), color = AppSurface.copy(alpha = .9f)) {
                                    IconButton(onClick = { vm.images = vm.images.filterNot { it === bytes } }, enabled = !vm.busy, modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.Default.Close, "移除附件 ${index + 1}", Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        if (images.size < 3) {
                            Surface(onClick = onAddImage, enabled = !vm.busy, modifier = Modifier.weight(1f).aspectRatio(1f), shape = RoundedCornerShape(14.dp), color = AppSurfaceRaised) {
                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, "选择图片", Modifier.size(25.dp), tint = AppAccent) }
                            }
                        }
                        repeat((2 - images.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        item { FeedbackError(vm.error) }
        item {
            val interaction = remember { MutableInteractionSource() }
            Button(onClick = onSubmit, enabled = !vm.busy, interactionSource = interaction,
                modifier = Modifier.fillMaxWidth().height(48.dp).appPressMotion(interaction), shape = RoundedCornerShape(16.dp)) {
                if (vm.busy) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                else Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (vm.busy) "正在处理…" else "提交反馈", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun FeedbackList(vm: FeedbackViewModel, onCreate: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(AppPageHorizontalPadding, 18.dp, AppPageHorizontalPadding, 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${vm.items.size} 条反馈", fontSize = 12.sp, color = AppTextMuted, modifier = Modifier.weight(1f))
                TextButton(onClick = onCreate, enabled = !vm.busy) { Icon(Icons.Default.Add, null, Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("提交反馈") }
            }
        }
        item { FeedbackError(vm.error) }
        item {
            AnimatedVisibility(vm.busy, enter = fadeIn(appTween(150)), exit = fadeOut(appTween(120))) {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(2.dp)))
            }
        }
        if (vm.items.isEmpty() && !vm.busy && vm.error == null) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 70.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(32.dp), tint = AppTextMuted.copy(alpha = .55f))
                Text("暂无反馈", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text("您的问题与建议会显示在这里", color = AppTextMuted, fontSize = 12.sp)
            }
        }
        items(vm.items, key = { it.id }) { item ->
            val interaction = remember { MutableInteractionSource() }
            Surface(onClick = { vm.open(item) }, enabled = !vm.busy, interactionSource = interaction,
                modifier = Modifier.fillMaxWidth().appPressMotion(interaction).animateContentSize(appTween(220)), shape = RoundedCornerShape(18.dp), color = AppSurface) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(item.title, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        FeedbackStatus(item)
                    }
                    Text(item.reply.ifBlank { item.body }, fontSize = 12.sp, color = AppTextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(feedbackTime(item.createdAt), color = AppTextMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        if (item.revision > item.readRevision) Text("有新进展", color = AppAccent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = AppTextMuted.copy(alpha = .6f))
                    }
                }
            }
        }
        if (vm.more) item { TextButton(onClick = { vm.load(true) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("加载更多") } }
    }
}

@Composable
private fun FeedbackDetail(item: Feedback, vm: FeedbackViewModel, onPreview: (ByteArray) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(AppPageHorizontalPadding, 20.dp, AppPageHorizontalPadding, 28.dp), verticalArrangement = Arrangement.spacedBy(AppSectionSpacing)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(item.title, fontSize = 23.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeedbackStatus(item)
                    Text(feedbackTime(item.createdAt), fontSize = 11.sp, color = AppTextMuted)
                }
            }
        }
        item { FeedbackGroup { Text(item.body, fontSize = 14.sp, lineHeight = 23.sp) } }
        if (item.imageCount > 0) item {
            FeedbackGroup {
                Text("附件", fontSize = 13.sp, color = AppTextMuted)
                if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                vm.attachments.forEachIndexed { index, bytes ->
                    AsyncImage(bytes, "附件 ${index + 1}，点击查看大图", Modifier.fillMaxWidth().heightIn(max = 250.dp).clip(RoundedCornerShape(14.dp)).clickable { onPreview(bytes) }, contentScale = ContentScale.Fit)
                }
            }
        }
        item {
            AnimatedContent(targetState = item.reply to item.status, transitionSpec = {
                (fadeIn(appTween(220)) + slideInVertically(appTween(260)) { it / 12 }) togetherWith fadeOut(appTween(150))
            }, label = "feedback-reply") { (reply, _) ->
                FeedbackGroup {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(17.dp), tint = AppAccent)
                        Text("处理进展", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(reply.ifBlank { if (item.status == "processing") "管理员正在处理您的反馈" else "等待管理员处理" }, fontSize = 14.sp, lineHeight = 23.sp, color = if (reply.isBlank()) AppTextMuted else MaterialTheme.colorScheme.onSurface)
                    if (item.revision > 0) Text(feedbackTime(item.updatedAt), fontSize = 11.sp, color = AppTextMuted)
                }
            }
        }
        item {
            FeedbackError(vm.error)
            if (vm.error != null) TextButton(onClick = { vm.open(item) }, enabled = !vm.busy) { Text("重新加载") }
        }
    }
}

@Composable
private fun FeedbackGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AppSurface)
        .animateContentSize(appTween(220)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}

@Composable
private fun FeedbackStatus(item: Feedback) {
    AnimatedContent(item.statusLabel, transitionSpec = { fadeIn(appTween(180)) togetherWith fadeOut(appTween(120)) }, label = "feedback-status") { label ->
        Surface(shape = RoundedCornerShape(50), color = AppAccent.copy(alpha = .09f)) {
            Text(label, Modifier.padding(horizontal = 9.dp, vertical = 4.dp), color = AppAccent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun FeedbackError(error: String?) {
    AnimatedVisibility(error != null, enter = fadeIn(appTween(180)) + expandVertically(appTween(220)), exit = fadeOut(appTween(120)) + shrinkVertically(appTween(180))) {
        Text(error.orEmpty(), color = AppDanger, fontSize = 12.sp, lineHeight = 19.sp)
    }
}

private fun feedbackTime(value: String): String = runCatching {
    java.time.OffsetDateTime.parse(value).atZoneSameInstant(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}.getOrDefault(value)
