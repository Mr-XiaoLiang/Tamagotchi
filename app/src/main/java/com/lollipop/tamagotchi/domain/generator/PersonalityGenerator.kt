package com.lollipop.tamagotchi.domain.generator

import com.lollipop.tamagotchi.core.attribute.FoodFlavor
import com.lollipop.tamagotchi.domain.model.Personality
import com.lollipop.tamagotchi.domain.model.TraitDescriptor
import com.lollipop.tamagotchi.domain.model.Traits
import kotlin.random.Random

/**
 * 性格生成器（doc/02 §4.2/§4.3）：seed → 6 维 traits(0~1) + flavor 派生 + 描述词映射。
 *
 * - 同 seed 派生全等且可复现（建档后 traits/flavor 落 SP，seed 留档可回溯）。
 * - traits 六维均匀取样 [0,1]；flavor 用独立抽取位从合法口味表均匀派生。
 * - 描述词 = 规则优先级匹配（doc §4.3 示例映射），高 ≥0.6 / 低 ≤0.4（假设，可调），
 *   未命中回退默认文案；仅为感知包装，不改任何数值决策。
 */
object PersonalityGenerator {

    /** 描述词判定阈值（doc §4.3 未给数值，此为设计假设，可调）。 */
    private const val HIGH = 0.6f
    private const val LOW = 0.4f

    private data class DescriptorRule(
        val primary: String,
        val secondary: String,
        val hit: (Traits) -> Boolean,
    )

    // 顺序即优先级（doc §4.3 表自上而下；先命中先返回）。
    private val rules = listOf(
        DescriptorRule("调皮", "活泼") { t -> t.activity >= HIGH && t.curiosity >= HIGH },
        DescriptorRule("文静", "温柔") { t -> t.activity <= LOW && t.temper >= HIGH },
        DescriptorRule("粘人", "温顺") { t -> t.affinity >= HIGH && t.activity <= LOW },
        DescriptorRule("睿智", "机灵") { t -> t.learner >= HIGH && t.curiosity >= HIGH },
        DescriptorRule("贪吃", "小吃货") { t -> t.appetite >= HIGH },
    )

    private val fallback = TraitDescriptor("温和", "淡定")

    /**
     * 由 seed 确定性派生完整性格（traits + flavor）。
     * 抽取位：前 6 次 = 六维 traits；第 7 次 = flavor（独立、合法表均匀）。
     */
    fun generate(seed: Long): Personality {
        val rnd = Random(seed)
        val traits = Traits(
            activity = rnd.nextFloat(),
            affinity = rnd.nextFloat(),
            appetite = rnd.nextFloat(),
            curiosity = rnd.nextFloat(),
            temper = rnd.nextFloat(),
            learner = rnd.nextFloat(),
        )
        val flavor = FoodFlavor.entries[rnd.nextInt(FoodFlavor.entries.size)]
        return Personality(seed = seed, traits = traits, flavor = flavor)
    }

    /** traits → 主副描述词（仅文案；同 traits 恒定同输出，顺序稳定）。 */
    fun describe(traits: Traits): TraitDescriptor =
        rules.firstOrNull { it.hit(traits) }
            ?.let { TraitDescriptor(it.primary, it.secondary) }
            ?: fallback
}
