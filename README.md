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

| 種族・職業 | 足す | 外す |
| - | - | - |
| `origins-classes:miner` | `shiftingorigins:vein_mine`（鉱脈掘り） | — |
| `origins-classes:explorer` | `shiftingorigins:keen_eye`（見通す目）／`shiftingorigins:tireless`（疲れ知らず） | `origins-classes:explorer_kit`（開始装備） |
| `origins-classes:cleric` | `shiftingorigins:potion_sharing`（分かち合う祈り） | `origins-classes:better_enchanting`（エンチャ台の本棚パワー +10） |
| `origins:elytrian` | `shiftingorigins:light_armor_iron`（軽装・鉄まで） | `origins:light_armor`（革／チェインまで）／`origins:claustrophobia`（閉所で衰弱＋鈍足） |

> **エリトリアンの罰則緩和（2026-08-04 ユーザー判断）**: 当部は elytraslot を入れており、
> **誰でも胸当てと両立してエリトラを装備できる**。その状態でエリトリアンだけが防具制限と
> 閉所の衰弱を背負うと「エリトリアンを選ぶと純粋に損」という逆転が起きるため、2つ緩めた。
> 建築が主活動の当部で「拠点にいるだけで弱る」のは特に噛み合わない。
> ⚠ `origins:more_kinetic_damage`（落下・壁激突 +50%）は**残す**——空を飛ぶ種族の味であり、
> elytraslot 組が負わない危険なので、ここが差になる。
> 詳しい経緯は [selection/origins-vs-convenience-mods-2026-08-04.md](../../selection/origins-vs-convenience-mods-2026-08-04.md)。

- 能力の定義は**この jar の中**（`data/pacedmultimine/powers/`）。datapack に依存しない
- **上流の定義は書き換えない**（足す／外すだけ）ので、上流が職業の中身を変えても黙って壊れない
- 配ったことは**ログに出る**（`Dev に shiftingorigins:vein_mine を配った（origins-classes:miner）`）
- 自分が配ったぶんには印（source）を付けてあるので、職業を変えると自分のぶんだけ外れる

### 上流の power を上書きする（2026-08-04〜）

`data/origins/powers/` と `data/origins-classes/powers/` に、**上流と同じ ID**の JSON を置き、
**`loading_priority: 100`** を書く。Origins の公式ドキュメントが案内している正規のやり方
（「Higher numbers mean it's loaded later, which means it will override those with lower
loading priorities which share the same ID」。既定MODの値は 0）。

| power | 上書きの形 |
| - | - |
| `origins:light_armor` | **値だけ**（型は `origins:restrict_armor` のまま、閾値を 999 にして実質無効化） |
| `origins:claustrophobia` | 型ごと `apoli:simple` へ |
| `origins-classes:explorer_kit` | 型ごと `apoli:simple` へ |
| `origins-classes:better_enchanting` | 型ごと `apoli:simple` へ |

⚠ **値だけ変えたいときは、型を替えずに数値を書き換えるほうがよい**（ユーザー方針 2026-08-04）。
`apoli:simple` に替えると**元が何の power だったか読めなくなる**。

⚠ **上書きは power を消さない。** `power has` は真のままなので、
効いたかどうかは**振る舞い**で判定する（台本 shifting-origins を参照）。

### ⚠ 「外す」は効いていない（2026-08-04 実測）

台本 [shifting-origins](../../dev/verify/scenarios/shifting-origins.json) の逆向き判定3本
（`explorer_kit` / `better_enchanting` / `claustrophobia`）が**2回とも NG**。サーバログ:

```
[shifting] Dev から origins-classes:explorer_kit を外した（source=origins-classes:explorer / …）
[shifting] Dev から origins:claustrophobia を外した（source=origins:elytrian / …）
[shifting] Dev から origins-classes:explorer_kit を外した（…）   ← 毎秒くり返している
```

`removePower` は呼べており source も正しい（`getSources` で引き直しても同じ値）。
それでも `power has` は真のまま＝**外した直後に上流が付け直している**。
**「外す」という手そのものが成立しない。**

