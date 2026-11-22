# French glossary (Français)

The words Tandem uses in French, chosen once and used everywhere. Read this before translating or reviewing any
`values-fr/strings_*.xml`. When a choice changes, change it here first, then in every file.

## Voice

- **Register:** warm, plain, a little cheeky, like a Nintendo France menu written by someone who loves handhelds.
  Never corporate, never « veuillez ». The user is **tu**, everywhere, without exception (« Appuie sur Y »,
  « Ta bibliothèque »). Settings help text is conversational second person: « Choisis… », « Tu peux… ».
- **Pip:** a small, cheerful robot. Pip says *je*, talks to *tu*, short lines, the odd « ! »: « Bonjour ! »,
  « Rien à signaler. », « Tu veilles ? Moi aussi. ». Playful, never childish, no slang heavier than « trop
  bien » or « nickel ».
- **Labels:** sentence case (« Modifier la grille », never « Modifier La Grille »), infinitive for actions
  (« Masquer ce jeu », « Choisir les visuels »), as short as the English where they sit in chips, tiles, segmented
  options and button hints (the English XML comments say which). French runs 20–30 % longer: prefer the shorter
  natural word (« appli » over « application », « infos » over « informations », « stats » over « statistiques »
  in labels).
- **Typography:**
  - Non-breaking space (U+00A0) before `:` `;` `!` `?` and `%`, and inside `« »`. Written as a plain space by
    translators; `fix-fr.py` (see the translation brief) turns it into U+00A0.
  - Apostrophe is the typographic `’` (U+2019), so no `\'` escaping is needed. Quotation marks are `« … »`, never
    `"…"`.
  - Capitals keep their accents: « État », « À jouer », « Écran du haut ».
  - The ellipsis is the one character `…`, glued to the word (« Nouveau dossier… »).
  - The path arrow `›` and the dot `·` stay as in English, with a space each side.
  - Numbers: « 12 jeux », « 87 % », « 1,5 Go ». Sizes are Go / Mo / Ko (Android formats them itself).
