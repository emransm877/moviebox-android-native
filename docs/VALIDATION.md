# Validation scope

## Version 0.1.4

The current Home image in the README is an actual 0.1.4/build 7 Android 6/API 23 emulator capture after normal release startup with the live catalogue. [Screenshot provenance](images/0.1.4-home-provenance.json) binds its SHA-256 and production APK. It is not owner-phone runtime evidence and does not alter the release-time source archive.

[Stable 0.1.4](https://github.com/emransm877/moviebox-android-native/releases/tag/v0.1.4) is published. Its release manifest records source commit, recorded asset digests and metadata-only live verification.

See [0.1.4 scope](releases/0.1.4.md) and [artifact manifest](releases/0.1.4-validation.json). The exact signed APK passed release build/lint, 46 unit tests and 105 Android6/API23 emulator scenarios. Thirteen fresh visual scenarios and one tutorial-free landscape case passed on the same production APK; independent HQ and owner visual review passed.

The exact published 0.1.3 APK was seeded on a disposable emulator and updated in place to this exact 0.1.4 APK with the retained signer. List, progress, audio/subtitle preferences and recent search survived. This is a real Android installation/update test, not a live GitHub download or owner-phone update. The owner phone remains on 0.1.3. Modern platform Back gestures, Android8+ install-permission UI and broader physical0.1.4 behavior remain unverified.

QA uses owned metadata/media, a private signed future-APK fixture that was never installed, and actual native screenshots. An earlier capture harness stalled while cleaning up an animated spinner; the corrected test-only harness passed the complete105-case run. Raw SurfaceFlinger screenshots and first-use tutorial dismissal corrected capture artifacts without editing pixels or changing production code. Public source omits the private APK fixture and campaign recorder; archive scenarios explicitly skip when that fixture is absent.

## Version 0.1.3

See [0.1.3 release scope](releases/0.1.3.md) and its [candidate manifest](releases/0.1.3-validation.json). The signed production APK passed 38 release unit tests, build/lint, 72 combined Android 6/API 23 emulator scenarios and one additional stable portrait/landscape scenario. Independent HQ review covered the applicable implementation, test evidence and final visuals.

The exact published 0.1.2 APK was installed on a disposable emulator, seeded with owned library/progress/preferences/search fixtures, then updated in place to this signed 0.1.3 APK. Both seed and post-update verification passed, preserving those values. This is an Android installation test, not a physical GitHub download or owner's updater test.

These automated results use owned fixtures in an emulator. On **25 November 2026**, the owner reported installing 0.1.3 through the app with their data preserved. Read-only inspection independently confirmed version 0.1.3/build 6 and installed APK SHA-256 `12680df3240ddf9c91eb2b39c02bc991856946c1a5007f9b5c2d16bf01dd9633`. The agent did not install or clear the physical app. Saved-data preservation is the owner's report, not an independent comparison of private library contents.

Broader physical playback, exact complete upstream UI/feature parity and other device coverage remain unverified. No older artifact's runtime result is transferred to this candidate. This documentation update does not replace the published APK, source ZIP, tag or checksums; the original source ZIP remains the release-time snapshot.

## Version 0.1.2

See [0.1.2 release scope](releases/0.1.2.md). The exact signed APK passed 35 unit tests, 53 scoped API23 emulator scenarios, eight updater scenarios and two in-place-upgrade probes. Physical0.1.2 and live-service coverage remain unverified; previous physical results do not transfer to this candidate.

## Version 0.1.1

Version 0.1.1 (version code 4) uses the existing signing identity. Release build and debug/instrumentation compilation passed, together
with 29 release unit tests and lint (zero errors, 89 warnings).

The exact version-0.1.1 APK passed 131 scoped scenarios in one combined Android 17 physical-phone run (174.119 seconds, zero failures). These cover navigation and stale search, metadata refresh, artwork fallback/cache, controlled source recovery, playback, subtitles/audio, gestures, accessibility, library backup/recovery, controlled downloads, Continue watching and isolated update metadata. The same-signer in-place update preserved app data and settings; the temporary test package was removed. Exact artifact identity is in [release validation](releases/0.1.1-validation.json).

Instrumentation fixtures isolate library data where provided. Fake download
backends and injected update metadata do not perform production downloads or
install updates. Optional captured-art inputs stay private and tests explicitly
skip when those inputs are absent. Full minimum-OS, other-device/OEM, real carrier,
full-disk/power-loss, RTL and accessibility matrices remain open for this update.
No whole-app or all-device certification is implied.

Open work includes search pagination, process-death navigation restoration, richer
resume information, remaining general feedback/help usability and broader layout
coverage. Source availability and external service response time remain variable;
segmented HLS/DASH offline downloading is not supported.

See [0.1.1 notes](releases/0.1.1.md) and [fixture provenance](TEST_FIXTURES.md).

## Historical 0.1.0 release evidence

Version 0.1.0 (version code 2) is the initial native Android phone release.
The release APK is built from its recorded public source commit. Its exact
SHA256 and signing verification are recorded with the release assets/notes,
rather than inserting a self-referential artifact hash into source files.

The maintained local evidence records 39 scenario tests on one Android 17 physical
phone and 40 on an Android 6/API 23 emulator before the final episode-dock/update
changes. A subsequent physical-phone run passed ten focused episode-dock,
slider-playback and update scenarios. These are bounded observations, not proof
that every device or every streaming source works. The release notes record the precise source commit, artifact checksum and
source-check run. The limitations below remain part of the release scope.

Automated coverage includes Unicode metadata, route/detail caching, home artwork,
search, local library, playback state, subtitles, audio, gestures, downloads,
launcher aliases, orientation and accessibility readiness. Instrumentation tests
are source scenarios; compiling them in CI is not the same as executing them.

Real-service loading observations are environment-dependent samples. No universal
instant-play claim or superiority claim over another app is made. Test data is
isolated where provided; do not run instrumentation on an irreplaceable personal
library without inspecting the test's setup and cleanup first.

## Public source and artifact checks

The curated public tree passed 15 local unit tests, Android lint and
instrumentation compilation. Captured-art tests explicitly skip when optional
third-party fixtures are absent; those bytes and private device/library exports
are not published. The initial release is built locally from the public source
commit using the retained signing identity. Exact APK SHA256, signature identity,
permissions and non-debuggable status are recorded with the release evidence.
GitHub CI checks compilation and local tests, not real-service or device playback.

The focused final ten scenarios also passed on Android 6/API 23 in 17.898s
after a clean test-app reinstall, and on the physical Android 17 phone in 12.078s.
These cover EpisodeDock (4), Updates (2) and SliderPlayback (4). An earlier
emulator installation-race failure was retained in private evidence; it was not
counted as a test pass or repaired by wiping user data.

The reliability changes received Agency HQ engineering review and independent coordinator source/evidence review before publication. A review pass is scoped to the recorded tests and limitations above.
