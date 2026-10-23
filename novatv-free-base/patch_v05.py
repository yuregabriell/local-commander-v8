#!/usr/bin/env python3
"""Nova TV v0.5 patch, applied after safe + v0.4 patches.
- Full-width sharp cover wallpaper with no letterboxing.
- Manage/remove shelf favorites, reorder, app details and uninstall user apps.
- Temperature beside TV icon, shown only when weather is available.
"""
from pathlib import Path
base=Path("app/src/io/freemytv")
def change(file,old,new,expected=1):
    path=base/file
    s=path.read_text(encoding="utf-8")
    n=s.count(old)
    if n!=expected:
        raise ValueError(f"{file}: expected {expected} occurrence(s) of {old[:110]!r}, got {n}")
    path.write_text(s.replace(old,new), encoding="utf-8")
    print("OK",file,old[:55])

# FIX: original 'own wallpaper' downsampled to 480x270, then upsized to 1366x768;
# keep nearly-screen resolution and use a light contrast overlay, still 16:9 cover.
change("Backdrop.java",
'''            if (own) {
                int[] sharp = reduce(out, GW, GH, 1, 1);
                int[] lightBg = ramp(sharp.clone(), 0.82f, 0.45f, true), nightBg = ramp(sharp, 0.76f, 0.5f, false);
                out.recycle();
                return new Backdrop(null, frost, null, wide, tall, light, lightGlass, night, nightGlass, lightBg, nightBg, GW, GH);
            }''',
'''            if (own) {
                // Near-native screen resolution, not the old blurry 480×270 wallpaper.
                // Cover fill is already calculated above by max(width ratio, height ratio):
                // no top/bottom or side bars, and no unnecessary extra zoom.
                int bw = Math.min(W, 1120), bh = Math.max(1, Math.round((float) bw * H / W));
                int[] sharp = reduce(out, bw, bh, 1, 0);
                int[] lightBg = rampCover(sharp.clone(), bw, bh, 0.28f, 0.10f, true);
                int[] nightBg = rampCover(sharp, bw, bh, 0.46f, 0.22f, false);
                out.recycle();
                return new Backdrop(null, frost, null, wide, tall, light, lightGlass, night, nightGlass, lightBg, nightBg, bw, bh);
            }''')
change("Backdrop.java",
'''    private static int[] ramp(int[] px, float left, float right, boolean light) {''',
'''    private static int[] rampCover(int[] px, int width, int height, float left, float right, boolean light) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float t = Math.max(0, Math.min(1, (x / (float) width - 0.24f) / 0.64f));
                float a = left + (right - left) * t * t * (3 - 2 * t);
                int idx = y * width + x, col = px[idx];
                int target = light ? 255 : 0;
                int r = Math.round(((col >> 16) & 255) * (1-a) + target * a);
                int g = Math.round(((col >> 8) & 255) * (1-a) + target * a);
                int b = Math.round((col & 255) * (1-a) + target * a);
                px[idx] = 0xff000000 | (r << 16) | (g << 8) | b;
            }
        }
        return px;
    }

    private static int[] ramp(int[] px, float left, float right, boolean light) {''')

# Both the apps sheet and shelf now get an explicit TV-friendly action menu
# on holding OK. All changes to apps are through Android's own package installer UI.
change("HomeView.java",
'''    private void hold() {
        if (sheetOpen) toggleShelf(); else startReorder();
    }''',
'''    private void hold() {
        if (reordering) return;
        if (sheetOpen && canToggleShelf(gridIndex)) {
            okHeld = true;
            manageApp(others.get(gridIndex), false);
        } else if (zone == Z_SHELF && shelfIndex < shelf.size()) {
            HomeApps.Item item = shelf.get(shelfIndex);
            if (item.kind == HomeApps.APP && item.pkg != null) {
                okHeld = true;
                manageApp(item, true);
            } else startReorder();
        } else startReorder();
    }''')
# same menu via TalkBack long click
change("HomeView.java",
'''        toggleShelf();
    }

    private boolean canToggleShelf''',
'''        if (canToggleShelf(gridIndex)) manageApp(others.get(gridIndex), false);
    }

    private boolean canToggleShelf''')
