package org.emransm.moviebox.phone;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;
import org.json.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.*;


/** User-selected Android document providers only. No network, automatic uploads or broad storage permissions. */
final class LibraryTransfer {
    static final int EXPORT=8091,IMPORT=8092,DAMAGED=8093;
    interface Current {Library get();}
    private final Activity host;private final Current current;private final Runnable changed;
    private final Set<Dialog> dialogs=new HashSet<>();
    private final ExecutorService files=Executors.newSingleThreadExecutor();private final Handler ui=new Handler(Looper.getMainLooper());
    LibraryTransfer(Activity host,Current current,Runnable changed){this.host=host;this.current=current;this.changed=changed;}
    void close(){for(Dialog dialog:new ArrayList<>(dialogs))dialog.dismiss();dialogs.clear();files.shutdown();}
    private void show(AlertDialog dialog){dialog.setOnDismissListener(value->dialogs.remove(dialog));dialogs.add(dialog);dialog.show();}
    private boolean alive(){return !host.isFinishing()&&!host.isDestroyed();}
    private void message(String text){if(alive())Toast.makeText(host,text,Toast.LENGTH_LONG).show();}
    void export(boolean damaged){
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(damaged?"application/zip":"application/json").putExtra(Intent.EXTRA_TITLE,damaged?"MovieBox-damaged-saved-data.zip":"MovieBox-library-backup.json");
        try{host.startActivityForResult(intent,damaged?DAMAGED:EXPORT);}catch(ActivityNotFoundException unavailable){message("No file picker is available on this phone.");}
    }
    void restore(){try{host.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/json","text/plain","application/octet-stream"}),IMPORT);}catch(ActivityNotFoundException unavailable){message("No file picker is available on this phone.");}}
    interface Task {String run()throws Exception;}
    private void execute(String title,Task task){execute(title,task,null);}
    private void execute(String title,Task task,Runnable success){
        AlertDialog progress=new AlertDialog.Builder(host).setTitle(title).setMessage("Please wait a moment.").setCancelable(false).create();show(progress);
        try{files.execute(()->{try{String result=task.run();ui.post(()->{if(alive()){progress.dismiss();if(result!=null)message(result);if(success!=null)success.run();}});}catch(Exception error){ui.post(()->{if(alive()){progress.dismiss();message(error.getMessage()==null?"That could not finish. Your saved data has been kept.":error.getMessage());}});}});}catch(RejectedExecutionException stopped){progress.dismiss();message("Please reopen the app and try again.");}
    }
    static byte[] readBackup(InputStream input)throws IOException{
        if(input==null)throw new IOException("The selected backup could not be opened.");try(InputStream in=input;ByteArrayOutputStream bytes=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>2*1024*1024)throw new IOException("This backup is too large. Choose a MovieBox library backup under 2 MB.");bytes.write(buffer,0,n);}return bytes.toByteArray();}
    }
    void result(int request,int result,Intent intent){
        if(request!=EXPORT&&request!=IMPORT&&request!=DAMAGED)return;if(result!=Activity.RESULT_OK)return;
        Uri uri=intent==null?null:intent.getData();if(uri==null||!"content".equals(uri.getScheme())){message("Choose a file through the Android file picker.");return;}
        if(request==IMPORT){
            Library[] original={null};JSONObject[] parsed={null};LibraryBackup.MergeResult[] preview={null};String[] expected={null};
            execute("Reading backup",()->{parsed[0]=LibraryBackup.parse(readBackup(host.getContentResolver().openInputStream(uri)));original[0]=current.get();JSONObject snapshot=original[0]==null?new JSONObject().put("list",new JSONObject()).put("history",new JSONObject()):original[0].snapshot();preview[0]=LibraryBackup.merge(snapshot,parsed[0]);expected[0]=snapshot.toString();return null;},()->confirmRestore(original[0],parsed[0],preview[0],expected[0]));return;
        }
        execute("Saving backup",()->{
            Library library=current.get();byte[] bytes;int omitted=0;if(request==DAMAGED)bytes=Library.damagedArchive(host);else{if(library==null)throw new IOException("Saved data is unavailable. Use Save damaged data instead.");LibraryBackup.ExportResult exported=LibraryBackup.exportDetails(library.snapshot());bytes=exported.bytes;omitted=exported.excludedOfflineRecords;}
            try(OutputStream output=host.getContentResolver().openOutputStream(uri,"wt")){if(output==null)throw new IOException("The selected file could not be opened.");output.write(bytes);output.flush();}return request==DAMAGED?"Damaged data saved. Keep this private file safe.":"Library backup saved. Downloaded videos are not included."+(omitted>0?" "+omitted+" local-only entries stay on this phone.":"");
        });
    }
    private void confirmRestore(Library original,JSONObject parsed,LibraryBackup.MergeResult preview,String expected){
        String description=original==null?"Restore this backup into a new library. The damaged files will be preserved privately on this phone.":"Merge this backup with your saved library. Existing My list titles stay saved; newer watch progress wins.";
        description+="\n\n"+preview.addedTitles+" titles added and "+preview.importedHistory+" watch-history entries updated.";
        if(preview.evictedExistingHistory>0)description+="\nThis will remove "+preview.evictedExistingHistory+" oldest history entries to keep the 300-entry limit. My list is kept.";
        show(new AlertDialog.Builder(host).setTitle("Restore library backup?").setMessage(description).setNegativeButton("Cancel",null).setPositiveButton("Restore",(dialog,which)->execute("Restoring library",()->{if(original==null)Library.recoverAfterConfirmation(host,parsed);else{if(current.get()!=original)throw new IOException("Your library changed. Choose the backup again.");original.commitRestored(preview.library,expected);}return "Library restored. Your saved titles have been kept.";},changed)).create());
    }
    void confirmReset(){show(new AlertDialog.Builder(host).setTitle("Start a new library?").setMessage("Your unreadable saved files will first be copied into private recovery storage on this phone. This starts an empty My list and watch history. You can restore a backup afterward.").setNegativeButton("Cancel",null).setPositiveButton("Preserve files and start new",(dialog,which)->execute("Preserving saved data",()->{Library.recoverAfterConfirmation(host,null);return "Original files preserved. A new library is ready.";},changed)).create());}
}
