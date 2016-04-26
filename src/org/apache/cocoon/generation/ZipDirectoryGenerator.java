package org.apache.cocoon.generation;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.ResourceNotFoundException;
import org.apache.cocoon.environment.SourceResolver;
import org.xml.sax.SAXException;

public class ZipDirectoryGenerator extends DirectoryGenerator {

  /** Constant for the jar protocol. */
  private static final String JARFILE = "jar:file:";

  protected static final String COMP_SIZE_ATTR_NAME = "compressed-size";

  /**
   * Set the request parameters. Must be called before the generate method.
   *
   * @param resolver     the SourceResolver object
   * @param objectModel  a <code>Map</code> containing model object
   * @param src          the directory to be XMLized specified as src attribute on &lt;map:generate/>
   * @param par          configuration parameters
   */
  public void setup(SourceResolver resolver, Map objectModel, String src, Parameters par) throws ProcessingException, SAXException, IOException {
    if (src == null) {
      throw new ProcessingException("No src attribute pointing to a directory to be XMLized specified.");
    }
    if (!src.startsWith(JARFILE))
      throw new ProcessingException(src + " does not denote a zip-file (use the jar:file: protocol)");
    src = src.substring(4); // remove "jar:"
    super.setup(resolver, objectModel, src, par);
  }

  /**
   * Generate XML data.
   * 
   * @throws SAXException  if an error occurs while outputting the document
   * @throws ProcessingException  if the requested URI isn't a directory on the local filesystem
   */
  @Override
  public void generate() throws SAXException, ProcessingException {
    this.contentHandler.startDocument();
    this.contentHandler.startPrefixMapping(PREFIX, URI);
    try {
      String systemId = this.directorySource.getURI();
      // This relies on systemId being of the form "file://..."
      File directoryFile = new File(new URL(systemId).getFile());
      if (!directoryFile.isFile()) {
        throw new ResourceNotFoundException(super.source + " is not a file.");
      }
      Stack ancestors = getAncestors(directoryFile);
      addAncestorPath(directoryFile, ancestors);
    } catch (IOException ioe) {
      throw new ResourceNotFoundException("Could not read directory " + super.source, ioe);
    }
    this.contentHandler.endPrefixMapping(PREFIX);
    this.contentHandler.endDocument();
  }

  /**
   * Adds a single node to the generated document. If the path is a
   * directory, and depth is greater than zero, then recursive calls
   * are made to add nodes for the directory's children.
   * 
   * @param path   the file/directory to process
   * @param depth  how deep to scan the directory
   * @throws SAXException  if an error occurs while constructing nodes
   */
  @Override
  protected void addPath(File path, int depth) throws SAXException {
    startNode(DIR_NODE_NAME, path);
    if (depth > 0) {
      ZipFile zipfile = null;
      try {
        zipfile = new ZipFile(path, Charset.forName("UTF-8"));
        Enumeration<? extends ZipEntry> entries = zipfile.entries();
        List<ZipEntry> contents = new ArrayList<ZipEntry>();
        while (entries.hasMoreElements()) {
          contents.add((ZipEntry)entries.nextElement());
        }
        if (sort.equals("name")) {
          Collections.sort(contents, new Comparator<ZipEntry>() {
            public int compare(ZipEntry o1, ZipEntry o2) {
              if (reverse) {
                return o2.getName().compareTo(o1.getName());
              }
              return o1.getName().compareTo(o2.getName());
            }
          });
        } else if (sort.equals("size")) {
          Collections.sort(contents, new Comparator<ZipEntry>() {
            public int compare(ZipEntry o1, ZipEntry o2) {
              if (reverse) {
                return new Long(o2.getSize()).compareTo(new Long(o1.getSize()));
              }
              return new Long(o1.getSize()).compareTo(new Long(o2.getSize()));
            }
          });
        } else if (sort.equals("lastmodified")) {
          Collections.sort(contents, new Comparator<ZipEntry>() {
            public int compare(ZipEntry o1, ZipEntry o2) {
              if (reverse) {
                return new Long(o2.getTime()).compareTo(new Long(o1.getTime()));
              }
              return new Long(o1.getTime()).compareTo(new Long(o2.getTime()));
            }
          });
        }
        for (int i = 0; i < contents.size(); i++) {
          if (isIncluded(contents.get(i)) && !isExcluded(contents.get(i))) {
            startNode(FILE_NODE_NAME, contents.get(i));
            endNode(FILE_NODE_NAME);
          }
        }
      } catch (ZipException e) {
        throw new SAXException(e);
      } catch (IOException e) {
        throw new SAXException(e);
      } finally {
        if (zipfile != null) {
          try {
            zipfile.close();
          } catch (IOException e) {
          }
        }
      } // finally
    } // if depth > 0
    endNode(DIR_NODE_NAME);
  }

  /**
   * Begins a named node and calls setNodeAttributes to set its attributes.
   * 
   * @param nodeName  the name of the new node
   * @param path      the file/directory to use when setting attributes
   * @throws SAXException  if an error occurs while creating the node
   */
  protected void startNode(String nodeName, ZipEntry path) throws SAXException {
      setNodeAttributes(path);
      super.contentHandler.startElement(URI, nodeName, PREFIX + ':' + nodeName, attributes);
  }

  /**
   * Sets the attributes for a given path. The default method sets attributes
   * for the name of the zipentry and for the last modification time.
   * 
   * @param zipEntry  the file/directory to use when setting attributes
   * @throws SAXException  if an error occurs while setting the attributes
   */
  protected void setNodeAttributes(ZipEntry zipEntry) throws SAXException {
      String fullName = zipEntry.getName();
      long lastModified = zipEntry.getTime();
      attributes.clear();
      attributes.addAttribute("", FILENAME_ATTR_NAME, FILENAME_ATTR_NAME, "CDATA", fullName);
      attributes.addAttribute("", LASTMOD_ATTR_NAME, LASTMOD_ATTR_NAME, "CDATA", Long.toString(lastModified));
      attributes.addAttribute("", DATE_ATTR_NAME, DATE_ATTR_NAME, "CDATA", dateFormatter.format(new Date(lastModified)));
      attributes.addAttribute("", SIZE_ATTR_NAME, SIZE_ATTR_NAME, "CDATA", Long.toString(zipEntry.getSize()));
      attributes.addAttribute("", COMP_SIZE_ATTR_NAME, COMP_SIZE_ATTR_NAME, "CDATA", Long.toString(zipEntry.getCompressedSize()));
      if (this.isRequestedDirectory) {
          attributes.addAttribute("", "sort", "sort", "CDATA", this.sort);
          attributes.addAttribute("", "reverse", "reverse", "CDATA",
                                  String.valueOf(this.reverse));
          attributes.addAttribute("", "requested", "requested", "CDATA", "true");
          this.isRequestedDirectory = false;
      }
  }

  /**
   * Determines if a given ZipEntry shall be visible.
   * 
   * @param zipEntry  the ZipEntry to check
   * @return true if the ZipEntry shall be visible or the include Pattern is <code>null</code>,
   *         false otherwise.
   */
  protected boolean isIncluded(ZipEntry zipEntry) {
      return (this.includeRE == null) ? true : this.includeRE.match(zipEntry.getName());
  }

  /**
   * Determines if a given File shall be excluded from viewing.
   * 
   * @param zipEntry  the ZipEntry to check
   * @return false if the given ZipEntry shall not be excluded or the exclude Pattern is <code>null</code>,
   *         true otherwise.
   */
  protected boolean isExcluded(ZipEntry zipEntry) {
      return (this.excludeRE == null) ? false : this.excludeRE.match(zipEntry.getName());
  }

}
