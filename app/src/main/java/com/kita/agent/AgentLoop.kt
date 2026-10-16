package com.kita.agent

import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import org.json.JSONObject

// Loop agen: kirim pesan, jalankan worker, panggil model, jalankan alat, berhenti.
// Fungsi-fungsi di sini adalah ekstensi MainActivity (dipecah dari MainActivity.kt).

/** Tutup keyboard dan lepas fokus dari kolom input setelah pesan dikirim, agar tata letak kembali normal. */
/** Mode otonom: agen menjalankan tindakan tanpa dialog persetujuan dan tanpa menunggu jawaban pengguna. Harus opt-in. */
internal fun MainActivity.isAutonomous(): Boolean = prefs.getBoolean("autonomous", false)

internal fun MainActivity.hideKeyboard() {
    val close = {
        try {
            input.clearFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(input.windowToken, 0)
        } catch (e: Exception) {
        }
    }
    close()
    // Setelah dialog ditutup, Android sering mengembalikan fokus ke kolom input; tutup lagi sebentar kemudian.
    input.postDelayed({ close() }, 300)
}

internal fun MainActivity.stopAgent() {
    if (!busy) return
    cancelled = true
    try { activeConn?.disconnect() } catch (e: Exception) {}
    worker?.interrupt()
    confirmDialog?.dismiss()
    setStatus(IconView.STOP, "Menghentikan...")
}

internal fun MainActivity.onCancelled() {
    runLog.outcome = RunOutcome.CANCELLED
    trimDangling()
    val p = lastPartial
    if (p.isNotBlank()) synchronized(hist) { hist.add(Hist(1, p)) }
    lastPartial = ""
    addNote("Dihentikan.")
}

internal fun MainActivity.send() {
    val typed = input.text.toString().trim()
    if (busy) {
        if (typed.isNotEmpty()) {
            pendingInterrupt = typed
            input.setText("")
            hideKeyboard()
            addNote("Pesan baru diterima. Menghentikan jawaban saat ini lalu melanjutkan dengan pesan tersebut...")
            stopAgent()
        }
        return
    }
    if (typed.isEmpty() && attachments.isEmpty()) return
    if (attachments.any { it.path == null }) { toast("Tunggu file selesai disalin"); return }
    val imageRefs = attachments.filter { isImageName(it.name) }.mapNotNull { a ->
        a.path?.let { HistImage(it) }
    }
    val msg = typed.ifEmpty { "Periksa lampiran dan gunakan sebagai konteks tugas." } +
        attachments.joinToString("") {
            "\n${if (isImageName(it.name)) "Gambar" else "File"} terlampir di \"${it.path}\""
        }
    val checkpoint = loadAgentCheckpoint()
    val resume = typed.equals("lanjutkan", ignoreCase = true) && checkpoint != null
    val key = cfgKey()
    if (needsKey() && key.isEmpty()) { showSettings(); return }
    if (preset(prov).fmt != "cli" && cfgModel().isBlank()) {
        toast("Isi nama model dulu")
        pickModel()
        return
    }
    busy = true
    input.setText("")
    hideKeyboard()
    clearAttachments()
    if (resume) {
        addNote("Melanjutkan tugas terakhir dari checkpoint (putaran ${checkpoint!!.turn}).")
        startWorker(checkpoint.userMessage, key, addUser = false, images = emptyList())
    } else {
        addMsg(USER, msg)
        startWorker(msg, key, addUser = true, images = imageRefs)
    }
}

