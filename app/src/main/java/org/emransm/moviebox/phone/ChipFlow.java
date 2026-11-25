package org.emransm.moviebox.phone;
import android.content.Context;
import android.view.*;

/** Lightweight wrapping native chips; no nested RecyclerView or animation loop. */
final class ChipFlow extends ViewGroup {
    private final int gap;
    ChipFlow(Context context,int gap){super(context);this.gap=gap;}
    @Override protected void onMeasure(int widthSpec,int heightSpec){int width=MeasureSpec.getSize(widthSpec),available=Math.max(0,width-getPaddingLeft()-getPaddingRight()),x=0,y=0,line=0;
        for(int i=0;i<getChildCount();i++){View child=getChildAt(i);if(child.getVisibility()==GONE)continue;child.measure(MeasureSpec.makeMeasureSpec(available,MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));if(x>0&&x+child.getMeasuredWidth()>available){y+=line;line=0;x=0;}x+=child.getMeasuredWidth()+gap;line=Math.max(line,child.getMeasuredHeight());}
        setMeasuredDimension(resolveSize(width,widthSpec),resolveSize(y+line+getPaddingTop()+getPaddingBottom(),heightSpec));}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){int available=r-l-getPaddingLeft()-getPaddingRight(),x=0,y=getPaddingTop(),line=0;boolean rtl=getLayoutDirection()==LAYOUT_DIRECTION_RTL;
        for(int i=0;i<getChildCount();i++){View child=getChildAt(i);if(child.getVisibility()==GONE)continue;int width=child.getMeasuredWidth(),height=child.getMeasuredHeight();if(x>0&&x+width>available){y+=line;line=0;x=0;}int left=rtl?r-l-getPaddingRight()-x-width:getPaddingLeft()+x;child.layout(left,y,left+width,y+height);x+=width+gap;line=Math.max(line,height);}}
}
