package com.zaslon.zasdict.domain

/**
 * テキストの前処理とカスタムソート（func.py TextProcessor 移植）
 */
object TextProcessor {

    private val orderMap: Map<Char, Int> =
        Const.CUSTOM_ORDER.withIndex().associate { (i, c) -> c to i }

    /** 前処理: ハイフン・括弧・アポストロフィを除去し小文字化 */
    fun preprocess(text: String): String {
        var t = text
        t = t.replace(Regex("^-+"), "")
        t = t.replace(Regex("-+$"), "")
        t = t.replace("（", "").replace("）", "").replace("'", "")
        return t.lowercase()
    }

    /** カスタムソート順による比較関数 */
    fun compareForms(a: String, b: String): Int {
        val origA = a
        val origB = b
        val procA = preprocess(a)
        val procB = preprocess(b)

        // 1. カスタム順序で比較
        for (i in 0 until minOf(procA.length, procB.length)) {
            val ca = procA[i]
            val cb = procB[i]
            if (ca != cb) {
                return (orderMap[ca] ?: 999) - (orderMap[cb] ?: 999)
            }
        }

        // 2. 長さで比較
        if (procA.length != procB.length) {
            return procA.length - procB.length
        }

        // 3. アポストロフィの有無
        val aApos = origA.contains("'")
        val bApos = origB.contains("'")
        if (aApos != bApos) {
            return if (!aApos) -1 else 1
        }

        // 4. 大文字小文字
        for (i in 0 until minOf(origA.length, origB.length)) {
            val ca = origA[i]
            val cb = origB[i]
            if (ca != cb) {
                if (ca.isUpperCase() && cb.isLowerCase()) return -1
                if (cb.isUpperCase() && ca.isLowerCase()) return 1
            }
        }

        // 5. 記号の有無
        val symbols = setOf('-', '(', ')')
        val aHasSymbol = origA.any { it in symbols }
        val bHasSymbol = origB.any { it in symbols }
        if (aHasSymbol != bHasSymbol) {
            return if (!aHasSymbol) -1 else 1
        }

        // 6. ハイフン位置
        if (origA.contains("-") || origB.contains("-")) {
            val posA = if (origA.contains("-")) origA.lastIndexOf("-") else -1
            val posB = if (origB.contains("-")) origB.lastIndexOf("-") else -1
            if (posA != posB) {
                return (origA.length - posA) - (origB.length - posB)
            }
        }

        // 7. 括弧位置
        if (origA.contains("（") || origB.contains("（")) {
            var posA = if (origA.contains("（")) origA.indexOf("（") else 999
            var posB = if (origB.contains("（")) origB.indexOf("（") else 999
            if (posA != posB) return posA - posB
            posA = if (origA.contains("）")) origA.indexOf("）") else 999
            posB = if (origB.contains("）")) origB.indexOf("）") else 999
            if (posA != posB) return posA - posB
        }

        return 0
    }
}
