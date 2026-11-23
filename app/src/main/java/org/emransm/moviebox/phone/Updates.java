package org.emransm.moviebox.phone;

import org.json.*;
import java.net.URI;
import javax.net.ssl.HttpsURLConnection;
import java.net.URL;

/** Optional release metadata checks. No installation or silent APK downloads. */
final class Updates {
 static final String REPOSITORY="emransm877/moviebox-android-native",VERSION=BuildConfig.VERSION_NAME;
 static final String AVAILABLE="available",CURRENT="current",OLDER="older",INCOMPLETE="incomplete",INVALID="invalid";
 static boolean version(String value){try{String normalized=value.replaceFirst("^v","");if(!normalized.matches("[0-9]+\\.[0-9]+\\.[0-9]+"))return false;for(String part:normalized.split("\\."))Integer.parseInt(part);return true;}catch(RuntimeException invalid){return false;}}
 static boolean newer(String candidate,String installed){try{
  String a=candidate.replaceFirst("^v", ""),b=installed.replaceFirst("^v", "");if(!a.matches("[0-9]+\\.[0-9]+\\.[0-9]+")||!b.matches("[0-9]+\\.[0-9]+\\.[0-9]+"))return false;
  String[] first=a.split("\\."),second=b.split("\\.");for(int n=0;n<3;n++){int compare=Integer.compare(Integer.parseInt(first[n]),Integer.parseInt(second[n]));if(compare!=0)return compare>0;}return false;
 }catch(RuntimeException invalid){return false;}}
 static String apk(JSONObject release){if(release.optBoolean("draft")||release.optBoolean("prerelease"))return "";JSONArray assets=release.optJSONArray("assets");if(assets==null)return "";
  for(int n=0;n<assets.length();n++){JSONObject asset=assets.optJSONObject(n);if(asset==null||asset.optLong("size")<=0)continue;String name=asset.optString("name"),url=asset.optString("browser_download_url");
   if(!(name.startsWith("MovieBox-Android-App-")||name.startsWith("MovieBox-Android-Native-"))||!name.endsWith(".apk"))continue;
   if(validDownload(url))return url;
  }return "";
 }
 static boolean validDownload(String value){try{URI uri=new URI(value);return "https".equals(uri.getScheme())&&"github.com".equals(uri.getHost())&&uri.getUserInfo()==null&&uri.getPort()==-1&&uri.getQuery()==null&&uri.getFragment()==null&&uri.getPath().startsWith("/"+REPOSITORY+"/releases/download/")&&uri.getPath().endsWith(".apk");}catch(Exception invalid){return false;}}
 static JSONObject evaluate(JSONObject release,String installed)throws JSONException{
  String tag=release.optString("tag_name"),normalized=tag.replaceFirst("^v", ""),status=INVALID,url="";
  if(version(tag)&&version(installed)&&!release.optBoolean("draft")&&!release.optBoolean("prerelease")){
   url=apk(release);boolean matching=false;try{matching=validDownload(url)&&new URI(url).getPath().startsWith("/"+REPOSITORY+"/releases/download/"+tag+"/");}catch(Exception invalid){}if(!matching)status=INCOMPLETE;
   else if(newer(tag,installed))status=AVAILABLE;else if(newer(installed,tag))status=OLDER;else status=CURRENT;
  }
  return new JSONObject().put("status",status).put("available",AVAILABLE.equals(status)).put("version",normalized).put("url",url);
 }
 static boolean successful(JSONObject result){String status=result.optString("status");return AVAILABLE.equals(status)||CURRENT.equals(status)||OLDER.equals(status);}
 static String message(JSONObject result){String status=result.optString("status");if(CURRENT.equals(status))return "You're using the latest published version.";if(OLDER.equals(status))return "No newer published version was found.";if(INCOMPLETE.equals(status))return "The release has no supported installation file yet. Try again later.";return "Update information couldn't be verified. Try again later.";}
 static JSONObject check()throws Exception{
  HttpsURLConnection connection=(HttpsURLConnection)new URL("https://api.github.com/repos/"+REPOSITORY+"/releases/latest").openConnection();connection.setConnectTimeout(6000);connection.setReadTimeout(8000);connection.setRequestProperty("Accept","application/vnd.github+json");connection.setRequestProperty("User-Agent","MovieBoxPhone/"+VERSION);
  try{if(connection.getResponseCode()!=200)throw new java.io.IOException("Update information is unavailable. Try again later.");JSONObject release=new JSONObject(Utf8Json.decode(Api.read(connection.getInputStream(),1024*1024)));return evaluate(release,VERSION);}finally{connection.disconnect();}
 }
}
