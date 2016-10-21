package org.apache.cocoon.transformation;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * This transformer downloads a new file to disk.
 * <p>
 * It triggers for elements in the namespace "http://apache.org/cocoon/download/1.0".
 * Attributes:
 * @src : the file that should be downloaded
 * @target (optional): path where the file should be stored (includes filename)
 * @target-dir (optional): directory where the file should be stored
 * @unzip (optional): if "true" then unzip file after downloading.
 * If there is no @target or @target-dir attribute a temporary file is created.
 * <p>
 * Example XML input:
 * <pre>
 * {@code
 *   <download:download src="http://some.server.com/zipfile.zip"
 *                  target="/tmp/zipfile.zip" unzip="true"/>
 * }
 * </pre>
 * The @src specifies the file that should be downloaded. The 
 * @target specifies where the file should be stored. @unzip is true, so the 
 * file will be unzipped immediately.
 * <p>
 * The result is
 * <pre>
 * {@code
 *   <download:result unzipped="/path/to/unzipped/file/on/disk">/path/to/file/on/disk</download:result>
 * }
 * </pre>
 * (@unzipped is only present when @unzip="true") or 
 * <pre>
 * {@code
 *   <download:error>The error message</download:file>
 * }
 * </pre>
 * if an error (other than a HTTP error) occurs.
 * HTTP errors are thrown.
 * Define this transformer in the sitemap:
 * <pre>
 * {@code
 * <map:components>
 *   <map:transformers>
 *     <map:transformer name="download" logger="sitemap.transformer.download"
 *         src="org.apache.cocoon.transformation.DownloadTransformer"/>
 *  ...
 * }
 * </pre>
 * Use this transformer:
 * <pre>
 * {@code
 * <map:transform type="download"/>
 * }
 * </pre>
 * 
 *
 * @author <a href="mailto:maarten.kroon@koop.overheid.nl">Maarten Kroon</a>
 * @author <a href="mailto:hhv@x-scale.nl">Huib Verweij</a>
 */
public class DownloadTransformer extends AbstractSAXTransformer {

    public static final String DOWNLOAD_NS = "http://apache.org/cocoon/download/1.0";

    public static final String DOWNLOAD_ELEMENT = "download";
    private static final String DOWNLOAD_PREFIX = "download";
    public static final String RESULT_ELEMENT = "result";
    public static final String ERROR_ELEMENT = "error";
    public static final String SRC_ATTRIBUTE = "src";
    public static final String TARGET_ATTRIBUTE = "target";
    public static final String TARGETDIR_ATTRIBUTE = "target-dir";
    public static final String UNZIP_ATTRIBUTE = "unzip";
    public static final String RECURSIVE_UNZIP_ATTRIBUTE = "recursive-unzip";
    public static final String UNZIPPED_ATTRIBUTE = "unzipped";
    
    
    public DownloadTransformer() {
        this.defaultNamespaceURI = DOWNLOAD_NS;
    }

    @Override
    public void setup(SourceResolver resolver, Map objectModel, String src,
            Parameters params) throws ProcessingException, SAXException, IOException {
        super.setup(resolver, objectModel, src, params);
    }
    
    @Override
    public void startTransformingElement(String uri, String localName,
            String qName, Attributes attributes) throws SAXException, ProcessingException, IOException {
        if (DOWNLOAD_NS.equals(uri) && DOWNLOAD_ELEMENT.equals(localName)) {
            try {
                File[] downloadResult = download(
                    attributes.getValue(SRC_ATTRIBUTE), 
                    attributes.getValue(TARGETDIR_ATTRIBUTE),
                    attributes.getValue(TARGET_ATTRIBUTE),
                    attributes.getValue(UNZIP_ATTRIBUTE),
                    attributes.getValue(RECURSIVE_UNZIP_ATTRIBUTE)
                );
                File downloadedFile = downloadResult[0];
                File unzipDir = downloadResult[1];
                
                String absPath = downloadedFile.getCanonicalPath();
                
                AttributesImpl attrsImpl = new AttributesImpl();
                if (unzipDir != null) {
                    attrsImpl.addAttribute("", UNZIPPED_ATTRIBUTE, UNZIPPED_ATTRIBUTE, "CDATA", unzipDir.getAbsolutePath());
                }
                xmlConsumer.startElement(uri, RESULT_ELEMENT, String.format("%s:%s", DOWNLOAD_PREFIX, RESULT_ELEMENT), attrsImpl);
                xmlConsumer.characters(absPath.toCharArray(), 0, absPath.length());
                xmlConsumer.endElement(uri, RESULT_ELEMENT, String.format("%s:%s", DOWNLOAD_PREFIX, RESULT_ELEMENT));
            } catch (Exception e) {
                // throw new SAXException("Error downloading file", e);
                xmlConsumer.startElement(uri, ERROR_ELEMENT, qName, attributes);
                String message = e.getMessage();
                xmlConsumer.characters(message.toCharArray(), 0, message.length());
                xmlConsumer.endElement(uri, ERROR_ELEMENT, qName);
            }
        } else {
            super.startTransformingElement(uri, localName, qName, attributes);
        }
    }

