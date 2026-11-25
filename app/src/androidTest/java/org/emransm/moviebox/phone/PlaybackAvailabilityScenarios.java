package org.emransm.moviebox.phone;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.*;
import java.io.IOException;
import static org.junit.Assert.*;

/** Resource metadata fixtures; never download a video or infer missing renditions. */
@RunWith(AndroidJUnit4.class)
public class PlaybackAvailabilityScenarios {
    static JSONObject quality(int q)throws Exception{return new JSONObject().put("resolution",q);}
    static JSONObject row(int q,int se,int ep)throws Exception{return new JSONObject().put("resourceId","r"+q).put("resolution",q).put("se",se).put("ep",ep).put("resourceLink","https://example.invalid/owned"+q+".mp4").put("size",10000000).put("duration",600);}
    static JSONObject batch(JSONArray list,JSONObject... qualities)throws Exception{JSONArray values=new JSONArray();for(JSONObject q:qualities)values.put(q);return new JSONObject().put("list",list).put("collectionResolutions",values);}
    static class Service extends Api {
        final List<Integer> requests=new ArrayList<>();final Map<Integer,JSONObject> responses=new HashMap<>();int failQuality=-1,failStatus;
        Service()throws Exception{super("0123456789abcdef0123456789abcdef");}
        @Override public void warm(){}
        @Override public Object request(String path,Map<String,String> args,JSONObject body,boolean nativeApi)throws Exception{assertEquals("/subject-api/resource",path);int q=Integer.parseInt(args.get("resolution"));requests.add(q);if(q==failQuality)throw new ServiceHttpException(failStatus);JSONObject value=responses.get(q);if(value==null)throw new AssertionError("Unadvertised quality requested: "+q);return value;}
    }
    @Test public void emptyPreferredReturnsFirstActualFallbackWithoutEnumeratingAll()throws Exception{
        Service api=new Service();try{
            api.responses.put(1080,batch(new JSONArray(),quality(1080),quality(720),quality(480)));
            api.responses.put(720,batch(new JSONArray().put(row(720,0,0)),quality(720)));
            List<JSONObject> result=api.initialPlayback(BlockFlowScenarios.title("movie",1),0,0,1080);
            assertEquals(720,result.get(0).getInt("resolution"));assertEquals(Arrays.asList(1080,720),api.requests);
        }finally{api.close();}
    }
    @Test public void refreshCanUseAdvertisedHigherQualityWhenSelectedQualityDisappears()throws Exception{
        Service api=new Service();try{
            api.responses.put(720,batch(new JSONArray(),quality(1080)));
            api.responses.put(1080,batch(new JSONArray().put(row(1080,2,4)),quality(1080)));
            assertEquals(1080,api.freshPlayback(BlockFlowScenarios.title("series",2),2,4,720).get(0).getInt("resolution"));assertEquals(Arrays.asList(720,1080),api.requests);
        }finally{api.close();}
    }
    @Test public void lockedAndWrongEpisodeNeverBecomeFallback()throws Exception{
        Service api=new Service();try{
            api.responses.put(1080,batch(new JSONArray().put(row(1080,2,3)),quality(720).put("requireMemberType",1),quality(480)));
            api.responses.put(480,batch(new JSONArray().put(row(480,2,4).put("vipLocked",true)),quality(480)));
            try{api.initialPlayback(BlockFlowScenarios.title("series",2),2,4,1080);fail();}catch(IOException expected){}
            assertEquals(Arrays.asList(1080,480),api.requests);
        }finally{api.close();}
    }
    @Test public void emptyAvailabilityIsNotCachedAndNo720IsInvented()throws Exception{
        Service api=new Service();try{
            api.responses.put(1080,batch(new JSONArray()));JSONObject movie=BlockFlowScenarios.title("movie",1);
            try{api.initialPlayback(movie,0,0,1080);fail();}catch(IOException expected){}
            api.responses.put(1080,batch(new JSONArray().put(row(1080,0,0))));assertFalse(api.initialPlayback(movie,0,0,1080).isEmpty());assertEquals(Arrays.asList(1080,1080),api.requests);
        }finally{api.close();}
    }
    @Test public void permissionFailureIsNotSwallowedOrRetriedAcrossQualities()throws Exception{
        for(int status:new int[]{401,403,429,441}){Service api=new Service();try{
            api.responses.put(1080,batch(new JSONArray(),quality(720),quality(480)));api.failQuality=720;api.failStatus=status;
            try{api.initialPlayback(BlockFlowScenarios.title("movie-"+status,1),0,0,1080);fail("Protected status must propagate: "+status);}catch(Api.ServiceHttpException expected){assertEquals(status,expected.status);}
            assertEquals("No fallback after protected status "+status,Arrays.asList(1080,720),api.requests);
        }finally{api.close();}}
    }
}
