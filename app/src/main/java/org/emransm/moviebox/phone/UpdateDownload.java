package org.emransm.moviebox.phone;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.HttpsURLConnection;

/** Explicit in-app APK download. The Android installer always owns installation. */
final class UpdateDownload {
    static final class Spec {
        final String version,url,sha256;final long size;
        Spec(JSONObject metadata)throws Exception {
            if(!Updates.downloadable(metadata))throw new IOException("Update details could not be verified. Check again.");
            version=metadata.getString("version").replaceFirst("^v","");url=metadata.getString("url");sha256=metadata.getString("sha256").toLowerCase(Locale.ROOT);size=metadata.getLong("size");
            String path=new URI(url).getPath(),base="/"+Updates.REPOSITORY+"/releases/download/";
            String suffix=path.substring(base.length());int separator=suffix.indexOf('/');
            if(separator<0||!suffix.substring(0,separator).replaceFirst("^v","").equals(version))throw new IOException("Update version does not match its download.");
            String name=suffix.substring(separator+1);if(!name.equals("MovieBox-Android-App-"+version+".apk")&&!name.equals("MovieBox-Android-Native-"+version+".apk"))throw new IOException("Update filename does not match its version.");
        }
    }
    interface Listener {void progress(long bytes,long total);void ready(File file);void failed();void cancelled();}
    interface Transfer extends Closeable {InputStream stream()throws Exception;long length();}
    interface Source {Transfer open(Spec spec,Job job)throws Exception;}
    interface Verifier {void verify(Context context,File file,Spec spec)throws Exception;}
    interface Installer {void open(Activity activity,File file,Spec spec)throws Exception;}
    static final class Job {
        volatile boolean cancelled;volatile Closeable connection;volatile Thread thread;
        void cancel(){cancelled=true;Closeable active=connection;if(active!=null)try{active.close();}catch(Exception ignored){}Thread activeThread=thread;if(activeThread!=null&&activeThread!=Thread.currentThread())activeThread.interrupt();}
        void check()throws InterruptedIOException {if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Update download cancelled");}
    }
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    static final class Engine {
        final Context context;final File directory;final Executor worker;final Source source;final Verifier verifier;
        Engine(Context context){this(context,new File(context.getFilesDir(),"updates"),WORK,UpdateDownload::network,UpdateDownload::verifyArchive);}
        Engine(Context context,File directory,Executor worker,Source source,Verifier verifier){this.context=context.getApplicationContext();this.directory=directory;this.worker=worker;this.source=source;this.verifier=verifier;}
        Job start(Spec spec,Listener listener){Job job=new Job();try{worker.execute(()->run(spec,listener,job));}catch(RejectedExecutionException unavailable){listener.failed();}return job;}
        private void run(Spec spec,Listener listener,Job job){
            File part=null,staged=null;job.thread=Thread.currentThread();
            try{
                job.check();if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Update storage unavailable");
                // A killed process cannot execute finally. Remove only our old
                // staging names on a later explicit download; never user files.
                File[] abandoned=directory.listFiles();if(abandoned!=null)for(File file:abandoned)if(file.getName().matches("[0-9a-f-]{36}\\.(part|apk)")&&file.lastModified()<System.currentTimeMillis()-4*UpdateDownloadPolicy.MAX_MILLIS)file.delete();
                File ready=new File(directory,"ready");if(!ready.isDirectory()&&!ready.mkdirs())throw new IOException("Update storage unavailable");
                File target=new File(ready,spec.sha256+".apk");
                if(target.exists())try{verifyBytes(target,spec);verifier.verify(context,target,spec);job.check();listener.ready(target);return;}catch(Exception invalid){if(job.cancelled)throw invalid;if(!target.delete())throw new IOException("Old update cannot be replaced");}
                long space=directory.getUsableSpace();if(space>0&&space<spec.size)throw new IOException("Not enough space for update");
                String attempt=UUID.randomUUID().toString();part=new File(directory,attempt+".part");staged=new File(directory,attempt+".apk");
                long began=SystemClock.elapsedRealtime(),read=0,lastProgress=0;MessageDigest digest=MessageDigest.getInstance("SHA-256");
                try(Transfer transfer=source.open(spec,job);InputStream input=transfer.stream();FileOutputStream output=new FileOutputStream(part)){
                    job.connection=transfer;if(transfer.length()>=0&&transfer.length()!=spec.size)throw new IOException("Update size does not match");byte[] buffer=new byte[32768];listener.progress(0,spec.size);
                    for(int amount;(amount=input.read(buffer))!=-1;){job.check();if(SystemClock.elapsedRealtime()-began>UpdateDownloadPolicy.MAX_MILLIS)throw new IOException("Update download timed out");if(amount==0)continue;read+=amount;if(read>spec.size||read>UpdateDownloadPolicy.MAX_BYTES)throw new IOException("Update is larger than expected");output.write(buffer,0,amount);digest.update(buffer,0,amount);long now=SystemClock.elapsedRealtime();if(now-lastProgress>=100){listener.progress(read,spec.size);lastProgress=now;}}
                    output.flush();output.getFD().sync();if(read!=spec.size||!hex(digest.digest()).equals(spec.sha256))throw new IOException("Update could not be verified");
                }finally{job.connection=null;}
                job.check();if(!part.renameTo(staged))throw new IOException("Update cannot be staged");verifier.verify(context,staged,spec);job.check();
                if(!staged.renameTo(target))throw new IOException("Update cannot be saved");listener.progress(spec.size,spec.size);listener.ready(target);
                // Keep a previous ready APK while a new download is incomplete;
                // after verified success retain only this bounded ready artifact.
                File[] previous=ready.listFiles();if(previous!=null)for(File file:previous)if(!file.equals(target)&&file.getName().matches("[0-9a-f]{64}\\.apk"))file.delete();
            }catch(Exception failure){if(job.cancelled||Thread.currentThread().isInterrupted())listener.cancelled();else listener.failed();}
            finally{if(part!=null)part.delete();if(staged!=null)staged.delete();job.connection=null;job.thread=null;}
        }
    }
    private static Transfer network(Spec spec,Job job)throws Exception {
        URL current=new URL(spec.url);
        for(int hop=0;hop<=5;hop++){
            job.check();if(!UpdateDownloadPolicy.redirect(current.toString()))throw new IOException("Unexpected update redirect");
            HttpsURLConnection connection=(HttpsURLConnection)current.openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(6000);connection.setReadTimeout(8000);connection.setRequestProperty("Accept","application/octet-stream");connection.setRequestProperty("User-Agent","MovieBoxPhone/"+Updates.VERSION);job.connection=connection::disconnect;
            int status;try{status=connection.getResponseCode();}catch(Exception failure){connection.disconnect();throw failure;}
            if(status==301||status==302||status==303||status==307||status==308){String location=connection.getHeaderField("Location");connection.disconnect();if(location==null||hop==5)throw new IOException("Update redirect limit reached");current=new URL(current,location);continue;}
            if(status!=200){connection.disconnect();throw new IOException("Update download unavailable");}
            long contentLength=-1;try{String value=connection.getHeaderField("Content-Length");if(value!=null)contentLength=Long.parseLong(value);}catch(NumberFormatException invalid){connection.disconnect();throw new IOException("Invalid update size");}
            final long length=contentLength;return new Transfer(){public InputStream stream()throws Exception{return connection.getInputStream();}public long length(){return length;}public void close(){connection.disconnect();}};
        }throw new IOException("Update redirect limit reached");
    }
    static String hex(byte[] bytes){StringBuilder result=new StringBuilder();for(byte value:bytes)result.append(String.format(Locale.ROOT,"%02x",value&255));return result.toString();}
    static void verifyBytes(File file,Spec spec)throws Exception {if(file.length()!=spec.size)throw new IOException("Update size does not match");MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream input=new FileInputStream(file)){byte[] buffer=new byte[32768];for(int read;(read=input.read(buffer))!=-1;){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Update verification cancelled");digest.update(buffer,0,read);}}if(!hex(digest.digest()).equals(spec.sha256))throw new IOException("Update hash does not match");}
    private static Set<String> signers(PackageInfo info)throws Exception {
        Signature[] signatures=Build.VERSION.SDK_INT>=28?(info.signingInfo==null?null:info.signingInfo.getApkContentsSigners()):info.signatures;
        Set<String> result=new HashSet<>();if(signatures!=null)for(Signature signature:signatures)result.add(hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())));return result;
    }
    static void verifyArchive(Context context,File file,Spec spec)throws Exception {
        PackageManager manager=context.getPackageManager();int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        PackageInfo installed=manager.getPackageInfo(context.getPackageName(),flags),archive=manager.getPackageArchiveInfo(file.getAbsolutePath(),flags);
        if(archive==null)throw new IOException("Update package cannot be read");long installedCode=Build.VERSION.SDK_INT>=28?installed.getLongVersionCode():installed.versionCode,archiveCode=Build.VERSION.SDK_INT>=28?archive.getLongVersionCode():archive.versionCode;
        if(!Updates.newer(spec.version,installed.versionName)||!UpdateDownloadPolicy.identity(context.getPackageName(),archive.packageName,spec.version,archive.versionName,installedCode,archiveCode,signers(installed),signers(archive)))throw new IOException("Update identity does not match this app");
    }
    static Intent installIntent(Context context,File file){Uri uri=FileProvider.getUriForFile(context,context.getPackageName()+".updates",file);Intent intent=new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri("MovieBox update",uri));return intent;}
    private static void launchInstaller(Activity activity,File file,Spec spec)throws Exception {
        if(Build.VERSION.SDK_INT>=26&&!activity.getPackageManager().canRequestPackageInstalls()){
            new AlertDialog.Builder(activity).setTitle("Allow MovieBox updates").setMessage("Android needs your permission to install updates from MovieBox. Return here afterward and tap Install update.").setNegativeButton("Not now",null).setPositiveButton("Open settings",(dialog,which)->{try{activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())));}catch(ActivityNotFoundException unavailable){Toast.makeText(activity,"Open Android settings to allow updates from MovieBox.",Toast.LENGTH_LONG).show();}}).show();return;
        }
        activity.startActivity(installIntent(activity,file));
    }
    private static final Map<Activity,Dialog> DIALOGS=new WeakHashMap<>();
    static void show(Activity activity,JSONObject metadata){show(activity,metadata,new Engine(activity),UpdateDownload::launchInstaller);}
    static void showAndStart(Activity activity,JSONObject metadata){showAndStart(activity,metadata,new Engine(activity),UpdateDownload::launchInstaller);}
    static Dialog showAndStart(Activity activity,JSONObject metadata,Engine engine,Installer installer){
        Dialog dialog=show(activity,metadata,engine,installer);if(dialog instanceof AlertDialog){Button action=((AlertDialog)dialog).getButton(AlertDialog.BUTTON_POSITIVE);if(action!=null&&"Download update".equals(action.getContentDescription()))action.performClick();}return dialog;
    }
    static Dialog show(Activity activity,JSONObject metadata,Engine engine,Installer installer){
        Dialog existing=DIALOGS.get(activity);if(existing!=null&&existing.isShowing())return existing;
        final Spec spec;try{spec=new Spec(metadata);}catch(Exception invalid){return new AlertDialog.Builder(activity).setTitle("Update unavailable").setMessage("Update details could not be verified. Check for updates again.").setPositiveButton("Close",null).show();}
        int padding=Math.round(20*activity.getResources().getDisplayMetrics().density);LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(padding,padding,padding,padding);
        TextView heading=new TextView(activity);heading.setText("MovieBox "+spec.version);heading.setTextSize(20);heading.setTextColor(0xfff3f4f6);heading.setTypeface(null,android.graphics.Typeface.BOLD);body.addView(heading);
        TextView status=new TextView(activity);status.setTextColor(0xffaeb4c0);status.setPadding(0,padding/2,0,0);status.setText("Download "+String.format(Locale.getDefault(),"%.1f MB",spec.size/1048576d));status.setContentDescription("Update download status");status.setTextSize(15);body.addView(status);
        ProgressBar progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setVisibility(View.GONE);progress.setContentDescription("Update download progress");LinearLayout.LayoutParams progressLayout=new LinearLayout.LayoutParams(-1,Math.round(12*activity.getResources().getDisplayMetrics().density));progressLayout.topMargin=padding;body.addView(progress,progressLayout);
        AlertDialog dialog=new AlertDialog.Builder(activity).setView(body).setNegativeButton("Cancel",null).setPositiveButton("Update",null).create();DIALOGS.put(activity,dialog);
        Handler ui=new Handler(Looper.getMainLooper());final Job[] job={null};final File[] downloaded={null};final boolean[] active={false};
        Application.ActivityLifecycleCallbacks lifecycle=new Application.ActivityLifecycleCallbacks(){public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}public void onActivityResumed(Activity a){}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){if(a==activity){if(job[0]!=null)job[0].cancel();dialog.dismiss();}}};
        activity.getApplication().registerActivityLifecycleCallbacks(lifecycle);
        dialog.setOnDismissListener(ignored->{if(job[0]!=null)job[0].cancel();DIALOGS.remove(activity);activity.getApplication().unregisterActivityLifecycleCallbacks(lifecycle);});
        dialog.show();android.graphics.drawable.GradientDrawable panel=new android.graphics.drawable.GradientDrawable();panel.setColor(0xff191c23);panel.setCornerRadius(padding);panel.setStroke(Math.max(1,padding/20),0xff343a46);dialog.getWindow().setBackgroundDrawable(panel);dialog.getWindow().setDimAmount(.35f);dialog.getWindow().setLayout(Math.min(activity.getResources().getDisplayMetrics().widthPixels-padding*2,Math.round(380*activity.getResources().getDisplayMetrics().density)),WindowManager.LayoutParams.WRAP_CONTENT);int parentId=activity.getResources().getIdentifier("parentPanel","id","android");View parentPanel=dialog.findViewById(parentId);if(parentPanel!=null)parentPanel.setBackgroundColor(Color.TRANSPARENT);int buttonId=activity.getResources().getIdentifier("buttonPanel","id","android");View buttonPanel=dialog.findViewById(buttonId);if(buttonPanel!=null)buttonPanel.setBackgroundColor(Color.TRANSPARENT);Button action=dialog.getButton(AlertDialog.BUTTON_POSITIVE),cancel=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);action.setContentDescription("Download update");cancel.setContentDescription("Close update dialog");for(Button button:new Button[]{action,cancel}){button.setAllCaps(false);button.setMinHeight(Math.round(48*activity.getResources().getDisplayMetrics().density));button.setTextColor(button==action?0xff42e4b0:0xffb3bac6);}progress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xff42e4b0));
            action.setOnClickListener(v->{if(active[0])return;if(downloaded[0]!=null){
                active[0]=true;action.setEnabled(false);status.setText("Preparing installer...");
                Job verification=new Job();job[0]=verification;File file=downloaded[0];
                try{engine.worker.execute(()->{verification.thread=Thread.currentThread();boolean verified=false;try{verification.check();verifyBytes(file,spec);engine.verifier.verify(engine.context,file,spec);verification.check();verified=true;}catch(Exception invalid){}finally{verification.thread=null;}final boolean valid=verified;ui.post(()->{if(!dialog.isShowing()||activity.isFinishing()||activity.isDestroyed()||verification.cancelled)return;active[0]=false;action.setEnabled(true);if(!valid){downloaded[0]=null;status.setText("This download could not be verified. Download it again.");action.setVisibility(View.VISIBLE);action.setText("Retry");action.setContentDescription("Retry update download");return;}try{installer.open(activity,file,spec);status.setText("Android will ask you to confirm installation.");}catch(Exception failure){status.setText("Could not open the installer. Check for updates and try again.");}});});}catch(RejectedExecutionException unavailable){active[0]=false;action.setEnabled(true);status.setText("Could not prepare the installer. Try again.");}return;}
                active[0]=true;action.setEnabled(false);action.setVisibility(View.GONE);status.setText("Downloading update - 0%");progress.setProgress(0);progress.setVisibility(View.VISIBLE);cancel.setText("Cancel");cancel.setContentDescription("Cancel update download");
                job[0]=engine.start(spec,new Listener(){
                    void post(Runnable update){ui.post(()->{if(dialog.isShowing()&&!activity.isFinishing()&&!activity.isDestroyed())update.run();});}
                    public void progress(long bytes,long total){post(()->{int value=(int)Math.min(100,bytes*100/total);progress.setProgress(value);status.setText("Downloading update - "+value+"%\n"+String.format(Locale.getDefault(),"%.1f / %.1f MB",bytes/1048576d,total/1048576d));});}
                    public void ready(File file){post(()->{active[0]=false;downloaded[0]=file;progress.setProgress(100);status.setText("Ready to install");action.setVisibility(View.VISIBLE);action.setText("Install");action.setContentDescription("Install update");action.setEnabled(true);cancel.setText("Later");cancel.setContentDescription("Close update dialog");});}
                    public void failed(){post(()->{active[0]=false;progress.setVisibility(View.GONE);status.setText("Couldn't download or verify this update. Check your connection and try again.");action.setVisibility(View.VISIBLE);action.setText("Retry");action.setContentDescription("Retry update download");action.setEnabled(true);cancel.setText("Close");cancel.setContentDescription("Close update dialog");});}
                    public void cancelled(){post(()->{active[0]=false;progress.setVisibility(View.GONE);status.setText("Download cancelled.");action.setVisibility(View.VISIBLE);action.setEnabled(true);});}
                });
            });
        return dialog;
    }
}
