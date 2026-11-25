package org.emransm.moviebox.phone;
import org.junit.Test;
import static org.junit.Assert.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class UpdatesBackoffTest {
 private static final long NOW=1764000000000L;
 @Test public void forbiddenNeedsActualRateSignals(){
  assertTrue(Updates.limited(429,null,null));assertTrue(Updates.limited(403,"0",null));assertTrue(Updates.limited(403,"9","120"));
  assertFalse(Updates.limited(403,"9",null));assertFalse(Updates.limited(404,"0","120"));
 }
 @Test public void retrySecondsAndResetUseLaterServerDeadline(){
  assertEquals(NOW+120000,Updates.retryAt(NOW,429,true,"120",null));
  assertEquals(NOW+3600000,Updates.retryAt(NOW,403,true,"120",Long.toString((NOW+3600000)/1000)));
  assertEquals(NOW+60000,Updates.retryAt(NOW,403,false,"120",Long.toString((NOW+3600000)/1000)));
 }
 @Test public void httpDateWorksAndExpiredHeadersDoNotLoopImmediately(){
  SimpleDateFormat format=new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz",Locale.US);format.setTimeZone(TimeZone.getTimeZone("GMT"));
  assertEquals(NOW+180000,Updates.retryAt(NOW,429,true,format.format(new Date(NOW+180000)),null));
  assertEquals(NOW+60000,Updates.retryAt(NOW,429,true,"0",Long.toString((NOW-1000)/1000)));
 }
 @Test public void malformedMissingAndUnboundedValuesStayBounded(){
  assertEquals(NOW+900000,Updates.retryAt(NOW,429,true,"nonsense","bad"));
  assertEquals(NOW+86400000,Updates.retryAt(NOW,429,true,Long.toString(Long.MAX_VALUE),Long.toString(Long.MAX_VALUE)));
  assertEquals(NOW+60000,Updates.retryAt(NOW,500,false,null,null));
 }
}
