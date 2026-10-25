package com.hmc.auto

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class TapService : AccessibilityService() {
    private lateinit var wm: WindowManager
    private lateinit var panel: View
    private lateinit var lp: WindowManager.LayoutParams
    private lateinit var status: TextView
    private lateinit var body: View
    private lateinit var tempoTv: TextView
    private lateinit var tapTv: TextView
    private lateinit var modeBtn: Button
    private var calib: View? = null
    private val h = Handler(Looper.getMainLooper())
    private val token = Any()
    private var playing = false
    private val prefs get() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private var tempoScale: Float
        get() = prefs.getFloat("scale", 1f)
        set(v) { prefs.edit().putFloat("scale", v).apply() }
    private var tapLen: Int
        get() = prefs.getInt("tap", 60)
        set(v) { prefs.edit().putInt("tap", v).apply() }
    private var holdMode: Boolean
        get() = prefs.getBoolean("hold", false)
        set(v) { prefs.edit().putBoolean("hold", v).apply() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    override fun onServiceConnected() {
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        buildPanel()
        refresh()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() { stopPlay() }

    override fun onDestroy() {
        stopPlay()
        try { wm.removeView(panel) } catch (_: Exception) {}
        calib?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        super.onDestroy()
    }

    private fun overlayParams(w: Int, hh: Int) = WindowManager.LayoutParams(
        w, hh, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    )

    private fun refresh() {
        tempoTv.text = "${(tempoScale * 100).toInt()}%"
        tapTv.text = "${tapLen}ms"
        modeBtn.text = if (holdMode) "Nửa cung: giữ" else "Nửa cung: bật/tắt"
    }

    private fun buildPanel() {
        val c = ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault)
        fun b(t: String, cb: () -> Unit) = Button(c).apply {
            text = t; textSize = 12f; minHeight = 0; minimumHeight = dp(36)
            minWidth = 0; minimumWidth = dp(40); setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { cb() }
        }
        fun label() = TextView(c).apply { setTextColor(Color.WHITE); textSize = 12f; gravity = Gravity.CENTER }
        status = label().apply { text = "♪ kéo để di chuyển"; setPadding(dp(4), dp(4), dp(4), dp(4)) }
        tempoTv = label()
        tapTv = label()
        modeBtn = b("") { holdMode = !holdMode; refresh() }
        val r1 = LinearLayout(c).apply {
            addView(b("Căn") { startCalib() })
            addView(b("▶") { startPlay() })
            addView(b("■") { stopPlay() })
        }
        val r2 = LinearLayout(c).apply {
            addView(b("−") { tempoScale = (tempoScale - 0.05f).coerceAtLeast(0.3f); refresh() })
            addView(tempoTv, LinearLayout.LayoutParams(dp(52), -2))
            addView(b("+") { tempoScale = (tempoScale + 0.05f).coerceAtMost(2f); refresh() })
        }
        val r3 = LinearLayout(c).apply {
            addView(b("−") { tapLen = (tapLen - 10).coerceAtLeast(20); refresh() })
            addView(tapTv, LinearLayout.LayoutParams(dp(52), -2))
            addView(b("+") { tapLen = (tapLen + 10).coerceAtMost(300); refresh() })
        }
        body = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            addView(r1); addView(r2); addView(r3); addView(modeBtn)
        }
        val head = LinearLayout(c).apply {
            addView(status, LinearLayout.LayoutParams(0, -2, 1f))
            addView(b("▾") { body.visibility = if (body.visibility == View.VISIBLE) View.GONE else View.VISIBLE })
        }
        panel = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xDD101820.toInt())
            setPadding(dp(6), dp(6), dp(6), dp(6))
            addView(head); addView(body)
        }
        lp = overlayParams(-2, -2).apply { gravity = Gravity.TOP or Gravity.START; x = dp(8); y = dp(60) }
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0
        status.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = ox + (e.rawX - sx).toInt(); lp.y = oy + (e.rawY - sy).toInt()
                    wm.updateViewLayout(panel, lp)
                }
            }
            true
        }
        wm.addView(panel, lp)
    }

    private fun loadPts(): List<FloatArray>? {
        val s = prefs.getString("pts", null) ?: return null
        val l = s.split(";").map { it.split(",").map { v -> v.toFloat() }.toFloatArray() }
        return if (l.size == LABELS.size) l else null
    }

    private fun startCalib() {
        stopPlay()
        val got = ArrayList<FloatArray>()
        val ctx = ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault)
        val tv = TextView(ctx).apply {
            setTextColor(Color.WHITE); textSize = 16f
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(24), 0, 0); setBackgroundColor(0x44000000)
        }
        fun lab() { tv.text = "Chạm vào: ${LABELS[got.size]} (${got.size + 1}/${LABELS.size})" }
        lab()
        tv.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_DOWN) {
                got.add(floatArrayOf(e.rawX, e.rawY))
                if (got.size == LABELS.size) {
                    prefs.edit().putString("pts", got.joinToString(";") { "${it[0]},${it[1]}" }).apply()
                    wm.removeView(tv); calib = null
                    status.text = "Đã căn xong"
                } else lab()
            }
            true
        }
        wm.addView(tv, overlayParams(-1, -1)); calib = tv
    }

    private fun startPlay() {
        val pts = loadPts()
        if (pts == null) { toast("Chưa căn phím"); return }
        val js = prefs.getString("song", null)
        if (js == null) { toast("Chưa có bài, nạp trong app"); return }
        val song = try { parseSong(js) } catch (e: Exception) { toast("JSON lỗi"); return }
        stopPlay()
        val acts = compile(song, tapLen.toLong(), tempoScale.toDouble(), holdMode)
        val t0 = SystemClock.uptimeMillis() + 800
        playing = true
        status.text = "Đang chơi..."
        for (a in acts) h.postAtTime({ if (playing) fire(a, pts) }, token, t0 + a.t)
        val endT = (acts.lastOrNull()?.t ?: 0L) + 600
        h.postAtTime({ stopPlay() }, token, t0 + endT)
    }

    private fun stopPlay() {
        playing = false
        h.removeCallbacksAndMessages(token)
        if (::status.isInitialized) status.text = "♪ kéo để di chuyển"
    }

    private fun fire(a: Act, pts: List<FloatArray>) {
        val b = GestureDescription.Builder()
        for (s in a.strokes) {
            val path = Path().apply { moveTo(pts[s.p][0], pts[s.p][1]) }
            b.addStroke(GestureDescription.StrokeDescription(path, s.off, s.dur))
        }
        dispatchGesture(b.build(), null, null)
    }
}
