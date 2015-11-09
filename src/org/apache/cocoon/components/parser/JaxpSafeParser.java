/*
 * This is copied from org.apache.cocoon.components.parser.JaxpParser,
 * but it is (by default) safe against well known XML attacks.
 * All SAX and DOM parser features must be controlled by parameters with names starting with the URI prefix
 * given in [http://www.saxproject.org/apidoc/org/xml/sax/package-summary.html#package_description]
 * and [https://xerces.apache.org/xerces2-j/features.html].
 * See also [http://docs.oracle.com/javase/7/docs/api/index.html?javax/xml/parsers/SAXParserFactory.html]
 * and [http://docs.oracle.com/javase/7/docs/api/javax/xml/parsers/DocumentBuilderFactory.html].
 * The feature XMLConstants.FEATURE_SECURE_PROCESSING is always true.
 * The following features are set to non-default values to increase security:
 *   http://xml.org/sax/features/external-parameter-entities : false
 *   http://apache.org/xml/features/nonvalidating/load-external-dtd : false
 * The validate parameter still exists. It is false by default because it implies reading the DTD.
 */

package org.apache.cocoon.components.parser;

import java.io.IOException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.apache.avalon.excalibur.pool.Poolable;
import org.apache.avalon.framework.activity.Disposable;
import org.apache.avalon.framework.component.Component;
import org.apache.avalon.framework.logger.AbstractLogEnabled;
import org.apache.avalon.framework.parameters.ParameterException;
import org.apache.avalon.framework.parameters.Parameterizable;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.avalon.framework.service.ServiceException;
import org.apache.avalon.framework.service.ServiceManager;
import org.apache.avalon.framework.service.Serviceable;
import org.apache.excalibur.xml.EntityResolver;
import org.apache.excalibur.xml.dom.DOMParser;
import org.apache.excalibur.xml.sax.SAXParser;
import org.w3c.dom.Document;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.LexicalHandler;

