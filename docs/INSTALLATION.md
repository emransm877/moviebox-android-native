# Installation and updates

Download the APK from this repository's Releases page, not the separate Android
patch or Android TV project. The 0.1.4 filename is
`MovieBox-Android-App-0.1.4.apk`; installed label MovieBox; package
`org.emransm.moviebox.phone`; Android 6/API 23 minimum.

If Android blocks installation, check free space and the browser/file-manager
installation permission. An incompatible signature or a different project cannot
update this app in place. Do not uninstall a working app just to bypass a signature
mismatch if you need its private library. Report the package/version instead.

To update, use Me -> Check for updates, or the repository Releases page.
Successful automatic checks are spaced twelve hours apart. Failed or limited checks
wait for their saved retry time. Reminders respect their setting, wait during
playback, and offer Update/Later; Update starts an in-app download in 0.1.4. A new GitHub commit alone is not an app update:
a newer complete stable APK must be published. There is no forced upgrade or silent
installation.

Version 0.1.4 retains the package and signing certificate. Historically, with 0.1.3, On 25 November 2026, the owner reported a successful in-app update with their data preserved; read-only inspection confirmed the installed version and APK identity. This does not establish update behavior on every device. A locally built APK or new source commit will not appear in the updater: the matching complete, non-draft, non-prerelease GitHub release must be published first.

Before an update, you can use **Me -> Back up library** to save your list and
progress. Restoring a backup previews a merge before you confirm. Downloaded
videos and another app's private data are not transferred. Keep backups private.

Missing subtitle/audio/quality choices can reflect the source itself. Try another
available source or quality, verify the connection, then report the exact title,
season/episode and app version. Do not include personal tokens or private URLs.

## Updating with 0.1.4 and newer

Use Me -> Check for updates. Update downloads inside MovieBox; Cancel stops an unfinished download. When Ready to install appears, tap Install and confirm in Android. Android8+ may first ask you to allow installs from MovieBox; return and tap Install afterward. You can revoke that permission later. An older installed version retains its older update interface until it is updated. Do not uninstall to update if you want to preserve the local library.

Rate-limit or connection failures do not mean a new version was installed or that playback is unavailable. The app waits for the server's retry time before making another update check.
