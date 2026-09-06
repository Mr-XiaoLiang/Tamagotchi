package com.lollipop.tamagotchi.presentation.screen

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lollipop.tamagotchi.R
import com.lollipop.tamagotchi.data.store.PetStore
import com.lollipop.tamagotchi.domain.model.PetTomb
import com.lollipop.tamagotchi.presentation.base.BaseActivity
import com.lollipop.tamagotchi.presentation.component.PillItem
import com.lollipop.tamagotchi.presentation.component.RoundEdgeSpace
import com.lollipop.tamagotchi.presentation.component.roundEdgeFade
import com.lollipop.tamagotchi.presentation.component.roundSafeInset
import com.lollipop.tamagotchi.presentation.theme.ColorToken

/**
 * 电子墓碑页（M15.S2，doc/04 §3.3 / 09 §5.5）：列出换宠归档的宠物快照，
 * 支持「切回」——把墓碑还原为当前档并重启主屏。墓碑只读、可恢复。
 * 结构：设置 → 我的档案 / 电子墓碑。
 * 遵循圆屏列表规范：滚动流 [space、title、item…、space]，首末 RoundEdgeSpace 留半屏。
 */
class TombActivity : BaseActivity() {

    override fun onBootStart() {
        injectContent(load = { PetStore(this).listTombs() }) { tombs ->
            TombScreen(tombs = tombs, onSwitchBack = ::switchBack)
        }
    }

    /** 切回：墓碑 → 当前档，重启主屏即打开该宠（doc/08 §3 打开即结算复用）。 */
    private fun switchBack(petId: String) {
        val store = PetStore(this)
        store.switchTo(petId)
        val intent = Intent(this, PetActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }
}

@Composable
private fun TombScreen(
    tombs: List<PetTomb>,
    onSwitchBack: (String) -> Unit,
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
        item { PageTitle(stringResource(R.string.archive_title)) }
        if (tombs.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.archive_empty),
                    color = ColorToken.Text2,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(8.dp),
                )
            }
        } else {
            items(tombs, key = { it.petId }) { tomb ->
                TombRow(
                    tomb = tomb,
                    onSwitchBack = { onSwitchBack(tomb.petId) },
                )
            }
        }
        item { RoundEdgeSpace() }
    }
}

/** 单个墓碑行：宠物名 + 共处时长（无背景列表行）+ 切回按钮（PillItem）。 */
@Composable
private fun TombRow(
    tomb: PetTomb,
    onSwitchBack: () -> Unit,
) {
    val duration = formatNoteDuration(tomb.companionshipMs, LocalContext.current)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            tomb.profile.petName,
            color = ColorToken.Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            stringResource(R.string.archive_together, duration),
            color = ColorToken.Text2,
            fontSize = 11.sp,
        )
        PillItem(
            text = stringResource(R.string.archive_switch_back),
            textAlign = TextAlign.Center,
            onClick = onSwitchBack,
        )
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
