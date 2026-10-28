package io.freemytv;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The actual local VistaStream catalog and active watch-progress, never fake films. */
final class VistaFeed {
    static final String VS_DEBUG = "com.vistastream.tv.debug";
    static final String VS_RELEASE = "com.vistastream.tv";
    private static final Uri URI = Uri.parse("content://com.vistastream.tv.homefeed/items");
    private static final long TTL = 45_000L;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile List<Entry> entries = new ArrayList<>();
    private static volatile boolean fetching;
    private static volatile long lastFetch;
    private static volatile String lastPackage;

    static final class Entry {
        String section, kind, title, meta, artwork, url;
        int tmdbId;
    }

    static String packageName(Context context) {
        for (String pkg : new String[] {VS_DEBUG, VS_RELEASE}) {
            try {
                Intent app = HomeApps.launchIntent(context.getPackageManager(), pkg);
                if (app != null) return pkg;
            } catch (Exception ignored) {}
        }
        return null;
    }

    static boolean installed(Context context) { return packageName(context) != null; }

    static void refresh(Context context) {
        final String target = packageName(context);
        if (target == null) { entries = new ArrayList<>(); lastPackage = null; return; }
        long now = System.currentTimeMillis();
        if (fetching || (target.equals(lastPackage) && now >= lastFetch && now - lastFetch < TTL)) return;
        fetching = true;
        final Context app = context.getApplicationContext();
        IO.execute(() -> {
            List<Entry> received = new ArrayList<>();
            try (Cursor cursor = app.getContentResolver().query(URI, null, null, null, null)) {
                if (cursor != null) {
                    int section = cursor.getColumnIndexOrThrow("section");
                    int kind = cursor.getColumnIndexOrThrow("kind");
                    int title = cursor.getColumnIndexOrThrow("title");
                    int meta = cursor.getColumnIndexOrThrow("meta");
                    int art = cursor.getColumnIndexOrThrow("artwork");
                    int url = cursor.getColumnIndexOrThrow("url");
                    int tmdb = cursor.getColumnIndexOrThrow("tmdbId");
                    while (cursor.moveToNext() && received.size() < 50) {
                        Entry e = new Entry();
                        e.section = cursor.getString(section);
                        e.kind = cursor.getString(kind);
                        e.title = cursor.getString(title);
                        e.meta = cursor.getString(meta);
                        e.artwork = cursor.getString(art);
                        e.url = cursor.getString(url);
                        e.tmdbId = cursor.getInt(tmdb);
                        if (e.title != null && !e.title.trim().isEmpty()) received.add(e);
                    }
                }
            } catch (Exception ignored) {
                // VS may be absent, sharing disabled, locked or updating; launcher stays usable.
            } finally {
                entries = received;
                lastPackage = target;
                lastFetch = System.currentTimeMillis();
                fetching = false;
            }
        });
    }

    static List<Recs.Pick> picks(Context c, String kind) {
        refresh(c);
        final String pkg = packageName(c);
        List<Recs.Pick> out = new ArrayList<>();
        if (pkg == null) return out;
        List<Entry> current = entries;
        List<Entry> selected = new ArrayList<>();
        if (kind == null) {
            for (Entry e : current) if ("continue".equals(e.section)) { selected.add(e); break; }
            for (String section : new String[] {"movie", "series", "anime"}) {
                for (Entry e : current) if (section.equals(e.section)) { selected.add(e); break; }
            }
        } else if ("movie".equals(kind)) {
            for (Entry e : current)
                if ("continue".equals(e.section) && "movie".equals(e.kind)) selected.add(e);
            for (Entry e : current) if ("movie".equals(e.section)) selected.add(e);
        } else {
            String section = "tv".equals(kind) ? "series" : "anime";
            for (Entry e : current) if (section.equals(e.section)) selected.add(e);
        }
        Set<String> seen = new HashSet<>();
        for (Entry e : selected) {
            if (out.size() >= 3) break;
            String dedupe = e.kind + ":" + (e.tmdbId > 0 ? e.tmdbId : e.title.toLowerCase(java.util.Locale.ROOT));
            if (!seen.add(dedupe)) continue;
            Recs.Pick p = new Recs.Pick();
            p.id = e.tmdbId;
            p.title = e.title;
            p.kind = e.kind;
            p.meta = e.meta == null ? "" : e.meta;
            p.backdrop = e.artwork == null || e.artwork.isEmpty() ? null : e.artwork;
            p.pkg = pkg;
            p.app = "vistastream";
            p.link = e.url == null || e.url.isEmpty() ? null : e.url;
            p.detailed = true;
            out.add(p);
        }
        return out;
    }

    static boolean isReady(Context context) {
        return installed(context) && !entries.isEmpty();
    }
}
