package org.emransm.moviebox.phone;

import android.content.res.Configuration;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Debug;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.common.Player;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.*;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

/** Isolated configuration and interruption tests using owned generated media. */
@RunWith(AndroidJUnit4.class)
public class FullCompatibilityScenarios {
    @After public void cleanupOwnedFixture()throws Exception {Thread.sleep(350);Library s=new Library(InstrumentationRegistry.getInstrumentation().getTargetContext());s.removeTitles("history",Collections.singleton("generated-native-phone-fixture"));s.close();}
    private static void collect(View v,List<Button> buttons){if(v instanceof Button&&v.isShown())buttons.add((Button)v);if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int n=0;n<g.getChildCount();n++)collect(g.getChildAt(n),buttons);}}
    @Test public void fontTwoPlayerButtonsKeepCompleteTextHeight()throws Exception {
        final Configuration[] original={null};
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            try{
                main(scenario,a->{original[0]=new Configuration(a.getResources().getConfiguration());Configuration large=new Configuration(original[0]);large.fontScale=2f;a.getResources().updateConfiguration(large,a.getResources().getDisplayMetrics());assertEquals(2f,a.getResources().getConfiguration().fontScale,.01f);});
                start(scenario,Uri.fromFile(fixture()).toString());waitFor(()->playing(scenario),15000);Thread.sleep(500);
                main(scenario,a->{call(a,"showControls",new Class[]{boolean.class},true);List<Button> buttons=new ArrayList<>();collect((View)field(a,"root"),buttons);assertTrue(buttons.size()>5);List<String> clipped=new ArrayList<>();for(Button b:buttons){if(b.getLayout()!=null&&b.getLayout().getHeight()>b.getHeight()-b.getCompoundPaddingTop()-b.getCompoundPaddingBottom()+1)clipped.add(b.getContentDescription()+" textheight="+b.getLayout().getHeight()+" available="+(b.getHeight()-b.getCompoundPaddingTop()-b.getCompoundPaddingBottom()));}assertTrue("2x font labels must not be vertically clipped: "+clipped,clipped.isEmpty());});
            }finally{if(original[0]!=null)main(scenario,a->a.getResources().updateConfiguration(original[0],a.getResources().getDisplayMetrics()));}
        }
    }
    @Test public void rtlPlayerTransportStaysInsideViewport()throws Exception {
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            start(scenario,Uri.fromFile(fixture()).toString());waitFor(()->playing(scenario),15000);
            main(scenario,a->{((View)field(a,"root")).setLayoutDirection(View.LAYOUT_DIRECTION_RTL);call(a,"showControls",new Class[]{boolean.class},true);});Thread.sleep(500);
            main(scenario,a->{View root=(View)field(a,"root");Rect viewport=new Rect();assertTrue(root.getGlobalVisibleRect(viewport));for(String label:new String[]{"Seek back ten seconds","Seek forward ten seconds","More playback options"}){View b=description(root,label);assertNotNull(label,b);Rect r=new Rect();assertTrue(label+" visible",b.getGlobalVisibleRect(r));assertTrue(label+" within viewport",viewport.contains(r));assertEquals(label+" full horizontal hit region",b.getWidth(),r.width());}});
        }
    }
    @Test public void ninetySecondPlaybackSeekSoakAndBackgroundReturn()throws Exception {
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            start(scenario,Uri.fromFile(fixture()).toString());waitFor(()->playing(scenario),15000);
            main(scenario,a->{a.applyMemoryPolicy(true);((ExoPlayer)field(a,"player")).setRepeatMode(Player.REPEAT_MODE_ALL);});
            Debug.MemoryInfo before=new Debug.MemoryInfo();Debug.getMemoryInfo(before);
            for(int n=0;n<45;n++){Thread.sleep(2000);final int turn=n;main(scenario,a->{ExoPlayer p=(ExoPlayer)field(a,"player");assertNotNull("Player survives soak iteration "+turn,p);assertNull("No decoder error during soak "+turn,p.getPlayerError());if(turn%5==0)p.seekTo((turn*1300L)%45000L);});}
            Debug.MemoryInfo after=new Debug.MemoryInfo();Debug.getMemoryInfo(after);android.util.Log.i("MovieBoxCompatibility","90-second soak PSS beforeKB="+before.getTotalPss()+" afterKB="+after.getTotalPss());
            scenario.moveToState(Lifecycle.State.CREATED);scenario.moveToState(Lifecycle.State.RESUMED);
            main(scenario,a->{assertNull("Background releases decoder",field(a,"player"));assertNotNull("Details are usable on return",description((View)field(a,"root"),"Back to browsing"));});
        }
    }
}
