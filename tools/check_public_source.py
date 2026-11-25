"""Validate public-source boundaries without reading private signing material."""
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
for path in root.rglob('*'):
 if not path.is_file() or '.git' in path.parts or 'build' in path.parts or '.gradle' in path.parts:continue
 assert path.suffix.lower() not in {'.apk','.jks','.keystore','.p12','.pfx','.mobileprovision'},'Private signing material must remain outside Git'
 assert path.name not in {'local.properties','release-identity.json'},'Machine configuration must remain outside Git'
assets=root/'app/src/androidTest/assets'
for name in ['actual-datang-poster.webp','actual-native-banner.webp','actual-wide-cover.webp','native-banner-feed.json']:
 assert not (assets/name).exists(),'Optional third-party capture must stay outside the curated public tree'
package=root/'app/src/androidTest/java/org/emransm/moviebox/phone'
helper=(package/'CapturedArtworkFixtures.java').read_text()
assert 'Assume.assumeTrue' in helper and 'SKIPPED:' in helper
assert (package/'ArtworkVisualScenarios.java').read_text().count('CapturedArtworkFixtures.require(')==3
assert (package/'ArtworkDetailScenarios.java').read_text().count('CapturedArtworkFixtures.require(')==1
assert (assets/'fixture.mp4').stat().st_size>0
assert "applicationId 'org.emransm.moviebox.phone'" in (root/'app/build.gradle').read_text()


assert not (package/"MarketingNativeCapture.java").exists(), "Private campaign recorder is not public source"
assert (package/"OwnedQaArtwork.java").is_file()

print(json.dumps({'publicSourceBoundaries':True,'optionalArtworkTestsUseExplicitAssumptions':4,'applicationDownloads':0}))
