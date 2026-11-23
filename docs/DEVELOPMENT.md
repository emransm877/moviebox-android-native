# Development and release checks

JDK 17, Android SDK platform 36, Gradle wrapper 8.9 and Android Gradle Plugin 8.7.3
are used. Configure your SDK through ANDROID_HOME or an untracked local.properties.
Run `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`.
The committed wrapper verifies its distribution SHA256.

The optional manually dispatched GitHub workflow runs unit tests, Android lint, debug APK compilation and
instrumentation APK compilation. It neither contacts a live service for tests nor
publishes an application. Device/emulator instrumentation is a separate maintainer
step; optional live observation classes are not automatic CI acceptance checks.
Run routine validation locally. Never enable or trigger GitHub Actions without
the maintainer's explicit confirmation for that run.

For a release, `tools/build_release.py` accepts `--identity` pointing to an existing
private signing identity outside this repository and `--output` for local outputs.
It reads private signing credentials locally without printing them, runs release
unit/lint/build plus debug/test compilation, and writes the versioned APK and
SHA256 file. It does not create a signing key or publish anything. Never commit
identity JSON, keystores, passwords or local build logs. Keep the existing signing
certificate for in-place updates. Do not configure release signing in public CI
until a separately reviewed secret-storage workflow is ready.

Before publication: run source checks, inspect permissions and non-debuggable
release status, verify the signing certificate and checksum, run the selected
physical/emulator scenarios, inspect screenshots against the exact source, update
release notes and known limits, then upload the approved APK and checksum together.

See [fixture provenance and explicit skips](TEST_FIXTURES.md) before running
instrumentation. A compilation pass is not a captured-art scenario pass.
