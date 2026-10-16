package com.kita.agent

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.text.style.LineBackgroundSpan
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.sin

/** Latar penuh selebar baris untuk tampilan diff. */
internal class DiffBg(private val color: Int) : LineBackgroundSpan {
    override fun drawBackground(
        c: Canvas, p: Paint, left: Int, right: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, lnum: Int
    ) {
        val old = p.color
        p.color = color
        c.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), p)
        p.color = old
    }
}

/** Ikon sederhana yang digambar lewat kode (tanpa file gambar). */
class IconView(ctx: Context, t: Int, c: Int = Palette.TEXT_MAIN) : View(ctx) {
    var type: Int = t
        set(v) { field = v; invalidate() }
    var color: Int = c
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    companion object {
        const val MENU = 0
        const val PLUS = 1
        const val MIC = 2
        const val SEND = 3
        const val WAVE = 4
        const val NEW = 5
        const val CHEVRON = 6
        const val TERMINAL = 7
        const val CODE = 8
        const val CLOCK = 9
        const val TRASH = 10
        const val STOP = 11
        const val GRID = 12 // empat lingkaran: mencari / mengumpulkan informasi
        const val BLOB_TERM = 13 // awan berisi >_ : perintah terminal umum
        const val FILE = 14 // lembar dokumen: membaca file
        const val FOLDER = 15 // folder: melihat/mengatur isi folder
        const val GEAR = 16 // roda gigi: build (berputar saat berjalan)
        const val ARCHIVE = 17 // kotak arsip: zip/tar
        const val DOWNLOAD = 18 // panah ke bawah: pasang paket / unduh
        const val GLOBE = 19 // bola dunia: jaringan (curl, wget)
        const val CHECK = 20 // centang: uji / verifikasi
        const val CROSS = 21 // silang: gagal
        const val NEW_CHAT = 22 // lingkaran putus-putus + kapsul miring: chat baru
        const val CHEVRON_DOWN = 23 // panah kecil ke bawah
        const val BRAIN = 24 // analisis / berpikir
        const val BOOK = 25 // membaca dan memahami file
        const val WRITE = 26 // menulis atau mengubah file
    }

    /** true = ikon "sedang bekerja": berdenyut (roda gigi: berputar). Berhenti sendiri saat dilepas dari layar. */
    var working: Boolean = false
        set(v) { field = v; if (v) startWorking() else stopWorking() }
    private var workAnim: ValueAnimator? = null

    private fun startWorking() {
        workAnim?.cancel()
        val spin = type == GEAR
        workAnim = (if (spin) ValueAnimator.ofFloat(0f, 360f) else ValueAnimator.ofFloat(0.35f, 1f)).apply {
            duration = if (spin) 1800L else 800L
            repeatCount = ValueAnimator.INFINITE
            if (spin) interpolator = LinearInterpolator() else repeatMode = ValueAnimator.REVERSE
            addUpdateListener { if (spin) rotation = it.animatedValue as Float else alpha = it.animatedValue as Float }
            start()
        }
    }

    private fun stopWorking() {
        workAnim?.cancel()
        workAnim = null
        alpha = 1f
        rotation = 0f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (working && workAnim == null) startWorking()
    }

