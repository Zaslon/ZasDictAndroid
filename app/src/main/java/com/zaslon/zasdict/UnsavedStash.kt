package com.zaslon.zasdict

import android.content.Context
import com.zaslon.zasdict.data.ChangelogEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 退避していた、ローカルモードの未保存の辞書 */
data class StashedDictionary(val dictJson: String, val pendingChangelog: List<ChangelogEntry>)

/** 退避していた、編集画面の入力中の内容。[baseline] は画面を開いた時点の内容で、変更の有無の判定に使う。 */
data class StashedDraft<T>(val baseline: T, val current: T)

/**
 * 保存していない編集内容の、端末内の退避先。
 *
 * Android は裏に回したアプリを予告なく終了させることがあり、終了前に保存を確認できない。
 * そのため未保存の内容をここへ書いておき、次の起動で戻す。
 * クラウドモードは辞書ごとのローカルキャッシュが同じ役目を持つので、辞書の退避はローカルモードだけが使う。
 */
class UnsavedStash(context: Context) {

    private val dir = File(context.filesDir, "unsaved")

    private val dictFile = File(dir, "dict.json")
    // 辞書本体より後に書く。これがあれば辞書本体も書き終わっている。
    private val dictMetaFile = File(dir, "dict_meta.json")
    private val editorDraftFile = File(dir, "editor_draft.json")
    private val exampleDraftFile = File(dir, "example_draft.json")

    // ------------------------------------------------------------------
    // ローカルモードの辞書
    // ------------------------------------------------------------------

    fun saveDictionary(uri: String, dictJson: String, pendingChangelog: List<ChangelogEntry>) {
        dictMetaFile.delete()
        writeAtomically(dictFile, dictJson)
        val meta = JSONObject()
            .put("uri", uri)
            .put("changelog", JSONArray(pendingChangelog.map { e ->
                JSONObject()
                    .put("timestamp", e.timestamp)
                    .put("type", e.type)
                    .put("form", e.form)
                    .put("details", e.details)
            }))
        writeAtomically(dictMetaFile, meta.toString())
    }

    /** [uri] の辞書の退避があれば返す。別の辞書の退避は、もう戻す先が無いので消す。 */
    fun loadDictionary(uri: String): StashedDictionary? {
        if (!dictMetaFile.exists() || !dictFile.exists()) return null
        val meta = JSONObject(dictMetaFile.readText())
        if (meta.optString("uri") != uri) {
            clearDictionary()
            return null
        }
        val changelog = meta.optJSONArray("changelog") ?: JSONArray()
        val entries = (0 until changelog.length()).map { i ->
            val e = changelog.getJSONObject(i)
            ChangelogEntry(
                e.optString("timestamp"),
                e.optString("type"),
                e.optString("form"),
                e.optString("details")
            )
        }
        return StashedDictionary(dictFile.readText(), entries)
    }

    fun clearDictionary() {
        dictMetaFile.delete()
        dictFile.delete()
    }

    // ------------------------------------------------------------------
    // 編集画面の入力中の内容
    // ------------------------------------------------------------------

    fun saveEditorDraft(draft: StashedDraft<EditorDraft>?) =
        saveDraft(editorDraftFile, draft) { it.toJson() }

    fun loadEditorDraft(): StashedDraft<EditorDraft>? =
        loadDraft(editorDraftFile) { it.toEditorDraft() }

    fun saveExampleDraft(draft: StashedDraft<DraftExample>?) =
        saveDraft(exampleDraftFile, draft) { it.toJson() }

    fun loadExampleDraft(): StashedDraft<DraftExample>? =
        loadDraft(exampleDraftFile) { it.toDraftExample() }

    private fun <T> saveDraft(file: File, draft: StashedDraft<T>?, toJson: (T) -> JSONObject) {
        if (draft == null) {
            file.delete()
            return
        }
        val json = JSONObject()
            .put("baseline", toJson(draft.baseline))
            .put("current", toJson(draft.current))
        writeAtomically(file, json.toString())
    }

    private fun <T> loadDraft(file: File, fromJson: (JSONObject) -> T): StashedDraft<T>? {
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            StashedDraft(fromJson(json.getJSONObject("baseline")), fromJson(json.getJSONObject("current")))
        } catch (e: Exception) {
            // 書きかけで終了された等で読めない退避は、戻せないので捨てる
            file.delete()
            null
        }
    }

    // 書いている最中に終了されても、前回の退避が壊れずに残るようにする
    private fun writeAtomically(file: File, text: String) {
        dir.mkdirs()
        val tmp = File(dir, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}

// ----------------------------------------------------------------------
// ドラフトの JSON 変換
// ----------------------------------------------------------------------

private fun EditorDraft.toJson(): JSONObject = JSONObject()
    .put("originalId", originalId ?: JSONObject.NULL)
    .put("form", form)
    .put("pronunciation", pronunciation)
    .put("translations", JSONArray(translations.map { JSONObject().put("title", it.title).put("forms", it.forms) }))
    .put("contents", JSONArray(contents.map { JSONObject().put("title", it.title).put("text", it.text) }))
    .put("relations", JSONArray(relations.map {
        JSONObject().put("title", it.title).put("targetId", it.targetId).put("targetForm", it.targetForm)
    }))
    .put("tags", tags)
    .put("variations", JSONArray(variations.map { JSONObject().put("title", it.title).put("form", it.form) }))

private fun JSONObject.toEditorDraft(): EditorDraft = EditorDraft(
    originalId = if (isNull("originalId")) null else getInt("originalId"),
    form = optString("form"),
    pronunciation = optString("pronunciation"),
    translations = objects("translations").map { DraftTranslation(it.optString("title"), it.optString("forms")) },
    contents = objects("contents").map { DraftContent(it.optString("title"), it.optString("text")) },
    relations = objects("relations").map {
        DraftRelation(it.optString("title"), it.optInt("targetId", -1), it.optString("targetForm"))
    },
    tags = optString("tags"),
    variations = objects("variations").map { DraftVariation(it.optString("title"), it.optString("form")) }
)

private fun DraftExample.toJson(): JSONObject = JSONObject()
    .put("originalId", originalId ?: JSONObject.NULL)
    .put("sentence", sentence)
    .put("translation", translation)
    .put("supplement", supplement)
    .put("tags", tags)
    .put("linkedWords", JSONArray(linkedWords.map { (id, form) -> JSONObject().put("id", id).put("form", form) }))
    .put("offerCatalog", offerCatalog)
    .put("offerNumber", offerNumber)

private fun JSONObject.toDraftExample(): DraftExample = DraftExample(
    originalId = if (isNull("originalId")) null else getInt("originalId"),
    sentence = optString("sentence"),
    translation = optString("translation"),
    supplement = optString("supplement"),
    tags = optString("tags"),
    linkedWords = objects("linkedWords").map { Pair(it.getInt("id"), it.optString("form")) },
    offerCatalog = optString("offerCatalog"),
    offerNumber = optInt("offerNumber")
)

private fun JSONObject.objects(key: String): List<JSONObject> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).map { array.getJSONObject(it) }
}
