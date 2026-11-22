package org.emransm.moviebox.phone;

import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

/** Device-only integration checks. Owned media and uniquely scoped records only. */
@RunWith(AndroidJUnit4.class)
public class PhoneFlowScenarios {
    private static final String LIST_ID="generated-phone-flow-list-fixture";
    private static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private static File ownedFixture()throws Exception {
        File file=new File(context().getFilesDir(),"flow-owned-fixture.mp4");
        if(!file.exists())try(InputStream input=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("fixture.mp4");OutputStream output=new FileOutputStream(file)){
            byte[] buffer=new byte[32768];int n;while((n=input.read(buffer))!=-1)output.write(buffer,0,n);
        }
        return file;
    }
    @After public void removeOnlyThisSuiteRecords()throws Exception {
        Thread.sleep(400);
        Library disk=new Library(context());
        Set<String> ids=new HashSet<>(Arrays.asList(LIST_ID,"generated-native-phone-fixture","offline:"+Uri.fromFile(new File(context().getFilesDir(),"flow-owned-fixture.mp4"))));
        disk.removeTitles("history",ids);disk.removeTitles("list",ids);disk.close();Thread.sleep(300);
    }
    private static void tap(View view,float horizontalFraction){
        long time=SystemClock.uptimeMillis();float x=Math.max(1,view.getWidth()*horizontalFraction),y=Math.max(1,view.getHeight()*.4f);
        MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,x,y,0);
        MotionEvent up=MotionEvent.obtain(time,time+20,MotionEvent.ACTION_UP,x,y,0);
        try{view.dispatchTouchEvent(down);view.dispatchTouchEvent(up);}finally{down.recycle();up.recycle();}
    }
    private static boolean containsText(View view,String text){
        if(view instanceof TextView&&((TextView)view).getText().toString().contains(text))return true;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(containsText(group.getChildAt(i),text))return true;}
        return false;
    }
    @Test public void touchControlsToggleImmediatelyAndDoubleTapSeeks()throws Exception {
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            start(scenario,Uri.fromFile(ownedFixture()).toString());waitFor(()->playing(scenario),15000);waitFor(()->position(scenario)>1000,6000);
            android.graphics.Bitmap shot=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            if(shot!=null)try(OutputStream out=new FileOutputStream(new File(context().getExternalFilesDir(null),"qa-generated-player.png"))){shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{if(shot!=null)shot.recycle();}
            main(scenario,a->{View pause=(View)field(a,"pauseButton");assertTrue(pause.isShown());assertTrue(pause.performClick());});
            waitFor(()->!playing(scenario),1000);long paused=position(scenario);Thread.sleep(250);assertTrue(Math.abs(position(scenario)-paused)<100);
            main(scenario,a->assertTrue(((View)field(a,"pauseButton")).performClick()));waitFor(()->playing(scenario),1000);
            main(scenario,a->((Runnable)field(a,"hideControls")).run());
            main(scenario,a->tap((View)field(a,"video"),.5f));
            waitFor(()->{boolean[] visible={false};main(scenario,a->visible[0]=(Boolean)field(a,"visibleControls"));return visible[0];},1200);
            Thread.sleep(400);long before=position(scenario);
            main(scenario,a->tap((View)field(a,"video"),.8f));Thread.sleep(90);main(scenario,a->tap((View)field(a,"video"),.8f));
            waitFor(()->position(scenario)>before+8500,4000);
        }
    }
    @Test public void savedTitleSurvivesRecreationAndRendersInList()throws Exception {
        JSONObject fixture=new JSONObject().put("subjectId",LIST_ID).put("subjectType",1).put("title","Owned flow list fixture");
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            main(scenario,a->{Library library=(Library)field(a,"library");if(!library.contains(fixture))library.toggle(fixture);call(a,"browse",new Class[]{String.class},"My list");});
            scenario.recreate();
            main(scenario,a->{assertTrue(((Library)field(a,"library")).contains(fixture));call(a,"browse",new Class[]{String.class},"My list");});
            waitFor(()->{boolean[] found={false};main(scenario,a->found[0]=containsText((View)field(a,"root"),"Owned flow list fixture"));return found[0];},3000);
        }
    }
    @Test public void ownedOfflineVideoDecodesAndBackReturnsDownloads()throws Exception {
        Uri uri=Uri.fromFile(ownedFixture());
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            main(scenario,a->call(a,"openDownloaded",new Class[]{Uri.class,String.class},uri,"Owned offline flow fixture"));
            waitFor(()->playing(scenario),15000);waitFor(()->position(scenario)>1200,6000);
            main(scenario,a->{assertTrue((Boolean)call(a,"offline",new Class[]{}));call(a,"leavePlayer",new Class[]{});assertNull(field(a,"player"));assertEquals("Downloads",field(a,"route"));});
            Thread.sleep(500);Library disk=new Library(context());
            try{JSONObject subject=new JSONObject().put("subjectId","offline:"+uri);assertTrue(disk.position(subject,0,0)>=1000);}finally{disk.close();}
        }
    }
    @Test public void backgroundAndNavigationDiscardStaleWorkCallback()throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),finished=new CountDownLatch(1);AtomicBoolean applied=new AtomicBoolean(false),timedOut=new AtomicBoolean(false);
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            Class<?> workType=Class.forName("org.emransm.moviebox.phone.PhoneActivity$Work"),resultType=Class.forName("org.emransm.moviebox.phone.PhoneActivity$Result");
            Object work=Proxy.newProxyInstance(workType.getClassLoader(),new Class[]{workType},(p,m,args)->{if(m.getName().equals("run")){entered.countDown();if(!release.await(30,TimeUnit.SECONDS)){timedOut.set(true);throw new IllegalStateException("Test harness did not release the worker after navigation");}finished.countDown();return "stale fixture";}return null;});
            Object result=Proxy.newProxyInstance(resultType.getClassLoader(),new Class[]{resultType},(p,m,args)->{if(m.getName().equals("accept"))applied.set(true);return null;});
            int[] originalGeneration={-1};main(scenario,a->{originalGeneration[0]=(Integer)field(a,"generation");call(a,"work",new Class[]{workType,resultType},work,result);});assertTrue("Worker must start before navigation",entered.await(3,TimeUnit.SECONDS));
            scenario.moveToState(Lifecycle.State.CREATED);scenario.moveToState(Lifecycle.State.RESUMED);
            main(scenario,a->{call(a,"browse",new Class[]{String.class},"Downloads");assertTrue("Navigation must invalidate the original request",(Integer)field(a,"generation")>originalGeneration[0]);});assertFalse("Worker must still be held until after navigation",timedOut.get());assertFalse("Held worker must not have applied before navigation",applied.get());release.countDown();assertTrue(finished.await(2,TimeUnit.SECONDS));Thread.sleep(300);InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            main(scenario,a->{assertEquals("Downloads",field(a,"route"));assertNull(field(a,"player"));});assertFalse("Previous screen callback must not change current route",applied.get());
        }finally{release.countDown();}
    }
}
