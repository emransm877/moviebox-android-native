package org.emransm.moviebox.phone;

import androidx.media3.common.Format;
import java.util.*;

/** Truthful display labels only; indices always retain the provider's track order. */
public final class AudioLabels {
    private AudioLabels(){}
    private static final Set<String> UNKNOWN=new HashSet<>(Arrays.asList("","und","unknown","undefined","zxx","null","n/a","none"));
    public static List<String> tracks(List<Format> formats,Locale displayLocale){
        List<String> labels=new ArrayList<>(),languages=new ArrayList<>();for(Format format:formats){labels.add(format==null?null:format.label);languages.add(format==null?null:format.language);}return names(labels,languages,displayLocale);
    }
    /** Pure Java seam shared with release unit tests; does not instantiate Android media objects. */
    static List<String> names(List<String> labels,List<String> languages,Locale displayLocale){
        if(labels.size()!=languages.size())throw new IllegalArgumentException("Each audio label needs its matching language");
        Locale display=displayLocale==null?Locale.getDefault():displayLocale;List<String> out=new ArrayList<>();Map<String,Integer> counts=new HashMap<>();
        for(int i=0;i<labels.size();i++){String name=name(labels.get(i),languages.get(i),i+1,display);out.add(name);String key=name.toLowerCase(Locale.ROOT);Integer previous=counts.get(key);counts.put(key,previous==null?1:previous+1);}
        for(int i=0;i<out.size();i++){String name=out.get(i);if(counts.get(name.toLowerCase(Locale.ROOT))>1)out.set(i,name+" \u00b7 Track "+(i+1));}return out;
    }
    public static String editionName(String label,String language,int ordinal,Locale displayLocale){return name(label,language,Math.max(1,ordinal),displayLocale==null?Locale.getDefault():displayLocale)+" \u00b7 Separate edition";}
    private static String name(String label,String language,int ordinal,Locale display){
        String supplied=clean(label);
        if(!UNKNOWN.contains(supplied.toLowerCase(Locale.ROOT))){
            // A raw language code is metadata, rather than a useful human label.
            if(supplied.matches("[a-z]{2,3}([_-][A-Za-z0-9]{2,8})*")){String translated=languageName(supplied,display);if(translated!=null)return translated;}
            else return supplied;
        }
        String translated=languageName(language,display);return translated==null?"Audio track "+ordinal:translated;
    }
    private static String clean(String text){return text==null?"":text.trim();}
    private static String languageName(String raw,Locale display){
        String code=clean(raw).replace('_','-');if(UNKNOWN.contains(code.toLowerCase(Locale.ROOT)))return null;
        try{
            Locale locale=new Locale.Builder().setLanguageTag(code).build();String language=locale.getLanguage();
            if(UNKNOWN.contains(language.toLowerCase(Locale.ROOT)))return null;
            String translated=locale.getDisplayLanguage(display);
            // Java leaves unrecognized/private codes unchanged. Do not invent a name.
            if(translated.isEmpty()||translated.equalsIgnoreCase(language))return null;
            String name=locale.getDisplayName(display);return name.isEmpty()?null:name;
        }catch(IllformedLocaleException invalid){return null;}
    }
}
