package org.apache.cocoon.transformation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.components.modules.input.InputModuleHelper;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.transformation.helpers.TextRecorder;
import org.apache.cocoon.xml.SaxBuffer;
import org.apache.cocoon.xml.XMLConsumer;
import org.apache.cocoon.xml.XMLUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.excalibur.xml.sax.XMLizable;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 * This variation on the AbstractSAXTransformer modifies the sendXYZ methods,
 * so that they send to the next component in the pipeline.
 * Contrary to what you would expect, that is not what the AbstractSAXTransformer does.
 * It also fixes the broken behaviour of TextRecording, SerializedXMLRecording and SAXRecording.
 */
public class AbstractSAXPipelineTransformer extends AbstractSAXTransformer {

  /**
   * The namespaces and their prefixes.
   * Every list item is a 2-item array of prefix and URI.
   * List items at higher indexes override items with the same prefix at lower indexes.
   */
  private final List<String[]> namespaces = new ArrayList<String[]>(10);
  
  /**
   * The current prefix for the transforming element namespace of the transformer.
   */
  private String ourPrefix;
  
  /*
   * The prefixes emitted by sendAllStartPrefixMapping, to be ended by the next sendAllEndPrefixMapping.
   */
  private Stack<List<String>> prefixStack = new Stack<List<String>>();
  
  /**
   * Are we recording SAX events?
   * When recording SAX, we will produce our own prefix mapping events.
   */
  private boolean recordingSAX;
  
