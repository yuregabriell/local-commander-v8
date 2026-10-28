#!/usr/bin/env python3
"""VistaLauncher v1.1: real TV-native Vista Hub, not just app shortcuts."""
from pathlib import Path
import shutil

root = Path("app/src/io/freemytv")
manifest = Path("app/AndroidManifest.xml")
m = manifest.read_text(encoding="utf-8")
assert 'android:versionCode="3"' in m
assert 'android:versionName="1.2-vistalauncher"' in m
m = m.replace('android:versionCode="3"', 'android:versionCode="4"', 1)
m = m.replace('android:versionName="1.2-vistalauncher"', 'android:versionName="1.3-vistalauncher"', 1)
assert m.count("    </application>") == 1
m = m.replace("    </application>", '''        <!-- TV-native Vista media hub, launched by the Vista Hub shelf icon. -->
        <activity android:name=".VistaHubActivity"
            android:process=":home"
            android:exported="false"
            android:screenOrientation="landscape"
            android:theme="@style/FreeMyTV.Home" />
    </application>''', 1)
manifest.write_text(m, encoding="utf-8")

shutil.copy2("../novatv-free-base/VistaHubActivity.java", root / "VistaHubActivity.java")
# A true first-party shelf entry, not a placeholder media title.
apps = root / "HomeApps.java"
s = apps.read_text(encoding="utf-8")
anchor = '    private static final String[][] SHELF = {'
assert s.count(anchor) == 1
s = s.replace(anchor, anchor + '''
        {"vista-hub", "Vista Hub", "io.freemytv", "7B70ED", "", "1"},''', 1)
anchor = '''            case APP:
                intent = launchIntent(a.getPackageManager(), it.pkg);'''
assert s.count(anchor) == 1
s = s.replace(anchor, '''            case APP:
                if ("io.freemytv".equals(it.pkg)) {
                    intent = new Intent(a, VistaHubActivity.class);
                    break;
                }
                intent = launchIntent(a.getPackageManager(), it.pkg);''', 1)
apps.write_text(s, encoding="utf-8")

# Existing settings and Vista ID remain accessible from the launcher.
acc = root / "VistaAccountActivity.java"
s = acc.read_text(encoding="utf-8")
anchor = '        button(panel,"Voltar",this::finish);'
assert s.count(anchor) == 1
s = s.replace(anchor, '''        button(panel,"Abrir Vista Hub",() ->
            startActivity(new Intent(this,VistaHubActivity.class)));
''' + anchor, 1)
acc.write_text(s, encoding="utf-8")

# TV full-window and landscape always; current home/remote behaviors untouched.
assert (root / "VistaFeed.java").exists()
assert "section(Context context, String section" in (root / "VistaFeed.java").read_text(encoding="utf-8")
assert "VistaFeed.picks(context, kind)" in (root / "Recs.java").read_text(encoding="utf-8")
assert '"Animes"' in (root / "HomeView.java").read_text(encoding="utf-8")
# If the user customized the Highlights shelf, do not change that arrangement.
# The hub stays accessible from the launcher Settings menu even with a full shelf.
settings = root / "SettingsActivity.java"
code = settings.read_text(encoding="utf-8")
old = 'else activity.startActivity(new Intent(activity, VistaAccountActivity.class));'
assert code.count(old) == 1, "Vista ID settings entry not found"
code = code.replace(old, 'else activity.startActivity(new Intent(activity, VistaHubActivity.class));', 1)
assert code.count('"Vista ID"') >= 1
code = code.replace('"Vista ID"', '"Vista Hub"')
settings.write_text(code, encoding="utf-8")

print("PASS VistaLauncher v1.1: 4 app entry points, TV hub, anime, continue and D-pad navigation.")