    override fun onDetachedFromWindow() {
        workAnim?.cancel()
        workAnim = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val s = minOf(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val iconStroke = 1.8f * resources.displayMetrics.density // satu ketebalan untuk semua ikon
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = iconStroke
        p.color = color
        when (type) {
            MENU -> {
                c.drawLine(cx - s * 0.22f, cy - s * 0.075f, cx + s * 0.22f, cy - s * 0.075f, p)
                c.drawLine(cx - s * 0.22f, cy + s * 0.075f, cx + s * 0.22f, cy + s * 0.075f, p)
            }
            PLUS -> {
                p.strokeWidth = iconStroke
                c.drawLine(cx - s * 0.28f, cy, cx + s * 0.28f, cy, p)
                c.drawLine(cx, cy - s * 0.28f, cx, cy + s * 0.28f, p)
            }
            MIC -> {
                p.strokeWidth = iconStroke
                c.drawRoundRect(RectF(cx - s * 0.1f, cy - s * 0.3f, cx + s * 0.1f, cy + s * 0.05f), s * 0.1f, s * 0.1f, p)
                c.drawArc(RectF(cx - s * 0.2f, cy - s * 0.16f, cx + s * 0.2f, cy + s * 0.2f), 0f, 180f, false, p)
                c.drawLine(cx, cy + s * 0.2f, cx, cy + s * 0.32f, p)
            }
            SEND -> {
                c.drawLine(cx, cy + s * 0.24f, cx, cy - s * 0.24f, p)
                c.drawLine(cx - s * 0.2f, cy - s * 0.04f, cx, cy - s * 0.24f, p)
                c.drawLine(cx + s * 0.2f, cy - s * 0.04f, cx, cy - s * 0.24f, p)
            }
            WAVE -> {
                p.strokeWidth = iconStroke
                c.drawLine(cx - s * 0.18f, cy - s * 0.1f, cx - s * 0.18f, cy + s * 0.1f, p)
                c.drawLine(cx, cy - s * 0.24f, cx, cy + s * 0.24f, p)
                c.drawLine(cx + s * 0.18f, cy - s * 0.14f, cx + s * 0.18f, cy + s * 0.14f, p)
            }
            NEW -> {
                c.drawRoundRect(RectF(cx - s * 0.26f, cy - s * 0.2f, cx + s * 0.2f, cy + s * 0.26f), s * 0.08f, s * 0.08f, p)
                p.strokeWidth = iconStroke
                c.drawLine(cx - s * 0.04f, cy + s * 0.04f, cx + s * 0.26f, cy - s * 0.26f, p)
            }
            CHEVRON -> {
                c.drawLine(cx - s * 0.1f, cy - s * 0.2f, cx + s * 0.1f, cy, p)
                c.drawLine(cx + s * 0.1f, cy, cx - s * 0.1f, cy + s * 0.2f, p)
            }
            TERMINAL -> {
                p.strokeWidth = iconStroke
                c.drawRoundRect(RectF(cx - s * 0.32f, cy - s * 0.24f, cx + s * 0.32f, cy + s * 0.24f), s * 0.06f, s * 0.06f, p)
                c.drawLine(cx - s * 0.18f, cy - s * 0.09f, cx - s * 0.08f, cy, p)
                c.drawLine(cx - s * 0.08f, cy, cx - s * 0.18f, cy + s * 0.09f, p)
                c.drawLine(cx, cy + s * 0.1f, cx + s * 0.16f, cy + s * 0.1f, p)
            }
            CODE -> {
                c.drawLine(cx - s * 0.12f, cy - s * 0.16f, cx - s * 0.28f, cy, p)
                c.drawLine(cx - s * 0.28f, cy, cx - s * 0.12f, cy + s * 0.16f, p)
                c.drawLine(cx + s * 0.12f, cy - s * 0.16f, cx + s * 0.28f, cy, p)
                c.drawLine(cx + s * 0.28f, cy, cx + s * 0.12f, cy + s * 0.16f, p)
                c.drawLine(cx + s * 0.04f, cy - s * 0.2f, cx - s * 0.04f, cy + s * 0.2f, p)
            }
            CLOCK -> {
                p.strokeWidth = iconStroke
                c.drawCircle(cx, cy, s * 0.3f, p)
                c.drawLine(cx, cy, cx, cy - s * 0.16f, p)
                c.drawLine(cx, cy, cx + s * 0.12f, cy + s * 0.06f, p)
            }
            TRASH -> {
                p.strokeWidth = iconStroke
                // tutup dan gagang
                c.drawLine(cx - s * 0.27f, cy - s * 0.17f, cx + s * 0.27f, cy - s * 0.17f, p)
                c.drawLine(cx - s * 0.08f, cy - s * 0.17f, cx - s * 0.08f, cy - s * 0.27f, p)
                c.drawLine(cx + s * 0.08f, cy - s * 0.17f, cx + s * 0.08f, cy - s * 0.27f, p)
                c.drawLine(cx - s * 0.08f, cy - s * 0.27f, cx + s * 0.08f, cy - s * 0.27f, p)
                // badan tempat sampah
                c.drawLine(cx - s * 0.2f, cy - s * 0.17f, cx - s * 0.16f, cy + s * 0.28f, p)
                c.drawLine(cx - s * 0.16f, cy + s * 0.28f, cx + s * 0.16f, cy + s * 0.28f, p)
                c.drawLine(cx + s * 0.16f, cy + s * 0.28f, cx + s * 0.2f, cy - s * 0.17f, p)
                // garis di dalam
                c.drawLine(cx - s * 0.06f, cy - s * 0.06f, cx - s * 0.06f, cy + s * 0.18f, p)
                c.drawLine(cx + s * 0.06f, cy - s * 0.06f, cx + s * 0.06f, cy + s * 0.18f, p)
            }
            GRID -> {
                p.strokeWidth = iconStroke
                val d = s * 0.17f
                val r = s * 0.1f
                c.drawCircle(cx - d, cy - d, r, p)
                c.drawCircle(cx + d, cy - d, r, p)
                c.drawCircle(cx - d, cy + d, r, p)
                c.drawCircle(cx + d, cy + d, r, p)
            }
            BLOB_TERM -> {
                // badan awan bergelombang (6 tonjolan) + tanda >_
                val path = Path()
                val n = 72
                for (i in 0..n) {
                    val a = 2.0 * Math.PI * i / n
                    val r = s * 0.36f * (1f + 0.07f * cos(6.0 * a).toFloat())
                    val x = cx + r * cos(a).toFloat()
                    val y = cy + r * sin(a).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                p.strokeWidth = iconStroke
                c.drawPath(path, p)
                c.drawLine(cx - s * 0.17f, cy - s * 0.1f, cx - s * 0.06f, cy, p)
                c.drawLine(cx - s * 0.06f, cy, cx - s * 0.17f, cy + s * 0.1f, p)
                c.drawLine(cx + s * 0.02f, cy + s * 0.1f, cx + s * 0.16f, cy + s * 0.1f, p)
            }
            FILE -> {
                p.strokeWidth = iconStroke
                val path = Path()
                path.moveTo(cx - s * 0.2f, cy - s * 0.3f)
                path.lineTo(cx + s * 0.08f, cy - s * 0.3f)
                path.lineTo(cx + s * 0.22f, cy - s * 0.16f)
                path.lineTo(cx + s * 0.22f, cy + s * 0.3f)
                path.lineTo(cx - s * 0.2f, cy + s * 0.3f)
                path.close()
                c.drawPath(path, p)
                c.drawLine(cx + s * 0.08f, cy - s * 0.3f, cx + s * 0.08f, cy - s * 0.16f, p)
                c.drawLine(cx + s * 0.08f, cy - s * 0.16f, cx + s * 0.22f, cy - s * 0.16f, p)
                c.drawLine(cx - s * 0.1f, cy + s * 0.02f, cx + s * 0.12f, cy + s * 0.02f, p)
                c.drawLine(cx - s * 0.1f, cy + s * 0.16f, cx + s * 0.12f, cy + s * 0.16f, p)
            }
            FOLDER -> {
                p.strokeWidth = iconStroke
                val path = Path()
                path.moveTo(cx - s * 0.3f, cy - s * 0.2f)
                path.lineTo(cx - s * 0.1f, cy - s * 0.2f)
                path.lineTo(cx, cy - s * 0.1f)
                path.lineTo(cx + s * 0.3f, cy - s * 0.1f)
                path.lineTo(cx + s * 0.3f, cy + s * 0.22f)
                path.lineTo(cx - s * 0.3f, cy + s * 0.22f)
                path.close()
                c.drawPath(path, p)
            }
            GEAR -> {
                p.strokeWidth = iconStroke
                c.drawCircle(cx, cy, s * 0.19f, p)
                c.drawCircle(cx, cy, s * 0.07f, p)
                for (i in 0..7) {
                    val a = i * Math.PI / 4
                    val ca = cos(a).toFloat()
                    val sa = sin(a).toFloat()
                    c.drawLine(cx + s * 0.22f * ca, cy + s * 0.22f * sa, cx + s * 0.31f * ca, cy + s * 0.31f * sa, p)
                }
            }
            ARCHIVE -> {
                p.strokeWidth = iconStroke
                c.drawRoundRect(RectF(cx - s * 0.3f, cy - s * 0.28f, cx + s * 0.3f, cy - s * 0.1f), s * 0.04f, s * 0.04f, p)
                c.drawRoundRect(RectF(cx - s * 0.24f, cy - s * 0.1f, cx + s * 0.24f, cy + s * 0.28f), s * 0.04f, s * 0.04f, p)
                c.drawLine(cx - s * 0.08f, cy + s * 0.04f, cx + s * 0.08f, cy + s * 0.04f, p)
            }
            DOWNLOAD -> {
                c.drawLine(cx, cy - s * 0.26f, cx, cy + s * 0.1f, p)
                c.drawLine(cx - s * 0.14f, cy - s * 0.04f, cx, cy + s * 0.1f, p)
                c.drawLine(cx + s * 0.14f, cy - s * 0.04f, cx, cy + s * 0.1f, p)
                c.drawLine(cx - s * 0.26f, cy + s * 0.12f, cx - s * 0.26f, cy + s * 0.27f, p)
                c.drawLine(cx - s * 0.26f, cy + s * 0.27f, cx + s * 0.26f, cy + s * 0.27f, p)
                c.drawLine(cx + s * 0.26f, cy + s * 0.27f, cx + s * 0.26f, cy + s * 0.12f, p)
            }
            GLOBE -> {
                p.strokeWidth = iconStroke
                c.drawCircle(cx, cy, s * 0.3f, p)
                c.drawOval(RectF(cx - s * 0.12f, cy - s * 0.3f, cx + s * 0.12f, cy + s * 0.3f), p)
                c.drawLine(cx - s * 0.3f, cy, cx + s * 0.3f, cy, p)
            }
            CHECK -> {
                p.strokeWidth = iconStroke
                c.drawLine(cx - s * 0.22f, cy + s * 0.02f, cx - s * 0.07f, cy + s * 0.17f, p)
                c.drawLine(cx - s * 0.07f, cy + s * 0.17f, cx + s * 0.24f, cy - s * 0.16f, p)
            }
            CROSS -> {
                p.strokeWidth = iconStroke
                c.drawLine(cx - s * 0.19f, cy - s * 0.19f, cx + s * 0.19f, cy + s * 0.19f, p)
                c.drawLine(cx + s * 0.19f, cy - s * 0.19f, cx - s * 0.19f, cy + s * 0.19f, p)
            }
            NEW_CHAT -> {
                val r = s * 0.36f
                val circ = (2.0 * Math.PI * r).toFloat()
                p.strokeWidth = iconStroke * 1.1f
                p.pathEffect = DashPathEffect(floatArrayOf(0.01f, circ / 14f - 0.01f), 0f)
                c.drawCircle(cx, cy, r, p)
                p.pathEffect = null
                p.strokeWidth = iconStroke
                c.save()
                c.rotate(-45f, cx, cy)
                c.drawRoundRect(RectF(cx - s * 0.2f, cy - s * 0.085f, cx + s * 0.2f, cy + s * 0.085f), s * 0.085f, s * 0.085f, p)
                c.restore()
            }
            CHEVRON_DOWN -> {
                c.drawLine(cx - s * 0.2f, cy - s * 0.1f, cx, cy + s * 0.1f, p)
                c.drawLine(cx, cy + s * 0.1f, cx + s * 0.2f, cy - s * 0.1f, p)
            }
            BRAIN -> {
                c.drawCircle(cx - s * 0.13f, cy, s * 0.2f, p)
                c.drawCircle(cx + s * 0.13f, cy, s * 0.2f, p)
                c.drawLine(cx, cy - s * 0.2f, cx, cy + s * 0.2f, p)
                c.drawLine(cx - s * 0.27f, cy - s * 0.03f, cx - s * 0.12f, cy - s * 0.03f, p)
                c.drawLine(cx + s * 0.12f, cy + s * 0.08f, cx + s * 0.27f, cy + s * 0.08f, p)
            }
            BOOK -> {
                c.drawLine(cx, cy - s * 0.25f, cx, cy + s * 0.27f, p)
                c.drawRoundRect(RectF(cx - s * 0.3f, cy - s * 0.22f, cx - s * 0.03f, cy + s * 0.22f), s * 0.04f, s * 0.04f, p)
                c.drawRoundRect(RectF(cx + s * 0.03f, cy - s * 0.22f, cx + s * 0.3f, cy + s * 0.22f), s * 0.04f, s * 0.04f, p)
            }
            WRITE -> {
                c.drawLine(cx - s * 0.22f, cy + s * 0.23f, cx + s * 0.22f, cy - s * 0.22f, p)
                c.drawLine(cx - s * 0.27f, cy + s * 0.28f, cx - s * 0.12f, cy + s * 0.23f, p)
                c.drawLine(cx + s * 0.15f, cy - s * 0.29f, cx + s * 0.27f, cy - s * 0.17f, p)
                c.drawLine(cx - s * 0.26f, cy + s * 0.29f, cx + s * 0.27f, cy + s * 0.29f, p)
            }
            STOP -> {
                p.style = Paint.Style.FILL
                c.drawRoundRect(RectF(cx - s * 0.17f, cy - s * 0.17f, cx + s * 0.17f, cy + s * 0.17f), s * 0.04f, s * 0.04f, p)
            }
        }
    }
}

/** Tiga titik berwarna yang memantul bergantian: penanda "sedang berpikir". Berhenti sendiri saat lepas dari layar. */
class DotsView(ctx: Context, dotColor: Int = 0xFF000000.toInt()) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val colors = intArrayOf(dotColor, dotColor, dotColor)
    private var phase = 0f
    private var anim: ValueAnimator? = null

    private fun start() {
        if (anim != null) return
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1100
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); start() }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        anim = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val cy = height / 2f
        val r = height * 0.11f
        for (i in 0..2) {
            // tiap titik naik-turun dengan jeda sepertiga siklus
            val t = ((phase - i / 3f) % 1f + 1f) % 1f
            val lift = sin(Math.PI * minOf(t * 2f, 1f)).toFloat() * (if (t < 0.5f) 1f else 0f)
            p.color = colors[i]
            c.drawCircle(width * (0.2f + 0.3f * i), cy - lift * height * 0.16f, r * (1f + 0.25f * lift), p)
        }
    }
}

