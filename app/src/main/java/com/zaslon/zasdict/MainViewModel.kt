package com.zaslon.zasdict

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zaslon.zasdict.data.BoxApiClient
import com.zaslon.zasdict.data.ChangelogStore
import com.zaslon.zasdict.data.DictionaryStore
import com.zaslon.zasdict.data.DropboxApiClient
import com.zaslon.zasdict.data.GitHubApiClient
import com.zaslon.zasdict.data.Prefs
import com.zaslon.zasdict.data.SafIo
import com.zaslon.zasdict.data.SearchEngine
import com.zaslon.zasdict.data.StorageMode
import com.zaslon.zasdict.data.ZpdicApiClient
import com.zaslon.zasdict.domain.Const
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// ----------------------------------------------------------------------
// エディタ用ドラフト
// ----------------------------------------------------------------------

data class DraftTranslation(val title: String = "名詞", val forms: String = "")
data class DraftContent(val title: String = "", val text: String = "")
data class DraftRelation(val title: String = "関連", val targetId: Int = -1, val targetForm: String = "")
data class DraftVariation(val title: String = "", val form: String = "")

data class DraftExample(
    val originalId: Int? = null,
    val sentence: String = "",
    val translation: String = "",
    val supplement: String = "",
    val tags: String = "",
    val linkedWords: List<Pair<Int, String>> = emptyList(),
    val offerCatalog: String = "self",
    val offerNumber: Int = 0
)

