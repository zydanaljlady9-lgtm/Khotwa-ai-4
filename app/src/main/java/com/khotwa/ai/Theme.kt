package com.khotwa.ai

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/** ألوان خُطوة: داكن هادئ بلمسة تركوازية، وفاتح نظيف. تتبدّل فوراً حسب K.dark. */
object K {
    /** حالة الثيم الحالية. قراءتها داخل Composable تجعل الشاشة تتحدّث عند تغييرها. */
    var dark by mutableStateOf(true)

    private fun pick(d: Long, l: Long): Color = Color(if (dark) d else l)

    val Bg get() = pick(0xFF0A0E14, 0xFFF6F7F9)
    val Surface get() = pick(0xFF111821, 0xFFFFFFFF)
    val SurfaceHigh get() = pick(0xFF1A2330, 0xFFEBEFF4)
    val Border get() = pick(0xFF263244, 0xFFD8DEE6)
    val Text get() = pick(0xFFE8EEF5, 0xFF0F172A)
    val Muted get() = pick(0xFF8A99AD, 0xFF566377)
    val Accent get() = pick(0xFF5EEAD4, 0xFF0D9488)
    val OnAccent get() = pick(0xFF05201C, 0xFFFFFFFF)
    val Sky get() = pick(0xFF38BDF8, 0xFF0284C7)
    val Danger get() = pick(0xFFFB7185, 0xFFE11D48)
    val Ok get() = pick(0xFF34D399, 0xFF059669)
    val CodeBg get() = pick(0xFF0D131B, 0xFFF1F3F6)
    val WelcomeTop get() = pick(0xFF0B1A1C, 0xFFE3F5F2)
    val Violet get() = pick(0xFFA78BFA, 0xFF7C3AED)

    /** تدرّج الهوية: تركوازي إلى أزرق، كالحلقة في الأيقونة. */
    val AccentBrush: Brush get() = Brush.linearGradient(listOf(Accent, Sky))
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun KhotwaTheme(content: @Composable () -> Unit) {
    val sys = isSystemInDarkTheme()
    val want = resolveDark(GuideState.themeMode, sys)
    // عند وضع «النظام» وتغيّر ثيم الهاتف أثناء فتح التطبيق.
    SideEffect { if (K.dark != want) K.dark = want }
    val dark = K.dark
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val w = view.context.findActivity()?.window
            if (w != null) {
                val c = WindowCompat.getInsetsController(w, view)
                c.isAppearanceLightStatusBars = !dark
                c.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = K.Accent,
            onPrimary = K.OnAccent,
            secondary = K.Sky,
            background = K.Bg,
            onBackground = K.Text,
            surface = K.Surface,
            onSurface = K.Text,
            surfaceVariant = K.SurfaceHigh,
            onSurfaceVariant = K.Muted,
            outline = K.Border,
            error = K.Danger
        )
    } else {
        lightColorScheme(
            primary = K.Accent,
            onPrimary = K.OnAccent,
            secondary = K.Sky,
            background = K.Bg,
            onBackground = K.Text,
            surface = K.Surface,
            onSurface = K.Text,
            surfaceVariant = K.SurfaceHigh,
            onSurfaceVariant = K.Muted,
            outline = K.Border,
            error = K.Danger
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** شفق ناعم متحرك خلف الشاشات الرئيسية. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "aurora")
    val drift by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift"
    )
    val strength = if (K.dark) 0.22f else 0.16f
    val c1 = K.Accent
    val c2 = K.Violet
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val a = Offset(w * (0.15f + 0.35f * drift), h * 0.10f)
        val b = Offset(w * (0.95f - 0.35f * drift), h * 0.34f)
        drawCircle(
            brush = Brush.radialGradient(listOf(c1.copy(alpha = strength), Color.Transparent), center = a, radius = w * 0.95f),
            radius = w * 0.95f,
            center = a
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(c2.copy(alpha = strength * 0.9f), Color.Transparent), center = b, radius = w * 0.85f),
            radius = w * 0.85f,
            center = b
        )
    }
}

/** شعار خُطوة: حلقة متدرّجة متوهجة ونقطة ومسار خطوات، كأيقونة التطبيق تماماً. */
@Composable
fun KhotwaLogo(boxSize: Dp, animated: Boolean = false, modifier: Modifier = Modifier) {
    val p = if (animated) {
        val t = rememberInfiniteTransition(label = "logo")
        t.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
            label = "ring"
        ).value
    } else 0f
    val c1 = K.Accent
    val c2 = K.Sky
    val c3 = K.Violet
    Canvas(modifier.size(boxSize)) {
        val m = size.minDimension
        // الشعار الكبير يحمل «الخطوات» مثل أيقونة التطبيق، والصغير حلقة ونقطة فقط.
        val trail = boxSize.value >= 56f
        val c = if (trail) Offset(m * 0.60f, m * 0.40f) else Offset(m * 0.5f, m * 0.5f)
        val r = if (trail) m * 0.25f else m * 0.31f
        val ringBrush = Brush.linearGradient(
            colors = listOf(c1, c2, c3),
            start = Offset(c.x - r, c.y + r),
            end = Offset(c.x + r, c.y - r)
        )
        // هالة ناعمة
        drawCircle(
            brush = Brush.radialGradient(listOf(c1.copy(alpha = 0.30f), Color.Transparent), center = c, radius = r * 1.9f),
            radius = r * 1.9f,
            center = c
        )
        if (animated) {
            drawCircle(
                brush = ringBrush,
                radius = r + p * m * 0.20f,
                center = c,
                alpha = (1f - p) * 0.6f,
                style = Stroke(width = m * 0.03f)
            )
        }
        drawCircle(color = c1.copy(alpha = 0.10f), radius = r, center = c)
        drawCircle(brush = ringBrush, radius = r, center = c, style = Stroke(width = m * 0.07f))
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.White, c1),
                center = Offset(c.x - m * 0.012f, c.y - m * 0.015f),
                radius = m * 0.10f
            ),
            radius = m * 0.075f,
            center = c
        )
        if (trail) {
            drawCircle(color = c1.copy(alpha = 0.50f), radius = m * 0.034f, center = Offset(m * 0.17f, m * 0.80f))
            drawCircle(color = c1.copy(alpha = 0.72f), radius = m * 0.044f, center = Offset(m * 0.27f, m * 0.71f))
            drawCircle(color = c1.copy(alpha = 0.95f), radius = m * 0.055f, center = Offset(m * 0.38f, m * 0.61f))
        }
    }
}
