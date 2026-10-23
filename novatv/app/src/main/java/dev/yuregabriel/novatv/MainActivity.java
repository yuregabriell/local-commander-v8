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
import android.widget.HorizontalScrollView;
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
    private LinearLayout allRow;
    private FrameLayout contentRoot;
    private CursorOverlay cursor;
    private RemoteServer remoteServer;
    private AppItem featuredApp;
    private TextView heroTitle,heroSubtitle,heroLaunch;
    private ImageView heroIcon;
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
    @Override protected void onResume() { super.onResume(); handler.removeCallbacks(updateClock); handler.post(updateClock); startRemote(); }
    @Override protected void onPause() { handler.removeCallbacks(updateClock); if(remoteServer!=null)remoteServer.stop(); super.onPause(); }
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
        FrameLayout root = new FrameLayout(this);
        contentRoot=root;
        scenic=new ScenicBackground(this);
        scenic.setScene(prefs.getInt("scene",0));
        root.addView(scenic,new FrameLayout.LayoutParams(-1,-1));
        photoView=new ImageView(this);photoView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(photoView,new FrameLayout.LayoutParams(-1,-1));
        loadPhoto();
        View shade=new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0xb9080e1b,0x9f080d18,0xed060b13}));
        root.addView(shade,new FrameLayout.LayoutParams(-1,-1));

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout page=vertical();
        page.setPadding(d(55),d(28),d(55),d(34));
        scroll.addView(page,new ScrollView.LayoutParams(-1,-2));
        root.addView(scroll,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout nav=horizontal();
        TextView start=text("Para você",31,WHITE,true);
        nav.addView(start);
        TextView tabs=text("    Explorar     Aplicações",14,0xffc6cede,false);
        nav.addView(tabs);
        nav.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        clockView=text("",17,WHITE,true);
        LinearLayout.LayoutParams c=new LinearLayout.LayoutParams(-2,-2);c.rightMargin=d(20);
        nav.addView(clockView,c);
        nav.addView(roundAction("⚙",()->showMenu()));
        page.addView(nav,new LinearLayout.LayoutParams(-1,d(53)));

        FrameLayout hero=new FrameLayout(this);
        GradientDrawable heroBg=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
          new int[]{0xee111b31,0xdb233b54,0x787392b3});
        heroBg.setCornerRadius(d(24));
        hero.setBackground(heroBg);
        hero.setClipToOutline(true);
        LinearLayout heroContent=vertical();
        heroContent.setPadding(d(30),d(25),d(26),d(18));
        TextView eyebrow=text("EM DESTAQUE   ·   NOVA TV",11,0xffb4d3f3,true);
        eyebrow.setLetterSpacing(.20f);
        heroContent.addView(eyebrow);
        heroTitle=text("As tuas aplicações.",35,WHITE,true);
        LinearLayout.LayoutParams h=new LinearLayout.LayoutParams(-1,-2);h.topMargin=d(14);
        heroContent.addView(heroTitle,h);
        heroSubtitle=text("Tudo no seu lugar. Sem recomendações nem distrações.",14,0xffc6d5eb,false);
        heroSubtitle.setMaxLines(2);
        LinearLayout.LayoutParams subp=new LinearLayout.LayoutParams(-1,-2);subp.topMargin=d(5);
        heroContent.addView(heroSubtitle,subp);
        TextView launch=roundAction("Abrir em destaque   ›",()->{ if(featuredApp!=null)launchApp(featuredApp); });
        heroLaunch=launch;
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(d(215),d(45));lp.topMargin=d(17);
        heroContent.addView(launch,lp);
        FrameLayout.LayoutParams hc=new FrameLayout.LayoutParams(d(675),-1,Gravity.LEFT);
        hero.addView(heroContent,hc);
        heroIcon=new ImageView(this);heroIcon.setAlpha(.75f);
        heroIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams iconp=new FrameLayout.LayoutParams(d(133),d(133),Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        iconp.rightMargin=d(93);hero.addView(heroIcon,iconp);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,d(227));hp.topMargin=d(24);
        page.addView(hero,hp);

        LinearLayout l=horizontal();
        TextView favoriteTitle=text("As tuas aplicações",19,WHITE,true);
        l.addView(favoriteTitle);
        l.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        TextView edit=roundAction("Personalizar   ›",()->showMenu());
        l.addView(edit,new LinearLayout.LayoutParams(d(148),d(37)));
        LinearLayout.LayoutParams fl=new LinearLayout.LayoutParams(-1,d(48));fl.topMargin=d(16);
        page.addView(l,fl);
        HorizontalScrollView favScroll=new HorizontalScrollView(this);
        favScroll.setHorizontalScrollBarEnabled(false);favScroll.setClipToPadding(false);
        favoritesRow=horizontal();favoritesRow.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
        favScroll.addView(favoritesRow,new HorizontalScrollView.LayoutParams(-2,-2));
        page.addView(favScroll,new LinearLayout.LayoutParams(-1,d(114)));

        LinearLayout l2=horizontal();
        TextView allLabel=text("Todas as aplicações",18,WHITE,true);
        l2.addView(allLabel);
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,d(46));ap.topMargin=d(12);
        page.addView(l2,ap);
        HorizontalScrollView allScroll=new HorizontalScrollView(this);
        allScroll.setHorizontalScrollBarEnabled(false);allScroll.setClipToPadding(false);
        allRow=horizontal();allRow.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
        allScroll.addView(allRow,new HorizontalScrollView.LayoutParams(-2,-2));
        page.addView(allScroll,new LinearLayout.LayoutParams(-1,d(106)));
        TextView help=text("Mantém OK para fixar apps    ·    ⚙ Personalizar    ·    O launcher original continua disponível",12,0xffa4b5cd,false);
        LinearLayout.LayoutParams foot=new LinearLayout.LayoutParams(-1,-2);
        foot.topMargin=d(17);page.addView(help,foot);

        cursor=new CursorOverlay(this);cursor.setClickable(false);
        root.addView(cursor,new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);
        renderApps();
    }
    private TextView roundAction(String name,Runnable task) {
        TextView v=text(name,15,WHITE,true);
        v.setGravity(Gravity.CENTER);v.setPadding(d(11),0,d(11),0);
        v.setBackground(focusBackground());decorateFocus(v);
        v.setOnClickListener(x->task.run());return v;
    }
    private void renderApps(){
        if(favoritesRow==null||allRow==null)return;
        favoritesRow.removeAllViews();allRow.removeAllViews();
        Set<String> chosen=favorites();
        int count=0;
        for(AppItem a:apps){if(chosen.contains(a.pkg)){favoritesRow.addView(tile(a,true));count++;}}
        if(count==0){
            TextView hint=text("Mantém OK pressionado numa aplicação abaixo para a adicionar aqui.",13,MUTED,false);
            favoritesRow.addView(hint);
        }
        for(AppItem a:apps)allRow.addView(tile(a,false));
        if(featuredApp==null||!apps.contains(featuredApp)){
            featuredApp=apps.isEmpty()?null:apps.get(0);
        }
        if(count>0&&!chosen.contains(featuredApp==null?"":featuredApp.pkg)){
            for(AppItem a:apps)if(chosen.contains(a.pkg)){featuredApp=a;break;}
        }
        updateFeatured(featuredApp);
    }
    private View tile(AppItem item,boolean favorite) {
        LinearLayout tile=vertical();
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(d(11),d(10),d(11),d(9));
        tile.setBackground(focusBackground());
        LinearLayout.LayoutParams tileParams=new LinearLayout.LayoutParams(d(favorite?155:138),d(favorite?101:93));
        tileParams.rightMargin=d(12);
        if(favorite)tileParams.bottomMargin=d(8);
        tile.setLayoutParams(tileParams);
        ImageView img=new ImageView(this);
        img.setImageDrawable(item.icon);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tile.addView(img,new LinearLayout.LayoutParams(d(favorite?46:39),d(favorite?46:39)));
        TextView name=text(item.title,13,WHITE,true);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,d(27));np.topMargin=d(5);
        tile.addView(name,np);
        decorateFocus(tile);
        tile.setOnFocusChangeListener((v,focused)->{
            v.animate().scaleX(focused?1.048f:1f).scaleY(focused?1.048f:1f).setDuration(130).start();
            if(focused)updateFeatured(item);
        });
        tile.setOnClickListener(v->launchApp(item));
        tile.setOnLongClickListener(v->{toggleFavorite(item);return true;});
        return tile;
    }
    private void updateFeatured(AppItem app){
        if(app==null||heroTitle==null)return;
        featuredApp=app;
        heroTitle.setText(app.title);
        heroSubtitle.setText("Abre "+app.title+" diretamente a partir da tua televisão.");
        heroIcon.setImageDrawable(app.icon);
    }
    private void showMenu(){
        String[] choices={"Alterar tema de fundo","Escolher fotografia","Remover fotografia","Emparelhar telemóvel","Definições da TV","Fechar Nova TV"};
        new android.app.AlertDialog.Builder(this).setTitle("Personalizar Nova TV")
            .setItems(choices,(dialog,index)->{
                if(index==0)cycleTheme();
                else if(index==1)choosePhoto();
                else if(index==2)clearPhoto();
                else if(index==3)showPairing();
                else if(index==4)openSettings();
                else if(index==5)finish();
            }).show();
    }
    private void showPairing(){
        String address=RemoteServer.localIp();
        String pin=remoteServer==null?"Abre novamente Nova TV":remoteServer.pin;
        new android.app.AlertDialog.Builder(this)
          .setTitle("Ligar o telemóvel")
          .setMessage("No APK Nova TV Remote, introduz:\n\nIP: "+address+"\nPorta: 42377\nCódigo: "+pin+
          "\n\nTelemóvel e TV devem estar na mesma rede Wi-Fi. O controlo funciona apenas enquanto Nova TV estiver aberto.")
          .setPositiveButton("Entendi",(d,w)->{}).show();
    }
    private void startRemote(){
        if(remoteServer!=null)remoteServer.stop();
        remoteServer=new RemoteServer(handler,new RemoteServer.Listener(){
            @Override public void onMove(float dx,float dy){
                if(cursor==null)return;
                float x=cursor.cursorX(),y=cursor.cursorY();
                if(x<0||y<0){x=cursor.getWidth()/2f;y=cursor.getHeight()/2f;}
                cursor.place(Math.max(0,Math.min(cursor.getWidth()-d(24),x+dx)),
                    Math.max(0,Math.min(cursor.getHeight()-d(24),y+dy)));
            }
            @Override public void onClick(){remoteClick();}
            @Override public void onBack(){onBackPressed();}
            @Override public void onNavigate(String key){
                if("SELECT".equals(key)){View f=getCurrentFocus();if(f!=null)f.performClick();return;}
                int dir="UP".equals(key)?View.FOCUS_UP:"DOWN".equals(key)?View.FOCUS_DOWN:
                    "LEFT".equals(key)?View.FOCUS_LEFT:View.FOCUS_RIGHT;
                View focus=getCurrentFocus();
                View next=focus==null?null:focus.focusSearch(dir);
                if(next!=null)next.requestFocus();
            }
        });
        remoteServer.start();
    }
    private void remoteClick(){
        if(cursor==null||contentRoot==null||cursor.cursorX()<0)return;
        View target=hit(contentRoot,(int)cursor.cursorX(),(int)cursor.cursorY());
        if(target!=null){target.requestFocus();target.performClick();}
    }
    private View hit(View v,int x,int y){
        if(v==cursor||!v.isShown())return null;
        android.graphics.Rect rect=new android.graphics.Rect();
        if(!v.getGlobalVisibleRect(rect)||!rect.contains(x,y))return null;
        if(v instanceof ViewGroup){
            ViewGroup group=(ViewGroup)v;
            for(int i=group.getChildCount()-1;i>=0;i--){
                View found=hit(group.getChildAt(i),x,y);
                if(found!=null)return found;
            }
        }
        return v.isClickable()?v:null;
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
