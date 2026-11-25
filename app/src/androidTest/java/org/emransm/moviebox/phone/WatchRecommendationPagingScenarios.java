package org.emransm.moviebox.phone;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.*;
import static org.junit.Assert.*;

/** Owned synthetic metadata only; no claims about real catalogue size. */
@RunWith(AndroidJUnit4.class)
public class WatchRecommendationPagingScenarios {
    static JSONObject title(String id)throws Exception{return new JSONObject().put("subjectId",id).put("subjectType",1).put("title","موسیقی · "+id);}
    static JSONObject response(int page,boolean more,int next,int start,int count)throws Exception{
        JSONArray rows=new JSONArray();for(int n=0;n<count;n++)rows.put(title("id-"+(start+n)));
        return new JSONObject().put("items",rows).put("pager",new JSONObject().put("page",Integer.toString(page)).put("nextPage",Integer.toString(next)).put("hasMore",more).put("perPage",12).put("totalCount",1000000));
    }
    @Test public void actualPagerContractAndRequestBounds()throws Exception{
        JSONObject body=WatchRecommendations.requestBody("self",2,12);assertEquals(2,body.getInt("page"));assertEquals(12,body.getInt("perPage"));assertEquals(4,body.length());
        WatchRecommendations.Page page=WatchRecommendations.parsePage(response(2,true,3,0,12),"self",false,2);
        assertEquals("self",page.subjectId);assertEquals(2,page.page);assertEquals(12,page.perPage);assertTrue(page.hasMore);assertEquals(3,page.nextPage);assertEquals(12,page.items.size());assertTrue(page.items.get(0).getString("title").startsWith("موسیقی ·"));
        for(int invalid:new int[]{0,-1,11,Integer.MAX_VALUE})try{WatchRecommendations.requestBody("self",invalid,12);fail();}catch(IllegalArgumentException expected){}
        for(int invalid:new int[]{0,13,1000})try{WatchRecommendations.requestBody("self",1,invalid);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void appendsUniqueTitlesUpToFiftyWithoutInventingRows()throws Exception{
        WatchRecommendations state=new WatchRecommendations();WatchRecommendations.Ticket ticket=state.begin("self",false);
        for(int page=1;page<=5;page++){assertEquals(page,state.nextPage("self",false));assertTrue(state.accept(ticket,WatchRecommendations.parsePage(response(page,true,page+1,(page-1)*12,12),"self",false,page)));}
        List<JSONObject> rows=state.forTitle("self",false);assertEquals(50,rows.size());assertFalse(state.hasMore("self",false));assertEquals(0,state.nextPage("self",false));
        Set<String> ids=new HashSet<>();for(JSONObject row:rows)assertTrue(ids.add(row.getString("subjectId")));
        assertFalse(state.accept(ticket,WatchRecommendations.parsePage(response(6,true,7,60,12),"self",false,6)));
        rows.get(0).put("title","mutated");assertNotEquals("mutated",state.forTitle("self",false).get(0).getString("title"));
    }
    @Test public void shortEndEmptyAndDuplicatePagesStopWithoutFillingFifty()throws Exception{
        WatchRecommendations state=new WatchRecommendations();WatchRecommendations.Ticket ticket=state.begin("self",false);
        assertTrue(state.accept(ticket,WatchRecommendations.parsePage(response(1,true,2,0,12),"self",false,1)));
        assertTrue(state.accept(ticket,WatchRecommendations.parsePage(response(2,false,0,8,7),"self",false,2)));
        assertEquals(15,state.forTitle("self",false).size());assertFalse(state.hasMore("self",false));
        ticket=state.begin("self",false);assertTrue(state.accept(ticket,WatchRecommendations.parsePage(response(1,true,2,0,12),"self",false,1)));
        assertTrue(state.accept(ticket,WatchRecommendations.parsePage(response(2,true,3,0,12),"self",false,2)));assertEquals(12,state.forTitle("self",false).size());assertFalse(state.hasMore("self",false));
        ticket=state.begin("self",false);assertTrue(state.accept(ticket,WatchRecommendations.parsePage(response(1,true,2,0,0),"self",false,1)));assertEquals(0,state.forTitle("self",false).size());assertFalse(state.hasMore("self",false));
    }
    @Test public void staleWrongTitlePreferenceAndOutOfOrderResultsCannotAppend()throws Exception{
        WatchRecommendations state=new WatchRecommendations();WatchRecommendations.Ticket old=state.begin("old",false),current=state.begin("self",false);
        assertFalse(state.accept(old,WatchRecommendations.parsePage(response(1,true,2,0,12),"old",false,1)));
        assertFalse(state.accept(current,WatchRecommendations.parsePage(response(1,true,2,0,12),"other",false,1)));
        assertFalse(state.accept(current,WatchRecommendations.parsePage(response(1,true,2,0,12),"self",true,1)));
        assertFalse(state.accept(current,WatchRecommendations.parsePage(response(2,true,3,12,12),"self",false,2)));
        assertEquals(0,state.forTitle("self",false).size());assertTrue(state.accept(current,WatchRecommendations.parsePage(response(1,true,2,0,12),"self",false,1)));
        assertFalse(state.accept(current,WatchRecommendations.parsePage(response(1,true,2,0,12),"self",false,1)));assertEquals(12,state.forTitle("self",false).size());state.clear();assertFalse(state.hasMore("self",false));
    }
    @Test public void malformedOrUnverifiedPagerCannotCreateEndlessFeed()throws Exception{
        for(JSONObject feed:new JSONObject[]{response(1,true,9,0,12),response(2,true,3,0,12),response(1,true,2,0,12).put("pager",new JSONObject().put("hasMore","true").put("page","1").put("nextPage","2"))})assertFalse(WatchRecommendations.parsePage(feed,"self",false,1).hasMore);
        JSONObject noPager=response(1,true,2,0,12);noPager.remove("pager");assertFalse(WatchRecommendations.parsePage(noPager,"self",false,1).hasMore);
        try{WatchRecommendations.parsePage(new JSONObject().put("list",new JSONArray()),"self",false,1);fail();}catch(JSONException expected){}
        assertFalse(WatchRecommendations.parsePage(response(10,true,11,0,12),"self",false,10).hasMore);
    }
    @Test public void invalidRowsCurrentTitleAndRestrictedMetadataAreNotRecommendations()throws Exception{
        JSONObject feed=response(1,false,0,0,0);JSONArray rows=feed.getJSONArray("items");rows.put(title("self")).put(title("one")).put(title("one")).put(title("adult").put("isAdult",true)).put(title("type").put("subjectType",3)).put(new JSONObject()).put("broken").put(title("two").put("subjectType",2));
        WatchRecommendations.Page page=WatchRecommendations.parsePage(feed,"self",true,1);assertEquals(2,page.items.size());assertEquals("one",page.items.get(0).getString("subjectId"));assertEquals("two",page.items.get(1).getString("subjectId"));
    }
    @Test public void apiKeepsPageContractAndDropsChangedPreferenceResponse()throws Exception{
        class Fake extends Api {JSONObject body;Map<String,String> params;boolean change;Fake()throws Exception{super("owned-paged-recommendation-fixture");}@Override public void warm(){}@Override public Object request(String path,Map<String,String> args,JSONObject request,boolean nativeApi)throws Exception{assertEquals("/subject-api/play-related-rec",path);assertTrue(nativeApi);body=request;params=args;if(change)setFamilyMode(false);return response(request.getInt("page"),true,request.getInt("page")+1,0,12);}}
        Fake api=new Fake();try{WatchRecommendations.Page page=api.recommendationsPage("self",2);assertEquals(2,page.page);assertTrue(page.hasMore);assertEquals(12,api.body.getInt("perPage"));assertEquals(Api.NATIVE,api.params.get("host"));api.setFamilyMode(true);api.change=true;page=api.recommendationsPage("self",1);assertTrue(page.items.isEmpty());assertFalse(page.hasMore);}finally{api.close();}
    }
}
