package org.apache.cocoon.transformation;

import java.io.IOException;
import java.util.Map;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.xml.sax.SAXException;

public class NoOpTransformer extends AbstractTransformer {

  public void setup(SourceResolver resolver, Map objectModel, String src,
      Parameters par) throws ProcessingException, SAXException, IOException {
  }

}
