package org.emransm.moviebox.phone;

import android.app.*;
import android.content.*;
import android.net.Uri;
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
 private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
 private static final String STORE="moviebox-optional-updates";
 private static final Map<SharedPreferences,Boolean> IN_FLIGHT=new WeakHashMap<>();
 static boolean enabled(Context c){return c.getSharedPreferences(STORE,0).getBoolean("enabled",true);}
 static void enabled(Context c,boolean value){SharedPreferences preferences=c.getSharedPreferences(STORE,0);preferences.edit().putBoolean("enabled",value).remove("pending").apply();if(!value&&c instanceof Activity){Activity activity=(Activity)c;activity.runOnUiThread(()->{View old=activity.getWindow().getDecorView().findViewWithTag("moviebox-update-notice");if(old!=null&&old.getParent() instanceof ViewGroup)((ViewGroup)old.getParent()).removeView(old);});}}
 static void check(Activity activity,FrameLayout root,Busy busy,boolean manual){
  check(activity,root,busy,manual,activity.getSharedPreferences(STORE,0),Updates::check,System::currentTimeMillis,WORK);
 }
 static void check(Activity activity,FrameLayout root,Busy busy,boolean manual,SharedPreferences preferences,Checker checker,Clock clock,Executor worker){
  presentPending(activity,root,busy,preferences);long checked=preferences.getLong("checked",0),age=clock.now()-checked;
  if(!manual&&(!preferences.getBoolean("enabled",true)||checked>0&&age>=0&&age<12L*60*60*1000))return;
  if(IN_FLIGHT.containsKey(preferences)){if(manual)Toast.makeText(activity,"Already checking for updates.",Toast.LENGTH_SHORT).show();return;}IN_FLIGHT.put(preferences,true);
  try{worker.execute(()->{JSONObject result=null;try{result=checker.check();}catch(Exception unavailable){}final JSONObject update=result;
   activity.runOnUiThread(()->{IN_FLIGHT.remove(preferences);boolean valid=update!=null&&Updates.successful(update)&&Updates.version(update.optString("version"))&&(!update.optBoolean("available")||acceptable(update));
    if(valid){preferences.edit().putLong("checked",clock.now()).apply();if(update.optBoolean("available")&&preferences.getBoolean("enabled",true)&&!preferences.getString("dismissed","").equals(update.optString("version")))preferences.edit().putString("pending",update.toString()).apply();else preferences.edit().remove("pending").apply();}
    if(activity.isFinishing()||activity.isDestroyed())return;
    if(!valid){if(manual)Toast.makeText(activity,update==null||Updates.successful(update)?"Couldn't check updates. Try again later.":Updates.message(update),Toast.LENGTH_LONG).show();return;}
    if(!update.optBoolean("available")){if(manual)Toast.makeText(activity,Updates.message(update),Toast.LENGTH_SHORT).show();return;}
    if(manual&&!busy.playing()){show(activity,root,preferences,update);return;}
    presentPending(activity,root,busy,preferences);
    if(manual&&busy.playing())Toast.makeText(activity,"A new version is available. Check again after playback to view it.",Toast.LENGTH_LONG).show();
   });
  });}catch(RejectedExecutionException unavailable){IN_FLIGHT.remove(preferences);if(manual)Toast.makeText(activity,"Couldn't check updates. Try again later.",Toast.LENGTH_LONG).show();}
 }
 private static boolean acceptable(JSONObject update){return Updates.AVAILABLE.equals(update.optString("status"))&&Updates.newer(update.optString("version"),Updates.VERSION)&&Updates.validDownload(update.optString("url"));}
 static void presentPending(Activity activity,FrameLayout root,Busy busy){presentPending(activity,root,busy,activity.getSharedPreferences(STORE,0));}
 static void presentPending(Activity activity,FrameLayout root,Busy busy,SharedPreferences preferences){
  String raw=preferences.getString("pending","");if(raw.isEmpty())return;JSONObject update;try{update=new JSONObject(raw);}catch(Exception invalid){preferences.edit().remove("pending").apply();return;}
  if(!preferences.getBoolean("enabled",true)||preferences.getString("dismissed","").equals(update.optString("version"))||!acceptable(update)){preferences.edit().remove("pending").apply();return;}
  if(activity.isFinishing()||activity.isDestroyed()||busy.playing())return;show(activity,root,preferences,update);
 }
 private static int dp(Context c,int value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
 static void show(Activity activity,FrameLayout root,SharedPreferences preferences,JSONObject update){
  View old=root.findViewWithTag("moviebox-update-notice");if(old!=null)root.removeView(old);
  LinearLayout notice=new LinearLayout(activity);notice.setTag("moviebox-update-notice");notice.setGravity(Gravity.CENTER_VERTICAL);notice.setPadding(dp(activity,12),0,0,0);GradientDrawable background=new GradientDrawable();background.setColor(0xff183c30);background.setCornerRadius(dp(activity,12));notice.setBackground(background);
  Button open=new Button(activity);open.setText("MovieBox "+update.optString("version")+" available \u00b7 View");open.setAllCaps(false);open.setTextSize(13);open.setTextColor(0xfff3fff8);open.setBackgroundColor(android.graphics.Color.TRANSPARENT);open.setOnClickListener(v->{try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(update.optString("url"))));}catch(ActivityNotFoundException unavailable){Toast.makeText(activity,"Open the project's GitHub Releases to update.",Toast.LENGTH_LONG).show();}});notice.addView(open,new LinearLayout.LayoutParams(0,dp(activity,48),1));
  Button close=new Button(activity);close.setText("\u00d7");close.setTextSize(22);close.setContentDescription("Dismiss optional update");close.setTextColor(0xfff3fff8);close.setBackgroundColor(android.graphics.Color.TRANSPARENT);close.setOnClickListener(v->{preferences.edit().putString("dismissed",update.optString("version")).remove("pending").apply();root.removeView(notice);});notice.addView(close,new LinearLayout.LayoutParams(dp(activity,48),dp(activity,48)));
  FrameLayout.LayoutParams layout=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);layout.setMargins(dp(activity,12),0,dp(activity,12),dp(activity,88));root.addView(notice,layout);
 }
}
