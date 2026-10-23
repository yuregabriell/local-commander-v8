#!/usr/bin/env python3
"""A deliberately restricted, non-invasive Android TV visual preview.

Based on the MIT-licensed Free My TV project at
https://github.com/simbasjungles/free-my-tv
The Home role, localhost ADB changes, boot hooks and accessibility service
are NOT exposed. The existing factory launcher remains untouched.
"""
from pathlib import Path

manifest_path = Path("app/AndroidManifest.xml")
text = manifest_path.read_text(encoding="utf-8")

old_main = '''            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>'''
old_home = '''            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>'''
assert text.count(old_main) == 1, "Upstream main intent changed"
assert text.count(old_home) == 1, "Upstream HOME intent changed"
text = text.replace(old_main, "", 1)
text = text.replace(old_home, old_main, 1)
text = text.replace('android:label="Free My TV"', 'android:label="Nova TV Preview"')
text = text.replace('<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />', '')
for component in (".ModeJob", ".HomeReceiver", ".RemoteKeyService"):
    original = f'android:name="{component}"'
    assert original in text, f"Upstream component not found: {component}"
    text = text.replace(original, f'android:name="{component}" android:enabled="false"', 1)
manifest_path.write_text(text, encoding="utf-8")

mode_path = Path("app/src/io/freemytv/HomeMode.java")
mode = mode_path.read_text(encoding="utf-8")
switch = 'ModeJob.schedule(c, ModeJob.APPLY, mode, 0);'
assert mode.count(switch) == 1, "Upstream mode switch implementation changed"
mode = mode.replace(switch, '/* Safe preview: never switch the TV home screen. */')
for operation in ("REVERT", "VERIFY", "REMOTE"):
    # Extra defense; none of these should run because ModeJob is disabled in the manifest.
    mode = mode.replace(f'ModeJob.schedule(c, ModeJob.{operation}, null, 0);',
                        '/* Safe preview: background ADB jobs disabled. */')
mode_path.write_text(mode, encoding="utf-8")

print("Safe preview patch applied. Main opens HomeActivity; system HOME, ADB jobs, boot and accessibility inactive.")
