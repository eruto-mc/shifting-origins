> ## ⚠⚠⚠ 2026-09-01: このリポジトリではもう作らない
>
> **ソースは `eruto-mc/eruto-origins/origins/src/main/java/net/erutobusiness/shiftingorigins/` へ移した。**
>
> ⚠⚠ **ここを直しても、部員に届く jar に1バイトも入らない。**
> ⚠ `build_bundle.py` はこのリポジトリを見ていない（`TOP` からも `BUILT` からも外した）。
>
> **なぜ移したか**（依頼者の指摘）:
> 「⚠ そもそも1つの実装にまとめていったら循環参照とかに困らない気がするけど、どういう発想なの？」
>
> ⚠ そのとおりで、循環参照は「実装を1つにした」から起きたのではなく、
> ⚠⚠ **ビルドの単位を2つのまま残した**から起きていた。
> 単位を1つにしたら、口も登録も要らなくなった。
>
> ⚠ **直す場所**: `eruto-mc/eruto-origins/origins/src/main/java/net/erutobusiness/shiftingorigins/`
> ⚠ **建て方**: `eruto-origins` で `./gradlew :origins:jarJar` →
> `py -3.12 tools/build_bundle.py --write`
>
> ⚠ 履歴のために残しているだけ。**下の説明は移す前のもの。**

# Shifting Origins — 種族・職業を「あとから変えられる」前提に作り直す

Origins（種族）と Origins: Classes（職業）を、**一度きりの選択ではなく、
いつでも選び直せるもの**として成立させる当部の自作MOD。
⚠ **クライアントとサーバの両方に置く。**

| 受け持ち | 中身 |
| - | - |
| 能力を配り直す | 職業・種族ごとに、当部の能力を足す／上流の能力を無害な定義で上書きする |
| 一括破壊を「少しずつ」にする | 木こりの伐採・鉱夫の鉱脈掘りを、同じ tick に全部ではなく数tickかけて壊す |
| **選び直しをやめられるようにする** | 珠で開いた選択画面を Esc と × で閉じられる。種族は元に戻り、珠も返る（2026-08-07 追加） |
| **珠の説明欄を読める文にする** | 「種族を『』にする。」と出ていたのを直す（2026-08-07 追加） |
| **スケルトンがイヌ獣人から逃げる** | バニラがオオカミへ使っている目標を、同じ数字でスケルトンへ足す（2026-08-24 追加。下記） |
| **人に乗っているあいだ、相手の名前札を消す** | ピクシーが頭に乗ると名前札が目の前に貼り付くので、**乗っている本人の画面からだけ**消す（2026-08-27 追加。下記） |
| **`[種族・職業]` を Tab とサイドバーにだけ出す** | 名札・チャット・死亡メッセージには出さない（2026-08-28 追加。下記） |
| **精気吸収をアンデッドに効かなくする** | 上流が説明に書いているのに実装していなかった除外を足す（2026-08-28 追加。下記） |
| **ブレイズボーンが溶岩入りの瓶を飲める** | 水筒の水で痛む種族に、痛まない水源を渡す（2026-08-28 追加。下記） |

## 人に乗っているあいだ、相手の名前札を消す（2026-08-27）

ピクシーは `medievalorigins:pixie/mount`（**両手を空にして人を右クリック**）で相手に乗れる。
乗ると視点が相手の頭のすぐ上に来るので、**相手の名前札が目の前に貼り付いて前が見えない**。

⚠ **消えるのは乗っている本人の画面だけ。** 他の人からは今までどおり見える。

⚠ **なぜチームを使わないか**: バニラのチームには `nametagVisibility=never` が在るが、
あれは**全員から**消える。「乗っている人の視界からだけ消す」は描画の話なので、
サーバー側では表現できない。

**やり方**: Forge が `EntityRenderer.render` に差し込んでいる `RenderNameTagEvent` を DENY にする。

```java
if (vehicle instanceof Player && event.getEntity() == vehicle) {
  event.setResult(Event.Result.DENY);
}
```

⚠ **DENY は `renderNameTag` の呼び出しごと飛ばす**ので、名前の下に出る順位表の点数も一緒に消える
（Forge のパッチを読んで確かめた。`renderNameTagEvent.getResult() != DENY && (…)` の中でだけ呼ばれる）。

