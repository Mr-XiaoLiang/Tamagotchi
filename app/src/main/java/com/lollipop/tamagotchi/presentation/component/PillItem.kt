package com.lollipop.tamagotchi.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lollipop.tamagotchi.presentation.theme.ColorToken

/**
 * 胶囊条目（doc/06 §8.3）：
 *  - 实心 = 可执行动作（可点击）；空心 = 只读信息位 / 锁定（不可点击）；
 *  - [color] 可覆写（属性/状态/类别色）：实心换填充色、空心换描边与文字色；
 *  - [icon] 前置小图标（形状自行着色，不受胶囊文字色限制）；
 *  - [trailing] 尾随位（文字后、行右 padding 内）；[contentPadding] 可覆写内边距
 *    （默认两端 18dp；状态行传非对称值把行尾环右移至与胶囊端半圆同心）。
 */
@Composable
fun PillItem(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    color: Color? = null,
    icon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp),
    textAlign: TextAlign = TextAlign.Start,
    onClick: (() -> Unit)? = null,
) {
    val bg = if (filled) (color ?: ColorToken.PillFilled) else Color.Transparent
    val borderColor = if (filled) Color.Transparent else (color ?: ColorToken.PillOutline)
    val textColor = if (filled) ColorToken.OnAccent else (color ?: ColorToken.Text2)
    val shape = CircleShape

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(shape)
            .background(bg)
            .border(if (filled) 0.dp else 2.dp, borderColor, shape)
            .clickable(enabled = onClick != null, onClick = { onClick?.invoke() })
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = text,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

/** 属性圆点（PillItem icon 用）：如「饱腹」琥珀点。 */
@Composable
fun ColorDot(color: Color, size: androidx.compose.ui.unit.Dp = 10.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(color),
    )
}
