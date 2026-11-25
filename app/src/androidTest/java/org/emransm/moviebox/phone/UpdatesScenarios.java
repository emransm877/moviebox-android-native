package org.emransm.moviebox.phone;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.*;
import android.view.View;
import android.widget.FrameLayout;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;
@RunWith(AndroidJUnit4.class)
public class UpdatesScenarios {
 JSONObject release(String url)throws Exception{return new JSONObject().put("assets",new JSONArray().put(new JSONObject().put("name","MovieBox-Android-App-0.2.0.apk").put("size",1234).put("digest","sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa").put("browser_download_url",url)));}
 @Test public void onlyNativeProjectsCompleteStableApkCanBeOffered()throws Exception{
  String good="https://github.com/emransm877/moviebox-android-native/releases/download/v0.2.0/MovieBox-Android-App-0.2.0.apk";assertEquals(good,Updates.apk(release(good)));for(String url:new String[]{good.replace("https:","http:"),good.replace("github.com","github.com.invalid.test"),good.replace("moviebox-android-native","moviebox-android-tv")})assertEquals("",Updates.apk(release(url)));assertEquals("",Updates.apk(release(good).put("prerelease",true)));assertEquals("",Updates.apk(release(good).put("draft",true)));JSONObject empty=release(good);empty.getJSONArray("assets").getJSONObject(0).put("size",0);assertEquals("",Updates.apk(empty));
 }
 @Test public void updateNoticeIsDismissibleAndDoesNotLaunchOrInstallAnything()throws Exception{
  try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
   main(scenario,a->{FrameLayout root=(FrameLayout)field(a,"root");android.content.SharedPreferences prefs=a.getSharedPreferences("qa-update-notice",0);try{UpdateNotice.show(a,root,prefs,new JSONObject().put("version","0.2.0").put("url","https://github.com/emransm877/moviebox-android-native/releases"));View notice=root.findViewWithTag("moviebox-update-notice");assertNotNull(notice);View close=description(notice,"Dismiss optional update");assertNotNull(close);assertTrue(close.performClick());assertNull(root.findViewWithTag("moviebox-update-notice"));assertEquals("0.2.0",prefs.getString("dismissed",""));}finally{prefs.edit().clear().commit();}});
  }
 }
}
