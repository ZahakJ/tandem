# Japanese glossary (日本語)

The words Tandem uses in Japanese, chosen once and used everywhere. Read this before translating or reviewing any
`values-ja/strings_*.xml`. When a choice changes, change it here first, then in every file.

## Voice

- **Register:** the Japanese of a Nintendo or Sony system menu. Labels are short nouns or noun phrases (「設定」
  「並べ替え」「非表示にする」); sentences and help text use です/ます, gentle and plain, never stiff
  (「〜することができます」 becomes 「〜できます」, 「〜を実行」 becomes a real verb).
- **Pip (ピップ):** a small, cheerful robot. Speaks です/ます with a light, friendly lilt and the odd ！: 「こんにちは、ピップです！」「なにもなし。」「夜ふかし？ぼくもです。」. Pip says ぼく. A little
  cute, never baby talk, never 〜だよん.
- **Katakana** where Japanese gamers and Android use it (ホーム、テーマ、ウィジェット、フォルダ、アイコン、
  ドック、エミュレーター), kanji where natural (設定、一覧、上画面、非表示、再開). Long-vowel marks follow Android
  and Steam Japan: フォルダ, ライブラリ, エミュレーター, コントローラー, ユーザー.
- **Punctuation:** full-width 、。！？（）：. No spaces between Japanese words. 「」 around names, typed text and
  menu items quoted in a sentence. The path arrow › and the dot · stay as they are, with a space each side as in
  English (「Y › フォルダへ移動」). Toasts that have no full stop in English get none in Japanese either.
- **No あなた:** Japanese drops "your" ("your games" is just ゲーム, "your systems" is ライブラリにある機種). Pip may say
  あなた in a warm line of its own, nowhere else.
- **Latin and digits** sit directly against Japanese with no space (「ゲーム12本」「Yで選択」), Android's own style
  for Japanese.
- **Counting:** games 本 (「12本」), things in general 個, systems 機種, songs 曲, pictures 枚, buttons 個,
  times played 回. Japanese has no plural forms: every `<plurals>` has `other` only.
- **Search examples** stay in English inside 「」: the search keyboard types Latin letters only, and search matches
  English names (「bigger icons」「nintendo」).
