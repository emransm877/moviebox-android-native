package org.emransm.moviebox.phone;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

/** Strict portable data contracts; coordinator separately tests SAF and atomic disk commit. */
@RunWith(AndroidJUnit4.class)
public class LibraryBackupScenarios {
    private static JSONObject title(String id)throws Exception{return new JSONObject().put("subjectId",id).put("subjectType",1).put("title","বাংলা 日本語 · café "+id).put("cover",new JSONObject().put("url","https://owned.invalid/poster%2Fsafe.png?token=secret#private").put("width",400).put("headers","private"));}
    private static JSONObject data()throws Exception{return new JSONObject().put("identity","private-identity").put("settings",new JSONObject().put("token","private-token")).put("list",new JSONObject()).put("history",new JSONObject());}
    private static JSONObject row(String id,long updated)throws Exception{return new JSONObject().put("subject",title(id)).put("season",0).put("episode",0).put("position",3000).put("duration",10000).put("completed",false).put("updated",updated);}
    private static JSONObject portable(JSONObject source)throws Exception{return LibraryBackup.parse(LibraryBackup.export(source));}
    interface Bad {void run()throws Exception;}
    private static void rejected(Bad action)throws Exception{try{action.run();fail("Invalid backup must be rejected");}catch(IOException expected){}}
    @Test public void exportRoundTripsUnicodeAndWhitelistsNestedPublicData()throws Exception{
        JSONObject source=data(),subject=title("safe").put("resourceLink","https://private.invalid/video").put("identity","secret");source.getJSONObject("list").put("safe",subject);source.getJSONObject("history").put("safe:0:0",row("safe",10).put("localUri","content://secret"));String before=source.toString();
        byte[] bytes=LibraryBackup.export(source);String text=new String(bytes,StandardCharsets.UTF_8);assertFalse(text.contains("secret"));assertFalse(text.contains("identity"));assertFalse(text.contains("settings"));assertFalse(text.contains("resourceLink"));assertFalse(text.contains("headers"));assertFalse(text.contains("content:"));JSONObject parsed=LibraryBackup.parse(bytes);assertEquals(subject.getString("title"),parsed.getJSONArray("list").getJSONObject(0).getString("title"));assertEquals("https://owned.invalid/poster%2Fsafe.png",parsed.getJSONArray("list").getJSONObject(0).getJSONObject("cover").getString("url"));assertEquals(before,source.toString());
    }
    @Test public void offlineEntriesAreExcludedFromPortableDataButPreservedByLocalMerge()throws Exception{
        JSONObject current=data();JSONObject offline=title("offline:content://private");current.getJSONObject("list").put(offline.getString("subjectId"),offline);current.getJSONObject("history").put("offline:content://private:0:0",row("offline:content://private",30));
        LibraryBackup.ExportResult exported=LibraryBackup.exportDetails(current);assertEquals(2,exported.excludedOfflineRecords);assertFalse(new String(exported.bytes,StandardCharsets.UTF_8).contains("content:"));LibraryBackup.MergeResult result=LibraryBackup.merge(current,LibraryBackup.parse(exported.bytes));assertEquals(1,result.library.getJSONObject("list").length());assertEquals(1,result.library.getJSONObject("history").length());assertEquals("private-identity",result.library.getString("identity"));assertEquals("private-token",result.library.getJSONObject("settings").getString("token"));
    }
    @Test public void mergeIsDefensiveExistingListWinsAndNewestHistoryWinsWithExistingTies()throws Exception{
        JSONObject current=data(),incoming=data();current.getJSONObject("list").put("same",title("same").put("title","Existing title"));current.getJSONObject("history").put("same:0:0",row("same",20));incoming.getJSONObject("list").put("same",title("same")).put("new",title("new"));incoming.getJSONObject("history").put("same:0:0",row("same",20).put("position",5000));String before=current.toString();JSONObject parsed=portable(incoming);LibraryBackup.MergeResult equal=LibraryBackup.merge(current,parsed);assertEquals(1,equal.addedTitles);assertEquals(0,equal.importedHistory);assertEquals("Existing title",equal.library.getJSONObject("list").getJSONObject("same").getString("title"));assertEquals(3000,equal.library.getJSONObject("history").getJSONObject("same:0:0").getInt("position"));
        parsed.getJSONArray("history").getJSONObject(0).put("updated",21);LibraryBackup.MergeResult newer=LibraryBackup.merge(current,parsed);assertEquals(1,newer.importedHistory);assertEquals(5000,newer.library.getJSONObject("history").getJSONObject("same:0:0").getInt("position"));newer.library.getJSONObject("settings").put("token","changed");assertEquals(before,current.toString());assertEquals(21,parsed.getJSONArray("history").getJSONObject(0).getInt("updated"));
    }
    @Test public void historyCapReportsExistingEvictionsAndDoesNotCountDiscardedImports()throws Exception{
        JSONObject current=data(),incoming=data();for(int i=0;i<300;i++)current.getJSONObject("history").put("old"+i+":0:0",row("old"+i,100+i));for(int i=0;i<3;i++)incoming.getJSONObject("history").put("new"+i+":0:0",row("new"+i,1000+i));LibraryBackup.MergeResult result=LibraryBackup.merge(current,portable(incoming));assertEquals(300,result.library.getJSONObject("history").length());assertEquals(3,result.evictedExistingHistory);assertEquals(3,result.importedHistory);assertEquals(300,current.getJSONObject("history").length());
        incoming=data();incoming.getJSONObject("history").put("ancient:0:0",row("ancient",1));result=LibraryBackup.merge(current,portable(incoming));assertEquals(0,result.importedHistory);assertEquals(0,result.evictedExistingHistory);
    }
    @Test public void strictParserRejectsMalformedDuplicateTrailingFutureAndInvalidUtf8()throws Exception{
        byte[] valid=LibraryBackup.export(data());String text=new String(valid,StandardCharsets.UTF_8);for(String bad:new String[]{text.substring(0,text.length()-1),text+" garbage",text.replace("\"version\":1","\"version\":2"),"{\"format\":\"moviebox-library\",\"version\":1,\"version\":1,\"list\":[],\"history\":[]}","{'format':'moviebox-library','version':1,'list':[],'history':[]}"})rejected(()->LibraryBackup.parse(bad.getBytes(StandardCharsets.UTF_8)));rejected(()->LibraryBackup.parse(new byte[]{(byte)0xc3,(byte)0x28}));rejected(()->LibraryBackup.parse(new byte[LibraryBackup.MAX_BYTES+1]));
    }
    @Test public void recordTypesBoundsAndUnknownPrivateFieldsRejectWithoutMutation()throws Exception{
        JSONObject original=data();original.getJSONObject("history").put("safe:0:0",row("safe",100));JSONObject valid=portable(original);String before=original.toString();
        for(String field:new String[]{"position","duration","updated"}){JSONObject bad=new JSONObject(valid.toString());bad.getJSONArray("history").getJSONObject(0).put(field,-1);rejected(()->LibraryBackup.merge(original,bad));}
        JSONObject bad=new JSONObject(valid.toString());bad.getJSONArray("history").getJSONObject(0).put("completed","false");rejected(()->LibraryBackup.merge(original,bad));JSONObject privateRoot=new JSONObject(valid.toString()).put("identity","secret");rejected(()->LibraryBackup.merge(original,privateRoot));assertEquals(before,original.toString());
    }
    @Test public void duplicateIdentitiesOversizedRecordSetsAndLocalArtworkAreRejected()throws Exception{
        JSONObject valid=portable(data());JSONObject publicTitle=LibraryBackup.parse(LibraryBackup.export(new JSONObject(data().toString()).put("list",new JSONObject().put("safe",title("safe"))))).getJSONArray("list").getJSONObject(0);valid.getJSONArray("list").put(publicTitle).put(publicTitle);final JSONObject duplicate=valid;rejected(()->LibraryBackup.merge(data(),duplicate));
        JSONObject bad=portable(data());for(int i=0;i<=LibraryBackup.MAX_RECORDS;i++)bad.getJSONArray("list").put(new JSONObject(publicTitle.toString()).put("subjectId","id"+i));rejected(()->LibraryBackup.merge(data(),bad));JSONObject local=new JSONObject(publicTitle.toString()).put("cover","https://127.0.0.1/private");JSONObject unsafe=portable(data());unsafe.getJSONArray("list").put(local);rejected(()->LibraryBackup.merge(data(),unsafe));
    }
    @Test public void nullableOptionalLocalMetadataIsOmittedButImportAndWrongShapesStayStrict()throws Exception{
        JSONObject source=data(),item=title("nullable");for(String key:new String[]{"cover","releaseDate","countryName","genre","imdbRatingValue"})item.put(key,JSONObject.NULL);source.getJSONObject("list").put("nullable",item);JSONObject exported=portable(source).getJSONArray("list").getJSONObject(0);for(String key:new String[]{"cover","releaseDate","countryName","genre","imdbRatingValue"})assertFalse(exported.has(key));
        for(String key:new String[]{"cover","releaseDate","countryName","genre","imdbRatingValue"}){JSONObject incoming=portable(source);incoming.getJSONArray("list").getJSONObject(0).put(key,JSONObject.NULL);rejected(()->LibraryBackup.merge(data(),incoming));}
        item.put("cover",new JSONArray());rejected(()->LibraryBackup.export(source));
        assertEquals(0,LibraryBackup.strictObject("{}".getBytes(StandardCharsets.UTF_8),16*1024*1024).length());rejected(()->LibraryBackup.strictObject("{}garbage".getBytes(StandardCharsets.UTF_8),16*1024*1024));rejected(()->LibraryBackup.strictObject("{\"a\":1,\"a\":2}".getBytes(StandardCharsets.UTF_8),16*1024*1024));
    }
    @Test public void onlyExactLocalUnknownDurationSentinelExportsAsZero()throws Exception{
        JSONObject source=data(),entry=row("unknown-duration",10).put("duration",-9223372036854775807L);source.getJSONObject("history").put("unknown-duration:0:0",entry);JSONObject portable=portable(source);assertEquals(0,portable.getJSONArray("history").getJSONObject(0).getLong("duration"));assertEquals(-9223372036854775807L,entry.getLong("duration"));
        portable.getJSONArray("history").getJSONObject(0).put("duration",-9223372036854775807L);rejected(()->LibraryBackup.merge(data(),portable));entry.put("duration",-1);rejected(()->LibraryBackup.export(source));
    }
}
