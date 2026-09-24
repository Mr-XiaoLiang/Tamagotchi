package com.lollipop.tamagotchi.presentation.face

import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.lollipop.tamagotchi.domain.engine.MoodEvent
import kotlin.math.abs

/**
 * 全屏 Robot 的娱乐手势（doc/10 §3.3 / §4.3）。
 *
 * 只在 **Robot 展开态** 存在（收起态压根没有这一层，交互随面板一起消失 —— 无需判 `FaceMode`）。
 * **边缘隔离靠几何**：调用方把本层限制在 `minSide − 2×edgeBand` 的内圈，边缘带不在 hit 范围内，
 * 三向抽屉天然不受影响（不做矩形规避，也不依赖手势层的先后顺序）。
 *
 * `requireUnconsumed = true`：只吃**未被消费**的 down —— 于是「点底部宠物缩略 / 点问候气泡」
 * 这类已经被上层控件消费掉的点击，不会顺带把 Robot 逗一下（R2 收口）。
 * 依据是库源码：`GrokBot` 自带那层 `pointerInput`（`GrokFace.kt`，眼随手指）只做
 * `awaitPointerEvent` 记录坐标、**从不 `consume`**，且它的 block 是 `while(true)` 永不结束，
 * 也就不会触发 `awaitPointerEventScope` 结束时对未消费 change 的统一吞掉 —— 所以 down 到本层时
 * 一定是干净的。
 */
enum class RobotGesture { TAP, SWIPE_X, SWIPE_Y, HOLD }

/** 手势 → 情绪瞬态（每次都给，不受属性冷却限制）。 */
fun RobotGesture.toMoodEvent(): MoodEvent = when (this) {
    RobotGesture.TAP -> MoodEvent.AMUSE_TAP
    RobotGesture.SWIPE_X -> MoodEvent.AMUSE_SPIN
    RobotGesture.SWIPE_Y -> MoodEvent.AMUSE_BURST
    RobotGesture.HOLD -> MoodEvent.AMUSE_HOLD
}

/** 手势 → 一次性动作（长按是「定格注视」，没有动效）。 */
fun RobotGesture.toOneShot(): RobotOneShot? = when (this) {
    RobotGesture.TAP -> RobotOneShot.BOUNCE
    RobotGesture.SWIPE_X -> RobotOneShot.SPIN
    RobotGesture.SWIPE_Y -> RobotOneShot.BURST
    RobotGesture.HOLD -> null
}

/**
 * 挂在全屏 Robot 上的手势识别：单击 / 横滑 / 纵滑 / 长按。
 *
 * 判定即消费（[androidx.compose.ui.input.pointer.PointerInputChange.consume]），
 * 避免同一串事件再被下层（宠物游走层）重复消费。
 *
 * @param enabled 面板打开 / debug 覆盖时应传 false：`requireUnconsumed = false` 连已被消费的 down
 *   也收，不关掉会在「点面板」时顺带逗一下 Robot。
 */
fun Modifier.robotPlayGestures(
    enabled: Boolean,
    onGesture: (RobotGesture) -> Unit,
): Modifier = if (!enabled) {
    Modifier
} else {
    pointerInput(Unit) {
        val slopPx = 12.dp.toPx()
        val holdMs = 500L
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            down.consume()
            val start = down.position
            var gesture: RobotGesture? = null
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                change.consume()
                if (!change.pressed) break
                if (gesture != null) continue // 已判定：继续吃掉剩余事件直到抬手
                val d = change.position - start
                gesture = when {
                    d.getDistance() > slopPx -> if (abs(d.x) > abs(d.y)) {
                        RobotGesture.SWIPE_X
                    } else {
                        RobotGesture.SWIPE_Y
                    }
                    change.uptimeMillis - down.uptimeMillis >= holdMs -> RobotGesture.HOLD
                    else -> null
                }
            }
            onGesture(gesture ?: RobotGesture.TAP)
        }
    }
}
