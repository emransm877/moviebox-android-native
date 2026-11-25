package org.emransm.moviebox.phone;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import android.net.Uri;
import android.view.View;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

@RunWith(AndroidJUnit4.class)
public class PlaybackRecoveryScenarios {
    static class Fresh extends SliderPlaybackScenarios.Local {
        final AtomicInteger refreshes=new AtomicInteger();volatile int quality;volatile boolean fail,empty;
        volatile String edition;CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(0);
        Fresh()throws Exception{}
        @Override public List<JSONObject> freshPlayback(JSONObject title,int se,int ep,int q)throws Exception{
            refreshes.incrementAndGet();edition=title.optString("subjectId");season=se;episode=ep;quality=q;entered.countDown();assertTrue(release.await(8,TimeUnit.SECONDS));
            if(fail)throw new IOException("Owned lookup failure");if(empty)return Collections.emptyList();
            return Collections.singletonList(new JSONObject().put("resourceId","new-resource").put("resourceLink",Uri.parse(media).buildUpon().fragment("fresh").build().toString()).put("resolution",q));
        }
        int season,episode;
    }
    @Test public void refreshedPlaybackKeepsPositionPauseCaptionOffAndAudioEditionChoices()throws Exception{
        for(boolean paused:new boolean[]{false,true}){
            Fresh api=new Fresh();try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
                BlockFlowScenarios.isolate(s,api);JSONObject title=BlockFlowScenarios.title("recovery-title",2);
                main(s,a->call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},title,true));waitFor(()->playing(s),10000);
                Object[] same={null};Uri[] subtitle={null};main(s,a->{
                    File caption=new File(a.getCacheDir(),"owned-recovery.srt");try(FileOutputStream out=new FileOutputStream(caption)){out.write("1\n00:00:00,000 --> 00:00:30,000\nOwned recovery caption\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));}
                    call(a,"startStream",new Class[]{long.class,boolean.class,File.class},10000L,!paused,caption);ExoPlayer player=(ExoPlayer)field(a,"player");same[0]=player;subtitle[0]=player.getCurrentMediaItem().localConfiguration.subtitleConfigurations.get(0).uri;
                    player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setPreferredAudioLanguage("fr").setTrackTypeDisabled(C.TRACK_TYPE_TEXT,paused).build());player.setVolume(.43f);
                    put(a,"currentAudio",new JSONObject(title.toString()).put("subjectId","selected-dub"));call(a,"retryPlaybackSource",new Class[]{});
                });waitFor(()->api.refreshes.get()==1,3000);waitFor(()->{boolean[] ready={false};main(s,a->ready[0]=!(Boolean)field(a,"recoveringSource"));return ready[0];},4000);Thread.sleep(300);
                main(s,a->{ExoPlayer player=(ExoPlayer)field(a,"player");assertSame(same[0],player);assertEquals(!paused,player.getPlayWhenReady());assertTrue(player.getCurrentPosition()>=10000);assertEquals("fresh",player.getCurrentMediaItem().localConfiguration.uri.getFragment());assertEquals(subtitle[0],player.getCurrentMediaItem().localConfiguration.subtitleConfigurations.get(0).uri);assertEquals(paused,player.getTrackSelectionParameters().disabledTrackTypes.contains(C.TRACK_TYPE_TEXT));assertTrue(player.getTrackSelectionParameters().preferredAudioLanguages.contains("fr"));assertEquals(.43f,player.getVolume(),.001f);});
                assertEquals("selected-dub",api.edition);assertEquals(1,api.season);assertEquals(1,api.episode);assertEquals(1080,api.quality);
            }
        }
    }
    @Test public void duplicateRetryCoalescesAndBackCancelsDelayedRecovery()throws Exception{
        Fresh api=new Fresh();api.release=new CountDownLatch(1);
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(s,api);main(s,a->call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},BlockFlowScenarios.title("delayed-retry",1),true));waitFor(()->playing(s),10000);
            main(s,a->{call(a,"retryPlaybackSource",new Class[]{});call(a,"retryPlaybackSource",new Class[]{});});assertTrue(api.entered.await(2,TimeUnit.SECONDS));assertEquals(1,api.refreshes.get());
            main(s,a->call(a,"leavePlayer",new Class[]{}));api.release.countDown();Thread.sleep(300);main(s,a->{assertNull(field(a,"player"));assertNull("Back returns to browsing, without a details card",field(a,"subject"));assertNotNull(field(a,"content"));});
        }finally{api.release.countDown();api.close();}
    }
    @Test public void failedAndEmptyRecoveryRemainNavigableWithoutReplacingCurrentItem()throws Exception{
        for(boolean empty:new boolean[]{false,true}){
            Fresh api=new Fresh();api.empty=empty;api.fail=!empty;
            try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
                BlockFlowScenarios.isolate(s,api);main(s,a->call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},BlockFlowScenarios.title("failed-retry",1),true));waitFor(()->playing(s),10000);
                Object[] before={null};main(s,a->{before[0]=((ExoPlayer)field(a,"player")).getCurrentMediaItem();call(a,"retryPlaybackSource",new Class[]{});});assertTrue(api.entered.await(2,TimeUnit.SECONDS));waitFor(()->{boolean[] done={false};main(s,a->done[0]=!(Boolean)field(a,"recoveringSource"));return done[0];},4000);
                main(s,a->{assertSame(before[0],((ExoPlayer)field(a,"player")).getCurrentMediaItem());assertTrue((Boolean)field(a,"visibleControls"));assertNotNull(description((View)field(a,"root"),"Back to browsing"));call(a,"leavePlayer",new Class[]{});assertNull(field(a,"player"));});
            }
        }
    }
    @Test public void userPauseDuringLookupAndNewQualityCancelAreRespected()throws Exception{
        Fresh api=new Fresh();api.release=new CountDownLatch(1);
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(s,api);main(s,a->call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},BlockFlowScenarios.title("intent-retry",1),true));waitFor(()->playing(s),10000);
            main(s,a->call(a,"retryPlaybackSource",new Class[]{}));assertTrue(api.entered.await(2,TimeUnit.SECONDS));main(s,a->((ExoPlayer)field(a,"player")).pause());api.release.countDown();Thread.sleep(300);main(s,a->assertFalse(((ExoPlayer)field(a,"player")).getPlayWhenReady()));
            api.entered=new CountDownLatch(1);api.release=new CountDownLatch(1);main(s,a->{put(a,"lastRecoveryAt",0L);call(a,"retryPlaybackSource",new Class[]{});});assertTrue(api.entered.await(2,TimeUnit.SECONDS));Object[] replacement={null};
            main(s,a->{call(a,"startStream",new Class[]{long.class,boolean.class,File.class},5000L,false,null);replacement[0]=((ExoPlayer)field(a,"player")).getCurrentMediaItem();});api.release.countDown();Thread.sleep(300);main(s,a->assertSame(replacement[0],((ExoPlayer)field(a,"player")).getCurrentMediaItem()));
        }finally{api.release.countDown();api.close();}
    }
    static class ResourceApi extends Api {
        AtomicInteger requests=new AtomicInteger();CountDownLatch oldEntered=new CountDownLatch(1),oldFree=new CountDownLatch(1);boolean forbidden;
        ResourceApi()throws Exception{super("0123456789abcdef0123456789abcdef");}
        @Override public void warm(){}
        @Override public Object request(String path,Map<String,String> args,JSONObject body,boolean nativeApi)throws Exception{
            int number=requests.incrementAndGet();if(number==1&&!forbidden){oldEntered.countDown();assertTrue(oldFree.await(6,TimeUnit.SECONDS));}
            JSONObject row=new JSONObject().put("resourceLink","https://owned.invalid/video-"+number+".mp4").put("resourceId","r"+number).put("resolution",1080).put("se",2).put("ep",forbidden?8:4).put("size",20000000).put("duration",65000).put("requireMemberType",forbidden?1:0);
            return new JSONObject().put("list",new JSONArray().put(row));
        }
    }
    @Test public void pendingManualSubtitleSurvivesRecoveryInEitherOrderAndOffWins()throws Exception{
        for(int order:new int[]{0,1,2}){
            CountDownLatch captionEntered=new CountDownLatch(1),captionFree=new CountDownLatch(1);
            Fresh api=new Fresh(){@Override public byte[] captionBytes(String url,int maximum)throws Exception{captionEntered.countDown();assertTrue(captionFree.await(8,TimeUnit.SECONDS));return "1\n00:00:00,000 --> 00:00:40,000\nOwned new subtitle\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);}};api.release=new CountDownLatch(1);
            try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
                BlockFlowScenarios.isolate(s,api);String fixtureId="caption-race-"+order+"-"+System.nanoTime();main(s,a->call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},BlockFlowScenarios.title(fixtureId,1),true));waitFor(()->playing(s),10000);
                main(s,a->{call(a,"retryPlaybackSource",new Class[]{});call(a,"useCaption",new Class[]{JSONObject.class},new JSONObject().put("lan","fr").put("url","https://owned.invalid/fr.srt"));});assertTrue(api.entered.await(2,TimeUnit.SECONDS));assertTrue(captionEntered.await(2,TimeUnit.SECONDS));
                if(order==2){main(s,a->call(a,"subtitleDialog",new Class[]{}));BlockFlowScenarios.click("Off");}
                if(order==1){captionFree.countDown();Thread.sleep(250);api.release.countDown();}else{api.release.countDown();Thread.sleep(250);captionFree.countDown();}
                waitFor(()->{boolean[] done={false};main(s,a->done[0]=!(Boolean)field(a,"recoveringSource"));return done[0];},3000);Thread.sleep(350);
                main(s,a->{ExoPlayer p=(ExoPlayer)field(a,"player");assertEquals("fresh",p.getCurrentMediaItem().localConfiguration.uri.getFragment());if(order==2){assertTrue(p.getTrackSelectionParameters().disabledTrackTypes.contains(C.TRACK_TYPE_TEXT));assertFalse(((Library)field(a,"library")).settings().optBoolean("subtitles"));}else{assertEquals("fr",p.getCurrentMediaItem().localConfiguration.subtitleConfigurations.get(0).language);assertTrue(((Library)field(a,"library")).settings().optBoolean("subtitles"));}});
            }finally{captionFree.countDown();api.release.countDown();api.close();}
        }
    }
    @Test public void recoveryDoesNotProbeUnboundedAdvertisedQualities()throws Exception{
        AtomicInteger count=new AtomicInteger();Api api=new Api("0123456789abcdef0123456789abcdef"){
            @Override public void warm(){}
            @Override public Object request(String path,Map<String,String> args,JSONObject body,boolean nativeApi)throws Exception{count.incrementAndGet();JSONArray advertised=new JSONArray();for(int q=1;q<=2160;q++)advertised.put(new JSONObject().put("resolution",q));return new JSONObject().put("list",new JSONArray()).put("collectionResolutions",advertised);}
        };
        try{api.freshPlayback(BlockFlowScenarios.title("bounded",2),2,4,1080);fail("No source should fail boundedly");}catch(IOException expected){assertEquals(3,count.get());}finally{api.close();}
    }
    @Test public void busyWorkerQueueDoesNotLeaveRetryPermanentlyBusy()throws Exception{
        Fresh api=new Fresh();CountDownLatch free=new CountDownLatch(1);
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(s,api);main(s,a->call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},BlockFlowScenarios.title("queue-retry",1),true));waitFor(()->playing(s),10000);Thread.sleep(100);
            ThreadPoolExecutor[] executor={null};main(s,a->executor[0]=(ThreadPoolExecutor)field(a,"network"));CountDownLatch occupied=new CountDownLatch(executor[0].getMaximumPoolSize());
            for(int i=0;i<executor[0].getMaximumPoolSize();i++)executor[0].execute(()->{occupied.countDown();try{free.await(6,TimeUnit.SECONDS);}catch(InterruptedException ignored){}});assertTrue(occupied.await(2,TimeUnit.SECONDS));
            main(s,a->{while(executor[0].getQueue().remainingCapacity()>0)executor[0].execute(()->{});call(a,"retryPlaybackSource",new Class[]{});assertFalse((Boolean)field(a,"recoveringSource"));assertNotNull(field(a,"player"));assertEquals(0,api.refreshes.get());});
        }finally{free.countDown();api.close();}
    }
    @Test public void freshEpochCannotConsumeOldInflightPrefetchOrBypassResourcePolicies()throws Exception{
        ResourceApi api=new ResourceApi();ExecutorService jobs=Executors.newFixedThreadPool(2);JSONObject title=BlockFlowScenarios.title("epoch-title",2);
        try{
            Future<?> old=jobs.submit(()->{api.prefetchPlayback(title,2,4,1080);return null;});assertTrue(api.oldEntered.await(2,TimeUnit.SECONDS));
            List<JSONObject> fresh=jobs.submit(()->api.freshPlayback(title,2,4,1080)).get(3,TimeUnit.SECONDS);assertEquals("r2",fresh.get(0).optString("resourceId"));api.oldFree.countDown();old.get(3,TimeUnit.SECONDS);assertEquals("r2",api.initialPlayback(title,2,4,1080).get(0).optString("resourceId"));assertEquals(2,api.requests.get());
            api.forbidden=true;try{api.freshPlayback(title,2,4,1080);fail("Locked/wrong-episode source must be rejected");}catch(IOException expected){}
        }finally{api.oldFree.countDown();jobs.shutdownNow();api.close();}
    }
}
