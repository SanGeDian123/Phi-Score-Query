package xyz.plcliangpicup.phigrosscore.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.plcliangpicup.phigrosscore.BuildConfig
import xyz.plcliangpicup.phigrosscore.R
import xyz.plcliangpicup.phigrosscore.data.AccountRksProjection
import xyz.plcliangpicup.phigrosscore.data.B30Snapshot
import xyz.plcliangpicup.phigrosscore.data.ChartRksSolution
import xyz.plcliangpicup.phigrosscore.data.RksCalculatorDraft
import xyz.plcliangpicup.phigrosscore.data.SuggestionAuthor
import xyz.plcliangpicup.phigrosscore.data.SuggestionComment
import xyz.plcliangpicup.phigrosscore.data.calculateCustomCompositeRks
import xyz.plcliangpicup.phigrosscore.data.projectAccountRksIncrease
import xyz.plcliangpicup.phigrosscore.data.solveChartRks
import java.io.File
import java.util.Locale

private enum class MoreFeature { RKS, SUGGESTION }
private enum class CalculatorMode(val label: String, val preferenceValue: String) {
    THREE_VALUE("知二推一", "three_value"),
    GROWTH("提升估算", "growth"),
    CUSTOM("自定义 B/P30", "custom");

    companion object {
        fun fromPreference(value: String): CalculatorMode =
            entries.firstOrNull { it.preferenceValue == value } ?: THREE_VALUE
    }
}
private enum class CurrentMetric(val label: String, val preferenceValue: String) {
    ACC("当前 ACC", "acc"),
    RKS("当前单曲 RKS", "rks");

    companion object {
        fun fromPreference(value: String): CurrentMetric =
            entries.firstOrNull { it.preferenceValue == value } ?: ACC
    }
}
private enum class CustomRanking(val label: String, val preferenceValue: String) {
    B30("B30", "b30"),
    P30("P30", "p30");

    companion object {
        fun fromPreference(value: String): CustomRanking =
            entries.firstOrNull { it.preferenceValue == value } ?: B30
    }
}
private enum class SuggestionTab(val label: String) { ASK("求建议"), GIVE("给建议") }

private data class GeneratedScoreImage(val label: String, val file: File)

