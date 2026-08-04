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

| 職業 | 足す | 外す |
| - | - | - |
| `origins-classes:miner` | `pacedmultimine:vein_mine`（鉱脈掘り） | — |
| `origins-classes:explorer` | `pacedmultimine:keen_eye`（見通す目） | `origins-classes:explorer_kit`（開始装備） |

- 能力の定義は**この jar の中**（`data/pacedmultimine/powers/`）。datapack に依存しない
- **上流の定義は書き換えない**（足す／外すだけ）ので、上流が職業の中身を変えても黙って壊れない
- 配ったことは**ログに出る**（`Dev に pacedmultimine:vein_mine を配った（origins-classes:miner）`）
- 自分が配ったぶんには印（source）を付けてあるので、職業を変えると自分のぶんだけ外れる

## 壊す順番

上流が返すのは**順序を持たない集合**なので、そのまま壊すとバラバラの順になる。
**掘った位置からの距離順**に並べ替えてから壊す（木なら下から上へ崩れる）。

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
  （`pacedmultimine:vein_mine`）を**登録する**ので、その登録内容がログイン時に同期される。
  片側だけに置くと、持っていない側が `Failed to load registry` で弾かれる（2026-08-04 に実際に踏んだ）
- ⚠ **`pack.mcmeta` が要る**。無いとクライアントが起動途中で止まる（`Missing metadata in pack`
  という警告が1行出るだけで、原因が非常に分かりにくい）
- どちらも `py verify/check_custom_mods.py` が機械で出す

## 確かめ方

`py verify/run_probe_client.py --scenario paced-multimine --server`
（丸太24段と鉄鉱石8段を掘り、**掘った直後はまだ上が残っている**ことと
**待てば最後まで消える**ことを対で見る）。
