package org.emransm.moviebox.phone;

import android.util.JsonReader;
import android.util.JsonToken;
import org.json.*;
import java.io.*;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

/** Portable public library data only. Parsing and preview never mutate the live library. */
public final class LibraryBackup {
    public static final int MAX_BYTES=2*1024*1024,MAX_RECORDS=3000,MAX_HISTORY=300;
    private static final long MAX_MEDIA_MS=7L*24*60*60*1000,MAX_UPDATED=4102444800000L;
    private static final String FORMAT="moviebox-library";
    private LibraryBackup(){}
    public static final class ExportResult {
        public final byte[] bytes;public final int excludedOfflineRecords;
        private ExportResult(byte[] bytes,int excluded){this.bytes=bytes;this.excludedOfflineRecords=excluded;}
    }
    public static final class MergeResult {
        public final JSONObject library;public final int addedTitles,importedHistory,evictedExistingHistory;
        private MergeResult(JSONObject library,int added,int imported,int evicted){this.library=library;addedTitles=added;importedHistory=imported;evictedExistingHistory=evicted;}
    }
    private static IOException invalid(String message){return new IOException("Invalid MovieBox backup: "+message);}
    public static byte[] export(JSONObject library)throws IOException{return exportDetails(library).bytes;}
    public static ExportResult exportDetails(JSONObject library)throws IOException{
        try{
            JSONObject out=new JSONObject().put("format",FORMAT).put("version",1);JSONArray list=new JSONArray(),history=new JSONArray();int excluded=0;
            JSONObject localList=object(library,"list"),localHistory=object(library,"history");
            for(String key:keys(localList)){JSONObject item=object(localList,key);if(isLocal(item)){excluded++;continue;}list.put(title(item,false));}
            for(String key:keys(localHistory)){JSONObject row=object(localHistory,key);if(isLocal(object(row,"subject"))){excluded++;continue;}history.put(history(row,false));}
            out.put("list",list).put("history",history);validate(out);
            byte[] bytes=out.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_BYTES)throw invalid("file exceeds 2 MiB");return new ExportResult(bytes,excluded);
        }catch(JSONException error){throw invalid("invalid local library structure");}
    }
    public static JSONObject parse(byte[] bytes)throws IOException{
        try{return validate(strictObject(bytes,MAX_BYTES));}catch(JSONException error){throw invalid("invalid backup structure");}
    }
    /** Strict local/portable JSON parsing; caller applies the appropriate schema afterward. */
    static JSONObject strictObject(byte[] bytes,int maximum)throws IOException{
        if(maximum<1||bytes==null||bytes.length==0||bytes.length>maximum)throw invalid("empty file or file exceeds the allowed size");
        try{
            String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            try(JsonReader reader=new JsonReader(new StringReader(text))){reader.setLenient(false);Object value=read(reader,0);if(!(value instanceof JSONObject)||reader.peek()!=JsonToken.END_DOCUMENT)throw invalid("expected one JSON object");return (JSONObject)value;}
        }catch(JSONException|IllegalStateException|NumberFormatException error){throw invalid("malformed JSON");}
    }
    public static MergeResult merge(JSONObject current,JSONObject parsed)throws IOException{
        try{
            JSONObject backup=validate(parsed),copy=new JSONObject(current.toString()),list=new JSONObject(object(current,"list").toString()),history=new JSONObject(object(current,"history").toString());
            Set<String> existing=new HashSet<>(keys(history)),changed=new HashSet<>();int added=0;
            JSONArray incoming=backup.getJSONArray("list");for(int i=0;i<incoming.length();i++){JSONObject title=incoming.getJSONObject(i);String id=title.getString("subjectId");if(!list.has(id)){list.put(id,new JSONObject(title.toString()));added++;}}
            if(list.length()>MAX_RECORDS)throw invalid("merged My list exceeds 3000 titles");
            JSONArray records=backup.getJSONArray("history");for(int i=0;i<records.length();i++){JSONObject row=records.getJSONObject(i);String key=historyKey(row);JSONObject old=history.optJSONObject(key);
                if(old==null||integer(row,"updated",0,MAX_UPDATED)>integer(old,"updated",0,MAX_UPDATED)){history.put(key,new JSONObject(row.toString()));changed.add(key);}}
            List<String> ordered=keys(history);for(String key:ordered)integer(object(history,key),"updated",0,MAX_UPDATED);
            final JSONObject sorted=history;Collections.sort(ordered,(a,b)->{int age=Long.compare(sorted.optJSONObject(b).optLong("updated"),sorted.optJSONObject(a).optLong("updated"));return age!=0?age:a.compareTo(b);});
            int evicted=0;for(int i=MAX_HISTORY;i<ordered.size();i++){String key=ordered.get(i);history.remove(key);if(existing.contains(key))evicted++;}
            int imported=0;for(String key:changed)if(history.has(key))imported++;copy.put("list",list).put("history",history);return new MergeResult(copy,added,imported,evicted);
        }catch(JSONException error){throw invalid("invalid merge structure");}
    }
    private static JSONObject validate(JSONObject root)throws IOException,JSONException{
        only(root,"format","version","list","history");if(!FORMAT.equals(string(root,"format",40))||integer(root,"version",1,Integer.MAX_VALUE)!=1)throw invalid("unsupported format or version");
        Object l=root.opt("list"),h=root.opt("history");if(!(l instanceof JSONArray)||!(h instanceof JSONArray))throw invalid("list and history must be arrays");JSONArray list=(JSONArray)l,history=(JSONArray)h;
        if((long)list.length()+history.length()>MAX_RECORDS)throw invalid("more than 3000 records");JSONObject clean=new JSONObject().put("format",FORMAT).put("version",1);JSONArray titles=new JSONArray(),records=new JSONArray();Set<String> ids=new HashSet<>();
        for(int i=0;i<list.length();i++){if(!(list.opt(i) instanceof JSONObject))throw invalid("invalid title record");JSONObject item=title(list.getJSONObject(i),true);if(!ids.add(item.getString("subjectId")))throw invalid("duplicate title");titles.put(item);}
        ids.clear();for(int i=0;i<history.length();i++){if(!(history.opt(i) instanceof JSONObject))throw invalid("invalid history record");JSONObject row=history(history.getJSONObject(i),true);if(!ids.add(historyKey(row)))throw invalid("duplicate history record");records.put(row);}
        return clean.put("list",titles).put("history",records);
    }
    private static JSONObject title(JSONObject source,boolean strict)throws IOException,JSONException{
        if(strict)only(source,"subjectId","subjectType","title","cover","releaseDate","imdbRatingValue","countryName","genre");
        String id=string(source,"subjectId",128);if(!id.matches("[A-Za-z0-9_-]{1,128}"))throw invalid("invalid title identity");String name=string(source,"title",1024);if(name.trim().isEmpty())throw invalid("empty title");
        JSONObject out=new JSONObject().put("subjectId",id).put("subjectType",integer(source,"subjectType",1,2)).put("title",name);
        for(String key:new String[]{"releaseDate","countryName","genre"})if(present(source,key,strict))out.put(key,string(source,key,key.equals("genre")?1024:256));
        if(present(source,"imdbRatingValue",strict)){Object rating=source.get("imdbRatingValue");if(!(rating instanceof String)&&!(rating instanceof Number))throw invalid("invalid rating");String text=rating.toString();if(text.length()>32)throw invalid("invalid rating");try{double number=Double.parseDouble(text);if(Double.isNaN(number)||Double.isInfinite(number)||number<0||number>10)throw invalid("invalid rating");}catch(NumberFormatException bad){if(!text.isEmpty())throw invalid("invalid rating");}out.put("imdbRatingValue",rating);}
        if(present(source,"cover",strict)){Object cover=source.get("cover");String location;JSONObject object=null;if(cover instanceof String)location=(String)cover;else if(cover instanceof JSONObject){object=(JSONObject)cover;if(strict)only(object,"url","width","height");location=string(object,"url",2048);}else throw invalid("invalid cover");
            String safe=publicArtwork(location,strict);if(safe!=null&&!safe.isEmpty()){if(object==null)out.put("cover",safe);else{JSONObject art=new JSONObject().put("url",safe);for(String size:new String[]{"width","height"})if(object.has(size))art.put(size,integer(object,size,0,16384));out.put("cover",art);}}}
        return out;
    }
    private static JSONObject history(JSONObject row,boolean strict)throws IOException,JSONException{
        if(strict)only(row,"subject","season","episode","position","duration","completed","updated");JSONObject subject=title(object(row,"subject"),strict);long type=subject.getLong("subjectType"),season=integer(row,"season",0,100000),episode=integer(row,"episode",0,100000);
        if(type==1&&(season!=0||episode!=0)||type==2&&(season<1||episode<1))throw invalid("episode does not match title type");long position=integer(row,"position",0,MAX_MEDIA_MS),duration;
        Object rawDuration=row.opt("duration");boolean unset=!strict&&rawDuration instanceof Number&&"-9223372036854775807".equals(rawDuration.toString());
        duration=unset?0:integer(row,"duration",0,MAX_MEDIA_MS);if(duration>0&&position>duration)throw invalid("position exceeds duration");Object completed=row.opt("completed");if(!(completed instanceof Boolean))throw invalid("completed must be boolean");
        return new JSONObject().put("subject",subject).put("season",season).put("episode",episode).put("position",position).put("duration",duration).put("completed",completed).put("updated",integer(row,"updated",0,MAX_UPDATED));
    }
    private static boolean isLocal(JSONObject subject){String id=subject.optString("subjectId");return id.startsWith("offline:")||id.startsWith("content:")||id.startsWith("file:");}
    private static boolean present(JSONObject source,String key,boolean strict){return source.has(key)&&(strict||!source.isNull(key));}
    private static String historyKey(JSONObject row)throws JSONException{return row.getJSONObject("subject").getString("subjectId")+":"+row.getLong("season")+":"+row.getLong("episode");}
    private static JSONObject object(JSONObject parent,String key)throws IOException{Object value=parent.opt(key);if(!(value instanceof JSONObject))throw invalid("invalid "+key);return (JSONObject)value;}
    private static String string(JSONObject parent,String key,int maximum)throws IOException{Object value=parent.opt(key);if(!(value instanceof String)||((String)value).length()>maximum)throw invalid("invalid "+key);String text=(String)value;for(int i=0;i<text.length();i++){char c=text.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=text.length()||!Character.isLowSurrogate(text.charAt(i)))throw invalid("invalid Unicode");}else if(Character.isLowSurrogate(c)||c==0)throw invalid("invalid Unicode");}return text;}
    private static long integer(JSONObject parent,String key,long minimum,long maximum)throws IOException{Object value=parent.opt(key);if(!(value instanceof Number))throw invalid("invalid "+key);try{long number=new BigDecimal(value.toString()).longValueExact();if(number<minimum||number>maximum)throw invalid("out of range "+key);return number;}catch(NumberFormatException|ArithmeticException bad){throw invalid("invalid "+key);}}
    private static void only(JSONObject object,String...allowed)throws IOException{Set<String> keys=new HashSet<>(Arrays.asList(allowed));for(String key:keys(object))if(!keys.contains(key))throw invalid("unexpected field "+key);}
    private static List<String> keys(JSONObject object){List<String> out=new ArrayList<>();Iterator<String> keys=object.keys();while(keys.hasNext())out.add(keys.next());return out;}
    private static String publicArtwork(String value,boolean strict)throws IOException{
        if(value.isEmpty())return "";try{URI uri=new URI(value);String host=uri.getHost();boolean safe=value.length()<=2048&&"https".equalsIgnoreCase(uri.getScheme())&&host!=null&&uri.getUserInfo()==null;
            if(host!=null){String h=host.toLowerCase(Locale.ROOT);safe&=!h.equals("localhost")&&!h.endsWith(".local")&&!h.contains(":")&&!h.startsWith("127.")&&!h.startsWith("10.")&&!h.startsWith("192.168.")&&!h.startsWith("169.254.")&&!h.equals("0.0.0.0");if(h.matches("172\\.(1[6-9]|2[0-9]|3[01])\\..*"))safe=false;}
            if(!safe){if(strict)throw invalid("non-public artwork address");return null;}
            if(strict&&(uri.getRawQuery()!=null||uri.getRawFragment()!=null))throw invalid("artwork address includes private parameters");String ascii=uri.toASCIIString();int end=ascii.length(),query=ascii.indexOf('?'),fragment=ascii.indexOf('#');if(query>=0)end=Math.min(end,query);if(fragment>=0)end=Math.min(end,fragment);return ascii.substring(0,end);
        }catch(java.net.URISyntaxException bad){if(strict)throw invalid("invalid artwork address");return null;}
    }
    private static Object read(JsonReader reader,int depth)throws IOException,JSONException{
        if(depth>12)throw invalid("JSON nesting too deep");switch(reader.peek()){
            case BEGIN_OBJECT:{JSONObject object=new JSONObject();Set<String> names=new HashSet<>();reader.beginObject();while(reader.hasNext()){String key=reader.nextName();if(!names.add(key))throw invalid("duplicate JSON field");object.put(key,read(reader,depth+1));}reader.endObject();return object;}
            case BEGIN_ARRAY:{JSONArray array=new JSONArray();reader.beginArray();while(reader.hasNext()){if(array.length()>MAX_RECORDS)throw invalid("array too long");array.put(read(reader,depth+1));}reader.endArray();return array;}
            case STRING:return reader.nextString();case BOOLEAN:return reader.nextBoolean();case NULL:reader.nextNull();return JSONObject.NULL;
            case NUMBER:{String number=reader.nextString();if(number.length()>32)throw invalid("number too long");return new BigDecimal(number);}
            default:throw invalid("unexpected JSON token");
        }
    }
}