  /**
   * Way too simple parser to interpolate input module expressions.
   * @param value A string, possibly containing input module expressions.
   * @return The interpolated string.
   */
  protected String interpolateModules(String value) {
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

  /**
   * @see org.apache.cocoon.transformation.AbstractSAXTransformer#setup(org.apache.cocoon.environment.SourceResolver, java.util.Map, java.lang.String, org.apache.avalon.framework.parameters.Parameters)
   */
  @Override
  public void setup(SourceResolver resolver, Map objectModel, String src, Parameters params) throws ProcessingException, SAXException, IOException {
    super.setup(resolver, objectModel, src, params);
    this.ourPrefix = null;
    this.namespaces.clear();
    /* The prefix xml is by definition bound to the namespace name http://www.w3.org/XML/1998/namespace.
     * It MAY, but need not, be declared, and MUST NOT be bound to any other namespace name.
     * Other prefixes MUST NOT be bound to this namespace name, and it MUST NOT be declared as the default namespace.
     */
    this.namespaces.add(new String[]{"xml", "http://www.w3.org/XML/1998/namespace"});
    this.recordingSAX = false;
  }

  /**
   * @see org.apache.avalon.excalibur.pool.Recyclable#recycle()
   */
  public void recycle() {
      this.ourPrefix = null;
      this.namespaces.clear();
      super.recycle();
  }
  
  /**
   * @see org.xml.sax.ContentHandler#startPrefixMapping
   * ourPrefix is private to AbstractSAXTransformer, so we have to duplicate it to use it here.
   */
  public void startPrefixMapping(String prefix, String uri) throws SAXException {
    if (prefix != null) {
      this.namespaces.add(new String[] {prefix, uri});
    }
    if (this.namespaceURI.equals(uri)) {
      this.ourPrefix = prefix;
    }
    if (!this.recordingSAX) {
      // When recording SAX, we will produce our own prefix mapping events.
      super.startPrefixMapping(prefix, uri);
    }
  }

  /**
   * Process the SAX event.
   * @see org.xml.sax.ContentHandler#endPrefixMapping
   * namespaces is private to AbstractSAXTransformer, so we have to duplicate it to use it here.
   */
  public void endPrefixMapping(String prefix) throws SAXException {
    if (prefix != null) {
      // Find and remove the namespace prefix
      boolean found = false;
      for (int i = this.namespaces.size() - 1; i >= 0; i--) {
        final String[] prefixAndUri = (String[]) this.namespaces.get(i);
        if (prefixAndUri[0].equals(prefix)) {
          this.namespaces.remove(i);
          found = true;
          break;
        }
      }
      if (!found) {
        throw new SAXException("Namespace for prefix '" + prefix + "' not found.");
      }
      if (prefix.equals(this.ourPrefix)) {
        // Reset our current prefix
        this.ourPrefix = null;
        // Now search if we have a different prefix for our namespace
        for (int i = this.namespaces.size() - 1; i >= 0; i--) {
          final String[] prefixAndUri = (String[]) this.namespaces.get(i);
          if (namespaceURI.equals(prefixAndUri[1])) {
            this.ourPrefix = prefixAndUri[0];
            break;
          }
        }
      }
    }
    if (!this.recordingSAX) {
      // When recording SAX, we will produce our own prefix mapping events.
      super.endPrefixMapping(prefix);
    }
  }

  /**
   * Start recording of serialized xml.
   * All events are converted to an xml string which can be retrieved by endSerializedXMLRecording.
   * @param format The format for the serialized output. If <code>null</code> is specified, a very simple format is used.
   */
  public void startSerializedXMLRecording(Properties format) throws SAXException {
    if (getLogger().isDebugEnabled()) {
      getLogger().debug("Start serialized XML recording. Format=" + format);
    }
    if (format != null) {
      startSAXRecording();
    } else {
      addRecorder(new NamespacePrefixRepeatingSerializingRecorder(this));
    }
    this.stack.push(format);
  }

  /**
   * Return the serialized xml string.
   * @return A string containing the recorded xml information, formatted by
   * the properties passed to the corresponding startSerializedXMLRecording().
   * Namespace-prefix declarations are repeated for each element to allow fragments to be taken from the serialized content.
   */
  public String endSerializedXMLRecording() throws SAXException, ProcessingException {
    Properties format = (Properties) this.stack.pop();
    String text = null;
    if (format != null) {
      XMLizable xml = endSAXRecording();
      text = XMLUtils.serialize(xml, format);
    } else {
      text = ((NamespacePrefixRepeatingSerializingRecorder)removeRecorder()).toString();
    }
    if (getLogger().isDebugEnabled()) {
        getLogger().debug("End serialized XML recording. XML=" + text);
    }
    return text;
  }

  /**
   * Start recording of SAX events. All incoming events are recorded and not forwarded.
   * The resulting XMLizable can be obtained by the matching {@link #endSAXRecording} call.
   */
  public void startSAXRecording()
  throws SAXException {
    addRecorder(new NamespacePrefixRepeatingSaxBuffer(this));
    this.recordingSAX = true;
  }

  /**
   * Stop recording of SAX events.
   * This method returns the resulting XMLizable.
   */
  public XMLizable endSAXRecording()
  throws SAXException {
    this.recordingSAX = false;
    return (XMLizable) removeRecorder();
  }

  /**
   * Start recording of a text.
   * No events forwarded, and all characters events are collected into a string.
   */
  public void startTextRecording() throws SAXException {
      if (getLogger().isDebugEnabled()) {
          getLogger().debug("Start text recording");
      }
      addRecorder(new TextRecorder());
      sendStartPrefixMapping();
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
   * Send start prefix mapping events to the current content handler.
   * @param prefixes The prefixes for which to send mappings.
   * @throws SAXException
   */
  protected void sendAllStartPrefixMapping(List<String> prefixes) throws SAXException {
    Map<String, String> prefixAndUris = uniqueNamespacePrefixes();
    for (String prefix : prefixes) {
      String uri = prefixAndUris.get(prefix);
      contentHandler.startPrefixMapping(prefix, uri);
    }
    // Remember the prefixes for sendAllEndPrefixMapping.
    this.prefixStack.push(prefixes);
  }

  /**
   * Send all end prefix mapping events to the current content handler.
   * @throws SAXException
   */
  protected void sendAllEndPrefixMapping() throws SAXException {
    List<String> prefixes = this.prefixStack.pop();
    for (String prefix : prefixes) {
      contentHandler.endPrefixMapping(prefix);
    }
  }
  
  /**
   * A map of namespaces (prefix => URI) currently in use.
   * @return
   */
  private Map<String, String> uniqueNamespacePrefixes() {
    Map<String, String> prefixAndUris = new HashMap<String, String>();
    // Go through namespaces in order, so newer declarations for a prefix overwrite older ones.
    final int l = this.namespaces.size();
    for (int i = 0; i < l; i++) {
      String[] prefixAndUri = (String[]) this.namespaces.get(i);
      prefixAndUris.put(prefixAndUri[0], prefixAndUri[1]);
    }
    return prefixAndUris;
  }

  /**
   * Make a list of unique prefixes used by a startElement. If the tag has no prefix, guess that no attributes have prefixes.
   * @param namespaceURI
   * @param qName
   * @param atts
   * @return
   */
  private static List<String> getUsedPrefixes(String namespaceURI, String qName, Attributes atts) {
    int nrPrefixes = namespaceURI.length() > 0 ? 1 : 0;
    for (int i = 0; i < atts.getLength(); ++i) {
      String name = atts.getQName(i);
      if (name != null && name.contains(":")) ++nrPrefixes;
    }
    List<String> prefixes = new ArrayList<String>(nrPrefixes);
    if (namespaceURI.length() > 0) prefixes.add(qName.contains(":") ? qName.substring(0, qName.indexOf(':')) : "");
    for (int i = 0; i < atts.getLength(); ++i) {
      String name = atts.getQName(i);
      if (name != null && name.contains(":")) {
        String prefix = name.substring(0, name.indexOf(':'));
        if (!prefixes.contains(prefix)) prefixes.add(prefix);
      }
    }
    return prefixes;
  }
  
  /**
   * This SAX buffer repeats namespace prefix mappings for all namespace prefixes that are used by an element.
   */
  public class NamespacePrefixRepeatingSaxBuffer extends SaxBuffer {
    private static final long serialVersionUID = 1L;
    
    private AbstractSAXPipelineTransformer transformer;
    
    public NamespacePrefixRepeatingSaxBuffer(AbstractSAXPipelineTransformer transformer) {
      this.transformer = transformer;
    }

    public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
      List<String> prefixes = getUsedPrefixes(namespaceURI, qName, atts);
      transformer.sendAllStartPrefixMapping(prefixes);
      saxbits.add(new StartElement(namespaceURI, localName, qName, atts));
    }

    public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
      String nsPrefix = (qName.contains(":") ? qName.substring(0, qName.indexOf(':')) : "");
      saxbits.add(new EndElement(namespaceURI, localName, qName));
      transformer.sendAllEndPrefixMapping();
    }
    
  }
  
