package org.emransm.moviebox.phone;
import org.junit.Test;
import static org.junit.Assert.*;
public class UpdatesTest {
 @Test public void semanticVersionsCompareNumerically(){assertTrue(Updates.newer("v0.10.0","0.9.9"));assertTrue(Updates.newer("1.0.0","0.99.99"));assertFalse(Updates.newer("0.1.0","0.1.0"));assertFalse(Updates.newer("0.0.9","0.1.0"));}
 @Test public void malformedAndPreviewVersionsAreNotAdvertisedAsStable(){for(String version:new String[]{"","v1","0.2.0-preview.1","1.2.3.4","<script>","9999999999999999.0.0"})assertFalse(Updates.newer(version,"0.1.0"));}
 @Test public void failuresExplainRateLimitWithoutCallingPermissionErrorsRateLimits(){assertTrue(Updates.failureMessage(403,true).contains("temporarily limited"));assertTrue(Updates.failureMessage(429,false).contains("temporarily limited"));assertFalse(Updates.failureMessage(403,false).contains("temporarily limited"));assertTrue(Updates.failureMessage(404,false).contains("No published update"));assertTrue(Updates.failureMessage(500,false).contains("unavailable"));}
}
