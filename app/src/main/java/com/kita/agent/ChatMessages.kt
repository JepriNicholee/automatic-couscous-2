package com.kita.agent

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

// Menambah gelembung pesan, catatan, kartu pasang-APK dan kartu undo ke layar chat.
// Fungsi-fungsi di sini adalah ekstensi MainActivity (dipecah dari MainActivity.kt).

internal fun MainActivity.insertBeforeStatus(v: View, lp: LinearLayout.LayoutParams) {
    val idx = if (statusRoot.parent === chatBox) chatBox.indexOfChild(statusRoot) else -1
    val zoneIdx = if (zone.parent === chatBox) chatBox.indexOfChild(zone) else -1
    val insertAt = listOf(idx, zoneIdx).filter { it >= 0 }.minOrNull()
    if (insertAt != null) chatBox.addView(v, insertAt, lp) else chatBox.addView(v, lp)
}

/**
 * Dulu menambah header "AI Agent · status" di tiap balasan; itu mengulang baris aktivitas dan membuat layar ramai.
 * Sekarang balasan tampil bersih; status hanya ada di satu pil aktivitas. Fungsi dipertahankan agar pemanggil lama aman.
 */
@Suppress("UNUSED_PARAMETER")
internal fun MainActivity.decorateAgentBubble(box: LinearLayout, active: Boolean) {
    box.findViewWithTag<View>("ai_action_header")?.let { box.removeView(it) }
}

internal fun MainActivity.refreshAgentBubbleHeader() {
    streamBox?.let { decorateAgentBubble(it, busy) }
}

private fun MainActivity.updateStreamingText(box: LinearLayout, text: String) {
    val tv = box.findViewWithTag<ShimmerText>("ai_stream_text") ?: ShimmerText(this).apply {
        tag = "ai_stream_text"
        textSize = 16.5f
        setTextColor(Palette.INK)
        setLineSpacing(0f, 1.35f)
        setShimmer(false)
        box.addView(this, LinearLayout.LayoutParams(MATCH, WRAP))
    }
    // Hanya berubah isi TextView; bubble, header, ukuran, dan posisi tetap stabil.
    tv.text = mdToSpannable(text)
}

internal fun MainActivity.addMsg(kind: Int, text: String) = runOnUiThread {
    emptyState.visibility = View.GONE
    if (kind == USER) {
        val tv = TextView(this).apply {
            this.text = text
            tag = MsgMeta(USER, text)
            setOnLongClickListener { showMsgMenu(this); true }
            textSize = 16.5f
            setTextColor(Palette.INK)
            setLineSpacing(0f, 1.25f)
            background = GradientDrawable().apply {
                setColor(Palette.BUBBLE)
                val big = dp(20).toFloat()
                val small = dp(5).toFloat()
                cornerRadii = floatArrayOf(big, big, big, big, small, small, big, big)
            }
            setPadding(dp(16), dp(11), dp(16), dp(11))
        }
        val lp = LinearLayout.LayoutParams(WRAP, WRAP).apply {
            topMargin = dp(20)
            gravity = Gravity.END
            marginStart = dp(56)
        }
        insertBeforeStatus(tv, lp)
    } else {
        val box = newAiBox()
        fillMarkdown(box, text)
        decorateAgentBubble(box, active = false)
    }
    scrollDown()
}

internal fun MainActivity.newAiBox(): LinearLayout {
    // Balasan teks menutup kartu aktivitas yang sedang berjalan; langkah berikutnya membuka kartu baru.
    zone.feed.seal()
    val box = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        tag = MsgMeta(AI, "")
        background = null
        setPadding(dp(2), dp(4), dp(2), dp(2))
    }
    box.setOnLongClickListener { showMsgMenu(box); true }
    insertBeforeStatus(box, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16); marginEnd = dp(16) })
    return box
}

/** Dipanggil saat teks balasan mengalir masuk; menggambar ulang pesan yang sedang ditulis. */
internal fun MainActivity.streamUpdate(text: String) = runOnUiThread {
    emptyState.visibility = View.GONE
    val box = streamBox ?: newAiBox().also { streamBox = it }
    streamText = text
    streamEnded = false
    streamCopyAdded = false
    typewriterTarget = text
    if (!typewriterTarget.startsWith(typewriterShown)) typewriterShown = ""
    typewriterHandler.removeCallbacksAndMessages(null)
    val tick = object : Runnable {
        override fun run() {
            val target = typewriterTarget
            if (typewriterShown.length < target.length) {
                val step = if (target.length - typewriterShown.length > 240) 2 else 1
                typewriterShown = target.take((typewriterShown.length + step).coerceAtMost(target.length))
                decorateAgentBubble(box, active = true)
                updateStreamingText(box, typewriterShown)
                if (typewriterShown.length % 3 == 0 || typewriterShown.length == target.length) scrollDown()
                typewriterHandler.postDelayed(this, 26L)
            } else if (streamEnded && !streamCopyAdded) {
                streamCopyAdded = true
                fillMarkdown(box, typewriterShown, withCopy = false, streaming = false)
                decorateAgentBubble(box, active = false)
                addCopyRow(box, typewriterShown)
            }
        }
    }
    typewriterHandler.post(tick)
}

internal fun MainActivity.streamEnd() = runOnUiThread {
    streamEnded = true
    val b = streamBox
    if (b == null || streamText.isBlank()) {
        streamBox = null
        streamText = ""
        return@runOnUiThread
    }
    typewriterHandler.post(object : Runnable {
        override fun run() {
            if (typewriterShown.length < typewriterTarget.length) {
                typewriterHandler.postDelayed(this, 26L)
            } else if (!streamCopyAdded) {
                streamCopyAdded = true
                fillMarkdown(b, typewriterShown, withCopy = false, streaming = false)
                decorateAgentBubble(b, active = false)
                addCopyRow(b, typewriterShown)
                streamBox = null
                streamText = ""
            }
        }
    })
}

internal fun MainActivity.addNote(t: String) = runOnUiThread {
    val tv = TextView(this).apply {
        text = t
        textSize = 13f
        setTextColor(Palette.INK_SOFT)
        setPadding(dp(4), dp(8), 0, dp(2))
    }
    insertBeforeStatus(tv, LinearLayout.LayoutParams(WRAP, WRAP))
    scrollDown()
}

internal fun MainActivity.addInstallCard(path: String) = runOnUiThread {
    val btn = TextView(this).apply {
        text = "Pasang APK"
        textSize = 15f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        background = pressable(rounded(Palette.BRAND, 24))
        setPadding(dp(24), dp(12), dp(24), dp(12))
    }
    btn.setOnClickListener { installApk(path, btn) }
    insertBeforeStatus(btn, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(14) })
    scrollDown()
}

internal fun MainActivity.addUndoCard(path: String, bak: String) = runOnUiThread {
    val label = "Urungkan: " + path.substringAfterLast('/')
    val btn = TextView(this).apply {
        text = label
        textSize = 14f
        setTextColor(darkOf(Palette.AMBER))
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(tint(Palette.AMBER, 0.86f))
            cornerRadius = dp(20).toFloat()
        }
        setPadding(dp(16), dp(8), dp(16), dp(8))
    }
    btn.setOnClickListener { undoWrite(path, bak, btn, label) }
    insertBeforeStatus(btn, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(12) })
    scrollDown()
}

/** Versi lebih gelap dari [c] untuk teks di atas latar pastel. */
internal fun darkOf(c: Int): Int {
    val r = ((c shr 16) and 0xFF) * 6 / 10
    val g = ((c shr 8) and 0xFF) * 6 / 10
    val b = (c and 0xFF) * 6 / 10
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
