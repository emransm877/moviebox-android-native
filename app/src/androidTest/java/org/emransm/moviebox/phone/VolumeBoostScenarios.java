package org.emransm.moviebox.phone;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.audiofx.LoudnessEnhancer;
import android.net.Uri;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.Collections;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

@RunWith(AndroidJUnit4.class)
public class VolumeBoostScenarios {
    private AudioManager audio;private int original;
    @Before public void before(){audio=(AudioManager)InstrumentationRegistry.getInstrumentation().getTargetContext().getSystemService(Context.AUDIO_SERVICE);original=audio.getStreamVolume(AudioManager.STREAM_MUSIC);}
    @After public void after()throws Exception {audio.setStreamVolume(AudioManager.STREAM_MUSIC,original,0);Thread.sleep(350);Library store=new Library(InstrumentationRegistry.getInstrumentation().getTargetContext());store.removeTitles("history",Collections.singleton("generated-native-phone-fixture"));store.close();}
    private static PlayerVolume volume(PhoneActivity a)throws Exception{return (PlayerVolume)field(a,"playerVolume");}
    private static void ready(ActivityScenario<PhoneActivity> s)throws Exception{start(s,Uri.fromFile(fixture()).toString());waitFor(()->playing(s),15000);waitFor(()->{boolean[] found={false};main(s,a->found[0]=((ExoPlayer)field(a,"player")).getAudioSessionId()>0);return found[0];},8000);}
    private static void capture(String name)throws Exception{Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();if(bitmap==null){try(android.os.ParcelFileDescriptor pipe=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand("screencap -p");InputStream input=new android.os.ParcelFileDescriptor.AutoCloseInputStream(pipe)){bitmap=android.graphics.BitmapFactory.decodeStream(input);}}assertNotNull(bitmap);try(OutputStream out=new FileOutputStream(new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),name))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}finally{bitmap.recycle();}}
    @Test public void actualAudioSessionHasEnabledBoostAndNormalVolumeDisablesIt()throws Exception {
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            ready(s);main(s,a->{assertEquals(602,PlayerVolume.millibels(2));assertEquals(0,PlayerVolume.millibels(1));assertTrue("Platform audio boost accepts this audio session",volume(a).setLevel(2));LoudnessEnhancer effect=(LoudnessEnhancer)field(volume(a),"enhancer");assertNotNull(effect);assertTrue(effect.getEnabled());assertEquals(602,effect.getTargetGain(),1);assertEquals(2,volume(a).level(),.01f);assertEquals(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),audio.getStreamVolume(AudioManager.STREAM_MUSIC));assertFalse(volume(a).setLevel(Float.NaN));assertEquals(2,volume(a).level(),.01f);assertTrue(volume(a).setLevel(1));assertNull("Normal volume releases the effect",field(volume(a),"enhancer"));});
        }
    }
    @Test public void volumeRailStatesFitPortraitAndLandscapeAndExposeAccessibleSlider()throws Exception {
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            ready(s);
            for(int orientation:new int[]{Configuration.ORIENTATION_PORTRAIT,Configuration.ORIENTATION_LANDSCAPE}){
                main(s,a->a.setRequestedOrientation(orientation==Configuration.ORIENTATION_PORTRAIT?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
                waitFor(()->{boolean[] value={false};main(s,a->value[0]=a.getResources().getConfiguration().orientation==orientation);return value[0];},6000);Thread.sleep(500);
                for(float level:new float[]{.5f,1,1.5f,2}){
                    main(s,a->{call(a,"showControls",new Class[]{boolean.class},true);call(a,"setPlayerVolume",new Class[]{float.class},level);});Thread.sleep(250);
                    main(s,a->{View hud=(View)field(a,"volumeHud"),top=(View)field(a,"playerTopbar"),bottom=(View)field(a,"controls");Rect h=new Rect(),t=new Rect(),b=new Rect();assertTrue(hud.getGlobalVisibleRect(h));if(top.isShown()){top.getGlobalVisibleRect(t);assertTrue("Volume rail clears title bar",h.top>=t.bottom);}if(bottom.isShown()){bottom.getGlobalVisibleRect(b);assertTrue("Volume rail clears seek/options",h.bottom<=b.top);}assertTrue(h.width()>=a.getResources().getDisplayMetrics().density*48);AccessibilityNodeInfo info=AccessibilityNodeInfo.obtain();hud.onInitializeAccessibilityNodeInfo(info);assertEquals(android.widget.SeekBar.class.getName(),info.getClassName());assertNotNull(info.getRangeInfo());assertEquals(2,info.getRangeInfo().getMax(),.01f);assertFalse(hud.getContentDescription().toString().contains("%"));assertFalse(hud.getContentDescription().toString().contains("200"));});
                    capture("qa-volume-"+(orientation==Configuration.ORIENTATION_PORTRAIT?"portrait":"landscape")+"-"+Math.round(level*100)+".png");
                }
            }
            main(s,a->{call(a,"setPlayerVolume",new Class[]{float.class},0f);assertTrue(((View)field(a,"volumeHud")).getContentDescription().toString().contains("muted"));});
        }
    }
    @Test public void boostReattachesAfterStreamRebuildAndReleasesOnExit()throws Exception {
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            ready(s);PlayerVolume[] previous={null};main(s,a->{previous[0]=volume(a);assertTrue(previous[0].setLevel(2));call(a,"startStream",new Class[]{long.class,boolean.class,File.class},10000L,true,null);});waitFor(()->playing(s),10000);
            waitFor(()->{boolean[] active={false};main(s,a->active[0]=volume(a).level()>1.99f&&field(volume(a),"enhancer")!=null);return active[0];},6000);
            main(s,a->{assertSame(previous[0],volume(a));call(a,"leavePlayer",new Class[]{});assertNull(field(a,"playerVolume"));assertNull(field(previous[0],"enhancer"));assertEquals(1,(Float)field(previous[0],"gain"),.01f);});
        }
    }
    @Test public void hardwareKeysAndSliderActionsAdjustVolumeThenHideCleanly()throws Exception {
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            ready(s);main(s,a->{volume(a).setLevel(1);assertTrue(a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_VOLUME_UP)));assertTrue(volume(a).level()>1);View slider=(View)field(a,"volumeHud");assertTrue(slider.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,null));assertTrue(volume(a).level()>1.1f);assertTrue(a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_VOLUME_DOWN)));call(a,"setPlayerVolume",new Class[]{float.class},.5f);});
            waitFor(()->{boolean[] hidden={false};main(s,a->hidden[0]=((View)field(a,"volumeHud")).getVisibility()==View.GONE);return hidden[0];},3500);
            assertEquals(0xff2feda1,PlayerLevelSlider.boostColor(0));assertEquals(0xffec6464,PlayerLevelSlider.boostColor(1));
        }
    }
}
