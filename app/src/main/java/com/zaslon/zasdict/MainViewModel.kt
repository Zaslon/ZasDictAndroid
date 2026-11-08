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
import com.zaslon.zasdict.data.ChangelogEntry
import com.zaslon.zasdict.data.ChangelogStore
import com.zaslon.zasdict.data.DictionaryStore
import com.zaslon.zasdict.data.DropboxApiClient
import com.zaslon.zasdict.data.GitHubApiClient
import com.zaslon.zasdict.data.HttpStatusException
import com.zaslon.zasdict.data.Prefs
import com.zaslon.zasdict.data.SafIo
import com.zaslon.zasdict.ui.theme.ThemeMode
import com.zaslon.zasdict.data.SearchEngine
import com.zaslon.zasdict.data.StorageMode
import com.zaslon.zasdict.data.ZpdicApiClient
import com.zaslon.zasdict.domain.Const
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
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
    val offerCatalog: String = Const.EXAMPLE_CATALOG_SELF,
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

/** 閉じるまで他の操作をさせない失敗の通知。見過ごすと変更が失われる失敗に使う。 */
data class ErrorNotice(val title: String, val body: String)

/** 通知の種類。デザインシステムの SSIP コードの Semantic に当たり、添えるアイコンを決める。 */
enum class Semantic { INF, SUC, WRN, ERR }

/**
 * スナックバー（縦に狭い画面のステータスバー）に出す通知。
 * 失敗は文言だけでも分かるように書いたうえで、色とアイコンでも示す。
 * [actionLabel] があれば、通知に操作ボタン（削除直後の「元に戻す」等）を付ける。
 */
