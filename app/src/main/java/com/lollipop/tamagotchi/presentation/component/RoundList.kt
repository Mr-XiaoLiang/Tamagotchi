package com.lollipop.tamagotchi.presentation.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * RoundList —— overlay / 页面内纵向列表（doc/06 §8.3）。
 * 可滚动 + 上下缘 EdgeFade（[roundEdgeFade]），内容横向安全边距由外层 RoundSheet/PillItem 布局保障。
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