- **Menu paths quoted in text** use exactly the words of the menu items, as listed under
  [Menu items quoted elsewhere](#menu-items-quoted-elsewhere). An option or menu item named inside a sentence goes
  in « » when it would otherwise read as part of the sentence (« Active « Afficher les jeux masqués » dans les
  Paramètres »); paths with `›` (« Start › Masqués ») and capitalised option lists don't need them.
- **Search examples** stay in English inside « »: the search keyboard types Latin letters and search matches English
  names as well as French ones.
- **Plurals:** `one` and `other` (and `many` only if wanted; not used so far). French `one` covers 0 and 1: write
  it so « 0 jeu » reads right, or leave the number in. `other` keeps every argument.

## Never translated

Tandem, Pip, Shizuku, Cocoon, ES-DE, RetroAchievements, Steam, Discord, GitHub, LaunchBox, libretro, IGDB,
SteamGridDB, ScreenScraper, HowLongToBeat, Sunshine, Moonlight, Eden Duo, Peanuts Native, the emulators, game
titles, song titles and soundtrack names (Calm, Meadow Tales), file paths and formats (ROMs/…, gamelist.xml, PNG,
CSV), button letters (A, B, X, Y, L1, R1, L2, R2, Start, Select, Home), « USB », « Wi-Fi », « Bluetooth »,
« GPL-3.0 », « PIN » (« code PIN »). System names come from `platforms.json` and stay as they are there (the
Android Games system too: its name is fixed in the code, so text says « Android Games », not « Jeux Android »). Steam's
website menus are quoted the way Steam France writes them.

## Android's own words (match the device)

| English | French | Note |
| --- | --- | --- |
| Settings | Paramètres | Android FR |
| Quick settings | Réglages rapides | Android FR's own name for the pull-down tiles |
| Home screen (Android's) | Écran d’accueil | Android FR |
| Home app | Appli d’écran d’accueil | Android FR « Application d’écran d’accueil », shortened like Google does |
| Wi-Fi / Bluetooth | Wi-Fi / Bluetooth | |
| Airplane mode | Mode Avion | Android FR capitalises Avion |
| Battery saver | Économiseur de batterie | Android FR |
| Notifications | Notifications | |
| Brightness / Volume / Muted | Luminosité / Volume / Muet | |
| Screensaver | Économiseur d’écran | Android FR |
| Wallpaper | Fond d’écran | |
| Widget | Widget | Android FR |
| Back up / Restore | Sauvegarder / Restaurer | |
| Backup (noun) | Sauvegarde | |
| Language / System default | Langue / Langue du système | |
| Auto-rotate | Rotation automatique | |
| Refresh rate | Fréquence d’actualisation | Android FR |
| Do Not Disturb | Ne pas déranger | |
| All apps | Toutes les applis | Google FR says « appli » |
| App / apps | Appli / applis | Short, Google FR style |
| App info | Infos sur l’appli | Android FR |
| Uninstall | Désinstaller | |
| Permission / Allow | Autorisation / Autoriser | |
| "Use this folder" (folder picker) | « Utiliser ce dossier » | Android FR's button |
| Undo | Annuler | |
| Search | Rechercher (action) / Recherche (title) | |
| Back / Close / Cancel / Done / Choose / Clear | Retour / Fermer / Annuler / OK / Choisir / Effacer | |
| On / Off | Activé / Désactivé | Android FR switches |
| Storage / free | Stockage / libre(s) | « 12 Go libres » |
| Download | Télécharger | |
| Screenshot | Capture d’écran (chip: Capture) | |

## Tandem's words

| English | French | Why |
| --- | --- | --- |
| Home (Tandem's home) | Accueil | What Android and every console say in French |
| Home layout | Disposition de l’accueil | |
| Library | Bibliothèque | Steam FR |
| System (a console or platform) | Système | What French frontends (Recalbox, Batocera, ES-DE) say; covers arcade and PC. « Console » only where it truly is a console |
| Game | Jeu / jeux | |
| Emulator | Émulateur | |
| Handheld / device | Console (the handheld) / appareil (Android's view) | « la console » for the Thor in Pip-ish text, « l’appareil » in technical help |
| Top screen / Bottom screen | Écran du haut / Écran du bas | What French DS and 3DS players say |
| Other screen | Autre écran | |
| Two screens (page) | Deux écrans | |
| Look & feel (page) | Apparence | Android FR's word; one word, fits the tile |
| Home & layout (page) | Accueil et disposition | |
| Sound & music (page) | Son et musique | |
| Controls (page) | Commandes | Nintendo FR |
| Library & art (page) | Bibliothèque et visuels | |
| Play & system (page) | Jeu et système | |
| Home & play (older title) | Accueil et jeu | |
| For tinkerers | Pour les bidouilleurs | The French word for people who tinker, affectionate |
| Your data | Tes données | |
| Start panel | Panneau Start | Named after its button |
| Side panel | Panneau latéral | |
| Theme / Theme pack | Thème / Pack de thème (pl. packs de thèmes) | |
| Accent colour | Couleur d’accent | |
| Highlight / highlighted | Curseur / sélectionné | French menus call the highlight the cursor too |
| Highlight style | Style du curseur | |
| Surface | Matière | What cards are "made of" |
| Reduce motion | Réduire les animations | iOS and Android accessibility wording |
| Motion (group) | Animations | |
| Animation style | Style d’animation | |
| Motion intensity | Intensité des animations | |
| Floating titles | Titres flottants | |
| Icon grid | Grille d’icônes | |
| Icon style | Style des icônes | |
| System icons | Icônes des systèmes | |
| Dock (apps dock) | Dock | |
| Folder / Smart folder | Dossier / Dossier intelligent | |
| Smart folder rules | Règles | « Modifier les règles » |
| Manual folder (not smart) | Dossier classique | |
| Collection | Collection | |
| Genre | Genre | |
| Tile | Vignette | Windows FR; « tuile » sounds like roofing |
| Empty spot | Case vide | |
| Shelves (home layout, rows you scroll) | Rayons | Rows like a shop's aisles; keeps « étagère » free |
| Shelf (games view, spines on a bookcase) / bookcase | Étagère | |
| Shelf (dock style) | Tablette | |
| Case (cartridges in a case) | Boîtier | |
| Cartridge | Cartouche | |
| Grid / List | Grille / Liste | |
| Carousel | Carrousel | |
| Console (home layout) | Console | |
| Orbit | Orbite | |
| Spotlight | Vedette | « En vedette »: short and French, says what it does |
| Spotlight rail | Bandeau | |
| Classic | Classique | |
| Showcase (setting preview) | Aperçu | |
| Box art / cover | Jaquette | What every French player calls it |
| Cover frame | Cadre de jaquette | |
| Cover mosaic | Mosaïque de jaquettes | |
| Hero (wide art) | Bannière | The wide art across the top; « héros » means nothing here |
| Logo | Logo | |
| Art / artwork | Visuels | « illustrations » only in long help text |
| Fanart | Fanart | Used as is by French fans and sites |
| Video snap | Aperçu vidéo | |
| Moving art | Visuels animés | |
| Scrape (verb) / scraping | Scraper / le scraping | Established in the French retro scene (Recalbox and Batocera, both French, say « scraper ») |
| Scrape library | Scraper la bibliothèque | |
| Scrape this game | Scraper ce jeu | |
| Art source / Set up sources | Source / Configurer les sources | |
| Region & language (scrape) | Région et langue | |
| Regions | Amérique du Nord / Europe / Japon / Monde (short: US / EU / JP / Monde) | |
| Details (game info) | Infos (label) / Fiche du jeu (the page) | |
| Edit details | Modifier les infos | |
| Artwork & details | Visuels et infos | |
| Extras (per game) | Bonus | Like a DVD's bonus features |
| Manual | Notice | Short, what French boxes said; Nintendo's « mode d’emploi » is too long for a chip |
| Game folders | Dossiers de jeux | |
| Rescan / Rescan games | Réanalyser / Réanalyser les jeux | |
| Import details | Importer les infos | |
| Carry / Drop / Paste (moving a tile) | Transporter / Poser / Coller | The menu item that starts it is « Déplacer » |
| Tidy up (close the gaps) | Ranger | |
| Sort / Sort orders | Trier / Nom (A à Z) · Joués récemment · Les plus joués · Ajoutés récemment · Par système · Manuel | |
| A to Z | A à Z | |
| Pip | Pip | A name; French keeps mascot names (Pikachu, Kirby) |
| Resume (bring back a running game) | Reprendre | Nintendo FR |
| Resume page | Page Reprendre | |
| Continue (row of recent games) | Continuer | The title-screen word |
| Continue playing | Continuer à jouer | |
| Now playing | En jeu | |
| Running (a game still open) | Lancé | « En cours » is the Playing status |
| Recently played | Joués récemment | |
| Game switcher | Sélecteur de jeux | |
| Switch games | Changer de jeu | |
| Play (button hint) | Jouer | |
| Hidden games / Hide / Unhide | Jeux masqués / Masquer / Réafficher | Android FR « masquer » |
| Tuck away | Ranger en douce | Playful, a little sneaky |
| Private mode | Mode privé | |
| PIN | Code PIN | |
| Favorite / Unfavorite / Favorites | Ajouter aux favoris / Retirer des favoris / Favoris | « ♥ » kept where English has it |
| Up next (app, widget, list) | Prochains jeux (« Ajouter aux prochains jeux » / « Retirer des prochains jeux ») | « À suivre » made the menu items read « Ajouter à À suivre » |
| Pin / Unpin (home, dock) | Épingler / Retirer | « Épingler à l’accueil » |
| Button prompts | Icônes des boutons | The button marks drawn on screen |
| Button hints (bottom row) | Aide des boutons | |
| Corner hints | Aide dans le coin | |
| Rebind buttons | Réassigner les boutons | |
| Swap screens | Inverser les écrans | |
| Top bar | Barre du haut | |
| Deck (keyboard + trackpad on the bottom screen) | Deck | Tandem's name for it |
| Controller | Manette | What every French player says |
| Connected controllers | Manettes connectées | |
| Controller (Built-in) | Manette (Intégrée) | |
| Controller Home button | Bouton Home des manettes | |
| Play with a controller | Jouer avec une manette | |
| Companion (bottom screen while playing) | Acolyte | The French word for a sidekick; short enough for a chip |
| Companion app (started with a game) | Appli acolyte | |
| Leave to emulator (bottom screen) | Laisser à l’émulateur | |
| Single screen | Un seul écran | |
| Surprise me | Surprends-moi | |
| Time to beat (HowLongToBeat) | Durée de vie (Histoire / + Extras / 100 %) | French players' own word for how long a game lasts |
| Lock / unlock (edited details) | Verrouiller / Déverrouiller | |
| Match (RetroAchievements) | Correspondance / Associer | |
| Trophy | Trophée | PlayStation FR |
| Bronze / Silver / Gold / Platinum | Bronze / Argent / Or / Platine | PlayStation FR |
| Achievement | Succès | Xbox and Steam FR |
| Unlocked / Locked (achievement) | Débloqué / Verrouillé | |
| Stats & trophies | Stats et trophées | |
| Play stats | Stats de jeu | |
| Play sessions / play history | Sessions de jeu / Historique de jeu | |
| Session (the count) | Session | |
| Play time | Temps de jeu | Nintendo FR |
| Status | Statut | |
| Backlog | À jouer | Short for the status chip; « la pile de la honte » is fair game in a joke |
| Playing | En cours | |
| Finished | Terminé | |
| Completed | Complété | What French players say for 100 % |
| Shelved | En pause | |
| Endless | Sans fin | |
| No status | Sans statut | |
| Performance mode / fan | Mode performance / ventilateur | |
| Quick looks (presets) | Looks express | Playful, French says « look » |
| Welcome (first run) | Bienvenue | |
| Update (noun) / Update (the button) / Check | Mise à jour / Mettre à jour / Vérifier | The button is quoted by name in settings help |
| UI sounds | Sons de l’interface | |
| Sound pack | Pack de sons | |
| Music / My music | Musique / Ma musique | |
| Soundtrack (built-in) | Bande-son | |
| Shuffle | Aléatoire | |
| Pip's voice | Voix de Pip | |
| Notes / Journal / Gallery / Files | Notes / Journal / Galerie / Fichiers | |
| Widget editor | Modifier le widget | |
| Android widget | Widget Android | |
| Screenshot / Clip | Capture d’écran / Clip | |
| Screen off (companion option) | Écran éteint | |
| Immersion mode | Mode immersion | |
| Hold (a button) | Maintenir (« Maintiens Start ») | |
| Root (Shizuku) | Root | |
| Download(ed) media | Médias téléchargés | |
| Regex / Plain text | Regex / Texte simple | |

## Menu items quoted elsewhere

These menu items are named inside other strings ("Y › Move to folder on any tile"); the quote uses the item's
exact words, without the trailing `…`.

| Item | French | Key |
| --- | --- | --- |
| Start › New | Start › Nouveau | `panels_start_new` |
| Start › Sort | Start › Trier | `panels_start_sort` |
| Start › Edit grid | Start › Modifier la grille | `panels_start_edit_grid` |
| Start › Hidden | Start › Masqués | `panels_start_hidden` |
| Start › Stats & trophies | Start › Stats et trophées | `panels_start_stats` |
| New › Smart folder | Nouveau › Dossier intelligent | `panels_start_new_smart_folder` |
| Y › Move to folder | Y › Déplacer vers un dossier | `grid_sel_move_to_folder` |
| Y › Add to favorites | Y › Ajouter aux favoris | `menus_game_favorite` |
| Y › Status › Backlog / Finished | Y › Statut › À jouer / Terminé | `data_status_*` |
| Y › Sort inside › Manual | Y › Trier le contenu › Manuel | `grid_menu_sort_inside`, `grid_sort_manual` |
| Y › Edit details › Manual | Y › Modifier les infos › Notice | `menus_game_edit`, `menus_game_manual` |
| Edit details › Extras | Modifier les infos › Bonus | `library_extras_title` |
| Edit details › Logo › Place | Modifier les infos › Logo › Placer | `library_logo_row` |
| Y › Select | Y › Sélectionner | `grid_menu_select` |
| Y › Move | Y › Déplacer | `grid_menu_move` |
| Y › View as | Y › Afficher en | `grid_menu_view_as` |
| Y › Design folder | Y › Relooker le dossier | `grid_menu_design` |
| Y › Delete folder | Y › Supprimer le dossier | `grid_menu_delete` |
| Y › Edit rules | Y › Modifier les règles | `grid_form_title_edit` |
| Y › Always open on | Y › Toujours ouvrir sur | `menus_app_screen` |
| Y › More | Y › Plus | `menus_action_more_title` |
| Add here › Widget… / System | Ajouter ici › Widget… / Système | `grid_pick_add_here`, `grid_spot_widget`, `grid_spot_system` |
| Add to home | Ajouter à l’accueil | `grid_add_home` |
| Paste | Coller | `home_spot_hint_paste` |
| Show hidden games | Afficher les jeux masqués | `settings_library_hidden` |
| Show hidden apps | Afficher les applis masquées | `home_apps_show_hidden` |
| Type a colour… | Saisir une couleur… | `look_accent_type` |
| Stats › By system / Achievements | Stats › Par système / Succès | stats area |
| Settings › Look & feel › Themes / Theme packs / Clock / Video snaps / Language | Paramètres › Apparence › Thèmes / Packs de thèmes / Horloge / Aperçus vidéo / Langue | |
| Settings › Home & layout › Home screen / Dock & widgets | Paramètres › Accueil et disposition › Écran d’accueil / Dock et widgets | |
| Settings › Two screens › Other screen | Paramètres › Deux écrans › Autre écran | |
| Settings › Sound & music | Paramètres › Son et musique | |
| Settings › Controls › Rebind buttons / X button / Deck / Game controllers / Controller Home button / Controller in games / Play with a controller | Paramètres › Commandes › Réassigner les boutons / Bouton X / Deck / Manettes de jeu / Bouton Home des manettes / Manette en jeu / Jouer avec une manette | |
| Settings › Library & art › Game folders / Artwork & details / Set up sources / Scrape library / Accounts › Steam / Discord / Systems & launching / Android apps | Paramètres › Bibliothèque et visuels › Dossiers de jeux / Visuels et infos / Configurer les sources / Scraper la bibliothèque / Comptes › Steam / Discord / Systèmes et lancement / Applis Android | |
| Settings › Play & system › Playing / Performance & sleep › Screen off / Privacy & hidden › Show hidden games / Your data › Undo Cocoon import / Updates / Language | Paramètres › Jeu et système › En jeu / Performances et veille › Écran éteint / Confidentialité et masqués › Afficher les jeux masqués / Tes données › Annuler l’import Cocoon / Mises à jour / Langue | |
| Settings › Your data › Back up / Restore | Paramètres › Tes données › Sauvegarder / Restaurer | |
| Steam: Profile › Edit › Privacy › Game details › Public | Profil › Modifier le profil › Paramètres de confidentialité › Détails des jeux › Public | Steam FR's own words |

## Swatches, wallpapers and styles

Accent swatches take soft French colour words: Menthe, Pêche, Lavande, Ciel, Beurre, Sakura, Corail, Rose bonbon,
Raisin, Océan, Sarcelle, Citron vert, Or, Neige. Highlight styles: Lueur, Contour, Relief, Pulsation. Animation
styles: Rebondi, Fluide, Sobre. Sound packs: Moderne, Ludique, Chiptune, Pétillant, Minimal, Studio, Cristal, Brise. Wallpapers nod to
French memories of each console where one exists (Clear shell is Transparent, like the see-through Game Boy Color;
Rainbow road is Route arc-en-ciel, Mario Kart's French name; Tall grass is Hautes herbes, Pokémon's French word).

## Settled while translating

| English | French | Note |
| --- | --- | --- |
| Layout / Layouts | Disposition / Dispositions | |
| Custom / Yours / Own order | Perso / Ordre perso | Short, colloquial, fits chips |
| Start over (group) | Repartir de zéro | |
| While playing (group) | Pendant le jeu | « En jeu » is the Playing group |
| Game controllers / Controllers (groups) | Manettes de jeu / Manettes | |
| Status (Android notification, side panel) | État | « Statut » is a game's status |
| Now playing (music) | Lecture | Keeps « En cours » for the game status |
| Swap A and B / Swap X and Y | Inverser A et B / Inverser X et Y | Quoted in controls help |
| Bottom-screen styles | Infos / Visuel + infos / Visuel animé / Horloge | Quoted in look help |
| Chord / Hold (rebinding) | Combo / Appui long | |
| Reset / Changed | Réinitialiser / Modifié | |
| Trackpad / Key haptics | Pavé tactile / Vibration des touches | |
| Recent apps / App drawer / Stock launcher | Applis récentes / Tiroir d’applis / Lanceur | |
| Mastered (achievements) | Maîtrisé | |
| Matched / Look up again (RetroAchievements) | Associé / Réassocier les jeux | |
| Web API key | Clé Web API | |
| Hardcore / Softcore | Hardcore / Softcore | As the French RA community says |
| Gameplay screenshot / Title screen | Capture de jeu (tile: Gameplay) / Écran titre | |
| Physical box, front, spine | Boîte, face, tranche | |
| Thumbnails (libretro) | Miniatures | |
| Scrape modes | L’essentiel / Ce qui manque / Tout refaire | |
| Metered connection / Unreachable | Connexion limitée / Injoignable | |
| Fan modes / Keep (perf) | Arrêt / Silencieux / Intelligent / Sport · Inchangé | |
| Not set | Non renseigné | |
| Internal storage / SD card | Stockage interne / Carte SD | |
| Cocoon setup | Config Cocoon | |
| Glyph / Colour sweep | Pictogramme / Dégradé | |
| Cover fan / Cover wall | Éventail de jaquettes / Mur de jaquettes | |
| Mark (multi-select) | Cocher | |
| Unplayed / Times played / Last played | Jamais lancés / Parties / Dernière partie | |
| Design (folder page title) | Relooking | Goes with « Relooker le dossier » |
| Spotlight rail | Bandeau de jaquettes | |
| Needs Shizuku | Requiert Shizuku | |
| Reset play history | Effacer l’historique de jeu | |
| Hero fade (vignette) | Vignettage | The photo word |
| Surfaces | Plein / Verre dépoli / Contours | |
| In use / Built in (theme) | Actif / Intégré | |
| Song (count) | Morceau | |
| Hold-to-confirm hint | « Maintiens A (ou reste appuyé) pour %1$s »: the label passed in is an infinitive (« supprimer », « les effacer ») | |
| Auto-hide dock | Dock rétractable | « Masquer le dock » read as hiding it for good |
| Look again (theme packs) | Actualiser | « Rechercher » clashed with Search |
| In use (controller in games) | Active | « En jeu » is Now playing |
| Replace locked fields (scrape) | Ignorer les verrous | |
| Rows (icon grid) | Rangées | Everywhere on the icon grid, zoom toast included; « Rayons » is the Shelves home |
| Undo labels (grid) | Nouns: « Déplacement de… », « Retrait de la vignette » | They also follow « Annulé : » with the first letter lowered, so they never start with a name |
