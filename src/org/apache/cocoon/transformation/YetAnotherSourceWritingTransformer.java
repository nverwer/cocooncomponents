package org.apache.cocoon.transformation;

import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.util.Map;

import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.apache.avalon.framework.CascadingRuntimeException;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.components.source.SourceUtil;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.util.ClassUtils;
import org.apache.excalibur.source.ModifiableSource;
import org.apache.excalibur.source.Source;
import org.apache.excalibur.source.SourceException;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

public class YetAnotherSourceWritingTransformer extends AbstractSAXTransformer {
  /* This is another streaming source-writing transformer. It was mostly copied from the TeeTransformer.
   * I added the code for the source:xyz elements, and the switching between xml consumers.
   * The source to write to can be set in the sitemap (src attribute of <map:transform>) or
   * in the XML stream, like the official sourcewriting transformer.
   */

  public static final String SWT_URI = "http://apache.org/cocoon/source/1.0";

  /** incoming elements */
  public static final String WRITE_ELEMENT = "write";
  public static final String INSERT_ELEMENT = "insert";
  public static final String PATH_ELEMENT = "path";
  public static final String FRAGMENT_ELEMENT = "fragment";
  public static final String REPLACE_ELEMENT = "replace";
  public static final String DELETE_ELEMENT = "delete";
  public static final String SOURCE_ELEMENT = "source";
  public static final String REINSERT_ELEMENT = "reinsert";
  /** outgoing elements */
  public static final String RESULT_ELEMENT = "sourceResult";
  public static final String EXECUTION_ELEMENT = "execution";
  public static final String BEHAVIOUR_ELEMENT = "behaviour";
  public static final String ACTION_ELEMENT = "action";
  public static final String MESSAGE_ELEMENT = "message";
  public static final String SERIALIZER_ELEMENT = "serializer";
  /** main (write or insert) tag attributes */
  public static final String SERIALIZER_ATTRIBUTE = "serializer";
  public static final String CREATE_ATTRIBUTE = "create";
  public static final String OVERWRITE_ATTRIBUTE = "overwrite";
  /** results */
  public static final String RESULT_FAILED = "failed";
  public static final String RESULT_SUCCESS = "success";
  public static final String ACTION_NONE = "none";
  public static final String ACTION_NEW = "new";
  public static final String ACTION_OVER = "overwritten";
  public static final String ACTION_DELETE = "deleted";

  private TransformerHandler serializer;

  /** the transformer factory to use */
  private SAXTransformerFactory transformerFactory;

  private SourceResolver resolver;
  private String outputSourceName;
  private Source source;
  private OutputStream outputSource;
  
  private boolean writingToSource;
  
