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
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LiveGuestSessionObservation {
 @Test public void recreatedClientUsesItsEncryptedGuestWithoutFetchingHomeAgain()throws Exception{
  org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveService","false").equals("true"));
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();String store="qa-live-guest-"+System.nanoTime(),identity="owned-live-guest-20261123-identity";GuestSession guest=new GuestSession(context,identity,store);JSONObject report=new JSONObject();Api first=new Api(identity,null,guest);Api second=null;
  try{long at=SystemClock.elapsedRealtime();first.home();report.put("firstBootstrapMs",SystemClock.elapsedRealtime()-at);assertFalse(guest.load().isEmpty());first.close();JSONArray calls=new JSONArray();
   second=new Api(identity,null,new GuestSession(context,identity,store)){
    @Override public Object request(String path,Map<String,String> params,JSONObject body,boolean nativeApi)throws Exception{calls.put(path);return super.request(path,params,body,nativeApi);}
   };at=SystemClock.elapsedRealtime();JSONObject item=second.detail(new JSONObject().put("subjectId","3486534283613956736").put("subjectType",1));report.put("recreatedUncachedDetailMs",SystemClock.elapsedRealtime()-at).put("calls",calls);assertEquals("3486534283613956736",item.optString("subjectId"));assertEquals("A recreated guest must not reload Home as a prerequisite",1,calls.length());assertEquals("/subject-api/get",calls.getString(0));at=SystemClock.elapsedRealtime();assertFalse(second.initialPlayback(item,0,0,1080).isEmpty());report.put("recreatedSourceMs",SystemClock.elapsedRealtime()-at);
  }finally{first.close();if(second!=null)second.close();guest.clear();try(FileOutputStream out=new FileOutputStream(new File(context.getCacheDir(),"live-guest-observation.json"))){out.write(report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}}
 }
}
