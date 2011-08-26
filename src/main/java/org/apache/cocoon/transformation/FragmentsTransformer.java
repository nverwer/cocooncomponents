package org.apache.cocoon.transformation;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Hashtable;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.TransformerHandler;

import org.apache.avalon.framework.activity.Disposable;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.xml.AbstractXMLPipe;
import org.apache.cocoon.xml.XMLConsumer;
import org.apache.cocoon.xml.XMLUtils;
import org.apache.commons.io.IOUtils;
import org.apache.excalibur.source.Source;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 * <p>This abstract transformer works with Cocoon 2.2. Compatibility with
 * Cocoon 2.1 has not been checked, YMMV.</p>
 * <p>This transformer processes <code>transform</code> elements in the
 * <code>http://org.apache.cocoon.transformation/fragments/1.0</code> namespace.
 * These <code>fragments:transform</code> elements have
 * <code>fragment-name</code> and <code>fragment-namespace</code> attributes
 * to determinse which child elements will be processed 
 * with the stylesheet that is loaded from the 
 * <code>stylesheet-uri</code> attribute.
 *
 * The transformer does not do caching.
 * </p>
 * 
 * 
 * <h2>Configure</h2>
 *
 * <bean name="org.apache.cocoon.transformation.Transformer/fragments"
 *  class="org.apache.cocoon.transformation.FragmentsTransformer" scope="prototype">
 *      <property name="xsltProcessor" ref="org.apache.excalibur.xml.xslt.XSLTProcessor"/>
 *  </bean>
 * 
 * <h2>Invocation</h2>
 * 
 * <pre>
 * &lt;map:transform type="fragments"/&gt;
 * </pre>
 * 
 * <h2>Use:</h2>
 * <pre>
 *  &lt;fragments:transform stylesheet-uri="cocoon:/generateStylesheet?param=1"
 *      fragment-name="item" fragment-namespace="http://example.com/item"&gt;
 *      &lt;item:item&gt;
 *          &lt;Item data/&gt;
 *      &lt;/item:item&gt;
 *  &lt;/fragments:transform&gt;
 * </pre>
 * 
 * @author Huib Verweij, huiver@mpi.nl
 */