@Composable
internal fun MoreFeaturesPage(
    state: AppUiState,
    onRefreshB30: () -> Unit,
    onLoadRandomSuggestion: (Boolean) -> Unit,
    onRksCalculatorDraftChange: (RksCalculatorDraft) -> Unit,
    onSubmitSuggestionPost: (String, ByteArray, String, (Boolean) -> Unit) -> Unit,
    onSubmitSuggestionComment: (String, ByteArray?, String?, (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf<MoreFeature?>(null) }
    BackHandler(enabled = selected != null) { selected = null }
    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            if (targetState != null) {
                (fadeIn(tween(220)) + slideInHorizontally(tween(280)) { it / 7 }) togetherWith
                    (fadeOut(tween(150)) + slideOutHorizontally(tween(220)) { -it / 9 })
            } else {
                (fadeIn(tween(220)) + slideInHorizontally(tween(280)) { -it / 7 }) togetherWith
                    (fadeOut(tween(150)) + slideOutHorizontally(tween(220)) { it / 9 })
            }
        },
        label = "more-feature-transition",
        modifier = modifier.fillMaxSize(),
    ) { feature ->
        when (feature) {
            null -> MoreDashboard(onSelect = { selected = it })
            MoreFeature.RKS -> FeatureScaffold("RKS 计算器", onBack = { selected = null }) {
                RksCalculatorScreen(
                    draft = state.rksCalculatorDraft,
                    snapshot = state.snapshot,
                    isRefreshing = state.isLoading,
                    onRefreshB30 = onRefreshB30,
                    onDraftChange = onRksCalculatorDraftChange,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            MoreFeature.SUGGESTION -> FeatureScaffold("求建议 / 给建议", onBack = { selected = null }) {
                SuggestionHubScreen(
                    state = state,
                    onLoadRandomSuggestion = onLoadRandomSuggestion,
                    onSubmitSuggestionPost = onSubmitSuggestionPost,
                    onSubmitSuggestionComment = onSubmitSuggestionComment,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun MoreDashboard(onSelect: (MoreFeature) -> Unit) {
    val illustration = ImageBitmap.imageResource(R.drawable.more_under_construction)
    val lineColor = MaterialTheme.colorScheme.onBackground
    val lineColorFilter = remember(lineColor) {
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    0f, 0f, 0f, 0f, lineColor.red * 255f,
                    0f, 0f, 0f, 0f, lineColor.green * 255f,
                    0f, 0f, 0f, 0f, lineColor.blue * 255f,
                    -.299f, -.587f, -.114f, 0f, 255f,
                ),
            ),
        )
    }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(260)) + slideInVertically(tween(360)) { it / 4 },
                    modifier = Modifier.weight(1f),
                ) {
                    MoreFeatureTile(
                        title = "RKS 计算器",
                        caption = "推算 · 估算 · 自定义",
                        icon = { Icon(Icons.Default.Calculate, null) },
                        onClick = { onSelect(MoreFeature.RKS) },
                    )
                }
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(340, delayMillis = 70)) +
                        slideInVertically(tween(420, delayMillis = 70)) { it / 4 },
                    modifier = Modifier.weight(1f),
                ) {
                    MoreFeatureTile(
                        title = "求建议 / 给建议",
                        caption = "发成绩图 · 留建议",
                        icon = { Icon(Icons.Default.Forum, null) },
                        onClick = { onSelect(MoreFeature.SUGGESTION) },
                    )
                }
            }
        }
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 900.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    drawImage(
                        image = illustration,
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        colorFilter = lineColorFilter,
                        filterQuality = FilterQuality.Medium,
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "更多功能敬请期待......",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun MoreFeatureTile(
    title: String,
    caption: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val glow = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(180)
        glow.animateTo(1f, tween(520))
    }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .graphicsLayer {
                translationY = (1f - glow.value) * 10f
                alpha = .76f + glow.value * .24f
            }
            .clickable(onClick = onClick),
    ) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(AppAccent.copy(alpha = .16f)),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalContentColor provides AppAccent) { icon() }
            }
            Column {
                Text(title, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 2)
                Text(caption, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun FeatureScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回更多")
            }
            Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun RksCalculatorScreen(
    draft: RksCalculatorDraft,
    snapshot: B30Snapshot?,
    isRefreshing: Boolean,
    onRefreshB30: () -> Unit,
    onDraftChange: (RksCalculatorDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mode = CalculatorMode.fromPreference(draft.mode)
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CalculatorMode.entries.forEach { item ->
                FilterChip(
                    selected = mode == item,
                    onClick = { onDraftChange(draft.copy(mode = item.preferenceValue)) },
                    label = { Text(item.label) },
                    leadingIcon = if (mode == item) {
                        { Icon(Icons.Default.Functions, null, Modifier.size(16.dp)) }
                    } else null,
                )
            }
        }
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 12 }) togetherWith
                    (fadeOut(tween(130)) + slideOutVertically(tween(180)) { -it / 14 })
            },
            label = "calculator-mode",
            modifier = Modifier.weight(1f),
        ) {
            when (it) {
                CalculatorMode.THREE_VALUE -> ThreeValueCalculator(draft, onDraftChange)
                CalculatorMode.GROWTH -> GrowthCalculator(
                    draft = draft,
                    snapshot = snapshot,
                    isRefreshing = isRefreshing,
                    onRefreshB30 = onRefreshB30,
                    onDraftChange = onDraftChange,
                )
                CalculatorMode.CUSTOM -> CustomRankingCalculator(draft, onDraftChange)
            }
        }
    }
}

