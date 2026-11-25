package org.emransm.moviebox.phone;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Iterator;

/** Conservative display state, never an entitlement decision or a playback gate. */
final class TitleAvailability {
    private static final String PROOF="_movieboxAvailability";
    private TitleAvailability(){}
    static boolean supportsComingSoon(String id,int type,Object hasResource,boolean emptyDetectors,boolean blankWebContext,boolean alternateContext){
        return id!=null&&!id.trim().isEmpty()&&!id.equals("0")&&!id.startsWith("offline:")&&type==1
            &&Boolean.FALSE.equals(hasResource)&&emptyDetectors&&blankWebContext&&!alternateContext;
    }
    private static boolean hasRawContext(JSONObject item){for(String key:new String[]{"hasResource","resourceDetectors","playUrl","externalWebsite","streams","resources","resourceList","resourceLink","downloadUrl","url"})if(item.has(key))return true;return false;}
    private static boolean rawComingSoon(JSONObject item){
        JSONArray detectors=item.optJSONArray("resourceDetectors");JSONObject play=item.optJSONObject("playUrl");boolean blank=false;
        if(play!=null&&play.opt("playUrl") instanceof String&&((String)play.opt("playUrl")).trim().isEmpty()&&"PLAY_URL_WEB".equals(play.opt("urlType"))){
            blank=true;Iterator<String> keys=play.keys();while(keys.hasNext()){String key=keys.next();if(!key.equals("playUrl")&&!key.equals("urlType")){blank=false;break;}}
        }
        boolean alternate=false;for(String key:new String[]{"externalWebsite","streams","resources","resourceList","resourceLink","downloadUrl","url"})if(item.has(key)&&!item.isNull(key)){alternate=true;break;}
        return supportsComingSoon(item.optString("subjectId"),item.optInt("subjectType"),item.opt("hasResource"),detectors!=null&&detectors.length()==0,blank,alternate);
    }
    private static boolean validProof(JSONObject item){
        JSONObject proof=item.optJSONObject(PROOF);return proof!=null&&proof.length()==3&&proof.opt("schema") instanceof Number&&proof.optInt("schema")==1
            &&"coming-soon".equals(proof.opt("state"))&&item.opt("subjectId") instanceof String
            &&supportsComingSoon(item.optString("subjectId"),item.optInt("subjectType"),Boolean.FALSE,true,true,false)&&item.optString("subjectId").equals(proof.opt("subjectId"));
    }
    /** Call only after Api.NoSourcesException. Other failures remain retryable. */
    static boolean comingSoon(JSONObject item){return item!=null&&(hasRawContext(item)?rawComingSoon(item):validProof(item));}
    /** Preserve only the derived title-bound display proof, never source URLs/context. */
    static void copyCacheProof(JSONObject source,JSONObject safe)throws Exception{
        safe.remove(PROOF);if(comingSoon(source)&&safe.optString("subjectId").equals(source.optString("subjectId"))&&safe.optInt("subjectType")==1)
            safe.put(PROOF,new JSONObject().put("schema",1).put("state","coming-soon").put("subjectId",safe.optString("subjectId")));
    }
}
