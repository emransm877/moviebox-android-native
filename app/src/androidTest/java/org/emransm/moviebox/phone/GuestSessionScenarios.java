package org.emransm.moviebox.phone;

import android.content.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class GuestSessionScenarios {
 @Test public void keystoreEncryptionSurvivesRecreationAndRejectsWrongIdentityOrCorruption()throws Exception{
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();String store="qa-guest-"+System.nanoTime();GuestSession guest=new GuestSession(context,"owned-identity",store);
  try{assertEquals("",guest.load());guest.save("owned-fixture-bearer");assertEquals("owned-fixture-bearer",new GuestSession(context,"owned-identity",store).load());assertFalse(context.getSharedPreferences(store,0).getString("encrypted","").contains("owned-fixture-bearer"));assertEquals("",new GuestSession(context,"different-identity",store).load());context.getSharedPreferences(store,0).edit().putString("encrypted","corrupt").commit();assertEquals("",guest.load());guest.clear();assertEquals("",guest.load());}finally{context.getSharedPreferences(store,0).edit().clear().commit();}
 }
 static class Saved implements Api.NativeSession {String token="valid-guest";int cleared;public String load(){return token;}public void save(String token){this.token=token;}public void clear(){cleared++;token="";}}
 static class Service extends Api {
  final List<String> calls=new ArrayList<>();boolean fail,denyAgain;
  Service(Saved saved)throws Exception{super("owned-session-fixture-00000000",null,saved);}
  void set(String name,Object value)throws Exception{java.lang.reflect.Field f=Api.class.getDeclaredField(name);f.setAccessible(true);f.set(this,value);}
  @Override protected Object requestOnce(String path,Map<String,String> params,JSONObject body,boolean nativeApi)throws Exception{calls.add(path);if(path.equals("/tab-operating")){set("nativeToken","refreshed-guest");set("nativeReady",true);return new JSONObject();}if(fail){fail=denyAgain;throw new ServiceHttpException(441);}return new JSONObject().put("subjectId","owned");}
 }
 @Test public void restoredGuestAvoidsHomeBootstrapAndExpiryRetriesOnlyOnce()throws Exception{
  Saved saved=new Saved();Service service=new Service(saved);try{
   service.warm();assertTrue("A saved valid guest must not fetch all Home data",service.calls.isEmpty());service.fail=true;assertEquals("owned",((JSONObject)service.request("/subject-api/get",Api.args("subjectId","owned"),null,true)).getString("subjectId"));assertEquals(Arrays.asList("/subject-api/get","/tab-operating","/subject-api/get"),service.calls);assertEquals(1,saved.cleared);
   service.calls.clear();service.fail=true;service.denyAgain=true;try{service.request("/subject-api/get",null,null,true);fail("Must return a real persistent authentication failure");}catch(Api.ServiceHttpException expected){assertEquals(441,expected.status);}assertEquals("No infinite refresh loop",3,service.calls.size());
  }finally{service.close();}
 }
}
