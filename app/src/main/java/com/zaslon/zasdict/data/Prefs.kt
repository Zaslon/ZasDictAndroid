package com.zaslon.zasdict.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 環境設定の永続化（デスクトップ版 settings.ini 相当）。
 * Android では端末ごとにアプリデータが分かれるため、PC名でのグループ分けは不要。
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("zasdict_prefs", Context.MODE_PRIVATE)

    var lastDictionaryUri: String?
        get() = sp.getString("last_dictionary", null)
        set(value) = sp.edit().putString("last_dictionary", value).apply()

    /** フォントサイズ倍率（デスクトップ版のフォントサイズ設定に相当） */
    var fontScale: Float
        get() = sp.getFloat("font_scale", 1.0f)
        set(value) = sp.edit().putFloat("font_scale", value).apply()

    /** 自動上書き保存 */
    var autoSave: Boolean
        get() = sp.getBoolean("auto_save", false)
        set(value) = sp.edit().putBoolean("auto_save", value).apply()

    /** Heksa（イジェール文字フォント）を有効にする */
    var idyerFont: Boolean
        get() = sp.getBoolean("idyer_font", false)
        set(value) = sp.edit().putBoolean("idyer_font", value).apply()

    /** E-inkモード（アニメーション・影を排除した白黒表示） */
    var einkMode: Boolean
        get() = sp.getBoolean("eink_mode", false)
        set(value) = sp.edit().putBoolean("eink_mode", value).apply()

    /** 検索モード・スコープの保持 */
    var searchMode: String
        get() = sp.getString("search_mode", "前方") ?: "前方"
        set(value) = sp.edit().putString("search_mode", value).apply()

    var searchScope: String
        get() = sp.getString("search_scope", "見出し語・訳語") ?: "見出し語・訳語"
        set(value) = sp.edit().putString("search_scope", value).apply()
}
