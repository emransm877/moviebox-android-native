package org.emransm.moviebox.phone;

import java.nio.file.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.Test;
import static org.junit.Assert.*;

public class SourceEncodingTest {
    @Test public void shippedSourceAndResourcesAreUtf8WithoutKnownBrokenLiterals() throws Exception {
        Path root=Paths.get("src","main");
        if(!Files.isDirectory(root))root=Paths.get("app","src","main");
        assertTrue("Locate actual app source",Files.isDirectory(root));
        Pattern damaged=Pattern.compile("\u00c2[\u0080-\u00bf]|\u00e2\u20ac|\u00c3[\u00a0-\u00bf]|\ufffd");
        try(Stream<Path> paths=Files.walk(root)) {
            for(Path path:(Iterable<Path>)paths.filter(Files::isRegularFile)::iterator) {
                String name=path.getFileName().toString();
                if(!(name.endsWith(".java")||name.endsWith(".xml")||name.endsWith(".json")))continue;
                String source=Utf8Json.decode(Files.readAllBytes(path));
                assertFalse("Unexpected mojibake literal in "+path,damaged.matcher(source).find());
            }
        }
    }
}
