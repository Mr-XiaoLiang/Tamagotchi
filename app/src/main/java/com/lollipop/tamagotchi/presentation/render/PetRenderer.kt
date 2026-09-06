package com.lollipop.tamagotchi.presentation.render

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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
import kotlin.math.abs
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

    /** 动作短演出时长 tick 数（250ms×10 = 2.5s，doc/02 §1.1 短状态 2~3s）。 */
    const val FX_TICKS = 10L
}

/**
 * 情绪层总开关（doc/07 §3）：置 false 即整体停用情绪形变（scale/rotate/translate + SICK tint），
 * **不破坏**任何 FSM 状态、动作演出或气泡浮字——仅退化为「基底帧 + 呼吸」。用于真机/调试关闭表现。
 */
object EmotionLayer { var enabled = true }

/** 情绪枚举（doc/07 §3）：无表情素材，用传统拉伸/倾斜表达情绪。 */
private enum class Emotion {
    NONE, HAPPY, SAD, ANGRY, TIRED, SICK, CURIOUS, SHY, STARTLED,
}

/** 一次情绪形变参数（围绕模型中心施加，doc/07 §3）。 */
private data class PoseTransform(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationDeg: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    companion object { val IDENTITY = PoseTransform() }
}

/** 动作短演出 → 情绪（层2 由动作触发的临时情绪；其余动作态由基底/脚本处理）。 */
private fun fxEmotion(kind: FxKind): Emotion = when (kind) {
    FxKind.EXCITED -> Emotion.HAPPY
    FxKind.AFFECTION -> Emotion.SHY
    FxKind.TREATED -> Emotion.NONE
    FxKind.EATING -> Emotion.NONE
    FxKind.CLEANING -> Emotion.NONE
    FxKind.STUDYING -> Emotion.NONE
}

/** FSM 持久状态 → 情绪（doc/07 §3 触发场景）。 */
private fun stateEmotion(state: PetState): Emotion = when (state) {
    PetState.SAD -> Emotion.SAD
    PetState.SICK -> Emotion.SICK
    PetState.SLEEPING -> Emotion.TIRED
    else -> Emotion.NONE
}

/**
 * 情绪 → 参数化形变脚本（doc/07 §3）：纯数值（amplitude/period），不新增状态机逻辑。
 * [tick] 为主循环相位（250ms/tick）。[side] = 模型边长（px），用于把比例换算成位移。
 */
private fun emotionTransform(e: Emotion, tick: Long, side: Float): PoseTransform {
    val p = tick.toDouble()
    val TAU = 2.0 * PI
    return when (e) {
        Emotion.NONE -> PoseTransform.IDENTITY
        // HAPPY 蹦跳：纵 scale 1→1.25 交替上弹（约 750ms/跳），~2.5s 演出含 2~3 跳
        Emotion.HAPPY -> {
            val bounce = abs(sin(p / 3.0 * TAU)).toFloat()
            PoseTransform(scaleX = 1f, scaleY = 1f + 0.25f * bounce, offsetY = -side * 0.12f * bounce)
        }
        // SAD 压扁：横 1.2 / 纵 0.8，低频下沉
        Emotion.SAD -> PoseTransform(scaleX = 1.2f, scaleY = 0.8f, offsetY = side * 0.05f)
        // ANGRY 横向抽动：±2% 快速抖动（250ms 周期）
        Emotion.ANGRY -> {
            val j = sin(p / 1.0 * TAU).toFloat()
            PoseTransform(scaleX = 1f + 0.02f * j, offsetX = side * 0.02f * j)
        }
        // TIRED 瞌睡点头：绕中心 ±4°，约 800ms 周期
        Emotion.TIRED -> PoseTransform(rotationDeg = (sin(p / 3.2 * TAU) * 4.0).toFloat())
        // SICK 微弱战栗 + 变暗（tint 由绘制层叠加）
        Emotion.SICK -> {
            val t = sin(p / 2.0 * TAU).toFloat()
            PoseTransform(scaleY = 1f + 0.02f * t, offsetX = side * 0.01f * t)
        }
        // CURIOUS 前倾歪头：固定 8° + 向屏前探 4%
        Emotion.CURIOUS -> PoseTransform(rotationDeg = 8f, offsetX = side * 0.06f)
        // SHY 侧头躲闪：偏移出再快速回正
        Emotion.SHY -> {
            val d = sin(p / 4.0 * TAU).toFloat()
            PoseTransform(offsetX = side * 0.07f * d)
        }
        // STARTLED 受惊跳起（预留随机惊吓事件触发）
        Emotion.STARTLED -> {
            val b = abs(sin(p / 3.0 * TAU)).toFloat()
            PoseTransform(scaleY = 1f + 0.3f * b, offsetY = -side * 0.12f * b)
        }
    }
}

