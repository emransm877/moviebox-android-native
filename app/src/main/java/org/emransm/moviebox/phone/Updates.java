package org.emransm.moviebox.phone;

import org.json.*;
import java.net.URI;
import javax.net.ssl.HttpsURLConnection;
import java.net.URL;

/** Optional release metadata checks. No installation or silent APK downloads. */
final class Updates {
 static final String REPOSITORY="emransm877/moviebox-android-native",VERSION=BuildConfig.VERSION_NAME;
 static boolean newer(String candidate,String installed){try{
  String a=candidate.replaceFirst("^v", ""),b=installed.replaceFirst("^v", "");if(!a.matches("[0-9]+\\.[0-9]+\\.[0-9]+")||!b.matches("[0-9]+\\.[0-9]+\\.[0-9]+"))return false;
  String[] first=a.split("\\."),second=b.split("\\.");for(int n=0;n<3;n++){int compare=Integer.compare(Integer.parseInt(first[n]),Integer.parseInt(second[n]));if(compare!=0)return compare>0;}return false;
 }catch(RuntimeException invalid){return false;}}
 static String apk(JSONObject release){if(release.optBoolean("draft")||release.optBoolean("prerelease"))return "";JSONArray assets=release.optJSONArray("assets");if(assets==null)return "";
  for(int n=0;n<assets.length();n++){JSONObject asset=assets.optJSONObject(n);if(asset==null||asset.optLong("size")<=0)continue;String name=asset.optString("name"),url=asset.optString("browser_download_url");
   if(!(name.startsWith("MovieBox-Android-App-")||name.startsWith("MovieBox-Android-Native-"))||!name.endsWith(".apk"))continue;
   try{URI uri=new URI(url);if("https".equals(uri.getScheme())&&"github.com".equals(uri.getHost())&&uri.getUserInfo()==null&&uri.getPort()==-1&&uri.getPath().startsWith("/"+REPOSITORY+"/releases/download/"))return url;}catch(Exception invalid){}
  }return "";
 }
 static JSONObject check()throws Exception{
  HttpsURLConnection connection=(HttpsURLConnection)new URL("https://api.github.com/repos/"+REPOSITORY+"/releases/latest").openConnection();connection.setConnectTimeout(6000);connection.setReadTimeout(8000);connection.setRequestProperty("Accept","application/vnd.github+json");connection.setRequestProperty("User-Agent","MovieBoxPhone/"+VERSION);
  try{if(connection.getResponseCode()!=200)throw new java.io.IOException("Update information is unavailable. Try again later.");JSONObject release=new JSONObject(Utf8Json.decode(Api.read(connection.getInputStream(),1024*1024)));String version=release.optString("tag_name");String url=apk(release);return new JSONObject().put("available",!url.isEmpty()&&newer(version,VERSION)).put("version",version.replaceFirst("^v", "")).put("url",url);}finally{connection.disconnect();}
 }
}