data class EditorDraft(
    val originalId: Int? = null, // null = 新規
    val form: String = "",
    val pronunciation: String = "",
    val translations: List<DraftTranslation> = emptyList(),
    val contents: List<DraftContent> = emptyList(),
    val relations: List<DraftRelation> = emptyList(),
    val tags: String = "",
    val variations: List<DraftVariation> = emptyList()
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val prefs = Prefs(app)
    val store = DictionaryStore()
    val engine = SearchEngine(store)
    val changelog = ChangelogStore(app)

    private val dropboxClient = DropboxApiClient()
    private val githubClient = GitHubApiClient()
    private val boxClient = BoxApiClient()
    private val zpdicClient = ZpdicApiClient()

    // ------------------------------------------------------------------
    // UI状態
    // ------------------------------------------------------------------

    var searchText by mutableStateOf("")
        private set
    var searchMode by mutableStateOf(prefs.searchMode)
        private set
    var searchScope by mutableStateOf(prefs.searchScope)
        private set
    var results by mutableStateOf<List<JSONObject>>(emptyList())
        private set
    var wordCount by mutableStateOf(0)
        private set
    var fileName by mutableStateOf<String?>(null)
        private set
    var hasUnsavedChanges by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
    var editorDraft by mutableStateOf<EditorDraft?>(null)
        private set
    var exampleDraft by mutableStateOf<DraftExample?>(null)
        private set

    /** 更新履歴画面の再読込トリガ（連携・保存のたびに増える） */
    var changelogVersion by mutableStateOf(0)
        private set

    /** 例文リスト再描画トリガ（例文の追加・編集・削除のたびに増える） */
    var examplesVersion by mutableStateOf(0)
        private set

    // ------------------------------------------------------------------
    // ZpDIC Online API 状態
    // ------------------------------------------------------------------

    /** APIキーが登録済みかどうか（キー本体はPrefsにのみ保持する） */
    var zpdicApiKeySet by mutableStateOf(prefs.zpdicApiKey != null)
        private set

    /** 出典照会中フラグ */
    var zpdicOfferFetching by mutableStateOf(false)
        private set

    /** 出典照会のステータスメッセージ（空文字 = 非表示） */
    var zpdicOfferStatus by mutableStateOf("")

    /** 照会結果（編集画面が消費して null にする） */
    var zpdicOfferResult by mutableStateOf<ZpdicApiClient.OfferData?>(null)
        private set

    /** 出典一覧ロード中フラグ */
    var zpdicListLoading by mutableStateOf(false)
        private set

    /** 出典一覧の取得結果 */
    var zpdicListItems by mutableStateOf<List<ZpdicApiClient.OfferListItem>>(emptyList())
        private set

    /** 出典一覧取得のエラーメッセージ */
    var zpdicListError by mutableStateOf<String?>(null)
        private set

    /** 名前を付けて保存で連携が外れた際、新しいCSVの選択を促すダイアログ表示フラグ */
    var promptRelinkChangelog by mutableStateOf(false)
        private set

    fun dismissRelinkPrompt() { promptRelinkChangelog = false }

    // 環境設定
    var fontScale by mutableStateOf(prefs.fontScale)
        private set
    var autoSave by mutableStateOf(prefs.autoSave)
        private set
    var useIdyerFont by mutableStateOf(prefs.idyerFont)
        private set
    var einkMode by mutableStateOf(prefs.einkMode)
        private set
    var idyerFontFamily by mutableStateOf<FontFamily?>(null)
        private set

    private var currentUri: Uri? = null
    private var searchJob: Job? = null

    // ------------------------------------------------------------------
    // Dropbox 状態
    // ------------------------------------------------------------------

    var storageMode by mutableStateOf(prefs.storageMode)
        private set
    var dropboxConnected by mutableStateOf(prefs.dropboxRefreshToken != null)
        private set
    var dropboxDisplayName by mutableStateOf(prefs.dropboxDisplayName)
        private set
    var dropboxDictName by mutableStateOf(prefs.dropboxDictName)
        private set

    /** ローカルキャッシュにDropboxへ未アップロードの変更がある */
    var dropboxHasPendingUpload by mutableStateOf(prefs.dropboxHasPendingUpload)
        private set

    /** "dict" = 辞書ファイル選択中（Dropboxブラウザを表示） */
    var dropboxBrowserTarget by mutableStateOf<String?>(null)
        private set
    var dropboxBrowserEntries by mutableStateOf<List<DropboxApiClient.FileEntry>>(emptyList())
        private set
    var dropboxBrowserPath by mutableStateOf("")
        private set
    var dropboxBrowserLoading by mutableStateOf(false)
        private set
    var dropboxBrowserError by mutableStateOf<String?>(null)
        private set

    // ------------------------------------------------------------------
    // GitHub 状態
    // ------------------------------------------------------------------

    var githubConnected by mutableStateOf(prefs.githubDisplayName != null)
        private set
    var githubDisplayName by mutableStateOf(prefs.githubDisplayName)
        private set
    var githubDictName by mutableStateOf(prefs.githubDictName)
        private set
    var githubHasPendingUpload by mutableStateOf(prefs.githubHasPendingUpload)
        private set

    var githubBrowserTarget by mutableStateOf<String?>(null)
        private set
    var githubBrowserEntries by mutableStateOf<List<GitHubApiClient.FileEntry>>(emptyList())
        private set
    var githubBrowserPath by mutableStateOf("")
        private set
    var githubBrowserLoading by mutableStateOf(false)
        private set
    var githubBrowserError by mutableStateOf<String?>(null)
        private set

    // ------------------------------------------------------------------
    // Box 状態
    // ------------------------------------------------------------------

    var boxConnected by mutableStateOf(prefs.boxRefreshToken != null)
        private set
    var boxDisplayName by mutableStateOf(prefs.boxDisplayName)
        private set
    var boxDictName by mutableStateOf(prefs.boxDictName)
        private set
    var boxHasPendingUpload by mutableStateOf(prefs.boxHasPendingUpload)
        private set

    var boxBrowserTarget by mutableStateOf<String?>(null)
        private set
    var boxBrowserEntries by mutableStateOf<List<BoxApiClient.FileEntry>>(emptyList())
        private set
    var boxBrowserFolderId by mutableStateOf("0")
        private set
    var boxBrowserFolderName by mutableStateOf("Box")
        private set
    var boxBrowserFolderStack by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set
    var boxBrowserLoading by mutableStateOf(false)
        private set
    var boxBrowserError by mutableStateOf<String?>(null)
        private set

    companion object {
        /** MainActivity から OAuth2 コールバックのコードを受け取るチャンネル */
        val pendingDropboxAuthCode = MutableStateFlow<String?>(null)
        /** PKCE フロー中の code_verifier（認可〜トークン交換まで保持） */
        var pkceVerifier: String? = null
        val pendingBoxAuthCode = MutableStateFlow<String?>(null)
        var boxPkceVerifier: String? = null
    }

    init {
        loadIdyerFontFamily()
        restoreLastDictionary()
        viewModelScope.launch {
            pendingDropboxAuthCode.collect { code ->
                if (code != null) {
                    pendingDropboxAuthCode.value = null
                    handleDropboxAuthCode(code)
                }
            }
        }
        viewModelScope.launch {
            pendingBoxAuthCode.collect { code ->
                if (code != null) {
                    pendingBoxAuthCode.value = null
                    handleBoxAuthCode(code)
                }
            }
        }
    }

    fun consumeMessage() { message = null }
    private fun post(msg: String) { message = msg }

    /** 見出し語表示用フォント（Heksa有効時のみカスタムフォント） */
    val headwordFontFamily: FontFamily?
        get() = if (useIdyerFont) idyerFontFamily else null

    // ------------------------------------------------------------------
    // ファイル操作（ローカルモード）
    // ------------------------------------------------------------------

    private fun restoreLastDictionary() {
        when (storageMode) {
            StorageMode.DROPBOX -> { restoreDropboxCache(); return }
            StorageMode.GITHUB -> { restoreGitHubCache(); return }
            StorageMode.BOX -> { restoreBoxCache(); return }
            else -> {}
        }
        val uriString = prefs.lastDictionaryUri ?: return
        val uri = Uri.parse(uriString)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                loadDictionaryFromUri(uri)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    post("前回の辞書を読み込めませんでした: ${e.message}")
                }
            }
        }
    }

    fun openDictionary(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = getApplication<Application>().contentResolver
                try {
                    resolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: SecurityException) { }
                loadDictionaryFromUri(uri)
                prefs.lastDictionaryUri = uri.toString()
                withContext(Dispatchers.Main) {
                    searchText = ""
                    results = emptyList()
                    post("辞書を読み込みました")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { post("読み込みエラー: ${e.message}") }
            }
        }
    }

    private suspend fun loadDictionaryFromUri(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        val text = resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: throw IllegalStateException("ファイルを開けません")
        store.loadFromString(text)
        engine.rebuild()
        currentUri = uri
        changelog.setDictionary(uri.toString())
        val name = queryDisplayName(uri)
        withContext(Dispatchers.Main) {
            fileName = name
            hasUnsavedChanges = false
            wordCount = store.wordCount()
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                return cursor.getString(idx) ?: uri.lastPathSegment ?: "dictionary.json"
            }
        }
        return uri.lastPathSegment ?: "dictionary.json"
    }

    /** 上書き保存。保存先未指定なら false（呼び出し側で「名前を付けて保存」を起動する） */
    fun saveFile(): Boolean {
        val uri = currentUri ?: return false
        saveToUri(uri, updateCurrent = false)
        return true
    }

    /** 名前を付けて保存（CreateDocument の結果を渡す） */
    fun saveAs(uri: Uri) {
        saveToUri(uri, updateCurrent = true)
    }

    private fun saveToUri(uri: Uri, updateCurrent: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = getApplication<Application>().contentResolver
                SafIo.writeText(resolver, uri, store.toJsonString())

                var relinkNeeded = false
                if (updateCurrent) {
                    try {
                        resolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    } catch (_: SecurityException) { }
                    currentUri = uri
                    prefs.lastDictionaryUri = uri.toString()
                    relinkNeeded = changelog.isExternalLinked()
                    changelog.setDictionary(uri.toString(), clearPending = false)
                }
                val flushed = changelog.flush()
                val name = queryDisplayName(uri)
                withContext(Dispatchers.Main) {
                    fileName = name
                    hasUnsavedChanges = false
                    changelogVersion++
                    if (relinkNeeded) promptRelinkChangelog = true
                    post(
                        if (flushed) "保存しました"
                        else "保存しました（更新履歴CSVへの書き込みに失敗。連携先が移動・削除された可能性があります）"
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { post("保存エラー: ${e.message}") }
            }
        }
    }

    private fun autoSaveIfEnabled() {
        when (storageMode) {
            StorageMode.LOCAL -> if (autoSave && currentUri != null) saveFile()
            StorageMode.DROPBOX -> if (autoSave && prefs.dropboxDictPath != null) saveToDropboxLocalCache()
            StorageMode.GITHUB -> if (autoSave && prefs.githubDictPath != null) saveToGitHubLocalCache()
            StorageMode.BOX -> if (autoSave && prefs.boxDictFileId != null) saveToBoxLocalCache()
        }
    }

    // ------------------------------------------------------------------
    // Dropbox 操作
    // ------------------------------------------------------------------

    /** App Key を保存してモードを DROPBOX に切り替える */
    fun updateDropboxAppKey(key: String) {
        prefs.dropboxAppKey = key.trim()
    }

    /** ストレージモードを切り替える */
    fun updateStorageMode(mode: StorageMode) {
        storageMode = mode
        prefs.storageMode = mode
        // 辞書状態をリセット
        store.clear()
        currentUri = null
        fileName = null
        hasUnsavedChanges = false
        wordCount = 0
        results = emptyList()
        searchText = ""
        editorDraft = null
        exampleDraft = null
        changelog.setDictionary(null)
        // 切替先の辞書を自動復元
        restoreLastDictionary()
        post("${when (mode) { StorageMode.LOCAL -> "ローカル"; StorageMode.DROPBOX -> "Dropbox"; StorageMode.GITHUB -> "GitHub"; StorageMode.BOX -> "Box" }}モードに切り替えました")
    }

    /** Dropbox OAuth2 PKCE 認証フローをブラウザで開始する */
    fun launchDropboxAuth(context: Context) {
        val appKey = prefs.dropboxAppKey
        if (appKey.isEmpty()) {
            post("App Key を入力してください")
            return
        }
        val verifier = dropboxClient.generatePkceVerifier()
        pkceVerifier = verifier
        val challenge = dropboxClient.generatePkceChallenge(verifier)
        val url = dropboxClient.buildAuthUrl(appKey, challenge)
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    /** MainActivity から OAuth2 コードを受け取ってトークン交換を行う */
    private fun handleDropboxAuthCode(code: String) {
        val verifier = pkceVerifier ?: run {
            post("認証エラー: PKCE verifier が見つかりません")
            return
        }
        pkceVerifier = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = dropboxClient.exchangeCodeForToken(code, verifier, prefs.dropboxAppKey)
                prefs.dropboxAccessToken = result.accessToken
                if (result.refreshToken != null) prefs.dropboxRefreshToken = result.refreshToken
                prefs.dropboxTokenExpiry = System.currentTimeMillis() + result.expiresIn * 1000L
                val name = dropboxClient.getCurrentAccountName(result.accessToken)
                prefs.dropboxDisplayName = name
                withContext(Dispatchers.Main) {
                    dropboxConnected = true
                    dropboxDisplayName = name
                    post("Dropboxに接続しました${if (name.isNotEmpty()) "（$name）" else ""}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { post("Dropbox接続エラー: ${e.message}") }
            }
        }
    }

    /** Dropbox 接続を解除する */
    fun disconnectDropbox() {
        prefs.dropboxAccessToken = null
        prefs.dropboxRefreshToken = null
        prefs.dropboxTokenExpiry = 0L
        prefs.dropboxDisplayName = null
        prefs.dropboxDictPath = null
        prefs.dropboxDictName = null
        prefs.dropboxChangelogPath = null
        prefs.dropboxHasPendingUpload = false
        dropboxConnected = false
        dropboxDisplayName = null
        dropboxDictName = null
        dropboxHasPendingUpload = false
        post("Dropbox接続を解除しました")
    }

    /** 有効なアクセストークンを返す（必要なら refresh する） */
    private suspend fun getValidAccessToken(): String = withContext(Dispatchers.IO) {
        val token = prefs.dropboxAccessToken
            ?: throw IllegalStateException("Dropboxに接続されていません")
        val refreshToken = prefs.dropboxRefreshToken
            ?: throw IllegalStateException("Dropboxに接続されていません")

        if (System.currentTimeMillis() < prefs.dropboxTokenExpiry - 300_000L) {
            return@withContext token
        }
        val result = dropboxClient.refreshAccessToken(refreshToken, prefs.dropboxAppKey)
        prefs.dropboxAccessToken = result.accessToken
        prefs.dropboxTokenExpiry = System.currentTimeMillis() + result.expiresIn * 1000L
        result.accessToken
    }

    // ------------------------------------------------------------------
    // Dropbox ファイルブラウザ
    // ------------------------------------------------------------------

    fun openDropboxBrowser() {
        if (!dropboxConnected) {
            post("先にDropboxに接続してください")
            return
        }
        dropboxBrowserTarget = "dict"
        dropboxBrowserPath = ""
        loadDropboxFolder("")
    }

    fun openDropboxBrowserForChangelog() {
        if (!dropboxConnected) {
            post("先にDropboxに接続してください")
            return
        }
        val startPath = prefs.dropboxChangelogPath
            ?.substringBeforeLast("/")?.takeIf { it.isNotEmpty() }
            ?: prefs.dropboxDictPath?.substringBeforeLast("/")?.takeIf { it.isNotEmpty() }
            ?: ""
        dropboxBrowserTarget = "changelog"
        dropboxBrowserPath = startPath
        loadDropboxFolder(startPath)
    }

    fun selectDropboxChangelogPath(path: String) {
        prefs.dropboxChangelogPath = path
        dropboxBrowserTarget = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = getValidAccessToken()
                val csvText = dropboxClient.downloadFile(token, path)
                changelog.loadFromText(csvText)
            } catch (_: Exception) { }
            withContext(Dispatchers.Main) {
                changelogVersion++
                post("更新履歴の保存先を変更しました: $path")
            }
        }
    }

    fun selectDropboxChangelogFolder(folderPath: String) {
        val dictBaseName = (prefs.dropboxDictName ?: "dictionary").removeSuffix(".json")
        val csvPath = "$folderPath/${dictBaseName}_changelog.csv"
        prefs.dropboxChangelogPath = csvPath
        dropboxBrowserTarget = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = getValidAccessToken()
                val csvText = dropboxClient.downloadFile(token, csvPath)
                changelog.loadFromText(csvText)
            } catch (_: Exception) { }
            withContext(Dispatchers.Main) {
                changelogVersion++
                post("更新履歴の保存先を設定しました: $csvPath")
            }
        }
    }

    fun dismissDropboxBrowser() {
        dropboxBrowserTarget = null
    }

    fun dropboxNavigateTo(path: String) {
        dropboxBrowserPath = path
        loadDropboxFolder(path)
    }

    fun dropboxNavigateUp() {
        val parent = dropboxBrowserPath.substringBeforeLast("/", "")
        dropboxBrowserPath = parent
        loadDropboxFolder(parent)
    }

    private fun loadDropboxFolder(path: String) {
        dropboxBrowserLoading = true
        dropboxBrowserError = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = getValidAccessToken()
                val entries = dropboxClient.listFolder(token, path)
                withContext(Dispatchers.Main) {
                    dropboxBrowserEntries = entries
                    dropboxBrowserLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    dropboxBrowserError = e.message
                    dropboxBrowserLoading = false
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Dropbox からファイルを開く
    // ------------------------------------------------------------------

    fun openFromDropbox(path: String, name: String) {
        dropboxBrowserTarget = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = getValidAccessToken()
                val text = dropboxClient.downloadFile(token, path)
                store.loadFromString(text)
                engine.rebuild()

                // ローカルキャッシュに書き込む（タスクキル対策）
                dropboxCacheFile().writeText(text)

                // changelog を辞書に紐付ける
                val changelogPath = path.removeSuffix(".json") + "_changelog.csv"
                prefs.dropboxDictPath = path
                prefs.dropboxDictName = name
                prefs.dropboxChangelogPath = changelogPath
                prefs.dropboxHasPendingUpload = false
                changelog.setDictionary("dropbox:$path")
                // Dropbox上に既存のchangelogがあれば読み込む
                try {
                    val csvText = dropboxClient.downloadFile(token, changelogPath)
                    changelog.loadFromText(csvText)
                } catch (_: Exception) { }

                withContext(Dispatchers.Main) {
                    fileName = name
                    dropboxDictName = name
                    hasUnsavedChanges = false
                    wordCount = store.wordCount()
                    dropboxHasPendingUpload = false
                    searchText = ""
                    results = emptyList()
                    changelogVersion++
                    post("Dropboxから辞書を読み込みました")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { post("読み込みエラー: ${e.message}") }
            }
        }
    }

    // ------------------------------------------------------------------
    // Dropbox ローカルキャッシュ保存（タスクキル対策）
    // ------------------------------------------------------------------

    private fun dropboxCacheFile(): File =
        File(getApplication<Application>().filesDir, "dropbox_dict_cache.json")

    /** アプリ強制終了に備えてローカルキャッシュへ保存する */
    private fun saveToDropboxLocalCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dropboxCacheFile().writeText(store.toJsonString())
                changelog.flush()
                prefs.dropboxHasPendingUpload = true
                withContext(Dispatchers.Main) {
                    hasUnsavedChanges = false
                    dropboxHasPendingUpload = true
                }
            } catch (_: Exception) { }
        }
    }

    /** 起動時に Dropbox キャッシュを復元する */
    private fun restoreDropboxCache() {
        val dictPath = prefs.dropboxDictPath ?: return
        val dictName = prefs.dropboxDictName ?: return
        val cache = dropboxCacheFile()
        if (!cache.exists()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                store.loadFromString(cache.readText())
                engine.rebuild()
                changelog.setDictionary("dropbox:$dictPath")
                val hasPending = prefs.dropboxHasPendingUpload
                withContext(Dispatchers.Main) {
                    fileName = dictName
                    dropboxDictName = dictName
                    hasUnsavedChanges = false
                    wordCount = store.wordCount()
                    dropboxHasPendingUpload = hasPending
                    if (hasPending) post("ローカルキャッシュを復元しました（Dropboxへの未同期があります）")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { post("キャッシュ復元エラー: ${e.message}") }
            }
        }
    }

    // ------------------------------------------------------------------
    // Dropbox へアップロード
    // ------------------------------------------------------------------

    /** 辞書 + 更新履歴を Dropbox へアップロードする */
    fun uploadToDropbox() {
        val dictPath = prefs.dropboxDictPath ?: run {
            post("Dropboxの辞書ファイルが選択されていません")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = getValidAccessToken()

                // 辞書 JSON をアップロード
                val dictText = store.toJsonString()
                dropboxClient.uploadFile(token, dictPath, dictText)
                dropboxCacheFile().writeText(dictText)

                // changelog を flush してアップロード
                changelog.flush()
                val changelogText = changelog.exportCsvText()
                val changelogPath = prefs.dropboxChangelogPath
                if (changelogPath != null && changelogText.isNotBlank()) {
                    val csvWithHeader = if (changelogText.startsWith(ChangelogStore.HEADER)) {
                        changelogText
                    } else {
                        ChangelogStore.HEADER + "\n" + changelogText
                    }
                    dropboxClient.uploadFile(token, changelogPath, csvWithHeader)
                }

                prefs.dropboxHasPendingUpload = false
                withContext(Dispatchers.Main) {
                    hasUnsavedChanges = false
                    dropboxHasPendingUpload = false
                    changelogVersion++
                    post("Dropboxに保存しました")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { post("Dropboxへの保存エラー: ${e.message}") }
            }
        }
    }

    /** Dropbox 上の最新ファイルを再読み込みする */
    fun reloadFromDropbox() {
        val dictPath = prefs.dropboxDictPath ?: run {
            post("Dropboxの辞書ファイルが選択されていません")
            return
        }
        val dictName = prefs.dropboxDictName ?: return
        openFromDropbox(dictPath, dictName)
    }

    // ------------------------------------------------------------------
    // GitHub 操作
    // ------------------------------------------------------------------

    /** トークン・オーナー・リポジトリを保存して接続を確認する */
    fun connectGitHub(token: String, owner: String, repo: String, branch: String) {
        val t = token.trim(); val o = owner.trim(); val r = repo.trim(); val b = branch.trim().ifEmpty { "main" }
        if (t.isEmpty() || o.isEmpty() || r.isEmpty()) {
            post("トークン、オーナー、リポジトリを入力してください")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val name = githubClient.getCurrentUser(t)
                prefs.githubToken = t
                prefs.githubOwner = o
                prefs.githubRepo = r
                prefs.githubBranch = b
                prefs.githubDisplayName = name.ifEmpty { o }
                withContext(Dispatchers.Main) {
                    githubConnected = true
                    githubDisplayName = prefs.githubDisplayName
                    post("GitHubに接続しました（$o/$r）")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { post("GitHub接続エラー: ${e.message}") }
            }
        }
    }

    /** GitHub 接続を解除する */
    fun disconnectGitHub() {
        prefs.githubToken = null
        prefs.githubOwner = null
        prefs.githubRepo = null
        prefs.githubDisplayName = null
        prefs.githubDictPath = null
        prefs.githubDictName = null
        prefs.githubChangelogPath = null
        prefs.githubHasPendingUpload = false
        githubConnected = false
        githubDisplayName = null
        githubDictName = null
        githubHasPendingUpload = false
        post("GitHub接続を解除しました")
    }

    // ------------------------------------------------------------------
    // GitHub ファイルブラウザ
    // ------------------------------------------------------------------

    fun openGitHubBrowser() {
        if (!githubConnected) { post("先にGitHubに接続してください"); return }
        githubBrowserTarget = "dict"
        githubBrowserPath = ""
        loadGitHubFolder("")
    }

    fun openGitHubBrowserForChangelog() {
        if (!githubConnected) { post("先にGitHubに接続してください"); return }
        val startPath = prefs.githubChangelogPath
            ?.substringBeforeLast("/")?.takeIf { it.isNotEmpty() }
            ?: prefs.githubDictPath?.substringBeforeLast("/")?.takeIf { it.isNotEmpty() }
            ?: ""
        githubBrowserTarget = "changelog"
        githubBrowserPath = startPath
        loadGitHubFolder(startPath)
    }

    fun selectGitHubChangelogPath(path: String) {
        prefs.githubChangelogPath = path
        githubBrowserTarget = null
        val token = prefs.githubToken ?: return
        val owner = prefs.githubOwner ?: return
        val repo = prefs.githubRepo ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = githubClient.getFileContent(token, owner, repo, path)
                changelog.loadFromText(content.text)
            } catch (_: Exception) { }
            withContext(Dispatchers.Main) {
                changelogVersion++
                post("更新履歴の保存先を変更しました: $path")
            }
        }
    }

    fun selectGitHubChangelogFolder(folderPath: String) {
        val dictBaseName = (prefs.githubDictName ?: "dictionary").removeSuffix(".json")
        val csvPath = if (folderPath.isEmpty()) "${dictBaseName}_changelog.csv"
                      else "$folderPath/${dictBaseName}_changelog.csv"
        prefs.githubChangelogPath = csvPath
        githubBrowserTarget = null
        val token = prefs.githubToken ?: run {
            viewModelScope.launch(Dispatchers.Main) {
                changelogVersion++
                post("更新履歴の保存先を設定しました: $csvPath")
            }
            return
        }
        val owner = prefs.githubOwner ?: return
        val repo = prefs.githubRepo ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = githubClient.getFileContent(token, owner, repo, csvPath)
                changelog.loadFromText(content.text)
            } catch (_: Exception) { }
            withContext(Dispatchers.Main) {
                changelogVersion++
                post("更新履歴の保存先を設定しました: $csvPath")
            }
        }
    }

    fun dismissGitHubBrowser() { githubBrowserTarget = null }

    fun gitHubNavigateTo(path: String) {
        githubBrowserPath = path
        loadGitHubFolder(path)
    }

    fun gitHubNavigateUp() {
        val parent = githubBrowserPath.substringBeforeLast("/", "")
        githubBrowserPath = parent
        loadGitHubFolder(parent)
    }

    private fun loadGitHubFolder(path: String) {
        val token = prefs.githubToken ?: return
        val owner = prefs.githubOwner ?: return
        val repo = prefs.githubRepo ?: return
        githubBrowserLoading = true
        githubBrowserError = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entries = githubClient.getContents(token, owner, repo, path)
                withContext(Dispatchers.Main) {
                    githubBrowserEntries = entries
                    githubBrowserLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    githubBrowserError = e.message
                    githubBrowserLoading = false
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // GitHub からファイルを開く
    // ------------------------------------------------------------------

    fun openFromGitHub(path: String, name: String) {
        githubBrowserTarget = null
        val token = prefs.githubToken ?: run { post("GitHubに接続されていません"); return }
        val owner = prefs.githubOwner ?: return
        val repo = prefs.githubRepo ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileContent = githubClient.getFileContent(token, owner, repo, path)
                store.loadFromString(fileContent.text)
                engine.rebuild()

                gitHubCacheFile().writeText(fileContent.text)

                val changelogPath = path.removeSuffix(".json") + "_changelog.csv"
                prefs.githubDictPath = path
                prefs.githubDictName = name
                prefs.githubChangelogPath = changelogPath
                prefs.githubHasPendingUpload = false
                changelog.setDictionary("github:$path")

                try {
                    val csvContent = githubClient.getFileContent(token, owner, repo, changelogPath)
                    changelog.loadFromText(csvContent.text)
                } catch (_: Exception) { }

                withContext(Dispatchers.Main) {
                    fileName = name
                    githubDictName = name
                    hasUnsavedChanges = false
                    wordCount = store.wordCount()
                    githubHasPendingUpload = false
                    searchText = ""
                    results = emptyList()
                    changelogVersion++
                    post("GitHubから辞書を読み込みました")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { post("読み込みエラー: ${e.message}") }
            }
        }
    }

    // ------------------------------------------------------------------
    // GitHub ローカルキャッシュ
    // ------------------------------------------------------------------

    private fun gitHubCacheFile(): File =
        File(getApplication<Application>().filesDir, "github_dict_cache.json")

    private fun saveToGitHubLocalCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                gitHubCacheFile().writeText(store.toJsonString())
                changelog.flush()
                prefs.githubHasPendingUpload = true
                withContext(Dispatchers.Main) {
                    hasUnsavedChanges = false
                    githubHasPendingUpload = true
                }
            } catch (_: Exception) { }
        }
    }

    private fun restoreGitHubCache() {
        val dictPath = prefs.githubDictPath ?: return
        val dictName = prefs.githubDictName ?: return
        val cache = gitHubCacheFile()
        if (!cache.exists()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                store.loadFromString(cache.readText())
                engine.rebuild()
                changelog.setDictionary("github:$dictPath")
                val hasPending = prefs.githubHasPendingUpload
                withContext(Dispatchers.Main) {
                    fileName = dictName
                    githubDictName = dictName
                    hasUnsavedChanges = false
                    wordCount = store.wordCount()
                    githubHasPendingUpload = hasPending
                    if (hasPending) post("ローカルキャッシュを復元しました（GitHubへの未同期があります）")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { post("キャッシュ復元エラー: ${e.message}") }
            }
        }
    }

    // ------------------------------------------------------------------
    // GitHub へアップロード（コミット作成）
    // ------------------------------------------------------------------

    /** 辞書 + 更新履歴を GitHub へ1コミットとしてまとめてアップロードする */
    fun uploadToGitHub(commitMessage: String = "Update Dictionary (Android)") {
        val dictPath = prefs.githubDictPath ?: run { post("GitHubの辞書ファイルが選択されていません"); return }
        val token = prefs.githubToken ?: run { post("GitHubに接続されていません"); return }
        val owner = prefs.githubOwner ?: return
        val repo = prefs.githubRepo ?: return
        val branch = prefs.githubBranch
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dictText = store.toJsonString()

                changelog.flush()
                val changelogText = changelog.exportCsvText()
                val changelogPath = prefs.githubChangelogPath

                // 辞書と更新履歴を1コミットにまとめる（Git Data API）
                val filesToCommit = mutableListOf(GitHubApiClient.FileChange(dictPath, dictText))
                if (changelogPath != null && changelogText.isNotBlank()) {
                    val csvWithHeader = if (changelogText.startsWith(ChangelogStore.HEADER)) changelogText
                                        else ChangelogStore.HEADER + "\n" + changelogText
                    filesToCommit.add(GitHubApiClient.FileChange(changelogPath, csvWithHeader))
                }

                githubClient.commitFiles(token, owner, repo, branch, filesToCommit, commitMessage)
                gitHubCacheFile().writeText(dictText)

                prefs.githubHasPendingUpload = false
                withContext(Dispatchers.Main) {
                    hasUnsavedChanges = false
                    githubHasPendingUpload = false
                    changelogVersion++
                    post("GitHubにコミットしました")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { post("GitHubへの保存エラー: ${e.message}") }
            }
        }
    }

    /** GitHub 上の最新ファイルを再読み込みする */
    fun reloadFromGitHub() {
        val dictPath = prefs.githubDictPath ?: run { post("GitHubの辞書ファイルが選択されていません"); return }
        val dictName = prefs.githubDictName ?: return
        openFromGitHub(dictPath, dictName)
    }

    // ------------------------------------------------------------------
    // Box 操作
    // ------------------------------------------------------------------

    fun updateBoxClientId(id: String) { prefs.boxClientId = id.trim() }
    fun updateBoxClientSecret(secret: String) { prefs.boxClientSecret = secret.trim() }

    fun launchBoxAuth(context: Context) {
        val clientId = prefs.boxClientId
        if (clientId.isEmpty()) { post("Client IDを入力してください"); return }
        val verifier = boxClient.generatePkceVerifier()
        boxPkceVerifier = verifier
        val challenge = boxClient.generatePkceChallenge(verifier)
        val url = boxClient.buildAuthUrl(clientId, challenge)
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun handleBoxAuthCode(code: String) {
        val verifier = boxPkceVerifier ?: run { post("認証エラー: PKCE verifierが見つかりません"); return }
        boxPkceVerifier = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = boxClient.exchangeCodeForToken(code, verifier, prefs.boxClientId, prefs.boxClientSecret)
                prefs.boxAccessToken = result.accessToken
                if (result.refreshToken != null) prefs.boxRefreshToken = result.refreshToken
                prefs.boxTokenExpiry = System.currentTimeMillis() + result.expiresIn * 1000L
                val name = boxClient.getCurrentUser(result.accessToken)
                prefs.boxDisplayName = name
                withContext(Dispatchers.Main) {
                    boxConnected = true
                    boxDisplayName = name
                    post("Boxに接続しました${if (name.isNotEmpty()) "（$name）" else ""}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { post("Box接続エラー: ${e.message}") }
            }
        }
    }

    fun disconnectBox() {
        prefs.boxAccessToken = null
        prefs.boxRefreshToken = null
        prefs.boxTokenExpiry = 0L
        prefs.boxDisplayName = null
        prefs.boxDictFileId = null
        prefs.boxDictFolderId = null
        prefs.boxDictName = null
        prefs.boxChangelogFileId = null
        prefs.boxChangelogFolderId = null
        prefs.boxHasPendingUpload = false
        boxConnected = false
        boxDisplayName = null
        boxDictName = null
        boxHasPendingUpload = false
        post("Box接続を解除しました")
    }

    private suspend fun getValidBoxAccessToken(): String = withContext(Dispatchers.IO) {
        val token = prefs.boxAccessToken ?: throw IllegalStateException("Boxに接続されていません")
        val refreshToken = prefs.boxRefreshToken ?: throw IllegalStateException("Boxに接続されていません")
        if (System.currentTimeMillis() < prefs.boxTokenExpiry - 300_000L) return@withContext token
        val result = boxClient.refreshAccessToken(refreshToken, prefs.boxClientId, prefs.boxClientSecret)
        prefs.boxAccessToken = result.accessToken
        if (result.refreshToken != null) prefs.boxRefreshToken = result.refreshToken
        prefs.boxTokenExpiry = System.currentTimeMillis() + result.expiresIn * 1000L
        result.accessToken
    }

    // ------------------------------------------------------------------
    // Box ファイルブラウザ
    // ------------------------------------------------------------------

    fun openBoxBrowser() {
        if (!boxConnected) { post("先にBoxに接続してください"); return }
        boxBrowserTarget = "dict"
        boxBrowserFolderStack = emptyList()
        boxBrowserFolderId = "0"
        boxBrowserFolderName = "Box"
        loadBoxFolder("0")
    }

    fun openBoxBrowserForChangelog() {
        if (!boxConnected) { post("先にBoxに接続してください"); return }
        val startId = prefs.boxDictFolderId ?: "0"
        val startName = if (startId == "0") "Box" else "辞書フォルダ"
        boxBrowserTarget = "changelog"
        boxBrowserFolderStack = emptyList()
        boxBrowserFolderId = startId
        boxBrowserFolderName = startName
        loadBoxFolder(startId)
    }

    fun selectBoxChangelogFile(fileId: String, fileName: String) {
        prefs.boxChangelogFileId = fileId
        boxBrowserTarget = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = getValidBoxAccessToken()
                val content = boxClient.downloadFile(token, fileId)
                changelog.loadFromText(content)
            } catch (_: Exception) { }
            withContext(Dispatchers.Main) {
                changelogVersion++
                post("更新履歴の保存先を変更しました: $fileName")
            }
        }
    }

    fun selectBoxChangelogFolder(folderId: String, folderName: String) {
        boxBrowserTarget = null
        val dictBaseName = (prefs.boxDictName ?: "dictionary").removeSuffix(".json")
        val csvName = "${dictBaseName}_changelog.csv"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = getValidBoxAccessToken()
                val fileId = boxClient.findFileInFolder(token, folderId, csvName)
                if (fileId != null) {
                    try {
                        val content = boxClient.downloadFile(token, fileId)
                        changelog.loadFromText(content)
                    } catch (_: Exception) { }
                }
                prefs.boxChangelogFileId = fileId
                prefs.boxChangelogFolderId = folderId
            } catch (_: Exception) { }
            withContext(Dispatchers.Main) {
                changelogVersion++
                post("更新履歴の保存先を設定しました: $folderName/$csvName")
            }
        }
    }

    fun dismissBoxBrowser() { boxBrowserTarget = null }

    fun boxNavigateTo(id: String, name: String) {
        boxBrowserFolderStack = boxBrowserFolderStack + (boxBrowserFolderId to boxBrowserFolderName)
        boxBrowserFolderId = id
        boxBrowserFolderName = name
        loadBoxFolder(id)
    }

    fun boxNavigateUp() {
        val parent = boxBrowserFolderStack.lastOrNull() ?: return
        boxBrowserFolderStack = boxBrowserFolderStack.dropLast(1)
        boxBrowserFolderId = parent.first
        boxBrowserFolderName = parent.second
        loadBoxFolder(parent.first)
    }

    private fun loadBoxFolder(folderId: String) {
        boxBrowserLoading = true
        boxBrowserError = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = getValidBoxAccessToken()
                val entries = boxClient.listFolder(token, folderId)
                withContext(Dispatchers.Main) {
                    boxBrowserEntries = entries
                    boxBrowserLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    boxBrowserError = e.message
                    boxBrowserLoading = false
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Box からファイルを開く
    // ------------------------------------------------------------------

    fun openFromBox(fileId: String, name: String) {
        val folderId = boxBrowserFolderId
        boxBrowserTarget = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = getValidBoxAccessToken()
                val text = boxClient.downloadFile(token, fileId)
                store.loadFromString(text)
                engine.rebuild()
                boxCacheFile().writeText(text)

                val dictBaseName = name.removeSuffix(".json")
                val csvName = "${dictBaseName}_changelog.csv"
                val changelogFileId = boxClient.findFileInFolder(token, folderId, csvName)

                prefs.boxDictFileId = fileId
                prefs.boxDictFolderId = folderId
                prefs.boxDictName = name
                prefs.boxChangelogFileId = changelogFileId
                prefs.boxChangelogFolderId = folderId
                prefs.boxHasPendingUpload = false
                changelog.setDictionary("box:$fileId")

                if (changelogFileId != null) {
                    try {
                        val csvText = boxClient.downloadFile(token, changelogFileId)
                        changelog.loadFromText(csvText)
                    } catch (_: Exception) { }
                }

                withContext(Dispatchers.Main) {
                    fileName = name
                    boxDictName = name
                    hasUnsavedChanges = false
                    wordCount = store.wordCount()
                    boxHasPendingUpload = false
                    searchText = ""
                    results = emptyList()
                    changelogVersion++
                    post("Boxから辞書を読み込みました")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { post("読み込みエラー: ${e.message}") }
            }
        }
    }

    // ------------------------------------------------------------------
    // Box ローカルキャッシュ
    // ------------------------------------------------------------------

    private fun boxCacheFile(): File =
        File(getApplication<Application>().filesDir, "box_dict_cache.json")

    private fun saveToBoxLocalCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                boxCacheFile().writeText(store.toJsonString())
                changelog.flush()
                prefs.boxHasPendingUpload = true
                withContext(Dispatchers.Main) {
                    hasUnsavedChanges = false
                    boxHasPendingUpload = true
                }
            } catch (_: Exception) { }
        }
    }

    private fun restoreBoxCache() {
        val dictFileId = prefs.boxDictFileId ?: return
        val dictName = prefs.boxDictName ?: return
        val cache = boxCacheFile()
        if (!cache.exists()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                store.loadFromString(cache.readText())
                engine.rebuild()
                changelog.setDictionary("box:$dictFileId")
                val hasPending = prefs.boxHasPendingUpload
                withContext(Dispatchers.Main) {
                    fileName = dictName
                    boxDictName = dictName
                    hasUnsavedChanges = false
                    wordCount = store.wordCount()
                    boxHasPendingUpload = hasPending
                    if (hasPending) post("ローカルキャッシュを復元しました（Boxへの未同期があります）")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { post("キャッシュ復元エラー: ${e.message}") }
            }
        }
    }

    // ------------------------------------------------------------------
    // Box へアップロード
    // ------------------------------------------------------------------

    fun uploadToBox() {
        val dictFileId = prefs.boxDictFileId ?: run { post("Boxの辞書ファイルが選択されていません"); return }
        val dictName = prefs.boxDictName ?: return
        val dictFolderId = prefs.boxDictFolderId ?: "0"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = getValidBoxAccessToken()
                val dictText = store.toJsonString()

                boxClient.updateFile(token, dictFileId, dictName, dictText)
                boxCacheFile().writeText(dictText)

                changelog.flush()
                val changelogText = changelog.exportCsvText()
                if (changelogText.isNotBlank()) {
                    val csvWithHeader = if (changelogText.startsWith(ChangelogStore.HEADER)) changelogText
                                        else ChangelogStore.HEADER + "\n" + changelogText
                    val dictBaseName = dictName.removeSuffix(".json")
                    val csvName = "${dictBaseName}_changelog.csv"
                    val changelogFileId = prefs.boxChangelogFileId
                    val changelogFolderId = prefs.boxChangelogFolderId ?: dictFolderId
                    if (changelogFileId != null) {
                        boxClient.updateFile(token, changelogFileId, csvName, csvWithHeader)
                    } else {
                        val newId = boxClient.uploadNewFile(token, changelogFolderId, csvName, csvWithHeader)
                        prefs.boxChangelogFileId = newId
                    }
                }

                prefs.boxHasPendingUpload = false
                withContext(Dispatchers.Main) {
                    hasUnsavedChanges = false
                    boxHasPendingUpload = false
                    changelogVersion++
                    post("Boxに保存しました")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { post("Boxへの保存エラー: ${e.message}") }
            }
        }
    }

    fun reloadFromBox() {
        val dictFileId = prefs.boxDictFileId ?: run { post("Boxの辞書ファイルが選択されていません"); return }
        val dictName = prefs.boxDictName ?: return
        val folderId = prefs.boxDictFolderId ?: "0"
        boxBrowserFolderId = folderId
        openFromBox(dictFileId, dictName)
    }

    // ------------------------------------------------------------------
    // 検索
    // ------------------------------------------------------------------

    fun onSearchTextChanged(text: String) {
        searchText = text
        runSearch()
    }

    fun onSearchModeChanged(mode: String) {
        searchMode = mode
        prefs.searchMode = mode
        if (searchText.isNotEmpty()) runSearch()
    }

    fun onSearchScopeChanged(scope: String) {
        searchScope = scope
        prefs.searchScope = scope
        if (searchText.isNotEmpty()) runSearch()
    }

    private fun runSearch() {
        searchJob?.cancel()
        val text = searchText
        if (text.isEmpty()) {
            results = emptyList()
            return
        }
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            delay(80)
            val r = engine.search(searchMode, searchScope, text)
            withContext(Dispatchers.Main) { results = r }
        }
    }

    private fun refreshAfterDataChange() {
        engine.rebuild()
        wordCount = store.wordCount()
        hasUnsavedChanges = true
        if (searchText.isNotEmpty()) runSearch()
        autoSaveIfEnabled()
    }

    fun wordById(id: Int): JSONObject? = engine.lookup(id)

    /** 関係先選択などのための簡易前方一致検索 */
    fun searchFormsForPicker(query: String, limit: Int = 50): List<JSONObject> {
        val q = query.lowercase()
        val list = engine.allWords().filter {
            q.isEmpty() || DictionaryStore.formOf(it).lowercase().startsWith(q)
        }
        return list.sortedWith { a, b ->
            com.zaslon.zasdict.domain.TextProcessor.compareForms(
                DictionaryStore.formOf(a), DictionaryStore.formOf(b)
            )
        }.take(limit)
    }

    // ------------------------------------------------------------------
    // エディタ
    // ------------------------------------------------------------------

    fun startNewEntry(initialForm: String = "") {
        if (!store.isLoaded) {
            post("先に辞書ファイルを開いてください。")
            return
        }
        editorDraft = EditorDraft(
            originalId = null,
            form = initialForm.trim(),
            translations = listOf(DraftTranslation())
        )
    }

    fun startEditEntry(id: Int) {
        val word = engine.lookup(id) ?: return
        editorDraft = draftFrom(word, keepId = true)
    }

    fun startDuplicateEntry(id: Int) {
        val word = engine.lookup(id) ?: return
        editorDraft = draftFrom(word, keepId = false)
    }

    private fun draftFrom(word: JSONObject, keepId: Boolean): EditorDraft {
        val pron = DictionaryStore.pronunciationOf(word) ?: ""
        return EditorDraft(
            originalId = if (keepId) DictionaryStore.idOf(word) else null,
            form = DictionaryStore.formOf(word),
            pronunciation = pron,
            translations = DictionaryStore.translationsOf(word).map { (title, forms) ->
                DraftTranslation(title, forms.joinToString(", "))
            },
            contents = DictionaryStore.contentsOf(word)
                .filter { it.first != Const.PRONUNCIATION_TITLE }
                .map { DraftContent(it.first, it.second) },
            relations = DictionaryStore.relationsOf(word).map { (title, tid, tform) ->
                DraftRelation(title, tid, tform)
            },
            tags = DictionaryStore.tagsOf(word).joinToString(", "),
            variations = DictionaryStore.variationsOf(word).map { DraftVariation(it.first, it.second) }
        )
    }

    fun discardDraft() { editorDraft = null }

    /** ドラフトを辞書へ反映（対照関係の自動登録を含む） */
    fun commitDraft(draft: EditorDraft): Boolean {
        val form = draft.form.trim()
        if (form.isEmpty()) {
            post("見出し語を入力してください。")
            return false
        }

        val isNew = draft.originalId == null
        val id = draft.originalId ?: (store.maxId() + 1)

        val oldRelations: List<Triple<String, Int, String>> =
            if (isNew) emptyList()
            else engine.lookup(id)?.let { DictionaryStore.relationsOf(it) } ?: emptyList()

        val contentsArr = JSONArray()
        if (draft.pronunciation.trim().isNotEmpty()) {
            contentsArr.put(JSONObject().put("title", Const.PRONUNCIATION_TITLE).put("text", draft.pronunciation.trim()))
        }
        for (c in draft.contents) {
            if (c.text.trim().isEmpty()) continue
            contentsArr.put(JSONObject().put("title", c.title.trim()).put("text", c.text))
        }

        val translationsArr = JSONArray()
        for (t in draft.translations) {
            val forms = t.forms.split(",", "、").map { it.trim() }.filter { it.isNotEmpty() }
            if (forms.isEmpty()) continue
            translationsArr.put(
                JSONObject().put("title", t.title).put("forms", JSONArray(forms))
            )
        }

        val relationsArr = JSONArray()
        val newRelations = draft.relations.filter { it.targetId >= 0 && it.targetForm.isNotEmpty() }
        for (r in newRelations) {
            relationsArr.put(
                JSONObject().put("title", r.title).put(
                    "entry", JSONObject().put("id", r.targetId).put("form", r.targetForm)
                )
            )
        }

        val tagsArr = JSONArray(draft.tags.split(",", "、").map { it.trim() }.filter { it.isNotEmpty() })

        val variationsArr = JSONArray()
        for (v in draft.variations) {
            if (v.form.trim().isEmpty()) continue
            variationsArr.put(JSONObject().put("title", v.title.trim()).put("form", v.form.trim()))
        }

        if (isNew) {
            val word = JSONObject()
            word.put("entry", JSONObject().put("id", id).put("form", form))
            word.put("translations", translationsArr)
            word.put("tags", tagsArr)
            word.put("contents", contentsArr)
            word.put("variations", variationsArr)
            word.put("relations", relationsArr)
            store.addWord(word)
            changelog.addEntry("ADD", form)
        } else {
            val word = engine.lookup(id) ?: run {
                post("対象の単語が見つかりません。")
                return false
            }
            val oldForm = DictionaryStore.formOf(word)
            word.optJSONObject("entry")?.put("form", form)
            word.put("translations", translationsArr)
            word.put("tags", tagsArr)
            word.put("contents", contentsArr)
            word.put("variations", variationsArr)
            word.put("relations", relationsArr)
            if (oldForm != form) updateRelationFormsEverywhere(id, form)
            changelog.addEntry("CHANGE", form, if (oldForm != form) "旧: $oldForm" else "")
        }

        syncReciprocalRelations(
            selfId = id,
            selfForm = form,
            old = oldRelations.map { Pair(it.first, it.second) }.toSet(),
            new = newRelations.map { Pair(it.title, it.targetId) }.toSet(),
            newRelationList = newRelations
        )

        editorDraft = null
        refreshAfterDataChange()
        return true
    }

    private fun updateRelationFormsEverywhere(targetId: Int, newForm: String) {
        for (word in store.wordList()) {
            val rels = word.optJSONArray("relations") ?: continue
            for (i in 0 until rels.length()) {
                val r = rels.optJSONObject(i) ?: continue
                val e = r.optJSONObject("entry") ?: continue
                if (e.optInt("id", -1) == targetId) e.put("form", newForm)
            }
        }
    }

    private fun syncReciprocalRelations(
        selfId: Int,
        selfForm: String,
        old: Set<Pair<String, Int>>,
        new: Set<Pair<String, Int>>,
        newRelationList: List<DraftRelation>
    ) {
        for ((title, targetId) in new - old) {
            val reciprocal = Const.RECIPROCAL_MAP[title] ?: continue
            val target = engine.lookup(targetId) ?: store.findById(targetId) ?: continue
            val rels = target.optJSONArray("relations") ?: JSONArray().also { target.put("relations", it) }
            var exists = false
            for (i in 0 until rels.length()) {
                val r = rels.optJSONObject(i) ?: continue
                if (r.optString("title") == reciprocal &&
                    r.optJSONObject("entry")?.optInt("id", -1) == selfId
                ) { exists = true; break }
            }
            if (!exists) {
                rels.put(
                    JSONObject().put("title", reciprocal).put(
                        "entry", JSONObject().put("id", selfId).put("form", selfForm)
                    )
                )
            }
        }
        for ((title, targetId) in old - new) {
            val reciprocal = Const.RECIPROCAL_MAP[title] ?: continue
            val target = engine.lookup(targetId) ?: store.findById(targetId) ?: continue
            val rels = target.optJSONArray("relations") ?: continue
            var i = 0
            while (i < rels.length()) {
                val r = rels.optJSONObject(i)
                if (r != null &&
                    r.optString("title") == reciprocal &&
                    r.optJSONObject("entry")?.optInt("id", -1) == selfId
                ) {
                    rels.remove(i)
                } else i++
            }
        }
    }

    // ------------------------------------------------------------------
    // 例文エディタ
    // ------------------------------------------------------------------

    fun startNewExample() {
        if (!store.isLoaded) {
            post("先に辞書ファイルを開いてください。")
            return
        }
        exampleDraft = DraftExample()
    }

    fun startEditExample(id: Int) {
        val arr = store.examples
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            if (e.optInt("id", -1) == id) {
                exampleDraft = draftFromExample(e)
                return
            }
        }
    }

    private fun draftFromExample(e: JSONObject): DraftExample {
        val words = e.optJSONArray("words")
        val linkedWords = if (words != null) {
            (0 until words.length()).mapNotNull { i ->
                val wid = words.optJSONObject(i)?.optInt("id", -1) ?: return@mapNotNull null
                if (wid < 0) return@mapNotNull null
                val form = store.findById(wid)?.let { DictionaryStore.formOf(it) } ?: "ID:$wid"
                Pair(wid, form)
            }
        } else emptyList()
        val offer = e.optJSONObject("offer")
        return DraftExample(
            originalId = e.optInt("id", -1).takeIf { it >= 0 },
            sentence = e.optString("sentence", ""),
            translation = e.optString("translation", ""),
            supplement = e.optString("supplement", ""),
            tags = e.optJSONArray("tags")?.let { ta ->
                (0 until ta.length()).mapNotNull { ta.optString(it, null) }.joinToString(", ")
            } ?: "",
            linkedWords = linkedWords,
            offerCatalog = offer?.optString("catalog", Const.EXAMPLE_CATALOG_SELF) ?: Const.EXAMPLE_CATALOG_SELF,
            offerNumber = offer?.optInt("number", 0) ?: 0
        )
    }

    fun discardExampleDraft() { exampleDraft = null }

    fun commitExampleDraft(draft: DraftExample): Boolean {
        val sentence = draft.sentence.trim()
        if (sentence.isEmpty()) {
            post("「文」を入力してください。")
            return false
        }
        val isNew = draft.originalId == null
        val id = draft.originalId ?: (store.maxExampleId() + 1)

        val wordsArr = JSONArray()
        for ((wid, _) in draft.linkedWords) wordsArr.put(JSONObject().put("id", wid))

        val tagsArr = JSONArray(
            draft.tags.split(",", "、").map { it.trim() }.filter { it.isNotEmpty() }
        )

        val obj = JSONObject()
            .put("id", id)
            .put("sentence", sentence)
            .put("translation", draft.translation.trim())
            .put("supplement", draft.supplement.trim())
            .put("tags", tagsArr)
            .put("words", wordsArr)
            .put("offer", JSONObject()
                .put("catalog", draft.offerCatalog)
                .put("number", draft.offerNumber))

        if (isNew) {
            store.addExample(obj)
        } else {
            store.updateExample(id, obj)
        }

        exampleDraft = null
        examplesVersion++
        refreshAfterDataChange()
        return true
    }

    fun deleteExample(id: Int) {
        store.removeExampleById(id)
        examplesVersion++
        refreshAfterDataChange()
        post("例文を削除しました")
    }

    fun exampleList(): List<JSONObject> = store.exampleList()

    // ------------------------------------------------------------------
    // ZpDIC Online API 操作
    // ------------------------------------------------------------------

    fun saveZpdicApiKey(key: String) {
        val trimmed = key.trim()
        prefs.zpdicApiKey = trimmed.ifEmpty { null }
        zpdicApiKeySet = trimmed.isNotEmpty()
        if (trimmed.isNotEmpty()) post("ZpDIC APIキーを保存しました")
    }

    fun clearZpdicApiKey() {
        prefs.zpdicApiKey = null
        zpdicApiKeySet = false
        post("ZpDIC APIキーを削除しました")
    }

    fun consumeZpdicOfferResult() { zpdicOfferResult = null }

    /** 出典を1件照会して結果を zpdicOfferResult に設定する */
    fun fetchZpdicOffer(catalog: String, number: Int) {
        val apiKey = prefs.zpdicApiKey ?: run {
            zpdicOfferStatus = "APIキーが設定されていません（環境設定で登録してください）"
            return
        }
        if (catalog.trim().isEmpty()) {
            zpdicOfferStatus = "カタログ名を入力してください"
            return
        }
        if (number <= 0) {
            zpdicOfferStatus = "No. を入力してください"
            return
        }
        zpdicOfferFetching = true
        zpdicOfferStatus = "照会中..."
        viewModelScope.launch(Dispatchers.IO) {
            val result = zpdicClient.fetchOffer(catalog.trim(), number, apiKey)
            withContext(Dispatchers.Main) {
                zpdicOfferFetching = false
                when (result) {
                    is ZpdicApiClient.Result.Success -> {
                        zpdicOfferResult = result.data
                        val d = result.data
                        zpdicOfferStatus = if (d.author.isNotEmpty()) "照会成功（作者: ${d.author}）" else "照会成功"
                    }
                    is ZpdicApiClient.Result.Failure -> {
                        zpdicOfferStatus = errorMessage(result.error, number)
                        if (result.error == "auth_failed" || result.error == "api_key_non_ascii") {
                            prefs.zpdicApiKey = null
                            zpdicApiKeySet = false
                        }
                        if (result.error == "not_found") {
                            zpdicOfferResult = ZpdicApiClient.OfferData("", "", "")
                        }
                    }
                }
            }
        }
    }

    /** 出典一覧を取得して zpdicListItems に設定する */
    fun loadZpdicOfferList(catalog: String, offset: Int = 0, limit: Int = 50) {
        val apiKey = prefs.zpdicApiKey ?: run {
            zpdicListError = "APIキーが設定されていません（環境設定で登録してください）"
            return
        }
        if (catalog.trim().isEmpty()) {
            zpdicListError = "カタログ名を入力してください"
            return
        }
        zpdicListLoading = true
        zpdicListError = null
        if (offset == 0) zpdicListItems = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            val result = zpdicClient.fetchOfferList(catalog.trim(), offset, limit, apiKey)
            withContext(Dispatchers.Main) {
                zpdicListLoading = false
                when (result) {
                    is ZpdicApiClient.Result.Success -> {
                        zpdicListItems = if (offset == 0) result.data
                                         else zpdicListItems + result.data
                    }
                    is ZpdicApiClient.Result.Failure -> {
                        zpdicListError = errorMessage(result.error, null)
                        if (result.error == "auth_failed" || result.error == "api_key_non_ascii") {
                            prefs.zpdicApiKey = null
                            zpdicApiKeySet = false
                        }
                    }
                }
            }
        }
    }

    fun clearZpdicList() {
        zpdicListItems = emptyList()
        zpdicListError = null
    }

    private fun errorMessage(error: String, number: Int?): String = when (error) {
        "bad_request" -> "HTTP 400: リクエストの内容が誤っています"
        "auth_failed" -> "HTTP 401: APIキーが正しくありません。環境設定から再設定してください"
        "not_found" -> if (number != null) "HTTP 404: No. $number の出典は存在しません" else "HTTP 404: 見つかりません"
        "rate_limit" -> "HTTP 429: 呼び出し回数の上限に達しています"
        "api_key_non_ascii" -> "APIキーに使用できない文字が含まれています。環境設定から再設定してください"
        else -> "エラー: $error"
    }

    /** 単語の削除（他の単語からの参照も取り除く） */
    fun deleteWord(id: Int) {
        val word = engine.lookup(id) ?: return
        val form = DictionaryStore.formOf(word)
        store.removeWordById(id)
        for (w in store.wordList()) {
            val rels = w.optJSONArray("relations") ?: continue
            var i = 0
            while (i < rels.length()) {
                val r = rels.optJSONObject(i)
                if (r?.optJSONObject("entry")?.optInt("id", -1) == id) rels.remove(i) else i++
            }
        }
        changelog.addEntry("DELETE", form)
        refreshAfterDataChange()
        post("「$form」を削除しました")
    }

    // ------------------------------------------------------------------
    // 設定
    // ------------------------------------------------------------------

    fun updateFontScale(scale: Float) {
        fontScale = scale
        prefs.fontScale = scale
    }

    fun updateAutoSave(enabled: Boolean) {
        autoSave = enabled
        prefs.autoSave = enabled
    }

    fun updateUseIdyerFont(enabled: Boolean) {
        useIdyerFont = enabled
        prefs.idyerFont = enabled
    }

    fun updateEinkMode(enabled: Boolean) {
        einkMode = enabled
        prefs.einkMode = enabled
    }

    private fun idyerFontFile(): File =
        File(getApplication<Application>().filesDir, "idyer_font.ttf")

    fun hasIdyerFontFile(): Boolean = idyerFontFile().exists()

    private fun loadIdyerFontFamily() {
        val f = idyerFontFile()
        idyerFontFamily = if (f.exists()) {
            try { FontFamily(Typeface.createFromFile(f)) } catch (e: Exception) { null }
        } else null
    }

    /** フォントファイル（Fazik-regular.ttf 等）をアプリ内へ取り込む */
    fun importIdyerFont(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = getApplication<Application>().contentResolver
                resolver.openInputStream(uri)?.use { input ->
                    idyerFontFile().outputStream().use { output -> input.copyTo(output) }
                }
                withContext(Dispatchers.Main) {
                    loadIdyerFontFamily()
                    post("フォントを取り込みました")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { post("フォント取込エラー: ${e.message}") }
            }
        }
    }

    // ------------------------------------------------------------------
    // 辞書依存設定
    // ------------------------------------------------------------------

    fun updateDictionarySettings(punctuations: List<String>, ignoredPattern: String) {
        if (!store.isLoaded) {
            post("先に辞書ファイルを開いてください。")
            return
        }
        if (ignoredPattern.isNotEmpty()) {
            try { Regex(ignoredPattern) } catch (e: Exception) {
                post("無視パターンが正しい正規表現ではありません: ${e.message}")
                return
            }
        }
        store.setZpdicSettings(punctuations, ignoredPattern)
        refreshAfterDataChange()
        post("設定を更新しました（区切り文字: ${punctuations.joinToString("")} / 無視パターン: ${ignoredPattern.ifEmpty { "(なし)" }}）")
    }

    // ------------------------------------------------------------------
    // 更新履歴エクスポート・外部CSV連携（ローカルモード）
    // ------------------------------------------------------------------

    fun exportChangelog(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = changelog.exportCsvText()
                val resolver = getApplication<Application>().contentResolver
                SafIo.writeText(resolver, uri, text)
                withContext(Dispatchers.Main) { post("更新履歴をエクスポートしました") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { post("エクスポートエラー: ${e.message}") }
            }
        }
    }

    fun linkChangelogCsv(uri: Uri) {
        if (!store.isLoaded) {
            post("先に辞書ファイルを開いてください。")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = getApplication<Application>().contentResolver
                try {
                    resolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: SecurityException) { }
                changelog.linkExternalCsv(uri)
                withContext(Dispatchers.Main) {
                    changelogVersion++
                    post("更新履歴CSVと連携しました。以後、辞書の保存と連動して自動更新されます。")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { post("連携エラー: ${e.message}") }
            }
        }
    }

    fun unlinkChangelogCsv() {
        changelog.unlinkExternalCsv()
        changelogVersion++
        post("連携を解除しました。以後はアプリ内部に保存されます。")
    }

    fun clearChangelogHistory() {
        changelog.clearInternal()
        changelogVersion++
        post("更新履歴を削除しました")
    }

    fun changelogLinkedName(): String? =
        changelog.linkedUri()?.let {
            try { queryDisplayName(it) } catch (e: Exception) { it.lastPathSegment }
        }
}
