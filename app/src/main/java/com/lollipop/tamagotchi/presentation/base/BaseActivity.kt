package com.lollipop.tamagotchi.presentation.base

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import com.lollipop.tamagotchi.R
import com.lollipop.tamagotchi.presentation.boot.BootLog
import com.lollipop.tamagotchi.presentation.boot.BootStage
import com.lollipop.tamagotchi.presentation.theme.ColorToken
import com.lollipop.tamagotchi.presentation.theme.TamagotchiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity 基座（doc/08 §2）。
 *
 * 职责：
 *  - 纯黑根视图 + 首帧 Logo（原生 View，无 Compose —— 白名单例外）；
 *  - [injectContent]：数据（后台）就绪后注入 Compose 内容并淡入、移除 Logo 壳。
 *  - 离线结算（⑤）由持有档案事实源的组合层编排（PetActivity.EntryFlow，M5.S2 起），
 *    Activity 自身不再承担结算逻辑。
 */
abstract class BaseActivity : ComponentActivity() {

    private lateinit var root: FrameLayout
    private var shellView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this).apply { setBackgroundColor(ColorToken.bg.toArgb()) }
        setContentView(root)
        onBootStart()
    }

    /** 启动入口：默认挂 Logo 壳；子类可覆写以在就绪后调用 [injectContent]。 */
    protected open fun onBootStart() {
        showShell()
    }

    /** 原生 Logo 壳上屏（纯黑 + 居中静态 Logo）。 */
    protected fun showShell() {
        val shell = FrameLayout(this).apply {
            setBackgroundColor(ColorToken.bg.toArgb())
            val logo = ImageView(this@BaseActivity).apply {
                setImageResource(R.drawable.logo_firstframe)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val dp = resources.displayMetrics.density
            val size = (120 * dp).toInt()
            addView(logo, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        }
        shellView = shell
        root.addView(
            shell,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        BootLog.s(BootStage.Shell, "原生 Logo 壳已挂载")
    }

    protected fun removeShell() {
        shellView?.let { if (it.parent === root) root.removeView(it) }
        shellView = null
    }

    /**
     * 数据就绪后注入 Compose 首页，淡入并移除壳。
     * [load] 在后台线程执行（M1 占位瞬时返回）；[content] 为 @Composable 首页。
     */
    protected fun <T> injectContent(
        load: suspend () -> T,
        content: @Composable (T) -> Unit,
    ) {
        lifecycleScope.launch {
            val data = withContext(Dispatchers.Default) { load() }
            val cv = ComposeView(this@BaseActivity).apply {
                alpha = 0f
                setContent {
                    TamagotchiTheme { content(data) }
                }
            }
            root.addView(cv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            cv.animate()
                .alpha(1f)
                .setDuration(160)
                .withEndAction { removeShell() }
                .start()
        }
    }
}
