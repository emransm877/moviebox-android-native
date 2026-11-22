package org.emransm.moviebox.phone;

import android.view.*;
import android.widget.*;
import android.os.SystemClock;
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

@RunWith(AndroidJUnit4.class)
public class ArtworkDetailScenarios {
 static TextView text(View view,String label){if(view instanceof TextView&&label.contentEquals(((TextView)view).getText()))return(TextView)view;if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++){TextView found=text(((ViewGroup)view).getChildAt(n),label);if(found!=null)return found;}return null;}
 @Test public void detailPaintsBeforeSlowApiAndDescriptionNeedsNoMoreControl()throws Exception{
  CountDownLatch entered=new CountDownLatch(1),finish=new CountDownLatch(1);
  String summary="Full description · Café বাংলা العربية. "+String.join(" ",Collections.nCopies(30,"Every line remains readable without an extra control."));
  JSONObject original=BlockFlowScenarios.title("qa-detail-latency",2).put("description",summary);
  BlockFlowScenarios.FakeApi service=new BlockFlowScenarios.FakeApi(){@Override public JSONObject detail(JSONObject item)throws Exception{entered.countDown();if(!finish.await(10,TimeUnit.SECONDS))throw new java.io.IOException("Owned fixture timed out");return new JSONObject(item.toString()).put("seasons",new JSONArray().put(new JSONObject().put("se",2).put("allEp","1,4")));}};
  try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
   BlockFlowScenarios.isolate(scenario,service);long start=SystemClock.elapsedRealtime();main(scenario,a->{put(a,"subject",original);put(a,"season",2);put(a,"episode",4);call(a,"openDetail",new Class[]{JSONObject.class},original);assertEquals(2,field(a,"season"));assertEquals(4,field(a,"episode"));});assertTrue(entered.await(3,TimeUnit.SECONDS));
   main(scenario,a->{View root=(View)field(a,"root");assertNotNull(text(root,original.optString("title")));TextView description=text(root,summary);assertNotNull(description);assertEquals(Integer.MAX_VALUE,description.getMaxLines());assertNull(description.getEllipsize());assertNull(description(root,"Read full description"));assertFalse(description(root,"Watch or resume").isEnabled());assertFalse(description(root,"Download selected video").isEnabled());});
   android.util.Log.i("MovieBoxPerf","owned_detail_preview_assert_ms="+(SystemClock.elapsedRealtime()-start));finish.countDown();
   waitFor(()->{boolean[] ready={false};main(scenario,a->ready[0]=description((View)field(a,"root"),"Watch or resume").isEnabled());return ready[0];},5000);
   main(scenario,a->{assertNotNull(description((View)field(a,"root"),"Play episode 4, selected"));assertEquals(2,field(a,"season"));assertEquals(4,field(a,"episode"));});
  }finally{finish.countDown();}
 }
 @Test public void verifiedBannerAssetIsParsedOnlyOncePerActivity()throws Exception{
  try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
   main(scenario,a->{Object first=call(a,"verifiedBanners",new Class[]{});assertTrue(first instanceof JSONArray);long at=SystemClock.elapsedRealtime();for(int n=0;n<100;n++)assertSame(first,call(a,"verifiedBanners",new Class[]{}));android.util.Log.i("MovieBoxPerf","memoized_banner_100_ms="+(SystemClock.elapsedRealtime()-at));});
  }
 }
 @Test public void nativeFeedAttachesOnlyExactNestedSubjectAndOwnImage()throws Exception{
  JSONObject subject=BlockFlowScenarios.title("qa-native-banner",1).put("releaseDate","2026-11-22");
  JSONObject image=new JSONObject().put("url","https://127.0.0.1:1/banner.webp").put("width",640).put("height",360);
  JSONObject row=new JSONObject().put("subjectId",subject.getString("subjectId")).put("subject",subject).put("image",image);
  JSONObject response=new JSONObject().put("items",new JSONArray().put(new JSONObject().put("banner",new JSONObject().put("banners",new JSONArray().put(new JSONObject().put("subjectId","0").put("image",image)).put(row)))));
  Api service=new Api("owned-fixture-native-banner"){@Override public Object request(String path,Map<String,String> params,JSONObject body,boolean nativeApi)throws Exception{return new JSONObject(response.toString());}};
  List<JSONObject> found=service.home();assertEquals(1,found.size());assertEquals(image.getString("url"),found.get(0).getJSONObject("banner").getString("url"));
  image.put("subjectId","wrong-title");assertFalse(service.home().get(0).has("banner"));image.remove("subjectId");
  response.getJSONArray("items").put(new JSONObject(subject.toString()).put("releaseDate","2026"));
  assertTrue("Native year and full web release date describe the same title",service.home().get(0).has("banner"));
  response.getJSONArray("items").put(1,new JSONObject(subject.toString()).put("releaseDate","2027-01-01"));
  assertFalse("Same ID with conflicting release metadata must not borrow the banner",service.home().get(0).has("banner"));
 }
 @Test public void capturedNineteenNativeBannerRowsKeepTheirExactSubjectMetadata()throws Exception{
  CapturedArtworkFixtures.require("native-banner-feed.json");
  JSONObject response;try(java.io.InputStream input=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("native-banner-feed.json")){response=new JSONObject(Utf8Json.decode(Api.read(input,256000)));}
  Api service=new Api("owned-captured-feed-test"){@Override public Object request(String path,Map<String,String> params,JSONObject body,boolean nativeApi)throws Exception{return new JSONObject(response.toString());}};
  List<JSONObject> found=service.home();assertEquals(15,found.size());android.util.Log.i("MovieBoxPerf","captured_native_rows=19 exact_subject_banners="+found.size());Map<String,JSONObject> rows=new HashMap<>();JSONArray banners=response.getJSONArray("items").getJSONObject(0).getJSONObject("banner").getJSONArray("banners");assertEquals(19,banners.length());for(int n=0;n<banners.length();n++){JSONObject row=banners.getJSONObject(n);if(!row.optString("subjectId").equals("0"))rows.put(row.optString("subjectId"),row);}
  for(JSONObject title:found){assertNotEquals("0",title.optString("subjectId"));JSONObject row=rows.get(title.getString("subjectId"));assertNotNull(row);JSONObject original=row.getJSONObject("subject");assertEquals(original.getString("title"),title.getString("title"));assertEquals(original.getString("releaseDate"),title.getString("releaseDate"));assertEquals(original.getInt("subjectType"),title.getInt("subjectType"));assertEquals(row.getJSONObject("image").getString("url"),title.getJSONObject("banner").getString("url"));assertEquals(title.getString("subjectId"),title.getJSONObject("banner").getString("subjectId"));}
 }
 @Test public void oneWideHeroDoesNotHideTheOtherLatestTitles()throws Exception{
  List<JSONObject> titles=new ArrayList<>();for(int n=0;n<100;n++)titles.add(BlockFlowScenarios.title("qa-all-latest-"+n,1));
  titles.get(0).put("banner",new JSONObject().put("url","https://127.0.0.1:1/wide.webp").put("width",640).put("height",360).put("subjectId","qa-all-latest-0"));
  BlockFlowScenarios.FakeApi service=new BlockFlowScenarios.FakeApi(){@Override public List<JSONObject> home(){return titles;}};
  try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
   BlockFlowScenarios.isolate(scenario,service);main(scenario,a->call(a,"browse",new Class[]{String.class},"Discover"));
   waitFor(()->{int[] count={0};main(scenario,a->{RecyclerView row=HomeCarouselScenarios.recycler((View)field(a,"root"));if(row!=null)count[0]=row.getAdapter().getItemCount();});return count[0]==100;},5000);
   main(scenario,a->{assertEquals("https://127.0.0.1:1/wide.webp",call(a,"bannerUrl",new Class[]{JSONObject.class},titles.get(0)));assertEquals("",call(a,"bannerUrl",new Class[]{JSONObject.class},titles.get(1)));});
  }
 }
 @Test public void mixedGridKeepsPortraitFramesThroughAppend()throws Exception{
  try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
   BlockFlowScenarios.isolate(scenario,new BlockFlowScenarios.FakeApi());
   List<JSONObject> batch=new ArrayList<>();for(int n=0;n<12;n++)batch.add(BlockFlowScenarios.title("qa-grid-"+n,1).put("cover",new JSONObject().put("url","https://invalid.test/owned"+n).put("width",300).put("height",n%3==0?169:n%3==1?300:450)));
   main(scenario,a->{put(a,"route","Search");call(a,"appShell",new Class[]{String.class},"Search");call(a,"showTitles",new Class[]{List.class,boolean.class},batch,false);RecyclerView grid=(RecyclerView)field(a,"grid");assertTrue(grid.getLayoutManager() instanceof GridLayoutManager);Object adapter=grid.getAdapter();assertFalse((Boolean)field(adapter,"landscapeCards"));call(a,"showTitles",new Class[]{List.class,boolean.class},Collections.singletonList(BlockFlowScenarios.title("qa-new-wide",1)),true);assertSame(adapter,grid.getAdapter());assertFalse((Boolean)field(adapter,"landscapeCards"));});
   Thread.sleep(250);main(scenario,a->{RecyclerView grid=(RecyclerView)field(a,"grid");int height=-1;for(int n=0;n<grid.getChildCount();n++){View card=grid.getChildAt(n);if(height<0)height=card.getHeight();assertEquals("Uniform card geometry",height,card.getHeight());}assertTrue(grid.getChildCount()>1);});
  }
 }
}
