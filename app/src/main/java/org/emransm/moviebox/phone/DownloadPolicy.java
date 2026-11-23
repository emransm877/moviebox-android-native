package org.emransm.moviebox.phone;
import java.net.URI;import java.util.Locale;
/** Truthful single-file downloads; no segmented offline claims or stored source URLs. */
public final class DownloadPolicy {
 private DownloadPolicy(){}
 public static String extension(String address,String mime,String format){
  String path;try{URI uri=new URI(address);if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)return "";path=uri.getPath()==null?"":uri.getPath().toLowerCase(Locale.ROOT);}catch(Exception bad){return "";}
  String type=(mime==null?"":mime).split(";",2)[0].trim().toLowerCase(Locale.ROOT),kind=(format==null?"":format).trim().toLowerCase(Locale.ROOT);
  if(path.endsWith(".m3u8")||path.endsWith(".mpd")||type.contains("mpegurl")||type.contains("dash")||kind.matches(".*(hls|dash|m3u8|mpd).*"))return "";
  if(type.equals("video/mp4")||kind.equals("mp4")||path.endsWith(".mp4"))return ".mp4";
  if(type.equals("video/webm")||kind.equals("webm")||path.endsWith(".webm"))return ".webm";
  if(type.equals("video/x-matroska")||kind.equals("mkv")||path.endsWith(".mkv"))return ".mkv";
  return "";
 }
 public static int allowedNetworkTypes(boolean mobile){return mobile?3:2;}
 public static boolean terminal(int state){return state==8||state==16||state==0;}
 public static String status(int state,int reason){
  if(state==8)return "Ready to play";if(state==2)return "Downloading";if(state==1)return "Queued";
  if(state==4){if(reason==3)return "Waiting for Wi-Fi";if(reason==2)return "Connection interrupted. Waiting to retry";if(reason==1)return "Waiting for a connection";return "Waiting for the system to resume";}
  if(state==16){switch(reason){case 1006:return "Not enough storage. Free space, then retry.";case 1007:return "Saved folder unavailable. Check phone storage, then retry.";case 1008:return "The video could not resume. Retry to get a fresh source.";case 1009:return "A saved file already exists. Choose a new download.";case 1001:return "The file could not be saved. Check phone storage, then retry.";case 1002:return "The server response could not be read. Retry for a fresh source.";case 1004:case 1005:return "The video server could not finish the download. Retry later.";default:if(reason==401||reason==403||reason==404||reason==410)return "The source is no longer available. Retry to refresh it.";if(reason==429)return "The server is busy. Wait a moment before retrying.";if(reason>=500&&reason<=599)return "The video server is unavailable. Retry later.";return "The download could not finish. Check connection and storage, then retry.";}}
  if(state==0)return "Saved file unavailable. Download it again.";return "Could not check the download. Trying again shortly.";
 }
}
