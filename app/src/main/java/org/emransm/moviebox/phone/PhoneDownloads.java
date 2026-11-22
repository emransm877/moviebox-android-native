package org.emransm.moviebox.phone;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** System-managed downloads. Only explicitly chosen, unlocked service resources are queued. */
public final class PhoneDownloads {
    private static final String PREFS="phone_downloads";
    public interface Playback { void open(Uri uri,String title); }
    private PhoneDownloads() {}
    public static void enqueue(Activity a, JSONObject subject, JSONObject stream,int season,int episode) {
        try {
            String source=stream.optString("resourceLink");
            Uri uri=Uri.parse(source);
            if(!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null
                    || stream.optInt("requireMemberType")>0 || stream.optBoolean("vipLocked"))
                throw new IllegalArgumentException("This source cannot be downloaded.");
            String title=subject.optString("title","MovieBox video")+(season>0?" · S"+season+" E"+episode:"");
            String name=title.replaceAll("[^a-zA-Z0-9._ -]","_");
            if(name.length()>90)name=name.substring(0,90);
            name+="-"+System.currentTimeMillis()+".mp4";
            DownloadManager manager=(DownloadManager)a.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request request=new DownloadManager.Request(uri).addRequestHeader("User-Agent",Api.UA).setTitle(title)
                .setDescription("MovieBox · "+stream.optInt("resolution")+"p")
                .setAllowedOverMetered(true).setAllowedOverRoaming(false)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(a,Environment.DIRECTORY_MOVIES,name);
            long id=manager.enqueue(request);
            a.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(Long.toString(id),title).apply();
            Toast.makeText(a,"Download started. See Downloads for progress.",Toast.LENGTH_LONG).show();
        }catch(Exception error){Toast.makeText(a,error.getMessage()==null?"Download could not start.":error.getMessage(),Toast.LENGTH_LONG).show();}
    }
    public static View screen(Activity a) {
        return screen(a,null);
    }
    public static View screen(Activity a,Playback playback) {
        ScrollView scroll=new ScrollView(a);scroll.setFillViewport(true);
        LinearLayout list=new LinearLayout(a);list.setOrientation(LinearLayout.VERTICAL);
        int space=dp(a,16);list.setPadding(space,space,space,space);scroll.addView(list);
        DownloadManager dm=(DownloadManager)a.getSystemService(Context.DOWNLOAD_SERVICE);
        SharedPreferences prefs=a.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        Map<String,?> records=prefs.getAll();
        if(records.isEmpty()){
            LinearLayout empty=new LinearLayout(a);empty.setOrientation(LinearLayout.VERTICAL);empty.setGravity(Gravity.CENTER);empty.setPadding(space,dp(a,12),space,space);
            TextView icon=text(a,"\u21e9",38);icon.setTextColor(0xff2feda1);icon.setGravity(Gravity.CENTER);empty.addView(icon);
            TextView title=text(a,"No downloads yet",20);title.setTypeface(null,android.graphics.Typeface.BOLD);title.setGravity(Gravity.CENTER);empty.addView(title);
            TextView note=text(a,"Save a video from its detail page to watch it offline.",14);note.setGravity(Gravity.CENTER);note.setTextColor(0xffadb0b6);empty.addView(note);
            list.addView(empty,new LinearLayout.LayoutParams(-1,-1));return scroll;
        }
        TextView note=text(a,"Ready when you are. Saved on this phone.",13);note.setTextColor(0xffadb0b6);list.addView(note);
        ArrayList<Runnable> polls=new ArrayList<>();
        Handler handler=new Handler(Looper.getMainLooper());
        final ExecutorService[] worker={null};final AtomicBoolean busy=new AtomicBoolean(false);final int[] attachment={0};
        ArrayList<String> ids=new ArrayList<>(records.keySet());Collections.sort(ids,Collections.reverseOrder());
        for(String key:ids) {
            long id;try{id=Long.parseLong(key);}catch(Exception ignored){continue;}
            LinearLayout card=new LinearLayout(a);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(space,dp(a,12),space,dp(a,12));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(a,12);card.setLayoutParams(cp);card.setBackground(shape(a,0xff191a1e));
            TextView title=text(a,String.valueOf(records.get(key)),16);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setTypeface(null,android.graphics.Typeface.BOLD);card.addView(title);
            TextView state=text(a,"Checking download\u2026",13);state.setTextColor(0xffadb0b6);card.addView(state);
            ProgressBar progress=new ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xff2feda1));
            LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(a,4));pp.topMargin=dp(a,8);pp.bottomMargin=dp(a,12);card.addView(progress,pp);
            LinearLayout actions=new LinearLayout(a);actions.setGravity(Gravity.CENTER_VERTICAL);card.addView(actions);final int[] previous={-1};final AtomicBoolean removed=new AtomicBoolean(false);final Runnable[] pollHolder={null};
            Runnable poll=()->{
                if(removed.get()||Thread.currentThread().isInterrupted())return;final int expectedAttachment=attachment[0];
                int value=0;long got=0,total=-1;
                try(Cursor c=dm.query(new DownloadManager.Query().setFilterById(id))){if(c!=null&&c.moveToFirst()){
                    value=c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));got=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));total=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                }}catch(Exception ignored){}
                final int result=value;final long downloaded=got,length=total;
                handler.post(()->{if(removed.get()||!scroll.isAttachedToWindow()||attachment[0]!=expectedAttachment||card.getParent()!=list)return;
                int valueOnUi=result;long gotOnUi=downloaded,totalOnUi=length;
                boolean completed=valueOnUi==DownloadManager.STATUS_SUCCESSFUL;boolean removable=completed||valueOnUi==DownloadManager.STATUS_FAILED||valueOnUi==0;
                String label=completed?"Ready to play":valueOnUi==DownloadManager.STATUS_FAILED?"Download failed. Remove it and try again.":valueOnUi==DownloadManager.STATUS_PAUSED?"Waiting for connection":valueOnUi==DownloadManager.STATUS_PENDING?"Queued":valueOnUi==DownloadManager.STATUS_RUNNING?"Downloading":"File unavailable";
                if(!completed&&valueOnUi==DownloadManager.STATUS_RUNNING)label+=" \u00b7 "+android.text.format.Formatter.formatShortFileSize(a,gotOnUi)+(totalOnUi>0?" / "+android.text.format.Formatter.formatShortFileSize(a,totalOnUi)+" ("+(gotOnUi*100/totalOnUi)+"%)":"");
                state.setText(label);progress.setIndeterminate(totalOnUi<=0&&valueOnUi==DownloadManager.STATUS_RUNNING);if(totalOnUi>0)progress.setProgress((int)Math.min(100,gotOnUi*100/totalOnUi));progress.setVisibility(valueOnUi==DownloadManager.STATUS_RUNNING?View.VISIBLE:View.GONE);
                if(previous[0]==valueOnUi)return;previous[0]=valueOnUi;actions.removeAllViews();
                if(completed){Button play=action(a,"\u25b6  Play",true);play.setOnClickListener(v->{try{Uri uri=dm.getUriForDownloadedFile(id);if(uri==null)throw new IllegalStateException();
                    if(playback!=null)playback.open(uri,String.valueOf(records.get(key)));else a.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"video/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                }catch(Exception e){Toast.makeText(a,"This file could not be opened.",Toast.LENGTH_LONG).show();}});actions.addView(play);}
                Button remove=action(a,removable?"Remove":"Cancel",false);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-2,dp(a,48));rp.leftMargin=completed?dp(a,12):0;actions.addView(remove,rp);
                remove.setOnClickListener(v->new AlertDialog.Builder(a).setTitle(completed?"Remove downloaded video?":removable?"Remove download?":"Cancel download?").setMessage(removable&&!completed?"Remove this entry and any partial file from this phone.":"This removes the saved file from this phone.").setNegativeButton("Keep",null).setPositiveButton("Remove",(d,w)->{ExecutorService active=worker[0];if(active==null||active.isShutdown())return;removed.set(true);remove.setEnabled(false);try{active.execute(()->{try{dm.remove(id);prefs.edit().remove(key).commit();handler.post(()->{polls.remove(pollHolder[0]);if(card.getParent()==list)list.removeView(card);if(scroll.isAttachedToWindow()&&prefs.getAll().isEmpty())replace(scroll,screen(a,playback));});}catch(Exception failure){removed.set(false);handler.post(()->{if(scroll.isAttachedToWindow()){remove.setEnabled(true);Toast.makeText(a,"The download could not be removed. Try again.",Toast.LENGTH_LONG).show();}});}});}catch(RejectedExecutionException detached){removed.set(false);remove.setEnabled(true);}}).show());
                });
            };pollHolder[0]=poll;polls.add(poll);list.addView(card);
        }
        Runnable ticker=new Runnable(){public void run(){
            if(!scroll.isAttachedToWindow())return;ExecutorService active=worker[0];
            if(active!=null&&!active.isShutdown()&&busy.compareAndSet(false,true)){
                List<Runnable> snapshot=new ArrayList<>(polls);
                try{active.execute(()->{try{for(Runnable poll:snapshot){if(Thread.currentThread().isInterrupted())break;poll.run();}}finally{busy.set(false);}});}catch(RejectedExecutionException detached){busy.set(false);}
            }
            handler.postDelayed(this,1500);
        }};
        scroll.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){attachment[0]++;busy.set(false);worker[0]=Executors.newSingleThreadExecutor();handler.post(ticker);}public void onViewDetachedFromWindow(View v){attachment[0]++;handler.removeCallbacksAndMessages(null);ExecutorService active=worker[0];worker[0]=null;if(active!=null)active.shutdownNow();}});
        return scroll;
    }
    private static void replace(View old,View next){if(old.getParent() instanceof ViewGroup){ViewGroup p=(ViewGroup)old.getParent();int i=p.indexOfChild(old);ViewGroup.LayoutParams lp=old.getLayoutParams();p.removeView(old);p.addView(next,i,lp);}}
    private static int dp(Activity a,int n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
    private static android.graphics.drawable.GradientDrawable shape(Activity a,int color){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(color);d.setCornerRadius(dp(a,12));d.setStroke(dp(a,1),0xff303238);return d;}
    private static Button action(Activity a,String value,boolean primary){Button b=new Button(a);b.setText(value);b.setTextSize(14);b.setAllCaps(false);b.setMinHeight(dp(a,48));b.setPadding(dp(a,16),0,dp(a,16),0);b.setTextColor(primary?0xff101114:0xffc8ccd1);b.setBackground(shape(a,primary?0xff2feda1:0xff191a1e));b.setContentDescription(value);return b;}
    private static TextView text(Activity a,String value,int size){TextView t=new TextView(a);t.setText(value);t.setTextColor(Color.rgb(240,241,243));t.setTextSize(size);t.setPadding(0,dp(a,4),0,dp(a,4));return t;}
}
