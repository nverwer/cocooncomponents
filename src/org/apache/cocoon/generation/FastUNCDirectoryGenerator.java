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
package org.apache.cocoon.generation;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.caching.CacheableProcessingComponent;
import org.apache.cocoon.components.source.SourceUtil;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.excalibur.source.Source;
import org.apache.excalibur.source.SourceException;
import org.apache.excalibur.source.SourceValidity;
import org.apache.log.Logger;
import org.apache.regexp.RE;
import org.apache.regexp.RESyntaxException;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.nio.file.*;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @cocoon.sitemap.component.documentation
 * Generates an XML directory listing.
 * A more general approach is implemented by the TraversableGenerator (src/blocks/repository/java/org/apache/cocoon/generation/TraversableGenerator.java)
 * 
 * @cocoon.sitemap.component.name   directory
 * @cocoon.sitemap.component.label  content
 * @cocoon.sitemap.component.logger sitemap.generator.directory
 * @cocoon.sitemap.component.documentation.caching
 *               Uses the last modification date of the directory and the contained files
 * 
 * @cocoon.sitemap.component.pooling.max  16
 *
 * @version $Id: DirectoryGenerator.java 433543 2006-08-22 06:22:54Z crossley $
 */
public class UNCDirectoryGenerator 
    extends org.apache.cocoon.generation.ServiceableGenerator
    implements CacheableProcessingComponent {

    /** Constant for the file protocol. */
    private static final String FILE = "file:";

    /** The URI of the namespace of this generator. */
    protected static final String URI = "http://apache.org/cocoon/directory/2.0";

    /** The namespace prefix for this namespace. */
    protected static final String PREFIX = "dir";

    /* Node and attribute names */
    protected static final String DIR_NODE_NAME = "directory";
    protected static final String FILE_NODE_NAME = "file";

    protected static final String FILENAME_ATTR_NAME = "name";
    protected static final String LASTMOD_ATTR_NAME = "lastModified";
    protected static final String DATE_ATTR_NAME = "date";
    protected static final String SIZE_ATTR_NAME = "size";

    /** The validity that is being built */
    protected DirValidity validity;
    /** Convenience object, so we don't need to create an AttributesImpl for every element. */
    protected AttributesImpl attributes;

    /**
     * The cache key needs to be generated for the configuration of this
     * generator, so storing the parameters for generateKey().
     * Using the member variables after setup() would not work I guess. I don't
     * know a way from the regular expressions back to the pattern or at least
     * a useful string.
     */
    protected List cacheKeyParList;

    /** The depth parameter determines how deep the DirectoryGenerator should delve. */
    protected int depth;
    /**
     * The dateFormatter determines into which date format the lastModified
     * time should be converted.
     * FIXME: SimpleDateFormat is not supported by all locales!
     */
    protected SimpleDateFormat dateFormatter;
    /** The delay between checks on updates to the filesystem. */
    protected long refreshDelay;
    /**
     * The sort parameter determines by which attribute the content of one
     * directory should be sorted. Possible values are "name", "size", "lastmodified"
     * and "directory", where "directory" is the same as "name", except that
     * directory entries are listed first.
     */
    protected String sort;
    /** The reverse parameter reverses the sort order. <code>false</code> is default. */
    protected boolean reverse;
    /** The regular expression for the root pattern. */
    protected RE rootRE;
    /** The regular expression for the include pattern. */
    protected RE includeRE;
    /** The regular expression for the exclude pattern. */
    protected RE excludeRE;
    /**
     * This is only set to true for the requested directory specified by the
     * <code>src</code> attribute on the generator's configuration.
     */
    protected boolean isRequestedDirectory;

    /** The source object for the directory. */
    protected Source directorySource;
    private String src;
    private LinkOption linkOptions;

    /**
     * Set the request parameters. Must be called before the generate method.
     *
     * @param resolver     the SourceResolver object
     * @param objectModel  a <code>Map</code> containing model object
     * @param src          the directory to be XMLized specified as src attribute on &lt;map:generate/>
     * @param par          configuration parameters
     */
    public void setup(SourceResolver resolver, Map objectModel, String src, Parameters par)
            throws ProcessingException, SAXException, IOException {
        if (src == null) {
            throw new ProcessingException("No src attribute pointing to a directory to be XMLized specified.");
        }
        super.setup(resolver, objectModel, src, par);

        try {
            this.src = src;
            this.directorySource = this.resolver.resolveURI(src);
            this.linkOptions = LinkOption.NOFOLLOW_LINKS;
        } catch (SourceException se) {
            throw SourceUtil.handle(se);
        }

        this.cacheKeyParList = new ArrayList();
        this.cacheKeyParList.add(this.directorySource.getURI());

        this.depth = par.getParameterAsInteger("depth", 1);
        this.cacheKeyParList.add(String.valueOf(this.depth));

        String dateFormatString = par.getParameter("dateFormat", null);
        this.cacheKeyParList.add(dateFormatString);
        if (dateFormatString != null) {
            this.dateFormatter = new SimpleDateFormat(dateFormatString);
        } else {
            this.dateFormatter = new SimpleDateFormat();
        }

        this.sort = par.getParameter("sort", "no-sort");
        this.cacheKeyParList.add(this.sort);

        this.reverse = par.getParameterAsBoolean("reverse", false);
        this.cacheKeyParList.add(String.valueOf(this.reverse));

        this.refreshDelay = par.getParameterAsLong("refreshDelay", 1L) * 1000L;
        this.cacheKeyParList.add(String.valueOf(this.refreshDelay));

        if (this.getLogger().isDebugEnabled()) {
            this.getLogger().debug("depth: " + this.depth);
            this.getLogger().debug("dateFormat: " + this.dateFormatter.toPattern());
            this.getLogger().debug("sort: " + this.sort);
            this.getLogger().debug("reverse: " + this.reverse);
            this.getLogger().debug("refreshDelay: " + this.refreshDelay);
        }

        String rePattern = null;
        try {
            rePattern = par.getParameter("root", null);
            this.cacheKeyParList.add(rePattern);
            this.rootRE = (rePattern == null) ? null : new RE(rePattern);
            if (this.getLogger().isDebugEnabled()) {
                this.getLogger().debug("root pattern: " + rePattern);
            }

            rePattern = par.getParameter("include", null);
            this.cacheKeyParList.add(rePattern);
            this.includeRE = (rePattern == null) ? null : new RE(rePattern);
            if (this.getLogger().isDebugEnabled()) {
                this.getLogger().debug("include pattern: " + rePattern);
            }

            rePattern = par.getParameter("exclude", null);
            this.cacheKeyParList.add(rePattern);
            this.excludeRE = (rePattern == null) ? null : new RE(rePattern);
            if (this.getLogger().isDebugEnabled()) {
                this.getLogger().debug("exclude pattern: " + rePattern);
            }
        } catch (RESyntaxException rese) {
            throw new ProcessingException("Syntax error in regexp pattern '"
                                          + rePattern + "'", rese);
        }

        this.isRequestedDirectory = false;
        this.attributes = new AttributesImpl();
    }

    /* (non-Javadoc)
     * @see org.apache.cocoon.caching.CacheableProcessingComponent#getKey()
     */
    public Serializable getKey() {
        StringBuffer buffer = new StringBuffer();
        int len = this.cacheKeyParList.size();
        for (int i = 0; i < len; i++) {
            buffer.append((String)this.cacheKeyParList.get(i) + ":");
        }
        return buffer.toString();
    }

    /**
     * Gets the source validity, using a deferred validity object. The validity
     * is initially empty since the files that define it are not known before
     * generation has occured. So the returned object is kept by the generator
     * and filled with each of the files that are traversed.
     * 
     * @see DirectoryGenerator.DirValidity
     */
    public SourceValidity getValidity() {
        if (this.validity == null) {
            this.validity = new DirValidity(this.refreshDelay, this.linkOptions, this.getLogger());
        }
        return this.validity;
    }

    /**
     * Generate XML data.
     * 
     * @throws SAXException  if an error occurs while outputting the document
     * @throws ProcessingException  if the requsted URI isn't a directory on the local filesystem
     */
    public void generate() throws SAXException, ProcessingException, IOException {
    
//        String systemId = this.directorySource.getURI();
        
//        String urlString = systemId;



        this.contentHandler.startDocument();
        this.contentHandler.startPrefixMapping(PREFIX, URI);

        Path path = Paths.get(this.src);
        addPath(path, this.depth);

        this.contentHandler.endPrefixMapping(PREFIX);
        this.contentHandler.endDocument();
    }


    private List<Path> sortFiles(Path path, Comparator<Path> comparator) {

        long startTime = System.currentTimeMillis();
        List<Path> files = new ArrayList<Path>();
        try {
            DirectoryStream<Path> stream = Files.newDirectoryStream(path);
            try {
                for (Path p : stream) {
                    files.add(p);
                }
            } finally {
                stream.close();
            }
        } catch(IOException ioEx) {
            this.getLogger().error("Error getting list of directory [" + path + "] : " + ioEx.getLocalizedMessage());
        }

        long endTime = System.currentTimeMillis();

        this.getLogger().info("Got directory contents in " + (endTime - startTime) + " milliseconds");

        if (null != comparator) {

            this.getLogger().info("Sorting " + files.size() + " files.");
            Collections.sort(files, comparator);
        }
        return files;
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
    protected void addPath(Path path, int depth) throws SAXException {
        // HHV: Not very fast I assume, but hey, it works.
        boolean isDirectory = Files.isDirectory(path, this.linkOptions);

        if (isDirectory) {
            startNode(DIR_NODE_NAME, path);
            if (depth > 0) {

                Comparator comparator = getComparator(sort, this.linkOptions);

                List<Path> contents = sortFiles(path, comparator);

                for (Path p : contents) {
                    if (isIncluded(p) && !isExcluded(p)) {
                        addPath(p, depth - 1);
                    }
                }
            }
            endNode(DIR_NODE_NAME);
        }

        else {
            if (isIncluded(path) && !isExcluded(path)) {
                startNode(FILE_NODE_NAME, path);
                endNode(FILE_NODE_NAME);
            }
        }
    }

private Comparator getComparator(final String sort, final LinkOption linkOptions) {

    Comparator comparator = null;

    switch (sort) {
        case "date":
            comparator = new Comparator<Path>() {
                public int compare(Path o1, Path o2) {
                    try {
                        return Files.getLastModifiedTime(o1).compareTo(Files.getLastModifiedTime(o2));
                    } catch (IOException e) {
                        // handle exception
                    }
                    return 0;
                }
            };
            break;
        case "name":
            comparator = new Comparator<Path>() {
                public int compare(Path o1, Path o2) {
                    if (reverse) {
                        return o2.getFileName().toString().compareToIgnoreCase(o1.getFileName().toString());
                    }
                    return o1.getFileName().toString().compareToIgnoreCase(o2.getFileName().toString());
                }
            };
            break;
        case "lastmodified":
            comparator = new Comparator<Path>() {
                public int compare(Path o1, Path o2) {
                    try {
                        if (reverse) {
                            return Files.getLastModifiedTime(o2).compareTo(
                                    Files.getLastModifiedTime(o1));
                        }
                        return Files.getLastModifiedTime(o1).compareTo(
                                Files.getLastModifiedTime(o2));
                    } catch (IOException e) {
                        // handle exception
                    }
                    return 0;
                }
            };
            break;
        case "directory":
            comparator = new Comparator<Path>() {
                public int compare(Path o1, Path o2) {
                    boolean o1Dir = Files.isDirectory(o1, linkOptions);
                    boolean o2Dir = Files.isDirectory(o2, linkOptions);
                    if (reverse) {
                        if (o2Dir && !o1Dir)
                            return -1;
                        if (!o2Dir && o1Dir)
                            return 1;
                        return o2.getFileName().compareTo(o1.getFileName());
                    }
                    if (o2Dir && !o1Dir)
                        return 1;
                    if (!o2Dir && o1Dir)
                        return -1;
                    return o1.getFileName().compareTo(o2.getFileName());
                }
            };
            break;
    }

    this.getLogger().info("Created comparator " + comparator + " [sort="+sort+"].");
    return comparator;
}

    /**
     * Begins a named node and calls setNodeAttributes to set its attributes.
     * 
     * @param nodeName  the name of the new node
     * @param path      the file/directory to use when setting attributes
     * @throws SAXException  if an error occurs while creating the node
     */
    protected void startNode(String nodeName, java.nio.file.Path path) throws SAXException {
        if (this.validity != null) {
            this.validity.addFile(path);
        }
        setNodeAttributes(path);
        super.contentHandler.startElement(URI, nodeName, PREFIX + ':' + nodeName, attributes);
    }

    /**
     * Sets the attributes for a given path. The default method sets attributes
     * for the name of thefile/directory and for the last modification time
     * of the path.
     * 
     * @param path  the file/directory to use when setting attributes
     * @throws SAXException  if an error occurs while setting the attributes
     */
    protected void setNodeAttributes(java.nio.file.Path path) throws SAXException {

        BasicFileAttributes bfas = null;
        try {
            bfas = Files.readAttributes(path, BasicFileAttributes.class);

            FileTime lastModified = bfas.lastModifiedTime();
            long size = bfas.size();

            attributes.clear();
            attributes.addAttribute("", FILENAME_ATTR_NAME, FILENAME_ATTR_NAME,
                    "CDATA", path.getFileName().toString());
            attributes.addAttribute("", LASTMOD_ATTR_NAME, LASTMOD_ATTR_NAME,
                    "CDATA", Long.toString(lastModified.toMillis()));
            attributes.addAttribute("", DATE_ATTR_NAME, DATE_ATTR_NAME,
                    "CDATA", dateFormatter.format(new Date(lastModified.toMillis())));
            attributes.addAttribute("", SIZE_ATTR_NAME, SIZE_ATTR_NAME,
                    "CDATA", Long.toString(size));
            if (this.isRequestedDirectory) {
                attributes.addAttribute("", "sort", "sort", "CDATA", this.sort);
                attributes.addAttribute("", "reverse", "reverse", "CDATA",
                        String.valueOf(this.reverse));
                attributes.addAttribute("", "requested", "requested", "CDATA", "true");
                this.isRequestedDirectory = false;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Ends the named node.
     * 
     * @param nodeName  the name of the new node
     * @throws SAXException  if an error occurs while closing the node
     */
    protected void endNode(String nodeName) throws SAXException {
        super.contentHandler.endElement(URI, nodeName, PREFIX + ':' + nodeName);
    }

    /**
     * Determines if a given File is the defined root.
     * 
     * @param path  the File to check
     * @return true if the File is the root or the root pattern is not set,
     *         false otherwise.
     */
    protected boolean isRoot(Path path) {
        return (this.rootRE == null) ? true : this.rootRE.match(path.getFileName().toString());
    }

    /**
     * Determines if a given File shall be visible.
     * 
     * @param path  the File to check
     * @return true if the File shall be visible or the include Pattern is <code>null</code>,
     *         false otherwise.
     */
    protected boolean isIncluded(Path path) {
        return (this.includeRE == null) ? true : this.includeRE.match(path.getFileName().toString());
    }

    /**
     * Determines if a given File shall be excluded from viewing.
     * 
     * @param path  the File to check
     * @return false if the given File shall not be excluded or the exclude Pattern is <code>null</code>,
     *         true otherwise.
     */
    protected boolean isExcluded(Path path) {
        return (this.excludeRE == null) ? false : this.excludeRE.match(path.getFileName().toString());
    }

    /**
     * Recycle resources
     */
    public void recycle() {
        if ( this.resolver != null ) {
            this.resolver.release(this.directorySource);
            this.directorySource = null;
        }
        this.cacheKeyParList = null;
        this.attributes = null;
        this.dateFormatter = null;
        this.rootRE = null;
        this.includeRE = null;
        this.excludeRE = null;
        this.validity = null;
        super.recycle();
    }

    /** Specific validity class, that holds all files that have been generated */
    public static class DirValidity implements SourceValidity {

        private long expiry;
        private long delay;
        private LinkOption linkOptions;
        org.apache.avalon.framework.logger.Logger logger;
        List<Path> files = new ArrayList();
        List<FileTime> fileDates = new ArrayList();

        public DirValidity(long delay, LinkOption linkOptions, org.apache.avalon.framework.logger.Logger logger) {
            expiry = System.currentTimeMillis() + delay;
            this.delay = delay;
            this.linkOptions = linkOptions;
            this.logger = logger;
        }

        public int isValid() {
            if (System.currentTimeMillis() <= expiry) {
                return SourceValidity.VALID;
            }

            try {
                int len = files.size();
                for (int i = 0; i < len; i++) {
                    Path f = files.get(i);
                    if (Files.exists(f, this.linkOptions)) {
                        return SourceValidity.INVALID; // File was removed
                    }

                    FileTime oldDate = fileDates.get(i);
                    FileTime newDate = Files.getLastModifiedTime(f);

                    if (oldDate != newDate) {
                        // File's last modified date has changed since last check
                        // NOTE: this occurs on directories as well when a file is added
                        return SourceValidity.INVALID;
                    }
                }

                // all content is up to date: update the expiry date
                expiry = System.currentTimeMillis() + delay;
                return SourceValidity.VALID;
            } catch (IOException ioEx) {
                logger.error("Error getting validity: " + ioEx.getLocalizedMessage());
            }
            return SourceValidity.INVALID;
        }

        public int isValid(SourceValidity newValidity) {
            return isValid();
        }

        public void addFile(Path f) {
            files.add(f);
            try {
                fileDates.add(Files.getLastModifiedTime(f));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
