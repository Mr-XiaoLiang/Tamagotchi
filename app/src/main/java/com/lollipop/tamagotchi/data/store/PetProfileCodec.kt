package com.lollipop.tamagotchi.data.store

import com.lollipop.tamagotchi.core.attribute.AttributeMap
import com.lollipop.tamagotchi.core.attribute.AttributeRegistry
import com.lollipop.tamagotchi.core.attribute.FoodFlavor
import com.lollipop.tamagotchi.core.behavior.PetState
import com.lollipop.tamagotchi.core.motion.Direction
import com.lollipop.tamagotchi.domain.model.Cooldowns
import com.lollipop.tamagotchi.domain.model.Milestones
import com.lollipop.tamagotchi.domain.model.Personality
import com.lollipop.tamagotchi.domain.model.PetPlugins
import com.lollipop.tamagotchi.domain.model.PetPosition
import com.lollipop.tamagotchi.domain.model.PetProfile
import com.lollipop.tamagotchi.domain.model.Traits
import org.json.JSONArray
import org.json.JSONObject

/**
 * pet_profile 单键 JSON 编解码（doc/01 §9 schema v2）。
 *
 * 设计要点：
 * - 纯字符串 ↔ [PetProfile]，不触碰 Android SP / Context，JVM 单测可直接跑。
 * - 容错补默认：缺失/损坏字段一律回落中性默认（属性取注册表建档默认、FSM→IDLE、
 *   traits→0.5 中性、flavor→BALANCED、cooldowns/stats/plugins→空），杜绝坏档崩溃。
 * - 只写已注册属性键；未知键忽略（属性注册表驱动）。
 * - petId/petName 是档的身份，缺失视为坏档，由 [PetStore.load] 兜底返回无档。
 *
 * JSON 键与 doc/01 §9 逐字对齐（feed_until / sick_total / activity… 小写下划线）。
 */
internal object PetProfileCodec {

    /** 单键 SharedPreferences key。 */
    const val PREFS_KEY = "pet_profile"

    // ---- 编码（PetProfile → JSON 字符串） ----

    fun toJson(profile: PetProfile): String {
        val root = JSONObject()
        root.put("schemaVersion", profile.schemaVersion)
        root.put("petId", profile.petId)
        root.put("petName", profile.petName)
        root.put("createdAt", profile.createdAt)
        root.put("lastSettledAt", profile.lastSettledAt)

        root.put("attributes", attributesToJson(profile.attributes))
        root.put("fsmState", profile.fsmState.name)
        root.put("pos", positionToJson(profile.position))
        root.put(
            "sleep",
            JSONObject().put("isAsleep", profile.isAsleep),
        )
        root.put("sadDurationHours", number(profile.sadDurationHours))
        root.put("cooldowns", cooldownsToJson(profile.cooldowns))
        root.put("stats", milestonesToJson(profile.milestones))
        root.put("personality", personalityToJson(profile.personality))
        root.put("plugins", pluginsToJson(profile.plugins))
        return root.toString()
    }

    private fun attributesToJson(attributes: AttributeMap): JSONObject {
        val obj = JSONObject()
        // 注册表 all 顺序 = 面板顺序；键 = AttributeId.name 小写（satiation/mood/…）
        AttributeRegistry.all.forEach { meta ->
            val value = attributes[meta.id]
            obj.put(meta.id.name.lowercase(), number(value))
        }
        return obj
    }

    private fun positionToJson(pos: PetPosition): JSONObject = JSONObject()
        .put("x", number(pos.x))
        .put("y", number(pos.y))
        .put("dir", pos.dir.ordinal)
        .put("frame", pos.frame)

    private fun cooldownsToJson(c: Cooldowns): JSONObject = JSONObject()
        .put("feed_until", c.feedUntil)
        .put("play_until", c.playUntil)
        .put("pet_until", c.petUntil)
        .put("clean_until", c.cleanUntil)
        .put("study_until", c.studyUntil)

    private fun milestonesToJson(m: Milestones): JSONObject = JSONObject()
        .put("feed", m.feed)
        .put("play", m.play)
        .put("pet", m.pet)
        .put("heal", m.heal)
        .put("sick_total", m.sickTotal)
        .put("days_together", m.daysTogether)

    private fun personalityToJson(p: Personality): JSONObject {
        val root = JSONObject()
        root.put("seed", p.seed)
        val t = JSONObject()
        t.put("activity", p.traits.activity.toDouble())
        t.put("affinity", p.traits.affinity.toDouble())
        t.put("appetite", p.traits.appetite.toDouble())
        t.put("curiosity", p.traits.curiosity.toDouble())
        t.put("temper", p.traits.temper.toDouble())
        t.put("learner", p.traits.learner.toDouble())
        root.put("traits", t)
        root.put("flavor", p.flavor.name.lowercase())
        return root
    }

    private fun pluginsToJson(plugins: PetPlugins): JSONObject {
        val obj = JSONObject()
        obj.put("hidden", JSONArray(plugins.hidden))
        obj.put("sort", JSONArray(plugins.sort))
        return obj
    }

    /** 整数值按整型写（避免 80.0 这类噪音），小数写 double。 */
    private fun number(f: Float): Any =
        if (f % 1f == 0f) f.toLong() else f.toDouble()

    // ---- 解码（JSON 字符串 → PetProfile） ----

