#!/usr/bin/env python3
"""VistaLauncher v1: upgrade original Nova TV v0.5, preserving Free My TV UX and package.
Runs AFTER patch_safe_preview, patch_v04 and patch_v05.
"""
from pathlib import Path
import shutil

base = Path("app/src/io/freemytv")
def replace(file, old, new, n=1):
    path = base/file
    code = path.read_text(encoding="utf-8")
    found = code.count(old)
    if found != n:
        raise AssertionError(f"{file}: expected {n} occurrences of {old[:85]!r}, got {found}")
    path.write_text(code.replace(old, new), encoding="utf-8")
    print("PATCH", file, old[:55])

# Media recommendations are genuine saved VistaStream public-catalog metadata.
# Continue watching reflects exactly the active local VS profile on the TV.
shutil.copy2("../novatv-free-base/VistaFeed.java", base/"VistaFeed.java")
shutil.copy2("../novatv-free-base/VistaAccountActivity.java", base/"VistaAccountActivity.java")

# Stable package io.freemytv (can update previous Nova TV; never change app id).
manifest = Path("app/AndroidManifest.xml")
m = manifest.read_text(encoding="utf-8")
assert 'package="io.freemytv"' in m
assert 'android:versionCode="2"' in m
m = m.replace('android:versionCode="2"', 'android:versionCode="3"',1)
m = m.replace('android:versionName="1.1"', 'android:versionName="1.2-vistalauncher"',1)
m = m.replace('android:label="Nova TV Preview"', 'android:label="VistaLauncher"')
m = m.replace('android:label="Free My TV"', 'android:label="VistaLauncher"')
queries = '''        <!-- Discover the installed, native Vista apps and the VS same-device home feed. -->
        <package android:name="com.vistastream.tv.debug" />
        <package android:name="com.vistastream.tv" />
        <package android:name="app.vistatube.mobile.debug" />
        <package android:name="app.vistatube.mobile" />
        <package android:name="com.vistamusic.app" />
        <package android:name="com.harvester.browser" />
        <package android:name="com.yure.videoharvester" />
        <package android:name="com.vista.id" />
        <provider android:authorities="com.vistastream.tv.homefeed" />
'''
assert m.count("    </queries>") == 1
m = m.replace("    </queries>", queries+"    </queries>",1)
activity = '''        <activity android:name=".VistaAccountActivity"
            android:process=":home"
            android:exported="false"
            android:screenOrientation="landscape"
            android:theme="@style/FreeMyTV.Home" />
'''
assert m.count("    </application>") == 1
m = m.replace("    </application>",activity+"    </application>",1)
manifest.write_text(m,encoding="utf-8")

# All four Vista apps are ordinary TV packages, D-pad accessible, installed separately.
# Retain user shelf customizations; display these only when installed.
replace("HomeApps.java",
    '    private static final String[][] SHELF = {',
    '''    private static final String[][] SHELF = {
        {"vistastream", "VistaStream", "com.vistastream.tv.debug", "7557F5", "", "1"},
        {"vistastream", "VistaStream", "com.vistastream.tv", "7557F5", "", "1"},
        {"vistatube", "VistaTube", "app.vistatube.mobile.debug", "25A7E8", "", "1"},
        {"vistatube", "VistaTube", "app.vistatube.mobile", "25A7E8", "", "1"},
        {"vistamusic", "VistaMusic", "com.vistamusic.app", "FF4FA3", "", "1"},
        {"vistabrowser", "VistaBrowser", "com.harvester.browser", "786DFA", "", "1"},
        {"vistabrowser", "VistaBrowser", "com.yure.videoharvester", "786DFA", "", "1"},''')

# Four tabs: Para ti, Filmes, Séries and Animes; still three large artwork cards per tab.
replace("HomeView.java",'private static final String[] TAB_KIND = {null, "movie", "tv"};',
    'private static final String[] TAB_KIND = {null, "movie", "tv", "anime"};')
replace("HomeView.java",'new Spring[3 + HomeApps.MAX_TOP]','new Spring[4 + HomeApps.MAX_TOP]')
replace("HomeView.java",'new RectF[3 + HomeApps.MAX_TOP]','new RectF[4 + HomeApps.MAX_TOP]')
replace("HomeView.java",'tabs = new String[] {Ui.tr("Explore"), Ui.tr("Movies"), Ui.tr("Shows")};',
    'tabs = new String[] {"Para ti", "Filmes", "Séries", "Animes"};')
