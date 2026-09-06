package com.lollipop.tamagotchi.presentation.screen.setup

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lollipop.tamagotchi.R
import com.lollipop.tamagotchi.core.attribute.FoodFlavor
import com.lollipop.tamagotchi.data.sprite.SpriteRepository
import com.lollipop.tamagotchi.data.sprite.SpriteRepository.PetEntry
import com.lollipop.tamagotchi.domain.generator.PersonalityGenerator
import com.lollipop.tamagotchi.domain.model.Personality
import com.lollipop.tamagotchi.domain.model.Traits
import com.lollipop.tamagotchi.presentation.component.PillItem
import com.lollipop.tamagotchi.presentation.component.RoundEdgeSpace
import com.lollipop.tamagotchi.presentation.component.roundEdgeFade
import com.lollipop.tamagotchi.presentation.render.SpriteSheetDecoder
import com.lollipop.tamagotchi.presentation.screen.ChevronDir
import com.lollipop.tamagotchi.presentation.screen.MiniChevron
import com.lollipop.tamagotchi.presentation.screen.TRAIT_NAMES_RES
import com.lollipop.tamagotchi.presentation.screen.labelRes
import com.lollipop.tamagotchi.presentation.theme.ColorToken
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 建档屏（M3.S2 · 无档首启 / debug 重开档后进入）：
 *
 * - 列表：SpriteRepository.listPets 主名聚合全量；缩略按可视行懒解码，
 *   表级 Bitmap LRU 池 ≤12（doc/07 §6），回收替换防尖峰。
 * - 交互：点宠 → 确定性 seed 派生性格 → 预览（描述词 + 六维 trait 条 + 偏好）→ 确认建档。
 * - 确认只上抛 (pet, personality)：存档/建档/切回主屏由调用方（PetActivity 路由）负责。
 * - 建档即随机的性格不落中间态：预览与确认共用同一 Personality，无二次随机。
 */
@Composable
fun SetupProfileScreen(
    onConfirm: (pet: PetEntry, personality: Personality) -> Unit,
) {
    val ctx = LocalContext.current
    val pool = remember(ctx) { SpriteThumbPool(ctx.assets) }
    // 屏退出即整体回收缩略池：建档页离开后不再需要任何缩略图（子 PetPortrait 先 unpin、此后再全量 clear，顺序安全）
    DisposableEffect(pool) { onDispose { pool.clear() } }
    val pets = remember(ctx) { SpriteRepository(ctx.assets).listPets() }
    var selected by remember { mutableStateOf<PetEntry?>(null) }
    var personality by remember { mutableStateOf<Personality?>(null) }

    // 预览态 → 列表态（系统返回键同义）
    BackHandler(enabled = selected != null) { selected = null }

    Box(
        Modifier
            .fillMaxSize()
            .background(ColorToken.bg),
    ) {
        val current = selected
        if (current == null) {
            PetListContent(
                pets = pets,
                pool = pool,
                onSelect = { pet ->
                    selected = pet
                    personality = PersonalityGenerator.generate(kotlin.random.Random.nextLong())
                },
            )
        } else {
            val preview = personality
                ?: PersonalityGenerator.generate(kotlin.random.Random.nextLong())
            SetupDetailContent(
                pet = current,
                personality = preview,
                pool = pool,
                onBack = { selected = null },
                onConfirm = { onConfirm(current, preview) },
            )
        }
    }
}

// ── 列表态 ────────────────────────────────────────────────────────────────

@Composable
private fun PetListContent(
    pets: List<PetEntry>,
    pool: SpriteThumbPool,
    onSelect: (PetEntry) -> Unit,
) {
    // 圆屏规范 [space、title、item...、space]（doc/06 §8.1）：标题作为内容首元素、初始居中并随滚动上行，
    // 首末 RoundEdgeSpace 留白；行懒加载 + 上下缘 EdgeFade 保证圆形小屏可读。
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .roundEdgeFade(),
        contentPadding = PaddingValues(horizontal = 18.dp),
    ) {
        item { RoundEdgeSpace() }
        item {
            Column {
                Text(
                    stringResource(R.string.setup_title),
                    color = ColorToken.Accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.setup_subtitle, pets.size),
                    color = ColorToken.Text2,
                    fontSize = 11.sp,
                )
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
        items(items = pets, key = { it.id }) { pet ->
            PetRow(pet = pet, pool = pool, onClick = { onSelect(pet) })
            Spacer(Modifier.height(4.dp))
        }
        item { Spacer(Modifier.height(14.dp)) }
        item { RoundEdgeSpace() }
    }
}

@Composable
private fun PetRow(
    pet: PetEntry,
    pool: SpriteThumbPool,
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
        PetPortrait(file = pet.defaultFile, pool = pool, size = 26.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            pet.displayName,
            color = ColorToken.Accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        MiniChevron(dir = ChevronDir.Right, tint = ColorToken.Accent, size = 18.dp)
    }
}

