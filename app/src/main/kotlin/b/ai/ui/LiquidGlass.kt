package b.ai.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.highlight.Highlight
import b.ai.core.Cfg
import b.ai.ui.theme.AccentBlue
import b.ai.ui.theme.AccentBlueSoft
import b.ai.ui.theme.DarkColors
import b.ai.ui.theme.SurfaceMuted
import b.ai.ui.theme.SurfaceWhite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 卡片采样源：只登记页面背景那一层（不含卡片自己），没登记的页面卡片照旧是白卡 */
val LocalCardBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** 最近一次登记的背景层：抽屉这类挂在 Activity 层、不在页面树里的浮层靠它取采样源 */
object CardBackdropHolder {
    var current: Backdrop? by mutableStateOf(null)
} //全局背景层

/** 玻璃的性能开关：只保留「整屏弹窗」这一档。滑动与按压动画不再降档：降档会在滑动瞬间把全站卡片刷成白色，而且每切一次就往 UI 线程扔一次整页重组（实测 130~260ms） */
object GlassPerf {
    /** 列表正在滑：只给掉帧探针看，不参与降档 */
    var scrolling by mutableStateOf(false)
    /** 底栏胶囊、按压这类自绘动画在跑：同上，仅记录 */
    var anim by mutableStateOf(false)
    /** 带遮罩的真弹窗开着：整屏已经有一次离屏了，底下卡片不许再各做一遍 */
    var dialog by mutableStateOf(false)
    /** 要不要真玻璃：只有弹窗期间关掉。没有「刚进页面」「正在滑动」这两档，它们都会让画面白一下再变回来 */
    val heavy: Boolean get() = !dialog

    private var seq = 0
    /**
     * 首屏是否已经过去（进程起来 1.2 秒后置真）。读它要的是一次性判断，所以用普通字段而不是 State：
     * 它一变就让全站重组的话，又回到"切换一下就卡一下"的老路
     */
    @Volatile
    var ready = false
    /**
     * 升级到真玻璃的错峰延迟（毫秒）。
     * 冷启动时所有大卡片会在同一帧完成测量、同时去建离屏节点，实测能顶出 219ms 的长帧，
     * 所以首屏期间按序号错开 0~140ms；
     * 首屏之后（切设置分类、切页面）只错开 0~40ms：既不会一帧建一堆，也不会白着等
     */
    fun stagger(): Long {
        val s = settle
        val now = android.os.SystemClock.uptimeMillis()
        //刚换过内容：先一起按住到窗口结束，再按序号散开升级；
        //不按住的话，切分类那一帧会有几十个大卡片同时去建离屏节点，直接顶出 200ms
        val hold = if (s == 0L) 0L else (s + SETTLE_MS - now).coerceAtLeast(0L)
        //窗口内升级的这批要散得更开（0~190ms），否则一出窗口又是二三十个挤在一起
        val step = if (hold > 0L) 12L else if (ready) 2L else 7L
        return hold + ((seq++ % 20) * step).toLong()
    }
    /** 内容刚换过（切设置分类、切页面）的时刻 */
    @Volatile
    private var settle = 0L
    /** 换内容时调一次：新组合出来的大卡片第一帧先平涂，再错峰升级成真玻璃 */
    fun switched() {
        settle = android.os.SystemClock.uptimeMillis()
    }
    /** 内容刚换过的那一小段时间：这段时间里新组合的大卡片第一帧不做离屏 */
    val warming: Boolean
        get() {
            val s = settle
            return s != 0L && android.os.SystemClock.uptimeMillis() - s < SETTLE_MS
        }
    private const val SETTLE_MS = 200L
} //玻璃降档开关

/** 页面把自己那层纯背景登记给全局，同时下发 CompositionLocal；离开页面自动清掉 */
@Composable
fun ProvideCardBackdrop(bd: Backdrop, content: @Composable () -> Unit) {
    DisposableEffect(bd) {
        CardBackdropHolder.current = bd
        onDispose { if (CardBackdropHolder.current === bd) CardBackdropHolder.current = null }
    }
    CompositionLocalProvider(LocalCardBackdrop provides bd, content = content)
} //背景层登记

/** LazyColumn 版的联动滚动状态：同样只给探针看，不参与降档 */
@Composable
fun glassLazyListState(): androidx.compose.foundation.lazy.LazyListState {
    val st = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(st.isScrollInProgress) {
        if (st.isScrollInProgress) {
            GlassPerf.scrolling = true
        } else {
            delay(180)
            GlassPerf.scrolling = false
        }
    }
    return st
} //懒列表联动滚动
/** 玻璃联动滚动状态：滑动中关模糊，停手 180ms 再恢复（滚动列表专用） */
@Composable
fun glassScrollState(): ScrollState {
    val st = rememberScrollState()
    LaunchedEffect(st.isScrollInProgress) {
        if (st.isScrollInProgress) {
            GlassPerf.scrolling = true
        } else {
            delay(180)
            GlassPerf.scrolling = false
        }
    }
    return st
} //玻璃联动滚动

