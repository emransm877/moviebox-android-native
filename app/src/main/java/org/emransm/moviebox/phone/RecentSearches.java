package org.emransm.moviebox.phone;
import java.text.Normalizer;
import java.util.*;

/** Small private history, independent of My list and playback progress. */
final class RecentSearches {
    static final int LIMIT=12;
    static String clean(String text){if(text==null)return "";String value=Normalizer.normalize(text.trim(),Normalizer.Form.NFC);return value.length()<=200?value:"";}
    static List<String> add(List<String> previous,String keyword){String value=clean(keyword);List<String> result=new ArrayList<>();if(!value.isEmpty())result.add(value);for(String item:previous){String entry=clean(item);if(entry.isEmpty())continue;boolean duplicate=false;for(String seen:result)if(seen.equalsIgnoreCase(entry)){duplicate=true;break;}if(!duplicate)result.add(entry);if(result.size()>=LIMIT)break;}return result;}
}
