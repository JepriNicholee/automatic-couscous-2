package com.kita.agent

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Kartu peralihan AI otomatis: judul + keterangan, daftar percobaan (ikon status, nama AI, alasan) dan
 * garis kemajuan yang bergerak selama sistem mencari AI yang bisa dipakai.
 * Setelah berhasil, kartu mengecil sendiri menjadi satu baris hijau; ketuk untuk membuka lagi.
 */
internal class FailoverView(ctx: Context) : View(ctx) {

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d

    private val card = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1f) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(2f); strokeCap = Paint.Cap.ROUND
    }
    private val titleP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(15f); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); color = Palette.INK
    }
    private val subP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = dp(12.5f); color = Palette.INK_SOFT }
    private val rowP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = dp(13f); color = Palette.TEXT_MAIN }
    private val noteP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = dp(11.5f); color = Palette.INK_SOFT; textAlign = Paint.Align.RIGHT }

    private var snap: FoSnapshot? = null
    private var expanded = true
    private var phase = 0f
    private var anim: ValueAnimator? = null
    private val collapseTask = Runnable { expanded = false; requestLayout(); invalidate() }
    private val rect = RectF()
    private val path = Path()

    private val maxRows = 5

    init {
        setOnClickListener { if (snap?.tone != 0) { removeCallbacks(collapseTask); expanded = !expanded; requestLayout(); invalidate() } }
    }

    fun update(s: FoSnapshot) {
        val before = snap
        snap = s
        removeCallbacks(collapseTask)
        if (s.tone == 0) expanded = true
        if (s.tone == 1 && before?.tone != 1) postDelayed(collapseTask, 5000)
        requestLayout()
        invalidate()
        updateAnim()
    }

    private fun accent(s: FoSnapshot): Int = when {
        s.tone == 1 -> Palette.GREEN
        s.tone == 2 -> Palette.RED
        s.waiting -> Palette.AMBER
        else -> Palette.BRAND
    }

    private fun updateAnim() {
        val need = isAttachedToWindow && snap?.tone == 0
        if (need && anim == null) {
            anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1200
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { phase = it.animatedValue as Float; invalidate() }
                start()
            }
        } else if (!need) {
            anim?.cancel()
            anim = null
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateAnim() }

    override fun onDetachedFromWindow() {
        removeCallbacks(collapseTask)
        anim?.cancel()
        anim = null
        super.onDetachedFromWindow()
    }

    private fun shownRows(s: FoSnapshot): List<FoRowSnap> = if (!expanded) emptyList() else s.rows.takeLast(maxRows)

    private fun contentHeight(s: FoSnapshot): Float {
        val head = dp(14f) + dp(20f) + dp(18f) + dp(10f)
        val n = shownRows(s).size
        val more = if (expanded && s.rows.size > maxRows) dp(20f) else 0f
        return head + n * dp(28f) + more + (if (n > 0) dp(8f) else 0f) + dp(8f)
    }

    override fun onMeasure(w: Int, h: Int) {
        val s = snap
        val width = MeasureSpec.getSize(w)
        val height = if (s == null) 0 else (contentHeight(s) + dp(12f)).toInt() // 12dp = jarak atas-bawah antar pesan
        setMeasuredDimension(width, height)
    }

    override fun onDraw(c: Canvas) {
        val s = snap ?: return
        val ac = accent(s)
        val left = dp(2f)
        val right = width - dp(2f)
        val top = dp(6f)
        val bottom = top + contentHeight(s)
        rect.set(left, top, right, bottom)

        // Latar kartu: putih dengan tepi halus dan garis aksen di kiri.
        card.color = Palette.SURFACE
        c.drawRoundRect(rect, dp(16f), dp(16f), card)
        line.color = Palette.BORDER
        c.drawRoundRect(rect, dp(16f), dp(16f), line)
        fill.color = ac
        c.save()
        path.reset()
        path.addRoundRect(rect, dp(16f), dp(16f), Path.Direction.CW)
        c.clipPath(path)
        c.drawRect(left, top, left + dp(4f), bottom, fill)
        c.restore()

        val pad = left + dp(18f)
        var y = top + dp(14f)

        // Ikon kepala: lingkaran aksen. Berputar saat bekerja, centang saat berhasil, tanda seru saat gagal.
        val hr = dp(10f)
        val hcx = pad + hr
        val hcy = y + dp(10f)
        drawStateIcon(c, hcx, hcy, hr, if (s.tone == 1) FoState.OK else if (s.tone == 2) FoState.REJECTED else FoState.TRYING, ac)

        val tx = hcx + hr + dp(10f)
        val tw = right - tx - dp(14f)
        c.drawText(TextUtils.ellipsize(s.title, titleP, tw, TextUtils.TruncateAt.END).toString(), tx, y + dp(15f), titleP)
        c.drawText(TextUtils.ellipsize(s.subtitle, subP, tw, TextUtils.TruncateAt.END).toString(), tx, y + dp(34f), subP)
        y += dp(20f) + dp(18f) + dp(10f)

        val rows = shownRows(s)
        if (rows.isNotEmpty()) {
            line.color = Palette.DIVIDER
            c.drawLine(pad, y - dp(4f), right - dp(14f), y - dp(4f), line)
            y += dp(4f)
            if (s.rows.size > maxRows) {
                subP.textAlign = Paint.Align.LEFT
                c.drawText("+${s.rows.size - maxRows} percobaan sebelumnya", pad, y + dp(12f), subP)
                y += dp(20f)
            }
            for (r in rows) {
                val cy = y + dp(14f)
                val rc = stateColor(r.state)
                drawStateIcon(c, pad + dp(8f), cy, dp(7f), r.state, rc)
                val nx = pad + dp(26f)
                noteP.color = if (r.state == FoState.OK) Palette.GREEN else Palette.INK_SOFT
                val noteW = noteP.measureText(r.note)
                val nameW = right - nx - noteW - dp(28f)
                rowP.color = if (r.state == FoState.TRYING || r.state == FoState.OK) Palette.INK else Palette.TEXT_SOFT
                c.drawText(TextUtils.ellipsize(r.label, rowP, nameW, TextUtils.TruncateAt.END).toString(), nx, cy + dp(4.5f), rowP)
                c.drawText(r.note, right - dp(14f), cy + dp(4f), noteP)
                y += dp(28f)
            }
        }

        // Garis kemajuan tak-tentu di dasar kartu selama masih mencari.
        if (s.tone == 0) {
            val bw = right - left - dp(36f)
            val bx = left + dp(18f)
            val by = bottom - dp(8f)
            fill.color = Palette.DIVIDER
            c.drawRoundRect(bx, by, bx + bw, by + dp(3f), dp(2f), dp(2f), fill)
            val seg = bw * 0.28f
            val sx = bx + (bw + seg) * phase - seg
            fill.color = ac
            c.save()
            c.clipRect(bx, by, bx + bw, by + dp(3f))
            c.drawRoundRect(sx, by, sx + seg, by + dp(3f), dp(2f), dp(2f), fill)
            c.restore()
        }
    }

    private fun stateColor(st: FoState): Int = when (st) {
        FoState.OK -> Palette.GREEN
        FoState.TRYING -> Palette.BRAND
        FoState.BUSY -> Palette.AMBER
        FoState.QUOTA -> Palette.ORANGE
        FoState.OFFLINE -> Palette.SLATE
        FoState.REJECTED -> Palette.RED
        FoState.WAIT -> Palette.INK_SOFT
    }

    private fun drawStateIcon(c: Canvas, cx: Float, cy: Float, r: Float, st: FoState, color: Int) {
        when (st) {
            FoState.TRYING -> {
                stroke.color = (color and 0x00FFFFFF) or 0x33000000
                c.drawCircle(cx, cy, r - dp(1f), stroke)
                stroke.color = color
                rect.set(cx - r + dp(1f), cy - r + dp(1f), cx + r - dp(1f), cy + r - dp(1f))
                c.drawArc(rect, phase * 360f, 100f, false, stroke)
            }
            FoState.OK -> {
                fill.color = color
                c.drawCircle(cx, cy, r, fill)
                stroke.color = 0xFFFFFFFF.toInt()
                path.reset()
                path.moveTo(cx - r * 0.42f, cy + r * 0.02f)
                path.lineTo(cx - r * 0.1f, cy + r * 0.34f)
                path.lineTo(cx + r * 0.46f, cy - r * 0.32f)
                c.drawPath(path, stroke)
            }
            FoState.WAIT -> {
                stroke.color = color
                c.drawCircle(cx, cy, r - dp(1f), stroke)
            }
            else -> {
                fill.color = (color and 0x00FFFFFF) or 0x26000000
                c.drawCircle(cx, cy, r, fill)
                stroke.color = color
                val k = r * 0.36f
                c.drawLine(cx - k, cy - k, cx + k, cy + k, stroke)
                c.drawLine(cx - k, cy + k, cx + k, cy - k, stroke)
            }
        }
    }
}
