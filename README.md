# Paced Multi Mine — 職業の一括破壊を「少しずつ」にする＋職業の能力を配る

Origins Classes の一括破壊（木こりの伐採／このMODが足す鉱夫の鉱脈掘り）を、
**同じ tick に全部ではなく、数tickかけて少しずつ**壊すようにする当部の自作MOD。
併せて**職業ごとの能力を配る**（下記）。⚠ **クライアントとサーバの両方に置く。**

## なぜ作ったか（2026-08-04）

- Origins Classes は **最大255ブロックを1tickで**壊す。小さい木なら気にならないが、
  巨木や大鉱脈は**一瞬で消えて手応えが無く**、負荷も1tickに集中する
- ユーザー要望は「ポコポコと時間をかけて壊れてほしい」。
  **Origins Classes 側にその設定は無い**（`MultiMineConfiguration` は対象ブロック・道具・
  プレイヤーへの作用の3つだけ）

一度は Vein Mining MOD を fork して同じことを作ったが（`worlds/veinmining/`）、
**一括破壊は Origins の職業の特権に一本化する**方針になったため、そちらは不採用。
このMODが後継で、**木こりにも鉱夫にも同じように効く**。

## どうやっているか

```
Origins Classes                          このMOD
─────────────────────────────────        ────────────────────────────
ServerPlayerGameModeMixin
  └ MultiMinePower.apply(...)  ──────→   @Inject(at=RETURN) で戻り値を横取り
       返り値＝壊すブロック一覧            ├ キューに積む
                                          └ **空の一覧を返す**
  └ 一覧を回して全部壊す（＝何もしない）
                                         LevelTickEvent
                                          └ 数tickごとに N 個ずつ
                                             player.gameMode.destroyBlock(pos)
```

- **破壊はプレイヤー自身の採掘経路（`ServerPlayerGameMode.destroyBlock`）を通す。**
  ドロップ・経験値・道具の消耗・保護MODの判定・他MODのイベントが手掘りとまったく同じに走る
  （FTB Ultimine も Ore Excavation も同じ考え方）
- **⚠ 再入防止が要る。** 自分が壊すたびに Origins Classes の mixin が再び発火して power が呼ばれる。
  キューが動いている間は横取り側で「空の一覧」を返して**何も積まない**
- 中断条件は Ore Excavation に倣う: 退場・死亡・次元移動・**メインハンドの道具が変わった**・距離超過

## 職業の能力を配る（2026-08-04 追加）

**当部の `origins_unlock` datapack で職業の中身を上書きしていたが、実測すると1つも効いていなかった。**
datapack 自体は有効（`/datapack list enabled` に出る）なのに、職業の powers だけ上流の定義が使われる。

| 職業 | 期待していた中身 | 実際（上書き前） |
| - | - | - |
| 鉱夫 | 上流2つ ＋ 鉱脈掘り | 上流2つだけ |
| 探索者 | 見通す目 ＋ 疾走 | **開始装備** ＋ 疾走 |

⚠ 探索者の開始装備は当部が 2026-08-02 に**意図的に外したはずのもの**
（職業を付け替えるたびに地図が9枚増えるため）。それが丸2日効いていなかった。

そこで `ClassPowers` が **1秒に1回だけ職業を見て、能力を足す／外す**。

| 素性・職業 | 足す | 外す |
| - | - | - |
| `origins-classes:miner` | `pacedmultimine:vein_mine`（鉱脈掘り） | — |
| `origins-classes:explorer` | `pacedmultimine:keen_eye`（見通す目） | `origins-classes:explorer_kit`（開始装備） |
| `origins:elytrian` | `pacedmultimine:light_armor_iron`（軽装・鉄まで） | `origins:light_armor`（革／チェインまで）／`origins:claustrophobia`（閉所で衰弱＋鈍足） |

> **エリトリアンの罰則緩和（2026-08-04 ユーザー判断）**: 当部は elytraslot を入れており、
> **誰でも胸当てと両立してエリトラを装備できる**。その状態でエリトリアンだけが防具制限と
> 閉所の衰弱を背負うと「エリトリアンを選ぶと純粋に損」という逆転が起きるため、2つ緩めた。
> 建築が主活動の当部で「拠点にいるだけで弱る」のは特に噛み合わない。
> ⚠ `origins:more_kinetic_damage`（落下・壁激突 +50%）は**残す**——空を飛ぶ種族の味であり、
> elytraslot 組が負わない危険なので、ここが差になる。
> 詳しい経緯は [selection/origins-vs-convenience-mods-2026-08-04.md](../../selection/origins-vs-convenience-mods-2026-08-04.md)。

- 能力の定義は**この jar の中**（`data/pacedmultimine/powers/`）。datapack に依存しない
- **上流の定義は書き換えない**（足す／外すだけ）ので、上流が職業の中身を変えても黙って壊れない
- 配ったことは**ログに出る**（`Dev に pacedmultimine:vein_mine を配った（origins-classes:miner）`）
- 自分が配ったぶんには印（source）を付けてあるので、職業を変えると自分のぶんだけ外れる

## 壊す順番 — 掘った所から繋がりを辿る（幅優先）

上流が返すのは**順序を持たない集合**なので、そのまま壊すとバラバラの順になる。
**掘った位置から隣り合いを辿った順**（幅優先）に並べ替えてから壊す。
こうすると**壊す位置は必ず、直前までに壊した所と隣り合っている**＝壊れ方が繋がって進む。

