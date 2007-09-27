package org.apache.cocoon.transformation;

import java.io.IOException;
import java.util.Map;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class PipelineSlowdownTransformer extends AbstractTransformer
{
  public void setup(SourceResolver resolver, Map objectModel, String src, Parameters par)
    throws ProcessingException, SAXException, IOException
  {
  }
  public void startElement(String namespaceURI, String lName, String qName, Attributes attributes)
    throws SAXException
  {
    super.startElement(namespaceURI, lName, qName, attributes);
    System.err.print('.');
    try {Thread.sleep(100);} catch (Exception e) {}
  }
}
