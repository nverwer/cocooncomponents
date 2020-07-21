package org.apache.cocoon.transformation;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Map;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.xml.sax.SAXException;

/**
 * If your text content comes from a source that uses the wrong character encoding,
 * you will see things like "CosmÃ©tique" instead of "Cosmétique".
 * In that case, the source says it uses ISO-8859-1 or Windows-1252 encoding, but the text is UTF-8 encoded.
 * What about co�rdinatie? (Displayed as UTF-8, but actual encoding is different.)
 * This transformer tries to fix this and other situation by decoding and re-encoding text content.
 * It has two parameters:
 * <dl>
 *   <dt>sourceEncoding</dt> <dd>The source's actual encoding (default is "ISO-8859-1")</dd>
 *   <dt>targetEncoding</dt> <dd>The declared or expected encoding (default is "UTF-8")</dd>
 * </dl>
 *
 * @author Rakensi
 */
public class CharacterEncodingRepairTransformer extends AbstractTransformer {

  private String sourceEncoding;
  private String targetEncoding;

  @Override
  public void setup(SourceResolver resolver, Map objectModel, String src, Parameters par)
      throws ProcessingException, SAXException, IOException {
    sourceEncoding = par.getParameter("sourceEncoding", "ISO-8859-1");
    targetEncoding = par.getParameter("targetEncoding", "UTF-8");
  }

  @Override
  public void characters(char[] chars, int start, int len) throws SAXException {
    try {
      String inputString = new String(chars, start, len);
      // Encode the string into bytes using the source encoding.
      byte[] inputBytes = inputString.getBytes(sourceEncoding);
      // Decode the bytes into a string using the target encoding.
      String outputString = new String(inputBytes, targetEncoding);
      super.characters(outputString.toCharArray(), 0, outputString.length());
    } catch (UnsupportedEncodingException e) {
      throw new SAXException(e);
    }
  }

}
