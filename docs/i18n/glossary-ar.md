# Arabic glossary (العربية)

The words Tandem uses in Arabic, chosen once and used everywhere. Read this before translating or reviewing any
`values-ar/strings_*.xml`. When a choice changes, change it here first, then in every file.

## Voice

- **Register:** friendly Modern Standard Arabic, the kind a good Arabic game menu or phone uses: short, warm, never
  legal or bureaucratic, never heavy dialect. Prefer the common word over the classical one (اضغط، اختر، شغّل).
- **Addressing the player:** imperatives and neutral phrasing first ("اختر"، "لعبتك"، "جاهز للعب"). Where gender
  can't be avoided, the usual masculine default of Arabic UI.
- **Pip (بيب):** a small, cheerful robot. Speaks in the first person, masculine (روبوت), in short light lines with a
  smile ("أهلًا! أنا بيب!"، "كل شيء هادئ."، "سهران؟ وأنا كذلك."). Warm, a bit cheeky, never childish.
- **Labels** are noun phrases or short imperatives, about as long as the English. Help text is conversational
  second person.
- **Punctuation:** Arabic comma ، semicolon ؛ question mark ؟. Quotes «…» for names and terms. The path arrow ›
  and the dot · stay as they are. **Western digits (0–9) always**, never Arabic-Indic (٠١٢): the owner's choice. Tandem
  formats every number, time, date and percentage in Arabic with them (`Lang.westernDigits`), so write `%1$d` and
  never type ٣ into a string. A list of names is joined with «، » (`common_list_sep`).
- **"Already"** is بالفعل (or من قبل). أصلًا in that sense is dialect; keep it only for "at all" ("بلا ساعة أصلًا").
- **No bare بلا / أيّ as a value:** an option reads as a whole phrase ("بلا تأثير"، "بلا حالة"، "الكل").
- **Directions:** the layout is mirrored for Arabic, so help text says where things are by order, not by side
  ("قبل الشبكة"، "بجانب الأغلفة"), never يسار/يمين. D-pad Left/Right (the buttons) are fine.
- **Search examples** stay in English inside «…» (the search keyboard types Latin letters only, and search matches
  the English names): «bigger icons»، «nintendo».
- **Numbers and plurals:** all six forms (zero, one, two, few, many, other). zero/one/two usually drop the number:
  لا ألعاب / لعبة واحدة / لعبتان / 3 ألعاب / 11 لعبة / 100 لعبة. Counted nouns after 11+ are singular accusative
  in form (لعبةً is written لعبة, no tanween in UI).

## Never translated

Tandem, Shizuku, Cocoon, ES-DE, RetroAchievements, Steam, Discord, GitHub, LaunchBox, libretro, IGDB, SteamGridDB,
ScreenScraper, HowLongToBeat, Sunshine, Moonlight, Eden Duo, Peanuts Native, the emulators, system and console
names (Switch, PlayStation, Game Boy…: Nintendo and Sony market them in Latin letters in the Arab world), game
titles, song titles and soundtrack names (Calm, Meadow Tales), file paths and formats (ROMs/…, gamelist.xml, PNG),
button letters (A, B, X, Y, L1, R1, L2, R2, Start, Select, Home), "OLED", "LCD", "USB", "Wi-Fi", "GPL-3.0".

## Android's own words (match the device)

