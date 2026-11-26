# Simplified Chinese glossary (简体中文, zh-CN)

The words Tandem uses in Simplified Chinese, chosen once and used everywhere. Read this before translating or
reviewing any `values-zh-rCN/strings_*.xml`. When a choice changes, change it here first, then in every file.

## Voice

- **Register:** the Chinese of a mainland Android phone, the Nintendo Switch's Chinese menus and Steam China:
  short, plain, friendly. Labels are short verb or noun phrases (「设置」「排序方式」「隐藏」); help text is
  conversational, addressed to 你 (never 您: Tandem is a friend, not a bank). Drop filler like 进行、可以…的、
  该: 「重新扫描」, not 「进行重新扫描操作」.
- **Pip:** stays **Pip** in Latin letters, as a name (Chinese UIs keep short Latin mascot names; a transliteration
  like 皮普 reads stiff). Pip is a small, lively robot: says 我, short warm lines, the odd ！ or ～ but no internet
  slang and no baby talk: 「嗨！我是 Pip！」「一切安好。」「熬夜呢？我也是。」.
- **Playful where the English is:** the jokes are rewritten to land in Chinese, never translated word for word
  (「折腾党专区」 for "For tinkerers", 「真·全都玩过了！」, 「通关啦」).
- **Punctuation:** full-width ，。！？：；（）. Typed text, setting names and menu items quoted inside a sentence
  use “” (mainland standard; 「」 is Taiwan/Japan style and is not used). The path arrow › and the dot · stay as
  in English, with a space each side (「设置 › 主页与布局」). The ellipsis is …. Toasts with no full stop in English
  get none in Chinese either.
- **Spacing (one rule, everywhere):** Android's zh-CN style: a half-width space between Chinese and Latin letters or
  digits (「12 款游戏」「按 Y 打开」「Tandem 主题包」), none between Chinese and full-width punctuation, none
  between a number and %, ° or ×. Placeholders count as Latin: 「已删除 %1$s」. No space inside “”.
- **Counting:** games 款 (「12 款游戏」), systems/folders/things 个, songs 首, pictures 张, times 次, buttons 个,
  minutes 分钟 (short pills 分), hours 小时. Chinese has no plural forms: every `<plurals>` has `other` only.
- **Search examples** stay in English inside “”: the search keyboard types Latin letters only and search matches
  English names (“bigger icons”“nintendo”).
