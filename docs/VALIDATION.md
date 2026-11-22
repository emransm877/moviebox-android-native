# Validation scope

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