@Composable
private fun ThreeValueCalculator(
    draft: RksCalculatorDraft,
    onDraftChange: (RksCalculatorDraft) -> Unit,
) {
    val constant = draft.threeConstant
    val accuracy = draft.threeAccuracy
    val rks = draft.threeRks
    var result by remember { mutableStateOf<ChartRksSolution?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("留空一项", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        DecimalField(constant, { onDraftChange(draft.copy(threeConstant = it)) }, "谱面定数", "15.9")
        DecimalField(accuracy, { onDraftChange(draft.copy(threeAccuracy = it)) }, "ACC", "99.25")
        DecimalField(rks, { onDraftChange(draft.copy(threeRks = it)) }, "单曲 RKS", "15.37")
        Button(
            onClick = {
                runCatching {
                    solveChartRks(constant.toDoubleOrNull(), accuracy.toDoubleOrNull(), rks.toDoubleOrNull())
                }.onSuccess {
                    result = it
                    error = null
                    onDraftChange(
                        draft.copy(
                            threeConstant = constant.ifBlank { formatNumber(it.chartConstant, 4) },
                            threeAccuracy = accuracy.ifBlank { formatNumber(it.accuracy, 4) },
                            threeRks = rks.ifBlank { formatNumber(it.chartRks, 4) },
                        ),
                    )
                }.onFailure {
                    result = null
                    error = it.message
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text("计算", fontWeight = FontWeight.Bold) }
        AnimatedVisibility(visible = result != null || error != null, enter = fadeIn() + slideInVertically { it / 3 }) {
            result?.let {
                ResultPanel(
                    title = "计算结果",
                    lines = listOf(
                        "定数" to formatNumber(it.chartConstant, 4),
                        "ACC" to "${formatNumber(it.accuracy, 4)}%",
                        "单曲 RKS" to formatNumber(it.chartRks, 6),
                    ),
                )
            } ?: ErrorText(error.orEmpty())
        }
    }
}

@Composable
private fun GrowthCalculator(
    draft: RksCalculatorDraft,
    snapshot: B30Snapshot?,
    isRefreshing: Boolean,
    onRefreshB30: () -> Unit,
    onDraftChange: (RksCalculatorDraft) -> Unit,
) {
    val constant = draft.growthConstant
    val metric = CurrentMetric.fromPreference(draft.growthMetric)
    val currentValue = draft.growthCurrentValue
    val targetAccuracy = draft.growthTargetAccuracy
    var result by remember { mutableStateOf<AccountRksProjection?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(snapshot?.cachedAtEpochMs) {
        result = null
        error = null
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DecimalField(constant, { onDraftChange(draft.copy(growthConstant = it)) }, "谱面定数", "15.9")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CurrentMetric.entries.forEach {
                FilterChip(
                    selected = metric == it,
                    onClick = { onDraftChange(draft.copy(growthMetric = it.preferenceValue)) },
                    label = { Text(it.label) },
                )
            }
        }
        DecimalField(
            currentValue,
            { onDraftChange(draft.copy(growthCurrentValue = it)) },
            metric.label,
            if (metric == CurrentMetric.ACC) "98.50" else "14.87",
        )
        DecimalField(
            targetAccuracy,
            { onDraftChange(draft.copy(growthTargetAccuracy = it)) },
            "目标 ACC",
            "99.50",
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = AppAccent.copy(alpha = .09f)),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("真实 B30", fontWeight = FontWeight.Black)
                        Text(
                            snapshot?.let { "当前综合 ${formatNumber(it.totalRks, 6)}" } ?: "尚未读取成绩",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                    OutlinedButton(onClick = onRefreshB30, enabled = !isRefreshing) {
                        if (isRefreshing) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("刷新")
                        }
                    }
                }
                snapshot?.items
                    ?.filter { it.section.equals("BEST", ignoreCase = true) }
                    ?.lastOrNull()
                    ?.let { Text("B27 末位 ${formatNumber(it.rks, 6)}", color = AppAccent, fontSize = 12.sp) }
            }
        }
        Button(
            onClick = {
                runCatching {
                    val value = requireNotNull(currentValue.toDoubleOrNull()) { "请填写当前成绩" }
                    val actualB30 = requireNotNull(snapshot) { "暂无真实 B30，请先刷新成绩" }
                    projectAccountRksIncrease(
                        chartConstant = requireNotNull(constant.toDoubleOrNull()) { "请填写谱面定数" },
                        currentAccuracy = value.takeIf { metric == CurrentMetric.ACC },
                        currentChartRks = value.takeIf { metric == CurrentMetric.RKS },
                        targetAccuracy = requireNotNull(targetAccuracy.toDoubleOrNull()) { "请填写目标 ACC" },
                        currentAccountRks = actualB30.totalRks,
                        b30Items = actualB30.items,
                    )
                }.onSuccess {
                    result = it
                    error = null
                }.onFailure {
                    result = null
                    error = it.message
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text("估算提升", fontWeight = FontWeight.Bold) }
        AnimatedVisibility(visible = result != null || error != null, enter = fadeIn() + slideInVertically { it / 3 }) {
            result?.let {
                ResultPanel(
                    title = "+${formatNumber(it.accountIncrease, 6)} RKS",
                    lines = listOf(
                        "真实综合" to formatNumber(it.currentAccountRks, 6),
                        "当前单曲" to formatNumber(it.currentChartRks, 6),
                        "目标单曲" to formatNumber(it.targetChartRks, 6),
                        "B27 末位" to formatNumber(it.best27Floor, 6),
                        "B27 提升" to "+${formatNumber(it.best27Increase, 6)}",
                        "AP3 提升" to "+${formatNumber(it.ap3Increase, 6)}",
                        "预计综合" to formatNumber(it.projectedAccountRks, 6),
                    ),
                )
            } ?: ErrorText(error.orEmpty())
        }
        Text(
            "按真实 B27 与 AP3 重新排序；目标未进入有效槽位时提升为 0。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun CustomRankingCalculator(
    draft: RksCalculatorDraft,
    onDraftChange: (RksCalculatorDraft) -> Unit,
) {
    val ranking = CustomRanking.fromPreference(draft.customRanking)
    val values = if (ranking == CustomRanking.B30) draft.b30Values else draft.p30Values
    val parsed = values.mapNotNull(String::toDoubleOrNull)
    val composite = runCatching { calculateCustomCompositeRks(parsed) }.getOrDefault(0.0)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CustomRanking.entries.forEach {
                    FilterChip(
                        selected = ranking == it,
                        onClick = { onDraftChange(draft.copy(customRanking = it.preferenceValue)) },
                        label = { Text(it.label) },
                    )
                }
            }
        }
        item {
            ResultPanel(
                title = formatNumber(composite, 6),
                lines = listOf("已填写" to "${parsed.size} / 30", "总和" to formatNumber(parsed.sum(), 6)),
            )
        }
        items(30) { index ->
            val label = if (ranking == CustomRanking.B30) {
                if (index < 3) "P${index + 1}" else "B${index - 2}"
            } else {
                "#${index + 1}"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = AppAccent, fontWeight = FontWeight.Black, modifier = Modifier.width(48.dp))
                DecimalField(
                    value = values[index],
                    onValueChange = { next ->
                        val updated = values.toMutableList().apply { this[index] = next }
                        onDraftChange(
                            if (ranking == CustomRanking.B30) draft.copy(b30Values = updated)
                            else draft.copy(p30Values = updated),
                        )
                    },
                    label = "单曲 RKS",
                    placeholder = "0.0000",
                    modifier = Modifier.weight(1f),
                )
                if (values[index].isNotBlank()) {
                    IconButton(onClick = {
                        val updated = values.toMutableList().apply { this[index] = "" }
                        onDraftChange(
                            if (ranking == CustomRanking.B30) draft.copy(b30Values = updated)
                            else draft.copy(p30Values = updated),
                        )
                    }) {
                        Icon(Icons.Default.DeleteOutline, "清空 $label")
                    }
                }
            }
        }
    }
}