  /**
   * A recorder that serializes the incoming XML, repeating namespace prefix mappings for all namespace prefixes that are used by an element. 
   */
  public class NamespacePrefixRepeatingSerializingRecorder implements XMLConsumer {
    private AbstractSAXPipelineTransformer transformer;
    private StringBuilder text;
    
    public NamespacePrefixRepeatingSerializingRecorder(AbstractSAXPipelineTransformer transformer) {
      this.transformer = transformer;
      text = new StringBuilder();
    }
    
    public String toString() {
      return text.toString();
    }
    
    // ContentHandler Interface

    public void skippedEntity(String name) throws SAXException {
    }

    public void setDocumentLocator(Locator locator) {
    }

    public void ignorableWhitespace(char ch[], int start, int length) throws SAXException {
      text.append(String.copyValueOf(ch, start, length));
    }

    public void processingInstruction(String target, String data) throws SAXException {
      text.append("<?").append(target).append(" ").append(data).append("?>");
    }

    public void startDocument() throws SAXException {
    }

    public void endDocument() throws SAXException {
    }

    public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
      text.append('<').append(qName);
      List<String> prefixes = getUsedPrefixes(namespaceURI, qName, atts);
      Map<String, String> prefixAndUris = uniqueNamespacePrefixes();
      for (String prefix : prefixes) {
        String uri = prefixAndUris.get(prefix);
        text.append(" xmlns");
        if (!(prefix.equals(""))) {
            text.append(":").append(prefix);
        }
        text.append("=\"").append(StringEscapeUtils.escapeXml(uri)).append('"');
      }
      for (int i = 0; i < atts.getLength(); ++i) {
        text.append(' ').append(atts.getQName(i)).append("=\"").append(StringEscapeUtils.escapeXml(atts.getValue(i))).append('"');
      }
      text.append('>');
    }

    public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
      text.append("</").append(qName).append('>');
    }

    public void characters(char ch[], int start, int length) throws SAXException {
      text.append(StringEscapeUtils.escapeXml(String.copyValueOf(ch, start, length)));
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
    }

    public void endPrefixMapping(String prefix) throws SAXException {
    }

    // LexicalHandler Interface

    public void startCDATA() throws SAXException {
    }

    public void endCDATA() throws SAXException {
    }

    public void comment(char ch[], int start, int length) throws SAXException {
      text.append("<!--").append(String.copyValueOf(ch, start, length)).append("-->");
    }

    public void startEntity(String name) throws SAXException {
    }

    public void endEntity(String name) throws SAXException {
    }

    public void startDTD(String name, String publicId, String systemId) throws SAXException {
    }

    public void endDTD() throws SAXException {
    }
  }
}
