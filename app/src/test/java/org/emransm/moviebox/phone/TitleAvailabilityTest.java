package org.emransm.moviebox.phone;

import org.junit.Test;
import static org.junit.Assert.*;

public class TitleAvailabilityTest {
    @Test public void onlyActualBooleanFalseSupportsNegativeEvidence(){
        assertTrue(TitleAvailability.supportsComingSoon("movie",1,Boolean.FALSE,true,true,false));
        for(Object value:new Object[]{null,Boolean.TRUE,"false","0",0,1})assertFalse(TitleAvailability.supportsComingSoon("movie",1,value,true,true,false));
    }
    @Test public void alternateOrUnverifiedSourceContextIsUnknown(){
        assertFalse(TitleAvailability.supportsComingSoon("movie",1,Boolean.FALSE,false,true,false));
        assertFalse(TitleAvailability.supportsComingSoon("movie",1,Boolean.FALSE,true,false,false));
        assertFalse(TitleAvailability.supportsComingSoon("movie",1,Boolean.FALSE,true,true,true));
    }
    @Test public void onlyVerifiedMovieScopeAndCatalogueIdentityAreAccepted(){
        for(String id:new String[]{null,""," ","0","offline:clip"})assertFalse(TitleAvailability.supportsComingSoon(id,1,Boolean.FALSE,true,true,false));
        for(int type:new int[]{0,2,3})assertFalse(TitleAvailability.supportsComingSoon("movie",type,Boolean.FALSE,true,true,false));
    }
}
