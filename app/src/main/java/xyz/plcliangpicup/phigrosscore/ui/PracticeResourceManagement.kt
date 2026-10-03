package xyz.plcliangpicup.phigrosscore.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.plcliangpicup.phigrosscore.data.PracticeChartDownloader
import xyz.plcliangpicup.phigrosscore.data.PracticeChartSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PracticeResourceManagementSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var resources by remember { mutableStateOf<List<PracticeChartDownloader.StoredResource>?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        resources = withContext(Dispatchers.IO) { PracticeChartDownloader.storedResources(context) }
    }

    val stored = resources.orEmpty().filter { it.isDownloaded }
    val selectedResources = stored.filter { it.chart.fileName in selected }
    val selectedBytes = selectedResources.sumOf { it.totalBytes }
    val totalBytes = stored.sumOf { it.totalBytes }

    ModalBottomSheet(
        onDismissRequest = { if (!deleting) onDismiss() },
        sheetState = sheetState,
        containerColor = AppSurface,
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(.88f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = AppReadableMaxWidth).fillMaxWidth().padding(horizontal = 20.dp)) {
                Text("谱面资源管理", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text("查看与删除已下载的谱面", color = AppTextMuted, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp))
            }
            if (resources == null) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppAccent)
                }
            } else {
                Column(
                    Modifier.widthIn(max = AppReadableMaxWidth).fillMaxWidth().weight(1f)
                        .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AppAccent.copy(alpha = .09f)),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().animateContentSize(appTween(240)),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp))
                                .background(AppAccent.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.FolderOpen, null, tint = AppAccent)
                            }
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(resourceSize(totalBytes), fontSize = 23.sp, fontWeight = FontWeight.Black)
                                Text("${stored.size} 份已下载谱面", color = AppTextMuted, fontSize = 12.sp)
                            }
                        }
                    }
                    Text("已下载谱面", color = AppTextMuted, fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
                    if (stored.isEmpty()) Text("暂无已下载的谱面", color = AppTextMuted,
                        fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
                    stored.forEachIndexed { index, resource ->
                        PracticeResourceRow(
                            resource = resource,
                            index = index,
                            checked = resource.chart.fileName in selected,
                            enabled = !deleting,
                            onToggle = {
                                val name = resource.chart.fileName
                                selected = if (name in selected) selected - name else selected + name
                            },
                        )
                    }
                    if (stored.isNotEmpty()) Text("删除后，再次进入对应谱面时会重新下载。", color = AppTextMuted,
                        fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
                }
                HorizontalDivider(color = AppTextMuted.copy(alpha = .12f))
                Row(
                    Modifier.widthIn(max = AppReadableMaxWidth).fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (stored.size > 1) {
                        TextButton(onClick = {
                            selected = if (stored.all { it.chart.fileName in selected }) emptySet()
                                else stored.mapTo(mutableSetOf()) { it.chart.fileName }
                        }, enabled = !deleting) {
                            Text(if (stored.all { it.chart.fileName in selected }) "取消全选" else "全选")
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { confirming = true },
                        enabled = selectedResources.isNotEmpty() && !deleting,
                        colors = ButtonDefaults.buttonColors(containerColor = AppDanger, contentColor = Color.White),
                    ) {
                        if (deleting) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        else Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(if (deleting) "正在删除" else "删除所选 (${selectedResources.size})")
                    }
                }
            }
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("删除所选谱面资源？") },
            text = { Text("将释放 ${resourceSize(selectedBytes)} 空间。再次进入对应谱面时需要重新下载。") },
            confirmButton = {
                Button(
                    onClick = {
                        confirming = false
                        deleting = true
                        val charts: List<PracticeChartSource> = selectedResources.map { it.chart }
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    PracticeChartDownloader.deleteStoredResources(context, charts)
                                }
                                selected = emptySet()
                            } catch (error: Exception) {
                                failure = error.message ?: "删除失败，请重试。"
                            } finally {
                                resources = withContext(Dispatchers.IO) { PracticeChartDownloader.storedResources(context) }
                                deleting = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppDanger, contentColor = Color.White),
                ) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("取消") } },
        )
    }
    failure?.let { message ->
        AlertDialog(
            onDismissRequest = { failure = null },
            title = { Text("删除未完成") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { failure = null }) { Text("知道了") } },
        )
    }
}

@Composable
private fun PracticeResourceRow(
    resource: PracticeChartDownloader.StoredResource,
    index: Int,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    var visible by remember(resource.chart.fileName) { mutableStateOf(false) }
    LaunchedEffect(resource.chart.fileName) {
        delay(index.coerceAtMost(6) * 45L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(appTween(220)) + slideInVertically(appTween(250)) { it / 8 } + expandVertically(appTween(250)),
        exit = fadeOut(appTween(120)),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = AppBackground),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(resource.chart.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(resource.chart.difficulty, color = AppTextMuted, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("已下载 · ${resourceSize(resource.totalBytes)}", color = AppAccent, fontSize = 12.sp)
                }
                Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
            }
        }
    }
}

private fun resourceSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}
