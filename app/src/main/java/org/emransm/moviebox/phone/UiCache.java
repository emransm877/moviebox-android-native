package org.emransm.moviebox.phone;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Optional app-private UI cache. Never stores sessions, streams or captions. Call off main thread. */
public final class UiCache {
    static final int MAX_SNAPSHOT_BYTES=512*1024,MAX_ARTWORK_BYTES=2*1024*1024;
    static final long MAX_AGE_MS=7L*24*60*60*1000;
    private static final Object LOCK=new Object();
    private final File catalogue,artwork,detailDirectory;private final long artBudget;
    static final long DETAIL_AGE_MS=5L*60*1000;
    public UiCache(Context context,boolean low){this(context,low,(String)null);}
    public UiCache(Context context,boolean low,boolean family){this(context,low,family?"family-v2":"all-v2");}
    private UiCache(Context context,boolean low,String mode){File root=new File(context.getCacheDir(),"moviebox-ui-v1");catalogue=new File(root,mode==null?"catalogue":"catalogue-"+mode);artwork=new File(root,"artwork");detailDirectory=new File(root,mode==null?"details":"details-"+mode);artBudget=(low?8L:24L)*1024*1024;}
    // Only public display metadata survives process death. Never persist guest tokens,
    // resource links, membership decisions or subtitle download addresses here.
    private static JSONObject detailMetadata(JSONObject source)throws Exception {
        JSONObject out=metadata(source);if(out==null)return null;
        if(source.opt("description") instanceof String)out.put("description",source.getString("description"));
        JSONArray dubs=source.optJSONArray("dubs");if(dubs!=null){JSONArray safe=new JSONArray();for(int n=0;n<Math.min(100,dubs.length());n++){
            JSONObject row=dubs.optJSONObject(n);if(row==null||row.optString("subjectId").isEmpty())continue;
            JSONObject dub=new JSONObject();for(String key:new String[]{"subjectId","type","lanName","lanCode"}){Object value=row.opt(key);if(value instanceof String||value instanceof Number)dub.put(key,value);}safe.put(dub);
        }out.put("dubs",safe);}
        if(out.optInt("subjectType")==2){
            JSONArray rows=source.optJSONArray("seasons");if(rows==null||rows.length()>100)return null;
            JSONArray safe=new JSONArray();for(int n=0;n<rows.length();n++){
                JSONObject row=rows.optJSONObject(n);if(row==null||row.optInt("se")<=0)continue;
                safe.put(new JSONObject().put("se",row.optInt("se")).put("maxEp",row.optInt("maxEp")).put("allEp",row.optString("allEp")));
            }out.put("seasons",safe);
        }return out;
    }
    static final long DETAIL_DISPLAY_AGE_MS=24L*60*60*1000;
    static final class DetailRecord {final JSONObject item;final long age;DetailRecord(JSONObject item,long age){this.item=item;this.age=age;}}
    private DetailRecord readDetailRecord(File file,long maxAge){try{
        JSONObject entry=new JSONObject(Utf8Json.decode(read(file,MAX_SNAPSHOT_BYTES)));
        long age=System.currentTimeMillis()-entry.getLong("savedAt");if(entry.optInt("schema")!=1||age<0||age>=maxAge)return null;
        JSONObject item=detailMetadata(entry.getJSONObject("item"));if(item==null||!file.getName().equals(digest(item.getString("subjectId"))+".json"))return null;
        return new DetailRecord(item,age);
    }catch(Exception miss){return null;}}
    DetailRecord readDetailRecord(String id){synchronized(LOCK){try{return readDetailRecord(new File(detailDirectory,digest(id)+".json"),DETAIL_AGE_MS);}catch(Exception miss){return null;}}}
    public JSONObject readDetail(String id){DetailRecord record=readDetailRecord(id);return record==null?null:record.item;}
    /** Restore only bounded public display metadata in a worker, never UI-thread disk I/O. */
    List<DetailRecord> recentDetails(){synchronized(LOCK){
        List<DetailRecord> records=new ArrayList<>();File[] files=detailDirectory.listFiles((dir,name)->name.endsWith(".json"));if(files==null)return records;
        Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        for(int n=0;n<Math.min(48,files.length);n++){DetailRecord record=readDetailRecord(files[n],DETAIL_DISPLAY_AGE_MS);if(record!=null)records.add(record);}return records;
    }}
    public void writeDetail(String id,JSONObject value){synchronized(LOCK){try{
        JSONObject safe=detailMetadata(value);if(safe==null||!id.equals(safe.optString("subjectId")))return;
        byte[] bytes=new JSONObject().put("schema",1).put("savedAt",System.currentTimeMillis()).put("item",safe).toString().getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_SNAPSHOT_BYTES)return;atomicWrite(new File(detailDirectory,digest(id)+".json"),bytes);trim(detailDirectory,48,8L*1024*1024);
    }catch(Exception optional){/* A cache write can never prevent playback. */}}}
    private static String digest(String key)throws Exception {byte[] value=MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:value)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    private static byte[] read(File file,int limit)throws IOException {try(InputStream input=new AtomicFile(file).openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[16384];int n;while((n=input.read(buffer))!=-1){if(out.size()+n>limit)throw new IOException("UI cache entry exceeds budget");out.write(buffer,0,n);}return out.toByteArray();}}
    private static void atomicWrite(File file,byte[] bytes)throws IOException {File parent=file.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("UI cache unavailable");AtomicFile atomic=new AtomicFile(file);FileOutputStream stream=null;try{stream=atomic.startWrite();stream.write(bytes);atomic.finishWrite(stream);}catch(IOException error){if(stream!=null)atomic.failWrite(stream);throw error;}}
    private static void trim(File directory,int count,long budget){File[] entries=directory.listFiles((dir,name)->name.endsWith(".json")||name.endsWith(".bin"));if(entries==null)return;Arrays.sort(entries,(a,b)->Long.compare(a.lastModified(),b.lastModified()));long bytes=0;for(File file:entries)bytes+=file.length();int remaining=entries.length;for(File file:entries){if(remaining<=count&&bytes<=budget)break;long length=file.length();if(file.delete()){remaining--;bytes-=length;new File(file.getPath()+".bak").delete();}}}
    private static JSONObject artMetadata(Object source)throws Exception {if(source instanceof String){String url=(String)source;return url.startsWith("https://")?new JSONObject().put("url",url):null;}if(!(source instanceof JSONObject))return null;JSONObject input=(JSONObject)source;String url=input.optString("url");if(!url.startsWith("https://"))return null;JSONObject out=new JSONObject().put("url",url);for(String name:new String[]{"width","height","subjectId"}){Object value=input.opt(name);if(value instanceof String||value instanceof Number)out.put(name,value);}return out;}
    private static JSONObject metadata(JSONObject item)throws Exception {String id=item.optString("subjectId"),title=item.optString("title");int type=item.optInt("subjectType");if(id.isEmpty()||id.equals("0")||title.isEmpty()||(type!=1&&type!=2))return null;JSONObject out=new JSONObject().put("subjectId",id).put("title",title).put("subjectType",type);if(AdultContentPolicy.markedAdult(item))out.put("isAdult",true);for(String key:new String[]{"releaseDate","imdbRatingValue","countryName","genre"}){Object value=item.opt(key);if(value instanceof String||value instanceof Number||value instanceof Boolean)out.put(key,value);}for(String key:new String[]{"cover","banner"}){JSONObject art=artMetadata(item.opt(key));if(art!=null)out.put(key,art);}return out;}
    public List<JSONObject> readCatalogue(String key){synchronized(LOCK){try{File file=new File(catalogue,digest(key)+".json");JSONObject envelope=new JSONObject(Utf8Json.decode(read(file,MAX_SNAPSHOT_BYTES)));long at=envelope.optLong("savedAt"),now=System.currentTimeMillis();if(envelope.optInt("schema")!=1||at<=0||at>now||now-at>MAX_AGE_MS)return null;JSONArray items=envelope.getJSONArray("items");if(items.length()>200)return null;List<JSONObject> result=new ArrayList<>();for(int n=0;n<items.length();n++){JSONObject item=metadata(items.getJSONObject(n));if(item!=null)result.add(item);}if(result.isEmpty())return null;file.setLastModified(now);return result;}catch(Exception miss){return null;}}}
    public void writeCatalogue(String key,List<JSONObject> items){synchronized(LOCK){try{JSONArray values=new JSONArray();for(JSONObject input:items){if(values.length()>=200)break;JSONObject item=metadata(input);if(item!=null)values.put(item);}if(values.length()==0)return;byte[] bytes=new JSONObject().put("schema",1).put("savedAt",System.currentTimeMillis()).put("items",values).toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_SNAPSHOT_BYTES)return;atomicWrite(new File(catalogue,digest(key)+".json"),bytes);trim(catalogue,16,16L*MAX_SNAPSHOT_BYTES);}catch(Exception optional){/* Cache cannot prevent browsing. */}}}
    public void removeArtwork(String resizedUrl){synchronized(LOCK){try{if(!resizedUrl.startsWith("https://"))return;File file=new File(artwork,digest(resizedUrl)+".bin");file.delete();new File(file.getPath()+".bak").delete();}catch(Exception optional){/* Failed cache eviction cannot block a fresh fetch. */}}}
    public byte[] readArtwork(String resizedUrl){synchronized(LOCK){try{if(!resizedUrl.startsWith("https://"))return null;File file=new File(artwork,digest(resizedUrl)+".bin");byte[] bytes=read(file,MAX_ARTWORK_BYTES);long now=System.currentTimeMillis();if(file.lastModified()>now||now-file.lastModified()>MAX_AGE_MS)return null;if(bytes.length==0)return null;file.setLastModified(now);return bytes;}catch(Exception miss){return null;}}}
    public void writeArtwork(String resizedUrl,byte[] bytes){synchronized(LOCK){try{if(!resizedUrl.startsWith("https://")||bytes==null||bytes.length==0||bytes.length>MAX_ARTWORK_BYTES)return;atomicWrite(new File(artwork,digest(resizedUrl)+".bin"),bytes);trim(artwork,Integer.MAX_VALUE,artBudget);}catch(Exception optional){/* Cache cannot prevent artwork rendering. */}}}
}
