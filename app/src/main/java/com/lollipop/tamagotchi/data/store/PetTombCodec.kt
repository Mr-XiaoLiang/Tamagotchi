package com.lollipop.tamagotchi.data.store

import com.lollipop.tamagotchi.domain.model.PetProfile
import com.lollipop.tamagotchi.domain.model.PetTomb
import org.json.JSONArray
import org.json.JSONObject

/**
 * 墓碑 JSON 编解码（M15，doc/04 §3.3）。嵌套复用 [PetProfileCodec] 序列化最终快照，
 * 外层包 pet_id / retired_at / companionship_ms。纯字符串 ↔ [PetTomb]，JVM 单测可直接跑。
 */
internal object PetTombCodec {

    fun toJson(tomb: PetTomb): String {
        val root = JSONObject()
        root.put("pet_id", tomb.petId)
        root.put("profile", JSONObject(PetProfileCodec.toJson(tomb.profile)))
        root.put("retired_at", tomb.retiredAt)
        root.put("companionship_ms", tomb.companionshipMs)
        return root.toString()
    }

    fun fromJson(raw: String): PetTomb? {
        return try {
            val root = JSONObject(raw)
            val petId = root.optString("pet_id").trim()
            if (petId.isEmpty()) return null
            val profile = PetProfileCodec.fromJson(root.optString("profile")) ?: return null
            PetTomb(
                petId = petId,
                profile = profile,
                retiredAt = root.optLong("retired_at", 0L),
                companionshipMs = root.optLong("companionship_ms", 0L),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun listToJson(tombs: List<PetTomb>): String = JSONArray().also { arr ->
        tombs.forEach { arr.put(JSONObject(toJson(it))) }
    }.toString()

    fun listFromJson(raw: String?): List<PetTomb> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = mutableListOf<PetTomb>()
            for (i in 0 until arr.length()) {
                fromJson(arr.optString(i))?.let(out::add)
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }
}
