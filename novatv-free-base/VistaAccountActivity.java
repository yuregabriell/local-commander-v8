package io.freemytv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** TV remote friendly, opt-in local Vista ID hub; no cloud account token is exchanged. */
public final class VistaAccountActivity extends Activity {
    private static final int LINK = 8607;
    private SharedPreferences prefs;

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable rounded(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(17)); return d;
    }
    private TextView label(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextColor(color); t.setTextSize(size);
        t.setPadding(0, dp(8), 0, dp(8));
        return t;
    }
    private void add(LinearLayout panel, String title, int size, int color) { panel.addView(label(title,size,color)); }
    private void button(LinearLayout panel, String title, Runnable action) {
        Button b = new Button(this);
        b.setText(title); b.setAllCaps(false); b.setTextSize(16); b.setTextColor(0xffffffff);
        b.setBackground(rounded(0xff292b49));
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(57));
        params.bottomMargin = dp(12);
        panel.addView(b,params);
    }
    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        prefs = getSharedPreferences("vista_launcher_account", MODE_PRIVATE);
        draw();
    }
    private void draw() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(45),dp(28),dp(45),dp(38));
        panel.setBackgroundColor(0xff10121e);
        add(panel, "VISTA LAUNCHER", 14, 0xffafb2ff);
        TextView title = label("Vista ID", 32, 0xffffffff);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD); panel.addView(title);
        String id = prefs.getString("id",null);
        if (id == null) {
            add(panel,"Usar sem conta está sempre disponível. Podes ligar o teu perfil local do Vista ID a esta TV.",17,0xffc6c8d3);
        } else {
            add(panel,"Perfil local: " + prefs.getString("name","") + " · " + id,19,0xffffffff);
        }
        add(panel,"A biblioteca e o progresso continuam dentro de cada app. Não existe sincronização cloud entre dispositivos nesta versão.",15,0xffaeb4cb);
        button(panel,id == null ? "Ligar Vista ID" : "Alterar Vista ID", () -> {
            Intent link = new Intent("com.vista.id.action.LINK_LOCAL_PROFILE").setPackage("com.vista.id");
            try { startActivityForResult(link, LINK); }
            catch (ActivityNotFoundException e) { toast("Instala primeiro Vista ID v0.2 ou superior nesta TV."); }
            catch (Exception e) { toast("Não foi possível abrir Vista ID."); }
        });
        if (id != null) button(panel,"Desligar, sem apagar histórico", () -> {
            prefs.edit().remove("id").remove("name").apply(); draw();
        });
        add(panel,"Aplicações Vista instaladas nesta televisão",18,0xffffffff);
        for (String[] app : new String[][]{
            {"VistaStream","com.vistastream.tv.debug"},
            {"VistaTube","app.vistatube.mobile.debug"},
            {"VistaMusic","com.vistamusic.app"},
            {"VistaBrowser","com.harvester.browser"}}) {
            Intent launch = HomeApps.launchIntent(getPackageManager(),app[1]);
            if (launch != null) {
                button(panel,"Abrir " + app[0],() -> {
                    try { startActivity(launch); }
                    catch(Exception e){ toast("Não foi possível abrir "+app[0]); }
                });
            } else add(panel,app[0]+" · não instalado ou incompatível com TV",13,0xff8c90a8);
        }
        button(panel,"Voltar",this::finish);
        ScrollView root = new ScrollView(this);
        root.addView(panel);
        setContentView(root);
    }
    private void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(req!=LINK || result!=RESULT_OK || data==null)return;
        String id=data.getStringExtra("com.vista.id.profile_id");
        String name=data.getStringExtra("com.vista.id.profile_name");
        if(id==null || !id.matches("V[A-Z2-9]{12}") ||
            !"local".equals(data.getStringExtra("com.vista.id.mode"))) {
            toast("Resposta Vista ID inválida.");return;
        }
        prefs.edit().putString("id",id).putString("name",name==null?"":name.substring(0,Math.min(48,name.length()))).apply();
        draw();
    }
}
