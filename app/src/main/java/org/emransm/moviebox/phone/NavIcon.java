package org.emransm.moviebox.phone;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Consistent outline icons, drawn natively at the current display density. */
final class NavIcon extends Drawable {
    private final String name;private final Paint pen=new Paint(3);private final int size;
    NavIcon(String name,int color,int size){this.name=name;this.size=size;pen.setColor(color);pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(1.6f);pen.setStrokeCap(Paint.Cap.ROUND);pen.setStrokeJoin(Paint.Join.ROUND);setBounds(0,0,size,size);}
    public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}
    @Override public void draw(Canvas canvas){canvas.save();canvas.translate(getBounds().left,getBounds().top);canvas.scale(getBounds().width()/24f,getBounds().height()/24f);Path path=new Path();
        switch(name){
            case "Play":path.moveTo(7,4);path.lineTo(20,12);path.lineTo(7,20);path.close();break;
            case "Pause":canvas.drawLine(8,4,8,20,pen);canvas.drawLine(16,4,16,20,pen);break;
            case "Fullscreen":path.moveTo(3,9);path.lineTo(3,3);path.lineTo(9,3);path.moveTo(15,3);path.lineTo(21,3);path.lineTo(21,9);path.moveTo(21,15);path.lineTo(21,21);path.lineTo(15,21);path.moveTo(9,21);path.lineTo(3,21);path.lineTo(3,15);break;
            case "Share":canvas.drawRoundRect(3,9,15,21,2,2,pen);canvas.drawLine(10,14,21,3,pen);canvas.drawLine(14,3,21,3,pen);canvas.drawLine(21,3,21,10,pen);break;
            case "Download":canvas.drawLine(12,3,12,16,pen);canvas.drawLine(7,11,12,16,pen);canvas.drawLine(17,11,12,16,pen);path.moveTo(3,16);path.lineTo(3,21);path.lineTo(21,21);path.lineTo(21,16);break;
            case "Volume":case "Muted":path.moveTo(3,9);path.lineTo(7,9);path.lineTo(12,5);path.lineTo(12,19);path.lineTo(7,15);path.lineTo(3,15);path.close();if(name.equals("Volume")){canvas.drawArc(10,7,20,17,-60,120,false,pen);canvas.drawArc(9,3,25,21,-55,110,false,pen);}else{canvas.drawLine(17,9,22,15,pen);canvas.drawLine(22,9,17,15,pen);}break;
            case "ChevronDown":path.moveTo(6,9);path.lineTo(12,15);path.lineTo(18,9);break;
            case "Back":canvas.drawLine(20,12,4,12,pen);path.moveTo(11,5);path.lineTo(4,12);path.lineTo(11,19);break;
            case "Discover":path.moveTo(3,10);path.lineTo(12,3);path.lineTo(21,10);path.lineTo(21,21);path.lineTo(15,21);path.lineTo(15,14);path.lineTo(9,14);path.lineTo(9,21);path.lineTo(3,21);path.close();break;
            case "Downloads":canvas.drawRoundRect(4,3,20,22,3,3,pen);canvas.drawLine(12,7,12,16,pen);canvas.drawLine(8,12,12,16,pen);canvas.drawLine(16,12,12,16,pen);break;
            case "Me":canvas.drawCircle(12,7,4,pen);canvas.drawOval(3,14,21,22,pen);break;
            case "Movies":canvas.drawRoundRect(3,4,21,21,2,2,pen);canvas.drawLine(3,10,21,10,pen);canvas.drawLine(7,4,10,10,pen);canvas.drawLine(14,4,17,10,pen);break;
            case "TV Shows":canvas.drawRoundRect(3,7,21,21,2,2,pen);canvas.drawLine(8,2,12,7,pen);canvas.drawLine(17,2,12,7,pen);break;
            case "My list":path.moveTo(6,3);path.lineTo(18,3);path.lineTo(18,22);path.lineTo(12,17);path.lineTo(6,22);path.close();break;
            case "History":canvas.drawCircle(12,12,9,pen);canvas.drawLine(12,6,12,12,pen);canvas.drawLine(12,12,17,15,pen);break;
            case "Search":canvas.drawCircle(10,10,6.5f,pen);canvas.drawLine(15,15,21,21,pen);break;
            case "Settings":canvas.drawCircle(12,12,7,pen);canvas.drawCircle(12,12,2.5f,pen);for(int i=0;i<8;i++){double a=i*Math.PI/4;canvas.drawLine(12+(float)Math.cos(a)*7,12+(float)Math.sin(a)*7,12+(float)Math.cos(a)*10,12+(float)Math.sin(a)*10,pen);}break;
        }
        canvas.drawPath(path,pen);canvas.restore();
    }
    @Override public void setAlpha(int alpha){pen.setAlpha(alpha);}@Override public void setColorFilter(ColorFilter filter){pen.setColorFilter(filter);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
