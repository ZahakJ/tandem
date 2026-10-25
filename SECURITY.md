# Security policy

## Supported versions

Only the latest release gets fixes. Please check you are on it before reporting, and say which version you tested.

## Reporting a vulnerability

Use GitHub's private vulnerability reporting: the **Security** tab of this repository, then **Report a vulnerability** ([direct link](https://github.com/ZahakJ/tandem/security/advisories/new)). Please don't open a public issue or pull request for a security problem until a fix is released.

A useful report says what you did, what happened, and how to reproduce it. You can expect an acknowledgement within a week and an assessment within a month. Fixes ship in the next release, and you are credited in its notes unless you'd rather not be.

## What is in scope

Tandem is an Android app with broad file access, so these matter most:

- Anything that lets another app, a file in `ROMs/`, a `platforms.json`, a `gamelist.xml` or a theme make Tandem read or write outside what the user intended, or start something other than an emulator.
- Ways the permissions Tandem holds (all files access, the shared `ROMs` folder, Shizuku) could be used by someone other than the user.
- Anything that sends data the user didn't ask to send, such as a hidden game, or leaks a stored key or login.
- Problems with how release APKs are built and signed.

## What is not

- A custom `platforms.json` can start any app the user names. That is the feature: the file is in the user's own storage and only they can edit it.
- Emulators themselves, Shizuku, and the Android system have their own maintainers; report problems there.
- Problems in the third-party services themselves (scraping sources, RetroAchievements); report those to them.
