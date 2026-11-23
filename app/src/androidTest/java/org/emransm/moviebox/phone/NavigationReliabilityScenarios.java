package org.emransm.moviebox.phone;

import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

/** Actual native navigation, with owned media and controlled delayed API responses. */
@RunWith(AndroidJUnit4.class)
public class NavigationReliabilityScenarios {
    static AutoCompleteTextView input(View view){if(view instanceof AutoCompleteTextView)return (AutoCompleteTextView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){AutoCompleteTextView found=input(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;}
    static int count(ActivityScenario<PhoneActivity> s){int[] n={0};main(s,a->{RecyclerView grid=(RecyclerView)field(a,"grid");n[0]=grid==null?0:grid.getAdapter().getItemCount();});return n[0];}
    static void clickTitle(ActivityScenario<PhoneActivity> s,String title)throws Exception{waitFor(()->{boolean[] ready={false};main(s,a->ready[0]=description((View)field(a,"root"),title)!=null);return ready[0];},4000);BlockFlowScenarios.clickRoot(s,title);}
    static class Pages extends SliderPlaybackScenarios.Local {
        final List<String> requests=Collections.synchronizedList(new ArrayList<>());
        Pages()throws Exception{}
        @Override public List<JSONObject> catalogue(int type,int page,JSONObject filters)throws Exception{
            requests.add(type+":"+page+":"+filters.optString("genre"));List<JSONObject> items=new ArrayList<>();
            if(page<=3)for(int i=0;i<30;i++)items.add(BlockFlowScenarios.title(type+"-"+filters.optString("genre")+"-"+((page-1)*30+i),type));return items;
        }
    }
    @Test public void ordinaryHistoryDetailsWatchRestoresLatestValidUnfinishedEpisode()throws Exception{
        SliderPlaybackScenarios.Local api=new SliderPlaybackScenarios.Local();JSONObject seen=BlockFlowScenarios.title("resume-ordinary",2);
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(s,api);main(s,a->{Library lib=(Library)field(a,"library");lib.progress(seen,2,7,12000,65000,false);lib.progress(seen,5,8,14000,65000,false);lib.progress(seen,1,2,1000,65000,true);call(a,"browse",new Class[]{String.class},"History");});
            waitFor(()->count(s)>0,3000);clickTitle(s,"Owned block resume-ordinary");
            waitFor(()->{boolean[] ok={false};main(s,a->ok[0]=(Boolean)field(a,"detailResolved"));return ok[0];},4000);
            main(s,a->{assertEquals(2,field(a,"season"));assertEquals(7,field(a,"episode"));});BlockFlowScenarios.capture("batch1-saved-episode.png");BlockFlowScenarios.clickRoot(s,"Watch or resume");
            waitFor(()->playing(s),10000);assertEquals(2,api.se);assertEquals(7,api.ep);assertTrue(position(s)>=12000);
        }
    }
    @Test public void cachedResumeDoesNotOverrideManualSelectionWhenFreshDetailsArrive()throws Exception{
        DetailFlowLoadingScenarios.Delayed api=new DetailFlowLoadingScenarios.Delayed();JSONObject title=BlockFlowScenarios.title("resume-refresh",2);
        api.display=new JSONObject(title.toString()).put("seasons",new JSONArray().put(new JSONObject().put("se",1).put("allEp","1,2,4")).put(new JSONObject().put("se",2).put("allEp","1,4,7")));
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(s,api);main(s,a->{((Library)field(a,"library")).progress(title,2,7,12000,65000,false);call(a,"openDetail",new Class[]{JSONObject.class},title);assertEquals(7,field(a,"episode"));call(a,"episodeMenu",new Class[]{boolean.class},false);});
            assertTrue(api.entered.await(2,TimeUnit.SECONDS));BlockFlowScenarios.click("Season 1");main(s,a->{assertEquals(1,field(a,"season"));assertEquals(1,field(a,"episode"));});
            api.release.countDown();Thread.sleep(350);main(s,a->{assertEquals(1,field(a,"season"));assertEquals(1,field(a,"episode"));});
            BlockFlowScenarios.clickRoot(s,"Watch or resume");waitFor(()->playing(s),10000);assertEquals(1,api.se);assertEquals(1,api.ep);
        }finally{api.release.countDown();api.close();}
    }
    @Test public void sparseResumeAndCrossTitleSelectionUseVerifiedEpisodeNumbers()throws Exception{
        SliderPlaybackScenarios.Local api=new SliderPlaybackScenarios.Local(){@Override public JSONObject detail(JSONObject item)throws Exception{return new JSONObject(item.toString()).put("seasons",new JSONArray().put(new JSONObject().put("se",5).put("allEp","4,8")));}};
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(s,api);JSONObject seen=BlockFlowScenarios.title("sparse-seen",2),fresh=BlockFlowScenarios.title("sparse-fresh",2);
            main(s,a->{((Library)field(a,"library")).progress(seen,5,8,5000,65000,false);call(a,"openDetail",new Class[]{JSONObject.class},seen);});
            waitFor(()->{boolean[] ok={false};main(s,a->ok[0]=(Boolean)field(a,"detailResolved"));return ok[0];},4000);main(s,a->{assertEquals(5,field(a,"season"));assertEquals(8,field(a,"episode"));call(a,"openDetail",new Class[]{JSONObject.class},fresh);});
            waitFor(()->{boolean[] ok={false};main(s,a->ok[0]=(Boolean)field(a,"detailResolved"));return ok[0];},4000);main(s,a->{assertEquals(5,field(a,"season"));assertEquals(4,field(a,"episode"));});
        }
    }
    @Test public void backspaceCancelsDelayedSearchSuccessAndErrorAndCannotResurrectOnReentry()throws Exception{
        for(boolean fail:new boolean[]{false,true}){
            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
            BlockFlowScenarios.FakeApi api=new BlockFlowScenarios.FakeApi(){@Override public List<JSONObject> search(String word)throws Exception{entered.countDown();assertTrue(release.await(8,TimeUnit.SECONDS));if(fail)throw new java.io.IOException("Owned delayed error");return Collections.singletonList(BlockFlowScenarios.title("old-results",1));}};
            try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
                BlockFlowScenarios.isolate(s,api);main(s,a->{call(a,"browse",new Class[]{String.class},"Search");input((View)field(a,"root")).setText("old query");call(a,"runSearch",new Class[]{String.class},"old query");});assertTrue(entered.await(2,TimeUnit.SECONDS));
                main(s,a->input((View)field(a,"root")).setText(""));release.countDown();Thread.sleep(350);
                main(s,a->{assertEquals("",field(a,"query"));assertTrue(BlockFlowScenarios.hasText((View)field(a,"root"),"Find your next watch"));assertNull(description((View)field(a,"root"),"Retry search"));assertNull(field(a,"grid"));call(a,"browse",new Class[]{String.class},"Settings");call(a,"browse",new Class[]{String.class},"Search");assertEquals("",input((View)field(a,"root")).getText().toString());});
            }finally{release.countDown();api.close();}
        }
    }
    @Test public void oneCharacterAndWhitespaceClearExistingSearchResults()throws Exception{
        BlockFlowScenarios.FakeApi api=new BlockFlowScenarios.FakeApi();try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(s,api);main(s,a->call(a,"browse",new Class[]{String.class},"Search"));
            for(String cleared:new String[]{"x","  ",""}){
                main(s,a->{input((View)field(a,"root")).setText("result");call(a,"runSearch",new Class[]{String.class},"result");});waitFor(()->count(s)>0,3000);
                main(s,a->{input((View)field(a,"root")).setText(cleared);assertEquals("",field(a,"query"));assertNull(field(a,"grid"));assertTrue(BlockFlowScenarios.hasText((View)field(a,"root"),"Find your next watch"));});
                if(cleared.isEmpty())BlockFlowScenarios.capture("batch1-search-cleared.png");
            }
        }
    }
    @Test public void moviesAndSeriesKeepThreeLoadedPagesScrollAndNextPageAcrossDetails()throws Exception{
        Pages api=new Pages();try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(s,api);
            for(String route:new String[]{"Movies","TV Shows"}){
                main(s,a->call(a,"browse",new Class[]{String.class},route));waitFor(()->count(s)==30,4000);
                for(int target:new int[]{60,90}){Thread.sleep(200);main(s,a->{RecyclerView grid=(RecyclerView)field(a,"grid");grid.scrollBy(0,100000);});waitFor(()->count(s)==target,4000);}
                main(s,a->((GridLayoutManager)((RecyclerView)field(a,"grid")).getLayoutManager()).scrollToPositionWithOffset(63,17));Thread.sleep(150);
                BlockFlowScenarios.capture("batch1-"+route.replace(" ","-")+"-before.png");int[] anchor={0,0};main(s,a->{RecyclerView grid=(RecyclerView)field(a,"grid");GridLayoutManager lm=(GridLayoutManager)grid.getLayoutManager();anchor[0]=lm.findFirstVisibleItemPosition();anchor[1]=lm.getDecoratedTop(lm.findViewByPosition(anchor[0]));call(a,"openDetail",new Class[]{JSONObject.class},BlockFlowScenarios.title("detail-away",1));});
                Thread.sleep(150);main(s,a->a.onBackPressed());Thread.sleep(200);
                main(s,a->{RecyclerView grid=(RecyclerView)field(a,"grid");GridLayoutManager lm=(GridLayoutManager)grid.getLayoutManager();assertEquals(90,grid.getAdapter().getItemCount());assertEquals(3,field(a,"page"));assertEquals(anchor[0],lm.findFirstVisibleItemPosition());assertEquals(anchor[1],lm.getDecoratedTop(lm.findViewByPosition(anchor[0])));});BlockFlowScenarios.capture("batch1-"+route.replace(" ","-")+"-after.png");main(s,a->((RecyclerView)field(a,"grid")).scrollBy(0,100000));
                waitFor(()->{boolean[] done={false};main(s,a->done[0]=!(Boolean)field(a,"catalogueHasMore"));return done[0];},4000);
                assertEquals(1,Collections.frequency(api.requests,(route.equals("Movies")?1:2)+":1:"));assertTrue(api.requests.contains((route.equals("Movies")?1:2)+":4:"));
            }
        }
    }
    @Test public void searchResultsReturnWithoutRefetchAndLibraryReturnReflectsRemoval()throws Exception{
        Pages api=new Pages();JSONObject saved=BlockFlowScenarios.title("saved",1);
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(s,api);main(s,a->{call(a,"browse",new Class[]{String.class},"Search");call(a,"runSearch",new Class[]{String.class},"owned");});waitFor(()->count(s)==1,4000);
            clickTitle(s,"Owned block search-result");Thread.sleep(150);main(s,a->a.onBackPressed());Thread.sleep(200);assertEquals(1,count(s));assertEquals(1,api.searches.get());
            main(s,a->{((Library)field(a,"library")).toggle(saved);call(a,"browse",new Class[]{String.class},"My list");});assertEquals(1,count(s));clickTitle(s,"Owned block saved");Thread.sleep(150);
            BlockFlowScenarios.clickRoot(s,"Toggle saved title");main(s,a->a.onBackPressed());assertEquals(0,count(s));
        }
    }
    @Test public void staleMetadataMissingSavedEpisodeRestoresItWhenFreshUnlessUserChooses()throws Exception{
        for(boolean manual:new boolean[]{false,true}){
            DetailFlowLoadingScenarios.Delayed api=new DetailFlowLoadingScenarios.Delayed();JSONObject title=BlockFlowScenarios.title("resume-new-season",2);
            api.display=new JSONObject(title.toString()).put("seasons",new JSONArray().put(new JSONObject().put("se",1).put("allEp","1,2,4")));
            try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
                BlockFlowScenarios.isolate(s,api);main(s,a->{Library lib=(Library)field(a,"library");lib.progress(title,1,2,4000,65000,false);lib.data.getJSONObject("history").getJSONObject(Library.key(title,1,2)).put("updated",1L);lib.progress(title,2,7,12000,65000,false);call(a,"openDetail",new Class[]{JSONObject.class},title);assertEquals(1,field(a,"season"));});assertTrue(api.entered.await(2,TimeUnit.SECONDS));
                if(manual){main(s,a->call(a,"episodeMenu",new Class[]{boolean.class},false));BlockFlowScenarios.click("Season 1");}
                api.release.countDown();waitFor(()->{boolean[] ready={false};main(s,a->ready[0]=((TreeMap<?,?>)field(a,"seasons")).containsKey(2));return ready[0];},4000);
                main(s,a->{assertEquals(manual?1:2,field(a,"season"));assertEquals(manual?1:7,field(a,"episode"));});
            }finally{api.release.countDown();api.close();}
        }
    }
    @Test public void staleRefreshAddsNewTitlesWithoutLosingConcurrentNextPageOrScroll()throws Exception{
        CountDownLatch refreshEntered=new CountDownLatch(1),pageEntered=new CountDownLatch(1),refreshFree=new CountDownLatch(1),pageFree=new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger firsts=new java.util.concurrent.atomic.AtomicInteger();
        Pages api=new Pages(){@Override public List<JSONObject> catalogue(int type,int page,JSONObject filters)throws Exception{
            List<JSONObject> result=super.catalogue(type,page,filters);
            if(page==1&&firsts.incrementAndGet()>1){refreshEntered.countDown();assertTrue(refreshFree.await(10,TimeUnit.SECONDS));result.set(0,BlockFlowScenarios.title("new-release",type));result.get(1).put("title","Updated metadata");}
            if(page==2){pageEntered.countDown();assertTrue(pageFree.await(10,TimeUnit.SECONDS));}return result;
        }};
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(s,api);main(s,a->call(a,"browse",new Class[]{String.class},"Movies"));waitFor(()->count(s)==30,4000);Thread.sleep(200);
            main(s,a->{call(a,"openDetail",new Class[]{JSONObject.class},BlockFlowScenarios.title("refresh-away",1));Map<?,?> states=(Map<?,?>)field(a,"browseStates");put(states.get("Movies{}"),"loadedAt",0L);});Thread.sleep(150);main(s,a->a.onBackPressed());assertTrue(refreshEntered.await(2,TimeUnit.SECONDS));Thread.sleep(200);
            main(s,a->((RecyclerView)field(a,"grid")).scrollBy(0,100000));assertTrue(pageEntered.await(2,TimeUnit.SECONDS));
            String[] anchorId={""};int[] anchorTop={0};main(s,a->{RecyclerView grid=(RecyclerView)field(a,"grid");GridLayoutManager lm=(GridLayoutManager)grid.getLayoutManager();int pos=lm.findFirstVisibleItemPosition();List<?> items=(List<?>)field(grid.getAdapter(),"items");anchorId[0]=((JSONObject)items.get(pos)).optString("subjectId");anchorTop[0]=lm.getDecoratedTop(lm.findViewByPosition(pos));});
            refreshFree.countDown();waitFor(()->count(s)==31,4000);Thread.sleep(200);
            main(s,a->{RecyclerView grid=(RecyclerView)field(a,"grid");GridLayoutManager lm=(GridLayoutManager)grid.getLayoutManager();List<?> items=(List<?>)field(grid.getAdapter(),"items");for(int i=0;i<items.size();i++)if(anchorId[0].equals(((JSONObject)items.get(i)).optString("subjectId"))){assertNotNull(lm.findViewByPosition(i));assertEquals(anchorTop[0],lm.getDecoratedTop(lm.findViewByPosition(i)));}});
            pageFree.countDown();waitFor(()->count(s)==61,4000);
            main(s,a->{assertEquals(2,field(a,"page"));call(a,"saveBrowseState",new Class[]{});Map<?,?> states=(Map<?,?>)field(a,"browseStates");List<?> items=(List<?>)field(states.get("Movies{}"),"items");Set<String> ids=new HashSet<>();for(Object item:items)ids.add(((JSONObject)item).optString("subjectId"));assertEquals(61,ids.size());assertTrue(ids.contains("new-release"));assertTrue(ids.contains("1--59"));assertEquals("Updated metadata",((JSONObject)items.get(2)).optString("title"));});
        }finally{refreshFree.countDown();pageFree.countDown();api.close();}
    }
}
