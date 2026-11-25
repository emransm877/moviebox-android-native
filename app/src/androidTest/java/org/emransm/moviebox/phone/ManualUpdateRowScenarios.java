package org.emransm.moviebox.phone;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;
/** Queued metadata only, with owned preferences. Does not contact GitHub. */
@RunWith(AndroidJUnit4.class)
public class ManualUpdateRowScenarios {
 @Test public void physicalPressShowsRippleThenRowChecksUntilQueuedResult()throws Exception{
  AtomicReference<Runnable> pending=new AtomicReference<>();AtomicInteger checks=new AtomicInteger(),submitted=new AtomicInteger();View[] row={null};SharedPreferences[] prefs={null};PhoneActivity[] activity={null};
  try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
   main(s,a->{activity[0]=a;prefs[0]=a.getSharedPreferences("qa-row-"+UUID.randomUUID(),0);UpdateNotice.Checker checker=()->{checks.incrementAndGet();return new JSONObject().put("status",Updates.CURRENT).put("available",false).put("version",BuildConfig.VERSION_NAME);};Executor worker=task->{submitted.incrementAndGet();pending.set(task);};row[0]=(View)call(a,"updateCheckRow",new Class[]{SharedPreferences.class,UpdateNotice.Checker.class,Executor.class},prefs[0],checker,worker);FrameLayout root=(FrameLayout)field(a,"root");root.removeAllViews();root.addView(row[0],new FrameLayout.LayoutParams(-1,-2));});Thread.sleep(200);
   UpdateVisualScenarios.now(activity[0],a->{View button=row[0];assertTrue(button.getBackground() instanceof android.graphics.drawable.RippleDrawable);assertTrue(button.getHeight()>=Math.round(48*a.getResources().getDisplayMetrics().density));long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,button.getWidth()/2f,button.getHeight()/2f,0);MotionEvent up=MotionEvent.obtain(now,now+50,MotionEvent.ACTION_UP,button.getWidth()/2f,button.getHeight()/2f,0);try{assertTrue(button.dispatchTouchEvent(down));assertTrue("Actual touch has pressed state",button.isPressed());assertTrue(button.dispatchTouchEvent(up));}finally{down.recycle();up.recycle();}});Thread.sleep(100);
   UpdateVisualScenarios.now(activity[0],a->{View button=row[0];assertFalse(button.isEnabled());assertEquals("Checking for updates",button.getContentDescription());assertTrue(BlockFlowScenarios.hasText(button,"Checking for updates"));assertNotNull(description(button,"Checking for updates"));button.performClick();assertEquals(1,submitted.get());assertEquals(0,checks.get());});UpdateVisualScenarios.captureSpinner("qa-manual-update-row-checking.png");
   pending.get().run();UpdateVisualScenarios.now(activity[0],a->{assertTrue(row[0].isEnabled());assertTrue(BlockFlowScenarios.hasText(row[0],"Check for updates"));assertEquals(1,checks.get());assertTrue(prefs[0].contains("checked"));});BlockFlowScenarios.capture("qa-manual-update-row-restored.png");
  }finally{if(prefs[0]!=null)prefs[0].edit().clear().commit();}
 }
}
