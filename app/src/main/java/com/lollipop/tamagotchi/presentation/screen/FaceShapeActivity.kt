package com.lollipop.tamagotchi.presentation.screen

import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lollipop.grokbot.GrokMood
import com.lollipop.grokbot.GrokShape
import com.lollipop.tamagotchi.R
import com.lollipop.tamagotchi.data.store.PetState
import com.lollipop.tamagotchi.data.store.SettingsStore
import com.lollipop.tamagotchi.presentation.base.BaseActivity
import com.lollipop.tamagotchi.presentation.component.ColorDot
import com.lollipop.tamagotchi.presentation.component.PillItem
import com.lollipop.tamagotchi.presentation.component.RoundEdgeSpace
import com.lollipop.tamagotchi.presentation.component.roundEdgeFade
import com.lollipop.tamagotchi.presentation.component.roundSafeInset
import com.lollipop.tamagotchi.presentation.face.RobotFace
import com.lollipop.tamagotchi.presentation.face.RobotShapePicker
import com.lollipop.tamagotchi.presentation.theme.ColorToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Robot 脸型选择页（doc/10 §2.5，M20.S5）：独立全屏 Activity，设置页为入口。
 *
 * 自动选择（`RobotShapePicker.pick`：种类哈希 + 性格邻域微调）**照旧保留**，
 * 这里只是在用户不满意时给一个「手动覆盖」的出口：
 *  - 选「自动」= 清掉覆盖，回到种类 + 性格推导；
 *  - 选某个形状 = 按**当前宠物 id** 记一条覆盖（换宠不继承，见 `SettingsStore.faceShapeOverride`）。
 *
 * 列表每项带**该形状的真实预览**（`RobotFace` 静态帧，`paused = true` 只画首帧不跑循环，
 * 18 项同时挂在树上也不吃 CPU），形状名走 strings（可 i18n）。
 */
class FaceShapeActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 读当前档（petId / traits）：必须在读 snapshot 前 attach
        PetState.attach(this)
    }

    override fun onBootStart() {
        val store = SettingsStore(this)
        injectContent(load = {
            val profile = PetState.snapshot()
            val petId = profile?.petId.orEmpty()
            FaceShapeSnapshot(
                petId = petId,
                // 自动选择的结果：仅用于「自动」项上标出「当前是哪一款」，不参与选中判定
                autoShape = profile?.let { RobotShapePicker.pick(it.petId, it.personality.traits) },
                overrideName = if (petId.isEmpty()) null else store.faceShapeOverride(petId),
            )
        }) { snap ->
            FaceShapeContent(snapshot = snap, store = store)
        }
    }
}

/** 首屏数据快照（后台线程载入）。 */
private data class FaceShapeSnapshot(
    val petId: String,
    val autoShape: GrokShape?,
    val overrideName: String?,
)

@Composable
private fun FaceShapeContent(
    snapshot: FaceShapeSnapshot,
    store: SettingsStore,
) {
    val scope = rememberCoroutineScope()
    // null = 跟随自动
    var selected by remember { mutableStateOf(RobotShapePicker.restore(snapshot.overrideName)) }

    /** 落盘：null → 清覆盖（回自动）；否则写形状名。IO 线程（SP 用 commit 是同步磁盘写）。 */
    fun apply(pick: GrokShape?) {
        selected = pick
        scope.launch(Dispatchers.IO) {
            if (pick == null) {
                store.clearFaceShapeOverride(snapshot.petId)
            } else {
                store.setFaceShapeOverride(snapshot.petId, pick.name)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .roundEdgeFade()
            .padding(horizontal = roundSafeInset()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item { RoundEdgeSpace() }
        item { PageTitle(stringResource(R.string.face_shape_title)) }
        item {
            val autoName = snapshot.autoShape
                ?.let { stringResource(RobotShapePicker.labelRes(it)) }
                .orEmpty()
            ShapeRow(
                text = stringResource(R.string.face_shape_auto, autoName),
                preview = snapshot.autoShape,
                selected = selected == null,
                onClick = { apply(null) },
            )
        }
        items(RobotShapePicker.SELECTABLE, key = { it.name }) { shape ->
            ShapeRow(
                text = stringResource(RobotShapePicker.labelRes(shape)),
                preview = shape,
                selected = selected == shape,
                onClick = { apply(shape) },
            )
        }
        item { RoundEdgeSpace() }
    }
}

/** 一行 = 形状预览 + 名称；当前选中项实心高亮 + 尾随圆点。 */
@Composable
private fun ShapeRow(
    text: String,
    preview: GrokShape?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    PillItem(
        text = text,
        filled = selected,
        icon = if (preview == null) {
            null
        } else {
            {
                // 静态预览：paused → 只画首帧不进渲染循环（库内 frame 计数只在第 0 帧自增）。
                // 选中项底是实心胶囊（浅色），脸必须翻深色，否则「白底 + 白脸」看不见。
                RobotFace(
                    mood = GrokMood.IDLE,
                    size = 30.dp,
                    paused = true,
                    shape = preview,
                    inverted = selected,
                )
            }
        },
        trailing = if (selected) {
            // 同上：浅底上的标记点也要翻深色
            { ColorDot(ColorToken.OnAccent, size = 8.dp) }
        } else {
            null
        },
        onClick = onClick,
    )
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
