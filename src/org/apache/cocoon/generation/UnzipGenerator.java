package org.apache.cocoon.generation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.components.source.SourceUtil;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.commons.io.FilenameUtils;
import org.apache.excalibur.source.Source;
import org.apache.excalibur.source.SourceException;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Unzip a file.
 * @author Huib Verweij.
 *
 */
public class UnzipGenerator extends ServiceableGenerator {

    private Source inputSource;
    private String entryName;
    private String note;
    private final String ZIPEXTENSION = "zip";
    private final String PREFIX = "unzip";
    private final String ROOT_ELEMENT = "folder";
    private final String NAMESPACE = "http://apache.org/cocoon/unzip/1.0";

    ;

  /**
   * Setup the file generator.
   * Try to get the last modification date of the source for caching.
   */
  @Override
    public void setup(SourceResolver resolver, Map objectModel, String src, Parameters par)
            throws ProcessingException, SAXException, IOException {
        if (!src.endsWith(ZIPEXTENSION)) {
            throw new ProcessingException(src + " does not denote a zip-file (use the " + ZIPEXTENSION + " protocol)");
        }

        super.setup(resolver, objectModel, src, par);
        try {
            this.inputSource = super.resolver.resolveURI(src);
        } catch (SourceException se) {
            throw SourceUtil.handle("Error during resolving of '" + src + "'.", se);
        }
    }

    @Override
    public void generate() throws IOException, SAXException, ProcessingException {

        String systemId = this.inputSource.getURI();

        String unzipDir = FilenameUtils.removeExtension(systemId);

        File unZipped = unZipIt(new File(systemId), unzipDir);

        contentHandler.startDocument();
        String rootQName = PREFIX + ":" + ROOT_ELEMENT;
        AttributesImpl attrs = new AttributesImpl();
        contentHandler.startElement(NAMESPACE, ROOT_ELEMENT, rootQName, attrs);
        contentHandler.characters(unZipped.getAbsolutePath().toCharArray(), 0, unZipped.getAbsolutePath().length());
        contentHandler.endElement(NAMESPACE, ROOT_ELEMENT, rootQName);
        contentHandler.endDocument();
    }

    /**
     * Unzip it
     * @param zipFile input zip file
     * @param outputFolder zip file output folder
     */
    private File unZipIt(File zipFile, String outputFolder) {

        byte[] buffer = new byte[4096];

        File folder = null;

        try {

            //create output directory is not exists
            folder = new File(outputFolder);
            if (!folder.exists()) {
                folder.mkdir();
            }

            //get the zipped file list entry
            try ( //get the zip file content
                    ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
                //get the zipped file list entry
                ZipEntry ze = zis.getNextEntry();

                while (ze != null) {

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

                    ze = zis.getNextEntry();
                }

                zis.closeEntry();
            }

            
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return folder;
    }

}
