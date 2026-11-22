package org.emransm.moviebox.phone;

import android.content.Context;
import android.net.Uri;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.common.C;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.After;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

/** Async playback races using only generated local media/caption files. */
@RunWith(AndroidJUnit4.class)
public class PlaybackReadinessScenarios {
    @After public void cleanupOwnedHistory() throws Exception {
        Library store=new Library(InstrumentationRegistry.getInstrumentation().getTargetContext());
        store.removeTitles("history",Collections.singleton("generated-native-phone-fixture"));store.close();
    }
    private static void clickLabel(String value) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
        assertNotNull(root);List<AccessibilityNodeInfo> found=root.findAccessibilityNodeInfosByText(value);
        for(AccessibilityNodeInfo node:found){if(!value.contentEquals(node.getText()==null?"":node.getText()))continue;AccessibilityNodeInfo current=node;for(int n=0;n<4&&current!=null;n++,current=current.getParent())if(current.isClickable()){assertTrue(current.performAction(AccessibilityNodeInfo.ACTION_CLICK));return;}}
        fail("Clickable sheet label not found: "+value);
    }
    @Test public void qualityLabelSurvivesLateQualityEnrichment() throws Exception {
        String local=Uri.fromFile(fixture()).toString();
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            start(scenario,local);waitFor(()->playing(scenario),15000);
            main(scenario,a->{List<JSONObject> streams=new ArrayList<>();streams.add(new JSONObject().put("resourceLink",local).put("resourceId","generated-fixture").put("resolution",1080));streams.add(new JSONObject().put("resourceLink",local).put("resourceId","generated-720").put("resolution",720));put(a,"streams",streams);call(a,"qualityDialog",new Class[]{});});
            // The 720p row is already visible. A late response inserts another quality.
            main(scenario,a->{List<JSONObject> enriched=new ArrayList<>();enriched.add(new JSONObject().put("resourceLink",local).put("resourceId","generated-fixture").put("resolution",1080));enriched.add(new JSONObject().put("resourceLink",local).put("resourceId","generated-900").put("resolution",900));enriched.add(new JSONObject().put("resourceLink",local).put("resourceId","generated-720").put("resolution",720));call(a,"mergeAvailableQualities",new Class[]{List.class,ExoPlayer.class},enriched,field(a,"player"));});
            clickLabel("720p");
            main(scenario,a->{List<JSONObject> streams=(List<JSONObject>)field(a,"streams");assertEquals("Clicking displayed 720p must select that source even after background enrichment",720,streams.get((Integer)field(a,"streamIndex")).optInt("resolution"));});
        }
    }
    @Test public void savedSubtitleOffAppliesWhenPlayerIsRebuilt() throws Exception {
        String local=Uri.fromFile(fixture()).toString();Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File subtitle=new File(context.getFilesDir(),"qa-readiness-off.srt");
        try(FileOutputStream out=new FileOutputStream(subtitle)){out.write("1\n00:00:00,000 --> 00:01:05,000\nGenerated saved-Off subtitle fixture\n".getBytes(StandardCharsets.UTF_8));}
        boolean[] original={true};
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            try {
                start(scenario,local);waitFor(()->playing(scenario),15000);
                main(scenario,a->{Library library=(Library)field(a,"library");original[0]=library.settings().optBoolean("subtitles",true);library.settings().put("subtitles",false);library.save();call(a,"startStream",new Class[]{long.class,boolean.class,File.class},0L,true,subtitle);});
                waitFor(()->playing(scenario),15000);
                main(scenario,a->assertTrue("Saved Off must disable text in each rebuilt player",((ExoPlayer)field(a,"player")).getTrackSelectionParameters().disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)));
            } finally {main(scenario,a->{Library library=(Library)field(a,"library");library.settings().put("subtitles",original[0]);library.save();});}
        } finally {subtitle.delete();}
    }
    @Test public void subtitleOffCancelsPendingCaptionApplication() throws Exception {
        String local=Uri.fromFile(fixture()).toString();Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File directory=new File(context.getFilesDir(),"subtitles");directory.mkdirs();
        String identity="generated-native-phone-fixture:0:0:generated-fixture:qa";
        File subtitle=new File(directory,Api.md5(identity.getBytes(StandardCharsets.UTF_8))+".srt");
        try(FileOutputStream out=new FileOutputStream(subtitle)){out.write("1\n00:00:00,000 --> 00:01:05,000\nGenerated async subtitle fixture\n".getBytes(StandardCharsets.UTF_8));}
        CountDownLatch release=new CountDownLatch(1);boolean[] originalEnabled={true};String[] originalLanguage={"en"};
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            try {
                start(scenario,local);waitFor(()->playing(scenario),15000);
                ThreadPoolExecutor[] executor={null};main(scenario,a->{executor[0]=(ThreadPoolExecutor)field(a,"network");Library library=(Library)field(a,"library");originalEnabled[0]=library.settings().optBoolean("subtitles",true);originalLanguage[0]=library.settings().optString("subtitleLanguage","en");});
                CountDownLatch started=new CountDownLatch(executor[0].getCorePoolSize());
                for(int n=0;n<executor[0].getCorePoolSize();n++)executor[0].execute(()->{started.countDown();try{release.await(30,TimeUnit.SECONDS);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}});
                assertTrue("All workers gated before caption request",started.await(30,TimeUnit.SECONDS));
                main(scenario,a->{call(a,"useCaption",new Class[]{JSONObject.class},new JSONObject().put("lan","qa").put("url","https://example.invalid/generated.srt"));call(a,"subtitleDialog",new Class[]{});});
                clickLabel("Off");release.countDown();Thread.sleep(1000);InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                main(scenario,a->{Library library=(Library)field(a,"library");assertFalse("Off must remain selected after previously pending caption finishes",library.settings().optBoolean("subtitles",true));assertTrue(((ExoPlayer)field(a,"player")).getTrackSelectionParameters().disabledTrackTypes.contains(C.TRACK_TYPE_TEXT));});
            } finally {
                release.countDown();main(scenario,a->{Library library=(Library)field(a,"library");library.settings().put("subtitles",originalEnabled[0]).put("subtitleLanguage",originalLanguage[0]);library.save();});
            }
        } finally {subtitle.delete();}
    }
}
