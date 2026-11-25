package org.emransm.moviebox.phone;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.test.runner.AndroidJUnitRunner;
import java.io.*;
import java.util.*;

/** Test-only isolation: automatic release checks never consume public API quota. */
public final class OwnedQaRunner extends AndroidJUnitRunner {
    private File backup;
    private SharedPreferences preferences;
    @Override public void onStart() {
        Context context=getTargetContext();
        preferences=context.getSharedPreferences("moviebox-optional-updates",0);
        backup=new File(context.getCacheDir(),"qa-owned-update-preferences.bin");
        restore(); // Recover the exact prior state after an interrupted QA run.
        Map<String,Object> saved=new HashMap<>();
        for(Map.Entry<String,?> entry:preferences.getAll().entrySet()) {
            Object value=entry.getValue();
            saved.put(entry.getKey(),value instanceof Set?new HashSet<>((Set<?>)value):value);
        }
        try(ObjectOutputStream output=new ObjectOutputStream(new FileOutputStream(backup))){output.writeObject(saved);}
        catch(IOException failed){throw new IllegalStateException("QA preference snapshot failed",failed);}
        super.onStart();
    }
    @Override public void callActivityOnCreate(Activity activity,Bundle state) {
        if(preferences==null)throw new IllegalStateException("QA network isolation was not initialized");
        // Every Activity launch is isolated, even if a settings scenario toggled reminders.
        preferences.edit().putBoolean("enabled",false).commit();
        super.callActivityOnCreate(activity,state);
    }
    @Override public void finish(int resultCode,Bundle results) {restore();super.finish(resultCode,results);}
    @SuppressWarnings("unchecked") private void restore() {
        if(backup==null||preferences==null||!backup.isFile())return;
        try(ObjectInputStream input=new ObjectInputStream(new FileInputStream(backup))) {
            Map<String,Object> saved=(Map<String,Object>)input.readObject();
            SharedPreferences.Editor edit=preferences.edit().clear();
            for(Map.Entry<String,Object> entry:saved.entrySet()) {
                String key=entry.getKey();Object value=entry.getValue();
                if(value instanceof String)edit.putString(key,(String)value);
                else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);
                else if(value instanceof Integer)edit.putInt(key,(Integer)value);
                else if(value instanceof Long)edit.putLong(key,(Long)value);
                else if(value instanceof Float)edit.putFloat(key,(Float)value);
                else if(value instanceof Set)edit.putStringSet(key,new HashSet<>((Set<String>)value));
                else throw new IOException("Unsupported QA preference type");
            }
            if(!edit.commit())throw new IOException("QA preference restore failed");
        }catch(Exception failed){throw new IllegalStateException("QA preference restore failed",failed);}
        if(!backup.delete())throw new IllegalStateException("QA preference snapshot cleanup failed");
    }
}
