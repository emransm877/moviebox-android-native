package org.emransm.moviebox.phone;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import android.content.Context;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Explicit two-phase probe for a disposable emulator; never a maintainer phone test. */
@RunWith(AndroidJUnit4.class)
public class PortraitUpgradeProbe {
    private Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    @Test public void seedBeforeUpdate()throws Exception{
        Library library=new Library(context());JSONObject title=new JSONObject().put("subjectId","qa-update-preservation").put("title","Owned upgrade fixture").put("subjectType",1);
        library.toggle(title);library.progress(title,0,0,12345,65000,false);library.settings().put("audioLanguage","hi").put("subtitleLanguage","bn");library.data.put("recentSearches",new org.json.JSONArray().put("Owned persisted query"));library.save();library.close();
    }
    @Test public void verifyAfterUpdate()throws Exception{
        Library library=new Library(context());try{JSONObject title=new JSONObject().put("subjectId","qa-update-preservation").put("subjectType",1);assertTrue(library.contains(title));assertEquals(12345,library.position(title,0,0));assertEquals("hi",library.settings().getString("audioLanguage"));assertEquals("bn",library.settings().getString("subtitleLanguage"));assertEquals("Owned persisted query",library.data.getJSONArray("recentSearches").getString(0));assertFalse("New family default applies to existing users",library.settings().optBoolean("allowAdultContent",false));}finally{library.close();}
    }
}
