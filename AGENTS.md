# 開発・リリースの進め方

- 作業は`main`で進める。作業前に`git status --short`を確認し、既存の変更を上書き・削除しない。
- バージョンは勝手に上げない。`1.0.0`を維持する。
- リリースを更新するときは、既存の`v1.0.0`リリースの`spamedal-1.0.0.jar`を同じファイル名で差し替える（`gh release upload v1.0.0 … --clobber`）。コミットハッシュ付きの別名（`spamedal-1.0.0-<hash>.jar`）で追加しない。`SHA256SUMS`も一緒に差し替える。
- 差し替えたら、公開URLからJARを落とし直してSHA-256を確かめ、`../spsmc-infra/Dockerfile`の`SPAMEDAL_SHA256`だけを新しい値に変える。URLは変えない。
- 表記は「スパメダル」「スパコイン」に統一する。「spaメダル」「spaコイン」とは書かない。
- 変更したら`mvn -B package`と`python3 scripts/test-medal-paper.py`を通す。
