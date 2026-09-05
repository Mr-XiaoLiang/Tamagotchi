package com.lollipop.tamagotchi.presentation.screen

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.lollipop.tamagotchi.data.store.PetStore
import com.lollipop.tamagotchi.domain.model.PetProfile
import com.lollipop.tamagotchi.presentation.base.BaseActivity
import com.lollipop.tamagotchi.presentation.screen.setup.SetupProfileScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 主屏宠物 Activity（doc/00 §7 / Task.md M1.S2；M3.S2 建档闭环）。
 *
 * boot 流程：
 * 1. [showShell] Logo 壳停留一帧后注入 Compose；
 * 2. 读档：有档 → [PetScreen]（真实快照）；无档/坏档 → [SetupProfileScreen] 建档；
 * 3. 建档确认 → PetProfile.new（注册表建档初值 + 随机性格）→ [PetStore.save] → 切主屏；
 * 4. debug 构建主屏长按 → 快捷面板「重开档」清档回到建档（release 不注入回调）。
 */
class PetActivity : BaseActivity() {

    override fun onBootStart() {
        showShell()
        val store = PetStore(this)
        val isDebug = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        lifecycleScope.launch {
            // 让 Logo 壳停留一个完整首帧，随后注入 Compose 主屏（M1 无真实加载）
            delay(200)
            injectContent(load = { Unit }) {
                EntryFlow(
                    store = store,
                    debugReset = isDebug,
                    onSettleReady = { bootSettle() },
                )
            }
        }
    }
}

/**
 * 有档 / 建档路由。建档确认存盘后置 profile 即切主屏；
 * profile 的创建只此一处（PetStore.save → 内存态 → UI），无二义事实源。
 */
@Composable
private fun EntryFlow(
    store: PetStore,
    debugReset: Boolean,
    onSettleReady: () -> Unit,
) {
    val initial = remember { store.load() }
    var profile by remember { mutableStateOf(initial) }

    val current = profile
    if (current == null) {
        SetupProfileScreen(
            onConfirm = { pet, personality ->
                val created = PetProfile.new(
                    petId = pet.id,
                    petName = pet.displayName,
                    personality = personality,
                    now = System.currentTimeMillis(),
                )
                store.save(created)
                profile = created
            },
        )
    } else {
        PetScreen(
            profile = current,
            onSettleReady = onSettleReady,
            onResetProfile = if (debugReset) {
                {
                    store.delete()
                    profile = null
                }
            } else {
                null
            },
        )
    }
}
