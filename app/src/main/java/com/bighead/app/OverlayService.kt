package com.bighead.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.core.app.NotificationCompat
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.widget.LinearLayout

class OverlayService : Service() {

    private var wm: WindowManager? = null
    private var panelView: View? = null
    private var circleView: CircleOverlayView? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f
    private var currentMode = Mode.BIGHEAD
    private val handler = Handler(Looper.getMainLooper())

    // Ссылки на элементы панели
    private var bigheadSliderBox: View? = null
    private var aimSliderBox: View? = null
    private var btnBH: TextView? = null
    private var btnAIM: TextView? = null

    enum class Mode { BIGHEAD, AIM }

    companion object {
        const val CHANNEL_ID  = "bh_ch"
        const val NOTIF_ID    = 42
        const val ACTION_STOP = "STOP"

        fun start(ctx: Context) {
            val i = Intent(ctx, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ctx.startForegroundService(i)
            else
                ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, OverlayService::class.java).apply { action = ACTION_STOP })
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotif())
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        handler.postDelayed({ buildOverlay() }, 200)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { cleanup(); stopSelf() }
        return START_NOT_STICKY
    }

    override fun onDestroy() { cleanup(); super.onDestroy() }

    private fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        try { panelView?.let { if (it.isAttachedToWindow) wm?.removeView(it) } } catch (_: Exception) {}
        try { circleView?.let { if (it.isAttachedToWindow) wm?.removeView(it) } } catch (_: Exception) {}
        panelView = null; circleView = null
    }

    // ── Конвертация dp → px ───────────────────────────────────────────────────

    private fun px(dp: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()

    // ── Строим весь оверлей программно ───────────────────────────────────────

    private fun buildOverlay() {
        try {
            addCircleLayer()
            addPanel()
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private fun addCircleLayer() {
        circleView = CircleOverlayView(this)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        circleView!!.visibility = View.INVISIBLE
        wm!!.addView(circleView, lp)
    }

    private fun addPanel() {
        val panel = buildPanel()
        panelView = panel

        val lp = WindowManager.LayoutParams(
            px(260f),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).also {
            it.gravity = Gravity.TOP or Gravity.START
            it.x = px(16f)
            it.y = px(100f)
        }
        panelParams = lp

        wm!!.addView(panel, lp)

        // Анимация появления
        panel.alpha = 0f; panel.scaleX = 0.7f; panel.scaleY = 0.7f
        panel.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator(1.4f))
            .start()
    }

    // ── Строим панель программно (без XML) ───────────────────────────────────

    private fun buildPanel(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
        }

        // ── Карточка ──────────────────────────────────────────────────────────
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(10f), px(8f), px(10f), px(10f))
            background = roundedBg(Color.parseColor("#141829"), px(18f), Color.parseColor("#7C4DFF"), 2f)
        }

        // Drag handle + close
        val dragRow = FrameLayout(this)
        val handle = View(this).apply {
            background = roundedBg(Color.parseColor("#2A3050"), px(3f))
            layoutParams = FrameLayout.LayoutParams(px(36f), px(4f)).also {
                it.gravity = Gravity.CENTER
            }
        }
        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#5C647E"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            background = roundedBg(Color.parseColor("#1A1F35"), px(14f))
            layoutParams = FrameLayout.LayoutParams(px(28f), px(28f)).also {
                it.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }
            setOnClickListener { cleanup(); stopSelf() }
        }
        dragRow.addView(handle)
        dragRow.addView(closeBtn)
        dragRow.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, px(36f))

        // Toggle row
        val toggleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundedBg(Color.parseColor("#0D1120"), px(12f))
            setPadding(px(4f), px(4f), px(4f), px(4f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, px(44f)).also {
                it.topMargin = px(4f)
                it.bottomMargin = px(4f)
            }
        }

        btnBH = TextView(this).apply {
            text = "BIGHEAD"; gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            letterSpacing = 0.08f
            background = roundedBg(Color.parseColor("#7C4DFF"), px(9f))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }
        btnAIM = TextView(this).apply {
            text = "AIM"; gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#5C647E"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            letterSpacing = 0.08f
            background = roundedBg(Color.TRANSPARENT, px(9f))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }

        btnBH!!.setOnClickListener {
            if (currentMode == Mode.BIGHEAD) return@setOnClickListener
            currentMode = Mode.BIGHEAD
            selectToggle(btnBH!!, btnAIM!!)
            revealView(bigheadSliderBox!!); hideView(aimSliderBox!!); hideCircle()
        }
        btnAIM!!.setOnClickListener {
            if (currentMode == Mode.AIM) return@setOnClickListener
            currentMode = Mode.AIM
            selectToggle(btnAIM!!, btnBH!!)
            revealView(aimSliderBox!!); hideView(bigheadSliderBox!!); showCircle()
        }

        toggleRow.addView(btnBH)
        toggleRow.addView(btnAIM)

        card.addView(dragRow)
        card.addView(toggleRow)

        // ── BIGHEAD slider box ─────────────────────────────────────────────────
        bigheadSliderBox = buildSliderBox("HEAD SIZE", isAim = false)
        // ── AIM slider box ──────────────────────────────────────────────────
        aimSliderBox = buildSliderBox("AIM SIZE", isAim = true)
        aimSliderBox!!.visibility = View.GONE
        aimSliderBox!!.alpha = 0f

        root.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(bigheadSliderBox, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = px(6f) })
        root.addView(aimSliderBox, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = px(6f) })

        // Drag
        setupDrag(dragRow)

        return root
    }

    private fun buildSliderBox(label: String, isAim: Boolean): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(Color.parseColor("#0F1322"), px(12f),
                Color.parseColor("#1A1F35"), 1f)
            setPadding(px(14f), px(10f), px(14f), px(12f))
        }
        val lbl = TextView(this).apply {
            text = label
            setTextColor(if (isAim) Color.parseColor("#00E5FF") else Color.parseColor("#00E5FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            letterSpacing = 0.2f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = px(6f) }
        }
        val seek = SeekBar(this).apply {
            max = 100
            progress = if (isAim) 40 else 50
            progressTintList = android.content.res.ColorStateList.valueOf(
                if (isAim) Color.parseColor("#00E5FF") else Color.parseColor("#7C4DFF"))
            thumbTintList = android.content.res.ColorStateList.valueOf(
                if (isAim) Color.parseColor("#00E5FF") else Color.parseColor("#7C4DFF"))
            if (isAim) {
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar, p: Int, u: Boolean) {
                        circleView?.setRadius(p)
                    }
                    override fun onStartTrackingTouch(sb: SeekBar) {}
                    override fun onStopTrackingTouch(sb: SeekBar) {}
                })
            }
        }
        box.addView(lbl)
        box.addView(seek)
        return box
    }

    // ── Drag ──────────────────────────────────────────────────────────────────

    private fun setupDrag(handle: View) {
        handle.setOnTouchListener { _, ev ->
            val lp = panelParams ?: return@setOnTouchListener false
            val root = panelView ?: return@setOnTouchListener false
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragOffsetX = ev.rawX - lp.x
                    dragOffsetY = ev.rawY - lp.y
                    root.animate().scaleX(1.03f).scaleY(1.03f).setDuration(80).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = (ev.rawX - dragOffsetX).toInt().coerceAtLeast(0)
                    lp.y = (ev.rawY - dragOffsetY).toInt().coerceAtLeast(0)
                    try { wm?.updateViewLayout(root, lp) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    root.animate().scaleX(1f).scaleY(1f).setDuration(180)
                        .setInterpolator(OvershootInterpolator(2f)).start()
                    true
                }
                else -> false
            }
        }
    }

    // ── Drawable helper ───────────────────────────────────────────────────────

    private fun roundedBg(fill: Int, radius: Int,
                           stroke: Int = Color.TRANSPARENT,
                           strokeWidthDp: Float = 0f) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = radius.toFloat()
            if (strokeWidthDp > 0f)
                setStroke(px(strokeWidthDp), stroke)
        }

    // ── Toggle anims ──────────────────────────────────────────────────────────

    private fun selectToggle(on: TextView, off: TextView) {
        on.setTextColor(Color.WHITE)
        on.background = roundedBg(Color.parseColor("#7C4DFF"), px(9f))
        off.setTextColor(Color.parseColor("#5C647E"))
        off.background = roundedBg(Color.TRANSPARENT, px(9f))
        on.animate().scaleX(1.05f).scaleY(1.05f).setDuration(80).withEndAction {
            on.animate().scaleX(1f).scaleY(1f).setDuration(130)
                .setInterpolator(OvershootInterpolator(2f)).start()
        }.start()
    }

    private fun revealView(v: View) {
        v.visibility = View.VISIBLE; v.alpha = 0f; v.translationY = -12f
        v.animate().alpha(1f).translationY(0f).setDuration(260)
            .setInterpolator(AccelerateInterpolator(0.8f)).start()
    }

    private fun hideView(v: View) {
        if (v.visibility == View.GONE) return
        v.animate().alpha(0f).translationY(-10f).setDuration(200)
            .withEndAction { v.visibility = View.GONE }.start()
    }

    private fun showCircle() {
        circleView?.apply {
            visibility = View.VISIBLE; alpha = 0f; scaleX = 0.3f; scaleY = 0.3f
            animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(380).setInterpolator(OvershootInterpolator(1.1f)).start()
        }
    }

    private fun hideCircle() {
        circleView?.apply {
            animate().alpha(0f).scaleX(0.3f).scaleY(0.3f).setDuration(220)
                .withEndAction { visibility = View.INVISIBLE }.start()
        }
    }

    // ── Уведомление ───────────────────────────────────────────────────────────

    private fun createChannel() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "BigHead",
                    NotificationManager.IMPORTANCE_LOW)
            )
    }

    private fun buildNotif(): Notification {
        val pi = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BigHead активен")
            .setContentText("Нажми для остановки")
            .setSmallIcon(R.drawable.ic_launch)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(R.drawable.ic_close, "Остановить", pi)
            .build()
    }
}
