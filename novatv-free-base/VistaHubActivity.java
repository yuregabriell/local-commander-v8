package io.freemytv;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * An actual native TV hub inside VistaLauncher, not four fake app tiles.
 * Installed apps retain their own isolated data and playback; VS provides
 * the same-device, current-profile movie/series/anime/continue feed.
 */
public final class VistaHubActivity extends Activity {
    private static final int BG = 0xff0b0f1b;
    private static final int SURFACE = 0xff20263a;
    private static final int MUTED = 0xffb1b9d2;
    private static final int INK = 0xfff6f7ff;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService imageIO = Executors.newFixedThreadPool(2);
    private LinearLayout body;
    private int lastCount = -1;
    private boolean alive;

    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private GradientDrawable background(int color, int radius, boolean focused) {
        GradientDrawable drawable = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR, new int[] {color, 0xff13182a});
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(focused ? 3 : 1), focused ? 0xffa0a9ff : 0xff323b53);
        return drawable;
    }
    private TextView text(String value, float size, int color, boolean strong) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextColor(color); t.setTextSize(size);
        t.setMaxLines(2);
        if (strong) t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return t;
    }
    private void space(LinearLayout c, int n) {
        View v = new View(this);
        c.addView(v, new LinearLayout.LayoutParams(1, dp(n)));
    }
    private void decorateFocus(View v, int color) {
        v.setFocusable(true); v.setFocusableInTouchMode(false);
        v.setBackground(background(color, 18, false));
        v.setOnFocusChangeListener((target, hasFocus) -> {
            target.setBackground(background(color, 18, hasFocus));
            target.animate().scaleX(hasFocus ? 1.035f : 1f).scaleY(hasFocus ? 1.035f : 1f).setDuration(100).start();
        });
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        alive = true;
        showShell();
    }
    @Override protected void onResume() {
        super.onResume();
        VistaFeed.refresh(this);
        handler.postDelayed(update, 700);
    }
    @Override protected void onDestroy() {
        alive = false;
        handler.removeCallbacksAndMessages(null);
        imageIO.shutdownNow();
        super.onDestroy();
    }
    private void showShell() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(45), dp(25), dp(45), dp(55));
        scroll.addView(column);
        setContentView(scroll);
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        column.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.addView(text("VISTA", 14, 0xff929cff, true));
        brand.addView(text("O teu entretenimento", 32, INK, true));
        heading.addView(brand, new LinearLayout.LayoutParams(0, -2, 1f));
        Button account = actionButton("Vista ID", () -> startActivity(new Intent(this, VistaAccountActivity.class)));
        heading.addView(account);
        Button back = actionButton("Voltar à Home", this::finish);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(-2, dp(54));
        backParams.leftMargin = dp(12); heading.addView(back, backParams);
        space(column, 21);
        column.addView(text("Todos os apps Vista, numa experiência para a televisão. Usa as setas e OK no comando.", 15, MUTED, false));
        space(column, 19);
        sectionTitle(column, "Aplicações Vista");
        appRow(column);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        column.addView(body);
        space(column, 24);
        column.addView(text("Os catálogos são metadados do VistaStream. Só aparecem títulos reais; a reprodução depende das fontes e apps instalados. Conta e sincronização cloud continuam opcionais.", 13, MUTED, false));
        fillFeeds();
    }
    private Button actionButton(String title, Runnable work) {
        Button button = new Button(this);
        button.setText(title); button.setAllCaps(false);
        button.setTextSize(15); button.setTextColor(INK);
        decorateFocus(button, 0xff303955);
        button.setOnClickListener(v -> work.run());
        return button;
    }
    private void sectionTitle(LinearLayout parent, String heading) {
        space(parent, 16);
        parent.addView(text(heading, 24, INK, true));
        space(parent, 11);
    }
    private void appRow(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        addApp(row, "VistaStream", "Filmes, séries e animes", 0xff7158b5,
            "com.vistastream.tv.debug", "com.vistastream.tv");
        addApp(row, "VistaTube", "Vídeos e canais", 0xff146d9d,
            "app.vistatube.mobile.debug", "app.vistatube.mobile");
        addApp(row, "VistaMusic", "Música, playlists e rádio", 0xffa63172,
            "com.vistamusic.app");
        addApp(row, "VistaBrowser", "Navegação e vídeos", 0xff4739a1,
            "com.harvester.browser", "com.yure.videoharvester");
    }
    private void addApp(LinearLayout row, String title, String subtitle, int color, String... packages) {
        String installed = installed(packages);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(10), dp(16), dp(10));
        decorateFocus(card, color);
        card.addView(text(title, 20, INK, true));
        space(card, 7);
        card.addView(text(installed == null ? "Não instalado na TV" : subtitle, 13,
            installed == null ? 0xffd8a9ab : 0xffe6e7fc, false));
        LinearLayout.LayoutParams param = new LinearLayout.LayoutParams(0, dp(116), 1f);
        param.rightMargin = dp(12); row.addView(card, param);
        card.setOnClickListener(v -> {
            if (installed == null) toast(title + " ainda não está instalado nesta TV.");
            else launch(installed, null);
        });
    }
    private String installed(String... packages) {
        PackageManager pm = getPackageManager();
        for (String pkg : packages) if (HomeApps.launchIntent(pm, pkg) != null) return pkg;
        return null;
    }
    private void launch(String pkg, String link) {
        try {
            if (link != null && link.startsWith("https://www.themoviedb.org/")) {
                Intent deep = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                deep.addCategory(Intent.CATEGORY_BROWSABLE);
                deep.setPackage(pkg);
                if (deep.resolveActivity(getPackageManager()) != null) {
                    startActivity(deep); return;
                }
            }
            Intent app = HomeApps.launchIntent(getPackageManager(), pkg);
            if (app != null) startActivity(app);
            else toast("Aplicação indisponível.");
        } catch (Exception error) { toast("Não foi possível abrir a aplicação."); }
    }
    private final Runnable update = new Runnable() {
        @Override public void run() {
            if (!alive || body == null) return;
            int count = VistaFeed.section(VistaHubActivity.this, "movie", 12).size()
                + VistaFeed.section(VistaHubActivity.this, "series", 12).size()
                + VistaFeed.section(VistaHubActivity.this, "anime", 12).size()
                + VistaFeed.section(VistaHubActivity.this, "continue", 4).size();
            if (count != lastCount) { lastCount = count; fillFeeds(); }
            if (count == 0) handler.postDelayed(this, 2400);
        }
    };
    private void fillFeeds() {
        if (body == null) return;
        body.removeAllViews();
        String pkg = VistaFeed.packageName(this);
        if (pkg == null) {
            sectionTitle(body, "Recomendações");
            body.addView(text("Instala o VistaStream nesta televisão para mostrar filmes, séries, animes e Continuar a assistir.", 16, MUTED, false));
            return;
        }
        section(body, "Continuar a assistir", "continue", pkg, 5);
        section(body, "Filmes para descobrir", "movie", pkg, 10);
        section(body, "Séries em destaque", "series", pkg, 10);
        section(body, "Animes", "anime", pkg, 10);
    }
    private void section(LinearLayout parent, String title, String key, String pkg, int limit) {
        sectionTitle(parent, title);
        List<VistaFeed.Entry> items = VistaFeed.section(this, key, limit);
        if (items.isEmpty()) {
            parent.addView(text("continue".equals(key)
                ? "Quando começares um filme ou episódio no VistaStream, poderá aparecer aqui."
                : "Abre o VistaStream para atualizar o catálogo desta secção.", 14, MUTED, false));
            return;
        }
        HorizontalScrollView strip = new HorizontalScrollView(this);
        strip.setHorizontalScrollBarEnabled(false);
        LinearLayout track = new LinearLayout(this);
        track.setOrientation(LinearLayout.HORIZONTAL);
        strip.addView(track);
        parent.addView(strip);
        for (VistaFeed.Entry item : items) movieCard(track, item, pkg, "continue".equals(key));
    }
    private void movieCard(LinearLayout row, VistaFeed.Entry item, String pkg, boolean resume) {
        FrameLayout frame = new FrameLayout(this);
        decorateFocus(frame, resume ? 0xff5b599e : SURFACE);
        frame.setClipToOutline(true);
        frame.setOnClickListener(v -> launch(pkg, item.url));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(282), dp(166));
        params.rightMargin = dp(14);
        row.addView(frame, params);
        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setAlpha(0.72f);
        frame.addView(poster, new FrameLayout.LayoutParams(-1, -1));
        loadImage(poster, item.artwork);
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0x00111626,0xdd101424}));
        frame.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout labels = new LinearLayout(this);
        labels.setPadding(dp(15), dp(10), dp(12), dp(14));
        labels.setOrientation(LinearLayout.VERTICAL);
        if (resume) labels.addView(text("CONTINUAR", 12, 0xffbcbcff, true));
        labels.addView(text(item.title == null ? "" : item.title, 19, INK, true));
        space(labels, 5);
        labels.addView(text(item.meta == null ? "" : item.meta, 12, 0xffd6dbed, false));
        FrameLayout.LayoutParams caption = new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        frame.addView(labels,caption);
    }
    private void loadImage(ImageView image, String uri) {
        if (uri == null || !uri.startsWith("https://")) return;
        imageIO.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection)new URL(uri).openConnection();
                connection.setConnectTimeout(3500); connection.setReadTimeout(4000);
                connection.setInstanceFollowRedirects(true);
                if (connection.getResponseCode() / 100 != 2) return;
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream input = connection.getInputStream()) {
                    byte[] block = new byte[4096]; int n,total = 0;
                    while ((n=input.read(block))>0 && total < 900000) {
                        int count = Math.min(n, 900000-total);
                        bytes.write(block,0,count);
                        total+=count;
                    }
                }
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 3;
                byte[] source = bytes.toByteArray();
                Bitmap bmp = BitmapFactory.decodeByteArray(source,0,source.length,opts);
                if (bmp != null) handler.post(() -> { if (alive && image.getParent()!=null) image.setImageBitmap(bmp); });
            } catch (Exception ignored) {
            } finally { if (connection != null) connection.disconnect(); }
        });
    }
    private void toast(String text) { Toast.makeText(this,text,Toast.LENGTH_LONG).show(); }
}
