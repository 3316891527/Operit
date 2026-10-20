package com.ai.assistance.operit.ui.common.oktoast

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.widget.PopupWindow
import androidx.annotation.StringRes
import com.ai.assistance.operit.util.AppLogger
import java.lang.ref.WeakReference
import java.util.ArrayDeque

/** 应用内提示统一入口，状态必须由调用方根据操作结果明确选择。 */
object OKToast {
    const val LENGTH_SHORT = 0
    const val LENGTH_LONG = 1
    enum class Kind { Info, Success, Warning, Error }
    internal enum class Placement { Bottom, Top }
    data class Appearance(val dark: Boolean, val surface: Int, val text: Int)
    private var appearance = Appearance(false, android.graphics.Color.WHITE, android.graphics.Color.BLACK)

    fun useAppearance(dark: Boolean, surface: Int, text: Int) {
        appearance = Appearance(dark, surface, text)
    }

    class Notice internal constructor(internal val message: String, internal val kind: Kind, internal val duration: Int, internal val placement: Placement = Placement.Bottom) {
        fun show() { main.post { enqueue(this) } }
    }

    fun info(context: Context, text: CharSequence, duration: Int = LENGTH_SHORT) = Notice(text.toString(), Kind.Info, duration)
    fun info(context: Context, @StringRes text: Int, duration: Int = LENGTH_SHORT) = info(context, context.getText(text), duration)
    fun success(context: Context, text: CharSequence, duration: Int = LENGTH_SHORT) = Notice(text.toString(), Kind.Success, duration)
    fun success(context: Context, @StringRes text: Int, duration: Int = LENGTH_SHORT) = success(context, context.getText(text), duration)
    fun warning(context: Context, text: CharSequence, duration: Int = LENGTH_SHORT) = Notice(text.toString(), Kind.Warning, duration)
    fun warning(context: Context, @StringRes text: Int, duration: Int = LENGTH_SHORT) = warning(context, context.getText(text), duration)
    fun error(context: Context, text: CharSequence, duration: Int = LENGTH_SHORT) = Notice(text.toString(), Kind.Error, duration)
    fun error(context: Context, @StringRes text: Int, duration: Int = LENGTH_SHORT) = error(context, context.getText(text), duration)
    /** 请求错误沿用上方卡片，普通操作提示仍在窗口下方显示。 */
    fun requestError(context: Context, text: CharSequence, duration: Int = LENGTH_LONG) = Notice(text.toString(), Kind.Error, duration, Placement.Top)
    fun typed(context: Context, text: CharSequence, kind: Kind, duration: Int = LENGTH_SHORT) = Notice(text.toString(), kind, duration)

    private val main = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<Notice>()
    private var activityHost: WeakReference<View>? = null
    private val windowHosts = mutableListOf<WeakReference<View>>()
    private var popup: PopupWindow? = null
    private var popupPlacement = Placement.Bottom
    private var lastMessage = ""
    private var lastKind = Kind.Info
    private var lastAt = 0L
    private var initialized = false
    private val dismiss = Runnable { dismissCurrent(animate = true) }