menu=r'''
    private void manageApp(HomeApps.Item item, boolean fromShelf) {
        if (item == null || item.pkg == null) return;
        handler.removeCallbacks(holdStart);
        holdArmed = false;
        okHeld = true;
        final boolean onShelf = HomeApps.shelfPackages(getContext()).contains(item.pkg);
        final String[] options = fromShelf
                ? new String[]{"Remover dos destaques", "Mover na faixa", "Desinstalar app", "Informações da app", "Cancelar"}
                : new String[]{onShelf ? "Remover dos destaques" : "Adicionar aos destaques",
                        "Desinstalar app", "Informações da app", "Cancelar"};
        new android.app.AlertDialog.Builder(activity)
                .setTitle(item.label)
                .setItems(options, (dialog, choice) -> {
                    if (fromShelf) {
                        if (choice == 0) changeShelf(item.pkg, true);
                        else if (choice == 1) startReorder();
                        else if (choice == 2) uninstallApp(item);
                        else if (choice == 3) appDetails(item);
                    } else {
                        if (choice == 0) changeShelf(item.pkg, onShelf);
                        else if (choice == 1) uninstallApp(item);
                        else if (choice == 2) appDetails(item);
                    }
                }).show();
    }

    private void changeShelf(String pkg, boolean remove) {
        final List<String> list = new ArrayList<>(HomeApps.shelfPackages(getContext()));
        list.remove(pkg);
        if (!remove) {
            if (list.size() >= HomeApps.MAX_SHELF) {
                showToast("A faixa está cheia. Remove primeiro uma aplicação.");
                return;
            }
            list.add(pkg);
        }
        Prefs.setShelf(list);
        appsStale = true;
        shelfEdited = true;
        showToast(remove ? "Removida dos destaques" : "Adicionada aos destaques");
        reload();
    }

    private void uninstallApp(HomeApps.Item item) {
        try {
            android.content.pm.ApplicationInfo info =
                    activity.getPackageManager().getApplicationInfo(item.pkg, 0);
            boolean system = (info.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
            if (system) {
                // System apps cannot be uninstalled by a third-party launcher.
                showToast("Aplicação de sistema. Abri as opções do Android.");
                appDetails(item);
                return;
            }
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_DELETE);
            intent.setData(android.net.Uri.parse("package:" + item.pkg));
            activity.startActivity(intent);
        } catch (Exception e) {
            showToast("A TV não permite desinstalar esta aplicação aqui.");
            appDetails(item);
        }
    }

    private void appDetails(HomeApps.Item item) {
        try {
            android.content.Intent intent = new android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + item.pkg));
            activity.startActivity(intent);
        } catch (Exception e) {
            showToast("Não foi possível abrir as informações da aplicação.");
        }
    }

'''
change("HomeView.java","    // MARK: - Moving apps on the shelf\n",menu+"    // MARK: - Moving apps on the shelf\n")
# Shows weather value next to the "live TV" button, without inventing temperature.
# The existing Open-Meteo HomeData feeds weather; city is editable in Home Settings.
change("HomeView.java",
'''    private void drawLightButtons(Canvas c) {
        applyTone();
        for (int i = 0; i < lightButtons.size(); i++) {''',
'''    private void drawLightButtons(Canvas c) {
        applyTone();
        // Open-Meteo current temperature, to the left of the top-right TV icon.
        // No temperature is shown if network weather is unavailable.
        HomeData.Weather wx = data.weather;
        if (Prefs.weather() && !Double.isNaN(wx.temp) && !lightButtons.isEmpty()) {
            String temp = HomeData.degrees(wx.temp);
            lTip.setAlpha(255);
            lTip.setColor(tone(0xff1c1c1e, 0xfff6f7fc));
            float x = topRects[3].left - 25*k - lTip.measureText(temp);
            float y = topRects[3].centerY() + Ui.halfCap(lTip);
            c.drawText(temp, x, y, lTip);
        }
        for (int i = 0; i < lightButtons.size(); i++) {''')
# Weather fetch change does not redraw status unless hero layer re-rendered
change("HomeView.java",
'''        heroDirty = statusDirty = drawerDirty = true;
        platter.invalidate();''',
'''        heroDirty = statusDirty = drawerDirty = true;
        heroLayer.invalidate();
        status.invalidate();
        platter.invalidate();''')
print("Nova TV 0.5: changes prepared")
