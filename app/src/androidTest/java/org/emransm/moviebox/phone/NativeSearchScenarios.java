package org.emransm.moviebox.phone;
import android.content.*;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

@RunWith(AndroidJUnit4.class)
public class NativeSearchScenarios {
    @Test public void searchActionRemainsVisibleAtLargeText()throws Exception{
        try(ActivityScenario<PhoneActivity> s=start(new Local())){
            final android.content.res.Configuration[] original={null};
            try{main(s,a->{original[0]=new android.content.res.Configuration(a.getResources().getConfiguration());android.content.res.Configuration large=new android.content.res.Configuration(original[0]);large.fontScale=1.5f;a.getResources().updateConfiguration(large,a.getResources().getDisplayMetrics());call(a,"browse",new Class[]{String.class},"Search");});Thread.sleep(250);
                main(s,a->{View root=(View)field(a,"root"),submit=description(root,"Submit search");assertNotNull(submit);int[] at=new int[2],base=new int[2];submit.getLocationOnScreen(at);root.getLocationOnScreen(base);assertTrue(at[0]>=base[0]);assertTrue(at[0]+submit.getWidth()<=base[0]+root.getWidth());assertTrue(submit.getWidth()>=((TextView)submit).getPaint().measureText("Search"));TextView clear=(TextView)description(root,"Clear recent searches");if(clear!=null)assertTrue(clear.getWidth()-clear.getPaddingLeft()-clear.getPaddingRight()>=clear.getPaint().measureText("Clear"));assertEquals(View.GONE,((View)field(a,"bottomNav")).getVisibility());});BlockFlowScenarios.capture("qa-search-large-text.png");
            }finally{if(original[0]!=null)main(s,a->a.getResources().updateConfiguration(original[0],a.getResources().getDisplayMetrics()));}
        }
    }
    static class Local extends SliderPlaybackScenarios.Local {
        final AtomicInteger searches=new AtomicInteger();String last="";
        Local()throws Exception{}
        @Override public List<JSONObject> search(String keyword)throws Exception{last=keyword;searches.incrementAndGet();return Collections.singletonList(BlockFlowScenarios.title("qa-search-result",1).put("title",keyword+" owned result"));}
        @Override public List<String> suggest(String keyword){try{Thread.sleep(keyword.equals("a")?300:10);}catch(InterruptedException stopped){Thread.currentThread().interrupt();}return Arrays.asList(keyword+" suggestion",keyword+" second");}
        @Override public JSONObject searchLanding()throws Exception{return new JSONObject().put("everyoneSearch",new JSONArray().put(new JSONObject().put("title","Owned popular query")).put(new JSONObject().put("title","Owned marked query").put("isAdult",true))).put("hot",new JSONArray().put(new JSONObject().put("category","Movie").put("name","Hot Movies").put("items",new JSONArray().put(BlockFlowScenarios.title("qa-hot-movie",1)))).put(new JSONObject().put("category","TV").put("name","Hot Series").put("items",new JSONArray().put(BlockFlowScenarios.title("qa-hot-series",2)))));}
    }
    private ActivityScenario<PhoneActivity> start(Local api)throws Exception{ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class);BlockFlowScenarios.isolate(s,api);main(s,a->{call(a,"rememberSearch",new Class[]{String.class},"Owned recent query");call(a,"browse",new Class[]{String.class},"Search");});Thread.sleep(400);return s;}
    @Test public void recentAndGenuineHotDataUseOriginalLikeStructureAndFilterMetadata()throws Exception{
        try(ActivityScenario<PhoneActivity> s=start(new Local())){
            main(s,a->{View root=(View)field(a,"root");assertNotNull(description(root,"Submit search"));assertTrue(BlockFlowScenarios.hasText(root,"Recent"));assertNotNull(description(root,"Search for Owned recent query"));assertTrue(BlockFlowScenarios.hasText(root,"Everyone is searching"));assertNull(description(root,"Search for Owned marked query"));assertNotNull(description(root,"Show Hot Movies"));assertNotNull(description(root,"Show Hot Series"));assertNotNull(description(root,"Play Owned block qa-hot-movie"));assertTrue(description(root,"Show Hot Series").performClick());assertNotNull(description(root,"Play Owned block qa-hot-series"));});BlockFlowScenarios.capture("qa-search-landing.png");
        }
    }
    @Test public void recentClearDoesNotEraseMyListAndHistoryPersistsAcrossLibraryRecreation()throws Exception{
        try(ActivityScenario<PhoneActivity> s=start(new Local())){
            main(s,a->{Library lib=(Library)field(a,"library");JSONObject kept=BlockFlowScenarios.title("qa-keep-list",1);lib.toggle(kept);call(a,"rememberSearch",new Class[]{String.class},"Owned second query");call(a,"rememberSearch",new Class[]{String.class},"OWNED RECENT QUERY");List<?> terms=(List<?>)call(a,"recentSearches",new Class[]{});assertEquals(2,terms.size());assertEquals("OWNED RECENT QUERY",terms.get(0));lib.save();String path=(String)field(lib,"path");Context wrapper=new ContextWrapper(a){@Override public java.io.File getFilesDir(){return new java.io.File(path).getParentFile();}};Library reopened=new Library(wrapper);assertEquals(2,reopened.data.getJSONArray("recentSearches").length());reopened.close();call(a,"clearRecentSearches",new Class[]{});assertTrue(((List<?>)call(a,"recentSearches",new Class[]{})).isEmpty());assertTrue(lib.contains(kept));});
        }
    }
    @Test public void typingKeepsInputFocusAndLatestSuggestionsWin()throws Exception{
        try(ActivityScenario<PhoneActivity> s=start(new Local())){
            main(s,a->{AutoCompleteTextView input=(AutoCompleteTextView)field(a,"searchInput");input.requestFocus();input.setText("a");});Thread.sleep(250);
            main(s,a->((AutoCompleteTextView)field(a,"searchInput")).setText("atlas"));Thread.sleep(650);
            main(s,a->{AutoCompleteTextView input=(AutoCompleteTextView)field(a,"searchInput");assertTrue(input.hasFocus());assertEquals("atlas",input.getText().toString());View root=(View)field(a,"root");assertNotNull(description(root,"Search for atlas suggestion"));assertNull(description(root,"Search for a suggestion"));assertFalse(BlockFlowScenarios.hasText(root,"Recent"));});BlockFlowScenarios.capture("qa-search-suggestions.png");
        }
    }
    @Test public void submittedQueryAndResultsReturnFromPlayerWithoutInputFocus()throws Exception{
        Local api=new Local();try(ActivityScenario<PhoneActivity> s=start(api)){
            main(s,a->call(a,"runSearch",new Class[]{String.class},"Owned query"));waitFor(()->api.searches.get()==1,3000);Thread.sleep(200);
            main(s,a->{assertEquals("Owned query",((AutoCompleteTextView)field(a,"searchInput")).getText().toString());View card=description((View)field(a,"root"),"Owned query owned result");assertNotNull(card);assertTrue(card.performClick());});waitFor(()->playing(s),10000);
            main(s,a->call(a,"leavePlayer",new Class[]{}));Thread.sleep(300);
            main(s,a->{assertEquals("Search",field(a,"route"));assertEquals("Owned query",field(a,"query"));assertFalse(((AutoCompleteTextView)field(a,"searchInput")).hasFocus());assertNotNull(description((View)field(a,"root"),"Owned query owned result"));});assertEquals("Cached results survive playback roundtrip",1,api.searches.get());
        }
    }
    @Test public void backReturnsToOriginatingCatalogue()throws Exception{
        try(ActivityScenario<PhoneActivity> s=start(new Local())){main(s,a->{call(a,"browse",new Class[]{String.class},"Movies");call(a,"browse",new Class[]{String.class},"Search");assertEquals("Movies",field(a,"searchOrigin"));View back=description((View)field(a,"root"),"Back to Movies");assertNotNull(back);assertTrue(back.performClick());assertEquals("Movies",field(a,"route"));});}
    }
    @Test public void suggestionsParserFiltersMetadataBeforeReducingToStrings()throws Exception{
        Api api=new Api("owned-search-fixture-identity"){ @Override protected Object requestOnce(String path,Map<String,String> args,JSONObject body,boolean nativeApi)throws Exception{if(path.equals("/tab-operating"))return new JSONObject();return new JSONArray().put(new JSONObject().put("word","Owned marked").put("isAdult",true)).put(new JSONObject().put("word","Owned safe"));}};
        try{assertEquals(Collections.singletonList("Owned safe"),api.suggest("owned"));api.setFamilyMode(false);assertEquals(2,api.suggest("owned").size());}finally{api.close();}
    }
}
