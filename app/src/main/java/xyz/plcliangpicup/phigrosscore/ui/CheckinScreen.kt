package xyz.plcliangpicup.phigrosscore.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.plcliangpicup.phigrosscore.data.*
import java.io.File
import java.time.YearMonth
import java.time.ZoneId

@Composable
internal fun CheckinEntry(state: AppUiState, onClick: () -> Unit) {
    val done = state.checkin?.today != null
    val accent = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
        .background(if (done) AppSurface else accent.copy(alpha = .12f))
        .clickable(onClick = onClick).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(Icons.Default.CheckCircle, null, tint = accent, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("每日签到", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(when {
                !state.hasStoredSessionToken -> "登录后领取每日 Coin"
                done -> "今日已签到 · 累计 ${state.checkin?.totalDays} 天"
                state.checkinError != null -> "点击查看签到状态"
                state.checkin == null -> "每日可领取 15–50 Coin"
                else -> "今日还未签到，领取 15–50 Coin"
            }, style = MaterialTheme.typography.bodySmall, color = if (done) AppTextMuted else accent)
        }
        Icon(Icons.Default.ChevronRight, "前往签到", tint = accent)
    }
}

@Composable
internal fun CheckinScreen(state: AppUiState, onCheckin: (String, Boolean) -> Unit, onRanks: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var month by remember { mutableStateOf(state.checkin?.month ?: YearMonth.now(ZoneId.of("Asia/Shanghai")).toString()) }
    var rankVisible by remember { mutableStateOf(false) }
    var image by remember(state.checkin?.today) { mutableStateOf<File?>(null) }
    var imageError by remember(state.checkin?.today) { mutableStateOf<String?>(null) }
    var generating by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val today = state.checkin?.today
    LaunchedEffect(Unit) { onCheckin(month, false) }
    LaunchedEffect(state.checkin?.month) { state.checkin?.month?.let { month = it } }
    LaunchedEffect(today, retry) {
        image = null
        if (today != null) {
            generating = true; imageError = null
            try {
                val result = CheckinImageRenderer(context.applicationContext).render(today)
                image = result.file
                if (result.artworkMissing) imageError = "曲绘暂时不可用，可重试补全"
            }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { imageError = e.message ?: "图片生成失败，请重试" }
            finally { generating = false }
        }
    }
    fun save() {
        val file = image ?: return
        scope.launch {
            notice = try { withContext(Dispatchers.IO) { saveB30ImageToGallery(context, file, "Phi-Checkin-${today?.date}.png") }; "已保存到相册" }
            catch(e: Exception) { "保存失败：${e.message}" }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if(it) save() else notice = "未获得相册写入权限，可使用分享保存图片" }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(AppPageHorizontalPadding, 12.dp, AppPageHorizontalPadding, 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AppSurface).animateContentSize(appTween(260)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(if(today != null) "今日已签到" else "每天，留下一点幸运", style = MaterialTheme.typography.titleLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        state.checkin?.let { AnimatedRksNumber(it.totalCoin.toDouble(), fontSize = 28.sp,
                            fontWeight = FontWeight.SemiBold, fractionDigits = 0) }
                            ?: Text("—", style = MaterialTheme.typography.headlineMedium)
                        Text("Coin", color = AppTextMuted)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            state.checkin?.let { AnimatedRksNumber(it.totalDays.toDouble(), fontSize = 28.sp,
                                fontWeight = FontWeight.SemiBold, fractionDigits = 0) }
                                ?: Text("—", style = MaterialTheme.typography.headlineMedium)
                            Text(" 天", style = MaterialTheme.typography.headlineMedium)
                        }
                        Text("累计签到", color = AppTextMuted)
                    }
                }
                Text("每日获得 15–50 Coin · 北京时间 00:00 重置", style=MaterialTheme.typography.bodySmall,color=AppTextMuted)
                if(today != null) {
                    val color = when { today.luck >= 85 -> Color(0xFFD49B00);today.luck>=50 -> MaterialTheme.colorScheme.onSurface;else -> AppTextMuted }
                    Text("Coin + ${today.coin}  ·  今日幸运值 ${today.luck}",fontWeight=FontWeight.SemiBold)
                    Text(checkinLuckText(today.luck),color=color)
                }
                Button(onClick={onCheckin(YearMonth.now(ZoneId.of("Asia/Shanghai")).toString(),true)},enabled=state.hasStoredSessionToken && !state.checkinLoading && today==null,modifier=Modifier.fillMaxWidth()) {
                    if(state.checkinLoading) { CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp);Spacer(Modifier.width(8.dp)) }
                    Text(if(today!=null) "已领取今日奖励" else if(!state.hasStoredSessionToken) "请先登录" else "立即签到")
                }
                state.checkinError?.let { Text(it,color=MaterialTheme.colorScheme.error);TextButton(onClick={onCheckin(month,false)}) {Text("重新加载")} }
            }
        }
        if(today != null) item {
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("今日签到卡",style=MaterialTheme.typography.titleMedium)
                if(generating) LinearProgressIndicator(Modifier.fillMaxWidth())
                image?.let { file ->
                    AsyncImage(file,"签到图片，点击放大",Modifier.fillMaxWidth().aspectRatio(16f/9f).clip(RoundedCornerShape(16.dp)).clickable {preview=true})
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick={
                            if(Build.VERSION.SDK_INT<=28 && ContextCompat.checkSelfPermission(context,Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else save()
                        },modifier=Modifier.weight(1f)) {Text("保存图片")}
                        OutlinedButton(onClick={runCatching {shareImage(context,file,"分享每日签到")}.onFailure {notice="分享失败，请重试"}},modifier=Modifier.weight(1f)) {Text("分享")}
                    }
                }
                imageError?.let { Text(it,color=MaterialTheme.colorScheme.error);TextButton(onClick={retry++}) {Text("重新生成图片")} }
                if(today.chart==null) Text("暂无可推分谱面，可能已满分或存档暂时不可用。",style=MaterialTheme.typography.bodySmall,color=AppTextMuted)
                notice?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AppSurface).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick={month=YearMonth.parse(month).minusMonths(1).toString();onCheckin(month,false)}, enabled=!state.checkinLoading && month>"2020-01") {Text("上月")}
                    Text(month,Modifier.weight(1f),textAlign=TextAlign.Center,fontWeight=FontWeight.SemiBold)
                    TextButton(onClick={month=YearMonth.parse(month).plusMonths(1).toString();onCheckin(month,false)},enabled=!state.checkinLoading && month<(state.checkin?.serverDate?.take(7) ?: YearMonth.now().toString())) {Text("下月")}
                }
                Row {listOf("一","二","三","四","五","六","日").forEach {Text(it,Modifier.weight(1f),textAlign=TextAlign.Center,color=AppTextMuted)} }
                val ym=YearMonth.parse(month)
                val offset=ym.atDay(1).dayOfWeek.value-1
                val signed=if(state.checkin?.month==month) state.checkin.dates.toSet() else emptySet()
                repeat((offset+ym.lengthOfMonth()+6)/7) {week ->
                    Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        repeat(7) {day ->
                            val number=week*7+day-offset+1
                            val valid=number in 1..ym.lengthOfMonth()
                            val checked=valid && ym.atDay(number).toString() in signed
                            Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(if(checked) MaterialTheme.colorScheme.primary.copy(alpha=.15f) else Color.Transparent),contentAlignment=Alignment.Center) {
                                if(valid) Column(horizontalAlignment=Alignment.CenterHorizontally) {
                                    Text(number.toString(),color=if(checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,fontWeight=if(checked) FontWeight.Bold else FontWeight.Normal)
                                    if(checked) Text("已签",fontSize=9.sp,color=MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
                Text("本月已签到 ${signed.size} 天",color=AppTextMuted,style=MaterialTheme.typography.bodySmall)
            }
        }
        item {
            OutlinedButton(onClick={rankVisible=!rankVisible;if(rankVisible) onRanks()},modifier=Modifier.fillMaxWidth()) {Text(if(rankVisible) "收起累计签到排行榜" else "累计签到排行榜")}
        }
        if(rankVisible) {
            item {Text("累计天数前 100 名 · 同天数按达成日期排序\n未公开资料的用户以匿名显示",style=MaterialTheme.typography.bodySmall,color=AppTextMuted)}
            if(state.checkinRanks.isEmpty()) item { Text("暂无排行数据，可稍后重试",color=AppTextMuted) }
            items(state.checkinRanks.size) {index ->
                val row=state.checkinRanks[index]
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AppSurface).padding(16.dp),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    Text("${row.rank}",color=MaterialTheme.colorScheme.primary)
                    Text(row.nickname,Modifier.weight(1f));Text("${row.totalDays} 天",fontWeight=FontWeight.SemiBold)
                }
            }
        }
    }
    if (preview) image?.let {
        ZoomableB30ImageDialog(it, onDismiss = { preview = false }, contentDescription = "每日签到图片")
    }
}
