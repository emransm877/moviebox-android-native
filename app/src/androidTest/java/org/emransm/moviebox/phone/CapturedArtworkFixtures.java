package org.emransm.moviebox.phone;

import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Assume;
import java.io.IOException;
import java.io.InputStream;

/** Optional captured art is supplied locally with permission, never silently replaced. */
final class CapturedArtworkFixtures {
 static void require(String... names) {
  for(String name:names) {
   try(InputStream input=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(name)) {
    Assume.assumeTrue("Optional captured-art fixture is empty: "+name,input.read()!=-1);
   } catch(IOException missing) {
    Assume.assumeTrue("SKIPPED: optional captured-art fixture is not supplied: "+name,false);
   }
  }
 }
}
