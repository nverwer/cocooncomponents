package org.apache.cocoon.transformation;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.sitemap.DisposableSitemapComponent;
import org.apache.cocoon.xml.AbstractXMLPipe;
import org.apache.cocoon.xml.XMLConsumer;
import org.apache.cocoon.xml.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * <p>This abstract transformer works with Cocoon 2.2. Compatibility with
 * Cocoon 2.1 has not been checked, YMMV.</p>
 * <p>This transformer processes <code>transform</code> elements in the
 * <code>http://org.apache.cocoon.transformation/fragments/1.0</code> namespace.
 * These <code>fragments:transform</code> elements have
 * <code>fragment-name</code> and <code>fragment-namespace</code> attributes
 * to determinse which child elements will be processed with the
 * processFragment() method that is provided by an implementation of this
 * abstract class.
 *
 * The abstract transformer does not do caching.
 * </p>
 * 
 * @author Huib Verweij, huiver@mpi.nl
 */
public abstract class FragmentsProcessor extends AbstractSAXTransformer
        implements DisposableSitemapComponent {
    private static final String NS_URI = "http://org.apache.cocoon.transformation/fragments/1.0";
    private static final String NS_PREFIX = "fragments";
    private static final String TRANSFORM_TAG = "transform";
    
    private static final String FRAGMENT_NAME_ATTR = "fragment-name";
    private static final String FRAGMENT_NAMESPACE_ATTR = "fragment-namespace";
    private Parameters par;
    
    private String fragmentName = "";
    private String fragmentNamespace = "";
    
    private boolean recording;
    private boolean insideTransformElement;
    private FragmentsProcessorConsumer fpc;

    private XMLConsumer consumer;
    private FragmentsProcessorConsumer fragmentsProcessorConsumer;
    
    public FragmentsProcessor() {
        super.defaultNamespaceURI = "";
    }

    @Override
    public void dispose() {
        super.dispose();
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

    /**
     * Set the <code>XMLConsumer</code> that will receive XML data.
     */
    @Override
    public void setConsumer(XMLConsumer consumer) {
        super.setConsumer(consumer);
        this.consumer = consumer;
        fragmentsProcessorConsumer = new FragmentsProcessorConsumer();
        fragmentsProcessorConsumer.setConsumer(this.consumer);
    }
    
    
    /*
     * SAX methods.
     */
    
    @Override
    public void startDocument() throws SAXException {
        super.startDocument();
    }

    @Override
    public void endDocument() throws SAXException {
        super.endDocument();
    }
    
    @Override
    public void startElement(String namespaceURI, String lName, String qName, Attributes attributes)
            throws SAXException {
        if (namespaceURI.equals(NS_URI) && lName.equals(TRANSFORM_TAG)) {
            insideTransformElement = true;
            this.fragmentName = attributes.getValue(FRAGMENT_NAME_ATTR);
            this.fragmentNamespace = attributes.getValue(FRAGMENT_NAMESPACE_ATTR);
            if (getLogger().isDebugEnabled()) {
                getLogger().debug("fragmentName=" + fragmentName);
                getLogger().debug("fragmentNamespace=" + fragmentNamespace);
            }
        } else if (insideTransformElement && lName.equals(this.fragmentName) && namespaceURI.equals(this.fragmentNamespace)) {
            if (getLogger().isDebugEnabled()) {
                getLogger().debug("start recording (" + this.fragmentNamespace + ":" + this.fragmentName + ")");
            }
            startSerializedXMLRecording(XMLUtils.createPropertiesForXML(true));
            super.startElement(namespaceURI, lName, qName, attributes);
            recording = true;
        } else {
            super.startElement(namespaceURI, lName, qName, attributes);
        }
    }

    @Override
    public void endElement(String namespaceURI, String lName, String qName)
            throws SAXException {
        if (recording && lName.equals(this.fragmentName) && namespaceURI.equals(this.fragmentNamespace)) {
            if (getLogger().isDebugEnabled()) {
                getLogger().debug("end recording (" + this.fragmentNamespace + ":" + this.fragmentName + ")");
            }
            super.endElement(namespaceURI, lName, qName);
            recording = false;
            try {
                String serializedXML = endSerializedXMLRecording();
                processFragment(serializedXML, fragmentsProcessorConsumer);
            } catch (ProcessingException ex) {
                Logger.getLogger(FragmentsTransformer.class.getName()).log(Level.SEVERE, null, ex);
            }
            return;
        }
        if (namespaceURI.equals(NS_URI) && lName.equals(TRANSFORM_TAG)) {
            insideTransformElement = false;
        } else {
            super.endElement(namespaceURI, lName, qName);
        }
    }

    
    public abstract void processFragment(String xml, AbstractXMLPipe fragmentsProcessorConsumer) throws SAXException;

    
    
    /**
     * Consume XML from the TraxTransformer, and pass it on to the consumer
     * of the FragmentsTransformer, except start/endDocument.
     */
    private class FragmentsProcessorConsumer extends AbstractXMLPipe {

        @Override
        public void startDocument() throws SAXException {
            // Do not pass on when generated from a fragment transformation.
            if (getLogger().isDebugEnabled()) {
                getLogger().debug("Skipping startDocument()");
            }
        }

        @Override
        public void endDocument() throws SAXException {
            // Do not pass on when generated from a fragment transformation.
            if (getLogger().isDebugEnabled()) {
                getLogger().debug("Skipping endDocument()");
            }
        }

    }
}
