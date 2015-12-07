package org.apache.cocoon.transformation;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import javax.xml.transform.OutputKeys;

import org.apache.avalon.framework.parameters.ParameterException;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * This class serializes the XML within the transforming element.
 * Namespace-prefix declarations are repeated as often as needed to allow fragments to be taken from the serialized content.
 */
public class SerializationTransformer extends AbstractSAXPipelineTransformer {
  
  public static final String SERIALIZE_ELEMENT_TAG_PARAMETER_NAME = "serialize";
  
  private String serializeElementTag;

  /* (non-Javadoc)
   * @see org.apache.cocoon.transformation.AbstractSAXTransformer#setup(org.apache.cocoon.environment.SourceResolver, java.util.Map, java.lang.String, org.apache.avalon.framework.parameters.Parameters)
   */
  @Override
  public void setup(SourceResolver resolver, Map objectModel, String src, Parameters params)
  throws ProcessingException, SAXException, IOException {
    super.setup(resolver, objectModel, src, params);
    try {
      this.serializeElementTag = params.getParameter(SERIALIZE_ELEMENT_TAG_PARAMETER_NAME);
    } catch (ParameterException e) {
      throw new ProcessingException("The "+SERIALIZE_ELEMENT_TAG_PARAMETER_NAME+" parameter is required!", e);
    }
  }

  /* (non-Javadoc)
   * @see org.apache.cocoon.transformation.AbstractSAXTransformer#startTransformingElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
   */
  @Override
  public void startTransformingElement(String uri, String name, String raw, Attributes attr)
      throws ProcessingException, IOException, SAXException {
    if (name.equals(serializeElementTag)) {
      sendStartElementEventNS(name, attr);
      startSerializedXMLRecording(propertiesForXML());
    } else  {
      super.startTransformingElement(uri, name, raw, attr);
    }
  }

  /* (non-Javadoc)
   * @see org.apache.cocoon.transformation.AbstractSAXTransformer#endTransformingElement(java.lang.String, java.lang.String, java.lang.String)
   */
  @Override
  public void endTransformingElement(String uri, String name, String raw)
      throws ProcessingException, IOException, SAXException {
    if (name.equals(serializeElementTag)) {
      String serialized = endSerializedXMLRecording();
      sendTextEvent(serialized);
      sendEndElementEventNS(name);
    } else  {
      super.endTransformingElement(uri, name, raw);
    }
  }

  /**
   * Properties for XML serialization of parsed fragments.
   */
  private static Properties propertiesForXML() {
    final Properties format = new Properties();
    format.put(OutputKeys.METHOD, "xml");
    format.put(OutputKeys.OMIT_XML_DECLARATION, "yes");
    format.put(OutputKeys.INDENT, "no");
    format.put(OutputKeys.ENCODING, "UTF-8");
    return format;
  }

}