@Composable
private fun DecimalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next ->
            if (next.isEmpty() || next.matches(Regex("\\d{0,3}(\\.\\d{0,8})?"))) onValueChange(next)
        },
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun ResultPanel(title: String, lines: List<Pair<String, String>>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppAccent.copy(alpha = .12f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize(spring()),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, color = AppAccent, fontSize = 25.sp, fontWeight = FontWeight.Black)
            lines.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
}

@Composable
private fun SuggestionHubScreen(
    state: AppUiState,
    onLoadRandomSuggestion: (Boolean) -> Unit,
    onSubmitSuggestionPost: (String, ByteArray, String, (Boolean) -> Unit) -> Unit,
    onSubmitSuggestionComment: (String, ByteArray?, String?, (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(SuggestionTab.ASK) }
    val generatedImages = remember(state.imageFile, state.p30ImageFile) {
        listOfNotNull(
            state.imageFile?.takeIf(File::exists)?.let { GeneratedScoreImage("B30", it) },
            state.p30ImageFile?.takeIf(File::exists)?.let { GeneratedScoreImage("P30", it) },
        )
    }
    LaunchedEffect(tab, state.suggestionPost) {
        if (tab == SuggestionTab.GIVE && state.suggestionPost == null && !state.isSuggestionLoading) {
            onLoadRandomSuggestion(false)
        }
    }
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SuggestionTab.entries.forEach {
                FilterChip(
                    selected = tab == it,
                    onClick = { tab = it },
                    label = { Text(it.label) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 8 }) togetherWith
                    (fadeOut(tween(140)) + slideOutHorizontally(tween(180)) { -it / 10 })
            },
            label = "suggestion-tab",
            modifier = Modifier.weight(1f),
        ) {
            when (it) {
                SuggestionTab.ASK -> AskSuggestionPane(
                    submitting = state.isSuggestionSubmitting,
                    generatedImages = generatedImages,
                    onSubmit = { description, bytes, mime, done ->
                        onSubmitSuggestionPost(description, bytes, mime) { success ->
                            done(success)
                            if (success) tab = SuggestionTab.GIVE
                        }
                    },
                )
                SuggestionTab.GIVE -> GiveSuggestionPane(
                    state = state,
                    generatedImages = generatedImages,
                    onAnother = { onLoadRandomSuggestion(true) },
                    onSubmitComment = onSubmitSuggestionComment,
                )
            }
        }
    }
}

