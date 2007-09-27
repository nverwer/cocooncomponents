package org.apache.cocoon.transformation;

import java.io.*;
import java.util.Map;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class TeeTransformer extends AbstractTransformer
{

  private String src;
  private PrintStream tee;

  public void setup(SourceResolver resolver, Map objectModel, String src, Parameters par)
    throws ProcessingException, SAXException, IOException
  {
    this.src = src;
  }

  public void startDocument() throws SAXException {
    try {
      tee = new PrintStream(new FileOutputStream(src), true);
    } catch (IOException e) {
      tee = System.err;
      tee.println("Cannot open "+src+": "+e.getMessage()+"\nWriting to standard-error stream.\n");
    }
    super.startDocument();
  }
  public void endDocument() throws SAXException {
    super.endDocument();
    tee.close();
  }
  public void characters(char[] ch, int start, int length) throws SAXException {
    String pcdata = String.copyValueOf(ch, start, length).replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
    tee.println(pcdata);
    super.characters(ch, start, length);
  }
  public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
    String attrs = "";
    for (int i = 0; i < atts.getLength(); ++i) {
      attrs += " "+atts.getQName(i)+"=\""+atts.getValue(i).replaceAll("\"", "&quot;")+"\"";
    }
    tee.println("<"+qName+attrs+">");
    super.startElement(namespaceURI, localName, qName, atts);
  }
  public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
    tee.println("</"+qName+">");
    super.endElement(namespaceURI, localName, qName);
  }
  public void comment(char[] ch, int start, int length) throws SAXException {
    tee.println("<!-- "+String.copyValueOf(ch, start, length)+" -->");
    super.comment(ch, start, length);
  }
  public void processingInstruction(String target, String data) throws SAXException {
    tee.println("<?"+target+" "+data+" ?>");
    super.processingInstruction(target, data);
  }

}