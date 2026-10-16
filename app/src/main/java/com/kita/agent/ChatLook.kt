package com.kita.agent

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View

// Latar layar chat gaya Gemini: putih bersih di atas, biru muda lembut menyapu ke bawah.
// Warna dan titik henti ada di Palette (SKY_COLORS, SKY_STOPS).
internal class ChatBackground(ctx: Context) : View(ctx) {
    private val paint = Paint()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        paint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(), Palette.SKY_COLORS, Palette.SKY_STOPS, Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(c: Canvas) {
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}