    /**
     * 容错解码：身份缺失（petId/petName 为空）返回 null，其余字段逐项回落默认。
     * JSON 语法损坏抛 [Exception]，由 [PetStore.load] 统一兜底为无档。
     */
    fun fromJson(raw: String): PetProfile? {
        val root = JSONObject(raw)
        val petId = root.optString("petId").trim()
        val petName = root.optString("petName").trim()
        if (petId.isEmpty() || petName.isEmpty()) return null

        return PetProfile(
            schemaVersion = root.optInt("schemaVersion", PetProfile.SCHEMA_VERSION),
            petId = petId,
            petName = petName,
            createdAt = root.optLong("createdAt", 0L),
            lastSettledAt = root.optLong("lastSettledAt", 0L),
            attributes = attributesFromJson(root.optJSONObject("attributes")),
            fsmState = enumOr(
                root.optString("fsmState", ""),
                PetState.entries,
                PetState.IDLE,
            ),
            position = positionFromJson(root.optJSONObject("pos")),
            isAsleep = root.optJSONObject("sleep")?.optBoolean("isAsleep", false) ?: false,
            sadDurationHours = root.optDouble("sadDurationHours", 0.0)
                .toFloat().coerceAtLeast(0f),
            cooldowns = cooldownsFromJson(root.optJSONObject("cooldowns")),
            milestones = milestonesFromJson(root.optJSONObject("stats")),
            personality = personalityFromJson(root.optJSONObject("personality")),
            plugins = pluginsFromJson(root.optJSONObject("plugins")),
        )
    }

    private fun attributesFromJson(obj: JSONObject?): AttributeMap {
        val values = AttributeRegistry.all.associate { meta ->
            val raw = obj?.optDouble(meta.id.name.lowercase(), Double.NaN)
                ?.takeIf { !it.isNaN() }?.toFloat()
            val v = raw ?: meta.defaultStart
            meta.id to meta.clamp(v)
        }
        return AttributeMap.of(values)
    }

    private fun positionFromJson(obj: JSONObject?): PetPosition {
        if (obj == null) return PetPosition()
        val x = obj.optDouble("x", 0.0).toFloat().coerceIn(-1f, 1f)
        val y = obj.optDouble("y", 0.0).toFloat().coerceIn(-1f, 1f)
        val dir = Direction.of(obj.optInt("dir", Direction.DOWN.ordinal))
        val frame = obj.optInt("frame", 0).coerceAtLeast(0)
        return PetPosition(x = x, y = y, dir = dir, frame = frame)
    }

    private fun cooldownsFromJson(obj: JSONObject?): Cooldowns {
        if (obj == null) return Cooldowns()
        return Cooldowns(
            feedUntil = obj.optLong("feed_until", 0L),
            playUntil = obj.optLong("play_until", 0L),
            petUntil = obj.optLong("pet_until", 0L),
            cleanUntil = obj.optLong("clean_until", 0L),
            studyUntil = obj.optLong("study_until", 0L),
        )
    }

    private fun milestonesFromJson(obj: JSONObject?): Milestones {
        if (obj == null) return Milestones()
        return Milestones(
            feed = obj.optLong("feed", 0L),
            play = obj.optLong("play", 0L),
            pet = obj.optLong("pet", 0L),
            heal = obj.optLong("heal", 0L),
            sickTotal = obj.optLong("sick_total", 0L),
            daysTogether = obj.optLong("days_together", 0L),
        )
    }

    private fun personalityFromJson(obj: JSONObject?): Personality {
        if (obj == null) return Personality(seed = 0L, traits = NEUTRAL_TRAITS, flavor = FoodFlavor.BALANCED)
        val traits = traitsFromJson(obj.optJSONObject("traits"))
        val flavor = enumOr(
            obj.optString("flavor", ""),
            FoodFlavor.entries,
            FoodFlavor.BALANCED,
        )
        return Personality(seed = obj.optLong("seed", 0L), traits = traits, flavor = flavor)
    }

    private fun traitsFromJson(obj: JSONObject?): Traits {
        if (obj == null) return NEUTRAL_TRAITS
        fun t(key: String, d: Float): Float =
            obj.optDouble(key, d.toDouble()).toFloat().coerceIn(0f, 1f)
        return Traits(
            activity = t("activity", NEUTRAL),
            affinity = t("affinity", NEUTRAL),
            appetite = t("appetite", NEUTRAL),
            curiosity = t("curiosity", NEUTRAL),
            temper = t("temper", NEUTRAL),
            learner = t("learner", NEUTRAL),
        )
    }

    private fun pluginsFromJson(obj: JSONObject?): PetPlugins {
        if (obj == null) return PetPlugins()
        fun list(key: String): List<String> {
            val arr = obj.optJSONArray(key) ?: return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
        return PetPlugins(hidden = list("hidden"), sort = list("sort"))
    }

    private fun <E : Enum<E>> enumOr(name: String, values: List<E>, fallback: E): E =
        values.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: fallback

    /** 残缺档补默认：中性 0.5（doc/01 §9「v1 迁移补 traits」；开档后不可变）。 */
    private const val NEUTRAL = 0.5f
    private val NEUTRAL_TRAITS = Traits(
        activity = NEUTRAL,
        affinity = NEUTRAL,
        appetite = NEUTRAL,
        curiosity = NEUTRAL,
        temper = NEUTRAL,
        learner = NEUTRAL,
    )
}