public class JaxpSafeParser extends AbstractLogEnabled
implements SAXParser, DOMParser, Poolable, Component, Parameterizable, Serviceable, Disposable, ErrorHandler
{
  /** the SAX Parser factory */
  private SAXParserFactory m_factory;

  /** The SAX reader. It is created lazily by {@link #setupXMLReader()} and cleared if a parsing error occurs. */
  private XMLReader m_reader;

  /** the Entity Resolver */
  private EntityResolver m_resolver;

  /** do we want namespaces also as xmlns-attributes ? */
  private boolean m_nsPrefixes;

  /** do we want to reuse parsers ? */
  private boolean m_reuseParsers;

  /** do we stop on warnings ? */
  private boolean m_stopOnWarning;

  /** do we stop on recoverable errors ? */
  private boolean m_stopOnRecoverableError;

  /** the hint to the entity resolver */
  private String m_resolverHint;

  /** the Document Builder factory */
  private DocumentBuilderFactory m_docFactory;

  /** The DOM builder. It is created lazily by {@link #setupDocumentBuilder()} and cleared if a parsing error occurs. */
  private DocumentBuilder m_docBuilder;

  /** Should comments appearing between startDTD / endDTD events be dropped ? */
  private boolean m_dropDtdComments;

  /** The service manager */
  private ServiceManager m_manager;

  /**
   * Get the Entity Resolver from the component m_manager
   */
  public void service( final ServiceManager manager ) throws ServiceException {
    m_manager = manager;
    if( manager.hasService( EntityResolver.ROLE ) ) {
      if ( m_resolverHint != null ) {
        // select the configured resolver
        m_resolver = (EntityResolver)manager.lookup( EntityResolver.ROLE + "/" + m_resolverHint );
      } else {
        // use default resolver
        m_resolver = (EntityResolver)manager.lookup( EntityResolver.ROLE );
      }
      if(getLogger().isDebugEnabled())
        getLogger().debug( "JaxpParser: Using EntityResolver: " + m_resolver );
    }
  }

  /*
   * @see org.apache.avalon.framework.activity.Disposable#dispose()
   */
  public void dispose() {
    if ( m_manager != null ) {
      m_manager.release( m_resolver );
      m_manager = null;
      m_resolver = null;
    }
  }

  public void parameterize( final Parameters params )
      throws ParameterException {
    // Validation and namespace prefixes parameters.
    boolean validate = params.getParameterAsBoolean( "validate", false );
    m_nsPrefixes = params.getParameterAsBoolean( "namespace-prefixes", false );
    m_reuseParsers = params.getParameterAsBoolean( "reuse-parsers", true );
    m_stopOnWarning = params.getParameterAsBoolean( "stop-on-warning", true );
    m_stopOnRecoverableError = params.getParameterAsBoolean( "stop-on-recoverable-error", true );
    m_dropDtdComments = params.getParameterAsBoolean( "drop-dtd-comments", false );
    m_resolverHint = params.getParameter( "resolver-hint", null );
    // Get the SAXFactory to obtain a SAX based parser to parse XML documents.
    final String saxParserFactoryName = params.getParameter( "sax-parser-factory", "javax.xml.parsers.SAXParserFactory" );
    if( "javax.xml.parsers.SAXParserFactory".equals( saxParserFactoryName ) ) {
      m_factory = SAXParserFactory.newInstance();
    } else {
      try {
        final Class<? extends SAXParserFactory> factoryClass = loadClass( saxParserFactoryName );
        m_factory = (SAXParserFactory)factoryClass.newInstance();
      } catch( Exception e ) {
        throw new ParameterException( "Cannot load SAXParserFactory class " + saxParserFactoryName, e );
      }
    }
System.out.println("************************************************** Hello planet Earth, are you there?");
    m_factory.setNamespaceAware( true );
    m_factory.setValidating( validate );
    String feature = null;
    try {
      feature = "http://xml.org/sax/features/external-parameter-entities";
      m_factory.setFeature(feature, false);
      feature = "http://apache.org/xml/features/nonvalidating/load-external-dtd";
      m_factory.setFeature(feature, false);
    } catch(Exception e) {
      getLogger().warn("Feature "+feature+" is not available.");
    }
    try {
      feature = XMLConstants.FEATURE_SECURE_PROCESSING;
      m_factory.setFeature(feature, true);
    } catch (Exception e) {
      throw new ParameterException("Cannot set feature "+feature, e);
    }
    // Get the DocumentFactory used to obtain a parser that produces DOM object trees from XML documents.
    final String documentBuilderFactoryName = params.getParameter( "document-builder-factory", "javax.xml.parsers.DocumentBuilderFactory" );
    if( "javax.xml.parsers.DocumentBuilderFactory".equals( documentBuilderFactoryName ) ) {
      m_docFactory = DocumentBuilderFactory.newInstance();
    } else {
      try {
        final Class<? extends DocumentBuilderFactory> factoryClass = loadClass( documentBuilderFactoryName );
        m_docFactory = (DocumentBuilderFactory)factoryClass.newInstance();
      } catch( Exception e ) {
        throw new ParameterException( "Cannot load DocumentBuilderFactory class " + documentBuilderFactoryName, e );
      }
    }
    m_docFactory.setNamespaceAware( true );
    m_docFactory.setValidating( validate );
    try {
      feature = "http://xml.org/sax/features/external-parameter-entities";
      m_factory.setFeature(feature, false);
      feature = "http://apache.org/xml/features/nonvalidating/load-external-dtd";
      m_factory.setFeature(feature, false);
    } catch(Exception e) {
      getLogger().warn("Feature "+feature+" is not available.");
    }
    try {
      feature = XMLConstants.FEATURE_SECURE_PROCESSING;
      m_docFactory.setFeature(feature, true);
    } catch (Exception e) {
      throw new ParameterException("Cannot set feature "+feature, e);
    }
  }

  /**
   * Load a class
   */
  private Class loadClass( String name ) throws Exception {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    if( loader == null ) {
      loader = getClass().getClassLoader();
    }
    return loader.loadClass( name );
  }

  /**
   * Parse the <code>InputSource</code> and send SAX events to the consumer.
   * Attention: the consumer can  implement the <code>LexicalHandler</code> as well.
   * The parse should take care of this.
   */
  public void parse( final InputSource in, final ContentHandler contentHandler, final LexicalHandler lexicalHandler )
          throws SAXException, IOException {
    setupXMLReader();
    // Ensure we will use a fresh new parser at next parse in case of failure
    XMLReader tmpReader = m_reader;
    try {
      LexicalHandler theLexicalHandler = null;
      if ( null == lexicalHandler && contentHandler instanceof LexicalHandler){
        theLexicalHandler = (LexicalHandler)contentHandler;
      }   
      if( null != lexicalHandler ) {
        theLexicalHandler = lexicalHandler;
      }
      if (theLexicalHandler != null) {
        if (m_dropDtdComments) theLexicalHandler = new DtdCommentEater(theLexicalHandler);
        tmpReader.setProperty( "http://xml.org/sax/properties/lexical-handler", theLexicalHandler );
      }
    } catch( final SAXException e ) {
      final String message = "SAX2 driver does not support property: 'http://xml.org/sax/properties/lexical-handler'";
      getLogger().warn( message );
    }
    tmpReader.setErrorHandler( this );
    tmpReader.setContentHandler( contentHandler );
    if( null != m_resolver ) {
      tmpReader.setEntityResolver( m_resolver );
    }
    m_reader = null;
    tmpReader.parse( in );
    // Here, parsing was successful : restore reader
    if( m_reuseParsers ) {
      m_reader = tmpReader;
    }
  }

  /**
   * Parse the {@link InputSource} and send SAX events to the consumer.
   * Attention: the consumer can  implement the {@link LexicalHandler} as well.
   * The parse should take care of this.
   */
  public void parse( InputSource in, ContentHandler consumer )
      throws SAXException, IOException {
    this.parse( in, consumer, (consumer instanceof LexicalHandler ? (LexicalHandler)consumer : null));
  }

  /**
   * Creates a new {@link XMLReader} if needed.
   */
  private void setupXMLReader() throws SAXException {
    if( null == m_reader ) {
      // Create the XMLReader
      try {
        m_reader = m_factory.newSAXParser().getXMLReader();
      } catch( final ParserConfigurationException pce ) {
        final String message = "Cannot produce a valid parser";
        throw new SAXException( message, pce );
      }
      m_reader.setFeature( "http://xml.org/sax/features/namespaces", true );
      if( m_nsPrefixes ) {
        try {
          m_reader.setFeature( "http://xml.org/sax/features/namespace-prefixes", m_nsPrefixes );
        } catch( final SAXException se ) {
          final String message =
              "SAX2 XMLReader does not support setting feature: 'http://xml.org/sax/features/namespace-prefixes'";
          getLogger().warn( message );
        }
      }
    }
  }

  /**
   * Parses a new Document object from the given InputSource.
   */
  public Document parseDocument( final InputSource input ) throws SAXException, IOException {
    setupDocumentBuilder();
    // Ensure we will use a fresh new parser at next parse in case of failure.
    DocumentBuilder tmpBuilder = m_docBuilder;
    if( null != m_resolver ) {
      tmpBuilder.setEntityResolver( m_resolver );
    }
    m_docBuilder = null;
    Document result = tmpBuilder.parse( input );
    // Here, parsing was successful : restore builder
    if( m_reuseParsers ) {
      m_docBuilder = tmpBuilder;
    }
    return result;
  }

  /**
   * Creates a new {@link DocumentBuilder} if needed.
   */
  private void setupDocumentBuilder() throws SAXException {
    if( null == m_docBuilder ) {
      try {
        m_docBuilder = m_docFactory.newDocumentBuilder();
      } catch( final ParserConfigurationException pce ) {
        final String message = "Could not create DocumentBuilder";
        throw new SAXException( message, pce );
      }
    }
  }

  /**
   * Return a new {@link Document}.
   */
  public Document createDocument() throws SAXException {
    setupDocumentBuilder();
    return m_docBuilder.newDocument();
  }

  /**
   * Receive notification of a recoverable error.
   */
  public void error( final SAXParseException spe )
      throws SAXException {
    final String message = "Error parsing " + messageLocation( spe );
    if( m_stopOnRecoverableError ) {
      throw new SAXException( message, spe );
    }
    getLogger().error( message, spe );
  }

  /**
   * Receive notification of a fatal error.
   */
  public void fatalError( final SAXParseException spe ) throws SAXException {
    final String message = "Fatal error parsing " + messageLocation( spe );
    throw new SAXException( message, spe );
  }

  /**
   * Receive notification of a warning.
   */
  public void warning( final SAXParseException spe ) throws SAXException {
    final String message = "Warning parsing " + messageLocation( spe );
    if( m_stopOnWarning ) {
      throw new SAXException( message, spe );
    }
    getLogger().warn( message, spe );
  }
  
  private String messageLocation( final SAXParseException spe ) {
    return spe.getSystemId() + " (line " + spe.getLineNumber() + " col. " + spe.getColumnNumber() + "): " + spe.getMessage();
  }

  /**
   * A LexicalHandler implementation that strips all comment events between
   * startDTD and endDTD. In all other cases the events are forwarded to another
   * LexicalHandler.
   */
  private static class DtdCommentEater implements LexicalHandler {
    private LexicalHandler next;
    private boolean inDTD;
    
    public DtdCommentEater(LexicalHandler nextHandler) {
      this.next = nextHandler;
      this.inDTD = false;
    }
    
    public void startDTD (String name, String publicId, String systemId) throws SAXException {
      inDTD = true;
      next.startDTD(name, publicId, systemId);
    }
    
    public void endDTD () throws SAXException {
      inDTD = false;
      next.endDTD();
    }
    
    public void startEntity (String name) throws SAXException {
      next.startEntity(name);
    }

    public void endEntity (String name) throws SAXException {
      next.endEntity(name);
    }

    public void startCDATA () throws SAXException {
      next.startCDATA();
    }

    public void endCDATA () throws SAXException {
      next.endCDATA();
    }

    public void comment (char ch[], int start, int length) throws SAXException {
      if (!inDTD)
        next.comment(ch, start, length);
    }
  }

}
