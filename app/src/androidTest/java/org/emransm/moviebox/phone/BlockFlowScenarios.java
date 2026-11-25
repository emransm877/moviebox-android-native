package org.emransm.moviebox.phone;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Rect;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.widget.TextView;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.media3.ui.PlayerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

/** Isolated native block flows; network replaced by deterministic API responses. */
@RunWith(AndroidJUnit4.class)
public class BlockFlowScenarios {
    static JSONObject title(String id,int type)throws Exception{return new JSONObject().put("subjectId",id).put("title","Owned block "+id).put("subjectType",type).put("releaseDate","2026-01-01").put("description","Generated block-flow fixture.");}
    static class FakeApi extends Api {
        final AtomicInteger searches=new AtomicInteger(),catalogues=new AtomicInteger();volatile int kind;volatile JSONObject sentFilters=new JSONObject();volatile boolean fail;
        FakeApi()throws Exception{super("0123456789abcdef0123456789abcdef");}
        public List<JSONObject> home() throws Exception{return Collections.emptyList();}
        @Override public JSONObject searchLanding()throws Exception{return new JSONObject();}
        public List<String> suggest(String word){return Collections.emptyList();}
        public List<JSONObject> search(String word)throws Exception{searches.incrementAndGet();if(fail)throw new IOException("Search failed. Retry.");return word.equals("empty")?Collections.emptyList():Collections.singletonList(title("search-result",1));}
        public List<JSONObject> catalogue(int type,int page,JSONObject filters)throws Exception{kind=type;sentFilters=new JSONObject(filters.toString());catalogues.incrementAndGet();if(page>1)return Collections.emptyList();List<JSONObject> result=new ArrayList<>();for(int i=0;i<12;i++)result.add(title("catalogue-"+type+"-"+i,type));return result;}
        public List<JSONObject> playback(JSONObject subject,int season,int episode)throws Exception{return Arrays.asList(new JSONObject().put("resolution",1080).put("resourceLink","https://example.invalid/owned1080.mp4"),new JSONObject().put("resolution",720).put("resourceLink","https://example.invalid/owned720.mp4"));}
    }
    static void isolate(ActivityScenario<PhoneActivity> s,FakeApi api){main(s,a->{call(a,"releasePlayer",new Class[]{});put(a,"generation",(Integer)field(a,"generation")+1);((java.util.concurrent.ThreadPoolExecutor)field(a,"network")).getQueue().clear();((Library)field(a,"library")).close();File dir=new File(a.getCacheDir(),"agency-block-owned-"+System.nanoTime());assertTrue(dir.mkdirs());Context wrapper=new ContextWrapper(a){@Override public File getFilesDir(){return dir;}};put(a,"library",new Library(wrapper));put(a,"api",api);((Map<?,?>)field(a,"catalogueCache")).clear();((Map<?,?>)field(a,"cacheTimes")).clear();});}
    static boolean hasText(View view,String wanted){if(view instanceof TextView&&wanted.contentEquals(((TextView)view).getText()))return true;if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++)if(hasText(((ViewGroup)view).getChildAt(n),wanted))return true;return false;}
    static void clickRoot(ActivityScenario<PhoneActivity> s,String desc){main(s,a->{View view=description((View)field(a,"root"),desc);assertNotNull("Control exists "+desc,view);assertTrue(view.performClick());});}
    static void click(String label)throws Exception{FullPlaybackScenarios.click(label);}
    static void capture(String name)throws Exception{InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(250);android.graphics.Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();if(image==null){android.os.ParcelFileDescriptor fd=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand("screencap -p");try(InputStream input=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)){image=android.graphics.BitmapFactory.decodeStream(input);}}assertNotNull("A real screenshot is required (UiAutomation or SurfaceFlinger)",image);try(FileOutputStream out=new FileOutputStream(new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),name))){assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}finally{image.recycle();}}
    static void back()throws Exception{InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
    @Test public void searchEmptyFailureRetryAndClearKeepUsableNativeFlow()throws Exception{
        FakeApi api=new FakeApi();try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            isolate(s,api);main(s,a->{call(a,"browse",new Class[]{String.class},"Search");call(a,"runSearch",new Class[]{String.class},"empty");});
            waitFor(()->{boolean[] found={false};main(s,a->found[0]=hasText((View)field(a,"root"),"No matches. Try another title."));return found[0];},5000);
            capture("qa-block-search-empty.png");api.fail=true;main(s,a->call(a,"runSearch",new Class[]{String.class},"retry"));waitFor(()->{boolean[] found={false};main(s,a->found[0]=((TextView)field(a,"status")).getText().toString().contains("Search failed"));return found[0];},5000);
            capture("qa-block-search-error.png");api.fail=false;clickRoot(s,"Retry search");waitFor(()->{boolean[] found={false};main(s,a->found[0]=description((View)field(a,"root"),"Owned block search-result")!=null);return found[0];},5000);
            capture("qa-block-search-result.png");clickRoot(s,"Clear search");main(s,a->{assertEquals("",field(a,"query"));assertTrue(hasText((View)field(a,"root"),"Find your next watch"));assertNull(description((View)field(a,"root"),"Owned block search-result"));});assertEquals(3,api.searches.get());
        }
    }
    @Test public void movieAndTvFiltersDraftCancelApplyAndReset()throws Exception{
        FakeApi api=new FakeApi();try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            isolate(s,api);
            for(String route:new String[]{"Movies","TV Shows"}){
                main(s,a->{put(a,"filters",new JSONObject());call(a,"browse",new Class[]{String.class},route);});waitFor(()->api.kind==(route.equals("Movies")?1:2),5000);
                main(s,a->call(a,"filterDialog",new Class[]{}));click("Genre");click("Action");back();main(s,a->assertFalse("Cancel must not commit filter draft",((JSONObject)field(a,"filters")).has("genre")));
                main(s,a->call(a,"filterDialog",new Class[]{}));click("Genre");click("Action");Thread.sleep(2200);AccessibilityNodeInfo sheet=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();List<AccessibilityNodeInfo> applies=sheet.findAccessibilityNodeInfosByText("Apply filters");assertFalse(applies.isEmpty());Rect applyBounds=new Rect();applies.get(0).getBoundsInScreen(applyBounds);int[] minimum={0};main(s,a->minimum[0]=Math.round(47*a.getResources().getDisplayMetrics().density));assertTrue("Filter Apply stays fully visible without scrolling",applyBounds.height()>=minimum[0]);capture("qa-block-filter-selected.png");click("Apply filters");waitFor(()->"Action".equals(api.sentFilters.optString("genre")),5000);main(s,a->{assertEquals("Action",((JSONObject)field(a,"filters")).optString("genre"));assertEquals(route,field(a,"route"));});
                main(s,a->call(a,"filterDialog",new Class[]{}));click("Reset");main(s,a->assertEquals("Reset clears all active fields",0,((JSONObject)field(a,"filters")).length()));
            }
        }
    }
    @Test public void movieDetailsExcludeSeasonsAndSeriesSeasonChoiceUpdatesEpisodes()throws Exception{
        FakeApi api=new FakeApi();try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            isolate(s,api);main(s,a->{put(a,"subject",title("movie",1));call(a,"appShell",new Class[]{String.class},"Details");call(a,"renderDetail",new Class[]{});assertFalse(hasText((View)field(a,"root"),"Episodes"));assertTrue(((TreeMap<?,?>)field(a,"seasons")).isEmpty());assertNotNull(description((View)field(a,"root"),"Watch or resume"));});
            main(s,a->{put(a,"subject",title("series",2).put("seasons",new JSONArray().put(new JSONObject().put("se",1).put("allEp","2,5")).put(new JSONObject().put("se",3).put("allEp","4,9"))));call(a,"renderDetail",new Class[]{});call(a,"episodeMenu",new Class[]{boolean.class},false);});capture("qa-block-season-choice.png");click("Season 3");main(s,a->{assertEquals(3,field(a,"season"));assertEquals(4,field(a,"episode"));assertNotNull(description((View)field(a,"root"),"Play episode 4, selected"));assertNotNull(description((View)field(a,"root"),"Play episode 9"));assertNull(description((View)field(a,"root"),"Play episode 5"));});
        }
    }
    @Test public void subtitleSizeChoicePersistsAndDownloadChoicesStayBounded()throws Exception{
        FakeApi api=new FakeApi();try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            isolate(s,api);main(s,a->call(a,"browse",new Class[]{String.class},"Settings"));click("Subtitle size");capture("qa-block-subtitle-size.png");click("28 sp");main(s,a->{assertEquals(28,((Library)field(a,"library")).settings().optInt("subtitleSize"));assertTrue(hasText((View)field(a,"root"),"28 sp"));});
            start(s,Uri.fromFile(fixture()).toString());waitFor(()->playing(s),15000);main(s,a->{Object subtitles=((PlayerView)field(a,"video")).getSubtitleView();assertNotNull(subtitles);assertEquals("Player applies saved subtitle size",android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,28f,a.getResources().getDisplayMetrics()),((Number)field(subtitles,"defaultTextSize")).floatValue(),.1f);call(a,"leavePlayer",new Class[]{});put(a,"api",api);call(a,"requestDownload",new Class[]{});});
            waitFor(()->{AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();return root!=null&&!root.findAccessibilityNodeInfosByText("Download quality").isEmpty();},5000);
            AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();assertFalse(root.findAccessibilityNodeInfosByText("1080p").isEmpty());assertFalse(root.findAccessibilityNodeInfosByText("720p").isEmpty());Rect bounds=new Rect();root.getBoundsInScreen(bounds);int[] screen={0};main(s,a->screen[0]=a.getResources().getDisplayMetrics().heightPixels);assertTrue("Download sheet does not consume entire display",bounds.height()<screen[0]*.86f);capture("qa-block-download-qualities.png");back();main(s,a->assertNull("Cancel download quality must not launch player",field(a,"player")));
        }
    }
}
