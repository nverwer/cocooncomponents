// $Id: LOWProfilerTransformer.java,v 1.1 2005/02/28 12:41:00 verwe00t Exp $

/*
  Use the LOWProfilerGenerator to get the data from the log-file.
  This must be inserted just before the serializer in the pipeline.
  This transformer logs one line, which looks like
  INFO    (2005-02-24) 16:40.55:477   [lowprofiler] (URI) PoolThread-4/LOWProfilerTransformer: Time: 1234 ms. / Memory: 5678 kB.
  You do need to configure the lowprofiler (or whatever you call it) log-target.
*/

package org.apache.cocoon.transformation;

import java.io.*;
import java.util.*;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.transformation.AbstractTransformer;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class LOWProfilerTransformer
  extends AbstractTransformer
{

  private long usedMemory;
  private long startTime;

  private void minimizeMemory() {
    try {
      System.gc();
      Thread.sleep(100);
      System.runFinalization();
      Thread.sleep(100);
      System.gc();
      Thread.sleep(100);
    } catch (InterruptedException e) {
    }
  }

  private void measureMemory() {
    long usedNow = Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
    if (usedNow > usedMemory) {
      usedMemory = usedNow;
    }
  }

  public void setup(SourceResolver resolver, Map objectModel, String src, Parameters par)
    throws ProcessingException, SAXException, IOException
  {
    minimizeMemory();
    usedMemory = 0;
    startTime = new Date().getTime();
  }

  public void startElement(String namespaceURI, String lName, String qName, Attributes attributes)
    throws SAXException
  {
    measureMemory();
    super.startElement(namespaceURI, lName, qName, attributes);
  }

  public void endElement(String namespaceURI, String lName, String qName)
    throws SAXException
  {
    measureMemory();
    super.endElement(namespaceURI, lName, qName);
  }

/*
  public void startDocument()
    throws SAXException
  {
System.err.println((new Date().getTime() - startTime) + " ms. since start.");
    super.startDocument();
  }
*/

  public void endDocument()
    throws SAXException
  {
    super.endDocument();
    long time = new Date().getTime() - startTime;
    getLogger().info("Time: "+time+" ms. / Memory: "+(usedMemory/1024)+" kB.");
  }

}
