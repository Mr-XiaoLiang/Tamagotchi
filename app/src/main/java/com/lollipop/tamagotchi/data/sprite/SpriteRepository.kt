package com.lollipop.tamagotchi.data.sprite

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * 精灵素材仓库（doc/07 §6 / Task M2.S1）。
 *
 * - assets/sprite 下每张 `*.png` 表 = 256×256 / 4×4 / 单帧 64×64（行=朝向、列=帧，见 presentation/render/SpriteSheetDecoder）。
 * - 命名规则：`<主名>.png` = 默认形态；`<主名>_<N>.png` = 第 N 形态；纯数字命名（如 `000.png`）剔除。
 * - [listPets] 只做一次 `AssetManager.list` 并按主名聚合（每宠只挂默认形态文件）。
 * - [loadSheet] 缓存最近 1 张整表 Bitmap（Bitmap 池=1），换宠回收替换，余量 <30MB。
 */
class SpriteRepository(private val assets: AssetManager) {

    /** 可加载的宠。 */
    data class PetEntry(
        val id: String,          // 主名（去形态后缀），如 ALCREMIE
        val displayName: String, // 展示名（当前=id，M3 建档后可接本地化/图鉴名）
        val defaultFile: String, // 默认形态文件名（.png）
    )

    private var petsCache: List<PetEntry>? = null

    /** 所有宠主名列表（按主名排序；形态聚合、剔除纯数字命名）。 */
    fun listPets(): List<PetEntry> {
        petsCache?.let { return it }
        val byBase = HashMap<String, MutableList<Pair<Int?, String>>>()
        for (file in assets.list("sprite").orEmpty()) {
            if (!file.endsWith(".png", ignoreCase = true)) continue
            val stem = file.substring(0, file.length - 4)
            // 拆 <主名>[_<数字形态>]：形态后缀须全数字；其余含 "_" 的名字整体当主名。
            val us = stem.indexOfLast { it == '_' }
            val base: String
            val form: Int?
            if (us > 0 && us < stem.lastIndex) {
                val suffix = stem.substring(us + 1)
                if (suffix.isNotEmpty() && suffix.all { it.isDigit() }) {
                    base = stem.substring(0, us)
                    form = suffix.toInt()
                } else {
                    base = stem
                    form = null
                }
            } else {
                base = stem
                form = null
            }
            if (base.isEmpty() || !base.any { it.isLetter() }) continue // 剔除 000 等
            byBase.getOrPut(base) { mutableListOf() }.add(form to file)
        }
        val out = ArrayList<PetEntry>(byBase.size)
        for ((base, files) in byBase) {
            // 默认形态：优先无形态后缀，其次最小 _N。
            val dflt = files.firstOrNull { it.first == null }?.second
                ?: files.minByOrNull { it.first ?: Int.MAX_VALUE }?.second
                ?: continue
            out += PetEntry(id = base, displayName = base, defaultFile = dflt)
        }
        petsCache = out.sortedBy { it.id }
        return petsCache!!
    }

    private var cacheId: String? = null
    private var cacheBmp: Bitmap? = null

    /** 当前宠的 256×256 整表 Bitmap（Bitmap 池仅此 1 张，换宠回收替换）。
     *  [petId] 即形态主名（可能含 `_N` 后缀，如 `ALCREMIE_2`），直接按 `sprite/<petId>.png` 取精确形态文件。 */
    fun loadSheet(petId: String): Bitmap {
        if (cacheId == petId) return cacheBmp!!
        val file = "$petId.png"
        val bmp = BitmapFactory.decodeStream(assets.open("sprite/$file"))
            ?: error("sprite 不存在: $file")
        cacheBmp?.recycle()
        cacheBmp = bmp
        cacheId = petId
        return bmp
    }

    /** 精确按形态主名取该形态整表 Bitmap（进化/退化候选列表用；不进缓存池、调用方自行管理生命周期）。 */
    fun loadFormBitmap(id: String): Bitmap? =
        runCatching { BitmapFactory.decodeStream(assets.open("sprite/$id.png")) }.getOrNull()
}
