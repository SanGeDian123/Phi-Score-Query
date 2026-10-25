package xyz.plcliangpicup.phigrosscore.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.plcliangpicup.phigrosscore.data.UserModerationStatus
import java.io.ByteArrayOutputStream

@Composable
fun UserModerationOverlay(
    status: UserModerationStatus,
    isSubmitting: Boolean,
    message: String?,
    submissionEvent: Long,
    onSubmitAppeal: (restrictionType: String, body: String, images: List<ByteArray>) -> Unit,
    onLogout: () -> Unit,
    onRefresh: () -> Unit = {},
) {
    if (status.accountSuspended) {
        AccountSuspendedScreen(
            status = status,
            isSubmitting = isSubmitting,
            message = message,
            submissionEvent = submissionEvent,
            onSubmitAppeal = onSubmitAppeal,
            onLogout = onLogout,
            onRefresh = onRefresh,
        )
        return
    }
    if (!status.publicHidden) return

    var appealOpen by remember { mutableStateOf(false) }
        Card(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .fillMaxWidth()
                .clickable { appealOpen = true },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .97f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Column(Modifier.weight(1f)) {
                    Text("公开展示已停止", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("成绩已停止公开展示 · 点击查看并申诉", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("申诉", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    if (appealOpen) {
        Dialog(
            onDismissRequest = { if (!isSubmitting) appealOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp).heightIn(max = 720.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                    AppealHeader("公开展示限制申诉", status.publicReason, status.publicExpiresAt)
                    AppealForm(
                        restrictionType = "public_hidden",
                        status = status,
                        isSubmitting = isSubmitting,
                        message = message,
                        submissionEvent = submissionEvent,
                        onSubmit = onSubmitAppeal,
                    )
                    TextButton(onClick = { appealOpen = false }, modifier = Modifier.align(Alignment.End), enabled = !isSubmitting) { Text("关闭") }
                }
            }
        }
    }
}

@Composable
private fun AccountSuspendedScreen(
    status: UserModerationStatus,
    isSubmitting: Boolean,
    message: String?,
    submissionEvent: Long,
    onSubmitAppeal: (String, String, List<ByteArray>) -> Unit,
    onLogout: () -> Unit,
    onRefresh: () -> Unit,
) {
    var appealType by remember { mutableStateOf("account_suspended") }
    Box(
        Modifier.fillMaxSize().background(AppBackground).padding(WindowInsets.safeDrawing.asPaddingValues()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(74.dp).clip(CircleShape).background(MaterialTheme.colorScheme.errorContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.error)
            }
            Text("账户暂停使用", Modifier.padding(top = 18.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("该账号目前无法登录或使用应用服务，成绩已停止公开展示。", Modifier.padding(top = 7.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Card(
                Modifier.fillMaxWidth().padding(top = 20.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = AppSurface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.ReportProblem, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text("限制详情", fontWeight = FontWeight.Bold)
                    }
                    if (!status.accountReason.isNullOrBlank()) Text(status.accountReason!!, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        status.accountExpiresAt?.let { "暂停至 ${formatModerationDate(it)}" } ?: "限制时长：长期，直至管理员解除",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Card(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AppSurface),
            ) {
                Column(Modifier.padding(17.dp)) {
                    AppealHeader("提交申诉", status.accountReason, status.accountExpiresAt, showDetails = false)
                    if (status.publicHidden) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = appealType == "account_suspended", onClick = { appealType = "account_suspended" }, label = { Text("账户暂停") })
                            FilterChip(selected = appealType == "public_hidden", onClick = { appealType = "public_hidden" }, label = { Text("公开展示") })
                        }
                    }
                    key(appealType) { AppealForm(
                        restrictionType = appealType,
                        status = status,
                        isSubmitting = isSubmitting,
                        message = message,
                        submissionEvent = submissionEvent,
                        onSubmit = onSubmitAppeal,
                    ) }
                }
            }
            Row {
                TextButton(onClick = onRefresh, enabled = !isSubmitting) { Text("刷新限制状态") }
                TextButton(onClick = onLogout, enabled = !isSubmitting) { Text("退出当前账号") }
            }
            Text("申诉材料仅用于核实此项限制", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AppealHeader(
    title: String,
    reason: String?,
    expiresAt: String?,
    showDetails: Boolean = true,
) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (showDetails && !reason.isNullOrBlank()) Text(reason, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (showDetails) Text(
            expiresAt?.let { "限制至 ${formatModerationDate(it)}" } ?: "限制时长：长期",
            Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppealForm(
    restrictionType: String,
    status: UserModerationStatus,
    isSubmitting: Boolean,
    message: String?,
    submissionEvent: Long,
    onSubmit: (String, String, List<ByteArray>) -> Unit,
) {
    var body by remember { mutableStateOf("") }
    var images by remember { mutableStateOf<List<Pair<ByteArray, Uri>>>(emptyList()) }
    var imageError by remember { mutableStateOf<String?>(null) }
    var visibleMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(3)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            imageError = null
            try {
                val remaining = (3 - images.size).coerceAtLeast(0)
                val loaded = withContext(Dispatchers.IO) {
                    uris.take(remaining).map { uri -> compressModerationImage(context, uri) to uri }
                }
                images = images + loaded
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                imageError = error.message ?: "无法读取图片，请重新选择"
            }
        }
    }
    LaunchedEffect(submissionEvent) {
        if (submissionEvent > 0) {
            body = ""
            images = emptyList()
            imageError = null
        }
    }
    LaunchedEffect(message) { visibleMessage = message }
    val appealStatus = if (restrictionType == "account_suspended") status.accountAppealStatus else status.publicAppealStatus
    val appealReply = if (restrictionType == "account_suspended") status.accountAppealReply else status.publicAppealReply

    Column(Modifier.padding(top = if (restrictionType == "public_hidden") 14.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        if (appealStatus == "pending" || appealStatus == "reviewing") {
            Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .6f), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("申诉已提交，正在等待处理", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            OutlinedTextField(
                value = body,
                onValueChange = { body = it.take(4000) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSubmitting,
                label = { Text("申诉说明") },
                placeholder = { Text("请描述情况并说明需要核实的内容") },
                minLines = 4,
                maxLines = 7,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("图片材料", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("${images.size}/3", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                images.forEachIndexed { index, (bytes, _) ->
                    val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
                    Box(Modifier.size(62.dp)) {
                        if (bitmap != null) Image(bitmap, contentDescription = "申诉图片 ${index + 1}", modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        IconButton(onClick = { images = images.filterIndexed { imageIndex, _ -> imageIndex != index } }, modifier = Modifier.align(Alignment.TopEnd).size(21.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = .92f), CircleShape), enabled = !isSubmitting) {
                            Text("×", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (images.size < 3) {
                    OutlinedButton(
                        onClick = { picker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = !isSubmitting,
                        modifier = Modifier.height(62.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("添加图片")
                    }
                }
            }
            imageError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Text("支持 JPG、PNG、WebP，每张不超过 1 MB。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = { onSubmit(restrictionType, body.trim(), images.map { it.first }) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = body.isNotBlank() && !isSubmitting,
                shape = RoundedCornerShape(14.dp),
            ) {
                if (isSubmitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("提交申诉", fontWeight = FontWeight.Bold)
            }
        }
        visibleMessage?.let { messageText ->
            Text(messageText, color = if (messageText.contains("失败") || messageText.contains("无法")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        appealReply?.takeIf { it.isNotBlank() && (appealStatus == "accepted" || appealStatus == "rejected") }?.let { reply ->
            Text("处理说明：$reply", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun formatModerationDate(value: String): String = remember(value) {
    runCatching {
        java.time.OffsetDateTime.parse(value).withOffsetSameInstant(java.time.ZoneOffset.ofHours(8))
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm 'UTC+8'"))
    }.getOrDefault(value)
}

private fun compressModerationImage(context: Context, uri: Uri): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取图片" }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1800) sample *= 2
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("无法读取图片")
    try {
        for (quality in listOf(88, 75, 60, 45)) {
            val bytes = ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
                output.toByteArray()
            }
            if (bytes.size <= 1024 * 1024) return bytes
        }
        error("图片较大，请裁剪后重新添加")
    } finally {
        bitmap.recycle()
    }
}