// ── 预览 / 确认态 ────────────────────────────────────────────────────────

@Composable
private fun SetupDetailContent(
    pet: PetEntry,
    personality: Personality,
    pool: SpriteThumbPool,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    // 内容型页面同样遵守圆屏规范 [space、title、item...、space]（doc/06 §8.1）：
    // 返回+名称头行是滚动内容首元素、打开时落在屏中（不再钉在屏顶被圆沿裁切）；
    // 头像/描述/六维数值/确认钮随滚动上行进入屏中可读带，末段留白保证末项（确认钮）也能滚到屏中。
    Column(
        modifier = Modifier
            .fillMaxSize()
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
                pet.displayName,
                color = ColorToken.Accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(34.dp))
        }
        Spacer(Modifier.height(6.dp))
        PetPortrait(file = pet.defaultFile, pool = pool, size = 74.dp)
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

/** 六维 trait 条（顺序与 Traits.NAMES / orderedValues 一致）。
 * 注意：不锁行高（此前 height(10.dp) 把 8sp 标题字上下裁掉导致标题看不见），
 * 行高由文本自然撑开；标签/数值列宽留足，字体不小于 10sp 保证圆屏可读。 */
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

// ── LRU 缩略池（doc/07 §6） ─────────────────────────────────────────────

/**
 * 建档/预览共用的 1-Bitmap/宠 LRU 池（整表 256×256，帧 0 裁剪展示）。
 * 上限 12 张表；逐出即 recycle。解码在 IO 线程串行执行（内部锁），可见行懒加载。
 *
 * **Pin 保护（曾 CRASH：`Canvas: trying to use a recycled bitmap`）**：
 * LazyColumn 会保留滚出可视区的行（key 缓存），其 PetPortrait 仍持有位图引用；
 * 若池按纯 LRU 逐出该位图并 recycle，回滚显示时 drawImage 即崩。
 * 因此组合存续期间由 [pin]/[unpin] 计数锁定——池只回收非 Pin 项；
 * 全部 Pin 时允许短暂超 cap（同时组合的宠数量上界很小，内存可控）。
 */
private class SpriteThumbPool(
    private val assets: AssetManager,
    private val cap: Int = 12,
) {
    private val cache = object : LinkedHashMap<String, Bitmap>(cap, 0.75f, true) {}
    /** file → 仍在组合中引用它的 PetPortrait 数量。 */
    private val pinned = HashMap<String, Int>()
    private val lock = Any()

    /** 进入组合时持有该宠位图（PetPortrait 组合期调用，可与 [unpin] 嵌套计数）。 */
    fun pin(file: String) = synchronized(lock) {
        pinned[file] = (pinned[file] ?: 0) + 1
    }

    /** 离开组合时释放持有（与 [pin] 对称）。 */
    fun unpin(file: String) = synchronized(lock) {
        val n = (pinned[file] ?: 1) - 1
        if (n <= 0) pinned.remove(file) else pinned[file] = n
    }

    fun get(file: String): Bitmap? = synchronized(lock) {
        cache[file]?.let { return it }
        // 只逐出非 Pin 项；全被 Pin 时放行（短暂超 cap），绝不回收仍在绘制中的位图
        val itr = cache.entries.iterator()
        while (cache.size >= cap && itr.hasNext()) {
            val entry = itr.next()
            if ((pinned[entry.key] ?: 0) > 0) continue
            itr.remove()
            entry.value.recycle()
        }
        val decoded = runCatching {
            assets.open("sprite/$file").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
        if (decoded != null) cache[file] = decoded
        decoded
    }

    /** 整屏退出时清空并回收全部剩余位图（子组合已先 unpin，此处为最后兜底）。 */
    fun clear() = synchronized(lock) {
        cache.values.forEach { it.recycle() }
        cache.clear()
        pinned.clear()
    }
}

/** 列表/预览通用宠物肖像：切 4×4 表左上帧 0（下朝向静止帧），放大显示。 */
@Composable
private fun PetPortrait(
    file: String,
    pool: SpriteThumbPool,
    size: Dp,
) {
    var sheet by remember(file) { mutableStateOf<Bitmap?>(null) }
    // 组合存续即 Pin：保证池不回收本肖像持有的位图（先于下方 LaunchedEffect 解码执行）。
    DisposableEffect(file) {
        pool.pin(file)
        onDispose { pool.unpin(file) }
    }
    // 懒解码：进入可视区触发一次；滑走后命中 LRU 不重复解码
    LaunchedEffect(file) {
        sheet = withContext(Dispatchers.IO) { pool.get(file) }
    }
    val image = sheet?.let { if (it.isRecycled) null else it.asImageBitmap() }
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
