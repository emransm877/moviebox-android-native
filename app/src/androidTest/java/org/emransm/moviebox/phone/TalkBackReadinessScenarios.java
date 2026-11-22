package org.emransm.moviebox.phone;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.UiAutomation;
import android.content.Context;
import android.os.SystemClock;
import android.graphics.Rect;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.*;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

/** Real installed TalkBack, actual touch exploration + double tap, app-only observations. */
@RunWith(AndroidJUnit4.class)
public class TalkBackReadinessScenarios {
 private static final Set<String> focusedEvents=Collections.synchronizedSet(new HashSet<>());private static ActivityScenario<PhoneActivity> activeScenario;
 private static String route(){String[] value={""};main(activeScenario,a->value[0]=(String)field(a,"route"));return value[0];}
 private static final String PKG="org.emransm.moviebox.phone";
 private static UiAutomation automation(){return InstrumentationRegistry.getInstrumentation().getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);}
 private static AccessibilityNodeInfo ownRoot(){AccessibilityNodeInfo node=automation().getRootInActiveWindow();assertNotNull("An active app accessibility window exists",node);assertEquals("Abort if a tutorial or another app takes foreground",PKG,String.valueOf(node.getPackageName()));return node;}
 private static AccessibilityNodeInfo named(AccessibilityNodeInfo node,String name){if(name.contentEquals(node.getContentDescription()==null?"":node.getContentDescription()))return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null){AccessibilityNodeInfo found=named(child,name);if(found!=null)return found;}}return null;}
 private static boolean filterInput(android.view.InputEvent event){try{UiAutomation.class.getDeclaredMethod("injectInputEventToInputFilter",android.view.InputEvent.class).invoke(automation(),event);return true;}catch(Exception failure){throw new AssertionError("Real TalkBack gestures require Android accessibility input-filter TestApi",failure);}}
 private static void explore(float x,float y){UiAutomation ui=automation();long down=SystemClock.uptimeMillis();float start=x-24;MotionEvent first=MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,start,y,0);first.setSource(InputDevice.SOURCE_TOUCHSCREEN);try{assertTrue(filterInput(first));}finally{first.recycle();}for(int n=1;n<=4;n++){SystemClock.sleep(70);MotionEvent move=MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,start+12*n,y,0);move.setSource(InputDevice.SOURCE_TOUCHSCREEN);try{assertTrue(filterInput(move));}finally{move.recycle();}}MotionEvent last=MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x+24,y,0);last.setSource(InputDevice.SOURCE_TOUCHSCREEN);try{assertTrue(filterInput(last));}finally{last.recycle();}}
 private static void readingSwipe(boolean forward){AccessibilityNodeInfo root=ownRoot();Rect screen=new Rect();root.getBoundsInScreen(screen);float start=screen.width()*(forward?.75f:.25f),end=screen.width()*(forward?.25f:.75f),y=screen.height()*.4f;long down=SystemClock.uptimeMillis();for(int n=0;n<=10;n++){if(n>0)SystemClock.sleep(18);MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),n==0?MotionEvent.ACTION_DOWN:n==10?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,start+(end-start)*n/10,y,0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);try{assertTrue(filterInput(e));}finally{e.recycle();}}SystemClock.sleep(300);}
 private static void tap(float x,float y){contact(x,y,200);}
 private static void contact(float x,float y,long duration){UiAutomation ui=automation();long downTime=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(downTime,downTime,MotionEvent.ACTION_DOWN,x,y,0);down.setSource(InputDevice.SOURCE_TOUCHSCREEN);try{assertTrue(filterInput(down));SystemClock.sleep(duration);MotionEvent up=MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,y,0);up.setSource(InputDevice.SOURCE_TOUCHSCREEN);try{assertTrue(filterInput(up));}finally{up.recycle();}}finally{down.recycle();}}
 private static String focusName(){AccessibilityNodeInfo root=ownRoot();AccessibilityNodeInfo focus=root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);if(focus==null)return "";String name=String.valueOf(focus.getContentDescription());return name.equals("null")?String.valueOf(focus.getText()):name;}
 private static void activate(String label)throws Exception{AccessibilityNodeInfo target=named(ownRoot(),label);assertNotNull("Named target: "+label,target);Rect b=new Rect();target.getBoundsInScreen(b);assertFalse(b.isEmpty());focusedEvents.clear();explore(b.exactCenterX(),b.exactCenterY());android.os.Bundle diagnostic=new android.os.Bundle();diagnostic.putString("stream","\nTalkBack target="+label+"; actual focused label after exploration="+focusName()+"\n");InstrumentationRegistry.getInstrumentation().sendStatus(2,diagnostic);try{waitFor(()->focusName().equals(label)||focusedEvents.contains(label),5000);}catch(AssertionError failure){android.os.Bundle extra=new android.os.Bundle();extra.putString("stream","\nFAILED_FOCUS_TARGET="+label+"; LAST_FOCUS="+focusName()+"; ROUTE="+route()+"; FOCUS_EVENTS="+focusedEvents+"; TOUCH_EXPLORATION="+((AccessibilityManager)InstrumentationRegistry.getInstrumentation().getTargetContext().getSystemService(Context.ACCESSIBILITY_SERVICE)).isTouchExplorationEnabled()+"\n");InstrumentationRegistry.getInstrumentation().sendStatus(2,extra);throw failure;}android.os.Bundle evidence=new android.os.Bundle();evidence.putString("stream","\nREAL_TALKBACK_FOCUS="+label+"\n");InstrumentationRegistry.getInstrumentation().sendStatus(2,evidence);String destination=label.equals("Me")?"Settings":label.equals("Home")?"Discover":"Downloads";if(!route().equals(destination)){Rect viewport=new Rect();ownRoot().getBoundsInScreen(viewport);float clickX=label.equals("Home")?viewport.exactCenterX():b.exactCenterX(),clickY=label.equals("Home")?viewport.exactCenterY():b.exactCenterY();contact(clickX,clickY,40);SystemClock.sleep(85);contact(clickX,clickY,40);SystemClock.sleep(500);}System.out.println("TALKBACK_ACTIVATION_ROUTE="+route()+"; FOCUS_EVENTS="+focusedEvents);}
 @Test public void installedTalkBackTouchExploreAndDoubleTapOwnNavigation()throws Exception{
  automation();AccessibilityManager am=(AccessibilityManager)InstrumentationRegistry.getInstrumentation().getTargetContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
  org.junit.Assume.assumeTrue("Requires explicit reversible real-TalkBack session; see REAL-TALKBACK-REVIEW.md",am.isEnabled()&&am.isTouchExplorationEnabled());
  waitFor(()->am.isEnabled()&&am.isTouchExplorationEnabled(),15000);
  boolean spoken=false;for(AccessibilityServiceInfo info:am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN)){if(info.getId().startsWith("com.google.android.marvin.talkback/"))spoken=true;}assertTrue("Real Google TalkBack spoken service enabled",spoken);android.os.Bundle enabled=new android.os.Bundle();enabled.putString("stream","\nREAL_TALKBACK_ENABLED=true; TOUCH_EXPLORATION=true\n");InstrumentationRegistry.getInstrumentation().sendStatus(2,enabled);
  try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
   activeScenario=scenario;automation().setOnAccessibilityEventListener(event->{if(PKG.contentEquals(event.getPackageName()==null?"":event.getPackageName())&&event.getEventType()==android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED){AccessibilityNodeInfo source=event.getSource();if(source!=null){CharSequence desc=source.getContentDescription();if(desc==null)desc=source.getText();if(desc!=null)focusedEvents.add(desc.toString());}}});InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(900);ownRoot();
   activate("Me");main(scenario,a->assertEquals("Settings",field(a,"route")));System.out.println("REAL_TALKBACK_ACTIVATED=Me; ROUTE=Settings");
   activate("Downloads");main(scenario,a->assertEquals("Downloads",field(a,"route")));System.out.println("REAL_TALKBACK_ACTIVATED=Downloads; ROUTE=Downloads");
   activate("Home");main(scenario,a->assertEquals("Discover",field(a,"route")));System.out.println("REAL_TALKBACK_ACTIVATED=Home; ROUTE=Discover");
  }
 }
}
