package org.emransm.moviebox.phone;

import android.os.Build;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;

/** Native service adapter. Uses fresh guest identity, never phone account data. */
public class Api {
    static final String NATIVE="https://api6.aoneroom.com",PREFIX="/wefeed-mobile-bff";
    static final String WEB="https://h5-api.aoneroom.com/wefeed-h5api-bff";
    static final String UA="MovieBoxPhone/"+BuildConfig.VERSION_NAME+" (Android "+Build.VERSION.RELEASE+"; "+Build.MODEL+")";
    private volatile String webToken="",nativeToken="";private volatile boolean nativeReady=false;
    private final Map<String,JSONObject> details=new java.util.concurrent.ConcurrentHashMap<>();private final Map<String,Long> detailTimes=new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String,JSONObject> sourceCache=new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String,Long> sourceTimes=new java.util.concurrent.ConcurrentHashMap<>();
    private final JSONObject client;
    private UiCache uiCache;
    interface NativeSession {String load();void save(String token);void clear();}
    private NativeSession session;private volatile boolean sessionLoaded;private final Object sessionLock=new Object();
    private final Object[] detailLocks=new Object[8],resourceLocks=new Object[8];
    {for(int n=0;n<8;n++){detailLocks[n]=new Object();resourceLocks[n]=new Object();}}
    private final java.util.concurrent.ThreadPoolExecutor detailRequests=new java.util.concurrent.ThreadPoolExecutor(0,4,15,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.SynchronousQueue<>());
    public Api(String identity,UiCache cache)throws JSONException{this(identity);uiCache=cache;}
    Api(String identity,UiCache cache,NativeSession session)throws JSONException{this(identity,cache);this.session=session;}
    private void restoreSession(){if(sessionLoaded)return;synchronized(sessionLock){if(!sessionLoaded){if(session!=null)nativeToken=session.load();sessionLoaded=true;}}}
    public void close(){detailRequests.shutdownNow();}
    private final Map<String,JSONObject> resourceContext=new java.util.concurrent.ConcurrentHashMap<>();
    public Api(String identity) throws JSONException {
        // Existing desktop/Tizen wire-protocol compatibility fields. The APK,
        // User-Agent, device model and store remain independently identified;
        // no upstream certificate, account session or attestation is supplied.
        client=new JSONObject().put("package_name","com.community.oneroom").put("version_name","4.0.03.0930.03").put("version_code",50020104)
            .put("os","android").put("os_version",Build.VERSION.RELEASE).put("device_id",identity).put("gaid",identity.substring(0,16))
            .put("install_ch","independent").put("install_store","sideload").put("brand",Build.BRAND).put("model",Build.MODEL)
            .put("system_language","en").put("net","NETWORK_WIFI").put("region","US").put("timezone",TimeZone.getDefault().getID())
            .put("sp_code","20801").put("X-Child-UID","").put("X-Play-Mode","2").put("X-Idle-Data","1").put("X-Family-Mode","0").put("X-Content-Mode","0");
    }
    static String md5(byte[] bytes) throws Exception {byte[] digest=MessageDigest.getInstance("MD5").digest(bytes);StringBuilder value=new StringBuilder();for(byte b:digest)value.append(String.format(Locale.ROOT,"%02x",b&255));return value.toString();}
    static String encode(String value) throws Exception{return URLEncoder.encode(value,"UTF-8");}
    static String query(Map<String,String> params) throws Exception {StringBuilder out=new StringBuilder();for(Map.Entry<String,String> entry:params.entrySet()){if(out.length()>0)out.append('&');out.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));}return out.toString();}
    static Map<String,String> args(Object... pairs){Map<String,String> result=new TreeMap<>();for(int i=0;i<pairs.length;i+=2)result.put(pairs[i].toString(),String.valueOf(pairs[i+1]));return result;}
    static byte[] read(InputStream stream,int max) throws IOException {try(InputStream input=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[16384];int n;while((n=input.read(buffer))!=-1){if(out.size()+n>max)throw new IOException("Response exceeds limit");out.write(buffer,0,n);}return out.toByteArray();}}
    static final class ServiceHttpException extends IOException {final int status;ServiceHttpException(int status){super("Service unavailable (HTTP "+status+"). Try again.");this.status=status;}}
    public Object request(String path,Map<String,String> params,JSONObject body,boolean nativeApi)throws Exception{
        if(nativeApi)restoreSession();String attempted=nativeToken;
        try{return requestOnce(path,params,body,nativeApi);}catch(ServiceHttpException failure){
            if(!nativeApi||(failure.status!=401&&failure.status!=441))throw failure;
            synchronized(sessionLock){if(nativeToken.equals(attempted)){nativeToken="";nativeReady=false;if(session!=null)session.clear();}}
            if(path.equals("/tab-operating"))return requestOnce(path,params,body,true);
            warm();return requestOnce(path,params,body,true);
        }
    }
    protected Object requestOnce(String path,Map<String,String> params,JSONObject body,boolean nativeApi) throws Exception {
        String location=(nativeApi?NATIVE+PREFIX:WEB)+path+(params==null||params.isEmpty()?"":"?"+query(params));
        URL url=new URL(location);HttpsURLConnection connection=(HttpsURLConnection)url.openConnection();
        connection.setConnectTimeout(8000);connection.setReadTimeout(10000);connection.setInstanceFollowRedirects(false);
        byte[] payload=body==null?null:body.toString().getBytes(StandardCharsets.UTF_8);String method=payload==null?"GET":"POST";
        connection.setRequestMethod(method);connection.setRequestProperty("Accept","application/json");connection.setRequestProperty("Content-Type","application/json");connection.setRequestProperty("User-Agent",UA);
        String timestamp=String.valueOf(nativeApi?System.currentTimeMillis():System.currentTimeMillis()/1000);
        connection.setRequestProperty("X-Client-Token",timestamp+","+md5(new StringBuilder(timestamp).reverse().toString().getBytes(StandardCharsets.UTF_8)));
        connection.setRequestProperty("X-Client-Info",nativeApi?client.toString():new JSONObject().put("timezone",TimeZone.getDefault().getID()).toString());
        String token=nativeApi?nativeToken:webToken;if(!token.isEmpty())connection.setRequestProperty("Authorization","Bearer "+token);
        if(nativeApi){
            StringBuilder canonical=new StringBuilder(url.getPath());if(params!=null&&!params.isEmpty()){canonical.append('?');for(Map.Entry<String,String> entry:new TreeMap<>(params).entrySet())canonical.append(entry.getKey()).append('=').append(entry.getValue()).append('&');canonical.setLength(canonical.length()-1);}
            String message=method+"\napplication/json\napplication/json\n"+(payload==null?"":payload.length)+"\n"+timestamp+"\n"+(payload==null?"":md5(Arrays.copyOf(payload,Math.min(payload.length,102400))))+"\n"+canonical;
            byte[] key=Base64.decode("76iRl07s0xSN9jqmEWAt79EBJZulIQIsV64FZr2O",Base64.DEFAULT);Mac signer=Mac.getInstance("HmacMD5");signer.init(new SecretKeySpec(key,"HmacMD5"));
            connection.setRequestProperty("x-tr-signature",timestamp+"|2|"+Base64.encodeToString(signer.doFinal(message.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP));
            connection.setRequestProperty("X-Client-Status","0");
            for(String field:new String[]{"X-Child-UID","X-Play-Mode","X-Idle-Data","X-Family-Mode","X-Content-Mode"})connection.setRequestProperty(field,client.optString(field));
        }else{connection.setRequestProperty("Referer","https://movieboxhd.net/");connection.setRequestProperty("X-Request-Lang","en");connection.setRequestProperty("X-Vip-Restrict","1");}
        try{
            if(payload!=null){connection.setDoOutput(true);try(OutputStream output=connection.getOutputStream()){output.write(payload);}}
            int status=connection.getResponseCode();if(status!=200)throw new ServiceHttpException(status);
            String user=connection.getHeaderField("x-user");if(user!=null){String fresh=new JSONObject(user).optString("token");if(nativeApi){boolean changed=!fresh.equals(nativeToken);nativeToken=fresh;if(changed&&session!=null&&!fresh.isEmpty())session.save(fresh);}else webToken=fresh;}
            JSONObject result=new JSONObject(Utf8Json.decode(read(connection.getInputStream(),4*1024*1024)));
            if(result.optInt("code",0)!=0)throw new IOException(result.optString("message","Service refused this request"));
            if(nativeApi&&path.equals("/tab-operating"))nativeReady=true;return result.has("data")&&!result.isNull("data")?result.get("data"):result;
        }finally{connection.disconnect();}
    }
    static boolean sameReleaseYear(String first,String second){
        if(first==null||second==null||!first.matches("[0-9]{4}(?:-[0-9]{2}-[0-9]{2})?")||!second.matches("[0-9]{4}(?:-[0-9]{2}-[0-9]{2})?"))return false;
        return first.substring(0,4).equals(second.substring(0,4));
    }
    private synchronized Object nativeHome()throws Exception{
        return request("/tab-operating",args("page",1,"tabId",0,"version",""),null,true);
    }
    public void warm() throws Exception {restoreSession();if(nativeReady||!nativeToken.isEmpty())return;synchronized(this){if(!nativeReady&&nativeToken.isEmpty())nativeHome();}}
    public List<JSONObject> home() throws Exception {
        // The mobile feed supplies title-bound landscape banners. Ignore non-title promotions.
        try {
            Object response=nativeHome();
            LinkedHashMap<String,JSONObject> titles=new LinkedHashMap<>();collect(response,titles);
            JSONArray sections=response instanceof JSONObject?((JSONObject)response).optJSONArray("items"):null;
            if(sections!=null)for(int i=0;i<sections.length();i++){
                JSONObject section=sections.optJSONObject(i), banner=section==null?null:section.optJSONObject("banner");
                JSONArray banners=banner==null?null:banner.optJSONArray("banners");if(banners==null)continue;
                for(int j=0;j<banners.length();j++){
                    JSONObject row=banners.optJSONObject(j);if(row==null)continue;
                    JSONObject original=row.optJSONObject("subject"),image=row.optJSONObject("image");String id=row.optString("subjectId");JSONObject title=titles.get(id);
                    if(original==null||image==null||title==null||id.equals("0")||!id.equals(original.optString("subjectId"))||!title.optString("title").equals(original.optString("title"))||title.optInt("subjectType")!=original.optInt("subjectType")||!sameReleaseYear(title.optString("releaseDate"),original.optString("releaseDate")))continue;
                    String link=image.optString("url");if(!link.startsWith("https://")||image.optInt("width")<image.optInt("height")||(image.has("subjectId")&&!id.equals(image.optString("subjectId"))))continue;
                    title.put("banner",new JSONObject(image.toString()).put("subjectId",id));
                }
            }
            if(!titles.isEmpty())return new ArrayList<>(titles.values());
        }catch(Exception ignored){ /* The web catalogue remains available during a mobile-feed outage. */ }
        return webHome();
    }
    private List<JSONObject> webHome() throws Exception {
        Object result=request("/home",args("host","movieboxhd.net"),null,false);LinkedHashMap<String,JSONObject> found=new LinkedHashMap<>();collect(result,found);
        if(result instanceof JSONObject){JSONArray sections=((JSONObject)result).optJSONArray("operatingList");if(sections!=null)for(int i=0;i<sections.length();i++){
            JSONObject section=sections.optJSONObject(i);JSONObject banner=section==null?null:section.optJSONObject("banner");JSONArray items=banner==null?null:banner.optJSONArray("items");if(items==null)continue;
            for(int j=0;j<items.length();j++){JSONObject row=items.optJSONObject(j);if(row==null)continue;JSONObject subject=row.optJSONObject("subject"),image=row.optJSONObject("image");String id=row.optString("subjectId");JSONObject selected=found.get(id);
                if(subject==null||image==null||selected==null||!id.equals(subject.optString("subjectId"))||row.optInt("subjectType")!=subject.optInt("subjectType")||!selected.optString("title").equals(subject.optString("title"))||!sameReleaseYear(selected.optString("releaseDate"),subject.optString("releaseDate")))continue;
                JSONObject art=new JSONObject(image.toString()).put("subjectId",id);selected.put("banner",art);
            }
        }}
        return new ArrayList<>(found.values());
    }
    private void collect(Object value,Map<String,JSONObject> found) throws JSONException {
        if(value instanceof JSONObject){JSONObject item=(JSONObject)value;int type=item.optInt("subjectType");if(item.has("subjectId")&&!item.optString("title").isEmpty()&&(type==1||type==2)){found.put(item.optString("subjectId"),item);return;}Iterator<String> keys=item.keys();while(keys.hasNext())collect(item.opt(keys.next()),found);}
        else if(value instanceof JSONArray){JSONArray array=(JSONArray)value;for(int i=0;i<array.length();i++)collect(array.opt(i),found);}
    }
    static List<JSONObject> items(Object value) throws JSONException {JSONArray list=value instanceof JSONArray?(JSONArray)value:((JSONObject)value).optJSONArray("items");if(list==null)list=((JSONObject)value).optJSONArray("list");List<JSONObject> result=new ArrayList<>();if(list!=null)for(int i=0;i<list.length();i++)if(list.optJSONObject(i)!=null)result.add(list.getJSONObject(i));return result;}
    public List<JSONObject> catalogue(int type,int page,JSONObject filters) throws Exception {JSONObject body=new JSONObject(filters.toString()).put("channelId",type).put("page",page).put("perPage",30);return items(request("/subject/filter",null,body,false));}
    public List<JSONObject> search(String keyword) throws Exception {warm();return items(request("/subject-api/search",args("host",NATIVE),new JSONObject().put("keyword",keyword).put("page",1).put("perPage",20).put("subjectType",0),true));}
    public List<String> suggest(String keyword) throws Exception {warm();Object response=request("/subject-api/search-suggest",args("keyword",keyword,"perPage",8,"resultMode",0),null,true);List<String> result=new ArrayList<>();for(JSONObject item:items(response)){String text=item.optString("word",item.optString("title",item.optString("keyword")));if(!text.isEmpty())result.add(text);if(result.size()>=8)break;}return result;}
    /** Memory-only lookup is safe on the UI thread. Disk and network run in detail(). */
    public JSONObject cachedDetail(String id){try{JSONObject saved=details.get(id);Long at=detailTimes.get(id);if(saved!=null&&at!=null&&android.os.SystemClock.elapsedRealtime()-at<UiCache.DETAIL_AGE_MS)return new JSONObject(saved.toString());}catch(Exception ignored){}return null;}
    private JSONObject requestSeasons(String id)throws Exception{return (JSONObject)request("/subject-api/season-info",args("host",NATIVE,"subjectId",id),null,true);}
    public JSONObject detail(JSONObject subject) throws Exception {
        String id=subject.getString("subjectId");
        synchronized(detailLocks[(id.hashCode()&Integer.MAX_VALUE)%detailLocks.length]){
            JSONObject saved=cachedDetail(id);if(saved!=null)return saved;
            if(uiCache!=null){saved=uiCache.readDetail(id);if(saved!=null)return saved;}
            warm();java.util.concurrent.Future<JSONObject> seasonRequest=null;
            if(subject.optInt("subjectType")==2)try{seasonRequest=detailRequests.submit(()->requestSeasons(id));}catch(java.util.concurrent.RejectedExecutionException busy){/* Foreground request can still run sequentially. */}
            try{
                JSONObject result=(JSONObject)request("/subject-api/get",args("host",NATIVE,"subjectId",id),null,true);
                if(!id.equals(result.optString("subjectId")))throw new IOException("The service returned a different title. Please retry.");
                if(result.optInt("subjectType")==2){JSONObject seasons;
                    try{seasons=seasonRequest==null?requestSeasons(id):seasonRequest.get(20,java.util.concurrent.TimeUnit.SECONDS);}
                    catch(java.util.concurrent.ExecutionException failure){Throwable cause=failure.getCause();if(cause instanceof Exception)throw (Exception)cause;throw failure;}
                    result.put("seasons",seasons.optJSONArray("seasons"));
                }
                if(details.size()>=48){details.clear();detailTimes.clear();}details.put(id,new JSONObject(result.toString()));detailTimes.put(id,android.os.SystemClock.elapsedRealtime());
                if(uiCache!=null)uiCache.writeDetail(id,result);return result;
            }finally{if(seasonRequest!=null&&!seasonRequest.isDone())seasonRequest.cancel(true);}
        }
    }
    private JSONObject resources(JSONObject subject,int season,int episode,int quality) throws Exception {
        String cacheKey=subject.getString("subjectId")+":"+season+":"+episode+":"+quality;
        synchronized(resourceLocks[(cacheKey.hashCode()&Integer.MAX_VALUE)%resourceLocks.length]){
        JSONObject cached=sourceCache.get(cacheKey);Long at=sourceTimes.get(cacheKey);
        if(cached!=null&&at!=null&&android.os.SystemClock.elapsedRealtime()-at<45000)return cached;
        JSONObject batch=(JSONObject)request("/subject-api/resource",args("host",NATIVE,"subjectId",subject.getString("subjectId"),"page",1,"perPage",20,"all",0,"startPosition",1,"endPosition",1,"pagerMode",season>0?2:0,"resolution",quality,"se",season,"epFrom",episode,"epTo",episode),null,true);
        if(sourceCache.size()>64){sourceCache.clear();sourceTimes.clear();}
        sourceCache.put(cacheKey,batch);sourceTimes.put(cacheKey,android.os.SystemClock.elapsedRealtime());return batch;
        }
    }

    /** Metadata only: prepare the selected episode while the user reads Details. */
    public void prefetchPlayback(JSONObject subject,int season,int episode,int quality)throws Exception {
        if(subject.optInt("subjectType")==1){season=0;episode=0;}
        else if(subject.optInt("subjectType")!=2||season<1||episode<1)return;
        warm();resources(subject,season,episode,quality);
    }
    /** Resolve the first usable source without blocking startup on every quality variant. */
    public List<JSONObject> initialPlayback(JSONObject subject,int season,int episode) throws Exception {return initialPlayback(subject,season,episode,1080);}
    public List<JSONObject> initialPlayback(JSONObject subject,int season,int episode,int preferredQuality) throws Exception {
        warm();int type=subject.optInt("subjectType");if(type==1){season=0;episode=0;}else if(type==2&&(season<1||episode<1))throw new IOException("Choose a season and episode first.");
        JSONObject batch=resources(subject,season,episode,preferredQuality);Set<Integer> locked=new HashSet<>();
        JSONArray qualities=batch.optJSONArray("collectionResolutions");if(qualities!=null)for(int i=0;i<qualities.length();i++){JSONObject q=qualities.optJSONObject(i);if(q!=null&&q.optInt("requireMemberType")>0)locked.add(q.optInt("resolution"));}
        List<JSONObject> sources=new ArrayList<>();JSONArray list=batch.optJSONArray("list");
        if(list!=null)for(int i=0;i<list.length();i++){JSONObject row=list.optJSONObject(i);if(row==null)continue;int q=row.optInt("resolution",batch.optInt("resolution",preferredQuality));
            if(locked.contains(q)||!Policies.eligibleResource(type,season,episode,row.optInt("se"),row.optInt("ep"),row.optInt("requireMemberType"),row.optBoolean("vipLocked"),q,row.optLong("size"),row.optLong("duration"))||!row.optString("resourceLink").startsWith("https://"))continue;
            sources.add(row);resourceContext.put(subject.optString("subjectId")+":"+row.optString("resourceId"),row);
        }
        if(sources.isEmpty())return playback(subject,season,episode);
        Collections.sort(sources,(a,b)->Integer.compare(b.optInt("resolution"),a.optInt("resolution")));return sources;
    }
    public List<JSONObject> playback(JSONObject subject,int season,int episode) throws Exception {
        warm();int type=subject.optInt("subjectType");if(type==1){season=0;episode=0;}else if(type==2&&(season<1||episode<1))throw new IOException("Choose a season and episode first.");
        JSONObject first=resources(subject,season,episode,1080);Map<Integer,JSONObject> selected=new TreeMap<>(Collections.reverseOrder());Set<Integer> locked=new HashSet<>(),allowed=new HashSet<>();
        JSONArray qualities=first.optJSONArray("collectionResolutions");if(qualities!=null)for(int i=0;i<qualities.length();i++){JSONObject row=qualities.getJSONObject(i);int q=row.optInt("resolution");if(row.optInt("requireMemberType")>0)locked.add(q);else if(q>0&&q<=2160)allowed.add(q);}
        List<JSONObject> batches=new ArrayList<>();batches.add(first);
        // Additional quality lookup is bounded; a failure never discards the first usable source.
        for(int q:allowed)if(q!=1080)try{batches.add(resources(subject,season,episode,q));}catch(Exception ignored){}
        for(JSONObject batch:batches){JSONArray list=batch.optJSONArray("list");if(list==null)continue;for(int i=0;i<list.length();i++){
            JSONObject row=list.getJSONObject(i);int q=row.optInt("resolution",batch.optInt("resolution"));
            if(locked.contains(q)||!Policies.eligibleResource(type,season,episode,row.optInt("se"),row.optInt("ep"),row.optInt("requireMemberType"),row.optBoolean("vipLocked"),q,row.optLong("size"),row.optLong("duration")))continue;
            String link=row.optString("resourceLink");if(!link.startsWith("https://"))continue;
            JSONObject old=selected.get(q);if(old==null||row.optLong("size")>old.optLong("size"))selected.put(q,row);
            resourceContext.put(subject.optString("subjectId")+":"+row.optString("resourceId"),row);
        }}
        if(selected.isEmpty())throw new IOException("No playable unlocked source is available for this selection.");return new ArrayList<>(selected.values());
    }
    public List<JSONObject> captions(JSONObject subject,JSONObject stream) throws Exception {
        JSONArray ext=stream.optJSONArray("extCaptions");if(ext!=null&&ext.length()>0)return items(ext);
        Object response=request("/subject-api/get-ext-captions",args("host",NATIVE,"subjectId",subject.getString("subjectId"),"resourceId",stream.optString("resourceId"),"episode",stream.optInt("episode",stream.optInt("ep"))),null,true);
        if(response instanceof JSONArray)return items(response);
        JSONObject result=(JSONObject)response;for(String key:new String[]{"captions","extCaptions","list","items"})if(result.optJSONArray(key)!=null)return items(result.getJSONArray(key));
        return Collections.emptyList();
    }
    public static byte[] download(String location,int max) throws Exception {
        URL url=new URL(location);if(!"https".equals(url.getProtocol()))throw new IOException("Only HTTPS downloads are supported.");
        HttpsURLConnection connection=(HttpsURLConnection)url.openConnection();connection.setConnectTimeout(8000);connection.setReadTimeout(10000);connection.setRequestProperty("User-Agent",UA);
        try{if(connection.getResponseCode()!=200)throw new IOException("Download unavailable");return read(connection.getInputStream(),max);}finally{connection.disconnect();}
    }
    public static String artwork(String location,int width){
        try{android.net.Uri uri=android.net.Uri.parse(location);String host=uri.getHost();if(!"https".equals(uri.getScheme())||host==null||!(host.equals("aoneroom.com")||host.endsWith(".aoneroom.com"))||uri.getPath()==null||!uri.getPath().startsWith("/image/"))return location;
            android.net.Uri.Builder builder=uri.buildUpon().clearQuery();for(String key:uri.getQueryParameterNames())if(!key.equals("x-oss-process"))for(String value:uri.getQueryParameters(key))builder.appendQueryParameter(key,value);
            return builder.appendQueryParameter("x-oss-process","image/resize,w_"+Math.max(120,Math.min(1280,width))+"/format,webp").build().toString();
        }catch(Exception ignored){return location;}
    }
}
