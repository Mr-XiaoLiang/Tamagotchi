package com.lollipop.tamagotchi.presentation.screen

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lollipop.tamagotchi.R
import com.lollipop.tamagotchi.data.store.PetStore
import com.lollipop.tamagotchi.presentation.base.BaseActivity
import com.lollipop.tamagotchi.presentation.component.PillItem
import com.lollipop.tamagotchi.presentation.component.RoundEdgeSpace
import com.lollipop.tamagotchi.presentation.component.roundEdgeFade
import com.lollipop.tamagotchi.presentation.component.roundSafeInset
import com.lollipop.tamagotchi.presentation.theme.ColorToken

/**
 * 设置页（M17 入口枢纽）：收敛低频外部功能，避免散落感知。
 * 当前承载 M15 的「换宠 / 档案」：
 *  - 重新开始选择宠物：先经二次确认（[ConfirmRestartScreen]）→ 归档当前宠（进墓碑，可在「档案」切回）+ 清当前档 + 重启建档；
 *  - 我的档案 / 电子墓碑：打开墓碑列表（[TombActivity]）。
 * 结构：首页 → 右侧面板 → 设置 → 换宠/档案（用户决策）。
 * 遵循圆屏列表规范：滚动流 [space、title、item…、space]，首末 RoundEdgeSpace 留半屏 + 标题作为滚动首元素。
 */
class SettingsActivity : BaseActivity() {

    override fun onBootStart() {
        injectContent(load = { Unit }) {
            SettingsContent(
                onRestartPet = ::restartPet,
                onOpenArchive = ::openArchive,
            )
        }
    }

    /** 换宠：先归档当前宠（可恢复），再清当前档并重启建档流程（doc/04 §3.3）。仅经二次确认后调用。 */
    private fun restartPet() {
        val store = PetStore(this)
        val current = store.load()
        if (current != null) {
            store.archiveCurrent(current, System.currentTimeMillis())
            store.delete()
        }
        val intent = Intent(this, PetActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }

    private fun openArchive() {
        startActivity(Intent(this, TombActivity::class.java))
    }
}

/** 设置页容器：持有「二次确认」状态，确认前只请求、确认后才真正换宠。 */
@Composable
private fun SettingsContent(
    onRestartPet: () -> Unit,
    onOpenArchive: () -> Unit,
) {
    var confirmRestart by remember { mutableStateOf(false) }
    if (confirmRestart) {
        ConfirmRestartScreen(
            onConfirm = {
                confirmRestart = false
                onRestartPet()
            },
            onCancel = { confirmRestart = false },
        )
    } else {
        SettingsScreen(
            onRequestRestart = { confirmRestart = true },
            onOpenArchive = onOpenArchive,
        )
    }
}

@Composable
private fun SettingsScreen(
    onRequestRestart: () -> Unit,
    onOpenArchive: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .roundEdgeFade()
            .padding(horizontal = roundSafeInset()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // 圆屏列表规范：首末 RoundEdgeSpace 留半屏 + 标题作为滚动首元素
        item { RoundEdgeSpace() }
        item { PageTitle(stringResource(R.string.settings_title)) }
        item {
            PillItem(
                text = stringResource(R.string.settings_restart_pet),
                filled = true,
                textAlign = TextAlign.Center,
                onClick = onRequestRestart,
            )
        }
        item {
            PillItem(
                text = stringResource(R.string.settings_archive),
                textAlign = TextAlign.Center,
                onClick = onOpenArchive,
            )
        }
        item { RoundEdgeSpace() }
    }
}

/**
 * 换宠二次确认页（销毁性操作前置确认，遵循圆屏列表规范）：
 * 警示红实心「确认」+ 空心「取消」，标题提示当前宠将归档、可切回。
 */
@Composable
private fun ConfirmRestartScreen(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .roundEdgeFade()
            .padding(horizontal = roundSafeInset()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item { RoundEdgeSpace() }
        item { PageTitle(stringResource(R.string.settings_restart_confirm_title)) }
        item {
            Text(
                stringResource(R.string.settings_restart_confirm_hint),
                color = ColorToken.Text2,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        item {
            PillItem(
                text = stringResource(R.string.settings_restart_confirm),
                filled = true,
                color = ColorToken.Warn,
                textAlign = TextAlign.Center,
                onClick = onConfirm,
            )
        }
        item {
            PillItem(
                text = stringResource(R.string.cancel),
                textAlign = TextAlign.Center,
                onClick = onCancel,
            )
        }
        item { RoundEdgeSpace() }
    }
}

/** 页面标题：滚动流首元素（随列表滚动，doc/06 §8.1），Accent / 15sp / Bold。 */
@Composable
private fun PageTitle(title: String) {
    Text(
        title,
        color = ColorToken.Accent,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
