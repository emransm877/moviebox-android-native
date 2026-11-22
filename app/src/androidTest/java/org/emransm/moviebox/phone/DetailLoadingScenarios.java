package org.emransm.moviebox.phone;

import android.content.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Causal gates instead of fragile speed thresholds. No external service or library writes. */
@RunWith(AndroidJUnit4.class)
public class DetailLoadingScenarios {
 static JSONObject title()throws Exception{return new JSONObject().put("subjectId","qa-details").put("subjectType",2).put("title","Dhindora · S2 — 日本語").put("description","Public synopsis");}
 static JSONObject seasons()throws Exception{return new JSONObject().put("seasons",new JSONArray().put(new JSONObject().put("se",2).put("allEp","1,4,7").put("maxEp",7)));}
 static class Service extends Api {
  final AtomicInteger detail=new AtomicInteger(),season=new AtomicInteger(),resources=new AtomicInteger();
  CountDownLatch both,release;
  Service(UiCache cache)throws Exception{super("owned-detail-loading-fixture-0000",cache);}
  @Override public synchronized void warm(){}
  @Override public Object request(String path,Map<String,String> params,JSONObject body,boolean nativeApi)throws Exception{
   if(path.endsWith("/resource")){resources.incrementAndGet();if(release!=null)release.await(5,TimeUnit.SECONDS);return new JSONObject().put("list",new JSONArray());}
   if(path.endsWith("/get"))detail.incrementAndGet();else if(path.endsWith("/season-info"))season.incrementAndGet();else throw new AssertionError(path);
   if(both!=null){both.countDown();assertTrue("Independent requests must overlap",both.await(5,TimeUnit.SECONDS));}
   if(release!=null)assertTrue(release.await(5,TimeUnit.SECONDS));
   return path.endsWith("/get")?title():seasons();
  }
 }
 @Test public void seriesRequestsOverlapAndMemoryCacheReturnsDefensiveCopies()throws Exception{
  Service api=new Service(null);api.both=new CountDownLatch(2);ExecutorService work=Executors.newSingleThreadExecutor();
  try{JSONObject result=work.submit(()->api.detail(title())).get(8,TimeUnit.SECONDS);assertEquals(2,result.getJSONArray("seasons").getJSONObject(0).getInt("se"));result.put("title","Wrong mutation");assertEquals(title().getString("title"),api.detail(title()).getString("title"));assertEquals(1,api.detail.get());assertEquals(1,api.season.get());}
  finally{api.close();work.shutdownNow();}
 }
 @Test public void simultaneousDetailSelectionsShareOnePairOfRequests()throws Exception{
  Service api=new Service(null);api.release=new CountDownLatch(1);ExecutorService work=Executors.newFixedThreadPool(3);
  try{List<Future<JSONObject>> tasks=new ArrayList<>();for(int n=0;n<3;n++)tasks.add(work.submit(()->api.detail(title())));Thread.sleep(150);api.release.countDown();for(Future<JSONObject> task:tasks)assertEquals("qa-details",task.get(8,TimeUnit.SECONDS).getString("subjectId"));assertEquals(1,api.detail.get());assertEquals(1,api.season.get());}
  finally{api.release.countDown();api.close();work.shutdownNow();}
 }
 @Test public void preparingAndPlayingSameSelectionCoalescesResourceLookup()throws Exception{
  Service api=new Service(null);api.release=new CountDownLatch(1);ExecutorService work=Executors.newFixedThreadPool(2);
  try{Future<?> first=work.submit(()->{api.prefetchPlayback(title(),2,4,1080);return null;});Future<?> second=work.submit(()->{api.prefetchPlayback(title(),2,4,1080);return null;});Thread.sleep(150);api.release.countDown();first.get(6,TimeUnit.SECONDS);second.get(6,TimeUnit.SECONDS);assertEquals(1,api.resources.get());api.prefetchPlayback(title(),2,7,1080);assertEquals("Different episodes retain distinct cache keys",2,api.resources.get());}
  finally{api.release.countDown();api.close();work.shutdownNow();}
 }
 @Test public void processRecreationRestoresOnlyPublicMetadataAndExpiresIt()throws Exception{
  Context real=InstrumentationRegistry.getInstrumentation().getTargetContext();File root=new File(real.getCacheDir(),"qa-detail-cache-"+System.nanoTime());assertTrue(root.mkdirs());
  Context isolated=new ContextWrapper(real){@Override public File getCacheDir(){return root;}};UiCache cache=new UiCache(isolated,false);
  Service first=new Service(cache),second=new Service(new UiCache(isolated,false));
  try{
   JSONObject result=first.detail(title());result.put("token","fixture-secret").put("resourceLink","https://invalid.test/signed").put("extCaptions",new JSONArray().put("fixture-secret"));cache.writeDetail("qa-details",result);
   JSONObject restored=second.detail(title());assertEquals(title().getString("title"),restored.getString("title"));assertEquals("1,4,7",restored.getJSONArray("seasons").getJSONObject(0).getString("allEp"));assertEquals(0,second.detail.get());assertFalse(restored.has("token"));assertFalse(restored.has("resourceLink"));assertFalse(restored.has("extCaptions"));assertNull(cache.readDetail("other-title"));
   File[] entries=new File(root,"moviebox-ui-v1/details").listFiles();assertNotNull(entries);assertEquals(1,entries.length);String encoded;try(FileInputStream input=new FileInputStream(entries[0])){encoded=new String(Api.read(input,UiCache.MAX_SNAPSHOT_BYTES),StandardCharsets.UTF_8);}assertFalse(encoded.contains("fixture-secret"));JSONObject expired=new JSONObject(encoded).put("savedAt",System.currentTimeMillis()-UiCache.DETAIL_AGE_MS-1);try(FileOutputStream out=new FileOutputStream(entries[0])){out.write(expired.toString().getBytes(StandardCharsets.UTF_8));}assertNull(cache.readDetail("qa-details"));
   try(FileOutputStream out=new FileOutputStream(entries[0])){out.write("broken".getBytes(StandardCharsets.UTF_8));}assertNull(cache.readDetail("qa-details"));
  }finally{first.close();second.close();remove(root,root.getCanonicalPath());}
 }
 static void remove(File file,String root)throws Exception{assertTrue(file.getCanonicalPath().equals(root)||file.getCanonicalPath().startsWith(root+File.separator));File[] children=file.listFiles();if(children!=null)for(File child:children)remove(child,root);if(file.exists())assertTrue(file.delete());}
}
