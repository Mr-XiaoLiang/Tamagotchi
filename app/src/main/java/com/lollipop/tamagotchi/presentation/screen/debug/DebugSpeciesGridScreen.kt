package com.lollipop.tamagotchi.presentation.screen.debug

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lollipop.tamagotchi.data.sprite.SpriteRepository
import com.lollipop.tamagotchi.presentation.render.SpriteSheetDecoder
import com.lollipop.tamagotchi.presentation.screen.ChevronDir
import com.lollipop.tamagotchi.presentation.screen.MiniChevron
import com.lollipop.tamagotchi.presentation.screen.RoundHeader
import com.lollipop.tamagotchi.presentation.theme.ColorToken

/**
 * Debug-only 精灵切片核对屏（M2.S1，Task M2.S1 P0）：
 *
 * 把当前宠的 4×4 切片按整表原样网格放大（FilterQuality.None 保留像素，不模糊），
 * 左列标注行=朝向（0下/1左/2右/3上）、顶列标注帧号，左右切换上一只/下一只，
 * 人工核对「行=朝向、列=帧 0→3」映射是否正确；发现不符回写 render/SpriteSheetDecoder 常量。
 *
 * 入口：Debug 构建在 PetScreen 中央活动区长按；release 不编译。
 * M5.S2 起叠加「结算 Debug · 时间旅行」：非空 [onTimeTravel] 时展示，
 * 把 lastSettledAt 拨回 1h/6h/24h/72h 并立即结算（EntryFlow 侧幂等 apply），验证离线推进。
 */
@Composable
internal fun DebugSpeciesGridScreen(
    onDismiss: () -> Unit,
    onTimeTravel: ((hours: Long) -> Unit)? = null,
) {
    val context = LocalContext.current
    val repo = remember(context) { SpriteRepository(context.assets) }
    val pets = remember(repo) { repo.listPets() }
    var index by remember { mutableIntStateOf(0) }
    val pet = pets.getOrNull(index)
    val sheet = remember(pet) { pet?.let { repo.loadSheet(it.id).asImageBitmap() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(ColorToken.bg),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            RoundHeader("Debug · 精灵切片核对", ChevronDir.Up, onDismiss)

            // ── 切换上一只 / 下一只 ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clickable { index = (index + pets.size - 1) % pets.size },
                    contentAlignment = Alignment.Center,
                ) {
                    MiniChevron(dir = ChevronDir.Left, tint = ColorToken.Text2, size = 18.dp)
                }
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        pet?.displayName ?: "—",
                        color = ColorToken.Accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (pet != null) "第 ${index + 1}/${pets.size} 只 · ${pet.defaultFile}" else "无可用切片素材",
                        color = ColorToken.Text2,
                        fontSize = 11.sp,
                    )
                }
                Box(
                    Modifier
                        .size(32.dp)
                        .clickable { index = (index + 1) % pets.size },
                    contentAlignment = Alignment.Center,
                ) {
                    MiniChevron(dir = ChevronDir.Right, tint = ColorToken.Text2, size = 18.dp)
                }
            }

            Spacer(Modifier.height(10.dp))

            if (sheet != null) {
                SpriteGrid(sheet)
                Spacer(Modifier.height(10.dp))
                Text(
                    "行=朝向：0下 / 1左 / 2右 / 3上；列=帧 0→3（@4FPS 行走循环）",
                    color = ColorToken.Text2,
                    fontSize = 11.sp,
                )
                Text(
                    "核对：每行 4 格应是同一朝向的连续步态；若方向错位/帧序跳帧 → 回写 SpriteSheetDecoder 常量或排查文件。",
                    color = ColorToken.Text2,
                    fontSize = 11.sp,
                )
            } else {
                Text(
                    "加载失败：请确认 assets/sprite 与 SpriteRepository.listPets() 过滤规则。",
                    color = ColorToken.Warn,
                    fontSize = 11.sp,
                )
            }

            if (onTimeTravel != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "结算 Debug · 时间旅行",
                    color = ColorToken.Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    TimeTravelChip("1h", Modifier.weight(1f)) {
                        onTimeTravel(1)
                        onDismiss()
                    }
                    Spacer(Modifier.width(6.dp))
                    TimeTravelChip("6h", Modifier.weight(1f)) {
                        onTimeTravel(6)
                        onDismiss()
                    }
                    Spacer(Modifier.width(6.dp))
                    TimeTravelChip("24h", Modifier.weight(1f)) {
                        onTimeTravel(24)
                        onDismiss()
                    }
                    Spacer(Modifier.width(6.dp))
                    TimeTravelChip("72h", Modifier.weight(1f)) {
                        onTimeTravel(72)
                        onDismiss()
                    }
                }
                Text(
                    "把 lastSettledAt 拨回 N 小时并立即结算（幂等、可反复点）：验证离线扣减 / SICK·SAD 推进 / 夜间回血；点按即落盘，重启亦可复现。",
                    color = ColorToken.Text2,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/** 时间旅行回拨按钮（debug-only）：等宽热区 ≥36dp、文字不透明 ≥11sp（doc/06 §8）。 */
@Composable
private fun RowScope.TimeTravelChip(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(ColorToken.Accent.copy(alpha = 0.1f))
            .border(1.dp, ColorToken.Accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = ColorToken.Accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 4×4 网格：左列 = 朝向名，顶部 = 帧号；格子保留像素近邻放大。 */
@Composable
private fun SpriteGrid(sheet: ImageBitmap) {
    val labelW = 26.dp
    val cell = 56.dp
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // 帧号行
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(labelW))
            for (c in 0 until SpriteSheetDecoder.GRID) {
                Text(
                    "帧$c",
                    color = ColorToken.Text2,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(cell),
                )
            }
        }
        for (r in 0 until SpriteSheetDecoder.GRID) {
            val dir = SpriteSheetDecoder.Dir.of(r)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (dir) {
                        SpriteSheetDecoder.Dir.Down -> "下"
                        SpriteSheetDecoder.Dir.Left -> "左"
                        SpriteSheetDecoder.Dir.Right -> "右"
                        SpriteSheetDecoder.Dir.Up -> "上"
                    },
                    color = ColorToken.Accent,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(labelW),
                )
                for (c in 0 until SpriteSheetDecoder.GRID) {
                    SpriteCell(sheet = sheet, row = r, col = c, cellSize = cell)
                }
            }
        }
    }
}

/** 单格：切 64×64 子矩形，近邻放大到 [cellSize]，四角淡色便于看透明边界。 */
@Composable
private fun SpriteCell(sheet: ImageBitmap, row: Int, col: Int, cellSize: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(cellSize)) {
        drawRect(color = ColorToken.Accent.copy(alpha = 0.05f))
        drawImage(
            image = sheet,
            srcOffset = IntOffset(col * SpriteSheetDecoder.FRAME_PX, row * SpriteSheetDecoder.FRAME_PX),
            srcSize = IntSize(SpriteSheetDecoder.FRAME_PX, SpriteSheetDecoder.FRAME_PX),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt()),
            filterQuality = FilterQuality.None,
        )
    }
}
