package org.apache.cocoon.transformation;

import java.io.IOException;
import java.util.Map;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.xml.sax.SAXException;

/**
 * According to [http://en.wikipedia.org/wiki/HTML_decimal_character_rendering]:
 * HTML forbids the use of the characters with Universal Character Set/Unicode code points
 * * 0000 to 0008
 * * 0011 to 0012
 * * 0014 to 0031
 * * 0127
 * * 0128 to 0159 (see [http://en.wikipedia.org/wiki/Windows-1252])
 * * 55296 to 57343
 * These characters are not even allowed by reference. That is, you are not even allowed to write them as numeric character references.
 *
 * This transformer replaces these characters by legal equivalent unicode characters as much as possible.
 * This is needed, because the HTML serializer and/or the saxon transformer have problems when these characters occur in XHTML documents.
 */
public class HTMLCharacterTransformer extends AbstractTransformer {

  @Override
  public void setup(SourceResolver resolver, Map objectModel, String src, Parameters par)
      throws ProcessingException, SAXException, IOException {
  }

  @Override
  public void characters(char[] c, int start, int len) throws SAXException {
    for (int i = start; i < start+len; ++i) {
      if (c[i] <= 8) {
        c[i] = 0;
      } else if (c[i] >= 14 && c[i] <= 31) {
        c[i] = 0;
      } else if (c[i] >= 55296 && c[i] <= 57343) {
        c[i] = 0;
      } else {
        switch(c[i]) {
        case 11: c[i] = 0; break;
        case 12: c[i] = 0; break;
        case 127: c[i] = 0; break;
        case 128: c[i] = '\u20ac'; break;
        case 129: c[i] = 0; break;
        case 130: c[i] = '\u201a'; break;
        case 131: c[i] = '\u0192'; break;
        case 132: c[i] = '\u201e'; break;
        case 133: c[i] = '\u2026'; break;
        case 134: c[i] = '\u2020'; break;
        case 135: c[i] = '\u2021'; break;
        case 136: c[i] = '\u02c6'; break;
        case 137: c[i] = '\u2030'; break;
        case 138: c[i] = '\u0160'; break;
        case 139: c[i] = '\u2039'; break;
        case 140: c[i] = '\u0152'; break;
        case 141: c[i] = 0; break;
        case 142: c[i] = '\u017d'; break;
        case 143: c[i] = 0; break;
        case 144: c[i] = 0; break;
        case 145: c[i] = '\u2018'; break;
        case 146: c[i] = '\u2019'; break;
        case 147: c[i] = '\u201c'; break;
        case 148: c[i] = '\u201d'; break;
        case 149: c[i] = '\u2022'; break;
        case 150: c[i] = '\u2013'; break;
        case 151: c[i] = '\u2014'; break;
        case 152: c[i] = '\u02dc'; break;
        case 153: c[i] = '\u2122'; break;
        case 154: c[i] = '\u0161'; break;
        case 155: c[i] = '\u203a'; break;
        case 156: c[i] = '\u0153'; break;
        case 157: c[i] = 0; break;
        case 158: c[i] = '\u017e'; break;
        case 159: c[i] = '\u0178'; break;
        }
      }
    }
    super.characters(c, start, len);
  }

}
