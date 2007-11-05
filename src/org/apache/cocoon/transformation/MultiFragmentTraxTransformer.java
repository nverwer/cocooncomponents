package org.apache.cocoon.transformation;

import java.io.IOException;
import java.util.*;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.xml.AbstractXMLPipe;
import org.apache.cocoon.xml.XMLConsumer;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 * Apply a TrAX transformer to selected parts of the input document.
 *
 * Usage:
 * <pre>
 *       <map:transformer logger="sitemap.transformer.xslt" name="multifragment-xslt"
 *           pool-grow="2" pool-max="32" pool-min="8"
 *           src="org.apache.cocoon.transformation.MultiFragmentTraxTransformer">
 *         <use-request-parameters>false</use-request-parameters>
 *         <use-session-parameters>false</use-session-parameters>
 *         <use-cookie-parameters>false</use-cookie-parameters>
 *         <xslt-processor-role>xalan</xslt-processor-role>
 *         <check-includes>true</check-includes>
 *       </map:transformer>
 * 
 *       <map:transform type="multifragment-xslt" src="multifragment.xsl">
 *         <map:parameter name="fragment.namespace" value="http://namespace.uri/fragment"/>
 *         <map:parameter name="fragment.element" value="fragment"/>
 *       </map:transform>
 * </pre>
 * The multifragment transformer will apply the stylesheet 'multifragment.xsl' to all fragments in the input document.
 * A fragment is a sub-document demarcated by an element with the name indicated by the 'fragment.element' parameter,
 * in the namespace indicated by the 'fragment.namespace' parameter. Other parameters are passed on to the stylesheet.
 * If the fragment-element is not in the namespace, the 'fragment.namespace' parameter must be omitted.
 *
 * @author <a href="mailto:nverwer@be-value.nl">Nico Verwer</a>
 * @version 2007-02-19
 *
 * Bugs:
 * Although this version does not generate duplicate namespace-prefix declaration attributes,
 * it will omit prefix mappings with the same prefix but different URI's.
 */
public class MultiFragmentTraxTransformer extends TraxTransformer {
  
  public static final String NAMESPACE_PAR = "fragment.namespace";
  public static final String ELEMENT_PAR = "fragment.element";
  
  private int transforming;
  private MultiFragmentConsumer fragmentConsumer;
  private XMLConsumer nextStage;
  private String triggerElement;
  private String triggerNamespace;
  private Hashtable<String, Integer> nsPrefixes;
  
  public void setup(SourceResolver resolver, Map objectModel, String src, Parameters par)
      throws SAXException, ProcessingException, IOException
  {
    triggerElement = par.getParameter(ELEMENT_PAR, "");
    triggerNamespace = par.getParameter(NAMESPACE_PAR, "");
    // Pre-fetch to make the stylesheet-source valid. See org.apache.excalibur.source.Source.
    // If you don't it will stay invalid and is requested (and compiled?) every time.
    resolver.resolveURI(src).getInputStream();
    super.setup(resolver, objectModel, src, par);
  }

  public void setConsumer(XMLConsumer nextStage) {
    this.nextStage = nextStage;
    fragmentConsumer = new MultiFragmentConsumer();
    fragmentConsumer.setConsumer(nextStage);
  }
  
  // In the following methods, choose between
  // - super.method to forward the event to the TraxTransformer, which uses the XSLT stylesheet to transform.
  // - nextStage.method to pass it on directly to the next pipeline stage.
  // - fragmentConsumer to filter fragment events to the next pipeline stage, removing start/endDocument etcetera.
  //   The super object will have fragmentConsumer as its consumer.
  
  public void startDocument() throws SAXException {
    nsPrefixes = new Hashtable<String, Integer>();
    transforming = 0;
    nextStage.startDocument();
  }
  
  public void endDocument() throws SAXException {
    nextStage.endDocument();
  }
  
  public boolean isTriggerElement(String nsUri, String lName, String qName) {
    return (nsUri == null ? "" : nsUri).equals(triggerNamespace) && lName.equals(triggerElement);
  }

  public void startElement(String nsUri, String lName, String qName, Attributes attrs)
    throws SAXException
  {
    if ( isTriggerElement(nsUri, lName, qName) ) {
      if (transforming == 0) {
        this.transformerHandler = null; // Force setting this in setConsumer, otherwise there is no DTM in Xalan.
        super.setConsumer(fragmentConsumer); // Do this for every fragment, because it is reset after endDocument().
        super.startDocument();
      }
      transforming++;
    }
    if (transforming > 0) {
      super.startElement(nsUri, lName, qName, attrs);
    } else {
      nextStage.startElement(nsUri, lName, qName, attrs);
    }
  }
  
