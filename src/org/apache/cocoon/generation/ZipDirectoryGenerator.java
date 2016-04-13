package org.apache.cocoon.generation;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
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
import org.apache.cocoon.components.source.SourceUtil;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.excalibur.source.SourceException;
import org.apache.regexp.RE;
import org.apache.regexp.RESyntaxException;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

public class ZipDirectoryGenerator extends DirectoryGenerator {

  /** Constant for the jar protocol. */
  private static final String JARFILE = "jar:file:";

  protected static final String COMP_SIZE_ATTR_NAME = "compressed-size";

  /**
   * Set the request parameters. Must be called before the generate method.
   *
   * @param resolver     the SourceResolver object
   * @param objectModel  a <code>Map</code> containing model object
   * @param src          the directory to be XMLized specified as src attribute on &lt;map:generate/&gt;
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
   * Adds everything in the zip-file to the generated document.
   * 
   * @param path   the zip-file to process
   * @param depth  how deep to scan the directories in the zip-file
   * @throws SAXException  if an error occurs while constructing nodes
   */
  @Override
  protected void addPath(File path, int depth) throws SAXException {
    List<ZipEntry> contents = new ArrayList<ZipEntry>();
    if (depth > 0) {
      ZipFile zipfile = null;
      try {
        zipfile = new ZipFile(path, Charset.forName("UTF-8"));
        Enumeration<? extends ZipEntry> entries = zipfile.entries();
        while (entries.hasMoreElements()) contents.add((ZipEntry)entries.nextElement());
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
    startNode(DIR_NODE_NAME, path);
    processZipEntries(contents, "", depth);
    endNode(DIR_NODE_NAME);
  }

  /**
   * Generate XML for one level in the zip-file.
   * @param contents All zip-entries.
   * @param prefix The prefix at this level, e.g., "" or "root/sub/".
   * @throws SAXException
   */
  private void processZipEntries(List<ZipEntry> contents, String prefix, int depth) throws SAXException {
    if (depth > 0) {
      // Get the zip-entries at this level.
      ArrayList<Map.Entry<String, ZipEntry>> level = new ArrayList<Map.Entry<String, ZipEntry>>();
      for (ZipEntry entry : contents) {
        String name = entry.getName();
        if (name.length() > prefix.length() && name.startsWith(prefix)) { // Entry is below this level.
          name = name.substring(prefix.length());
          // Names of files and directories at this level contain no '/' or '/' at the end.
          if ((name.indexOf('/') + 1) % name.length() == 0) {
            level.add(new AbstractMap.SimpleImmutableEntry<String, ZipEntry>(name, entry));
          }
        }
      }
      // Do sorting of the level, if required.
      if (sort.equals("name")) {
        Collections.sort(level, new Comparator<Map.Entry<String, ZipEntry>>() {
          public int compare(Map.Entry<String, ZipEntry> o1, Map.Entry<String, ZipEntry> o2) {
            return reverse ? o2.getKey().compareTo(o1.getKey())
                           : o1.getKey().compareTo(o2.getKey());
          }
        });
      } else if (sort.equals("size")) {
        Collections.sort(level, new Comparator<Map.Entry<String, ZipEntry>>() {
          public int compare(Map.Entry<String, ZipEntry> o1, Map.Entry<String, ZipEntry> o2) {
            return reverse ? Long.compare(o2.getValue().getSize(), o1.getValue().getSize())
                           : Long.compare(o1.getValue().getSize(), o2.getValue().getSize());
          }
        });
      } else if (sort.equals("lastmodified")) {
        Collections.sort(level, new Comparator<Map.Entry<String, ZipEntry>>() {
          public int compare(Map.Entry<String, ZipEntry> o1, Map.Entry<String, ZipEntry> o2) {
            return reverse ? Long.compare(o2.getValue().getTime(), o1.getValue().getTime())
                           : Long.compare(o1.getValue().getTime(), o2.getValue().getTime()) ;
          }
        });
      }
      // Output nodes in this level.
      for (Map.Entry<String, ZipEntry> entry : level) {
        if (isIncluded(entry.getKey()) && !isExcluded(entry.getKey())) {
          if (entry.getValue().isDirectory()) {
            startNode(DIR_NODE_NAME, entry);
            processZipEntries(contents, entry.getValue().getName(), depth - 1);
            endNode(DIR_NODE_NAME);
          } else {
            startNode(FILE_NODE_NAME, entry);
            endNode(FILE_NODE_NAME);
          }
        }
      }
    }
  }

  /**
   * Begins a named node and calls setNodeAttributes to set its attributes.
   * 
   * @param nodeName  the name of the new node
   * @param path      the file/directory to use when setting attributes
   * @throws SAXException  if an error occurs while creating the node
   */
  protected void startNode(String nodeName, Map.Entry<String, ZipEntry> entry) throws SAXException {
      setNodeAttributes(entry);
      super.contentHandler.startElement(URI, nodeName, PREFIX + ':' + nodeName, attributes);
  }

  /**
   * Sets the attributes for a given path. The default method sets attributes
   * for the name of the zipentry and for the last modification time.
   * 
   * @param path  the file/directory to use when setting attributes
   * @throws SAXException  if an error occurs while setting the attributes
   */
  protected void setNodeAttributes(Map.Entry<String, ZipEntry> entry) throws SAXException {
    ZipEntry zipEntry = entry.getValue();
    String name = entry.getKey();
    if (zipEntry.isDirectory() && name.charAt(name.length()-1) == '/') name = name.substring(0, name.length()-1);
    long lastModified = zipEntry.getTime();
    attributes.clear();
    attributes.addAttribute("", FILENAME_ATTR_NAME, FILENAME_ATTR_NAME, "CDATA", name);
    attributes.addAttribute("", LASTMOD_ATTR_NAME, LASTMOD_ATTR_NAME, "CDATA", Long.toString(lastModified));
    attributes.addAttribute("", DATE_ATTR_NAME, DATE_ATTR_NAME, "CDATA", dateFormatter.format(new Date(lastModified)));
    if (!zipEntry.isDirectory()) {
      attributes.addAttribute("", SIZE_ATTR_NAME, SIZE_ATTR_NAME, "CDATA", Long.toString(zipEntry.getSize()));
      attributes.addAttribute("", COMP_SIZE_ATTR_NAME, COMP_SIZE_ATTR_NAME, "CDATA", Long.toString(zipEntry.getCompressedSize()));
    }
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
  protected boolean isIncluded(String name) {
      return (this.includeRE == null) ? true : this.includeRE.match(name);
  }

  /**
   * Determines if a given File shall be excluded from viewing.
   * 
   * @param ipEntry  the ZipEntry to check
   * @return false if the given ZipEntry shall not be excluded or the exclude Pattern is <code>null</code>,
   *         true otherwise.
   */
  protected boolean isExcluded(String name) {
      return (this.excludeRE == null) ? false : this.excludeRE.match(name);
  }

}
