package dev.yuregabriel.novatv;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.content.Context;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MainActivity extends Activity {
    private static final int SELECT_IMAGE = 901;
    private static final int WHITE = 0xfff3f6ff, MUTED = 0xffb7c6df;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<AppItem> apps = new ArrayList<>();
    private SharedPreferences prefs;
    private ScenicBackground scenic;
    private ImageView photoView;
    private LinearLayout favoritesRow;
    private GridLayout allGrid;
    private TextView clockView;
    private Bitmap photoBitmap;
    private final Runnable updateClock = new Runnable() {
        @Override public void run() {
            if (clockView != null) {
                String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
                clockView.setText(time);
            }
            handler.postDelayed(this, 30000);
        }
    };
    private static final class AppItem {
        String pkg, title;
        ComponentName component;
        Drawable icon;
        AppItem(String p, String t, ComponentName c, Drawable i) {
            pkg=p; title=t; component=c; icon=i;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=getSharedPreferences("nova_preferences",MODE_PRIVATE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
        loadApps();
        prepareFavorites();
        buildUi();
    }
    @Override protected void onResume() { super.onResume(); handler.removeCallbacks(updateClock); handler.post(updateClock); }
    @Override protected void onPause() { handler.removeCallbacks(updateClock); super.onPause(); }
    @Override protected void onDestroy() {
        handler.removeCallbacks(updateClock);
        if(photoBitmap != null && !photoBitmap.isRecycled()) photoBitmap.recycle();
        super.onDestroy();
    }

    private int d(float px){ return (int)(getResources().getDisplayMetrics().density*px+.5f); }
    private LinearLayout vertical(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout horizontal(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private TextView text(String value,int sp,int color,boolean bold){
        TextView v=new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(color);
        if(bold) v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return v;
    }
    private GradientDrawable bg(int color,int radius,int strokeColor) {
        GradientDrawable b=new GradientDrawable(); b.setColor(color); b.setCornerRadius(d(radius));
        if(strokeColor!=0)b.setStroke(d(2),strokeColor);
        return b;
    }
    private Drawable focusBackground() {
        StateListDrawable b=new StateListDrawable();
        b.addState(new int[]{android.R.attr.state_focused},bg(0xff435780,17,0xffd0dbff));
        b.addState(new int[]{android.R.attr.state_pressed},bg(0xff435780,17,0xffd0dbff));
        b.addState(new int[]{},bg(0x9b10192d,17,0x447b8eaa));
        return b;
    }
    private void decorateFocus(View v) {
        v.setFocusable(true);v.setFocusableInTouchMode(false);
        v.setOnFocusChangeListener((view,focused)->{
            view.animate().scaleX(focused?1.055f:1f).scaleY(focused?1.055f:1f).setDuration(120).start();
        });
    }
    private void loadApps() {
        PackageManager pm=getPackageManager(); Map<String,AppItem> map=new HashMap<>();
        queryApps(pm,Intent.CATEGORY_LEANBACK_LAUNCHER,map);
        queryApps(pm,Intent.CATEGORY_LAUNCHER,map);
        apps.clear();apps.addAll(map.values());
        Collections.sort(apps,Comparator.comparing(a->a.title.toLowerCase(Locale.getDefault())));
    }
    private void queryApps(PackageManager pm,String category,Map<String,AppItem> map) {
        Intent intent=new Intent(Intent.ACTION_MAIN);intent.addCategory(category);
        List<ResolveInfo> list=pm.queryIntentActivities(intent,0);
        for(ResolveInfo item:list) {
            ActivityInfo a=item.activityInfo; if(a==null || a.packageName.equals(getPackageName()))continue;
            if(map.containsKey(a.packageName))continue;
            try {
                String name=String.valueOf(item.loadLabel(pm));
                map.put(a.packageName,new AppItem(a.packageName,name,
                    new ComponentName(a.packageName,a.name),item.loadIcon(pm)));
            } catch(Exception ignored) {}
        }
    }
    private void prepareFavorites() {
        if(prefs.contains("favorites"))return;
        HashSet<String> defaults=new HashSet<>();
        for(AppItem a:apps) {
            String n=a.title.toLowerCase(Locale.ROOT);
            if(n.contains("netflix") || n.contains("youtube") || n.contains("prime video"))defaults.add(a.pkg);
        }
        prefs.edit().putStringSet("favorites",defaults).apply();
    }
    private Set<String> favorites(){ return new HashSet<>(prefs.getStringSet("favorites",Collections.emptySet())); }
    private void toggleFavorite(AppItem app) {
        Set<String> set=favorites();boolean added;
        if(set.contains(app.pkg)){ set.remove(app.pkg);added=false;} else {set.add(app.pkg);added=true;}
        prefs.edit().putStringSet("favorites",set).apply();
        renderApps();
        toast(added?"Adicionado aos favoritos":"Removido dos favoritos");
    }

    private void buildUi() {
        FrameLayout root=new FrameLayout(this);
        scenic=new ScenicBackground(this);scenic.setScene(prefs.getInt("scene",0));
        root.addView(scenic,new FrameLayout.LayoutParams(-1,-1));
        photoView=new ImageView(this);photoView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(photoView,new FrameLayout.LayoutParams(-1,-1));
        loadPhoto();
        View shade=new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{0xf5050b1a,0xc5070d1b,0x70070918}));
        root.addView(shade,new FrameLayout.LayoutParams(-1,-1));

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
        LinearLayout page=vertical();page.setPadding(d(54),d(29),d(54),d(37));
        scroll.addView(page,new ScrollView.LayoutParams(-1,-2));
        root.addView(scroll,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout header=horizontal();
        TextView brand=text("◈  NOVA TV",23,WHITE,true);
        brand.setLetterSpacing(.13f);
        header.addView(brand);
        View spacer=new View(this);header.addView(spacer,new LinearLayout.LayoutParams(0,1,1));
        clockView=text("",22,WHITE,true);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.setMargins(0,0,d(18),0);
        header.addView(clockView,cp);
        header.addView(action("Temas",()->cycleTheme()));
        header.addView(action("Escolher fundo",()->choosePhoto()));
        header.addView(action("Retirar foto",()->clearPhoto()));
        header.addView(action("Definições",()->openSettings()));
        page.addView(header,new LinearLayout.LayoutParams(-1,d(65)));

        TextView eyebrow=text("A TUA TELEVISÃO, À TUA MANEIRA",12,0xffb7c9ee,true);eyebrow.setLetterSpacing(.23f);
        LinearLayout.LayoutParams e=new LinearLayout.LayoutParams(-1,-2);e.topMargin=d(47);page.addView(eyebrow,e);
        TextView hero=text("Bem-vindo a casa.",44,WHITE,true);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=d(6);page.addView(hero,hp);
        TextView sub=text("Só o que interessa. Todas as tuas aplicações num único lugar.",16,MUTED,false);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=d(8);sp.bottomMargin=d(33);page.addView(sub,sp);

        page.addView(section("OS TEUS FAVORITOS"),new LinearLayout.LayoutParams(-1,d(39)));
        favoritesRow=horizontal();favoritesRow.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
        page.addView(favoritesRow,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout.LayoutParams h1=new LinearLayout.LayoutParams(-1,d(28));page.addView(new View(this),h1);
        page.addView(section("TODAS AS APLICAÇÕES"),new LinearLayout.LayoutParams(-1,d(39)));
        allGrid=new GridLayout(this);allGrid.setColumnCount(5);
        page.addView(allGrid,new LinearLayout.LayoutParams(-1,-2));
        TextView footer=text("OK: abrir app    •    Manter OK: adicionar ou remover favorito    •    Voltar: regressar à Hisense",12,0xffa3afc5,false);
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,-2);fp.topMargin=d(30);page.addView(footer,fp);
        setContentView(root);
        renderApps();
    }
    private TextView section(String label){
        TextView t=text(label,14,0xffe9eeff,true);t.setLetterSpacing(.14f);return t;
    }
    private TextView action(String title,Runnable task){
        TextView b=text(title,13,WHITE,true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(d(13),0,d(13),0);
        b.setBackground(focusBackground());decorateFocus(b);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,d(40));
        lp.leftMargin=d(9);b.setLayoutParams(lp);
        b.setOnClickListener(v->task.run());return b;
    }
    private void renderApps() {
        if(favoritesRow==null)return;
        favoritesRow.removeAllViews();allGrid.removeAllViews();
        Set<String> fav=favorites();int count=0;
        for(AppItem a:apps) {
            if(fav.contains(a.pkg) && count<7){ favoritesRow.addView(tile(a,true));count++; }
        }
        if(count==0) {
            TextView label=text("Mantém OK pressionado sobre uma aplicação para a fixar aqui.",14,MUTED,false);
            favoritesRow.addView(label);
        }
        for(AppItem a:apps) {
            GridLayout.LayoutParams p=new GridLayout.LayoutParams();
            p.width=d(162);p.height=d(121);p.setMargins(d(4),d(4),d(10),d(10));
            allGrid.addView(tile(a,false),p);
        }
    }
    private View tile(AppItem item,boolean favorite) {
        LinearLayout tile=vertical();tile.setGravity(Gravity.CENTER);
        tile.setBackground(focusBackground());tile.setPadding(d(10),d(9),d(10),d(7));
        if(favorite) {
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(d(162),d(121));p.setMargins(0,d(3),d(14),d(6));
            tile.setLayoutParams(p);
        }
        ImageView icon=new ImageView(this);icon.setImageDrawable(item.icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tile.addView(icon,new LinearLayout.LayoutParams(d(49),d(49)));
        TextView t=text(item.title,14,WHITE,true);t.setGravity(Gravity.CENTER);t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tparams=new LinearLayout.LayoutParams(-1,d(41));tparams.topMargin=d(6);
        tile.addView(t,tparams);
        decorateFocus(tile);
        tile.setOnClickListener(v->launchApp(item));
        tile.setOnLongClickListener(v->{toggleFavorite(item);return true;});
        return tile;
    }
    private void launchApp(AppItem item) {
        try {
            PackageManager pm=getPackageManager();
            Intent i=pm.getLeanbackLaunchIntentForPackage(item.pkg);
            if(i==null)i=pm.getLaunchIntentForPackage(item.pkg);
            if(i==null){i=new Intent(Intent.ACTION_MAIN);i.setComponent(item.component);}
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch(Exception e){toast("Não foi possível abrir esta aplicação.");}
    }
    private void cycleTheme() {
        int next=(prefs.getInt("scene",0)+1)%4;
        prefs.edit().putInt("scene",next).apply();scenic.setScene(next);
        String[] names={"Aurora","Oceano","Pôr do sol","Noturno"};
        toast("Tema: "+names[next]);
    }
    private void openSettings() {
        try {startActivity(new Intent(Settings.ACTION_SETTINGS));}
        catch(Exception e){toast("Abre as Definições pelo comando da televisão.");}
    }
    private void choosePhoto() {
        try {
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(i,SELECT_IMAGE);
        } catch(Exception ex) {
            try {
                Intent i=new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("image/*");
                startActivityForResult(i,SELECT_IMAGE);
            } catch(Exception e){toast("Esta TV precisa de um seletor de ficheiros para escolher uma fotografia.");}
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==SELECT_IMAGE && result==RESULT_OK && data!=null && data.getData()!=null) {
            Uri uri=data.getData();
            try {getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
            catch(Exception ignored){}
            prefs.edit().putString("photo",uri.toString()).apply();
            loadPhoto();
        }
    }
    private void clearPhoto(){prefs.edit().remove("photo").apply();loadPhoto();toast("Foto removida");}
    private void loadPhoto() {
        if(photoView==null)return;
        photoView.setImageDrawable(null);
        if(photoBitmap!=null && !photoBitmap.isRecycled())photoBitmap.recycle();
        photoBitmap=null;
        String uriString=prefs.getString("photo","");
        if(uriString.isEmpty())return;
        Uri uri=Uri.parse(uriString);
        try {
            BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;
            try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,opts);}
            int sample=1;
            while((opts.outWidth/sample)>1920 || (opts.outHeight/sample)>1080)sample*=2;
            BitmapFactory.Options real=new BitmapFactory.Options();
            real.inSampleSize=sample;real.inPreferredConfig=Bitmap.Config.RGB_565;
            try(InputStream in=getContentResolver().openInputStream(uri)){
                photoBitmap=BitmapFactory.decodeStream(in,null,real);
            }
            if(photoBitmap!=null)photoView.setImageBitmap(photoBitmap);
        } catch(Exception e) {toast("Não foi possível carregar o fundo. Escolhe outra imagem.");}
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}