    @Override
    public void endTransformingElement(String uri, String localName, String qName)
            throws SAXException, ProcessingException, IOException {
        if (DOWNLOAD_NS.equals(namespaceURI) && DOWNLOAD_ELEMENT.equals(localName)) {
            return;
        }
        super.endTransformingElement(uri, localName, qName);
    }

    private File[] download(String sourceUri, String targetDir, String target, String unzip, String recursiveUnzip)
            throws ProcessingException, IOException, SAXException {
        File targetFile;
        File unZipped = null;

        if (null != target && !target.equals("")) {
            targetFile = new File(target);
        } else if (null != targetDir && !targetDir.equals("")) {
            targetFile = new File(targetDir);
        } else {
            String baseName = FilenameUtils.getBaseName(sourceUri);
            String extension = FilenameUtils.getExtension(sourceUri);

            targetFile = File.createTempFile(baseName, "." + extension);
        }
        if (!targetFile.getParentFile().exists()) {
            targetFile.getParentFile().mkdirs();
        }
        boolean unzipFile = (null != unzip && unzip.equals("true")) || 
                (null != recursiveUnzip && recursiveUnzip.equals("true"));
        String absPath = targetFile.getAbsolutePath();
        String unzipDir = unzipFile ? FilenameUtils.removeExtension(absPath) : "";
        
        HttpClient httpClient = new HttpClient();
        httpClient.setConnectionTimeout(60000);
        httpClient.setTimeout(60000);
        
        if (System.getProperty("http.proxyHost") != null) {
            // getLogger().warn("PROXY: "+System.getProperty("http.proxyHost"));
            String nonProxyHostsRE = System.getProperty("http.nonProxyHosts", "");
            if (nonProxyHostsRE.length() > 0) {
                String[] pHosts = nonProxyHostsRE.replaceAll("\\.", "\\\\.").replaceAll("\\*", ".*").split("\\|");
                nonProxyHostsRE = "";
                for (String pHost : pHosts) {
                    nonProxyHostsRE += "|(^https?://" + pHost + ".*$)";
                }
                nonProxyHostsRE = nonProxyHostsRE.substring(1);
            }
            if (nonProxyHostsRE.length() == 0 || !sourceUri.matches(nonProxyHostsRE)) {
                try {
                    HostConfiguration hostConfiguration = httpClient.getHostConfiguration();
                    hostConfiguration.setProxy(System.getProperty("http.proxyHost"), Integer.parseInt(System.getProperty("http.proxyPort", "80")));
                    httpClient.setHostConfiguration(hostConfiguration);
                } catch (Exception e) {
                    throw new ProcessingException("Cannot set proxy!", e);
                }
            }
        }

        
        HttpMethod httpMethod = new GetMethod(sourceUri);
        try {
            int responseCode = httpClient.executeMethod(httpMethod);
            if (responseCode < 200 || responseCode >= 300) {
                throw new ProcessingException(String.format("Received HTTP status code %d (%s)", responseCode, httpMethod.getStatusText()));
            }
            OutputStream os = new BufferedOutputStream(new FileOutputStream(targetFile));
            try {
                IOUtils.copyLarge(httpMethod.getResponseBodyAsStream(), os);
            } finally {
                os.close();
            }
        } finally {
                httpMethod.releaseConnection();
        }
        if (!"".equals(unzipDir)) {
            unZipped = unZipIt(targetFile, unzipDir, recursiveUnzip);
        }
        
        return new File[] {targetFile, unZipped};
    }
    
    
    /**
     * Unzip it
     * @param zipFile input zip file
     * @param outputFolder zip file output folder
     */
    private File unZipIt(File zipFile, String outputFolder, String recursiveUnzip){

    byte[] buffer = new byte[4096];
    
    File folder = null;

    try{
    		
    	//create output directory is not exists
    	folder = new File(outputFolder);
    	if (!folder.exists()){
    		folder.mkdir();
    	}
    		
         //get the zipped file list entry
         try (
             //get the zip file content
             ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
             //get the zipped file list entry
             ZipEntry ze = zis.getNextEntry();
             
             while(ze != null){
                 
                 String fileName = ze.getName();
                 File newFile = new File(outputFolder + File.separator + fileName);
                 
                 // System.out.println("file unzip : "+ newFile.getAbsoluteFile());
                 
                 // create all non existing folders
                 // else you will hit FileNotFoundException for compressed folder
                 new File(newFile.getParent()).mkdirs();
                 
                 try (FileOutputStream fos = new FileOutputStream(newFile)) {
                     int len;
                     while ((len = zis.read(buffer)) > 0) {
                         fos.write(buffer, 0, len);
                     }
                 }
                 
                 if ((null != recursiveUnzip && "true".equals(recursiveUnzip)) && FilenameUtils.getExtension(fileName).equals("zip")) {
                     unZipIt(newFile, FilenameUtils.concat(outputFolder, FilenameUtils.getBaseName(fileName)), recursiveUnzip);
                 }
                 ze = zis.getNextEntry();
             }
             
             zis.closeEntry();
         }
    		
    	// System.out.println("Done unzipping.");
    		
        } catch(IOException ex){
           ex.printStackTrace(); 
        }
     
        return folder;
    }    
}

