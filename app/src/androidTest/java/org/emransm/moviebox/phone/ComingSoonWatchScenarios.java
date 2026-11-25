package org.emransm.moviebox.phone;

import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

/** Synthetic availability metadata plus owned media; never infers live title availability. */
@RunWith(AndroidJUnit4.class)
public class ComingSoonWatchScenarios {
    static class AvailabilityApi extends SliderPlaybackScenarios.Local {
        final AtomicInteger starts=new AtomicInteger();final boolean proof;final String failure;
        AvailabilityApi(boolean proof,String failure)throws Exception{this.proof=proof;this.failure=failure;}
        @Override public JSONObject detail(JSONObject item)throws Exception{
            JSONObject result=new JSONObject(item.toString()).put("fixtureDetailReady",true).put("hasResource",false);
            if(proof)result.put("resourceDetectors",new JSONArray()).put("playUrl",new JSONObject().put("urlType","PLAY_URL_WEB").put("playUrl",""));
            return result;
        }
        @Override public List<JSONObject> initialPlayback(JSONObject item,int season,int episode,int quality)throws Exception{
            if(starts.incrementAndGet()==1){
                if(failure.equals("network"))throw new SocketTimeoutException("Owned fixture network timeout");
                if(failure.startsWith("auth"))throw new IOException("HTTP "+(failure.equals("auth403")?"403":"401")+": Owned fixture authorization failed");
                throw new Api.NoSourcesException();
            }
            return super.initialPlayback(item,season,episode,quality);
        }
        @Override public List<JSONObject> recommendations(String id)throws Exception{
            return Collections.singletonList(BlockFlowScenarios.title("qa-coming-related",1));
        }
    }
    static boolean has(ActivityScenario<PhoneActivity> scenario,String label){boolean[] found={false};main(scenario,a->found[0]=description((View)field(a,"root"),label)!=null);return found[0];}
    static boolean enriched(ActivityScenario<PhoneActivity> scenario){boolean[] ready={false};main(scenario,a->ready[0]=((JSONObject)field(a,"subject")).optBoolean("fixtureDetailReady"));return ready[0];}
    @Test public void provenUpcomingTitleKeepsAudioAndRelatedThenAvailabilityRecheckPlays()throws Exception{
        AvailabilityApi service=new AvailabilityApi(true,"empty");try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(scenario,service);main(scenario,a->call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},BlockFlowScenarios.title("qa-coming-main",1),true));
            waitFor(()->has(scenario,"Coming soon")&&has(scenario,"Check availability"),6000);
            waitFor(()->has(scenario,"Play recommended Owned block qa-coming-related"),6000);
            main(scenario,a->{View root=(View)field(a,"root");assertNull("Unavailable title must not show misleading Play",description(root,"Play selected video"));assertNotNull(description(root,"Audio"));assertEquals("qa-coming-main",((JSONObject)field(a,"subject")).optString("subjectId"));assertNull(field(a,"player"));});
            BlockFlowScenarios.clickRoot(scenario,"Check availability");
            waitFor(()->playing(scenario)&&service.id.equals("qa-coming-main"),10000);
            assertTrue("Recheck must actually request sources rather than reuse negative metadata",service.starts.get()>=2);
        }finally{service.close();}
    }
    @Test public void unknownAvailabilityMetadataKeepsRetryInsteadOfInventingComingSoon()throws Exception{
        AvailabilityApi service=new AvailabilityApi(false,"empty");try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(scenario,service);main(scenario,a->call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},BlockFlowScenarios.title("qa-coming-unknown",1),true));
            waitFor(()->enriched(scenario)&&has(scenario,"Retry video loading"),6000);
            main(scenario,a->{View root=(View)field(a,"root");assertNull(description(root,"Coming soon"));assertNull(description(root,"Check availability"));assertNotNull(description(root,"Audio"));});
        }finally{service.close();}
    }
    @Test public void transportAndAuthorizationFailuresStayRetryEvenWithNegativeMetadata()throws Exception{
        for(String failure:new String[]{"network","auth","auth403"}){
            AvailabilityApi service=new AvailabilityApi(true,failure);try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
                BlockFlowScenarios.isolate(scenario,service);main(scenario,a->call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},BlockFlowScenarios.title("qa-coming-"+failure,1),true));
                waitFor(()->enriched(scenario)&&has(scenario,"Retry video loading"),6000);
                main(scenario,a->{View root=(View)field(a,"root");assertNull("A connection/auth failure is not Coming soon",description(root,"Coming soon"));assertNull(description(root,"Check availability"));assertNotNull(description(root,"Audio"));});
            }finally{service.close();}
        }
    }
    @Test public void comingSoonBackExitsWithoutStartingPlayback()throws Exception{
        AvailabilityApi service=new AvailabilityApi(true,"empty");try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(scenario,service);main(scenario,a->call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},BlockFlowScenarios.title("qa-coming-back",1),true));
            waitFor(()->has(scenario,"Coming soon")&&has(scenario,"Check availability"),6000);
            main(scenario,a->a.onBackPressed());
            main(scenario,a->{assertNull(field(a,"subject"));assertNull(field(a,"player"));assertNull(description((View)field(a,"root"),"Coming soon"));});
            assertEquals("Back must not trigger a source retry",1,service.starts.get());
        }finally{service.close();}
    }
    @Test public void lateParentNegativeProofCannotRelabelNewAudioEdition()throws Exception{
        java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
        AtomicInteger details=new AtomicInteger();
        SliderPlaybackScenarios.Local service=new SliderPlaybackScenarios.Local(){
            @Override public List<JSONObject> initialPlayback(JSONObject item,int season,int episode,int quality)throws Exception{
                if(item.optString("subjectId").equals("qa-coming-parent"))throw new Api.NoSourcesException();
                throw new IOException("HTTP 403: Owned fixture new audio unavailable");
            }
            @Override public JSONObject detail(JSONObject item)throws Exception{
                if(details.incrementAndGet()==1){entered.countDown();release.await(8,java.util.concurrent.TimeUnit.SECONDS);}
                return new JSONObject(item.toString()).put("hasResource",false).put("resourceDetectors",new JSONArray()).put("playUrl",new JSONObject().put("urlType","PLAY_URL_WEB").put("playUrl",""));
            }
            @Override public List<JSONObject> recommendations(String id){return Collections.emptyList();}
        };
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(scenario,service);
            JSONObject original=BlockFlowScenarios.title("qa-coming-parent",1).put("dubs",new JSONArray().put(new JSONObject().put("type",0).put("subjectId","qa-recovery-dub").put("lanName","French dub").put("lanCode","fr")));
            main(scenario,a->call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},original,true));
            assertTrue("Old edition metadata request is in flight",entered.await(6,java.util.concurrent.TimeUnit.SECONDS));
            RecoveryWatchScenarios.chooseDub(scenario);
            waitFor(()->has(scenario,"Retry video loading"),6000);release.countDown();Thread.sleep(400);
            main(scenario,a->{assertEquals("qa-recovery-dub",((JSONObject)field(a,"currentAudio")).optString("subjectId"));View root=(View)field(a,"root");assertNotNull(description(root,"Retry video loading"));assertNull("Old parent proof cannot classify selected audio",description(root,"Coming soon"));assertNull(description(root,"Check availability"));assertEquals(1,field(a,"watchPreviewState"));});
        }finally{release.countDown();service.close();}
    }

    @Test public void captureComingSoonPortraitAndLandscape()throws Exception{
        AvailabilityApi service=new AvailabilityApi(true,"empty");
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            try{
                BlockFlowScenarios.isolate(scenario,service);
                JSONObject title=BlockFlowScenarios.title("qa-coming-visual",1).put("title","Upcoming sample movie");
                main(scenario,a->call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},title,true));
                waitFor(()->has(scenario,"Coming soon")&&has(scenario,"Check availability"),6000);
                main(scenario,a->{assertEquals(3,field(a,"watchPreviewState"));assertNull(description((View)field(a,"root"),"Play selected video"));});
                BlockFlowScenarios.capture("qa-comingsoon-portrait.png");
                main(scenario,a->a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
                waitFor(()->{boolean[] ready={false};main(scenario,a->ready[0]=a.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE&&description((View)field(a,"root"),"Coming soon")!=null);return ready[0];},6000);
                main(scenario,a->{
                    View root=(View)field(a,"root");assertEquals(3,field(a,"watchPreviewState"));
                    for(String label:new String[]{"Back to browsing","Check availability"}){
                        View action=description(root,label);assertNotNull(label,action);assertTrue(label+" stays shown",action.isShown());assertTrue(label+" stays clickable",action.isClickable());
                        android.graphics.Rect visible=new android.graphics.Rect();assertTrue(label+" is inside visible player",action.getGlobalVisibleRect(visible));assertTrue(label+" retains touch height",visible.height()>=Math.round(47*a.getResources().getDisplayMetrics().density));
                    }
                    assertNull("Coming soon never gains a misleading Play on rotation",description(root,"Play selected video"));
                });
                // Configuration/layout can settle before SurfaceFlinger finishes
                // its rotation transition. Capture the stable screen, not a frame
                // in the operating system's animation.
                Thread.sleep(1000);
                BlockFlowScenarios.capture("qa-comingsoon-landscape.png");
                BlockFlowScenarios.clickRoot(scenario,"Back to browsing");
                waitFor(()->{boolean[] done={false};main(scenario,a->done[0]=field(a,"subject")==null&&a.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT);return done[0];},6000);
                assertEquals("Back did not retry or start playback",1,service.starts.get());
                // Reopen the same synthetic unavailable title and prove the visible
                // landscape recheck is an actual action, not just a painted label.
                service.starts.set(0);main(scenario,a->call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},title,true));
                waitFor(()->has(scenario,"Coming soon"),6000);
                main(scenario,a->a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
                waitFor(()->{boolean[] ready={false};main(scenario,a->ready[0]=a.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE&&description((View)field(a,"root"),"Check availability")!=null);return ready[0];},6000);
                BlockFlowScenarios.clickRoot(scenario,"Check availability");waitFor(()->playing(scenario)&&service.id.equals("qa-coming-visual"),10000);
                main(scenario,a->a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
                waitFor(()->{boolean[] done={false};main(scenario,a->done[0]=a.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT);return done[0];},6000);
            }finally{main(scenario,a->a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));}
        }finally{service.close();}
    }

}
