package org.emransm.moviebox.phone;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

/** On-phone input, native accessibility actions and real system font/service settings. */
@RunWith(AndroidJUnit4.class)
public class PlayerAccessibilityScenarios {
    static UiAutomation automation(){return InstrumentationRegistry.getInstrumentation().getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);}
    static String shell(String command)throws Exception{try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(automation().executeShellCommand(command));ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[1024];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return out.toString("UTF-8").trim();}}
    static void setting(String area,String key,String value)throws Exception{if(value.equals("null")||value.isEmpty())shell("settings delete "+area+" "+key);else{assertTrue(value.matches("[A-Za-z0-9_./:$-]+"));shell("settings put "+area+" "+key+" "+value);}}
    static ActivityScenario<PhoneActivity> start()throws Exception{ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class);BlockFlowScenarios.isolate(s,new SliderPlaybackScenarios.Local());main(s,a->call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},BlockFlowScenarios.title("accessibility-owned",2),true));waitFor(()->playing(s),10000);return s;}
    static Dialog sheet(PhoneActivity a)throws Exception{Map<?,?> sheets=(Map<?,?>)field(a,"openSheets");assertFalse(sheets.isEmpty());return (Dialog)sheets.keySet().iterator().next();}
    static void readableLabels(View view){if(view instanceof TextView){TextView text=(TextView)view;if(text.getLayout()!=null){for(int line=0;line<text.getLineCount();line++)assertEquals("No clipped menu label: "+text.getText(),0,text.getLayout().getEllipsisCount(line));assertTrue("Full menu text height: "+text.getText(),text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom()>=text.getLayout().getHeight());}}if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)readableLabels(((ViewGroup)view).getChildAt(i));}
    static void capture(String name)throws Exception{Bitmap image=automation().takeScreenshot();assertNotNull(image);try(FileOutputStream out=new FileOutputStream(new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),name))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}finally{image.recycle();}}
    @Test public void intentionalKeyboardFocusSurvivesTimeoutButTouchModeCanHideAgain()throws Exception{
        try(ActivityScenario<PhoneActivity> s=start()){
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_TAB);main(s,a->{call(a,"showControls",new Class[]{boolean.class},false);assertTrue(description((View)field(a,"root"),"Quality").requestFocus());});Thread.sleep(4300);
            main(s,a->{assertTrue((Boolean)field(a,"visibleControls"));assertTrue((Boolean)call(a,"protectControls",new Class[]{}));long t=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,5,5,0),cancel=MotionEvent.obtain(t,t+1,MotionEvent.ACTION_CANCEL,5,5,0);try{a.dispatchTouchEvent(down);a.dispatchTouchEvent(cancel);}finally{down.recycle();cancel.recycle();}call(a,"maybeHideControls",new Class[]{});assertFalse((Boolean)field(a,"visibleControls"));View surface=description((View)field(a,"root"),"Show player controls");assertNotNull(surface);assertTrue(surface.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK,null));assertTrue((Boolean)field(a,"visibleControls"));assertFalse((Boolean)field(a,"keyboardNavigation"));});
        }
    }
    @Test public void seekHas48dpHitAreaAndNativeAccessibilityActionActuallySeeks()throws Exception{
        try(ActivityScenario<PhoneActivity> s=start()){
            main(s,a->((ExoPlayer)field(a,"player")).pause());
            for(int orientation:new int[]{ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}){
                main(s,a->a.setRequestedOrientation(orientation));Thread.sleep(1000);main(s,a->{call(a,"updatePlayerDensity",new Class[]{});call(a,"showControls",new Class[]{boolean.class},false);});Thread.sleep(150);
                int[] tap={0,0};main(s,a->{SeekBar seek=(SeekBar)field(a,"seek");float d=a.getResources().getDisplayMetrics().density;assertTrue(seek.getHeight()/d>=47.9f);int[] at=new int[2];seek.getLocationOnScreen(at);tap[0]=at[0]+(int)(seek.getWidth()*.75);tap[1]=at[1]+(int)(d*3);});
                long time=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,tap[0],tap[1],0),up=MotionEvent.obtain(time,time+100,MotionEvent.ACTION_UP,tap[0],tap[1],0);try{InstrumentationRegistry.getInstrumentation().sendPointerSync(down);InstrumentationRegistry.getInstrumentation().sendPointerSync(up);}finally{down.recycle();up.recycle();}waitFor(()->position(s)>40000,3000);
                main(s,a->{Bundle args=new Bundle();args.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,5000);assertTrue(((SeekBar)field(a,"seek")).performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),args));});waitFor(()->position(s)>28000&&position(s)<36000,3000);assertFalse(playing(s));
            }
            capture("batch3-player-seek-landscape.png");
        }
    }
    @Test public void openQualitySheetProtectsControlsAndReturnsKeyboardFocus()throws Exception{
        try(ActivityScenario<PhoneActivity> s=start()){
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_TAB);main(s,a->{View quality=description((View)field(a,"root"),"Quality");quality.requestFocus();assertTrue(quality.performClick());});Thread.sleep(4300);
            main(s,a->{assertTrue((Boolean)field(a,"visibleControls"));assertTrue(sheet(a).isShowing());sheet(a).dismiss();});Thread.sleep(200);main(s,a->{assertTrue(description((View)field(a,"root"),"Quality").hasFocus());assertTrue(((Map<?,?>)field(a,"openSheets")).isEmpty());});assertTrue(playing(s));
        }
    }
    @Test public void systemInteractiveTimeoutIsHonoredAndRestored()throws Exception{
        String key="accessibility_interactive_ui_timeout_ms",original=shell("settings get secure "+key);
        try{setting("secure",key,"12000");try(ActivityScenario<PhoneActivity> s=start()){waitFor(()->{int[] timeout={0};main(s,a->timeout[0]=(Integer)call(a,"controlsTimeout",new Class[]{}));return timeout[0]>=12000;},4000);}}
        finally{setting("secure",key,original);}
    }
    @Test public void audioMenuUsesFriendlyLabelsAndRetainsTheSelectedTrack()throws Exception{
        try(ActivityScenario<PhoneActivity> s=start()){
            main(s,a->call(a,"audioDialog",new Class[]{}));Thread.sleep(250);capture("batch3-audio-menu.png");main(s,a->{View decor=sheet(a).getWindow().getDecorView();assertTrue(BlockFlowScenarios.hasText(decor,"English"));assertTrue(BlockFlowScenarios.hasText(decor,"Hindi"));assertFalse(BlockFlowScenarios.hasText(decor,"und"));View row=description(decor,"Hindi");assertNotNull(row);assertTrue(row.performClick());});assertTrue(playing(s));
        }
    }
    @Test public void realTalkBackKeepsControlsAndCanOpenAndCloseAudio()throws Exception{
        String services=shell("settings get secure enabled_accessibility_services"),enabled=shell("settings get secure accessibility_enabled");String talkback="com.google.android.marvin.talkback/.TalkBackService";
        try{
            setting("secure","enabled_accessibility_services",services.equals("null")||services.isEmpty()?talkback:services+":"+talkback);setting("secure","accessibility_enabled","1");
            AccessibilityManager manager=(AccessibilityManager)InstrumentationRegistry.getInstrumentation().getTargetContext().getSystemService(Context.ACCESSIBILITY_SERVICE);waitFor(manager::isTouchExplorationEnabled,10000);
            try(ActivityScenario<PhoneActivity> s=start()){
                main(s,a->call(a,"showControls",new Class[]{boolean.class},false));Thread.sleep(4300);main(s,a->{assertTrue((Boolean)field(a,"visibleControls"));View audio=description((View)field(a,"root"),"Audio");assertTrue(audio.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,null));assertTrue(audio.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK,null));});Thread.sleep(300);
                main(s,a->{View decor=sheet(a).getWindow().getDecorView();assertTrue(BlockFlowScenarios.hasText(decor,"English"));assertTrue(description(decor,"Close Audio").performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK,null));});Thread.sleep(400);assertTrue(playing(s));main(s,a->{assertTrue((Boolean)field(a,"visibleControls"));assertTrue("TalkBack focus returns to Audio",description((View)field(a,"root"),"Audio").isAccessibilityFocused());});capture("batch3-talkback-player.png");
            }
        }finally{setting("secure","enabled_accessibility_services",services);setting("secure","accessibility_enabled",enabled);}
    }
    @Test public void enlargedPlayerAndLongMenusRemainReadableAt130And200Percent()throws Exception{
        String original=shell("settings get system font_scale");
        try{for(String scale:new String[]{"1.3","2.0"}){setting("system","font_scale",scale);Thread.sleep(800);
            try(ActivityScenario<PhoneActivity> s=start()){
                main(s,a->assertEquals(Float.parseFloat(scale),a.getResources().getConfiguration().fontScale,.01f));
                for(int orientation:new int[]{ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}){
                    main(s,a->a.setRequestedOrientation(orientation));Thread.sleep(1000);main(s,a->call(a,"showControls",new Class[]{boolean.class},false));Thread.sleep(200);
                    main(s,a->{LinearLayout buttons=(LinearLayout)field(a,"playerButtons");float density=a.getResources().getDisplayMetrics().density;for(int i=0;i<buttons.getChildCount();i++){TextView button=(TextView)buttons.getChildAt(i);assertTrue(button.getWidth()>=47.9*density);assertTrue(button.getHeight()>=47.9*density);if(button.getLayout()!=null)for(int line=0;line<button.getLineCount();line++)assertEquals("Full player label "+button.getText(),0,button.getLayout().getEllipsisCount(line));}});
                }
                capture("batch3-player-font-"+scale+".png");
                main(s,a->{List<String> names=Arrays.asList("Portuguese (Brazil) descriptive audio with a deliberately long label","English subtitles with an extended descriptive language label");call(a,"choiceSheet",new Class[]{String.class,List.class,int.class,PhoneActivity.SheetChoice.class},"Audio and subtitle language choices",names,0,(PhoneActivity.SheetChoice)i->{});});Thread.sleep(250);
                main(s,a->{Dialog dialog=sheet(a);View decor=dialog.getWindow().getDecorView();assertTrue(decor.getHeight()<=a.getResources().getDisplayMetrics().heightPixels);readableLabels(decor);View close=description(decor,"Close Audio and subtitle language choices");Rect rect=new Rect();assertTrue(close.getGlobalVisibleRect(rect));assertTrue(rect.height()>=a.getResources().getDisplayMetrics().density*47);});capture("batch3-sheet-font-"+scale+".png");main(s,a->sheet(a).dismiss());
            }
        }}finally{setting("system","font_scale",original);}
    }
    @Test public void iconPickerAt200PercentWrapsLabelsAndKeepsCloseReachable()throws Exception{
        String original=shell("settings get system font_scale");
        try{setting("system","font_scale","2.0");Thread.sleep(800);
            try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
                BlockFlowScenarios.isolate(s,new SliderPlaybackScenarios.Local());Dialog[] dialog={null};main(s,a->{assertEquals(2f,a.getResources().getConfiguration().fontScale,.01f);dialog[0]=AppLogos.show(a,0xff101114,0xff25262a,0xfff5f5f5,0xff2feda1,0xffadb0b6,()->{});});Thread.sleep(350);
                main(s,a->{View decor=dialog[0].getWindow().getDecorView();readableLabels(decor);View close=description(decor,"Close app icon choices");assertNotNull(close);Rect bounds=new Rect();assertTrue(close.getGlobalVisibleRect(bounds));assertTrue(bounds.height()>=a.getResources().getDisplayMetrics().density*47);});capture("batch3-icons-font-2.0.png");main(s,a->dialog[0].dismiss());
            }
        }finally{setting("system","font_scale",original);}
    }
}
