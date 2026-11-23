package org.emransm.moviebox.phone;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.media3.exoplayer.ExoPlayer;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

/** Screen-injected touches exercise the actual SurfaceView/gesture-layer dispatch. */
@RunWith(AndroidJUnit4.class)
public class PlayerChromeScenarios {
    private ActivityScenario<PhoneActivity> start()throws Exception{
        ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class);
        SliderPlaybackScenarios.Local service=new SliderPlaybackScenarios.Local();BlockFlowScenarios.isolate(s,service);
        JSONObject title=BlockFlowScenarios.title("qa-player-chrome",2);
        main(s,a->call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},title,true));waitFor(()->playing(s),10000);return s;
    }
    private void tap(ActivityScenario<PhoneActivity> s,float fraction)throws Exception{
        int[] point={0,0};main(s,a->{View v=(View)field(a,"video");int[] at=new int[2];v.getLocationOnScreen(at);point[0]=at[0]+Math.round(v.getWidth()*fraction);point[1]=at[1]+v.getHeight()/2;});
        long t=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,point[0],point[1],0),up=MotionEvent.obtain(t,t+50,MotionEvent.ACTION_UP,point[0],point[1],0);
        try{InstrumentationRegistry.getInstrumentation().sendPointerSync(down);InstrumentationRegistry.getInstrumentation().sendPointerSync(up);}finally{down.recycle();up.recycle();}Thread.sleep(450);
    }
    private void visible(ActivityScenario<PhoneActivity> s,boolean expected){main(s,a->{assertEquals(expected,field(a,"visibleControls"));assertEquals(expected,((View)field(a,"controls")).isShown());assertEquals(expected,((View)field(a,"playerTopbar")).isShown());});}
    private void capture(String name)throws Exception{
        Bitmap b=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(b);
        File file=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),name);
        try(OutputStream out=new FileOutputStream(file)){b.compress(Bitmap.CompressFormat.PNG,100,out);}finally{b.recycle();}
    }
    @Test public void singleTapAnywhereTogglesBothBarsWithoutSeekingIncludingZoom()throws Exception{
        try(ActivityScenario<PhoneActivity> s=start()){
            main(s,a->((ExoPlayer)field(a,"player")).pause());long before=position(s);
            for(float x:new float[]{.15f,.5f,.85f}){
                main(s,a->call(a,"showControls",new Class[]{boolean.class},false));tap(s,x);visible(s,false);tap(s,x);visible(s,true);
                assertTrue("Single tap must not seek",Math.abs(position(s)-before)<150);assertFalse(playing(s));
            }
            main(s,a->{put(a,"videoZoom",1.5f);call(a,"applyVideoTransform",new Class[]{});});tap(s,.5f);visible(s,false);tap(s,.5f);visible(s,true);
        }
    }
    @Test public void doubleTapSeeksOnceWithoutTogglingPlayback()throws Exception{
        try(ActivityScenario<PhoneActivity> s=start()){
            main(s,a->{ExoPlayer p=(ExoPlayer)field(a,"player");p.pause();p.seekTo(15000);});Thread.sleep(400);
            final java.util.concurrent.atomic.AtomicInteger seeks=new java.util.concurrent.atomic.AtomicInteger();
            main(s,a->((ExoPlayer)field(a,"player")).addListener(new androidx.media3.common.Player.Listener(){public void onPositionDiscontinuity(androidx.media3.common.Player.PositionInfo old,androidx.media3.common.Player.PositionInfo now,int reason){if(reason==androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK)seeks.incrementAndGet();}}));
            int[] at=new int[2];main(s,a->{View v=(View)field(a,"video");v.getLocationOnScreen(at);at[0]+=Math.round(v.getWidth()*.85f);at[1]+=v.getHeight()/2;});
            for(int n=0;n<2;n++){long t=SystemClock.uptimeMillis();MotionEvent d=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,at[0],at[1],0),u=MotionEvent.obtain(t,t+30,MotionEvent.ACTION_UP,at[0],at[1],0);try{InstrumentationRegistry.getInstrumentation().sendPointerSync(d);InstrumentationRegistry.getInstrumentation().sendPointerSync(u);}finally{d.recycle();u.recycle();}Thread.sleep(70);}
            Thread.sleep(700);assertEquals(1,seeks.get());assertTrue(position(s)>=24000&&position(s)<26000);assertFalse(playing(s));
        }
    }
    @Test public void narrowLandscapeStacksInsteadOfShrinkingTouchTargets()throws Exception{
        try(ActivityScenario<PhoneActivity> s=start()){
            main(s,a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));Thread.sleep(1300);
            main(s,a->{View v=(View)field(a,"playerRoot");ViewGroup.LayoutParams lp=v.getLayoutParams();lp.width=Math.round(480*a.getResources().getDisplayMetrics().density);v.setLayoutParams(lp);});Thread.sleep(350);
            main(s,a->{call(a,"updatePlayerDensity",new Class[]{});call(a,"showControls",new Class[]{boolean.class},false);assertEquals(LinearLayout.VERTICAL,((LinearLayout)field(a,"playerActionRow")).getOrientation());float d=a.getResources().getDisplayMetrics().density;View root=(View)field(a,"playerRoot");for(String name:new String[]{"Quality","Audio","Subtitles","Episodes"}){View b=description(root,name);assertTrue(name+" target remains usable",b.getWidth()>=47*d&&b.getHeight()>=47*d);}});
        }
    }
    @Test public void controlsFitLandscapeAndEpisodesMatchMobileSheets()throws Exception{
        try(ActivityScenario<PhoneActivity> s=start()){
            main(s,a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            waitFor(()->{boolean[] yes={false};main(s,a->yes[0]=a.getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE);return yes[0];},5000);Thread.sleep(1100);
            main(s,a->{call(a,"showControls",new Class[]{boolean.class},false);View controls=(View)field(a,"controls"),root=(View)field(a,"playerRoot");float density=a.getResources().getDisplayMetrics().density;
                View top=(View)field(a,"playerTopbar");assertTrue("Header recalculates rotated cutout insets instead of retaining portrait gap",top.getTop()<=24*density);
                assertTrue("Bottom controls have breathing room",controls.getPaddingBottom()>=12*density);assertTrue("Header inset has breathing room",top.getTop()>=12*density);
                View more=description(root,"More playback options");assertSame("More belongs with settings",field(a,"playerButtons"),more.getParent());assertEquals("More is the last setting",more,((ViewGroup)more.getParent()).getChildAt(((ViewGroup)more.getParent()).getChildCount()-1));
                assertTrue("Compact landscape controls",controls.getHeight()<=112*density);assertTrue("Most video remains unobscured",controls.getHeight()<root.getHeight()*.3f);
                for(String name:new String[]{"Play or pause","Quality","Audio","Subtitles","Episodes"}){View button=description(root,name);assertNotNull(button);assertTrue(button.isShown());assertTrue(button.getWidth()>=47*density);assertTrue(button.getHeight()>=47*density);int[] at=new int[2];button.getLocationOnScreen(at);assertTrue(at[0]>=0&&at[0]+button.getWidth()<=a.getResources().getDisplayMetrics().widthPixels);}
            });Thread.sleep(200);capture("player-fixed-landscape.png");
            main(s,a->call(a,"showEpisodeDrawer",new Class[]{}));Thread.sleep(300);capture("episodes-fixed-landscape.png");assertTrue(playing(s));
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            main(s,a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));Thread.sleep(1200);
            main(s,a->call(a,"showEpisodeDrawer",new Class[]{}));Thread.sleep(300);capture("episodes-fixed-portrait.png");assertTrue(playing(s));
        }
    }
}
