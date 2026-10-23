#!/usr/bin/env python3
"""Nova TV v0.4: safe Free My TV adaptation, offline demos, personalized landscape.
All changes run *after* patch_safe_preview.py, which suppresses system/Home changes.
Pinned upstream 7e6754c1887182fd29220fc605833336bc69e6a4 (MIT).
"""
from pathlib import Path
src=Path("app/src/io/freemytv")
def replace(file, before, after, expected=1):
    p=src/file
    s=p.read_text(encoding="utf-8")
    n=s.count(before)
    if n!=expected: raise ValueError(f"{file}: expected {expected} occurrences of {before[:100]!r}, found {n}")
    p.write_text(s.replace(before,after),encoding="utf-8")
    print("Patched",file)

nova_demo=r'''package io.freemytv;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/** Nova TV's original offline sample cards, never advertised as playable movies. */
final class NovaDemo {
    private NovaDemo() {}

    static List<Recs.Pick> picks(String kind) {
        List<Recs.Pick> out=new ArrayList<>();
        if(kind==null||"movie".equals(kind)) {
            out.add(pick(1001,"movie","Horizonte Azul","NOVA PLAY · CONCEITO · DEMONSTRAÇÃO","/nova_demo1.png"));
            out.add(pick(1002,"movie","Além das Estrelas","FILME FICTÍCIO · EM BREVE","/nova_demo2.png"));
        }
        if(kind==null||"tv".equals(kind)) {
            out.add(pick(2001,"tv","A Última Aurora","SÉRIE FICTÍCIA · EM BREVE","/nova_demo3.png"));
        }
        return out;
    }
    private static Recs.Pick pick(int id,String kind,String title,String meta,String background) {
        Recs.Pick p=new Recs.Pick();
        p.id=id;
        p.kind=kind;
        p.title=title;
        p.meta=meta;
        p.backdrop=background;
        p.pkg=null; // Non-interactive demonstrator; not a fake playable title.
        p.detailed=true;
        return p;
    }
    private static void copyAsset(Context c,String name,File destination) throws IOException{
        File parent=destination.getParentFile();
        if(parent==null||(!parent.isDirectory()&&!parent.mkdirs()))throw new IOException("Pasta indisponível");
        try(InputStream in=c.getAssets().open(name);OutputStream out=new FileOutputStream(destination)){
            byte[] buffer=new byte[16384];int n;
            while((n=in.read(buffer))>=0)if(n>0)out.write(buffer,0,n);
        }
    }
    static void seed(Context c) {
        try {
            File recs=new File(c.getCacheDir(),"recs");
            if(!recs.isDirectory()) recs.mkdirs();
            for(int i=1;i<=3;i++){
                String name="nova_demo"+i+".png";
                File out=new File(recs,name);
                if(!out.exists())copyAsset(c,name,out);
            }
            SharedPreferences sp=c.getSharedPreferences("nova-tv",Context.MODE_PRIVATE);
            if(!sp.getBoolean("wallpaper_seeded",false)){
                if(Photos.files(c).isEmpty())restoreWallpaper(c);
                sp.edit().putBoolean("wallpaper_seeded",true).apply();
            }
        }catch(Exception ex){
            Log.w("NovaTV","Offline assets not ready",ex);
        }
    }
    static void restoreWallpaper(Context c) throws IOException {
        Photos.removeAll(c);
        File inbox=c.getExternalFilesDir("photos");
        if(inbox==null)throw new IOException("Armazenamento indisponível");
        String name="nova_landscape.jpg";
        try(InputStream ignored=c.getAssets().open(name)){}catch(IOException ex){name="nova_landscape.png";}
        copyAsset(c,name,new File(inbox,name));
        Photos.importInbox(c,null);
    }
    static void chooseWallpaper(Context c,Uri uri) throws IOException {
        // First stage in private inbox. Change existing images only once we know reading works.
        File folder=c.getExternalFilesDir("photos");
        if(folder==null)throw new IOException("Armazenamento indisponível");
        if(!folder.isDirectory())folder.mkdirs();
        File tmp=new File(c.getCacheDir(),"nova_wallpaper_import.tmp");
        try(InputStream in=c.getContentResolver().openInputStream(uri);
            OutputStream out=new FileOutputStream(tmp)){
            if(in==null)throw new IOException("Não foi possível abrir a imagem");
            byte[] buffer=new byte[16384];int n;long bytes=0;
            while((n=in.read(buffer))>=0){if(n>0){bytes+=n;if(bytes>16000000)throw new IOException("Imagem demasiado grande");out.write(buffer,0,n);}}
            if(bytes<512)throw new IOException("Imagem inválida");
        }
        Photos.removeAll(c);
        File saved=new File(folder,"wallpaper.jpg");
        try(InputStream in=new FileInputStream(tmp);OutputStream out=new FileOutputStream(saved)){
            byte[] b=new byte[16384];int n;while((n=in.read(b))>=0)if(n>0)out.write(b,0,n);
        }finally {tmp.delete();}
        Photos.importInbox(c,null);
    }
}
'''
(src/"NovaDemo.java").write_text(nova_demo,encoding="utf-8")

