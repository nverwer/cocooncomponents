package org.apache.cocoon.serialization;

/*
 * This serializar uses FOP 0.9x instead of 0.2x, which comes standard with Cocoon 2.1.
 * 
 */

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

import org.apache.avalon.framework.configuration.Configurable;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.configuration.DefaultConfigurationBuilder;
import org.apache.avalon.framework.logger.Logger;
import org.apache.avalon.framework.service.ServiceException;
import org.apache.avalon.framework.service.ServiceManager;
import org.apache.avalon.framework.service.Serviceable;
import org.apache.cocoon.caching.CacheableProcessingComponent;
import org.apache.cocoon.serialization.AbstractSerializer;
import org.apache.excalibur.source.SourceValidity;
import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;


/**
 * Cocoon FOP serializer implementation.
 *
 * @author Arne Hildebrand 
 * @version 1.0
 */

public class CocoonFOPSerializer extends AbstractSerializer implements Configurable, CacheableProcessingComponent, Serviceable {

    protected Logger logger = null;

    protected String mimetype = null;

    protected boolean setContentLength = true;

    protected FOUserAgent userAgent = null;

    protected Fop fop = null;

    protected ServiceManager manager = null;

    protected javax.xml.transform.stream.StreamResult res = null;

    private FopFactory fopFactory = null;

    public void service(ServiceManager manager) throws ServiceException {
        this.manager = manager;
    }

    public void configure(Configuration conf) throws ConfigurationException {
        this.logger = getLogger().getChildLogger("fop");

        this.setContentLength = conf.getChild("set-content-length").getValueAsBoolean(true);
        this.mimetype = conf.getAttribute("mime-type");

        // TODO get and use user configuration
        // TODO get and use user's renderer definition, ...

    }

    public String getMimeType() {
        return this.mimetype;
    }

    public void setOutputStream(OutputStream o) {
        DefaultHandler dh = null;

        try {
            // get user agent & configure it
            userAgent = getFopFactory().newFOUserAgent();
            userAgent.setTargetResolution(150);
            userAgent.setCreator("http://www.link-lab.net");

            // create new fop for transformation
            fop = fopFactory.newFop(this.getMimeType(), userAgent, o);
            dh = fop.getDefaultHandler();
            setContentHandler(dh);
        }
        catch (Exception ex) {
            ex.printStackTrace();

        }
    }

    private FopFactory getFopFactory() throws ConfigurationException, SAXException, IOException {
        if (fopFactory == null) {
            fopFactory = FopFactory.newInstance();
            // use custom config file if exists
            DefaultConfigurationBuilder cfgBuilder = new DefaultConfigurationBuilder();
            File cfgFile = new File("conf/fop-config.xml");
            if (cfgFile.exists()) {
                Configuration cfg = cfgBuilder.buildFromFile(cfgFile);
                ((Configurable) fopFactory).configure(cfg);
            }

        }
        return fopFactory;
    }

    public Serializable getKey() {
        return "0";
    }

    public SourceValidity getValidity() {
        return null; // NOPValidity.SHARED_INSTANCE;
    }

    public void recycle() {
        super.recycle();
        this.userAgent = null;
        this.fop = null;
    }

    public boolean shouldSetContentLength() {
        return this.setContentLength;
    }

}