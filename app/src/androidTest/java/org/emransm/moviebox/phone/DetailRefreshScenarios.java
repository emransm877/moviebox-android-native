package org.emransm.moviebox.phone;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.*;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

/** Owned delayed metadata; no service media or user library is required. */
@RunWith(AndroidJUnit4.class)
public class DetailRefreshScenarios {
    static final String ID="qa-viewport-series",FRESH="Fresh metadata arrived.";
    static String descriptionText(int count){StringBuilder text=new StringBuilder();for(int n=0;n<count;n++)text.append("Owned story paragraph ").append(n).append(". A long description keeps the reader below the title and actions.\n\n");return text.toString();}
    static JSONObject detail(int paragraphs,boolean second)throws Exception{return BlockFlowScenarios.title(ID,2).put("description",descriptionText(paragraphs)).put("seasons",new JSONArray().put(new JSONObject().put("se",1).put("allEp","1,2")).put(new JSONObject().put("se",second?2:3).put("allEp",second?"4,7":"8,9")));}
    static class Delayed extends SliderPlaybackScenarios.Local {
        final JSONObject cached,fresh;final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        Delayed(int paragraphs,boolean second)throws Exception{cached=DetailRefreshScenarios.detail(45,true);fresh=DetailRefreshScenarios.detail(paragraphs,second);fresh.put("description",FRESH+"\n"+fresh.optString("description"));}
        @Override public JSONObject cachedDisplayDetail(String id){return ID.equals(id)?cached:null;}
        @Override public JSONObject detail(JSONObject ignored)throws Exception{entered.countDown();assertTrue("Release fixture metadata",release.await(20,TimeUnit.SECONDS));return new JSONObject(fresh.toString());}
    }
    static ScrollView scroll(PhoneActivity a)throws Exception{ViewGroup content=(ViewGroup)field(a,"content");assertTrue(content.getChildAt(0) instanceof ScrollView);return (ScrollView)content.getChildAt(0);}
    static void open(ActivityScenario<PhoneActivity> scenario,Delayed api)throws Exception{BlockFlowScenarios.isolate(scenario,api);main(scenario,a->call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},BlockFlowScenarios.title(ID,2),false));assertTrue(api.entered.await(3,TimeUnit.SECONDS));waitFor(()->{boolean[] ready={false};main(scenario,a->ready[0]=scroll(a).getHeight()>0&&scroll(a).getChildAt(0).getHeight()>scroll(a).getHeight());return ready[0];},4000);}
    static void refreshed(ActivityScenario<PhoneActivity> scenario)throws Exception{waitFor(()->{boolean[] ready={false};main(scenario,a->ready[0]=((JSONObject)field(a,"subject")).optString("description").startsWith(FRESH)&&scroll(a).getHeight()>0);return ready[0];},4000);androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
    @Test public void delayedRefreshPreservesScrollAndDeliberateSeasonChoice()throws Exception{
        Delayed api=new Delayed(45,true);try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            open(scenario,api);main(scenario,a->call(a,"episodeMenu",new Class[]{boolean.class},false));FullPlaybackScenarios.click("Season 2");
            int[] before={0};main(scenario,a->{assertEquals(2,field(a,"season"));scroll(a).scrollTo(0,420);before[0]=scroll(a).getScrollY();assertTrue(before[0]>0);});api.release.countDown();refreshed(scenario);
            main(scenario,a->{assertEquals("Keep the reader's offset",before[0],scroll(a).getScrollY());assertEquals(2,field(a,"season"));assertEquals(4,field(a,"episode"));assertNotNull(description((View)field(a,"root"),"Play episode 4, selected"));});
        }finally{api.release.countDown();api.close();}
    }
    @Test public void removedSeasonFallsBackToRealAvailableEpisode()throws Exception{
        Delayed api=new Delayed(45,false);try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            open(scenario,api);main(scenario,a->call(a,"episodeMenu",new Class[]{boolean.class},false));FullPlaybackScenarios.click("Season 2");main(scenario,a->scroll(a).scrollTo(0,350));api.release.countDown();refreshed(scenario);
            main(scenario,a->{assertEquals(1,field(a,"season"));assertEquals(1,field(a,"episode"));assertNotNull(description((View)field(a,"root"),"Play episode 1, selected"));assertNull(description((View)field(a,"root"),"Play episode 4, selected"));});
        }finally{api.release.countDown();api.close();}
    }
    @Test public void shorterRefreshClampsOffsetToActualContent()throws Exception{
        Delayed api=new Delayed(1,true);try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            open(scenario,api);int[] before={0};main(scenario,a->{scroll(a).scrollTo(0,1800);before[0]=scroll(a).getScrollY();assertTrue(before[0]>500);});api.release.countDown();refreshed(scenario);
            main(scenario,a->{ScrollView view=scroll(a);int maximum=Math.max(0,view.getChildAt(0).getHeight()-view.getHeight()+view.getPaddingTop()+view.getPaddingBottom());assertTrue(view.getScrollY()<=maximum);assertTrue("Short content does not retain an empty lower viewport",view.getScrollY()<before[0]);});
        }finally{api.release.countDown();api.close();}
    }
    @Test public void leavingDetailsBeforeRefreshDoesNotReopenOrScrollOldPage()throws Exception{
        Delayed api=new Delayed(45,true);try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            open(scenario,api);main(scenario,a->{scroll(a).scrollTo(0,400);call(a,"browse",new Class[]{String.class},"Settings");});api.release.countDown();Thread.sleep(250);androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            main(scenario,a->{assertEquals("Settings",field(a,"route"));assertNull(field(a,"subject"));assertFalse(BlockFlowScenarios.hasText((View)field(a,"root"),FRESH));});
        }finally{api.release.countDown();api.close();}
    }
}
