package org.emransm.moviebox.phone;

import android.app.Dialog;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.SystemClock;
import android.view.*;
import android.widget.ScrollView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.media3.exoplayer.ExoPlayer;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

/** Owned video and isolated library. Actual window bounds, sparse selection and playback preservation. */
@RunWith(AndroidJUnit4.class)
public class EpisodeDockScenarios {
    private ActivityScenario<PhoneActivity> start(SliderPlaybackScenarios.Local service)throws Exception{
        ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class);BlockFlowScenarios.isolate(scenario,service);
        JSONObject series=BlockFlowScenarios.title("qa-episode-dock",2);
        main(scenario,a->call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},series,true));waitFor(()->playing(scenario),10000);return scenario;
    }
    private void open(ActivityScenario<PhoneActivity> scenario)throws Exception{main(scenario,a->call(a,"showEpisodeDrawer",new Class[]{}));Thread.sleep(300);}
    private void geometry(ActivityScenario<PhoneActivity> scenario){
        main(scenario,a->{
            View dock=(View)field(a,"episodeDrawer"),top=(View)field(a,"playerTopbar"),bottom=(View)field(a,"controls");Dialog dialog=(Dialog)field(a,"episodeDialog");assertNotNull(dock);assertTrue(dialog.isShowing());float density=a.getResources().getDisplayMetrics().density;
            int[] at=new int[2];dock.getLocationOnScreen(at);View root=(View)field(a,"playerRoot");
            assertTrue("Shared mobile sheet stays inside screen",at[1]>=0&&at[1]+dock.getHeight()<=a.getResources().getDisplayMetrics().heightPixels+2);
            assertTrue("Sheet is content-sized, not a full-height TV dock",dock.getHeight()<root.getHeight()*.86f);
            assertTrue("Shared landscape sheet caps width at 440dp",dock.getWidth()<=Math.round(440*density)+2);
            View close=description(dock,"Close Seasons & episodes");assertNotNull(close);assertTrue(close.getHeight()>=Math.round(48*density));
            View current=description(dock,"Play season 1 episode 1, current");assertNotNull(current);assertTrue("Same 50dp choice rows as Quality",current.getHeight()>=Math.round(50*density)-2);
            assertNotNull(description(dock,"Show season 2"));assertNull("Sparse episode numbers are preserved",description(dock,"Play season 1 episode 3"));
            WindowManager.LayoutParams attrs=dialog.getWindow().getAttributes();assertNotEquals(0,attrs.flags&WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            assertTrue("Movie continues beneath the dock",((ExoPlayer)field(a,"player")).isPlaying());
        });
    }
    @Test public void portraitDockFitsBarsAndCurrentEpisodeDoesNotRestart()throws Exception{
        SliderPlaybackScenarios.Local service=new SliderPlaybackScenarios.Local();try(ActivityScenario<PhoneActivity> scenario=start(service)){
            open(scenario);geometry(scenario);final Object[] player={null};long before=position(scenario);
            main(scenario,a->{player[0]=field(a,"player");View current=description((View)field(a,"episodeDrawer"),"Play season 1 episode 1, current");assertNotNull(current);assertTrue(current.performClick());assertFalse((Boolean)field(a,"drawerOpen"));assertSame("Current episode closes without rebuilding playback",player[0],field(a,"player"));});
            Thread.sleep(250);assertTrue(position(scenario)>before);assertTrue(playing(scenario));
        }
    }
    @Test public void landscapeDockKeepsOneUsableRowAndBackReturnsToRunningVideo()throws Exception{
        SliderPlaybackScenarios.Local service=new SliderPlaybackScenarios.Local();try(ActivityScenario<PhoneActivity> scenario=start(service)){
            main(scenario,a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));waitFor(()->{boolean[] yes={false};main(scenario,a->yes[0]=a.getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE);return yes[0];},5000);
            open(scenario);geometry(scenario);long before=position(scenario);InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);Thread.sleep(150);
            main(scenario,a->assertFalse((Boolean)field(a,"drawerOpen")));assertTrue(playing(scenario));assertTrue(position(scenario)>before);
        }
    }
    @Test public void browsingSeasonDoesNotChangePlaybackUntilSparseEpisodeIsChosen()throws Exception{
        SliderPlaybackScenarios.Local service=new SliderPlaybackScenarios.Local();try(ActivityScenario<PhoneActivity> scenario=start(service)){
            open(scenario);main(scenario,a->call(a,"showEpisodeSheet",new Class[]{int.class},2));Thread.sleep(250);
            main(scenario,a->{assertEquals(1,field(a,"season"));assertEquals(1,field(a,"episode"));View option=description((View)field(a,"episodeDrawer"),"Play season 2 episode 7");assertNotNull(option);assertTrue(option.performClick());});
            waitFor(()->playing(scenario)&&service.se==2&&service.ep==7,10000);main(scenario,a->{assertEquals(2,field(a,"season"));assertEquals(7,field(a,"episode"));});
        }
    }
    @Test public void outsideTapDismissesWithoutPausingUnderlyingVideo()throws Exception{
        SliderPlaybackScenarios.Local service=new SliderPlaybackScenarios.Local();try(ActivityScenario<PhoneActivity> scenario=start(service)){
            open(scenario);final int[] point={0,0};main(scenario,a->{View root=(View)field(a,"playerRoot");int[] at=new int[2];root.getLocationOnScreen(at);point[0]=at[0]+8;point[1]=at[1]+Math.round(60*a.getResources().getDisplayMetrics().density);});
            long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,point[0],point[1],0),up=MotionEvent.obtain(now,now+30,MotionEvent.ACTION_UP,point[0],point[1],0);
            try{InstrumentationRegistry.getInstrumentation().sendPointerSync(down);InstrumentationRegistry.getInstrumentation().sendPointerSync(up);}finally{down.recycle();up.recycle();}Thread.sleep(200);
            main(scenario,a->assertFalse((Boolean)field(a,"drawerOpen")));assertTrue("Dismissal does not toggle the player",playing(scenario));
        }
    }
}
