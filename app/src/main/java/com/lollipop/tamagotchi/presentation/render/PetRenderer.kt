package com.lollipop.tamagotchi.presentation.render

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.lollipop.tamagotchi.core.behavior.PetState
import com.lollipop.tamagotchi.core.motion.NormalizedPos
import com.lollipop.tamagotchi.domain.engine.BehaviorFSM
import com.lollipop.tamagotchi.domain.engine.FSMResult
import com.lollipop.tamagotchi.domain.model.PetPosition
import com.lollipop.tamagotchi.domain.model.PetProfile
import com.lollipop.tamagotchi.presentation.theme.ColorToken
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 宠物渲染器 v1（Task.md M4.S2；doc/07 §4/§5）。
 *
 * v0 → v1 的变化：静态占位 [PetStaticSprite] 升级为**跑动状态机驱动的动态宠物**：
 * - [PetLivingSprite] 拥有 250ms 主循环（`LaunchedEffect + delay`，doc/07 §5）：
 *   每 tick `BehaviorFSM.step(now, workSnapshot)` → 产出 [LivingPose]（FSMResult + 相位号）；
 *   呼吸 / 睡眠 Zzz 的相位由 tick 推进（静态 4FPS 低帧，不再是 60FPS 无限过渡动画）。
 * - 帧用法（doc/07 §4 层1）：IDLE=首帧+呼吸；WALKING=dir 行 × frame 列循环 0→3；
 *   SLEEPING=首帧 + 暗罩（近似「闭眼」，素材无睡姿帧，M10.S2 真机复核，见 doc/07 §9 新增风险行）
 *   + Zzz 粒子；SICK/SAD/EATING/EXCITED（M6/M10 接入）先按静止基底帧+呼吸兜底。
 * - 坐标映射（doc/02 §2.1，M4.S2 布局调整：宠物全屏叠层）：canvas = 全屏方形（调用方圆形 clip），
 *   FSM 归一化 pos（可活动圆 R=1）→ px 偏移 = pos × (屏半径 − 模型半径 − 呼吸余量)，
 *   保证模型本体永不越出屏幕；主环 / 三向把手是上层 overlay，允许宠物从其下方重叠穿过。
 * - 模型本体尺寸 = 屏直径 × [MODEL_DIA_FRACTION] × [DST_FILL]（≈0.30 屏直径，doc/06 §1），
 *   可跑范围（全屏）与模型尺寸是两个量，不随全屏放大而放大。
 * - 睡眠闭合圆遮罩叠于模型上（约「闭眼」）；Zzz 从头顶上浮（0.8s 一颗、交错重生）。
 *
 * 过滤策略不变：源帧放大 FilterQuality.None（近邻像素，doc/07 §9；真机定稿 M10.S2）。
 * Bubble 层（doc/06 §7）为独立 UI 概念：睡眠 Zzz 属渲染脚本，气泡挂点等 M6/M9 文案事件接入。
 */
object PetRenderer {

    /** IDLE 呼吸幅度：纵向 ±2px（字面像素；doc/07 §4.4，真机观感 M4.S2 可复核调参）。 */
    const val IDLE_AMP_PX = 2f

    /** IDLE 呼吸半程时长 ms（往返 2s 周期 = 8 tick @250ms；tick 相位即 cos(2π·tick/8)）。 */
    const val IDLE_HALF_MS = 1000

    /** 呼吸单程所需 tick 数（250ms×4=1s 半程；整周期 8 tick=2s）。 */
    const val BREATH_TICKS_HALF = 4L

    /** 模型直径占屏直径比例 ≈0.30（doc/06 §1；canvas=全屏方形时即 0.30）。 */
    const val MODEL_DIA_FRACTION = 0.30f

    /** 目标矩形占模型尺寸比例（留 2% 透明余量：呼吸/亚像素不裁到透明区破边）。 */
    const val DST_FILL = 0.96f

    /** 睡眠暗罩（近似闭眼）不透明度（底色 [ColorToken.bg]，叠模型上变暗）。 */
    const val SLEEP_DIM_ALPHA = 0.45f
}

