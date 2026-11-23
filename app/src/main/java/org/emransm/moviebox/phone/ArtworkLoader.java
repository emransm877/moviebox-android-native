package org.emransm.moviebox.phone;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import java.util.*;
import java.util.concurrent.*;

/** UI-owned visible artwork demand; no fetch or decode is ever run on the UI thread. */
public final class ArtworkLoader implements AutoCloseable {
    public interface Fetcher { Bitmap load(String url,int width) throws Exception; }
    private static final int MAX_ATTEMPTS=3;
    private final Handler ui;private final LruCache<String,Bitmap> cache;private final Fetcher fetcher;private final Drawable fallback;
    private final ThreadPoolExecutor workers;
    // Weak keys avoid retaining detached screens. Demand is bounded by live UI targets,
    // while requests never exceed the worker policy (no growing task queue).
    private final WeakHashMap<ImageView,Binding> targets=new WeakHashMap<>();
    private final Set<String> running=new HashSet<>();private boolean closed;
    private final Runnable pumpLater=this::pump;
    private static final class Binding {
        final String key,url;final int width;final Runnable success;int attempts;long retryAt;
        View.OnAttachStateChangeListener attach;
        Binding(String url,int width,Runnable success){this.url=url;this.width=width;this.key=url+"@"+width;this.success=success;}
    }
    public ArtworkLoader(Handler ui,LruCache<String,Bitmap> cache,Fetcher fetcher,Drawable fallback){this(ui,cache,fetcher,fallback,new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(24),new ThreadPoolExecutor.AbortPolicy()));}
    ArtworkLoader(Handler ui,LruCache<String,Bitmap> cache,Fetcher fetcher,Drawable fallback,ThreadPoolExecutor workers){this.ui=ui;this.cache=cache;this.fetcher=fetcher;this.fallback=fallback;this.workers=workers;}
    private void onUi(){if(Looper.myLooper()!=ui.getLooper())throw new IllegalStateException("Artwork targets must be changed on the UI thread");}
    public void load(ImageView view,String url,int width,Runnable success){
        onUi();Binding old=targets.remove(view);if(old!=null)view.removeOnAttachStateChangeListener(old.attach);
        Binding binding=new Binding(url==null?"":url,Math.max(1,width),success);view.setTag(binding);view.setImageDrawable(copyFallback());
        if(closed||!binding.url.startsWith("https://"))return;
        Bitmap cached=cache.get(binding.key);if(cached!=null&&!cached.isRecycled()){view.setImageBitmap(cached);if(success!=null)success.run();return;}
        binding.attach=new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){pump();}public void onViewDetachedFromWindow(View v){/* Remains weak demand, eligible again only when attached. */}};
        targets.put(view,binding);view.addOnAttachStateChangeListener(binding.attach);pump();
    }
    private Drawable copyFallback(){Drawable.ConstantState state=fallback==null?null:fallback.getConstantState();return state==null?fallback:state.newDrawable().mutate();}
    private boolean eligible(ImageView view,Binding b){return view!=null&&view.getTag()==b&&view.isAttachedToWindow()&&view.isShown()&&b.attempts<MAX_ATTEMPTS;}
    private void pump(){
        onUi();ui.removeCallbacks(pumpLater);if(closed)return;long now=SystemClock.uptimeMillis(),next=Long.MAX_VALUE;
        // Snapshot iteration: callbacks may replace a binding or clear the screen.
        List<Map.Entry<ImageView,Binding>> demand=new ArrayList<>(),prefetch=new ArrayList<>();android.graphics.Rect visible=new android.graphics.Rect();
        for(Map.Entry<ImageView,Binding> entry:new ArrayList<>(targets.entrySet())){ImageView view=entry.getKey();if(view!=null&&view.getGlobalVisibleRect(visible))demand.add(entry);else prefetch.add(entry);}demand.addAll(prefetch);
        for(Map.Entry<ImageView,Binding> entry:demand){
            ImageView view=entry.getKey();Binding b=entry.getValue();if(!eligible(view,b)||running.contains(b.key))continue;
            Bitmap cached=cache.get(b.key);if(cached!=null&&!cached.isRecycled()){targets.remove(view);view.removeOnAttachStateChangeListener(b.attach);view.setImageBitmap(cached);if(b.success!=null)b.success.run();continue;}
            if(b.retryAt>now){next=Math.min(next,b.retryAt);continue;}if(running.size()>=workers.getMaximumPoolSize())continue;
            running.add(b.key);
            try{workers.execute(()->{Bitmap result=null;try{result=fetcher.load(b.url,b.width);}catch(Exception ignored){}final Bitmap loaded=result;ui.post(()->finish(b.key,loaded));});}
            catch(RejectedExecutionException rejected){running.remove(b.key);b.attempts++;b.retryAt=now+250;next=Math.min(next,b.retryAt);}
        }
        if(next!=Long.MAX_VALUE)ui.postDelayed(pumpLater,Math.max(1,next-now));
    }
    private void finish(String key,Bitmap bitmap){
        onUi();running.remove(key);if(closed)return;boolean good=bitmap!=null&&!bitmap.isRecycled();if(good)cache.put(key,bitmap);
        for(Map.Entry<ImageView,Binding> entry:new ArrayList<>(targets.entrySet())){
            ImageView view=entry.getKey();Binding b=entry.getValue();if(view==null||!b.key.equals(key)||view.getTag()!=b)continue;
            if(good){targets.remove(view);view.removeOnAttachStateChangeListener(b.attach);view.setImageBitmap(bitmap);if(b.success!=null)b.success.run();}
            else{b.attempts++;b.retryAt=SystemClock.uptimeMillis()+250L*b.attempts;view.setImageDrawable(copyFallback());if(b.attempts>=MAX_ATTEMPTS){targets.remove(view);view.removeOnAttachStateChangeListener(b.attach);}}
        }pump();
    }
    /** Drop all old screen subscribers; active fetches may safely warm the cache. */
    public void clearTargets(){onUi();ui.removeCallbacks(pumpLater);for(Map.Entry<ImageView,Binding> e:new ArrayList<>(targets.entrySet())){ImageView view=e.getKey();if(view!=null){view.removeOnAttachStateChangeListener(e.getValue().attach);if(view.getTag()==e.getValue())view.setTag(null);}}targets.clear();}
    @Override public void close(){onUi();clearTargets();closed=true;workers.shutdownNow();}
}
