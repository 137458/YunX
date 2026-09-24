/*
 * YunX (云析) - A network drive share-link parser and high-speed downloader for Android.
 * Copyright (C) 2026 CYQawa
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

/*
 * ── 折射与色散着色器来源 ──
 *
 * 本文件里的 ROUNDED_RECT_REFRACTION_SHADER 与 ROUNDED_RECT_REFRACTION_WITH_DISPERSION_SHADER
 * 移植自 kyant 的 AndroidLiquidGlass（com.kyant.backdrop 的 lens 效果与色散采样实现）。
 * miuix 0.9.3 的 miuix-blur 经 javap 确认只提供 blur / colorControls / blendColors /
 * noiseDither / textureBlurEffect 等效果，internal.ShadersKt 中不存在折射或色散着色器，
 * 所以透镜折射必须自带 AGSL。代价是上游修复不会自动流入本项目：升级 AndroidLiquidGlass
 * 或调整折射算法时，需要手工比对并同步这两段着色器。
 *
 * 限制：四角半径按当前 shape 解析（支持任意 CornerBasedShape，并处理 LTR），无法解析的
 * 自定义形状退回胶囊半径，因此 lens() 复用到非胶囊形状时折射依旧可用；但着色器只实现圆角
 * 矩形 SDF，其他轮廓（斜切、不对称路径）仍会被当作圆角矩形处理。
 */

package com.yunx.app.ui.components.liquid

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.colorControls
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.runtimeShaderEffect

/**
 * 双重背景合成采样器。
 * 支持同时叠加底图离屏纹理 (first) 与激活态离屏采样 (second)。
 */
@Stable
class CombinedBackdrop(
    val first: Backdrop,
    val second: Backdrop,
) : Backdrop {
    override val isCoordinatesDependent: Boolean = first.isCoordinatesDependent || second.isCoordinatesDependent
    override val offsetResidualX: Float get() = first.offsetResidualX
    override val offsetResidualY: Float get() = first.offsetResidualY

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Int,
    ) {
        with(first) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
        with(second) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
    }
}

@Composable
fun rememberCombinedBackdrop(first: Backdrop, second: Backdrop): Backdrop =
    remember(first, second) { CombinedBackdrop(first, second) }

/**
 * 液态玻璃色彩饱和度与鲜艳度增强 (Vibrancy)。
 */
fun BackdropEffectScope.vibrancy() {
    colorControls(
        brightness = 0f,
        contrast = 1f,
        saturation = 1.5f,
    )
}

/**
 * AGSL/SkSL 圆角矩形透镜折射效果与色散彩虹晕扩展。
 *
 * 四角半径从当前 [shape] 解析，按短边一半封顶；解析不到时退回胶囊半径，保证折射不静默失效。
 */
fun BackdropEffectScope.lens(
    refractionHeight: Float,
    refractionAmount: Float,
    depthEffect: Boolean = false,
    chromaticAberration: Float = 0f,
) {
    if (!isRuntimeShaderSupported()) return
    if (refractionHeight <= 0f || refractionAmount <= 0f) return
    if (size.width <= 1f || size.height <= 1f) return

    if (padding < refractionAmount) {
        padding = refractionAmount
    }

    val radii = cornerRadiiPx()
    val dispersionEnabled = chromaticAberration > 0f
    val shaderString = if (dispersionEnabled) {
        ROUNDED_RECT_REFRACTION_WITH_DISPERSION_SHADER
    } else {
        ROUNDED_RECT_REFRACTION_SHADER
    }
    val key = if (dispersionEnabled) "LiquidGlassLensDispersion" else "LiquidGlassLens"

    // 采样尺寸与半径按降采样系数缩放，全部带下限守卫，避免 0 尺寸或 0 边缘厚度进入着色器
    val sf = downscaleFactor.coerceAtLeast(1).toFloat()
    val scaledSizeW = (size.width / sf).coerceAtLeast(1f)
    val scaledSizeH = (size.height / sf).coerceAtLeast(1f)
    val scaledPadding = padding / sf
    val scaledRefractionHeight = (refractionHeight / sf).coerceAtLeast(0.001f)
    val scaledRefractionAmount = refractionAmount / sf
    val scaledRadii = FloatArray(radii.size) { radii[it] / sf }

    runtimeShaderEffect(
        key = key,
        shaderString = shaderString,
        uniformShaderName = "content",
    ) {
        setFloatUniform("size", scaledSizeW, scaledSizeH)
        setFloatUniform("offset", -scaledPadding, -scaledPadding)
        setFloatUniform("cornerRadii", scaledRadii)
        setFloatUniform("refractionHeight", scaledRefractionHeight)
        setFloatUniform("refractionAmount", -scaledRefractionAmount)
        setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
        if (dispersionEnabled) {
            setFloatUniform("chromaticAberration", chromaticAberration)
        }
    }
}

