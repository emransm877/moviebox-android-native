package org.emransm.moviebox.phone;

import android.app.Dialog;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

/** Owned media and synthetic API metadata; no claims of live-provider availability. */
@RunWith(AndroidJUnit4.class)
public class RecoveryWatchScenarios {
    static class RecoveryApi extends SliderPlaybackScenarios.Local {
        final AtomicInteger parentStarts=new AtomicInteger(),dubStarts=new AtomicInteger();
        final CountDownLatch sourceGate=new CountDownLatch(1);
        volatile boolean holdSource=false;
        RecoveryApi()throws Exception{}
        @Override public JSONObject detail(JSONObject item)throws Exception{
            return new JSONObject(item.toString()).put("dubs",new JSONArray().put(new JSONObject().put("type",0).put("subjectId","qa-recovery-dub").put("lanName","French dub").put("lanCode","fr")));
        }
        @Override public List<JSONObject> initialPlayback(JSONObject item,int season,int episode,int quality)throws Exception{
            String selected=item.optString("subjectId");
            if(selected.equals("qa-recovery-parent")){
                parentStarts.incrementAndGet();if(holdSource)sourceGate.await(8,TimeUnit.SECONDS);
                throw new IOException("Owned fixture: no source for parent edition");
            }
            if(selected.equals("qa-recovery-dub")&&dubStarts.incrementAndGet()==1)throw new IOException("Owned fixture: first dub attempt fails");
            return super.initialPlayback(item,season,episode,quality);
        }
        @Override public List<JSONObject> recommendations(String id)throws Exception{
            return id.equals("qa-recovery-parent")?Collections.singletonList(BlockFlowScenarios.title("qa-recovery-related",1)):Collections.emptyList();
        }
    }
    static void bounded(PhoneActivity activity,Action action)throws Exception{
        CountDownLatch done=new CountDownLatch(1);AtomicReference<Throwable> failure=new AtomicReference<>();
        new Handler(Looper.getMainLooper()).post(()->{try{action.run(activity);}catch(Throwable error){failure.set(error);}finally{done.countDown();}});
        assertTrue("Watch UI responds while metadata/source is pending",done.await(5,TimeUnit.SECONDS));
        if(failure.get()!=null)throw new AssertionError(failure.get());
    }
    static boolean has(ActivityScenario<PhoneActivity> scenario,String description){boolean[] yes={false};main(scenario,a->yes[0]=description((View)field(a,"root"),description)!=null);return yes[0];}
    static void chooseDub(ActivityScenario<PhoneActivity> scenario){
        main(scenario,a->{call(a,"audioDialog",new Class[]{});Map<Dialog,Runnable> sheets=(Map<Dialog,Runnable>)field(a,"openSheets");String label=AudioLabels.editionName("French dub","fr",1,Locale.getDefault());View choice=null;for(Dialog sheet:sheets.keySet()){choice=description(sheet.getWindow().getDecorView(),label);if(choice!=null)break;}assertNotNull("Only verified metadata supplies the dub option",choice);assertTrue(choice.performClick());});
    }
    @Test public void pendingAndFailedWatchKeepAudioAndRetrySelectedVerifiedEdition()throws Exception{
        RecoveryApi service=new RecoveryApi();service.holdSource=true;
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(scenario,service);PhoneActivity[] owner={null};
            main(scenario,a->{owner[0]=a;call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},BlockFlowScenarios.title("qa-recovery-parent",1),true);});
            bounded(owner[0],a->{View root=(View)field(a,"root");assertNotNull(description(root,"Audio"));assertNotNull(description(root,"Loading video"));assertNull(description(root,"Quality"));});
            service.sourceGate.countDown();
            waitFor(()->has(scenario,"Retry video loading"),6000);
            waitFor(()->{boolean[] ready={false};main(scenario,a->ready[0]=((JSONObject)field(a,"subject")).optJSONArray("dubs")!=null);return ready[0];},6000);
            chooseDub(scenario);waitFor(()->service.dubStarts.get()==1&&has(scenario,"Retry video loading"),6000);
            main(scenario,a->{assertEquals("qa-recovery-parent",((JSONObject)field(a,"subject")).optString("subjectId"));assertEquals("qa-recovery-dub",((JSONObject)field(a,"currentAudio")).optString("subjectId"));assertEquals(1,field(a,"watchPreviewState"));});
            BlockFlowScenarios.clickRoot(scenario,"Retry video loading");
            waitFor(()->playing(scenario)&&service.id.equals("qa-recovery-dub"),10000);
            assertEquals("Retry must not silently return to the failed parent edition",1,service.parentStarts.get());assertTrue(service.dubStarts.get()>=2);
        }finally{service.sourceGate.countDown();service.close();}
    }
    @Test public void failedTitleShowsRelatedTitlesWithoutHidingRetryOrAutoReplacingMovie()throws Exception{
        RecoveryApi service=new RecoveryApi();try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(scenario,service);main(scenario,a->call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},BlockFlowScenarios.title("qa-recovery-parent",1),true));
            waitFor(()->has(scenario,"Play recommended Owned block qa-recovery-related"),6000);
            main(scenario,a->{View root=(View)field(a,"root");assertNotNull(description(root,"Retry video loading"));assertNotNull(description(root,"Audio"));assertNull(field(a,"player"));assertEquals("qa-recovery-parent",((JSONObject)field(a,"subject")).optString("subjectId"));assertEquals(1,field(a,"watchPreviewState"));});
            BlockFlowScenarios.clickRoot(scenario,"Play recommended Owned block qa-recovery-related");waitFor(()->playing(scenario)&&service.id.equals("qa-recovery-related"),10000);
        }finally{service.close();}
    }
    @Test public void oldFailedTitlesDetailAndRecommendationsCannotOverwriteNewTitle()throws Exception{
        CountDownLatch started=new CountDownLatch(2),release=new CountDownLatch(1);
        RecoveryApi service=new RecoveryApi(){
            @Override public JSONObject detail(JSONObject item)throws Exception{if(item.optString("subjectId").equals("qa-recovery-parent")){started.countDown();release.await(8,TimeUnit.SECONDS);}return super.detail(item);}
            @Override public List<JSONObject> recommendations(String id)throws Exception{if(id.equals("qa-recovery-parent")){started.countDown();release.await(8,TimeUnit.SECONDS);}return super.recommendations(id);}
        };
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(scenario,service);main(scenario,a->call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},BlockFlowScenarios.title("qa-recovery-parent",1),true));
            assertTrue("Both optional requests started after failure",started.await(6,TimeUnit.SECONDS));
            main(scenario,a->call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},BlockFlowScenarios.title("qa-recovery-new",1),true));waitFor(()->playing(scenario)&&service.id.equals("qa-recovery-new"),10000);
            release.countDown();Thread.sleep(400);
            main(scenario,a->{assertEquals("qa-recovery-new",((JSONObject)field(a,"subject")).optString("subjectId"));assertEquals("qa-recovery-new",((JSONObject)field(a,"currentAudio")).optString("subjectId"));assertNull(description((View)field(a,"root"),"Play recommended Owned block qa-recovery-related"));});
        }finally{release.countDown();service.close();}
    }
    @Test public void episodeAfterFailedVerifiedDubKeepsDubIdentity()throws Exception{
        RecoveryApi service=new RecoveryApi(){
            @Override public JSONObject detail(JSONObject item)throws Exception{
                return super.detail(item).put("seasons",new JSONArray().put(new JSONObject().put("se",1).put("allEp","1,2")));
            }
        };
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(scenario,service);main(scenario,a->call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},BlockFlowScenarios.title("qa-recovery-parent",2),true));
            waitFor(()->has(scenario,"Retry video loading")&&has(scenario,"Play season 1 episode 2"),6000);
            chooseDub(scenario);waitFor(()->service.dubStarts.get()==1&&has(scenario,"Retry video loading"),6000);
            BlockFlowScenarios.clickRoot(scenario,"Play season 1 episode 2");
            waitFor(()->playing(scenario)&&service.id.equals("qa-recovery-dub"),10000);
            assertEquals("Episode choice preserves explicitly selected dub",2,service.ep);assertEquals(1,service.parentStarts.get());
            main(scenario,a->assertEquals("qa-recovery-dub",((JSONObject)field(a,"currentAudio")).optString("subjectId")));
        }finally{service.close();}
    }

}
