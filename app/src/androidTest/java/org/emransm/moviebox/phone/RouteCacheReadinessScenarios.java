package org.emransm.moviebox.phone;

import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

/** Integration checks for native route cache behavior, with no service calls or shared user state. */
@RunWith(AndroidJUnit4.class)
public class RouteCacheReadinessScenarios {
    private static class CachedApi extends Api {
        final AtomicInteger homes=new AtomicInteger(),movies=new AtomicInteger(),tv=new AtomicInteger();
        volatile CountDownLatch gate,refreshStarted;
        CachedApi()throws Exception {super("0123456789abcdef0123456789abcdef");}
        private List<JSONObject> titles(String route,int type)throws Exception {List<JSONObject> out=new ArrayList<>();for(int n=0;n<20;n++)out.add(new JSONObject().put("subjectId","qa-route-cache-"+route+"-"+n).put("subjectType",type).put("title","Cache "+route+" "+n).put("releaseDate","2026-01-01"));return out;}
        @Override public List<JSONObject> home()throws Exception {homes.incrementAndGet();return titles("home",1);}
        @Override public List<JSONObject> catalogue(int type,int page,JSONObject filters)throws Exception {if(page>1)return Collections.emptyList();(type==1?movies:tv).incrementAndGet();CountDownLatch wait=gate;if(wait!=null){CountDownLatch entered=refreshStarted;if(entered!=null)entered.countDown();if(!wait.await(10,TimeUnit.SECONDS))throw new java.io.IOException("Owned refresh fixture timed out");}return titles(type==1?"movies":"tv",type);}
    }
    private static File isolate(ActivityScenario<PhoneActivity> scenario,CachedApi api)throws Exception {
        File[] owned={null};main(scenario,a->{call(a,"releasePlayer",new Class[]{});put(a,"generation",(Integer)field(a,"generation")+1);((ThreadPoolExecutor)field(a,"network")).getQueue().clear();((Library)field(a,"library")).close();owned[0]=new File(a.getCacheDir(),"qa-route-cache-"+System.nanoTime());assertTrue(owned[0].mkdirs());Context wrapped=new ContextWrapper(a){@Override public File getFilesDir(){return owned[0];}};put(a,"library",new Library(wrapped));put(a,"api",api);((Map<?,?>)field(a,"catalogueCache")).clear();((Map<?,?>)field(a,"cacheTimes")).clear();put(a,"filters",new JSONObject());});return owned[0];
    }
    private static void removeOwned(File file)throws Exception {if(file==null)return;assertTrue(file.getName().startsWith("qa-route-cache-"));removeChildren(file,file.getCanonicalPath()+File.separator);}
    private static void removeChildren(File file,String root)throws Exception {String path=file.getCanonicalPath();assertTrue(path.equals(root.substring(0,root.length()-1))||path.startsWith(root));File[] children=file.listFiles();if(children!=null)for(File child:children)removeChildren(child,root);if(file.exists())assertTrue(file.delete());}
    private static void visit(ActivityScenario<PhoneActivity> scenario,String route){main(scenario,a->call(a,"browse",new Class[]{String.class},route));}
    private static boolean cached(ActivityScenario<PhoneActivity> scenario,String key){boolean[] value={false};main(scenario,a->value[0]=((Map<?,?>)field(a,"catalogueCache")).containsKey(key));return value[0];}
    @Test public void freshHomeMoviesAndTvRevisitsDoNotRefetchCatalogue()throws Exception {
        CachedApi api=new CachedApi();File owned=null;try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            owned=isolate(s,api);for(String route:new String[]{"Discover","Movies","TV Shows"}){visit(s,route);waitFor(()->cached(s,route+"{}"),5000);}
            assertEquals(1,api.homes.get());assertEquals(1,api.movies.get());assertEquals(1,api.tv.get());
            for(int n=0;n<3;n++)for(String route:new String[]{"Discover","Movies","TV Shows"})main(s,a->{call(a,"browse",new Class[]{String.class},route);assertEquals("Fresh memory cache displays without loading status","",((TextView)field(a,"status")).getText().toString());});
            Thread.sleep(500);assertEquals("Home is reused",1,api.homes.get());assertEquals("Movies is reused",1,api.movies.get());assertEquals("TV is reused",1,api.tv.get());
        }finally{Thread.sleep(300);removeOwned(owned);}
    }
    @Test public void staleMoviesRenderImmediatelyAndUnchangedRefreshPreservesGrid()throws Exception {
        CachedApi api=new CachedApi();File owned=null;try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            owned=isolate(s,api);visit(s,"Movies");waitFor(()->cached(s,"Movies{}"),5000);
            main(s,a->((Map<String,Long>)field(a,"cacheTimes")).put("Movies{}",System.currentTimeMillis()-300001));api.gate=new CountDownLatch(1);api.refreshStarted=new CountDownLatch(1);visit(s,"Movies");assertTrue("Stale refresh runs in background",api.refreshStarted.await(5,TimeUnit.SECONDS));
            Object[] grid={null};main(s,a->{grid[0]=field(a,"grid");assertNotNull("Cached title grid rendered before blocked refresh finishes",grid[0]);assertEquals(20,((RecyclerView)grid[0]).getAdapter().getItemCount());((RecyclerView)grid[0]).scrollToPosition(10);});Thread.sleep(200);api.gate.countDown();
            waitFor(()->{boolean[] fresh={false};main(s,a->fresh[0]=System.currentTimeMillis()-((Map<String,Long>)field(a,"cacheTimes")).get("Movies{}")<5000);return fresh[0];},5000);
            main(s,a->{assertSame("Unchanged background catalogue must not rebuild/reset visible grid",grid[0],field(a,"grid"));assertEquals("",((TextView)field(a,"status")).getText().toString());});assertEquals(2,api.movies.get());
        }finally{if(api.gate!=null)api.gate.countDown();Thread.sleep(300);removeOwned(owned);}
    }
}
