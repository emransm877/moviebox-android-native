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
 static JSONObject asset(JSONObject release){if(release.optBoolean("draft")||release.optBoolean("prerelease"))return null;JSONArray assets=release.optJSONArray("assets");if(assets==null)return null;
  for(int n=0;n<assets.length();n++){JSONObject asset=assets.optJSONObject(n);if(asset==null||asset.optLong("size")<=0)continue;String name=asset.optString("name"),url=asset.optString("browser_download_url");
   if(!(name.startsWith("MovieBox-Android-App-")||name.startsWith("MovieBox-Android-Native-"))||!name.endsWith(".apk"))continue;
   if(validDownload(url)&&UpdateDownloadPolicy.metadata(asset.optLong("size"),asset.optString("digest")))return asset;
  }return null;
 }
 static String apk(JSONObject release){JSONObject asset=asset(release);return asset==null?"":asset.optString("browser_download_url");}
 static boolean validDownload(String value){try{URI uri=new URI(value);return "https".equals(uri.getScheme())&&"github.com".equals(uri.getHost())&&uri.getUserInfo()==null&&uri.getPort()==-1&&uri.getQuery()==null&&uri.getFragment()==null&&uri.normalize().equals(uri)&&uri.getRawPath().equals(uri.getPath())&&uri.getPath().startsWith("/"+REPOSITORY+"/releases/download/")&&uri.getPath().endsWith(".apk");}catch(Exception invalid){return false;}}
 static JSONObject evaluate(JSONObject release,String installed)throws JSONException{
  String tag=release.optString("tag_name"),normalized=tag.replaceFirst("^v", ""),status=INVALID,url="";
  if(version(tag)&&version(installed)&&!release.optBoolean("draft")&&!release.optBoolean("prerelease")){
   url=apk(release);boolean matching=false;try{matching=validDownload(url)&&new URI(url).getPath().startsWith("/"+REPOSITORY+"/releases/download/"+tag+"/");}catch(Exception invalid){}if(!matching)status=INCOMPLETE;
   else if(newer(tag,installed))status=AVAILABLE;else if(newer(installed,tag))status=OLDER;else status=CURRENT;
  }
  JSONObject result=new JSONObject().put("status",status).put("available",AVAILABLE.equals(status)).put("version",normalized).put("url",url);JSONObject asset=asset(release);
  if(asset!=null)result.put("size",asset.optLong("size")).put("sha256",asset.optString("digest").substring(7).toLowerCase(java.util.Locale.ROOT));return result;
 }
 static boolean downloadable(JSONObject update){return update!=null&&AVAILABLE.equals(update.optString("status"))&&newer(update.optString("version"),VERSION)&&validDownload(update.optString("url"))&&UpdateDownloadPolicy.metadata(update.optLong("size"),"sha256:"+update.optString("sha256"));}
 static boolean successful(JSONObject result){String status=result.optString("status");return AVAILABLE.equals(status)||CURRENT.equals(status)||OLDER.equals(status);}
 static String message(JSONObject result){String status=result.optString("status");if(CURRENT.equals(status))return "You're up to date.";if(OLDER.equals(status))return "You're up to date.";if(INCOMPLETE.equals(status))return "The release has no supported installation file yet. Try again later.";return "Update information couldn't be verified. Try again later.";}
 static final class CheckUnavailable extends java.io.IOException {final long retryAt;CheckUnavailable(String message){this(message,0);}CheckUnavailable(String message,long retryAt){super(message);this.retryAt=retryAt;}}
 static boolean limited(int status,String remaining,String retryAfter){return status==429||status==403&&("0".equals(remaining)||retryAfter!=null&&!retryAfter.trim().isEmpty());}
 static long retryAt(long now,int status,boolean rateLimited,String retryAfter,String reset){
  long cap=now+24L*60*60*1000,deadline=now+(rateLimited?15L*60*1000:60000);
  if(rateLimited){long header=0;boolean supplied=false;try{long seconds=Long.parseLong(retryAfter==null?"":retryAfter.trim());if(seconds>=0){supplied=true;header=seconds>86400?cap:now+seconds*1000;}}catch(Exception invalid){try{java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz",java.util.Locale.US);format.setLenient(false);header=format.parse(retryAfter).getTime();supplied=true;}catch(Exception ignored){}}
   long epoch=0;try{long seconds=Long.parseLong(reset==null?"":reset.trim());if(seconds>0){supplied=true;epoch=seconds>cap/1000?cap:seconds*1000;}}catch(Exception ignored){}
   long advertised=Math.max(header,epoch);if(supplied)deadline=Math.max(now+60000,advertised);
  }return Math.min(cap,Math.max(now+60000,deadline));
 }
 static String failureMessage(int status,boolean rateLimited){if(status==429||(status==403&&rateLimited))return "Update checks are temporarily limited. Try again later; you can keep watching.";if(status==404)return "No published update is available yet.";return "Update information is unavailable. Check your connection and try again.";}
 static JSONObject check()throws Exception{
  HttpsURLConnection connection=(HttpsURLConnection)new URL("https://api.github.com/repos/"+REPOSITORY+"/releases/latest").openConnection();connection.setConnectTimeout(6000);connection.setReadTimeout(8000);connection.setRequestProperty("Accept","application/vnd.github+json");connection.setRequestProperty("User-Agent","MovieBoxPhone/"+VERSION);
  try{int status=connection.getResponseCode();if(status!=200){boolean rateLimited=limited(status,connection.getHeaderField("X-RateLimit-Remaining"),connection.getHeaderField("Retry-After"));throw new CheckUnavailable(failureMessage(status,rateLimited),retryAt(System.currentTimeMillis(),status,rateLimited,connection.getHeaderField("Retry-After"),connection.getHeaderField("X-RateLimit-Reset")));}JSONObject release=new JSONObject(Utf8Json.decode(Api.read(connection.getInputStream(),1024*1024)));return evaluate(release,VERSION);}finally{connection.disconnect();}
 }
}
