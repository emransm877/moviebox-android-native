package org.emransm.moviebox.phone;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Atomic app-private library, separate from APK files and service sessions. */
public final class Library {
    private static final Object WRITE_LOCK=new Object();
    private static final Map<String,byte[]> LATEST=new HashMap<>();
    public interface SaveFailureListener { void onSaveFailure(IOException error); }
    private volatile SaveFailureListener saveFailureListener;
    public void setSaveFailureListener(SaveFailureListener listener){saveFailureListener=listener;}
    private final String path;
    public volatile JSONObject data;
    private final AtomicFile file;
    private final ExecutorService writer=Executors.newSingleThreadExecutor();
    public Library(Context context) throws JSONException {
        File backing=new File(context.getFilesDir(),"library.json");path=backing.getAbsolutePath();file=new AtomicFile(backing);JSONObject loaded;
        synchronized(WRITE_LOCK){
            byte[] snapshot=LATEST.get(path);
            if(snapshot!=null){try{loaded=checked(snapshot);}catch(Exception invalid){throw new ReadFailure();}}
            else {
                File backup=new File(path+".bak");loaded=null;
                // Inspect without AtomicFile.openRead(): an unreadable .bak must not overwrite the original.
                for(File candidate:new File[]{backup,backing})if(candidate.isFile())try{loaded=checked(readLocal(candidate));break;}catch(Exception ignored){}
                if(loaded==null){if(backing.exists()||backup.exists()||new File(path+".new").exists())throw new ReadFailure();loaded=new JSONObject();}
            }
        }
        data=loaded;
        for(String key:new String[]{"list","history","settings"})if(data.optJSONObject(key)==null)data.put(key,new JSONObject());
        if(data.optString("identity").isEmpty())data.put("identity",UUID.randomUUID().toString().replace("-",""));
    }
    public JSONObject snapshot()throws JSONException{synchronized(WRITE_LOCK){return new JSONObject(data.toString());}}
    public JSONObject settings(){return data.optJSONObject("settings");}
    public void save(){final byte[] bytes;synchronized(WRITE_LOCK){bytes=data.toString().getBytes(StandardCharsets.UTF_8);LATEST.put(path,bytes);}writer.execute(()->{synchronized(WRITE_LOCK){if(LATEST.get(path)!=bytes)return;try{writeAtomically(file,bytes);}catch(IOException error){SaveFailureListener listener=saveFailureListener;if(listener!=null)listener.onSaveFailure(error);}}});}
    public static final class ReadFailure extends JSONException {ReadFailure(){super("Saved library could not be read. Your original data has been kept.");}}
    private static byte[] readLocal(File source)throws IOException{try(InputStream input=new FileInputStream(source);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1){if(out.size()+count>16*1024*1024)throw new IOException("Saved data is too large to read safely.");out.write(buffer,0,count);}return out.toByteArray();}}
    private static JSONObject checked(byte[] bytes)throws Exception{
        JSONObject value=LibraryBackup.strictObject(bytes,16*1024*1024);
        if(value.has("identity")&&!(value.get("identity") instanceof String))throw new JSONException("Invalid saved identity");
        for(String key:new String[]{"list","history","settings"})if(value.has(key)&&!(value.get(key) instanceof JSONObject))throw new JSONException("Invalid saved section");
        for(String section:new String[]{"list","history"}){JSONObject records=value.optJSONObject(section);if(records==null)continue;Iterator<String> ids=records.keys();while(ids.hasNext()){String id=ids.next();JSONObject record=records.optJSONObject(id);if(record==null)throw new JSONException("Invalid saved entry");JSONObject title=section.equals("history")?record.optJSONObject("subject"):record;if(title==null||title.optString("subjectId").isEmpty())throw new JSONException("Invalid saved title");}}
        return value;
    }
    private static void writeAtomically(AtomicFile target,byte[] bytes)throws IOException{
        if(bytes.length>16*1024*1024)throw new IOException("Saved data is too large to commit safely.");
        FileOutputStream stream=null;boolean finished=false;try{stream=target.startWrite();stream.write(bytes);stream.getFD().sync();target.finishWrite(stream);finished=true;if(!target.getBaseFile().isFile()||!Arrays.equals(bytes,readLocal(target.getBaseFile())))throw new IOException("Saved data could not be committed. Your in-memory library has been kept.");}catch(IOException failure){if(stream!=null&&!finished)target.failWrite(stream);throw failure;}
    }
    /** Called off the UI thread only after the user accepts a preview. A stale preview cannot replace new changes. */
    public void commitRestored(JSONObject merged,String expected)throws Exception{
        JSONObject candidate=new JSONObject(merged.toString());byte[] bytes=candidate.toString().getBytes(StandardCharsets.UTF_8);
        synchronized(WRITE_LOCK){if(!data.toString().equals(expected))throw new IOException("Your library changed. Choose the backup again to review the updated merge.");writeAtomically(file,bytes);data=candidate;LATEST.put(path,bytes);}
    }
    private static File archiveDamaged(Context context)throws IOException{
        File archive=new File(new File(context.getFilesDir(),"library-recovery"),UUID.randomUUID().toString());if(!archive.mkdirs())throw new IOException("Could not preserve the original data. Free some storage and try again.");
        for(String name:new String[]{"library.json","library.json.bak","library.json.new"}){File original=new File(context.getFilesDir(),name);if(!original.exists())continue;if(!original.isFile())throw new IOException("Saved data path is not a file. Original data was not replaced.");try(InputStream input=new FileInputStream(original);FileOutputStream output=new FileOutputStream(new File(archive,name))){byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1)output.write(buffer,0,count);output.getFD().sync();}}
        return archive;
    }
    /** Caller must obtain explicit reset/restore confirmation. Never deletes the quarantined original files. */
    public static File recoverAfterConfirmation(Context context,JSONObject portable)throws Exception{
        synchronized(WRITE_LOCK){File backing=new File(context.getFilesDir(),"library.json");String path=backing.getAbsolutePath();JSONObject fresh=new JSONObject().put("list",new JSONObject()).put("history",new JSONObject()).put("settings",new JSONObject()).put("identity",UUID.randomUUID().toString().replace("-",""));
            if(portable!=null)fresh=LibraryBackup.merge(fresh,portable).library;
            File archive=archiveDamaged(context);byte[] bytes=fresh.toString().getBytes(StandardCharsets.UTF_8);writeAtomically(new AtomicFile(backing),bytes);LATEST.put(path,bytes);return archive;}
    }
    public static byte[] damagedArchive(Context context)throws IOException{
        synchronized(WRITE_LOCK){ByteArrayOutputStream bytes=new ByteArrayOutputStream();int total=0;try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(bytes)){for(String name:new String[]{"library.json","library.json.bak","library.json.new"}){File source=new File(context.getFilesDir(),name);if(!source.isFile())continue;byte[] raw=readLocal(source);if((long)total+raw.length>16*1024*1024)throw new IOException("Damaged data is too large to export here. Original files remain on this phone.");total+=raw.length;zip.putNextEntry(new java.util.zip.ZipEntry(name));zip.write(raw);zip.closeEntry();}}return bytes.toByteArray();}
    }
    public void close(){writer.shutdown();}
    static JSONObject summary(JSONObject subject) throws JSONException {JSONObject out=new JSONObject();for(String key:new String[]{"subjectId","subjectType","title","cover","releaseDate","imdbRatingValue","countryName","genre"})if(subject.has(key))out.put(key,subject.get(key));return out;}
    public boolean contains(JSONObject subject){return data.optJSONObject("list").has(subject.optString("subjectId"));}
    public boolean toggle(JSONObject subject) throws JSONException {synchronized(WRITE_LOCK){JSONObject list=data.getJSONObject("list");String id=subject.getString("subjectId");if(list.has(id))list.remove(id);else list.put(id,summary(subject));save();return list.has(id);}}
    public List<JSONObject> listing(String name) throws JSONException {
        JSONObject values=data.getJSONObject(name);List<JSONObject> out=new ArrayList<>();Set<String> seen=new HashSet<>();
        List<JSONObject> records=new ArrayList<>();Iterator<String> keys=values.keys();while(keys.hasNext()){JSONObject entry=values.optJSONObject(keys.next());if(entry!=null)records.add(entry);}
        Collections.sort(records,(a,b)->Long.compare(b.optLong("updated"),a.optLong("updated")));
        for(JSONObject entry:records){JSONObject subject=name.equals("history")?entry.optJSONObject("subject"):entry;if(subject!=null&&seen.add(subject.optString("subjectId")))out.add(subject);}return out;
    }
    /** Presentation-only snapshot. Full history, including completed episodes, remains untouched. */
    public static final class ResumeEntry {
        public final JSONObject subject;public final int season,episode;public final long position,duration,updated;
        private ResumeEntry(JSONObject row)throws JSONException{subject=new JSONObject(row.getJSONObject("subject").toString());season=row.optInt("season");episode=row.optInt("episode");position=row.optLong("position");duration=row.optLong("duration");updated=row.optLong("updated");}
    }
    public List<ResumeEntry> resumeEntries(int limit)throws JSONException{
        int maximum=Math.max(0,Math.min(300,limit));List<ResumeEntry> out=new ArrayList<>();if(maximum==0)return out;
        List<JSONObject> records=new ArrayList<>();synchronized(WRITE_LOCK){JSONObject history=data.getJSONObject("history");Iterator<String> keys=history.keys();while(keys.hasNext()){JSONObject row=history.optJSONObject(keys.next());if(row!=null)records.add(new JSONObject(row.toString()));}}
        Collections.sort(records,(a,b)->{int order=Long.compare(b.optLong("updated"),a.optLong("updated"));if(order==0)order=Integer.compare(b.optInt("season"),a.optInt("season"));if(order==0)order=Integer.compare(b.optInt("episode"),a.optInt("episode"));if(order==0){JSONObject left=a.optJSONObject("subject"),right=b.optJSONObject("subject");order=(left==null?"":left.optString("subjectId")).compareTo(right==null?"":right.optString("subjectId"));}return order;});
        Set<String> seen=new HashSet<>();for(JSONObject row:records){JSONObject title=row.optJSONObject("subject");if(title==null)continue;String id=title.optString("subjectId");int type=title.optInt("subjectType");if(id.trim().isEmpty()||!seen.add(id))continue;
            // A newer completed/empty record must not revive an older abandoned episode.
            long position=row.optLong("position"),duration=row.optLong("duration");int season=row.optInt("season"),episode=row.optInt("episode");
            if(row.optBoolean("completed")||position<1000||duration>0&&position>=duration||type!=1&&type!=2||type==2&&(season<=0||episode<=0)||type==1&&(season!=0||episode!=0))continue;
            out.add(new ResumeEntry(row));if(out.size()==maximum)break;
        }return out;
    }
    public static String key(JSONObject subject,int season,int episode){return subject.optString("subjectId")+":"+season+":"+episode;}
    public long position(JSONObject subject,int season,int episode){JSONObject value=data.optJSONObject("history").optJSONObject(key(subject,season,episode));return value==null||value.optBoolean("completed")?0:value.optLong("position");}
    public void progress(JSONObject subject,int season,int episode,long position,long duration,boolean completed) throws JSONException {synchronized(WRITE_LOCK){
        if(position<1000&&!completed)return;
        JSONObject history=data.getJSONObject("history");history.put(key(subject,season,episode),new JSONObject().put("subject",summary(subject)).put("season",season).put("episode",episode).put("position",position).put("duration",duration).put("completed",completed).put("updated",System.currentTimeMillis()));
        if(history.length()>300){String oldest=null;long at=Long.MAX_VALUE;Iterator<String> keys=history.keys();while(keys.hasNext()){String key=keys.next();long value=history.getJSONObject(key).optLong("updated");if(value<at){at=value;oldest=key;}}if(oldest!=null)history.remove(oldest);}
        save();
    }}
    public void removeTitles(String name,Set<String> identities) throws JSONException {synchronized(WRITE_LOCK){JSONObject section=data.getJSONObject(name);List<String> remove=new ArrayList<>();Iterator<String> keys=section.keys();while(keys.hasNext()){String key=keys.next();JSONObject record=section.getJSONObject(key);JSONObject subject=name.equals("history")?record.optJSONObject("subject"):record;if(subject!=null&&identities.contains(subject.optString("subjectId")))remove.add(key);}for(String key:remove)section.remove(key);save();}}
}
