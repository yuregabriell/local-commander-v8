package dev.yuregabriel.novatv;
import android.content.Context;
import android.graphics.*;
import android.view.View;
public final class CursorOverlay extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float px=-100,py=-100;
    public CursorOverlay(Context c){super(c);setWillNotDraw(false);}
    public void place(float x,float y){px=x;py=y;invalidate();}
    public float cursorX(){return px;} public float cursorY(){return py;}
    @Override protected void onDraw(Canvas canvas){
        if(px<0||py<0)return;
        float x=px,y=py,scale=getResources().getDisplayMetrics().density;
        Path triangle=new Path();triangle.moveTo(x,y);
        triangle.lineTo(x+5*scale,y+21*scale);
        triangle.lineTo(x+10*scale,y+14*scale);
        triangle.lineTo(x+17*scale,y+17*scale);triangle.close();
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4*scale);p.setColor(0xee000000);
        canvas.drawPath(triangle,p);
        p.setStyle(Paint.Style.FILL);p.setColor(Color.WHITE);
        canvas.drawPath(triangle,p);
    }
}
