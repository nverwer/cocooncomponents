package org.apache.cocoon.generation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.components.source.SourceUtil;
import org.apache.cocoon.environment.ObjectModelHelper;
import org.apache.cocoon.environment.Request;
import org.apache.excalibur.source.SourceException;
import org.apache.excalibur.source.impl.AbstractSource;
import org.xml.sax.SAXException;

public class RequestParameterGenerator extends ServiceableGenerator {
  
  String parameterValue;

  @Override
  public void generate() throws IOException, SAXException, ProcessingException {
    Request request = ObjectModelHelper.getRequest(this.objectModel);
    parameterValue = request.getParameter(this.source);
    SourceUtil.parse(this.manager, new StringSource(parameterValue), super.xmlConsumer);
  }

  class StringSource extends AbstractSource {

    String value;
    
    public StringSource(String value) {
      this.value = value;
    }

    @Override
    public boolean exists() {
      return value != null;
    }

    @Override
    public InputStream getInputStream() throws IOException, SourceException {
      return new ByteArrayInputStream(value.getBytes("UTF-8"));
    }
    
  }
  
}
