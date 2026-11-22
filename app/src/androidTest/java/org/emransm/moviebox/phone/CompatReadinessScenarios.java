package org.emransm.moviebox.phone;

import android.net.Uri;
import android.util.LruCache;
import android.view.View;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Collections;
import java.util.concurrent.ThreadPoolExecutor;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

/** Deterministic pressure-policy test; does not pretend emulator hardware is low-end. */
@RunWith(AndroidJUnit4.class)
public class CompatReadinessScenarios {
    @After public void cleanupFixture()throws Exception {
        Thread.sleep(350);
        Library store=new Library(InstrumentationRegistry.getInstrumentation().getTargetContext());
        store.removeTitles("history",Collections.singleton("generated-native-phone-fixture"));
        store.close();
    }
    @Test public void lowMemoryPolicyStillDecodesSeeksAndReturns()throws Exception {
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)) {
            main(scenario,a->{
                a.applyMemoryPolicy(true);
                assertTrue((Boolean)field(a,"lowMemoryDevice"));
                assertEquals(4*1024*1024,((LruCache<?,?>)field(a,"bitmapCache")).maxSize());
                assertEquals(2,((ThreadPoolExecutor)field(a,"network")).getMaximumPoolSize());
                assertEquals(1,((ThreadPoolExecutor)field(a,"images")).getMaximumPoolSize());
            });
            start(scenario,Uri.fromFile(fixture()).toString());
            waitFor(()->playing(scenario),15000);
            main(scenario,a->{ExoPlayer p=(ExoPlayer)field(a,"player");assertNull(p.getPlayerError());p.seekTo(20000);});
            waitFor(()->position(scenario)>=20000&&playing(scenario),5000);
            main(scenario,a->{View pause=(View)field(a,"pauseButton");assertTrue(pause.performClick());});
            waitFor(()->!playing(scenario),1000);
            main(scenario,a->assertTrue(((View)field(a,"pauseButton")).performClick()));
            waitFor(()->playing(scenario),1000);
            main(scenario,a->{call(a,"showControls",new Class[]{boolean.class},true);View back=description((View)field(a,"root"),"Back to details");assertNotNull(back);assertTrue(back.performClick());});
            waitFor(()->{boolean[] left={false};main(scenario,a->left[0]=field(a,"player")==null);return left[0];},3000);
        }
    }
}
