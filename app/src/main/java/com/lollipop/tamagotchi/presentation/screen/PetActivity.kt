package com.lollipop.tamagotchi.presentation.screen

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.lollipop.tamagotchi.presentation.base.BaseActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 主屏宠物 Activity（doc/00 §7 / Task.md M1.S2）。
 *
 * M1：BaseActivity 壳 → 注入 PetScreen（主屏四区 + 三 overlay 路由，占位内容）。
 * Logo 首帧保持原生 View；[BaseActivity.showShell] 挂壳后短暂停留 Logo 再注入。
 */
class PetActivity : BaseActivity() {

    override fun onBootStart() {
        showShell()
        lifecycleScope.launch {
            // 让 Logo 壳停留一个完整首帧，随后注入 Compose 主屏（M1 无真实加载）
            delay(200)
            injectContent(load = { Unit }) {
                PetScreen(onSettleReady = { bootSettle() })
            }
        }
    }
}
