package com.lollipop.tamagotchi.data.plugin

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.lollipop.tamagotchi.domain.plugin.LocalActionKind
import com.lollipop.tamagotchi.domain.plugin.LocalActionRunner

/**
 * 本地动作执行（doc/05 §3）：TORCH 需相机权限（默认关，权限流 M8.S2 接）；
 * DND 切换勿扰；TIMER 拉起系统计时器。未就绪者返回 false → executor 走 notifier。
 */
class AndroidLocalActionRunner(private val context: Context) : LocalActionRunner {
    override fun run(kind: LocalActionKind): Boolean = when (kind) {
        LocalActionKind.TORCH -> false // 占位：需相机权限流程（doc/05 §6），M8.S2 接入
        LocalActionKind.DND -> runDnd()
        LocalActionKind.TIMER -> runTimer()
    }

    private fun runDnd(): Boolean = try {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.setNotificationPolicy(
            NotificationManager.Policy(NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS, 0, 0),
        )
        true
    } catch (_: Throwable) {
        false
    }

    private fun runTimer(): Boolean = try {
        context.startActivity(
            Intent(AlarmClock.ACTION_SET_TIMER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: Throwable) {
        false
    }
}