/** 一次渲染帧的状态：FSM 结果 + tick 相位号（呼吸/Zzz 低帧推进源）。 */
data class LivingPose(
    val tick: Long,
    val pose: FSMResult,
)

/** 首帧静止姿态（主循环首个 step 前的瞬时渲染，避免黑帧）。 */
private fun restingPose(profile: PetProfile): LivingPose {
    val p = profile.position
    return LivingPose(
        tick = 0L,
        pose = FSMResult(
            state = profile.fsmState,
            dir = p.dir,
            frame = 0,
            position = NormalizedPos(p.x, p.y),
        ),
    )
}

/**
 * 动态宠物（M4.S2 主循环接线）。
 *
 * @param profile 领域快照（seed→FSM；fsmState/position 为自决策与渲染起始输入）。
 *   本组件在内存中推进工作副本（position/fsmState/isAsleep），**不落盘**（跑动坐标是实时表现；
 *   M5 settle 以属性与作息为准；持久化/离线语义在 M5.S2 接入）。
 * @param sheet 整表 256×256 位图；null 表示素材未就绪（保留占位不绘）。
 * @param running 循环闸：false 时停循环（0 CPU），恢复后从上次状态继续
 *   （心跳 cap 单步 ≤250ms，不跨帧跳跃）。由上层接 onPause/onStop 与 overlay 覆盖状态。
 * @param modifier 全屏方形画布（调用方 `size(minSide)` + 圆 clip = 物理屏圆）；
 *   宠物位移范围与恒不出屏由 FSM clamp（R=1）+ 本组件映射（扣模型半径/呼吸余量）保证。
 */
@Composable
fun PetLivingSprite(
    profile: PetProfile,
    sheet: ImageBitmap?,
    running: Boolean,
    modifier: Modifier = Modifier,
) {
    val seed = profile.personality.seed
    val fsm = remember(seed) { BehaviorFSM(seed = seed) }
    // tick 间连续快照（协程内读写，不参与重组）：仅首次建档/换宠时初始化；
    // 同宠档案刷新（M5.S2 settle apply）走下方软合并——保留行走位置，只对齐状态字段，
    // 避免宠物每次结算瞬移回档位坐标。
    var work by remember { mutableStateOf(profile) }
    // 当前渲染帧（每 tick 写一次；组合期订阅 → 低帧重组重绘，UI 树其它节点不动）
    var living by remember { mutableStateOf(restingPose(profile)) }

    // profile 变化：同 petId = settle apply/建档快照刷新 → 软合并（跑动坐标/行走进度延续）；
    // 异 petId = 换宠/重开档 → 整档对齐。
    LaunchedEffect(profile) {
        if (work.petId == profile.petId) {
            work = work.copy(
                attributes = profile.attributes,
                sadDurationHours = profile.sadDurationHours,
                milestones = profile.milestones,
                fsmState = profile.fsmState,
            )
            if (profile.fsmState == PetState.SLEEPING) work = work.copy(isAsleep = true)
        } else {
            work = profile
            living = restingPose(profile)
        }
    }

    LaunchedEffect(fsm, running, profile) {
        if (!running) return@LaunchedEffect
        while (true) {
            val now = System.currentTimeMillis()
            val r = fsm.step(now, work)
            living = LivingPose(tick = living.tick + 1, pose = r)
            work = work.copy(
                position = PetPosition(
                    x = r.position.x,
                    y = r.position.y,
                    dir = r.dir,
                    frame = r.frame,
                ),
                fsmState = r.state,
                isAsleep = r.state == PetState.SLEEPING,
            )
            delay(BehaviorFSM.DEFAULT_TICK_MS)
        }
    }

    val textMeasurer = rememberTextMeasurer()
    // 组合期订阅 living（状态变量）：tick 变化 → 每 4FPS 轻量重组并触发重绘；
    // 帧内容变化（pose 更新）在同一节奏随重组生效。UI 树其它节点不受影响。
    val frame = living
    Canvas(modifier) {
        if (sheet == null) return@Canvas
        val pose = frame.pose
        val s = this.size.minDimension
        val dstSide = s * PetRenderer.MODEL_DIA_FRACTION * PetRenderer.DST_FILL
        // 全屏映射：pos=1 → 中心距屏心 = 屏半径−模型半径−呼吸余量（模型永不越屏，doc/02 §2.1）
        val reach = s / 2f - dstSide / 2f - PetRenderer.IDLE_AMP_PX
        val cx = size.width / 2f + pose.position.x * reach
        val cy = size.height / 2f + pose.position.y * reach
        val sleeping = pose.state == PetState.SLEEPING
        // 呼吸（2s 往返，tick 相位 8 步）；行走帧自带步态、睡眠冻结
        val breath = if (sleeping || pose.state == PetState.WALKING) {
            0f
        } else {
            cos(2.0 * PI * frame.tick / (PetRenderer.BREATH_TICKS_HALF * 2)).toFloat()
        }
        val yOff = breath * PetRenderer.IDLE_AMP_PX
        val dst = Rect(
            left = cx - dstSide / 2f,
            top = cy - dstSide / 2f + yOff,
            right = cx + dstSide / 2f,
            bottom = cy + dstSide / 2f + yOff,
        )
        // 层1 帧用法（doc/07 §4）：WALKING 用 FSM 帧循环，其余基底用帧 0
        val dir = if (sleeping) SpriteSheetDecoder.Dir.Down else SpriteSheetDecoder.Dir.of(pose.dir.ordinal)
        val srcCol = if (pose.state == PetState.WALKING) pose.frame else 0
        drawPetFrame(
            image = sheet,
            src = SpriteSheetDecoder.frameRect(dir, srcCol),
            dst = dst,
        )
        if (sleeping) {
            // 近似「闭眼」的暗罩（素材无睡姿帧；rotate 躺姿留 M10 真机复核）
            drawCircle(
                color = ColorToken.bg.copy(alpha = PetRenderer.SLEEP_DIM_ALPHA),
                radius = dstSide / 2f,
                center = Offset(cx, cy + yOff / 2f),
            )
            drawSleepZzz(
                textMeasurer = textMeasurer,
                tick = frame.tick,
                baseX = cx,
                baseY = cy - dstSide / 2f,
                side = dstSide,
            )
        }
    }
}

