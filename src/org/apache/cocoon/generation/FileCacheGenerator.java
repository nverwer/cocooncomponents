package org.apache.cocoon.generation;

import java.io.IOException;
import java.util.Date;
import java.util.Map;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.excalibur.source.ModifiableSource;
import org.apache.excalibur.source.Source;
import org.apache.excalibur.source.SourceUtil;
import org.apache.excalibur.source.TraversableSource;
import org.xml.sax.SAXException;

public class FileCacheGenerator extends FileGenerator {
  
  private String cache;
  private String lifetime;
  private long lifeSeconds;

  @Override
  public void setup(SourceResolver resolver, Map objectModel, String src, Parameters par)
  throws ProcessingException, SAXException, IOException {
    super.setup(resolver, objectModel, src, par);
    cache = par.getParameter("cache", null);
    if (cache == null) {
      throw new ProcessingException("Missing parameter: cache");
    }
    lifetime = par.getParameter("lifetime", "1 day");
    String[] lt = lifetime.split("\\s+", 2);
    lifeSeconds = Integer.parseInt(lt[0]);
    if (lifeSeconds <= 0) {
      throw new ProcessingException("Incomprehensible lifetime quantity ["+lt[0]+"] in "+lifetime);
    } else if (lt[1].matches("sec.*")) {
    } else if (lt[1].matches("min.*")) {
      lifeSeconds *= 60;
    } else if (lt[1].matches("h(ou)?r.*")) {
      lifeSeconds *= 60 * 60;
    } else if (lt[1].matches("day.*")) {
      lifeSeconds *= 60 * 60 * 24;
    } else {
      throw new ProcessingException("Incomprehensible lifetime unit ["+lt[1]+"] in "+lifetime);
    }
  }
    
  @Override
  public void generate() throws IOException, SAXException, ProcessingException {
    Source cacheSource = resolver.resolveURI(cache);
    if (cacheSource instanceof ModifiableSource) {
      ModifiableSource cacheModifiableSource = (ModifiableSource) cacheSource;
      if ((cacheSource.exists()) &&
          (new Date().getTime() - cacheModifiableSource.getLastModified() < lifeSeconds * 1000)
      ) {
        // Use the cache as the source.
        resolver.release(this.inputSource);
        this.inputSource = cacheSource;
      } else {
        // Use the given source, copy it to the cache.
        SourceUtil.copy(this.inputSource, cacheSource);
        resolver.release(cacheSource);
      }
    } else {
      throw new ProcessingException("Cannot write to cache ["+cache+"].");
    }
    super.generate();
  }

}
