package org.apache.cocoon.transformation;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.Normalizer;
import java.util.Map;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * Transform strings inside the trigger-tags into low ASCII, removing accents, diacritics, etcetera.
 * 
 * The transformer takes the following parameters:
 * <ul>
 *   <li>namespaceURI The namespace of the trigger element.</li>
 *   <li>elementTag The name of the trigger element. Default is the root element (normalize all text).</li>
 * </ul>
 */
public class NormalizeToASCIITransformer extends AbstractSAXPipelineTransformer {
  
  private String elementTag;
  private int normalize;

  /*
   * @see org.apache.cocoon.transformation.AbstractSAXTransformer#setup(org.apache.cocoon.environment.SourceResolver, java.util.Map, java.lang.String, org.apache.avalon.framework.parameters.Parameters)
   */
  @Override
  public void setup(SourceResolver resolver, Map objectModel, String src, Parameters params)
  throws ProcessingException, SAXException, IOException {
    super.setup(resolver, objectModel, src, params);
    this.elementTag = params.getParameter("elementTag", "");
    this.normalize = 0;
  }
  
  private boolean trigger(String name) {
    return elementTag.length() == 0 || name.equals(elementTag);
  }

  /*
   * @see org.apache.cocoon.transformation.AbstractSAXTransformer#startTransformingElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
   */
  @Override
  public void startTransformingElement(String uri, String name, String raw, Attributes attr)
      throws ProcessingException, IOException, SAXException {
    if (trigger(name)) {
      sendStartElementEventNS(name, attr);
      this.normalize += 1;
    } else if (ignoreEventsCount == 0) {
      contentHandler.startElement(uri, name, raw, attr);
    }
  }

  /*
   * @see org.apache.cocoon.transformation.AbstractSAXTransformer#endTransformingElement(java.lang.String, java.lang.String, java.lang.String)
   */
  @Override
  public void endTransformingElement(String uri, String name, String raw)
      throws ProcessingException, IOException, SAXException {
    if (trigger(name)) {
      this.normalize -= 1;
      sendEndElementEventNS(name);
    } else if (ignoreEventsCount == 0) {
      contentHandler.endElement(uri, name, raw);
    }
  }

  /*
   * @see org.apache.cocoon.transformation.AbstractSAXTransformer#characters(char[], int, int)
   */
  @Override
  public void characters(char[] text, int start, int length) throws SAXException {
    if (this.normalize > 0) {
      text = normalize(new String(text, start, length)).toCharArray();
      super.characters(text, 0, text.length);
    } else {
      super.characters(text, start, length);
    }
  }
  
  // Normalize a string to ASCII [http://stackoverflow.com/a/2097224/1021892].
  private static String normalize(String s) {
    s = s.replaceAll("[\u2018`\u2032\u00B4\u2019]", "'")
         .replaceAll("[\u201C\u201D]", "\"")
         .replaceAll("[\u2010\u2011\u2012\u2013\u2014\u2015\u2212]",  "-");
    String sn = Normalizer.normalize(s, Normalizer.Form.NFKD);
    String sr;
    try {
      sr = new String(sn.replaceAll("[\\p{InCombiningDiacriticalMarks}\\p{IsLm}\\p{IsSk}]+", "").getBytes("ascii"), "ascii");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
    return sr;
  }

}
