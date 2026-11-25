package org.emransm.moviebox.phone;

import android.content.*;
import android.os.SystemClock;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static org.emransm.moviebox.phone.PhoneMediaScenarios.*;

/** Explicitly opt-in real service, isolated history. Records actual rendered-first-frame, not Activity launch. */
@RunWith(AndroidJUnit4.class)
public class LivePlaybackObservation {
 private static int episodeArgument(String name){String value=InstrumentationRegistry.getArguments().getString(name,"0");try{int parsed=Integer.parseInt(value);if(parsed<0)throw new NumberFormatException();return parsed;}catch(NumberFormatException invalid){throw new IllegalArgumentException(name+" must be a non-negative integer");}}
 private static String fieldType(JSONObject object,String key){if(!object.has(key))return "missing";Object value=object.opt(key);return value==null||value==JSONObject.NULL?"null":value instanceof JSONObject?"object":value instanceof JSONArray?"array":value instanceof Number?"number":value instanceof Boolean?"boolean":value instanceof String?"string":"other";}
 private static String linkScheme(String value){if(value==null||value.isEmpty())return "missing";int separator=value.indexOf(':');String scheme=separator<0?"relative":value.substring(0,separator).toLowerCase(Locale.ROOT);return scheme.equals("https")||scheme.equals("http")||scheme.equals("content")||scheme.equals("file")?scheme:"other";}
 /** Whitelisted metadata only: never serializes raw rows, links, tokens or IDs. */
 private static JSONObject resourceMetadata(Object response,Map<String,String> args)throws Exception{
  JSONObject out=new JSONObject().put("resolutionParameterPresent",args.containsKey("resolution")).put("requestedResolution",args.get("resolution")).put("requestedSeason",args.get("se")).put("requestedEpisode",args.get("epFrom")).put("requestedEpisodeTo",args.get("epTo")).put("requestedPagerMode",args.get("pagerMode")).put("responseType",response instanceof JSONObject?"object":response instanceof JSONArray?"array":"other");
  if(!(response instanceof JSONObject))return out;JSONObject batch=(JSONObject)response;
  for(String key:new String[]{"list","items","resources","resourceList","collectionResolutions","resolution","subjectId","se","ep","pager","requireMemberType","vipLocked"})out.put(key+"Type",fieldType(batch,key));
  out.put("batchResolution",batch.optInt("resolution",-1)).put("batchRequireMemberType",batch.optInt("requireMemberType",-1)).put("batchVipLocked",batch.optBoolean("vipLocked",false));JSONArray qualities=batch.optJSONArray("collectionResolutions"),rows=batch.optJSONArray("list"),summaries=new JSONArray(),qualitySummary=new JSONArray();
  if(qualities!=null)for(int n=0;n<Math.min(qualities.length(),24);n++){JSONObject q=qualities.optJSONObject(n);if(q!=null)qualitySummary.put(new JSONObject().put("resolution",q.optInt("resolution",-1)).put("requireMemberType",q.optInt("requireMemberType",0)).put("vipLocked",q.optBoolean("vipLocked",false)).put("membershipType",fieldType(q,"requireMemberType")));}
  out.put("advertisedQualityCount",qualities==null?0:qualities.length()).put("qualities",qualitySummary).put("resourceRowCount",rows==null?0:rows.length());
  if(rows!=null)for(int n=0;n<Math.min(rows.length(),20);n++){
   JSONObject row=rows.optJSONObject(n);if(row==null){summaries.put(new JSONObject().put("rowType","not-object"));continue;}
   JSONObject summary=new JSONObject();for(String key:new String[]{"resolution","se","ep","season","episode","requireMemberType","vipLocked","resourceLink","url","downloadUrl","size","duration"})summary.put(key+"Type",fieldType(row,key));
   for(String key:new String[]{"resolution","se","ep","season","episode","requireMemberType","size","duration"})summary.put(key,row.optLong(key,-1));
   summary.put("vipLocked",row.optBoolean("vipLocked",false)).put("resourceLinkScheme",linkScheme(row.optString("resourceLink"))).put("resourceLinkPresent",!row.optString("resourceLink").isEmpty());
   long size=row.optLong("size"),duration=row.optLong("duration");summary.put("smallMediaPolicyReject",duration>0&&size>0&&size<3145728);
   summaries.put(summary);
  }
  return out.put("rows",summaries);
 }
 private static JSONObject playInfoMetadata(Object response)throws Exception{
  JSONObject out=new JSONObject().put("responseType",response instanceof JSONObject?"object":response instanceof JSONArray?"array":"other");if(!(response instanceof JSONObject))return out;
  JSONObject batch=(JSONObject)response;for(String key:new String[]{"streams","list","items","se","ep","title","requireMemberType","vipLocked"})out.put(key+"Type",fieldType(batch,key));
  out.put("season",batch.optInt("se",-1)).put("episode",batch.optInt("ep",-1)).put("requireMemberType",batch.optInt("requireMemberType",-1)).put("vipLocked",batch.optBoolean("vipLocked",false));
  JSONArray streams=batch.optJSONArray("streams"),summary=new JSONArray();out.put("streamCount",streams==null?0:streams.length());
  if(streams!=null)for(int n=0;n<Math.min(20,streams.length());n++){JSONObject row=streams.optJSONObject(n);if(row==null){summary.put(new JSONObject().put("rowType","not-object"));continue;}JSONObject entry=new JSONObject();
   for(String key:new String[]{"id","url","signCookie","format","size","duration","resolutions","requireMemberType","vipLocked"})entry.put(key+"Type",fieldType(row,key));
   String format=row.optString("format").toLowerCase(Locale.ROOT);entry.put("format",Arrays.asList("mp4","mkv","m3u8","hls","dash","mpd","webm").contains(format)?format:"other");
   entry.put("urlScheme",linkScheme(row.optString("url"))).put("urlPresent",!row.optString("url").isEmpty()).put("signCookiePresent",!row.optString("signCookie").isEmpty()).put("size",row.optLong("size",-1)).put("duration",row.optLong("duration",-1)).put("requireMemberType",row.optInt("requireMemberType",-1)).put("vipLocked",row.optBoolean("vipLocked",false));
   String resolutions=row.optString("resolutions");if(resolutions.matches("[0-9pPxX,; /.-]{1,80}"))entry.put("resolutions",resolutions);summary.put(entry);
  }return out.put("streamMetadata",summary);
 }
 private static JSONObject detailMetadata(JSONObject detail)throws Exception{
  JSONObject out=new JSONObject();for(String key:new String[]{"hasResource","playUrl","resourceDetectors","dubs","subjectType","externalWebsite"})out.put(key+"Type",fieldType(detail,key));
  Object has=detail.opt("hasResource");if(has instanceof Boolean||has instanceof Number)out.put("hasResource",has);else if(has instanceof String&&Arrays.asList("true","false","0","1").contains(((String)has).toLowerCase(Locale.ROOT)))out.put("hasResource",has);
  JSONObject play=detail.optJSONObject("playUrl");out.put("playUrlPresent",play!=null);if(play!=null){String kind=play.optString("urlType");JSONObject safe=new JSONObject().put("urlTypeType",fieldType(play,"urlType")).put("playUrlType",fieldType(play,"playUrl")).put("urlScheme",linkScheme(play.optString("playUrl"))).put("urlPresent",!play.optString("playUrl").isEmpty());if(kind.matches("[A-Za-z0-9_ .-]{1,40}"))safe.put("urlType",kind);out.put("playUrlMetadata",safe);}
  JSONArray detectors=detail.optJSONArray("resourceDetectors"),safeDetectors=new JSONArray();out.put("resourceDetectorCount",detectors==null?0:detectors.length());
  if(detectors!=null)for(int n=0;n<Math.min(20,detectors.length());n++){
   JSONObject row=detectors.optJSONObject(n);if(row==null){safeDetectors.put(new JSONObject().put("rowType","not-object"));continue;}
   JSONObject safe=new JSONObject();for(String key:new String[]{"type","source","resolution","resourceLink","downloadUrl","resolutionList","extSubtitle"})safe.put(key+"Type",fieldType(row,key));
   safe.put("type",row.optInt("type",-1)).put("resolution",row.optInt("resolution",-1));for(String key:new String[]{"resourceLink","downloadUrl"})safe.put(key+"Scheme",linkScheme(row.optString(key))).put(key+"Present",!row.optString(key).isEmpty());
   JSONArray choices=row.optJSONArray("resolutionList"),safeChoices=new JSONArray();safe.put("resolutionCount",choices==null?0:choices.length());
   if(choices!=null)for(int j=0;j<Math.min(20,choices.length());j++){JSONObject choice=choices.optJSONObject(j);if(choice==null)continue;JSONObject entry=new JSONObject().put("resolution",choice.optInt("resolution",-1)).put("requireMemberType",choice.optInt("requireMemberType",-1)).put("vipLocked",choice.optBoolean("vipLocked",false));for(String key:new String[]{"resourceLink","downloadUrl","url"})entry.put(key+"Type",fieldType(choice,key)).put(key+"Scheme",linkScheme(choice.optString(key)));safeChoices.put(entry);}
   safe.put("resolutionMetadata",safeChoices);safeDetectors.put(safe);
  }return out.put("resourceDetectorMetadata",safeDetectors);
 }
 @Test public void actualSourceAndFirstFrame()throws Exception{
  org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveService","false").equals("true"));
  Context real=InstrumentationRegistry.getInstrumentation().getTargetContext();File owned=new File(real.getCacheDir(),"qa-live-loading-"+System.nanoTime());assertTrue(owned.mkdirs());
  Context isolated=new ContextWrapper(real){@Override public File getCacheDir(){return owned;}@Override public File getFilesDir(){return owned;}};
  boolean probeFamily=InstrumentationRegistry.getArguments().getString("liveFamilyMode","false").equals("true");
  UiCache cache=new UiCache(isolated,false,probeFamily);JSONObject report=new JSONObject();boolean observeMetadata=InstrumentationRegistry.getArguments().getString("liveMetadata","false").equals("true"),omitResolution=InstrumentationRegistry.getArguments().getString("liveOmitResolution","false").equals("true"),originalDefaults=InstrumentationRegistry.getArguments().getString("liveOriginalDefaults","false").equals("true");
  assertFalse("Use one query comparison at a time",omitResolution&&originalDefaults);final AtomicInteger probeType=new AtomicInteger();
  report.put("probeOmitResolution",omitResolution).put("probeOriginalDefaults",originalDefaults).put("probeFamilyMode",probeFamily).put("isolatedSourceCache",true);
  Api api=new Api("owned-live-playback-20261123-0000",cache){
   @Override public Object request(String path,Map<String,String> args,JSONObject body,boolean nativeApi)throws Exception{
    if(originalDefaults&&path.equals("/subject-api/resource")&&probeType.get()==1){args=new TreeMap<>(args);args.put("resolution","0");args.put("se","1");args.put("epFrom","1");args.put("epTo","1");args.put("pagerMode","0");}
    if(omitResolution&&path.equals("/subject-api/resource")){args=new TreeMap<>(args);args.remove("resolution");}
    long started=SystemClock.elapsedRealtime();Object response;
    try{response=super.request(path,args,body,nativeApi);}catch(Exception failure){if(observeMetadata&&path.equals("/subject-api/resource")){JSONObject item=new JSONObject().put("requestMs",SystemClock.elapsedRealtime()-started).put("failureClass",failure.getClass().getSimpleName());if(failure instanceof Api.ServiceHttpException)item.put("httpStatus",((Api.ServiceHttpException)failure).status);synchronized(report){JSONArray entries=report.optJSONArray("resourceMetadata");if(entries==null){entries=new JSONArray();report.put("resourceMetadata",entries);}if(entries.length()<24)entries.put(item);}}throw failure;}
    if(observeMetadata&&path.equals("/subject-api/resource")){JSONObject item=resourceMetadata(response,args).put("requestMs",SystemClock.elapsedRealtime()-started);synchronized(report){JSONArray entries=report.optJSONArray("resourceMetadata");if(entries==null){entries=new JSONArray();report.put("resourceMetadata",entries);}if(entries.length()<24)entries.put(item);}}
    return response;
   }
  };
  api.setFamilyMode(probeFamily);
  long observationStarted=SystemClock.elapsedRealtime(),stageStarted=observationStarted;String stage="selection";
  try{
   String requested=InstrumentationRegistry.getArguments().getString("liveTitle","").trim();JSONObject selected=null;
   if(requested.isEmpty()){
    List<JSONObject> home=api.home();for(JSONObject item:home)if(item.optInt("subjectType")==1){selected=item;break;}
   }else{
    report.put("requestedTitle",requested);String requestedId=InstrumentationRegistry.getArguments().getString("liveSubjectId","").trim();List<JSONObject> matches=new ArrayList<>();Set<String> matchedIds=new HashSet<>();JSONArray candidates=new JSONArray();
    for(JSONObject item:api.search(requested))if(requested.equalsIgnoreCase(item.optString("title").trim())&&(item.optInt("subjectType")==1||item.optInt("subjectType")==2)){
     String id=item.optString("subjectId").trim();if(id.isEmpty()||!matchedIds.add(id))continue;
     candidates.put(new JSONObject().put("id",id).put("title",item.optString("title")).put("subjectType",item.optInt("subjectType")));
     if(requestedId.isEmpty()||requestedId.equals(id))matches.add(item);
    }
    report.put("matchCandidates",candidates);
    assertEquals("An exact unambiguous title is required; use liveSubjectId if needed. No fallback title was selected.",1,matches.size());selected=matches.get(0);
   }
   assertNotNull("No requested playable catalogue type was found",selected);probeType.set(selected.optInt("subjectType"));report.put("selectedTitle",selected.optString("title")).put("selectionMs",SystemClock.elapsedRealtime()-stageStarted);
   stage="detail";stageStarted=SystemClock.elapsedRealtime();
   long at=SystemClock.elapsedRealtime();JSONObject detail=api.detail(selected);report.put("id",detail.optString("subjectId")).put("detailMs",SystemClock.elapsedRealtime()-at);if(observeMetadata)report.put("detailMetadata",detailMetadata(detail));
   int requestedSeason=0,requestedEpisode=0;
   if(detail.optInt("subjectType")==2){
    int suppliedSeason=episodeArgument("liveSeason"),suppliedEpisode=episodeArgument("liveEpisode");JSONArray seasons=detail.optJSONArray("seasons");boolean found=false;
    if(seasons!=null)for(int n=0;n<seasons.length()&&!found;n++){JSONObject row=seasons.optJSONObject(n);if(row==null||row.optInt("se")<1||suppliedSeason>0&&suppliedSeason!=row.optInt("se"))continue;int[] episodes=Policies.episodes(row.optString("allEp"),row.optInt("maxEp"));for(int ep:episodes)if(suppliedEpisode==0||suppliedEpisode==ep){requestedSeason=row.getInt("se");requestedEpisode=ep;found=true;break;}}
    assertTrue("No verified requested series episode was found",found);
   }
   final int season=requestedSeason,episode=requestedEpisode;report.put("season",season).put("episode",episode);
   stage="recreatedDetail";stageStarted=SystemClock.elapsedRealtime();
   Api recreated=new Api("owned-live-playback-20261123-0000",cache);recreated.setFamilyMode(probeFamily);try{at=SystemClock.elapsedRealtime();JSONObject disk=recreated.detail(selected);report.put("recreatedDetailMs",SystemClock.elapsedRealtime()-at);assertEquals(detail.optString("title"),disk.optString("title"));}finally{recreated.close();}
   stage="coldSourceLookup";stageStarted=SystemClock.elapsedRealtime();at=stageStarted;
   List<JSONObject> coldSources;
   try{coldSources=api.initialPlayback(detail,season,episode,1080);}catch(IOException unavailable){
    report.put("coldSourceLookupMs",SystemClock.elapsedRealtime()-at);
    if(InstrumentationRegistry.getArguments().getString("livePlayInfo","false").equals("true")){
     long probeStarted=SystemClock.elapsedRealtime();try{Object playInfo=api.request("/subject-api/play-info",Api.args("subjectId",detail.getString("subjectId"),"se",season,"ep",episode,"host",Api.NATIVE),null,true);report.put("playInfoMetadata",playInfoMetadata(playInfo).put("requestMs",SystemClock.elapsedRealtime()-probeStarted));}
     catch(Exception failedProbe){JSONObject safe=new JSONObject().put("requestMs",SystemClock.elapsedRealtime()-probeStarted).put("failureClass",failedProbe.getClass().getSimpleName());if(failedProbe instanceof Api.ServiceHttpException)safe.put("httpStatus",((Api.ServiceHttpException)failedProbe).status);report.put("playInfoMetadata",safe);}
    }throw unavailable;
   }
   report.put("coldSourceLookupMs",SystemClock.elapsedRealtime()-at).put("coldSourceCount",coldSources.size());assertFalse(coldSources.isEmpty());
   JSONArray resolutions=new JSONArray();for(JSONObject source:coldSources)resolutions.put(source.optInt("resolution"));report.put("returnedResolutions",resolutions);
   stage="preparedSourceLookup";stageStarted=SystemClock.elapsedRealtime();at=stageStarted;
   api.prefetchPlayback(detail,season,episode,1080);report.put("prepareSourceMs",SystemClock.elapsedRealtime()-at);at=SystemClock.elapsedRealtime();List<JSONObject> sources=api.initialPlayback(detail,season,episode,1080);report.put("preparedLookupMs",SystemClock.elapsedRealtime()-at).put("sources",sources.size()).put("clickUsesPreparedSources",true);assertFalse(sources.isEmpty());
   stage="renderFirstFrame";stageStarted=SystemClock.elapsedRealtime();
   final JSONObject movie=detail;final AtomicLong firstFrame=new AtomicLong();final long[] clicked={0};
   try(ActivityScenario<PhoneActivity> scenario=ActivityScenario.launch(PhoneActivity.class)){
    main(scenario,a->{call(a,"releasePlayer",new Class[]{});put(a,"generation",(Integer)field(a,"generation")+1);((ThreadPoolExecutor)field(a,"network")).getQueue().clear();((Library)field(a,"library")).close();put(a,"library",new Library(isolated));put(a,"api",api);put(a,"subject",movie);put(a,"season",season);put(a,"episode",episode);clicked[0]=SystemClock.elapsedRealtime();call(a,"playSelection",new Class[]{JSONObject.class,int.class,int.class,boolean.class},movie,season,episode,true);});
    final ExoPlayer[] attached={null};long deadline=SystemClock.elapsedRealtime()+25000;
    while(firstFrame.get()==0&&SystemClock.elapsedRealtime()<deadline){main(scenario,a->{ExoPlayer player=(ExoPlayer)field(a,"player");if(player!=null&&player!=attached[0]){attached[0]=player;player.addListener(new Player.Listener(){@Override public void onRenderedFirstFrame(){firstFrame.compareAndSet(0,SystemClock.elapsedRealtime());}@Override public void onPlayerError(PlaybackException error){try{report.put("playerErrorCode",error.errorCode);Throwable cause=error;for(int depth=0;cause!=null&&depth<8;depth++,cause=cause.getCause())if(cause instanceof androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException){report.put("playerHttpStatus",((androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)cause).responseCode);break;}}catch(JSONException ignored){}}});}});Thread.sleep(15);}
    assertTrue("Real source must render a video frame",firstFrame.get()>0);report.put("clickToFirstFrameMs",firstFrame.get()-clicked[0]);
    main(scenario,a->{ExoPlayer player=(ExoPlayer)field(a,"player");report.put("width",player.getVideoSize().width).put("height",player.getVideoSize().height);assertNull(player.getPlayerError());player.pause();});
   }
   report.put("status","PASS").put("totalMs",SystemClock.elapsedRealtime()-observationStarted);
  }catch(Exception|AssertionError failure){
   report.put("status","FAIL").put("failureStage",stage).put("failureStageMs",SystemClock.elapsedRealtime()-stageStarted).put("totalMs",SystemClock.elapsedRealtime()-observationStarted).put("failureClass",failure.getClass().getSimpleName());
   Throwable cause=failure;for(int depth=0;cause!=null&&depth<8;depth++,cause=cause.getCause())if(cause instanceof Api.ServiceHttpException){report.put("serviceHttpStatus",((Api.ServiceHttpException)cause).status);break;}
   throw failure;
  }finally{
   api.close();try(FileOutputStream out=new FileOutputStream(new File(real.getCacheDir(),"live-playback-observation.json"))){out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));}
   Thread.sleep(300);DetailLoadingScenarios.remove(owned,owned.getCanonicalPath());
  }
 }
}
