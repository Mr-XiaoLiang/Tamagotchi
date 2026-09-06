package com.lollipop.tamagotchi.domain.model

/**
 * 宠物展示名（双语言，随 [PetProfile] 一起持久化）。
 *
 * - [zh]/[en] 取自建档时解析的名字映射（assets/pokemon_names.json）；
 *   缺译名时 [zh] 为空，[resolve] 自动降级 [en]。
 * - 纯值对象，不依赖 Android 资源；性别符号已含在值内（en 由 [enOf] 补 ♀/♂）。
 * - 持久化进 pet_profile 后，UI 直接 [resolve] 取词，无需再查全局名字映射表。
 */
data class PetDisplayName(
    val zh: String = "",
    val en: String = "",
) {
    /** 按语言取展示名：中文环境优先 [zh]，否则 / 未收录回退 [en]。 */
    fun resolve(preferZh: Boolean): String =
        if (preferZh && zh.isNotBlank()) zh else en.ifBlank { zh }

    companion object {
        /** 文件名主名 → 规范英文名（程序化美化 + 性别符号），等同于「真实英文名」。 */
        fun enOf(base: String): String {
            val raw = base.removeSuffix(".png")
            val (root, sym) = when {
                raw.endsWith("_female") -> raw.removeSuffix("_female") to " ♀"
                raw.endsWith("_male") -> raw.removeSuffix("_male") to " ♂"
                else -> raw to ""
            }
            return beautifyRoot(root) + sym
        }

        private fun beautifyRoot(root: String): String = when (root) {
            "NIDORANfE" -> "Nidoran ♀"
            "NIDORANmA" -> "Nidoran ♂"
            "MRMIME" -> "Mr. Mime"
            "MRRIME" -> "Mr. Rime"
            "MIMEJR" -> "Mime Jr."
            "SIRFETCHD" -> "Sirfetch'd"
            "FARFETCHD" -> "Farfetch'd"
            else -> root.split("_").joinToString(" ") { tok ->
                when (tok) {
                    "MR" -> "Mr."
                    "JR" -> "Jr."
                    else -> tok.lowercase().replaceFirstChar { it.uppercase() }
                }
            }
        }
    }
}
