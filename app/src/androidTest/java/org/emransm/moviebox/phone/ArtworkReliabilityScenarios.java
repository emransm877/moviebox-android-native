package org.emransm.moviebox.phone;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.*;
import android.util.LruCache;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

/** Owned bitmap fixtures; no live artwork service or download counters are used. */
@RunWith(AndroidJUnit4.class)
public class ArtworkReliabilityScenarios {
    private static final class Fixture {
        ArtworkLoader loader;LinearLayout body;List<ImageView> views=new ArrayList<>();
        void install(PhoneActivity activity,ArtworkLoader.Fetcher fetcher){body=new LinearLayout(activity);activity.setContentView(body);loader=new ArtworkLoader(new Handler(Looper.getMainLooper()),new LruCache<>(8),fetcher,new ColorDrawable(Color.DKGRAY));}
        ImageView target(PhoneActivity activity){ImageView view=new ImageView(activity);body.addView(view,new LinearLayout.LayoutParams(4,4));views.add(view);return view;}
    }
    private static Bitmap bitmap(int color){Bitmap value=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888);value.eraseColor(color);return value;}
    @Test public void newlyAttachedDemandUsesCacheWarmedAfterBinding()throws Exception{
        AtomicInteger fetches=new AtomicInteger(),done=new AtomicInteger();Fixture f=new Fixture();LruCache<String,Bitmap> cache=new LruCache<>(8);ImageView[] target={null};
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            main(s,a->{f.body=new LinearLayout(a);a.setContentView(f.body);f.loader=new ArtworkLoader(new Handler(Looper.getMainLooper()),cache,(url,width)->{fetches.incrementAndGet();return bitmap(Color.RED);},new ColorDrawable(Color.DKGRAY));target[0]=new ImageView(a);f.loader.load(target[0],"https://owned.invalid/warmed",40,done::incrementAndGet);cache.put("https://owned.invalid/warmed@40",bitmap(Color.BLUE));f.body.addView(target[0],new LinearLayout.LayoutParams(4,4));});
            waitFor(()->done.get()==1,3000);assertEquals(0,fetches.get());main(s,a->{assertEquals(Color.BLUE,((android.graphics.drawable.BitmapDrawable)target[0].getDrawable()).getBitmap().getPixel(0,0));f.loader.close();});
        }
    }
    @Test public void lowMemoryWorkerPolicyAndWidthVariantsStayIndependent()throws Exception{
        AtomicInteger active=new AtomicInteger(),peak=new AtomicInteger(),done=new AtomicInteger();List<Integer> widths=Collections.synchronizedList(new ArrayList<>());Fixture f=new Fixture();
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            main(s,a->{f.body=new LinearLayout(a);a.setContentView(f.body);ThreadPoolExecutor single=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(24),new ThreadPoolExecutor.AbortPolicy());f.loader=new ArtworkLoader(new Handler(Looper.getMainLooper()),new LruCache<>(8),(url,width)->{widths.add(width);int n=active.incrementAndGet();peak.accumulateAndGet(n,Math::max);try{Thread.sleep(40);return bitmap(width==40?Color.RED:Color.BLUE);}finally{active.decrementAndGet();}},new ColorDrawable(Color.DKGRAY),single);f.loader.load(f.target(a),"https://owned.invalid/variant",40,done::incrementAndGet);f.loader.load(f.target(a),"https://owned.invalid/variant",80,done::incrementAndGet);});
            waitFor(()->done.get()==2,3000);assertEquals(1,peak.get());assertTrue(widths.containsAll(Arrays.asList(40,80)));
            main(s,a->{for(int i=0;i<2;i++){android.graphics.drawable.BitmapDrawable image=(android.graphics.drawable.BitmapDrawable)f.views.get(i).getDrawable();assertEquals(i==0?Color.RED:Color.BLUE,image.getBitmap().getPixel(0,0));}f.loader.close();});
        }
    }
    @Test public void saturatedDistinctVisibleDemandDrainsWithOnlyTwoFetches()throws Exception{
        AtomicInteger active=new AtomicInteger(),peak=new AtomicInteger(),done=new AtomicInteger();Fixture f=new Fixture();
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            main(s,a->{f.install(a,(url,width)->{assertNotEquals(Looper.getMainLooper(),Looper.myLooper());int n=active.incrementAndGet();peak.accumulateAndGet(n,Math::max);try{Thread.sleep(15);return bitmap(Color.GREEN);}finally{active.decrementAndGet();}});for(int i=0;i<70;i++)f.loader.load(f.target(a),"https://owned.invalid/"+i,40,done::incrementAndGet);});
            waitFor(()->done.get()==70,6000);assertEquals(2,peak.get());main(s,a->f.loader.close());
        }
    }
    @Test public void sameKeyCoalescesAndRecycledViewNeverReceivesOldBitmap()throws Exception{
        AtomicInteger redFetches=new AtomicInteger(),redCallbacks=new AtomicInteger(),blueCallbacks=new AtomicInteger();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);Fixture f=new Fixture();
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            main(s,a->{f.install(a,(url,width)->{if(url.endsWith("red")){redFetches.incrementAndGet();entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return bitmap(Color.RED);}return bitmap(Color.BLUE);});for(int i=0;i<8;i++)f.loader.load(f.target(a),"https://owned.invalid/red",40,redCallbacks::incrementAndGet);});assertTrue(entered.await(2,TimeUnit.SECONDS));
            main(s,a->f.loader.load(f.views.get(0),"https://owned.invalid/blue",40,blueCallbacks::incrementAndGet));waitFor(()->blueCallbacks.get()==1,3000);release.countDown();waitFor(()->redCallbacks.get()==7,3000);assertEquals(1,redFetches.get());
            main(s,a->{android.graphics.drawable.BitmapDrawable image=(android.graphics.drawable.BitmapDrawable)f.views.get(0).getDrawable();assertEquals(Color.BLUE,image.getBitmap().getPixel(0,0));f.loader.close();});
        }finally{release.countDown();}
    }
    @Test public void failuresRetryBoundedlyThenRemainFallbackAndCanRebind()throws Exception{
        AtomicInteger attempts=new AtomicInteger(),done=new AtomicInteger();AtomicBoolean healthy=new AtomicBoolean();Fixture f=new Fixture();
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            main(s,a->{f.install(a,(url,width)->{attempts.incrementAndGet();if(!healthy.get())throw new java.io.IOException("Owned failure");return bitmap(Color.GREEN);});f.loader.load(f.target(a),"https://owned.invalid/retry",40,done::incrementAndGet);});
            waitFor(()->attempts.get()==3,4000);Thread.sleep(500);assertEquals(3,attempts.get());assertEquals(0,done.get());main(s,a->assertTrue(f.views.get(0).getDrawable() instanceof ColorDrawable));healthy.set(true);
            main(s,a->f.loader.load(f.views.get(0),"https://owned.invalid/retry",40,done::incrementAndGet));waitFor(()->done.get()==1,3000);assertEquals(4,attempts.get());main(s,a->f.loader.close());
        }
    }
    @Test public void clearAndClosePreventStaleCallbacksAndDetachedTargetsDoNotFetch()throws Exception{
        AtomicInteger calls=new AtomicInteger(),done=new AtomicInteger();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);Fixture f=new Fixture();
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            main(s,a->{f.install(a,(url,width)->{calls.incrementAndGet();entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return bitmap(Color.GREEN);});ImageView detached=new ImageView(a);f.loader.load(detached,"https://owned.invalid/detached",40,done::incrementAndGet);});Thread.sleep(250);assertEquals(0,calls.get());
            main(s,a->f.loader.load(f.target(a),"https://owned.invalid/active",40,done::incrementAndGet));assertTrue(entered.await(2,TimeUnit.SECONDS));main(s,a->{f.loader.clearTargets();assertNull(f.views.get(0).getTag());f.loader.close();});release.countDown();Thread.sleep(350);assertEquals(0,done.get());
        }finally{release.countDown();}
    }
}
