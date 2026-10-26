package b.ai.ui
import android.os.Build
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceAtMost
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.runtimeShaderEffect

/**页面层采样源：玻璃控件从这里取 backdrop；没有就退化成不透明卡片 */
val LocalPageBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * 液态玻璃的折射：圆角矩形 SDF 把边缘附近的采样坐标往外推，做出透镜/水滴的弯曲感
 * shader 与 Kyant0/AndroidLiquidGlass 同源（Apache 2.0），chromaticAberration > 0 时叠色散
 */
fun BackdropEffectScope.lens(
    refractionHeight: Float,
    refractionAmount: Float,
    depthEffect: Boolean = false,
    chromaticAberration: Float = 0f
) {
    //RuntimeShader 是 API 33 才有的，低版本直接不叠这层（底色与模糊照常）
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    if (refractionHeight <= 0f || refractionAmount <= 0f) return
    if (padding < refractionAmount) padding = refractionAmount
    val radii = cornerRadii() ?: return
    val dispersion = chromaticAberration > 0f
    runtimeShaderEffect(
        key = if (dispersion) "LiquidGlassLensDispersion" else "LiquidGlassLens",
        shaderString = if (dispersion) REFRACTION_DISPERSION else REFRACTION,
        uniformShaderName = "content"
    ) {
        setFloatUniform("size", size.width, size.height)
        setFloatUniform("offset", -padding, -padding)
        setFloatUniform("cornerRadii", radii)
        setFloatUniform("refractionHeight", refractionHeight)
        setFloatUniform("refractionAmount", -refractionAmount)
        setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
        if (dispersion) setFloatUniform("chromaticAberration", chromaticAberration)
    }
} //边缘折射

/** 提饱和：模糊之后画面会发灰，抬到 1.5 倍才像玻璃 */
fun BackdropEffectScope.vibrancy() {
    colorControls(brightness = 0f, contrast = 1f, saturation = 1.5f)
} //提饱和

/** 按当前形状抠出四个角的半径，不是圆角矩形就不做折射 */
private fun BackdropEffectScope.cornerRadii(): FloatArray? {
    val s = shape as? CornerBasedShape ?: return null
    val sizePx = size
    val maxRadius = sizePx.minDimension / 2f
    val isLtr = layoutDirection == LayoutDirection.Ltr
    val topLeft = if (isLtr) s.topStart.toPx(sizePx, this) else s.topEnd.toPx(sizePx, this)
    val topRight = if (isLtr) s.topEnd.toPx(sizePx, this) else s.topStart.toPx(sizePx, this)
    val bottomRight = if (isLtr) s.bottomEnd.toPx(sizePx, this) else s.bottomStart.toPx(sizePx, this)
    val bottomLeft = if (isLtr) s.bottomStart.toPx(sizePx, this) else s.bottomEnd.toPx(sizePx, this)
    return floatArrayOf(
        topLeft.fastCoerceAtMost(maxRadius),
        topRight.fastCoerceAtMost(maxRadius),
        bottomRight.fastCoerceAtMost(maxRadius),
        bottomLeft.fastCoerceAtMost(maxRadius)
    )
} //四角半径

private const val SDF = """
float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}
"""

private const val REFRACTION = """
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
$SDF

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}
half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);
    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));
    float2 refractedCoord = coord + d * grad;
    return content.eval(refractedCoord);
}
"""

private const val REFRACTION_DISPERSION = """
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;
$SDF
float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}
half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);
    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);
    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));
    float2 refractedCoord = coord + d * grad;
    float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
    float2 dispersedCoord = d * grad * dispersionIntensity;

    half4 color = half4(0.0);
    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;
    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;
    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;
    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;

    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;
    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;
    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;
    return color;
}
"""