package org.emransm.moviebox.phone;

import android.content.SharedPreferences;
import android.view.View;
import android.widget.FrameLayout;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

/** Isolated metadata/checker/preferences. No live GitHub, package download, browser or installer. */
@RunWith(AndroidJUnit4.class)
public class UpdateReliabilityScenarios {
    static final String URL="https://github.com/"+Updates.REPOSITORY+"/releases/download/v0.2.0/MovieBox-Android-App-0.2.0.apk";
    static JSONObject release(String version)throws Exception{String name="MovieBox-Android-App-"+version.replaceFirst("^v","")+".apk";return new JSONObject().put("tag_name",version).put("assets",new JSONArray().put(new JSONObject().put("name",name).put("size",1234).put("digest","sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa").put("browser_download_url","https://github.com/"+Updates.REPOSITORY+"/releases/download/"+version+"/"+name)));}
    static JSONObject available()throws Exception{return Updates.evaluate(release("v0.2.0"),"0.1.0");}
    static SharedPreferences prefs(PhoneActivity a){return a.getSharedPreferences("qa-owned-update-"+UUID.randomUUID(),0);}
    static final class Queued implements Executor {Runnable task;public void execute(Runnable value){assertNull("Only one check is queued",task);task=value;}void finish(){Runnable value=task;assertNotNull(value);task=null;value.run();}}
    @Test public void metadataDistinguishesCurrentOlderInvalidAndIncompleteWithoutFalseLatest()throws Exception{
        assertEquals(Updates.AVAILABLE,available().optString("status"));JSONObject current=Updates.evaluate(release("v0.2.0"),"0.2.0"),older=Updates.evaluate(release("v0.2.0"),"0.3.0");assertEquals(Updates.CURRENT,current.optString("status"));assertEquals(Updates.OLDER,older.optString("status"));assertEquals("You're up to date.",Updates.message(current));assertFalse(Updates.message(older).contains("latest"));
        JSONObject incomplete=release("v0.2.0");incomplete.put("assets",new JSONArray());JSONObject result=Updates.evaluate(incomplete,"0.1.0");assertEquals(Updates.INCOMPLETE,result.optString("status"));assertFalse(Updates.successful(result));assertFalse(Updates.message(result).contains("latest"));
        for(String version:new String[]{"","v1","0.2.0-preview.1","9999999999999999.0.0"}){result=Updates.evaluate(release(version),"0.1.0");assertEquals(Updates.INVALID,result.optString("status"));assertFalse(Updates.message(result).contains("latest"));}
        assertEquals(Updates.INVALID,Updates.evaluate(release("v0.2.0").put("draft",true),"0.1.0").optString("status"));assertEquals(Updates.INVALID,Updates.evaluate(release("v0.2.0").put("prerelease",true),"0.1.0").optString("status"));
    }
    @Test public void busyResultIsRetainedThenDisplayedAndDismissedWithoutAnotherRequest()throws Exception{
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            main(scenario,a->{SharedPreferences preferences=prefs(a);try{FrameLayout root=new FrameLayout(a);AtomicBoolean busy=new AtomicBoolean(true);AtomicInteger calls=new AtomicInteger();UpdateNotice.check(a,root,busy::get,false,preferences,()->{calls.incrementAndGet();return available();},()->100000000L,Runnable::run);assertEquals(1,calls.get());assertEquals(100000000L,preferences.getLong("checked",0));assertFalse(preferences.getString("pending","").isEmpty());assertNull(root.findViewWithTag("moviebox-update-notice"));
                busy.set(false);UpdateNotice.presentPending(a,root,busy::get,preferences);View notice=root.findViewWithTag("moviebox-update-notice");assertNotNull(notice);assertEquals(1,calls.get());assertTrue(description(notice,"Dismiss optional update").performClick());assertNull(root.findViewWithTag("moviebox-update-notice"));assertEquals("0.2.0",preferences.getString("dismissed",""));assertFalse(preferences.getString("pending","").isEmpty());
                UpdateNotice.check(a,root,busy::get,false,preferences,()->{calls.incrementAndGet();return available();},()->200000000L,Runnable::run);assertEquals(2,calls.get());assertNull(root.findViewWithTag("moviebox-update-notice"));
            }finally{preferences.edit().clear().commit();}});
        }
    }
    @Test public void disablingWhileCheckingPreventsDeferredNoticeButManualCheckStillWorks()throws Exception{
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            main(scenario,a->{SharedPreferences preferences=prefs(a);try{FrameLayout root=new FrameLayout(a);Queued worker=new Queued();UpdateNotice.check(a,root,()->true,false,preferences,UpdateReliabilityScenarios::available,()->100000000L,worker);assertFalse(preferences.contains("checked"));preferences.edit().putBoolean("enabled",false).commit();worker.finish();assertEquals("",preferences.getString("pending",""));UpdateNotice.presentPending(a,root,()->false,preferences);assertNull(root.findViewWithTag("moviebox-update-notice"));
                AtomicInteger calls=new AtomicInteger();UpdateNotice.check(a,root,()->false,false,preferences,()->{calls.incrementAndGet();return available();},()->200000000L,Runnable::run);assertEquals(0,calls.get());UpdateNotice.check(a,root,()->false,true,preferences,()->{calls.incrementAndGet();return available();},()->200000000L,Runnable::run);assertEquals(1,calls.get());assertNotNull(root.findViewWithTag("moviebox-update-notice"));
            }finally{preferences.edit().clear().commit();}});
        }
    }
    @Test public void errorsAndIncompleteMetadataDoNotConsumeSuccessThrottleAndRetryCanSucceed()throws Exception{
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            main(scenario,a->{SharedPreferences preferences=prefs(a);try{FrameLayout root=new FrameLayout(a);AtomicInteger calls=new AtomicInteger();UpdateNotice.check(a,root,()->false,false,preferences,()->{calls.incrementAndGet();throw new IOException("Owned failure");},()->100000000L,Runnable::run);assertFalse(preferences.contains("checked"));
                UpdateNotice.check(a,root,()->false,false,preferences,()->{calls.incrementAndGet();return Updates.evaluate(release("0.2.0").put("assets",new JSONArray()),"0.1.0");},()->100060001L,Runnable::run);assertFalse(preferences.contains("checked"));assertNull(root.findViewWithTag("moviebox-update-notice"));
                UpdateNotice.check(a,root,()->false,false,preferences,()->{calls.incrementAndGet();return available();},()->100120002L,Runnable::run);assertEquals(3,calls.get());assertEquals(100120002L,preferences.getLong("checked",0));assertNotNull(root.findViewWithTag("moviebox-update-notice"));
                UpdateNotice.check(a,root,()->false,false,preferences,()->{calls.incrementAndGet();return available();},()->100120003L,Runnable::run);assertEquals("Successful metadata is throttled",3,calls.get());
            }finally{preferences.edit().clear().commit();}});
        }
    }
    @Test public void inflightChecksCoalesceAndSameOlderResultsNeverCreatePendingNotice()throws Exception{
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            main(scenario,a->{SharedPreferences preferences=prefs(a);try{FrameLayout root=new FrameLayout(a);Queued worker=new Queued();AtomicInteger calls=new AtomicInteger();UpdateNotice.Checker checker=()->{calls.incrementAndGet();return Updates.evaluate(release("0.1.0"),"0.1.0");};UpdateNotice.check(a,root,()->false,false,preferences,checker,()->100000000L,worker);UpdateNotice.check(a,root,()->false,false,preferences,checker,()->100000000L,worker);worker.finish();assertEquals(1,calls.get());assertEquals(100000000L,preferences.getLong("checked",0));assertEquals("",preferences.getString("pending",""));assertNull(root.findViewWithTag("moviebox-update-notice"));
                UpdateNotice.check(a,root,()->false,true,preferences,()->Updates.evaluate(release("0.0.9"),"0.1.0"),()->100000001L,Runnable::run);assertEquals(100000001L,preferences.getLong("checked",0));assertNull(root.findViewWithTag("moviebox-update-notice"));
            }finally{preferences.edit().clear().commit();}});
        }
    }
    @Test public void pendingMetadataMustBeEnabledUndismissedNewerAndPointToOwnApk()throws Exception{
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            main(scenario,a->{SharedPreferences preferences=prefs(a);try{FrameLayout root=new FrameLayout(a);for(JSONObject value:new JSONObject[]{available().put("url",URL.replace("github.com","github.com.invalid.test")),available().put("version","0.0.1"),new JSONObject().put("status",Updates.INVALID)}){preferences.edit().putString("pending",value.toString()).commit();UpdateNotice.presentPending(a,root,()->false,preferences);assertEquals("",preferences.getString("pending",""));assertNull(root.findViewWithTag("moviebox-update-notice"));}
                preferences.edit().putString("pending",available().toString()).putString("dismissed","0.2.0").commit();UpdateNotice.presentPending(a,root,()->false,preferences);assertFalse("Later keeps verified metadata for next cold launch",preferences.getString("pending","").isEmpty());preferences.edit().remove("dismissed").putBoolean("enabled",false).putString("pending",available().toString()).commit();UpdateNotice.presentPending(a,root,()->false,preferences);assertEquals("",preferences.getString("pending",""));assertNull(root.findViewWithTag("moviebox-update-notice"));
            }finally{preferences.edit().clear().commit();}});
        }
    }
}
