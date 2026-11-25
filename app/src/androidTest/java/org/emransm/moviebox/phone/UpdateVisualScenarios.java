package org.emransm.moviebox.phone;

import android.content.SharedPreferences;
import android.widget.FrameLayout;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import java.util.UUID;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

/** Production candidate visuals with owned metadata; no network download or install. */
@RunWith(AndroidJUnit4.class)
public class UpdateVisualScenarios {
 static void now(PhoneActivity activity,PhoneMediaScenarios.Action action){java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try{action.run(activity);}catch(Throwable error){failure.set(error);}});if(failure.get()!=null)throw new AssertionError(failure.get());}

 static void redraw(android.view.View view){view.invalidate();if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)redraw(group.getChildAt(i));}}
 static void captureSpinner(String name)throws Exception {
  Thread.sleep(250);android.graphics.Bitmap image;android.os.ParcelFileDescriptor descriptor=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand("screencap -p");try(java.io.InputStream input=new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)){image=android.graphics.BitmapFactory.decodeStream(input);}assertNotNull(image);
  try(java.io.FileOutputStream output=new java.io.FileOutputStream(new java.io.File(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),name))){assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output));}finally{image.recycle();}
 }

 @Test public void meCheckAndAvailableNoticeAreVisibleWithoutLeavingTheApp() throws Exception {
  UpdateReliabilityScenarios.Queued worker=new UpdateReliabilityScenarios.Queued();
  SharedPreferences[] preferences={null};PhoneActivity[] activity={null};
  try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)) {
   BlockFlowScenarios.isolate(scenario,new BlockFlowScenarios.FakeApi());Thread.sleep(3600);
   try {main(scenario,a->{
    activity[0]=a;call(a,"browse",new Class[]{String.class},"Settings");
    FrameLayout root=(FrameLayout)field(a,"root");
    preferences[0]=a.getSharedPreferences("qa-owned-update-visual-"+UUID.randomUUID(),0);
    JSONObject available=InAppUpdateScenarios.metadata(new byte[]{42});
    android.view.View original=root.findViewWithTag("moviebox-manual-update-row");assertNotNull(original);android.view.ViewGroup group=(android.view.ViewGroup)original.getParent();int index=group.indexOfChild(original);android.view.View replacement=(android.view.View)call(a,"updateCheckRow",new Class[]{SharedPreferences.class,UpdateNotice.Checker.class,java.util.concurrent.Executor.class},preferences[0],(UpdateNotice.Checker)()->available,worker);group.removeView(original);group.addView(replacement,index);replacement.requestRectangleOnScreen(new android.graphics.Rect(0,0,replacement.getWidth(),replacement.getHeight()),true);assertTrue(replacement.performClick());assertEquals("Checking for updates",replacement.getContentDescription());
   });
   Thread.sleep(250);now(activity[0],a->{android.view.View row=((FrameLayout)field(a,"root")).findViewWithTag("moviebox-manual-update-row");row.requestRectangleOnScreen(new android.graphics.Rect(0,0,row.getWidth(),row.getHeight()),true);});captureSpinner("qa-update-me-checking.png");
   now(activity[0],a->{worker.finish();FrameLayout root=(FrameLayout)field(a,"root");assertNull(root.findViewWithTag("moviebox-update-checking"));assertNotNull(root.findViewWithTag("moviebox-update-notice"));});
   Thread.sleep(250);BlockFlowScenarios.capture("qa-update-me-available.png");
   } finally {if(activity[0]!=null&&worker.task!=null)now(activity[0],a->worker.finish());}
  } finally {if(preferences[0]!=null)preferences[0].edit().clear().commit();}
 }
 @Test public void laterSuppressesForegroundButNextColdLaunchUsesCachedAvailableWithoutNewRequest() throws Exception {
  try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)) {
   main(scenario,a->{SharedPreferences prefs=a.getSharedPreferences("qa-owned-cold-update-"+UUID.randomUUID(),0);try{
    FrameLayout root=(FrameLayout)field(a,"root");JSONObject available=InAppUpdateScenarios.metadata(new byte[]{42});
    prefs.edit().putString("pending",available.toString()).putLong("checked",100000000L).commit();
    java.lang.reflect.Field consumed=UpdateNotice.class.getDeclaredField("coldLaunchConsumed");consumed.setAccessible(true);consumed.setBoolean(null,false);
    UpdateNotice.beginColdLaunch(prefs);UpdateNotice.presentPending(a,root,()->false,prefs);
    android.view.View notice=root.findViewWithTag("moviebox-update-notice");assertNotNull(notice);assertTrue(description(notice,"Dismiss optional update").performClick());
    UpdateNotice.beginColdLaunch(prefs);UpdateNotice.presentPending(a,root,()->false,prefs);assertNull(root.findViewWithTag("moviebox-update-notice"));
    assertFalse(prefs.getString("pending","").isEmpty());consumed.setBoolean(null,false);UpdateNotice.beginColdLaunch(prefs);
    java.util.concurrent.atomic.AtomicInteger network=new java.util.concurrent.atomic.AtomicInteger();UpdateNotice.check(a,root,()->false,false,prefs,()->{network.incrementAndGet();return available;},()->100000001L,Runnable::run);
    assertNotNull(root.findViewWithTag("moviebox-update-notice"));assertEquals(0,network.get());root.removeView(root.findViewWithTag("moviebox-update-notice"));
   }finally{prefs.edit().clear().commit();}});
  }
 }

 @Test public void persistedRateDeadlineSurvivesRelaunchAndManualCannotBurnQuota()throws Exception {
  String name="qa-owned-rate-backoff-"+UUID.randomUUID();java.util.concurrent.atomic.AtomicLong clock=new java.util.concurrent.atomic.AtomicLong(100000000L);java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();SharedPreferences[] prefs={null};
  try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)) {
   main(scenario,a->{prefs[0]=a.getSharedPreferences(name,0);FrameLayout root=(FrameLayout)field(a,"root");UpdateNotice.check(a,root,()->false,false,prefs[0],()->{calls.incrementAndGet();throw new Updates.CheckUnavailable("Update checks are temporarily limited.",100120000L);},clock::get,Runnable::run);assertEquals(1,calls.get());assertEquals(100120000L,prefs[0].getLong("retry_after",0));assertFalse(prefs[0].contains("checked"));prefs[0].edit().commit();});
   scenario.recreate();main(scenario,a->{SharedPreferences stored=a.getSharedPreferences(name,0);FrameLayout root=(FrameLayout)field(a,"root");UpdateNotice.Checker checker=()->{calls.incrementAndGet();return new JSONObject().put("status",Updates.CURRENT).put("available",false).put("version",BuildConfig.VERSION_NAME);};
    UpdateNotice.check(a,root,()->false,true,stored,checker,clock::get,Runnable::run);UpdateNotice.check(a,root,()->false,false,stored,checker,clock::get,Runnable::run);assertEquals(1,calls.get());
    stored.edit().putString("pending",InAppUpdateScenarios.metadata(new byte[]{42}).toString()).putString("dismissed","0.1.5").commit();java.lang.reflect.Field consumed=UpdateNotice.class.getDeclaredField("coldLaunchConsumed");consumed.setAccessible(true);consumed.setBoolean(null,false);UpdateNotice.beginColdLaunch(stored);UpdateNotice.presentPending(a,root,()->false,stored);assertNotNull("Cached available prompt still works during server cooldown",root.findViewWithTag("moviebox-update-notice"));assertEquals(1,calls.get());
    clock.set(100120001L);UpdateNotice.check(a,root,()->false,true,stored,checker,clock::get,Runnable::run);assertEquals(2,calls.get());assertFalse(stored.contains("retry_after"));assertNull(root.findViewWithTag("moviebox-update-notice"));assertEquals("You're up to date.",Updates.message(checker.check()));
   });
  }finally{if(prefs[0]!=null)prefs[0].edit().clear().commit();}
 }

}
