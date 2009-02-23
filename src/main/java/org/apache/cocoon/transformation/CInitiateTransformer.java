//$Id: CInitiateTransformer.java,v 1.10 local $
package org.apache.cocoon.transformation;

import java.io.*;
import java.util.*;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.components.source.SourceUtil;
import org.apache.cocoon.environment.ObjectModelHelper;
import org.apache.cocoon.environment.Request;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.xml.XMLUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.excalibur.source.Source;
import org.apache.excalibur.source.SourceParameters;
import org.apache.excalibur.source.impl.URLSourceFactory;

import org.xml.sax.Attributes;
//import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Process another request in a new thread, continuing the current request.
 *
 * Declaration in sitemap:
 * <pre>
 *  &lt;map:transformer name="cinitiate" logger="cinitiate" src="org.apache.cocoon.transformation.CInitiateTransformer"/&gt;
 * </pre>
 * Of course, you'll need to declare a cinitiate logger in logkit.xconf.
 * Define a new logtarget:
 * <pre>
 *     &lt;cocoon id="cinitiate"&gt;
 *        &lt;filename&gt;${context-root}/WEB-INF/logs/process.log&lt;/filename&gt;
 *        &lt;format type="cocoon"&gt;
 *          %7.7{priority} %{time}   [%{category}] (%{uri}) %{thread}/%{class:short}:\n%{message}\n
 *        &lt;/format&gt;
 *        &lt;append&gt;false&lt;/append&gt;
 *      &lt;/cocoon&gt;
 * </pre>
 * and a new category:
 * <pre>
 *     &lt;category name="cinitiate" log-level="INFO"&gt;
 *       &lt;log-target id-ref="cinitiate"/&gt;
 *       &lt;log-target id-ref="error"/&gt;
 *     &lt;/category&gt;
 * </pre>
 *
 * Usage in pipeline:
 * <pre>
 *  &lt;map:transform type="cinitiate"/&gt;
 * </pre>
 * It will transform 'initiate' elements in the "http://apache.org/cocoon/initiate/1.0" namespace.
 *
 * The XML looks like this:
 * <pre>
 *  &lt;initiate src="http://myserver/full/path/to/include.xml"
 *      method="GET"
 *      xmlns="http://apache.org/cocoon/initiate/1.0"&gt;
 *    &lt;parameter name="parameter-name"&gt;parameter-value&lt;/parameter&gt;
 *  &lt;/initiate&gt;
 * </pre>
 * The request will be initiated in a separate thread.
 * The src attribute must be a complete, absolute URL, and may use any protocol that Cocoon
 * understands (including cocoon://).
 * If the cocoon: protocol is used, the URL is rewritten to use the protocol of the original request.
 * This is because 'internal' requests cause weird problems (the source resolver disappears after
 * some time).
 * The method attribute is optional, and may be "GET" (default) or "POST".
 * There may be zero or more &lt;parameter&gt; elements, which are the request parameters.
 * The parameter value may be an XML fragment.
 * 
 * The result of the request will be written to the log file.
 *
 * The CInitiate transformer will replace the 'initiate' element with a 'process' element:
 * <pre>
 *  &lt;process src="include.xml" request="http://myserver/full/path/to/include.xml"
 *      output="WEB-INF/logs/process/123.out"
 *      id="123" status="started" xmlns=http://apache.org/cocoon/initiate/1.0"
 * /&gt;
 * </pre>
 * The id attribute is used to identify the process and find it in the log-file.
 * The status attribute may be 'started' (if the thread was successfully started)
 * or 'failed' (if the thread could not be started).
 * The src attribute is the original URI, and the request attribute the rewritten URI.
 * The output attribute is the path to the file, relative to the webapp context,
 * on which the process writes its output.
 */
public class CInitiateTransformer
        extends AbstractSAXTransformer {

    private static long timestamp = (new java.util.Date()).getTime(); // for CThread class
    private static final String CINITIATE_NAMESPACE_URI = "http://apache.org/cocoon/initiate/1.0";
    private static final String INITIATE_ELEMENT = "initiate";
    private static final String PARAMETER_ELEMENT = "parameter";
    private static final String PROCESS_ELEMENT = "process";
    private static final String SOURCE_ATTRIBUTE = "src";
    private static final String METHOD_ATTRIBUTE = "method";
    private static final String NAME_ATTRIBUTE = "name";
    private static final String REQUEST_ATTRIBUTE = "request";
    private static final String OUTPUT_ATTRIBUTE = "output";
    private static final String PROCID_ATTRIBUTE = "id";
    private static final String STATUS_ATTRIBUTE = "status";//  private SourceResolver resolver;
    private Log logger;
    private String contextDir;
    private String requestSchemeHostPort;
    private String src;
    private Parameters configParameters;
    private SourceParameters requestParameters;
    private String parameterName;

    public CInitiateTransformer() {
        this.defaultNamespaceURI = CINITIATE_NAMESPACE_URI;
    }

    @Override
    public void setup(SourceResolver resolver, Map objectModel, String src, Parameters par)
            throws ProcessingException, SAXException, IOException {
        super.setup(resolver, objectModel, src, par);
//    this.resolver = resolver;
        this.logger = this.getLogger();
        this.contextDir = ObjectModelHelper.getContext(objectModel).getRealPath("");
        Request request = ObjectModelHelper.getRequest(objectModel);
        this.requestSchemeHostPort = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
    }

    @Override
    public void startTransformingElement(String namespaceURI, String lName, String qName, Attributes attributes)
            throws SAXException {
        if (lName.equals(INITIATE_ELEMENT)) {
            this.src = attributes.getValue(SOURCE_ATTRIBUTE);
            this.configParameters = new Parameters();
            String method = attributes.getIndex(METHOD_ATTRIBUTE) < 0 ? "GET" : attributes.getValue(METHOD_ATTRIBUTE);
            this.configParameters.setParameter("method", method);
            this.requestParameters = new SourceParameters();
        } else if (lName.equals(PARAMETER_ELEMENT)) {
            parameterName = attributes.getValue(NAME_ATTRIBUTE);
            startSerializedXMLRecording(XMLUtils.createPropertiesForXML(true));
        } else {
            throw new SAXException("Bad element in " + CINITIATE_NAMESPACE_URI + " namespace: " + lName);
        }
    }

    @Override
    public void endTransformingElement(String namespaceURI, String lName, String qName)
            throws SAXException, ProcessingException {
        if (lName.equals(INITIATE_ELEMENT)) {
            AttributesImpl attrs = new AttributesImpl();
            attrs.addAttribute(null, SOURCE_ATTRIBUTE, SOURCE_ATTRIBUTE, "CDATA", src);
            try {
                CThread process = new CThread(src, resolver, logger, contextDir, configParameters, requestParameters);
                process.start();
                attrs.addAttribute(null, REQUEST_ATTRIBUTE, REQUEST_ATTRIBUTE, "CDATA", process.getSrc());
                attrs.addAttribute(null, OUTPUT_ATTRIBUTE, OUTPUT_ATTRIBUTE, "CDATA", process.getOut());
                attrs.addAttribute(null, PROCID_ATTRIBUTE, PROCID_ATTRIBUTE, "CDATA", process.getName());
                attrs.addAttribute(null, STATUS_ATTRIBUTE, STATUS_ATTRIBUTE, "CDATA", "started");
            } catch (Exception e) {
                logger.error("Unable to initiate sub-process for URL " + src, e);
                attrs.addAttribute(null, STATUS_ATTRIBUTE, STATUS_ATTRIBUTE, "CDATA", "failed");
            }
            // Don't use super, because it will redirect to this class!
            contentHandler.startElement(CINITIATE_NAMESPACE_URI, PROCESS_ELEMENT, PROCESS_ELEMENT, attrs);
            contentHandler.endElement(CINITIATE_NAMESPACE_URI, PROCESS_ELEMENT, PROCESS_ELEMENT);
        } else if (lName.equals(PARAMETER_ELEMENT)) {
            requestParameters.setParameter(parameterName, endSerializedXMLRecording());
        }
    }

    private class CThread extends Thread {

        private String src;
        private SourceResolver resolver;
        private Log logger;
        private String outputFileName;
        private Parameters configParameters;
        private SourceParameters requestParameters;

        public CThread(String src, SourceResolver resolver, Log logger, String contextDir, Parameters configParameters, SourceParameters requestParameters)
                throws Exception {
            super("CThread_" + (++timestamp));
            this.resolver = resolver;
            this.logger = logger;
            /* We used to resolve now, to absolutize the URI.
            However, resolving actually makes a request, and that is not what we intend.
            Source source = resolver.resolveURI(src);
            this.src = source.getURI().replaceFirst("^cocoon:/", requestSchemeHostPort);
            resolver.release(source);
             */
            src = src.replaceFirst("^cocoon:/", requestSchemeHostPort);
            if (!src.matches("^\\w+://.*")) {
                throw new Exception("Cinitiate URL is not complete and absolute: " + src);
            }
            this.src = src;
            new File(contextDir + "/WEB-INF/logs/process").mkdir();
            this.outputFileName = "WEB-INF/logs/process/" + this.getName() + ".out";
            this.configParameters = configParameters;
            this.requestParameters = requestParameters;
        }

        public String getSrc() {
            return src;
        }

        public String getOut() {
            return outputFileName;
        }

        public void run() {
            URLSourceFactory sourceFactory = new URLSourceFactory();
            Source source = null;
            OutputStream output = null;
            logger.info("Starting sub-process " + this.getName() + " (" + src + ") writing output to " + contextDir + "/" + outputFileName);
            try {
                output = new FileOutputStream(contextDir + "/" + outputFileName);
                // Resolve with absolute URI in src, made in the constructor.
                //source = sourceFactory.getSource(src, null);
                source = SourceUtil.getSource(src, configParameters, requestParameters, resolver);
                /* This does not seem to work correctly when method="POST". So we rely on getInputStream to throw an exception.
                if (!source.exists()) {
                throw new IOException("The source "+src+" does not exist?!");
                }
                 */
                InputStream sourceInput = source.getInputStream();
                IOUtils.copy(sourceInput, output);
                logger.info("Completing sub-process " + this.getName() + " (" + src + ")");
            } catch (Exception e) {
                logger.error("Error in sub-process " + this.getName() + " (" + src + "):\n" + e.toString(), e.getCause());
            }
            if (source != null) {
                sourceFactory.release(source);
            }
            try {
                if (output != null) {
                    output.close();
                }
            } catch (IOException e) {
            }
        }
    }
}
