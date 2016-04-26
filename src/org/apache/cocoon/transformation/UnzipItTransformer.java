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
import org.apache.cocoon.transformation.AbstractTransformer;
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
 * @unzip (optional): if "true" then the dowloaded file will be unzipped into
 * a folder with the name of the target-file without extension.
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
public class DownloadTransformer extends AbstractTransformer {

    public static final String DOWNLOAD_NS = "http://apache.org/cocoon/download/1.0";

    public static final String DOWNLOAD_ELEMENT = "download";
    public static final String RESULT_ELEMENT = "result";
    public static final String ERROR_ELEMENT = "error";
    public static final String SRC_ATTRIBUTE = "src";
    public static final String TARGET_ATTRIBUTE = "target";
    public static final String TARGETDIR_ATTRIBUTE = "target-dir";
    public static final String UNZIP_ATTRIBUTE = "unzip";
    public static final String UNZIPPED_ATTRIBUTE = "unzipped";
    
    @SuppressWarnings("rawtypes")
    @Override
    public void setup(SourceResolver sourceResolver, Map model, String src,
            Parameters params) throws ProcessingException, SAXException, IOException {
    }

    @Override
    public void startElement(String namespaceURI, String localName,
            String qName, Attributes attributes) throws SAXException {
        if (DOWNLOAD_NS.equals(namespaceURI) && DOWNLOAD_ELEMENT.equals(localName)) {
            try {
                File[] downloadResult = download(
                    attributes.getValue(SRC_ATTRIBUTE), 
                    attributes.getValue(TARGETDIR_ATTRIBUTE),
                    attributes.getValue(TARGET_ATTRIBUTE),
                    attributes.getValue(UNZIP_ATTRIBUTE)
                );
                File downloadedFile = downloadResult[0];
                File unzipDir = downloadResult[1];
                
                String absPath = downloadedFile.getCanonicalPath();
                
                AttributesImpl attrsImpl = new AttributesImpl();
                if (!("".equals(unzipDir))) {
                    attrsImpl.addAttribute("", UNZIPPED_ATTRIBUTE, UNZIPPED_ATTRIBUTE, "CDATA", unzipDir.getAbsolutePath());
                }
                super.startElement(namespaceURI, RESULT_ELEMENT, qName, attrsImpl);
                super.characters(absPath.toCharArray(), 0, absPath.length());
                super.endElement(namespaceURI, RESULT_ELEMENT, qName);
            } catch (Exception e) {
                // throw new SAXException("Error downloading file", e);
                super.startElement(namespaceURI, ERROR_ELEMENT, qName, attributes);
                String message = e.getMessage();
                super.characters(message.toCharArray(), 0, message.length());
                super.endElement(namespaceURI, ERROR_ELEMENT, qName);
            }
        } else {
            super.startElement(namespaceURI, localName, qName, attributes);
        }
    }

    @Override
    public void endElement(String namespaceURI, String localName, String qName)
            throws SAXException {
        if (DOWNLOAD_NS.equals(namespaceURI) && DOWNLOAD_ELEMENT.equals(localName)) {
            return;
        }
        super.endElement(namespaceURI, localName, qName);
    }

    private File[] download(String sourceUri, String targetDir, String target, String unzip)
            throws ProcessingException, IOException, SAXException {
        File targetFile = null;
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
        boolean unzipFile = null != unzip && unzip.equals("true");                
        String unzipDir = unzipFile ? FilenameUtils.getBaseName(targetFile.getAbsolutePath()) : "";
        
        HttpClient httpClient = new HttpClient();
        httpClient.setConnectionTimeout(5000);
        httpClient.setTimeout(5000);
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
            unZipped = unZipIt(targetFile, unzipDir);
        }
        
        return new File[] {targetFile, unZipped};
    }
    
    
    /**
     * Unzip it
     * @param zipFile input zip file
     * @param outputFolder zip file output folder
     */
    private File unZipIt(File zipFile, String outputFolder){

    byte[] buffer = new byte[4096];
    
    File folder = null;

    try{
    		
    	//create output directory is not exists
    	folder = new File(outputFolder);
    	if(!folder.exists()){
    		folder.mkdir();
    	}
    		
         //get the zipped file list entry
         try ( //get the zip file content
                 ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
             //get the zipped file list entry
             ZipEntry ze = zis.getNextEntry();
             
             while(ze!=null){
                 
                 String fileName = ze.getName();
                 File newFile = new File(outputFolder + File.separator + fileName);
                 
                 System.out.println("file unzip : "+ newFile.getAbsoluteFile());
                 
                 //create all non existing folders
                 //else you will hit FileNotFoundException for compressed folder
                 new File(newFile.getParent()).mkdirs();
                 
                 try (FileOutputStream fos = new FileOutputStream(newFile)) {
                     int len;
                     while ((len = zis.read(buffer)) > 0) {
                         fos.write(buffer, 0, len);
                     }
                 }
                 ze = zis.getNextEntry();
             }
             
             zis.closeEntry();
         }
    		
    	System.out.println("Done unzipping.");
    		
        } catch(IOException ex){
           ex.printStackTrace(); 
        }
     
        return folder;
    }    
}

