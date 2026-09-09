package com.lollipop.tamagotchi.presentation.screen

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lollipop.tamagotchi.R
import com.lollipop.tamagotchi.data.sprite.SpriteRepository
import com.lollipop.tamagotchi.data.store.PetState
import com.lollipop.tamagotchi.presentation.base.BaseActivity
import com.lollipop.tamagotchi.presentation.component.RoundEdgeSpace
import com.lollipop.tamagotchi.presentation.component.roundEdgeFade
import com.lollipop.tamagotchi.presentation.i18n.PokemonNames
import com.lollipop.tamagotchi.presentation.render.SpriteSheetDecoder
import com.lollipop.tamagotchi.presentation.theme.ColorToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** 进化 / 退化模式（与 PetScreen 操作行、EntryFlow 启动意图共用）。 */
enum class FormMode { EVOLVE, DEVOLVE }

/**
 * 形态切换页（进化 / 退化，M?）：展示当前形态可去往的上一 / 下一形态列表（形象 + 名称）；
 * 点击某形态 = 切换形象并带回结果；直接关闭（返回键 / 收起条） = 放弃切换（不改动当前档）。
 */
class FormSwitchActivity : BaseActivity() {

    override fun onBootStart() {
        showShell()
        PetState.attach(this)
        val mode = runCatching { FormMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: "EVOLVE") }
            .getOrDefault(FormMode.EVOLVE)
        injectContent(load = { Unit }) {
            FormSwitchScreen(
                mode = mode,
                onPick = { id -> finishWith(id, mode) },
            )
        }
    }

    private fun finishWith(id: String, mode: FormMode) {
        setResult(Activity.RESULT_OK, Intent().apply {
            putExtra(EXTRA_PET_ID, id)
            putExtra(EXTRA_MODE, mode.name)
        })
        finish()
    }

    companion object {
        const val EXTRA_PET_ID = "pet_id"
        const val EXTRA_MODE = "mode"
    }
}

@Composable
private fun FormSwitchScreen(
    mode: FormMode,
    onPick: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val repo = remember(ctx) { SpriteRepository(ctx.assets) }
    // 候选由中央状态机提供（基于当前宠的可进化/可退化链），不在此各自推导
    val candidates = remember(mode) {
        if (mode == FormMode.EVOLVE) PetState.evolveList() else PetState.devolveList()
    }
    val titleRes = if (mode == FormMode.EVOLVE) {
        R.string.form_switch_title_evolve
    } else {
        R.string.form_switch_title_devolve
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(ColorToken.bg)
            .verticalScroll(rememberScrollState())
            .roundEdgeFade()
            .padding(horizontal = 18.dp),
    ) {
        RoundEdgeSpace()
        Text(
            stringResource(titleRes),
            color = ColorToken.Accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        if (candidates.isEmpty()) {
            Text(
                stringResource(R.string.form_switch_empty),
                color = ColorToken.Text2,
                fontSize = 11.sp,
            )
        } else {
            candidates.forEach { id ->
                FormRow(id = id, repo = repo, onClick = { onPick(id) })
                Spacer(Modifier.height(4.dp))
            }
        }
        RoundEdgeSpace()
    }
}

@Composable
private fun FormRow(
    id: String,
    repo: SpriteRepository,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(CircleShape)
            .border(1.dp, ColorToken.PillOutline.copy(alpha = 0.35f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FormPortrait(id = id, repo = repo, size = 26.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            PokemonNames.display(id, PokemonNames.isZh()),
            color = ColorToken.Accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        MiniChevron(dir = ChevronDir.Right, tint = ColorToken.Accent, size = 18.dp)
    }
}

/** 单个形态形象：取该形态整表第 0 帧（下朝向静止帧）放大显示。 */
@Composable
private fun FormPortrait(
    id: String,
    repo: SpriteRepository,
    size: androidx.compose.ui.unit.Dp,
) {
    var bmp by remember(id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(id) {
        bmp = withContext(Dispatchers.IO) { repo.loadFormBitmap(id) }
    }
    val image = bmp?.let { if (it.isRecycled) null else it.asImageBitmap() }
    val px = with(LocalDensity.current) { size.toPx().roundToInt() }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            image?.let {
                drawImage(
                    image = it,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(SpriteSheetDecoder.FRAME_PX, SpriteSheetDecoder.FRAME_PX),
                    dstSize = IntSize(px, px),
                    filterQuality = FilterQuality.None,
                )
            }
        }
    }
}
