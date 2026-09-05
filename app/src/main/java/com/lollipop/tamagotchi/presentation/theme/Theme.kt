package com.lollipop.tamagotchi.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * 全 App 深色 Material3 主题（doc/06 §4 / Task.md M1.S1）。
 * Starter 空主题 + 演示 Button 列表已移除；配色一律取 [ColorToken]。
 */
@Composable
fun TamagotchiTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = ColorToken.Accent,
        onPrimary = ColorToken.OnAccent,
        secondary = ColorToken.Text2,
        onSecondary = ColorToken.bg,
        background = ColorToken.bg,
        onBackground = ColorToken.Accent,
        surface = ColorToken.bg,
        onSurface = ColorToken.Accent,
        error = ColorToken.Warn,
        onError = ColorToken.OnAccent,
    )
    // 注：当前用标准 material3 scheme（wear 的 compose-material3 1.6.2 无顶层 darkColorScheme，
    // 其 MaterialTheme 仅收自家 28 字段 ColorScheme）。本工程组件直接取 ColorToken 上色，
    // scheme 仅作语义默认位。后续接入 Wear 组件（Scaffold/EdgeButton 等）时按需切换。
    MaterialTheme(
        colorScheme = scheme,
        content = content,
    )
}
