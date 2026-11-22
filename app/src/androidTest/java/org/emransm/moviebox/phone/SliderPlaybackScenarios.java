package org.emransm.moviebox.phone;

import android.content.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.media3.exoplayer.ExoPlayer;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

@RunWith(AndroidJUnit4.class)
public class SliderPlaybackScenarios {
 static class Local extends BlockFlowScenarios.FakeApi {
  volatile int se=-1,ep=-1;volatile String id="";final String media;
  Local()throws Exception{media=Uri.fromFile(fixture()).toString();}
  @Override public JSONObject detail(JSONObject item)throws Exception{return new JSONObject(item.toString()).put("seasons",new JSONArray().put(new JSONObject().put("se",1).put("allEp","1,2,4")).put(new JSONObject().put("se",2).put("allEp","1,4,7")));}
  @Override public List<JSONObject> initialPlayback(JSONObject item,int season,int episode,int quality)throws Exception{se=season;ep=episode;id=item.optString("subjectId");return Collections.singletonList(new JSONObject().put("resourceId","owned-slider-stream").put("resourceLink",media).put("resolution",1080));}
  @Override public List<JSONObject> playback(JSONObject item,int season,int episode)throws Exception{return initialPlayback(item,season,episode,1080);}
  @Override public List<JSONObject> captions(JSONObject item,JSONObject stream){return Collections.emptyList();}
 }
 @Test public void sliderCardAndArtworkPlayOwnTitleInsteadOfStoppingAtDetails()throws Exception{
  Local service=new Local();try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
   BlockFlowScenarios.isolate(scenario,service);JSONObject title=BlockFlowScenarios.title("qa-slider-movie",1);
   main(scenario,a->{put(a,"route","Discover");call(a,"appShell",new Class[]{String.class},"Discover");call(a,"showTitles",new Class[]{List.class,boolean.class},Collections.singletonList(title),false);});Thread.sleep(200);
   main(scenario,a->{View card=description((View)field(a,"root"),"Play featured title, "+title.optString("title"));assertNotNull(card);assertTrue(card.performClick());});waitFor(()->playing(scenario),10000);assertEquals("qa-slider-movie",service.id);assertEquals(0,service.se);assertEquals(0,service.ep);
   main(scenario,a->{call(a,"releasePlayer",new Class[]{});put(a,"route","Discover");call(a,"appShell",new Class[]{String.class},"Discover");call(a,"showTitles",new Class[]{List.class,boolean.class},Collections.singletonList(title),false);});Thread.sleep(200);
   main(scenario,a->{View art=description((View)field(a,"root"),"Play featured artwork, "+title.optString("title"));assertNotNull(art);assertTrue(art.performClick());});waitFor(()->playing(scenario),10000);assertEquals("qa-slider-movie",service.id);
  }
 }
 @Test public void seriesUsesOwnHistoryAndNewSeriesCannotInheritAnotherTitlesEpisode()throws Exception{
  Local service=new Local();try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
   BlockFlowScenarios.isolate(scenario,service);JSONObject seen=BlockFlowScenarios.title("qa-seen-series",2),fresh=BlockFlowScenarios.title("qa-fresh-series",2);
   main(scenario,a->{((Library)field(a,"library")).progress(seen,2,4,10000,65000,false);put(a,"season",2);put(a,"episode",7);call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},seen,true);});waitFor(()->playing(scenario),10000);assertEquals(2,service.se);assertEquals(4,service.ep);assertTrue(position(scenario)>=10000);
   main(scenario,a->call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},fresh,true));waitFor(()->playing(scenario)&&service.id.equals("qa-fresh-series"),10000);assertEquals("New series begins at its first available season",1,service.se);assertEquals("Never inherits S2E4",1,service.ep);
  }
 }
 @Test public void cachedAutomaticSubtitleIsAttachedBeforePrepareAndNotReloadedAgain()throws Exception{
  Local service=new Local();try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
   BlockFlowScenarios.isolate(scenario,service);JSONObject movie=BlockFlowScenarios.title("qa-caption-reuse",1);File[] caption={null};
   main(scenario,a->{Library library=(Library)field(a,"library");library.settings().put("subtitles",true).put("subtitleLanguage","en");File directory=new File(a.getFilesDir(),"subtitles");assertTrue(directory.isDirectory()||directory.mkdirs());caption[0]=new File(directory,Api.md5("qa-caption-reuse:0:0:owned-slider-stream:en".getBytes(java.nio.charset.StandardCharsets.UTF_8))+".srt");try(FileOutputStream out=new FileOutputStream(caption[0])){out.write("1\n00:00:00,000 --> 00:00:20,000\nOwned caption reuse\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));}call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},movie,true);});waitFor(()->playing(scenario),10000);
   final Object[] before={null};main(scenario,a->{ExoPlayer player=(ExoPlayer)field(a,"player");before[0]=player.getCurrentMediaItem();assertEquals(Uri.fromFile(caption[0]),player.getCurrentMediaItem().localConfiguration.subtitleConfigurations.get(0).uri);call(a,"useCaption",new Class[]{JSONObject.class},new JSONObject().put("lan","en").put("url","https://invalid.test/already-cached.srt"));});Thread.sleep(300);
   main(scenario,a->assertSame("Subtitle reuse must not replace/reprepare the playing media item",before[0],((ExoPlayer)field(a,"player")).getCurrentMediaItem()));assertTrue(playing(scenario));
  }
 }
 @Test public void landscapeControlsUseOneActionRowAndLeaveThePictureVisible()throws Exception{
  Local service=new Local();try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
   BlockFlowScenarios.isolate(scenario,service);JSONObject movie=BlockFlowScenarios.title("qa-landscape-controls",1);
   main(scenario,a->call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},movie,true));waitFor(()->playing(scenario),10000);
   main(scenario,a->a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
   waitFor(()->{boolean[] yes={false};main(scenario,a->yes[0]=a.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE);return yes[0];},5000);
   main(scenario,a->call(a,"showControls",new Class[]{boolean.class},true));Thread.sleep(250);
   main(scenario,a->{float density=a.getResources().getDisplayMetrics().density;LinearLayout actions=(LinearLayout)field(a,"playerActionRow");View controls=(View)field(a,"controls"),top=(View)field(a,"playerTopbar"),root=(View)field(a,"playerRoot");assertEquals(LinearLayout.HORIZONTAL,actions.getOrientation());assertTrue("Bottom controls stay below 108dp",controls.getHeight()/density<=108);assertTrue("Top bar stays below 52dp",top.getHeight()/density<=52);assertTrue("Most of the picture remains clear",controls.getHeight()+top.getHeight()<root.getHeight()*.5f);for(String name:new String[]{"Quality","Audio","Subtitles","Play or pause"}){View button=description(root,name);assertNotNull(name,button);assertTrue(name+" remains a 48dp target",button.getHeight()/density>=47.5);}});
   BlockFlowScenarios.capture("qa-landscape-controls-compact.png");
  }
 }
}
