package org.apache.cocoon.serialization;

import javax.xml.transform.OutputKeys;

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.cocoon.components.serializers.encoding.Charset;
import org.apache.cocoon.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class EncodingTextSerializer extends TextSerializer {
  /**
   * Set to true after first XML element.
   * Text is not passed on before the first (root) element.
   */
  private boolean hadRootElement;

  /** The <code>Charset</code> associated with the character encoding. */
  protected Charset charset = null;

  @Override
  public void configure(Configuration conf) throws ConfigurationException {
    super.configure(conf);
    String encoding = conf.getChild("encoding").getValue(null);
    this.format.setProperty(OutputKeys.ENCODING, encoding);
    this.format.setProperty(OutputKeys.INDENT, "no");
    this.format.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
  }
  
  @Override
  public void startDocument() throws SAXException {
    this.hadRootElement = false;
    super.startDocument();
  }

  @Override
  public void startElement(String namespaceURI, String localName, String qName, Attributes atts)
  throws SAXException {
    this.hadRootElement = true;
    super.startElement(namespaceURI, localName, qName, atts);
  }
  
  @Override
  public void characters(char c[], int start, int len)
  throws SAXException {
    if (this.hadRootElement) {
      super.characters(c, start, len);
    }
  }

}
