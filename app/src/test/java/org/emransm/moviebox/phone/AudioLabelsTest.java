package org.emransm.moviebox.phone;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AudioLabelsTest {
    @Test public void undefinedAndUnrecognizedMetadataNeverInventLanguage(){
        List<String> absent=Arrays.asList(null,"","und","unknown","undefined","zxx","qaa","xx");
        List<String> actual=AudioLabels.names(absent,absent,Locale.ENGLISH);
        for(int i=0;i<actual.size();i++)assertEquals("Audio track "+(i+1),actual.get(i));
    }
    @Test public void meaningfulSourceLabelsAndUnicodeTakePrecedence(){
        assertEquals(Arrays.asList("Director commentary","বাংলা অডিও","日本語 解説"),AudioLabels.names(Arrays.asList(" Director commentary ","বাংলা অডিও","日本語 解説"),Arrays.asList("en","en","en"),Locale.ENGLISH));
        assertEquals(Arrays.asList("AAC","Hi-Fi"),AudioLabels.names(Arrays.asList("AAC","Hi-Fi"),Arrays.asList("en","en"),Locale.ENGLISH));
    }
    @Test public void regionalAndScriptTagsRetainTheirIdentity(){
        List<String> names=AudioLabels.names(Arrays.asList(null,null,null),Arrays.asList("en-US","pt-BR","zh-Hant"),Locale.ENGLISH);
        assertTrue(names.get(0).contains("English"));assertTrue(names.get(0).contains("United States"));assertTrue(names.get(1).contains("Portuguese"));assertTrue(names.get(1).contains("Brazil"));assertTrue(names.get(2).contains("Chinese"));assertTrue(names.get(2).contains("Traditional"));
    }
    @Test public void rawLabelCodesAndUnknownLabelUseValidLanguageMetadata(){
        assertEquals(Arrays.asList("English (United States)","French","Portuguese (Brazil)"),AudioLabels.names(Arrays.asList("en-US","und","unknown"),Arrays.asList(null,"fr","pt_BR"),Locale.ENGLISH));
    }
    @Test public void duplicateNamesStayDistinctAndKeepStableIndices(){
        assertEquals(Arrays.asList("English \u00b7 Track 1","English \u00b7 Track 2","Commentary"),AudioLabels.names(Arrays.asList(null,"English","Commentary"),Arrays.asList("en","en","en"),Locale.ENGLISH));
    }
    @Test public void malformedLanguageFallsBackAndSeparateEditionIsExplicit(){
        assertEquals(Collections.singletonList("Audio track 1"),AudioLabels.names(Collections.singletonList(null),Collections.singletonList("not a tag"),Locale.ENGLISH));
        assertEquals("Audio track 3 \u00b7 Separate edition",AudioLabels.editionName("und",null,3,Locale.ENGLISH));
        assertTrue(AudioLabels.editionName(null,"fr-CA",1,Locale.ENGLISH).contains("Canada"));
    }
    @Test public void displayLocaleIsHonoredWithoutChangingTrackOrder(){
        List<String> labels=AudioLabels.names(Arrays.asList(null,null),Arrays.asList("fr","de"),Locale.FRENCH);assertTrue(labels.get(0).toLowerCase(Locale.ROOT).startsWith("fran"));assertTrue(labels.get(1).toLowerCase(Locale.ROOT).startsWith("allemand"));
    }
}