/**
 * 睡眠 Zzz 粒子（3 颗交错上浮，0.8s 一颗、0.8s 升程，随 tick 低帧推进）。
 * 字形不透明 [ColorToken.Text2] ≥11sp（doc/06 §8.1），为装饰性轨迹粒子。
 */
private fun DrawScope.drawSleepZzz(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    tick: Long,
    baseX: Float,
    baseY: Float,
    side: Float,
) {
    val style = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = ColorToken.Text2,
    )
    val tickSec = tick / 4f // 250ms/tick
    val rise = side * 0.9f
    repeat(3) { i ->
        // 每颗相位：0.8s 升程、0.8s/3 交错起步 → 视觉上三颗连续上浮
        val zi = (tickSec / 0.8f + i * 0.333f) % 1f
        val layout = textMeasurer.measure("Z", style)
        val x = baseX + sin(zi * 2.0 * PI).toFloat() * side * 0.08f
        val y = baseY - zi * rise - layout.size.height
        drawText(layout, topLeft = Offset(x - layout.size.width / 2f, y))
    }
}

/**
 * 单帧绘制（顶层扩展，DrawScope 内可直接调用）：
 * 把整表 [image] 的 [src] 子矩形画进目标矩形 [dst]。
 * [filterQuality] 默认近邻（None）；真机观感定稿见 doc/07 §9。
 */
fun DrawScope.drawPetFrame(
    image: ImageBitmap,
    src: Rect,
    dst: Rect,
    filterQuality: FilterQuality = FilterQuality.None,
) {
    drawImage(
        image = image,
        srcOffset = IntOffset(src.left.toInt(), src.top.toInt()),
        srcSize = IntSize(src.width.toInt(), src.height.toInt()),
        dstOffset = IntOffset(dst.left.toInt(), dst.top.toInt()),
        dstSize = IntSize(dst.width.toInt(), dst.height.toInt()),
        filterQuality = filterQuality,
    )
}
