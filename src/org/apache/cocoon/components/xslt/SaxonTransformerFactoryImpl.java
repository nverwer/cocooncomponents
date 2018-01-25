package org.apache.cocoon.components.xslt;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TemplatesHandler;
import javax.xml.transform.sax.TransformerHandler;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLFilter;

import net.sf.saxon.TransformerFactoryImpl;

/**
 * The Saxon transformer sends messages from <xsl:message> to a messageEmitter, not to the errorListener,
 * see https://stackoverflow.com/questions/4695489/capture-xslmessage-output-in-java.
 * This can be changed in the underlying controller (https://www.saxonica.com/html/documentation/javadoc/index.html?net/sf/saxon/Controller.html).
 * The re-routing of messages is done automatically in this class, which behaves like a normal SAXTransformerFactory,
 * using a normal Transformer. Classes using this do not need to have any Saxon-specific code.
 */

public class SaxonTransformerFactoryImpl extends SAXTransformerFactory {
  
  private net.sf.saxon.TransformerFactoryImpl saxonTransformerFactory;
  
  protected SaxonTransformerFactoryImpl() throws TransformerConfigurationException {
    try {
      // Prefer the ProfessionalTransformerFactory (commercial) if it is present.
      saxonTransformerFactory = (TransformerFactoryImpl) Class.forName("com.saxonica.config.ProfessionalTransformerFactory").newInstance();
    } catch (Exception ex1) {
      try {
        // If only the open source version is present, use that.
        saxonTransformerFactory = (TransformerFactoryImpl) Class.forName("net.sf.saxon.TransformerFactoryImpl").newInstance();
      } catch (Exception ex2) {
        throw new TransformerConfigurationException("It looks like you do not have the Saxon jar.", ex2);
      }
    }
  }
  
  /**
   * Re-route messages from <xsl:message> to the errorListener, not to the messageEmitter.
   */
  private Transformer routeMessages(Transformer transformer) {
    if (transformer instanceof net.sf.saxon.jaxp.TransformerImpl) {
      net.sf.saxon.jaxp.TransformerImpl saxon = (net.sf.saxon.jaxp.TransformerImpl)transformer;
      saxon.getUnderlyingController().setMessageEmitter(new net.sf.saxon.serialize.MessageWarner());
    }
    return transformer;
  }

  /**
   * Wrap the Saxon transformerhandler.
   */
  @Override
  public TransformerHandler newTransformerHandler(Source src) throws TransformerConfigurationException {
    return new SaxonTransformerHandler(saxonTransformerFactory.newTransformerHandler(src));
  }

  @Override
  public TransformerHandler newTransformerHandler(Templates templates) throws TransformerConfigurationException {
    return new SaxonTransformerHandler(saxonTransformerFactory.newTransformerHandler(templates));
  }

  @Override
  public TransformerHandler newTransformerHandler() throws TransformerConfigurationException {
    return new SaxonTransformerHandler(saxonTransformerFactory.newTransformerHandler());
  }
  
  /**
   * Modify the transformer.
   */

  @Override
  public Transformer newTransformer(Source source) throws TransformerConfigurationException {
    return routeMessages(saxonTransformerFactory.newTransformer(source));
  }

  @Override
  public Transformer newTransformer() throws TransformerConfigurationException {
    return routeMessages(saxonTransformerFactory.newTransformer());
  }

  // All other messages are passed on.
  
  @Override
  public TemplatesHandler newTemplatesHandler() throws TransformerConfigurationException {
    return saxonTransformerFactory.newTemplatesHandler();
  }

  @Override
  public XMLFilter newXMLFilter(Source src) throws TransformerConfigurationException {
    return saxonTransformerFactory.newXMLFilter(src);
  }

  @Override
  public XMLFilter newXMLFilter(Templates templates) throws TransformerConfigurationException {
    return saxonTransformerFactory.newXMLFilter(templates);
  }

  @Override
  public Templates newTemplates(Source source) throws TransformerConfigurationException {
    return saxonTransformerFactory.newTemplates(source);
  }