# Shift the right-side top buttons one position.
shifts = [
    ('topIndex >= 3 + lightButtons.size()', 'topIndex >= 4 + lightButtons.size()'),
    ('topIndex = 2 + lightButtons.size()', 'topIndex = 3 + lightButtons.size()'),
    ('topIndex < 3', 'topIndex < 4'),
    ('for (int i = 3; i < 3 + lightButtons.size()', 'for (int i = 4; i < 4 + lightButtons.size()'),
    ('topIndex - 3', 'topIndex - 4'),
    ('picksOn() ? 0 : 3', 'picksOn() ? 0 : 4'),
    ('i < 3 + lightButtons.size()', 'i < 4 + lightButtons.size()'),
    ('i >= 3 + lightButtons.size()', 'i >= 4 + lightButtons.size()'),
    ('i >= 3)', 'i >= 4)'),
    ('i < 3 && !picksOn()', 'i < 4 && !picksOn()'),
    ('i < 3) selectTab(i)', 'i < 4) selectTab(i)'),
    ('i - 3', 'i - 4'),
    ('topRects[3 + i]', 'topRects[4 + i]'),
    ('topFocus[3 + i]', 'topFocus[4 + i]'),
    ('topRects[3].left', 'topRects[4].left'),  # weather in v0.5
    ('topIndex = picksOn() ? tab : 3;', 'topIndex = picksOn() ? tab : 4;'),
    ('Math.min(2 + lightButtons.size(), topIndex + dx)', 'Math.min(3 + lightButtons.size(), topIndex + dx)'),
    ('topIndex = 3;   // up from the tabs', 'topIndex = 4;   // up from the tabs'),
    ('"n", 3)', '"n", 4)'),
]
p=base/"HomeView.java"
s=p.read_text(encoding="utf-8")
for before,after in shifts:
    if before in s:
        s=s.replace(before,after)
    else:
        raise AssertionError("HomeView UI anchor not found "+before)
# Only change actual tab loops; leave the 3 card loops and focus arrays intact.
needle='for (int i = 0; i < 3; i++) {\n            float tw = Math.max(lTabOn.measureText(tabs()[i])'
assert needle in s
s=s.replace(needle,'for (int i = 0; i < 4; i++) {\n            float tw = Math.max(lTabOn.measureText(tabs()[i])',1)
needle='for (int i = 0; i < 3; i++) {\n            RectF r = topRects[i];'
assert needle in s
s=s.replace(needle,'for (int i = 0; i < 4; i++) {\n            RectF r = topRects[i];',1)
# Explain empty states and keep a chosen wallpaper when VS hasn't cached its catalogue yet.
needle='''        int top = -1;
        for (int i = 0; i < 3; i++) if (cardFocus[i].value > 0.02f'''
assert needle in s
s=s.replace(needle,'''        if (cards.isEmpty()) {
            String hint = VistaFeed.installed(getContext())
                ? "Abre o VistaStream para carregar o catálogo e as recomendações."
                : "Instala o VistaStream nesta TV para recomendações reais.";
            c.drawText(hint, MARGIN * k, (L_CARDS_TOP + 130) * k, lCardMeta);
        }
''' + needle,1)
needle="boolean own = !Recs.hasKey(getContext()) && Photos.on(getContext());"
assert needle in s
s=s.replace(needle,"boolean own = cards.isEmpty() && Photos.on(getContext());",1)

needle='for (Recs.Pick p : recs.wanted()) recs.backdrop(p);'
assert needle in s
s=s.replace(needle,'''for (Recs.Pick p : cards) recs.backdrop(p);
        for (Recs.Pick p : recs.wanted()) recs.backdrop(p);''',1)
p.write_text(s,encoding="utf-8")

# Favor VS's actual catalog over third-party TMDB service recommendations.
replace("Recs.java", '        List<Pick> list = new ArrayList<>();\n        if (!hasKey(context)) return NovaDemo.picks(kind);',
        '''        List<Pick> list = new ArrayList<>();
        if (VistaFeed.installed(context)) {
            List<Pick> fromVista = VistaFeed.picks(context, kind);
            return fromVista; // never show fictional demos when VistaStream is installed
        }
        if (!hasKey(context)) return NovaDemo.picks(kind);''')
replace("Recs.java",'SERVICE_NAME.put("netflix", "Netflix");',
    'SERVICE_NAME.put("vistastream", "VistaStream");\n        SERVICE_NAME.put("netflix", "Netflix");')
replace("Recs.java",'return new File(new File(context.getCacheDir(), "recs"), p.backdrop.replace("/", ""));',
    '''String name = p.backdrop.startsWith("https://")
            ? "vista_" + Integer.toHexString(p.backdrop.hashCode()) + ".jpg"
            : p.backdrop.replace("/", "");
        return new File(new File(context.getCacheDir(), "recs"), name);''')
replace("Recs.java", 'new URL(IMAGES + p.backdrop).openConnection()',
    'new URL(p.backdrop.startsWith("https://") ? p.backdrop : IMAGES + p.backdrop).openConnection()')

# Add a first-class Vista ID menu entry to the ORIGINAL FreeMyTV settings shell.
replace("SettingsActivity.java",'case MAIN: return 7;', 'case MAIN: return 8;')
replace("SettingsActivity.java", 'Ui.tr("Remote Buttons"), Ui.tr("TV Settings")};',
    'Ui.tr("Remote Buttons"), Ui.tr("TV Settings"), "Vista ID"};',n=2)
replace("SettingsActivity.java",
    'String[] values = {working ? "" : mode, Ui.tr("Preview"), "", "", "", "", ""};',
    'String[] values = {working ? "" : mode, Ui.tr("Preview"), "", "", "", "", "", ""};')
replace("SettingsActivity.java",
    '                activity.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));',
    '''                if (focus == 6) activity.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                else activity.startActivity(new Intent(activity, VistaAccountActivity.class));''')
replace("SettingsActivity.java", 'c.drawText("Free My TV", cx, 612 * k, title);',
    'c.drawText("VistaLauncher", cx, 612 * k, title);')
print("VistaLauncher v1 patched: native shelf, VS continue + anime, remote UI, optional Vista ID.")
