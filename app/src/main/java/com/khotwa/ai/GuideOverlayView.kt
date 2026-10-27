package com.khotwa.ai

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * نافذة شفافة ترسم دائرة صغيرة على العنصر المطلوب، وفقاعة الكلام، وسهم التمرير، وشارة «أراك في…»،
 * ولا تعترض اللمس.
 */
class GuideOverlayView(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private var target: RectF? = null
    private var pulse = 0f
    private var message: String? = null
    private var step: String? = null
    private var heard: String? = null
    private var chip: String? = null

    /** +1 تمرير للأسفل، -1 للأعلى، 0 لا شيء. */
    private var scrollDir = 0

    private val msgBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xEE0F172A.toInt() }
    private val msgBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * d
        color = 0xFF5EEAD4.toInt()
    }
    private val msgText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 16f * d
        typeface = Typeface.DEFAULT_BOLD
    }
    private val stepText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF5EEAD4.toInt()
        textSize = 12f * d
        typeface = Typeface.DEFAULT_BOLD
    }
    private val heardText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFCBD5E1.toInt()
        textSize = 13f * d
    }
    private val heardBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC1E293B.toInt() }
    private val chipText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE2E8F0.toInt()
        textSize = 12.5f * d
        typeface = Typeface.DEFAULT_BOLD
    }
    private val chipBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE60F172A.toInt() }

    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * d
        color = 0x885EEAD4.toInt()
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x265EEAD4
    }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4.5f * d
        color = 0x99FFFFFF.toInt()
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * d
        color = 0xFF5EEAD4.toInt()
    }
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * d
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF5EEAD4.toInt()
    }
    private val chevron = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f * d
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0xFF5EEAD4.toInt()
    }
    private val chevronHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 9f * d
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0x66FFFFFF
    }
    private val chevronPath = Path()

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1400
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            pulse = it.animatedValue as Float
            invalidate()
        }
    }

    private fun ensureAnimating() {
        if (!animator.isStarted) animator.start()
    }

    private fun stopAnimatingIfIdle() {
        if (target == null && scrollDir == 0) animator.cancel()
    }

    fun showMessage(text: String, stepLabel: String? = null) {
        message = text
        step = stepLabel
        invalidate()
    }

    fun hideMessage() {
        message = null
        step = null
        heard = null
        invalidate()
    }

    fun setHeard(text: String?) {
        heard = text
        invalidate()
    }

    /** شارة صغيرة أعلى الشاشة تقول أين تراك خُطوة الآن. null لإخفائها. */
    fun showChip(text: String?) {
        chip = text
        invalidate()
    }

    /** سهم متحرك يدلّ على اتجاه التمرير: +1 للأسفل و-1 للأعلى. */
    fun showScrollHint(dir: Int) {
        scrollDir = if (dir > 0) 1 else if (dir < 0) -1 else 0
        if (scrollDir != 0) ensureAnimating() else stopAnimatingIfIdle()
        invalidate()
    }

    fun hideScrollHint() {
        scrollDir = 0
        stopAnimatingIfIdle()
        invalidate()
    }

    fun showTarget(rect: Rect) {
        target = RectF(rect)
        ensureAnimating()
        invalidate()
    }

    fun hideTarget() {
        target = null
        stopAnimatingIfIdle()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        drawChip(canvas)
        drawMessage(canvas)
        drawScrollHint(canvas)
        val t = target ?: return
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val r = RectF(t.left - loc[0], t.top - loc[1], t.right - loc[0], t.bottom - loc[1])
        // العنصر الذي يبرز جزء منه خارج الشاشة: نرسم على الجزء الظاهر منه فقط.
        if (!r.intersect(0f, 0f, width.toFloat(), height.toFloat())) return
        val cx = r.centerX()
        val cy = r.centerY()
        val shortSide = min(r.width(), r.height())
        val longSide = max(r.width(), r.height())

        // العناصر الكبيرة: إطار خفيف جداً يبيّن حدود الزر.
        if (longSide > 60f * d) {
            val rr = RectF(r)
            rr.inset(-2f * d, -2f * d)
            canvas.drawRoundRect(rr, 12f * d, 12f * d, outline)
        }

        // الدائرة الصغيرة نفسها.
        val radius = min(24f * d, max(15f * d, shortSide / 2f))
        // هالة متوهجة ثم حلقة بتدرّج الهوية (تركوازي، أزرق، بنفسجي) كأيقونة التطبيق.
        glowPaint.shader = android.graphics.RadialGradient(
            cx, cy, radius * 2.3f, intArrayOf(0x555EEAD4, 0x005EEAD4), null, android.graphics.Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius * 2.3f, glowPaint)
        canvas.drawCircle(cx, cy, radius, fillPaint)
        canvas.drawCircle(cx, cy, radius, haloPaint)
        ringPaint.shader = android.graphics.LinearGradient(
            cx - radius, cy + radius, cx + radius, cy - radius,
            intArrayOf(0xFF5EEAD4.toInt(), 0xFF38BDF8.toInt(), 0xFFA78BFA.toInt()), null, android.graphics.Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, ringPaint)
        canvas.drawCircle(cx, cy, 3.4f * d, dotPaint)

        // موجة تتسع وتتلاشى لجذب النظر.
        ripplePaint.color = ((((1f - pulse) * 170).toInt()) shl 24) or 0x005EEAD4
        canvas.drawCircle(cx, cy, radius + pulse * 12f * d, ripplePaint)
    }

    private fun drawChip(canvas: Canvas) {
        val c = chip ?: return
        if (c.isBlank()) return
        val maxW = (width - 48f * d).toInt()
        if (maxW <= 0) return
        val desired = StaticLayout.getDesiredWidth(c, chipText).toInt() + 1
        val w = min(desired, maxW)
        val layout = StaticLayout.Builder.obtain(c, 0, c.length, chipText, w)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
            .setMaxLines(1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setEllipsizedWidth(w)
            .build()
        val padX = 14f * d
        val padY = 7f * d
        val boxW = w + 2 * padX
        val boxH = layout.height + 2 * padY
        val left = (width - boxW) / 2f
        val top = 56f * d
        val box = RectF(left, top, left + boxW, top + boxH)
        canvas.drawRoundRect(box, boxH / 2f, boxH / 2f, chipBg)
        canvas.drawRoundRect(box, boxH / 2f, boxH / 2f, msgBorder)
        canvas.save()
        canvas.translate(left + padX, top + padY)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawScrollHint(canvas: Canvas) {
        val dir = scrollDir
        if (dir == 0) return
        val cx = width / 2f
        val baseY = if (dir > 0) height * 0.55f else height * 0.36f
        val w = 22f * d
        val gap = 15f * d
        for (i in 0..2) {
            val phase = (pulse + i / 3f) % 1f
            val alpha = ((1f - kotlin.math.abs(phase - 0.5f) * 2f) * 255).toInt().coerceIn(40, 255)
            val y = baseY + dir * (i * gap + pulse * 6f * d)
            chevronPath.reset()
            chevronPath.moveTo(cx - w, y)
            chevronPath.lineTo(cx, y + dir * w * 0.6f)
            chevronPath.lineTo(cx + w, y)
            chevron.alpha = alpha
            chevronHalo.alpha = (alpha * 0.4f).toInt()
            canvas.drawPath(chevronPath, chevronHalo)
            canvas.drawPath(chevronPath, chevron)
        }
        chevron.alpha = 255
        val label = if (dir > 0) L.s(R.string.svc_scroll_down_chip) else L.s(R.string.svc_scroll_up_chip)
        val tw = msgText.measureText(label)
        val padX = 16f * d
        val padY = 8f * d
        val lh = msgText.fontMetrics.let { it.descent - it.ascent }
        val boxW = tw + 2 * padX
        val boxH = lh + 2 * padY
        val top = if (dir > 0) baseY + dir * (3 * gap + 18f * d) else baseY - boxH - 18f * d
        val left = (width - boxW) / 2f
        val box = RectF(left, top, left + boxW, top + boxH)
        canvas.drawRoundRect(box, boxH / 2f, boxH / 2f, msgBg)
        canvas.drawRoundRect(box, boxH / 2f, boxH / 2f, msgBorder)
        val layout = StaticLayout.Builder.obtain(label, 0, label.length, msgText, tw.toInt() + 2)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
            .build()
        canvas.save()
        canvas.translate(left + padX, top + padY)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawMessage(canvas: Canvas) {
        val pad = 14f * d
        val w = (width - 32f * d).toInt()
        val innerW = (w - 2 * pad).toInt()
        val left = 16f * d
        var top = height - 96f * d

        val m = message
        if (m != null) {
            val layout = StaticLayout.Builder.obtain(m, 0, m.length, msgText, innerW)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
                .setMaxLines(7)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setEllipsizedWidth(innerW)
                .build()
            val st = step
            val stepLayout = if (st.isNullOrBlank()) null else
                StaticLayout.Builder.obtain(st, 0, st.length, stepText, innerW)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
                    .setMaxLines(1)
                    .build()
            val stepH = if (stepLayout != null) stepLayout.height + 4f * d else 0f
            val h = layout.height + 2 * pad + stepH
            top = height - h - 96f * d
            val box = RectF(left, top, left + w, top + h)
            canvas.drawRoundRect(box, 18f * d, 18f * d, msgBg)
            canvas.drawRoundRect(box, 18f * d, 18f * d, msgBorder)
            canvas.save()
            canvas.translate(left + pad, top + pad)
            if (stepLayout != null) {
                stepLayout.draw(canvas)
                canvas.translate(0f, stepH)
            }
            layout.draw(canvas)
            canvas.restore()
        }

        val hr = heard
        if (!hr.isNullOrBlank()) {
            val line = L.f(R.string.svc_heard, hr)
            val hw = (width - 64f * d).toInt()
            val hl = StaticLayout.Builder.obtain(line, 0, line.length, heardText, hw)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
                .setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setEllipsizedWidth(hw)
                .build()
            val hh = hl.height + 12f * d
            val hTop = top - hh - 8f * d
            val hBox = RectF(left + 16f * d, hTop, left + w - 16f * d, hTop + hh)
            canvas.drawRoundRect(hBox, 14f * d, 14f * d, heardBg)
            canvas.save()
            canvas.translate(left + 32f * d, hTop + 6f * d)
            hl.draw(canvas)
            canvas.restore()
        }
    }
}
