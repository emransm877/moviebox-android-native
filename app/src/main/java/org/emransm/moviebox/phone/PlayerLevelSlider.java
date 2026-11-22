package org.emransm.moviebox.phone;

import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;

/** Agency-specified compact, text-free volume rail. */
final class PlayerLevelSlider extends View {
    interface Change {void set(float value);}
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Change change;
    private float level,normalStep=.1f;
    void setNormalStep(float step){normalStep=Math.max(.1f,step);}
    private float step(boolean up){return level>1.001f||up&&level>=.999f?.1f:normalStep;}
    private final float density;
    PlayerLevelSlider(Context context,Change change){super(context);this.change=change;density=getResources().getDisplayMetrics().density;setFocusable(true);setClickable(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);setLevel(0);setVisibility(GONE);if(android.os.Build.VERSION.SDK_INT>=26)setDefaultFocusHighlightEnabled(false);}
    static int boostColor(float value){
        int[] colors={0xff2feda1,0xfff3d86a,0xfff49a52,0xffec6464};
        float step=Math.max(0,Math.min(1,value))*3;int index=Math.min(2,(int)step);float part=step-index;
        int a=colors[index],b=colors[index+1];return Color.rgb(Math.round(Color.red(a)+(Color.red(b)-Color.red(a))*part),Math.round(Color.green(a)+(Color.green(b)-Color.green(a))*part),Math.round(Color.blue(a)+(Color.blue(b)-Color.blue(a))*part));
    }
    void setLevel(float value){level=Math.max(0,Math.min(2,value));setContentDescription(level==0?"Volume muted":level>1?"Volume boosted":"Volume");invalidate();}
    private float top(){return 48*density;}
    private float bottom(){return getHeight()-20*density;}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float d=density,cx=getWidth()/2f,t=top(),b=bottom(),middle=(t+b)/2,y=b-(b-t)*level/2;
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xf0191a1e);canvas.drawRoundRect(0,0,getWidth(),getHeight(),26*d,26*d,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d);paint.setColor(0x24ffffff);canvas.drawRoundRect(d/2,d/2,getWidth()-d/2,getHeight()-d/2,26*d,26*d,paint);
        NavIcon icon=new NavIcon(level==0?"Muted":"Volume",0xfff5f5f5,(int)(22*d));icon.setBounds((int)(cx-11*d),(int)(15*d),(int)(cx+11*d),(int)(37*d));icon.draw(canvas);
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xff414449);RectF track=new RectF(cx-4*d,t,cx+4*d,b);canvas.drawRoundRect(track,4*d,4*d,paint);
        canvas.save();Path clip=new Path();clip.addRoundRect(track,4*d,4*d,Path.Direction.CW);canvas.clipPath(clip);
        paint.setColor(0xfff5f5f5);canvas.drawRect(cx-4*d,Math.max(y,middle),cx+4*d,b,paint);
        if(level>1){paint.setShader(new LinearGradient(0,middle,0,t,new int[]{0xff2feda1,0xfff3d86a,0xfff49a52,0xffec6464},null,Shader.TileMode.CLAMP));canvas.drawRect(cx-4*d,y,cx+4*d,middle,paint);paint.setShader(null);}canvas.restore();
        paint.setColor(0xff92969c);paint.setStrokeWidth(d);canvas.drawLine(cx-7*d,middle,cx+7*d,middle,paint);
        paint.setColor(level>1?boostColor(level-1):0xfff5f5f5);canvas.drawCircle(cx,y,6*d,paint);
        if(isFocused()){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*d);paint.setColor(0xfff5f5f5);canvas.drawRoundRect(2*d,2*d,getWidth()-2*d,getHeight()-2*d,24*d,24*d,paint);}
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN||e.getActionMasked()==MotionEvent.ACTION_MOVE){getParent().requestDisallowInterceptTouchEvent(true);change.set(Math.max(0,Math.min(2,(bottom()-e.getY())/Math.max(1,bottom()-top())*2)));return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_UP){performClick();return true;}return e.getActionMasked()==MotionEvent.ACTION_CANCEL;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName(android.widget.SeekBar.class.getName());AccessibilityNodeInfo.RangeInfo range=AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT,0,2,level);if(android.os.Build.VERSION.SDK_INT==23&&(range.getMax()!=2||range.getCurrent()!=level)){try{java.lang.reflect.Constructor<AccessibilityNodeInfo.RangeInfo> constructor=AccessibilityNodeInfo.RangeInfo.class.getDeclaredConstructor(int.class,float.class,float.class,float.class);constructor.setAccessible(true);range=constructor.newInstance(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT,0f,2f,level);}catch(ReflectiveOperationException ignored){/* Spoken state/actions remain available on a vendor-restricted legacy system. */}}info.setRangeInfo(range);info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);if(android.os.Build.VERSION.SDK_INT>=24)info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);}
    @Override public boolean performAccessibilityAction(int action,Bundle args){if(android.os.Build.VERSION.SDK_INT>=24&&action==AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId()&&args!=null){change.set(args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,level));return true;}if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){change.set(level+(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?step(true):-step(false)));return true;}return super.performAccessibilityAction(action,args);}
    @Override public boolean onKeyDown(int key,KeyEvent event){if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){change.set(level+(key==KeyEvent.KEYCODE_DPAD_UP?step(true):-step(false)));return true;}return super.onKeyDown(key,event);}
}
