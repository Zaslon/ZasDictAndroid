package com.zaslon.zasdict.domain

/**
 * イジェール語方言変換（kaiomom.py 移植）
 *
 * 元コードの挙動を忠実に再現している。
 * 注: 元コードの titauini() では「3母音化」の結果が直後の行で
 * 上書きされて捨てられている（w を再使用しているため）。
 * 互換性のため同じ挙動を維持している。修正したい場合は
 * KAIOMOM_FAITHFUL_TITAUINI を false にすること。
 */
object Kaiomom {

    private const val KAIOMOM_FAITHFUL_TITAUINI = true

    private fun String.tr(from: String, to: String): String {
        val map = from.zip(to).toMap()
        return this.map { map[it] ?: it }.joinToString("")
    }

    /** 大文字や語頭、語末文字の削除 */
    private fun strip(w: String): String {
        var x = w.replace("#", "")
        x = x.replace("φ", "")
        x = x.replace("e", "(a|i)")
        x = x.replace("o", "(e|a)")
        x = x.replace("q", "(b|u)")
        x = x.replace("x", "(sa|s'i)")
        x = x.replace("l", "(r'a|ri)")
        x = x.lowercase()
        return x
    }

    /** 前処理イジェール語正書法への適合 */
    private fun ortho1(w: String): String {
        var x = w.tr("AIUEO", "12345")
        x = x.lowercase()
        x = x.tr("12345", "AIUEO")
        x = x.replace("ki", "kyi")
        x = x.replace("kI", "kyI")
        x = x.replace("sh", "sy")
        x = x.replace("si", "syi")
        x = x.replace("sI", "syI")
        x = x.replace("ti", "tyi")
        x = x.replace("tI", "tyI")
        x = x.replace("ch", "ty")
        x = x.replace("ts", "c")
        x = x.replace("tu", "cu")
        x = x.replace("tU", "cU")
        x = x.replace("fu", "hu")
        x = x.replace("fU", "hU")
        x = x.replace("dh", "dy")
        x = x.replace("j", "zy")
        return x
    }

    /** 後処理イジェール語正書法への適合 */
    private fun ortho2(w: String): String {
        var x = Regex("([stnzdbrSTNZDBR])y").replace(w) { m -> m.groupValues[1] + "'" }
        x = x.replace("#y", "#i")
        x = x.tr("yw", "iu")
        return x
    }

    private fun commonf(w: String): String {
        var x = w.replace("#hu", "#fu")
        x = Regex("(#)h([^y])").replace(x) { m -> m.groupValues[1] + m.groupValues[2] }
        x = x.tr("AIUEO", "EAOIU")
        x = x.tr("aiueo", "uiaeo")
        x = Regex("([^c])uφ").replace(x) { m -> m.groupValues[1] + "φ" }
        return x
    }

    private fun commone(w: String): String {
        var x = w.replace("#hu", "#fu")
        x = Regex("(#)h([^y])").replace(x) { m -> m.groupValues[1] + m.groupValues[2] }
        x = x.tr("AIUEO", "EAOIU")
        x = x.tr("aiueo", "uiaeo")
        x = Regex("([^c])uφ").replace(x) { m -> m.groupValues[1] + "eφ" }
        return x
    }

    /** 旗艦方言(Sekore)への変換 */
    private fun sekore(w: String): String {
        // C1強勢時
        var x = Regex("h([AIUEO])").replace(w) { m -> "F" + m.groupValues[1] }
        x = Regex("r(y*?)([AIUEO])").replace(x) { m -> "D" + m.groupValues[1] + m.groupValues[2] }
        // C2強勢時
        x = Regex("([AIUEO])[uw]").replace(x) { m -> m.groupValues[1] + "V" }
        x = Regex("([AIUEO])t").replace(x) { m -> m.groupValues[1] + "C" }
        x = Regex("([AIUEO])r").replace(x) { m -> m.groupValues[1] + "D" }
        // 強勢VC後C1
        x = Regex("([AIUEO])[sc]").replace(x) { m -> m.groupValues[1] + "Z" }
        x = Regex("([AIUEO])t").replace(x) { m -> m.groupValues[1] + "D" }
        x = Regex("([AIUEO])f").replace(x) { m -> m.groupValues[1] + "V" }
        x = Regex("([AIUEO])[kh]").replace(x) { m -> m.groupValues[1] + "G" }
        x = Regex("([AIUEO])p").replace(x) { m -> m.groupValues[1] + "B" }
        // C1の子音変化
        x = Regex("p(y*?)([aiueo])").replace(x) { m -> "f" + m.groupValues[1] + m.groupValues[2] }
        x = Regex("v(y*?)([aiueo])").replace(x) { m -> "u" + m.groupValues[1] + m.groupValues[2] }
        x = Regex("d(y*?)([aiueo])").replace(x) { m -> "r" + m.groupValues[1] + m.groupValues[2] }
        x = Regex("[kg](y*?)([aiueo])").replace(x) { m -> "h" + m.groupValues[1] + m.groupValues[2] }
        // C2の子音変化
        x = Regex("([aiueo])f").replace(x) { m -> m.groupValues[1] + "p" }
        x = Regex("([aiueo])[td]").replace(x) { m -> m.groupValues[1] + "r" }
        x = Regex("([aiueo])v").replace(x) { m -> m.groupValues[1] + "u" }
        x = Regex("([aiueo])g").replace(x) { m -> m.groupValues[1] + "h" }
        // 強勢のない半母音の母音化
        x = Regex("[y']([^AIUEO])").replace(x) { m -> m.groupValues[1] }
        // ts-->c
        x = Regex("[tT][sS]").replace(x, "c")
        x = ortho2(x)
        x = strip(x)
        return x
    }

