package org.apache.cocoon.transformation;

import java.io.IOException;
import java.util.Map;

import org.apache.avalon.framework.parameters.ParameterException;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class PipelineSlowdownTransformer extends AbstractTransformer
{
  int msecs = 100;
  
  public void setup(SourceResolver resolver, Map objectModel, String src, Parameters par)
    throws ProcessingException, SAXException, IOException
  {
    if (par.isParameter("duration")) {
      try {
        msecs = Integer.parseInt(par.getParameter("duration"));
      } catch (NumberFormatException e) {
        throw new ProcessingException(e);
      } catch (ParameterException e) {
        throw new ProcessingException(e);
      }
    }
  }
  public void startElement(String namespaceURI, String lName, String qName, Attributes attributes)
    throws SAXException
  {
    super.startElement(namespaceURI, lName, qName, attributes);
    System.err.print('.');
    try {Thread.sleep(msecs);} catch (Exception e) {}
  }
}
