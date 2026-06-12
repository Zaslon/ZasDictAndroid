package com.zaslon.zasdict.domain

/**
 * IPA記号を簡略化されたスペルに変換（ipa.py 移植）
 * 置換順序は元コードを忠実に維持している。
 */
object Ipa {

    fun ipaToSpell(input: String): String {
        var w = input
        // 母音
        w = w.replace(Regex("`|ˈ|ˌ"), "")
        w = w.replace(Regex("i|ɨ"), "i")
        w = w.replace(Regex("e|ɘ|e̞|ɛ|æ|ɜ|ɐ|œ|ɪ|ɪ̈"), "e")
        w = w.replace("ə", "(e|o)")
        w = w.replace("ɐ", "(e|a)")
        w = w.replace(Regex("ʊ|ø̞|o|ɤ̞|o̞|ʌ|ɔ"), "o")
        w = w.replace(Regex("a|ɶ|ä|ɑ|ɒ"), "a")
        w = w.replace(Regex("y|ʉ|ɯ|u|ʏ|ʊ̈|ɯ̽"), "u")
        // 子音
        w = w.replace(Regex("p|p̪"), "p")
        w = w.replace(Regex("t|t̪"), "t")
        w = w.replace(Regex("ʈ|c"), "t'")
        w = w.replace(Regex("k"), "k")
        w = w.replace(Regex("b|b̪"), "b")
        w = w.replace(Regex("d̪|d"), "d")
        w = w.replace(Regex("ɖ|ɟ"), "d'")
        w = w.replace(Regex("g"), "g")
        w = w.replace(Regex("m̥|m|ɱ̊|ɱ"), "m")
        w = w.replace(Regex("n̪̊|n̪|n̥|n"), "n")
        w = w.replace(Regex("ɳ|ɲ"), "n'")
        w = w.replace(Regex("ŋ"), "g")
        w = w.replace(Regex("r̥|r|ɹ̥|ɹ"), "r'")
        w = w.replace(Regex("ⱱ̟|ⱱ|ɸ|f|β̞|ʋ̥|ʋ"), "f")
        w = w.replace(Regex("ɾ|ɽ|ɟ̆"), "r")
        w = w.replace(Regex("β|v"), "v")
        w = w.replace(Regex("θ|s|ʃ"), "s")
        w = w.replace(Regex("ð|z|ʒ"), "z")
        w = w.replace(Regex("ʂ|ç|x"), "s'")
        w = w.replace(Regex("ʐ|ʝ|ɣ"), "z'")
        w = w.replace(Regex("χ"), "h")
        w = w.replace(Regex("ʁ"), "g")
        return w
    }
}
