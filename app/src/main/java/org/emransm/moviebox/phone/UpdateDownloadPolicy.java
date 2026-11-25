package org.emransm.moviebox.phone;

import java.net.URI;
import java.util.*;

/** Bounds and identity rules shared by the downloader and its isolated tests. */
final class UpdateDownloadPolicy {
    static final long MAX_BYTES=150L*1024*1024,MAX_MILLIS=120000;
    static boolean metadata(long size,String digest){return size>0&&size<=MAX_BYTES&&digest!=null&&digest.matches("sha256:[0-9a-fA-F]{64}");}
    static boolean redirect(String url){try{URI uri=new URI(url);return "https".equals(uri.getScheme())&&uri.getUserInfo()==null&&uri.getFragment()==null&&uri.getPort()==-1&&Arrays.asList("github.com","release-assets.githubusercontent.com","objects.githubusercontent.com","github-releases.githubusercontent.com").contains(uri.getHost());}catch(Exception invalid){return false;}}
    static boolean identity(String expectedPackage,String archivePackage,String expectedVersion,String archiveVersion,long installedCode,long archiveCode,Set<String> installedSigners,Set<String> archiveSigners){
        return expectedPackage!=null&&expectedPackage.equals(archivePackage)&&expectedVersion!=null&&expectedVersion.equals(archiveVersion)
            &&archiveCode>installedCode&&installedSigners!=null&&!installedSigners.isEmpty()&&installedSigners.equals(archiveSigners);
    }
}
