package org.emransm.moviebox.phone;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Rendering checks against packaged resources; never changes the selected launcher or library. */
@RunWith(AndroidJUnit4.class)
public class LogoTransparencyScenarios {
    private static final int SIZE=324;
    private static final int VISIBLE_ALPHA=32;

    private Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private Drawable drawable(String name,String kind){
        Context context=context();
        int id=context.getResources().getIdentifier(name,kind,context.getPackageName());
        assertNotEquals("Packaged "+name,0,id);
        return context.getDrawable(id).mutate();
    }
    private Bitmap render(Drawable drawable){
        Bitmap result=Bitmap.createBitmap(SIZE,SIZE,Bitmap.Config.ARGB_8888);
        drawable.setBounds(0,0,SIZE,SIZE);drawable.draw(new Canvas(result));return result;
    }
    private void transparentBorderAndVisibleMark(String name,Drawable drawable){
        Bitmap bitmap=render(drawable);
        try{
            int border=SIZE/20,borderPixels=0,visibleBorder=0,visible=0;
            int left=SIZE,right=-1,top=SIZE,bottom=-1;
            for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){
                boolean edge=x<border||y<border||x>=SIZE-border||y>=SIZE-border;
                int alpha=Color.alpha(bitmap.getPixel(x,y));
                if(edge){borderPixels++;if(alpha>4)visibleBorder++;}
                if(alpha>VISIBLE_ALPHA){visible++;left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);}
            }
            assertTrue(name+" must not retain an opaque background tile",visibleBorder<=borderPixels/200);
            assertTrue(name+" has a visible logo",visible>SIZE*SIZE/50);
            assertTrue(name+" keeps clear outer padding",left>=border&&top>=border&&right<SIZE-border&&bottom<SIZE-border);
            assertTrue(name+" has legible artwork rather than a tiny mark",Math.max(right-left+1,bottom-top+1)>=SIZE*.40);
        }finally{bitmap.recycle();}
    }

    @Test public void allInAppLogosHaveTransparentOuterPaddingAndVisibleArtwork(){
        transparentBorderAndVisibleMark("Original",drawable("phone_icon","drawable"));
        for(int n=1;n<=10;n++){
            String name=String.format(java.util.Locale.ROOT,"app_logo_%02d_safe",n);
            transparentBorderAndVisibleMark(name,drawable(name,"drawable"));
        }
    }

    @Test public void adaptiveForegroundsSurviveCircleAndRoundedLauncherMasks(){
        assumeTrue("Adaptive icon resources require API26+",Build.VERSION.SDK_INT>=26);
        for(int n=0;n<=10;n++){
            String name=n==0?"phone_launcher":String.format(java.util.Locale.ROOT,"app_logo_%02d",n);
            Drawable icon=drawable(name,"mipmap");assertTrue(name+" uses an adaptive icon",icon instanceof AdaptiveIconDrawable);
            AdaptiveIconDrawable adaptive=(AdaptiveIconDrawable)icon;
            Bitmap background=render(adaptive.getBackground());
            try{
                for(int y=0;y<SIZE;y+=4)for(int x=0;x<SIZE;x+=4)
                    assertEquals(name+" background is transparent",0,Color.alpha(background.getPixel(x,y)));
            }finally{background.recycle();}
            Bitmap foreground=render(adaptive.getForeground());
            try{
                int visible=0,circleLost=0,roundedLost=0;
                // Android's foreground safe area is central66dp of the108dp canvas.
                // A circle is stricter than a rounded rectangle with the same bounds.
                double center=SIZE/2.0,radius=SIZE*33.0/108.0;
                double boxLeft=center-radius,boxRight=center+radius,corner=radius*.38;
                for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++)if(Color.alpha(foreground.getPixel(x,y))>VISIBLE_ALPHA){
                    visible++;
                    double dx=x+.5-center,dy=y+.5-center;
                    if(dx*dx+dy*dy>radius*radius)circleLost++;
                    double qx=Math.max(Math.abs(dx)-(radius-corner),0),qy=Math.max(Math.abs(dy)-(radius-corner),0);
                    if(x+.5<boxLeft||x+.5>boxRight||y+.5<boxLeft||y+.5>boxRight||qx*qx+qy*qy>corner*corner)roundedLost++;
                }
                assertTrue(name+" contains visible foreground",visible>SIZE*SIZE/200);
                assertTrue(name+" would lose visible artwork in a circular launcher mask",circleLost<=Math.max(2,visible/200));
                assertTrue(name+" would lose visible artwork in a rounded launcher mask",roundedLost<=Math.max(2,visible/200));
            }finally{foreground.recycle();}
        }
    }
}
