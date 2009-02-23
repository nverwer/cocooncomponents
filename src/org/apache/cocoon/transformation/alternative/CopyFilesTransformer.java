package org.apache.cocoon.transformation.alternative;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.apache.avalon.excalibur.io.FileUtil;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.transformation.AbstractTransformer;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * Transformer that can be used to copy the source indicated in the
 * file element of the <code>http://kluwer.nl/cocoon/copy/1.0</code>
 * namespace.
 * @author Geert Josten (Daidalos BV)
 *
 * Usage:
 *   <copy:file src="mySourceFileOrDirectoryName" target="myTargetFileOrDirectoryName" xmlns:copy="http://kluwer.nl/cocoon/copy/1.0"/>
 */
public class CopyFilesTransformer extends AbstractTransformer {

  // Identifies the namespace used for the copy transformation
  public static final String COPY_FILES_NS =
    "http://kluwer.nl/cocoon/copy/1.0";

  // The file element that holds the source
  public static final String FILE_ELEMENT = "file";

  // Source attribute
  public static final String SRC_ATTRIBUTE = "src";
  // Target attribute
  public static final String TARGET_ATTRIBUTE = "target";

  public void setup(SourceResolver resolver, Map objectModel, String src,
    Parameters par)
    throws ProcessingException, SAXException, IOException {
  }

  public void startElement(String namespaceURI, String localName,
    String qName, Attributes attributes)
    throws SAXException {

    // If we encounter a file element in our namespace, invoke a copy
    // on the src attribute
    if (COPY_FILES_NS.equals(namespaceURI) && FILE_ELEMENT.equals(localName)) {
      copy(attributes.getValue(SRC_ATTRIBUTE), attributes.getValue(TARGET_ATTRIBUTE));
    }
    else {
      super.startElement(namespaceURI, localName, qName, attributes);
    }
  }

  public void endElement(String namespaceURI, String localName, String qName)
    throws SAXException {

    if (COPY_FILES_NS.equals(namespaceURI) && FILE_ELEMENT.equals(localName)) {
      ; // Do nothing here since we want to remove this element.
    } else {
      super.endElement(namespaceURI, localName, qName);
    }
  }

  /**
   * Method that creates a File object based on the given name and copys it if
   * possible. File may be a directory, in which case subdirs are also
   * removed.
   *
   * @param name The name of the file or directory to be copyd.
   */
  private void copy(String name, String target) {

    getLogger().debug("Copying file " + name + " to " + target);
    File file = new File(name);
    File outfile = new File(target);
    if (outfile != null && outfile.exists()) {
      try {
        // Also deletes subdirs
        FileUtil.forceDelete(outfile);
      } catch (Exception ignore) {
        getLogger().error("Could not overwrite target file: " + target, ignore);
      }
    }
    if (file != null && outfile != null && file.exists()) {
      try {
        // File to file or file to dir copy
        FileUtil.copyFile(file, outfile);
      } catch (Exception ignore) {
        getLogger().error("Could not copy file " + name + " to " + target, ignore);
      }
    }
  }
}