⚠ 条件は「乗り物が**人**のとき」だけ。馬・ボート・トロッコでは効かない
（人に乗れるのはピクシーだけなので、種族を見に行かなくても同じことになる）。

置き場: [`MountNameTagClient`](src/main/java/net/erutobusiness/shiftingorigins/MountNameTagClient.java)。

## スケルトンがイヌ獣人から逃げる（2026-08-24）

バニラのオオカミとスケルトンの関係は**双方向**——
オオカミは追いかけて襲い、スケルトンのほうは避けて逃げる。
当部の種族「イヌ獣人」は追う側を datapack で作れたが、**逃がす側が作れなかった**。

⚠ **なぜ Java が要るか**: バニラのスケルトンは
`AbstractSkeleton.registerGoals()` で
`new AvoidEntityGoal<>(this, Wolf.class, 6.0F, 1.0, 1.2)` を持っている。
⚠ **避ける相手が `Wolf.class` というクラスで固定**されているので、プレイヤーを入れる手が data 側に無い。

⚠ 探した範囲: apoli に登録されている power の型を全部（100種超）見たが、他のモブを逃がす型は無い。
Origins の `origins:scare_creepers` は在るが、datapack の中身は
`{"type": "origins:scare_creepers"}` の1行だけで、実体は `ScareCreepersMixin` という
**Java 専用の型**。⚠ **1つのモブのために Java を書く必要があったこと自体が、data 側では無理だという証拠。**

**やり方**: `EntityJoinLevelEvent` で、スケルトンが世界に入った瞬間に
**バニラがオオカミへ使っているのと同じ目標を、同じ数字で**足す。

| | 値 | 出どころ |
| - | - | - |
| 優先度 | 3 | バニラの `AbstractSkeleton.registerGoals()` と同じ |
| 距離 | 6.0 ブロック | 同上（config で変えられる） |
| 歩き / 走り | 1.0 / 1.2 | 同上 |

⚠ **数字を自分で決めていない。** バニラの関係を写すのが狙いなので、独自の値を入れると前提から外れる。
⚠ 対象は `AbstractSkeleton` なので**スケルトン・ストレイ・ウィザースケルトン**の3種
（追う側が使う `#minecraft:skeletons` タグの中身と同じ）。
⚠ 種族の id は `world3:beastfolk_dog`。config `skeletonShy.enabled` で切れる。

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
| `origins-classes:cleric` | `shiftingorigins:potion_sharing`（分かち合う祈り） | — |
| `origins:elytrian` | `shiftingorigins:light_armor_iron`（軽装・鉄まで） | `origins:light_armor`（革／チェインまで）／`origins:claustrophobia`（閉所で衰弱＋鈍足） |

> **エリトリアンの罰則緩和（2026-08-04 ユーザー判断）**: 当部は elytraslot を入れており、
> **誰でも胸当てと両立してエリトラを装備できる**。その状態でエリトリアンだけが防具制限と
> 閉所の衰弱を背負うと「エリトリアンを選ぶと純粋に損」という逆転が起きるため、2つ緩めた。
> 建築が主活動の当部で「拠点にいるだけで弱る」のは特に噛み合わない。
> ⚠ `origins:more_kinetic_damage`（落下・壁激突 +50%）は**残す**——空を飛ぶ種族の味であり、
> elytraslot 組が負わない危険なので、ここが差になる。
> 詳しい経緯は作者側の選定記録にある（公開していない）。

- 能力の定義は**この jar の中**（`data/shiftingorigins/powers/`）。datapack に依存しない
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

⚠ **値だけ変えたいときは、型を替えずに数値を書き換えるほうがよい**（ユーザー方針 2026-08-04）。
`apoli:simple` に替えると**元が何の power だったか読めなくなる**。

⚠ **上書きは power を消さない。** `power has` は真のままなので、
効いたかどうかは**振る舞い**で判定する（台本 shifting-origins を参照）。

### ⚠ 「外す」は効いていない（2026-08-04 実測）

検証台本の逆向き判定3本
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
詳細は作者側の選定記録「3.5 実測で分かったこと」にある。

⚠ **実害があるのは `claustrophobia` だけ。** 開始装備は「配られない」ことが別途 OK で確認でき
（`explorer-no-kit`）。