/** 动作短演出类别（doc/02 §1.1：短动作态 EATING/EXCITED 不落持久快照，表现层临时演出）。 */
enum class FxKind { EATING, EXCITED, AFFECTION, TREATED, CLEANING, STUDYING }

/** 浮字条目：属性变化反馈（如「+12 饱腹」），颜色随属性语义色。 */
data class FxFloat(
    val text: String,
    val color: Color,
)

/**
 * 一次动作短演出指令（由上层动作事件构建后喂给 [PetLivingSprite]）。
 * [nonce] 为上层动作事件自增号：同内容连续演出也能可靠重启。
 * [floats] 自下而上依次上浮（如 饱腹 +、心情 +、清洁 −）。
 */
data class PetFx(
    val nonce: Long,
    val kind: FxKind,
    val bubble: String,
    val floats: List<FxFloat> = emptyList(),
)

/** 短演出运行记录：仅内存，随 tick 相位推进（running 停时不动）。 */
private data class FxRun(
    val fx: PetFx,
    val startTick: Long,
) {
    /** 相位 0..1（含），结束返回 NaN。 */
    fun progress(tick: Long): Float {
        val d = tick - startTick
        return if (d in 0 until PetRenderer.FX_TICKS) (d + 1f) / PetRenderer.FX_TICKS else Float.NaN
    }
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
    fx: PetFx? = null,
) {
    val seed = profile.personality.seed
    val fsm = remember(seed) { BehaviorFSM(seed = seed) }
    // tick 间连续快照（协程内读写，不参与重组）：仅首次建档/换宠时初始化；
    // 同宠档案刷新（M5.S2 settle apply）走下方软合并——保留行走位置，只对齐状态字段，
    // 避免宠物每次结算瞬移回档位坐标。
    var work by remember { mutableStateOf(profile) }
    // 当前渲染帧（每 tick 写一次；组合期订阅 → 低帧重组重绘，UI 树其它节点不动）
    var living by remember { mutableStateOf(restingPose(profile)) }
    // 动作短演出运行记录（内存态，不落盘；随 tick 相位推进，面板覆盖暂停）
    var fxRun by remember { mutableStateOf<FxRun?>(null) }

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

    // fx 装填与「开演」解耦，消除首条演出偶发丢失（doc/06 §6 反馈竞态）：
    // 旧实现用 LaunchedEffect(fx, running) 且内部 !running 直接 return —— 首次交互面板是开着的
    // （running=false），fx 抵达即被吞，需等 closeSheet() 把 running 翻转为真、该 effect 因 key 变化
    // 重入补写 fxRun；这条「等翻转再补播」的链路在重组时序上偶发漏跑，表现为「点了没反应」。
    // 现改为：fx 抵达即按 nonce 去重入队 [pendingFx]（与 running 无关，必定装填）；
    // 真正开演交给下方 tick 循环在 running 时消费 [pendingFx]。已消费即清空，面板反复开合不重播。
    var lastFxNonce by remember { mutableStateOf(-1L) }
    var pendingFx by remember { mutableStateOf<PetFx?>(null) }
    LaunchedEffect(fx) {
        if (fx == null) return@LaunchedEffect
        if (fx.nonce == lastFxNonce) return@LaunchedEffect
        lastFxNonce = fx.nonce
        pendingFx = fx
    }

    LaunchedEffect(fsm, running, profile) {
        if (!running) return@LaunchedEffect
        while (true) {
            val now = System.currentTimeMillis()
            // running 才开演；消费排队的 fx（仅当无进行中演出时），避免面板覆盖/后台时抢演
            val due = pendingFx
            if (due != null && fxRun == null) {
                pendingFx = null
                fxRun = FxRun(due, startTick = living.tick)
            }
            val run = fxRun
            if (run != null && !run.progress(living.tick).isNaN()) {
                // 短演出：冻结 FSM（行为不推进、不移动），沿当前坐标原地演出；
                // tick 照常递增（相位驱动呼吸/蹦跳/气泡/浮字）。
                living = LivingPose(
                    tick = living.tick + 1,
                    pose = living.pose.copy(state = PetState.IDLE, frame = 0),
                )
            } else {
                if (run != null) fxRun = null
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
            }
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
        // 动作短演出：相位 0..1；NaN = 无演出
        val activeFx = fxRun
        val fxPhase = activeFx?.progress(frame.tick) ?: Float.NaN
        val inFx = !fxPhase.isNaN()
        val sleeping = !inFx && pose.state == PetState.SLEEPING
        // 呼吸（2s 往返，tick 相位 8 步）；行走帧自带步态、睡眠冻结
        val breath = if (sleeping || pose.state == PetState.WALKING) {
            0f
        } else {
            cos(2.0 * PI * frame.tick / (PetRenderer.BREATH_TICKS_HALF * 2)).toFloat()
        }
        // ── 层2 情绪修饰（doc/07 §3）：由 state（SAD/SICK/SLEEPING→TIRED）或
        //    动作短演出（EXCITED→HAPPY / AFFECTION→SHY）派生；SICK 附灰 tint。
        //    可由 [EmotionLayer.enabled] 整体停用，不破坏状态/演出。
        val emotion = if (inFx) {
            fxEmotion(activeFx!!.fx.kind)
        } else {
            stateEmotion(pose.state)
        }
        val et = if (EmotionLayer.enabled) emotionTransform(emotion, frame.tick, dstSide) else PoseTransform.IDENTITY
        // ── 层3 短脚本：动作专属小动效（与情绪层正交）；EATING 低头咀嚼、TREATED 轻微摇摆 ──
        var scriptExtraY = 0f
        var scriptSway = 0f
        if (inFx) {
            when (activeFx!!.fx.kind) {
                FxKind.EATING -> scriptExtraY = sin(fxPhase * 4.0 * PI).toFloat() * dstSide * 0.04f
                FxKind.TREATED -> scriptSway = sin(fxPhase * 2.0 * PI).toFloat() * 3f
                else -> Unit
            }
        }
        val yOff = breath * PetRenderer.IDLE_AMP_PX + scriptExtraY
        // 模型目标矩形（中心化；垂直位移/情绪形变由下方 withTransform 统一施加，模型不越屏由
        // 映射扣半径 + 屏圆 clip 保证，doc/07 §9「形变垫层」——形变仅放大整体、不裁源图）
        val dst = Rect(
            left = cx - dstSide / 2f,
            top = cy - dstSide / 2f,
            right = cx + dstSide / 2f,
            bottom = cy + dstSide / 2f,
        )
        // 层1 帧用法（doc/07 §4）：WALKING 用 FSM 帧循环，其余基底用帧 0；演出沿用静止基底帧
        val dir = if (sleeping || inFx) {
            SpriteSheetDecoder.Dir.Down
        } else {
            SpriteSheetDecoder.Dir.of(pose.dir.ordinal)
        }
        val srcCol = if (!inFx && pose.state == PetState.WALKING) pose.frame else 0
        val frameSrc = SpriteSheetDecoder.frameRect(dir, srcCol)
        // 情绪 + 脚本统一变换：绕模型中心 scale→rotate→整体位移（doc/07 §3 的 Save/Load 等价）
        val totalRotation = et.rotationDeg + scriptSway
        // TIRED 点头绕「脚底中心」，其余情绪绕模型中心（doc/07 §3）
        val rotPivotY = if (emotion == Emotion.TIRED && EmotionLayer.enabled) cy + dstSide / 2f else cy
        withTransform({
            translate(left = et.offsetX, top = yOff + et.offsetY)
            rotate(degrees = totalRotation, pivot = Offset(cx, rotPivotY))
            scale(scaleX = et.scaleX, scaleY = et.scaleY, pivot = Offset(cx, cy))
        }) {
            drawPetFrame(image = sheet, src = frameSrc, dst = dst)
            // SICK 灰 tint：Multiply 仅作用于不透明像素，透明留白不受影响（doc/07 §3）
            if (emotion == Emotion.SICK && EmotionLayer.enabled) {
                drawRect(
                    color = Color(0xFF8C8C8C),
                    topLeft = dst.topLeft,
                    size = dst.size,
                    alpha = 0.35f,
                    blendMode = BlendMode.Multiply,
                )
            }
            // 睡眠「闭眼」暗罩（素材无睡姿帧；rotate 躺姿留真机复核）
            if (sleeping) {
                drawCircle(
                    color = ColorToken.bg.copy(alpha = PetRenderer.SLEEP_DIM_ALPHA),
                    radius = dstSide / 2f,
                    center = Offset(cx, cy),
                )
            }
            // 层3 装饰：食物包 / 药丸 / 亲昵爱心（随宠物，简单矢量，doc/07 §4）
            if (inFx) {
                drawScriptDecor(
                    kind = activeFx!!.fx.kind,
                    cx = cx,
                    cy = cy,
                    side = dstSide,
                )
            }
        }
        if (sleeping) {
            drawSleepZzz(
                textMeasurer = textMeasurer,
                tick = frame.tick,
                baseX = cx,
                baseY = cy - dstSide / 2f,
                side = dstSide,
            )
        }
        if (inFx) {
            // 气泡 + 数值浮字（不随宠物形变，doc/06 §7/§8；文字不透明 ≥11sp）
            drawActionFx(
                textMeasurer = textMeasurer,
                fx = activeFx!!.fx,
                progress = fxPhase,
                cx = cx,
                dst = dst,
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
    val layout = textMeasurer.measure("Z", style)
    repeat(3) { i ->
        // 每颗相位：0.8s 升程、0.8s/3 交错起步 → 视觉上三颗连续上浮
        val zi = (tickSec / 0.8f + i * 0.333f) % 1f
        // 透明度方向：靠近宝可梦(头部, zi≈0)不透明、越往上飘(zi→1)越淡，
        // 经典 zzz 上浮消散。头部极短淡入避免硬冒出；顶点附近淡出使循环回收不可见。
        // 注意：此前写成「头部淡入/顶点淡出」会把视觉流误导成向下，已纠正。
        val alpha = when {
            zi < 0.1f -> zi / 0.1f
            zi > 0.6f -> (1f - zi) / 0.4f
            else -> 1f
        }.coerceIn(0f, 1f)
        val x = baseX + sin(zi * 2.0 * PI).toFloat() * side * 0.08f
        // 方向：随 zi 增大 y 增大 = 朝屏上方浮动（按真机观察已翻转，原 baseY - zi*rise 被感知为向下）。
        val y = baseY + zi * rise - layout.size.height
        drawText(
            layout,
            topLeft = Offset(x - layout.size.width / 2f, y),
            color = ColorToken.Text2.copy(alpha = alpha),
        )
    }
}

/**
 * 动作短演出反馈（doc/06 §7 气泡 + §8.1 数值浮字）：
 * 气泡 = 不透明黑底圆角胶囊 + Accent 字（文字 ≥12sp、不透明，不做透明度渐变）；
 * 浮字 = 属性语义色加粗字（11sp 起步），自气泡上方逐条竖直排列、随相位整体上浮，
 * 用位移表达动效、不用字面低 alpha（doc/06 §8 最小字号/不透明约束）。气泡悬在宠物上方，
 * 顶部空间不足时翻到下方，避免越出圆屏被裁。
 */
private fun DrawScope.drawActionFx(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    fx: PetFx,
    progress: Float,
    cx: Float,
    dst: Rect,
) {
    val bubbleStyle = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = ColorToken.Accent,
    )
    val bubble = textMeasurer.measure(fx.bubble, bubbleStyle)
    val padX = 8.dp.toPx()
    val padY = 4.dp.toPx()
    val bw = bubble.size.width + padX * 2f
    val bh = bubble.size.height + padY * 2f
    val gap = 4.dp.toPx()
    val placeAbove = dst.top - gap - bh >= 8f
    val bubbleTop = if (placeAbove) dst.top - gap - bh else dst.bottom + gap
    val left = (cx - bw / 2f).coerceIn(4f, (size.width - bw - 4f).coerceAtLeast(4f))
    drawRoundRect(
        color = ColorToken.bg.copy(alpha = 0.92f),
        topLeft = Offset(left, bubbleTop),
        size = Size(bw, bh),
        cornerRadius = CornerRadius(bh / 2f, bh / 2f),
    )
    drawText(bubble, topLeft = Offset(left + padX, bubbleTop + padY))

    if (fx.floats.isEmpty()) return
    // 浮字：气泡上方自下而上堆叠、随相位整体上浮
    val rise = progress * 22.dp.toPx()
    var accH = 0f
    fx.floats.forEach { f ->
        val style = TextStyle(
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = f.color,
        )
        val layout = textMeasurer.measure(f.text, style)
        accH += layout.size.height + 3.dp.toPx()
        val y = bubbleTop - accH - rise
        if (y >= 2f) {
            val x = (cx - layout.size.width / 2f).coerceIn(
                2f,
                (size.width - layout.size.width - 2f).coerceAtLeast(2f),
            )
            drawText(layout, topLeft = Offset(x, y))
        }
    }
}

/**
 * 层3 短脚本装饰（doc/07 §4）：随宠物绘制的简单矢量小物件，不依赖表情素材。
 * - EATING：嘴前食物包（圆角方块）；TREATED：药丸（胶囊）；AFFECTION：亲昵爱心；
 *   EXCITED 蹦跳由情绪层承担，无需额外装饰。随宠物整体形变（同 withTransform）一致运动。
 */
private fun DrawScope.drawScriptDecor(
    kind: FxKind,
    cx: Float,
    cy: Float,
    side: Float,
) {
    val r = side * 0.16f
    when (kind) {
        FxKind.EATING -> {
            // 食物包：嘴前（右上方）小圆角方块
            val fx = cx + side * 0.22f
            val fy = cy - side * 0.18f
            drawRoundRect(
                color = ColorToken.FoodBalanced,
                topLeft = Offset(fx - r, fy - r),
                size = Size(r * 2, r * 2),
                cornerRadius = CornerRadius(r * 0.4f, r * 0.4f),
            )
        }
        FxKind.TREATED -> {
            // 药丸：胶囊（左上）
            val px = cx - side * 0.24f
            val py = cy - side * 0.16f
            drawRoundRect(
                color = ColorToken.Health,
                topLeft = Offset(px - r, py - r * 0.6f),
                size = Size(r * 2, r * 1.2f),
                cornerRadius = CornerRadius(r * 0.6f, r * 0.6f),
            )
        }
        FxKind.AFFECTION -> {
            // 亲昵爱心（右上），简单心形 path
            val hx = cx + side * 0.24f
            val hy = cy - side * 0.20f
            val s = r * 0.9f
            val heart = Path().apply {
                moveTo(hx, hy + s * 0.3f)
                cubicTo(hx - s, hy - s * 0.6f, hx - s * 0.5f, hy - s * 1.1f, hx, hy - s * 0.3f)
                cubicTo(hx + s * 0.5f, hy - s * 1.1f, hx + s, hy - s * 0.6f, hx, hy + s * 0.3f)
                close()
            }
            drawPath(heart, ColorToken.Mood)
        }
        FxKind.CLEANING -> {
            // 泡泡：嘴前几颗小圆（M11.S2 占位视觉，真机定稿）
            val bx = cx + side * 0.20f
            val by = cy - side * 0.16f
            repeat(3) { i ->
                drawCircle(
                    color = ColorToken.Hygiene,
                    radius = r * (0.5f - i * 0.12f),
                    center = Offset(bx + i * side * 0.06f, by - i * side * 0.05f),
                )
            }
        }
        FxKind.EXCITED -> Unit
        FxKind.STUDYING -> Unit
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
