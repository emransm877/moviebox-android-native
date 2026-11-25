package org.emransm.moviebox.phone;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Title-bound metadata suggestions; inclusion never asserts playback availability. */
final class WatchRecommendations {
    static JSONObject requestBody(String subjectId) throws JSONException {
        if(subjectId==null||subjectId.trim().isEmpty()||subjectId.equals("0")||subjectId.startsWith("offline:"))
            throw new IllegalArgumentException("A catalogue title is required");
        return new JSONObject().put("subjectId",subjectId).put("name","").put("page",1).put("perPage",6);
    }
    static List<JSONObject> parse(Object response,String subjectId,boolean familyMode) throws JSONException {
        // ForYouBean has items: List<Subject>. Do not recursively collect arbitrary
        // nested promotion objects or substitute the home feed on schema failure.
        JSONArray items=response instanceof JSONObject?((JSONObject)response).optJSONArray("items"):null;
        if(items==null||subjectId==null||subjectId.isEmpty())return Collections.emptyList();
        List<JSONObject> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(int n=0;n<Math.min(items.length(),200)&&result.size()<6;n++){
            JSONObject item=items.optJSONObject(n);if(item==null)continue;
            String id=item.optString("subjectId").trim();int type=item.optInt("subjectType");
            if(id.isEmpty()||id.equals("0")||id.startsWith("offline:")||id.equals(subjectId)||!seen.add(id)
                ||item.optString("title").trim().isEmpty()||(type!=1&&type!=2)
                ||familyMode&&AdultContentPolicy.markedAdult(item))continue;
            result.add(new JSONObject(item.toString()));
        }
        return result;
    }

    static final class Ticket {
        final String subjectId;final boolean familyMode;private final long sequence;
        private Ticket(String id,boolean family,long sequence){subjectId=id;familyMode=family;this.sequence=sequence;}
    }
    private long sequence;private Ticket current;private List<JSONObject> items=Collections.emptyList();
    synchronized Ticket begin(String subjectId,boolean familyMode){
        current=new Ticket(subjectId,familyMode,++sequence);items=Collections.emptyList();return current;
    }
    synchronized void clear(){current=null;sequence++;items=Collections.emptyList();}
    synchronized boolean accept(Ticket ticket,List<JSONObject> result) throws JSONException {
        if(ticket==null||current!=ticket||ticket.sequence!=sequence)return false;
        List<JSONObject> copy=new ArrayList<>();if(result!=null)for(JSONObject item:result)copy.add(new JSONObject(item.toString()));
        items=copy;return true;
    }
    synchronized List<JSONObject> forTitle(String subjectId,boolean familyMode) throws JSONException {
        if(current==null||!current.subjectId.equals(subjectId)||current.familyMode!=familyMode)return Collections.emptyList();
        List<JSONObject> copy=new ArrayList<>();for(JSONObject item:items)copy.add(new JSONObject(item.toString()));return copy;
    }
}
