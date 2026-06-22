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

    // ------------------------------------------------------------------
    // ストレージモード
    // ------------------------------------------------------------------

    var storageMode: StorageMode
        get() = StorageMode.entries.find { it.name == sp.getString("storage_mode", "LOCAL") }
            ?: StorageMode.LOCAL
        set(value) = sp.edit().putString("storage_mode", value.name).apply()

    // ------------------------------------------------------------------
    // Dropbox 認証情報
    // ------------------------------------------------------------------

    var dropboxAppKey: String
        get() = sp.getString("dropbox_app_key", "") ?: ""
        set(value) = sp.edit().putString("dropbox_app_key", value).apply()

    var dropboxAccessToken: String?
        get() = sp.getString("dropbox_access_token", null)
        set(value) = sp.edit().putString("dropbox_access_token", value).apply()

    var dropboxRefreshToken: String?
        get() = sp.getString("dropbox_refresh_token", null)
        set(value) = sp.edit().putString("dropbox_refresh_token", value).apply()

    /** アクセストークンの有効期限（エポックミリ秒） */
    var dropboxTokenExpiry: Long
        get() = sp.getLong("dropbox_token_expiry", 0L)
        set(value) = sp.edit().putLong("dropbox_token_expiry", value).apply()

    var dropboxDisplayName: String?
        get() = sp.getString("dropbox_display_name", null)
        set(value) = sp.edit().putString("dropbox_display_name", value).apply()

    // ------------------------------------------------------------------
    // Dropbox ファイルパス
    // ------------------------------------------------------------------

    /** Dropbox上の辞書JSONファイルのパス（例: /zasdict/dictionary.json） */
    var dropboxDictPath: String?
        get() = sp.getString("dropbox_dict_path", null)
        set(value) = sp.edit().putString("dropbox_dict_path", value).apply()

    var dropboxDictName: String?
        get() = sp.getString("dropbox_dict_name", null)
        set(value) = sp.edit().putString("dropbox_dict_name", value).apply()

    /** Dropbox上のchangelog CSVパス（辞書パスから自動生成） */
    var dropboxChangelogPath: String?
        get() = sp.getString("dropbox_changelog_path", null)
        set(value) = sp.edit().putString("dropbox_changelog_path", value).apply()

    /**
     * ローカルキャッシュにDropboxへ未アップロードの変更があるか。
     * アプリが強制終了されてもこのフラグで未同期を検知できる。
     */
    var dropboxHasPendingUpload: Boolean
        get() = sp.getBoolean("dropbox_pending_upload", false)
        set(value) = sp.edit().putBoolean("dropbox_pending_upload", value).apply()

    // ------------------------------------------------------------------
    // GitHub 認証情報
    // ------------------------------------------------------------------

    /** Personal Access Token（repo スコープが必要） */
    var githubToken: String?
        get() = sp.getString("github_token", null)
        set(value) = sp.edit().putString("github_token", value).apply()

    var githubOwner: String?
        get() = sp.getString("github_owner", null)
        set(value) = sp.edit().putString("github_owner", value).apply()

    var githubRepo: String?
        get() = sp.getString("github_repo", null)
        set(value) = sp.edit().putString("github_repo", value).apply()

    /** 対象ブランチ（デフォルト: main） */
    var githubBranch: String
        get() = sp.getString("github_branch", "main") ?: "main"
        set(value) = sp.edit().putString("github_branch", value).apply()

    var githubDisplayName: String?
        get() = sp.getString("github_display_name", null)
        set(value) = sp.edit().putString("github_display_name", value).apply()

    // ------------------------------------------------------------------
    // GitHub ファイルパス
    // ------------------------------------------------------------------

    var githubDictPath: String?
        get() = sp.getString("github_dict_path", null)
        set(value) = sp.edit().putString("github_dict_path", value).apply()

    var githubDictName: String?
        get() = sp.getString("github_dict_name", null)
        set(value) = sp.edit().putString("github_dict_name", value).apply()

    var githubChangelogPath: String?
        get() = sp.getString("github_changelog_path", null)
        set(value) = sp.edit().putString("github_changelog_path", value).apply()

    var githubHasPendingUpload: Boolean
        get() = sp.getBoolean("github_pending_upload", false)
        set(value) = sp.edit().putBoolean("github_pending_upload", value).apply()

    // ------------------------------------------------------------------
    // Box 認証情報
    // ------------------------------------------------------------------

    var boxClientId: String
        get() = sp.getString("box_client_id", "") ?: ""
        set(value) = sp.edit().putString("box_client_id", value).apply()

    var boxClientSecret: String
        get() = sp.getString("box_client_secret", "") ?: ""
        set(value) = sp.edit().putString("box_client_secret", value).apply()

    var boxAccessToken: String?
        get() = sp.getString("box_access_token", null)
        set(value) = sp.edit().putString("box_access_token", value).apply()

    var boxRefreshToken: String?
        get() = sp.getString("box_refresh_token", null)
        set(value) = sp.edit().putString("box_refresh_token", value).apply()

    var boxTokenExpiry: Long
        get() = sp.getLong("box_token_expiry", 0L)
        set(value) = sp.edit().putLong("box_token_expiry", value).apply()

    var boxDisplayName: String?
        get() = sp.getString("box_display_name", null)
        set(value) = sp.edit().putString("box_display_name", value).apply()

    // ------------------------------------------------------------------
    // Box ファイル情報
    // ------------------------------------------------------------------

    /** Box上の辞書ファイル ID */
    var boxDictFileId: String?
        get() = sp.getString("box_dict_file_id", null)
        set(value) = sp.edit().putString("box_dict_file_id", value).apply()

    /** 辞書ファイルが置かれているフォルダ ID */
    var boxDictFolderId: String?
        get() = sp.getString("box_dict_folder_id", null)
        set(value) = sp.edit().putString("box_dict_folder_id", value).apply()

    var boxDictName: String?
        get() = sp.getString("box_dict_name", null)
        set(value) = sp.edit().putString("box_dict_name", value).apply()

    var boxChangelogFileId: String?
        get() = sp.getString("box_changelog_file_id", null)
        set(value) = sp.edit().putString("box_changelog_file_id", value).apply()

    /** changelog を配置するフォルダ ID（明示的に変更されていない場合は boxDictFolderId と同じ） */
    var boxChangelogFolderId: String?
        get() = sp.getString("box_changelog_folder_id", null)
        set(value) = sp.edit().putString("box_changelog_folder_id", value).apply()

    var boxHasPendingUpload: Boolean
        get() = sp.getBoolean("box_pending_upload", false)
        set(value) = sp.edit().putBoolean("box_pending_upload", value).apply()
}
