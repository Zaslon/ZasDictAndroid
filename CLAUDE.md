# CLAUDE.md

## バージョニング

規則の本文は README.md「バージョニング規則」に記載（`yyyymmdd` + 英小文字1文字、例 `20260808a`）。

コードを変更してリリース扱いにするときは、`app/build.gradle.kts` の
`versionName` を当日の日付＋英字で更新し、`versionCode` を +1 すること。
同日に複数回更新する場合は英字を `a` → `b` → `c` と進める。
