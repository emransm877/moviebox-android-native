package org.emransm.moviebox.phone;
import org.junit.Test;
import static org.junit.Assert.*;
public class BannerYearTest {
 @Test public void nativeYearAndWebDateRepresentTheSameRelease(){assertTrue(Api.sameReleaseYear("2023","2023-12-20"));assertTrue(Api.sameReleaseYear("2023-12-20","2023"));}
 @Test public void conflictingReleaseYearsCannotBorrowArtwork(){assertFalse(Api.sameReleaseYear("2023","2024-01-01"));}
 @Test public void missingOrMalformedYearsDoNotEstablishIdentity(){assertFalse(Api.sameReleaseYear("",""));assertFalse(Api.sameReleaseYear(null,"2023"));assertFalse(Api.sameReleaseYear("2023oops","2023"));}
}
