# Privacy

Lists, progress, settings, subtitle selections and downloaded files are stored
locally by this app. The app uses its own Android package and does not read another
MovieBox app's private library. Android application backup is disabled in the
manifest. Uninstalling can remove local app data.

Library backup and restore use Android's file picker only after you choose those
actions. Backup files contain selected saved-title and watch-progress information;
they exclude service identity, signed media links and downloaded video files.
Keep exported backups private. If damaged library data needs recovery, the app
preserves its original files before a confirmed replacement.

New video downloads default to Wi-Fi. Mobile data requires an explicit choice for
that file, and roaming is disabled. Saved retry information contains public title
and episode details, rather than a signed source link. System download status
comes from Android DownloadManager. Subtitle caches have size, count and age limits;
clearing that cache keeps the subtitle currently playing.

Browsing and streaming contact external catalogue/media/subtitle services. Those
services and network providers receive ordinary connection information such as
IP address and requested resources. The client may establish a guest service
session; it is not an account-login feature. Local caches reduce repeated work.

Optional update checks contact GitHub's public release API. In 0.1.4, an explicit
Update tap starts a private in-app APK download and Install opens Android's confirmation.
Older installed versions keep their older link-based update interface until updated.
No silent APK download or installation occurs.
This source does not include an advertising SDK or a project telemetry backend.
External media/service content may still contain advertising or tracking outside
this client's control. App settings do not change those providers' policies.

For a report, share the app version, Android version and reproduction steps. Do
not publish tokens, account details, private library exports, full signed stream
URLs or screenshots containing personal data.

## Recent searches

Version0.1.2 stores recent search queries locally when Save recent searches is enabled. Clear them or disable saving under Me. Clearing recent queries does not remove My list or watch history. Search terms submitted to the catalogue are still sent to its external service.

## Availability and content preferences in 0.1.3

Version 0.1.3 removes the 18+ restriction and its Settings switch. The native client no longer applies that family-mode filter. This does not change the external service's access rules or guarantee that all titles can play.

For confirmed unavailable movies, the display cache may retain a small subject-bound availability state. It does not store the guest session, media address or detector context. An explicit availability recheck requests sources again; cached display state does not prevent newly available playback. Related-title requests send the current title identifier to the catalogue service.

## Optional in-app update downloads (0.1.4)

Automatic checks request public release metadata and do not download an APK. When you explicitly choose Update, MovieBox downloads the official native project's APK into private app storage and checks its size, SHA-256, package, version and signer. Partial files are removed after cancellation/failure; a verified file remains available for the user's Install action. Android owns installation and confirmation. The REQUEST_INSTALL_PACKAGES permission supports only that explicit handoff; it does not permit silent installation. No GitHub token is embedded in the app.
