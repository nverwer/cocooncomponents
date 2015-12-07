package org.apache.cocoon.transformation;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.components.modules.input.InputModuleHelper;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.transformation.helpers.TextRecorder;
import org.apache.cocoon.xml.SaxBuffer;
import org.apache.cocoon.xml.XMLUtils;
import org.apache.cocoon.xml.SaxBuffer.EndElement;
import org.apache.cocoon.xml.SaxBuffer.StartElement;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * This variation on the AbstractSAXTransformer modifies the sendXYZ methods,
 * so that they send to the next component in the pipeline.
 * Contrary to what you would expect, that is not what the AbstractSAXTransformer does.
 * It also fixes the broken behaviour of endTextRecording().
 */
public class AbstractSAXPipelineTransformer extends AbstractSAXTransformer {
  
  /**
   * The current prefix for our namespace.
   */
  private String ourPrefix;

  /**
   * @see org.apache.cocoon.transformation.AbstractSAXTransformer#setup(org.apache.cocoon.environment.SourceResolver, java.util.Map, java.lang.String, org.apache.avalon.framework.parameters.Parameters)
   */
  @Override
  public void setup(SourceResolver resolver, Map objectModel, String src, Parameters params) throws ProcessingException, SAXException, IOException {
    super.setup(resolver, objectModel, src, params);
    this.ourPrefix = null;
  }
  
  /**
   * @see org.xml.sax.ContentHandler#startPrefixMapping
   */
  public void startPrefixMapping(String prefix, String uri)
  throws SAXException {
    if (this.namespaceURI.equals(uri)) {
      this.ourPrefix = prefix;
    }
    super.startPrefixMapping(prefix, uri);
  }

  /**
   * Start recording of serialized xml.
   * All events are converted to an xml string which can be retrieved by endSerializedXMLRecording.
   * @param format The format for the serialized output. If <CODE>null</CODE> is specified, the default format is used.
   */
  public void startSerializedXMLRecording(Properties format)
      throws SAXException {
    if (getLogger().isDebugEnabled()) {
      getLogger().debug("Start serialized XML recording. Format=" + format);
    }
    this.stack.push(format == null ? XMLUtils.createPropertiesForXML(false) : format);
    startSAXRecording();
  }

  /**
   * Start recording of SAX events. All incoming events are recorded and not forwarded.
   * The resulting XMLizable can be obtained by the matching {@link #endSAXRecording} call.
   */
  public void startSAXRecording()
  throws SAXException {
    addRecorder(new NamespacePrefixRepeatingSaxBuffer(this));
  }

  /**
   * Stop recording of text and return the recorded information.
   * @return The String, NOT trimmed (as in the overridden implementation). I like my spaces, thank you.
   */
  @Override
  public String endTextRecording() throws SAXException {
    sendEndPrefixMapping();
    TextRecorder recorder = (TextRecorder) removeRecorder();
    String text = recorder.getAllText();
    if (getLogger().isDebugEnabled()) {
        getLogger().debug("End text recording. Text=" + text);
    }
    return text;
  }

  /**
   * Send SAX events to the next pipeline component.
   * @param text The string containing the information.
   */
  public void sendCommentEvent(String text)
  throws SAXException {
    lexicalHandler.comment(text.toCharArray(), 0, text.length());
  }

  /**
   * Send SAX events to the next pipeline component.
   * @param text The string containing the information.
   */
  public void sendTextEvent(String text)
  throws SAXException {
    contentHandler.characters(text.toCharArray(), 0, text.length());
  }

  /**
   * Send SAX events to the next pipeline component.
   * @param localname The name of the event.
   */
  public void sendStartElementEvent(String localname)
  throws SAXException {
    contentHandler.startElement("", localname, localname, EMPTY_ATTRIBUTES);
  }

  /**
   * Send SAX events to the next pipeline component.
   * @param localname The name of the event.
   */
  public void sendStartElementEventNS(String localname)
  throws SAXException {
    String name = this.ourPrefix != null ? this.ourPrefix + ':' + localname : localname;
    contentHandler.startElement(this.namespaceURI, localname, name, EMPTY_ATTRIBUTES);
  }

  /**
   * Send SAX events to the next pipeline component.
   * @param localname The name of the event.
   * @param attr The Attributes of the element
   */
  public void sendStartElementEvent(String localname, Attributes attr)
  throws SAXException {
    contentHandler.startElement("", localname, localname, attr);
  }

  /**
   * Send SAX events to the next pipeline component.
   * @param localname The name of the event.
   * @param attr The Attributes of the element
   */
  public void sendStartElementEventNS(String localname, Attributes attr)
  throws SAXException {
    String name = this.ourPrefix != null ? this.ourPrefix + ':' + localname : localname;
    contentHandler.startElement(this.namespaceURI, localname, name, attr);
  }

  /**
   * Send SAX events to the next pipeline component.
   * The element has no namespace.
   * @param localname The name of the event.
   */
  public void sendEndElementEvent(String localname)
  throws SAXException {
    contentHandler.endElement("", localname, localname);
  }

  /**
   * Send SAX events to the next pipeline component.
   * @param localname The name of the event.
   */
  public void sendEndElementEventNS(String localname)
  throws SAXException {
    String name = this.ourPrefix != null ? this.ourPrefix + ':' + localname : localname;
    contentHandler.endElement(this.namespaceURI, localname, name);
  }
  
  /**
   * Way too simple parser to interpolate input module expressions.
   * @param value A string, possibly containing input module expressions.
   * @return The interpolated string.
   */
  protected String interpolateModules (String value) {
    InputModuleHelper imh = new InputModuleHelper();
    imh.setup(manager);
    Pattern modulePattern = Pattern.compile("\\{([^\\{\\}:]+):([^\\{\\}]+)\\}");
    Matcher moduleMatcher = modulePattern.matcher(value);
    StringBuffer sb = new StringBuffer();
    while (moduleMatcher.find()) {
      String moduleName = moduleMatcher.group(1);
      String accessor = moduleMatcher.group(2);
      Object moduleValue = imh.getAttribute(objectModel, moduleName, accessor, null);
      if (moduleValue != null) moduleMatcher.appendReplacement(sb, moduleValue.toString());
    }
    moduleMatcher.appendTail(sb);
    imh.releaseAll();
    return sb.toString();
  }

  public class NamespacePrefixRepeatingSaxBuffer extends SaxBuffer {
    private static final long serialVersionUID = 1L;
    
    private AbstractSAXTransformer transformer;
    
    public NamespacePrefixRepeatingSaxBuffer(AbstractSAXTransformer transformer) {
      this.transformer = transformer;
    }

    public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
      transformer.sendStartPrefixMapping();
      saxbits.add(new StartElement(namespaceURI, localName, qName, atts));
    }

    public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
      saxbits.add(new EndElement(namespaceURI, localName, qName));
      transformer.sendEndPrefixMapping();
    }
    
  }
}