> ⚠ **訂正（2026-08-07・対応済み）**: この根拠は消えたので、**上書きをやめて元に戻した**。**EnchantingInfuser は 2026-08-05 に除去済み**（決定の正は `build-checklist.py` の OVERRIDE）。いまのエンチャントは**バニラの台＋Easy Magic**（`EnchantmentTableBlockMixin` と accessor だけで計算は置き換えない）＋ Ensorcellation。Origins Classes は `common.minecraft.EnchantmentMenuMixin` で**バニラの `EnchantmentMenu` に当てており**、`check_mixin_targets.py` は「ずれ無し」＝当たっている。→ **`better_enchanting` はいま生きている見込みが高い**。当部が潰す理由が無くなったので、扱いは部長判断（`selection/origins-mod-interactions-2026-08-07.md`）。


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

## 珠で開いた選択画面をやめられるようにする（2026-08-07）

**要望**: 珠を使って選択画面を開き、「やっぱり変えない」で閉じたい。
解禁済みの種族の**能力を詳しく見る**のに選択画面が便利なので、見ただけで珠が消えるのは痛い。

上流は `ChooseOriginScreen.shouldCloseOnEsc()` が **`false` 固定**で、閉じる手段がまったく無い。
**理由はある**——層を空にしたまま閉じると `hasAllOrigins()` が偽のまま残り、
`SelectionInvulnerabilityMixin` が全ダメージを無効にする＝**無敵で詰む**。
だから「Esc を通す」だけでは悪化する。

### どう作ったか

```text
珠を使う ─ Mixin(HEAD)  ─→ OriginChangeCancel.remember()   いまの種族一覧と珠を控える
           上流の処理    ─→ 層を空にして選択画面を開かせる
         ─ Mixin(RETURN) → OriginChangeCancel.announce()   クライアントへ「やめられる」と伝える

Esc ─→ shouldCloseOnEsc()==true ┐
×  ─→ ボタン ──────────────────┴→ onClose() → CancelRequest → cancel()
                                                  ├ 控えた種族へ戻す
                                                  └ 珠を返す（クリエイティブでは返さない）
```

| 部品 | 置き場 |
| - | - |
| 控えと復元・珠の返却 | [`OriginChangeCancel`](src/main/java/net/erutobusiness/shiftingorigins/OriginChangeCancel.java) |
| 通信路（便り2本） | [`Net`](src/main/java/net/erutobusiness/shiftingorigins/Net.java) |
| クライアント側の印 | [`CancelClient`](src/main/java/net/erutobusiness/shiftingorigins/CancelClient.java) |
| 控えを取る・説明欄を直す | [`OrbOfOriginItemMixin`](src/main/java/net/erutobusiness/shiftingorigins/mixin/OrbOfOriginItemMixin.java) |
| Esc・× ボタン | [`ChooseOriginScreenMixin`](src/main/java/net/erutobusiness/shiftingorigins/mixin/client/ChooseOriginScreenMixin.java) |

### 詰まないようにしてある3点

| 守り | 中身 |
| - | - |
| **やめてよい場面だけ開ける** | 判断はサーバーが持つ。印が立つのは**珠を使った直後だけ**なので、`/origin gui` で開いた画面は今までどおり閉じられない（閉じられると層が空のまま残って無敵になる） |
| **戻す前に「もう選び終えていないか」を見る** | 選び終えた層は `origins:empty` ではないので触らないし、珠も返さない。便りが二重に届いても種族は巻き戻らない |
| **退場したら両側で控えを捨てる** | 残すと入り直したあとに古い種族へ戻せてしまう。捨てたあとは「やめられない＝選ぶしかない」に倒れるので、珠を消したまま何も選べない状態にはならない |

⚠ **サーバーを落とすと控えは消える**（記憶の上だけに持っている）。
珠を使ったまま落ちた場合、入り直すと選択画面はまた開くが、やめられない。

### 確かめ方（機械で判定している）

> ⚠ **訂正（2026-08-07）**: ここには当初「当部のハーネスで機械判定できない唯一の領域。
> 実プレイで確認するしかない」と書いていた。**誤り。**
> ハーネスには最初から **`"keep_screen": true`**（その手順では画面を閉じない）があり、
> 画面を**読む**手（`/probeclient screen` / `widget`）も揃っていた。
> 足りなかったのは**触る**手だけで、それは3本足せば済んだ。
> 「画面は機械で触れない」と決めつけて、**在るツールを確かめずに人へ送っていた。**

台本 `shifting-origins`（**作者環境の検証基盤**にある。この repo には入っていない）が見ている:

