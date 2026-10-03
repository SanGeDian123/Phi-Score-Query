package xyz.plcliangpicup.phigrosscore.ui

import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.launch

@Composable
internal fun SwipeNavigationHandle(
    onOpen: () -> Unit,
    hasUnread: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val threshold = with(LocalDensity.current) { 16.dp.toPx() }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var opened by remember { mutableStateOf(false) }
    val currentOnOpen by rememberUpdatedState(onOpen)
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier.fillMaxWidth().height(48.dp)
            .pointerInput(threshold) {
                detectVerticalDragGestures(
                    onDragStart = { dragDistance = 0f; opened = false },
                    onVerticalDrag = { change, delta ->
                        change.consume()
                        dragDistance = (dragDistance - delta).coerceIn(0f, threshold * 3f)
                        if (!opened && dragDistance >= threshold) {
                            opened = true
                            currentOnOpen()
                        }
                    },
                    onDragCancel = { dragDistance = 0f },
                    onDragEnd = {
                        dragDistance = 0f
                    },
                )
            }
            .clickable(interactionSource = interactionSource, indication = null,
                role = Role.Button, onClickLabel = "展开全部功能", onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.appPressMotion(interactionSource)
                .graphicsLayer { translationY = -dragDistance * .12f }
                .clip(RoundedCornerShape(999.dp)).background(AppSurface)
                .padding(horizontal = 18.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Default.KeyboardArrowUp, null, tint = AppAccent, modifier = Modifier.size(18.dp))
            Text("全部功能", fontSize = 12.sp, color = AppTextMuted, fontWeight = FontWeight.Medium)
            if (hasUnread) InAppUnreadDot()
        }
    }
}

@Composable
internal fun SwipeNavigationDrawer(
    items: List<NavItem>,
    currentPage: AppPage,
    onDismiss: () -> Unit,
    onPage: (AppPage) -> Unit,
    unreadPages: Set<AppPage> = emptySet(),
) {
    val progress = remember { Animatable(0f) }
    val dragOffset = remember { Animatable(0f) }
    var sheetHeight by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(220)) }
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                progress.animateTo(0f, tween(180))
                currentOnDismiss()
            }
        }
    }
    val closeCurrent by rememberUpdatedState(close)
    val density = LocalDensity.current
    val dismissDistance = with(density) { 48.dp.toPx() }
    val dismissVelocity = with(density) { 600.dp.toPx() }
    val settle: (Float) -> Unit = { velocity ->
        if (dragOffset.value >= dismissDistance || velocity > dismissVelocity) close()
        else scope.launch { dragOffset.animateTo(0f, tween(150)) }
    }
    val settleCurrent by rememberUpdatedState(settle)
    val scrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y < 0f && dragOffset.value > 0f) {
                    val consumed = available.y.coerceAtLeast(-dragOffset.value)
                    scope.launch { dragOffset.snapTo((dragOffset.value + consumed).coerceAtLeast(0f)) }
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    scope.launch { dragOffset.snapTo((dragOffset.value + available.y).coerceAtMost(sheetHeight)) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragOffset.value > 0f) {
                    settleCurrent(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }
    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
      val window = (LocalView.current.parent as? DialogWindowProvider)?.window
      SideEffect {
          // The drawer owns its scrim and transition; avoid a second platform fade.
          window?.setDimAmount(0f)
          window?.setWindowAnimations(0)
      }
      Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            alpha = progress.value * (1f - dragOffset.value / sheetHeight).coerceIn(0f, 1f)
        }.background(Color.Black.copy(alpha = .32f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                onClickLabel = "关闭全部功能", onClick = close))
        Column(
            Modifier.align(Alignment.BottomCenter).statusBarsPadding().widthIn(max = AppReadableMaxWidth)
                .fillMaxWidth().onSizeChanged { sheetHeight = it.height.toFloat().coerceAtLeast(1f) }
                .graphicsLayer { translationY = sheetHeight * (1f - progress.value) + dragOffset.value }
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(AppSurface)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                .navigationBarsPadding().nestedScroll(scrollConnection),
        ) {
        Box(Modifier.fillMaxWidth().height(36.dp).pointerInput(Unit) {
            detectVerticalDragGestures(
                onVerticalDrag = { change, delta ->
                    change.consume()
                    scope.launch { dragOffset.snapTo((dragOffset.value + delta).coerceIn(0f, sheetHeight)) }
                },
                onDragEnd = { settleCurrent(0f) },
                onDragCancel = { scope.launch { dragOffset.animateTo(0f, tween(150)) } },
            )
        }.clickable(onClickLabel = "收起全部功能", onClick = { closeCurrent() }), contentAlignment = Alignment.Center) {
            Box(Modifier.size(32.dp, 4.dp).clip(RoundedCornerShape(999.dp)).background(AppTextMuted.copy(alpha = .35f)))
        }
        Text("全部功能", fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 20.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max = LocalConfiguration.current.screenHeightDp.dp * .65f)) {
            val fontScale = LocalDensity.current.fontScale
            LazyVerticalGrid(
                columns = GridCells.Adaptive((88f * fontScale.coerceAtLeast(1f)).dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight).selectableGroup(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items, key = { it.page }) { item ->
                    val selected = item.page == currentPage
                    val interactionSource = remember { MutableInteractionSource() }
                    Column(
                        Modifier.fillMaxWidth().appPressMotion(interactionSource, pressedScale = .96f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (selected) AppAccent.copy(alpha = .09f) else AppSurface)
                            .selectable(
                                selected = selected, enabled = !closing,
                                interactionSource = interactionSource, indication = null, role = Role.Tab,
                                onClick = {
                                    onPage(item.page)
                                    close()
                                },
                            ).padding(horizontal = 4.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Box(
                            Modifier.size(52.dp).clip(RoundedCornerShape(17.dp))
                                .background(if (selected) AppAccent.copy(alpha = .16f) else AppSurfaceRaised),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(item.icon, null, tint = if (selected) AppAccent else AppTextMuted,
                                modifier = Modifier.size(25.dp))
                            if (item.page in unreadPages) {
                                InAppUnreadDot(Modifier.align(Alignment.TopEnd).padding(8.dp))
                            }
                        }
                        Text(item.title, fontSize = 13.sp,
                            color = if (selected) AppAccent else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            textAlign = TextAlign.Center)
                    }
                }
            }
        }
        }
      }
    }
}

@Composable
internal fun SwipeNavigationSetting(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = AppSurface), shape = MaterialTheme.shapes.medium) {
        CompactSettingRow("导航方式") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChoice("底部导航", Icons.Default.Menu, !enabled, { onChange(false) }, Modifier.weight(1f))
                ThemeChoice("上滑抽屉", Icons.Default.KeyboardArrowUp, enabled, { onChange(true) }, Modifier.weight(1f))
            }
        }
    }
}
