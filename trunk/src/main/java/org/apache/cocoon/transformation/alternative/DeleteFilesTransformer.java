package org.apache.cocoon.transformation.alternative;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.transformation.AbstractTransformer;
import org.apache.commons.io.FileUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * Transformer that can be used to delete the source indicated in the
 * file element of the <code>http://kluwer.nl/cocoon/delete/1.0</code>
 * namespace.
 * @author bdijkstra
 *
 * Usage:
 *   <delete:file src="myFileOrDirectoryName" xmlns:delete="http://kluwer.nl/cocoon/delete/1.0"/>
 */
public class DeleteFilesTransformer extends AbstractTransformer {

  // Identifies the namespace used for the delete transformation
  public static final String DELETE_FILES_NS =
    "http://kluwer.nl/cocoon/delete/1.0";

  // The file element that holds the source
  public static final String FILE_ELEMENT = "file";

  // Source attribute
  public static final String SRC_ATTRIBUTE = "src";

  public void setup(SourceResolver resolver, Map objectModel, String src,
    Parameters par)
    throws ProcessingException, SAXException, IOException {
  }

  @Override
  public void startElement(String namespaceURI, String localName,
    String qName, Attributes attributes)
    throws SAXException {

    // If we encounter a file element in our namespace, invoke a delete
    // on the src attribute
    if (DELETE_FILES_NS.equals(namespaceURI) && FILE_ELEMENT.equals(localName)) {
      delete(attributes.getValue(SRC_ATTRIBUTE));
    }
    else {
      super.startElement(namespaceURI, localName, qName, attributes);
    }
  }

  @Override
  public void endElement(String namespaceURI, String localName, String qName)
    throws SAXException {

    if (DELETE_FILES_NS.equals(namespaceURI) && FILE_ELEMENT.equals(localName)) {
       // Do nothing here since we want to remove this element.
    } else {
      super.endElement(namespaceURI, localName, qName);
    }
  }

  /**
   * Method that creates a File object based on the given name and deletes it if
   * possible. File may be a directory, in which case subdirs are also
   * removed.
   *
   * @param name The name of the file or directory to be deleted.
   */
  private void delete(String name) {

    getLogger().debug("Deleting file: " + name);
    File file = new File(name);
    if (file != null && file.exists()) {
      try {
        // Also deletes subdirs
        FileUtils.forceDelete(file);
      } catch (Exception ignore) {
        getLogger().error("Could not delete file: " + name, ignore);
      }
    }
  }
}