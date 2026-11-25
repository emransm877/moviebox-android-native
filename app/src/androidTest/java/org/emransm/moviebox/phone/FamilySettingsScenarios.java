package org.emransm.moviebox.phone;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.*;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

@RunWith(AndroidJUnit4.class)
public class FamilySettingsScenarios {
    @Test public void cachedAdultMetadataSurvivesAndFamilyProfilesCannotCross()throws Exception{
        android.content.Context base=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext();
        java.io.File owned=new java.io.File(base.getCacheDir(),"qa-family-cache-"+System.nanoTime());
        android.content.Context context=new android.content.ContextWrapper(base){@Override public java.io.File getCacheDir(){return owned;}};
        JSONObject adult=BlockFlowScenarios.title("qa-cached-adult",1).put("ageRating","18+");
        UiCache unrestricted=new UiCache(context,false,false),family=new UiCache(context,false,true);
        unrestricted.writeCatalogue("Discover",Collections.singletonList(adult));unrestricted.writeDetail("qa-cached-adult",adult);
        assertNull("Family mode never reuses unrestricted catalogue",family.readCatalogue("Discover"));assertNull(family.readDetail("qa-cached-adult"));
        assertTrue(AdultContentPolicy.markedAdult(new UiCache(context,false,false).readCatalogue("Discover").get(0)));
        assertTrue(AdultContentPolicy.markedAdult(new UiCache(context,false,false).readDetail("qa-cached-adult")));
        family.writeCatalogue("Discover",Collections.singletonList(adult));assertTrue(AdultContentPolicy.markedAdult(family.readCatalogue("Discover").get(0)));
    }
    @Test public void adultMarkerSurvivesBackupAndRestore()throws Exception{
        JSONObject title=BlockFlowScenarios.title("qa-adult-backup",1).put("contentRating","18+");
        JSONObject data=new JSONObject().put("list",new JSONObject().put("qa-adult-backup",title)).put("history",new JSONObject());
        JSONObject parsed=LibraryBackup.parse(LibraryBackup.export(data));
        assertTrue(AdultContentPolicy.markedAdult(parsed.getJSONArray("list").getJSONObject(0)));
    }
    private static View text(View root,String value){if(root instanceof TextView&&value.contentEquals(((TextView)root).getText()))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=text(((ViewGroup)root).getChildAt(i),value);if(found!=null)return found;}return null;}
    @Test public void adultOffByDefaultAndTogglePersistsAndChangesServerMode()throws Exception{
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(s,new SliderPlaybackScenarios.Local());
            main(s,a->{Library library=(Library)field(a,"library");assertFalse(library.settings().optBoolean("allowAdultContent",false));call(a,"browse",new Class[]{String.class},"Settings");CheckBox toggle=(CheckBox)text((View)field(a,"root"),"Allow 18+ content");assertNotNull(toggle);assertFalse(toggle.isChecked());toggle.performClick();assertTrue(library.settings().getBoolean("allowAdultContent"));java.lang.reflect.Field mode=Api.class.getDeclaredField("familyMode");mode.setAccessible(true);assertFalse(mode.getBoolean(field(a,"api")));call(a,"browse",new Class[]{String.class},"Settings");assertTrue(((CheckBox)text((View)field(a,"root"),"Allow 18+ content")).isChecked());((CheckBox)text((View)field(a,"root"),"Allow 18+ content")).performClick();assertFalse(library.settings().getBoolean("allowAdultContent"));assertTrue(mode.getBoolean(field(a,"api")));});
        }
    }
    @Test public void markedTitlesFilteredFromCachedListsAndDirectEntry()throws Exception{
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(s,new SliderPlaybackScenarios.Local());
            main(s,a->{JSONObject adult=BlockFlowScenarios.title("qa-adult",1).put("isAdult",true),ordinary=BlockFlowScenarios.title("qa-ordinary",1);assertTrue(AdultContentPolicy.markedAdult(adult));assertTrue(AdultContentPolicy.markedAdult(new JSONObject().put("contentRating","TV-MA")));assertFalse(AdultContentPolicy.markedAdult(ordinary));List<?> safe=(List<?>)call(a,"allowedTitles",new Class[]{List.class},Arrays.asList(adult,ordinary));assertEquals(1,safe.size());call(a,"openDetail",new Class[]{JSONObject.class},adult);assertNull(field(a,"player"));((Library)field(a,"library")).settings().put("allowAdultContent",true);assertEquals(2,((List<?>)call(a,"allowedTitles",new Class[]{List.class},Arrays.asList(adult,ordinary))).size());});
        }
    }
    @Test public void audioAndSubtitleLanguageChoicesSaveAndApply()throws Exception{
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(s,new SliderPlaybackScenarios.Local());
            main(s,a->{call(a,"browse",new Class[]{String.class},"Settings");call(a,"languagePreference",new Class[]{String.class,String.class,String.class},"Preferred embedded audio","audioLanguage","");});BlockFlowScenarios.click("Hindi");
            main(s,a->{assertEquals("hi",((Library)field(a,"library")).settings().getString("audioLanguage"));assertEquals("hi",call(a,"preferredAudioLanguage",new Class[]{}));call(a,"languagePreference",new Class[]{String.class,String.class,String.class},"Subtitle language","subtitleLanguage","en");});BlockFlowScenarios.click("Bangla");
            main(s,a->{Library lib=(Library)field(a,"library");assertEquals("bn",lib.settings().getString("subtitleLanguage"));CheckBox subtitles=(CheckBox)text((View)field(a,"root"),"Load subtitles automatically");assertNotNull(subtitles);subtitles.performClick();assertFalse(lib.settings().getBoolean("subtitles"));call(a,"browse",new Class[]{String.class},"Settings");assertFalse(((CheckBox)text((View)field(a,"root"),"Load subtitles automatically")).isChecked());});
        }
    }
}
