package org.apache.cocoon.transformation;

import java.io.IOException;
import java.util.Map;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.xml.sax.SAXException;

public class MessageLoggerTransformer extends AbstractSAXTransformer {
  
  private String message;

  @Override
  public void setup(SourceResolver resolver, Map objectModel, String src,
      Parameters params) throws ProcessingException, SAXException, IOException {
    message = params.getParameter("message", "");
    super.setup(resolver, objectModel, src, params);
  }

  @Override
  public void startDocument() throws SAXException {
    this.getLogger().info(message);
    super.startDocument();
  }

}
