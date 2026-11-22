package org.emransm.moviebox.phone;

import android.content.Context;
import android.content.ContextWrapper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Isolated on-device data files; never reads or modifies the user's real library. */
@RunWith(AndroidJUnit4.class)
public class DataReadinessScenarios {
    private static final class IsolatedContext extends ContextWrapper {
        final File directory;
        IsolatedContext() {super(InstrumentationRegistry.getInstrumentation().getTargetContext());directory=new File(super.getCacheDir(),"qa-data-readiness-"+UUID.randomUUID());assertTrue(directory.mkdirs());}
        @Override public File getFilesDir(){return directory;}
        void cleanup(){File[] children=directory.listFiles();if(children!=null)for(File child:children)assertTrue(child.delete());assertTrue(directory.delete());}
    }
    private static JSONObject disk(File file)throws Exception {try(FileInputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] block=new byte[1024];int n;while((n=in.read(block))!=-1)out.write(block,0,n);return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));}}
    @Test public void listAndResumeReachDiskAndRecreateWithoutDroppingOtherEntries()throws Exception {
        IsolatedContext context=new IsolatedContext();Library library=null;
        try {
            library=new Library(context);
            JSONObject first=new JSONObject().put("subjectId","qa-data-first").put("title","Generated \u09ac\u09be\u0982\u09b2\u09be title").put("subjectType",2);
            JSONObject second=new JSONObject().put("subjectId","qa-data-second").put("title","Generated second title").put("subjectType",1);
            library.toggle(first);library.toggle(second);library.progress(first,2,4,12500,60000,false);library.close();library=null;
            File file=new File(context.directory,"library.json");JSONObject saved=null;long deadline=System.currentTimeMillis()+5000;
            while(System.currentTimeMillis()<deadline){try{saved=disk(file);if(saved.getJSONObject("list").length()==2&&saved.getJSONObject("history").has("qa-data-first:2:4"))break;}catch(Exception ignored){}Thread.sleep(30);}
            assertNotNull("Atomic library bytes are durably readable",saved);assertEquals(2,saved.getJSONObject("list").length());assertEquals(12500,saved.getJSONObject("history").getJSONObject("qa-data-first:2:4").getLong("position"));
            library=new Library(context);assertTrue(library.contains(first));assertTrue(library.contains(second));assertEquals(12500,library.position(first,2,4));
            library.removeTitles("list",Collections.singleton("qa-data-first"));assertFalse(library.contains(first));assertTrue(library.contains(second));
            library.close();library=null;deadline=System.currentTimeMillis()+5000;while(System.currentTimeMillis()<deadline){try{if(!disk(file).getJSONObject("list").has("qa-data-first"))break;}catch(Exception ignored){}Thread.sleep(30);}assertFalse("Single removal reaches disk without deleting second entry",disk(file).getJSONObject("list").has("qa-data-first"));assertTrue(disk(file).getJSONObject("list").has("qa-data-second"));
        } finally {if(library!=null)library.close();Thread.sleep(300);context.cleanup();}
    }
    @Test public void writeFailureIsReportedInsteadOfSilentlyClaimingPersistence()throws Exception {
        IsolatedContext context=new IsolatedContext();Library library=null;
        try {
            File blocked=new File(context.directory,"blocked-parent");try(FileOutputStream out=new FileOutputStream(blocked)){out.write(1);}
            Context badRoot=new ContextWrapper(context){@Override public File getFilesDir(){return blocked;}};
            library=new Library(badRoot);CountDownLatch reported=new CountDownLatch(1);
            library.setSaveFailureListener(error->reported.countDown());library.save();
            assertTrue("Failed durable write must reach failure listener",reported.await(5,TimeUnit.SECONDS));
            assertFalse("Blocked storage cannot be represented as persisted library",new File(blocked,"library.json").exists());
        } finally {if(library!=null)library.close();Thread.sleep(200);context.cleanup();}
    }
    @Test public void corruptedLibraryIsPreservedInsteadOfSilentlyReplaced()throws Exception {
        IsolatedContext context=new IsolatedContext();
        try {
            File target=new File(context.directory,"library.json");byte[] bytes="{generated-corrupt-qa-fixture".getBytes(StandardCharsets.UTF_8);
            try(FileOutputStream out=new FileOutputStream(target)){out.write(bytes);}
            try{new Library(context);fail("Existing corrupt library must report load failure");}catch(JSONException expected){assertTrue(expected.getMessage().contains("kept"));}
            try(FileInputStream in=new FileInputStream(target)){byte[] found=new byte[bytes.length];assertEquals(bytes.length,in.read(found));assertArrayEquals("Original bytes must be left recoverable",bytes,found);}
        } finally {context.cleanup();}
    }
}
