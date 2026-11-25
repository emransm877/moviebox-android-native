package org.emransm.moviebox.phone;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.ActivityScenario;
import android.view.View;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

/** Synthetic metadata only; no network, account or playback is used. */
@RunWith(AndroidJUnit4.class)
public class WatchRecommendationsScenarios {
    static JSONObject title(String id,int type)throws Exception{return new JSONObject().put("subjectId",id).put("subjectType",type).put("title","Title "+id);}
    static JSONObject feed(Object... values)throws Exception{JSONArray rows=new JSONArray();for(Object value:values)rows.put(value);return new JSONObject().put("items",rows);}
    @Test public void filtersSelfDuplicatesUnsupportedAndExplicitAdult()throws Exception{
        JSONObject response=feed(title("self",1),title("movie",1),title("movie",1),title("series",2),title("music",3),title("adult",1).put("isAdult",true),title("",1),new JSONObject(),"bad");
        List<JSONObject> parsed=WatchRecommendations.parse(response,"self",true);
        assertEquals(2,parsed.size());assertEquals("movie",parsed.get(0).getString("subjectId"));assertEquals("series",parsed.get(1).getString("subjectId"));
        assertEquals(3,WatchRecommendations.parse(response,"self",false).size());
    }
    @Test public void rejectsUnverifiedShapesAndCapsResults()throws Exception{
        assertTrue(WatchRecommendations.parse(new JSONArray().put(title("a",1)),"self",true).isEmpty());
        assertTrue(WatchRecommendations.parse(new JSONObject().put("list",new JSONArray().put(title("a",1))),"self",true).isEmpty());
        JSONArray rows=new JSONArray();for(int i=0;i<20;i++)rows.put(title("id"+i,1));assertEquals(6,WatchRecommendations.parse(new JSONObject().put("items",rows),"self",true).size());
    }
    @Test public void staleTitleSameTitleRetryAndPreferenceCannotLeak()throws Exception{
        WatchRecommendations state=new WatchRecommendations();List<JSONObject> rows=WatchRecommendations.parse(feed(title("one",1)),"a",true);
        WatchRecommendations.Ticket a=state.begin("a",true),b=state.begin("b",true);
        assertFalse(state.accept(a,rows));assertTrue(state.forTitle("a",true).isEmpty());assertTrue(state.accept(b,rows));
        assertTrue(state.forTitle("b",false).isEmpty());WatchRecommendations.Ticket retry=state.begin("b",true);assertFalse(state.accept(b,rows));assertTrue(state.accept(retry,rows));
        state.forTitle("b",true).get(0).put("title","mutated");assertEquals("Title one",state.forTitle("b",true).get(0).getString("title"));state.clear();assertFalse(state.accept(retry,rows));
    }
    @Test public void requestMatchesRecoveredContractAndPreferenceRaceDropsResult()throws Exception{
        class Fake extends Api {
            String path;Map<String,String> params;JSONObject body;boolean changeFamily;
            Fake()throws Exception{super("recommendations-test-identity");}
            @Override public void warm(){}
            @Override public Object request(String p,Map<String,String> a,JSONObject b,boolean nativeApi)throws Exception{path=p;params=a;body=b;assertTrue(nativeApi);if(changeFamily)setFamilyMode(false);return feed(title("other",2));}
        }
        Fake api=new Fake();try{
            assertEquals(1,api.recommendations("123").size());assertEquals("/subject-api/play-related-rec",api.path);assertEquals(Api.NATIVE,api.params.get("host"));
            assertEquals(4,api.body.length());assertEquals("123",api.body.getString("subjectId"));assertEquals("",api.body.getString("name"));assertEquals(1,api.body.getInt("page"));assertEquals(6,api.body.getInt("perPage"));
            api.setFamilyMode(true);api.changeFamily=true;assertTrue(api.recommendations("123").isEmpty());
        }finally{api.close();}
    }
    @Test public void offlineAndMissingIdentityNeverRequest()throws Exception{
        for(String id:new String[]{"","0","offline:content://sample"})try{WatchRecommendations.requestBody(id);fail("Invalid catalogue identity accepted");}catch(IllegalArgumentException expected){}
    }
    @Test public void portraitRelatedCardStartsItsOwnTitleAfterFirstFrame()throws Exception{
        class Local extends SliderPlaybackScenarios.Local {
            volatile String requested="";Local()throws Exception{}
            @Override public List<JSONObject> recommendations(String id)throws Exception{requested=id;return id.equals("qa-rec-first")?java.util.Collections.singletonList(BlockFlowScenarios.title("qa-rec-second",1)):java.util.Collections.emptyList();}
            @Override public WatchRecommendations.Page recommendationsPage(String id,int page)throws Exception{return new WatchRecommendations.Page(id,false,page,page==1?recommendations(id):java.util.Collections.emptyList(),false,0);}
        }
        Local service=new Local();try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(scenario,service);main(scenario,a->call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},BlockFlowScenarios.title("qa-rec-first",1),true));
            waitFor(()->playing(scenario),10000);
            waitFor(()->{boolean[] ready={false};main(scenario,a->ready[0]=description((View)field(a,"root"),"Play recommended Owned block qa-rec-second")!=null);return ready[0];},5000);
            assertEquals("qa-rec-first",service.requested);main(scenario,a->{View card=description((View)field(a,"root"),"Play recommended Owned block qa-rec-second");assertNotNull(card);assertTrue(card.performClick());});
            waitFor(()->playing(scenario)&&service.id.equals("qa-rec-second"),10000);assertEquals("qa-rec-second",service.id);
            main(scenario,a->assertNull(description((View)field(a,"root"),"Play recommended Owned block qa-rec-second")));
        }finally{service.close();}
    }
}
