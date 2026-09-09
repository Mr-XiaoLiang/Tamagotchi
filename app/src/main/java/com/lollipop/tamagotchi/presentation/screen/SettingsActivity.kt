package com.lollipop.tamagotchi.presentation.screen

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lollipop.tamagotchi.R
import com.lollipop.tamagotchi.data.store.PetState
import com.lollipop.tamagotchi.data.store.SettingsStore
import com.lollipop.tamagotchi.presentation.base.BaseActivity
import com.lollipop.tamagotchi.presentation.component.PillItem
import com.lollipop.tamagotchi.presentation.component.RoundEdgeSpace
import com.lollipop.tamagotchi.presentation.component.roundEdgeFade
import com.lollipop.tamagotchi.presentation.component.roundSafeInset
import com.lollipop.tamagotchi.presentation.theme.ColorToken

/**
 * 设置页（M17 入口枢纽）：收敛低频外部功能，避免散落感知。
 * 当前承载 M15 的「换宠 / 过往」+ M17 偏好开关（是否归档 / 过往入口可见 / 切回确认）。
 * 结构：首页 → 右侧面板 → 设置 → 换宠/过往（用户决策）。
 * 遵循圆屏列表规范：滚动流 [space、title、item…、space]，首末 RoundEdgeSpace 留半屏 + 标题作为滚动首元素。
 */
class SettingsActivity : BaseActivity() {

    override fun onBootStart() {
        val store = SettingsStore(this)
        injectContent(load = {
            SettingsSnapshot(
                archiveEnabled = store.isArchiveEnabled(),
                tombEntryVisible = store.isTombEntryVisible(),
                switchBackConfirm = store.isSwitchBackConfirm(),
            )
        }) { snap ->
            SettingsContent(
                snapshot = snap,
                settingsStore = store,
                onRestartPet = ::restartPet,
                onOpenArchive = ::openArchive,
            )
        }
    }

    /** 换宠：是否归档取决于偏好（doc/09 §5.7）——开=归档旧宠（可经「过往」切回）+清当前档+重开建档；
     *  关=直接覆盖写重开（与 M3 debug 重开档口径一致）。仅经二次确认后调用（[ConfirmRestartScreen]）。 */
    private fun restartPet() {
        PetState.attach(this)
        val prefs = SettingsStore(this)
        val current = PetState.snapshot()
        if (current != null) {
            if (prefs.isArchiveEnabled()) {
                // 换宠归档：先写墓碑，再清当前档（archiveAndClear 内含 clear）
                PetState.archiveAndClear(current, System.currentTimeMillis())
            } else {
                PetState.clear()
            }
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

/** 设置页首屏数据快照（偏好项初始值，由 [SettingsStore] 载入）。 */
private data class SettingsSnapshot(
    val archiveEnabled: Boolean,
    val tombEntryVisible: Boolean,
    val switchBackConfirm: Boolean,
)

/** 设置页容器：持有「二次确认」状态与各偏好开关状态（即时落盘）。 */
@Composable
private fun SettingsContent(
    snapshot: SettingsSnapshot,
    settingsStore: SettingsStore,
    onRestartPet: () -> Unit,
    onOpenArchive: () -> Unit,
) {
    var confirmRestart by remember { mutableStateOf(false) }
    var archiveEnabled by remember { mutableStateOf(snapshot.archiveEnabled) }
    var tombEntryVisible by remember { mutableStateOf(snapshot.tombEntryVisible) }
    var switchBackConfirm by remember { mutableStateOf(snapshot.switchBackConfirm) }

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
            archiveEnabled = archiveEnabled,
            tombEntryVisible = tombEntryVisible,
            switchBackConfirm = switchBackConfirm,
            onToggleArchive = { v -> archiveEnabled = v; settingsStore.setArchiveEnabled(v) },
            onToggleTombVisible = { v -> tombEntryVisible = v; settingsStore.setTombEntryVisible(v) },
            onToggleSwitchConfirm = { v -> switchBackConfirm = v; settingsStore.setSwitchBackConfirm(v) },
            onRequestRestart = { confirmRestart = true },
            onOpenArchive = onOpenArchive,
        )
    }
}

@Composable
private fun SettingsScreen(
    archiveEnabled: Boolean,
    tombEntryVisible: Boolean,
    switchBackConfirm: Boolean,
    onToggleArchive: (Boolean) -> Unit,
    onToggleTombVisible: (Boolean) -> Unit,
    onToggleSwitchConfirm: (Boolean) -> Unit,
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
            ToggleRow(
                label = stringResource(R.string.settings_toggle_archive),
                checked = archiveEnabled,
                onToggle = { onToggleArchive(!archiveEnabled) },
            )
        }
        item {
            ToggleRow(
                label = stringResource(R.string.settings_toggle_tomb_visible),
                checked = tombEntryVisible,
                onToggle = { onToggleTombVisible(!tombEntryVisible) },
            )
        }
        item {
            ToggleRow(
                label = stringResource(R.string.settings_toggle_switch_confirm),
                checked = switchBackConfirm,
                onToggle = { onToggleSwitchConfirm(!switchBackConfirm) },
            )
        }
        item {
            PillItem(
                text = stringResource(R.string.settings_restart_pet),
                filled = true,
                textAlign = TextAlign.Center,
                onClick = onRequestRestart,
            )
        }
        if (tombEntryVisible) {
            item {
                PillItem(
                    text = stringResource(R.string.settings_archive),
                    textAlign = TextAlign.Center,
                    onClick = onOpenArchive,
                )
            }
        }
        item { RoundEdgeSpace() }
    }
}

/** 开关行：标签（13sp/Accent）+ 右侧自绘开关，整行可点（≥30dp 热区）。 */
@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = ColorToken.Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        SwitchIndicator(checked)
    }
}

/** 自绘开关（无外部图标依赖）：开 = 实心 Accent 轨道 + 亮钮；关 = 暗轨道 + 灰钮。 */
@Composable
private fun SwitchIndicator(checked: Boolean) {
    Box(
        Modifier
            .size(width = 36.dp, height = 20.dp)
            .clip(RoundedCornerShape(50.dp))
            .background(if (checked) ColorToken.PillFilled else ColorToken.Accent.copy(alpha = 0.25f))
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(50.dp))
                .background(if (checked) ColorToken.OnAccent else ColorToken.Text2),
        )
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
