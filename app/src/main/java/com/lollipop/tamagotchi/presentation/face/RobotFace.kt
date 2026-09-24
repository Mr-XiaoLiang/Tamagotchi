package com.lollipop.tamagotchi.presentation.face

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.lollipop.grokbot.GrokBot
import com.lollipop.grokbot.GrokBotConfig
import com.lollipop.grokbot.GrokColor
import com.lollipop.grokbot.GrokMode
import com.lollipop.grokbot.GrokMood
import com.lollipop.grokbot.GrokScheme
import com.lollipop.grokbot.GrokShape
import com.lollipop.grokbot.rememberGrokBotState
import com.lollipop.tamagotchi.presentation.theme.ColorToken

/**
 * 一次性动作（doc/10 §3.3）：点/滑命中时由宿主下发，交给 [GrokBotState] 播一次。
 * 与「情绪」正交——情绪换脸、动作是动作，互不打断。
 */
enum class RobotOneShot { BOUNCE, SPIN, BURST }

/**
 * 一次性动作指令：[nonce] 单调自增，连续同类型也能可靠重播
 * （与 `PetFx.nonce` 同一手法）。
 */
data class RobotAction(
    val shot: RobotOneShot,
    val nonce: Long,
)

/**
 * 常驻 Robot 表情（doc/10 §2）。
 *
 * 反色设计：**脸用近白**（`ColorToken.Accent`）、**眼睛用黑**（`ColorToken.OnAccent`），
 * 眼内徽标块同取黑，保证整体只有黑白两级。
 *
 * 驱动方式：
 * - `mode = HOLD` —— 表情由宿主显式给定，绝不跑库自带的 onboarding 轮播；
 *   [GrokBot] 会在配置变化时把新 mood 推给运行中的引擎（动画不中断）。
 * - **D4 常动**：默认不暂停，只在调用方判定「不可见」（退后台 / 面板打开）时传 `paused = true`。
 *
 * @param mood 当前要显示的情绪（M18.S2 起由 `MoodEngine` 的 `Mood` 映射而来）。
 * @param size 表情边长；不传则填满约束（见 [GrokBot]）。
 * @param shape 体型（D7：随种类/性格，无法判定退回 [GrokShape.BLOB]）。
 * @param inverted 画在**浅色背景**上（如实心胶囊选中态）时置 true：脸翻深色、眼翻浅色，
 *   否则「白底 + 白脸」直接糊成一片看不见（M20.S5 脸型选择页实测）。
 * @param action 一次性动作指令（M19.S2：全屏娱乐交互）；按 [RobotAction.nonce] 去重重播。
 */
@Composable
fun RobotFace(
    mood: GrokMood,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    paused: Boolean = false,
    followPointer: Boolean = false,
    shape: GrokShape = GrokShape.BLOB,
    inverted: Boolean = false,
    contentDescription: String? = null,
    action: RobotAction? = null,
) {
    val config = remember(shape, paused, followPointer, mood, inverted) {
        GrokBotConfig(
            shape = shape,
            color = GrokColor.BLACK,
            scheme = GrokScheme.LIGHT,
            mood = mood,
            mode = GrokMode.HOLD,
            followPointer = followPointer,
            paused = paused,
            // 反色：平涂近白脸 + 黑眼（覆盖 color/scheme 渐变）；
            // 浅底（inverted）时整块翻过来 —— 深脸 + 浅眼，保证两层色差始终成立。
            flatInk = if (inverted) ColorToken.OnAccent else ColorToken.Accent,
            eyeColor = if (inverted) ColorToken.Accent else ColorToken.OnAccent,
            badgeColor = if (inverted) ColorToken.Accent else ColorToken.OnAccent,
        )
    }
    val state = rememberGrokBotState(config)
    LaunchedEffect(action) {
        val shot = action?.shot ?: return@LaunchedEffect
        when (shot) {
            RobotOneShot.BOUNCE -> state.bounce()
            RobotOneShot.SPIN -> state.spin()
            RobotOneShot.BURST -> state.burst()
        }
    }
    GrokBot(
        modifier = modifier
            .then(if (size != null) Modifier.size(size) else Modifier)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
        config = config,
        state = state,
    )
}


