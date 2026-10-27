package com.khotwa.ai

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

/** الزر الدائري العائم: يتغير شكله بحسب حالة المساعد، ويمكن سحبه. */
class FloatingButtonView(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private val sizePx = (68f * d).toInt()

    var onTap: (() -> Unit)? = null
    var onDrag: ((dx: Float, dy: Float) -> Unit)? = null
    var onDragEnd: (() -> Unit)? = null

    var phase: Phase = Phase.IDLE
        set(value) {
            if (field != value) {
                field = value
                updateAnimation()
                invalidate()
            }
        }

    private var t = 0f
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1300
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            t = it.animatedValue as Float
            invalidate()
        }
    }

    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000 }
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ripple = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * d
    }
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0F172A.toInt()
        style = Paint.Style.FILL
    }
    private val glyphLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0F172A.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * d
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    init {
        contentDescription = L.s(R.string.fab_desc)
    }

    private fun updateAnimation() {
        if (phase == Phase.IDLE) {
            anim.cancel()
            t = 0f
        } else if (!anim.isStarted) {
            anim.start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimation()
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(sizePx, sizePx)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = 25f * d

        if (phase == Phase.LISTENING) {
            for (k in 0..1) {
                val p = (t + k * 0.5f) % 1f
                ripple.color = ((((1f - p) * 150).toInt()) shl 24) or 0x00FB7185
                canvas.drawCircle(cx, cy, r + p * 8f * d, ripple)
            }
        }

        canvas.drawCircle(cx, cy + 1.5f * d, r + 1.5f * d, shadow)
        body.color = when (phase) {
            Phase.IDLE -> 0xFF5EEAD4.toInt()
            Phase.LISTENING -> 0xFFFB7185.toInt()
            Phase.THINKING -> 0xFFFBBF24.toInt()
            Phase.SPEAKING -> 0xFF38BDF8.toInt()
        }
        canvas.drawCircle(cx, cy, r, body)

        when (phase) {
            Phase.IDLE, Phase.LISTENING -> drawMic(canvas, cx, cy)
            Phase.THINKING -> drawDots(canvas, cx, cy)
            Phase.SPEAKING -> drawBars(canvas, cx, cy)
        }
    }

    private fun drawMic(canvas: Canvas, cx: Float, cy: Float) {
        rect.set(cx - 4f * d, cy - 12f * d, cx + 4f * d, cy + 2f * d)
        canvas.drawRoundRect(rect, 4f * d, 4f * d, glyph)
        rect.set(cx - 8f * d, cy - 8f * d, cx + 8f * d, cy + 8f * d)
        canvas.drawArc(rect, 0f, 180f, false, glyphLine)
        canvas.drawLine(cx, cy + 8f * d, cx, cy + 12f * d, glyphLine)
        canvas.drawLine(cx - 4f * d, cy + 12f * d, cx + 4f * d, cy + 12f * d, glyphLine)
    }

    private fun drawDots(canvas: Canvas, cx: Float, cy: Float) {
        for (i in 0..2) {
            val p = (t + i * 0.2f) % 1f
            val y = cy - sin(p * Math.PI).toFloat() * 5f * d
            canvas.drawCircle(cx + (i - 1) * 8f * d, y, 2.8f * d, glyph)
        }
    }

    private fun drawBars(canvas: Canvas, cx: Float, cy: Float) {
        for (i in 0..3) {
            val h = (5f + 11f * abs(sin((t + i * 0.17f) * 2f * Math.PI)).toFloat()) * d
            val x = cx + (i - 1.5f) * 6.5f * d
            rect.set(x - 1.8f * d, cy - h / 2f, x + 1.8f * d, cy + h / 2f)
            canvas.drawRoundRect(rect, 1.8f * d, 1.8f * d, glyph)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX
                downY = e.rawY
                lastX = e.rawX
                lastY = e.rawY
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && hypot(e.rawX - downX, e.rawY - downY) > 10f * d) dragging = true
                if (dragging) onDrag?.invoke(e.rawX - lastX, e.rawY - lastY)
                lastX = e.rawX
                lastY = e.rawY
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    onDragEnd?.invoke()
                } else {
                    performClick()
                }
                dragging = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean {
        super.performClick()
        onTap?.invoke()
        return true
    }
}