public class FragmentsTransformer extends AbstractSAXTransformer
        implements Disposable {

    @Override
    public void dispose() {
        super.dispose();
    }
    private static final String NS_URI = "http://org.apache.cocoon.transformation/fragments/1.0";
    private static final String NS_PREFIX = "fragments";
    private static final String TRANSFORM_TAG = "transform";
    private static final String STYLESHEET_URI_ATTR = "stylesheet-uri";
    private static final String FRAGMENT_NAME_ATTR = "fragment-name";
    private static final String FRAGMENT_NAMESPACE_ATTR = "fragment-namespace";
    private Parameters par;
    private String stylesheet_uri;
    private String fragmentName = "";
    private String fragmentNamespace = "";
    private Document stylesheet = null;
    private org.apache.excalibur.xml.xslt.XSLTProcessor xsltProcessor = null;
    protected TransformerHandler transformerHandler = null;
    protected Transformer transformer = null;
    private org.apache.cocoon.xml.XMLConsumer consumer;
    private FragmentsTransformerConsumer fragmentsTransformerConsumer;
    private boolean recording;
    private boolean insideTransformElement;
    private Hashtable<String, Integer> nsPrefixes;

    public FragmentsTransformer() {
        super.defaultNamespaceURI = "";
    }

    @Override
    public void configure(Configuration configuration) throws
            ConfigurationException {
        super.configure(configuration);
    }

    @Override
    public void setup(SourceResolver resolver, Map objectModel, String src, Parameters par)
            throws ProcessingException, SAXException, IOException {
        super.setup(resolver, objectModel, src, par);

        this.resolver = resolver;
        recording = false;
        insideTransformElement = false;
        this.ignoreEmptyCharacters = false;
        this.ignoreWhitespaces = false;
    }

    /*
     * Setter called by Spring to set the xslt processor.
     */
    public void setXsltProcessor(org.apache.excalibur.xml.xslt.XSLTProcessor xsltProcessor) {
        this.xsltProcessor = xsltProcessor;

        if (getLogger().isDebugEnabled()) {
            getLogger().debug("xsltProcessor=" + xsltProcessor);
        }
    }

    /**
     * Set the <code>XMLConsumer</code> that will receive XML data.
     */
    @Override
    public void setConsumer(XMLConsumer consumer) {
        super.setConsumer(consumer);
        this.consumer = consumer;
        fragmentsTransformerConsumer = new FragmentsTransformerConsumer();
        fragmentsTransformerConsumer.setConsumer(this.consumer);
    }

    /*
     * SAX methods.
     */
    @Override
    public void startDocument() throws SAXException {
        nsPrefixes = new Hashtable<String, Integer>();
        consumer.startDocument();
    }

    @Override
    public void endDocument() throws SAXException {
        consumer.endDocument();
    }

    @Override
    public void startElement(String namespaceURI, String lName, String qName, Attributes attributes)
            throws SAXException {
        if (namespaceURI.equals(NS_URI) && lName.equals(TRANSFORM_TAG)) {
            try {
                insideTransformElement = true;
                stylesheet_uri = attributes.getValue(STYLESHEET_URI_ATTR);
                fragmentName = attributes.getValue(FRAGMENT_NAME_ATTR);
                fragmentNamespace = attributes.getValue(FRAGMENT_NAMESPACE_ATTR);
                if (getLogger().isDebugEnabled()) {
                    getLogger().debug("Stylesheet uri=" + stylesheet_uri);
                    getLogger().debug("fragmentName=" + fragmentName);
                    getLogger().debug("fragmentNamespace=" + fragmentNamespace);
                }
                Source sbu_source = resolver.resolveURI(stylesheet_uri);
                if (getLogger().isDebugEnabled()) {
                    getLogger().debug("Stylesheet source=" + IOUtils.toString(sbu_source.getInputStream(), "UTF-8"));
                    //sbu_source.getInputStream().);
                }
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = null;
                try {
                    db = dbf.newDocumentBuilder();
                } catch (ParserConfigurationException ex) {
                    Logger.getLogger(FragmentsTransformer.class.getName()).log(Level.SEVERE, null, ex);
                }
                if (null != db) {
                    stylesheet = db.parse(sbu_source.getInputStream());
                    try {
                        this.transformerHandler = this.xsltProcessor.getTransformerHandler(sbu_source);
                    } catch (org.apache.excalibur.xml.xslt.XSLTProcessorException ex) {
                        Logger.getLogger(FragmentsTransformer.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    this.transformer = null;
                }
            } catch (MalformedURLException ex) {
                Logger.getLogger(FragmentsTransformer.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(FragmentsTransformer.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else if (insideTransformElement && lName.equals(fragmentName) && namespaceURI.equals(fragmentNamespace)) {
            startRecording();
            recording = true;
        } else {
            if (!recording) {
                consumer.startElement(namespaceURI, lName, qName, attributes);
            } else {
                super.startElement(namespaceURI, lName, qName, attributes);
            }
        }
    }

    @Override
    public void endElement(String namespaceURI, String lName, String qName)
            throws SAXException {
        if (recording && lName.equals(fragmentName) && namespaceURI.equals(fragmentNamespace)) {
            Node fragment = endRecording();
            Object serializedXML;
            try {
                serializedXML = fragment == null ? "null" : XMLUtils.serializeNode(fragment);
                if (getLogger().isDebugEnabled()) {
                    getLogger().debug("End recording. Fragment=" + serializedXML);
                }
            } catch (ProcessingException ex) {
                Logger.getLogger(FragmentsTransformer.class.getName()).log(Level.SEVERE, null, ex);
            }
            if (null != stylesheet && null != transformerHandler) {
                final SAXResult result = new SAXResult(fragmentsTransformerConsumer);
                result.setLexicalHandler(consumer);
                this.transformerHandler.setResult(result);
                if (null == transformer) {
                    transformer = transformerHandler.getTransformer();
                }
                if (getLogger().isDebugEnabled()) {
                    getLogger().debug("Stylesheet=" + stylesheet.toString());
                    getLogger().debug("fragment=" + fragment);
                    getLogger().debug("result=" + result);
                    getLogger().debug("transformer=" + transformer);
                }
                if (null != transformer) {
                    try {
                        transformer.transform(new DOMSource(fragment), result);
                    } catch (TransformerException ex) {
                        Logger.getLogger(FragmentsTransformer.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
            recording = false;
            return;
        }
        if (namespaceURI.equals(NS_URI) && lName.equals(TRANSFORM_TAG)) {
            insideTransformElement = false;
        } else {
            if (!recording) {
                consumer.endElement(namespaceURI, lName, qName);
            } else {
                super.endElement(namespaceURI, lName, qName);
            }
        }
    }

    @Override
    public void characters(char[] c, int start, int len)
            throws SAXException {

        if (!recording) {
            consumer.characters(c, start, len);
        } else {
            super.characters(c, start, len);
        }
    }

    public void comment(char[] arg0, int arg1, int arg2) throws SAXException {
        if (recording) {
            super.comment(arg0, arg1, arg2);
        } else {
            consumer.comment(arg0, arg1, arg2);
        }
    }

    public void ignorableWhitespace(char[] arg0, int arg1, int arg2) throws SAXException {
        if (recording) {
            super.ignorableWhitespace(arg0, arg1, arg2);
        } else {
            consumer.ignorableWhitespace(arg0, arg1, arg2);
        }
    }

    public void processingInstruction(String arg0, String arg1) throws SAXException {
        if (recording) {
            super.processingInstruction(arg0, arg1);
        } else {
            consumer.processingInstruction(arg0, arg1);
        }
    }

    public void setDocumentLocator(Locator arg0) {
        if (recording) {
            super.setDocumentLocator(arg0);
        } else {
            consumer.setDocumentLocator(arg0);
        }
    }

    public void skippedEntity(String arg0) throws SAXException {
        if (recording) {
            super.skippedEntity(arg0);
        } else {
            consumer.skippedEntity(arg0);
        }
    }

    public void startCDATA() throws SAXException {
        if (recording) {
            super.startCDATA();
        } else {
            consumer.startCDATA();
        }
    }

    public void endCDATA() throws SAXException {
        if (recording) {
            super.endCDATA();
        } else {
            consumer.endCDATA();
        }
    }

    public void startDTD(String arg0, String arg1, String arg2)
            throws SAXException {
        if (recording) {
            super.startDTD(arg0, arg1, arg2);
        } else {
            consumer.startDTD(arg0, arg1, arg2);
        }
    }

    public void endDTD() throws SAXException {
        if (recording) {
            super.endDTD();
        } else {
            consumer.endDTD();
        }
    }

    public void startEntity(String arg0) throws SAXException {
        if (recording) {
            super.startEntity(arg0);
        } else {
            consumer.startEntity(arg0);
        }
    }

    public void endEntity(String arg0) throws SAXException {
        if (recording) {
            super.endEntity(arg0);
        } else {
            consumer.endEntity(arg0);
        }
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        if (recording) {
            super.startPrefixMapping(prefix, uri);
        } else {
            fragmentsTransformerConsumer.startPrefixMapping(prefix, uri);
        }
    }

    public void endPrefixMapping(String prefix) throws SAXException {
        if (recording) {
            super.endPrefixMapping(prefix);
        } else {
            fragmentsTransformerConsumer.endPrefixMapping(prefix);
        }
    }

    /**
     * Consume XML from the TraxTransformer, and pass it on to the consumer
     * of the FragmentsTransformer, except begin/endDocument.
     */
    private class FragmentsTransformerConsumer extends AbstractXMLPipe {

        @Override
        public void startDocument() throws SAXException {
            // Do not pass on when generated from a fragment transformation.
        }

        @Override
        public void endDocument() throws SAXException {
            // Do not pass on when generated from a fragment transformation.
        }

        public void startPrefixMapping(String prefix, String uri) throws SAXException {
            if (!nsPrefixes.containsKey(prefix)) {
                nsPrefixes.put(prefix, 1);
                super.startPrefixMapping(prefix, uri);
            } else {
                nsPrefixes.put(prefix, nsPrefixes.get(prefix) + 1);
            }
        }

        public void endPrefixMapping(String prefix) throws SAXException {
            if (!nsPrefixes.containsKey(prefix)) {
                return; // This is wrong, but it happens sometimes.
            }
            nsPrefixes.put(prefix, nsPrefixes.get(prefix) - 1);
            if (nsPrefixes.get(prefix) == 0) {
                super.endPrefixMapping(prefix);
                nsPrefixes.remove(prefix);
            }
        }
    }
}
