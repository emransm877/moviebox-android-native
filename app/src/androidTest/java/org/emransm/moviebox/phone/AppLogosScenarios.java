package org.emransm.moviebox.phone;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Changes only the app's own alias preference, restores the prior selected icon in finally. */
@RunWith(AndroidJUnit4.class)
public class AppLogosScenarios {
    private boolean active(Context context,int n){
        int state=context.getPackageManager().getComponentEnabledSetting(new ComponentName(context.getPackageName(),AppLogos.alias(n)));
        return state==PackageManager.COMPONENT_ENABLED_STATE_ENABLED||(state==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT&&n==0);
    }
    @Test public void eachIconIsPackagedAndOnlySelectedLauncherRemainsEnabled()throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();String prior=AppLogos.current(context);
        try{
            for(int n=0;n<AppLogos.IDS.length;n++){
                AppLogos.select(context,AppLogos.IDS[n]);assertEquals(AppLogos.IDS[n],AppLogos.current(context));
                for(int other=0;other<AppLogos.IDS.length;other++)assertEquals("Exactly one reachable launcher",other==n,active(context,other));
                ActivityInfo info=context.getPackageManager().getActivityInfo(new ComponentName(context.getPackageName(),AppLogos.alias(n)),PackageManager.GET_DISABLED_COMPONENTS);
                assertEquals("MovieBox",info.loadLabel(context.getPackageManager()).toString());assertNotNull(info.loadIcon(context.getPackageManager()));
                assertEquals(context.getPackageName()+".PhoneActivity",info.targetActivity);
                AppLogos.restore(context);assertEquals(AppLogos.IDS[n],AppLogos.current(context));assertTrue(active(context,n));
            }
            AppLogos.select(context,"unknown-icon");assertEquals(AppLogos.ORIGINAL,AppLogos.current(context));assertTrue(active(context,0));
        }finally{AppLogos.select(context,prior);}
    }
}
