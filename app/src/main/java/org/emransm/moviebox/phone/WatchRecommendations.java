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
    static final int PAGE_SIZE=12,MAX_ITEMS=50,MAX_PAGES=10;
    static JSONObject requestBody(String subjectId) throws JSONException {
        return requestBody(subjectId,1,6);
    }
    static JSONObject requestBody(String subjectId,int page,int perPage) throws JSONException {
        if(subjectId==null||subjectId.trim().isEmpty()||subjectId.equals("0")||subjectId.startsWith("offline:"))
            throw new IllegalArgumentException("A catalogue title is required");
        if(page<1||page>MAX_PAGES||perPage<1||perPage>PAGE_SIZE)throw new IllegalArgumentException("Related-title page is out of range");
        return new JSONObject().put("subjectId",subjectId).put("name","").put("page",page).put("perPage",perPage);
    }
    static List<JSONObject> parse(Object response,String subjectId,boolean familyMode) throws JSONException {
        return parseItems(response,subjectId,familyMode,6);
    }
    private static List<JSONObject> parseItems(Object response,String subjectId,boolean familyMode,int limit) throws JSONException {
        // ForYouBean has items: List<Subject>. Do not recursively collect arbitrary
        // nested promotion objects or substitute the home feed on schema failure.
        JSONArray items=response instanceof JSONObject?((JSONObject)response).optJSONArray("items"):null;
        if(items==null||subjectId==null||subjectId.isEmpty())return Collections.emptyList();
        List<JSONObject> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(int n=0;n<Math.min(items.length(),200)&&result.size()<limit;n++){
            JSONObject item=items.optJSONObject(n);if(item==null)continue;
            String id=item.optString("subjectId").trim();int type=item.optInt("subjectType");
            if(id.isEmpty()||id.equals("0")||id.startsWith("offline:")||id.equals(subjectId)||!seen.add(id)
                ||item.optString("title").trim().isEmpty()||(type!=1&&type!=2)
                ||familyMode&&AdultContentPolicy.markedAdult(item))continue;
            result.add(new JSONObject(item.toString()));
        }
        return result;
    }

    /** An actual service page, not a promise of media availability or total size. */
    static final class Page {
        final String subjectId;final boolean familyMode;final int page,perPage,nextPage;
        final boolean hasMore;final List<JSONObject> items;
        Page(String subjectId,boolean familyMode,int page,List<JSONObject> rows,boolean hasMore,int nextPage)throws JSONException {
            requestBody(subjectId,page,PAGE_SIZE);
            this.subjectId=subjectId;this.familyMode=familyMode;this.page=page;perPage=PAGE_SIZE;
            // Never trust totalCount (the real service currently returns a large
            // sentinel), jump to an arbitrary page or fetch without an end cap.
            this.hasMore=hasMore&&page<MAX_PAGES&&nextPage==page+1;
            this.nextPage=this.hasMore?nextPage:0;
            JSONArray array=new JSONArray();if(rows!=null)for(JSONObject row:rows)array.put(row);
            items=Collections.unmodifiableList(parseItems(new JSONObject().put("items",array),subjectId,familyMode,PAGE_SIZE));
        }
    }
    private static int pageNumber(Object value){
        if(!(value instanceof String||value instanceof Number))return -1;
        String number=value.toString();if(!number.matches("[1-9][0-9]{0,2}"))return -1;
        try{return Integer.parseInt(number);}catch(NumberFormatException invalid){return -1;}
    }
    static Page parsePage(Object response,String subjectId,boolean familyMode,int requestedPage)throws JSONException {
        requestBody(subjectId,requestedPage,PAGE_SIZE);
        if(!(response instanceof JSONObject)||!(((JSONObject)response).opt("items") instanceof JSONArray))throw new JSONException("Related-title metadata could not be read");
        JSONObject result=(JSONObject)response,pager=result.optJSONObject("pager");
        // Missing or unverified paging context is a single page, not an inferred
        // endless feed. String page numbers and boolean hasMore match live data.
        boolean more=pager!=null&&Boolean.TRUE.equals(pager.opt("hasMore"))&&pageNumber(pager.opt("page"))==requestedPage;
        int next=more?pageNumber(pager.opt("nextPage")):0;
        return new Page(subjectId,familyMode,requestedPage,parseItems(response,subjectId,familyMode,PAGE_SIZE),more,next);
    }

    static final class Ticket {
        final String subjectId;final boolean familyMode;private final long sequence;
        private Ticket(String id,boolean family,long sequence){subjectId=id;familyMode=family;this.sequence=sequence;}
    }
    private long sequence;private Ticket current;private List<JSONObject> items=Collections.emptyList();private int nextPage=1,pagesAccepted;private boolean more=true;
    synchronized Ticket begin(String subjectId,boolean familyMode){
        current=new Ticket(subjectId,familyMode,++sequence);items=Collections.emptyList();nextPage=1;pagesAccepted=0;more=true;return current;
    }
    synchronized void clear(){current=null;sequence++;items=Collections.emptyList();nextPage=0;pagesAccepted=0;more=false;}
    synchronized boolean accept(Ticket ticket,List<JSONObject> result) throws JSONException {
        if(ticket==null||current!=ticket||ticket.sequence!=sequence)return false;
        List<JSONObject> copy=new ArrayList<>();if(result!=null)for(JSONObject item:result)copy.add(new JSONObject(item.toString()));
        items=copy;more=false;nextPage=0;return true;
    }
    synchronized boolean accept(Ticket ticket,Page result)throws JSONException {
        if(ticket==null||current!=ticket||ticket.sequence!=sequence||result==null||!more||result.page!=nextPage
            ||!ticket.subjectId.equals(result.subjectId)||ticket.familyMode!=result.familyMode)return false;
        List<JSONObject> copy=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(JSONObject item:items){copy.add(new JSONObject(item.toString()));seen.add(item.optString("subjectId"));}
        int before=copy.size();JSONArray incoming=new JSONArray();for(JSONObject row:result.items)incoming.put(row);
        for(JSONObject item:parseItems(new JSONObject().put("items",incoming),ticket.subjectId,ticket.familyMode,PAGE_SIZE))
            if(copy.size()<MAX_ITEMS&&seen.add(item.optString("subjectId")))copy.add(new JSONObject(item.toString()));
        items=copy;pagesAccepted++;
        // A repeated/all-filtered page supplies no progress. Stop there rather
        // than issuing a scroll-triggered stream of duplicate requests.
        more=result.hasMore&&copy.size()>before&&copy.size()<MAX_ITEMS&&pagesAccepted<MAX_PAGES;
        nextPage=more?result.nextPage:0;return true;
    }
    synchronized boolean hasMore(String subjectId,boolean familyMode){
        return current!=null&&current.subjectId.equals(subjectId)&&current.familyMode==familyMode&&more&&items.size()<MAX_ITEMS;
    }
    synchronized int nextPage(String subjectId,boolean familyMode){
        return hasMore(subjectId,familyMode)?nextPage:0;
    }
    synchronized List<JSONObject> forTitle(String subjectId,boolean familyMode) throws JSONException {
        if(current==null||!current.subjectId.equals(subjectId)||current.familyMode!=familyMode)return Collections.emptyList();
        List<JSONObject> copy=new ArrayList<>();for(JSONObject item:items)copy.add(new JSONObject(item.toString()));return copy;
    }
}