/**
 * 页面内容层的采样纹理：只要不是整屏弹窗就挂着。
 * 不能在滑动时摘掉：底栏滑动中也是真玻璃，摘掉它底栏就采不到页面，只能退回白色平涂。
 */
@Composable
fun Modifier.glassPageLayer(bd: LayerBackdrop): Modifier {
    return if (GlassPerf.dialog) this else layerBackdrop(bd)
} //内容层采样纹理

/**
 * 轻档玻璃：不采样、不模糊、不建 RenderEffect，只画近不透明白/黑底 + 主色叠色。
 * 关键点：drawBackdrop 每个节点都是一次离屏合成，把模糊半径调小并不会变便宜，
 * 只有「不进 drawBackdrop」才是真的零成本。
 */
private fun Modifier.glassLite(shape: Shape, tint: Color, veil: Float): Modifier {
    val solid = if (DarkColors) Color(0xFF12161C) else Color(0xFFFFFFFF)
    //半透而不是盖死：透出下面的页面背景，从轻档切到真玻璃时色差很小，不会白一下再变色
    //veil 越大压得越实：弹窗（0.62）压到 0.71 保文字，普通卡片（0.12）只 0.51 保透感
    val a = (0.46f + veil * 0.40f).coerceIn(0.46f, 0.72f)
    var m = background(solid.copy(alpha = a), shape)
    if (tint == AccentBlue || tint == AccentBlueSoft) m = m.background(tint.copy(alpha = 0.55f), shape)
    return m
} //轻档玻璃

/** 按下去的进度：0 松手 / 1 按住，用来放大、加高光；只有可点的卡片才挂，不抢里面的按钮与滑杆 */
private class PressHighlight(private val scope: CoroutineScope, private val onClick: (() -> Unit)?) {
    val progress = Animatable(0f)
    val modifier: Modifier = Modifier.drawWithContent {
        //按压时整块叠一层白色 Plus 提亮，跟参考实现一致
        val p = progress.value
        if (p > 0f) drawRect(Color.White.copy(alpha = 0.25f * p), blendMode = BlendMode.Plus)
        drawContent()
    } //按压提亮
    val gesture: Modifier = Modifier.pointerInput(onClick) {
        detectTapGestures(
            onPress = {
                //按压动画期间整页降档：高光每帧都在变，不降档就是每帧做一遍离屏
                GlassPerf.anim = true
                scope.launch { progress.animateTo(1f, spring(0.5f, 300f)) }
                tryAwaitRelease()
                scope.launch { progress.animateTo(0f, spring(0.5f, 300f)) }
                scope.launch {
                    delay(320)
                    GlassPerf.anim = false
                }
            },
            onTap = if (onClick != null) ({ onClick() }) else null
        )
    } //按压探测
} //按压进度

/** 真液态玻璃卡片：采样页面 -> 提饱和 -> 模糊 -> 边缘折射 -> 高光边 -> 叠加色 */
@Composable
fun LiquidCard(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    corner: Dp = 16.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(corner)
    val scope = rememberCoroutineScope()
    //组合期读一次降档开关：滚动到一半 / 首帧期间直接走轻档
    val heavy = GlassPerf.heavy
    val press = remember(scope, onClick) { PressHighlight(scope, onClick) }
    //轻档：完全不进 drawBackdrop，只留半透底 + 高光边
    if (!heavy) {
        Column(
            modifier = modifier
                .then(Modifier.glassLite(shape, SurfaceWhite, 0.12f))
                .border(1.dp, if (DarkColors) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.70f), shape)
                .then(if (onClick != null) press.modifier.then(press.gesture) else Modifier)
                .padding(contentPadding),
            content = content
        )
        return
    }
    Column(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    padding = maxOf(padding, 24.dp.toPx())
                    vibrancy()
                    //提饱和与离屏模糊只在「没在滑 + 过了首帧 + 卡片够高」时做，其余降档
                    val big = heavy && size.minDimension >= 96.dp.toPx()
                    if (big && Cfg.glassBlur > 0) blur(Cfg.glassBlur.dp.toPx())
                    //边缘折射是 RuntimeShader，只有真·大卡才值得开
                    if (big && size.minDimension >= 140.dp.toPx()) lens(12.dp.toPx(), 14.dp.toPx())
                },
                highlight = {
                    Highlight.Default.copy(alpha = 0.25f + 0.5f * press.progress.value)
                },
                layerBlock = {
                    //按住时整块微微长大，像一层水膜被压下去
                    val s = 1f + (3.dp.toPx() / size.height.coerceAtLeast(1f)) * press.progress.value
                    scaleX = s
                    scaleY = s
                },
                onDrawSurface = {
                    //浅色下什么都不叠：玻璃直接透出页面背景（有背景图就是背景图，没有就是背景的模糊）
                    drawRect(if (DarkColors) Color.Black.copy(alpha = 0.10f) else Color.Transparent)
                    //矮卡片或者降档期间没有模糊，补一层乳白，既能看出是玻璃又不丢文字对比度
                    if (!heavy || size.minDimension < 96.dp.toPx()) {
                        drawRect(
                            if (DarkColors) Color.White.copy(alpha = 0.08f)
                            else Color.White.copy(alpha = 0.55f)
                        )
                    }
                }
            )
            //只有可点的卡片才挂按压：里面的开关、按钮、滑杆照常拿事件
            .then(if (onClick != null) press.modifier.then(press.gesture) else Modifier)
            .padding(contentPadding),
        content = content
    )
} //液态玻璃卡片

