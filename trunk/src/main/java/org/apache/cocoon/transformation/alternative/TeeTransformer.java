package org.apache.cocoon.transformation.alternative;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.transformation.AbstractSAXTransformer;
import org.apache.cocoon.xml.XMLConsumer;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class TeeTransformer extends AbstractSAXTransformer {

    private XMLConsumer teeXmlConsumer;

    @Override
    public void setup(SourceResolver resolver, Map objectModel, String src, Parameters params)
            throws ProcessingException, SAXException, IOException {
        teeXmlConsumer = new TeeXMLConsumer(getLogger(), resolver, objectModel, src, params);
        super.setup(resolver, objectModel, src, params);
    }

    @Override
    public void characters(char[] p0, int p1, int p2) throws SAXException {
        teeXmlConsumer.characters(p0, p1, p2);
        super.characters(p0, p1, p2);
    }

    @Override
    public void comment(char[] ary, int start, int length) throws SAXException {
        teeXmlConsumer.comment(ary, start, length);
        super.comment(ary, start, length);
    }

    @Override
    public void endCDATA() throws SAXException {
        teeXmlConsumer.endCDATA();
        super.endCDATA();
    }

    @Override
    public void endDocument() throws SAXException {
        teeXmlConsumer.endDocument();
        super.endDocument();
    }

    @Override
    public void endDTD() throws SAXException {
        teeXmlConsumer.endDTD();
        super.endDTD();
    }

    @Override
    public void endElement(String uri, String name, String raw)
            throws SAXException {
        teeXmlConsumer.endElement(uri, name, raw);
        super.endElement(uri, name, raw);
    }

    @Override
    public void endEntity(String name) throws SAXException {
        teeXmlConsumer.endEntity(name);
        super.endEntity(name);
    }

    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
        teeXmlConsumer.endPrefixMapping(prefix);
        super.endPrefixMapping(prefix);
    }

    @Override
    public void ignorableWhitespace(char[] p0, int p1, int p2)
            throws SAXException {
        teeXmlConsumer.ignorableWhitespace(p0, p1, p2);
        super.ignorableWhitespace(p0, p1, p2);
    }

    @Override
    public void processingInstruction(String target, String data)
            throws SAXException {
        teeXmlConsumer.processingInstruction(target, data);
        super.processingInstruction(target, data);
    }

    @Override
    public void skippedEntity(String name) throws SAXException {
        teeXmlConsumer.skippedEntity(name);
        super.skippedEntity(name);
    }

    @Override
    public void startCDATA() throws SAXException {
        teeXmlConsumer.startCDATA();
        super.startCDATA();
    }

    @Override
    public void startDocument() throws SAXException {
        teeXmlConsumer.startDocument();
        super.startDocument();
    }

    @Override
    public void startDTD(String name, String public_id, String system_id)
            throws SAXException {
        teeXmlConsumer.startDTD(name, public_id, system_id);
        super.startDTD(name, public_id, system_id);
    }

    @Override
    public void startElement(String uri, String name, String raw, Attributes attr)
            throws SAXException {
        teeXmlConsumer.startElement(uri, name, raw, attr);
        super.startElement(uri, name, raw, attr);
    }

    @Override
    public void startEntity(String name) throws SAXException {
        teeXmlConsumer.startEntity(name);
        super.startEntity(name);
    }

    @Override
    public void startPrefixMapping(String prefix, String uri) throws 
            SAXException {
        teeXmlConsumer.startPrefixMapping(prefix, uri);
        super.startPrefixMapping(prefix, uri);
    }

    private class TeeXMLConsumer extends AbstractSAXTransformer {

        private PrintStream tee;

        public TeeXMLConsumer(Log logger, SourceResolver resolver, Map objectModel, String src, Parameters params)
                throws ProcessingException, SAXException, IOException {
            setup(resolver, objectModel, src, params);
            tee = new PrintStream(new FileOutputStream(src), true, "UTF-8");
        }

        @Override
        public void endDocument() throws SAXException {
            try {
                String document = endSerializedXMLRecording();
                tee.print(document);
            } catch (ProcessingException e) {
                throw new SAXException(e.getMessage());
            }
        }

        @Override
        public void startDocument() throws SAXException {
            startSerializedXMLRecording(null);
        }
    }
}
