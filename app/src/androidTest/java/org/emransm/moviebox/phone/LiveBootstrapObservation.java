package org.emransm.moviebox.phone;

import android.content.Context;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.*;
import java.util.*;
import java.io.*;

/** Opt-in protocol evidence: can a public title request itself initialize a guest session? */
@RunWith(AndroidJUnit4.class)
public class LiveBootstrapObservation {
 @Test public void directPublicLookupWithoutHomeBootstrap()throws Exception{
  org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveService","false").equals("true"));
  JSONArray samples=new JSONArray();
  for(String id:new String[]{"3486534283613956736","5705878031781299520"}){
   JSONArray calls=new JSONArray();Api api=new Api("owned-direct-bootstrap-20261123"){
    @Override public synchronized void warm(){}
    @Override public Object request(String path,Map<String,String> params,JSONObject body,boolean nativeApi)throws Exception{long start=SystemClock.elapsedRealtime();boolean ok=false;try{Object result=super.request(path,params,body,nativeApi);ok=true;return result;}finally{synchronized(calls){calls.put(new JSONObject().put("path",path).put("ms",SystemClock.elapsedRealtime()-start).put("ok",ok));}}}
   };JSONObject sample=new JSONObject().put("id",id).put("calls",calls);samples.put(sample);long at=SystemClock.elapsedRealtime();
   try{int type=id.equals("3486534283613956736")?1:2;JSONObject detail=api.detail(new JSONObject().put("subjectId",id).put("subjectType",type));sample.put("detailMs",SystemClock.elapsedRealtime()-at).put("correctId",id.equals(detail.optString("subjectId")));java.lang.reflect.Field token=Api.class.getDeclaredField("nativeToken");token.setAccessible(true);sample.put("issuedGuestToken",!((String)token.get(api)).isEmpty());
    if(type==1){at=SystemClock.elapsedRealtime();List<JSONObject> streams=api.initialPlayback(detail,0,0,1080);sample.put("lookupMs",SystemClock.elapsedRealtime()-at).put("sourceCount",streams.size());}
   }catch(Exception failure){sample.put("error",failure.getClass().getSimpleName()).put("message",failure.getMessage());}finally{api.close();}
  }
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();try(FileOutputStream out=new FileOutputStream(new File(context.getCacheDir(),"direct-bootstrap-observation.json"))){out.write(new JSONObject().put("samples",samples).toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 }
}
