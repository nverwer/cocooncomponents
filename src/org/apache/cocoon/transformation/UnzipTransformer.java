/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE src distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this src to You under the Apache License, Version 2.0
 * (the "License"); you may not use this src except in compliance with
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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.xml.AttributesImpl;
import org.apache.commons.io.FilenameUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * This transformer unzips Zip files.
 * <p>
 * It triggers for elements in the namespace "http://apache.org/cocoon/unzip/1.0".
 * <p>
 * Example XML input:
 * <pre>
 * {@code
 *   <unzip:archive src="file:///tmp/my-temp-store/zipfile.zip" 
 *      target="file:///tmp/my-unzips/zipfile" recursive="true"/>
 * }
 * </pre>
 The @src specifies the file that should be created.
 The @target attribute specifies the directory where the content of the zip
 will be stored. If unspecified, the content will unzip to a folder
 with the name of the zip file, minus the '.zip' extension.
 If the @recursive attribute is "true" then zip files in the @src zip
 will also be unzipped.
 <p>
 * The result is
 * <pre>
 * {@code
 *   <zip:result target="file:///tmp/my-unzips/zipfile">OK</zip:result>
 * }
 * </pre>
 * or it throws an error.
 *
 * Define this transformer in the sitemap:
 * <pre>
 * {@code
 * <map:components>
   <map:transformers>
     <map:transformer target="unzip" logger="sitemap.transformer.unzip"
         recursive="org.apache.cocoon.transformation.UnzipTransformer"/>
  ...
 }
 * </pre>
 * Use this transformer:
 * <pre>
 * {@code
 * <map:transform type="unzip"/>
 * }
 * </pre>
 * @author Huib Verweij (hhv@x-scale.nl)
 *
 */
public class UnzipTransformer extends AbstractSAXTransformer {

    public static final String UNZIP_NAMESPACE_URI = "http://apache.org/cocoon/unzip/1.0";
    private static final String ZIP_PREFIX = "unzip";
    private static final String ARCHIVE_ELEMENT = "archive";
    private static final String SRC_ATTRIBUTE = "src";
    private static final String TARGET_ATTRIBUTE = "target";
    private static final String RECURSIVE_ATTRIBUTE = "recursive";

    private static final String RESULT_ELEMENT = "result";

    private String src;

    public UnzipTransformer() {
        this.defaultNamespaceURI = UNZIP_NAMESPACE_URI;
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
    public void startTransformingElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXException, ProcessingException, IOException {
        if (uri.equals(UNZIP_NAMESPACE_URI) && localName.equals(ARCHIVE_ELEMENT)) {

            this.src = getAttribute(attributes, SRC_ATTRIBUTE, null);
            if (null == this.src) {
                throw new SAXException("The " + SRC_ATTRIBUTE + " attribute is mandatory for " + ARCHIVE_ELEMENT + " elements.");
            }

            String targetFolderDefault = FilenameUtils.getFullPathNoEndSeparator(src);

            this.getLogger().debug("src=" + src + ", targetFolderDefault=" + targetFolderDefault);

            File unzipResult;
            try {
                unzipResult = unzip(
                        new File(this.src),
                        new File(getAttribute(attributes, TARGET_ATTRIBUTE, targetFolderDefault)),
                        Boolean.parseBoolean(getAttribute(attributes, RECURSIVE_ATTRIBUTE, "false"))
                );

                String absPath = unzipResult.getCanonicalPath();

                AttributesImpl attrsImpl = new AttributesImpl();
                attrsImpl.addAttribute("", TARGET_ATTRIBUTE, TARGET_ATTRIBUTE, "CDATA", absPath);
                xmlConsumer.startElement(UNZIP_NAMESPACE_URI, RESULT_ELEMENT, String.format("%s:%s", ZIP_PREFIX, RESULT_ELEMENT), attrsImpl);
                xmlConsumer.characters(absPath.toCharArray(), 0, absPath.length());
                xmlConsumer.endElement(UNZIP_NAMESPACE_URI, RESULT_ELEMENT, String.format("%s:%s", ZIP_PREFIX, RESULT_ELEMENT));
            } catch (IOException ex) {
                Logger.getLogger(UnzipTransformer.class.getName()).log(Level.SEVERE, null, ex);
                throw new SAXException("Error unzipping file", ex);
            }

        } else {
            super.startTransformingElement(uri, localName, qName, attributes);
        }
    }

    @Override
    public void endTransformingElement(String uri, String localName, String qName)
            throws SAXException, ProcessingException, IOException {
        if (uri.equals(UNZIP_NAMESPACE_URI) && localName.equals(ARCHIVE_ELEMENT)) {
            return;
        }

        super.endTransformingElement(uri, localName, qName);
    }

    private static File extractFile(ZipInputStream in, File outdir, String name) throws IOException {
        File newFile = new File(outdir, name);
        byte[] buffer = new byte[4096];
        BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(newFile));
        int count = -1;
        while ((count = in.read(buffer)) != -1) {
            out.write(buffer, 0, count);
        }
        out.close();
        return newFile;
    }

    private static void mkdirs(File outdir, String path) {
        File d = new File(outdir, path);
        if (!d.exists()) {
            d.mkdirs();
        }
    }

    private static String dirpart(String name) {
        int s = name.lastIndexOf('/');
        if (s == -1) {
            s = name.lastIndexOf('\\');
        }
        return s == -1 ? null : name.substring(0, s);
    }

    /***
     * Extract zipfile to outdir with complete directory structure
     * @param zipfile Input .zip file
     * @param outdir Output directory
     * @param recursiveUnzip If true, unzip zip files contained in the zipFile.
     * @return unzipped File
     * @throws java.io.FileNotFoundException
     */
    public static File unzip(File zipfile, File outdir, boolean recursiveUnzip) throws FileNotFoundException, IOException {
        ZipInputStream zin = new ZipInputStream(new FileInputStream(zipfile));
        ZipEntry entry;
        String name, dir;

        //create output directory if it does not exist
        if (!outdir.exists()) {
            outdir.mkdir();
        }

        while ((entry = zin.getNextEntry()) != null) {
            name = entry.getName();
            if (entry.isDirectory()) {
                mkdirs(outdir, name);
                continue;
            }
            /* this part is necessary because file entry can come before
         * directory entry where is file located
         * i.e.:
         *   /foo/foo.txt
         *   /foo/
             */
            dir = dirpart(name);
            if (dir != null) {
                mkdirs(outdir, dir);
            }

            File newFile = extractFile(zin, outdir, name);

            if (recursiveUnzip && FilenameUtils.getExtension(name).equals("zip")) {
                /* Plak naam van de zip ervoor */
                String newZippie = FilenameUtils.concat(FilenameUtils.concat(outdir.getCanonicalPath(), FilenameUtils.getPath(name)), FilenameUtils.getBaseName(name));
                unzip(newFile, new File(newZippie), recursiveUnzip);
                newFile.delete();
            }
        }
        zin.close();

        return outdir;
    }
}
