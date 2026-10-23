package dev.yuregabriel.novatvremote;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RemoteActivity extends Activity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private Socket socket;
    private BufferedWriter writer;
    private volatile boolean connected;
    private EditText ip,pin;
    private TextView status;
    private float lastX,lastY,startX,startY;
    private boolean drag;
    private int d(float dp){return (int)(getResources().getDisplayMetrics().density*dp+.5f);}
    private GradientDrawable shape(int color,int radius){
        GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(d(radius));return g;
    }
    private TextView t(String text,int sp,boolean bold){
        TextView v=new TextView(this);v.setText(text);v.setTextSize(sp);v.setTextColor(Color.WHITE);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return v;
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        try {
            buildScreen();
        } catch (Throwable e) {
            android.util.Log.e("NovaTVRemote","Erro ao iniciar interface",e);
            TextView fallback = new TextView(this);
            fallback.setTextColor(Color.WHITE);
            fallback.setTextSize(17);
            fallback.setPadding(d(24),d(60),d(24),d(24));
            fallback.setBackgroundColor(0xff0b1221);
            fallback.setText("O Nova TV Remote encontrou um problema ao abrir.\n\n"
                + e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage())
                + "\n\nEsta mensagem ajuda a diagnosticar a incompatibilidade.");
            setContentView(fallback);
        }
    }
    private void buildScreen(){
        getWindow().setStatusBarColor(0xff0b1221);
        getWindow().setNavigationBarColor(0xff0b1221);
        LinearLayout root=column();root.setPadding(d(22),d(32),d(22),d(16));root.setBackgroundColor(0xff0b1221);
        ScrollView scroll=new ScrollView(this);scroll.addView(root);
        setContentView(scroll);

        TextView h=t("NOVA TV",29,true);root.addView(h);
        TextView sub=t("Transforma o teu telemóvel num touchpad.",15,false);
        sub.setTextColor(0xffaabbd5);sub.setPadding(0,d(7),0,d(22));root.addView(sub);
        ip=new EditText(this);pin=new EditText(this);
        ip.setSingleLine(true);pin.setSingleLine(true);
        ip.setTextColor(-1);pin.setTextColor(-1);
        ip.setHintTextColor(0xff7186a2);pin.setHintTextColor(0xff7186a2);
        ip.setHint("IP da TV (ex.: 192.168.1.50)");
        pin.setHint("Código de 6 dígitos");
        ip.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        pin.setInputType(2);
        ip.setText(getPreferences(0).getString("ip",""));
        root.addView(ip,new LinearLayout.LayoutParams(-1,d(58)));
        root.addView(pin,new LinearLayout.LayoutParams(-1,d(58)));
        TextView connect=button("Ligar ao Nova TV",()->connect());
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,d(55));
        cp.setMargins(0,d(6),0,d(12));root.addView(connect,cp);
        status=t("Abre Nova TV na TV → Definições → Emparelhar telemóvel.",13,false);
        status.setTextColor(0xffaabbd5);root.addView(status);
        TextView padTitle=t("TOUCHPAD",13,true);padTitle.setTextColor(0xff93acd8);
        padTitle.setPadding(0,d(28),0,d(9));root.addView(padTitle);

        TextView pad=t("Desliza para mover o cursor\n\nTOCA PARA CLICAR",18,true);
        pad.setGravity(Gravity.CENTER);
        pad.setBackground(shape(0xff1b2943,22));
        root.addView(pad,new LinearLayout.LayoutParams(-1,d(285)));
        pad.setOnTouchListener((v,event)->{
            if(!connected)return true;
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
                lastX=startX=event.getX();lastY=startY=event.getY();drag=false;return true;
            }
            if(event.getActionMasked()==MotionEvent.ACTION_MOVE){
                float dx=event.getX()-lastX,dy=event.getY()-lastY;
                if(Math.abs(event.getX()-startX)>d(8)||Math.abs(event.getY()-startY)>d(8))drag=true;
                lastX=event.getX();lastY=event.getY();
                if(Math.abs(dx)+Math.abs(dy)>.4f)send(String.format(Locale.US,"MOVE %.1f %.1f",dx*1.9f,dy*1.9f));
                return true;
            }
            if(event.getActionMasked()==MotionEvent.ACTION_UP){
                if(!drag)send("CLICK");return true;
            }
            return true;
        });
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);
        controls.addView(button("← Voltar",()->send("BACK")),new LinearLayout.LayoutParams(0,d(57),1));
        controls.addView(button("  OK  ",()->send("CLICK")),new LinearLayout.LayoutParams(0,d(57),1));
        controls.addView(button(" Selecionar ",()->send("NAV SELECT")),new LinearLayout.LayoutParams(0,d(57),1));
        LinearLayout.LayoutParams ctrl=new LinearLayout.LayoutParams(-1,d(57));ctrl.topMargin=d(16);root.addView(controls,ctrl);
        root.addView(button("↑",()->send("NAV UP")),new LinearLayout.LayoutParams(-1,d(48)));
        LinearLayout lr=new LinearLayout(this);lr.setGravity(Gravity.CENTER);
        lr.addView(button("←",()->send("NAV LEFT")),new LinearLayout.LayoutParams(0,d(48),1));
        lr.addView(button("↓",()->send("NAV DOWN")),new LinearLayout.LayoutParams(0,d(48),1));
        lr.addView(button("→",()->send("NAV RIGHT")),new LinearLayout.LayoutParams(0,d(48),1));
        root.addView(lr,new LinearLayout.LayoutParams(-1,d(48)));
        TextView foot=t("Funciona apenas dentro do Nova TV nesta versão. Usa a mesma rede Wi-Fi nos dois dispositivos.",12,false);
        foot.setTextColor(0xff93a4bd);foot.setPadding(0,d(21),0,d(20));root.addView(foot);
    }
    private TextView button(String s,Runnable task){
        TextView b=t(s,15,true);b.setGravity(Gravity.CENTER);b.setBackground(shape(0xff314469,12));
        b.setOnClickListener(v->task.run());return b;
    }
    private void connect(){
        String host=ip.getText().toString().trim();
        String code=pin.getText().toString().trim();
        if(!code.matches("[0-9]{6}")||host.length()==0){status.setText("Insere o IP e o código de 6 dígitos.");return;}
        status.setText("A ligar…");
        getPreferences(0).edit().putString("ip",host).apply();
        io.execute(()->{
            try {
                if(socket!=null)socket.close();
                Socket s=new Socket();
                s.connect(new InetSocketAddress(host,42377),3000);
                s.setSoTimeout(3500);
                BufferedWriter output=new BufferedWriter(new OutputStreamWriter(s.getOutputStream(),"UTF-8"));
                BufferedReader input=new BufferedReader(new InputStreamReader(s.getInputStream(),"UTF-8"));
                output.write("AUTH "+code+"\n");output.flush();
                String response=input.readLine();
                if(!"OK".equals(response)){s.close();throw new IOException("Código incorreto.");}
                s.setSoTimeout(0);
                socket=s;writer=output;connected=true;
                ui.post(()->status.setText("● Ligado ao Nova TV — desliza para mover o cursor"));
            }catch(Exception e){
                connected=false;
                ui.post(()->status.setText("Falha na ligação. Confirma IP, código, rede Wi-Fi e se Nova TV está aberto."));
            }
        });
    }
    private void send(String command){
        if(!connected)return;
        io.execute(()->{
            try{
                if(writer==null)throw new IOException("Não ligado");
                writer.write(command+"\n");writer.flush();
            }catch(Exception e){
                connected=false;ui.post(()->status.setText("Ligação terminada. Abre Nova TV e volta a emparelhar."));
            }
        });
    }
    @Override protected void onDestroy(){
        connected=false;
        try{if(socket!=null)socket.close();}catch(Exception ignored){}
        io.shutdownNow();super.onDestroy();
    }
}