data class AppMessage(
    val text: String,
    val semantic: Semantic = Semantic.INF,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
) {
    val isFailure: Boolean get() = semantic == Semantic.ERR
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val prefs = Prefs(app)
    val store = DictionaryStore()
    val engine = SearchEngine(store)
    val changelog = ChangelogStore(app)

    private val dropboxClient = DropboxApiClient()
    private val githubClient = GitHubApiClient()
    private val boxClient = BoxApiClient()
    private val zpdicClient = ZpdicApiClient()

    private val stash = UnsavedStash(app)

    private class LocalStashRequest(
        val uri: String,
        val dictJson: String,
        val pendingChangelog: List<ChangelogEntry>
    )

    // 退避は最新の内容さえ書ければよいので、書き込み待ちの古い依頼は捨てる。null は退避の削除。
    private val localStashRequests = Channel<LocalStashRequest?>(Channel.CONFLATED)

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
    var message by mutableStateOf<AppMessage?>(null)
        private set
    var errorNotice by mutableStateOf<ErrorNotice?>(null)
        private set
    var editorDraft by mutableStateOf<EditorDraft?>(null)
        private set
    /** 退避から戻した編集画面の、画面を開いた時点の内容。戻した後も変更の有無を正しく判定するために使う。 */
    var editorDraftBaseline: EditorDraft? = null
        private set
    var exampleDraft by mutableStateOf<DraftExample?>(null)
        private set
    var exampleDraftBaseline: DraftExample? = null
        private set

    /**
     * クラウド（Dropbox / GitHub / Box）と通信中かどうか。
     * true の間は保存・再読込を受け付けず、保存ボタン連打による多重アップロードを防ぐ。
     */
    var isSyncing by mutableStateOf(false)
        private set

    /** 通信中に出し続ける表示 */
    val syncingStatus: String
        get() = when (storageMode) {
            StorageMode.DROPBOX -> "Dropboxと通信しています…"
            StorageMode.GITHUB -> "GitHubと通信しています…"
            StorageMode.BOX -> "Boxと通信しています…"
            StorageMode.LOCAL -> "通信しています…"
        }

    /**
     * 同期処理を1件だけ実行する。既に実行中なら false を返して何もしない。
     * [block] は Dispatchers.IO 上で実行される。
     */
    private fun launchSync(busyMessage: String = "同期処理中です。完了までお待ちください", block: suspend () -> Unit): Boolean {
        if (isSyncing) {
            postFailure(busyMessage)
            return false
        }
        isSyncing = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } finally {
                withContext(NonCancellable + Dispatchers.Main) { isSyncing = false }
            }
        }
        return true
    }

    /** ファイルブラウザに表示するエラー文言 */
    private fun browserErrorText(e: Exception): String =
        "一覧を取得できませんでした。${describeCause(e)}"

    /** 更新履歴画面の再読込トリガ（連携・保存のたびに増える） */
    var changelogVersion by mutableStateOf(0)
        private set

    /** 例文リスト再描画トリガ（例文の追加・編集・削除のたびに増える） */
    var examplesVersion by mutableStateOf(0)
        private set

    /** タブレット2ペインで右ペインに表示中の単語ID（null = 未選択） */
    var tabletSelectedWordId by mutableStateOf<Int?>(null)

    fun selectTabletWord(id: Int?) { tabletSelectedWordId = id }

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
    var themeMode by mutableStateOf(prefs.themeMode)
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
        // 画面の復元は ViewModel の生成直後に始まるので、編集画面が開く前に読み終えておく
        stash.loadEditorDraft()?.let {
            editorDraft = it.current
            editorDraftBaseline = it.baseline
        }
        stash.loadExampleDraft()?.let {
            exampleDraft = it.current
            exampleDraftBaseline = it.baseline
        }
        restoreLastDictionary()
        viewModelScope.launch(Dispatchers.IO) {
            for (request in localStashRequests) {
                try {
                    if (request == null) stash.clearDictionary()
                    else stash.saveDictionary(request.uri, request.dictJson, request.pendingChangelog)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        postFailure("編集内容を端末内に退避できませんでした。早めに保存してください。")
                    }
                }
            }
        }
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
    /** 操作の結果の通知。失敗でなければ成功として示す。 */
    private fun post(msg: String, isFailure: Boolean = false) {
        message = AppMessage(msg, if (isFailure) Semantic.ERR else Semantic.SUC)
    }
    /** 操作の成否ではなく、状態を知らせる通知 */
    private fun postInfo(msg: String) { message = AppMessage(msg, Semantic.INF) }
    private fun postFailure(msg: String) = post(msg, isFailure = true)

    fun dismissError() { errorNotice = null }

    /** [note] には、失敗の後で編集内容がどうなっているか（端末内に残っている等）を書く。 */
    private fun postError(title: String, e: Throwable, note: String? = null) {
        errorNotice = ErrorNotice(title, listOfNotNull(describeCause(e), note).joinToString("\n\n"))
    }

    /**
     * 例外を、利用者が次に何をすればよいか分かる文に言い換える。
     * 例外メッセージには HTTP 応答の本文や内部の情報が混じるため、言い換えられない場合にだけ詳細として添える。
     * 通信クライアントはタイムアウト等を独自の IOException に包んで投げるので、原因（cause）も調べる。
     */
    private fun describeCause(e: Throwable): String =
        knownCause(e) ?: e.cause?.let(::knownCause)
            ?: ("原因を特定できませんでした。" +
                (e.message?.takeIf { it.isNotBlank() }?.let { "\n詳細: ${it.take(200)}" } ?: ""))

    private fun knownCause(e: Throwable): String? = when (e) {
        is java.net.UnknownHostException, is java.net.ConnectException ->
            "インターネットに接続できません。通信状態を確認してください。"
        is java.net.SocketTimeoutException ->
            "通信がタイムアウトしました。電波状況を確認して、もう一度お試しください。"
        is HttpStatusException -> when (e.code) {
            401 -> "認証の期限が切れているか、取り消されています。設定で接続し直してください。"
            403 -> "アクセスする権限がありません。設定で、接続に使っている権限を確認してください。"
            404 -> "ファイルが見つかりません。移動・削除された可能性があります。"
            409, 412, 422 -> "他の端末で先に更新されています。最新の辞書を読み込んでから、もう一度お試しください。"
            429 -> "通信回数の上限に達しました。しばらく待ってから、もう一度お試しください。"
            in 500..599 -> "通信先のサーバーで問題が起きています。しばらく待ってから、もう一度お試しください。"
            else -> "通信先が要求を受け付けませんでした（HTTP ${e.code}）。"
        }
        is SecurityException, is java.io.FileNotFoundException ->
            "ファイルにアクセスできません。移動・削除されたか、アクセス権限が切れています。ファイルを選び直してください。"
        is org.json.JSONException ->
            "データの形式が正しくないため読み込めませんでした。"
        else -> null
    }

    /** 辞書を開いているか。store.isLoaded は Compose の状態ではないため、読み込みのたびに変わる fileName も併せて読み、画面を更新させる。 */
    val isDictionaryLoaded: Boolean
        get() = fileName != null && store.isLoaded

    /** 端末内の辞書ファイルを読んでいる間 true。起動直後の一覧が空なのを「辞書が無い」と誤って伝えないために使う。 */
    var isReadingDictionary by mutableStateOf(false)
        private set

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
                    postError("前回の辞書を開けませんでした", e)
                }
                return@launch
            }
            restoreLocalStash(uriString)
        }
    }

    /** 前回、保存しないまま終了されたときの編集内容があれば、読み込んだ辞書に重ねて戻す */
    private suspend fun restoreLocalStash(uriString: String) {
        try {
            val stashed = stash.loadDictionary(uriString) ?: return
            store.loadFromString(stashed.dictJson)
            engine.rebuild()
            changelog.pendingEntries.addAll(stashed.pendingChangelog)
            withContext(Dispatchers.Main) {
                hasUnsavedChanges = true
                wordCount = store.wordCount()
                postInfo("保存されていなかった編集内容を復元しました")
            }
        } catch (e: Exception) {
            // 読めない退避は次の起動でも読めないので、残さない
            stash.clearDictionary()
            withContext(Dispatchers.Main) {
                postError("保存されていなかった編集内容を復元できませんでした", e)
            }
        }
    }

    /** ローカルモードで、アプリが終了されても未保存の編集を失わないよう端末内へ退避する */
    private fun stashLocalChanges() {
        val uri = currentUri ?: return
        localStashRequests.trySend(
            LocalStashRequest(uri.toString(), store.toJsonString(), changelog.pendingEntries.toList())
        )
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
                    // 前の辞書の未保存の編集は、別の辞書を開いた時点で破棄されている
                    localStashRequests.trySend(null)
                    searchText = ""
                    results = emptyList()
                    post("辞書を読み込みました")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { postError("辞書を開けませんでした", e) }
            }
        }
    }

    private suspend fun loadDictionaryFromUri(uri: Uri) {
        withContext(Dispatchers.Main) { isReadingDictionary = true }
        try {
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
        } finally {
            withContext(Dispatchers.Main) { isReadingDictionary = false }
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
                    // 更新履歴を書けなかった場合は、保留中の履歴がまだ端末内にしか無いので退避を残す
                    if (flushed) localStashRequests.trySend(null) else stashLocalChanges()
                    if (flushed) post("保存しました")
                    else postFailure("辞書は保存しましたが、更新履歴CSVに書き込めませんでした。連携先が移動・削除された可能性があります。")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { postError("辞書を保存できませんでした", e, "編集内容はまだ保存されていません。アプリを閉じる前に、もう一度保存してください。") }
            }
        }
    }

    private fun autoSaveIfEnabled() {
        when (storageMode) {
            StorageMode.LOCAL -> {
                // 自動保存でも書き込みに失敗しうるので、退避は常に行う
                stashLocalChanges()
                if (autoSave && currentUri != null) saveFile()
            }
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
        clearEditorDraft()
        clearExampleDraft()
        changelog.setDictionary(null)
        // 切替先の辞書を自動復元
        restoreLastDictionary()
        post("${when (mode) { StorageMode.LOCAL -> "ローカル"; StorageMode.DROPBOX -> "Dropbox"; StorageMode.GITHUB -> "GitHub"; StorageMode.BOX -> "Box" }}モードに切り替えました")
    }

    /** Dropbox OAuth2 PKCE 認証フローをブラウザで開始する */
    fun launchDropboxAuth(context: Context) {
        val appKey = prefs.dropboxAppKey
        if (appKey.isEmpty()) {
            postFailure("App Key を入力してください")
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
            postFailure("認証エラー: PKCE verifier が見つかりません")
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
                withContext(Dispatchers.Main) { postFailure("Dropboxに接続できませんでした。${describeCause(e)}") }
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
            postFailure("先にDropboxに接続してください")
            return
        }
        dropboxBrowserTarget = "dict"
        dropboxBrowserPath = ""
        loadDropboxFolder("")
    }

    fun openDropboxBrowserForChangelog() {
        if (!dropboxConnected) {
            postFailure("先にDropboxに接続してください")
            return
        }
        // substringBeforeLast は区切り文字が無いと文字列全体を返すため、
        // ルート直下のCSVでもファイルパスを開こうとしないよう "" をフォールバックにする
        val startPath = prefs.dropboxChangelogPath
            ?.substringBeforeLast("/", "")?.takeIf { it.isNotEmpty() }
            ?: prefs.dropboxDictPath?.substringBeforeLast("/", "")?.takeIf { it.isNotEmpty() }
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

    private fun loadDropboxFolder(path: String, fallbackToRoot: Boolean = true) {
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
                if (fallbackToRoot && path.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        dropboxBrowserPath = ""
                        loadDropboxFolder("", fallbackToRoot = false)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        dropboxBrowserError = browserErrorText(e)
                        dropboxBrowserLoading = false
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Dropbox からファイルを開く
    // ------------------------------------------------------------------

    fun openFromDropbox(path: String, name: String) {
        if (isSyncing) { postFailure("Dropboxと同期中です。完了までお待ちください"); return }
        dropboxBrowserTarget = null
        launchSync("Dropboxと同期中です。完了までお待ちください") {
            try {
                val token = getValidAccessToken()
                val text = dropboxClient.downloadFile(token, path)
                store.loadFromString(text)
                engine.rebuild()

                // ローカルキャッシュに書き込む（タスクキル対策）
                dropboxCacheFile().writeText(text)

                // changelog を辞書に紐付ける。
                // 同じ辞書を開き直す（再読込）ときは、ユーザーが設定した保存先を維持する。
                val changelogPath = prefs.dropboxChangelogPath?.takeIf { prefs.dropboxDictPath == path }
                    ?: (path.removeSuffix(".json") + "_changelog.csv")
                prefs.dropboxDictPath = path
                prefs.dropboxDictName = name
                prefs.dropboxChangelogPath = changelogPath
                prefs.dropboxHasPendingUpload = false
                changelog.setDictionary("dropbox:$path")

                // Dropbox上に既存のchangelogがあれば読み込む
                val hadLocalHistory = changelog.exists()
                var changelogFailed = false
                try {
                    val csvText = dropboxClient.downloadFile(token, changelogPath)
                    changelog.loadFromText(csvText)
                } catch (_: Exception) {
                    changelogFailed = true
                }

                withContext(Dispatchers.Main) {
                    fileName = name
                    dropboxDictName = name
                    hasUnsavedChanges = false
                    wordCount = store.wordCount()
                    dropboxHasPendingUpload = false
                    searchText = ""
                    results = emptyList()
                    changelogVersion++
                    post(
                        if (changelogFailed && hadLocalHistory)
                            "Dropboxから辞書を読み込みました（更新履歴 $changelogPath を取得できませんでした。表示中の履歴は古い可能性があります）"
                        else "Dropboxから辞書を読み込みました",
                        isFailure = changelogFailed && hadLocalHistory
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { postError("Dropboxから辞書を読み込めませんでした", e) }
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
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { postError("端末内に保存できませんでした", e, "編集内容はまだ保存されていません。") }
            }
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
                    if (hasPending) postInfo("ローカルキャッシュを復元しました（Dropboxへの未同期があります）")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { postError("端末内の辞書を復元できませんでした", e) }
            }
        }
    }

    // ------------------------------------------------------------------
    // Dropbox へアップロード
    // ------------------------------------------------------------------

    /** 辞書 + 更新履歴を Dropbox へアップロードする */
    fun uploadToDropbox() {
        val dictPath = prefs.dropboxDictPath ?: run {
            postFailure("Dropboxの辞書ファイルが選択されていません")
            return
        }
        launchSync("Dropboxと同期中です。完了までお待ちください") {
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
                withContext(Dispatchers.Main) { postError("Dropboxに保存できませんでした", e, "編集内容は端末内に残っています。") }
            }
        }
    }

    /** Dropbox 上の最新ファイルを再読み込みする */
    fun reloadFromDropbox() {
        val dictPath = prefs.dropboxDictPath ?: run {
            postFailure("Dropboxの辞書ファイルが選択されていません")
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
            postFailure("トークン、オーナー、リポジトリを入力してください")
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
                withContext(Dispatchers.Main) { postFailure("GitHubに接続できませんでした。${describeCause(e)}") }
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
        if (!githubConnected) { postFailure("先にGitHubに接続してください"); return }
        githubBrowserTarget = "dict"
        githubBrowserPath = ""
        loadGitHubFolder("")
    }

    fun openGitHubBrowserForChangelog() {
        if (!githubConnected) { postFailure("先にGitHubに接続してください"); return }
        // substringBeforeLast は区切り文字が無いと文字列全体を返すため、
        // リポジトリ直下のCSV（例: dictionary_changelog.csv）ではルート("")を開始パスにする
        val startPath = prefs.githubChangelogPath
            ?.substringBeforeLast("/", "")?.takeIf { it.isNotEmpty() }
            ?: prefs.githubDictPath?.substringBeforeLast("/", "")?.takeIf { it.isNotEmpty() }
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

    /**
     * @param fallbackToRoot 取得に失敗したときルートを開き直すか。
     *   保存先が移動・削除されていてもブラウザを操作不能にしないためのフォールバック。
     */
    private fun loadGitHubFolder(path: String, fallbackToRoot: Boolean = true) {
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
                if (fallbackToRoot && path.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        githubBrowserPath = ""
                        loadGitHubFolder("", fallbackToRoot = false)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        githubBrowserError = browserErrorText(e)
                        githubBrowserLoading = false
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // GitHub からファイルを開く
    // ------------------------------------------------------------------

    fun openFromGitHub(path: String, name: String) {
        if (isSyncing) { postFailure("GitHubと同期中です。完了までお待ちください"); return }
        githubBrowserTarget = null
        val token = prefs.githubToken ?: run { postFailure("GitHubに接続されていません"); return }
        val owner = prefs.githubOwner ?: return
        val repo = prefs.githubRepo ?: return
        launchSync("GitHubと同期中です。完了までお待ちください") {
            try {
                val fileContent = githubClient.getFileContent(token, owner, repo, path)
                store.loadFromString(fileContent.text)
                engine.rebuild()

                gitHubCacheFile().writeText(fileContent.text)

                // 同じ辞書を開き直す（再読込）ときは、ユーザーが設定した更新履歴の保存先を維持する。
                // 別の辞書を開いたときだけ辞書名から自動設定する。
                val changelogPath = prefs.githubChangelogPath?.takeIf { prefs.githubDictPath == path }
                    ?: (path.removeSuffix(".json") + "_changelog.csv")
                prefs.githubDictPath = path
                prefs.githubDictName = name
                prefs.githubChangelogPath = changelogPath
                prefs.githubHasPendingUpload = false
                changelog.setDictionary("github:$path")

                val hadLocalHistory = changelog.exists()
                var changelogFailed = false
                try {
                    val csvContent = githubClient.getFileContent(token, owner, repo, changelogPath)
                    changelog.loadFromText(csvContent.text)
                } catch (_: Exception) {
                    changelogFailed = true
                }

                withContext(Dispatchers.Main) {
                    fileName = name
                    githubDictName = name
                    hasUnsavedChanges = false
                    wordCount = store.wordCount()
                    githubHasPendingUpload = false
                    searchText = ""
                    results = emptyList()
                    changelogVersion++
                    // 既存のローカル履歴がある状態で取得に失敗した＝古い履歴を表示し続けるので通知する
                    post(
                        if (changelogFailed && hadLocalHistory)
                            "GitHubから辞書を読み込みました（更新履歴 $changelogPath を取得できませんでした。表示中の履歴は古い可能性があります）"
                        else "GitHubから辞書を読み込みました",
                        isFailure = changelogFailed && hadLocalHistory
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { postError("GitHubから辞書を読み込めませんでした", e) }
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
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { postError("端末内に保存できませんでした", e, "編集内容はまだ保存されていません。") }
            }
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
                    if (hasPending) postInfo("ローカルキャッシュを復元しました（GitHubへの未同期があります）")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { postError("端末内の辞書を復元できませんでした", e) }
            }
        }
    }

    // ------------------------------------------------------------------
    // GitHub へアップロード（コミット作成）
    // ------------------------------------------------------------------

    /** 辞書 + 更新履歴を GitHub へ1コミットとしてまとめてアップロードする */
    fun uploadToGitHub(commitMessage: String = "Update Dictionary (Android)") {
        val dictPath = prefs.githubDictPath ?: run { postFailure("GitHubの辞書ファイルが選択されていません"); return }
        val token = prefs.githubToken ?: run { postFailure("GitHubに接続されていません"); return }
        val owner = prefs.githubOwner ?: return
        val repo = prefs.githubRepo ?: return
        val branch = prefs.githubBranch
        launchSync("GitHubと同期中です。完了までお待ちください") {
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
                withContext(Dispatchers.Main) { postError("GitHubにコミットできませんでした", e, "編集内容は端末内に残っています。") }
            }
        }
    }

    /** GitHub 上の最新ファイルを再読み込みする */
    fun reloadFromGitHub() {
        val dictPath = prefs.githubDictPath ?: run { postFailure("GitHubの辞書ファイルが選択されていません"); return }
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
        if (clientId.isEmpty()) { postFailure("Client IDを入力してください"); return }
        val verifier = boxClient.generatePkceVerifier()
        boxPkceVerifier = verifier
        val challenge = boxClient.generatePkceChallenge(verifier)
        val url = boxClient.buildAuthUrl(clientId, challenge)
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun handleBoxAuthCode(code: String) {
        val verifier = boxPkceVerifier ?: run { postFailure("認証エラー: PKCE verifierが見つかりません"); return }
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
                withContext(Dispatchers.Main) { postFailure("Boxに接続できませんでした。${describeCause(e)}") }
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
        if (!boxConnected) { postFailure("先にBoxに接続してください"); return }
        boxBrowserTarget = "dict"
        boxBrowserFolderStack = emptyList()
        boxBrowserFolderId = "0"
        boxBrowserFolderName = "Box"
        loadBoxFolder("0")
    }

    fun openBoxBrowserForChangelog() {
        if (!boxConnected) { postFailure("先にBoxに接続してください"); return }
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

    private fun loadBoxFolder(folderId: String, fallbackToRoot: Boolean = true) {
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
                // 保存先フォルダが削除・移動されていてもブラウズ不能にならないようルートへ戻す
                if (fallbackToRoot && folderId != "0") {
                    withContext(Dispatchers.Main) {
                        boxBrowserFolderStack = emptyList()
                        boxBrowserFolderId = "0"
                        boxBrowserFolderName = "Box"
                        loadBoxFolder("0", fallbackToRoot = false)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        boxBrowserError = browserErrorText(e)
                        boxBrowserLoading = false
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Box からファイルを開く
    // ------------------------------------------------------------------

    fun openFromBox(fileId: String, name: String) {
        if (isSyncing) { postFailure("Boxと同期中です。完了までお待ちください"); return }
        val folderId = boxBrowserFolderId
        boxBrowserTarget = null
        launchSync("Boxと同期中です。完了までお待ちください") {
            try {
                val token = getValidBoxAccessToken()
                val text = boxClient.downloadFile(token, fileId)
                store.loadFromString(text)
                engine.rebuild()
                boxCacheFile().writeText(text)

                val dictBaseName = name.removeSuffix(".json")
                val csvName = "${dictBaseName}_changelog.csv"
                // 同じ辞書を開き直す（再読込）ときは、ユーザーが設定した更新履歴の保存先を維持する。
                // 別の辞書を開いたときだけ辞書と同じフォルダから探す。
                val isSameDict = prefs.boxDictFileId == fileId
                val changelogFolderId =
                    if (isSameDict) prefs.boxChangelogFolderId ?: folderId else folderId
                val changelogFileId = (if (isSameDict) prefs.boxChangelogFileId else null)
                    ?: boxClient.findFileInFolder(token, changelogFolderId, csvName)

                prefs.boxDictFileId = fileId
                prefs.boxDictFolderId = folderId
                prefs.boxDictName = name
                prefs.boxChangelogFileId = changelogFileId
                prefs.boxChangelogFolderId = changelogFolderId
                prefs.boxHasPendingUpload = false
                changelog.setDictionary("box:$fileId")

                val hadLocalHistory = changelog.exists()
                var changelogFailed = false
                if (changelogFileId != null) {
                    try {
                        val csvText = boxClient.downloadFile(token, changelogFileId)
                        changelog.loadFromText(csvText)
                    } catch (_: Exception) {
                        changelogFailed = true
                    }
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
                    post(
                        if (changelogFailed && hadLocalHistory)
                            "Boxから辞書を読み込みました（更新履歴 $csvName を取得できませんでした。表示中の履歴は古い可能性があります）"
                        else "Boxから辞書を読み込みました",
                        isFailure = changelogFailed && hadLocalHistory
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { postError("Boxから辞書を読み込めませんでした", e) }
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
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { postError("端末内に保存できませんでした", e, "編集内容はまだ保存されていません。") }
            }
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
                    if (hasPending) postInfo("ローカルキャッシュを復元しました（Boxへの未同期があります）")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { postError("端末内の辞書を復元できませんでした", e) }
            }
        }
    }

    // ------------------------------------------------------------------
    // Box へアップロード
    // ------------------------------------------------------------------

    fun uploadToBox() {
        val dictFileId = prefs.boxDictFileId ?: run { postFailure("Boxの辞書ファイルが選択されていません"); return }
        val dictName = prefs.boxDictName ?: return
        val dictFolderId = prefs.boxDictFolderId ?: "0"
        launchSync("Boxと同期中です。完了までお待ちください") {
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
                withContext(Dispatchers.Main) { postError("Boxに保存できませんでした", e, "編集内容は端末内に残っています。") }
            }
        }
    }

    fun reloadFromBox() {
        val dictFileId = prefs.boxDictFileId ?: run { postFailure("Boxの辞書ファイルが選択されていません"); return }
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
        val list = engine.wordsWithFormPrefix(q)
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
            postFailure("先に辞書ファイルを開いてください。")
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

    fun discardDraft() = clearEditorDraft()

    private fun clearEditorDraft() {
        editorDraft = null
        editorDraftBaseline = null
        stash.saveEditorDraft(null)
    }

    /** 編集画面が裏に回るたびに呼ぶ。アプリが終了されても、次の起動で入力中の内容を戻せるようにする。 */
    fun stashEditorDraft(baseline: EditorDraft, current: EditorDraft) {
        try {
            stash.saveEditorDraft(StashedDraft(baseline, current))
        } catch (_: Exception) {
            // 退避は保険なので、書けなくても編集は続けられる
        }
    }

    /**
     * 退避していた入力中の内容を捨てる。利用者が自分でアプリを閉じた後の起動で呼ぶ。
     * 閉じた時点で編集をやめたとみなし、OS に終了された場合（画面の状態ごと戻る場合）だけ戻す。
     */
    fun discardStashedDrafts() {
        clearEditorDraft()
        clearExampleDraft()
    }

    /** ドラフトを辞書へ反映（対照関係の自動登録を含む） */
    fun commitDraft(draft: EditorDraft): Boolean {
        // OS に終了された後は、辞書の読み込みより先に編集画面が戻ることがある
        if (!store.isLoaded) {
            postFailure("辞書を読み込んでいます。読み込みが終わってから保存してください。")
            return false
        }
        val form = draft.form.trim()
        if (form.isEmpty()) {
            postFailure("見出し語を入力してください。")
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
                postFailure("対象の単語が見つかりません。")
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

        clearEditorDraft()
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
            postFailure("先に辞書ファイルを開いてください。")
            return
        }
        exampleDraft = DraftExample(offerNumber = store.maxExampleId() + 1)
    }

    fun nextSelfOfferNumber(): Int = store.maxExampleId() + 1

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

    fun discardExampleDraft() = clearExampleDraft()

    private fun clearExampleDraft() {
        exampleDraft = null
        exampleDraftBaseline = null
        stash.saveExampleDraft(null)
    }

    /** [stashEditorDraft] の例文版 */
    fun stashExampleDraft(baseline: DraftExample, current: DraftExample) {
        try {
            stash.saveExampleDraft(StashedDraft(baseline, current))
        } catch (_: Exception) {
            // 退避は保険なので、書けなくても編集は続けられる
        }
    }

    fun commitExampleDraft(draft: DraftExample): Boolean {
        if (!store.isLoaded) {
            postFailure("辞書を読み込んでいます。読み込みが終わってから保存してください。")
            return false
        }
        val sentence = draft.sentence.trim()
        if (sentence.isEmpty()) {
            postFailure("「文」を入力してください。")
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
                .put("number", if (isNew && draft.offerCatalog == Const.EXAMPLE_CATALOG_SELF) id else draft.offerNumber))

        if (isNew) {
            store.addExample(obj)
        } else {
            store.updateExample(id, obj)
        }

        clearExampleDraft()
        examplesVersion++
        refreshAfterDataChange()
        return true
    }

    fun deleteExample(id: Int) {
        val before = store.toJsonString()
        val pendingBefore = changelog.pendingEntries.toList()
        store.removeExampleById(id)
        examplesVersion++
        refreshAfterDataChange()
        offerUndo("例文を削除しました", before, pendingBefore, deletedForm = null)
    }

    fun exampleList(): List<JSONObject> = store.exampleList()

    fun examplesByWordId(wordId: Int): List<JSONObject> =
        store.exampleList().filter { ex ->
            val words = ex.optJSONArray("words") ?: return@filter false
            (0 until words.length()).any { i ->
                words.optJSONObject(i)?.optInt("id", -1) == wordId
            }
        }

    // ------------------------------------------------------------------
    // ZpDIC Online API 操作
    // ------------------------------------------------------------------

    fun saveZpdicApiKey(key: String) {
        val trimmed = key.trim()
        prefs.zpdicApiKey = trimmed.ifEmpty { null }
        zpdicApiKeySet = trimmed.isNotEmpty()
        if (trimmed.isNotEmpty()) post("ZpDIC APIキーを設定しました")
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
            zpdicOfferStatus = "APIキーが設定されていません（設定で登録してください）"
            return
        }
        if (catalog.trim().isEmpty() || catalog.trim() == Const.EXAMPLE_CATALOG_SELF) {
            zpdicOfferStatus = "カタログを選択してください"
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

    private fun errorMessage(error: String, number: Int?): String = when (error) {
        "bad_request" -> "HTTP 400: リクエストの内容が誤っています"
        "auth_failed" -> "HTTP 401: APIキーが正しくありません。設定から設定し直してください"
        "not_found" -> if (number != null) "HTTP 404: No. $number の出典は存在しません" else "HTTP 404: 見つかりません"
        "rate_limit" -> "HTTP 429: 呼び出し回数の上限に達しています"
        "api_key_non_ascii" -> "APIキーに使用できない文字が含まれています。設定から設定し直してください"
        else -> "エラー: $error"
    }

    /** 単語の削除（他の単語からの参照も取り除く） */
    fun deleteWord(id: Int) {
        val word = engine.lookup(id) ?: return
        val form = DictionaryStore.formOf(word)
        val before = store.toJsonString()
        val pendingBefore = changelog.pendingEntries.toList()
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
        offerUndo("「$form」を削除しました", before, pendingBefore, deletedForm = form)
    }

    /**
     * 削除を知らせる通知に「元に戻す」を付ける。元に戻すと、削除前の辞書をそのまま戻す。
     * 関連語の参照も一緒に外しているため、消した 1 件を足し直すより、丸ごと戻す方が確実。
     */
    private fun offerUndo(
        text: String,
        before: String,
        pendingBefore: List<ChangelogEntry>,
        deletedForm: String?
    ) {
        val after = store.toJsonString()
        message = AppMessage(text, Semantic.SUC, actionLabel = "元に戻す") { undoDelete(before, after, pendingBefore, deletedForm) }
    }

    private fun undoDelete(
        before: String,
        after: String,
        pendingBefore: List<ChangelogEntry>,
        deletedForm: String?
    ) {
        // 削除の後に別の編集や辞書の読み込みがあった場合、削除前に戻すとそれまで消えてしまう
        if (store.toJsonString() != after) {
            postFailure("削除の後に辞書が変更されたため、元に戻せませんでした。")
            return
        }
        store.loadFromString(before)
        val pending = changelog.pendingEntries
        if (pending.size > pendingBefore.size && pending.take(pendingBefore.size) == pendingBefore) {
            // 削除の記録がまだ更新履歴に書かれていなければ、記録ごと無かったことにする
            pending.clear()
            pending.addAll(pendingBefore)
        } else if (deletedForm != null) {
            // 自動保存等で削除の記録が書かれた後なので、復活したことを記録する
            changelog.addEntry("ADD", deletedForm, "削除を元に戻した")
        }
        examplesVersion++
        refreshAfterDataChange()
        post("元に戻しました")
    }

    // ------------------------------------------------------------------
    // 設定
    // ------------------------------------------------------------------

    fun updateFontScale(scale: Float) {
        val clamped = scale.coerceIn(Prefs.FONT_SCALE_MIN, Prefs.FONT_SCALE_MAX)
        fontScale = clamped
        prefs.fontScale = clamped
    }

    fun updateThemeMode(mode: ThemeMode) {
        themeMode = mode
        prefs.themeMode = mode
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
                withContext(Dispatchers.Main) { postFailure("フォントを取り込めませんでした。${describeCause(e)}") }
            }
        }
    }

    // ------------------------------------------------------------------
    // 辞書依存設定
    // ------------------------------------------------------------------

    fun updateDictionarySettings(punctuations: List<String>, ignoredPattern: String) {
        if (!store.isLoaded) {
            postFailure("先に辞書ファイルを開いてください。")
            return
        }
        if (ignoredPattern.isNotEmpty()) {
            try { Regex(ignoredPattern) } catch (e: Exception) {
                postFailure("無視パターンが正しい正規表現ではありません: ${e.message}")
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
                withContext(Dispatchers.Main) { post("更新履歴を書き出しました") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { postFailure("更新履歴を書き出せませんでした。${describeCause(e)}") }
            }
        }
    }

    fun linkChangelogCsv(uri: Uri) {
        if (!store.isLoaded) {
            postFailure("先に辞書ファイルを開いてください。")
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
                withContext(Dispatchers.Main) { postFailure("更新履歴CSVと連携できませんでした。${describeCause(e)}") }
            }
        }
    }

    fun unlinkChangelogCsv() {
        changelog.unlinkExternalCsv()
        changelogVersion++
        post("連携を解除しました。以後はアプリ内部に保存されます。")
    }

    /**
     * 更新履歴を削除する。
     *
     * ローカルモードでアプリ内部に履歴を持っている場合のみ、記録済みの履歴を削除する。
     * 外部CSV連携中・クラウドモードでは保存済み履歴の実体がアプリ外（連携先CSV／
     * リモートCSV）にあり、アプリ内部のファイルはその写しにすぎないため、
     * 未保存の変更を取り消すだけにする。
     */
    fun clearChangelogHistory() {
        if (storageMode == StorageMode.LOCAL && !changelog.isExternalLinked()) {
            changelog.clearInternal()
            changelogVersion++
            post("更新履歴を削除しました")
        } else {
            val count = changelog.pendingEntries.size
            changelog.clearPending()
            changelogVersion++
            post(
                if (count > 0) "未保存の変更 ${count} 件を取り消しました（保存済みの更新履歴は残ります）"
                else "取り消す未保存の変更はありません"
            )
        }
    }

    /**
     * クラウド上の更新履歴CSVを取得し直してローカル表示を最新化する。
     * 誤って履歴を消した場合や、他端末・PC版の更新を取り込みたい場合の復旧手段。
     */
    fun reloadChangelogFromCloud() {
        when (storageMode) {
            StorageMode.LOCAL -> postFailure("ローカルモードでは更新履歴をクラウドから読み込めません")
            StorageMode.DROPBOX -> {
                val path = prefs.dropboxChangelogPath
                    ?: run { postFailure("更新履歴の保存先が設定されていません"); return }
                viewModelScope.launch(Dispatchers.IO) {
                    val (message, failed) = try {
                        changelog.loadFromText(dropboxClient.downloadFile(getValidAccessToken(), path))
                        "更新履歴を読み込みました: $path" to false
                    } catch (e: Exception) {
                        "更新履歴を読み込めませんでした。${describeCause(e)}" to true
                    }
                    withContext(Dispatchers.Main) {
                        changelogVersion++
                        post(message, isFailure = failed)
                    }
                }
            }
            StorageMode.GITHUB -> {
                val path = prefs.githubChangelogPath
                    ?: run { postFailure("更新履歴の保存先が設定されていません"); return }
                val token = prefs.githubToken ?: run { postFailure("GitHubに接続されていません"); return }
                val owner = prefs.githubOwner ?: return
                val repo = prefs.githubRepo ?: return
                viewModelScope.launch(Dispatchers.IO) {
                    val (message, failed) = try {
                        changelog.loadFromText(githubClient.getFileContent(token, owner, repo, path).text)
                        "更新履歴を読み込みました: $path" to false
                    } catch (e: Exception) {
                        "更新履歴を読み込めませんでした。${describeCause(e)}" to true
                    }
                    withContext(Dispatchers.Main) {
                        changelogVersion++
                        post(message, isFailure = failed)
                    }
                }
            }
            StorageMode.BOX -> {
                val fileId = prefs.boxChangelogFileId
                    ?: run { postFailure("更新履歴の保存先が設定されていません"); return }
                viewModelScope.launch(Dispatchers.IO) {
                    val (message, failed) = try {
                        changelog.loadFromText(boxClient.downloadFile(getValidBoxAccessToken(), fileId))
                        "更新履歴を読み込みました" to false
                    } catch (e: Exception) {
                        "更新履歴を読み込めませんでした。${describeCause(e)}" to true
                    }
                    withContext(Dispatchers.Main) {
                        changelogVersion++
                        post(message, isFailure = failed)
                    }
                }
            }
        }
    }

    fun changelogLinkedName(): String? =
        changelog.linkedUri()?.let {
            try { queryDisplayName(it) } catch (e: Exception) { it.lastPathSegment }
        }
}
