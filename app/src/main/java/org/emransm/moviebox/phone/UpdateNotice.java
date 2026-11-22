package org.emransm.moviebox.phone;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;
import org.json.JSONObject;
import java.util.concurrent.*;

/** Dismissible in-app reminder. No forced screen or background APK download. */
final class UpdateNotice {
 interface Busy {boolean playing();}
 private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
 private static final String STORE="moviebox-optional-updates";
 static boolean enabled(Context c){return c.getSharedPreferences(STORE,0).getBoolean("enabled",true);}
 static void enabled(Context c,boolean value){c.getSharedPreferences(STORE,0).edit().putBoolean("enabled",value).apply();}
 static void check(Activity activity,FrameLayout root,Busy busy,boolean manual){
  SharedPreferences preferences=activity.getSharedPreferences(STORE,0);long age=System.currentTimeMillis()-preferences.getLong("checked",0);
  if(!manual&&(!enabled(activity)||age>=0&&age<12L*60*60*1000))return;
  preferences.edit().putLong("checked",System.currentTimeMillis()).apply();
  WORK.execute(()->{JSONObject result=null;try{result=Updates.check();}catch(Exception unavailable){}final JSONObject update=result;
   activity.runOnUiThread(()->{if(activity.isFinishing()||activity.isDestroyed())return;
    if(update==null){if(manual)Toast.makeText(activity,"Couldn't check updates. Try again later.",Toast.LENGTH_LONG).show();return;}
    if(!update.optBoolean("available")){if(manual)Toast.makeText(activity,"You're using the latest version.",Toast.LENGTH_SHORT).show();return;}
    if(!manual&&(busy.playing()||preferences.getString("dismissed","").equals(update.optString("version"))))return;
    show(activity,root,preferences,update);
   });
  });
 }
 private static int dp(Context c,int value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
 static void show(Activity activity,FrameLayout root,SharedPreferences preferences,JSONObject update){
  View old=root.findViewWithTag("moviebox-update-notice");if(old!=null)root.removeView(old);
  LinearLayout notice=new LinearLayout(activity);notice.setTag("moviebox-update-notice");notice.setGravity(Gravity.CENTER_VERTICAL);notice.setPadding(dp(activity,12),0,0,0);GradientDrawable background=new GradientDrawable();background.setColor(0xff183c30);background.setCornerRadius(dp(activity,12));notice.setBackground(background);
  Button open=new Button(activity);open.setText("MovieBox "+update.optString("version")+" available  ·  View");open.setAllCaps(false);open.setTextSize(13);open.setTextColor(0xfff3fff8);open.setBackgroundColor(android.graphics.Color.TRANSPARENT);open.setOnClickListener(v->{try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(update.optString("url"))));}catch(ActivityNotFoundException unavailable){Toast.makeText(activity,"Open the project's GitHub Releases to update.",Toast.LENGTH_LONG).show();}});notice.addView(open,new LinearLayout.LayoutParams(0,dp(activity,48),1));
  Button close=new Button(activity);close.setText("\u00d7");close.setTextSize(22);close.setContentDescription("Dismiss optional update");close.setTextColor(0xfff3fff8);close.setBackgroundColor(android.graphics.Color.TRANSPARENT);close.setOnClickListener(v->{preferences.edit().putString("dismissed",update.optString("version")).apply();root.removeView(notice);});notice.addView(close,new LinearLayout.LayoutParams(dp(activity,48),dp(activity,48)));
  FrameLayout.LayoutParams layout=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);layout.setMargins(dp(activity,12),0,dp(activity,12),dp(activity,88));root.addView(notice,layout);
 }
}