/** Teks dengan efek kilau yang bergerak (shimmer) saat aktif. */
class ShimmerText(ctx: Context) : TextView(ctx) {
    private var anim: ValueAnimator? = null
    private var shader: LinearGradient? = null
    private val m = Matrix()
    private var t = 0f
    private var active = false

    /** Warna dasar dan sorot kilau. Bawaan: warna merek. */
    var shimmerBase: Int = tint(Palette.BRAND, 0.5f)
        set(v) { field = v; shader = null; invalidate() }
    var shimmerHi: Int = Palette.BRAND
        set(v) { field = v; shader = null; invalidate() }

    fun setShimmer(v: Boolean) {
        active = v
        shader = null
        if (v && isAttachedToWindow) {
            startAnim()
        } else {
            stopAnim()
            paint.shader = null
            invalidate()
        }
    }

    private fun startAnim() {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { t = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun stopAnim() {
        anim?.cancel()
        anim = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (active) startAnim()
    }

    override fun onDetachedFromWindow() {
        stopAnim()
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        if (active && width > 0) {
            val w = width.toFloat()
            val sh = shader ?: LinearGradient(
                0f, 0f, w * 0.6f, 0f,
                intArrayOf(shimmerBase, shimmerHi, shimmerBase), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            ).also { shader = it }
            m.setTranslate(-w * 0.6f + t * w * 1.8f, 0f)
            sh.setLocalMatrix(m)
            paint.shader = sh
        }
        super.onDraw(c)
    }
}

/** Logo aplikasi (asterisk hitam; gambar dari res/drawable-nodpi/logo_mark.png). */
class LogoView(ctx: Context) : android.widget.ImageView(ctx) {
    init {
        setImageResource(R.drawable.logo_mark)
        scaleType = ScaleType.FIT_CENTER
    }
}
