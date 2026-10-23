package dev.yuregabriel.novatv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import java.util.Random;

public class ScenicBackground extends View {
    private int scene = 0;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    public ScenicBackground(Context c) { super(c); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
    public void setScene(int value) { scene = ((value % 4) + 4) % 4; invalidate(); }
    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        int[][] colors = {
            {0xff101a38,0xff254f83,0xff100e28,0xff668ba9},
            {0xff081b2b,0xff146078,0xff061c34,0xff62bac8},
            {0xff27192d,0xff734760,0xff17192c,0xffe4b08b},
            {0xff131927,0xff293850,0xff091522,0xff93a7bd}
        };
        int[] a = colors[scene];
        p.setShader(new LinearGradient(0,0,0,h,a[0],a[2],Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,p); p.setShader(null);
        p.setShader(new RadialGradient(w*.76f,h*.39f,w*.72f,a[1],Color.TRANSPARENT,Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,p); p.setShader(null);
        Random r = new Random(819 + scene);
        p.setColor(0x65ffffff);
        for (int i=0; i<55; i++) {
            float x=r.nextFloat()*w, y=r.nextFloat()*h*.62f;
            p.setAlpha(25+r.nextInt(80));
            c.drawCircle(x,y,.45f+r.nextFloat()*1.2f,p);
        }
        mountain(c,w,h,h*.64f, 0xff1b2b47, 1.6f, 1);
        mountain(c,w,h,h*.76f, 0xff122139, 2.5f, 2);
        mountain(c,w,h,h*.86f, 0xff0a1728, 3.2f, 3);
        p.setShader(new LinearGradient(0,h*.4f,0,h,0x00000000,0xdd030914,Shader.TileMode.CLAMP));
        c.drawRect(0,h*.4f,w,h,p); p.setShader(null);
    }
    private void mountain(Canvas c,float w,float h,float baseline,int color,float amp,int seed) {
        Path path = new Path(); path.moveTo(0,h); path.lineTo(0,baseline);
        Random r = new Random(32+seed+scene*8);
        for (int i=1;i<=16;i++) {
            float x=w*i/16f;
            float y=baseline - (r.nextFloat()-.3f)*h*.11f*amp;
            path.lineTo(x,y);
        }
        path.lineTo(w,h); path.close();
        p.setColor(color); p.setShader(null); p.setAlpha(255);
        c.drawPath(path,p);
    }
}
