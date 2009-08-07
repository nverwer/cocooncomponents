/**
 * A generator that extracts EXIF and IPTC metadata from JPEG images.
 * It uses the Metadata Extractor library (http://www.drewnoakes.com/code/exif/).
 * Depending on the version of that library, it may work for other image types than just JPEG.
 */
package org.apache.cocoon.generation;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.xml.AttributesImpl;
import org.xml.sax.SAXException;

import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.imaging.jpeg.JpegProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.Tag;

public class ImageMetadataGenerator
extends FileGenerator
{
  
  public static String NAMESPACE = "http://apache.org/cocoon/image-metadata/1.0";
  public static String NS_PREFIX = "meta";
  
  /* The input stream */
  protected InputStream inputStream;

  /**
   * @see org.apache.cocoon.generation.Generator#generate()
   */
  @Override
  public void generate() throws IOException, SAXException, ProcessingException {
    contentHandler.startDocument();
    contentHandler.startPrefixMapping(NS_PREFIX, NAMESPACE);
    inputStream = inputSource.getInputStream();
    try {
      Metadata metadata = JpegMetadataReader.readMetadata(inputStream);
      AttributesImpl metaAtts = new AttributesImpl();
      metaAtts.addCDATAAttribute("src", source);
      contentHandler.startElement(NAMESPACE, "image-metadata", NS_PREFIX+":image-metadata", metaAtts);
      // iterate through metadata directories
      Iterator directories = metadata.getDirectoryIterator();
      while (directories.hasNext()) {
        Directory directory = (Directory)directories.next();
        AttributesImpl dirAtts = new AttributesImpl();
        dirAtts.addCDATAAttribute("name", directory.getName());
        contentHandler.startElement(NAMESPACE, "directory", NS_PREFIX+":directory", dirAtts);
        // iterate through tags and print to System.out
        Iterator tags = directory.getTagIterator();
        while (tags.hasNext()) {
          Tag tag = (Tag)tags.next();
          AttributesImpl tagAtts = new AttributesImpl();
          tagAtts.addCDATAAttribute("name", tag.getTagName());
          contentHandler.startElement(NAMESPACE, "tag", NS_PREFIX+":tag", tagAtts);
          String value = tag.getDescription();
          contentHandler.characters(value.toCharArray(), 0, value.length());
          contentHandler.endElement(NAMESPACE, "tag", NS_PREFIX+":tag");
        }
        contentHandler.endElement(NAMESPACE, "directory", NS_PREFIX+":directory");
      }
      contentHandler.endElement(NAMESPACE, "image-metadata", NS_PREFIX+":image-metadata");
    } catch (JpegProcessingException e) {
      throw new ProcessingException(e);
    } catch (MetadataException e) {
      throw new ProcessingException(e);
    }
    contentHandler.endPrefixMapping(NS_PREFIX);
    contentHandler.endDocument();
  }

}
