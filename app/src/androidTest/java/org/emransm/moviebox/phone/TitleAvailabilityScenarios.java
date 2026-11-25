package org.emransm.moviebox.phone;

import android.content.Context;
import android.content.ContextWrapper;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TitleAvailabilityScenarios {
    static JSONObject upcoming()throws Exception{return BlockFlowScenarios.title("availability-movie",1).put("hasResource",false).put("resourceDetectors",new JSONArray()).put("playUrl",new JSONObject().put("playUrl","").put("urlType","PLAY_URL_WEB"));}
    @Test public void verifiedFlagCombinationOnlyAndNoAlternateContext()throws Exception{
        assertTrue(TitleAvailability.comingSoon(upcoming()));
        for(String field:new String[]{"hasResource","resourceDetectors","playUrl"}){JSONObject item=upcoming();item.remove(field);assertFalse(TitleAvailability.comingSoon(item));}
        assertFalse(TitleAvailability.comingSoon(upcoming().put("hasResource",true)));
        assertFalse(TitleAvailability.comingSoon(upcoming().put("hasResource","false")));
        assertFalse(TitleAvailability.comingSoon(upcoming().put("resourceDetectors",new JSONArray().put(new JSONObject()))));
        assertFalse(TitleAvailability.comingSoon(upcoming().put("playUrl",new JSONObject().put("playUrl","https://example.invalid/signed").put("urlType","PLAY_URL_WEB"))));
        assertFalse(TitleAvailability.comingSoon(upcoming().put("playUrl",new JSONObject().put("playUrl","").put("urlType","NEW_UNKNOWN_TYPE"))));
        assertFalse(TitleAvailability.comingSoon(upcoming().put("externalWebsite",new JSONObject())));
        JSONObject extra=upcoming();extra.getJSONObject("playUrl").put("url","https://example.invalid/alternate");assertFalse(TitleAvailability.comingSoon(extra));
    }
    @Test public void cachedProofIsTitleBoundAndFreshAvailableContextWins()throws Exception{
        JSONObject safe=BlockFlowScenarios.title("availability-movie",1);TitleAvailability.copyCacheProof(upcoming(),safe);assertTrue(TitleAvailability.comingSoon(safe));
        assertFalse(safe.has("playUrl"));assertFalse(safe.has("resourceDetectors"));assertFalse(safe.has("hasResource"));
        JSONObject dub=new JSONObject(safe.toString()).put("subjectId","different-audio-edition");assertFalse(TitleAvailability.comingSoon(dub));
        safe.put("hasResource",true);assertFalse(TitleAvailability.comingSoon(safe));JSONObject changed=BlockFlowScenarios.title("availability-movie",1);TitleAvailability.copyCacheProof(safe,changed);assertFalse(TitleAvailability.comingSoon(changed));
    }
    @Test public void persistentDisplayProofContainsNoSourceContextAndCanBeReplaced()throws Exception{
        Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();File owned=new File(target.getCacheDir(),"qa-availability-"+System.nanoTime());assertTrue(owned.mkdirs());
        Context isolated=new ContextWrapper(target){@Override public File getCacheDir(){return owned;}};
        try{UiCache cache=new UiCache(isolated,false,false);JSONObject movie=upcoming();cache.writeDetail("availability-movie",movie);JSONObject restored=cache.readDetail("availability-movie");assertNotNull(restored);assertTrue(TitleAvailability.comingSoon(restored));assertFalse(restored.has("playUrl"));assertFalse(restored.has("resourceDetectors"));
            movie.put("hasResource",true).put("playUrl",new JSONObject().put("playUrl","https://example.invalid/private-signed-context").put("urlType","PLAY_URL_WEB"));cache.writeDetail("availability-movie",movie);restored=cache.readDetail("availability-movie");assertNotNull(restored);assertFalse(TitleAvailability.comingSoon(restored));assertFalse(restored.toString().contains("private-signed-context"));
        }finally{DetailLoadingScenarios.remove(owned,owned.getCanonicalPath());}
    }
    @Test public void malformedSourceSchemaAndPermissionErrorsAreNotNoSources()throws Exception{
        PlaybackAvailabilityScenarios.Service api=new PlaybackAvailabilityScenarios.Service();try{
            api.responses.put(1080,new JSONObject());try{api.initialPlayback(BlockFlowScenarios.title("schema-movie",1),0,0,1080);fail();}catch(java.io.IOException expected){assertFalse(expected instanceof Api.NoSourcesException);}
            api.failQuality=1080;api.failStatus=403;try{api.freshPlayback(BlockFlowScenarios.title("schema-movie",1),0,0,1080);fail();}catch(Api.ServiceHttpException expected){assertEquals(403,expected.status);}
            api.failQuality=-1;api.responses.put(1080,PlaybackAvailabilityScenarios.batch(new JSONArray()));try{api.freshPlayback(BlockFlowScenarios.title("schema-movie",1),0,0,1080);fail();}catch(Api.NoSourcesException expected){}
        }finally{api.close();}
    }
}
