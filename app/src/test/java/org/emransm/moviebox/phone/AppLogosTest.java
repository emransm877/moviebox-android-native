package org.emransm.moviebox.phone;

import org.junit.Test;
import java.util.HashSet;
import static org.junit.Assert.*;

public class AppLogosTest {
    @Test public void allElevenChoicesHaveStableUniqueIdentities(){
        assertEquals(11,AppLogos.IDS.length);assertEquals(11,AppLogos.NAMES.length);
        HashSet<String> ids=new HashSet<>(),aliases=new HashSet<>();
        for(int n=0;n<AppLogos.IDS.length;n++){assertTrue(ids.add(AppLogos.IDS[n]));assertTrue(aliases.add(AppLogos.alias(n)));assertEquals(n,AppLogos.index(AppLogos.IDS[n]));}
        assertEquals("org.emransm.moviebox.phone.LauncherOriginal",AppLogos.alias(0));
        assertEquals("org.emransm.moviebox.phone.Launcher10",AppLogos.alias(10));
    }
    @Test public void unknownAndMissingPreferencesFallBackToOriginal(){
        assertEquals(AppLogos.ORIGINAL,AppLogos.normalized(null));
        assertEquals(AppLogos.ORIGINAL,AppLogos.normalized(""));
        assertEquals(AppLogos.ORIGINAL,AppLogos.normalized("retired-icon"));
        assertEquals("prism-play",AppLogos.normalized("prism-play"));
    }
}
