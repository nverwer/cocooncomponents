/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.cocoon.serialization;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.avalon.framework.activity.Disposable;
import org.apache.avalon.framework.service.ServiceException;
import org.apache.avalon.framework.service.ServiceManager;
import org.apache.avalon.framework.service.ServiceSelector;
import org.apache.avalon.framework.service.Serviceable;
import org.apache.excalibur.source.Source;
import org.apache.excalibur.source.SourceResolver;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.NamespaceSupport;

/**
 * A serializer that builds Zip archives by aggregating several sources.
 * <p>
 * The input document should describe entries of the archive by means of
 * their name (which can be a path) and their content either as URLs or
 * inline data :
 * <ul>
 * <li>URLs, given by the "src" attribute, are Cocoon sources and as such
 *     can use any of the protocols handled by Cocoon, including "cocoon:" to
 *     include dynamically generated content in the archive.</li>
 * <li>inline data is represented by an XML document that is serialized to the
 *     zip entry using the serializer identified by the "serializer" attribute.</li>
 * </ul>
 * <p>
 * Example :
 * <pre>
 *   &lt;zip:archive xmlns:zip="http://apache.org/cocoon/zip-archive/1.0"&gt;
 *     &lt;zip:entry name="foo.html" src="cocoon://dynFoo.html"/&gt;
 *     &lt;zip:entry name="images/bar.jpeg" src="bar.jpeg" comment="nice picture!"/&gt;
 *     &lt;zip:entry name="index.html" serializer="html" method="stored"&gt;
 *       &lt;html&gt;
 *         &lt;head&gt;
 *           &lt;title&gt;Index page&lt;/title&gt;
 *         &lt;/head&gt;
 *         &lt;body&gt;
 *           Please go &lt;a href="foo.html"&gt;there&lt;/a&gt;
 *         &lt;/body&lt;
 *       &lt;/html&gt;
 *     &lt;/zip:entry&gt;
 *   &lt;/zip:archive:zip&gt;
 * </pre>
 * 
 * The method attribute on an entry can be "deflated" (default, compressed) or "stored" (no compression).
 * The comment attribute on an entry is a string, which is added as a comment to the entry.
 * 
 * The optional debug attribute on &lt;zip:archive&gt;, when set to "INFO" or "WARN" (case-insensitive),
 * causes the transformer to log the zip actions for each entry, with the given log-level.
 *
 * @author <a href="http://www.apache.org/~sylvain">Sylvain Wallez</a>
 * @version $Id: ZipArchiveSerializer.java 437692 2006-08-28 13:09:39Z anathaniel $
 */

// TODO (1) : handle more attributes on <archive> for properties of ZipOutputStream
//            such as comment or default compression method and level

// TODO (2) : handle more attributes on <entry> for properties of ZipEntry
//            (compression method and level, time, comment, etc.); method and comment are done.

