package org.emransm.moviebox.phone;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.Uri;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.common.Player;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

/** Actual touch events and native sheet actions using generated local media. */
@RunWith(AndroidJUnit4.class)
public class PhoneGestureSheetScenarios {
    @After public void cleanupOwnedHistory()throws Exception {Thread.sleep(350);Library store=new Library(InstrumentationRegistry.getInstrumentation().getTargetContext());store.removeTitles("history",Collections.singleton("generated-native-phone-fixture"));store.close();Thread.sleep(250);}
    private static void event(View view,long down,long time,int action,float x,float y){MotionEvent e=MotionEvent.obtain(down,time,action,x,y,0);try{view.dispatchTouchEvent(e);}finally{e.recycle();}}
    private static void swipe(View view,float x,float fromY,float toY){long t=SystemClock.uptimeMillis();float px=view.getWidth()*x;event(view,t,t,MotionEvent.ACTION_DOWN,px,view.getHeight()*fromY);for(int n=1;n<=12;n++){SystemClock.sleep(25);event(view,t,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,px,view.getHeight()*(fromY+(toY-fromY)*n/12));}event(view,t,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,px,view.getHeight()*toY);}
    private static void pan(View view){long t=SystemClock.uptimeMillis();float x=view.getWidth()*.45f,y=view.getHeight()*.45f;event(view,t,t,MotionEvent.ACTION_DOWN,x,y);for(int n=1;n<=12;n++){SystemClock.sleep(25);event(view,t,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,x+view.getWidth()*.18f*n/12,y+view.getHeight()*.08f*n/12);}event(view,t,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x+view.getWidth()*.18f,y+view.getHeight()*.08f);}
    private static void multi(View view,long down,long time,int action,float x1,float x2,float y){
        MotionEvent.PointerProperties[] props={new MotionEvent.PointerProperties(),new MotionEvent.PointerProperties()};MotionEvent.PointerCoords[] coords={new MotionEvent.PointerCoords(),new MotionEvent.PointerCoords()};
        for(int i=0;i<2;i++){props[i].id=i;props[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i].y=y;coords[i].pressure=1;coords[i].size=1;}coords[0].x=x1;coords[1].x=x2;
        MotionEvent e=MotionEvent.obtain(down,time,action,2,props,coords,0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);try{view.dispatchTouchEvent(e);}finally{e.recycle();}
    }
    private static void pinch(View view){long t=SystemClock.uptimeMillis();float w=view.getWidth(),y=view.getHeight()*.4f;event(view,t,t,MotionEvent.ACTION_DOWN,w*.4f,y);SystemClock.sleep(25);multi(view,t,SystemClock.uptimeMillis(),MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),w*.4f,w*.6f,y);for(int n=1;n<=12;n++){SystemClock.sleep(25);float spread=.2f*n/12;multi(view,t,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,w*(.4f-spread),w*(.6f+spread),y);}multi(view,t,SystemClock.uptimeMillis(),MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),w*.2f,w*.8f,y);event(view,t,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,w*.2f,y);}
    private static View frame(PhoneActivity activity)throws Exception{return ((PlayerView)field(activity,"video")).findViewById(androidx.media3.ui.R.id.exo_content_frame);}
    private static AccessibilityNodeInfo sheet(String title)throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();AccessibilityNodeInfo root=null;long until=SystemClock.uptimeMillis()+2500;while(SystemClock.uptimeMillis()<until){root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();if(root!=null&&!root.findAccessibilityNodeInfosByText(title).isEmpty())break;Thread.sleep(50);}assertNotNull("Native sheet has an active window",root);assertFalse("Expected sheet title: "+title,root.findAccessibilityNodeInfosByText(title).isEmpty());return root;
    }
    private static void capture(String filename)throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull("Device screenshot captured: "+filename,bitmap);
        File file=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),filename);
        try(OutputStream output=new FileOutputStream(file)){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,output));}finally{bitmap.recycle();}
    }
    private static void clickText(String title,String value)throws Exception {
        AccessibilityNodeInfo root=sheet(title);boolean clicked=false;
        for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(value)){if(node.getText()==null||!value.contentEquals(node.getText()))continue;AccessibilityNodeInfo target=node;for(int level=0;target!=null&&level<4;level++,target=target.getParent()){if(target.isClickable()){clicked=target.performAction(AccessibilityNodeInfo.ACTION_CLICK);break;}}if(clicked)break;}
        assertTrue("Sheet action clickable: "+value,clicked);
    }
    private static void open(ActivityScenario<PhoneActivity> scenario,String description)throws Exception {main(scenario,a->{call(a,"showControls",new Class[]{boolean.class},false);View action=PhoneMediaScenarios.description((View)field(a,"root"),description);assertNotNull("Player action present: "+description,action);assertTrue(action.isShown());assertTrue(action.performClick());});}
    @Test public void fullscreenRotationReturnsBrowsingToPortrait()throws Exception {
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            start(scenario,Uri.fromFile(fixture()).toString());waitFor(()->playing(scenario),15000);main(scenario,a->call(a,"showControls",new Class[]{boolean.class},false));Thread.sleep(300);capture("qa-player-portrait.png");open(scenario,"Rotate fullscreen");
            waitFor(()->{boolean[] landscape={false};main(scenario,a->landscape[0]=a.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE);return landscape[0];},6000);
            main(scenario,a->call(a,"showControls",new Class[]{boolean.class},false));Thread.sleep(300);capture("qa-player-landscape.png");
            open(scenario,"Back to details");
            waitFor(()->{boolean[] portrait={false};main(scenario,a->portrait[0]=a.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT&&field(a,"player")==null);return portrait[0];},6000);
        }
    }
    @Test public void holdingSidePreviewsAcceleratingTimelineThenCommitsOneSeekAndResumes()throws Exception {
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            start(scenario,Uri.fromFile(fixture()).toString());waitFor(()->playing(scenario),15000);waitFor(()->position(scenario)>1200,6000);
            AtomicInteger seeks=new AtomicInteger();long[] down={0};float[] touch={0,0};
            main(scenario,a->{ExoPlayer player=(ExoPlayer)field(a,"player");player.addListener(new Player.Listener(){@Override public void onPositionDiscontinuity(Player.PositionInfo oldPosition,Player.PositionInfo newPosition,int reason){if(reason==Player.DISCONTINUITY_REASON_SEEK)seeks.incrementAndGet();}});View video=(View)field(a,"video");down[0]=SystemClock.uptimeMillis();touch[0]=video.getWidth()*.86f;touch[1]=video.getHeight()*.4f;event(video,down[0],down[0],MotionEvent.ACTION_DOWN,touch[0],touch[1]);});
            waitFor(()->!playing(scenario),1400);long paused=position(scenario);int[] progress=new int[4];
            main(scenario,a->progress[0]=((android.widget.SeekBar)field(a,"seek")).getProgress());Thread.sleep(600);main(scenario,a->progress[1]=((android.widget.SeekBar)field(a,"seek")).getProgress());
            Thread.sleep(1900);main(scenario,a->progress[2]=((android.widget.SeekBar)field(a,"seek")).getProgress());Thread.sleep(600);main(scenario,a->progress[3]=((android.widget.SeekBar)field(a,"seek")).getProgress());
            assertTrue("Held timeline advances before release",progress[1]>progress[0]);assertTrue("Longer hold accelerates timeline preview",progress[3]-progress[2]>(progress[1]-progress[0])*1.25f);assertEquals("Preview performs no encoded-media seeks",0,seeks.get());assertTrue("Decoder remains paused during preview",Math.abs(position(scenario)-paused)<150);
            main(scenario,a->event((View)field(a,"video"),down[0],SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,touch[0],touch[1]));
            waitFor(()->playing(scenario)&&seeks.get()==1,4000);assertTrue("Release jumps to the previewed point",position(scenario)>paused+4000);
        }
    }
    @Test public void verticalGesturesChangeOnlyPlayerVolumeAndBrightnessThenRestoreBrightness()throws Exception {
        AudioManager audio=(AudioManager)InstrumentationRegistry.getInstrumentation().getTargetContext().getSystemService(Context.AUDIO_SERVICE);int originalVolume=audio.getStreamVolume(AudioManager.STREAM_MUSIC),maximum=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);assertTrue(maximum>2);
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            float[] originalBrightness={-2};main(scenario,a->originalBrightness[0]=a.getWindow().getAttributes().screenBrightness);
            start(scenario,Uri.fromFile(fixture()).toString());waitFor(()->playing(scenario),15000);
            audio.setStreamVolume(AudioManager.STREAM_MUSIC,maximum/2,0);int before=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            main(scenario,a->swipe((View)field(a,"video"),.86f,.72f,.26f));assertTrue("Right upward swipe raises media volume",audio.getStreamVolume(AudioManager.STREAM_MUSIC)>before);
            main(scenario,a->{WindowManager.LayoutParams attrs=a.getWindow().getAttributes();attrs.screenBrightness=.35f;a.getWindow().setAttributes(attrs);});
            int volumeAfterRight=audio.getStreamVolume(AudioManager.STREAM_MUSIC);main(scenario,a->swipe((View)field(a,"video"),.14f,.72f,.26f));
            main(scenario,a->assertTrue("Left upward swipe raises this window brightness",a.getWindow().getAttributes().screenBrightness>.4f));assertEquals("Left brightness swipe must not change media volume",volumeAfterRight,audio.getStreamVolume(AudioManager.STREAM_MUSIC));
            main(scenario,a->{call(a,"leavePlayer",new Class[]{});assertEquals("Leaving restores original window brightness",originalBrightness[0],a.getWindow().getAttributes().screenBrightness,.005f);});
        }finally{audio.setStreamVolume(AudioManager.STREAM_MUSIC,originalVolume,0);}
    }
    @Test public void twoFingerPinchAndDragTransformVideoAndFitResetsIt()throws Exception {
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            start(scenario,Uri.fromFile(fixture()).toString());waitFor(()->playing(scenario),15000);
            main(scenario,a->pinch((View)field(a,"video")));main(scenario,a->assertTrue("Two-finger pinch visibly scales decoded video",frame(a).getScaleX()>1.1f));
            main(scenario,a->pan((View)field(a,"video")));main(scenario,a->assertTrue("Drag pans zoomed video",Math.abs(frame(a).getTranslationX())>5||Math.abs(frame(a).getTranslationY())>5));
            open(scenario,"More playback options");clickText("Playback options","Fit screen");
            main(scenario,a->{assertEquals(1f,frame(a).getScaleX(),.01f);assertEquals(0f,frame(a).getTranslationX(),1f);assertEquals(0f,frame(a).getTranslationY(),1f);assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT,((PlayerView)field(a,"video")).getResizeMode());});assertTrue(playing(scenario));
        }
    }
    @Test public void nativePickersStayBoundedBackDismissesAndQualitySwitchKeepsPosition()throws Exception {
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            start(scenario,Uri.fromFile(fixture()).toString());waitFor(()->playing(scenario),15000);
            main(scenario,a->{List<JSONObject> sources=new ArrayList<>((List<JSONObject>)field(a,"streams"));sources.add(new JSONObject(sources.get(0).toString()).put("resolution",720));put(a,"streams",sources);put(a,"currentAudio",null);JSONObject subject=(JSONObject)field(a,"subject");subject.put("subjectType",2);put(a,"season",1);put(a,"episode",1);TreeMap<Integer,int[]> seasons=(TreeMap<Integer,int[]>)field(a,"seasons");seasons.put(1,new int[]{1,2,4});seasons.put(2,new int[]{1,3});call(a,"startStream",new Class[]{long.class,boolean.class,File.class},12000L,true,null);});waitFor(()->playing(scenario),10000);waitFor(()->position(scenario)>12000,5000);
            String[][] pickers={{"Quality","Video quality"},{"Audio","Audio"},{"Subtitles","Subtitles"},{"Episodes","Seasons & episodes"}};
            for(String[] picker:pickers){open(scenario,picker[0]);AccessibilityNodeInfo root=sheet(picker[1]);Rect bounds=new Rect();root.getBoundsInScreen(bounds);int screen=InstrumentationRegistry.getInstrumentation().getTargetContext().getResources().getDisplayMetrics().heightPixels;assertTrue("Picker fits a bounded portion of the screen: "+picker[0],bounds.height()<screen*.86f);Thread.sleep(300);capture("qa-sheet-"+picker[0]+".png");InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);InstrumentationRegistry.getInstrumentation().waitForIdleSync();main(scenario,a->assertNotNull("Back dismisses picker without closing player",field(a,"player")));assertTrue("Playback continues after picker",playing(scenario));}
            long before=position(scenario);open(scenario,"Quality");clickText("Video quality","720p");waitFor(()->playing(scenario),10000);assertTrue("Quality change preserves playback position",position(scenario)>=before-750);main(scenario,a->assertEquals(1,field(a,"streamIndex")));
        }
    }
}