| 判定 | 何を見るか |
| - | - |
| `cancel-tooltip` | 珠の説明欄に「選び直せる」が出ている |
| `cancel-tooltip-broken` | **陰性対照**。壊れていた頃の文字（空のカギ括弧）が出て**いない** |
| `cancel-screen-open-esc` | **陽性対照**。そもそも選択画面が開いている（これが無いと以下が成立しない） |
| `cancel-esc` | Esc で閉じる |
| `cancel-esc-restored` | 閉じたあと**種族が元に戻り・珠が返ってくる** |
| `cancel-x-button` | × ボタンが見えて押せる状態で在り、**押せる** |
| `cancel-x-restored` | × でも同じ結果になる |
| `cancel-negative-esc` | **陰性対照**。`/origin gui` で開いた画面は Esc で**閉じない** |
| `cancel-negative-restore` | 陰性対照で空にした層を戻す（後始末＋以降の判定の関門） |

ハーネス側に足した3本（`work/verifyprobe/`）:

| コマンド | 何をするか |
| - | - |
| `/probeclient screenkey "escape" <ラベル> [閉じる期待]` | 開いている画面に**キーを渡す**（`Screen#keyPressed`＝実機と同じ入口）。⚠ `/probeclient key` は `KeyMapping.click()` なので画面には届かない |
| `/probeclient click "<文言>" <ラベル>` | 画面ごしにボタンを**押す**（`Screen#mouseClicked` をボタン中心へ） |
| `/probeclient tooltipmatch <枠> "<文言>" <ラベル>` | 説明欄にその文言が在るかを OK/NG で返す。⚠ 枠に **-1** で手に持っているもの |

## 珠の説明欄が「」になっていたのを直す（2026-08-07）

上流の `OrbOfOriginItem.appendHoverText` は、珠が指す種族が `Origin.EMPTY` と
**同一オブジェクトなら**「新しい〜を選び直せる」、違えば「〜を『(名前)』にする」と出す。

当部の珠は NBT に `Origin:"origins:empty"` を持つ。これはレジストリから引いた**別のオブジェクト**なので
後者に落ち、その名前は `Component.literal("")`（`Origin` クラスの静的初期化で空文字を渡している）。
結果、**「種族を『』にする。」**と表示されていた。

⚠ **NBT から `Origin` を外せば直る、ではない。** `getTargets` は `Origin` を持たない項目を**捨てる**ので、
対象0件＝「**全部の層を選び直す**」に化ける（種族用の珠を使ったのに職業まで選び直しになる）。
正しい直し方は「**名前が空のときに『選び直せる』側の文言を出す**」。

## 設定（`config/shiftingorigins-server.toml`）

| キー | 既定 | 内容 |
| - | - | - |
| `intervalTicks` | **1** | 何tickごとに壊すか。**0 なら上流どおり同じ tick で全部**。1＝毎秒20個 |
| `blocksPerBatch` | 1 | 1回に何個 |
| `maxTotalTicks` | 100 | 全体がこれを超えそうなら**1回あたりの個数を自動で増やす**（小さい鉱脈はゆっくり、巨木は待たされない） |
| `maxDistanceFromPlayer` | 48 | 掘っている間に離れたら、その先へ進まない |
| `oreVeinMaxBlocks` | 160 | **このMODが足した鉱脈掘りの上限**。木こり（上流）は255固定で、こちらの影響を受けない |

## ビルドと配置