| English | Arabic | Note |
| --- | --- | --- |
| Settings | الإعدادات | Android |
| Quick settings | الإعدادات السريعة | Android |
| Home screen (Android's) | الشاشة الرئيسية | Android |
| Wi-Fi | Wi-Fi | Android keeps it Latin |
| Bluetooth | بلوتوث | Android |
| Airplane mode | وضع الطيران | Android |
| Battery saver | توفير شحن البطارية | Android (AOSP) |
| Notifications | الإشعارات | Android |
| Brightness | السطوع | Android |
| Volume | مستوى الصوت | Android |
| Muted | صامت | Android |
| Screensaver | شاشة التوقف | Android |
| Wallpaper | الخلفية | Android |
| Widget | أداة (الأدوات) | Android |
| Back up / Restore | نسخ احتياطي / استعادة | Android |
| Language / System default | اللغة / لغة النظام | Android |
| Auto-rotate | التدوير التلقائي | Android |
| Refresh rate | معدّل التحديث | Android |
| Permission / Allow | إذن / السماح | Android |

## Tandem's words

| English | Arabic | Why |
| --- | --- | --- |
| Home (Tandem's home, the start screen) | الرئيسية | Short, what every Arabic launcher says |
| Home layout | تخطيط الرئيسية | Samsung's Arabic "تخطيط الشاشة الرئيسية" |
| Library | المكتبة | Standard (Steam, PlayStation Arabic) |
| System (a console or platform) | منصّة (منصّات) | Covers consoles, PC, Steam and arcade alike; "جهاز" would clash with the handheld itself |
| Game | لعبة (ألعاب) | |
| Emulator | محاكي (محاكيات) | Standard |
| Handheld / device | الجهاز / جهازك المحمول | |
| Top screen / Bottom screen | الشاشة العلوية / الشاشة السفلية | |
| Other screen | الشاشة الأخرى | |
| Two screens (Settings page) | الشاشتان | One word, dual form: neat and natural |
| Look & feel (page) | المظهر والأجواء | "Feel" as atmosphere; warmer than "الإحساس" |
| Home & layout (page) | الرئيسية والتخطيط | |
| Sound & music (page) | الصوت والموسيقى | |
| Controls (page) | التحكّم | |
| Library & art (page) | المكتبة والصور | "Art" is pictures here: covers, logos, videos |
| Play & system (page) | اللعب والنظام | |
| For tinkerers | لهواة العبث | Playful: "for those who love to fiddle" |
| Start panel | لوحة Start | Named after its button, which stays Start |
| Side panel | اللوحة الجانبية | |
| Theme | سمة (سمات) | Android's Arabic word |
| Theme pack | حزمة سمة (حزم السمات) | |
| Accent colour | لون التمييز | Android's Arabic |
| Highlight / highlighted | التحديد / المحدّد | What the cursor is on |
| Surface | الملمس | What cards are "made of": texture reads better than "سطح" |
| Reduce motion | تقليل الحركة | iOS/Android accessibility wording |
| Animation style | أسلوب الحركة | |
| Icon grid | شبكة الأيقونات | |
| Icon style | شكل الأيقونات | |
| System icons | أيقونات المنصّات | |
| Art set (Tandem's drawn icons, heroes, logos) | مجموعة الصور (مجموعات) | Set names are transcreated: حلوى الباستيل، سهرة النيون، خرطوشة بملصق ورقي، لون واحد هادئ |
| Dock (apps dock) | شريط التطبيقات | Says what it is; it can sit on top or bottom, so no "سفلي" |
| Folder / Smart folder | مجلد / مجلد ذكي | |
| Collection | مجموعة (مجموعات) | |
| Shelves (home layout) | الرفوف | |
| Shelf (games view) / bookcase | رف / خزانة الرفوف | "مكتبة" is taken by Library |
| Grid / List | شبكة / قائمة | |
| Carousel / coverflow | الدوّار / عرض دوّار | |
| Console (home layout) | الكونسول | What Arab gamers actually say |
| Case (cartridges in a case) | علبة الخراطيش | |
| Orbit | المدار | |
| Spotlight | تحت الأضواء (short: الأضواء) | The idiom says exactly "the star of the show" |
| Classic | كلاسيكي | |
| Showcase (setting preview) | معاينة | |
| Box art | الغلاف | |
| Hero (wide art) | صورة بارزة (صور بارزة) | A banner that stands out; "بطل" would mislead |
| Logo | شعار | |
| Cover frame | إطار الغلاف | |
| Video snap | مقطع فيديو قصير / لقطة فيديو | Row name: لقطات الفيديو |
| Moving art | صور متحرّكة | |
| Artwork / art | الصور | Arabic says pictures, not "فن" |
| Scrape (verb) / a scrape | جلب الصور والتفاصيل / عملية جلب | "Scrape" has no natural Arabic; it fetches |
| Scrape library | جلب صور المكتبة | |
| Art source / sources | مصدر الصور / المصادر | |
| Details (game info) | التفاصيل | |
| Manual | كتيّب (كتيّبات) | The paper booklet in the box |
| Pip | بيب | A character's name, written as Arabic speakers say it (like كيربي، ماريو) |
| Resume (bring back a running game) | الاستئناف / استئناف الألعاب | |
| Resume page | صفحة الاستئناف | |
| Continue (row of recent games) | تابِع اللعب | |
| Now playing | قيد اللعب الآن | |
| Recently played | لُعبت مؤخرًا | |
| Game switcher | مبدّل الألعاب | |
| Hidden games / Hide / Unhide | الألعاب المخفية / إخفاء / إظهار | |
| Tuck away | خبّئها | Playful, a little conspiratorial |
| Private mode | الوضع الخاص | Samsung's Arabic name for the same idea |
| PIN | رمز PIN | Android |
| Favourite | المفضّلة | |
| Button prompts | رموز الأزرار | |
| Rebind buttons | إعادة تعيين الأزرار | |
| Swap screens | تبديل الشاشتين | |
| Corner hints | تلميحات الزاوية | |
| Top bar | الشريط العلوي | |
| Deck (keyboard + trackpad on the bottom screen) | لوحة اللمس | It is a touch keyboard and trackpad: say so |
| Connected controllers | أذرع التحكّم المتصلة | "ذراع تحكّم" is the usual Arabic for a gamepad |
| Controller (Built-in) | ذراع التحكّم (المدمج) | |
| Companion (app/screen) | الرفيق | |
| Trophy | جائزة (جوائز) | PlayStation's Arabic word |
| Achievement | إنجاز (إنجازات) | Xbox's Arabic word |
| Bronze / Silver / Gold / Platinum | برونزية / فضية / ذهبية / بلاتينية | |
| Stats & trophies | الإحصاءات والجوائز | |
| Play time | وقت اللعب | |
| Session | جلسة لعب | |
| Status: Backlog / Playing / Finished / Completed / Shelved / Endless | قائمة الانتظار / قيد اللعب / أنهيتها / مكتملة 100% / مؤجّلة / بلا نهاية | Short tags |
| Performance mode / fan | وضع الأداء / المروحة | |
| Rescan games | إعادة البحث عن الألعاب | |
| Import details | استيراد التفاصيل | |
| Quick looks (presets) | إطلالات جاهزة | "Looks" as outfits: playful and exact |
| Floating titles | عناوين طائرة | |
| Motion intensity | قوة الحركة | |
| Welcome (first run) | الترحيب | |
| Update / Check | تحديث / تحقّق | |
| UI sounds | أصوات الواجهة | |
| Sound pack | حزمة أصوات | |
| My music / music folder | موسيقاي / مجلد الموسيقى | |
| Shuffle | تشغيل عشوائي | Android music apps |
| Soundtrack (built-in) | الموسيقى المدمجة | |
| Tile (on the grid or home) | مربع (مربعات) | |
| Hold (a button) | مطوّلًا: "Select مطوّلًا" | Button first, then the hold |
| Y › Move to folder | Y › نقل إلى مجلد | Menu item, quoted in other strings |
| Y › Edit details › Manual | Y › تعديل التفاصيل › الكتيّب | Menu path, quoted in other strings |
| Surprise me (Select) | فاجئني | |
| Up next (list) | «على الدور» | Playful "next in line"; quoted as a list name |
| Favorite / Unfavorite (menu) | أضف إلى المفضّلة / أزل من المفضّلة | Other strings quote "Y › أضف إلى المفضّلة" |
| Y › Status › Backlog / Finished | Y › الحالة › قائمة الانتظار / أنهيتها | |
| Y › Sort inside › Manual | Y › ترتيب المحتوى › يدوي | |
| Genre | نوع (أنواع) | |
| Pin / Unpin (home, dock) | ثبّت في / أزل من | |
| Paste (a carried tile) | ضع هنا | Also the A hint on an empty spot |
| Tidy up (close the gaps) | سدّ الفراغات | |
| Regex / Plain text | تعبير نمطي / نص عادي | |
| A–Z (sort) | أبجديًا | |
| Home layouts: Shelves, Icon grid, Carousel, List, Console, Orbit, Spotlight | الرفوف، شبكة الأيقونات، الدوّار، القائمة، الكونسول، المدار، تحت الأضواء | |
| Games views: Grid, List, Carousel, Shelf, Case | شبكة، قائمة، دوّار، رف، علبة | |
| Spotlight rail | سلسلة الأغلفة | |
| Running (a game) | تعمل / ● تعمل | |
| All apps (drawer) | كل التطبيقات / درج التطبيقات | |
| Immersion mode | وضع الانغماس | |
| Do Not Disturb | عدم الإزعاج | Android |
| Root (Shizuku as root) | بصلاحيات الجذر | Android |
| Play time units, short | 45د، 2س 5د | |
| Wait units (PIN pad) | 25 ث، 4 د، 23 س | |
| Statuses: Backlog, Playing, Finished, Completed, Shelved, Endless | قائمة الانتظار، قيد اللعب، أنهيتها، مكتملة، مؤجّلة، بلا نهاية | |
| Clip / Screenshot | مقطع / لقطة شاشة | |
| Companion app (per game, Extras) | التطبيق المرافق | Distinct from Tandem's own رفيق |
| Unlocked / Locked (achievement) | مفتوح / مقفل | |
| Locked field (Edit details) | خانة مقفلة | |
| Chord / Hold (rebinding) | تركيبة / ضغط مطوّل | |
| Performance modes: Standard, Performance, High performance | قياسي، أداء، أداء عالٍ | |
| Fan modes: Off, Quiet, Smart, Sport | إيقاف، هادئ، ذكي، رياضي | |
| Screen off (companion option) | شاشة مطفأة | |
| Time to beat | مدة الإنهاء | |
| Downloaded media | الوسائط المنزّلة | |
| Scrape modes: Missing basics, Missing selected, Force all | الأساسيات الناقصة، الناقص المختار، الكل من جديد | |
| Fanart | فن المعجبين | |
| Play time units, long | 45 دقيقة، ساعتان، 3 ساعات و5 د | |
| In use (theme, controller) | قيد الاستخدام | Not مستخدم, which reads as "used / second-hand" |
| Just now | للتو | |
| Last played (label next to a date) | آخر مرة | Widget heading: آخر ما لعبته |
| Launch animation / launch sound | حركة التشغيل / صوت التشغيل | إطلاق sounds like a rocket |
| Matte (shelf look) | غير لامع | مطفأ means switched off |
| Drop / Carry (grid move hints) | ضعه / احمله | Bare وضع / حمل read as "mode" / "pregnancy" |
| Move left / Move right (dock order) | قدّمه خطوة / أخّره خطوة | Order, not side: right in a mirrored layout |
| Keys (B on the search keyboard) | لوحة المفاتيح | |
| Needs Shizuku running | يحتاج إلى تشغيل Shizuku (والسماح فيه لـ Tandem) | Short tag stays يحتاج Shizuku |
| Any (filter value) | الكل | |
| Dual screen assistant (AYN's app) | مساعد الشاشة المزدوجة | Check against AYN's own Arabic name if it has one |

## Names of things inside Tandem

| English | Arabic |
| --- | --- |
| Pastel Night (theme) | ليلة الباستيل |
| OLED Black | أسود OLED |
| Paper | ورق |
| Sound packs: Modern, Playful, Chiptune, Bubbly, Minimal, Studio, Glass, Air | عصري، مرِح، 8-بت، فقاقيع، همس، استوديو، كريستال، نسيم |
| Swatches: Mint, Peach, Lavender, Sky, Butter, Sakura, Coral, Hot pink, Grape, Ocean, Teal, Lime, Gold, Snow | نعناع، خوخ، لافندر، سماء، زبدة، ساكورا، مرجان، وردي فاقع، عنب، محيط، فيروزي، ليمون أخضر، ذهبي، ثلج |
| Highlight: Glow, Outline, Lift, Pulse | توهّج، إطار، ارتفاع، نبض |
| Animation: Bouncy, Smooth, Minimal | نطّاط، انسيابي، خفيف |
| Swap animations: Scatter, Push, Flip, Depth, Fade, Instant | تناثر، دفع، قلب، عمق، تلاشٍ، فوري |

## Calendars (Settings › Look & feel › Calendar, `strings_calendar.xml`)

| English | Arabic | Note |
| --- | --- | --- |
| Calendar | التقويم | The setting |
| Gregorian / Hijri / Chinese | ميلادي / هجري / صيني | Options; "Gregorian + Hijri" is ميلادي + هجري |
| AH (after a Hijri year) | هـ | "11 جمادى الآخرة 1448 هـ" |
| Hijri months | محرم، صفر، ربيع الأول، ربيع الآخر، جمادى الأولى، جمادى الآخرة، رجب، شعبان، رمضان، شوال، ذو القعدة، ذو الحجة | As Umm al-Qura calendars write them |
| Lunar months (Chinese calendar) | الشهر الأول … الشهر الثاني عشر | A lunar day: "13 من الشهر العاشر" |
| Leap month | الشهر … الكبيس | "الشهر السادس الكبيس" |
| Year of the Horse… | عام الحصان | Rat الفأر، Ox الثور، Tiger النمر، Rabbit الأرنب، Dragon التنين، Snake الأفعى، Horse الحصان، Goat الماعز، Monkey القرد، Rooster الديك، Dog الكلب، Pig الخنزير |

Dates keep Western digits like every number.
