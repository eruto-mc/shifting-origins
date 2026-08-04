# Paced Multi Mine — 職業の一括破壊を「少しずつ」にする

Origins Classes の一括破壊（木こりの伐採／当部が datapack で足した鉱夫の鉱脈掘り）を、
**同じ tick に全部ではなく、数tickかけて少しずつ**壊すようにする当部の自作MOD。**サーバー専用**。

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

## 設定（`config/pacedmultimine-server.toml`）

| キー | 既定 | 内容 |
| - | - | - |
| `intervalTicks` | 2 | 何tickごとに壊すか。**0 なら上流どおり同じ tick で全部** |
| `blocksPerBatch` | 1 | 1回に何個 |
| `maxTotalTicks` | 100 | 全体がこれを超えそうなら**1回あたりの個数を自動で増やす**（小さい鉱脈はゆっくり、巨木は待たされない） |
| `maxDistanceFromPlayer` | 48 | 掘っている間に離れたら、その先へ進まない |

## ビルドと配置

```bash
export JAVA_HOME="c:/@projects/minecraft-club/worlds/world-3/dev/jdk17/jdk-17.0.19+10"
./gradlew build --no-daemon
cp build/libs/paced_multimine-1.0.0.jar ../../dev/server/mods/
```

- **`libs/origins-classes.jar` が要る**（`.gitignore` 済み・`dev/instance/mods/` からコピーする）。
  ⚠ mixin の対象をクラス名の**文字列**で指定していても、**注釈処理の段階で実物が要る**
  （2026-08-04 に無しで組んで `Mixin target ... could not be found` で落ちた）
- **client には置かない**（`mods.toml` も `side=SERVER`）。
  `MultiMinePower.apply` はサーバーでしか呼ばれない

## 確かめ方

`py verify/run_probe_client.py --scenario paced-multimine --server`
（丸太24段と鉄鉱石8段を掘り、**掘った直後はまだ上が残っている**ことと
**待てば最後まで消える**ことを対で見る）。
