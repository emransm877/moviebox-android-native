"""Build with an EXISTING private signing identity. Never creates keys or prints credentials."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--identity', type=Path, default=Path.home()/'.moviebox-signing/android-phone/release-identity.json')
    parser.add_argument('--output', type=Path, default=ROOT/'local-builds/release')
    args = parser.parse_args()
    identity = json.loads(args.identity.read_text(encoding='utf-8'))
    assert Path(identity['store_file']).is_file(), 'Existing signing keystore is required.'
    environment = dict(os.environ)
    environment.update(MOVIEBOX_PHONE_KEYSTORE=identity['store_file'],MOVIEBOX_PHONE_STORE_PASSWORD=identity['store_password'],MOVIEBOX_PHONE_KEY_ALIAS=identity['alias'],MOVIEBOX_PHONE_KEY_PASSWORD=identity['key_password'])
    args.output.mkdir(parents=True, exist_ok=True)
    wrapper = str(ROOT/('gradlew.bat' if os.name=='nt' else 'gradlew'))
    with (args.output/'build.log').open('w',encoding='utf-8') as log:
        result = subprocess.run([wrapper,':app:testReleaseUnitTest',':app:lintRelease',':app:assembleRelease',':app:assembleDebug',':app:assembleDebugAndroidTest','--console=plain'],cwd=ROOT,env=environment,stdout=log,stderr=subprocess.STDOUT)
    if result.returncode:
        raise SystemExit('Build checks failed. Inspect the local build log; no release was published.')
    import re
    version = re.search(r"versionName '([^']+)'",(ROOT/'app/build.gradle').read_text()).group(1)
    target = args.output/f'MovieBox-Android-App-{version}.apk'
    shutil.copy2(ROOT/'app/build/outputs/apk/release/app-release.apk',target)
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    target.with_suffix('.apk.sha256').write_text(f'{digest}  {target.name}\n',encoding='ascii')
    print(json.dumps({'apk':str(target),'sha256':digest,'bytes':target.stat().st_size}))

if __name__=='__main__':
    main()
