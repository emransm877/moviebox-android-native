package org.emransm.moviebox.phone;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.*;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;
@RunWith(AndroidJUnit4.class)
public class CatalogueReadinessScenarios {
 @Test public void automaticPaginationAppendsOnceAndStopsDuplicatePages()throws Exception {
  Api service=new Api("generated-readiness-identity-000000"){
   @Override public List<JSONObject> catalogue(int type,int page,JSONObject filters)throws Exception {
    List<JSONObject> out=new ArrayList<>();int start=page==1?0:20;
    for(int n=start;n<start+20;n++)out.add(new JSONObject().put("subjectId","qa-page-"+n).put("title","Generated title "+n).put("subjectType",type));return out;
   }
  };
  try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
   main(scenario,a->{put(a,"api",service);call(a,"browse",new Class[]{String.class},"Movies");});
   waitFor(()->count(scenario)==20,5000);
   main(scenario,a->((RecyclerView)field(a,"grid")).scrollToPosition(19));Thread.sleep(350);
   main(scenario,a->{call(a,"maybeLoadCataloguePage",new Class[]{});call(a,"maybeLoadCataloguePage",new Class[]{});});
   waitFor(()->count(scenario)==40,5000);
   main(scenario,a->((RecyclerView)field(a,"grid")).scrollToPosition(39));Thread.sleep(350);
   main(scenario,a->call(a,"maybeLoadCataloguePage",new Class[]{}));
   waitFor(()->{boolean[] done={false};main(scenario,a->done[0]=!(Boolean)field(a,"catalogueHasMore"));return done[0];},5000);
   assertEquals(40,count(scenario));
  }
 }
 private static int count(ActivityScenario<PhoneActivity> scenario)throws Exception{int[] value={0};main(scenario,a->{RecyclerView grid=(RecyclerView)field(a,"grid");if(grid!=null&&grid.getAdapter()!=null)value[0]=grid.getAdapter().getItemCount();});return value[0];}
}