  /**
   * Constructor. Set the namespace.
   */
  public YetAnotherSourceWritingTransformer() {
      this.defaultNamespaceURI = SWT_URI;
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.apache.cocoon.sitemap.SitemapModelComponent#setup(org.apache.cocoon.environment.SourceResolver,
   *      java.util.Map, java.lang.String,
   *      org.apache.avalon.framework.parameters.Parameters)
   */
  public void setup(SourceResolver resolver, Map objectModel, String src, Parameters parameters)
      throws ProcessingException, SAXException, IOException {
      super.setup(resolver, objectModel, src, parameters);
      this.source = null;
      this.writingToSource = false;
      try {
          this.resolver = resolver;
          this.serializer = this.transformerFactory.newTransformerHandler();
          this.outputSourceName = src;
          if (src != null && src.length() > 0) {
            setOutputSource(src);
          }
      } catch (TransformerConfigurationException e) {
          throw new ProcessingException(e);
      } catch (TransformerFactoryConfigurationError error) {
          throw new ProcessingException(error.getException());
      }
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.apache.avalon.framework.configuration.Configurable#configure(org.apache.avalon.framework.configuration.Configuration)
   */
  public void configure(Configuration configuration) throws ConfigurationException {
      String tFactoryClass = configuration.getChild("transformer-factory").getValue(null);
      if (tFactoryClass != null) {
          try {
              this.transformerFactory = (SAXTransformerFactory) ClassUtils
                  .newInstance(tFactoryClass);
              if (getLogger().isDebugEnabled()) {
                  getLogger().debug("Using transformer factory " + tFactoryClass);
              }
          } catch (Exception e) {
              throw new ConfigurationException(
                  "Cannot load transformer factory " + tFactoryClass, e);
          }
      } else {
          this.transformerFactory = (SAXTransformerFactory) TransformerFactory.newInstance();
      }
  }

  private void setOutputSource(String src) throws ProcessingException {
    try {
      this.source = this.resolver.resolveURI(src);
    } catch (MalformedURLException e) {
      throw new ProcessingException(e);
    } catch (IOException e) {
      throw new ProcessingException(e);
    } finally {
      if (this.source != null) {
          this.resolver.release(this.source);
      }
    }
    String systemId = source.getURI();
    if (!(source instanceof ModifiableSource)) {
        throw new ProcessingException("Source '" + src + "' (" + systemId + ") is not writeable.");
    }
  }

  public void startTransformingElement(String uri, String name, String raw, Attributes attr)
  throws SAXException, IOException, ProcessingException {
    if (name.equals(WRITE_ELEMENT)) {
    } else if (name.equals(SOURCE_ELEMENT)) {
      // Start recording the output-source name.
      this.startTextRecording();
    } else if (name.equals(FRAGMENT_ELEMENT)) {
      // Start writing to output source.
      this.writingToSource = true;
      startDocument();
    } else {
      throw new ProcessingException("Unknown element "+raw);
    }
  }

  public void endTransformingElement(String uri, String name, String raw)
  throws SAXException, IOException, ProcessingException {
    if (name.equals(WRITE_ELEMENT)) {
      // End of writing, report result.
      reportResult("xml",name, "entire source overwritten", this.outputSourceName, RESULT_SUCCESS, ACTION_OVER);
    } else if (name.equals(SOURCE_ELEMENT)) {
      // We now have the name of the source to write to.
      outputSourceName = this.endTextRecording();
      setOutputSource(outputSourceName);
    } else if (name.equals(FRAGMENT_ELEMENT)) {
      // End of fragment, continue normal processing.
      endDocument();
      this.writingToSource = false;
    } else {
      throw new ProcessingException("Unknown element "+raw);
    }
  }

  /**
   * Receive an object for locating the origin of SAX document events.
   */
  public void setDocumentLocator(Locator locator) {
      this.serializer.setDocumentLocator(locator);
      super.setDocumentLocator(locator);
  }

  /**
   * Receive notification of the beginning of a document.
   */
  public void startDocument() throws SAXException {
      if (writingToSource) {
        try {
          this.outputSource = ((ModifiableSource) source).getOutputStream();
        } catch (IOException e) {
          throw new CascadingRuntimeException("Error closing output stream.", e);
        }
        this.serializer.setResult(new StreamResult(outputSource));
        this.serializer.startDocument();
      }
      else super.startDocument();
  }

  /**
   * Receive notification of the end of a document.
   */
  public void endDocument() throws SAXException {
      if (writingToSource) {
        this.serializer.endDocument();
        if (outputSource != null) {
            try {
                outputSource.close();
            } catch (IOException e) {
                throw new CascadingRuntimeException("Error closing output stream.", e);
            }
        }
      }
      else
        super.endDocument();
  }

  /**
   * Begin the scope of a prefix-URI Namespace mapping.
   */
  public void startPrefixMapping(String prefix, String uri) throws SAXException {
      if (writingToSource) this.serializer.startPrefixMapping(prefix, uri);
      else super.startPrefixMapping(prefix, uri);
  }

  /**
   * End the scope of a prefix-URI mapping.
   */
  public void endPrefixMapping(String prefix) throws SAXException {
      if (writingToSource) this.serializer.endPrefixMapping(prefix);
      else super.endPrefixMapping(prefix);
  }

  /**
   * Receive notification of the beginning of an element.
   */
  public void startElement(String uri, String loc, String raw, Attributes a) throws SAXException {
      if (writingToSource) this.serializer.startElement(uri, loc, raw, a);
      else super.startElement(uri, loc, raw, a);
  }

  /**
   * Receive notification of the end of an element.
   */
  public void endElement(String uri, String loc, String raw) throws SAXException {
      if (writingToSource && !namespaceURI.equals(uri)) this.serializer.endElement(uri, loc, raw);
      else super.endElement(uri, loc, raw);
  }

  /**
   * Receive notification of character data.
   */
  public void characters(char ch[], int start, int len) throws SAXException {
      if (writingToSource) this.serializer.characters(ch, start, len);
      else super.characters(ch, start, len);
  }

  /**
   * Receive notification of ignorable whitespace in element content.
   */
  public void ignorableWhitespace(char ch[], int start, int len) throws SAXException {
      if (writingToSource) this.serializer.ignorableWhitespace(ch, start, len);
      else super.ignorableWhitespace(ch, start, len);
  }

  /**
   * Receive notification of a processing instruction.
   */
  public void processingInstruction(String target, String data) throws SAXException {
      if (writingToSource) this.serializer.processingInstruction(target, data);
      else super.processingInstruction(target, data);
  }

  /**
   * Receive notification of a skipped entity.
   */
  public void skippedEntity(String name) throws SAXException {
      if (writingToSource) this.serializer.skippedEntity(name);
      else super.skippedEntity(name);
  }

  /**
   * Report the start of DTD declarations, if any.
   */
  public void startDTD(String name, String publicId, String systemId) throws SAXException {
      if (writingToSource) this.serializer.startDTD(name, publicId, systemId);
      else super.startDTD(name, publicId, systemId);
  }

  /**
   * Report the end of DTD declarations.
   */
  public void endDTD() throws SAXException {
      if (writingToSource) this.serializer.endDTD();
      else super.endDTD();
  }

  /**
   * Report the beginning of an entity.
   */
  public void startEntity(String name) throws SAXException {
      if (writingToSource) this.serializer.startEntity(name);
      else super.startEntity(name);
  }

  /**
   * Report the end of an entity.
   */
  public void endEntity(String name) throws SAXException {
      if (writingToSource) this.serializer.endEntity(name);
      else super.endEntity(name);
  }

  /**
   * Report the start of a CDATA section.
   */
  public void startCDATA() throws SAXException {
      if (writingToSource) this.serializer.startCDATA();
      else super.startCDATA();
  }

  /**
   * Report the end of a CDATA section.
   */
  public void endCDATA() throws SAXException {
      if (writingToSource) this.serializer.endCDATA();
      else super.endCDATA();
  }

  /**
   * Report an XML comment anywhere in the document.
   */
  public void comment(char ch[], int start, int len) throws SAXException {
      if (writingToSource) this.serializer.comment(ch, start, len);
      else super.comment(ch, start, len);
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.apache.avalon.excalibur.pool.Recyclable#recycle()
   */
  public void recycle() {
      super.recycle();
      this.serializer = null;
  }

  private void reportResult(String localSerializer, String tagname,
      String message, String target, String result, String action)
      throws SAXException {
    sendStartElementEvent(RESULT_ELEMENT);
      sendStartElementEvent(EXECUTION_ELEMENT);
        sendTextEvent(result);
      sendEndElementEvent(EXECUTION_ELEMENT);
      sendStartElementEvent(MESSAGE_ELEMENT);
        sendTextEvent(message);
      sendEndElementEvent(MESSAGE_ELEMENT);
      sendStartElementEvent(BEHAVIOUR_ELEMENT);
        sendTextEvent(tagname);
      sendEndElementEvent(BEHAVIOUR_ELEMENT);
      sendStartElementEvent(ACTION_ELEMENT);
        sendTextEvent(action);
      sendEndElementEvent(ACTION_ELEMENT);
      sendStartElementEvent(SOURCE_ELEMENT);
        sendTextEvent(target);
      sendEndElementEvent(SOURCE_ELEMENT);
      if (localSerializer != null) {
        sendStartElementEvent(SERIALIZER_ELEMENT);
          sendTextEvent(localSerializer);
        sendEndElementEvent(SERIALIZER_ELEMENT);
      }
    sendEndElementEvent(RESULT_ELEMENT);
  }

}
