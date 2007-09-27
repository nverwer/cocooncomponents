package org.apache.cocoon.transformation;

import java.io.IOException;
import java.util.Map;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class CharacterEventConcatenatorTransformer extends AbstractTransformer {
  
  private StringBuffer collectedCharacters;
  
  private void outputCollectedCharacters() throws SAXException {
    if (collectedCharacters.length() > 0) {
      char[] allOfThem = new char[collectedCharacters.length()];
      collectedCharacters.getChars(0, allOfThem.length, allOfThem, 0);
      super.characters(allOfThem, 0, allOfThem.length);
      collectedCharacters.delete(0, allOfThem.length);
    }
  }

  @Override
  public void setup(SourceResolver resolver, Map objectModel, String src,
      Parameters par) throws ProcessingException, SAXException, IOException {
    // We don't need it.
  }

  @Override
  public void startDocument() throws SAXException {
    collectedCharacters = new StringBuffer();
    super.startDocument();
  }

  @Override
  public void characters(char[] c, int start, int len) throws SAXException {
    collectedCharacters.append(c, start, len);
  }

  @Override
  public void comment(char[] ch, int start, int len) throws SAXException {
    outputCollectedCharacters();
    super.comment(ch, start, len);
  }

  @Override
  public void endElement(String uri, String loc, String raw)
      throws SAXException {
    outputCollectedCharacters();
    super.endElement(uri, loc, raw);
  }

  @Override
  public void processingInstruction(String target, String data)
      throws SAXException {
    outputCollectedCharacters();
    super.processingInstruction(target, data);
  }

  @Override
  public void startElement(String uri, String loc, String raw, Attributes a)
      throws SAXException {
    outputCollectedCharacters();
    super.startElement(uri, loc, raw, a);
  }

}
