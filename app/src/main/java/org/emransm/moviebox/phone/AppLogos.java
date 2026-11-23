package org.emransm.moviebox.phone;

import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Local icon preference and API23-compatible launcher aliases. No process restart or user-data reset. */
public final class AppLogos {
    private AppLogos() {}
    static final String STORE="moviebox-app-icon", KEY="selected";
    public static final String ORIGINAL="original";
    static final String[] IDS={ORIGINAL,"prism-play","orbit-m","cinema-fold","portal-box","aurora-m","solar-play","monolith","film-wave","infinity-box","lunar-frame"};
    static final String[] NAMES={"Original","Prism Play","Orbit M","Cinema Fold","Portal Box","Aurora M","Solar Play","Monolith","Film Wave","Infinity Box","Lunar Frame"};
    private static final int[] PREVIEWS={R.drawable.phone_icon,R.drawable.app_logo_01_safe,R.drawable.app_logo_02_safe,R.drawable.app_logo_03_safe,R.drawable.app_logo_04_safe,R.drawable.app_logo_05_safe,R.drawable.app_logo_06_safe,R.drawable.app_logo_07_safe,R.drawable.app_logo_08_safe,R.drawable.app_logo_09_safe,R.drawable.app_logo_10_safe};

    static int index(String id){for(int n=0;n<IDS.length;n++)if(IDS[n].equals(id))return n;return 0;}
    static String normalized(String id){return IDS[index(id)];}
    static String alias(int index){return "org.emransm.moviebox.phone.Launcher"+(index==0?"Original":String.format(java.util.Locale.ROOT,"%02d",index));}
    private static SharedPreferences preferences(Context context){return context.getSharedPreferences(STORE,Context.MODE_PRIVATE);}
    public static String current(Context context){try{return normalized(preferences(context).getString(KEY,ORIGINAL));}catch(ClassCastException invalid){return ORIGINAL;}}
    public static String name(Context context){return NAMES[index(current(context))];}
    public static int preview(Context context){return PREVIEWS[index(current(context))];}
    /** Decode raster previews to their display size, not ten full-resolution launcher images. */
    private static Drawable previewDrawable(Context context,int resource){
        if(resource==R.drawable.phone_icon)return context.getDrawable(resource);
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        BitmapFactory.decodeResource(context.getResources(),resource,options);
        int target=Math.min(512,Math.max(128,dp(context,72)));options.inSampleSize=1;
        while(Math.max(options.outWidth,options.outHeight)/(options.inSampleSize*2)>=target)options.inSampleSize*=2;
        options.inJustDecodeBounds=false;
        Bitmap bitmap=BitmapFactory.decodeResource(context.getResources(),resource,options);
        return bitmap==null?context.getDrawable(R.drawable.phone_icon):new BitmapDrawable(context.getResources(),bitmap);
    }
    public static Drawable drawable(Context context){
        Drawable value=previewDrawable(context,preview(context));
        if(!(value instanceof BitmapDrawable))return value;
        Bitmap bitmap=((BitmapDrawable)value).getBitmap();int left=bitmap.getWidth(),top=bitmap.getHeight(),right=-1,bottom=-1;
        for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++)if(Color.alpha(bitmap.getPixel(x,y))>32){left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x);bottom=Math.max(bottom,y);}
        if(right<left)return value;
        // Remove empty canvas only for the small header/watermark; leave launcher safe padding intact.
        left=Math.max(0,left-2);top=Math.max(0,top-2);right=Math.min(bitmap.getWidth()-1,right+2);bottom=Math.min(bitmap.getHeight()-1,bottom+2);
        return new BitmapDrawable(context.getResources(),Bitmap.createBitmap(bitmap,left,top,right-left+1,bottom-top+1));
    }

    private static boolean enabled(PackageManager manager,ComponentName component,int n){int state=manager.getComponentEnabledSetting(component);return state==PackageManager.COMPONENT_ENABLED_STATE_ENABLED||(state==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT&&n==0);}
    /** Enable the replacement first so the launcher never has zero valid entries. */
    private static void apply(Context context,int selected){
        PackageManager manager=context.getPackageManager();
        ComponentName chosen=new ComponentName(context.getPackageName(),alias(selected));
        if(!enabled(manager,chosen,selected))manager.setComponentEnabledSetting(chosen,PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP);
        for(int n=0;n<IDS.length;n++)if(n!=selected){ComponentName other=new ComponentName(context.getPackageName(),alias(n));if(enabled(manager,other,n))manager.setComponentEnabledSetting(other,PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP);}
    }
    /** Called on normal startup; reconciles only inconsistent alias state after an interrupted change/update. */
    public static void restore(Context context){try{apply(context,index(current(context)));}catch(RuntimeException ignored){/* Keep the reachable existing launcher entry if platform rejects a change. */}}
    public static void select(Context context,String id){
        String chosen=normalized(id),previous=current(context);
        try{
            apply(context,index(chosen));
            if(!preferences(context).edit().putString(KEY,chosen).commit())throw new IllegalStateException("The app icon could not be saved.");
        }catch(RuntimeException failure){try{apply(context,index(previous));preferences(context).edit().putString(KEY,previous).commit();}catch(RuntimeException ignored){}throw failure;}
    }
    private static int dp(Context context,float amount){return Math.round(amount*context.getResources().getDisplayMetrics().density);}
    private static GradientDrawable surface(Context context,int color,int stroke){GradientDrawable value=new GradientDrawable();value.setColor(color);value.setCornerRadius(dp(context,14));if(stroke!=Color.TRANSPARENT)value.setStroke(dp(context,2),stroke);return value;}
    private static TextView label(Context context,String text,int size,int color){TextView value=new TextView(context);value.setText(text);value.setTextSize(size);value.setTextColor(color);return value;}

    /** Compact native selector. Callbacks run only after a successfully persisted launcher change. */
    public static Dialog show(Activity activity,int background,int panel,int ink,int accent,int muted,Runnable onChanged){
        Dialog dialog=new Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(activity,16),dp(activity,14),dp(activity,16),dp(activity,12));body.setBackground(surface(activity,background,Color.TRANSPARENT));
        LinearLayout heading=new LinearLayout(activity);heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=label(activity,"App icon",21,ink);title.setTypeface(null,Typeface.BOLD);heading.addView(title,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));title.setMinHeight(dp(activity,48));title.setGravity(Gravity.CENTER_VERTICAL);
        Button close=new Button(activity);close.setText("\u00d7");close.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,24);close.setPadding(0,0,0,0);close.setIncludeFontPadding(false);close.setGravity(Gravity.CENTER);close.setTextColor(muted);close.setBackgroundColor(Color.TRANSPARENT);close.setContentDescription("Close app icon choices");close.setOnClickListener(v->dialog.dismiss());heading.addView(close,new LinearLayout.LayoutParams(dp(activity,48),dp(activity,48)));body.addView(heading);
        TextView note=label(activity,"Choose your look. Your launcher may take a moment to refresh.",12,muted);note.setPadding(0,0,0,dp(activity,12));body.addView(note);
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(false);scroll.setClipToPadding(false);scroll.setPadding(0,0,0,dp(activity,4));
        GridLayout grid=new GridLayout(activity);int columns=activity.getResources().getConfiguration().fontScale>1.3f?2:3;grid.setColumnCount(columns);grid.setUseDefaultMargins(false);String selected=current(activity);
        // Show the ten new designs first, with Original retained as an explicit reversible option.
        for(int ordinal=0;ordinal<IDS.length;ordinal++){
            final int n=ordinal==IDS.length-1?0:ordinal+1;boolean active=IDS[n].equals(selected);
            LinearLayout card=new LinearLayout(activity);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(Gravity.CENTER);card.setPadding(dp(activity,5),dp(activity,10),dp(activity,5),dp(activity,8));card.setBackground(surface(activity,Color.TRANSPARENT,active?accent:Color.TRANSPARENT));card.setFocusable(true);card.setClickable(true);card.setSelected(active);card.setContentDescription(NAMES[n]+" app icon"+(active?", selected":", choose"));
            ImageView image=new ImageView(activity);image.setImageDrawable(previewDrawable(activity,PREVIEWS[n]));image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);card.addView(image,new LinearLayout.LayoutParams(dp(activity,64),dp(activity,64)));
            TextView name=label(activity,NAMES[n],11,active?accent:ink);name.setGravity(Gravity.CENTER);name.setMaxLines(Integer.MAX_VALUE);name.setMinHeight(dp(activity,30));LinearLayout.LayoutParams nameLp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);nameLp.topMargin=dp(activity,6);card.addView(name,nameLp);
            card.setOnFocusChangeListener((view,focused)->view.setBackground(surface(activity,Color.TRANSPARENT,focused||view.isSelected()?accent:Color.TRANSPARENT)));
            card.setOnClickListener(v->{try{select(activity,IDS[n]);dialog.dismiss();if(onChanged!=null)onChanged.run();Toast.makeText(activity,"App icon changed",Toast.LENGTH_SHORT).show();}catch(RuntimeException error){Toast.makeText(activity,"Couldn't change the app icon. Please try again.",Toast.LENGTH_LONG).show();}});
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams(GridLayout.spec(ordinal/columns),GridLayout.spec(ordinal%columns,1f));lp.width=0;lp.height=ViewGroup.LayoutParams.WRAP_CONTENT;card.setMinimumHeight(dp(activity,118));lp.setMargins(dp(activity,3),dp(activity,3),dp(activity,3),dp(activity,3));grid.addView(card,lp);
        }
        scroll.addView(grid,new ScrollView.LayoutParams(-1,-2));body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        dialog.setContentView(body);Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.setDimAmount(.5f);window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);}
        dialog.show();
        if(window!=null){int width=Math.min(dp(activity,440),activity.getResources().getDisplayMetrics().widthPixels-dp(activity,24));int height=Math.min(dp(activity,640),(int)(activity.getResources().getDisplayMetrics().heightPixels*.85f));window.setLayout(width,height);window.setGravity(Gravity.CENTER);}
        return dialog;
    }
}
