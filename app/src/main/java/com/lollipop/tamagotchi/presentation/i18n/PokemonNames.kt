package com.lollipop.tamagotchi.presentation.i18n

import android.content.Context
import com.lollipop.tamagotchi.domain.model.PetDisplayName
import org.json.JSONObject
import java.util.Locale

/**
 * 名字映射表（M9）：素材文件名主名 → 宝可梦真实名字（中文优先、未收录降级英文）。
 *
 * 数据来自 [assets/pokemon_names.json]（由 tools/gen_pokemon_names.py 生成）：
 *   { "BULBASAUR": { "en": "Bulbasaur", "zh": "妙蛙种子" }, "PIKACHU_female": { "en": "Pikachu ♀", "zh": "皮卡丘" }, ... }
 * - key = 素材文件名主名（忠实文件名，含 `_female`/`_male` 与数字形态）；
 * - `zh`：翻译库，空（""）即未翻译；`en`：程序化美化得到（见 [PetDisplayName.enOf]），带性别符号。
 *
 * 用法：
 * 1) [BaseActivity.onCreate] 调一次 [init]（读 assets 进内存，幂等；失败不崩，回退美化英文名）；
 * 2) UI 列表项用 [display]（按语言取字符串）；建档/当前档把双语言 [lookup] 结果写进 [PetProfile]，
 *    之后 UI 直接 `profile.displayName.resolve(PokemonNames.isZh())` 取词，不再查本表。
 */
object PokemonNames {

    @Volatile
    private var loaded = false
    private val zhMap = HashMap<String, String>()

    /** 解析 assets/pokemon_names.json（仅需一次；失败也不崩，回退美化英文名）。 */
    fun init(context: Context) {
        if (loaded) return
        runCatching {
            context.assets.open("pokemon_names.json").bufferedReader().use { src ->
                val root = JSONObject(src.readText())
                val keys = root.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val zh = root.optJSONObject(key)?.optString("zh", "")?.takeIf { it.isNotBlank() }
                    if (zh != null) zhMap[key] = zh
                }
            }
        }
        loaded = true
    }

    /** 是否中文环境（简 / 繁均算）。 */
    fun isZh(): Boolean = Locale.getDefault().language.startsWith("zh", ignoreCase = true)

    /**
     * 按当前语言环境取展示名：中文环境优先真中文、否则 / 未收录回退真英文；
     * 性别模型（key 以 `_female`/`_male` 结尾）自动补 ♀/♂（除非译名已含符号）。
     */
    fun display(base: String, zh: Boolean): String {
        val key = base.removeSuffix(".png")
        val raw = if (zh) zhMap[key] ?: en(key) else en(key)
        return withGender(key, raw)
    }

    /**
     * 取双语言展示名（zh 优先，缺失时 en 补位），随模型持久化用。
     * 性别符号已含在返回值内（zh 译名无符号时由 [withGender] 补，en 由 [PetDisplayName.enOf] 补）。
     */
    fun lookup(base: String): PetDisplayName {
        val key = base.removeSuffix(".png")
        val rawZh = zhMap[key]
        val zh = if (rawZh != null) withGender(key, rawZh) else ""
        return PetDisplayName(zh = zh, en = PetDisplayName.enOf(base))
    }

    /** 文件名主名 → 官方中文名；未收录返回 null（调用方回退 [en]）。 */
    fun zh(base: String): String? = zhMap[base.removeSuffix(".png")]

    /** 文件名主名 → 规范英文名（程序化美化 + 性别符号）。 */
    fun en(base: String): String = PetDisplayName.enOf(base)

    /** 性别模型名补 ♀/♂（译名已含符号则不重复）。 */
    private fun withGender(key: String, name: String): String {
        return when {
            key.endsWith("_female") && !name.contains("♀") -> "$name ♀"
            key.endsWith("_male") && !name.contains("♂") -> "$name ♂"
            else -> name
        }
    }
}
