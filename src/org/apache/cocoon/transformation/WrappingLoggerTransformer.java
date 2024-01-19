package org.apache.cocoon.transformation;

import java.io.IOException;
import java.util.Map;

import org.apache.avalon.framework.component.Component;
import org.apache.avalon.framework.component.ComponentException;
import org.apache.avalon.framework.component.ComponentSelector;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.avalon.framework.service.ServiceException;
import org.apache.avalon.framework.service.ServiceManager;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.xml.XMLConsumer;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/** THIS CLASS IS UNDER CONSTRUCTION!
 * This component sends messages to a logger, wrapping another transformer.
 *
 * Configuration:
 * ```
 * <map:transformer name="my-log" src="org.apache.cocoon.transformation.WrappingLoggerTransformer" logger="my-logger"/>
 * ```
 * Usage:
 * ```
 * <map:transform type="log-message">
 *   <map:parameter name="target" value="log"/>
 *   <map:parameter name="level" value="info"/>
 *   <map:parameter name="message" value="Hello, world."/>
 *   <map:parameter name="type" value="xslt-saxon"/>
 *   <map:parameter name="src" value="stylesheet.xslt"/>
 *   <map:paramater name=":xslt-param" value="pass this on to xslt"/>
 *   <map:parameter name=":message" value="Hello, xslt."/>
 * </map:transform>
 * ```
 * The `target` parameter can be 'log' (default), which writes to a log file,
 * or 'log-stat', which writes document statistics to the log,
 * or 'console', which writes to the standard output or standard error.
 * It can also be a combination like 'console and log'.
 *
 * The level can be 'error', 'warn', 'warning', 'info'. The default level is 'info'.
 *
 * The `type` and `src` parameters specify the wrapped transformer, and would normally appear as attributes on `<map:transform>`.
 * The parameters starting with `:` are passed on to the wrapped transformer.
 */
public class WrappingLoggerTransformer extends AbstractSAXPipelineTransformer
{

  private AbstractSAXTransformer wrappedTransformer;

  private AbstractSAXTransformer getNamedTransformer(String name) throws ProcessingException {
    // Taken from org.apache.cocoon.components.pipeline.AbstractProcessingPipeline:addTransformer l.270
    ComponentSelector selector = null;
    try {
      selector = (ComponentSelector) this.manager.lookup(Transformer.ROLE + "Selector");
    } catch (ServiceException ce) {
      throw new ProcessingException("Lookup of transformer selector failed", ce);
    }
    try {
      Component selectedComponent = selector.select(name);
      return (AbstractSAXTransformer) selectedComponent;
    } catch (ComponentException ce) {
      throw new ProcessingException("Lookup of transformer '"+name+"' failed", ce);
    }
  }

  @Override
  public void service(ServiceManager serviceManager) throws ServiceException
  {
    // TODO Auto-generated method stub
    super.service(serviceManager);
  }

  @Override
  public void configure(Configuration configuration) throws ConfigurationException
  {
    // TODO Auto-generated method stub
    super.configure(configuration);
  }

  @Override
  public void setup(SourceResolver resolver, Map objectModel, String src, Parameters params)
      throws ProcessingException, SAXException, IOException
  {
    // TODO Auto-generated method stub
    super.setup(resolver, objectModel, src, params);
  }

  @Override
  public void recycle()
  {
    // TODO Auto-generated method stub
    super.recycle();
  }

  @Override
  public void dispose()
  {
    // TODO Auto-generated method stub
    super.dispose();
  }

  @Override
  public void startPrefixMapping(String prefix, String uri) throws SAXException
  {
    // TODO Auto-generated method stub
    super.startPrefixMapping(prefix, uri);
  }

  @Override
  public void endPrefixMapping(String prefix) throws SAXException
  {
    // TODO Auto-generated method stub
    super.endPrefixMapping(prefix);
  }

  @Override
  public void setDocumentLocator(Locator locator)
  {
    // TODO Auto-generated method stub
    super.setDocumentLocator(locator);
  }

  @Override
  public void startDocument() throws SAXException
  {
    // TODO Auto-generated method stub
    super.startDocument();
  }

  @Override
  public void endDocument() throws SAXException
  {
    // TODO Auto-generated method stub
    super.endDocument();
  }

  @Override
  public void startElement(String uri, String name, String raw, Attributes attr) throws SAXException
  {
    // TODO Auto-generated method stub
    super.startElement(uri, name, raw, attr);
  }

  @Override
  public void endElement(String uri, String name, String raw) throws SAXException
  {
    // TODO Auto-generated method stub
    super.endElement(uri, name, raw);
  }

  @Override
  public void characters(char[] p0, int p1, int p2) throws SAXException
  {
    // TODO Auto-generated method stub
    super.characters(p0, p1, p2);
  }

  @Override
  public void ignorableWhitespace(char[] p0, int p1, int p2) throws SAXException
  {
    // TODO Auto-generated method stub
    super.ignorableWhitespace(p0, p1, p2);
  }

  @Override
  public void processingInstruction(String target, String data) throws SAXException
  {
    // TODO Auto-generated method stub
    super.processingInstruction(target, data);
  }

  @Override
  public void skippedEntity(String name) throws SAXException
  {
    // TODO Auto-generated method stub
    super.skippedEntity(name);
  }

  @Override
  public void startDTD(String name, String public_id, String system_id) throws SAXException
  {
    // TODO Auto-generated method stub
    super.startDTD(name, public_id, system_id);
  }

  @Override
  public void endDTD() throws SAXException
  {
    // TODO Auto-generated method stub
    super.endDTD();
  }

  @Override
  public void startEntity(String name) throws SAXException
  {
    // TODO Auto-generated method stub
    super.startEntity(name);
  }

  @Override
  public void endEntity(String name) throws SAXException
  {
    // TODO Auto-generated method stub
    super.endEntity(name);
  }

  @Override
  public void startCDATA() throws SAXException
  {
    // TODO Auto-generated method stub
    super.startCDATA();
  }

  @Override
  public void endCDATA() throws SAXException
  {
    // TODO Auto-generated method stub
    super.endCDATA();
  }

  @Override
  public void comment(char[] ary, int start, int length) throws SAXException
  {
    // TODO Auto-generated method stub
    super.comment(ary, start, length);
  }

  @Override
  protected void addRecorder(XMLConsumer recorder)
  {
    // TODO Auto-generated method stub
    super.addRecorder(recorder);
  }

  @Override
  public void setupTransforming() throws IOException, ProcessingException, SAXException
  {
    // TODO Auto-generated method stub
    super.setupTransforming();
  }

}
