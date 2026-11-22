package org.emransm.moviebox.phone;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

/** UI fixtures use a separate app-cache library; never mutate the user's library. */
@RunWith(AndroidJUnit4.class)
public class FullUiReadinessScenarios {
    private static JSONObject title(String id,String name,int type)throws Exception{return new JSONObject().put("subjectId",id).put("title",name).put("subjectType",type).put("releaseDate","2026-01-01").put("description","Owned interface test description. This long paragraph verifies the expand description action and proper line wrapping while avoiding service content. It is generated solely for local interface verification and must not be shown as a real catalogue title.").put("genre","Adventure, Drama");}
    private static void isolate(ActivityScenario<PhoneActivity> scenario)throws Exception{
        main(scenario,a->{call(a,"releasePlayer",new Class[]{});put(a,"generation",(Integer)field(a,"generation")+1);((java.util.concurrent.ThreadPoolExecutor)field(a,"network")).getQueue().clear();Library original=(Library)field(a,"library");original.close();File dir=new File(a.getCacheDir(),"agency-ui-owned-"+System.nanoTime());assertTrue(dir.mkdirs());Context isolated=new ContextWrapper(a){@Override public File getFilesDir(){return dir;}};put(a,"library",new Library(isolated));});
    }
    private static void capture(String name)throws Exception{InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(350);Bitmap b=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(b);File f=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),name);try(OutputStream out=new FileOutputStream(f)){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}finally{b.recycle();}}
    private static void click(ActivityScenario<PhoneActivity> scenario,String desc){main(scenario,a->{View control=description((View)field(a,"root"),desc);assertNotNull("Control exists: "+desc,control);assertTrue("Control clicks: "+desc,control.performClick());});}
    private static void dialogButton(String id)throws Exception{InstrumentationRegistry.getInstrumentation().waitForIdleSync();AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();assertNotNull(root);List<AccessibilityNodeInfo> nodes=root.findAccessibilityNodeInfosByViewId(id);if(nodes.isEmpty()){nodes=root.findAccessibilityNodeInfosByText(id.endsWith("button1")?"Remove":"Cancel");}assertFalse("Confirmation button "+id,nodes.isEmpty());assertTrue(nodes.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK));InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
    @Test public void ownedLibraryBulkSelectionCancelRemoveAndHistory()throws Exception{
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            isolate(s);JSONObject one=title("agency-ui-owned-one","Owned Movie UI fixture",1),two=title("agency-ui-owned-two","Owned Series UI fixture",2);
            main(s,a->{Library store=(Library)field(a,"library");store.toggle(one);store.toggle(two);call(a,"browse",new Class[]{String.class},"My list");});
            click(s,"Select titles");click(s,"Select all");main(s,a->assertEquals(2,((Set<?>)field(a,"selected")).size()));capture("qa-full-list-selected.png");
            click(s,"Remove selected");capture("qa-full-list-remove-confirm.png");dialogButton("android:id/button2");main(s,a->assertEquals(2,((Library)field(a,"library")).listing("list").size()));
            click(s,"Clear selection");main(s,a->assertTrue(((Set<?>)field(a,"selected")).isEmpty()));click(s,"Select all");click(s,"Remove selected");dialogButton("android:id/button1");main(s,a->assertTrue(((Library)field(a,"library")).listing("list").isEmpty()));capture("qa-full-list-empty.png");
            main(s,a->{Library store=(Library)field(a,"library");store.progress(one,0,0,10000,65000,false);store.progress(two,1,2,12000,65000,false);call(a,"browse",new Class[]{String.class},"History");});capture("qa-full-history.png");click(s,"Select titles");click(s,"Select all");click(s,"Remove selected");dialogButton("android:id/button1");main(s,a->assertTrue(((Library)field(a,"library")).listing("history").isEmpty()));
        }
    }
    @Test public void appScopedDoubleFontSettingsDetailAndMenus()throws Exception{
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            isolate(s);final Configuration[] original={null};final DisplayMetrics[] metrics={null};
            try{
                main(s,a->{original[0]=new Configuration(a.getResources().getConfiguration());metrics[0]=new DisplayMetrics();metrics[0].setTo(a.getResources().getDisplayMetrics());Configuration c=new Configuration(original[0]);c.fontScale=2f;DisplayMetrics scaled=new DisplayMetrics();scaled.setTo(metrics[0]);scaled.scaledDensity=scaled.density*2f;a.getResources().updateConfiguration(c,scaled);call(a,"browse",new Class[]{String.class},"Settings");});capture("qa-full-font2-settings.png");
                main(s,a->{put(a,"subject",title("agency-ui-owned-font","Owned long title for double font layout checks",2).put("seasons",new JSONArray().put(new JSONObject().put("se",1).put("allEp","1,2,3,4,5,6").put("maxEp",6)).put(new JSONObject().put("se",2).put("allEp","1,2,3").put("maxEp",3))));call(a,"appShell",new Class[]{String.class},"Details");call(a,"renderDetail",new Class[]{});});capture("qa-full-font2-details.png");
                main(s,a->call(a,"episodeMenu",new Class[]{boolean.class},false));capture("qa-full-font2-seasons.png");InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
            }finally{main(s,a->{if(original[0]!=null)a.getResources().updateConfiguration(original[0],metrics[0]);});}
        }
    }
}
