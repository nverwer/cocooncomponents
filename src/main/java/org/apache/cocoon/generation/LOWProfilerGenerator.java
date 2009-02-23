// $Id: LOWProfilerGenerator.java,v 1.1 2005/02/28 12:40:57 verwe00t Exp $
// Used in conjunction with LOWProfilerTransformer.
package org.apache.cocoon.generation;


import java.io.*;
import java.util.*;
import java.util.regex.*;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.generation.ServiceableGenerator;
import org.apache.cocoon.ProcessingException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.SAXException;


public class LOWProfilerGenerator extends ServiceableGenerator
{

  private static final String NAMESPACE = "http://apache.org/cocoon/profiler/1.0";
  private static final String PREFIX = "profiler";
  private static final String ROOT_ELEMENT = "profilerinfo";

  private String src;

  public void setup(SourceResolver resolver, Map objectModel, String src, Parameters par)
    throws ProcessingException, SAXException, IOException
  {
    super.setup(resolver, objectModel, src, par);
    this.src = src;
    if (src == null) {
      throw new ProcessingException("No src-attribute given.");
    }
  }

  public void generate()
    throws IOException, SAXException, ProcessingException
  {
    contentHandler.startDocument();
    String rootQName = PREFIX+":"+ROOT_ELEMENT;
    AttributesImpl attrs = new AttributesImpl();
    attrs.addAttribute("", "date", "date", "CDATA", new Date().toString());
    contentHandler.startElement(NAMESPACE, ROOT_ELEMENT, rootQName, attrs);
    try {
      BufferedReader log = new BufferedReader(new InputStreamReader(resolver.resolveURI(src).getInputStream()));
      Hashtable results = getResults(log);
      resultsToXml(results);
    } catch (java.net.MalformedURLException e) {
      throw new ProcessingException("Malformed URL: "+src, e);
    }
    contentHandler.endElement(NAMESPACE, ROOT_ELEMENT, rootQName);
    contentHandler.endDocument();
  }

  private Hashtable getResults(BufferedReader log)
    throws IOException
  {
    Hashtable results = new Hashtable();
    Pattern logline = Pattern.compile("INFO[^\\]]+\\] \\(([^\\)]+)\\) [^:]+: Time: (\\d+) ms\\. / Memory: (\\d+) kB\\.");
    String line;
    while ((line = log.readLine()) != null) {
      Matcher m = logline.matcher(line);
      if (m.matches()) {
        String uri = m.group(1);
        String time = m.group(2);
        String memory = m.group(3);
        if (! results.containsKey(uri)) {
          results.put(uri, new Vector());
        }
        ((Vector)results.get(uri)).add(new ProfileRecord(time, memory));
      }
    }
    return results;
  }

  private void resultsToXml(Hashtable results)
    throws SAXException
  {
    for (Enumeration eResults = results.keys(); eResults.hasMoreElements(); ) {
      String uri = (String)eResults.nextElement();
      Vector v = (Vector)results.get(uri);
      long totalTime = 0;
      int totalMemory = 0;
      for (Enumeration ev = v.elements(); ev.hasMoreElements(); ) {
        ProfileRecord pr = (ProfileRecord)ev.nextElement();
        totalTime += pr.time;
        totalMemory += pr.memory;
      }
      AttributesImpl attrsp = new AttributesImpl();
      attrsp.addAttribute("", "uri", "uri", "CDATA", uri);
      attrsp.addAttribute("", "count", "count", "CDATA", Integer.toString(v.size()));
      attrsp.addAttribute("", "processingTime", "processingTime", "CDATA", Long.toString(totalTime));
      attrsp.addAttribute("", "processingMemory", "processingMemory", "CDATA", Integer.toString(totalMemory));
      contentHandler.startElement(NAMESPACE, "pipeline", PREFIX+":pipeline", attrsp);
      AttributesImpl attrsa = new AttributesImpl();
      attrsa.addAttribute("", "time", "time", "CDATA", Long.toString(totalTime/v.size()));
      attrsa.addAttribute("", "memory", "memory", "CDATA", Integer.toString(totalMemory/v.size()));
      contentHandler.startElement(NAMESPACE, "average", PREFIX+":average", attrsa);
      contentHandler.endElement(NAMESPACE, "average", PREFIX+":average");
      for (int index = 0; index < v.size(); ++index) {
        AttributesImpl attrsr = new AttributesImpl();
        attrsr.addAttribute("", "time", "time", "CDATA", Long.toString(((ProfileRecord)v.get(index)).time));
        attrsr.addAttribute("", "memory", "memory", "CDATA", Integer.toString(((ProfileRecord)v.get(index)).memory));
        attrsr.addAttribute("", "index", "index", "CDATA", Integer.toString(index));
        contentHandler.startElement(NAMESPACE, "result", PREFIX+":result", attrsr);
        contentHandler.endElement(NAMESPACE, "result", PREFIX+":result");
      }
      contentHandler.endElement(NAMESPACE, "pipeline", PREFIX+":pipeline");
    }
  }

  private class ProfileRecord {
    public long time;
    public int memory;
    public ProfileRecord(String sTime, String sMemory) {
      try {
      	time = Long.parseLong(sTime);
        memory = Integer.parseInt(sMemory);
      } catch (Exception e) {
        time = -1;
        memory = -1;
      }
    }
  }

}
