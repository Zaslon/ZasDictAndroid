package com.zaslon.zasdict.domain

/**
 * ZasDict - 定数（const.py 移植）
 */
object Const {
    const val APP_TITLE = "ZasDict"
    const val CUSTOM_ORDER = "eaoiuhkstcnrmpfgzdbv- "

    val SEARCH_MODES = listOf("前方", "部分", "後方", "完全")
    val SEARCH_SCOPES = listOf("見出し語・訳語", "全文")

    /** 品詞の選択肢（バリデーション用） */
    val VALID_POS = listOf(
        "名詞",        // 通常名詞・複合名詞
        "代名詞",      // 私、あなた、彼など
        "固有名詞",    // 人名・地名など
        "動詞",        // 活用する動詞
        "記述詞",      // 形容詞・副詞
        "法性記述詞",  // モダリティを表す記述詞
        "助詞",        // は、が、を、など
        "接続詞",      // そして、しかし、など
        "間投詞",      // ああ、ええ、こんにちは、など
        "慣用句",      // 足が出る、鯖を読む、など
        "ことわざ",    // 勝てば官軍、仏の顔も三度まで、など
        "接頭辞",      // 再〜、非〜、など
        "接尾辞",      // 〜的、〜性、など
        "助動詞"       // 〜ない、〜れる、など
    )

    /** 品詞の説明（凡例表示用） */
    val POS_DESCRIPTIONS = mapOf(
        "名詞" to "通常名詞・複合名詞",
        "代名詞" to "私、あなた、彼など",
        "固有名詞" to "人名・地名など",
        "動詞" to "活用する動詞",
        "記述詞" to "形容詞・副詞",
        "法性記述詞" to "モダリティを表す記述詞",
        "助詞" to "は、が、を、など",
        "接続詞" to "そして、しかし、など",
        "間投詞" to "ああ、ええ、こんにちは、など",
        "慣用句" to "足が出る、鯖を読む、など",
        "ことわざ" to "勝てば官軍、仏の顔も三度まで、など",
        "接頭辞" to "再〜、非〜、など",
        "接尾辞" to "〜的、〜性、など",
        "助動詞" to "〜ない、〜れる、など"
    )

    /** 関係の選択肢（バリデーション用） */
    val VALID_RELATIONS = listOf(
        "類義語", "対義語", "上位語", "下位語", "関連", "参照", "省略", "同意"
    )

    /** 関係の対照関係 */
    val RECIPROCAL_MAP = mapOf(
        "類義語" to "類義語",
        "対義語" to "対義語",
        "上位語" to "下位語",
        "下位語" to "上位語",
        "関連" to "関連",
        "参照" to "参照",
        "省略" to "省略",
        "同意" to "同意"
    )

    const val PRONUNCIATION_TITLE = "発音記号"

    /** 内容欄の種類（エディタで上から順に追加ボタンとして表示。各項目は1つまで） */
    val CONTENT_TYPES = listOf("語法", "文化", "用例", "語源")

    /** 例文の出典カタログ（API識別名 to 表示名）。末尾の「自作」は固定 */
    const val EXAMPLE_CATALOG_SELF = "自作"
    val EXAMPLE_CATALOG_OPTIONS = listOf(
        Pair("zpdicDaily",    "zpdicDaily — 今日の例文"),
        Pair("appleAlpha",    "appleAlpha — リンゴを食べたい 58 文"),
        Pair("appleBeta",     "appleBeta — リンゴを食べ足りない 57 文"),
        Pair("appleGamma",    "appleGamma — リンゴをもっと食べたい 55 文"),
        Pair("survival",      "survival — 今日を生き抜く実用例文"),
        Pair("weaving",       "weaving — 手袋と辞書を編む 50 文"),
        Pair("shaleianAlpha", "shaleianAlpha — 今日のシャレイア語 I"),
        Pair("shaleianBeta",  "shaleianBeta — 今日のシャレイア語 II"),
        Pair("meat",          "meat — 古代の民族のためのお肉例文"),
        Pair("arithmetic",    "arithmetic — 算数例文"),
        Pair("adposition",    "adposition — 格や接置詞のための例文集"),
        Pair(EXAMPLE_CATALOG_SELF, EXAMPLE_CATALOG_SELF)
    )
}
