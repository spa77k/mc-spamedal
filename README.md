# SpaMedal

PaperMC サーバー向けの、スパコインと交換できる物理通貨「スパメダル」と、その両替所のプラグインです。
カジノなどのイベントで使う共通のメダルとして、プレイヤー間の手渡しやチェストショップでもやり取りできます。

## 動作環境

- Minecraft サーバー: PaperMC 26.2
- 対象 API: Paper API `1.21.4-R0.1-SNAPSHOT`
- Java: 21
- ビルドツール: Maven
- メインクラス: `dev.spa.spamedal.SpaMedalPlugin`
- 必須プラグイン: Vault（経済プラグインは EssentialsX を想定）

## メダル

| 額面 | 買うとき | 戻すとき |
|---|---|---|
| 1スパメダル | 2.5S | 2.0S |
| 10スパメダル | 25S | 20S |
| 100スパメダル | 250S | 200S |

- 素材はオウムガイの殻です。エンチャントの光を付け、名前は「1スパメダル」など、説明は「物理版スパコイン」です。
- 見た目は額面ごとの専用モデル `spamedal:medal_1`・`spamedal:medal_10`・`spamedal:medal_100` です。
  Java版は `mc-ecolife` の共通リソースパック、統合版はGeyserのパック `SpaMedal.mcpack` と `custom_mappings/spamedal.json` で表示します。
  原画と生成プロンプトは `mc-ecolife/assets/spamedal/` にあります。
- 本物かどうかは、アイテムに埋め込んだ額面のデータ（PersistentDataContainer の `spamedal:value`）だけで判定します。
  金床で同じ名前を付けただけのオウムガイの殻はメダルとして扱いません。
- 1枚ごとの通し番号は付けません。同じ額面どうしは64枚まで重なります。
- メダルを作業台・自動作業台・金床・砥石・鍛冶台の材料にはできません。オウムガイの殻はコンジットの材料なので、
  そのままだとメダルが消えてしまうためです。ただのオウムガイの殻は今までどおり使えます。

## 両替所

ロビーの村人「スパメダル両替所」を右クリックすると、両替画面が開きます。

- 1〜3段目: 額面ごとに「1枚・10枚・64枚買う」「1枚・10枚戻す」「全部戻す」
- 5段目: 崩す（100→10×10、10→1×10）と束ねる（10×10→100、1×10→10）。手数料はかかりません
- 戻すときだけ、買うときの値段に `redeem-rate`（既定0.8）を掛けた額になります

対象は持ち物欄だけで、防具・左手の枠は見ません。どの操作も、持ち物欄に入りきるかを先に確かめてから
お金を動かします。入りきらない・残高が足りないときは何も動きません。

### 村人の設置

`spamedal.admin` か、ロビーを編集できる `spsmc.lobby.break` を持つプレイヤーが使います。

- `/medalnpc here`: 足元に置きます。前の村人がいれば取り除いてから置きます
- `/medalnpc remove`: 取り除きます
- `/medalnpc status`: 設置場所と、村人がいるかどうかを表示します

村人はスコアボードタグ `spsmc_medal` で見分け、AI・ダメージ・取引・雷での変化を止めています。
設置場所とUUIDは `plugins/SpaMedal/npc.yml` に残します。

## 記録と監視

- `plugins/SpaMedal/medals.log`: 買う（`buy`）・戻す（`redeem`）・崩す（`split`）・束ねる（`merge`）を1行1件のJSONで追記します
- `plugins/SpaMedal/stats.yml`: 発行と回収の累計（1スパメダル換算）
- `/medal stats`: 発行・回収・出回っている枚数と、全部戻ったときの支払額を表示します
- 回収が発行を上回ったら、増殖の疑いとしてサーバーログに警告を出します

## 設定

`config.yml`

- `unit-price`: 1スパメダルの値段（既定2.5）
- `redeem-rate`: 戻すときの倍率（既定0.8。0〜1）
- `currency.symbol` / `currency.suffix`: 金額表示

`/medal reload` で読み込み直します。価格はメダルに書き込んでいないので、変えるとすでに出回っているメダルにも効きます。

## ビルドと検証

```bash
mvn -B package
```

```bash
python3 scripts/test-medal-paper.py
```

隔離Paper（`target/medal-paper-smoke`、`127.0.0.1:25586`）で、価格、偽物の判定、両替4種、
持ち物がいっぱいのとき、記録、コンジットの禁止、村人の設置を確かめます。
`server-data/` に本番と同じ `paper-26.2-129.jar` と、`plugins/Vault.jar`・`plugins/EssentialsX-2.22.0.jar` を置いておきます。
画面の見た目と、統合版での表示は実クライアントで確かめます。
