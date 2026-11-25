package org.emransm.moviebox.phone;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class RecentSearchesTest {
    @Test public void newestFirstAndCaseInsensitiveDuplicates(){assertEquals(Arrays.asList("Movie","Series"),RecentSearches.add(Arrays.asList("movie","Series")," Movie "));}
    @Test public void historyStaysBoundedAndRetainsUnicode(){List<String> history=new ArrayList<>();for(int i=0;i<30;i++)history=RecentSearches.add(history,"Title "+i);assertEquals(12,history.size());assertEquals("Title 29",history.get(0));assertEquals("বাংলা 映画",RecentSearches.add(history,"বাংলা 映画").get(0));}
    @Test public void invalidLongInputDoesNotEnterHistory(){assertEquals("",RecentSearches.clean(null));char[] large=new char[201];Arrays.fill(large,'a');assertEquals("",RecentSearches.clean(new String(large)));assertEquals(Collections.singletonList("café"),RecentSearches.add(Collections.singletonList("cafe\u0301"),"café"));}
}
