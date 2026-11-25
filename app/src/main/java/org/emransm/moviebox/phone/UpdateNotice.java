package org.emransm.moviebox.phone;

import android.app.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;
import org.json.JSONObject;
import java.util.concurrent.*;
import java.util.*;

/** Dismissible in-app reminder. No forced screen or background APK download. */
final class UpdateNotice {
 interface Busy {boolean playing();}
 interface Checker {JSONObject check()throws Exception;}
 interface Clock {long now();}
 interface Feedback {void checking(boolean value);}
 private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
 private static final String STORE="moviebox-optional-updates";
 private static final Map<SharedPreferences,Boolean> IN_FLIGHT=new WeakHashMap<>();
 private static final Map<SharedPreferences,List<Feedback>> FEEDBACK=new WeakHashMap<>();
 private static boolean coldLaunchConsumed;
 static void beginColdLaunch(SharedPreferences preferences){if(!coldLaunchConsumed){coldLaunchConsumed=true;preferences.edit().remove("dismissed").apply();}}
 private static void feedbackStart(SharedPreferences preferences,Feedback feedback){if(feedback!=null){List<Feedback> listeners=FEEDBACK.get(preferences);if(listeners==null){listeners=new ArrayList<>();FEEDBACK.put(preferences,listeners);}listeners.add(feedback);feedback.checking(true);}}
 private static void feedbackFinish(SharedPreferences preferences){List<Feedback> listeners=FEEDBACK.remove(preferences);if(listeners!=null)for(Feedback listener:listeners)listener.checking(false);}
 static boolean enabled(Context c){return c.getSharedPreferences(STORE,0).getBoolean("enabled",true);}
 static void enabled(Context c,boolean value){SharedPreferences preferences=c.getSharedPreferences(STORE,0);preferences.edit().putBoolean("enabled",value).remove("pending").apply();if(!value&&c instanceof Activity){Activity activity=(Activity)c;activity.runOnUiThread(()->{View old=activity.getWindow().getDecorView().findViewWithTag("moviebox-update-notice");if(old!=null&&old.getParent() instanceof ViewGroup)((ViewGroup)old.getParent()).removeView(old);});}}
 static void check(Activity activity,FrameLayout root,Busy busy,boolean manual){
  SharedPreferences preferences=activity.getSharedPreferences(STORE,0);if(!manual)beginColdLaunch(preferences);check(activity,root,busy,manual,preferences,Updates::check,System::currentTimeMillis,WORK);
 }
 static void check(Activity activity,FrameLayout root,Busy busy,boolean manual,Feedback feedback){
  check(activity,root,busy,manual,activity.getSharedPreferences(STORE,0),Updates::check,System::currentTimeMillis,WORK,feedback);
 }
 static void check(Activity activity,FrameLayout root,Busy busy,boolean manual,SharedPreferences preferences,Checker checker,Clock clock,Executor worker){
  check(activity,root,busy,manual,preferences,checker,clock,worker,null);
 }
 static void check(Activity activity,FrameLayout root,Busy busy,boolean manual,SharedPreferences preferences,Checker checker,Clock clock,Executor worker,Feedback feedback){
  presentPending(activity,root,busy,preferences);long now=clock.now(),retry=preferences.getLong("retry_after",0);if(retry>now&&retry-now<=24L*60*60*1000){if(manual)Toast.makeText(activity,preferences.getString("retry_reason","Update checks are temporarily paused. Try again later."),Toast.LENGTH_LONG).show();return;}long checked=preferences.getLong("checked",0),age=now-checked;
  if(!manual&&(!preferences.getBoolean("enabled",true)||checked>0&&age>=0&&age<12L*60*60*1000))return;
  if(IN_FLIGHT.containsKey(preferences)){feedbackStart(preferences,feedback);if(manual&&feedback==null)checking(activity,root,true);return;}IN_FLIGHT.put(preferences,true);
  if(manual&&feedback==null)checking(activity,root,true);feedbackStart(preferences,feedback);
  try{worker.execute(()->{JSONObject result=null;String reason=null;long retryAt=0;try{result=checker.check();}catch(Updates.CheckUnavailable unavailable){reason=unavailable.getMessage();retryAt=unavailable.retryAt;}catch(Exception unavailable){}final JSONObject update=result;final String failure=reason;final long nextAllowed=retryAt;
   activity.runOnUiThread(()->{IN_FLIGHT.remove(preferences);checking(activity,root,false);feedbackFinish(preferences);boolean valid=update!=null&&Updates.successful(update)&&Updates.version(update.optString("version"))&&(!update.optBoolean("available")||acceptable(update));
    if(valid){preferences.edit().putLong("checked",clock.now()).remove("retry_after").remove("retry_reason").apply();if(update.optBoolean("available")&&preferences.getBoolean("enabled",true))preferences.edit().putString("pending",update.toString()).apply();else preferences.edit().remove("pending").apply();}
    if(!valid){long current=clock.now(),until=nextAllowed>current?Math.min(current+24L*60*60*1000,nextAllowed):current+60000;preferences.edit().putLong("retry_after",until).putString("retry_reason",failure==null?"Could not check updates. Please try again shortly.":failure).apply();}
    if(activity.isFinishing()||activity.isDestroyed())return;
    if(!valid){if(manual)Toast.makeText(activity,update==null?(failure==null?"Couldn't check updates. Try again later.":failure):Updates.successful(update)?"Couldn't check updates. Try again later.":Updates.message(update),Toast.LENGTH_LONG).show();return;}
    if(!update.optBoolean("available")){View old=root.findViewWithTag("moviebox-update-notice");if(old!=null)root.removeView(old);if(manual)Toast.makeText(activity,Updates.message(update),Toast.LENGTH_SHORT).show();return;}
    if(manual&&!busy.playing()){show(activity,root,preferences,update);return;}
    presentPending(activity,root,busy,preferences);
    if(manual&&busy.playing())Toast.makeText(activity,"A new version is available. Check again after playback to view it.",Toast.LENGTH_LONG).show();
   });
  });}catch(RejectedExecutionException unavailable){IN_FLIGHT.remove(preferences);checking(activity,root,false);feedbackFinish(preferences);if(manual)Toast.makeText(activity,"Couldn't check updates. Try again later.",Toast.LENGTH_LONG).show();}
 }
 private static boolean acceptable(JSONObject update){return Updates.downloadable(update);}
 private static void checking(Activity activity,FrameLayout root,boolean active){
  View previous=root.findViewWithTag("moviebox-update-checking");if(previous!=null)root.removeView(previous);if(!active||activity.isFinishing()||activity.isDestroyed())return;
  LinearLayout row=new LinearLayout(activity);row.setTag("moviebox-update-checking");row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(activity,16),dp(activity,10),dp(activity,16),dp(activity,10));GradientDrawable background=new GradientDrawable();background.setColor(0xff25262b);background.setCornerRadius(dp(activity,12));row.setBackground(background);
  ProgressBar progress=new ProgressBar(activity);progress.setContentDescription("Checking for updates");row.addView(progress,new LinearLayout.LayoutParams(dp(activity,24),dp(activity,24)));TextView label=new TextView(activity);label.setText("Checking for updates…");label.setTextColor(0xfff3f4f6);label.setTextSize(14);label.setPadding(dp(activity,12),0,0,0);row.addView(label,new LinearLayout.LayoutParams(0,dp(activity,32),1));FrameLayout.LayoutParams position=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);position.setMargins(dp(activity,12),0,dp(activity,12),dp(activity,88));root.addView(row,position);
 }
 static void presentPending(Activity activity,FrameLayout root,Busy busy){presentPending(activity,root,busy,activity.getSharedPreferences(STORE,0));}
 static void presentPending(Activity activity,FrameLayout root,Busy busy,SharedPreferences preferences){
  String raw=preferences.getString("pending","");if(raw.isEmpty())return;JSONObject update;try{update=new JSONObject(raw);}catch(Exception invalid){preferences.edit().remove("pending").apply();return;}
  if(!preferences.getBoolean("enabled",true)||!acceptable(update)){preferences.edit().remove("pending").apply();return;}if(preferences.getString("dismissed","").equals(update.optString("version")))return;
  if(activity.isFinishing()||activity.isDestroyed()||busy.playing())return;show(activity,root,preferences,update);
 }
 private static int dp(Context c,int value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
 static void show(Activity activity,FrameLayout root,SharedPreferences preferences,JSONObject update){show(activity,root,preferences,update,()->UpdateDownload.showAndStart(activity,update));}
 static void show(Activity activity,FrameLayout root,SharedPreferences preferences,JSONObject update,Runnable updateAction){
  View old=root.findViewWithTag("moviebox-update-notice");if(old!=null)root.removeView(old);
  LinearLayout notice=new LinearLayout(activity);notice.setTag("moviebox-update-notice");notice.setGravity(Gravity.CENTER_VERTICAL);notice.setPadding(dp(activity,12),0,0,0);GradientDrawable background=new GradientDrawable();background.setColor(0xff183c30);background.setCornerRadius(dp(activity,12));notice.setBackground(background);
  TextView label=new TextView(activity);label.setText("MovieBox "+update.optString("version"));label.setTextColor(0xfff3fff8);label.setTextSize(13);notice.addView(label,new LinearLayout.LayoutParams(0,dp(activity,48),1));label.setGravity(Gravity.CENTER_VERTICAL);Button open=new Button(activity);open.setText("Update");open.setContentDescription("View update details");open.setAllCaps(false);open.setTextSize(13);open.setTextColor(0xfff3fff8);open.setBackgroundColor(android.graphics.Color.TRANSPARENT);open.setOnClickListener(v->updateAction.run());notice.addView(open,new LinearLayout.LayoutParams(dp(activity,76),dp(activity,48)));
  Button close=new Button(activity);close.setText("Later");close.setAllCaps(false);close.setTextSize(13);close.setContentDescription("Dismiss optional update");close.setTextColor(0xfff3fff8);close.setBackgroundColor(android.graphics.Color.TRANSPARENT);close.setOnClickListener(v->{preferences.edit().putString("dismissed",update.optString("version")).apply();root.removeView(notice);});notice.addView(close,new LinearLayout.LayoutParams(dp(activity,64),dp(activity,48)));
  FrameLayout.LayoutParams layout=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);layout.setMargins(dp(activity,12),0,dp(activity,12),dp(activity,88));root.addView(notice,layout);
 }
}
