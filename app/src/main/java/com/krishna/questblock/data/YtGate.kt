package com.krishna.questblock.data

import android.content.Context
import java.text.Normalizer
import java.util.Locale

/** Coins for a 5-minute block of non-study YouTube: 2 coins a minute. */
const val YT_FUN_RATE_PER_5 = 10

/**
 * YouTube study gate (v157). Matches a video title against the Class 12 chapters (Physics, Chemistry, Maths).
 * Offline, no AI, no database: the chapter names live in one HashMap, and the "studied" flag is the tick on the Chapters page.
 *  - chapter found and studied  -> FREE
 *  - chapter found, not studied -> STUDY_FIRST
 *  - no chapter in the title    -> FUN (coins)
 */
object YtGate {
    enum class Kind { FREE, STUDY_FIRST, FUN }
    data class Decision(val kind: Kind, val chapter: String = "")

    private class Ch(val subj: String, val n: Int, val name: String)

    // subject id + chapter number follow the Chapters page (P Physics, C Chemistry, M Maths)
    private val CHAPTERS = HashMap<String, Ch>().apply {
        fun a(key: String, s: String, n: Int, name: String) { put(key, Ch(s, n, name)) }
        a("electric charges", "P", 1, "Electric Charge and Electric Field")
        a("electrostatic potential", "P", 2, "Electrostatic Potential and Capacitance")
        a("current electricity", "P", 3, "Current Electricity")
        a("moving charges", "P", 4, "Moving Charges and Magnetism")
        a("magnetism and matter", "P", 5, "Magnetism and Matter")
        a("electromagnetic induction", "P", 6, "Electromagnetic Induction")
        a("alternating current", "P", 7, "Alternating Current")
        a("electromagnetic waves", "P", 8, "Electromagnetic Waves")
        a("ray optics", "P", 9, "Ray Optics and Optical Instruments")
        a("wave optics", "P", 10, "Wave Optics")
        a("dual nature", "P", 11, "Dual Nature of Radiation and Matter")
        a("atoms", "P", 12, "Atoms")
        a("nuclei", "P", 13, "Nuclei")
        a("semiconductor electronics", "P", 14, "Semiconductor Electronics")
        a("solutions", "C", 1, "Solutions")
        a("electrochemistry", "C", 2, "Electrochemistry")
        a("chemical kinetics", "C", 3, "Chemical Kinetics")
        a("d and f block", "C", 4, "d- and f-block Elements")
        a("coordination compounds", "C", 5, "Co-ordination Compounds")
        a("haloalkanes", "C", 6, "Haloalkanes and Haloarenes")
        a("alcohols phenols and ethers", "C", 7, "Alcohols, Phenols and Ethers")
        a("aldehydes ketones", "C", 8, "Aldehydes, Ketones and Carboxylic Acids")
        a("amines", "C", 9, "Amines")
        a("biomolecules", "C", 10, "Bio-molecules")
        a("relations and functions", "M", 1, "Relations and Functions")
        a("inverse trigonometric", "M", 2, "Inverse Trigonometric Functions")
        a("matrices", "M", 3, "Matrices")
        a("determinants", "M", 4, "Determinants")
        a("continuity and differentiability", "M", 5, "Continuity and Differentiability")
        a("application of derivatives", "M", 6, "Application of Derivatives")
        a("integration", "M", 7, "Integration")
        a("application of integrals", "M", 8, "Application of Integrals")
        a("differential equations", "M", 9, "Differential Equations")
        a("vector algebra", "M", 10, "Vector Algebra")
        a("three dimensional geometry", "M", 11, "Three-dimensional Geometry")
        a("linear programming", "M", 12, "Linear Programming")
        a("probability", "M", 13, "Probability")
    }

    /** Other ways a title may name the same chapter. */
    private val ALIAS = hashMapOf(
        "integrals" to "integration", "integral" to "integration", "definite integrals" to "integration",
        "electric charges and fields" to "electric charges", "electric charge" to "electric charges",
        "haloarenes" to "haloalkanes", "bio molecules" to "biomolecules",
        "d block" to "d and f block", "f block" to "d and f block", "d and f block elements" to "d and f block",
        "co ordination compounds" to "coordination compounds", "semiconductors" to "semiconductor electronics",
        "ray optics and optical instruments" to "ray optics", "dual nature of radiation and matter" to "dual nature",
        "3d geometry" to "three dimensional geometry", "differentiation" to "continuity and differentiability",
        "vectors" to "vector algebra", "moving charges and magnetism" to "moving charges",
    )

    /** Chapters per subject (Class 12), and "P1" -> chapter for the number-only titles. */
    private val SIZE = hashMapOf("P" to 14, "C" to 10, "M" to 13)
    private val BY_NUM: Map<String, Ch> = CHAPTERS.values.associateBy { it.subj + it.n }

