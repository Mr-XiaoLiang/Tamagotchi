package com.lollipop.tamagotchi.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lollipop.tamagotchi.R
import com.lollipop.tamagotchi.data.plugin.AppLister
import com.lollipop.tamagotchi.data.plugin.PluginPrefsStore
import com.lollipop.tamagotchi.domain.plugin.AppEntry
import com.lollipop.tamagotchi.presentation.base.BaseActivity
import com.lollipop.tamagotchi.presentation.component.AppIcon
import com.lollipop.tamagotchi.presentation.component.RoundEdgeSpace
import com.lollipop.tamagotchi.presentation.component.roundEdgeFade
import com.lollipop.tamagotchi.presentation.component.roundSafeInset
import com.lollipop.tamagotchi.presentation.component.toImageBitmap
import com.lollipop.tamagotchi.presentation.icon.add_circle
import com.lollipop.tamagotchi.presentation.icon.arrow_drop_up
import com.lollipop.tamagotchi.presentation.icon.do_not_disturb_on
import com.lollipop.tamagotchi.presentation.theme.ColorToken

/**
 * 功能清单编辑页（独立全屏 Activity）：
 *  - 与功能清单（仅读本地保存的包名序列）隔离，本页才执行一次重 IPC——枚举系统全部可启动 App + 加载图标；
 *  - 两组连续排列于同一滚动容器：上「已添加」（移除 / 图标 / 名称 / 向上），下「可添加」（图标 / 名称 / 添加）；
 *  - 遵循圆屏列表规范：滚动流 [space、title、item…、space]，首末 RoundEdgeSpace 留半屏；
 *  - 移除 / 添加按钮用警示红高亮；向上箭头为普通白色、不作高亮；所有操作按钮无背景色、≥30dp 触控热区。
 */
class QuickPanelEditActivity : BaseActivity() {

    override fun onBootStart() {
        val ctx = this@QuickPanelEditActivity
        val store = PluginPrefsStore(ctx)
        injectContent(
            load = {
                // 重 IPC 仅在此执行一次：枚举系统全部可启动 App + 加载图标
                val lister = AppLister(ctx)
                val apps = lister.launchableApps()
                val icons = apps.associate { it.packageName to lister.loadIcon(it.packageName)?.toImageBitmap() }
                apps to icons
            },
            content = { data ->
                val (apps, icons) = data
                EditScreenContent(apps = apps, icons = icons, store = store)
            },
        )
    }
}

@Composable
private fun EditScreenContent(
    apps: List<AppEntry>,
    icons: Map<String, ImageBitmap?>,
    store: PluginPrefsStore,
) {
    // 已选序列为本页事实源；每次操作后同步落盘（功能清单回归时读盘刷新）
    val selectedPkgs = remember { mutableStateListOf<String>().apply { addAll(store.load().selected) } }
    val added = selectedPkgs.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
    val available = apps.filter { it.packageName !in selectedPkgs }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .roundEdgeFade()
            .padding(horizontal = roundSafeInset()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // 圆屏列表规范：首末 RoundEdgeSpace 留半屏 + 标题作为滚动首元素
        item { RoundEdgeSpace() }
        item { PageTitle(stringResource(R.string.quick_edit_title)) }
        item { SectionHeader(stringResource(R.string.quick_edit_added)) }
        if (added.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.quick_edit_empty),
                    color = ColorToken.Text2,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        items(added, key = { it.packageName }) { app ->
            AddedRow(
                app = app,
                icons = icons,
                onRemove = {
                    selectedPkgs.remove(app.packageName)
                    store.remove(app.packageName)
                },
                onUp = {
                    val i = selectedPkgs.indexOf(app.packageName)
                    if (i > 0) {
                        selectedPkgs.add(i - 1, selectedPkgs.removeAt(i))
                        store.move(app.packageName, -1)
                    }
                },
            )
        }
        item { SectionHeader(stringResource(R.string.quick_edit_available)) }
        items(available, key = { it.packageName }) { app ->
            AvailableRow(
                app = app,
                icons = icons,
                onAdd = {
                    if (app.packageName !in selectedPkgs) {
                        selectedPkgs.add(app.packageName)
                        store.add(app.packageName)
                    }
                },
            )
        }
        item { RoundEdgeSpace() }
    }
}

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

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        color = ColorToken.Accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
    )
}

/** 已添加行：移除（警示红）× / 图标 / 名称 / 向上（普通白）↑。无背景、无挤压 padding。 */
@Composable
private fun AddedRow(
    app: AppEntry,
    icons: Map<String, ImageBitmap?>,
    onRemove: () -> Unit,
    onUp: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoveGlyph(ColorToken.Warn, onRemove)
        AppIcon(app.packageName, icons, 40.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            app.label,
            color = ColorToken.Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        UpArrowGlyph(ColorToken.Accent, onUp)
    }
}

/** 可添加行：图标 / 名称 / 添加（警示红）＋。样式与已添加行一致（无背景）。 */
@Composable
private fun AvailableRow(
    app: AppEntry,
    icons: Map<String, ImageBitmap?>,
    onAdd: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.packageName, icons, 40.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            app.label,
            color = ColorToken.Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AddGlyph(ColorToken.Accent, onAdd)
    }
}

/** 无背景的操作热区，至少 30dp，居中对齐内部图标（满足圆屏最小热区约束）。 */
@Composable
private fun ActionHitArea(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .sizeIn(minWidth = 30.dp, minHeight = 30.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** 移除图标：Material Symbols Outlined `do_not_disturb_on`，警示红着色。 */
@Composable
private fun RemoveGlyph(color: Color, onClick: () -> Unit) {
    ActionHitArea(onClick) {
        Icon(do_not_disturb_on, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
    }
}

/** 添加图标：Material Symbols Outlined `add_circle`，警示红着色。 */
@Composable
private fun AddGlyph(color: Color, onClick: () -> Unit) {
    ActionHitArea(onClick) {
        Icon(add_circle, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
    }
}

/** 向上箭头：Material Symbols Outlined `arrow_drop_up`，普通白色、不高亮。 */
@Composable
private fun UpArrowGlyph(color: Color, onClick: () -> Unit) {
    ActionHitArea(onClick) {
        Icon(arrow_drop_up, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
    }
}