/** Jalankan satu putaran agen di thread latar. `busy` sudah true saat dipanggil. */
internal fun MainActivity.startWorker(
    msg: String,
    key: String,
    addUser: Boolean,
    images: List<HistImage> = emptyList()
) {
    cancelled = false
    agentState = AgentState.Planning
    if (addUser || agentTaskId.isBlank()) agentTaskId = "task-${System.currentTimeMillis()}"
    saveAgentCheckpoint(AgentCheckpoint(agentTaskId, msg, 0, "", "planning"))
    allowAll = false
    try { startForegroundService(Intent(this, AgentService::class.java)) } catch (e: Exception) { }
    sawBuild = false
    sawFail = false
    clearChips()
    lastPartial = ""
    resetFailover()
    runLog.reset(System.currentTimeMillis())
    agentSteps.clear()
    runOnUiThread {
        zone.beginRun()
        zone.renderSteps(agentSteps.groups.value)
    }
    updateAction()
    setStatus(KIND_DOTS, "Sedang berpikir")
    val t = Thread {
        try {
            agentLoop(msg, key, addUser, images)
        } catch (e: Throwable) {
            try {
                if (cancelled || e is Cancelled || e is InterruptedException) {
                    Thread.interrupted()
                    agentState = AgentState.Cancelled
                    onCancelled()
                } else {
                    // Giliran model yang memanggil alat tapi belum dapat hasil harus dibuang, kalau tidak
                    // permintaan berikutnya ditolak API ("function call tanpa response").
                    trimDangling()
                    runLog.note = e.message ?: e.javaClass.simpleName
                    runLog.outcome = RunOutcome.ERROR
                    agentState = AgentState.Failed(runLog.note)
                    saveAgentCheckpoint(AgentCheckpoint(agentTaskId, msg, 0, "", "failed", runLog.note))
                    addMsg(AI, "Error: ${e.message ?: e.javaClass.simpleName}")
                }
            } catch (e2: Throwable) {
            }
        } finally {
            Thread.interrupted()
            try {
                streamEnd()
                hideStatus()
                saveHist()
            } catch (e: Throwable) {
            }
            // Lepaskan payload base64 dari memori setelah tugas selesai; path tetap tersimpan untuk riwayat.
            synchronized(hist) { hist.forEach { h -> h.images.forEach { it.cachedPayload = null } } }
                runOnUiThread {
                sealFeed()
                // Laporan akhir hanya setelah semua langkah selesai dan riwayat tersimpan.
                try { showRunReport() } catch (e: Throwable) { }
                    busy = false
                    if (runLog.outcome == RunOutcome.COMPLETED) {
                        agentState = AgentState.Completed(buildReport(runLog, System.currentTimeMillis() - runLog.startedAt))
                        clearAgentCheckpoint()
                    }
                    if (runLog.outcome == RunOutcome.CANCELLED) agentState = AgentState.Cancelled
                    worker = null
                    val next = pendingInterrupt
                    pendingInterrupt = null
                    if (next != null && !isFinishing) {
                        busy = true
                        addMsg(USER, next)
                        startWorker(next, cfgKey(), addUser = true)
                    }
                updateAction()
                // Jeda singkat agar tidak berebut dengan startForeground() bila pekerjaan selesai seketika.
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (!busy) try { stopService(Intent(this, AgentService::class.java)) } catch (e: Exception) { }
                }, 1500)
            }
        }
    }
    worker = t
    t.start()
}

internal fun MainActivity.statusFor(name: String, args: JSONObject): String {
    if (name == "write_file") {
        val path = args.optString("path")
        return if (Regex("\\.(kt|java|xml|gradle|kts|py|js|ts|sh)$", RegexOption.IGNORE_CASE).containsMatchIn(path)) "Memperbaiki kode" else "Memperbarui file"
    }
    if (name == "ask_user") return "Menunggu jawaban pengguna"
    val c = args.optString("command")
    return when {
        c.contains("gradlew") || c.contains("gradle ") -> "Menjalankan pemeriksaan"
        Regex("""\b(grep|find|rg|locate|which|whereis|pwd|tree)\b""").containsMatchIn(c) -> "Memeriksa struktur proyek"
        Regex("""\b(cat|head|tail|sed|wc|diff)\b""").containsMatchIn(c) -> "Membaca dan menganalisis kode"
        Regex("""\b(test|check|lint|verify)\b""").containsMatchIn(c) -> "Memvalidasi hasil"
        else -> "Menjalankan pemeriksaan"
    }
}


/**
 * Judul langkah untuk baris status live: kalimat 'deskripsi' yang ditulis AI sendiri (mis. "Memeriksa isi arsip ZIP")
 * agar pengguna melihat apa yang sebenarnya dikerjakan; bila kosong/terlalu pendek, pakai judul generik statusFor().
 */
internal fun MainActivity.stepTitleFor(name: String, args: JSONObject): String {
    val d = tidyLine(args.optString("deskripsi"), 90)
    return if (d.length >= 6) d else statusFor(name, args)
}

internal fun MainActivity.detailFor(name: String, args: JSONObject): String =
    if (name == "write_file") {
        args.optString("path") + "\n\n" + args.optString("content").take(1500)
    } else {
        "$ " + args.optString("command")
    }

/** Batas putaran model dalam satu permintaan: pengaman agar agen yang berputar-putar tidak menghabiskan kuota tanpa henti. */
// Batas pengaman saja: satu chat berjalan sampai tugas selesai. Hanya berhenti bila model sudah
// selesai memanggil alat, pengguna menekan Hentikan, atau batas ini tercapai.
internal const val MAX_AGENT_TURNS = 300

