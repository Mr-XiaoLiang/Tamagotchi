package com.lollipop.tamagotchi.presentation.face

import com.lollipop.grokbot.GrokShape
import com.lollipop.tamagotchi.domain.model.Traits

/**
 * Robot 表情的**体型选择**（doc/10 §2.5，D7 已拍板：随种类 / 性格变化，无法判定退回团块）。
 *
 * 纯函数、不进 domain —— [GrokShape] 是 `grokBot` 库的枚举，domain 不依赖 UI 库；
 * 但它是纯 Kotlin，可以直接写 JVM 单测。
 *
 * 三条规则（doc/10 §2.5）：
 * 1. **主判据 = 种类**：`petId` 稳定哈希 → 候选索引。**同一只宠永远同一体型**，
 *    不随重启、情绪、时间漂移（哈希自己实现，不依赖 `String.hashCode` 的实现细节）。
 * 2. **性格微调**：只有 `activity` 参与，且只在候选邻域内 ±1 偏移 —— 好动向更跳脱的轮廓、
 *    慵懒向更敦实的轮廓，**不跨类跳变**（避免「换个心情就换了个头」）。
 * 3. **兜底**：`petId` 空白 / traits 缺失 / 索引越界 → [GrokShape.BLOB]（团块，最接近圆形）。
 *
 * 体型在建档时即确定，换宠 / 从墓碑切回（M15）后随新 `petId` 重算；结果用 `remember(petId)` 缓存，
 * **不写 `PetProfile`**（与 D2 一致）。
 */
object RobotShapePicker {

    /**
     * 候选体型（有序）：只挑「圆润、适合当脸」的轮廓。
     *
     * 库里还有 `HEX/GEM/CRYSTAL/WEDGE/SHIELD/ARCH/LEAF` 等带棱角的形状，手表小屏下
     * 形体细节会糊成一团，且跟「团子宠物」的调性不合，故不放进候选 —— 想加只改这一行。
     * 顺序即「敦实 → 跳脱」的梯度，供 [Traits.activity] 邻域偏移使用。
     */
    private val CANDIDATES: List<GrokShape> = listOf(
        GrokShape.BLOB,      // 团块（兜底，最接近圆）
        GrokShape.PEBBLE,    // 卵石
        GrokShape.BEAN,      // 豆
        GrokShape.EGG,       // 蛋
        GrokShape.SQUIRCLE,  // 方圆形
        GrokShape.DOME,      // 穹顶
        GrokShape.CLOUD,     // 云
        GrokShape.TEARDROP,  // 水滴（最跳脱）
    )

    /** `activity` 高/低的分档阈值（0~1；中间档不偏移）。 */
    private const val ACTIVE_HIGH = 0.66f
    private const val ACTIVE_LOW = 0.33f

    /**
     * 选体型。
     *
     * @param petId 宠物种类 id（主判据）；空白或空串 → 兜底 [GrokShape.BLOB]。
     * @param traits 性格 6 维（只取 `activity` 做邻域偏移）；null → 不偏移。
     */
    fun pick(petId: String, traits: Traits? = null): GrokShape {
        if (petId.isBlank()) return GrokShape.BLOB
        // 偏移后**贴边 clamp**（不是回落到 BLOB）：否则最跳脱的体型被好动一推就越界、
        // 直接掉回团块，等于跨类跳变，违反「只在邻域内偏移」。
        val index = (stableIndex(petId) + activityBias(traits)).coerceIn(0, CANDIDATES.lastIndex)
        return CANDIDATES[index]
    }

    /**
     * 由 [petId] 稳定哈希出的候选索引。
     *
     * FNV-1a 32 位：实现简单、跨 JVM/Kotlin 版本结果固定，不依赖任何 JDK 实现细节；
     * 每步 `and 0x7fffffff` 保持非负，免得负数取模出负索引。
     */
    private fun stableIndex(petId: String): Int {
        // FNV-1a offset basis（0x811c9dc5 字面量超 Int 上限，故写成 Long 再截断；Int 乘法按二进制绕回）
        var hash = 0x811c9dc5.toInt()
        for (i in petId.indices) {
            hash = hash xor petId[i].code
            hash = (hash * 0x01000193) and 0x7fffffff
        }
        return hash % CANDIDATES.size
    }

    /** 性格偏移：好动 → 邻域 +1（更跳脱），慵懒 → −1（更敦实），中间不偏移。 */
    private fun activityBias(traits: Traits?): Int = when {
        traits == null -> 0
        traits.activity >= ACTIVE_HIGH -> 1
        traits.activity <= ACTIVE_LOW -> -1
        else -> 0
    }
}
