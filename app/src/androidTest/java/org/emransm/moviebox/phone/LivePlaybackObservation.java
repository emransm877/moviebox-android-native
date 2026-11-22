package org.emransm.moviebox.phone;

import android.content.*;
import android.os.SystemClock;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

/** Explicitly opt-in real service, isolated history. Records actual rendered-first-frame, not Activity launch. */
@RunWith(AndroidJUnit4.class)
public class LivePlaybackObservation {
 @Test public void actualSourceAndFirstFrame()throws Exception{
  org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveService","false").equals("true"));
  Context real=InstrumentationRegistry.getInstrumentation().getTargetContext();File owned=new File(real.getCacheDir(),"qa-live-loading-"+System.nanoTime());assertTrue(owned.mkdirs());
  Context isolated=new ContextWrapper(real){@Override public File getCacheDir(){return owned;}@Override public File getFilesDir(){return owned;}};
  UiCache cache=new UiCache(isolated,false);Api api=new Api("owned-live-playback-20261123-0000",cache);JSONObject report=new JSONObject();
  try{
   List<JSONObject> home=api.home();JSONObject selected=null;for(JSONObject item:home)if(item.optInt("subjectType")==1){selected=item;break;}assertNotNull(selected);
   long at=SystemClock.elapsedRealtime();JSONObject detail=api.detail(selected);report.put("id",detail.optString("subjectId")).put("detailMs",SystemClock.elapsedRealtime()-at);
   Api recreated=new Api("owned-live-playback-20261123-0000",cache);at=SystemClock.elapsedRealtime();JSONObject disk=recreated.detail(selected);report.put("recreatedDetailMs",SystemClock.elapsedRealtime()-at);assertEquals(detail.optString("title"),disk.optString("title"));recreated.close();
   at=SystemClock.elapsedRealtime();api.prefetchPlayback(detail,0,0,1080);report.put("prepareSourceMs",SystemClock.elapsedRealtime()-at);at=SystemClock.elapsedRealtime();List<JSONObject> sources=api.initialPlayback(detail,0,0,1080);report.put("preparedLookupMs",SystemClock.elapsedRealtime()-at).put("sources",sources.size());assertFalse(sources.isEmpty());
   final JSONObject movie=detail;final AtomicLong firstFrame=new AtomicLong();final long[] clicked={0};
   try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
    main(scenario,a->{call(a,"releasePlayer",new Class[]{});put(a,"generation",(Integer)field(a,"generation")+1);((ThreadPoolExecutor)field(a,"network")).getQueue().clear();((Library)field(a,"library")).close();put(a,"library",new Library(isolated));put(a,"api",api);put(a,"subject",movie);put(a,"season",0);put(a,"episode",0);clicked[0]=SystemClock.elapsedRealtime();call(a,"playSelection",new Class[]{JSONObject.class,int.class,int.class,boolean.class},movie,0,0,true);});
    final ExoPlayer[] attached={null};long deadline=SystemClock.elapsedRealtime()+25000;
    while(firstFrame.get()==0&&SystemClock.elapsedRealtime()<deadline){main(scenario,a->{ExoPlayer player=(ExoPlayer)field(a,"player");if(player!=null&&player!=attached[0]){attached[0]=player;player.addListener(new Player.Listener(){@Override public void onRenderedFirstFrame(){firstFrame.compareAndSet(0,SystemClock.elapsedRealtime());}});}});Thread.sleep(15);}
    assertTrue("Real source must render a video frame",firstFrame.get()>0);report.put("clickToFirstFrameMs",firstFrame.get()-clicked[0]);
    main(scenario,a->{ExoPlayer player=(ExoPlayer)field(a,"player");report.put("width",player.getVideoSize().width).put("height",player.getVideoSize().height);assertNull(player.getPlayerError());player.pause();});
   }
  }finally{
   api.close();try(FileOutputStream out=new FileOutputStream(new File(real.getCacheDir(),"live-playback-observation.json"))){out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));}
   Thread.sleep(300);DetailLoadingScenarios.remove(owned,owned.getCanonicalPath());
  }
 }
}
