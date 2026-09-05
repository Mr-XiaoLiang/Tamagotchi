package com.lollipop.tamagotchi.presentation.boot

import android.util.Log
import kotlinx.coroutines.delay

/**
 * 五阶启动骨架（doc/08 §1）：①壳 → ②快捷 → ③宠物 → ④状态 → ⑤结算。
 * 编号/打点标记统一在此，供真机核对时序与黑闪。
 */
enum class BootStage(val marker: String) {
    Shell("①壳"),
    Quick("②快捷"),
    Pet("③宠物"),
    Status("④状态"),
    Settle("⑤结算"),
}

/** 启动打点。tag=BootShell，格式 `[①壳 Shell] 消息`。 */
object BootLog {
    const val TAG = "BootShell"

    fun s(stage: BootStage, msg: String) =
        Log.i(TAG, "[${stage.marker} ${stage.name}] $msg")
}

/**
 * M1 骨架：占位数据「瞬时即就绪」，故用极短的帧间隔依次揭示，以便观察五阶顺序与打点。
 * M3+ 接入真实加载后，各阶揭示改由数据/系统就绪回调驱动，此表仅兜底最小帧间隔，不人为拉长启动。
 */
object ShellBridge {
    /** Shell→Quick→Pet→Status→Settle 各阶间隔（ms）。 */
    val STAGE_GAPS_MS = longArrayOf(200L, 140L, 140L, 120L)

    /** 依序推进五阶并打点（Shell 阶由 BaseActivity 在原生壳挂载时已打）。 */
    suspend fun reveal(onStage: suspend (BootStage) -> Unit) {
        BootStage.entries.forEachIndexed { i, stage ->
            BootLog.s(stage, if (i == 0) "壳已挂载，注入启动" else "前阶完成，揭示本阶")
            onStage(stage)
            if (i < BootStage.entries.lastIndex) delay(STAGE_GAPS_MS[i])
        }
        BootLog.s(BootStage.Settle, "五阶完成，UI 进入常驻")
    }
}
