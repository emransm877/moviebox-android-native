package org.emransm.moviebox.phone;

import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.*;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

/** Controlled delayed metadata proves actions do not wait on unrelated work. */
@RunWith(AndroidJUnit4.class)
public class DetailFlowLoadingScenarios {
    static class Delayed extends SliderPlaybackScenarios.Local {
        JSONObject display;
        @Override public JSONObject cachedDisplayDetail(String id){return display!=null&&id.equals(display.optString("subjectId"))?display:null;}
        final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        Delayed()throws Exception{}
        @Override public JSONObject detail(JSONObject item)throws Exception{entered.countDown();assertTrue(release.await(10,TimeUnit.SECONDS));return super.detail(item);}
    }
    @Test public void movieWatchStartsWhileSynopsisIsStillBlocked()throws Exception{
        Delayed service=new Delayed();try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(s,service);
            main(s,a->call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},BlockFlowScenarios.title("qa-loading-movie",1),false));
            assertTrue(service.entered.await(2,TimeUnit.SECONDS));
            main(s,a->{View watch=description((View)field(a,"root"),"Watch or resume");assertNotNull(watch);assertTrue("Movie Watch is usable before synopsis returns",watch.isEnabled());assertTrue(watch.performClick());});
            waitFor(()->playing(s),8000);assertEquals("qa-loading-movie",service.id);service.release.countDown();Thread.sleep(150);assertTrue(playing(s));
        }finally{service.release.countDown();service.close();}
    }
    @Test public void coldSeriesKeepsEpisodeSelectionDisabledUntilVerifiedDataArrives()throws Exception{
        Delayed service=new Delayed();try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(s,service);
            main(s,a->call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},BlockFlowScenarios.title("qa-loading-series",2),false));
            assertTrue(service.entered.await(2,TimeUnit.SECONDS));main(s,a->assertFalse(description((View)field(a,"root"),"Watch or resume").isEnabled()));
            service.release.countDown();waitFor(()->{boolean[] yes={false};main(s,a->yes[0]=description((View)field(a,"root"),"Play episode 4")!=null);return yes[0];},4000);
        }finally{service.release.countDown();service.close();}
    }
    @Test public void recentSavedSeriesIsUsableWhileFreshMetadataIsBlocked()throws Exception{
        Delayed service=new Delayed();service.display=BlockFlowScenarios.title("qa-saved-series",2).put("seasons",new org.json.JSONArray().put(new JSONObject().put("se",2).put("allEp","4,7")));
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(s,service);main(s,a->call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},BlockFlowScenarios.title("qa-saved-series",2),false));
            assertTrue(service.entered.await(2,TimeUnit.SECONDS));main(s,a->{assertTrue(description((View)field(a,"root"),"Watch or resume").isEnabled());assertNotNull(description((View)field(a,"root"),"Play episode 4, selected"));assertEquals("",((TextView)field(a,"status")).getText().toString());});
        }finally{service.release.countDown();service.close();}
    }
    @Test public void backgroundAndResumeDuringColdSeriesLoadDoesNotEnableUnverifiedEpisodes()throws Exception{
        Delayed service=new Delayed();try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(s,service);main(s,a->call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},BlockFlowScenarios.title("qa-resume-series",2),false));
            assertTrue(service.entered.await(2,TimeUnit.SECONDS));s.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);s.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
            main(s,a->{assertFalse(description((View)field(a,"root"),"Watch or resume").isEnabled());View saved=description((View)field(a,"root"),"Toggle saved title");assertTrue(saved.performClick());assertFalse("Saving a loading title must not enable an invented episode",description((View)field(a,"root"),"Watch or resume").isEnabled());});
            service.release.countDown();waitFor(()->{boolean[] yes={false};main(s,a->yes[0]=description((View)field(a,"root"),"Play episode 4")!=null);return yes[0];},4000);
        }finally{service.release.countDown();service.close();}
    }
    @Test public void detailRequestIsNotQueuedBehindBusyCatalogueWorkers()throws Exception{
        Delayed service=new Delayed();CountDownLatch free=new CountDownLatch(1);ThreadPoolExecutor[] pool={null};
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(s,service);
            main(s,a->pool[0]=(ThreadPoolExecutor)field(a,"network"));
            int workers=pool[0].getMaximumPoolSize();CountDownLatch occupied=new CountDownLatch(workers);
            // Occupy every configured worker, including the two-worker low-memory policy.
            // Startup requests can still be winding down; setup time is not the detail-start deadline.
            main(s,a->{for(int n=0;n<workers;n++)pool[0].execute(()->{occupied.countDown();try{free.await();}catch(InterruptedException ignored){Thread.currentThread().interrupt();}});});
            boolean ready=occupied.await(20,TimeUnit.SECONDS);
            assertTrue("Fixture occupancy: workers="+workers+", active="+pool[0].getActiveCount()+", queued="+pool[0].getQueue().size()+", remaining="+occupied.getCount(),ready);main(s,a->call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},BlockFlowScenarios.title("qa-foreground-series",2),false));
            assertTrue("Foreground details start while old catalogue work remains blocked",service.entered.await(2,TimeUnit.SECONDS));
        }finally{free.countDown();service.release.countDown();service.close();}
    }
}
