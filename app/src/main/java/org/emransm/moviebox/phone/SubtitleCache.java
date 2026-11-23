package org.emransm.moviebox.phone;
import android.util.AtomicFile;import java.io.*;import java.nio.*;import java.nio.charset.*;import java.util.*;import java.util.regex.Pattern;
/** Bounded on-device captions. The file used by the player is never evicted or cleared. */
final class SubtitleCache {
 static final int MAX_FILE_BYTES=2*1024*1024,MAX_FILES=40;static final long MAX_BYTES=24L*1024*1024,MAX_AGE_MS=30L*24*60*60*1000;
 private static final Pattern CUE=Pattern.compile("(?m)(?:\\d{1,3}:)?\\d{2}:\\d{2}[,.]\\d{3}[ \\t]*-->[ \\t]*(?:\\d{1,3}:)?\\d{2}:\\d{2}[,.]\\d{3}");
 private final File directory;private File active;
 SubtitleCache(File directory){this.directory=directory;}
 synchronized void protect(File file){active=file;}
 private boolean same(File a,File b){return a!=null&&b!=null&&a.getAbsolutePath().equals(b.getAbsolutePath());}
 private static String key(String identity){try{return Api.md5(identity.getBytes(StandardCharsets.UTF_8));}catch(Exception unavailable){throw new IllegalStateException("Subtitle cache key unavailable",unavailable);}}
 private static boolean caption(File file){String name=file.getName();return name.matches("[a-fA-F0-9]{32}\\.(srt|vtt)");}
 static String decoded(byte[] bytes)throws IOException{
  if(bytes.length==0||bytes.length>MAX_FILE_BYTES)throw new IOException("Subtitle file is empty or too large.");Charset charset=StandardCharsets.UTF_8;int offset=0;
  if(bytes.length>=2&&bytes[0]==(byte)0xff&&bytes[1]==(byte)0xfe){charset=StandardCharsets.UTF_16LE;offset=2;}else if(bytes.length>=2&&bytes[0]==(byte)0xfe&&bytes[1]==(byte)0xff){charset=StandardCharsets.UTF_16BE;offset=2;}
  String text=charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes,offset,bytes.length-offset)).toString();if(!CUE.matcher(text).find())throw new IOException("This subtitle file has no readable timed captions. Try another subtitle.");return text;
 }
 synchronized File find(String identity){String hash=key(identity);for(String extension:new String[]{".vtt",".srt"}){File file=new File(directory,hash+extension);if(file.isFile()&&System.currentTimeMillis()-file.lastModified()>MAX_AGE_MS&&!same(file,active)){file.delete();continue;}if(file.isFile()&&file.length()>0&&file.length()<=MAX_FILE_BYTES){try(InputStream input=new FileInputStream(file)){byte[] head=new byte[(int)Math.min(file.length(),16384)];int count=input.read(head);Charset charset=count>=2&&head[0]==(byte)0xff&&head[1]==(byte)0xfe?StandardCharsets.UTF_16LE:count>=2&&head[0]==(byte)0xfe&&head[1]==(byte)0xff?StandardCharsets.UTF_16BE:StandardCharsets.UTF_8;if(count>0&&CUE.matcher(new String(head,0,count,charset)).find()){file.setLastModified(System.currentTimeMillis());return file;}}catch(IOException ignored){}if(!same(file,active))file.delete();}}return null;}
 synchronized File put(String identity,String extension,byte[] bytes)throws IOException{
  String text=decoded(bytes).replaceFirst("^\\uFEFF","");if(text.trim().startsWith("WEBVTT"))extension=".vtt";if(!extension.equals(".srt")&&!extension.equals(".vtt"))throw new IOException("Unsupported subtitle type.");if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Subtitles could not be saved. Free some storage and retry.");
  File target=new File(directory,key(identity)+extension);AtomicFile atomic=new AtomicFile(target);FileOutputStream output=null;
  try{output=atomic.startWrite();output.write(bytes);atomic.finishWrite(output);}catch(IOException failure){if(output!=null)atomic.failWrite(output);throw new IOException("Subtitles could not be saved. Free some storage and retry.",failure);}
  target.setLastModified(System.currentTimeMillis());trim(target,System.currentTimeMillis());return target;
 }
 synchronized void trim(){trim(null,System.currentTimeMillis());}
 private void trim(File pending,long now){File[] all=directory.listFiles();if(all==null)return;List<File> files=new ArrayList<>();for(File file:all){if(caption(file)&&file.isFile())files.add(file);else if(file.isFile()&&file.getName().matches("[a-fA-F0-9]{32}\\.(srt|vtt)\\.(new|bak)"))file.delete();}Collections.sort(files,(a,b)->Long.compare(a.lastModified(),b.lastModified()));long total=0;for(File f:files)total+=f.length();int count=files.size();for(File file:files){if(same(file,active)||same(file,pending))continue;if(file.length()<=0||file.length()>MAX_FILE_BYTES||now-file.lastModified()>MAX_AGE_MS||count>MAX_FILES||total>MAX_BYTES){long length=file.length();if(file.delete()){count--;total-=length;}}}}
 synchronized int clear(){int removed=0;File[] all=directory.listFiles();if(all!=null)for(File f:all)if(caption(f)&&f.isFile()&&!same(f,active)&&f.delete())removed++;return removed;}
 File directory(){return directory;}
}
