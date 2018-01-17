package org.apache.cocoon.serialization;

import javax.xml.transform.OutputKeys;

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.commons.lang.StringEscapeUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * Accepts an XML document structured according to the XML representation of JSON
 * specified in [https://www.w3.org/TR/xslt-30/#json-to-xml-mapping]
 * and [https://www.w3.org/TR/xslt-30/#schema-for-json].
 * This serializer ignores the namespace, so you don't need to specify "http://www.w3.org/2005/xpath-functions".
 * 
 * Configuration parameters:
 *  - encoding: The text-encoding.
 * 
 * @author Rakensi
 *
 */
public class JsonSerializer extends TextSerializer {

  private enum JsonType {NONE, OBJECT, ARRAY, STRING, ESCAPED_STRING, NUMBER, BOOLEAN, NULL};
  private JsonType jsonType;

  @Override
  public String getMimeType() {
    return "application/json";
  }

  @Override
  public void configure(Configuration conf) throws ConfigurationException {
    super.configure(conf);
    String encoding = conf.getChild("encoding").getValue(null);
    this.format.setProperty(OutputKeys.METHOD, "text");
    this.format.setProperty(OutputKeys.ENCODING, encoding);
    this.format.setProperty(OutputKeys.INDENT, "no");
    this.format.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
  }
  
  @Override
  public void startDocument() throws SAXException {
    // The root looks like an object, to prevent a leading comma.
    jsonType = JsonType.OBJECT;
    super.startDocument();
  }

  @Override
  public void startElement(String namespaceURI, String localName, String qName, Attributes atts)
  throws SAXException {
    super.startElement(namespaceURI, localName, qName, atts);
    if (jsonType != JsonType.OBJECT && jsonType != JsonType.ARRAY) {
      // If an object or array has just been opened, don't output a comma, otherwise do.
      sendCharEvent(',');
    }
    StringBuilder sb = new StringBuilder();
    String key = atts.getValue("key");
    boolean escapedKey = "true".equals(atts.getValue("escaped-key"));
    // Check (on the first element) if there are keyed items in an array.
    if (jsonType == JsonType.ARRAY && key != null) {
      throw new SAXException("Items of an array cannot have keys.");
    }
    // If the element has a key, put it in the stringbuffer as JSON.
    if (key != null) {
      if (!escapedKey) {
        key = StringEscapeUtils.escapeJavaScript(key);
      }
      sb.append('"').append(key).append('"').append(':');
    }
    switch (localName) {
      case "map" :
        sendTextEvent(sb.append('{').toString());
        jsonType = JsonType.OBJECT;
      break;
      case "array" :
        sendTextEvent(sb.append('[').toString());
        jsonType = JsonType.ARRAY;
      break;
      case "string" :
        sendTextEvent(sb.append('"').toString());
        jsonType = "true".equals(atts.getValue("escaped")) ? JsonType.ESCAPED_STRING : JsonType.STRING;
      break;
      case "number" :
        sendTextEvent(sb.toString());
        jsonType = JsonType.NUMBER;
      break;
      case "boolean" :
        sendTextEvent(sb.toString());
        jsonType = JsonType.BOOLEAN;
      break;
      case "null" :
        sendTextEvent(sb.append("null").toString());
        jsonType = JsonType.NULL;
      break;
      default :
    }
  }
  
  @Override
  public void endElement(String namespaceURI, String localName, String qName)
  throws SAXException {
    switch (localName) {
      case "map" :
        sendCharEvent('}');
      break;
      case "array" :
        sendCharEvent(']');
      break;
      case "string" :
        sendCharEvent('"');
      break;
      default :
    }
    jsonType = JsonType.NONE;
    super.endElement(namespaceURI, localName, qName);
  }
  
  @Override
  public void characters(char[] c, int start, int len)
  throws SAXException {
    switch (jsonType) {
      case BOOLEAN :
        // Check the first character. This should allow for text to be broken into multiple characters events.
        if (c[start] == 't') sendTextEvent("true");
        else if (c[start] == 'f') sendTextEvent("false");
      break;
      case STRING :
        String escaped = StringEscapeUtils.escapeJavaScript(String.valueOf(c, start, len));
        super.characters(escaped.toCharArray(), 0, escaped.length());
      break;
      default :
        super.characters(c, start, len);
    }
  }
  
  private void sendCharEvent(char c) throws SAXException {
    super.characters(new char[] {c}, 0, 1);
  }
  
  private void sendTextEvent(String text)
  throws SAXException {
    super.characters(text.toCharArray(), 0, text.length());
  }

}
