package xyz.plcliangpicup.phigrosscore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import xyz.plcliangpicup.phigrosscore.data.MAX_BEST_N_IMAGE_COUNT
import java.io.File

@Composable
internal fun BestNImagePanel(
    state: AppUiState,
    onGenerate: (Int) -> Unit,
    onZoom: () -> Unit,
    onActions: () -> Unit,
) {
    var nText by rememberSaveable { mutableStateOf("30") }
    val n = nText.toIntOrNull()
    val valid = n != null && n in 1..MAX_BEST_N_IMAGE_COUNT
    val generating = state.isGeneratingBestNImage
    val image = state.bestNImageFile?.takeIf(File::exists)
    val context = LocalContext.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 960.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Best N 图片", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Card(colors = CardDefaults.cardColors(containerColor = AppSurface), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("谱面数量", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = nText,
                        onValueChange = { value -> if (value.length <= 5 && value.all(Char::isDigit)) nText = value },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !generating,
                        singleLine = true,
                        label = { Text("N") },
                        supportingText = { Text(if (nText.isNotEmpty() && !valid) "请输入 1–200 之间的整数" else "至多支持200张谱面") },
                        isError = nText.isNotEmpty() && !valid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(30, 50, 100, 200).forEach { count ->
                            FilterChip(
                                selected = n == count,
                                onClick = { nText = count.toString() },
                                enabled = !generating,
                                label = { Text("$count", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Button(
                        onClick = { n?.takeIf { valid }?.let(onGenerate) },
                        enabled = valid && !generating,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (generating) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Image, null, Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (generating) "正在生成 · ${state.bestNImageGenerationElapsedSeconds} 秒" else "生成 Best ${n ?: "N"} 图片")
                    }
                }
            }
            if (image != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(image)
                        .memoryCacheKey("best-n-${state.generatedBestNCount}-${state.generatedBestNStyle}-${image.lastModified()}-${image.length()}")
                        .build(),
                    contentDescription = "Best ${state.generatedBestNCount} 成绩图",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AppSurface)
                        .clickable(onClick = onZoom),
                )
                Text("点击放大，支持双指缩放", color = AppTextMuted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                OutlinedButton(onClick = onActions, enabled = !generating, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("保存或分享")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
