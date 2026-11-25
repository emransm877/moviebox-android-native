package org.emransm.moviebox.phone;

import org.json.JSONObject;
import java.util.Locale;

/** Explicit maturity metadata only; ordinary titles/genres are never keyword-censored. */
final class AdultContentPolicy {
    static boolean matureRating(String value){
        if(value==null)return false;
        String rating=value.trim().toUpperCase(Locale.ROOT).replace(" ","").replace("_","-");
        return rating.equals("18")||rating.equals("18+")||rating.equals("18PLUS")||rating.equals("R18")||rating.equals("R18+")||rating.equals("NC-17")||rating.equals("NC17")||rating.equals("TV-MA")||rating.equals("TVMA")||rating.equals("X")||rating.equals("XXX");
    }
    static boolean markedAdult(JSONObject item){
        if(item==null)return false;
        for(String key:new String[]{"isAdult","adult","is_adult","adultOnly","isAdultContent"}){
            Object value=item.opt(key);if(Boolean.TRUE.equals(value)||value instanceof Number&&((Number)value).intValue()==1||value instanceof String&&(((String)value).equalsIgnoreCase("true")||value.equals("1")))return true;
        }
        for(String key:new String[]{"contentRating","ageRating","maturityRating","certificate","certification"}){
            Object value=item.opt(key);if(value instanceof String&&matureRating((String)value))return true;
            if(value instanceof Number&&((Number)value).intValue()>=18)return true;
        }
        JSONObject rating=item.optJSONObject("contentRating");return rating!=null&&(matureRating(rating.optString("rating"))||matureRating(rating.optString("value")));
    }
}