/** Berapa kali model boleh memanggil alat yang persis sama berturut-turut sebelum dianggap macet. */
internal const val MAX_SAME_CALLS = 3

internal fun MainActivity.agentLoop(
    userMsg: String,
    key: String,
    addUser: Boolean = true,
    images: List<HistImage> = emptyList()
) {
    if (preset(prov).fmt == "cli") { cliChat(userMsg); return }
    if (addUser) {
        synchronized(hist) { hist.add(Hist(0, userMsg, images = images)) }
        saveHist()
    }
    if (addUser && prefs.getBoolean("understand", false)) {
        val brief = try { understandTask(key, userMsg) } catch (e: Cancelled) { throw e } catch (e: Exception) { Brief.Kosong }
        when (brief) {
            is Brief.Paham -> taskBrief = brief.text
            Brief.Kosong -> taskBrief = ""
        }
    }
    val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    var first = true
    var turns = 0
    var lastSig = ""
    var sameCalls = 0
    var lastOutSig = ""
    var prevOutSig = ""
    val errTracker = ErrorTracker()
    var verifyNudges = 0
    while (true) {
        if (cancelled) throw Cancelled()
        if (++turns > MAX_AGENT_TURNS) {
            stopWithNote("Berhenti: sudah $MAX_AGENT_TURNS putaran tanpa selesai. Kirim pesan lagi untuk melanjutkan.")
            return
        }
        agentState = AgentState.CallingModel(turns)
        saveAgentCheckpoint(AgentCheckpoint(agentTaskId, userMsg, turns, "", "calling_model"))
        val phase = if (first) "Menganalisis permintaan" else "Memproses hasil langkah"
        setStatus(if (first) KIND_DOTS else IconView.GRID, phase)
        first = false
        compactIfNeeded(key)
        var wrote = false
        lastPartial = ""
        // Status tunggu yang jujur: hanya muncul bila model benar-benar belum mengirim token setelah 1,8 detik.
        val waitTask = Runnable {
            if (busy && !cancelled && !wrote && curTitle == phase) setStatus(KIND_DOTS, "Menunggu respons model AI")
        }
        uiHandler.postDelayed(waitTask, 1800)
        val turn = try {
            callModelRetry(
                key,
                onText = { t ->
                    lastPartial = t
                    if (!wrote) {
                        wrote = true
                        setStatus(IconView.NEW, "Menulis jawaban")
                    }
                    streamUpdate(t)
                },
                onFlush = {
                    lastPartial = ""
                    streamEnd()
                }
            )
        } finally {
            uiHandler.removeCallbacks(waitTask)
        }
        if (turn == null) {
            if (cancelled) throw Cancelled()
            runLog.note = "Model tidak memberi jawaban"
            runLog.outcome = RunOutcome.INCOMPLETE
            addMsg(AI, "(tidak ada jawaban)")
            // Jaga selang-seling riwayat (pengguna/model) supaya permintaan berikutnya tidak ditolak API.
            synchronized(hist) { if (hist.lastOrNull()?.role != 1) hist.add(Hist(1, "(tidak ada jawaban)")) }
            saveHist()
            return
        }
        // Model yang mengulang panggilan alat yang sama terus-menerus = macet. Hentikan, jangan bakar kuota.
        val sig = turn.calls.joinToString("|") { it.name + it.args.toString() }
        sameCalls = if (sig.isNotEmpty() && sig == lastSig && lastOutSig == prevOutSig) sameCalls + 1 else 0
        lastSig = sig
        if (sameCalls >= MAX_SAME_CALLS) {
            if (turn.text.isNotBlank()) synchronized(hist) { hist.add(Hist(1, turn.text)) }
            stopWithNote("Berhenti: AI mengulang langkah yang sama berkali-kali. Beri arahan lain atau coba model lain.")
            return
        }
        synchronized(hist) { hist.add(turn) }
        saveHist()
        if (turn.calls.isEmpty()) {
            runLog.finalText = turn.text
            // Jangan anggap selesai bila build terakhir gagal / kode diubah tanpa build berhasil (maks MAX_VERIFY_NUDGES kali).
            agentState = AgentState.Verifying
            saveAgentCheckpoint(AgentCheckpoint(agentTaskId, userMsg, turns, "", "verifying"))
            val nudge = if (verifyNudges < MAX_VERIFY_NUDGES && !cancelled) verificationNudge(runLog.steps) else null
            if (nudge != null) {
                verifyNudges++
                synchronized(hist) { hist.add(Hist(0, nudge)) }
                saveHist()
                addNote("Hasil belum terbukti berhasil, agen diminta memeriksa dulu.")
                continue
            }
            if (turn.text.isNotBlank()) {
                showChips(turn.text)
                if (voiceTurnPending) {
                    voiceTurnPending = false
                    speakReply(turn.text)
                }
            }
            return
        }
        val results = ArrayList<Pair<Call, String>>()
        var repeatStop = false
        for (c in turn.calls) {
            if (cancelled) throw Cancelled()
            agentState = AgentState.RunningTool(c.name)
            saveAgentCheckpoint(AgentCheckpoint(agentTaskId, userMsg, turns, c.name, "running_tool"))
            val title = stepTitleFor(c.name, c.args)
            val card = onUi {
                val icon = iconFor(c.name, c.args, title)
                val detail = detailFor(c.name, c.args)
                // quiet: kartu Aktivitas Agen sudah menampilkan langkah ini secara langsung
                setStatus(icon, title, quiet = true)
                newStepCard(title, detail, icon, c.name, c.args.optString("path"))
            }
            activeStep = card
            val out = try {
                runTool(c.name, c.args, card)
            } catch (e: Exception) {
                val stopped = cancelled || e is Cancelled || e is InterruptedException
                card.finish(false, if (stopped) "dihentikan" else (e.message ?: "error").take(30),
                    state = if (stopped) StepState.STOPPED else StepState.FAIL)
                if (stopped) throw e
                // Galat tak terduga pada satu alat tidak boleh menggugurkan seluruh tugas.
                // Dikembalikan sebagai hasil gagal agar model bisa membaca errornya dan memperbaiki diri.
                recoverableToolResult(e)
            } finally {
                activeStep = null
            }
            // Keluaran gagal yang panjang dipadatkan; error yang sama berulang diberi peringatan ke model.
            val structured = parseToolResult(out)
            val command = c.args.optString("command")
            val verifyKind = verificationKind(c.name, command)
            var forModel = condenseFailure(out)
            if (!structured.ok) {
                val retryable = retryPolicy(c.name, command, structured)
                val hint = when (structured.errorKind) {
                    ErrorKind.COMMAND_TIMEOUT -> "Cek proses dan hasil sebelum mengulang."
                    ErrorKind.NETWORK_AUTH -> "Periksa API key; jangan mengulang tanpa perubahan."
                    ErrorKind.TERMUX_UNAVAILABLE -> "Pastikan Termux tersedia dan izinkan RUN_COMMAND."
                    else -> if (!retryable) "Jangan mengulang tanpa mengubah pendekatan." else "Boleh mencoba pendekatan jaringan lain."
                }
                forModel += "\n\n[Kategori error: ${structured.errorKind}. $hint]"
                saveAgentCheckpoint(AgentCheckpoint(agentTaskId, userMsg, turns, c.name, "tool_failed", structured.errorKind.name))
            } else if (verifyKind != VerificationKind.NONE) {
                forModel += "\n\n[Verifikasi yang disarankan: ${verifyKind.name}. Pastikan hasil nyata sebelum menyatakan selesai.]"
            }
            val seen = errTracker.record(out)
            if (seen in WARN_SAME_ERROR_AT until STOP_SAME_ERROR_AT) forModel += repeatWarning(seen)
            results.add(Pair(c, forModel))
            if (seen >= STOP_SAME_ERROR_AT) repeatStop = true
        }
        if (cancelled) throw Cancelled()
        prevOutSig = lastOutSig
        lastOutSig = results.joinToString("\n") { it.second }
        synchronized(hist) { hist.add(Hist(2, results = results)) }
        saveHist()
        if (repeatStop) {
            stopWithNote("Berhenti: error yang sama muncul $STOP_SAME_ERROR_AT kali. Beri arahan lain atau coba model lain.")
            return
        }
    }
}

/** Akhiri putaran dengan catatan, dan pastikan riwayat berakhir di giliran model (bukan hasil alat yang menggantung). */
private fun MainActivity.stopWithNote(msg: String) {
    runLog.note = msg
    runLog.outcome = RunOutcome.INCOMPLETE
    synchronized(hist) { if (hist.lastOrNull()?.role != 1) hist.add(Hist(1, msg)) }
    saveHist()
    addNote(msg)
}
