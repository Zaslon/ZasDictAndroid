package com.zaslon.zasdict.data

import com.zaslon.zasdict.domain.TextProcessor
import org.json.JSONObject

/**
 * 検索インデックスと検索処理（func.py の build_search_index / SearchWorker 移植）
 */
class SearchEngine(private val store: DictionaryStore) {

    private var index: MutableMap<String, MutableSet<Int>> = HashMap()
    private var idMap: MutableMap<Int, JSONObject> = LinkedHashMap()

    /** 見出し語の小文字形で昇順ソートした (form, id) 一覧。前方一致検索の二分探索に使う。 */
    private var formSorted: List<Pair<String, Int>> = emptyList()

    fun rebuild() {
        val newIndex = HashMap<String, MutableSet<Int>>()
        val newIdMap = LinkedHashMap<Int, JSONObject>()

        for (word in store.wordList()) {
            val entry = word.optJSONObject("entry") ?: continue
            val id = entry.optInt("id", -1)
            val form = entry.optString("form", "")
            if (id < 0 || form.isEmpty()) continue

            newIdMap[id] = word

            val keys = ArrayList<String>()
            keys.add(form.lowercase())

            // 訳語
            for ((_, forms) in DictionaryStore.translationsOf(word)) {
                keys.addAll(forms.filter { it.isNotEmpty() }.map { it.lowercase() })
            }
            // バリエーション
            for ((_, vform) in DictionaryStore.variationsOf(word)) {
                if (vform.isNotEmpty()) keys.add(vform.lowercase())
            }
            // 関連語
            for ((_, _, rform) in DictionaryStore.relationsOf(word)) {
                if (rform.isNotEmpty()) keys.add(rform.lowercase())
            }
            // タグ
            keys.addAll(DictionaryStore.tagsOf(word).map { it.lowercase() })
            // 内容テキスト
            for ((_, text) in DictionaryStore.contentsOf(word)) {
                if (text.isNotEmpty()) {
                    keys.addAll(text.split(Regex("\\s+")).filter { it.isNotEmpty() }.map { it.lowercase() })
                }
            }

            for (key in keys) {
                newIndex.getOrPut(key) { HashSet() }.add(id)
            }
        }

        index = newIndex
        idMap = newIdMap
        formSorted = newIdMap.values
            .map { DictionaryStore.formOf(it).lowercase() to DictionaryStore.idOf(it) }
            .sortedBy { it.first }
    }

    fun lookup(id: Int): JSONObject? = idMap[id] ?: store.findById(id)

    fun allWords(): Collection<JSONObject> = idMap.values

    /**
     * 小文字形が [prefix] で前方一致する単語を返す（総当たりせず二分探索で範囲を絞る）。
     * [prefix] が空の場合は全件を返す。
     */
    fun wordsWithFormPrefix(prefix: String): List<JSONObject> {
        if (prefix.isEmpty()) return idMap.values.toList()
        val from = formSorted.lowerBound(prefix)
        val to = formSorted.lowerBound(prefix + Char.MAX_VALUE)
        if (from >= to) return emptyList()
        return (from until to).mapNotNull { idMap[formSorted[it].second] }
    }

    /** [target] 以上となる最初の要素の位置（下限）を返す。 */
    private fun List<Pair<String, Int>>.lowerBound(target: String): Int {
        var lo = 0
        var hi = size
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (this[mid].first < target) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** 検索を実行し、カスタムソート順で並べた結果を返す */
    fun search(mode: String, scope: String, text: String): List<JSONObject> {
        if (text.isEmpty()) return emptyList()
        val keyword = text.lowercase()

        val ids: Set<Int> = if (scope == "見出し語・訳語") {
            searchHeadwordTranslation(keyword, mode)
        } else {
            searchFulltext(keyword, mode)
        }

        val results = ids.mapNotNull { idMap[it] }
        return results.sortedWith { a, b ->
            TextProcessor.compareForms(
                DictionaryStore.formOf(a),
                DictionaryStore.formOf(b)
            )
        }
    }

    private fun normalizeForSearch(text: String): String {
        val pattern = store.getIgnoredPattern()
        if (pattern.isEmpty()) return text
        return try {
            Regex(pattern).replace(text, "")
        } catch (e: Exception) {
            text
        }
    }

    private fun searchHeadwordTranslation(keyword: String, mode: String): Set<Int> {
        val results = HashSet<Int>()
        for ((id, word) in idMap) {
            val forms = ArrayList<String>()
            forms.add(DictionaryStore.formOf(word).lowercase())
            for ((_, tforms) in DictionaryStore.translationsOf(word)) {
                forms.addAll(tforms.filter { it.isNotEmpty() }.map { it.lowercase() })
            }
            if (match(forms, keyword, mode)) results.add(id)
        }
        return results
    }

    private fun searchFulltext(keyword: String, mode: String): Set<Int> {
        val results = HashSet<Int>()
        for ((key, ids) in index) {
            if (match(listOf(key), keyword, mode)) results.addAll(ids)
        }
        return results
    }

    private fun match(forms: List<String>, keyword: String, mode: String): Boolean {
        return when (mode) {
            "部分" -> {
                val text = forms.joinToString(" ")
                keyword.split(Regex("\\s+")).filter { it.isNotEmpty() }.all { it in text }
            }
            "前方" -> forms.any { normalizeForSearch(it).startsWith(keyword) }
            "後方" -> forms.any { normalizeForSearch(it).endsWith(keyword) }
            "完全" -> keyword in forms
            else -> false
        }
    }
}
