package org.emransm.moviebox.phone;
import android.content.*;import android.database.*;import android.net.Uri;import android.os.ParcelFileDescriptor;import java.io.*;
/** Instrumentation APK only. Cannot access the application's real library. */
public class BackupTestProvider extends ContentProvider {
 private File file(Uri uri)throws FileNotFoundException{String name=uri.getLastPathSegment();if(name==null||!name.matches("owned-[a-zA-Z0-9-]+\\.json"))throw new FileNotFoundException();return new File(getContext().getCacheDir(),name);}
 public boolean onCreate(){return true;}public String getType(Uri uri){return "application/json";}public Cursor query(Uri uri,String[] p,String s,String[] a,String sort){MatrixCursor c=new MatrixCursor(new String[]{"_display_name","_size"});try{File f=file(uri);c.addRow(new Object[]{f.getName(),f.length()});}catch(Exception ignored){}return c;}
 public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.parseMode(mode));}
 public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}public int update(Uri uri,ContentValues values,String s,String[] a){return 0;}public int delete(Uri uri,String s,String[] a){try{return file(uri).delete()?1:0;}catch(Exception e){return 0;}}
}