    fun initialize(application: Application) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (initialized) return
        initialized = true
        val dark = application.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        appearance = Appearance(dark, if (dark) 0xFF25272B.toInt() else 0xFFFFFBFF.toInt(), if (dark) 0xFFF0F0F4.toInt() else 0xFF202126.toInt())
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                queue.clear()
                dismissCurrent(animate = false)
                activityHost = WeakReference(activity.window.decorView)
            }
            override fun onActivityPaused(activity: Activity) {
                if (activityHost?.get() === activity.window.decorView) {
                    activityHost = null
                    queue.clear()
                    dismissCurrent(animate = false)
                }
            }
            override fun onActivityDestroyed(activity: Activity) = Unit
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        })
    }

    /** 悬浮聊天窗口使用自己已有的窗口 token，不请求额外权限。返回值用于对称注销。 */
    fun registerWindow(view: View): () -> Unit {
        check(Looper.myLooper() == Looper.getMainLooper())
        val reference = WeakReference(view)
        windowHosts.add(reference)
        return {
            windowHosts.remove(reference)
            queue.clear()
            dismissCurrent(animate = false)
        }
    }

    private fun host(): View? {
        windowHosts.removeAll { it.get() == null }
        val hosts = buildList {
            activityHost?.let { add(it) }
            addAll(windowHosts)
        }
        return hosts.mapNotNull { it.get() }.lastOrNull { it.isAttachedToWindow && it.isShown }
    }

    private fun enqueue(notice: Notice) {
        if (notice.message.isBlank() || host() == null) return
        val now = SystemClock.uptimeMillis()
        if (notice.kind != Kind.Error && notice.message == lastMessage && notice.kind == lastKind && now - lastAt < 800L) return
        lastMessage = notice.message
        lastKind = notice.kind
        lastAt = now
        // 保留最近的提示，避免大量后台完成事件形成长时间陈旧队列。
        if (queue.size == 5) queue.removeFirst()
        queue.addLast(notice)
        if (popup == null) showNext()
    }

    private fun showNext() {
        if (queue.isEmpty()) return
        val anchor = host()
        if (anchor == null) { queue.clear(); return }
        val notice = queue.removeFirst()
        val context = anchor.context
        val density = context.resources.displayMetrics.density
        val bounds = android.graphics.Rect()
        anchor.getWindowVisibleDisplayFrame(bounds)
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        if (!bounds.intersect(location[0], location[1], location[0] + anchor.width, location[1] + anchor.height)) return
        // 按当前窗口可见区域定位，键盘、分屏与悬浮窗口都使用各自的可用范围。
        val maxWidth = (bounds.width() * 0.9f).toInt().coerceAtLeast(1)
        val maxHeight = (bounds.height() * 0.25f).toInt().coerceAtLeast(1)
        val isRequestError = notice.placement == Placement.Top
        val card = OKToastView(context, notice.kind, notice.message, appearance, maxHeight, isRequestError)
        card.measure(
            View.MeasureSpec.makeMeasureSpec(maxWidth, if (isRequestError) View.MeasureSpec.EXACTLY else View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST),
        )
        val x = bounds.centerX() - card.measuredWidth / 2
        val targetY = if (isRequestError) {
            bounds.top + bounds.height() * 0.08f
        } else {
            bounds.top + bounds.height() * 0.9f - card.measuredHeight / 2f
        }
        val y = targetY.toInt().coerceIn(bounds.top, (bounds.bottom - card.measuredHeight).coerceAtLeast(bounds.top))
        val window = PopupWindow(card, card.measuredWidth, card.measuredHeight, false).apply {
            setIsLaidOutInScreen(true)
            isTouchable = true
            isOutsideTouchable = false
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            elevation = 10 * density
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            // 应用子窗口不获取输入焦点，也不盖住系统权限和其他应用。
            windowLayoutType = WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL
            setOnDismissListener {
                if (popup === this) {
                    popup = null
                    main.removeCallbacks(dismiss)
                    showNext()
                }
            }
        }
        popupPlacement = notice.placement
        popup = window
        try {
            window.showAtLocation(anchor, Gravity.TOP or Gravity.LEFT, x, y)
        } catch (error: WindowManager.BadTokenException) {
            popup = null
            queue.clear()
            AppLogger.w("OKToast", "提示宿主窗口已失效", error)
            return
        }
        card.announceForAccessibility(card.contentDescription)
        if (ValueAnimator.areAnimatorsEnabled()) {
            card.alpha = 0f
            card.translationY = (if (isRequestError) -8 else 8) * density
            card.animate().alpha(1f).translationY(0f).setDuration(180).start()
        }
        val readingTime = (2200 + notice.message.length * 35).coerceIn(2800, 10000)
        val base = if (notice.duration == LENGTH_LONG) readingTime.coerceAtLeast(5000) else readingTime
        val accessibility = requireNotNull(context.getSystemService(AccessibilityManager::class.java))
        val timeout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            accessibility.getRecommendedTimeoutMillis(base, AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_ICONS)
        } else base
        main.postDelayed(dismiss, timeout.toLong())
    }

    private fun dismissCurrent(animate: Boolean) {
        main.removeCallbacks(dismiss)
        val window = popup ?: return
        val card = window.contentView
        card.animate().cancel()
        if (animate && ValueAnimator.areAnimatorsEnabled() && card.isAttachedToWindow) {
            card.animate().alpha(0f).translationY((if (popupPlacement == Placement.Top) -6 else 6) * card.resources.displayMetrics.density).setDuration(140)
                .withEndAction { if (popup === window) window.dismiss() }.start()
        } else {
            popup = null
            window.setOnDismissListener(null)
            window.dismiss()
        }
    }
}