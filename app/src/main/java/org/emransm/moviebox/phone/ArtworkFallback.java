package org.emransm.moviebox.phone;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Honest missing-artwork mark, never a poster belonging to another title. */
final class ArtworkFallback extends Drawable {
    private final int background,foreground;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    ArtworkFallback(int background,int foreground){this.background=background;this.foreground=foreground;}
    @Override public void draw(Canvas canvas){Rect b=getBounds();paint.setColor(background);canvas.drawRect(b,paint);int side=Math.max(12,Math.min(b.width(),b.height())/5);NavIcon icon=new NavIcon("Movies",foreground,side);icon.setAlpha(95);int x=b.centerX()-side/2,y=b.centerY()-side/2;icon.setBounds(x,y,x+side,y+side);icon.draw(canvas);}
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
    @Override public int getOpacity(){return PixelFormat.OPAQUE;}
    @Override public ConstantState getConstantState(){return new ConstantState(){@Override public Drawable newDrawable(){return new ArtworkFallback(background,foreground);}@Override public int getChangingConfigurations(){return 0;}};}
}
