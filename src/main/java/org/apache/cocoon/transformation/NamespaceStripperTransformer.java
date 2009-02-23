/* This transformer removes unnecessary namespace-prefix events.
 * It works for elements as well as attributes with namespaces.
*/
package org.apache.cocoon.transformation;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class NamespaceStripperTransformer extends AbstractTransformer {
  
  private Map<String, Integer> prefixes;
  private Stack<Stack<String>> attributeNamespaces;
  
  public void setup(SourceResolver resolver, Map objectModel, String src, org.apache.avalon.framework.parameters.Parameters par)
    throws ProcessingException, SAXException, IOException
  {
    prefixes = new HashMap<String, Integer>();
    attributeNamespaces = new Stack<Stack<String>>();
  }
  
  private void enterPrefix(String prefix, String uri) throws SAXException {
    if (!prefixes.containsKey(prefix) || prefixes.get(prefix) == 0) {
      prefixes.put(prefix, 1);
      super.startPrefixMapping(prefix, uri);
    } else {
      prefixes.put(prefix, prefixes.get(prefix)+1);
    }
  }
  
  private void leavePrefix(String prefix) throws SAXException {
    prefixes.put(prefix, prefixes.get(prefix)-1);
    if (prefixes.get(prefix) == 0) {
      prefixes.remove(prefix);
      super.endPrefixMapping(prefix);
    }
  }

  public void startPrefixMapping(String prefix, String uri) throws SAXException {
    // We'll do this ourselves, thank you.
  }

  public void endPrefixMapping(String prefix) throws SAXException {
    // We'll do this ourselves, thank you.
  }

  public void startElement(String uri, String loc, String raw, Attributes a) throws SAXException {
    if (uri != null && !uri.equals("") && raw.contains(":")) {
      String prefix = raw.substring(0, raw.indexOf(':'));
      enterPrefix(prefix, uri);
    }
    Stack<String> attrNS = null;
    for (int i = 0; i < a.getLength(); ++i) {
      String auri = a.getURI(i);
      if (uri != null && !auri.equals("")) {
        String prefix = a.getQName(i).substring(0, a.getQName(i).indexOf(':'));
        if (attrNS == null) attrNS = new Stack<String>();
        attrNS.push(prefix);
        enterPrefix(prefix, auri);
      }
    }
    attributeNamespaces.push(attrNS);
    super.startElement(uri, loc, raw, a);
  }

  public void endElement(String uri, String loc, String raw) throws SAXException {
    super.endElement(uri, loc, raw);
    if (uri != null && !uri.equals("") && raw.contains(":")) {
      String prefix = raw.substring(0, raw.indexOf(':'));
      leavePrefix(prefix);
    }
    Stack<String> attrNS = attributeNamespaces.pop();
    if (attrNS != null)
      while(!attrNS.empty()) {
        leavePrefix(attrNS.pop());
      }
  }
  
}