@Composable
private fun AskSuggestionPane(
    submitting: Boolean,
    generatedImages: List<GeneratedScoreImage>,
    onSubmit: (String, ByteArray, String, (Boolean) -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var description by remember { mutableStateOf("求建议！") }
    var selectedLabel by remember { mutableStateOf<String?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }
    val selected = generatedImages.firstOrNull { it.label == selectedLabel }
        ?: generatedImages.firstOrNull()
    LaunchedEffect(generatedImages.map { it.label }) {
        if (selectedLabel !in generatedImages.map { it.label }) {
            selectedLabel = generatedImages.firstOrNull()?.label
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScoreImagePickerCard(
            images = generatedImages,
            selected = selected,
            onSelect = {
                selectedLabel = it.label
                localError = null
            },
        )
        OutlinedTextField(
            value = description,
            onValueChange = { if (it.length <= 120) description = it },
            label = { Text("文字描述") },
            placeholder = { Text("求建议！") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        localError?.let { ErrorText(it) }
        Button(
            enabled = selected != null && !submitting,
            onClick = {
                val image = selected ?: return@Button
                scope.launch {
                    runCatching { readGeneratedScoreImage(image) }
                        .onSuccess { bytes ->
                            onSubmit(description, bytes, "image/png") { success ->
                                if (success) {
                                    selectedLabel = null
                                    description = "求建议！"
                                }
                            }
                        }
                        .onFailure { localError = it.message }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            if (submitting) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.UploadFile, null)
                Spacer(Modifier.width(8.dp))
                Text("发送成绩图", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ScoreImagePickerCard(
    images: List<GeneratedScoreImage>,
    selected: GeneratedScoreImage?,
    onSelect: (GeneratedScoreImage) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        if (images.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                images.forEach { image ->
                    FilterChip(
                        selected = selected?.label == image.label,
                        onClick = { onSelect(image) },
                        label = { Text(image.label) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f)),
    ) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 560.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (selected == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AddPhotoAlternate, null, tint = AppAccent, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(9.dp))
                    Text("请先在成绩图页面生成 B30 / P30", fontWeight = FontWeight.Bold)
                }
            } else {
                AsyncImage(
                    model = selected.file,
                    contentDescription = "待发送成绩图",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 560.dp).padding(8.dp),
                )
            }
        }
    }
    }
}

@Composable
private fun GiveSuggestionPane(
    state: AppUiState,
    generatedImages: List<GeneratedScoreImage>,
    onAnother: () -> Unit,
    onSubmitComment: (String, ByteArray?, String?, (Boolean) -> Unit) -> Unit,
) {
    val post = state.suggestionPost
    if (state.isSuggestionLoading && post == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AppAccent)
        }
        return
    }
    if (post == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            OutlinedButton(onClick = onAnother) { Text("抽一张成绩图") }
        }
        return
    }
    AnimatedContent(
        targetState = post,
        transitionSpec = {
            (fadeIn(tween(260)) + slideInVertically(tween(320)) { it / 10 }) togetherWith
                (fadeOut(tween(150)) + slideOutVertically(tween(220)) { -it / 12 })
        },
        label = "random-suggestion-post",
        modifier = Modifier.fillMaxSize(),
    ) { current ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SuggestionAuthorRow(current.author, Modifier.weight(1f))
                    TextButton(onClick = onAnother, enabled = !state.isSuggestionLoading) {
                        Icon(Icons.Default.Casino, null)
                        Spacer(Modifier.width(5.dp))
                        Text("换一个")
                    }
                }
            }
            if (state.isSuggestionLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(current.imageUrl).crossfade(180).build(),
                    contentDescription = "${current.author.nickname} 的成绩图",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 260.dp, max = 760.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = .16f)),
                )
            }
            item {
                Text(current.description, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(formatCommunityTime(current.createdAt), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
            if (current.comments.isNotEmpty()) {
                item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)) }
                items(current.comments, key = { it.id }) { SuggestionCommentCard(it) }
            }
            item {
                CommentComposer(
                    submitting = state.isSuggestionSubmitting,
                    generatedImages = generatedImages,
                    onSubmit = onSubmitComment,
                )
            }
        }
    }
}

