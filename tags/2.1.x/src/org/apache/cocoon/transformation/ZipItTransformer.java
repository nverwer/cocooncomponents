/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE filename distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this filename to You under the Apache License, Version 2.0
 * (the "License"); you may not use this filename except in compliance with
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
package org.apache.cocoon.transformation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.excalibur.source.Source;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * This transformer creates a new Zip file on disk.
 * <p>
 * It triggers for elements in the namespace "http://apache.org/cocoon/zip-it/1.0".
 * <p>
 * Example XML input:
 * <pre>
 * {@code
 *   <zip-it:archive filename="file:///tmp/my-temp-store/zipfile.zip">
 *       <zip-it:entry name="entry-1" src="cocoon://entry-1"/>
 *   </zip-it:archive>
 * }
 * </pre>
 * The @filename specifies the file that should be created.
 * The @name attribute specifies the filename of the entry in the zip file.
 * The @src attribute is a URI providing the content for the entry.
 * <p>
 * The result is
 * <pre>
 * {@code
 *   <zip:result>OK</zip:result>
 * }
 * </pre>
 * or it throws an error.
 *
 * Define this transformer in the sitemap:
 * <pre>
 * {@code
 * <map:components>
 *   <map:transformers>
 *     <map:transformer name="zip-it" logger="sitemap.transformer.zip-it"
 *         src="org.apache.cocoon.transformation.ZipItTransformer"/>
 *  ...
 * }
 * </pre>
 * Use this transformer:
 * <pre>
 * {@code
 * <map:transform type="zip-it"/>
 * }
 * </pre>
 * @author Huib Verweij (hhv@x-scale.nl)
 *
 */
public class ZipItTransformer extends AbstractSAXTransformer {

    public static final String ZIP_NAMESPACE_URI = "http://apache.org/cocoon/zip-it/1.0";
    private static final String ZIP_PREFIX = "zip-it";
    private static final String ARCHIVE_ELEMENT = "archive";
    private static final String ENTRY_ELEMENT = "entry";
    private static final String FILENAME_ATTR = "filename";
    private static final String NAME_ATTR = "name";
    private static final String SRC_ATTR = "src";

    private static final String RESULT_ELEMENT = "result";

    private String filename;
    private String name;
    private String src;

    private ZipOutputStream zos = null;

    public ZipItTransformer() {
        this.defaultNamespaceURI = ZIP_NAMESPACE_URI;
    }

    @Override
    public void setup(SourceResolver resolver, Map objectModel, String src,
            Parameters params) throws ProcessingException, SAXException, IOException {
        super.setup(resolver, objectModel, src, params);
    }

    private String getAttribute(Attributes attr, String name, String defaultValue) {
        return (attr.getIndex(name) >= 0) ? attr.getValue(name) : defaultValue;
    }

    @Override
    public void startTransformingElement(String uri, String name, String raw, Attributes attr)
            throws ProcessingException, IOException, SAXException {
        if (name.equals(ARCHIVE_ELEMENT)) {
            this.filename = getAttribute(attr, FILENAME_ATTR, null);
            if (this.filename == null) {
                throw new ProcessingException("The " + FILENAME_ATTR + " attribute is mandatory for " + ARCHIVE_ELEMENT + " elements.");
            }
            createZip();
        }
        if (name.equals(ENTRY_ELEMENT)) {
            this.name = getAttribute(attr, NAME_ATTR, null);
            if (this.name == null) {
                throw new ProcessingException("The " + NAME_ATTR + " attribute is mandatory for " + ENTRY_ELEMENT + " elements.");
            }
            this.src = getAttribute(attr, SRC_ATTR, null);
            if (this.src == null) {
                throw new ProcessingException("The " + SRC_ATTR + " attribute is mandatory for " + ENTRY_ELEMENT + " elements.");
            }
            addEntry();
        }
    }

    @Override
    public void endTransformingElement(String uri, String name, String raw)
            throws ProcessingException, IOException, SAXException {
        if (name.equals(ARCHIVE_ELEMENT)) {
            try {
                // close the ZipOutputStream
                zos.close();
                result("OK");
            } catch (IOException ex) {
                Logger.getLogger(ZipItTransformer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * Create a new Zip filename from this.filename.
     * @throws IOException
     */
    private void createZip() throws IOException {
        File zipFile = new File(this.filename);

        if (this.getLogger().isDebugEnabled()) {
            this.getLogger().debug("Creating Zip file.");
        }

        FileOutputStream fos = new FileOutputStream(zipFile);

        this.zos = new ZipOutputStream(fos);
    }

    private void addEntry() throws IOException {
        // create byte buffer
        byte[] buffer = new byte[1024];

        Source entrySource = resolver.resolveURI(this.src);
        // begin writing a new ZIP entry, positions the stream to the start of the entry data
        InputStream is = entrySource.getInputStream();
        // begin writing a new ZIP entry, positions the stream to the start of the entry data
        zos.putNextEntry(new ZipEntry(this.name));
        int length;
        while ((length = is.read(buffer)) > 0) {
            zos.write(buffer, 0, length);
        }
        zos.closeEntry();
    }

    private void result(String result) throws SAXException {
        xmlConsumer.startElement(ZIP_NAMESPACE_URI, RESULT_ELEMENT,
                String.format("%s:%s", ZIP_PREFIX, RESULT_ELEMENT),
                EMPTY_ATTRIBUTES);
        char[] output = result.toCharArray();
        xmlConsumer.characters(output, 0, output.length);
        xmlConsumer.endElement(ZIP_NAMESPACE_URI, RESULT_ELEMENT,
                String.format("%s:%s", ZIP_PREFIX, RESULT_ELEMENT));
    }

}
