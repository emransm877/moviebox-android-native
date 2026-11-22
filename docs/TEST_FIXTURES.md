# Test fixture provenance and optional captured art

`app/src/androidTest/assets/fixture.mp4` is generated test media for local playback,
not a redistributed movie or television episode. No downloaded production video
is bundled into the app or test APK. Tests also build synthetic metadata and
local subtitle text in code.

Four optional captured-art fixtures are deliberately excluded from public Git:
`actual-datang-poster.webp`, `actual-native-banner.webp`, `actual-wide-cover.webp`
and `native-banner-feed.json`. Their upstream artwork/metadata is not licensed
by this project's MIT source license. Do not substitute invented art under these
"actual" filenames. A maintainer with appropriate rights may supply the exact
captured fixtures locally in `app/src/androidTest/assets/` to reproduce that visual
comparison. No repository tool downloads them automatically.

Four captured-art test methods call `CapturedArtworkFixtures.require(...)` before
using those inputs. JUnit assumptions explicitly mark them **skipped**, rather
than passed, when a required optional asset is absent. Other synthetic/offline
scenarios remain available. CI compiles the instrumentation suite; it does not
run a device or count these skipped scenarios as successful runtime evidence.
Previously recorded local captured-art results used the retained private inputs.

Some download scenarios reference Google's ExoPlayer Android developer sample
media by URL; those external bytes are not committed. Running them requires an
internet connection and follows the provider's sample usage terms. Optional
`Live*Observation` scenarios exercise external services and are not CI checks.

The home screenshot is a genuine application capture supplied for project
documentation. Visible catalogue artwork belongs to its respective owners and is
not offered as MIT-licensed artwork. Generated launcher concepts are separate
project assets; use of the MovieBox name does not establish trademark ownership.
