package org.apache.cocoon.generation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.avalon.framework.service.ServiceException;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.components.source.SourceUtil;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.excalibur.source.Source;
import org.apache.excalibur.source.SourceException;
import org.apache.excalibur.xml.sax.SAXParser;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * The standard FileGenerator can read zipfiles with the jar:file: protocol.
 * Unfortunately, it locks them and does not release the lock.
 * In 10 years since
 *   http://mail-archives.apache.org/mod_mbox/cocoon-dev/200408.mbox/%3C20040831142711.52874.qmail@web61204.mail.yahoo.com%3E,
 * nobody has solved that.
 * So I decided to write a ZipFileGenerator instead.
 * It can read streaming zip-files, so now you can use sources like jar:http://... not just jar:file://...!
 * @author Rakensi
 *
 */
public class ZipFileGenerator extends ServiceableGenerator {

  /** Constant for the jar protocol. */
  private static final String JARFILE = "jar:";
  
  private Source inputSource;
  private String entryName;
  private String note;

  /**
   * Setup the file generator.
   * Try to get the last modification date of the source for caching.
   */
  @Override
  public void setup(SourceResolver resolver, Map objectModel, String src, Parameters par)
      throws ProcessingException, SAXException, IOException {
    if (!src.startsWith(JARFILE))
      throw new ProcessingException(src + " does not denote a zip-file (use the "+JARFILE+" protocol)");
    src = src.substring(JARFILE.length()); // remove protocol
    entryName = null;
    int entryIndex = src.indexOf("!/");
    if (entryIndex >= 0) {
      entryName = src.substring(entryIndex+2);
      src = src.substring(0, entryIndex);
    }
    super.setup(resolver, objectModel, src, par);
    try {
      this.inputSource = super.resolver.resolveURI(src);
    } catch (SourceException se) {
      throw SourceUtil.handle("Error during resolving of '" + src + "'.", se);
    }
    note = par.getParameter("note", "");
  }

  @Override
  public void generate() throws IOException, SAXException, ProcessingException {
    String systemId = this.inputSource.getURI();
    InputStream sourceInput = this.inputSource.getInputStream();
    ZipInputStream zipInput = new ZipInputStream(sourceInput, Charset.forName("UTF-8"));
    boolean foundEntry = false;
    SAXParser parser = null;
    try {
      if (note.length() > 0) getLogger().info("Opening zipfile "+systemId+" "+note);
      ZipEntry zipEntry;
      while (!foundEntry && (zipEntry = zipInput.getNextEntry()) != null) {
        if (zipEntry.getName().equals(entryName)) {
          foundEntry = true;
          InputSource zipEntryInputSource = new InputSource(zipInput);
          zipEntryInputSource.setSystemId(systemId);
          zipEntryInputSource.setEncoding("UTF-8");
          parser = (SAXParser) manager.lookup(SAXParser.ROLE);
          parser.parse(zipEntryInputSource, super.xmlConsumer);
        } else {
          zipInput.closeEntry();
        }
      }
      if (!foundEntry) throw new ProcessingException(systemId+" does not contain the entry "+entryName);
    } catch (ServiceException e) {
      throw new ProcessingException("Exception during parsing zip-source.", e);
    } finally {
      if (note.length() > 0) getLogger().info("Closing zipfile "+systemId+" "+note);
      if (zipInput != null) {
        zipInput.close();
      }
      if (parser != null) {
        manager.release(parser);
      }
    }
  }

}
