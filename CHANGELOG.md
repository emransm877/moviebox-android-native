# Changelog

## 0.1.4 - 2026-11-26

- Align portrait resource controls and keep season/episode choices in one compact row.
- Remove full-width player header shading; preserve safe edges and 48dp controls.
- Protect Android system-edge Back gestures from in-player seeking.
- Use readable audio language names and append title-specific recommendations up to 50 real unique results when the service supplies them.
- Remove the Home carousel Pause control while retaining automatic rotation and navigation; refine Categories spacing.
- Give Me's update row immediate pressed/checking feedback. Offer optional Update/Later prompts each cold launch when a verified newer release is cached.
- Download an update inside the app only after an explicit Update tap, show progress/cancel, then require a separate Install tap and Android confirmation.
- Verify download size, SHA-256, package, newer version and installed signing identity; revalidate before installation off the UI thread.
- Persist GitHub rate-limit/retry deadlines and keep update failures distinct from an up-to-date result. QA startup checks no longer consume the public metadata quota.

Checked scope: 46 release unit tests, lint/build and 105 API23 emulator scenarios; 13 fresh visual scenarios plus one tutorial-free landscape check; exact published 0.1.3-to-0.1.4 same-signer emulator data preservation. Independent HQ and owner visual review passed. Physical 0.1.4 installation/playback, modern OS edge gestures and the Android 8+ install-permission UI remain unverified. See [0.1.4 scope](docs/releases/0.1.4.md).

## 0.1.3 — 2026-11-25

- Keep the portrait watch preview full width while sources load, and use compact player controls.
- Overlay home navigation on taller artwork and load optional title-specific recommendations.
- Keep the selected audio edition through source retries and episode changes.
- Show Coming soon only for a confirmed unavailable movie; keep network, authorization and unknown states retryable.
- Start with the first usable advertised quality instead of waiting for the full quality menu.
- Remove the native 18+ restriction and its Settings switch.

Checked scope: 38 release unit tests, build/lint, 72 combined signed Android 6 emulator scenarios and one additional visual scenario. A two-phase same-signer 0.1.2-to-0.1.3 emulator update preserved the owned saved library, progress and preferences. Physical updater acceptance and complete upstream parity remain unverified. See [0.1.3 release scope](docs/releases/0.1.3.md) and [validation](docs/releases/0.1.3-validation.json).

## 0.1.2 — 2026-11-25

- Open titles in a portrait watch page, with audio, quality, subtitles and episodes below the video.
- Retain recent searches and suggestions, with return-to-results behavior.
- Add the then-current default family filter and separate catalogue caches. That restriction is removed in 0.1.3.
- Explain GitHub update-check limits without silently downloading or forcing installation.

Historical checked scope: 35 release unit tests, 53 scoped Android 6 emulator scenarios, eight updater scenarios and two in-place-update probes. See the unchanged [0.1.2 notes](docs/releases/0.1.2.md) for artifact identity and limitations.

## 0.1.1 — 2026-11-24

- Keep your place when returning to browsing or when a title description refreshes.
- Prevent older search results from returning after you clear the search box.
- Make failed playback and artwork easier to recover from.
- Improve larger-text controls and available audio names.
- Add library backup, restore previews and recovery for unreadable saved data.
- Make downloads Wi-Fi-first, explain failures and offer deliberate fresh-source retry.
- Bound subtitle storage while keeping the subtitle currently playing.
- Keep completed titles in History, without showing them as Continue watching.
- Make optional update reminders honest about incomplete releases and wait until playback ends.

The final recorded 0.1.1 result was 131 scoped phone scenarios after a same-signer update, plus 29 unit tests; the earlier checkpoint covered 127 scenarios. Lint: zero errors/89 warnings.
See [validation scope](docs/VALIDATION.md) and [release notes](docs/releases/0.1.1.md).

## 0.1.0

Initial independent native Android phone client, separate from the Android patch.
Includes native browsing/playback, local list and resume, search suggestions,
available subtitles/audio, touch controls, episode navigation and autoplay,
selectable app icons, cache/loading improvements and optional release notices.
See validation and installation documentation for the tested scope and limits.
