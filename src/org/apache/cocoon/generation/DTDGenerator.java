/**
 * 
 */
package org.apache.cocoon.generation;

import java.io.IOException;

import org.apache.avalon.framework.logger.Logger;
import org.apache.avalon.framework.parameters.ParameterException;
import org.apache.avalon.framework.service.ServiceException;
import org.apache.avalon.framework.service.ServiceManager;
import org.apache.cocoon.ProcessingException;
import org.apache.excalibur.xml.DefaultEntityResolver;
import org.cyberneko.dtd.parsers.SAXParser;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.ParserAdapter;

public class DTDGenerator extends FileGenerator {

  @Override
  public void generate() throws IOException, SAXException, ProcessingException {
    DefaultEntityResolver entityResolver;
    try {
      entityResolver = new MyEntityResolver(parameters.getParameter("catalog"), this.manager, this.getLogger());
    } catch (ParameterException e) {
      throw new ProcessingException("Missing 'catalog' parameter.", e);
    } catch (ServiceException e) {
      throw new ProcessingException("Cannot service the entity-resolver?", e);
    }
    SAXParser saxParser = new SAXParser();
    saxParser.setEntityResolver(entityResolver);
    //ParserAdapter parser = new ParserAdapter(saxParser);
    //parser.setEntityResolver(entityResolver);
    saxParser.setFeature("http://xml.org/sax/features/namespaces", true);
    saxParser.setFeature("http://xml.org/sax/features/namespace-prefixes", true);
    saxParser.setContentHandler(contentHandler);
    //saxParser.parse(new org.xml.sax.InputSource(inputSource.getInputStream()));
    saxParser.parse(entityResolver.resolveEntity(null, source));
  }
  
  private class MyEntityResolver extends DefaultEntityResolver {
    
    public MyEntityResolver(String catalogFile, ServiceManager manager, Logger logger) throws ServiceException {
      enableLogging(logger);
      service(manager);
      parseCatalog(catalogFile);
    }
    
  }
  
}
