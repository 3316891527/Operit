package com.ai.assistance.operit.ui.common.oktoast

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ai.assistance.operit.R

/** 原生轻量提示卡片可附着 Activity 或已有悬浮窗口，不依赖 Compose 生命周期所有者。 */
internal class OKToastView(context: Context, kind: OKToast.Kind, message: String, appearance: OKToast.Appearance, close: () -> Unit) : LinearLayout(context) {
    init {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val dark = appearance.dark
        val accent = when (kind) {
            OKToast.Kind.Info -> if (dark) 0xFF9BCAFF else 0xFF235D9F
            OKToast.Kind.Success -> if (dark) 0xFF86D9AF else 0xFF246B49
            OKToast.Kind.Warning -> if (dark) 0xFFFFD58D else 0xFF895C14
            OKToast.Kind.Error -> if (dark) 0xFFFFB4AB else 0xFFB3261E
        }.toInt()
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(8), dp(4), dp(8))
        background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(appearance.surface)
            setStroke(dp(1).coerceAtLeast(1), Color.argb(90, Color.red(accent), Color.green(accent), Color.blue(accent)))
        }
        val statusLabel = context.getString(when (kind) {
            OKToast.Kind.Info -> R.string.oktoast_info
            OKToast.Kind.Success -> R.string.oktoast_success
            OKToast.Kind.Warning -> R.string.oktoast_warning
            OKToast.Kind.Error -> R.string.oktoast_error
        })
        contentDescription = "$statusLabel：$message"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(StatusIcon(context, kind, accent), LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(10) })
        val text = TextView(context).apply {
            this.text = message
            textSize = 14f
            setTextColor(appearance.text)
            contentDescription = "$statusLabel：$message"
            setPadding(0, dp(4), 0, dp(4))
        }
        val scroller = object : ScrollView(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(220), MeasureSpec.AT_MOST))
            }
        }.apply {
            isFillViewport = false
            addView(text, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        addView(scroller, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            imageTintList = android.content.res.ColorStateList.valueOf(accent)
            background = null
            contentDescription = context.getString(R.string.close)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { close() }
        }, LayoutParams(dp(48), dp(48)))
    }
}

private class StatusIcon(context: Context, private val kind: OKToast.Kind, private val color: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.scale(width / 24f, height / 24f)
        paint.color = color
        paint.strokeWidth = 2f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.style = Paint.Style.STROKE
        if (kind == OKToast.Kind.Warning) {
            path.reset()
            path.moveTo(12f, 3f); path.lineTo(22f, 21f); path.lineTo(2f, 21f); path.close()
            canvas.drawPath(path, paint)
        } else canvas.drawCircle(12f, 12f, 9f, paint)
        when (kind) {
            OKToast.Kind.Success -> {
                path.reset()
                path.moveTo(7f, 12f); path.lineTo(10f, 15f); path.lineTo(17f, 8f)
                canvas.drawPath(path, paint)
            }
            OKToast.Kind.Info -> {
                canvas.drawLine(12f, 11f, 12f, 16f, paint)
                canvas.drawPoint(12f, 7f, paint)
            }
            OKToast.Kind.Warning, OKToast.Kind.Error -> {
                canvas.drawLine(12f, 8f, 12f, 13f, paint)
                canvas.drawPoint(12f, 17f, paint)
            }
        }
        canvas.restore()
    }
}