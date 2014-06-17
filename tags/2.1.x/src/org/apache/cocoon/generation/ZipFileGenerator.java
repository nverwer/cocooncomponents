package org.apache.cocoon.generation;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

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
 * In 10 years since http://mail-archives.apache.org/mod_mbox/cocoon-dev/200408.mbox/%3C20040831142711.52874.qmail@web61204.mail.yahoo.com%3E,
 * nobody has solved that.
 * So I decided to write a ZipFileGenerator instead.
 * @author Rakensi
 *
 */
public class ZipFileGenerator extends ServiceableGenerator {

  /** Constant for the jar protocol. */
  private static final String JARFILE = "jar:file:";
  
  private Source inputSource;
  private String entryName;

  /**
   * Setup the file generator.
   * Try to get the last modification date of the source for caching.
   */
  @Override
  public void setup(SourceResolver resolver, Map objectModel, String src, Parameters par)
      throws ProcessingException, SAXException, IOException {
    if (!src.startsWith(JARFILE))
      throw new ProcessingException(src + " does not denote a zip-file (use the jar:file: protocol)");
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
  }

  @Override
  public void generate() throws IOException, SAXException, ProcessingException {
    String systemId = this.inputSource.getURI();
    ZipFile zipFile;
    ZipEntry zipEntry;
    try {
      zipFile = new ZipFile(systemId.replaceFirst("^file:", ""), Charset.forName("UTF-8"));
      zipEntry = zipFile.getEntry(entryName);
      if (zipEntry == null) throw new ProcessingException(systemId+" does not contaion the entry "+entryName);
    } catch (ZipException e) {
      throw new ProcessingException(e);
    } catch (IOException e) {
      throw new ProcessingException(e);
    }
    final InputSource zipEntryInputSource = new InputSource(zipFile.getInputStream(zipEntry));
    zipEntryInputSource.setSystemId(systemId);
    zipEntryInputSource.setEncoding("UTF-8");
    SAXParser parser = null;
    try {
      parser = (SAXParser) manager.lookup(SAXParser.ROLE);
      parser.parse(zipEntryInputSource, super.xmlConsumer);
    } catch (SourceException e) {
      throw SourceUtil.handle(e);
    } catch (ServiceException e) {
      throw new ProcessingException("Exception during parsing source.", e);
    } finally {
      manager.release(parser);
      zipFile.close();
    }
  }

}