⚠ **2026-08-04 に「掘った位置からの直線距離順」から変えた。** まっすぐな幹では結果が同じだが、
**曲がった鉱脈では破壊の先頭が飛ぶ**: C の字に曲がった鉱脈だと、掘った所から直線では近いが
繋がりでは遠い端が、**宙に浮いたまま先に消える**（試験場の形で数えると 36 個中 8 番目。
繋がり順なら 32 番目）。検証台本 `paced-multimine` の `pmm-ore-connected` がこれを見ている。

## 葉を早く消すMOD（FastLeafDecay）との共存

**噛み合わせに手当てが要った**（2026-08-04）。FastLeafDecay（Olafski・当部は 31）は

1. `BlockEvent.NeighborNotifyEvent` を受けるたびに「4〜11tick 後にその葉を1回だけ叩く」予約を入れる
2. 叩かれた葉は、**近くに丸太があるあいだ（`distance` が 7 未満）消えない**
3. **予約は撃って消えなければ捨てられ、予約し直されない**（`FldScheduler`）

上流の「1tick で全部」なら幹が同時に消えるので取りこぼさない。ところがこちらは幹を数tickかけて
下から消すので、**下のほうの丸太に隣した葉は、まだ上の幹が立っているうちに予約を撃たれて**
消えないまま予約を失う。＝**浮いた葉が取り残される。**

そこで**全部壊し終わってから、壊した位置のうち葉が隣にあるものへ近隣通知を投げ直す**
（`PacedBreakQueue.refreshLeafNeighbours`）。そのときは丸太が1本も残っていないので、
次の予約は必ず消える側に働く。`ServerLevel.updateNeighborsAt` が `NeighborNotifyEvent` を出すことは
1.20.1 Forge 47.3.0 の bytecode で確認済み。

## 設定（`config/pacedmultimine-server.toml`）

| キー | 既定 | 内容 |
| - | - | - |
| `intervalTicks` | **1** | 何tickごとに壊すか。**0 なら上流どおり同じ tick で全部**。1＝毎秒20個 |
| `blocksPerBatch` | 1 | 1回に何個 |
| `maxTotalTicks` | 100 | 全体がこれを超えそうなら**1回あたりの個数を自動で増やす**（小さい鉱脈はゆっくり、巨木は待たされない） |
| `maxDistanceFromPlayer` | 48 | 掘っている間に離れたら、その先へ進まない |
| `oreVeinMaxBlocks` | 160 | **このMODが足した鉱脈掘りの上限**。木こり（上流）は255固定で、こちらの影響を受けない |
| `leafRefreshDelayTicks` | 3 | 全部壊し終わってから、葉を早く消すMODへ**通知を投げ直す**までの猶予。`-1` で投げ直さない（上の「葉を早く消すMODとの共存」を参照） |

## ビルドと配置

```bash
export JAVA_HOME="c:/@projects/minecraft-club/worlds/world-3/dev/jdk17/jdk-17.0.19+10"
./gradlew build --no-daemon
cp build/libs/paced_multimine-1.0.0.jar ../../dev/instance/mods/
cp build/libs/paced_multimine-1.0.0.jar ../../dev/server/mods/
```

- **`libs/` に3つ要る**（`.gitignore` 済み・`dev/instance/mods/` からコピーする）:
  `origins-classes.jar`（mixin の対象）／`apoli.jar`（能力の付与 API）／`origins.jar`（職業の判定）。
  apoli は `origins-forge` の jar の中（`META-INF/jarjar/`）から取り出す。
  ⚠ mixin の対象をクラス名の**文字列**で指定していても、**注釈処理の段階で実物が要る**
  （2026-08-04 に無しで組んで `Mixin target ... could not be found` で落ちた）
- ⚠ **両側に置く**（`mods.toml` も `side=BOTH`）。このMODは Apoli の power 工場
  （`pacedmultimine:vein_mine`）を**登録する**ので、その登録内容がログイン時に同期される。
  片側だけに置くと、持っていない側が `Failed to load registry` で弾かれる（2026-08-04 に実際に踏んだ）
- ⚠ **`pack.mcmeta` が要る**。無いとクライアントが起動途中で止まる（`Missing metadata in pack`
  という警告が1行出るだけで、原因が非常に分かりにくい）
- どちらも `py verify/check_custom_mods.py` が機械で出す

## 確かめ方

`py verify/run_probe_client.py --scenario paced-multimine --server`

台本が見ているもの（2026-08-04 に拡張）:

| 判定 | 何を見るか |
| - | - |
| `pmm-log-early` / `pmm-log-mid` | 45段の幹を掘り、**時刻を変えて2回**「どこまで消えたか」を見る。**境目が上へ動いている**＝順々に壊れている |
| `pmm-log-done` | 最後には最上段まで倒れる（陽性対照。これが無いと「残っている」を「ゆっくり」と読めない） |
| `pmm-leaves-gone` | 伐採から十分待つと**葉が1枚も残っていない**＝FastLeafDecay と噛み合っている |
| `pmm-ore-connected` | **C の字に曲げた鉱脈**を掘り、直線では近いが繋がりでは遠い端が**まだ残っている**＝壊れ方が飛んでいない |
| `pmm-ore-done` | 鉱脈も最後まで掘り切れる（陽性対照） |
