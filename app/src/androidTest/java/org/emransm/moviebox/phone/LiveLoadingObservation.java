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

/** Opt-in real-service observations; never runs in the normal fixture suite. No tokens/URLs recorded. */
@RunWith(AndroidJUnit4.class)
public class LiveLoadingObservation {
 @Test public void observe() throws Exception {
  org.junit.Assume.assumeTrue("Opt-in real service benchmark",InstrumentationRegistry.getArguments().getString("liveService", "false").equals("true"));
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
  JSONArray calls=new JSONArray(),samples=new JSONArray();
  Api api=new Api("phone-loading-observation-20261123"){
   @Override public Object request(String path,Map<String,String> params,JSONObject body,boolean nativeApi)throws Exception{
    long start=SystemClock.elapsedRealtime();boolean ok=false;
    try{Object result=super.request(path,params,body,nativeApi);ok=true;return result;}
    finally{synchronized(calls){calls.put(new JSONObject().put("path",path).put("ms",SystemClock.elapsedRealtime()-start).put("ok",ok));}}
   }
  };
  JSONObject report=new JSONObject().put("calls",calls).put("samples",samples);
  try{
   long start=SystemClock.elapsedRealtime();List<JSONObject> home=api.home();report.put("homeMs",SystemClock.elapsedRealtime()-start).put("homeCount",home.size());
   int movies=0,series=0;
   for(JSONObject item:home){int type=item.optInt("subjectType");if(type==1&&movies++>=1||type==2&&series++>=2)continue;
    start=SystemClock.elapsedRealtime();try{JSONObject detail=api.detail(item);long cold=SystemClock.elapsedRealtime()-start;start=SystemClock.elapsedRealtime();api.detail(item);samples.put(new JSONObject().put("id",item.optString("subjectId")).put("type",type).put("firstDetailMs",cold).put("repeatDetailMs",SystemClock.elapsedRealtime()-start).put("seasons",detail.optJSONArray("seasons")==null?0:detail.optJSONArray("seasons").length()));}catch(Exception failure){samples.put(new JSONObject().put("id",item.optString("subjectId")).put("error",failure.getClass().getSimpleName()));}
    if(movies>=1&&series>=2)break;
   }
  }finally{api.close();try(FileOutputStream out=new FileOutputStream(new File(context.getCacheDir(),"live-loading-observation.json"))){out.write(report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}}
 }
}
