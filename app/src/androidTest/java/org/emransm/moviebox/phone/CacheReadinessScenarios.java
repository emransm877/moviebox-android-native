package org.emransm.moviebox.phone;

import android.content.Context;
import android.content.ContextWrapper;
import android.util.AtomicFile;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

/** Optional cache corruption/budget/privacy tests, isolated from real app cache/library. */
@RunWith(AndroidJUnit4.class)
public class CacheReadinessScenarios {
    private File owned;private Context context;
    @Before public void prepare()throws Exception {Context real=InstrumentationRegistry.getInstrumentation().getTargetContext();owned=new File(real.getCacheDir(),"qa-ui-cache-"+System.nanoTime());assertTrue(owned.mkdirs());context=new ContextWrapper(real){@Override public File getCacheDir(){return owned;}};}
    @After public void cleanup()throws Exception {if(owned!=null){String parent=owned.getCanonicalPath()+File.separator;removeOwned(owned,parent);}}
    private static void removeOwned(File entry,String prefix)throws Exception {if(!entry.getCanonicalPath().equals(prefix.substring(0,prefix.length()-1))&&!entry.getCanonicalPath().startsWith(prefix))throw new IOException("Fixture escaped owned cache");File[] children=entry.listFiles();if(children!=null)for(File child:children)removeOwned(child,prefix);assertTrue("Delete owned cache fixture "+entry,entry.delete());}
    private JSONObject title()throws Exception {return new JSONObject().put("subjectId","qa-owned-ui-cache").put("subjectType",1).put("title","Cache fixture").put("releaseDate","2026-01-01").put("genre","Drama").put("resourceLink","https://invalid.test/private-video").put("accessToken","must-not-persist").put("captions",new JSONArray().put("https://invalid.test/caption")).put("cover",new JSONObject().put("url","https://invalid.test/poster.webp").put("width",400).put("height",600).put("accessToken","nested-token")).put("banner",new JSONObject().put("url","https://invalid.test/banner.webp").put("width",1920).put("height",1080).put("subjectId","qa-owned-ui-cache").put("resourceLink","nested-stream"));}
    private File catalogueFile(){File[] entries=new File(owned,"moviebox-ui-v1/catalogue").listFiles((d,n)->n.endsWith(".json"));assertNotNull(entries);assertEquals(1,entries.length);return entries[0];}
    private static void overwrite(File file,byte[] bytes)throws Exception {try(OutputStream out=new FileOutputStream(file)){out.write(bytes);}}
    @Test public void catalogueSurvivesCacheInstanceAndContainsOnlyPublicMetadata()throws Exception {
        UiCache cache=new UiCache(context,false);cache.writeCatalogue("Discover",Collections.singletonList(title()));List<JSONObject> restored=new UiCache(context,false).readCatalogue("Discover");assertNotNull(restored);assertEquals(1,restored.size());assertEquals("Cache fixture",restored.get(0).getString("title"));assertEquals(1920,restored.get(0).getJSONObject("banner").getInt("width"));String disk=new String(UiCacheRead(catalogueFile()),StandardCharsets.UTF_8);assertFalse(disk.contains("must-not-persist"));assertFalse(disk.contains("accessToken"));assertFalse(disk.contains("resourceLink"));assertFalse(disk.contains("private-video"));assertFalse(disk.contains("captions"));assertFalse(disk.contains("nested-stream"));
    }
    private static byte[] UiCacheRead(File file)throws Exception {try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
    @Test public void corruptExpiredFutureAndInterruptedWritesAreSafeCacheMissesOrRecover()throws Exception {
        UiCache cache=new UiCache(context,false);cache.writeCatalogue("Discover",Collections.singletonList(title()));File file=catalogueFile();byte[] original=UiCacheRead(file);JSONObject envelope=new JSONObject(new String(original,StandardCharsets.UTF_8));
        overwrite(file,envelope.put("savedAt",System.currentTimeMillis()-UiCache.MAX_AGE_MS-10000).toString().getBytes(StandardCharsets.UTF_8));assertNull(cache.readCatalogue("Discover"));
        overwrite(file,envelope.put("savedAt",System.currentTimeMillis()+3600000).toString().getBytes(StandardCharsets.UTF_8));assertNull(cache.readCatalogue("Discover"));
        overwrite(file,"not-json".getBytes(StandardCharsets.UTF_8));assertNull(cache.readCatalogue("Discover"));
        overwrite(file,original);AtomicFile atomic=new AtomicFile(file);try(FileOutputStream interrupted=atomic.startWrite()){interrupted.write("partial".getBytes(StandardCharsets.UTF_8));}assertNotNull("AtomicFile recovers prior complete snapshot after interrupted write",cache.readCatalogue("Discover"));
    }
    @Test public void catalogueEntryCountAndBytesStayBoundedWithUntrustedKeys()throws Exception {
        UiCache cache=new UiCache(context,false);for(int n=0;n<20;n++)cache.writeCatalogue("../../route-"+n,Collections.singletonList(title()));File dir=new File(owned,"moviebox-ui-v1/catalogue");File[] entries=dir.listFiles((d,n)->n.endsWith(".json"));assertNotNull(entries);assertEquals(16,entries.length);for(File f:entries){assertTrue(f.length()<=UiCache.MAX_SNAPSHOT_BYTES);assertTrue(f.getName().matches("[0-9a-f]{64}\\.json"));}
        char[] huge=new char[UiCache.MAX_SNAPSHOT_BYTES+10000];Arrays.fill(huge,'x');cache.writeCatalogue("oversized",Collections.singletonList(title().put("title",new String(huge))));assertNull(cache.readCatalogue("oversized"));assertEquals(16,dir.listFiles((d,n)->n.endsWith(".json")).length);
    }
    @Test public void artworkRoundTripsAndLowMemoryDiskBudgetEvictsOldEntries()throws Exception {
        UiCache cache=new UiCache(context,true);byte[] payload=new byte[UiCache.MAX_ARTWORK_BYTES];Arrays.fill(payload,(byte)37);for(int n=0;n<6;n++)cache.writeArtwork("https://invalid.test/art-"+n,payload);File[] files=new File(owned,"moviebox-ui-v1/artwork").listFiles((d,n)->n.endsWith(".bin"));assertNotNull(files);long bytes=0;for(File f:files)bytes+=f.length();assertTrue(bytes<=8L*1024*1024);assertEquals(4,files.length);int hits=0;for(int n=0;n<6;n++){byte[] restored=cache.readArtwork("https://invalid.test/art-"+n);if(restored!=null){hits++;assertArrayEquals(payload,restored);}}assertEquals(4,hits);cache.writeArtwork("https://invalid.test/too-large",new byte[UiCache.MAX_ARTWORK_BYTES+1]);assertNull(cache.readArtwork("https://invalid.test/too-large"));cache.writeArtwork("http://invalid.test/insecure",new byte[]{1});assertNull(cache.readArtwork("http://invalid.test/insecure"));
    }
}
