package org.apache.cocoon.transformation;

import java.io.IOException;
import java.util.Map;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.apache.cocoon.xml.AttributesImpl;

public class SAXEventTransformer  extends AbstractTransformer {

 protected static final Attributes EMPTY_ATTRIBUTES = XMLUtils.EMPTY_ATTRIBUTES;

  public void startEntity(String name) throws SAXException {
    AttributesImpl a = new AttributesImpl();
    a.addCDATAAttribute("name", name);
    super.startElement("", "entity", "entity", a);
  }

  public void endEntity(String name) throws SAXException {
    super.endElement("", "entity", "entity");
  }

  public void comment(char[] ch, int start, int len) throws SAXException {
    super.startElement("", "comment", "comment", EMPTY_ATTRIBUTES);
    super.characters(ch, start, len);
    super.endElement("", "comment", "comment");
  }

  public void characters(char[] c, int start, int len) throws SAXException {
    super.characters(c, start, len);
  }

  public void ignorableWhitespace(char[] c, int start, int len) throws SAXException {
    // forget it
  }

  public void startPrefixMapping(String prefix, String uri) throws SAXException {
    AttributesImpl a = new AttributesImpl();
    a.addCDATAAttribute("prefix", prefix);
    a.addCDATAAttribute("uri", uri);
    super.startElement("", "prefixMapping", "prefixMapping", a);
  }

  public void endPrefixMapping(String prefix) throws SAXException {
    super.endElement("", "prefixMapping", "prefixMapping");
  }

  public void startElement(String uri, String loc, String raw, Attributes attr) throws SAXException {
    String name = "element-"+loc;
    AttributesImpl a = new AttributesImpl();
    a.addCDATAAttribute("namespace-uri", uri);
    a.addCDATAAttribute("local-name", loc);
    a.addCDATAAttribute("qualified-name", raw);
    super.startElement("", name, name, a);
    super.startElement("", "attributes", "attributes", attr);
    super.endElement("", "attributes", "attributes");
  }

  public void endElement(String uri, String loc, String raw) throws SAXException {
    String name = "element-"+loc;
    char[] elementName = raw.toCharArray();
    super.comment(elementName, 0, elementName.length);
    super.endElement("", name, name);
  }

  public void startDTD(String name, String publicId, String systemId) throws SAXException {
    AttributesImpl a = new AttributesImpl();
    a.addCDATAAttribute("name", name);
    a.addCDATAAttribute("publicId", publicId);
    a.addCDATAAttribute("systemId", systemId);
    super.startElement("", "dtd", "dtd", a);
  }

  public void endDTD() throws SAXException {
    super.endElement("", "dtd", "dtd");
  }

  public void processingInstruction(String target, String data) throws SAXException {
    AttributesImpl a = new AttributesImpl();
    a.addCDATAAttribute("target", target);
    a.addCDATAAttribute("data", data);
    super.startElement("", "processingInstruction", "processingInstruction", a);
    super.endElement("", "processingInstruction", "processingInstruction");
  }

  public void startCDATA() throws SAXException {
    super.startElement("", "cdata", "cdata", EMPTY_ATTRIBUTES);
  }

  public void endCDATA() throws SAXException {
    super.endElement("", "cdata", "cdata");
  }

  public void startDocument() throws SAXException {
    super.startDocument();
    super.startElement("", "document", "document", EMPTY_ATTRIBUTES);
  }

  public void endDocument() throws SAXException {
    super.endElement("", "document", "document");
    super.endDocument();
  }

  public void setup(SourceResolver resolver, Map objectModel, String src, org.apache.avalon.framework.parameters.Parameters par) throws ProcessingException, SAXException, IOException {
  }
  
}
