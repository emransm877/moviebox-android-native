package org.emransm.moviebox.phone;

import android.content.*;
import android.graphics.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

@RunWith(AndroidJUnit4.class)
public class ArtworkCacheRecoveryScenarios {
    @Test public void nativeCatalogueReceivesRealDecodedFixtureArtwork()throws Exception{
        Bitmap art=Bitmap.createBitmap(240,360,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(art);canvas.drawColor(0xff123a4a);Paint paint=new Paint(3);paint.setColor(Color.CYAN);canvas.drawCircle(120,120,70,paint);paint.setColor(Color.WHITE);paint.setTextSize(19);canvas.drawText("OWNED QA ART",36,235,paint);ByteArrayOutputStream data=new ByteArrayOutputStream();assertTrue(art.compress(Bitmap.CompressFormat.PNG,100,data));byte[] bytes=data.toByteArray();
        BlockFlowScenarios.FakeApi api=new BlockFlowScenarios.FakeApi(){@Override public java.util.List<org.json.JSONObject> catalogue(int type,int page,org.json.JSONObject filters)throws Exception{java.util.List<org.json.JSONObject> result=super.catalogue(type,page,filters);for(org.json.JSONObject item:result)item.put("cover","https://owned.invalid/art-"+System.identityHashCode(this));return result;}@Override public byte[] artworkBytes(String url,int max){return bytes;}};
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(s,api);main(s,a->call(a,"browse",new Class[]{String.class},"Movies"));
            waitFor(()->{boolean[] ready={false};main(s,a->{android.view.View view=description((android.view.View)field(a,"root"),"Owned block catalogue-1-0");if(view instanceof android.view.ViewGroup){android.widget.ImageView poster=(android.widget.ImageView)((android.view.ViewGroup)view).getChildAt(0);ready[0]=poster.getDrawable() instanceof android.graphics.drawable.BitmapDrawable;}});return ready[0];},5000);
            Thread.sleep(3500);BlockFlowScenarios.capture("batch2-artwork-grid.png");
        }finally{art.recycle();api.close();}
    }
    @Test public void corruptDiskArtworkIsEvictedThenOnlyDecodedNetworkArtworkIsCached()throws Exception{
        AtomicInteger downloads=new AtomicInteger();Bitmap owned=Bitmap.createBitmap(24,36,Bitmap.Config.ARGB_8888);owned.eraseColor(Color.CYAN);ByteArrayOutputStream bytes=new ByteArrayOutputStream();assertTrue(owned.compress(Bitmap.CompressFormat.PNG,100,bytes));byte[] valid=bytes.toByteArray();
        BlockFlowScenarios.FakeApi api=new BlockFlowScenarios.FakeApi(){@Override public byte[] artworkBytes(String url,int maximum){if(url.equals("https://owned.invalid/art-valid"))downloads.incrementAndGet();return url.endsWith("invalid")?new byte[]{8,9,10}:valid;}};
        try(ActivityScenario<PhoneActivity> s=ActivityScenario.launch(PhoneActivity.class)){
            BlockFlowScenarios.isolate(s,api);PhoneActivity[] activity={null};UiCache[] cache={null};String location="https://owned.invalid/art-valid";
            main(s,a->{activity[0]=a;File folder=new File(a.getCacheDir(),"art-recovery-"+System.nanoTime());assertTrue(folder.mkdirs());Context context=new ContextWrapper(a){@Override public File getCacheDir(){return folder;}};cache[0]=new UiCache(context,false);put(a,"uiCache",cache[0]);});
            cache[0].writeArtwork(location,new byte[]{1,2,3});Bitmap image=(Bitmap)call(activity[0],"fetchArtwork",new Class[]{String.class,int.class},location,360);assertNotNull(image);assertEquals(Color.CYAN,image.getPixel(0,0));assertEquals(1,downloads.get());assertArrayEquals(valid,cache[0].readArtwork(location));
            assertNotNull(call(activity[0],"fetchArtwork",new Class[]{String.class,int.class},location,360));assertEquals("Valid disk hit avoids network",1,downloads.get());
            try{call(activity[0],"fetchArtwork",new Class[]{String.class,int.class},"https://owned.invalid/invalid",360);fail("Malformed network bytes must fail decoding");}catch(Exception expected){}
            assertNull("Malformed network bytes never persist",cache[0].readArtwork("https://owned.invalid/invalid"));
        }finally{owned.recycle();api.close();}
    }
}
