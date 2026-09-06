package com.lollipop.tamagotchi.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.wear.compose.foundation.CurvedAlignment
import androidx.wear.compose.foundation.CurvedDirection
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.foundation.basicCurvedText
import com.lollipop.tamagotchi.R
import com.lollipop.tamagotchi.domain.game.Bubble
import com.lollipop.tamagotchi.domain.game.MiniGameEngine
import com.lollipop.tamagotchi.domain.game.MiniGameState
import com.lollipop.tamagotchi.presentation.base.BaseActivity
import com.lollipop.tamagotchi.presentation.component.PillItem
import com.lollipop.tamagotchi.presentation.component.RoundEdgeSpace
import com.lollipop.tamagotchi.presentation.component.roundEdgeFade
import com.lollipop.tamagotchi.presentation.component.roundSafeInset
import com.lollipop.tamagotchi.presentation.theme.ColorToken
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 休闲小游戏（M16，doc/09 §5.6）：挂在右滑功能清单里的轻量泡泡游戏。
 * 独立 Activity（与设置/过往同范式），仅会话级：限时 30s 点破泡泡计分，
 * 不写入 stats、不动宠物状态，返回主屏后无扰动。
 */
class GameActivity : BaseActivity() {

    override fun onBootStart() {
        injectContent(load = { Unit }) {
            GameScreen(onExit = { finish() })
        }
    }
}

private enum class GamePhase { Ready, Playing, Over }

@Composable
private fun GameScreen(onExit: () -> Unit) {
    var phase by remember { mutableStateOf(GamePhase.Ready) }
    var finalScore by remember { mutableIntStateOf(0) }
    when (phase) {
        GamePhase.Ready -> ReadyScreen(onStart = { phase = GamePhase.Playing })
        GamePhase.Playing -> PlayingScreen(
            onOver = { score ->
                finalScore = score
                phase = GamePhase.Over
            },
        )
        GamePhase.Over -> OverScreen(score = finalScore, onExit = onExit)
    }
}

/** 开始页（遵循圆屏列表规范：首末 RoundEdgeSpace + 标题）。 */
@Composable
private fun ReadyScreen(onStart: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .roundEdgeFade()
            .padding(horizontal = roundSafeInset()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item { RoundEdgeSpace() }
        item { PageTitle(stringResource(R.string.game_title)) }
        item {
            Text(
                stringResource(R.string.game_intro),
                color = ColorToken.Text2,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        item {
            PillItem(
                text = stringResource(R.string.game_start),
                filled = true,
                textAlign = TextAlign.Center,
                onClick = onStart,
            )
        }
        item { RoundEdgeSpace() }
    }
}

/** 进行中：主循环 + 泡泡场。状态自管，结束回调带出分数。 */
@Composable
private fun PlayingScreen(onOver: (Int) -> Unit) {
    var state by remember { mutableStateOf(MiniGameEngine.newGame()) }
    val bubbles = remember { mutableStateListOf<Bubble>() }

    LaunchedEffect(Unit) {
        var spawnAcc = 0L
        var nextId = 0
        val rng = Random(42)
        while (true) {
            delay(50)
            state = MiniGameEngine.tick(state, 50L)
            if (state.over) break
            for (i in bubbles.indices) {
                val b = bubbles[i]
                bubbles[i] = b.copy(remainingMs = b.remainingMs - 50L)
            }
            bubbles.removeAll { it.remainingMs <= 0L }
            spawnAcc += 50L
            if (spawnAcc >= MiniGameEngine.SPAWN_INTERVAL_MS && bubbles.size < MiniGameEngine.MAX_BUBBLES) {
                bubbles.add(MiniGameEngine.nextBubble(rng, nextId))
                nextId++
                spawnAcc = 0L
            }
        }
        onOver(state.score)
    }

    fun pop(id: Int) {
        state = MiniGameEngine.pop(state)
        bubbles.removeAll { it.id == id }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        // 圆屏：可玩区是内接圆，气泡映射到单位圆盘内，避免越出圆形屏缘。
        val fieldPx = min(maxWidth.value, maxHeight.value) * 0.82f
        val bubbleR = 15f // dp
        val rEff = fieldPx / 2f - bubbleR
        // 圆弧 HUD 文案需先在非组合 lambda 外解析（CurvedLayout 内容非 @Composable）。
        val hudText = stringResource(
            R.string.game_hud,
            state.timeLeftMs / 1000,
            state.score,
        )
        // 气泡区：保留圆屏安全边距与上下缘渐隐（气泡已在安全圆内，渐隐仅作保险）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .roundEdgeFade()
                .padding(horizontal = roundSafeInset()),
        ) {
            // 为顶部弧形 HUD 让出空间，整体下移，避免与弧形文字重叠。
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                Spacer(Modifier.height(36.dp))
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier.size(fieldPx.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        bubbles.forEach { b ->
                            // 归一化(0..1) → 居中单位方 → 裁剪进单位圆盘
                            var cx = (b.x - 0.5f) * 2f
                            var cy = (b.y - 0.5f) * 2f
                            val len = sqrt(cx * cx + cy * cy)
                            if (len > 1f) {
                                cx /= len
                                cy /= len
                            }
                            Box(
                                modifier = Modifier
                                    .offset(x = (cx * rEff).dp, y = (cy * rEff).dp)
                                    .size((bubbleR * 2f).dp)
                                    .clip(CircleShape)
                                    .background(ColorToken.PillFilled)
                                    .clickable { pop(b.id) },
                            )
                        }
                    }
                }
            }
        }
        // HUD：剩余时间 + 得分，沿圆屏顶部弧贴缘排布（CurvedText），最大化利用圆屏。
        CurvedLayout(
            modifier = Modifier.fillMaxSize(),
            anchor = 270f,
            radialAlignment = CurvedAlignment.Radial.Outer,
            angularDirection = CurvedDirection.Angular.Normal,
        ) {
            basicCurvedText(
                text = hudText,
                style = {
                    CurvedTextStyle(
                        color = ColorToken.Accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                },
            )
        }
    }
}

/** 结算页：显示本局得分 + 返回。 */
@Composable
private fun OverScreen(score: Int, onExit: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .roundEdgeFade()
            .padding(horizontal = roundSafeInset()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item { RoundEdgeSpace() }
        item { PageTitle(stringResource(R.string.game_over)) }
        item {
            Text(
                stringResource(R.string.game_final_score, score),
                color = ColorToken.Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        item {
            PillItem(
                text = stringResource(R.string.game_back),
                textAlign = TextAlign.Center,
                onClick = onExit,
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
