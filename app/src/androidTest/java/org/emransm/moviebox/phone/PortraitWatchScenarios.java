package org.emransm.moviebox.phone;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.media3.exoplayer.ExoPlayer;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

/** Owned media + isolated library. Run only against the local QA emulator. */
@RunWith(AndroidJUnit4.class)
public class PortraitWatchScenarios {
    private ActivityScenario<PhoneActivity> open(String id,int type)throws Exception{
        ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class);
        BlockFlowScenarios.isolate(s,new SliderPlaybackScenarios.Local());
        JSONObject title=BlockFlowScenarios.title(id,type);
        main(s,a->call(a,"openDetail",new Class[]{JSONObject.class},title));
        waitFor(()->playing(s),12000);return s;
    }
    @Test public void toolChipIconsAndLabelsAreCentredWithConsistentPadding()throws Exception{
        try(ActivityScenario<PhoneActivity> s=open("qa-watch-chip-alignment",1)){
            main(s,a->{View root=(View)field(a,"root");float d=a.getResources().getDisplayMetrics().density;for(String label:new String[]{"Toggle saved title","Share title","Download selected video","View downloads"}){LinearLayout chip=(LinearLayout)description(root,label);assertNotNull(chip);assertEquals(Math.round(48*d),chip.getHeight());assertTrue(chip.getPaddingLeft()>=Math.round(12*d));assertTrue(chip.getPaddingRight()>=Math.round(12*d));View icon=chip.getChildAt(0),text=chip.getChildAt(1);assertEquals(icon.getWidth(),icon.getHeight());assertEquals((icon.getTop()+icon.getBottom())/2f,(text.getTop()+text.getBottom())/2f,1f);assertTrue(text.getRight()<=chip.getWidth()-chip.getPaddingRight());}call(a,"showControls",new Class[]{boolean.class},true);});BlockFlowScenarios.capture("qa-watch-chip-alignment.png");
        }
    }
    @Test public void tileOpensPortraitPlayerWithToolsAndEpisodesBelow()throws Exception{
        try(ActivityScenario<PhoneActivity> s=open("qa-watch-portrait",2)){
            Thread.sleep(500);
            main(s,a->{View frame=(View)field(a,"playerRoot"),info=(View)field(a,"watchInfo"),root=(View)field(a,"root");
                assertNotNull(info);assertTrue(info.isShown());
                assertEquals("Video keeps landscape aspect within portrait page",16f/9f,(float)frame.getWidth()/frame.getHeight(),.04f);
                int[] videoAt=new int[2],infoAt=new int[2];frame.getLocationOnScreen(videoAt);info.getLocationOnScreen(infoAt);assertTrue(infoAt[1]>=videoAt[1]+frame.getHeight());
                for(String label:new String[]{"Share title","Toggle saved title","Download selected video","Quality","Audio","Subtitles","More","Choose season","Play season 1 episode 2"})assertNotNull(label,description(root,label));
                assertNull("No intermediate detail-card Watch action",description(root,"Watch or resume"));
            });BlockFlowScenarios.capture("qa-watch-portrait.png");
        }
    }
    @Test public void rotationAndHardwareBackKeepSamePlayerAndAudioPreference()throws Exception{
        try(ActivityScenario<PhoneActivity> s=open("qa-watch-rotate",2)){
            Object[] original={null};main(s,a->{original[0]=field(a,"player");((ExoPlayer)original[0]).pause();a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);});
            waitFor(()->{boolean[] ok={false};main(s,a->ok[0]=a.getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE);return ok[0];},6000);
            main(s,a->{assertSame(original[0],field(a,"player"));assertNull(field(a,"watchInfo"));a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK));a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK));});
            waitFor(()->{boolean[] ok={false};main(s,a->ok[0]=a.getResources().getConfiguration().orientation==Configuration.ORIENTATION_PORTRAIT);return ok[0];},6000);
            main(s,a->{assertSame(original[0],field(a,"player"));assertNotNull(field(a,"watchInfo"));assertFalse(((ExoPlayer)original[0]).getPlayWhenReady());});
        }
    }
    @Test public void episodeBelowPlayerUsesChosenAudioEdition()throws Exception{
        SliderPlaybackScenarios.Local api=new SliderPlaybackScenarios.Local();
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(s,api);JSONObject title=BlockFlowScenarios.title("qa-watch-source",2);
            main(s,a->call(a,"openDetail",new Class[]{JSONObject.class},title));waitFor(()->playing(s),12000);
            main(s,a->{JSONObject dub=new JSONObject(((JSONObject)field(a,"subject")).toString()).put("subjectId","qa-watch-dub");put(a,"currentAudio",dub);assertTrue(description((View)field(a,"root"),"Play season 1 episode 2").performClick());});
            waitFor(()->api.id.equals("qa-watch-dub")&&api.ep==2&&playing(s),10000);
        }
    }
    @Test public void movieHasNoEpisodesAndPortraitMoreCanOpenRecoveryTools()throws Exception{
        try(ActivityScenario<PhoneActivity> s=open("qa-watch-movie",1)){
            main(s,a->{View root=(View)field(a,"root");assertNull(description(root,"Choose season"));assertTrue(description(root,"More").performClick());});
            Thread.sleep(250);BlockFlowScenarios.capture("qa-watch-more.png");
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            main(s,a->{assertNotNull(field(a,"player"));});
        }
    }
    @Test public void settingsExposeFunctionalAudioAndSubtitleDefaults()throws Exception{
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(s,new SliderPlaybackScenarios.Local());
            main(s,a->{call(a,"browse",new Class[]{String.class},"Settings");View root=(View)field(a,"root");assertTrue(BlockFlowScenarios.hasText(root,"Subtitle language"));assertTrue(BlockFlowScenarios.hasText(root,"Preferred embedded audio"));assertTrue(BlockFlowScenarios.hasText(root,"Load subtitles automatically"));});
        }
    }
}
