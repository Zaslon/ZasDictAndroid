# ZasDict for Android

イジェール語用ローカル辞書ソフト [ZasDict](https://github.com/Zaslon/ZasDict)（PySide6/デスクトップ版）と同等の機能を持つ Android アプリです。
Kotlin + Jetpack Compose 製。モバイル向けに**画面分割を行わず、画面遷移ベースのUI**で構成しています。

## デスクトップ版との機能対応

| デスクトップ版 | Android版 |
|---|---|
| 検索欄＋結果リスト＋詳細ペインの分割表示 | 検索画面 → 詳細画面への遷移（分割なし） |
| 検索モード（前方/部分/後方/完全）、スコープ（見出し語・訳語/全文） | 検索画面のチップで切替（挙動は func.py と同一） |
| カスタムソート順 `eaoiuhkstcnrmpfgzdbv- ` | TextProcessor.kt に compare_forms を忠実移植 |
| 同音異義語の番号付け `form (2)` | 同様に実装 |
| OTM-JSON の開く/上書き保存/名前を付けて保存 | Storage Access Framework（前回の辞書を自動再読込） |
| 単語の新規登録（Ctrl+Enter）/編集/複製/削除（右クリック） | FAB で新規、リスト長押し・詳細画面のアイコンで編集/複製/削除 |
| 関係の対照関係（RECIPROCAL_MAP）自動登録 | 追加・削除とも相手側に自動同期、見出し語変更時の参照formも更新 |
| 更新履歴（<辞書名>_changelog.csv） | 既定ではアプリ内部に辞書ごとのCSVとして保持（SAFでは隣接ファイルを自動作成できないため）。手動配置した「辞書名_changelog.csv」を更新履歴画面から一度選択して連携すると、以後は辞書の保存（自動上書き保存を含む）と連動してそのCSVへ自動追記される。CSVエクスポートも可能 |
| ツール: 変換（kaiomom）/ IPA（ipa） | ツール画面のタブ（変換ロジックを忠実移植） |
| 凡例（legend） | 品詞・関係の凡例＋辞書統計画面 |
| 環境設定（フォント/サイズ/自動保存/Heksa） | フォントサイズ倍率・自動上書き保存・Heksa（イジェール文字フォント取込） |
| 辞書依存設定（punctuations / ignoredPattern） | 同等の編集画面（ignoredPattern は前方/後方一致検索に反映） |
| 未保存変更のタイトル「*」表示 | 同様に実装 |

OTM-JSON のロード→セーブでは JSONObject をそのまま保持するため、アプリが
関知しないフィールド（zpdicOnline 等のメタ情報）も保全されます。

## ビルド方法

1. Android Studio（Ladybug 以降推奨）で本フォルダを「Open」する
2. Gradle Sync 完了後、実機またはエミュレータで Run
   - minSdk 26（Android 8.0）/ targetSdk 35

コマンドラインの場合（Gradle 8.9 / JDK 17）:

```
gradle assembleDebug
```

生成物: `app/build/outputs/apk/debug/app-debug.apk`

## Heksa（イジェール文字フォント）について

フォントファイル（Fazik-regular.ttf）はリポジトリに含めていません。
環境設定 → 「フォントファイルを選択」から ttf を取り込み、
「Heksaを有効にする」をオンにすると見出し語・検索欄に適用されます。

## 既知の注意点

- kaiomom.py の `titauini()` では「3母音化」の結果が直後の行で上書きされて
  破棄されています（元コードのバグの可能性）。互換性のため同じ挙動を維持して
  いますが、`Kaiomom.kt` の `KAIOMOM_FAITHFUL_TITAUINI` を `false` にすると
  3母音化が有効な変換になります。
- 更新履歴は既定で辞書のURIごとに端末内へ保存されます。辞書ファイルを別の場所へ
  移動すると履歴の紐付けが変わります（エクスポートで退避可能）。
- 外部CSVと連携している場合、そのCSVを移動・削除すると追記に失敗します
  （保存時に通知され、保留中の履歴は保持されます）。更新履歴画面から再連携してください。