**直す方向（未着手）**: 外すのをやめ、**power の定義を無害な内容で datapack から上書きする**。
同じ回で `so-loading-priority` が OK になり、**`loading_priority` を書けば datapack から
上書きできる**ことが確定したので、Java を書かずに済む。
詳細は [selection/origins-vs-convenience-mods-2026-08-04.md](../../selection/origins-vs-convenience-mods-2026-08-04.md) の「3.5 実測で分かったこと」。

⚠ **実害があるのは `claustrophobia` だけ。** 開始装備は「配られない」ことが別途 OK で確認でき
（`explorer-no-kit`）、`better_enchanting` は EnchantingInfuser に効かないので元から死んでいる。

## 壊す順番 — 掘った所から繋がりを辿る（幅優先）

上流が返すのは**順序を持たない集合**なので、そのまま壊すとバラバラの順になる。
**掘った位置から隣り合いを辿った順**（幅優先）に並べ替えてから壊す。
こうすると**壊す位置は必ず、直前までに壊した所と隣り合っている**＝壊れ方が繋がって進む。

⚠ **2026-08-04 に「掘った位置からの直線距離順」から変えた。** まっすぐな幹では結果が同じだが、
**曲がった鉱脈では破壊の先頭が飛ぶ**: C の字に曲がった鉱脈だと、掘った所から直線では近いが
繋がりでは遠い端が、**宙に浮いたまま先に消える**。検証台本の C字（36個）で数えると、
その端は**直線距離順では9番目・繋がり順では36番目（最後）**。
台本の `pmm-ore-connected` はこの差を見ていて、**この1件だけで新旧の並べ方を区別できる**。

## 葉を早く消すMOD（FastLeafDecay）との共存 — **手当ては要らなかった**

**結論: 何もしなくても噛み合っている**（2026-08-04 実測）。

一度は「取りこぼしが起きる」と読んで手当てを入れた（壊し終わってから葉へ通知を投げ直す
`leafRefreshDelayTicks`）。**が、陰性対照を取ったら差が出なかったので外した。**

読み違えた筋道と、抜けていた1歩:

| | |
| - | - |
| 正しく読めていた所 | FastLeafDecay は葉1枚につき**1回だけ**「4〜11tick 後に叩く」予約を入れ、**撃って消えなければ捨てる**。葉は近くに丸太があるあいだ（`distance` が 7 未満）消えない。だから幹を下から順に消すと**予約を失う葉が出る** |
| **抜けていた所** | **葉が1枚消えると、そこが空気になって近隣通知が飛び、隣の葉が予約し直される。** 葉は繋がった塊なので、どれか1枚が消えれば端からほどけていく。予約を失った葉も拾い直される |
| 逃げ道が無いことの確認 | 「隣に葉が1枚も無い葉」なら拾い直されないが、その葉は丸太が消えた時点で `distance` が即 7 になるので**自分の予約で消える** |

**実測**（検証台本 `paced-multimine`・`pmm-leaves-gone`）: 投げ直す版3回・投げ直さない版1回の
いずれも、低い位置の葉・その列の上端・樹冠の角がすべて消えた。
投げ直す版でも**44個壊して 2〜4 箇所しか対象が無かった**＝その時点で葉はもう消えていた。

⚠ **これは FastLeafDecay の中で完結する話だった。** こちらが読む必要も、手を出す必要も無かった。
判断の材料は「台本に葉の判定を1件足して1回回す」だけで足りた。

## 設定（`config/pacedmultimine-server.toml`）

| キー | 既定 | 内容 |
| - | - | - |
| `intervalTicks` | **1** | 何tickごとに壊すか。**0 なら上流どおり同じ tick で全部**。1＝毎秒20個 |
| `blocksPerBatch` | 1 | 1回に何個 |
| `maxTotalTicks` | 100 | 全体がこれを超えそうなら**1回あたりの個数を自動で増やす**（小さい鉱脈はゆっくり、巨木は待たされない） |
| `maxDistanceFromPlayer` | 48 | 掘っている間に離れたら、その先へ進まない |
| `oreVeinMaxBlocks` | 160 | **このMODが足した鉱脈掘りの上限**。木こり（上流）は255固定で、こちらの影響を受けない |

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
  （`shiftingorigins:vein_mine`）を**登録する**ので、その登録内容がログイン時に同期される。
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
