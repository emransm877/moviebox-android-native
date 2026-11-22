package org.emransm.moviebox.phone;

import android.app.DownloadManager;
import android.app.UiAutomation;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Environment;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.InputDevice;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

/** Registered owned media exercises completed-card UX, not a network transfer. */
@RunWith(AndroidJUnit4.class)
public class PhoneDownloadsScenarios {
    private static final String TITLE="Owned offline download acceptance";
    private static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private static View findTitle(View view){
        if(view instanceof TextView&&TITLE.contentEquals(((TextView)view).getText()))return view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View match=findTitle(group.getChildAt(i));if(match!=null)return match;}}
        return null;
    }
    private static View button(View view,String suffix){
        if(view instanceof Button&&((Button)view).getText().toString().trim().endsWith(suffix))return view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View match=button(group.getChildAt(i),suffix);if(match!=null)return match;}}
        return null;
    }
    private static boolean text(View view,String value){
        if(view instanceof TextView&&value.contentEquals(((TextView)view).getText()))return true;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(text(group.getChildAt(i),value))return true;}
        return false;
    }
    private static String diagnostic(AccessibilityNodeInfo root){StringBuilder out=new StringBuilder();List<AccessibilityNodeInfo> todo=new ArrayList<>();todo.add(root);for(int i=0;i<todo.size()&&i<60;i++){AccessibilityNodeInfo node=todo.get(i);if(node==null)continue;CharSequence label=node.getText();String id=node.getViewIdResourceName();if(label!=null||id!=null)out.append("[").append(id).append(" ").append(node.getClassName()).append(" text=").append(label==null?"":label.toString().replaceAll("[\\r\\n]"," ").substring(0,Math.min(80,label.length()))).append(" clickable=").append(node.isClickable()).append("]");for(int n=0;n<node.getChildCount();n++)todo.add(node.getChild(n));}return out.toString();}
    private static boolean clickPositiveRemoval()throws Exception {
        UiAutomation automation=InstrumentationRegistry.getInstrumentation().getUiAutomation();AccessibilityServiceInfo info=automation.getServiceInfo();int flags=info.flags;
        try {
            info.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;automation.setServiceInfo(info);InstrumentationRegistry.getInstrumentation().waitForIdleSync();SystemClock.sleep(100);
            AccessibilityNodeInfo root=automation.getRootInActiveWindow();assertNotNull(root);List<AccessibilityNodeInfo> buttons=new ArrayList<>(root.findAccessibilityNodeInfosByViewId("android:id/button1"));
            if(buttons.isEmpty())for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText("Remove")){CharSequence label=node.getText();if(label!=null&&"remove".equalsIgnoreCase(label.toString().trim()))buttons.add(node);}
            for(AccessibilityNodeInfo button:buttons){if(!button.isVisibleToUser())continue;if(button.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;Rect bounds=new Rect();button.getBoundsInScreen(bounds);if(bounds.isEmpty())continue;long at=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(at,at,MotionEvent.ACTION_DOWN,bounds.exactCenterX(),bounds.exactCenterY(),0);down.setSource(InputDevice.SOURCE_TOUCHSCREEN);boolean sentDown;try{sentDown=automation.injectInputEvent(down,true);}finally{down.recycle();}SystemClock.sleep(40);MotionEvent up=MotionEvent.obtain(at,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,bounds.exactCenterX(),bounds.exactCenterY(),0);up.setSource(InputDevice.SOURCE_TOUCHSCREEN);boolean sentUp;try{sentUp=automation.injectInputEvent(up,true);}finally{up.recycle();}if(sentDown&&sentUp)return true;}
            fail("Could not activate owned Remove confirmation. Dialog nodes: "+diagnostic(root));return false;
        }finally{info.flags=flags;automation.setServiceInfo(info);}
    }
    @SuppressWarnings("deprecation")
    @Test public void completedOwnedDownloadCardPlaysAndDeletesThroughUi()throws Exception {
        Context target=context();File directory=target.getExternalFilesDir(Environment.DIRECTORY_MOVIES);assertNotNull(directory);assertTrue(directory.exists()||directory.mkdirs());
        File file=new File(directory,"owned-download-acceptance.mp4");
        try(InputStream input=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("fixture.mp4");OutputStream output=new FileOutputStream(file)){
            byte[] buffer=new byte[32768];int n;while((n=input.read(buffer))!=-1)output.write(buffer,0,n);
        }
        DownloadManager manager=(DownloadManager)target.getSystemService(Context.DOWNLOAD_SERVICE);long id=-1;String offlineIdentity=null;
        try {
            id=manager.addCompletedDownload(TITLE,"Locally generated acceptance video",false,"video/mp4",file.getAbsolutePath(),file.length(),true);
            assertTrue("Owned fixture registered with DownloadManager",id>0);String key=Long.toString(id);
            offlineIdentity="offline:"+manager.getUriForDownloadedFile(id);
            assertTrue(target.getSharedPreferences("phone_downloads",Context.MODE_PRIVATE).edit().putString(key,TITLE).commit());
            try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
                main(scenario,a->call(a,"browse",new Class[]{String.class},"Downloads"));
                waitFor(()->{boolean[] ready={false};main(scenario,a->{View title=findTitle((View)field(a,"root"));ready[0]=title!=null&&text((View)title.getParent(),"Ready to play");});return ready[0];},3000);
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                // View assertions may precede the rendered frame after navigation.
                // Allow layout/draw to settle for visual evidence only.
                Thread.sleep(500);InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                Bitmap screenshot=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull("Screenshot captured",screenshot);
                try(OutputStream out=new FileOutputStream(new File(target.getExternalFilesDir(null),"qa-download-card.png"))){assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG,100,out));}finally{screenshot.recycle();}
                main(scenario,a->{View title=findTitle((View)field(a,"root"));View play=button((View)title.getParent(),"Play");assertNotNull("Play button belongs to owned card",play);assertTrue(play.isShown());assertTrue(play.performClick());});
                waitFor(()->playing(scenario),15000);waitFor(()->position(scenario)>1200,6000);
                main(scenario,a->{assertTrue((Boolean)call(a,"offline",new Class[]{}));call(a,"leavePlayer",new Class[]{});assertEquals("Downloads",field(a,"route"));});
                waitFor(()->{boolean[] ready={false};main(scenario,a->{View title=findTitle((View)field(a,"root"));ready[0]=title!=null&&button((View)title.getParent(),"Remove")!=null;});return ready[0];},4000);
                main(scenario,a->{View title=findTitle((View)field(a,"root"));assertNotNull(title);View remove=button((View)title.getParent(),"Remove");assertNotNull(remove);assertTrue(remove.performClick());});
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                AccessibilityNodeInfo dialog=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();assertNotNull(dialog);
                assertFalse("Removal confirmation is open",dialog.findAccessibilityNodeInfosByText("Remove downloaded video?").isEmpty());
                assertTrue("Confirm owned file removal through actual dialog action",clickPositiveRemoval());
                waitFor(()->!target.getSharedPreferences("phone_downloads",Context.MODE_PRIVATE).contains(key)&&!file.exists(),4000);
                main(scenario,a->assertNull("Removed card is gone",findTitle((View)field(a,"root"))));
            }
        }finally {
            if(id>0){manager.remove(id);target.getSharedPreferences("phone_downloads",Context.MODE_PRIVATE).edit().remove(Long.toString(id)).commit();}
            if(file.exists())file.delete();Thread.sleep(400);
            if(offlineIdentity!=null){Library library=new Library(target);library.removeTitles("history",Collections.singleton(offlineIdentity));library.close();Thread.sleep(250);}
        }
    }
}
