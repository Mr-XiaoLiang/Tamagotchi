package com.lollipop.tamagotchi.presentation.screen.setup

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lollipop.tamagotchi.R
import com.lollipop.tamagotchi.data.sprite.SpriteRepository
import com.lollipop.tamagotchi.data.sprite.SpriteRepository.PetEntry
import com.lollipop.tamagotchi.presentation.component.RoundEdgeSpace
import com.lollipop.tamagotchi.presentation.component.roundEdgeFade
import com.lollipop.tamagotchi.presentation.i18n.PokemonNames
import com.lollipop.tamagotchi.presentation.render.SpriteSheetDecoder
import com.lollipop.tamagotchi.presentation.screen.ChevronDir
import com.lollipop.tamagotchi.presentation.screen.MiniChevron
import com.lollipop.tamagotchi.presentation.theme.ColorToken
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 建档屏（M3.S2 · 无档首启 / debug 重开档后进入）：
 *
 * - 列表：SpriteRepository.listPets 主名聚合全量；缩略按可视行懒解码，
 *   表级 Bitmap LRU 池 ≤12（doc/07 §6），回收替换防尖峰。
 * - 交互：点宠 → [onPick] 上抛宠物 → 调用方起独立详情 Activity（seed 由详情页生成）；
 *   本屏只负责选宠与滚动位置保留，不持有 / 不传递 seed。
 */
@Composable
fun SetupProfileScreen(
    onPick: (pet: PetEntry) -> Unit,
) {
    val ctx = LocalContext.current
    val pool = remember(ctx) { SpriteThumbPool(ctx.assets) }
    // 屏退出即整体回收缩略池：建档页离开后不再需要任何缩略图（子 PetPortrait 先 unpin、此后再全量 clear，顺序安全）
    DisposableEffect(pool) { onDispose { pool.clear() } }
    // 仅显示基础形态（prev 为空 = 初始形态）；进化后的形态不作为可选新伙伴（doc：进化/退化）。
    val pets = remember(ctx) {
        SpriteRepository(ctx.assets).listPets()
            .filter { !PokemonNames.isForm(it.id) && PokemonNames.isBase(it.id) }
    }
    // 列表态滚动位置：提升到父组合，进详情再返回时复用同一 LazyListState，保留滚动位置
    val listState = rememberLazyListState()

    Box(
        Modifier
            .fillMaxSize()
            .background(ColorToken.bg),
    ) {
        PetListContent(
            pets = pets,
            listState = listState,
            pool = pool,
            onSelect = onPick,
        )
    }
}

// ── 列表态 ────────────────────────────────────────────────────────────────

@Composable
private fun PetListContent(
    pets: List<PetEntry>,
    listState: LazyListState,
    pool: SpriteThumbPool,
    onSelect: (PetEntry) -> Unit,
) {
    // 圆屏规范 [space、title、item...、space]（doc/06 §8.1）：标题作为内容首元素、初始居中并随滚动上行，
    // 首末 RoundEdgeSpace 留白；行懒加载 + 上下缘 EdgeFade 保证圆形小屏可读。
    LazyColumn(
        state = listState,
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
            PokemonNames.display(pet.id, PokemonNames.isZh()),
            color = ColorToken.Accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        MiniChevron(dir = ChevronDir.Right, tint = ColorToken.Accent, size = 18.dp)
    }
}

// ── LRU 缩略池（doc/07 §6） ─────────────────────────────────────────────

/**
 * 列表/详情共用的 1-Bitmap/宠 LRU 池（整表 256×256，帧 0 裁剪展示）。
 * 上限 12 张表；逐出即 recycle。解码在 IO 线程串行执行（内部锁），可见行懒加载。
 *
 * **Pin 保护（曾 CRASH：`Canvas: trying to use a recycled bitmap`）**：
 * LazyColumn 会保留滚出可视区的行（key 缓存），其 PetPortrait 仍持有位图引用；
 * 若池按纯 LRU 逐出该位图并 recycle，回滚显示时 drawImage 即崩。
 * 因此组合存续期间由 [pin]/[unpin] 计数锁定——池只回收非 Pin 项；
 * 全部 Pin 时允许短暂超 cap（同时组合的宠数量上界很小，内存可控）。
 */
internal class SpriteThumbPool(
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

/** 列表/详情通用宠物肖像：切 4×4 表左上帧 0（下朝向静止帧），放大显示。 */
@Composable
internal fun PetPortrait(
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
