package org.emransm.moviebox.phone;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.SystemClock;
import android.view.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;
/** API23 runs prove fallback arbitration only; modern system dispatch needs its own device evidence. */
@RunWith(AndroidJUnit4.class)
public class SystemGestureArbitrationScenarios {
 static boolean event(View view,long down,int action,float x,float y){MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);try{return view.dispatchTouchEvent(e);}finally{e.recycle();}}
 static void orient(ActivityScenario<PhoneActivity>s,int requested,int wanted)throws Exception{main(s,a->a.setRequestedOrientation(requested));waitFor(()->{boolean[] ready={false};main(s,a->ready[0]=a.getResources().getConfiguration().orientation==wanted);return ready[0];},5000);Thread.sleep(150);}
 static void swipe(PhoneActivity a,float x,float y,float toX,float toY,boolean blocked)throws Exception{View video=description((View)field(a,"root"),"Show player controls");long down=SystemClock.uptimeMillis();assertEquals("Player surface consumes only its own interior stream",!blocked,event(video,down,MotionEvent.ACTION_DOWN,x,y));for(int n=1;n<=6;n++){SystemClock.sleep(20);event(video,down,MotionEvent.ACTION_MOVE,x+(toX-x)*n/6,y+(toY-y)*n/6);}assertEquals(!blocked,(Boolean)field(a,"swipeSeeking"));event(video,down,MotionEvent.ACTION_UP,toX,toY);}
 @Test public void sideBackEdgesNeverSeekOrChangeLevelsInEitherOrientation()throws Exception{
  try(ActivityScenario<PhoneActivity>s=ActivityScenario.launch(PhoneActivity.class)){start(s,Uri.fromFile(fixture()).toString());waitFor(()->playing(s),12000);
   for(int mode:new int[]{Configuration.ORIENTATION_PORTRAIT,Configuration.ORIENTATION_LANDSCAPE}){orient(s,mode==Configuration.ORIENTATION_PORTRAIT?ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,mode);
    main(s,a->{ExoPlayer p=(ExoPlayer)field(a,"player");p.pause();p.seekTo(20000);View video=(View)field(a,"video");float brightness=a.getWindow().getAttributes().screenBrightness,volume=((PlayerVolume)field(a,"playerVolume")).level();swipe(a,1,video.getHeight()*.5f,video.getWidth()*.65f,video.getHeight()*.5f,true);swipe(a,video.getWidth()-1,video.getHeight()*.5f,video.getWidth()*.35f,video.getHeight()*.25f,true);assertFalse((Boolean)field(a,"holdPreview"));assertEquals(20000,p.getCurrentPosition(),100);assertEquals(brightness,a.getWindow().getAttributes().screenBrightness,.001);assertEquals(volume,((PlayerVolume)field(a,"playerVolume")).level(),.001);});
   }
  }
 }
 @Test public void fullscreenTopBottomAreReservedButEmbeddedVideoTopIsNotPhysicalTop()throws Exception{
  try(ActivityScenario<PhoneActivity>s=ActivityScenario.launch(PhoneActivity.class)){start(s,Uri.fromFile(fixture()).toString());waitFor(()->playing(s),12000);
   main(s,a->{View video=(View)field(a,"video");int[] at=new int[2];video.getLocationInWindow(at);assertTrue("Portrait embedded video has a window offset",at[1]>0);MotionEvent down=MotionEvent.obtain(1,1,MotionEvent.ACTION_DOWN,video.getWidth()/2f,1,0);try{int top=Math.max(0,at[1]-1);assertFalse("Do not reserve an artificial notification edge at embedded video top",(Boolean)call(a,"systemGestureStart",new Class[]{View.class,MotionEvent.class,int[].class},video,down,new int[]{0,top,0,0}));assertTrue("Real window gesture region is projected into the video",(Boolean)call(a,"systemGestureStart",new Class[]{View.class,MotionEvent.class,int[].class},video,down,new int[]{0,at[1]+10,0,0}));}finally{down.recycle();}});
   orient(s,ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,Configuration.ORIENTATION_LANDSCAPE);main(s,a->{ExoPlayer p=(ExoPlayer)field(a,"player");p.pause();p.seekTo(20000);View v=(View)field(a,"video");swipe(a,v.getWidth()*.5f,1,v.getWidth()*.8f,v.getHeight()*.4f,true);swipe(a,v.getWidth()*.5f,v.getHeight()-1,v.getWidth()*.8f,v.getHeight()*.5f,true);assertEquals(20000,p.getCurrentPosition(),100);swipe(a,v.getWidth()*.25f,v.getHeight()*.5f,v.getWidth()*.7f,v.getHeight()*.5f,false);assertTrue(p.getCurrentPosition()>25000);});
  }
 }
 @Test public void canceledEdgeAndRotationCannotCommitOldStreamAndNewCenterSeekStillWorks()throws Exception{
  try(ActivityScenario<PhoneActivity>s=ActivityScenario.launch(PhoneActivity.class)){start(s,Uri.fromFile(fixture()).toString());waitFor(()->playing(s),12000);long[] down={0};
   main(s,a->{ExoPlayer p=(ExoPlayer)field(a,"player");p.pause();p.seekTo(20000);View v=(View)field(a,"video");down[0]=SystemClock.uptimeMillis();event(v,down[0],MotionEvent.ACTION_DOWN,1,v.getHeight()/2f);event(v,down[0],MotionEvent.ACTION_CANCEL,v.getWidth()/2f,v.getHeight()/2f);assertFalse((Boolean)field(a,"swipeSeeking"));down[0]=SystemClock.uptimeMillis();event(v,down[0],MotionEvent.ACTION_DOWN,v.getWidth()*.25f,v.getHeight()/2f);event(v,down[0],MotionEvent.ACTION_MOVE,v.getWidth()*.6f,v.getHeight()/2f);assertTrue((Boolean)field(a,"swipeSeeking"));});
   orient(s,ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,Configuration.ORIENTATION_LANDSCAPE);main(s,a->{ExoPlayer p=(ExoPlayer)field(a,"player");View v=(View)field(a,"video");assertFalse((Boolean)field(a,"swipeSeeking"));event(v,down[0],MotionEvent.ACTION_MOVE,v.getWidth()*.75f,v.getHeight()/2f);event(v,down[0],MotionEvent.ACTION_UP,v.getWidth()*.75f,v.getHeight()/2f);assertEquals(20000,p.getCurrentPosition(),100);swipe(a,v.getWidth()*.25f,v.getHeight()*.5f,v.getWidth()*.7f,v.getHeight()*.5f,false);assertTrue(p.getCurrentPosition()>25000);});
  }
 }
 @Test public void reservedEdgeLongPressCannotStartFastSeekOrAccessibilityClick()throws Exception{
  try(ActivityScenario<PhoneActivity>s=ActivityScenario.launch(PhoneActivity.class)){start(s,Uri.fromFile(fixture()).toString());waitFor(()->playing(s),12000);long[] down={0};boolean[] visible={false};
   main(s,a->{((ExoPlayer)field(a,"player")).pause();visible[0]=(Boolean)field(a,"visibleControls");View surface=description((View)field(a,"root"),"Show player controls");down[0]=SystemClock.uptimeMillis();assertFalse(event(surface,down[0],MotionEvent.ACTION_DOWN,1,surface.getHeight()/2f));});Thread.sleep(750);
   main(s,a->{View surface=description((View)field(a,"root"),"Show player controls");assertFalse((Boolean)field(a,"holdPreview"));assertFalse(event(surface,down[0],MotionEvent.ACTION_UP,1,surface.getHeight()/2f));assertEquals(visible[0],(Boolean)field(a,"visibleControls"));});
  }
 }

 @Test public void zeroAndMixedReportedInsetsKeepAllFourConservativeFences()throws Exception{
  try(ActivityScenario<PhoneActivity>s=ActivityScenario.launch(PhoneActivity.class)){main(s,a->{int expected=Math.round(24*a.getResources().getDisplayMetrics().density);int[] zero=(int[])call(a,"minimumGestureInsets",new Class[]{int.class,int.class,int.class,int.class},0,0,0,0);assertArrayEquals(new int[]{expected,expected,expected,expected},zero);int[] mixed=(int[])call(a,"minimumGestureInsets",new Class[]{int.class,int.class,int.class,int.class},expected+37,0,expected+11,0);assertArrayEquals(new int[]{expected+37,expected,expected+11,expected},mixed);});}
 }

}
