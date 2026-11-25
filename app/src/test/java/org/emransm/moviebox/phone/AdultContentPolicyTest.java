package org.emransm.moviebox.phone;
import org.junit.Test;
import static org.junit.Assert.*;
public class AdultContentPolicyTest {
    @Test public void explicitAdultCertificatesAreBlocked(){for(String value:new String[]{"18","18+"," R18+ ","NC-17","TV MA","TV_MA","XXX"})assertTrue(value,AdultContentPolicy.matureRating(value));}
    @Test public void ordinaryRatingsAndInternationalTitlesAreNotKeywords(){for(String value:new String[]{"PG-13","12","16","The 18th Birthday","সিনেমা","映画","Romance","18th Street",""})assertFalse(value,AdultContentPolicy.matureRating(value));assertFalse(AdultContentPolicy.matureRating(null));}
}
