package org.emransm.moviebox.phone;

import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

/** Real enlarged-font layout, isolated catalogue data; no user settings retained. */
@RunWith(AndroidJUnit4.class)
public class BrowsingAccessibilityScenarios {
 @Test public void homeSearchLabelWrapsWithoutClippingAt130And200Percent()throws Exception{
  String original=PlayerAccessibilityScenarios.shell("settings get system font_scale");
  try{for(String scale:new String[]{"1.3","2.0"}){
   PlayerAccessibilityScenarios.setting("system","font_scale",scale);Thread.sleep(800);
   try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
    BlockFlowScenarios.isolate(scenario,new BlockFlowScenarios.FakeApi());
    main(scenario,a->call(a,"browse",new Class[]{String.class},"Discover"));
    Thread.sleep(400);
    main(scenario,a->{
     assertEquals(Float.parseFloat(scale),a.getResources().getConfiguration().fontScale,.01f);
     TextView search=(TextView)description((View)field(a,"root"),"Search catalogue");
     assertNotNull(search);assertNotNull(search.getLayout());
     assertEquals("Complete search label is laid out",search.length(),search.getLayout().getLineEnd(search.getLineCount()-1));
     for(int i=0;i<search.getLineCount();i++)assertEquals(0,search.getLayout().getEllipsisCount(i));
     assertTrue("Wrapped label fits vertically",search.getHeight()-search.getCompoundPaddingTop()-search.getCompoundPaddingBottom()>=search.getLayout().getHeight());
     assertTrue("Accessible target stays at least 48dp",search.getHeight()>=47.9f*a.getResources().getDisplayMetrics().density);
    });
    PlayerAccessibilityScenarios.capture("batch6-home-font-"+scale+".png");
   }
  }}finally{PlayerAccessibilityScenarios.setting("system","font_scale",original);}
 }
}
