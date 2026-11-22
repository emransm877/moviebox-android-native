package org.emransm.moviebox.phone;

import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

@RunWith(AndroidJUnit4.class)
public class HorizontalSwipeScenarios {
    @After public void cleanup() throws Exception {
        Thread.sleep(350);
        Library store=new Library(InstrumentationRegistry.getInstrumentation().getTargetContext());
        store.removeTitles("history", Collections.singleton("generated-native-phone-fixture"));store.close();
    }
    private static void event(View view,long down,int action,float x,float y){
        MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);
        try{view.dispatchTouchEvent(e);}finally{e.recycle();}
    }
    private static long preview(PhoneActivity a,float from,float to,float drift)throws Exception {
        View view=(View)field(a,"video");long down=SystemClock.uptimeMillis();
        float x=view.getWidth()*from,y=view.getHeight()*.38f;
        event(view,down,MotionEvent.ACTION_DOWN,x,y);
        for(int n=1;n<=12;n++){
            SystemClock.sleep(20);
            event(view,down,MotionEvent.ACTION_MOVE,view.getWidth()*(from+(to-from)*n/12),y+view.getHeight()*drift*n/12);
        }
        assertTrue("Horizontal movement enters seek preview",(Boolean)field(a,"swipeSeeking"));
        return down;
    }
    private static AtomicInteger countSeeks(ActivityScenario<PhoneActivity> s){
        AtomicInteger count=new AtomicInteger();main(s,a->((ExoPlayer)field(a,"player")).addListener(new Player.Listener(){
            @Override public void onPositionDiscontinuity(Player.PositionInfo oldPosition,Player.PositionInfo newPosition,int reason){if(reason==Player.DISCONTINUITY_REASON_SEEK)count.incrementAndGet();}
        }));return count;
    }
    private static void finish(PhoneActivity a,long down,int action,float x)throws Exception {
        View view=(View)field(a,"video");event(view,down,action,view.getWidth()*x,view.getHeight()*.38f);
    }
    @Test public void rightAndLeftPreviewThenCommitOnceAndPreservePlaybackState()throws Exception {
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            start(s,Uri.fromFile(fixture()).toString());waitFor(()->playing(s),15000);
            main(s,a->{ExoPlayer p=(ExoPlayer)field(a,"player");p.pause();p.seekTo(20000);});
            AtomicInteger seeks=countSeeks(s);long[] down={0},target={0};
            main(s,a->{down[0]=preview(a,.2f,.7f,0);target[0]=(Long)field(a,"swipeTarget");assertTrue(target[0]>30000);assertEquals(0,seeks.get());assertEquals(20000,((ExoPlayer)field(a,"player")).getCurrentPosition(),100);});
            Thread.sleep(600);main(s,a->{assertEquals("Tick preserves the preview clock",((android.widget.SeekBar)field(a,"seek")).getProgress(),(int)(target[0]*10000/((ExoPlayer)field(a,"player")).getDuration()),1);finish(a,down[0],MotionEvent.ACTION_UP,.7f);});
            Thread.sleep(400);assertEquals("Release commits exactly one seek, no accidental tap",1,seeks.get());assertEquals(target[0],position(s),100);assertFalse("Paused playback stays paused",playing(s));
            main(s,a->((ExoPlayer)field(a,"player")).play());waitFor(()->playing(s),4000);
            main(s,a->{down[0]=preview(a,.75f,.25f,0);target[0]=(Long)field(a,"swipeTarget");assertTrue(target[0]<30000);assertEquals(1,seeks.get());finish(a,down[0],MotionEvent.ACTION_UP,.25f);});
            waitFor(()->playing(s),4000);Thread.sleep(400);assertEquals(2,seeks.get());assertTrue(Math.abs(position(s)-target[0])<1500);
        }
    }
    @Test public void canceledSwipeRestoresPlayingWithoutSeeking()throws Exception {
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            start(s,Uri.fromFile(fixture()).toString());waitFor(()->playing(s),15000);AtomicInteger seeks=countSeeks(s);
            main(s,a->{long down=preview(a,.2f,.7f,0);finish(a,down,MotionEvent.ACTION_CANCEL,.7f);assertFalse((Boolean)field(a,"swipeSeeking"));});
            waitFor(()->playing(s),4000);Thread.sleep(400);assertEquals("Canceled drag must not jump or turn into a tap",0,seeks.get());
        }
    }
    @Test public void horizontalAxisDoesNotChangeVolumeOrBrightnessAndClampsAtBothEnds()throws Exception {
        AudioManager audio=(AudioManager)InstrumentationRegistry.getInstrumentation().getTargetContext().getSystemService(Context.AUDIO_SERVICE);
        int before=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            start(s,Uri.fromFile(fixture()).toString());waitFor(()->playing(s),15000);
            main(s,a->{ExoPlayer p=(ExoPlayer)field(a,"player");p.pause();p.seekTo(2000);float brightness=a.getWindow().getAttributes().screenBrightness;long down=preview(a,.85f,.15f,.025f);assertEquals(0L,field(a,"swipeTarget"));finish(a,down,MotionEvent.ACTION_UP,.15f);assertEquals(brightness,a.getWindow().getAttributes().screenBrightness,.001f);assertEquals(0,p.getCurrentPosition());p.seekTo(p.getDuration()-4000);down=preview(a,.15f,.85f,0);assertEquals(p.getDuration()-250,(long)(Long)field(a,"swipeTarget"));finish(a,down,MotionEvent.ACTION_UP,.85f);assertFalse(p.getPlayWhenReady());assertEquals(p.getDuration()-250,p.getCurrentPosition());});
            assertEquals(before,audio.getStreamVolume(AudioManager.STREAM_MUSIC));
        }
    }
    @Test public void secondFingerCancelsSeekInsteadOfCommittingDuringPinch()throws Exception {
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            start(s,Uri.fromFile(fixture()).toString());waitFor(()->playing(s),15000);AtomicInteger seeks=countSeeks(s);
            main(s,a->{long down=preview(a,.2f,.65f,0);View view=(View)field(a,"video");MotionEvent.PointerProperties[] properties={new MotionEvent.PointerProperties(),new MotionEvent.PointerProperties()};MotionEvent.PointerCoords[] points={new MotionEvent.PointerCoords(),new MotionEvent.PointerCoords()};for(int n=0;n<2;n++){properties[n].id=n;properties[n].toolType=MotionEvent.TOOL_TYPE_FINGER;points[n].x=view.getWidth()*(.4f+.2f*n);points[n].y=view.getHeight()*.38f;points[n].pressure=1;points[n].size=1;}MotionEvent second=MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),2,properties,points,0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);try{view.dispatchTouchEvent(second);}finally{second.recycle();}assertFalse((Boolean)field(a,"swipeSeeking"));finish(a,down,MotionEvent.ACTION_CANCEL,.65f);});
            waitFor(()->playing(s),4000);Thread.sleep(400);assertEquals(0,seeks.get());
        }
    }
}
