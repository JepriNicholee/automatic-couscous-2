package com.kita.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit test susunan cadangan dan status kartu peralihan. Murni JVM: ./gradlew testDebugUnitTest */
class FailoverTest {

    private val gemini = ProviderPool("Gemini", "gemini-3.5-flash", listOf("gemini-3.5-flash", "gemini-2.5-flash", "gemini-2.5-flash-lite", "gemma-4"))
    private val openai = ProviderPool("OpenAI", "gpt-4o", listOf("gpt-4o", "gpt-4o-mini"))

    @Test
    fun modelYangGagalDilewatiDanPenyediaUtamaDidahulukan() {
        val chain = buildChain("Gemini", listOf(openai, gemini), setOf("Gemini/gemini-3.5-flash"))
        assertEquals(FoCandidate("Gemini", "gemini-2.5-flash"), chain.first())
        assertTrue(chain.none { it.id == "Gemini/gemini-3.5-flash" })
    }

    @Test
    fun penyediaLainDicobaSebelumModelKetigaDiPenyediaUtama() {
        val chain = buildChain("Gemini", listOf(gemini, openai), setOf("Gemini/gemini-3.5-flash"))
        val ids = chain.map { it.id }
        assertTrue(ids.indexOf("OpenAI/gpt-4o") < ids.indexOf("Gemini/gemma-4"))
    }

    @Test
    fun semuaDiblokirHasilnyaKosong() {
        val all = (gemini.models + "x").map { "Gemini/$it" }.toSet()
        assertTrue(buildChain("Gemini", listOf(ProviderPool("Gemini", "", gemini.models)), all).isEmpty())
    }

    @Test
    fun batasTotalDipatuhi() {
        val big = ProviderPool("Groq", "m0", (1..30).map { "m$it" })
        assertEquals(5, buildChain("Groq", listOf(big), emptySet(), total = 5).size)
    }

    @Test
    fun trackerAlurBerhasil() {
        val t = FailoverTracker(3)
        t.failed("Gemini", "a", FoState.BUSY)
        t.trying("OpenAI", "gpt-4o")
        assertEquals(0, t.snapshot().tone)
        t.ok()
        val s = t.snapshot()
        assertEquals(1, s.tone)
        assertTrue(s.title.contains("OpenAI · gpt-4o"))
        assertEquals(listOf(FoState.BUSY, FoState.OK), s.rows.map { it.state })
    }

    @Test
    fun trackerMenungguDanGagalTotal() {
        val t = FailoverTracker(3)
        t.failed("Gemini", "a", FoState.QUOTA)
        t.countdown = 12
        t.round = 2
        assertTrue(t.snapshot().waiting)
        assertTrue(t.snapshot().subtitle.contains("12 dtk") && t.snapshot().subtitle.contains("2/3"))
        t.giveUp()
        assertEquals(2, t.snapshot().tone)
    }
}
