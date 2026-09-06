package com.lollipop.tamagotchi.domain.note

import com.lollipop.tamagotchi.domain.engine.EndingMood
import com.lollipop.tamagotchi.domain.engine.SettlementSummary
import com.lollipop.tamagotchi.domain.log.EventLogType
import com.lollipop.tamagotchi.domain.log.SessionLog

/**
 * 宠物便条（M14，doc/04 §4/§5、doc/09 §5.4）。
 *
 * 纯 domain、零 Android 依赖：把「离线开场素材（离开时长档 + 结局基调）」与「本次在线陪伴计数」
 * 打包成结构化 [PetNote]，由 presentation 层经
 * `com.lollipop.tamagotchi.presentation.screen.PetNote.toText` 映射成多语言文案
 * （core/domain 不引 R，沿用 M7 的「枚举 → 资源」映射约定，见 I18n.kt）。
 *
 * 语义边界（doc/04 §1/§3.1）：便条只读内存日志与本次结算摘要，不新增状态、不持久化。
 */
object NoteGenerator {

    /** 拼装一条便条：离线开场（时长>0 才有）+ 在线陪伴（有互动才有）。 */
    fun generate(summary: SettlementSummary, log: SessionLog): PetNote {
        val opening = if (summary.elapsedMs > 0L) {
            OfflineOpening(
                durationMs = summary.elapsedMs,
                tone = NoteTone.fromDuration(summary.elapsedMs),
                endingMood = summary.endingMood,
            )
        } else {
            null
        }
        val comp = OnlineCompanionship(
            feed = log.liveCount(EventLogType.ACTION_FEED),
            pet = log.liveCount(EventLogType.ACTION_PET),
            play = log.liveCount(EventLogType.ACTION_PLAY),
            heal = log.liveCount(EventLogType.ACTION_HEAL),
            clean = log.liveCount(EventLogType.ACTION_CLEAN),
            study = log.liveCount(EventLogType.ACTION_STUDY),
        )
        val companionship = if (comp.totalInteractions > 0) comp else null
        return PetNote(opening = opening, companionship = companionship)
    }
}

/** 离线开场语气档（与 M7 `greetingRes` 同档位，doc/04 §5）。 */
enum class NoteTone {
    CASUAL,     // < 10min：普通招呼「回来啦」
    MISS,       // 10min~2h：撒娇「你去哪儿了」
    WAIT,       // 2h~6h：有点委屈「等你好久了」
    LONG_WAIT,  // 6h~24h：委屈偏 SAD「好久不见」
    ABANDONED,  // > 24h：病恹恹/大哭「我以为你不要我了」
    ;

    companion object {
        /** 离开时长（毫秒）→ 语气档，与 I18n.greetingRes 分档一致。 */
        fun fromDuration(ms: Long): NoteTone {
            val min = ms / 60_000L
            return when {
                min < 10 -> CASUAL
                min < 120 -> MISS
                min < 360 -> WAIT
                min < 1440 -> LONG_WAIT
                else -> ABANDONED
            }
        }
    }
}

/** 离线开场句素材（presentation 据此模板化文案）。 */
data class OfflineOpening(
    val durationMs: Long,
    val tone: NoteTone,
    val endingMood: EndingMood?,
)

/** 本次在线陪伴互动计数（presentation 据此拼「喂了 N 次、摸了 N 次…」）。 */
data class OnlineCompanionship(
    val feed: Int,
    val pet: Int,
    val play: Int,
    val heal: Int,
    val clean: Int,
    val study: Int,
) {
    val totalInteractions: Int
        get() = feed + pet + play + heal + clean + study
}

/** 一条宠物便条素材：离线开场（可空）+ 在线陪伴（可空）。两者皆空 = 无内容便条。 */
data class PetNote(
    val opening: OfflineOpening?,
    val companionship: OnlineCompanionship?,
)