  @Override
  public Source getAssociatedStylesheet(Source source, String media, String title, String charset) throws TransformerConfigurationException {
    return saxonTransformerFactory.getAssociatedStylesheet(source,  media, title, charset);
  }

  @Override
  public void setURIResolver(URIResolver resolver) {
    saxonTransformerFactory.setURIResolver(resolver);
  }

  @Override
  public URIResolver getURIResolver() {
    return saxonTransformerFactory.getURIResolver();
  }

  @Override
  public void setFeature(String name, boolean value) throws TransformerConfigurationException {
    saxonTransformerFactory.setFeature(name, value);
  }

  @Override
  public boolean getFeature(String name) {
    return saxonTransformerFactory.getFeature(name);
  }

  @Override
  public void setAttribute(String name, Object value) {
    saxonTransformerFactory.setAttribute(name, value);
  }

  @Override
  public Object getAttribute(String name) {
    return saxonTransformerFactory.getAttribute(name);
  }

  @Override
  public void setErrorListener(ErrorListener listener) {
    saxonTransformerFactory.setErrorListener(listener);
  }

  @Override
  public ErrorListener getErrorListener() {
    return saxonTransformerFactory.getErrorListener();
  }
  
  
  /**
   * A transformer handler that wraps the transformer handler from Saxon.
   */
  class SaxonTransformerHandler implements TransformerHandler {
    
    private TransformerHandler transformerHandler;
    
    public SaxonTransformerHandler(TransformerHandler transformerHandler) {
      this.transformerHandler = transformerHandler;
    }
    
    /**
     * Modify the transformer.
     */
    @Override
    public Transformer getTransformer() {
      return routeMessages(transformerHandler.getTransformer());
    }

    // All other methods are passed on.
    
    @Override
    public void setDocumentLocator(Locator locator) {
      transformerHandler.setDocumentLocator(locator);
    }
    @Override
    public void startDocument() throws SAXException {
      transformerHandler.startDocument();
    }
    @Override
    public void endDocument() throws SAXException {
      transformerHandler.endDocument();
    }
    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
      transformerHandler.startPrefixMapping(prefix, uri);
    }
    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
      transformerHandler.endPrefixMapping(prefix);
    }
    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
      transformerHandler.startElement(uri, localName, qName, atts);
    }
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
      transformerHandler.endElement(uri, localName, qName);
    }
    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
      transformerHandler.characters(ch, start, length);
    }
    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
      transformerHandler.ignorableWhitespace(ch, start, length);
    }
    @Override
    public void processingInstruction(String target, String data) throws SAXException {
      transformerHandler.processingInstruction(target, data);
    }
    @Override
    public void skippedEntity(String name) throws SAXException {
      transformerHandler.skippedEntity(name);
    }
    @Override
    public void startDTD(String name, String publicId, String systemId) throws SAXException {
      transformerHandler.startDTD(name, publicId, systemId);
    }
    @Override
    public void endDTD() throws SAXException {
      transformerHandler.endDTD();
    }
    @Override
    public void startEntity(String name) throws SAXException {
      transformerHandler.startEntity(name);
    }
    @Override
    public void endEntity(String name) throws SAXException {
      transformerHandler.endEntity(name);
    }
    @Override
    public void startCDATA() throws SAXException {
      transformerHandler.startCDATA();
    }
    @Override
    public void endCDATA() throws SAXException {
      transformerHandler.endCDATA();
    }
    @Override
    public void comment(char[] ch, int start, int length) throws SAXException {
      transformerHandler.comment(ch, start, length);
    }
    @Override
    public void notationDecl(String name, String publicId, String systemId) throws SAXException {
      transformerHandler.notationDecl(name, publicId, systemId);
    }
    @Override
    public void unparsedEntityDecl(String name, String publicId, String systemId, String notationName) throws SAXException {
      transformerHandler.unparsedEntityDecl(name, publicId, systemId, notationName);
    }
    @Override
    public void setResult(Result result) throws IllegalArgumentException {
      transformerHandler.setResult(result);
    }
    @Override
    public void setSystemId(String systemID) {
      transformerHandler.setSystemId(systemID);
    }
    @Override
    public String getSystemId() {
      return transformerHandler.getSystemId();
    }
  }

}
