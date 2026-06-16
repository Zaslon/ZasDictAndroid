package com.zaslon.zasdict

import android.app.Application
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zaslon.zasdict.data.ChangelogStore
import com.zaslon.zasdict.data.DictionaryStore
import com.zaslon.zasdict.data.Prefs
import com.zaslon.zasdict.data.SafIo
import com.zaslon.zasdict.data.SearchEngine
import com.zaslon.zasdict.domain.Const
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    /** 更新履歴画面の再読込トリガ（連携・保存のたびに増える） */
    var changelogVersion by mutableStateOf(0)
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

    init {
        loadIdyerFontFamily()
        restoreLastDictionary()
    }

    fun consumeMessage() { message = null }
    private fun post(msg: String) { message = msg }

    /** 見出し語表示用フォント（Heksa有効時のみカスタムフォント） */
    val headwordFontFamily: FontFamily?
        get() = if (useIdyerFont) idyerFontFamily else null

    // ------------------------------------------------------------------
    // ファイル操作
    // ------------------------------------------------------------------

    private fun restoreLastDictionary() {
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
                } catch (_: SecurityException) {
                    // 永続権限が取れない場合もそのまま読み込みは試みる
                }
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
                // クラウドプロバイダ（Box/OneDrive等）の "wt" 非対応に備えたフォールバック付き書き込み
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
                    // 名前を付けて保存で保存先が変わっても、保留中の履歴は失わない。
                    // 連携中の外部CSVは引き継がず、新しいCSVの選択を促す
                    // （保留中の履歴は一旦アプリ内部に保存され、再連携時にCSVへ移行される）。
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
        if (autoSave && currentUri != null) {
            saveFile()
        }
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
            delay(80) // 入力デバウンス
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

        // contents（発音記号を先頭に）
        val contentsArr = JSONArray()
        if (draft.pronunciation.trim().isNotEmpty()) {
            contentsArr.put(JSONObject().put("title", Const.PRONUNCIATION_TITLE).put("text", draft.pronunciation.trim()))
        }
        for (c in draft.contents) {
            // タイトルは固定（語法/文化/用例/語源）なので、本文が空のセクションは保存しない
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
            // 見出し語が変わった場合、他の単語の関係に記録された form を更新
            if (oldForm != form) {
                updateRelationFormsEverywhere(id, form)
            }
            changelog.addEntry("CHANGE", form, if (oldForm != form) "旧: $oldForm" else "")
        }

        // 対照関係の同期
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
        // 追加された関係 → 相手側に対照関係を追加
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
        // 削除された関係 → 相手側の対照関係を削除
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

    /** 単語の削除（他の単語からの参照も取り除く） */
    fun deleteWord(id: Int) {
        val word = engine.lookup(id) ?: return
        val form = DictionaryStore.formOf(word)
        store.removeWordById(id)
        // 参照の除去
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
            try {
                FontFamily(Typeface.createFromFile(f))
            } catch (e: Exception) { null }
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
        // 正規表現の妥当性チェック
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
    // 更新履歴エクスポート・外部CSV連携
    // ------------------------------------------------------------------

    fun exportChangelog(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = changelog.exportCsvText()
                val resolver = getApplication<Application>().contentResolver
                resolver.let { SafIo.writeText(it, uri, text) }
                withContext(Dispatchers.Main) { post("更新履歴をエクスポートしました") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { post("エクスポートエラー: ${e.message}") }
            }
        }
    }

    /**
     * 手動配置した「辞書名_changelog.csv」を連携する。
     * 永続権限を取得するため、以後は辞書保存（自動上書き保存を含む）の
     * たびにこのCSVへ自動で追記される。
     */
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

    /** 外部CSVとの連携を解除する（以後はアプリ内部に保存） */
    fun unlinkChangelogCsv() {
        changelog.unlinkExternalCsv()
        changelogVersion++
        post("連携を解除しました。以後はアプリ内部に保存されます。")
    }

    /** アプリ内部の更新履歴をすべて削除する */
    fun clearChangelogHistory() {
        changelog.clearInternal()
        changelogVersion++
        post("更新履歴を削除しました")
    }

    /** 連携中の外部CSVの表示名（未連携なら null） */
    fun changelogLinkedName(): String? =
        changelog.linkedUri()?.let {
            try { queryDisplayName(it) } catch (e: Exception) { it.lastPathSegment }
        }
}