@Composable
private fun SuggestionAuthorRow(author: SuggestionAuthor, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(13.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            author.avatar.validAvatarName()?.let { avatar ->
                AsyncImage(
                    model = "${BuildConfig.API_BASE_URL.trimEnd('/')}/avatar/${avatarAssetKey(avatar)}.png",
                    contentDescription = author.nickname,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(Modifier.padding(start = 10.dp)) {
            Text(author.nickname, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                challengeModeLabel(author.challengeModeRank)?.let {
                    Text("课题 $it", color = AppAccent, fontSize = 10.sp)
                }
                Text("${formatNumber(author.rks, 4)} RKS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun SuggestionCommentCard(comment: SuggestionComment) {
    Card(
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .58f)),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SuggestionAuthorRow(comment.author)
            if (comment.text.isNotBlank()) Text(comment.text, fontSize = 13.sp)
            comment.imageUrl?.let {
                AsyncImage(
                    model = it,
                    contentDescription = "评论成绩图",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 520.dp)
                        .clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = .12f)),
                )
            }
            Text(formatCommunityTime(comment.createdAt), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
        }
    }
}

@Composable
private fun CommentComposer(
    submitting: Boolean,
    generatedImages: List<GeneratedScoreImage>,
    onSubmit: (String, ByteArray?, String?, (Boolean) -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var selectedLabel by remember { mutableStateOf<String?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }
    val selected = generatedImages.firstOrNull { it.label == selectedLabel }
    LaunchedEffect(generatedImages.map { it.label }) {
        if (selectedLabel !in generatedImages.map { it.label }) {
            selectedLabel = null
        }
    }
    Card(
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 240) text = it },
                placeholder = { Text("写下建议") },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
            selected?.let {
                AsyncImage(
                    model = it.file,
                    contentDescription = "待发布成绩图",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 420.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }
            localError?.let { ErrorText(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (generatedImages.isEmpty()) {
                    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.AddPhotoAlternate, null)
                        Spacer(Modifier.width(5.dp))
                        Text("暂无成绩图")
                    }
                } else {
                    generatedImages.forEach { image ->
                        FilterChip(
                            selected = selected?.label == image.label,
                            onClick = {
                                selectedLabel = image.label.takeUnless { it == selectedLabel }
                                localError = null
                            },
                            label = { Text(image.label) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Button(
                    enabled = !submitting && (text.isNotBlank() || selected != null),
                    onClick = {
                        scope.launch {
                            runCatching {
                                selected?.let { readGeneratedScoreImage(it) }
                            }.onSuccess { bytes ->
                                onSubmit(text, bytes, bytes?.let { "image/png" }) { success ->
                                    if (success) {
                                        text = ""
                                        selectedLabel = null
                                    }
                                }
                            }.onFailure { localError = it.message }
                        }
                    },
                ) {
                    if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.AutoMirrored.Filled.Send, "发布建议")
                }
            }
        }
    }
}

private suspend fun readGeneratedScoreImage(image: GeneratedScoreImage): ByteArray = withContext(Dispatchers.IO) {
    require(image.file.exists() && image.file.isFile) { "APP 内生成的成绩图已失效，请重新生成" }
    val bytes = image.file.readBytes()
    require(bytes.isNotEmpty()) { "APP 内生成的成绩图为空" }
    require(bytes.size <= 8 * 1024 * 1024) { "成绩图不能超过 8 MB" }
    require(bytes.size >= PNG_SIGNATURE.size && PNG_SIGNATURE.indices.all { bytes[it] == PNG_SIGNATURE[it] }) {
        "APP 内生成的成绩图格式无效"
    }
    bytes
}

private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

private fun formatNumber(value: Double, decimals: Int): String =
    String.format(Locale.US, "%.${decimals}f", value)

private fun formatCommunityTime(value: String): String = value
    .replace('T', ' ')
    .substringBefore('.')
    .removeSuffix("Z")
    .take(16)
