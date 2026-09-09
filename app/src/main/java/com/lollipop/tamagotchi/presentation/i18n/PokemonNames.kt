package com.lollipop.tamagotchi.presentation.i18n

import android.content.Context
import com.lollipop.tamagotchi.domain.model.PetDisplayName
import org.json.JSONObject
import java.util.Locale

/**
 * 名字 + 形态映射表（M9 → M? 进化/退化）：素材文件名主名 → 宝可梦真实名字（中文优先、未收录降级英文）
 * 与形态前后链（prev/next，数组以支持多源进化 / 多目标进化）。
 *
 * 数据来自 [assets/pokemon.json]（由 tools/gen_pokemon.py 生成，重命名自 pokemon_names.json）：
 *   {
 *     "ALCREMIE":     { "name": { "en": "Alcremie", "zh": "霜奶仙" }, "prev": ["MILCERY"], "next": [],            "form": false },
 *     "ALCREMIE_5":   { "name": { "en": "Alcremie", "zh": "霜奶仙" }, "prev": [],          "next": [],            "form": true  },
 *     "PIKACHU":      { "name": { "en": "Pikachu",  "zh": "皮卡丘" }, "prev": ["PICHU"],   "next": ["RAICHU"],    "form": false },
 *     "PIKACHU_female": { "name": {...},                                      "prev": [],  "next": [],            "form": true  }
 *   }
 * - key = 形态主名（忠实文件名；含 `_N` 数字形态后缀，或 `_female`/`_male` 性别后缀）；
 * - `name.zh`：翻译库，空（""）即未翻译；`name.en`：程序化美化得到（见 [PetDisplayName.enOf]），带性别符号；
 *   同一物种各形态共享该物种的 name（进化不改变名字）。
 * - `prev`/`next`：上一 / 下一**进化**形态主名数组（来自权威进化表 Pokemon-Showdown 的 prevo/evos）；
 *   空数组 = 初始形态 / 终极形态。**注意**：`_N` 数字形态（如 ALCREMIE 的 36 种颜色装饰）与
 *   `_female`/`_male` 性别形态是**同一物种的外观变体，不是进化**，故 `form=true` 且 prev/next 恒为空，
 *   且不进入宠物选择列表。
 *
 * 用法：
 * 1) [BaseActivity.onCreate] 调一次 [init]（读 assets 进内存，幂等；失败不崩，回退美化英文名）；
 * 2) UI 列表项用 [display]（按语言取字符串）；建档/当前档把双语言 [lookup] 结果写进 [PetProfile]，
 *    之后 UI 直接 `profile.displayName.resolve(PokemonNames.isZh())` 取词，不再查本表；
 * 3) 进化/退化用 [prevOf]/[nextOf]/[isBase] 取形态链；选择列表用 [isForm] 排除外观变体。
 */
object PokemonNames {

    @Volatile
    private var loaded = false
    private val zhMap = HashMap<String, String>()
    private val prevMap = HashMap<String, List<String>>()
    private val nextMap = HashMap<String, List<String>>()
    private val formMap = HashMap<String, Boolean>()

    /** 解析 assets/pokemon.json（仅需一次；失败也不崩，回退美化英文名）。 */
    fun init(context: Context) {
        if (loaded) return
        runCatching {
            context.assets.open("pokemon.json").bufferedReader().use { src ->
                val root = JSONObject(src.readText())
                val keys = root.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val obj = root.optJSONObject(key) ?: continue
                    val zh = obj.optJSONObject("name")?.optString("zh", "")?.takeIf { it.isNotBlank() }
                    if (zh != null) zhMap[key] = zh
                    prevMap[key] = obj.optJSONArray("prev")?.toStringList().orEmpty()
                    nextMap[key] = obj.optJSONArray("next")?.toStringList().orEmpty()
                    if (obj.optBoolean("form", false)) formMap[key] = true
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

    /** 上一形态主名列表（数组；空 = 初始形态）。 */
    fun prevOf(id: String): List<String> = prevMap[id.removeSuffix(".png")].orEmpty()

    /** 下一形态主名列表（数组；空 = 终极形态，无可进化）。 */
    fun nextOf(id: String): List<String> = nextMap[id.removeSuffix(".png")].orEmpty()

    /** 是否初始形态：prev 为空即视为初始形态（可能有的宝可梦不能进化）。 */
    fun isBase(id: String): Boolean = prevOf(id).isEmpty()

    /** 是否外观变体（颜色装饰 `_N` / 性别 `_female`/`_male` 等）：非进化目标，不进入选择列表。 */
    fun isForm(id: String): Boolean = formMap[id.removeSuffix(".png")] ?: false

    /** 性别模型名补 ♀/♂（译名已含符号则不重复）。 */
    private fun withGender(key: String, name: String): String {
        return when {
            key.endsWith("_female") && !name.contains("♀") -> "$name ♀"
            key.endsWith("_male") && !name.contains("♂") -> "$name ♂"
            else -> name
        }
    }

    /** JSONArray → 字符串列表（形态链 prev/next 解析用）。 */
    private fun org.json.JSONArray.toStringList(): List<String> {
        val out = ArrayList<String>(length())
        for (i in 0 until length()) out.add(optString(i))
        return out
    }
}
