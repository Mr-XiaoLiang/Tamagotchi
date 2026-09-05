package com.lollipop.tamagotchi.domain.log

import com.lollipop.tamagotchi.core.attribute.AttributeDelta
import com.lollipop.tamagotchi.core.attribute.AttributeMap
import com.lollipop.tamagotchi.core.behavior.PetState
import com.lollipop.tamagotchi.domain.engine.SettlementSummary

/**
 * 在线日志类型（doc/04 §7 EventLogType）。
 * SETTLE 不占位：结算聚合由 SessionEntry 的 SettleEntry 表达（M7 扩展），
 * 见 doc/04 §7 注释「SETTLE 不再占用 EventLogType」。
 */
enum class EventLogType {
    ACTION_FEED,
    ACTION_PLAY,
    ACTION_PET,
    ACTION_HEAL,
    RANDOM_EVENT,
    STATE_CHANGE,
    WAKE,
    // 扩展：ACTION_CLEAN / ACTION_STUDY 随 M11 / M12 追加（doc/09 §5.2/§5.3）
}

/**
 * 一条在线事件日志（doc/04 §7 EventLog）：动作 / 随机事件 / 状态切换。
 * [before] = 动作前属性快照；[delta] = 实际生效变化（clamp 后）；[state] = 动作后 fsmState；
 * [note] = 气泡 key / 食物类型等补充信息。
 */
data class EventLog(
    val ts: Long,
    val type: EventLogType,
    val refId: String? = null,
    val before: AttributeMap,
    val delta: AttributeDelta,
    val state: PetState,
    val note: String? = null,
)

/**
 * 会话时间线统一条目（doc/04 §3.1 / §7 SessionEntry）——进程内、不落盘。
 * M6.S1 骨架只落 [LiveEntry]（在线追加）；开场段的 [SettleEntry]/[ReplayEntry]
 * 由 M7 SessionLog.openWith 扩展填充。
 */
sealed interface SessionEntry

/** 在线追加条目。 */
data class LiveEntry(val log: EventLog) : SessionEntry

/**
 * 会话日志（doc/04 §7 SessionLog）——domain、纯内存、进程内。
 *
 * 数据来源闭环：动作成功 / 在线事件命中 / settle 开场段都在这里汇成一条升序时间线；
 * 本会话统计（[liveCount]）供「本次陪伴小结」（doc/04 §4，M9）展示，kill 即清空。
 * [openWith] M6.S1 为空实现（M7 铺长离线回放开场段 + SETTLE 聚合锚点）。
 */
interface SessionLog {

    /** 启动 settle 后调用：铺开场段（回放剧 + SETTLE 锚点）。M6.S1 占位空实现，M7 落地。 */
    fun openWith(summary: SettlementSummary) = Unit

    /** 在线期间追加一条日志；命中即计数（[liveCount]）。 */
    fun append(log: EventLog)

    /** 按追加序返回完整会话时间线（元素为 [LiveEntry] 包裹）。 */
    fun entries(): List<SessionEntry>

    /** 本会话该类型日志条数（会话回顾统计用）。 */
    fun liveCount(type: EventLogType): Int

    /** 按时间窗取在线日志（供 doc/03 §2.2 在线事件冷却判定，M9）。 */
    fun liveLogsSince(time: Long): List<EventLog>
}

/**
 * 进程内默认实现（doc/04 §3.1 内存会话日志；无并发需求——主线程读写）。
 */
class InMemorySessionLog : SessionLog {

    private val buffer = ArrayList<EventLog>()
    private val counts = HashMap<EventLogType, Int>()

    override fun append(log: EventLog) {
        // 按 ts 升序插入（doc/04 §7 entries 时间线契约）；在线追加天然升序，此保序防乱序输入。
        val idx = buffer.indexOfFirst { it.ts > log.ts }
        buffer.add(if (idx == -1) buffer.size else idx, log)
        counts[log.type] = (counts[log.type] ?: 0) + 1
    }

    override fun entries(): List<SessionEntry> = buffer.map { LiveEntry(it) }

    override fun liveCount(type: EventLogType): Int = counts[type] ?: 0

    override fun liveLogsSince(time: Long): List<EventLog> =
        buffer.filter { it.ts >= time }
}
