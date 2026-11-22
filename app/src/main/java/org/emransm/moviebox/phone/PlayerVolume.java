package org.emransm.moviebox.phone;

import android.content.Context;
import android.media.AudioManager;
import android.media.audiofx.LoudnessEnhancer;

/** Session-scoped loudness boost, with normal device volume below unity. */
final class PlayerVolume {
    private final AudioManager audio;
    private LoudnessEnhancer enhancer;
    private int session;
    private float requested=1, gain=1;
    PlayerVolume(Context context){audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);}
    static int millibels(float multiplier){return Math.round((float)(2000*Math.log10(Math.max(1,Math.min(2,multiplier)))));}
    float level(){int max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);return max<=0?0:Math.min(2,audio.getStreamVolume(AudioManager.STREAM_MUSIC)/(float)max*gain);}
    boolean setLevel(float value){
        if(Float.isNaN(value)||Float.isInfinite(value))return false;
        requested=Math.max(0,Math.min(2,value));
        try{int max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);audio.setStreamVolume(AudioManager.STREAM_MUSIC,Math.round(Math.min(1,requested)*max),0);return apply();}
        catch(RuntimeException unavailable){requested=Math.min(1,requested);clearEffect();return false;}
    }
    void adjust(int direction){float current=level();int max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);float step=current>1.001f||direction>0&&current>=.999f?.08f:1f/Math.max(1,max);setLevel(current+(direction>0?step:-step));}
    float normalStep(){return 1f/Math.max(1,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));}
    void attach(int id){if(session!=id){clearEffect();session=id;}apply();}
    private boolean apply(){
        if(requested<=1){clearEffect();return true;}
        if(session<=0)return false;
        try{if(enhancer==null)enhancer=new LoudnessEnhancer(session);enhancer.setTargetGain(millibels(requested));if(enhancer.setEnabled(true)!=android.media.audiofx.AudioEffect.SUCCESS||!enhancer.getEnabled()||!enhancer.hasControl())throw new IllegalStateException("Audio boost not enabled");gain=requested;return true;}
        catch(RuntimeException unsupported){clearEffect();requested=1;return false;}
    }
    private void clearEffect(){gain=1;if(enhancer!=null){try{enhancer.release();}catch(RuntimeException ignored){}enhancer=null;}}
    void detach(){clearEffect();session=0;}
    void close(){requested=1;detach();}
}
