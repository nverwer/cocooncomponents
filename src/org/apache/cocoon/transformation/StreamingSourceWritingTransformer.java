/* Transformer die SAX events direct serialiseert naar een OutputStream.
 * BUG: Hoewel de input dezelfde is als voor SourceWritingTransformer, genereert deze nog geen output elementen.
 */

package org.apache.cocoon.transformation;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Map;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.xml.AbstractXMLConsumer;
import org.apache.excalibur.source.ModifiableSource;
import org.apache.excalibur.source.Source;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class StreamingSourceWritingTransformer extends AbstractSAXTransformer {

  public static final String SWT_URI = "http://apache.org/cocoon/source/1.0";

  /** incoming elements */
  public static final String WRITE_ELEMENT = "write";
  public static final String INSERT_ELEMENT = "insert";
  public static final String PATH_ELEMENT = "path";
  public static final String FRAGMENT_ELEMENT = "fragment";
  public static final String REPLACE_ELEMENT = "replace";
  public static final String DELETE_ELEMENT = "delete";
  public static final String SOURCE_ELEMENT = "source";
  public static final String REINSERT_ELEMENT = "reinsert";
  /** outgoing elements */
  public static final String RESULT_ELEMENT = "sourceResult";
  public static final String EXECUTION_ELEMENT = "execution";
  public static final String BEHAVIOUR_ELEMENT = "behaviour";
  public static final String ACTION_ELEMENT = "action";
  public static final String MESSAGE_ELEMENT = "message";
  public static final String SERIALIZER_ELEMENT = "serializer";
  /** main (write or insert) tag attributes */
  public static final String SERIALIZER_ATTRIBUTE = "serializer";
  public static final String CREATE_ATTRIBUTE = "create";
  public static final String OVERWRITE_ATTRIBUTE = "overwrite";
  /** results */
  public static final String RESULT_FAILED = "failed";
  public static final String RESULT_SUCCESS = "success";
  public static final String ACTION_NONE = "none";
  public static final String ACTION_NEW = "new";
  public static final String ACTION_OVER = "overwritten";
  public static final String ACTION_DELETE = "deleted";
  
  String filename;
  ContentHandler fileHandler;
  ContentHandler pipelineHandler;
  
  /**
   * Constructor. Set the namespace.
   */
  public StreamingSourceWritingTransformer() {
      this.defaultNamespaceURI = SWT_URI;
  }

  @Override
  public void setup(SourceResolver resolver, Map objectModel, String src, Parameters par) throws ProcessingException, SAXException, IOException {
    super.setup(resolver, objectModel, src, par);
    pipelineHandler = contentHandler;
  }

  public void startTransformingElement(String uri, String name, String raw, Attributes attr)
  throws SAXException, IOException, ProcessingException {
      if (name.equals(WRITE_ELEMENT)) {
      } else if (name.equals(SOURCE_ELEMENT)) {
          this.startTextRecording();
      } else if (name.equals(FRAGMENT_ELEMENT)) {
        contentHandler = fileHandler;
        fileHandler.startDocument();
      } else {
      }
  }

  public void endTransformingElement(String uri, String name, String raw)
  throws SAXException, IOException, ProcessingException {
      if (name.equals(WRITE_ELEMENT)) {
      } else if (name.equals(SOURCE_ELEMENT)) {
        filename = this.endTextRecording();
        Source source = this.resolver.resolveURI(filename);
        if (! (source instanceof ModifiableSource)) {
          throw new ProcessingException("Source '" + filename + "' is not writeable.");
        }
        fileHandler = new StreamWritingXMLConsumer(((ModifiableSource) source).getOutputStream());
      } else if (name.equals(FRAGMENT_ELEMENT)) {
        fileHandler.endDocument();
        fileHandler = null;
        contentHandler = pipelineHandler;
      } else {
      }
  }

  private class StreamWritingXMLConsumer extends AbstractXMLConsumer {
    
    Writer out;
    boolean cdata;
    String nsPrefixDeclarations;
    
    public StreamWritingXMLConsumer(OutputStream out) {
      this.out = new BufferedWriter(new OutputStreamWriter(out));
    }

    public void characters(char[] ch, int start, int len) throws SAXException {
      try {
        if (cdata) {
          out.write(ch, start, len);
        } else {
          String pcdata = String.copyValueOf(ch, start, len).replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
          out.write(pcdata);
        }
      } catch (IOException e) {
        throw new SAXException(e);
      }
    }

    public void comment(char[] ch, int start, int len) throws SAXException {
      try {
        out.write("<!-- ");
        out.write(ch, start, len);
        out.write(" -->");
      } catch (IOException e) {
        throw new SAXException(e);
      }
    }

    public void startDocument() throws SAXException {
      cdata = false;
      nsPrefixDeclarations = "";
    }

    public void startDTD(String name, String publicId, String systemId) throws SAXException {
    }

    public void startCDATA() throws SAXException {
      try {
        out.write("<![CDATA[");
      } catch (IOException e) {
        throw new SAXException(e);
      }
      cdata = true;
    }

    public void startElement(String uri, String lName, String qName, Attributes atts) throws SAXException {
      String attrs = nsPrefixDeclarations;
      nsPrefixDeclarations = "";
      for (int i = 0; i < atts.getLength(); ++i) {
        attrs += " "+atts.getQName(i)+"=\""+atts.getValue(i).replaceAll("\"", "&quot;")+"\"";
      }
      try {
        out.write("<"+qName+attrs+">");
      } catch (IOException e) {
        throw new SAXException(e);
      }
    }

    public void startEntity(String name) throws SAXException {
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
      nsPrefixDeclarations += " xmlns:"+prefix+"=\""+uri.replaceAll("\"", "&quot;")+"\"";
    }

    public void endDocument() throws SAXException {
      try {
        out.close();
      } catch (IOException e) {
        throw new SAXException(e);
      }
    }

    public void endDTD() throws SAXException {
    }

    public void endCDATA() throws SAXException {
      try {
        out.write("]]>");
      } catch (IOException e) {
        throw new SAXException(e);
      }
      cdata = false;
    }

    public void endElement(String uri, String lName, String qName) throws SAXException {
      try {
        out.write("</"+qName+">");
      } catch (IOException e) {
        throw new SAXException(e);
      }
    }

    public void endEntity(String name) throws SAXException {
    }

    public void endPrefixMapping(String prefix) throws SAXException {
    }

    public void ignorableWhitespace(char[] ch, int start, int len) throws SAXException {
      try {
        out.write(ch, start, len);
      } catch (IOException e) {
        throw new SAXException(e);
      }
    }

    public void processingInstruction(String target, String data) throws SAXException {
      try {
        out.write("<?"+target+" "+data+" ?>");
      } catch (IOException e) {
        throw new SAXException(e);
      }
    }

    public void skippedEntity(String name) throws SAXException {
    }    
  }
  
}
