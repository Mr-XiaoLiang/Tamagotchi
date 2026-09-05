package com.lollipop.tamagotchi.core.attribute

/**
 * 属性注册表核心值对象（doc/01 §1.1/§1.3）。
 *
 * 三类属性：主属性（可于主环展示）/ 可变隐藏属性 / 不可变性格特质（开档骰定）。
 * 性格特质的取值范围是 0~1，且只随 PersonalityGenerator 骰定，不入 AttributeMap——
 * 因此本文件仅注册「会随时间/操作变化的 0~100 属性」，特质由 domain 的 Traits 承载。
 */
enum class AttributeRole {
    /** 主属性：satiation / mood / health（主环三段进度条，实时 0~100）。 */
    PRIMARY_MAIN,

    /** 可变隐藏属性：intelligence / hygiene（状态面板 + 图标，不进主环）。 */
    MUTABLE_HIDDEN,
}

/** 可变属性 ID（0~100 值域；值域/默认值统一见 [AttributeRegistry]）。 */
enum class AttributeId(val label: String) {
    SATIATION("饱腹度"),
    MOOD("心情度"),
    HEALTH("健康度"),
    INTELLIGENCE("智力"),
    HYGIENE("清洁度"),
}

/**
 * 属性元数据（注册表条目）。doc/01 §1.3 的 color/icon/decayTable 属 UI/结算扩展，
 * 由 presentation / settle 各自持有映射，core 保持纯值对象（不引 Android 资源）。
 */
data class AttributeMeta(
    val id: AttributeId,
    val role: AttributeRole,
    /** 建档/缺省默认值（doc/01 §1；建档初始快照以此构建，见 M3.S2）。 */
    val defaultStart: Float,
    /** 表现下限，归零无恶劣后果（doc/01 §1.3 floor）。 */
    val floor: Float = 0f,
    /** 只增不减（doc/01 §4.1：智力学了不随时间衰减，仅学习增长）。 */
    val monotonicOnly: Boolean = false,
) {
    /** 值域上限（doc/01 §1.2 统一 0~100）。 */
    val ceiling: Float = 100f

    init {
        require(defaultStart in floor..ceiling) {
            "defaultStart $defaultStart out of [$floor, $ceiling] for ${id.name}"
        }
    }

    /** 收敛到 [floor, ceiling]。 */
    fun clamp(v: Float): Float = v.coerceIn(floor, ceiling)
}

/**
 * 属性注册表（doc/01 §1.3）：新增「类似的属性」只需在此加一条 Meta。
 * 建档初值均为「新手友好」取中——开场即可互动（喂食需 sat<95、玩耍需 mood<90，
 * 见 doc/01 §10），健康/清洁给足余量。数值建议初值，可调（doc/01 §11 已回写）。
 */
object AttributeRegistry {

    /** 建档默认：饱腹 80 / 心情 80 / 健康 100 / 智力 0 / 清洁 100。 */
    private const val DEFAULT_SATIATION = 80f
    private const val DEFAULT_MOOD = 80f
    private const val DEFAULT_HEALTH = 100f
    private const val DEFAULT_INTELLIGENCE = 0f
    private const val DEFAULT_HYGIENE = 100f

    /** 全部注册条目（顺序即面板展示顺序）。 */
    val all: List<AttributeMeta> = listOf(
        AttributeMeta(AttributeId.SATIATION, AttributeRole.PRIMARY_MAIN, DEFAULT_SATIATION),
        AttributeMeta(AttributeId.MOOD, AttributeRole.PRIMARY_MAIN, DEFAULT_MOOD),
        AttributeMeta(AttributeId.HEALTH, AttributeRole.PRIMARY_MAIN, DEFAULT_HEALTH),
        AttributeMeta(
            AttributeId.INTELLIGENCE,
            AttributeRole.MUTABLE_HIDDEN,
            DEFAULT_INTELLIGENCE,
            monotonicOnly = true, // doc/01 §4.1：学了不随时间衰减，只增不减
        ),
        AttributeMeta(AttributeId.HYGIENE, AttributeRole.MUTABLE_HIDDEN, DEFAULT_HYGIENE),
    )

    private val byId: Map<AttributeId, AttributeMeta> = all.associateBy { it.id }

    /** 主属性（主环三段进度条）。 */
    val main: List<AttributeMeta> = all.filter { it.role == AttributeRole.PRIMARY_MAIN }

    /** 可变隐藏属性（状态面板）。 */
    val hidden: List<AttributeMeta> = all.filter { it.role == AttributeRole.MUTABLE_HIDDEN }

    /** 按 ID 取注册条目（未注册会抛错——注册表驱动，杜绝散落魔法属性）。 */
    fun meta(id: AttributeId): AttributeMeta = byId.getValue(id)

    /** 该属性的建档默认值（缺档/建档兜底）。 */
    fun defaultOf(id: AttributeId): Float = meta(id).defaultStart

    /** 建档初始快照（M3.S2 PetStore 建档用）。 */
    fun initialSnapshot(): AttributeMap =
        AttributeMap.of(all.associate { it.id to it.defaultStart })
}