  public void endElement(String nsUri, String lName, String qName)
      throws SAXException
  {
    if (transforming > 0) {
      super.endElement(nsUri, lName, qName);
    } else {
      nextStage.endElement(nsUri, lName, qName);
    }
    if ( isTriggerElement(nsUri, lName, qName) ) {
      transforming--;
      if (transforming == 0) {
        super.endDocument();
      }
    }
  }
  
  public void characters(char[] chars, int start, int len) throws SAXException {
    if (transforming > 0) {
      super.characters(chars, start, len);
    } else {
      nextStage.characters(chars, start, len);
    }
  }
  
  public void comment(char[] arg0, int arg1, int arg2) throws SAXException {
    if (transforming > 0) {
      super.comment(arg0, arg1, arg2);
    } else {
      nextStage.comment(arg0, arg1, arg2);
    }
  }
  
  public void ignorableWhitespace(char[] arg0, int arg1, int arg2) throws SAXException {
    if (transforming > 0) {
      super.ignorableWhitespace(arg0, arg1, arg2);
    } else {
      nextStage.ignorableWhitespace(arg0, arg1, arg2);
    }
  }
  
  public void processingInstruction(String arg0, String arg1) throws SAXException {
    if (transforming > 0) {
      super.processingInstruction(arg0, arg1);
    } else {
      nextStage.processingInstruction(arg0, arg1);
    }
  }
  
  public void setDocumentLocator(Locator arg0) {
    if (transforming > 0) {
      super.setDocumentLocator(arg0);
    } else {
      nextStage.setDocumentLocator(arg0);
    }
  }
  
  public void skippedEntity(String arg0) throws SAXException {
    if (transforming > 0) {
      super.skippedEntity(arg0);
    } else {
      nextStage.skippedEntity(arg0);
    }
  }
  
  public void startCDATA() throws SAXException {
    if (transforming > 0) {
      super.startCDATA();
    } else {
      nextStage.startCDATA();
    }
  }
  
  public void endCDATA() throws SAXException {
    if (transforming > 0) {
      super.endCDATA();
    } else {
      nextStage.endCDATA();
    }
  }
  
  public void startDTD(String arg0, String arg1, String arg2)
      throws SAXException {
    if (transforming > 0) {
      super.startDTD(arg0, arg1, arg2);
    } else {
      nextStage.startDTD(arg0, arg1, arg2);
    }
  }
  
  public void endDTD() throws SAXException {
    if (transforming > 0) {
      super.endDTD();
    } else {
      nextStage.endDTD();
    }
  }
  
  public void startEntity(String arg0) throws SAXException {
    if (transforming > 0) {
      super.startEntity(arg0);
    } else {
      nextStage.startEntity(arg0);
    }
  }
  
  public void endEntity(String arg0) throws SAXException {
    if (transforming > 0) {
      super.endEntity(arg0);
    } else {
      nextStage.endEntity(arg0);
    }
  }

  public void startPrefixMapping(String prefix, String uri) throws SAXException {
    if (transforming > 0) {
      super.startPrefixMapping(prefix, uri);
    } else {
      fragmentConsumer.startPrefixMapping(prefix, uri);
    }
  }

  public void endPrefixMapping(String prefix) throws SAXException {
    if (transforming > 0) {
      super.endPrefixMapping(prefix);
    } else {
      fragmentConsumer.endPrefixMapping(prefix);
    }
  }
  
  /**
   * Consume XML from the TraxTransformer, and pass it on to the consumer
   * of the MultiFragmentTransformer, except begin/endDocument.
   * It may also receive and filter events from the multifragment transformer itself.
   * In this class, super is the next pipeline stage.
   */
  private class MultiFragmentConsumer extends AbstractXMLPipe {

    public void startDocument() throws SAXException {
      // Do not pass on this event when it is generated from a fragment.
    }

    public void endDocument() throws SAXException {
      // Do not pass on this event when it is generated from a fragment.
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
      if (!nsPrefixes.containsKey(prefix)) {
        nsPrefixes.put(prefix, 1);
        super.startPrefixMapping(prefix, uri);
      } else {
        nsPrefixes.put(prefix, nsPrefixes.get(prefix)+1);
      }
    }

    public void endPrefixMapping(String prefix) throws SAXException {
      if (!nsPrefixes.containsKey(prefix)) {
        return; // This is wrong, but it happens sometimes.
      }
      nsPrefixes.put(prefix, nsPrefixes.get(prefix)-1);
      if (nsPrefixes.get(prefix) == 0) {
        super.endPrefixMapping(prefix);
        nsPrefixes.remove(prefix);
      }
    }

  }
  
}
