package com.lollipop.tamagotchi.presentation.screen.setup

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lollipop.tamagotchi.R
import com.lollipop.tamagotchi.data.store.PetState
import com.lollipop.tamagotchi.domain.generator.PersonalityGenerator
import com.lollipop.tamagotchi.domain.model.Personality
import com.lollipop.tamagotchi.domain.model.PetProfile
import com.lollipop.tamagotchi.domain.model.Traits
import com.lollipop.tamagotchi.presentation.base.BaseActivity
import com.lollipop.tamagotchi.presentation.component.PillItem
import com.lollipop.tamagotchi.presentation.component.RoundEdgeSpace
import com.lollipop.tamagotchi.presentation.component.roundEdgeFade
import com.lollipop.tamagotchi.presentation.i18n.PokemonNames
import com.lollipop.tamagotchi.presentation.render.SpriteSheetDecoder
import com.lollipop.tamagotchi.presentation.screen.ChevronDir
import com.lollipop.tamagotchi.presentation.screen.MiniChevron
import com.lollipop.tamagotchi.presentation.screen.TRAIT_NAMES_RES
import com.lollipop.tamagotchi.presentation.screen.labelRes
import com.lollipop.tamagotchi.presentation.theme.ColorToken
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 建档详情页（独立 Activity）：列表页只负责跳转并传入宠主名 + 默认形态文件；
 * seed 由本页生成（开档即骰定一生），预览确认后【经 PetState.startNewCompanion 落档新宠】——
 * 若存在当前档，会先将其存为档案(墓碑)再落档，自己关闭退回首页，首页观察状态切到宠物屏；
 * 不回传消息、不持有 seed。
 */
class PetDetailActivity : BaseActivity() {

    override fun onBootStart() {
        showShell()
        PetState.attach(this)
        val petId = intent.getStringExtra(EXTRA_PET_ID) ?: ""
        val defaultFile = intent.getStringExtra(EXTRA_PET_FILE) ?: "$petId.png"
        // seed 在页面生命周期内稳定：开档骰定；确认时交给 PetState.set 落档（确定性重建同一性格）。
        val seed = Random.nextLong()
        val pool = SpriteThumbPool(assets)
        injectContent(load = { Unit }) {
            DetailContent(
                petId = petId,
                defaultFile = defaultFile,
                personality = remember { PersonalityGenerator.generate(seed) },
                pool = pool,
                onBack = { finish() },
                onConfirm = {
                    // 开启新伴侣：若存在当前档，中央状态机会先将其【存为档案(墓碑)】再落档新宠；
                    // 本页随即关闭，首页观察状态切到宠物屏。
                    PetState.startNewCompanion(
                        PetProfile.new(
                            petId = petId,
                            displayName = PokemonNames.lookup(petId),
                            personality = PersonalityGenerator.generate(seed),
                            now = System.currentTimeMillis(),
                        ),
                    )
                    finish()
                },
            )
        }
    }

    companion object {
        const val EXTRA_PET_ID = "pet_id"
        const val EXTRA_PET_FILE = "pet_file"
    }
}

@Composable
private fun DetailContent(
    petId: String,
    defaultFile: String,
    personality: Personality,
    pool: SpriteThumbPool,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    // 屏退出即回收缩略池（与建档列表共用同一 SpriteThumbPool 实现，仅本页实例）
    DisposableEffect(pool) { onDispose { pool.clear() } }
    // 圆屏规范 [space、title、item...、space]（doc/06 §8.1）：返回+名称头行是滚动内容首元素、打开时落在屏中；
    // 头像/描述/六维数值/确认钮随滚动上行进入屏中可读带，末段留白保证末项（确认钮）也能滚到屏中。
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorToken.bg)
            .verticalScroll(rememberScrollState())
            .roundEdgeFade()
            .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RoundEdgeSpace()
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackArrow(onBack)
            Text(
                PokemonNames.display(petId, PokemonNames.isZh()),
                color = ColorToken.Accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(34.dp))
        }
        Spacer(Modifier.height(6.dp))
        PetPortrait(file = defaultFile, pool = pool, size = 74.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            PersonalityGenerator.describe(personality.traits).phrase,
            color = ColorToken.Text2,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.flavor_pref, stringResource(personality.flavor.labelRes), personality.seed),
            color = ColorToken.Text2,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        TraitBars(personality.traits)
        Spacer(Modifier.height(10.dp))
        PillItem(
            text = stringResource(R.string.setup_confirm),
            filled = true,
            textAlign = TextAlign.Center,
            onClick = onConfirm,
        )
        RoundEdgeSpace()
    }
}

/**
 * 返回箭头（无外圈、字形大而清晰）：不套圆形边框把字形挤小（§11 #5），
 * 中粗字形直接可见，透明点击热区 ≥ 右侧留白宽度，保证标题居中。
 */
@Composable
private fun BackArrow(onBack: () -> Unit) {
    Box(
        Modifier
            .width(34.dp)
            .height(34.dp)
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "‹",
            color = ColorToken.Accent,
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 6.dp, bottom = 2.dp),
        )
    }
}

/** 六维 trait 条（顺序与 Traits.NAMES / orderedValues 一致）。 */
@Composable
private fun TraitBars(traits: Traits) {
    Column(Modifier.fillMaxWidth()) {
        traits.orderedValues.zip(TRAIT_NAMES_RES).forEach { (value, nameRes) ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(nameRes),
                    color = ColorToken.Text2,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(40.dp),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ColorToken.Accent.copy(alpha = 0.14f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(value)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(ColorToken.Accent.copy(alpha = 0.85f)),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    "${(value * 100).roundToInt()}",
                    color = ColorToken.Text2,
                    fontSize = 11.sp,
                    modifier = Modifier.width(26.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}
