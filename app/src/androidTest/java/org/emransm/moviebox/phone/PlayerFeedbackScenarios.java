package org.emransm.moviebox.phone;

import android.app.Dialog;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.drawable.InsetDrawable;
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

/** Actual view measurements and owned media only. Never run on the owner phone. */
@RunWith(AndroidJUnit4.class)
public class PlayerFeedbackScenarios {
    static class OwnedLocal extends SliderPlaybackScenarios.Local implements AutoCloseable {
        final android.graphics.Bitmap[] posters=new android.graphics.Bitmap[6];
        OwnedLocal()throws Exception{android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inPreferredConfig=android.graphics.Bitmap.Config.RGB_565;for(int i=0;i<6;i++)posters[i]=android.graphics.Bitmap.createScaledBitmap(android.graphics.BitmapFactory.decodeFile(OwnedQaArtwork.copy("release-poster-"+i+".png").toString(),options),240,360,true);}
        JSONObject decorate(JSONObject item,int index)throws Exception{return item.put("cover",new JSONObject().put("url","https://original-demo.invalid/poster-"+((index%6+6)%6)+".png").put("width",480).put("height",720));}
        @Override public JSONObject detail(JSONObject item)throws Exception{return decorate(super.detail(item),item.optString("subjectId").hashCode());}
        @Override public WatchRecommendations.Page recommendationsPage(String id,int page)throws Exception{List<JSONObject> rows=new ArrayList<>();if(page==1)for(int i=0;i<6;i++)rows.add(decorate(BlockFlowScenarios.title("owned-ui-rec-"+i,1),i));return new WatchRecommendations.Page(id,false,page,rows,false,0);}
        void prime(ActivityScenario<PhoneActivity> scenario){main(scenario,a->{android.util.LruCache<String,android.graphics.Bitmap> cache=(android.util.LruCache<String,android.graphics.Bitmap>)field(a,"bitmapCache");for(int i=0;i<6;i++)for(int width:new int[]{160,360})cache.put("https://original-demo.invalid/poster-"+i+".png@"+width,posters[i]);});}
    }
    private ActivityScenario<PhoneActivity> open(SliderPlaybackScenarios.Local service,String id,int type)throws Exception{
        ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class);BlockFlowScenarios.isolate(s,service);if(service instanceof OwnedLocal)((OwnedLocal)service).prime(s);
        main(s,a->call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},BlockFlowScenarios.title(id,type),true));waitFor(()->playing(s),12000);main(s,a->((androidx.media3.exoplayer.ExoPlayer)field(a,"player")).pause());Thread.sleep(3600);return s;
    }
    @Test public void resourcePillsAndSeasonEpisodeRailShareCompactUnclippedTouchGeometry()throws Exception{
        try(OwnedLocal service=new OwnedLocal();ActivityScenario<PhoneActivity> s=open(service,"qa-feedback-rail",2)){
            main(s,a->{View root=(View)field(a,"watchInfo");float d=a.getResources().getDisplayMetrics().density;int[] audioAt=new int[2];View audio=description(root,"Audio");audio.getLocationOnScreen(audioAt);
                for(String name:new String[]{"Audio","Quality","Subtitles","More"}){View chip=description(root,name);assertNotNull(name,chip);assertTrue(chip.getBackground() instanceof InsetDrawable);assertEquals(Math.round(48*d),chip.getHeight());int[] at=new int[2];chip.getLocationOnScreen(at);assertEquals(audioAt[1],at[1]);}
                assertTrue(BlockFlowScenarios.hasText(audio,"Audio"));assertFalse(BlockFlowScenarios.hasText(audio,"Audio \u2304"));
                View season=description(root,"Choose season"),ep=description(root,"Play season 1 episode 2");HorizontalScrollView scroll=(HorizontalScrollView)description(root,"Episode choices");assertNotNull(scroll);assertTrue(season.getParent()==scroll.getParent());
                int[] seAt=new int[2],epAt=new int[2];season.getLocationOnScreen(seAt);ep.getLocationOnScreen(epAt);assertEquals(seAt[1]+season.getHeight()/2f,epAt[1]+ep.getHeight()/2f,1f);assertEquals(Math.round(48*d),ep.getHeight());assertTrue(ep.getBackground() instanceof InsetDrawable);
                Rect visual=new Rect();assertTrue(ep.getGlobalVisibleRect(visual));assertEquals("No clipped upper or lower edge",ep.getHeight(),visual.height());assertTrue(scroll.getPaddingTop()>=Math.round(2*d));
                ((androidx.media3.exoplayer.ExoPlayer)field(a,"player")).pause();call(a,"showControls",new Class[]{boolean.class},true);
                ViewGroup header=(ViewGroup)field(a,"playerTopbar");assertTrue(header.getBackground() instanceof android.graphics.drawable.ColorDrawable);assertEquals(android.graphics.Color.TRANSPARENT,((android.graphics.drawable.ColorDrawable)header.getBackground()).getColor());assertTrue(header.getChildAt(0).getBackground() instanceof InsetDrawable);assertEquals(Math.round(48*d),header.getChildAt(0).getHeight());assertEquals(0,((android.widget.FrameLayout.LayoutParams)header.getLayoutParams()).topMargin);
            });Thread.sleep(150);capture(s,"qa-feedback-resource-episode-rail.png");capture(s,"qa-feedback-portrait-paused-header.png");
        }
    }
    private void dismissImmersiveTutorial()throws Exception{
        android.app.UiAutomation automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation();android.accessibilityservice.AccessibilityServiceInfo info=automation.getServiceInfo();info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;automation.setServiceInfo(info);
        long until=android.os.SystemClock.uptimeMillis()+2500;while(android.os.SystemClock.uptimeMillis()<until){List<android.view.accessibility.AccessibilityNodeInfo> roots=new ArrayList<>();android.view.accessibility.AccessibilityNodeInfo active=automation.getRootInActiveWindow();if(active!=null)roots.add(active);for(android.view.accessibility.AccessibilityWindowInfo window:automation.getWindows()){android.view.accessibility.AccessibilityNodeInfo root=window.getRoot();if(root!=null)roots.add(root);}for(android.view.accessibility.AccessibilityNodeInfo root:roots)for(String label:new String[]{"GOT IT","Got it"})for(android.view.accessibility.AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(label)){String owner=String.valueOf(node.getPackageName());if(owner.equals("android")||owner.equals("com.android.systemui")){assertTrue("Dismiss only Android's first-use immersive tutorial",node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK));Thread.sleep(300);return;}}Thread.sleep(100);}
    }
    @Test public void landscapeUsesTheSameHorizontalBoundsForHeaderSeekAndTransport()throws Exception{
        try(OwnedLocal service=new OwnedLocal();ActivityScenario<PhoneActivity> s=open(service,"qa-feedback-edge",2)){
            main(s,a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));waitFor(()->{boolean[] yes={false};main(s,a->yes[0]=a.getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE);return yes[0];},6000);Thread.sleep(250);
            main(s,a->{call(a,"showControls",new Class[]{boolean.class},true);ViewGroup top=(ViewGroup)field(a,"playerTopbar"),controls=(ViewGroup)field(a,"controls");SeekBar seek=(SeekBar)field(a,"seek");View transport=(View)field(a,"playerTransport");assertEquals("Header starts at video edge",0,((FrameLayout.LayoutParams)top.getLayoutParams()).topMargin);assertTrue(top.getBackground() instanceof android.graphics.drawable.ColorDrawable);assertEquals("No full-width fullscreen top strip",android.graphics.Color.TRANSPARENT,((android.graphics.drawable.ColorDrawable)top.getBackground()).getColor());assertEquals(top.getPaddingLeft(),controls.getPaddingLeft());assertEquals(top.getPaddingRight(),controls.getPaddingRight());assertEquals(controls.getPaddingLeft(),seek.getLeft());assertEquals(controls.getWidth()-controls.getPaddingRight(),seek.getRight());assertEquals(0,seek.getPaddingLeft());assertEquals(0,seek.getPaddingRight());assertEquals("Clock starts at transport left",0,((ViewGroup)transport).getChildAt(0).getLeft());});dismissImmersiveTutorial();capture(s,"qa-feedback-landscape-edges.png");
            main(s,a->call(a,"showEpisodeDrawer",new Class[]{}));Thread.sleep(250);capture(s,"qa-feedback-landscape-episodes.png");
            main(s,a->{call(a,"hideEpisodeDrawer",new Class[]{});call(a,"audioDialog",new Class[]{});});Thread.sleep(250);capture(s,"qa-feedback-landscape-audio.png");
        }
    }
    @Test public void audioSheetUsesHumanEditionNamesWithoutImplementationJargon()throws Exception{
        try(OwnedLocal service=new OwnedLocal();ActivityScenario<PhoneActivity> s=open(service,"qa-feedback-audio",1)){
            main(s,a->{JSONArray dubs=new JSONArray();for(String code:new String[]{"esla","ptbr","esla"})dubs.put(new JSONObject().put("type",0).put("subjectId","dub-"+code).put("lanName",code+" dub").put("lanCode",code));((JSONObject)field(a,"subject")).put("dubs",dubs);call(a,"audioDialog",new Class[]{});Map<Dialog,Runnable> sheets=(Map<Dialog,Runnable>)field(a,"openSheets");assertEquals(1,sheets.size());View sheet=sheets.keySet().iterator().next().getWindow().getDecorView();assertTrue(BlockFlowScenarios.hasText(sheet,"Spanish (Latin America)"));assertTrue(BlockFlowScenarios.hasText(sheet,"Portuguese (Brazil)"));assertFalse(BlockFlowScenarios.hasText(sheet,"esla dub"));assertFalse(BlockFlowScenarios.hasText(sheet,"ptbr dub"));});capture(s,"qa-feedback-audio-human-labels.png");
        }
    }
    @Test public void verticalScrollAppendsRealPagesOnceAndStopsAtFifty()throws Exception{
        class Paged extends OwnedLocal {final AtomicInteger calls=new AtomicInteger();Paged()throws Exception{}@Override public WatchRecommendations.Page recommendationsPage(String id,int page)throws Exception{calls.incrementAndGet();List<JSONObject> rows=new ArrayList<>();for(int n=0;n<12;n++)rows.add(decorate(BlockFlowScenarios.title("rec-"+((page-1)*12+n),1),n));return new WatchRecommendations.Page(id,false,page,rows,true,page+1);}}
        try(Paged service=new Paged();ActivityScenario<PhoneActivity> s=open(service,"qa-feedback-pages",1)){
            waitFor(()->count(s)==12,5000);assertEquals(1,service.calls.get());
            for(int expected:new int[]{24,36,48,50}){main(s,a->((ScrollView)field(a,"watchInfo")).fullScroll(View.FOCUS_DOWN));waitFor(()->count(s)==expected,5000);Thread.sleep(100);}
            main(s,a->((ScrollView)field(a,"watchInfo")).fullScroll(View.FOCUS_DOWN));Thread.sleep(250);assertEquals(50,count(s));assertEquals(5,service.calls.get());capture(s,"qa-feedback-fifty-recommendations.png");
        }
    }
    @Test public void delayedScrollRestorationCannotMoveReplacementWatchPage()throws Exception{
        try(OwnedLocal service=new OwnedLocal();ActivityScenario<PhoneActivity> s=open(service,"qa-feedback-scroll-race",1)){
            main(s,a->{StringBuilder summary=new StringBuilder();for(int i=0;i<100;i++)summary.append("Owned long description line ").append(i).append("\n");((JSONObject)field(a,"subject")).put("description",summary.toString());call(a,"refreshWatchInfo",new Class[]{});});Thread.sleep(200);
            Object[] replaced={null};main(s,a->{ScrollView old=(ScrollView)field(a,"watchInfo");old.scrollTo(0,200);assertEquals(200,old.getScrollY());call(a,"refreshWatchInfo",new Class[]{});call(a,"layoutWatchPage",new Class[]{});ScrollView newer=(ScrollView)field(a,"watchInfo");assertNotSame(old,newer);replaced[0]=newer;View root=(View)field(a,"root");int w=root.getWidth(),h=root.getHeight();root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h);newer.scrollTo(0,0);});Thread.sleep(200);
            main(s,a->{assertSame(replaced[0],field(a,"watchInfo"));assertEquals("Old restore must not scroll the replacement page",0,((ScrollView)replaced[0]).getScrollY());});
        }
    }
    @Test public void lateRecommendationFailuresCannotMarkAnotherTitleApiOrBackgroundPage()throws Exception{
        for(String change:new String[]{"title","api","background"}){
            class Late extends OwnedLocal {final java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);Late()throws Exception{}@Override public WatchRecommendations.Page recommendationsPage(String id,int page)throws Exception{if(id.equals("qa-late-"+change)){entered.countDown();release.await(10,java.util.concurrent.TimeUnit.SECONDS);throw new java.io.IOException("Owned delayed failure");}return new WatchRecommendations.Page(id,false,page,Collections.singletonList(BlockFlowScenarios.title("current-rec",1)),false,0);}}
            try(Late service=new Late();OwnedLocal replacement=new OwnedLocal();ActivityScenario<PhoneActivity> s=open(service,"qa-late-"+change,1)){
                assertTrue(service.entered.await(3,java.util.concurrent.TimeUnit.SECONDS));main(s,a->{if(change.equals("title"))call(a,"openDetail",new Class[]{JSONObject.class,boolean.class},BlockFlowScenarios.title("qa-next-title",1),true);else if(change.equals("api"))put(a,"api",replacement);else put(a,"stopped",true);});service.release.countDown();Thread.sleep(350);
                main(s,a->{assertFalse("A stale optional failure is not a current error: "+change,(Boolean)field(a,"recommendationsFailed"));if(change.equals("background"))put(a,"stopped",false);});
            }
        }
    }
    private void capture(ActivityScenario<PhoneActivity> scenario,String name)throws Exception{
        main(scenario,a->{UpdateVisualScenarios.redraw(a.getWindow().getDecorView());Map<Dialog,Runnable> sheets=(Map<Dialog,Runnable>)field(a,"openSheets");for(Dialog dialog:sheets.keySet())if(dialog.getWindow()!=null)UpdateVisualScenarios.redraw(dialog.getWindow().getDecorView());});Thread.sleep(450);BlockFlowScenarios.capture(name);
    }
    private int count(ActivityScenario<PhoneActivity> s){int[] count={0};main(s,a->count[0]=((WatchRecommendations)field(a,"watchRecommendations")).forTitle("qa-feedback-pages",false).size());return count[0];}
}
