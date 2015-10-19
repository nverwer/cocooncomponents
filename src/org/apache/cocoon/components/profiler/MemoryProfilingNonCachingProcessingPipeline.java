/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
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
package org.apache.cocoon.components.profiler;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.util.Iterator;
import java.util.List;

import org.apache.avalon.framework.activity.Disposable;
import org.apache.avalon.framework.component.ComponentException;
import org.apache.avalon.framework.component.ComponentManager;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.components.pipeline.impl.NonCachingProcessingPipeline;
import org.apache.cocoon.environment.Environment;
import org.apache.cocoon.sitemap.SitemapModelComponent;
import org.apache.cocoon.transformation.Transformer;
import org.apache.cocoon.xml.XMLConsumer;
import org.apache.cocoon.xml.XMLProducer;
import org.xml.sax.SAXException;

/**
 * Special version of the NonCachingProcessingPipeline that measures heap memory
 * usage of pipelines. Based on ProfilingNonCachingProcessingPipeline.
 * The total memory usage in bytes will be the value of the 'time' attribute in the profile.
 * In this way, the existing profiling componets can be used.
 * 
 * @author <a href="mailto:nverwer@rakensi.com">Nico Verwer</a>
 * @author <a href="mailto:vgritsenko@apache.org">Vadim Gritsenko</a>
 * @author <a href="mailto:stephan@apache.org">Stephan Michels</a>
 * @author <a href="mailto:bruno@outerthought.org">Bruno Dumon</a>
 */
public class MemoryProfilingNonCachingProcessingPipeline
    extends NonCachingProcessingPipeline implements Disposable {

  private Profiler profiler;
  private ProfilerData data;
  private List<MemoryPoolMXBean> memoryMX;

  /**
   * Composable
   * @param manager
   */
  public void compose(ComponentManager manager) throws ComponentException {
    super.compose(manager);
    this.profiler = (Profiler) manager.lookup(Profiler.ROLE);
    this.memoryMX = ManagementFactory.getMemoryPoolMXBeans();
  }

  /**
   * Disposable
   */
  public void dispose() {
    if (this.profiler != null) {
      this.manager.release(this.profiler);
      this.profiler = null;
    }
  }

  /**
   * Recyclable
   */
  public void recycle() {
    this.data = null;
    super.recycle();
  }
  
  private void initMemoryMeasurement() {
    System.gc();
    for (MemoryPoolMXBean mmx : this.memoryMX) {
      mmx.resetPeakUsage();
    }
  }
  
  private long getMemoryMeasurement() {
    long memoryInUse = 0;
    for (MemoryPoolMXBean mmx : this.memoryMX) {
      memoryInUse += mmx.getPeakUsage().getUsed();
    }
    return memoryInUse;
  }

  /**
   * Process the given <code>Environment</code>, producing the output.
   *
   * @param environment
   *
   * @return true on success
   */
  public boolean process(Environment environment) throws ProcessingException {
    if (this.data == null) this.data = new ProfilerData();
    if (this.data != null) {
      // Capture environment info
      this.data.setEnvironmentInfo(new EnvironmentInfo(environment));
      // Execute pipeline
      this.initMemoryMeasurement();
      boolean result = super.process(environment);
      this.data.setTotalTime(this.getMemoryMeasurement());
      // Report
      profiler.addResult(environment.getURI()+" (process)", this.data);
      return result;
    } else {
      getLogger().warn("Profiler Data does not have any components to measure");
      return super.process(environment);
    }
  }

}
