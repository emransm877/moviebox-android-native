package org.emransm.moviebox.phone;

import android.app.*;
import android.content.*;
import android.view.View;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

/** Owned private future-APK fixture and injected streams only; never fetches a release asset or installs a package. */
@RunWith(AndroidJUnit4.class)
public class InAppUpdateScenarios {
    static final String HASH="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    static Context target(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    static byte[] fixture()throws Exception{
        try(InputStream input=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("qa-update-future.apk");ByteArrayOutputStream output=new ByteArrayOutputStream()){byte[] b=new byte[8192];for(int n;(n=input.read(b))!=-1;)output.write(b,0,n);return output.toByteArray();}
        catch(FileNotFoundException missing){Assume.assumeTrue("SKIPPED: private owned future update APK is not supplied",false);return null;}
        catch(IOException missing){Assume.assumeTrue("SKIPPED: private owned future update APK is not supplied",false);return null;}
    }
    static JSONObject metadata(byte[] data)throws Exception{return new JSONObject().put("status",Updates.AVAILABLE).put("available",true).put("version","0.1.5").put("url","https://github.com/"+Updates.REPOSITORY+"/releases/download/v0.1.5/MovieBox-Android-App-0.1.5.apk").put("size",data.length).put("sha256",UpdateDownload.hex(MessageDigest.getInstance("SHA-256").digest(data)));}
    static File directory()throws Exception{File file=new File(target().getFilesDir(),"qa-owned-updater-"+UUID.randomUUID());assertTrue(file.mkdirs());return file;}
    static void remove(File directory)throws Exception{DetailLoadingScenarios.remove(directory,directory.getCanonicalPath());}
    static UpdateDownload.Source source(byte[] data,AtomicInteger requests){return (spec,job)->{requests.incrementAndGet();return new UpdateDownload.Transfer(){final InputStream input=new ByteArrayInputStream(data);public InputStream stream(){return input;}public long length(){return data.length;}public void close()throws IOException{input.close();}};};}
    static final class Result implements UpdateDownload.Listener {File ready;boolean failed,cancelled;long lastProgress=-1;public void progress(long bytes,long total){assertTrue(bytes>=lastProgress);assertTrue(bytes<=total);lastProgress=bytes;}public void ready(File file){ready=file;}public void failed(){failed=true;}public void cancelled(){cancelled=true;}}
    @Test public void manualCheckGivesImmediateProgressCoalescesAndClearsOnFailure()throws Exception{
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            main(scenario,a->{FrameLayout root=new FrameLayout(a);SharedPreferences prefs=a.getSharedPreferences("qa-check-progress-"+UUID.randomUUID(),0);try{UpdateReliabilityScenarios.Queued worker=new UpdateReliabilityScenarios.Queued();AtomicInteger checks=new AtomicInteger();List<Boolean> states=new ArrayList<>();UpdateNotice.Checker checker=()->{checks.incrementAndGet();throw new IOException("Owned metadata failure");};UpdateNotice.check(a,root,()->false,true,prefs,checker,()->100000000L,worker,states::add);assertNull("Caller feedback owns the checking presentation",root.findViewWithTag("moviebox-update-checking"));assertEquals(Collections.singletonList(true),states);UpdateNotice.check(a,root,()->false,true,prefs,checker,()->100000000L,worker);worker.finish();assertEquals(1,checks.get());assertEquals(Arrays.asList(true,false),states);assertNull(root.findViewWithTag("moviebox-update-checking"));assertFalse(prefs.contains("checked"));}finally{prefs.edit().clear().commit();}});
        }
    }
    @Test public void incompleteHashesBoundsAndMismatchedTagsCannotDownload()throws Exception{
        JSONObject release=UpdateReliabilityScenarios.release("0.2.0");JSONObject asset=release.getJSONArray("assets").getJSONObject(0);
        for(Object digest:new Object[]{JSONObject.NULL,"","sha256:a","md5:"+HASH}){asset.put("digest",digest);assertEquals(Updates.INCOMPLETE,Updates.evaluate(release,"0.1.0").getString("status"));}
        asset.put("digest","sha256:"+HASH).put("size",UpdateDownloadPolicy.MAX_BYTES+1);assertEquals(Updates.INCOMPLETE,Updates.evaluate(release,"0.1.0").getString("status"));
        byte[] bytes=fixture();JSONObject good=metadata(bytes);for(JSONObject invalid:new JSONObject[]{new JSONObject(good.toString()).put("sha256","bad"),new JSONObject(good.toString()).put("url",good.getString("url").replace("github.com","github.com.evil.invalid")),new JSONObject(good.toString()).put("version","0.1.6"),new JSONObject(good.toString()).put("url",good.getString("url").replace("MovieBox-Android-App-0.1.5.apk","../other.apk"))})try{new UpdateDownload.Spec(invalid);fail("Invalid update accepted");}catch(Exception expected){}
    }
    @Test public void ownedSignedArchiveDownloadsVerifiesAndReusesWithoutAnotherRequest()throws Exception{
        byte[] bytes=fixture();File dir=directory();try{AtomicInteger requests=new AtomicInteger();UpdateDownload.Engine engine=new UpdateDownload.Engine(target(),dir,Runnable::run,source(bytes,requests),UpdateDownload::verifyArchive);UpdateDownload.Spec spec=new UpdateDownload.Spec(metadata(bytes));Result first=new Result();engine.start(spec,first);assertFalse(first.failed);assertFalse(first.cancelled);assertNotNull(first.ready);assertEquals(bytes.length,first.ready.length());assertEquals(bytes.length,first.lastProgress);assertEquals(spec.sha256+".apk",first.ready.getName());assertEquals("ready",first.ready.getParentFile().getName());UpdateDownload.verifyBytes(first.ready,spec);UpdateDownload.verifyArchive(target(),first.ready,spec);
            Result cached=new Result();engine.start(spec,cached);assertNotNull(cached.ready);assertEquals(1,requests.get());assertFalse(cached.failed);
        }finally{remove(dir);}
    }
    @Test public void corruptTruncatedOversizeAndInvalidArchiveNeverBecomeReady()throws Exception{
        byte[] valid=fixture();UpdateDownload.Spec spec=new UpdateDownload.Spec(metadata(valid));byte[] corrupt=valid.clone();corrupt[0]^=1;
        for(byte[] body:new byte[][]{corrupt,Arrays.copyOf(valid,valid.length-1),Arrays.copyOf(valid,valid.length+1)}){File dir=directory();try{Result result=new Result();new UpdateDownload.Engine(target(),dir,Runnable::run,source(body,new AtomicInteger()),UpdateDownload::verifyArchive).start(spec,result);assertTrue(result.failed);assertNull(result.ready);assertFalse(result.cancelled);assertEquals(0,Objects.requireNonNull(new File(dir,"ready").list()).length);for(File file:Objects.requireNonNull(dir.listFiles()))assertTrue("Partial/staged files must be removed",file.isDirectory());}finally{remove(dir);}}
        byte[] notApk="Owned non-package fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8);File dir=directory();try{Result result=new Result();new UpdateDownload.Engine(target(),dir,Runnable::run,source(notApk,new AtomicInteger()),UpdateDownload::verifyArchive).start(new UpdateDownload.Spec(metadata(notApk)),result);assertTrue(result.failed);assertNull(result.ready);}finally{remove(dir);}
        dir=directory();try{JSONObject relabelled=metadata(valid);relabelled.put("version","0.1.6").put("url",relabelled.getString("url").replace("0.1.5","0.1.6"));Result result=new Result();new UpdateDownload.Engine(target(),dir,Runnable::run,source(valid,new AtomicInteger()),UpdateDownload::verifyArchive).start(new UpdateDownload.Spec(relabelled),result);assertTrue("A valid signed APK cannot be relabelled as another version",result.failed);assertNull(result.ready);}finally{remove(dir);}
    }
    @Test public void cancellationRemovesPartialAndKeepsPreviousVerifiedArtifact()throws Exception{
        byte[] bytes=fixture();File dir=directory();try{File ready=new File(dir,"ready");assertTrue(ready.mkdir());File previous=new File(ready,"previous-owned.apk");try(FileOutputStream out=new FileOutputStream(previous)){out.write(bytes);}AtomicReference<Runnable> task=new AtomicReference<>();Result result=new Result();UpdateDownload.Job job=new UpdateDownload.Engine(target(),dir,task::set,source(bytes,new AtomicInteger()),UpdateDownload::verifyArchive).start(new UpdateDownload.Spec(metadata(bytes)),result);job.cancel();task.get().run();assertTrue(result.cancelled);assertNull(result.ready);assertTrue(previous.exists());assertEquals(1,Objects.requireNonNull(ready.list()).length);
            Result mid=new Result();UpdateDownload.Source cancelSource=(spec,active)->new UpdateDownload.Transfer(){final InputStream input=new ByteArrayInputStream(bytes){@Override public synchronized int read(byte[] b,int offset,int length){int n=super.read(b,offset,length);active.cancel();return n;}};public InputStream stream(){return input;}public long length(){return bytes.length;}public void close()throws IOException{input.close();}};new UpdateDownload.Engine(target(),dir,Runnable::run,cancelSource,UpdateDownload::verifyArchive).start(new UpdateDownload.Spec(metadata(bytes)),mid);assertTrue(mid.cancelled);assertNull(mid.ready);for(File file:Objects.requireNonNull(dir.listFiles()))assertTrue(file.isDirectory());assertTrue(previous.exists());
        }finally{remove(dir);}
    }
    @Test public void dialogDownloadsInsideAppThenRequiresExplicitInstallTap()throws Exception{
        byte[] bytes=fixture();File dir=directory();AtomicReference<Runnable> task=new AtomicReference<>();AtomicInteger installCalls=new AtomicInteger(),requests=new AtomicInteger();AlertDialog[] shown={null};
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(scenario,new BlockFlowScenarios.FakeApi());main(scenario,a->call(a,"browse",new Class[]{String.class},"Settings"));Thread.sleep(3600);
            main(scenario,a->{try{shown[0]=(AlertDialog)UpdateDownload.show(a,metadata(bytes),new UpdateDownload.Engine(a,dir,task::set,source(bytes,requests),UpdateDownload::verifyArchive),(activity,file,spec)->installCalls.incrementAndGet());assertEquals(0,requests.get());assertEquals(0,installCalls.get());assertEquals("Download update",shown[0].getButton(AlertDialog.BUTTON_POSITIVE).getContentDescription());assertTrue(shown[0].getButton(AlertDialog.BUTTON_POSITIVE).performClick());assertFalse(shown[0].getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());assertEquals("Cancel update download",shown[0].getButton(AlertDialog.BUTTON_NEGATIVE).getContentDescription());String status=((TextView)description(shown[0].getWindow().getDecorView(),"Update download status")).getText().toString();assertEquals("Downloading update - 0%",status);assertFalse(status.contains("?"));}catch(Exception e){throw new AssertionError(e);}});
            assertNotNull(task.get());main(scenario,a->{UpdateVisualScenarios.redraw(a.getWindow().getDecorView());UpdateVisualScenarios.redraw(shown[0].getWindow().getDecorView());});Thread.sleep(450);BlockFlowScenarios.capture("qa-update-in-app-progress.png");task.get().run();main(scenario,a->{assertEquals(1,requests.get());assertEquals(0,installCalls.get());Button install=shown[0].getButton(AlertDialog.BUTTON_POSITIVE);assertEquals("Install update",install.getContentDescription());assertTrue(install.isEnabled());assertEquals("Ready to install",((TextView)description(shown[0].getWindow().getDecorView(),"Update download status")).getText().toString());});
            main(scenario,a->{UpdateVisualScenarios.redraw(a.getWindow().getDecorView());UpdateVisualScenarios.redraw(shown[0].getWindow().getDecorView());});Thread.sleep(450);BlockFlowScenarios.capture("qa-update-verified-ready.png");main(scenario,a->{assertTrue(shown[0].getButton(AlertDialog.BUTTON_POSITIVE).performClick());assertFalse(shown[0].getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());assertEquals(0,installCalls.get());});task.get().run();main(scenario,a->{assertEquals(1,installCalls.get());shown[0].dismiss();});
        }finally{remove(dir);}
    }
    @Test public void providerOnlyExposesReadyFilesAndInstallerIntentUsesReadGrant()throws Exception{
        byte[] bytes=fixture();File ready=new File(target().getFilesDir(),"updates/ready");assertTrue(ready.isDirectory()||ready.mkdirs());File file=new File(ready,"qa-owned-"+UUID.randomUUID()+".apk");File part=new File(ready.getParentFile(),"qa-owned-"+UUID.randomUUID()+".part");
        try{try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);}Intent intent=UpdateDownload.installIntent(target(),file);assertEquals(Intent.ACTION_INSTALL_PACKAGE,intent.getAction());assertEquals("content",intent.getData().getScheme());assertEquals(target().getPackageName()+".updates",intent.getData().getAuthority());assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION,intent.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);try(InputStream input=target().getContentResolver().openInputStream(intent.getData())){assertNotNull(input);assertEquals(bytes[0]&255,input.read());}
            try{androidx.core.content.FileProvider.getUriForFile(target(),target().getPackageName()+".updates",part);fail("Part file must be outside provider paths");}catch(IllegalArgumentException expected){}
        }finally{file.delete();part.delete();}
    }
    @Test public void asyncInstallRechecksTamperingAndDismissalWithoutOpeningInstaller()throws Exception {
        byte[] bytes=fixture();File dir=directory();AtomicReference<Runnable> task=new AtomicReference<>();AtomicInteger installs=new AtomicInteger();AlertDialog[] dialog={null};
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            main(scenario,a->{dialog[0]=(AlertDialog)UpdateDownload.show(a,metadata(bytes),new UpdateDownload.Engine(a,dir,task::set,source(bytes,new AtomicInteger()),UpdateDownload::verifyArchive),(activity,file,spec)->installs.incrementAndGet());dialog[0].getButton(AlertDialog.BUTTON_POSITIVE).performClick();});task.get().run();
            main(scenario,a->{assertEquals("Install update",dialog[0].getButton(AlertDialog.BUTTON_POSITIVE).getContentDescription());File[] files=new File(dir,"ready").listFiles();assertNotNull(files);assertEquals(1,files.length);try(FileOutputStream changed=new FileOutputStream(files[0])){changed.write(1);}assertTrue(dialog[0].getButton(AlertDialog.BUTTON_POSITIVE).performClick());assertEquals(0,installs.get());});
            task.get().run();main(scenario,a->{assertEquals(0,installs.get());assertEquals("Retry update download",dialog[0].getButton(AlertDialog.BUTTON_POSITIVE).getContentDescription());assertTrue(dialog[0].getButton(AlertDialog.BUTTON_POSITIVE).performClick());});task.get().run();
            main(scenario,a->{assertEquals("Install update",dialog[0].getButton(AlertDialog.BUTTON_POSITIVE).getContentDescription());assertTrue(dialog[0].getButton(AlertDialog.BUTTON_POSITIVE).performClick());dialog[0].dismiss();});task.get().run();main(scenario,a->assertEquals(0,installs.get()));
        }finally{remove(dir);}
    }

    @Test public void homeUpdateTapStartsOwnedDownloadWithoutSecondConfirmationOrAutoInstall()throws Exception {
        byte[] bytes=fixture();File dir=directory();AtomicReference<Runnable> task=new AtomicReference<>();AtomicInteger requests=new AtomicInteger(),installs=new AtomicInteger();AlertDialog[] dialog={null};SharedPreferences[] prefs={null};
        try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            main(scenario,a->{prefs[0]=a.getSharedPreferences("qa-owned-home-update-"+UUID.randomUUID(),0);FrameLayout root=(FrameLayout)field(a,"root");JSONObject update=metadata(bytes);UpdateNotice.show(a,root,prefs[0],update,()->dialog[0]=(AlertDialog)UpdateDownload.showAndStart(a,update,new UpdateDownload.Engine(a,dir,task::set,source(bytes,requests),UpdateDownload::verifyArchive),(activity,file,spec)->installs.incrementAndGet()));assertNull(task.get());assertEquals(0,requests.get());assertTrue(description(root.findViewWithTag("moviebox-update-notice"),"View update details").performClick());assertNotNull(task.get());assertEquals(0,requests.get());assertFalse(dialog[0].getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());assertEquals(android.view.View.GONE,dialog[0].getButton(AlertDialog.BUTTON_POSITIVE).getVisibility());assertEquals("Cancel",dialog[0].getButton(AlertDialog.BUTTON_NEGATIVE).getText().toString());});
            task.get().run();main(scenario,a->{assertEquals(1,requests.get());assertEquals(0,installs.get());assertEquals("Install update",dialog[0].getButton(AlertDialog.BUTTON_POSITIVE).getContentDescription());dialog[0].dismiss();});
        }finally{if(prefs[0]!=null)prefs[0].edit().clear().commit();remove(dir);}
    }

}