/**
 * 任意容器的玻璃底：开了液态玻璃且这一页有背景层就真玻璃，否则退回原来的白底
 * 小控件（胶囊 / 指示点 / 图标底）自动降档：不做离屏模糊，只提饱和 + 叠乳白底。
 * 一页几十个离屏 pass 是滚动卡顿的根因，模糊半径调多小都一样贵。
 */
@Composable
fun Modifier.liquidBg(
    shape: Shape,
    tint: Color = SurfaceWhite,
    highlightAlpha: Float = 0.5f,
    refract: Boolean = false,
    veil: Float = 0.12f,
    blur: Boolean = true,
    //弹窗卡片专用：弹窗一开 GlassPerf.dialog 就把全站降档，弹窗自己那张卡不能跟着变平涂
    always: Boolean = false
): Modifier {
    val bd = LocalCardBackdrop.current ?: CardBackdropHolder.current
    if (!Cfg.glassCard || bd == null) return background(tint, shape)
    //真玻璃的最小尺寸：设置页/聊天页里 156dp 以下的行、输入框、胶囊全部出局。
    //阈值越高，进页面首帧之后要"升级成玻璃"的节点越少，二次组合也就越轻
    val gate = with(LocalDensity.current) { 156.dp.toPx() }
    //首屏之后、且内容没有刚换过时，新建的卡片直接就是真玻璃：切设置分类时不用再白着等
    var big by remember { mutableStateOf(GlassPerf.ready && !GlassPerf.warming) }
    //测量完不立刻升级：错峰几十毫秒再建离屏，避免启动/换页时几个节点同帧一起建
    val scope = rememberCoroutineScope()
    //用普通数组当一次性开关，别用 mutableState：那只会在首帧后再触发一次重组
    val once = remember { booleanArrayOf(GlassPerf.ready && !GlassPerf.warming) }
    //小节点在这里就返回，全程不读 GlassPerf：否则全站几百个胶囊都会订阅全局降档开关，
    //一滑动、一点击就让整棵树重组一遍，那才是"点一下卡一秒"
    if (!big || !blur) {
        return glassLite(shape, tint, veil).onSizeChanged { s ->
            if (!once[0] && minOf(s.width, s.height) >= gate) {
                once[0] = true
                scope.launch {
                    delay(GlassPerf.stagger())
                    big = true
                }
            }
        }
    }
    //走到这里说明是真·大卡片，才订阅降档开关；开关翻转只让这几个节点重组
    //always 的卡（弹窗）不订阅降档：底下整页已经降了，弹窗这一张必须还是真玻璃
    if (!always && !GlassPerf.heavy) return glassLite(shape, tint, veil)
    //白/灰底不叠色，直接透背景；主色底（选中态、发送键）在玻璃上叠一层同色，保住"这个是开着的"
    val overlay = if (tint == AccentBlue || tint == AccentBlueSoft) tint.copy(alpha = 0.55f)
    else Color.Transparent
    //veil 只在弹窗这类需要压住底下的地方用：玻璃照做，再盖一层半透底保住文字可读
    val sheet = if (veil <= 0f) Color.Transparent
    else if (DarkColors) Color.Black.copy(alpha = veil) else Color.White.copy(alpha = veil)
    return drawBackdrop(
        backdrop = bd,
        shape = { shape },
        effects = {
            padding = maxOf(padding, 40.dp.toPx())
            vibrancy()
            if (Cfg.glassBlur > 0) blur(Cfg.glassBlur.dp.toPx())
            //气泡、输入框这类小元素默认不折射，边缘变形会显得脏；矮控件也不值得开 shader
            if (refract && size.minDimension >= 160.dp.toPx()) lens(6.dp.toPx(), 10.dp.toPx())
        },
        highlight = { Highlight.Default.copy(alpha = highlightAlpha) },
        onDrawSurface = {
            drawRect(if (DarkColors) Color.Black.copy(alpha = 0.10f) else Color.Transparent)
            if (overlay.alpha > 0f) drawRect(overlay)
            if (sheet.alpha > 0f) drawRect(sheet)
        }
    )
} //容器玻璃底