package org.apache.cocoon.transformation;

import org.xml.sax.SAXException;

import java.util.regex.*;

public class LigatureExpanderTransformer extends AbstractSAXPipelineTransformer {
  
  private static final String ligatures = "\u0132\u0133";
  private static final String[] expands = {"IJ", "ij"};
  
  private static final Pattern ligaturesPattern = Pattern.compile("["+ligatures+"]");

  public boolean hasLigatures(String text) {
    return ligaturesPattern.matcher(text).find();
  }
  
  public String expandLigatures(String text) {
    StringBuilder sb = new StringBuilder();
    int textLength = text.length();
    for (int i = 0; i < textLength; i++) {
      char c = text.charAt(i);
      int pos = ligatures.indexOf(c);
      if (pos > -1) {
        sb.append(expands[pos]);
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }
  
  /* (non-Javadoc)
   * @see org.apache.cocoon.transformation.AbstractSAXTransformer#characters(char[], int, int)
   */
  @Override
  public void characters(char[] text, int start, int length) throws SAXException {
    String textString = String.valueOf(text, start, length);
    if (!hasLigatures(textString)) {
      super.characters(text, start, length);
    } else {
      textString = expandLigatures(textString);
      super.characters(textString.toCharArray(), 0, textString.length());
    }
  }

  /* (non-Javadoc)
   * @see org.apache.cocoon.transformation.AbstractSAXTransformer#comment(char[], int, int)
   */
  @Override
  public void comment(char[] text, int start, int length) throws SAXException {
    String textString = String.valueOf(text, start, length);
    if (!hasLigatures(textString)) {
      super.comment(text, start, length);
    } else {
      textString = expandLigatures(textString);
      super.comment(textString.toCharArray(), 0, textString.length());
    }
  }

}