    /** 資源循環艦方言(Titauini)への変換 */
    private fun titauini(w: String): String {
        var x: String
        if (KAIOMOM_FAITHFUL_TITAUINI) {
            // 元コードと同じ挙動: 3母音化の結果は次行で破棄される
            @Suppress("UNUSED_VALUE")
            x = w.tr("oOE", "eUI")
            x = w.tr("jdbw", "drwq")
        } else {
            x = w.tr("oOE", "eUI")
            x = x.tr("jdbw", "drwq")
        }
        x = ortho2(x)
        x = strip(x)
        return x
    }

    /** 探査艦方言(Kaiko)への変換 */
    private fun kaiko(w: String): String {
        // s,r変化
        var x = Regex("s([ieIE])").replace(w) { m -> "sy" + m.groupValues[1] }
        x = x.replace("se", "x")
        x = Regex("r([auAU])").replace(x) { m -> "ry" + m.groupValues[1] }
        x = x.replace("ro", "l")
        // 強勢音節母音変化
        x = x.replace("Easi", "AU")
        x = x.replace("A", "AI")
        x = x.replace("O", "EI")
        x = x.replace("U", "OU")
        // 語末子音削除
        x = Regex("[^aiueoAIUEO]φ").replace(x, "φ")
        // 子音変化
        x = Regex("g([aiueoAIUEO])").replace(x) { m -> "ny" + m.groupValues[1] }
        x = x.tr("vh", "uu")
        x = x.replace("zy", "i")
        // 連続子音変化
        x = Regex("[^aiueoAIUEOxly#]([^aiueoAIUEOxly])").replace(x) { m ->
            m.groupValues[1] + m.groupValues[1]
        }
        x = Regex("[^aiueoAIUEOxly#]x").replace(x, "sx")
        x = Regex("[^aiueoAIUEOxly#]l").replace(x, "rl")
        x = ortho2(x)
        x = strip(x)
        return x
    }

    /** 教団暗号(Arzafire)への変換準備 */
    private fun arzafire(w: String): String {
        var x = w.tr("aiueoAIUEO", "iueoaIUEOA")
        x = x.tr("kstnhmyrwgzdbp", "stnhmrrrkzdbgp")
        x = x.replace("pr", "py")
        x = x.replace("sr", "sy")
        x = x.replace("nr", "ny")
        x = x.replace("hr", "hy")
        x = x.replace("mr", "my")
        x = x.replace("rr", "ry")
        x = x.replace("zr", "zy")
        x = x.replace("dr", "dy")
        x = x.replace("gr", "gy")
        return x
    }

    /**
     * イジェール語の単語を各方言に変換する
     * @param word 元単語（アクセントは大文字で指定）
     * @return 各方言の変換結果（sekore / titauini / kaiko / arzafire）
     */
    fun convertIdyer(word: String): Map<String, String> {
        // 前処理
        var processed = "#$word" + "φ"
        processed = ortho1(processed)

        val result = mutableMapOf<String, String>()

        // commoneとcommonfの結果が同じかチェック
        val ce = commone(processed)
        val cf = commonf(processed)
        if (ce == cf) {
            result["sekore"] = sekore(ce)
            result["titauini"] = titauini(ce)
            result["kaiko"] = kaiko(ce)
        } else {
            result["sekore"] = "${sekore(ce)}または${sekore(cf)}"
            result["titauini"] = "${titauini(ce)}または${titauini(cf)}"
            result["kaiko"] = "${kaiko(ce)}または${kaiko(cf)}"
        }

        // Arzafire変換
        val arzaWord = arzafire(processed)
        val ceArza = commone(arzaWord)
        val cfArza = commonf(arzaWord)
        result["arzafire"] = if (ceArza == cfArza) {
            sekore(ceArza)
        } else {
            "${sekore(ceArza)}または${sekore(cfArza)}"
        }

        return result
    }
}
