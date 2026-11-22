package org.emransm.moviebox.phone;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.util.LruCache;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

/** Actual captured public image bytes, isolated feed/API and bitmap cache; no user history writes. */
@RunWith(AndroidJUnit4.class) public class ArtworkVisualScenarios {
 static ImageView tagged(View root,String key){if(root instanceof ImageView&&key.equals(root.getTag()))return(ImageView)root;if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){ImageView found=tagged(((ViewGroup)root).getChildAt(n),key);if(found!=null)return found;}return null;}
 static Bitmap captured(String name)throws Exception{try(InputStream input=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(name)){byte[] bytes=Api.read(input,2*1024*1024);BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(bounds.outWidth/options.inSampleSize>480||bounds.outHeight/options.inSampleSize>600)options.inSampleSize*=2;options.inPreferredConfig=Bitmap.Config.RGB_565;Bitmap image=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);assertNotNull(image);return image;}}
 static void seed(PhoneActivity activity,String url,Bitmap bitmap)throws Exception{LruCache<String,Bitmap> cache=(LruCache<String,Bitmap>)field(activity,"bitmapCache");cache.evictAll();for(int width:new int[]{160,640,960})cache.put(url+"@"+width,bitmap);assertSame(bitmap,cache.get(url+"@640"));assertSame(bitmap,cache.get(url+"@960"));android.util.Log.i("MovieBoxPerf","owned_fixture_bitmap="+bitmap.getWidth()+"x"+bitmap.getHeight()+" bytes="+bitmap.getByteCount()+" cache="+cache.size()+"/"+cache.maxSize());}
 void render(JSONObject item,String asset,String prefix)throws Exception{
  Bitmap bitmap=captured(asset);String url=item.has("banner")?item.getJSONObject("banner").getString("url"):item.getJSONObject("cover").getString("url");
  BlockFlowScenarios.FakeApi service=new BlockFlowScenarios.FakeApi(){@Override public List<JSONObject> home(){return Collections.singletonList(item);}@Override public JSONObject detail(JSONObject input)throws Exception{return new JSONObject(input.toString());}};
  try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
   BlockFlowScenarios.isolate(scenario,service);main(scenario,a->{seed(a,url,bitmap);call(a,"browse",new Class[]{String.class},"Discover");});
   waitFor(()->{boolean[] shown={false};main(scenario,a->shown[0]=tagged((View)field(a,"root"),url+"@960")!=null||tagged((View)field(a,"root"),url+"@640")!=null);return shown[0];},5000);
   main(scenario,a->{ImageView image=tagged((View)field(a,"root"),url+"@960");if(image==null)image=tagged((View)field(a,"root"),url+"@640");assertEquals(ImageView.ScaleType.CENTER_CROP,image.getScaleType());assertTrue("Actual captured bitmap must be shown, not the neutral placeholder",image.getDrawable() instanceof android.graphics.drawable.BitmapDrawable);assertSame(bitmap,((android.graphics.drawable.BitmapDrawable)image.getDrawable()).getBitmap());assertTrue(image.isShown());assertTrue(image.getWidth()>0&&image.getHeight()>0);View play=description((View)field(a,"root"),"Play "+item.optString("title"));assertNotNull(play);int[] artAt=new int[2],playAt=new int[2];image.getLocationOnScreen(artAt);play.getLocationOnScreen(playAt);assertTrue("Existing play/title row must not obscure hero artwork",artAt[1]+image.getHeight()<=playAt[1]);assertTrue(play.isClickable());assertTrue(play.getWidth()>=48);});
   BlockFlowScenarios.capture(prefix+"-home.png");
   if(item.has("cover")){main(scenario,a->{((LruCache<String,Bitmap>)field(a,"bitmapCache")).put(url+"@360",bitmap);call(a,"openDetail",new Class[]{JSONObject.class},item);});Thread.sleep(200);BlockFlowScenarios.capture(prefix+"-detail.png");}
  }
 }
 @Test public void actualDatangArtworkFillsPhoneHeroAndDetailWithoutStretching()throws Exception{
  CapturedArtworkFixtures.require("actual-datang-poster.webp");
  JSONObject subject=new JSONObject().put("subjectId","577120636984879608").put("title","Datang Akan Pergi").put("subjectType",1).put("releaseDate","2026-12-24").put("cover",new JSONObject().put("url","https://pbcdn.aoneroom.com/image/2026/10/22/3a19672114efd688ae288ba71daec3bd.jpg").put("width",3276).put("height",4096));
  render(subject,"actual-datang-poster.webp","qa-actual-datang");
 }
 @Test public void actualNativeBannerUsesItsOwnExactSubject()throws Exception{
  CapturedArtworkFixtures.require("native-banner-feed.json","actual-native-banner.webp");
  JSONObject response;try(InputStream input=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("native-banner-feed.json")){response=new JSONObject(Utf8Json.decode(Api.read(input,256000)));}
  JSONObject row=response.getJSONArray("items").getJSONObject(0).getJSONObject("banner").getJSONArray("banners").getJSONObject(1);JSONObject subject=new JSONObject(row.getJSONObject("subject").toString());subject.put("banner",new JSONObject(row.getJSONObject("image").toString()).put("subjectId",subject.getString("subjectId")));
  render(subject,"actual-native-banner.webp","qa-actual-native-banner");
 }
 @Test public void requestedGridCropFillsExistingPortraitFramesWithoutStretching()throws Exception{
  CapturedArtworkFixtures.require("actual-wide-cover.webp","actual-datang-poster.webp");
  String wide="https://pbcdnw.aoneroom.com/image/2026/11/21/674cf4c7566ccc5f1fbffde2c2c3d1ba.jpg",tall="https://pbcdn.aoneroom.com/image/2026/10/22/3a19672114efd688ae288ba71daec3bd.jpg";
  List<JSONObject> titles=Arrays.asList(new JSONObject().put("subjectId","8492453872672328368").put("title","OJU EJU").put("subjectType",1).put("cover",new JSONObject().put("url",wide).put("width",1280).put("height",720)),new JSONObject().put("subjectId","577120636984879608").put("title","Datang Akan Pergi").put("subjectType",1).put("cover",new JSONObject().put("url",tall).put("width",3276).put("height",4096)));
  Bitmap wideImage=captured("actual-wide-cover.webp"),tallImage=captured("actual-datang-poster.webp");
  try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
   BlockFlowScenarios.isolate(scenario,new BlockFlowScenarios.FakeApi());
   main(scenario,a->{LruCache<String,Bitmap> cache=(LruCache<String,Bitmap>)field(a,"bitmapCache");cache.evictAll();cache.put(wide+"@360",wideImage);cache.put(tall+"@360",tallImage);put(a,"route","Movies");call(a,"appShell",new Class[]{String.class},"Movies");call(a,"showTitles",new Class[]{List.class,boolean.class},titles,false);});
   Thread.sleep(300);main(scenario,a->{int height=-1;for(String url:Arrays.asList(wide,tall)){ImageView image=tagged((View)field(a,"root"),url+"@360");assertNotNull(image);assertEquals(ImageView.ScaleType.CENTER_CROP,image.getScaleType());assertTrue(image.getDrawable() instanceof android.graphics.drawable.BitmapDrawable);if(height<0)height=image.getHeight();assertEquals(height,image.getHeight());RectF mapped=new RectF(0,0,image.getDrawable().getIntrinsicWidth(),image.getDrawable().getIntrinsicHeight());image.getImageMatrix().mapRect(mapped);assertTrue(mapped.left<=1&&mapped.top<=1&&mapped.right>=image.getWidth()-1&&mapped.bottom>=image.getHeight()-1);float[] values=new float[9];image.getImageMatrix().getValues(values);assertEquals("Crop scales proportionally",values[Matrix.MSCALE_X],values[Matrix.MSCALE_Y],.001f);}});
   BlockFlowScenarios.capture("qa-requested-grid-crop.png");
  }
 }
}
