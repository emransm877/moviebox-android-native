package org.emransm.moviebox.phone;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import java.io.*;
import java.net.*;
import java.lang.reflect.Method;
import java.util.*;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

/** Opt-in network scenarios: official Android sample only, bounded below 2 MB. */
@RunWith(AndroidJUnit4.class)
public class FullDownloadScenarios {
    @Before public void removeOnlyAbandonedOwnedTestFixtures(){for(Map.Entry<String,?> entry:prefs().getAll().entrySet()){String title=String.valueOf(entry.getValue());if(title.startsWith("QA queued network fixture ")||title.startsWith("QA official network video ")||title.startsWith("QA missing official fixture "))try{long id=Long.parseLong(entry.getKey());manager().remove(id);prefs().edit().remove(entry.getKey()).commit();}catch(Exception ignored){}}}
    private static final String SAMPLE="https://storage.googleapis.com/androiddevelopers/samples_assets/exoplayer-test-media-1/mp4/android-screens-10s.mp4";
    private static final long MAX_BYTES=2*1024*1024;
    private static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private static DownloadManager manager(){return (DownloadManager)context().getSystemService(Context.DOWNLOAD_SERVICE);}
    private static SharedPreferences prefs(){return context().getSharedPreferences("phone_downloads",Context.MODE_PRIVATE);}
    private static View title(View view,String label){if(view instanceof TextView&&label.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View match=title(group.getChildAt(i),label);if(match!=null)return match;}}return null;}
    private static View button(View view,String suffix){if(view instanceof Button&&((Button)view).getText().toString().trim().endsWith(suffix))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View match=button(group.getChildAt(i),suffix);if(match!=null)return match;}}return null;}
    private static long[] status(long id)throws Exception {try(Cursor c=manager().query(new DownloadManager.Query().setFilterById(id))){if(c==null||!c.moveToFirst())return new long[]{0,0,-1,0};return new long[]{c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))};}}
    private static boolean rowState(ActivityScenario<PhoneActivity> scenario,String name,String text){boolean[] found={false};main(scenario,a->{View label=title((View)field(a,"root"),name);found[0]=label!=null&&title((View)label.getParent(),text)!=null;});return found[0];}
    private static void screenshot(String filename)throws Exception {Thread.sleep(400);InstrumentationRegistry.getInstrumentation().waitForIdleSync();Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(image);try(OutputStream out=new FileOutputStream(new File(context().getExternalFilesDir(null),filename))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}finally{image.recycle();}}
    private static void confirmRemoval()throws Exception {Method method=PhoneDownloadsScenarios.class.getDeclaredMethod("clickPositiveRemoval");method.setAccessible(true);assertTrue((Boolean)method.invoke(null));}
    private static void cleanup(long id,String offline)throws Exception {if(id>0){manager().remove(id);prefs().edit().remove(Long.toString(id)).commit();}if(offline!=null){Library library=new Library(context());library.removeTitles("history",Collections.singleton("offline:"+offline));library.close();Thread.sleep(300);}}
    private static void verifyOfficialSize()throws Exception {HttpURLConnection head=(HttpURLConnection)new URL(SAMPLE).openConnection();head.setRequestMethod("HEAD");head.setConnectTimeout(10000);head.setReadTimeout(10000);head.setInstanceFollowRedirects(false);try{assertEquals(200,head.getResponseCode());long size=Long.parseLong(head.getHeaderField("Content-Length"));assertTrue("Official fixture stays within authorized 2 MB cap",size>0&&size<=MAX_BYTES);}finally{head.disconnect();}}
    @Test public void officialSmallHttpsVideoCompletesRetainsPlaysAndRemoves()throws Exception {
        verifyOfficialSize();String name="QA official network video "+UUID.randomUUID();long id=-1;String offline=null;
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            try{
                main(scenario,a->{PhoneDownloads.enqueue(a,new JSONObject().put("title",name).put("subjectId","qa-official-network"),new JSONObject().put("resourceLink",SAMPLE).put("resolution",480),0,0);call(a,"browse",new Class[]{String.class},"Downloads");});
                for(Map.Entry<String,?> entry:prefs().getAll().entrySet())if(name.equals(entry.getValue()))id=Long.parseLong(entry.getKey());assertTrue("Actual app enqueue persisted system ID",id>0);final long owned=id;Set<Long> observed=new TreeSet<>();
                waitFor(()->{long[] s=status(owned);observed.add(s[0]);assertTrue("Download remains within size cap",s[1]<=MAX_BYTES);assertNotEquals("Official sample download failed, reason="+s[3],DownloadManager.STATUS_FAILED,s[0]);return s[0]==DownloadManager.STATUS_SUCCESSFUL;},60000);
                long[] complete=status(id);assertEquals(1854368,complete[1]);assertEquals(complete[1],complete[2]);Uri uri=manager().getUriForDownloadedFile(id);assertNotNull(uri);offline=uri.toString();
                try(InputStream in=context().getContentResolver().openInputStream(uri)){assertNotNull(in);byte[] header=new byte[12];new DataInputStream(in).readFully(header);assertEquals("ftyp",new String(header,4,4,"US-ASCII"));}
                waitFor(()->rowState(scenario,name,"Ready to play"),5000);screenshot("qa-network-download-ready.png");scenario.recreate();main(scenario,a->call(a,"browse",new Class[]{String.class},"Downloads"));waitFor(()->rowState(scenario,name,"Ready to play"),5000);
                main(scenario,a->{View label=title((View)field(a,"root"),name);View play=button((View)label.getParent(),"Play");assertNotNull(play);assertTrue(play.performClick());});waitFor(()->playing(scenario),15000);waitFor(()->position(scenario)>1000,6000);main(scenario,a->call(a,"leavePlayer",new Class[]{}));
                waitFor(()->rowState(scenario,name,"Ready to play"),5000);main(scenario,a->{View label=title((View)field(a,"root"),name);assertTrue(button((View)label.getParent(),"Remove").performClick());});confirmRemoval();waitFor(()->!prefs().contains(Long.toString(owned))&&status(owned)[0]==0,5000);
                System.out.println("NETWORK_DOWNLOAD_OBSERVED_STATES="+observed+" bytes="+complete[1]);
            }finally{cleanup(id,offline);}
        }
    }
    @Test public void queuedOwnedDownloadPersistsThenCancelsThroughApp()throws Exception {
        String name="QA queued network fixture "+UUID.randomUUID();File file=new File(context().getExternalFilesDir(Environment.DIRECTORY_MOVIES),"qa-queued-"+UUID.randomUUID()+".mp4");long id=-1;
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            try{
                // No allowed network transports: deterministic queued/waiting state, zero transfer.
                id=manager().enqueue(new DownloadManager.Request(Uri.parse(SAMPLE)).setTitle(name).setAllowedNetworkTypes(0).setDestinationUri(Uri.fromFile(file)));final long owned=id;assertTrue(id>0);assertTrue(prefs().edit().putString(Long.toString(id),name).commit());main(scenario,a->call(a,"browse",new Class[]{String.class},"Downloads"));
                waitFor(()->rowState(scenario,name,"Queued")||rowState(scenario,name,"Waiting for connection"),5000);screenshot("qa-network-download-queued.png");scenario.recreate();main(scenario,a->call(a,"browse",new Class[]{String.class},"Downloads"));waitFor(()->rowState(scenario,name,"Queued")||rowState(scenario,name,"Waiting for connection"),5000);
                main(scenario,a->{View label=title((View)field(a,"root"),name);assertTrue(button((View)label.getParent(),"Cancel").performClick());});confirmRemoval();waitFor(()->!prefs().contains(Long.toString(owned))&&status(owned)[0]==0,5000);assertEquals("No network bytes transferred in queued-only scenario",0,file.exists()?file.length():0);
            }finally{cleanup(id,null);if(file.exists())file.delete();}
        }
    }
    @Test public void actualHttps404ShowsFailureAndCanBeRemoved()throws Exception {
        String name="QA missing official fixture "+UUID.randomUUID();long id=-1;
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            try{
                main(scenario,a->{PhoneDownloads.enqueue(a,new JSONObject().put("title",name).put("subjectId","qa-missing-network"),new JSONObject().put("resourceLink","https://storage.googleapis.com/androiddevelopers/samples_assets/exoplayer-test-media-1/mp4/qa-nonexistent-moviebox-fixture-2026.mp4").put("resolution",480),0,0);call(a,"browse",new Class[]{String.class},"Downloads");});
                for(Map.Entry<String,?> entry:prefs().getAll().entrySet())if(name.equals(entry.getValue()))id=Long.parseLong(entry.getKey());assertTrue(id>0);final long owned=id;waitFor(()->status(owned)[0]==DownloadManager.STATUS_FAILED,60000);waitFor(()->rowState(scenario,name,"Download failed. Remove it and try again."),5000);screenshot("qa-network-download-failed.png");
                main(scenario,a->{View label=title((View)field(a,"root"),name);assertTrue(button((View)label.getParent(),"Remove").performClick());});confirmRemoval();waitFor(()->!prefs().contains(Long.toString(owned))&&status(owned)[0]==0,5000);
            }finally{cleanup(id,null);}
        }
    }
}
