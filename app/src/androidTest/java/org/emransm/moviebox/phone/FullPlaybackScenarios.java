package org.emransm.moviebox.phone;

import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.lifecycle.Lifecycle;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import org.junit.After;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

@RunWith(AndroidJUnit4.class)
public class FullPlaybackScenarios {
    static final class LocalApi extends Api {
        final String location;final AtomicInteger starts=new AtomicInteger();volatile int requestedSeason=-1,requestedEpisode=-1;
        LocalApi(String location)throws Exception{super("0123456789abcdef0123456789abcdef");this.location=location;}
        public List<JSONObject> initialPlayback(JSONObject subject,int season,int episode,int preferred)throws Exception{starts.incrementAndGet();requestedSeason=season;requestedEpisode=episode;return playback(subject,season,episode);}
        public List<JSONObject> playback(JSONObject subject,int season,int episode)throws Exception{return Collections.singletonList(new JSONObject().put("resourceLink",location).put("resolution",1080).put("resourceId","generated-fixture").put("se",season).put("ep",episode));}
        public List<JSONObject> captions(JSONObject subject,JSONObject stream){return Collections.emptyList();}
    }
    @After public void cleanupOwnedHistory()throws Exception{Library store=new Library(InstrumentationRegistry.getInstrumentation().getTargetContext());store.removeTitles("history",Collections.singleton("generated-native-phone-fixture"));store.close();}
    static void click(String text)throws Exception{assertTrue("Clickable sheet label missing: "+text,clickIfPresent(text));}
    static boolean clickIfPresent(String text)throws Exception{
        long deadline=System.currentTimeMillis()+2000;
        do{InstrumentationRegistry.getInstrumentation().waitForIdleSync();AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();if(root!=null)for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text)){if(!text.contentEquals(node.getText()==null?"":node.getText()))continue;for(int n=0;n<4&&node!=null;n++,node=node.getParent())if(node.isClickable()){assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK));return true;}}Thread.sleep(100);}while(System.currentTimeMillis()<deadline);return false;
    }
    static void end(ActivityScenario<PhoneActivity> scenario){main(scenario,a->{ExoPlayer p=(ExoPlayer)field(a,"player");p.seekTo(p.getDuration()-300);p.play();});}
    @Test public void autoNextCrossesSparseSeasonsThenTerminalReturnsDetails()throws Exception{
        String local=Uri.fromFile(fixture()).toString();LocalApi api=new LocalApi(local);
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            start(scenario,local);waitFor(()->playing(scenario),15000);
            main(scenario,a->{JSONObject series=movie().put("subjectType",2).put("seasons",new JSONArray().put(new JSONObject().put("se",1).put("allEp","2")).put(new JSONObject().put("se",3).put("allEp","4")));put(a,"api",api);put(a,"subject",series);put(a,"currentAudio",series);put(a,"season",1);put(a,"episode",2);call(a,"readSeasons",new Class[]{});});
            end(scenario);waitFor(()->api.starts.get()==1&&api.requestedSeason==3&&api.requestedEpisode==4,10000);waitFor(()->playing(scenario),10000);
            main(scenario,a->{assertEquals(3,field(a,"season"));assertEquals(4,field(a,"episode"));assertNull(call(a,"nextEpisode",new Class[]{}));});
            end(scenario);waitFor(()->{boolean[] value={false};main(scenario,a->value[0]=field(a,"player")==null);return value[0];},10000);
            main(scenario,a->assertNotNull(description((View)field(a,"root"),"Watch or resume")));assertEquals("Terminal episode must not request another stream",1,api.starts.get());
        }
    }
    @Test public void failedLocalSourceCanRetryAfterFileBecomesAvailable()throws Exception{
        File source=fixture(),retry=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir(),"qa-full-retry.mp4");retry.delete();
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            start(scenario,Uri.fromFile(retry).toString());waitFor(()->{boolean[] failed={false};main(scenario,a->failed[0]=((ExoPlayer)field(a,"player")).getPlayerError()!=null);return failed[0];},10000);
            try(InputStream in=new FileInputStream(source);OutputStream out=new FileOutputStream(retry)){byte[] bytes=new byte[32768];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);}
            main(scenario,a->call(a,"playbackOptions",new Class[]{}));click("Retry source");waitFor(()->playing(scenario),15000);main(scenario,a->assertNull(((ExoPlayer)field(a,"player")).getPlayerError()));
        }finally{retry.delete();}
    }
    @Test public void backgroundForegroundReturnsDetailsAndWatchResumesPosition()throws Exception{
        String local=Uri.fromFile(fixture()).toString();LocalApi api=new LocalApi(local);
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            start(scenario,local);waitFor(()->playing(scenario),15000);main(scenario,a->{put(a,"api",api);((ExoPlayer)field(a,"player")).seekTo(18000);});waitFor(()->position(scenario)>=17500,5000);
            scenario.moveToState(Lifecycle.State.CREATED);scenario.moveToState(Lifecycle.State.RESUMED);
            main(scenario,a->{assertNull(field(a,"player"));View watch=description((View)field(a,"root"),"Watch or resume");assertNotNull(watch);assertTrue(watch.performClick());});waitFor(()->playing(scenario)&&position(scenario)>=17000,15000);assertEquals(1,api.starts.get());
        }
    }
    @Test public void controlsAutoHideWhilePlayingButStayVisibleWhenPaused()throws Exception{
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            start(scenario,Uri.fromFile(fixture()).toString());waitFor(()->playing(scenario),15000);main(scenario,a->call(a,"showControls",new Class[]{boolean.class},false));
            waitFor(()->{boolean[] hidden={false};main(scenario,a->hidden[0]=!((Boolean)field(a,"visibleControls")));return hidden[0];},6500);
            main(scenario,a->call(a,"togglePlayback",new Class[]{}));Thread.sleep(4300);main(scenario,a->{assertFalse(((ExoPlayer)field(a,"player")).isPlaying());assertTrue((Boolean)field(a,"visibleControls"));assertEquals(View.VISIBLE,((View)field(a,"controls")).getVisibility());});
        }
    }
    @Test public void audioEditionSheetCannotSwitchAReplacementPlayer()throws Exception{
        String local=Uri.fromFile(fixture()).toString();LocalApi api=new LocalApi(local);
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            start(scenario,local);waitFor(()->playing(scenario),15000);
            main(scenario,a->{put(a,"api",api);((JSONObject)field(a,"subject")).put("dubs",new JSONArray().put(new JSONObject().put("type",0).put("subjectId","qa-stale-dub").put("lanName","QA stale audio")));call(a,"audioDialog",new Class[]{});call(a,"startStream",new Class[]{long.class,boolean.class,File.class},0L,true,null);});
            clickIfPresent("QA stale audio ? separate edition");Thread.sleep(400);assertEquals("An audio sheet belonging to old player must not load its edition in a replacement player",0,api.starts.get());
        }
    }
    @Test public void playbackOptionsCannotResizeAReplacementPlayer()throws Exception{
        String local=Uri.fromFile(fixture()).toString();
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            start(scenario,local);waitFor(()->playing(scenario),15000);main(scenario,a->{call(a,"playbackOptions",new Class[]{});call(a,"startStream",new Class[]{long.class,boolean.class,File.class},0L,true,null);});clickIfPresent("Fill screen");main(scenario,a->assertEquals("Old popup must not resize the new player",AspectRatioFrameLayout.RESIZE_MODE_FIT,((PlayerView)field(a,"video")).getResizeMode()));
        }
    }
    @Test public void cancelledHoldRestoresPlaybackWithoutSeeking()throws Exception{
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            start(scenario,Uri.fromFile(fixture()).toString());waitFor(()->playing(scenario),15000);main(scenario,a->{((ExoPlayer)field(a,"player")).seekTo(15000);});waitFor(()->position(scenario)>=14500,5000);main(scenario,a->call(a,"beginHold",new Class[]{int.class},1));long before=position(scenario);Thread.sleep(1000);main(scenario,a->call(a,"cancelHold",new Class[]{boolean.class},false));waitFor(()->playing(scenario),3000);assertTrue("Canceled hold must keep original decoded position",Math.abs(position(scenario)-before)<2000);main(scenario,a->{assertFalse((Boolean)field(a,"holdPreview"));assertEquals(-1L,field(a,"pendingSeek"));});
        }
    }
}