```bash
export JAVA_HOME=/path/to/jdk-17      # JDK 17 ならどれでもよい
./gradlew build --no-daemon
# → build/libs/shifting_origins-<版>.jar をクライアントとサーバの mods/ へ置く
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
- どちらも作者環境の検査 `check_custom_mods` が機械で出す（この repo には入っていない）

## 確かめ方

台本 `paced-multimine`（同じく作者環境の検証基盤）

台本が見ているもの（2026-08-04 に拡張）:

| 判定 | 何を見るか |
| - | - |
| `pmm-log-early` / `pmm-log-mid` | 45段の幹を掘り、**時刻を変えて2回**「どこまで消えたか」を見る。**境目が上へ動いている**＝順々に壊れている |
| `pmm-log-done` | 最後には最上段まで倒れる（陽性対照。これが無いと「残っている」を「ゆっくり」と読めない） |
| `pmm-leaves-gone` | 伐採から十分待つと**葉が1枚も残っていない**＝FastLeafDecay と噛み合っている |
| `pmm-ore-connected` | **C の字に曲げた鉱脈**を掘り、直線では近いが繋がりでは遠い端が**まだ残っている**＝壊れ方が飛んでいない |
| `pmm-ore-done` | 鉱脈も最後まで掘り切れる（陽性対照） |

## `[種族・職業]` を Tab とサイドバーにだけ出す（2026-08-28）

それまでは datapack（`worlds/world-3/datapacks/club_stats`）が**チームの prefix** に飾りを入れていた。
prefix を読むのは `PlayerTeam.formatNameForTeam` で、これを呼ぶのは

| どこ | |
| - | - |
| サイドバー | 狙っているところ |
| Tab の一覧 | 狙っているところ |
| ⚠ 頭上の名札 | 出したくない |
| ⚠ チャット・死亡メッセージ | 出したくない |

の4か所。**1つの値を4か所が共有しているので出し分けができない。**
⚠ Forge の `PlayerEvent.NameFormat` でも消せない——patched な `Player.getDisplayName()` は、
あのイベントの**後で** `formatNameForTeam` を呼ぶ（bytecode で確認）。

### 面ごとに道が違う（1.20.1 の bytecode で確認）

**Tab** … `PlayerTabOverlay.getNameForDisplay`（難読名 `eqg.a(ffb)`）の分岐は2本で、
`PlayerInfo.getTabListDisplayName()` が null でなければ**それを使って `return`**する。
`formatNameForTeam` を通らないので、`PlayerEvent.TabListNameFormat` で入れれば**そこだけ**に出せる。
貼り直しは `ServerPlayer.refreshTabListName()`（Forge 追加）で、
**名前が変わったときだけ**全員へ `ClientboundPlayerInfoUpdatePacket` が飛ぶ。

**サイドバー** … 左の列は `formatNameForTeam` を通る。サーバ側から出し分ける手は無い。
そこで**飾りを「点の持ち主の名前」そのものにする**——サイドバーが並べているのは
持ち主の文字列で、実在のプレイヤー名である必要が無い。

| 心配 | 実際（javap で確認） |
| - | - |
| 持ち主の名前に長さ制限があるのでは | `Scoreboard.getOrCreatePlayerScore` は `computeIfAbsent` を2回呼ぶだけ。検査は無い。40文字の制限は `/scoreboard` コマンドの引数側だけ |
| 通信で切られるのでは | `ClientboundSetScorePacket` は `writeUtf`（既定 32767） |
| 仮の持ち主として隠されるのでは | バニラが隠すのは `#` で始まる名前。`[` は描かれる |

### オフラインの人も並ぶ（これが目的）

点は `scoreboard.dat` に残るので、**ログインしていない人の行も消えない**。
⚠ ただし**一度もログインしていない人には飾りが付かない**（種族はオンラインのときにしか読めない）。
入れば次の秒で付く。

### 和名は jar に焼いてある

サイドバーへ出すのは**ただの文字列**なので `Component.translatable` が使えず、
⚠ 専用サーバの `Language` は jar の en_us しか持たない。だから和名をサーバ側が持つしかない。

⚠⚠ **`data/shiftingorigins/origin_names.json` を手で書かない。**
`worlds/world-3/dev/work/export_origin_names.py` が、部員へ配る早見表と**同じ窓口**
（`handouts/build_origins_sheet.py` → `dev/work/ja_lang.py`）から書き出す。
種族・職業を足したり和名を直したら、あの script を回して jar を作り直す。

### ⚠ 止め方（配布物の作り直しは要らない）

`config/shiftingorigins-server.toml`:

```toml
[nameLabels]
    enabled = false
```

これで

1. 表示枠が `view.*` から元の `club.*` へ戻る
2. 旗（`#labels club.const`）が 0 に降り、datapack が**数秒で prefix を書き戻す**

⚠ **旗が無いときは datapack が今までどおり飾る**（`unless ... matches 1`）。
MOD が入っていない世界・MOD を止めた世界では、古い見え方へ倒れる。

## 精気吸収がアンデッドから吸えていた（2026-08-28）

上流（Medieval Origins Revival）の説明はこう書いている:

> "This affects all creatures for better or worse, **except summoned dead**."

⚠ **実装に除外が1つも無い。** 3か所を開いて確かめた:

