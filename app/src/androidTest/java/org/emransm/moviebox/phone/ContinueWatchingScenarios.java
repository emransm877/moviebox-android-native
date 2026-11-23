package org.emransm.moviebox.phone;

import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.util.*;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

/** Resume presentation is derived from owned history; completed history is never deleted. */
@RunWith(AndroidJUnit4.class)
public class ContinueWatchingScenarios {
    static Library owned()throws Exception{Context base=InstrumentationRegistry.getInstrumentation().getTargetContext();File directory=new File(base.getCacheDir(),"qa-resume-view-"+UUID.randomUUID());assertTrue(directory.mkdirs());return new Library(new ContextWrapper(base){@Override public File getFilesDir(){return directory;}});}
    static JSONObject row(String id,int type,int season,int episode,long position,long duration,boolean complete,long updated)throws Exception{return new JSONObject().put("subject",BlockFlowScenarios.title(id,type)).put("season",season).put("episode",episode).put("position",position).put("duration",duration).put("completed",complete).put("updated",updated);}
    static void putRow(Library library,JSONObject row)throws Exception{library.data.getJSONObject("history").put(Library.key(row.getJSONObject("subject"),row.optInt("season"),row.optInt("episode")),row);}
    @Test public void completedMoviesRemainInHistoryButNotContinue()throws Exception{
        Library library=owned();try{putRow(library,row("finished",1,0,0,60000,60000,true,30));putRow(library,row("unfinished",1,0,0,12000,60000,false,20));String before=library.snapshot().toString();
            List<Library.ResumeEntry> resume=library.resumeEntries(20);assertEquals(1,resume.size());assertEquals("unfinished",resume.get(0).subject.optString("subjectId"));assertEquals(12000,resume.get(0).position);assertEquals(2,library.listing("history").size());assertEquals(before,library.snapshot().toString());
            assertEquals("Finished titles deliberately restart if opened from full History",0,library.position(BlockFlowScenarios.title("finished",1),0,0));
        }finally{library.close();}
    }
    @Test public void latestSeriesRecordOwnsContinueAndNewerCompletionDoesNotReviveOldEpisode()throws Exception{
        Library library=owned();try{putRow(library,row("series",2,1,1,9000,60000,false,10));putRow(library,row("series",2,2,4,17000,60000,false,20));List<Library.ResumeEntry> resume=library.resumeEntries(20);assertEquals(1,resume.size());assertEquals(2,resume.get(0).season);assertEquals(4,resume.get(0).episode);
            putRow(library,row("series",2,2,5,60000,60000,true,30));assertTrue(library.resumeEntries(20).isEmpty());assertEquals(3,library.data.getJSONObject("history").length());assertEquals(1,library.listing("history").size());
            putRow(library,row("series",2,3,1,4000,60000,false,40));resume=library.resumeEntries(20);assertEquals(1,resume.size());assertEquals(3,resume.get(0).season);assertEquals(1,resume.get(0).episode);
        }finally{library.close();}
    }
    @Test public void zeroInvalidAndAtEndPositionsAreExcludedButUnknownDurationIsAllowed()throws Exception{
        Library library=owned();try{putRow(library,row("zero",1,0,0,0,60000,false,1));putRow(library,row("negative",1,0,0,-900,60000,false,2));putRow(library,row("too-short",1,0,0,999,60000,false,3));putRow(library,row("at-end",1,0,0,60000,60000,false,4));putRow(library,row("past-end",1,0,0,65000,60000,false,5));putRow(library,row("unknown-duration",1,0,0,1000,-1,false,6));putRow(library,row("invalid-series",2,0,0,3000,60000,false,7));putRow(library,row("invalid-movie",1,1,1,3000,60000,false,8));putRow(library,row("invalid-type",9,0,0,3000,60000,false,9));
            List<Library.ResumeEntry> resume=library.resumeEntries(20);assertEquals(1,resume.size());assertEquals("unknown-duration",resume.get(0).subject.optString("subjectId"));assertEquals(9,library.data.getJSONObject("history").length());
        }finally{library.close();}
    }
    @Test public void invalidNewestRecordDoesNotResurrectAnOlderSeriesEpisode()throws Exception{
        Library library=owned();try{putRow(library,row("series",2,1,1,12000,60000,false,10));putRow(library,row("series",2,1,2,0,60000,false,20));assertTrue(library.resumeEntries(20).isEmpty());assertEquals(2,library.data.getJSONObject("history").length());}finally{library.close();}
    }
    @Test public void limitsOrderAndTiesAreDeterministicWithoutMutatingStorage()throws Exception{
        Library library=owned();try{for(int n=0;n<305;n++)putRow(library,row("movie-"+n,1,0,0,2000,60000,false,n));String before=library.snapshot().toString();assertTrue(library.resumeEntries(-1).isEmpty());assertTrue(library.resumeEntries(0).isEmpty());assertEquals(1,library.resumeEntries(1).size());assertEquals("movie-304",library.resumeEntries(1).get(0).subject.optString("subjectId"));assertEquals(300,library.resumeEntries(Integer.MAX_VALUE).size());assertEquals(before,library.snapshot().toString());
            library.data.put("history",new JSONObject());putRow(library,row("tie",2,1,4,3000,60000,false,100));putRow(library,row("tie",2,2,1,4000,60000,false,100));assertEquals(2,library.resumeEntries(20).get(0).season);
        }finally{library.close();}
    }
    @Test public void resumeViewCopiesDoNotChangeMyListHistoryOrSettings()throws Exception{
        Library library=owned();try{JSONObject title=BlockFlowScenarios.title("copied",1);library.data.getJSONObject("list").put("copied",title);library.settings().put("subtitleSize",28);putRow(library,row("copied",1,0,0,5000,60000,false,1));String before=library.snapshot().toString();Library.ResumeEntry entry=library.resumeEntries(20).get(0);entry.subject.put("title","Changed view only");assertEquals(before,library.snapshot().toString());assertEquals("Owned block copied",library.resumeEntries(20).get(0).subject.optString("title"));}finally{library.close();}
    }
    @Test public void homeContinueExcludesCompletedAndFullHistoryKeepsBothTitles()throws Exception{
        BlockFlowScenarios.FakeApi api=new BlockFlowScenarios.FakeApi();try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(scenario,api);main(scenario,a->{Library library=(Library)field(a,"library");putRow(library,row("finished-home",1,0,0,60000,60000,true,20));putRow(library,row("resume-home",1,0,0,9000,60000,false,10));String before=library.snapshot().toString();put(a,"route","Discover");call(a,"appShell",new Class[]{String.class},"Discover");call(a,"mobileHome",new Class[]{List.class},Collections.singletonList(BlockFlowScenarios.title("catalogue-only",1)));View root=(View)field(a,"root");assertTrue(BlockFlowScenarios.hasText(root,"Continue watching"));assertNotNull(description(root,"Owned block resume-home"));assertNull(description(root,"Owned block finished-home"));assertEquals(before,library.snapshot().toString());
                call(a,"browse",new Class[]{String.class},"History");assertEquals("History",((android.widget.TextView)field(a,"titleLabel")).getText().toString());assertEquals(2,library.listing("history").size());assertEquals(2,((androidx.recyclerview.widget.RecyclerView)field(a,"grid")).getAdapter().getItemCount());});
        }finally{api.close();}
    }
}