- **Menu paths quoted in text** use exactly the words of the menu items, as listed under
  [Menu items quoted elsewhere](#menu-items-quoted-elsewhere).

## Never translated

Tandem, Pip, Shizuku, Cocoon, ES-DE, RetroAchievements, Steam, Discord, GitHub, LaunchBox, libretro, IGDB,
SteamGridDB, ScreenScraper, HowLongToBeat, Twitch, Eden Duo, Peanuts Native, GameNative, Winlator, DroidDeck, the
emulators, system names (from `platforms.json`), game titles, song titles and soundtrack names (Calm, Meadow
Tales), file paths and formats (ROMs/…, gamelist.xml, PNG, CSV, APK), button letters (A, B, X, Y, L1, R1, L2,
R2, Start, Select, Home; the D-pad is 十字键), Xbox, PlayStation, "Wi-Fi", "USB", "MTP", "PIN", "API", "ID", "GPL-3.0", "OLED", "LCD", "HOME 菜单" (Nintendo China's own words for
the 3DS menu). **Deck** stays Deck: it is Tandem's name for the keyboard and trackpad, and Chinese players know
the word from Steam Deck.

## Android's own words (match the device)

| English | Chinese | Note |
| --- | --- | --- |
| Settings | 设置 | Android |
| Quick settings | 快速设置 | AOSP zh-CN |
| Home screen (Android's) | 主屏幕 | Android |
| Home app | 主屏幕应用 | Android's 「默认主屏幕应用」 |
| Wi-Fi / Bluetooth | Wi-Fi / 蓝牙 | Wi-Fi stays Latin (the Thor and most mainland phones show it so in their quick tiles; 「WLAN」 would read odd beside a game launcher); Bluetooth is 蓝牙 everywhere on Android |
| Airplane mode | 飞行模式 | Android |
| Battery saver | 省电模式 | Android |
| Notifications | 通知 | Android |
| Brightness / Volume / Muted | 亮度 / 音量 / 静音 | Android |
| Screensaver | 屏保 | Android's 「屏幕保护程序」, shortened as every phone says it |
| Wallpaper | 壁纸 | Android |
| Widget | 小组件 | What every mainland phone (HyperOS, ColorOS, HarmonyOS, iOS) says; AOSP's 微件 is rarely seen |
| Back up / Restore | 备份 / 恢复 | Android |
| Language / System default | 语言 / 跟随系统 | 「跟随系统」 is what mainland phones say |
| Auto-rotate | 自动旋转 | Android |
| Refresh rate | 刷新率 | Android |
| Do Not Disturb | 勿扰模式 | Android |
| Accessibility | 无障碍 | Android |
| All apps | 所有应用 | Android |
| App info | 应用信息 | Android |
| Uninstall | 卸载 | Android |
| Permission / Allow | 权限 / 允许 | Android |
| "Use this folder" (folder picker) | “使用此文件夹” | Android's button |
| Undo | 撤销 | Android |
| Search | 搜索 | |
| Back / Close / Cancel / Done / OK | 返回 / 关闭 / 取消 / 完成 / 确定 | |
| On / Off | 开 / 关 (values), 开启 / 关闭 (actions) | |

## Tandem's words

| English | Chinese | Why |
| --- | --- | --- |
| Home (Tandem's home) | 主页 | Keeps 主屏幕 for Android's Home screen |
| Home layout | 主页布局 | |
| Library | 游戏库 | Steam China says 库; 游戏库 is clearer on its own |
| System (a console or platform) | 平台 | What Chinese emulation players say (「平台列表」); covers PC and arcade, and keeps 系统 free for Android |
| Game | 游戏 (counted 款) | |
| Emulator | 模拟器 | |
| Top screen / Bottom screen | 上屏 / 下屏 | What every Chinese DS/3DS player says |
| Other screen | 另一块屏幕 (short: 另一屏) | |
| Two screens (Settings page) | 双屏 | |
| Look & feel (page) | 外观与风格 | |
| Home & layout (page) | 主页与布局 | |
| Sound & music (page) | 声音与音乐 | |
| Controls (page) | 操控 | Game menus say 操控 / 操作 |
| Library & art (page) | 游戏库与图片 | |
| Play & system (page) | 游玩与系统 | |
| For tinkerers | 折腾党专区 | 折腾 is exactly what Chinese players call fiddling with settings for fun |
| Your data | 我的数据 | |
| Start panel | Start 面板 | Named after its button |
| Side panel | 侧边栏 | |
| Theme / Theme pack | 主题 / 主题包 | |
| Accent colour | 强调色 | Android |
| Highlight / highlighted | 选中 / 选中项 | |
| Highlight style | 选中样式 | |
| Surface | 卡片材质 | |
| Reduce motion | 减弱动态效果 | iOS and Android accessibility wording |
| Animation style | 动画风格 | |
| Motion intensity | 动态幅度 | |
| Floating titles | 漂浮标题 | |
| Icon grid | 图标网格 | |
| Icon style | 图标画风 | 画风 is the gamer's word for art style |
| System icons | 平台图标 | |
| Art set (Tandem's drawn icons, heroes, logos) | 美术套组 | Set names are transcreated: 马卡龙糖果、霓虹街机厅、纸标签卡带、极简黑白 |
| System art / Fetch system art / Real art | 平台图片 / 刮削平台图片 / 真实图片 | Photos and logos of the systems themselves, from the web. Styles: 真机照片、标志、同人画背景 |
| Dock (apps dock) | 程序坞 | Apple China's word; known to every phone user |
| Folder / Smart folder | 文件夹 / 智能文件夹 | |
| Smart folder rules | 规则 | 「编辑规则」 |
| Collection | 合集 | |
| Genre | 类型 | Steam China's 类型 |
| Tile | 图块 | |
| Empty spot | 空位 | |
| Shelves (home layout) | 陈列架 | Rows of games, like a shop display |
| Shelf (games view, spines on a bookcase) | 书架 | |
| Shelf (dock style) | 搁板 | |
| Case (cartridges in a case) | 卡带盒 | |
| Grid / List | 网格 / 列表 | |
| Carousel | 轮播 | |
| Console (home layout) | 主机风 | |
| Orbit | 轨道 | |
| Spotlight | 聚光灯 | |
| Classic | 经典 | |
| Showcase (setting preview) | 示例 | |
| Box art | 封面 | What players call it |
| Cover frame | 封面边框 | |
| Hero (wide art) | 主视觉图 (short: 主视觉) | The industry's KV word, known from game sites |
| Logo | 标志 | |
| Art / artwork | 图片 / 美术图 | |
| Video snap | 预览视频 | |
| Moving art | 动态画面 | |
| Physical box | 实体包装 | |
| Scrape (verb) / a scrape | 刮削 | The Chinese retro-handheld community's own word for it (「刮削封面」) |
| Scrape library | 刮削游戏库 | |
| Artwork & details | 图片与资料 | |
| Art source / Set up sources | 来源 / 配置来源 | |
| Region & language (scrape) | 地区与语言 | |
| Game folders | 游戏文件夹 | |
| Rescan | 重新扫描 | |
| Carry / Paste (moving a tile) | 拿起 / 粘贴 | |
| Tidy up | 整理 | |
| Sort orders | 按名称 / 最近玩过 / 最常玩 / 最近添加 / 发行年份 / 评分 / 手动 | |
| Manual folder (not smart) | 普通文件夹 | |
| Details (game info) | 资料 / 详情 | |
| Edit details | 编辑资料 | |
| Manual | 说明书 | Nintendo's 电子说明书 |
| Resume (bring back a running game) | 继续 | Button hints 「继续」; the feature 「继续游戏」 |
| Resume page | 继续游戏页 | |
| Continue (row of recent games) | 继续游玩 | |
| Now playing | 正在玩 | |
| Running (a game still open) | 运行中 | |
| Recently played | 最近玩过 | |
| Game switcher | 游戏切换器 | |
| Play (button hint) | 开玩 | Short and lively, as hints are |
| Hidden games / Hide / Unhide | 隐藏的游戏 / 隐藏 / 取消隐藏 | |
| Tuck away | 藏起来 | |
| Private mode | 隐私模式 | |
| Favourite | 收藏 | The menu item is 「收藏」, its opposite 「取消收藏」 |
| Up next | 接下来玩 | |
| Pin to home | 固定到主页 | Android's 固定 |
| Button prompts | 按键图标 | |
| Button hints (bottom row) | 按键提示 | |
| Corner hints | 角落提示 | |
| Rebind buttons | 按键映射 | What players call remapping |
| Swap screens | 交换屏幕 | |
| Top bar | 顶栏 | |
| Connected controllers | 已连接的手柄 | Chinese players say 手柄 for a controller |
| Controller (Built-in) | 手柄（内置） | |
| Companion (bottom screen while playing) | 游戏助手 | Every mainland phone's in-game panel is 游戏助手: the same idea |
| Companion app (an app started beside a game) | 配套应用 | The usual word for an app that goes with something; keeps 游戏助手 for Tandem's own companion |
| Start on / Open games on / Always open on / Apps open on | 启动屏幕 / 启动屏幕 / 固定屏幕 / 应用打开屏幕 | Rows whose value is a screen: a noun that reads right before 上屏, 下屏 or 默认 (never 「…于」, which reads like a form) |
| Players (game facts) | 单人 / %1$s 人 | As Nintendo China prints it on the box (「1-4 人」) |
| Credits (the end of a game) | 片尾字幕 | 「看到片尾字幕了」: what players say, not the literal 制作人员名单 |
| Home rows (Shelves layout) | 主页栏目 | |
| Trophy | 奖杯 | PlayStation China |
| Bronze / Silver / Gold / Platinum | 铜 / 银 / 金 / 白金 | PlayStation China |
| Achievement | 成就 | Steam and Xbox China |
| Stats & trophies | 统计与奖杯 | |
| Play stats | 游玩统计 | |
| Play sessions / play history | 游玩记录 | |
| Session (the count) | 次游玩 / 游玩次数 | |
| Play time | 游玩时长 | |
| Status | 状态 | |
| Backlog | 待玩 | The pile of games waiting their turn |
| Playing | 在玩 | |
| Finished | 已通关 | 通关 is the word for seeing the credits |
| Completed | 完美通关 | 100%, everything done |
| Shelved | 搁置 | |
| Endless | 无尽 | |
| No status | 无状态 | |
| Performance mode / fan | 性能模式 / 风扇 | AYN's own Chinese words |
| Import details | 导入资料 | |
| Quick looks (presets) | 一键风格 | |
| Welcome (first run) | 欢迎 | |
| Update / Check | 更新 / 检查 | |
| UI sounds | 操作音效 | |
| Sound pack | 音效包 | |
| Pip's voice | Pip 的声音 | |
| Music (the setting) | 背景音乐 | Every Chinese game menu's 背景音乐 |
| My music | 我的音乐 | |
| Notes | 笔记 | |
| Journal | 日记 | |
| Gallery | 相册 | |
| Files | 文件 | |
| Screenshot / Clip | 截图 / 录屏片段 (short: 片段) | |
| Hold (a button) | 长按 | 「长按 Start」 |
| Chord (buttons together) | 组合键 | |

## Menu items quoted elsewhere

These menu items are named inside other strings ("Y › Move to folder on any tile"); the quote uses the item's
exact words.

| Item | Chinese | Key |
| --- | --- | --- |
| Start › New | Start › 新建 | `panels_start_new` |
| Start › Edit grid | Start › 编辑网格 | `panels_start_edit_grid` |
| Start › Hidden | Start › 已隐藏 | `panels_start_hidden` |
| Start › Stats & trophies | Start › 统计与奖杯 | `panels_start_stats` |
| Y › Move to folder | Y › 移到文件夹 | `grid_sel_move_to_folder` |
| Y › Favorite ("Add to favorites" in English text) | Y › 收藏 | `menus_game_favorite` |
| Y › Status › Backlog / Finished | Y › 状态 › 待玩 / 已通关 | `screens_status`, `data_status_*` |
| Y › Sort inside › Manual | Y › 内容排序 › 手动 | `grid_menu_sort_inside`, `grid_sort_manual` |
| Y › Edit details › Manual | Y › 编辑资料 › 说明书 | `menus_game_edit`, `library_manual_row` |
| Y › Select | Y › 多选 | `grid_menu_select` |
| Y › Move | Y › 移动 | `grid_menu_move` |
| Y › Always open on | Y › 固定屏幕 | `menus_app_screen` |
| Y › More | Y › 更多 | `menus_action_more` |
| Add here › Widget… | 添加到这里 › 小组件… | `grid_pick_add_here`, `grid_spot_widget` |
| Add to home | 添加到主页 | `grid_menu_add_home`, `menus_app_home_add` |
| Edit rules | 编辑规则 | `grid_smart_edit_rules` |
| Paste | 粘贴 | `grid_spot_paste` |
| Show hidden games | 显示隐藏的游戏 | `settings_library_hidden` |
| Show hidden apps | 显示隐藏的应用 | `home_apps_show_hidden` |
| Undo Cocoon import | 撤销 Cocoon 导入 | `library_cocoon_undo_row` |
| Disconnect Discord | 断开 Discord | `accounts_discord_disconnect` |
| Hold the Home button | 长按 Home 键 | `controls_hold_home` |
| X button | X 键 | `look_grid_x_button` |
| Swap A and B / Swap X and Y | 交换 A 和 B / 交换 X 和 Y | `look_controls_swap_ab/xy` |
| Video snaps | 预览视频 | `look_bottom_video_snaps` |
| Update (button) | 更新 | `accounts_update_update` |
| Type a colour… | 输入颜色… | `look_accent_type` |
| Set up sources | 配置来源 | `scrape_sources_set_up` |
| Settings pages and groups | 外观与风格 › 主题、游玩与系统 › 隐私与隐藏、游玩与系统 › 游玩、游戏库与图片 › 账号… | `settings_page_*`, `settings_group_*`, `settings_sub_*` |

## Swatches, wallpapers and styles

Accent swatches use the soft colour words of Chinese cosmetics and stationery: 薄荷绿, 蜜桃, 薰衣草, 天空蓝, 奶油黄,
樱花粉, 珊瑚橙, 亮粉, 葡萄紫, 海洋蓝, 青碧, 青柠, 金色, 雪白.

Wallpapers nod to what Chinese players remember: Clear shell is 透明壳 (the see-through consoles everyone wanted),
Tall grass is 草丛 (Pokémon's "walk into the grass"), Rainbow road is 彩虹之路 (Mario Kart's Chinese name),
Pocket LCD is 掌机液晶, 8-bit evening is 8 位黄昏, Synthwave is 合成器浪潮.

Animation styles: Bouncy Q 弹, Smooth 顺滑, Minimal 简约. Sound packs: Modern 现代, Playful 欢快, Chiptune 芯片音乐,
Bubbly 泡泡, Minimal 极简, Studio 录音棚, Glass 玻璃, Air 空气.

## Calendars (Settings › Look & feel › Calendar, `strings_calendar.xml`)

| English | Chinese | Note |
| --- | --- | --- |
| Calendar | 历法 | The setting (日历 is the app) |
| Gregorian / Hijri / Chinese | 公历 / 伊斯兰历 / 农历 | Options; "Gregorian + Chinese" is 公历 + 农历 |
| A Hijri date | 伊斯兰历1448年主马达·阿色尼月11日 | Months: 穆哈兰姆月, 色法尔月, 赖比尔·敖外鲁月, 赖比尔·阿色尼月, 主马达·敖外鲁月, 主马达·阿色尼月, 赖哲卜月, 舍尔邦月, 赖买丹月, 闪瓦鲁月, 都尔喀尔德月, 都尔黑哲月 |
| Lunar months | 正月, 二月 … 十月, 冬月, 腊月 | |
| Lunar days | 初一 … 初十, 十一 … 二十, 廿一 … 廿九, 三十 | Written in code (`CalendarText.chineseDay`): 十月十三 |
| Leap month | 闰六月 | |
| The year | 丙午马年 | The stem and branch (丙午) are put before the animal year (马年) in code |