/**
 * 解析当前 shape 的四角半径（px）。
 * 顺序为 [左上, 右上, 右下, 左下]，与着色器 radiusAt 的 float4 分量一一对应；
 * 非 [CornerBasedShape] 的形状无法解析，退回胶囊半径。
 */
private fun BackdropEffectScope.cornerRadiiPx(): FloatArray {
    val maxRadius = size.minDimension / 2f
    val cornerShape = shape as? CornerBasedShape
        ?: return floatArrayOf(maxRadius, maxRadius, maxRadius, maxRadius)
    val isLtr = layoutDirection == LayoutDirection.Ltr
    return capCornerRadii(
        floatArrayOf(
            (if (isLtr) cornerShape.topStart else cornerShape.topEnd).toPx(size, this),
            (if (isLtr) cornerShape.topEnd else cornerShape.topStart).toPx(size, this),
            (if (isLtr) cornerShape.bottomEnd else cornerShape.bottomStart).toPx(size, this),
            (if (isLtr) cornerShape.bottomStart else cornerShape.bottomEnd).toPx(size, this),
        ),
        maxRadius,
    )
}

/** 四角半径规整：负值归零、超过短边一半的值封顶。 */
internal fun capCornerRadii(radii: FloatArray, maxRadius: Float): FloatArray {
    val cap = maxRadius.coerceAtLeast(0f)
    return FloatArray(radii.size) { radii[it].coerceIn(0f, cap) }
}

private const val ROUNDED_RECT_REFRACTION_SHADER = """
    uniform shader content;
    uniform float2 size;
    uniform float2 offset;
    uniform float4 cornerRadii;
    uniform float refractionHeight;
    uniform float refractionAmount;
    uniform float depthEffect;

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

    float circleMap(float x) {
        float clampedX = clamp(x, 0.0, 1.0);
        return 1.0 - sqrt(max(0.0, 1.0 - clampedX * clampedX));
    }

    float2 safeNormalize(float2 v) {
        float len = length(v);
        return len > 0.0001 ? v / len : float2(0.0);
    }

    half4 main(float2 coord) {
        if (size.x <= 1.0 || size.y <= 1.0) {
            return content.eval(coord);
        }
        float2 halfSize = size * 0.5;
        float2 centeredCoord = (coord + offset) - halfSize;
        float radius = clamp(radiusAt(centeredCoord, cornerRadii), 0.0, min(halfSize.x, halfSize.y));

        float sd = sdRoundedRect(centeredCoord, halfSize, radius);
        if (-sd >= refractionHeight) {
            return content.eval(coord);
        }
        sd = min(sd, 0.0);

        float d = circleMap(clamp(1.0 - -sd / max(refractionHeight, 1e-4), 0.0, 1.0)) * refractionAmount;
        float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
        float2 gradCenter = depthEffect > 0.5 ? safeNormalize(centeredCoord) : float2(0.0);
        float2 grad = safeNormalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + gradCenter);

        float2 refractedCoord = coord + d * grad;
        return content.eval(refractedCoord);
    }
"""

private const val ROUNDED_RECT_REFRACTION_WITH_DISPERSION_SHADER = """
    uniform shader content;
    uniform float2 size;
    uniform float2 offset;
    uniform float4 cornerRadii;
    uniform float refractionHeight;
    uniform float refractionAmount;
    uniform float depthEffect;
    uniform float chromaticAberration;

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

    float circleMap(float x) {
        float clampedX = clamp(x, 0.0, 1.0);
        return 1.0 - sqrt(max(0.0, 1.0 - clampedX * clampedX));
    }

    float2 safeNormalize(float2 v) {
        float len = length(v);
        return len > 0.0001 ? v / len : float2(0.0);
    }

    half4 main(float2 coord) {
        if (size.x <= 1.0 || size.y <= 1.0) {
            return content.eval(coord);
        }
        float2 halfSize = size * 0.5;
        float2 centeredCoord = (coord + offset) - halfSize;
        float radius = clamp(radiusAt(centeredCoord, cornerRadii), 0.0, min(halfSize.x, halfSize.y));

        float sd = sdRoundedRect(centeredCoord, halfSize, radius);
        if (-sd >= refractionHeight) {
            return content.eval(coord);
        }
        sd = min(sd, 0.0);

        float d = circleMap(clamp(1.0 - -sd / max(refractionHeight, 1e-4), 0.0, 1.0)) * refractionAmount;
        float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
        float2 gradCenter = depthEffect > 0.5 ? safeNormalize(centeredCoord) : float2(0.0);
        float2 grad = safeNormalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + gradCenter);

        float2 refractedCoord = coord + d * grad;
        float halfArea = max(halfSize.x * halfSize.y, 1.0);
        float dispersionIntensity = chromaticAberration * clamp((centeredCoord.x * centeredCoord.y) / halfArea, -2.0, 2.0);
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
