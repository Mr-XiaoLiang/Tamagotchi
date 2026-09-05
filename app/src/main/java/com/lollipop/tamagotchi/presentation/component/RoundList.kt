package com.lollipop.tamagotchi.presentation.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * RoundList —— overlay / 页面内纵向列表（doc/06 §8.3）。
 * 可滚动 + 上下缘 EdgeFade（[roundEdgeFade]），内容横向安全边距由外层 RoundSheet/PillItem 布局保障。
 * 交互结构约定（doc/06 §8.1）：页面内容 = [space、title、item...、space]——调用方将标题作为
 * 内容首元素置于 RoundList 内，首/末以 [RoundEdgeSpace] 留白，使标题初始居屏中并随滚动上行。
 * M1 行数少、无触控/表冠联动，仅保证结构与渐隐；表冠滚动在 doc/08 触控层（M8 前后）接入。
 */
@Composable
fun RoundList(
    modifier: Modifier = Modifier,
    verticalPadding: Dp = 2.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .roundEdgeFade()
            .padding(vertical = verticalPadding),
        content = content,
    )
}

/**
 * 圆屏列表页首/末留白段（交互结构 [space、title、item...、space]，doc/06 §8.1）：
 * 首段 ≈ 长边半屏 − 标题行半高（默认标题行 ~48dp），使标题（内容首元素）打开时初始竖直居中于屏中；
 * 末段同值，使滚动后末项也能到达屏中可读区。所有「非特定布局」的纵向列表一律使用，禁止自行拍脑袋留白。
 */
@Composable
fun RoundEdgeSpace(titleRowHalf: Dp = 24.dp) {
    val config = LocalConfiguration.current
    val longSide = max(config.screenWidthDp, config.screenHeightDp).dp
    Spacer(Modifier.height(longSide * 0.5f - titleRowHalf))
}
