package org.emransm.moviebox.phone;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;

/** Original sample art supplied with these tests, not service catalogue captures. */
final class OwnedQaArtwork {
 static File copy(String asset)throws Exception {
  if(!asset.matches("release-(poster-[0-5]|banner)\\.png"))throw new IOException("Unknown authored artwork fixture");
  Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();
  File file=new File(target.getCacheDir(),asset);
  try(InputStream input=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(asset);OutputStream output=new FileOutputStream(file)){
   byte[] bytes=new byte[32768];for(int size;(size=input.read(bytes))!=-1;)output.write(bytes,0,size);
  }
  return file;
 }
}
