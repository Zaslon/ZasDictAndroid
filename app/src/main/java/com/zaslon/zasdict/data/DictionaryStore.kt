package com.zaslon.zasdict.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * OTM-JSON 辞書データの保持と操作。
 * JSONObject をそのまま保持することで、アプリが関知しないフィールド
 * （zpdicOnline, version, snoj 等）もロード→セーブで保全される。
 */
class DictionaryStore {

    var root: JSONObject = emptyDictionary()
        private set

    var isLoaded: Boolean = false
        private set

    private fun emptyDictionary(): JSONObject =
        JSONObject().put("words", JSONArray())

    fun loadFromString(text: String) {
        val obj = JSONObject(text)
        if (!obj.has("words")) {
            throw IllegalArgumentException("OTM-JSON 形式ではありません（words がありません）")
        }
        root = obj
        isLoaded = true
    }

    fun clear() {
        root = emptyDictionary()
        isLoaded = false
    }

    /** 保存用の文字列。元実装と同様にコンパクト形式（separators=(",", ":")相当）で出力 */
    fun toJsonString(): String = root.toString()

    val words: JSONArray
        get() {
            if (root.optJSONArray("words") == null) {
                root.put("words", JSONArray())
            }
            return root.getJSONArray("words")
        }

    fun wordCount(): Int = words.length()

    fun wordList(): List<JSONObject> {
        val list = ArrayList<JSONObject>(words.length())
        for (i in 0 until words.length()) {
            words.optJSONObject(i)?.let { list.add(it) }
        }
        return list
    }

    fun findById(id: Int): JSONObject? {
        for (i in 0 until words.length()) {
            val w = words.optJSONObject(i) ?: continue
            if (w.optJSONObject("entry")?.optInt("id", -1) == id) return w
        }
        return null
    }

    fun maxId(): Int {
        var max = 0
        for (i in 0 until words.length()) {
            val id = words.optJSONObject(i)?.optJSONObject("entry")?.optInt("id", 0) ?: 0
            if (id > max) max = id
        }
        return max
    }

    fun addWord(word: JSONObject) {
        words.put(word)
    }

    fun removeWordById(id: Int): Boolean {
        for (i in 0 until words.length()) {
            val w = words.optJSONObject(i) ?: continue
            if (w.optJSONObject("entry")?.optInt("id", -1) == id) {
                words.remove(i)
                return true
            }
        }
        return false
    }

    // ------------------------------------------------------------------
    // 例文操作
    // ------------------------------------------------------------------

    val examples: JSONArray
        get() {
            if (root.optJSONArray("examples") == null) {
                root.put("examples", JSONArray())
            }
            return root.getJSONArray("examples")
        }

    fun exampleList(): List<JSONObject> {
        val arr = examples
        val list = ArrayList<JSONObject>(arr.length())
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list.add(it) }
        return list
    }

    fun maxExampleId(): Int {
        val arr = examples
        var max = 0
        for (i in 0 until arr.length()) {
            val id = arr.optJSONObject(i)?.optInt("id", 0) ?: 0
            if (id > max) max = id
        }
        return max
    }

    fun addExample(example: JSONObject) {
        examples.put(example)
    }

    fun updateExample(id: Int, updated: JSONObject): Boolean {
        val arr = examples
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            if (e.optInt("id", -1) == id) {
                arr.put(i, updated)
                return true
            }
        }
        return false
    }

    fun removeExampleById(id: Int): Boolean {
        val arr = examples
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            if (e.optInt("id", -1) == id) {
                arr.remove(i)
                return true
            }
        }
        return false
    }

    // ------------------------------------------------------------------
    // zpdicOnline（辞書依存設定）
    // ------------------------------------------------------------------

    fun getPunctuations(): List<String> {
        val arr = root.optJSONObject("zpdicOnline")?.optJSONArray("punctuations") ?: return listOf("、", ",")
        return (0 until arr.length()).mapNotNull { arr.optString(it, null) }
    }

    fun getIgnoredPattern(): String =
        root.optJSONObject("zpdicOnline")?.optString("ignoredPattern", "") ?: ""

    fun setZpdicSettings(punctuations: List<String>, ignoredPattern: String) {
        val zpdic = root.optJSONObject("zpdicOnline") ?: JSONObject().also { root.put("zpdicOnline", it) }
        zpdic.put("punctuations", JSONArray(punctuations))
        zpdic.put("ignoredPattern", ignoredPattern)
    }

    companion object {
        // ------------------------------------------------------------------
        // 単語(JSONObject)アクセサ
        // ------------------------------------------------------------------

        fun idOf(word: JSONObject): Int =
            word.optJSONObject("entry")?.optInt("id", -1) ?: -1

        fun formOf(word: JSONObject): String =
            word.optJSONObject("entry")?.optString("form", "") ?: ""

        /** [(title, [forms])] */
        fun translationsOf(word: JSONObject): List<Pair<String, List<String>>> {
            val arr = word.optJSONArray("translations") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                val t = arr.optJSONObject(i) ?: return@mapNotNull null
                val forms = t.optJSONArray("forms")?.let { fa ->
                    (0 until fa.length()).mapNotNull { fa.optString(it, null) }
                } ?: emptyList()
                Pair(t.optString("title", ""), forms)
            }
        }

        /** [(title, text)] */
        fun contentsOf(word: JSONObject): List<Pair<String, String>> {
            val arr = word.optJSONArray("contents") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                val c = arr.optJSONObject(i) ?: return@mapNotNull null
                Pair(c.optString("title", ""), c.optString("text", ""))
            }
        }

        /** [(title, targetId, targetForm)] */
        fun relationsOf(word: JSONObject): List<Triple<String, Int, String>> {
            val arr = word.optJSONArray("relations") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                val r = arr.optJSONObject(i) ?: return@mapNotNull null
                val entry = r.optJSONObject("entry")
                Triple(
                    r.optString("title", ""),
                    entry?.optInt("id", -1) ?: -1,
                    entry?.optString("form", "") ?: ""
                )
            }
        }

        fun tagsOf(word: JSONObject): List<String> {
            val arr = word.optJSONArray("tags") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { arr.optString(it, null) }
        }

        /** [(title, form)] */
        fun variationsOf(word: JSONObject): List<Pair<String, String>> {
            val arr = word.optJSONArray("variations") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                val v = arr.optJSONObject(i) ?: return@mapNotNull null
                Pair(v.optString("title", ""), v.optString("form", ""))
            }
        }

        /** 発音記号（contents の title=発音記号） */
        fun pronunciationOf(word: JSONObject): String? {
            for ((title, text) in contentsOf(word)) {
                if (title == com.zaslon.zasdict.domain.Const.PRONUNCIATION_TITLE && text.isNotEmpty()) {
                    return text
                }
            }
            return null
        }
    }
}