- **Menu paths quoted in text** use exactly the words of the menu items, as listed under
  [Menu items quoted elsewhere](#menu-items-quoted-elsewhere).

## Never translated

Tandem, Shizuku, Cocoon, ES-DE, RetroAchievements, Steam, Discord, GitHub, LaunchBox, libretro, IGDB, SteamGridDB,
ScreenScraper, HowLongToBeat, Sunshine, Moonlight, Eden Duo, Peanuts Native, the emulators, game titles, song
titles and soundtrack names (Plaza, Lagoon, Terrace), file paths and formats (ROMs/…, gamelist.xml, PNG, CSV), button
letters (A, B, X, Y, L1, R1, L2, R2, Start, Select, Home, D-pad is 十字ボタン), "USB", "Wi-Fi", "GPL-3.0", "PIN".
System names come from `platforms.json` and stay as they are there.

Two exceptions where Japan has its own word: **OLED** is 有機EL (what every Japanese spec sheet says), **LCD** is
液晶 in names and descriptions.

## Android's own words (match the device)

| English | Japanese | Note |
| --- | --- | --- |
| Settings | 設定 | Android |
| Quick settings | クイック設定 | Android |
| Home screen (Android's) | ホーム画面 | Android |
| Home app | ホームアプリ | Android |
| Wi-Fi / Bluetooth | Wi-Fi / Bluetooth | Android keeps them Latin |
| Airplane mode | 機内モード | Android |
| Battery saver | バッテリーセーバー | Android |
| Notifications | 通知 | Android |
| Brightness / Volume / Muted | 明るさ / 音量 / ミュート | Android |
| Screensaver | スクリーンセーバー | Android |
| Wallpaper | 壁紙 | Android |
| Widget | ウィジェット | Android |
| Back up / Restore | バックアップ / 復元 | Android |
| Language / System default | 言語 / システムのデフォルト | Android |
| Auto-rotate | 自動回転 | Android |
| Refresh rate | リフレッシュレート | Android |
| Do Not Disturb | サイレントモード | Android |
| All apps | すべてのアプリ | Android |
| App info | アプリ情報 | Android |
| Uninstall | アンインストール | Android |
| Permission / Allow | 権限 / 許可 | Android |
| "Use this folder" (folder picker) | 「このフォルダを使用」 | Android's button |
| Undo | 元に戻す | Android |
| Search | 検索 | |
| Back / Close / Cancel / Done | 戻る / 閉じる / キャンセル / 完了 | |
| On / Off | オン / オフ | |

## Tandem's words

| English | Japanese | Why |
| --- | --- | --- |
| Home (Tandem's home) | ホーム | What every Japanese console says |
| Home layout | ホームのレイアウト | |
| Library | ライブラリ | Steam Japan |
| System (a console or platform) | 機種 | What Japanese players say (対応機種); covers PC and arcade too, and keeps システム free for Android's system |
| Game | ゲーム (counted 本) | |
| Emulator | エミュレーター | |
| Top screen / Bottom screen | 上画面 / 下画面 | Nintendo DS and 3DS words: every Japanese player knows them |
| Other screen | もう一方の画面 | |
| Two screens (Settings page) | 2画面 | Short, like 「2画面表示」 |
| Look & feel (page) | 見た目と雰囲気 | |
| Home & layout (page) | ホームとレイアウト | |
| Sound & music (page) | サウンドとBGM | Japanese game menus say BGM |
| Music (the setting) | BGM | 「BGM音量」 is every Japanese game's option |
| Song | 曲 | |
| My music | マイミュージック | |
| Controls (page) | 操作 | |
| Library & art (page) | ライブラリとアート | |
| Play & system (page) | プレイとシステム | |
| For tinkerers | こだわり派向け | Playful: "for people who are particular" |
| Start panel | Startパネル | Named after its button |
| Side panel | サイドパネル | |
| Theme / Theme pack | テーマ / テーマパック | |
| Accent colour | アクセントカラー | |
| Highlight / highlighted | カーソル / 選択中 | Japanese menus call the highlight the cursor |
| Highlight style | カーソルの見た目 | |
| Surface | 質感 | What cards are "made of" |
| Reduce motion | 動きを減らす | iOS and Android accessibility wording |
| Animation style | アニメーションの種類 | |
| Motion intensity | 動きの大きさ | |
| Floating titles | ふわふわタイトル | Playful, and says exactly what happens |
| Icon grid | アイコングリッド | |
| Icon style | アイコンの描き方 | |
| System icons | 機種アイコン | |
| Art set (Tandem's drawn icons, heroes, logos) | アートセット | Set names are transcreated: ゆめかわパステル, ネオン街ゲーセン, 紙ラベルのカセット, すっきりモノトーン |
| System art / Fetch system art / Real art | 機種アート / 機種アートを取得 / 本物のアート | Photos and logos of the systems themselves, from the web. Styles: 本体の写真, ロゴ, ファンアートの背景 |
| Dock (apps dock) | ドック | |
| Folder / Smart folder | フォルダ / スマートフォルダ | |
| Smart folder rules | 条件 | 「条件を編集」 |
| Collection | コレクション | |
| Genre | ジャンル | |
| Tile | タイル | |
| Empty spot | 空きスペース | |
| Shelves (home layout) | シェルフ | Rows you scroll through, like a console's shelves |
| Shelf (games view, spines on a bookcase) | 本棚 | |
| Shelf (dock style) | 棚 | |
| Case (cartridges in a case) | ケース | |
| Grid / List | グリッド / リスト | |
| Carousel | カルーセル | |
| Console (home layout) | コンソール | |
| Orbit | オービット | |
| Spotlight | スポットライト | |
| Classic | クラシック | |
| Showcase (setting preview) | 見本 | |
| Box art | パッケージ | Japanese players call the box art the package (パッケージ画像) |
| Cover frame | パッケージ枠 | |
| Hero (wide art) | キービジュアル | The Japanese industry word for a game's main wide art |
| Logo | ロゴ | |
| Art / artwork | アート / アートワーク | |
| Video snap | プレビュー動画 | |
| Moving art | 動くアート | |
| Scrape (verb) / a scrape | 情報を取得 / 取得 | "Scraping" means nothing to players; it fetches |
| Scrape library | ライブラリの情報を取得 | |
| Art source / Set up sources | 取得元 / 取得元の設定 | Where a scrape fetches from |
| Region & language (scrape) | 地域と言語 | |
| Game folders | ゲームフォルダ | |
| Rescan | 再スキャン | |
| Carry / Paste (moving a tile) | 持ち運ぶ / 貼り付け | |
| Tidy up | 整理する | |
| Sort orders | 名前順 / 最近遊んだ順 / よく遊ぶ順 / 追加した順 / 機種順 / 手動 | |
| Sort by (the setting) | 並び順 | Never 並べ順 |
| Your data (page) | データ管理 | What Nintendo and Sony call it; 「あなたのデータ」 reads as translation |
| Developer / Publisher | 開発元 / 発売元 | |
| Art fade | アートのフェード | |
| Rows (horizontal: icon grid, widget row) | 段 (「2段」) | 列 reads as columns |
| Extras (Edit details › Extras) | おまけ | |
| Read / load (files) | 読みこむ | One spelling everywhere |
| Manual folder (not smart) | ふつうのフォルダ | |
| Details (game info) | ゲーム情報 / 詳細 | |
| Manual | 説明書 | Nintendo's 電子説明書 |
| Pip | ピップ | A character's name in katakana, as Japan writes every mascot (カービィ, ピカチュウ) |
| Resume (bring back a running game) | 再開 | |
| Resume page | 再開ページ | |
| Continue (row of recent games) | つづきから | The classic Japanese RPG menu word |
| Continue playing | つづきから遊ぶ | |
| Now playing | プレイ中 | |
| Running (a game still open) | 起動中 | |
| Recently played | 最近遊んだゲーム (short: 最近遊んだ) | |
| Game switcher | ゲーム切り替え | |
| Play (button hint) | 遊ぶ | |
| Hidden games / Hide / Unhide | 非表示のゲーム / 非表示にする / 再表示 | |
| Tuck away | こっそりしまう | Playful, a little secretive |
| Private mode | プライベートモード | |
| Favourite / Add to favorites | お気に入り / お気に入りに追加 | |
| Up next | 次に遊ぶ | |
| Pin to home | ホームにピン留め | Android's ピン留め |
| Button prompts | ボタンガイド | Japanese games' word for on-screen button marks |
| Button hints (bottom row) | 操作ガイド | |
| Corner hints | すみっこガイド | Playful: the little guide in the corner |
| Rebind buttons | ボタン割り当て | |
| Swap screens | 画面を入れ替え | |
| Top bar | 上部バー | |
| Deck (keyboard + trackpad on the bottom screen) | デッキ | Kept as Tandem's name for it, in katakana |
| Connected controllers | 接続中のコントローラー | |
| Controller (Built-in) | コントローラー（本体） | |
| Companion (bottom screen while playing) | おとも画面 (chip: おとも) | お供, the Japanese word for a sidekick (Monster Hunter's オトモ) |
| Companion app (started with a game) | おともアプリ | |
| Leave to emulator (bottom screen) | エミュレーターまかせ | |
| Single screen | 1画面モード | |
| Surprise me | おまかせ | |
| Time to beat (HowLongToBeat) | クリアまでの目安 (メイン / やりこみ / 100%) | |
| Lock / unlock (edited details) | ロック / ロック解除 | |
| Match (RetroAchievements) | 照合 | |
| Regions | 北米 / 欧州 / 日本 / 世界 | |
| Screenshot chip on the companion | スクショ | Short, what Japanese players say |
| Notebook (Notes) / General | ノート / 一般 | |
| A journal note on a session | ひとこと | Light, like a diary line |
| Glossary (note kind) / Symbol / Meaning | 用語集 / 記号 / 意味 | |
| Streak | 連続プレイ (🔥 %d日連続) | |
| Orbit (new design) | glowing ring of glassy orbs = 光るリング / ガラス玉; Continue card = 「つづきから」カード | |
| Trophy names | Transcreated: スタッフロール, 早起きは三文の徳, 積みゲーの山, 皆勤賞, あと1回だけ… | |
| Trophy | トロフィー | PlayStation's word |
| Bronze / Silver / Gold / Platinum | ブロンズ / シルバー / ゴールド / プラチナ | PlayStation's words |
| Achievement | 実績 | Xbox and Steam Japan |
| Stats & trophies | 記録とトロフィー | |
| Play stats | プレイ記録 | |
| Play sessions / play history | プレイ履歴 | |
| Session (the count) | プレイ回数 (counted 回) | |
| Play time | プレイ時間 | Nintendo |
| Status | ステータス | |
| Backlog | 積みゲー | The Japanese gamer's word for games bought and waiting |
| Playing | プレイ中 | |
| Finished | クリア済み | |
| Completed | コンプリート | |
| Shelved | 中断中 | |
| Endless | エンドレス | |
| No status | ステータスなし | |
| Performance mode / fan | パフォーマンスモード / ファン | |
| Rescan games | ゲームを再スキャン | |
| Import details | 情報をインポート | |
| Quick looks (presets) | おまかせスタイル | Playful: "leave it to us" styles |
| Welcome (first run) | ようこそ | |
| Update / Check | アップデート / 確認 | |
| UI sounds | 操作音 | What Japanese consoles call them |
| Sound pack | サウンドパック | |
| Pip's voice | ピップの声 | |
| Notes | メモ | |
| Journal | 日記 | |
| Gallery | ギャラリー | |
| Files | ファイル | |
| Widget editor | ウィジェットの編集 | |
| Android widget | Androidウィジェット | |
| Screenshot / Clip | スクリーンショット / クリップ | |

## Menu items quoted elsewhere

These menu items are named inside other strings ("Y › Move to folder on any tile"); the quote uses the item's
exact words.

| Item | Japanese | Key |
| --- | --- | --- |
| Start › New | Start › 新規作成 | `panels_start_new` |
| Start › Edit grid | Start › グリッドを編集 | `panels_start_edit_grid` |
| Start › Hidden | Start › 非表示 | `panels_start_hidden` |
| Start › Stats & trophies | Start › 記録とトロフィー | `panels_start_stats` |
| Y › Move to folder | Y › フォルダへ移動 | `grid_sel_move_to_folder` |
| Y › Add to favorites | Y › お気に入りに追加 | `menus_game_favorite` |
| Y › Status › Backlog / Finished | Y › ステータス › 積みゲー / クリア済み | library area |
| Y › Sort (inside) › Manual | Y › 中身を並べ替え › 手動 | `grid_menu_sort_inside`, `grid_sort_manual` |
| Y › Edit details › Manual | Y › 情報を編集 › 説明書 | `menus_game_edit` |
| Y › Select | Y › 選択 | `grid_menu_select` |
| Y › Move | Y › 移動 | `grid_menu_move` |
| Add here › Widget… | ここに追加 › ウィジェット… | `grid_pick_add_here`, `grid_spot_widget` |
| Add to home | ホームに追加 | `grid_menu_add_home` |
| Show hidden games | 非表示のゲームを表示 | `settings_library_hidden` |
| Show hidden apps | 非表示のアプリを表示 | `home_apps_show_hidden` |
| Update (button) | アップデート | |
| Type a colour… | 色を入力… | `look_accent_type` |
| Paste | 貼り付け | `grid_spot_paste` |

## Swatches, wallpapers and styles

Accent swatches take soft colour words, Japanese ones where Japanese has a prettier one: ミント, ピーチ, ラベンダー,
空色, クリーム, 桜色, コーラル, ホットピンク, ぶどう色, 海色, 青緑, ライム, 金色, 雪色.

Wallpapers nod to the Japanese memory of each console: Clear shell is スケルトン (what Japan called the see-through
consoles), Tall grass is 草むら (Pokémon's word), Rainbow road is レインボーロード (Mario Kart's Japanese name),
Pocket LCD is ポケット液晶, Pixel icons are ドット絵.

Animation styles: Bouncy ぽよん, Smooth なめらか, Minimal ひかえめ. Sound packs: Chiptune ピコピコ, Bubbly ぷくぷく,
Playful ポップ, Air そよかぜ.

Avoid モザイク for "mosaic" (in Japanese it means a censoring blur): Cover mosaic is パッケージのコラージュ.

## Calendars (Settings › Look & feel › Calendar, `strings_calendar.xml`)

| English | Japanese | Note |
| --- | --- | --- |
| Calendar | 暦法 | The setting (カレンダー is the app) |
| Gregorian / Hijri / Chinese | 西暦 / ヒジュラ暦 / 中国暦 | Options; "Gregorian + Hijri" is 西暦 + ヒジュラ暦 |
| A Hijri date | ヒジュラ暦1448年ジュマーダー・アル＝アーヒラ11日 | Month names in katakana: ムハッラム, サファル, ラビーウ・アル＝アウワル, ラビーウ・アッ＝サーニー, ジュマーダー・アル＝ウーラー, ジュマーダー・アル＝アーヒラ, ラジャブ, シャアバーン, ラマダーン, シャウワール, ズー・アル＝カアダ, ズー・アル＝ヒッジャ |
| Lunar months | 正月, 二月 … 十二月 | A lunar day: 十月13日 |
| Leap month | 閏六月 | |
| The year | 丙午年 | Its stem (丙) is put before the branch year (午年) in code |

## Bump and the Plaza (strings_plaza.xml)

| English | Japanese | Why |
| --- | --- | --- |
| Bump (the feature; code and old builds: Tandem Pass) | ばったり | The everyday "ばったり会う": playful, original, far from any console maker's feature name. 「ばったり」 in brackets inside a sentence, bare as a title, switch or channel name |
| the Plaza | ひろば | Soft hiragana, a place for everyone; the soundtrack Plaza stays Plaza |
| Me (the character maker) | わたし | Quoted when it names the screen: 「わたし」を編集 |
| bump into (someone) / bumping | ばったり会う / ばったり受付中 | 「サムさんとばったり！」, 「ばったり 3 回」, 「これまで 12 台の Tandem とばったり」. Never すれちがう / すれちがい: too close to a console maker's feature |
| card | カード | |
| Face details (Me: freckles, face paint, a plaster...) | 顔のアクセント | Playful, like a sticker on the cheek |
| Beard & moustache (Me) | ひげ | One word covers both |
| greeting | あいさつ | |
| wave back | 手をふり返す | |
| block | ブロック | Android and every app |
| Surprise me (random look) | おまかせ | As elsewhere |
| Nearby devices | 付近のデバイス | Android's Japanese |
| safety number | 安全番号 | |