replace("HomeActivity.java",
        "        Ui.init(this);\n        home = new HomeView(this);",
        "        Ui.init(this);\n        NovaDemo.seed(this);\n        home = new HomeView(this);")
replace("HomeView.java",
        "return Recs.hasKey(getContext());",
        "return true; // Show demo tiles even without a TMDB key, never ask the user to pay.")
replace("HomeView.java",
        "boolean own = !picksOn() && Photos.on(getContext());",
        "boolean own = !Recs.hasKey(getContext()) && Photos.on(getContext());")
replace("Recs.java",
        "if (!hasKey(context)) return list;    // no TMDB key: no recommendations",
        "if (!hasKey(context)) return NovaDemo.picks(kind);  // Clearly labeled local placeholders.")
replace("Recs.java",
        "if (f.exists()) return;\n        //noinspection ResultOfMethodCallIgnored",
        "if (f.exists() || p.backdrop.startsWith(\"/nova_demo\")) return;\n        //noinspection ResultOfMethodCallIgnored")

# When user selects the wallpapers row, open native image picker with bundled example reset.
replace("SettingsActivity.java",
        "                askRemovePhotos();\n                return;",
        "                activity.showWallpaperChoices();\n                return;")
replace("SettingsActivity.java",
        'l.add(Ui.tr("My Photos"));',
        'l.add("Papel de parede");')
replace("SettingsActivity.java",
        'v[PHOTOS_ROW] = photos == 0 ? Ui.tr("photos|None")\n                    : Prefs.photos() ? Ui.plural("plural|one:{n} photo|other:{n} photos", photos) : Ui.tr("Off");',
        'v[PHOTOS_ROW] = photos == 0 ? "Sem imagem" : Prefs.photos() ? "Imagem ativa · OK para alterar" : "Desligado";')

p=src/"SettingsActivity.java"
settings=p.read_text(encoding="utf-8")
anchor="    static final class SettingsView extends View implements Choreographer.FrameCallback {"
assert anchor in settings
dialog=r'''
    private static final int NOVA_PICK_WALLPAPER = 9017;

    void showWallpaperChoices() {
        final String[] opts = {"Escolher fotografia da TV", "Restaurar paisagem de exemplo",
                "Desligar imagem de fundo", "Voltar"};
        new AlertDialog.Builder(this)
            .setTitle("Papel de parede · Nova TV")
            .setItems(opts, (dlg, choice) -> {
                if(choice==0) {
                    Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("image/*");
                    try {
                        startActivityForResult(intent,NOVA_PICK_WALLPAPER);
                    }catch(Exception e){
                        try{
                            Intent alt=new Intent(Intent.ACTION_GET_CONTENT);
                            alt.setType("image/*");
                            startActivityForResult(alt,NOVA_PICK_WALLPAPER);
                        }catch(Exception ignored){
                            new AlertDialog.Builder(this).setMessage(
                                "Esta TV não tem um seletor de ficheiros. Instala um gestor de ficheiros que suporte a escolha de imagens.")
                                .setPositiveButton("OK",null).show();
                        }
                    }
                } else if(choice==1) {
                    try {
                        NovaDemo.restoreWallpaper(this);
                        view.invalidate();
                    } catch(Exception e){
                        new AlertDialog.Builder(this).setMessage("Erro ao restaurar: "+e.getMessage())
                            .setPositiveButton("OK",null).show();
                    }
                } else if(choice==2) {
                    Prefs.setPhotos(false);
                    view.invalidate();
                }
            }).show();
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=NOVA_PICK_WALLPAPER||resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        try {
            NovaDemo.chooseWallpaper(this,data.getData());
            Prefs.setPhotos(true);
            Photos.importInbox(this,()->{
                if(view!=null)view.invalidate();
            });
            new AlertDialog.Builder(this).setMessage("Imagem recebida. Volta ao Nova TV para veres o fundo.")
                .setPositiveButton("OK",null).show();
        }catch(Exception e){
            new AlertDialog.Builder(this).setMessage("Não foi possível usar a imagem: "+e.getMessage())
                .setPositiveButton("OK",null).show();
        }
    }

'''
settings=settings.replace(anchor,dialog+anchor,1)
p.write_text(settings,encoding="utf-8")
print("Added wallpaper picker and example restoration")
