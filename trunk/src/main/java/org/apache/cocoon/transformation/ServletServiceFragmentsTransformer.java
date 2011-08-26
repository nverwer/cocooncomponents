/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apache.cocoon.transformation;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;

import org.apache.avalon.framework.parameters.ParameterException;
import org.apache.commons.io.IOUtils;
import org.apache.excalibur.source.SourceException;

import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.components.source.util.SourceUtil;
import org.apache.cocoon.core.xml.SAXParser;
import org.apache.cocoon.servletservice.postable.PostableSource;
import org.apache.cocoon.xml.AbstractXMLPipe;

import org.xml.sax.SAXException;

/**
 *
 * @author huiver
 */
public class ServletServiceFragmentsTransformer extends FragmentsProcessor {

    private SAXParser saxParser;
    private String service;

    public SAXParser getSaxParser() {
        return saxParser;
    }

    public void setSaxParser(SAXParser saxParser) {
        this.saxParser = saxParser;
    }

    @Override
    public void setupTransforming() throws IOException, ProcessingException, SAXException {
        super.setupTransforming();

        try {
            service = parameters.getParameter("service");
        } catch (ParameterException e) {
            throw new ProcessingException(e);
        }

    }

    @Override
    public void processFragment(String xml, AbstractXMLPipe fragmentsProcessorConsumer) throws SAXException {
        PostableSource servletSource;
        OutputStream outputStream;
        if (null != xml) {
            try {
                try {
                    servletSource = (PostableSource) resolver.resolveURI(service);
                    outputStream = servletSource.getOutputStream();
                } catch (ClassCastException e) {
                    throw new ProcessingException("Resolved '" + service + "' to source that is not postable. Use servlet: protocol for service calls.");
                } catch (SourceException se) {
                    throw SourceUtil.handle("Error during resolving of '" + service + "'.", se);
                }

                if (getLogger().isDebugEnabled()) {
                    getLogger().debug("Source " + service + " resolved to " + servletSource.getURI());
                }
                if (getLogger().isDebugEnabled()) {
                    getLogger().debug("process fragment: xml = " + xml);
                    getLogger().debug("process fragment: outputStream = " + outputStream);
                }
                //FIXME: Not sure if UTF-8 should always be used, do we have defined this encoding somewhere in Cocoon?
                IOUtils.copy(new StringReader(xml), outputStream, "UTF-8");
                SourceUtil.parse(saxParser, servletSource, fragmentsProcessorConsumer);
                
                // Clean up
                outputStream.close();
                outputStream = null;
                if (servletSource != null) {
                    resolver.release(servletSource);
                    servletSource = null;
                }
            } catch (Exception e) {
                throw new SAXException("Exception occured while calling servlet service", e);
            }
        }
    }
    
}