    /** Subject words in a title (English and Hinglish). */
    private val SUBJECT_WORDS = hashMapOf(
        "physics" to "P", "physic" to "P", "phy" to "P", "bhautiki" to "P",
        "chemistry" to "C", "chem" to "C", "rasayan" to "C",
        "maths" to "M", "math" to "M", "mathematics" to "M", "ganit" to "M",
    )
    /** "ch 1", "chapter 1", "lecture 1", "adhyay 1": the number after these words is the chapter number. */
    private val CHAPTER_WORDS = hashSetOf("ch", "chap", "chapter", "adhyay", "adhyaay", "lecture", "lec", "lect")
    /** A number-only title ("Ch 1") counts only when it also looks like study content, so "Naruto Ch 5" stays FUN. */
    private val STUDY_WORDS = hashSetOf(
        "class", "12th", "xii", "jee", "neet", "cbse", "ncert", "board", "boards",
        "lecture", "lec", "lect", "revision", "oneshot", "shot", "pyq", "pyqs",
    )
    private val GLUED = Regex("^(ch|chap|chapter|adhyay|adhyaay|lecture|lec|lect)(\\d{1,2})$")   // "ch1" -> "ch" "1"

    /**
     * Lowercase words only. ALL CAPS, accents and Turkish-locale "I" are handled (Locale.ROOT), punctuation is dropped:
     * "SOLUTIONS - ONE SHOT | Co-ordination!" -> "solutions one shot coordination".
     */
    private fun words(title: String): List<String> {
        val s = Normalizer.normalize(title, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT).replace("co-ordination", "coordination")
            .replace(Regex("[^a-z0-9]+"), " ").trim()
        if (s.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        for (w in s.split(' ')) {
            val m = GLUED.matchEntire(w)
            if (m != null) { out += m.groupValues[1]; out += m.groupValues[2] } else out += w
        }
        return out
    }

    /** Same video = same key (used to judge a video once). */
    fun key(title: String): String = words(title).joinToString(" ")

    /** Longest word group first (4 down to 1); every probe is one hash lookup. */
    private fun find(w: List<String>): Ch? {
        for (n in 4 downTo 1) {
            for (i in 0..(w.size - n)) {
                val p = w.subList(i, i + n).joinToString(" ")
                CHAPTERS[ALIAS[p] ?: p]?.let { return it }
            }
        }
        return null
    }

    private fun subjectsOf(w: List<String>): Set<String> {
        val out = HashSet<String>()
        for (x in w) SUBJECT_WORDS[x]?.let { out += it }
        return out
    }

    /** The number after "ch" / "chapter" / "lecture" ("chapter no 3" too), or null. */
    private fun chapterNumber(w: List<String>): Int? {
        for (i in w.indices) {
            if (w[i] !in CHAPTER_WORDS) continue
            var j = i + 1
            if (j < w.size && w[j] == "no") j++
            val n = w.getOrNull(j)?.takeIf { it.length <= 2 }?.toIntOrNull() ?: continue
            if (n in 1..99) return n
        }
        return null
    }

    /** Class 11 is not part of this gate for now: such titles are plain FUN. */
    private fun isClass11(w: List<String>): Boolean {
        if ("11th" in w || "xi" in w) return true
        val i = w.indexOf("class")
        return i >= 0 && w.getOrNull(i + 1) == "11"
    }

    /** A chapter lecture is study time: free, whether the chapter is ticked or not. */
    private fun verdict(name: String, @Suppress("UNUSED_PARAMETER") cands: List<Ch>, @Suppress("UNUSED_PARAMETER") studied: (String, Int) -> Boolean): Decision =
        Decision(Kind.FREE, name)

    /**
     * [title] is the playing video's title, [hint] the few texts right under it (channel name ...).
     * 1. chapter name in the title                  -> exact chapter
     * 2. only "Ch 1" / "Lecture 1" (+ study words) -> subject word in the title (or the channel) picks the subject;
     *    no subject word -> the number is taken in any subject, and one studied "Ch 1" is enough for FREE
     * 3. nothing                                     -> FUN
     */
    fun classify(title: String, hint: String = "", studied: (String, Int) -> Boolean): Decision {
        val w = words(title)
        if (w.isEmpty() || isClass11(w)) return Decision(Kind.FUN)
        val inTitle = subjectsOf(w)
        find(w)?.let { c -> if (inTitle.isEmpty() || c.subj in inTitle) return verdict(c.name, listOf(c), studied) }
        val n = chapterNumber(w) ?: return Decision(Kind.FUN)
        val h = words(hint.replace(Regex("(?i)physics\\s*wallah"), " "))     // "Physics Wallah" teaches all subjects: not a hint
        val subjects = inTitle.ifEmpty { subjectsOf(h) }
        val studyLike = subjects.isNotEmpty() || w.any { it in STUDY_WORDS } || h.any { it in STUDY_WORDS }
        if (!studyLike) return Decision(Kind.FUN)
        val cands = (if (subjects.isEmpty()) SIZE.keys else subjects).mapNotNull { BY_NUM[it + n] }
        if (cands.isEmpty()) return Decision(Kind.FUN)
        return verdict(if (cands.size == 1) cands[0].name else "Chapter $n", cands, studied)
    }

    /** The tick on the Chapters page ("chap_done_<subject>" holds the studied chapter numbers). */
    fun decide(ctx: Context, title: String, hint: String = ""): Decision {
        val sp = ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE)
        return classify(title, hint) { subj, n ->
            (sp.getString("chap_done_$subj", "") ?: "").split(',').any { it.toIntOrNull() == n }
        }
    }
}
