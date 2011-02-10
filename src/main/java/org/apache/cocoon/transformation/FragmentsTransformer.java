package org.apache.cocoon.transformation;

import java.io.IOException;
import java.net.MalformedURLException;
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
import org.xml.sax.SAXException;

/**
 * <p>This transformer works with Cocoon 2.2. Compatibility with
 * Cocoon 2.1 has not been checked, YMMV.</p>
 * <p>This transformer processes <code>fragmentName</code> elements in the
 * <code>http://org.apache.cocoon.transformation/fragments/1.0</code> namespace. It uses the
 * <code>fragmentName:transform</code> element to transform child elements
 * with the stylesheet that is loaded from it's <code>stylesheet-uri</code>
 * attribute that obviouslycontains the URI for the stylesheet to use.
 * The <code>fragmentName</code> and the <code>fragmentName-namespace</code> attributes
 * specify which elements must be transformed.
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
        this.consumer = consumer;
        fragmentsTransformerConsumer = new FragmentsTransformerConsumer();
        fragmentsTransformerConsumer.setConsumer(this.consumer);
    }

    /*
     * SAX methods.
     */
    @Override
    public void startDocument() throws SAXException {
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
        }
        else {
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
    }
}