public class ZipArchiveSerializer extends AbstractSerializer
                                  implements Disposable, Serviceable {

    /**
     * The namespace for elements handled by this serializer,
     * "http://apache.org/cocoon/zip-archive/1.0".
     */
    public static final String ZIP_NAMESPACE = "http://apache.org/cocoon/zip-archive/1.0";

    private static final int START_STATE = 0;
    private static final int IN_ZIP_STATE = 1;
    private static final int IN_CONTENT_STATE = 2;

    /** The component manager */
    protected ServiceManager manager;

    /** The serializer component selector */
    protected ServiceSelector selector;

    /** The Zip stream where entries will be written */
    protected ZipOutputStream zipOutput;

    /** The current state */
    protected int state = START_STATE;

    /** The resolver to get sources */
    protected SourceResolver resolver;

    /** Temporary buffer for stored (uncompressed) entries */
    protected ByteArrayOutputStream baos;
    
    /** Temporary byte buffer to read source data */
    protected byte[] buffer;

    /** Serializer used when in IN_CONTENT state */
    protected Serializer serializer;

    /** Current depth of the serialized content */
    protected int contentDepth;

    /** Used to collect namespaces */
    private NamespaceSupport nsSupport = new NamespaceSupport();

    /**
     * Store exception
     */
    private SAXException exception;
    
    /* Current zip-entry. */
    ZipEntry entry;
    
    /* Debug attribute */
    private String debug;


    /**
     * @see org.apache.avalon.framework.service.Serviceable#service(ServiceManager)
     */
    public void service(ServiceManager manager) throws ServiceException {
        this.manager = manager;
        this.resolver = (SourceResolver)this.manager.lookup(SourceResolver.ROLE);
    }

    /**
     * Returns default mime type for zip archives, <code>application/zip</code>.
     * Can be overridden in the sitemap.
     * @return application/zip
     */
    public String getMimeType() {
        return "application/zip";
    }

    /**
     * @see org.xml.sax.ContentHandler#startDocument()
     */
    public void startDocument() throws SAXException {
        this.state = START_STATE;
        this.zipOutput = new ZipOutputStream(this.output);
    }

    /**
     * Begin the scope of a prefix-URI Namespace mapping.
     *
     * @param prefix The Namespace prefix being declared.
     * @param uri The Namespace URI the prefix is mapped to.
     */
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        if (state == IN_CONTENT_STATE && this.contentDepth > 0) {
            // Pass to the serializer
            super.startPrefixMapping(prefix, uri);

        } else {
            // Register it if it's not our own namespace (useless to content)
            if (!uri.equals(ZIP_NAMESPACE)) {
                this.nsSupport.declarePrefix(prefix, uri);
            }
        }
    }
    
    public void endPrefixMapping(String prefix) throws SAXException {
        if (state == IN_CONTENT_STATE && this.contentDepth > 0) {
            // Pass to the serializer
            super.endPrefixMapping(prefix);
        }
    }

    // Note : no need to implement endPrefixMapping() as we just need to pass it through if there
    // is a serializer, which is what the superclass does.

    /**
     * @see org.xml.sax.ContentHandler#startElement(String, String, String, Attributes)
     */
    public void startElement(String namespaceURI, String localName, String qName, Attributes atts)
        throws SAXException {

        // Damage control. Sometimes one exception is just not enough...
        if (this.exception != null) {
            throw this.exception;
        }

        switch (state) {
            case START_STATE:
                // expecting "zip" as the first element
                if (namespaceURI.equals(ZIP_NAMESPACE) && localName.equals("archive")) {
                    this.nsSupport.pushContext();
                    this.state = IN_ZIP_STATE;
                    debug = atts.getValue("debug");
                } else {
                    throw this.exception =
                        new SAXException("Expecting 'archive' root element (got '" + localName + "')");
                }
                break;

            case IN_ZIP_STATE:
                // expecting "entry" element
                if (namespaceURI.equals(ZIP_NAMESPACE) && localName.equals("entry")) {
                    this.nsSupport.pushContext();
                    // Get the source
                    startAddEntry(atts);
                } else {
                    throw this.exception =
                        new SAXException("Expecting 'entry' element (got '" + localName + "')");
                }
                break;

            case IN_CONTENT_STATE:
                if (this.contentDepth == 0) {
                    // Give it any namespaces already declared
                    Enumeration prefixes = this.nsSupport.getPrefixes();
                    while (prefixes.hasMoreElements()) {
                        String prefix = (String) prefixes.nextElement();
                        super.startPrefixMapping(prefix, this.nsSupport.getURI(prefix));
                    }
                }

                this.contentDepth++;
                super.startElement(namespaceURI, localName, qName, atts);
                break;
        }
    }

    /**
     * @see org.xml.sax.ContentHandler#characters(char[], int, int)
     */
    public void characters(char[] buffer, int offset, int length) throws SAXException {
        // Propagate text to the serializer only if we have encountered the content's top-level
        // element. Otherwhise, the serializer may be confused by some characters occuring between
        // startDocument() and the first startElement() (e.g. Batik fails hard in that case)
        if (this.state == IN_CONTENT_STATE && this.contentDepth > 0) {
            super.characters(buffer, offset, length);
        }
    }

    /**
     * Add an entry in the archive.
     * @param atts the attributes that describe the entry
     */
    protected void startAddEntry(Attributes atts) throws SAXException {
        // Buffer lazily allocated to 8kB, if it didn't already exist.
        if (this.buffer == null) this.buffer = new byte[8192];
        String name = atts.getValue("name");
        if (name == null) {
            throw this.exception =
                new SAXException("No name given to the Zip entry");
        }

        String src = atts.getValue("src");
        String serializerType = atts.getValue("serializer");
        String method = atts.getValue("method");
        String comment = atts.getValue("comment");
        
        if (debug != null) {
            String debugMessage = "Adding "+method+" zip-entry ["+name+"] from ["+src+"].";
            if (comment != null)
                debugMessage += "\n\t\"" + comment + "\"";
            if (debug.equalsIgnoreCase("WARN")) {
                this.getLogger().warn(debugMessage);
            } else if (debug.equalsIgnoreCase("INFO")) {
                this.getLogger().info(debugMessage);
            }
        }

        if (src == null && serializerType == null) {
            throw this.exception =
                new SAXException("No source nor serializer given for the Zip entry '" + name + "'");
        }

        if (src != null && serializerType != null) {
            throw this.exception =
                new SAXException("Cannot specify both 'src' and 'serializer' on a Zip entry '" + name + "'");
        }
        
        // Create a new Zip entry.
        entry = new ZipEntry(name);

        Source source = null;
        try {
            if (src != null) {
                // Get the source and its data
                source = resolver.resolveURI(src);
                InputStream sourceInput = source.getInputStream();

                // Set file modification time.
                long lastModified = source.getLastModified();
                if (lastModified != 0)
                    entry.setTime(lastModified);
                // If specified, set comment.
                if (comment != null)
                    entry.setComment(comment);
                // Specify the method if STORED.
                if (method != null && method.toLowerCase().equals("stored")) {
                    entry.setMethod(ZipEntry.STORED);
                    // For STORED entries, we have to determine size and checksum, by buffering the content.
                    baos = new ByteArrayOutputStream();
                    // Copy the source to the baos
                    int len;
                    while ((len = sourceInput.read(this.buffer)) > 0) {
                        baos.write(this.buffer, 0, len);
                    }
                } else {
                    this.zipOutput.putNextEntry(entry);
                    // Copy the source to the zip
                    int len;
                    while ((len = sourceInput.read(this.buffer)) > 0) {
                        this.zipOutput.write(this.buffer, 0, len);
                    }
                    // Close the entry
                    this.zipOutput.closeEntry();
                }
                endAddEntry(); // We are not going to serialize content.
                // close input stream (to avoid "too many open files" problem)
                sourceInput.close();
            } else {
                // Content is within entry element.
                // Zip entry already has current time as modification time.
                // If specified, set comment.
                if (comment != null)
                    entry.setComment(comment);
                // Specify the method if STORED.
                if (method != null && method.toLowerCase().equals("stored")) {
                    entry.setMethod(ZipEntry.STORED);
                    // For STORED entries, we have to determine size and checksum, by buffering the content.
                    baos = new ByteArrayOutputStream();
                } else {
                    this.zipOutput.putNextEntry(entry);
                    // Now ready to write output to the zip-outputstream
                }

                // Serialize content
                if (this.selector == null) {
                    this.selector =
                        (ServiceSelector) this.manager.lookup(Serializer.ROLE + "Selector");
                }
                // Get the serializer
                this.serializer = (Serializer) this.selector.select(serializerType);

                // For STORED entries, direct output to the baos buffer.
                // Otherwise, direct output to the zip file, filtering calls to close()
                // (we don't want the archive to be closed by the serializer)
                if (entry.getMethod() == ZipEntry.STORED) {
                    this.serializer.setOutputStream(baos);
                } else {
                    this.serializer.setOutputStream(new FilterOutputStream(this.zipOutput) {
                        public void close() { /* nothing */ }
                    });
                }

                // Set it as the current XMLConsumer
                setConsumer(serializer);
                // start its document
                this.serializer.startDocument();

                this.state = IN_CONTENT_STATE;
                this.contentDepth = 0;
            }

        } catch (RuntimeException re) {
            throw re;
        } catch (SAXException se) {
            throw this.exception = se;
        } catch (Exception e) {
            throw this.exception = new SAXException(e);
        } finally {
            this.resolver.release( source );
        }
    }
    
    /**
     * Complete adding an entry in the archive.
     * @throws SAXException 
     */
    protected void endAddEntry() throws SAXException {
        if (entry.getMethod() == ZipEntry.STORED) {
            try {
                // Determine length and checksum
                byte[] bytes =  baos.toByteArray();
                entry.setSize(bytes.length);
                CRC32 crc = new CRC32();
                crc.reset();
                crc.update(bytes);
                entry.setCrc(crc.getValue());
                // Put the entry in the zip-output
                this.zipOutput.putNextEntry(entry);
                // Copy the content
                this.zipOutput.write(bytes);
                // Close the entry
                this.zipOutput.closeEntry();
            } catch (IOException e) {
                throw this.exception = new SAXException(e);
            }
            baos = null;
            entry = null;
        }
        // Close the entry.
        try {
            this.zipOutput.closeEntry();
        } catch (IOException ioe) {
            throw this.exception = new SAXException(ioe);
        }
    }

    /**
     * @see org.xml.sax.ContentHandler#endElement(String, String, String)
     */
    public void endElement(String namespaceURI, String localName, String qName)
        throws SAXException {

        // Damage control. Sometimes one exception is just not enough...
        if (this.exception != null) {
            throw this.exception;
        }

        if (state == IN_CONTENT_STATE) {
            super.endElement(namespaceURI, localName, qName);
            this.contentDepth--;

            if (this.contentDepth == 0) {
                // End of this entry

                // close all declared namespaces.
                Enumeration prefixes = this.nsSupport.getPrefixes();
                while (prefixes.hasMoreElements()) {
                    String prefix = (String) prefixes.nextElement();
                    super.endPrefixMapping(prefix);
                }

                super.endDocument();
                
                endAddEntry();

                super.setConsumer(null);
                this.selector.release(this.serializer);
                this.serializer = null;

                // Go back to listening for entries
                this.state = IN_ZIP_STATE;
            }
        } else {
            this.nsSupport.popContext();
        }
    }

    /**
     * @see org.xml.sax.ContentHandler#endDocument()
     */
    public void endDocument() throws SAXException {
        try {
            // Close the zip archive
            this.zipOutput.finish();

        } catch (IOException ioe) {
            throw new SAXException(ioe);
        }
    }

    /**
     * @see org.apache.avalon.excalibur.pool.Recyclable#recycle()
     */
    public void recycle() {
        this.exception = null;
        if (this.serializer != null) {
            this.selector.release(this.serializer);
        }
        if (this.selector != null) {
            this.manager.release(this.selector);
        }

        this.nsSupport.reset();
        super.recycle();
    }

    /* (non-Javadoc)
     * @see org.apache.avalon.framework.activity.Disposable#dispose()
     */
    public void dispose() {
        if (this.manager != null) {
            this.manager.release(this.resolver);
            this.resolver = null;
            this.manager = null;
        }
    }
}
