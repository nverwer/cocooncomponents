package org.apache.cocoon.transformation;

import java.io.IOException;
import java.util.Map;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.xml.sax.SAXException;

public class MessageLoggerTransformer extends AbstractSAXTransformer {
  
  private String message;
  private String target;

  @Override
  public void setup(SourceResolver resolver, Map objectModel, String src, Parameters params)
      throws ProcessingException, SAXException, IOException {
    if (params != null) {
      target = params.getParameter("target", "");
      message = params.getParameter("message", "");
    } else {
      this.getLogger().warn("No parameters given to MessageLoggerTransformer.");
      target = "";
      message = "No message given.";
    }
    super.setup(resolver, objectModel, src, params);
  }

  @Override
  public void startDocument() throws SAXException {
    if (target.length() == 0 || target.matches(".*\\blog\\b.*"))
      this.getLogger().info(message);
    if (target.matches(".*\\bconsole\\b.*"))
      System.out.println(message);
    super.startDocument();
  }

}