| 開いた所 | 結果 |
| - | - |
| `revenant/siphon.json`（7,538バイト） | ⚠ 対象を絞る条件が無い。`bientity_condition` は無く、唯一の `apoli:in_tag` は**ダメージの種類**に掛かっていて、自分の吸収ダメージで再発火しないためのもの |
| `SummonedSkeleton.class` | ⚠ `siphon` の参照 **0件**・`isInvulnerableTo` の上書き無し |
| `SpellDamageAction.class` | ⚠ 召喚の判定 **0件**（`instanceof` は `LivingEntity` だけ） |

実害は**回復し放題**——自分で呼んだゾンビを攻撃し続ければ体力が戻り続ける。

### なぜ datapack でやらないか

⚠ 条件そのものは data 側にも在る（`apoli:entity_group`）。ただし当てるには
**上流の power を丸ごと写して置き直す**ことになり、7,538バイトを当部が抱えて
上流の変更に追随できなくなる。当部の方針は「**上流の定義は書き換えない（足す／外すだけ）**」。

### やり方

`LivingAttackEvent`（**ダメージが入る前**）で、ダメージの種類が
`medievalorigins:siphon` かつ相手が `MobType.UNDEAD` なら取り消す。

⚠ **召喚した分だけでなくアンデッド全体**（2026-08-28・あなたの判断）。
死んでいるものから生命力は吸えない、という筋のほう。

⚠ **回復も一緒に止まる見込み**——上流は「吸収ダメージが通ったか」を見てから回復させる
（`if_else_list` の中で `medievalorigins:spell_damage` を判定）ので、ダメージが入らなければ
回復の枝へ進まない。⚠ **これは bytecode の読みで、実測ではない。**

置き場: [`SiphonGuard`](src/main/java/net/erutobusiness/shiftingorigins/SiphonGuard.java)。
config `siphon.skipUndead` で切れる。

## ブレイズボーンが溶岩入りの瓶を飲める（2026-08-28）

あの種族は `world3:damage_from_canteens`（「水は毒」）を持っていて、
⚠ **水筒の水を飲むと 2 痛む**。一方 `world3:heat_tolerance` が面倒を見るのは**体温だけ**で、
⚠ **喉の渇きは普通にある**。つまり「渇くのに、水を飲むと痛む」種族だった。

Alex's Mobs の `alexsmobs:lava_bottle` は、**ガラス瓶を持って溶岩の源を右クリック**すると
手に入る（bytecode で確認: `Items.GLASS_BOTTLE` ＋ 6ブロックの `raycast` ＋ `FluidTags.LAVA`、
そして `setSecondsOnFire`）。⚠ **火が付く代償はブレイズボーンだけ無料**なので、
これを彼らの水にした。

### ⚠ タグでは飲めるようにならない

Tough As Nails の飲み物はアイテムタグで決まる（`toughasnails:thirst/N_thirst_drinks`）が、
⚠ **タグは「どれだけ回復するか」を決めているだけ**だった。
タグに入っているのは牛乳バケツ・ポーション・TaN 自身の瓶と水筒で、**全部もともと飲める物**。
⚠ `alexsmobs:lava_bottle` は `FoodProperties` を持たない素の `Item` なので、
タグに足しても右クリックで何も起きない。だから `PlayerInteractEvent.RightClickItem` で拾う。

### 回復量

⚠ **上流の「浄水の瓶」と同じ**（タグを読んで採った・自分で決めた数字ではない）:

| | 値 | 出どころ |
| - | - | - |
| 渇き | **5** | `toughasnails:thirst/5_thirst_drinks` に `purified_water_bottle` が居る |
| 水分 | **80** | `toughasnails:hydration/80_hydration_drinks` に同じく居る |

⚠ 満タンのときは飲まない（`ThirstHelper.canDrink`）。瓶は返す。

⚠ **Tough As Nails を必須依存にした**（実行時に `toughasnails.api.thirst` を触るので、
無い環境では右クリックした瞬間に落ちる）。`libs/toughasnails.jar` は
`dev/instance/mods` の実物の写し——⚠ **maven にしない**。渇きの値を上流の瓶に合わせているので、
版がずれると数字の根拠が消える。

置き場: [`LavaBottleDrink`](src/main/java/net/erutobusiness/shiftingorigins/LavaBottleDrink.java)。
config `lavaBottle.enabled` / `thirst` / `hydration`。
