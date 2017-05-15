package org.apache.cocoon.acting;

import java.util.Map;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.avalon.framework.service.ServiceException;
import org.apache.avalon.framework.service.ServiceManager;
import org.apache.avalon.framework.thread.ThreadSafe;
import org.apache.cocoon.components.source.impl.PartSource;
import org.apache.cocoon.environment.Redirector;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.excalibur.source.ModifiableSource;
import org.apache.excalibur.source.Source;
import org.apache.excalibur.source.SourceUtil;
import org.apache.excalibur.source.TraversableSource;

/**
 * This variation on CopySourceAction tries to be really thread-safe by locking,
 * so different threads won't write the same file simultaneously.
 * This should prevent ConcurrentModificationException from happening in SourceUtil.copy(src, dest).
 */
public class SynchronizedCopySourceAction extends ServiceableAction implements ThreadSafe {

  private SourceResolver resolver;

  @Override
  public void service(ServiceManager manager) throws ServiceException {
      super.service(manager);
      this.resolver = (SourceResolver)manager.lookup(SourceResolver.ROLE);
  }

  @Override
  public synchronized Map act(Redirector redirector, org.apache.cocoon.environment.SourceResolver oldResolver, Map objectModel, String source, Parameters par)
      throws Exception {

      // Get source and destination Sources
      Source src = resolver.resolveURI(source);
      Source dest = resolver.resolveURI(par.getParameter("dest"));

      // Check that dest is writeable
      if (! (dest instanceof ModifiableSource)) {
          throw new IllegalArgumentException("Non-writeable URI : " + dest.getURI());
      }

      if (dest instanceof TraversableSource) {
          TraversableSource trDest = (TraversableSource) dest;
          if (trDest.isCollection()) {
              if (src instanceof TraversableSource) {
                  dest = trDest.getChild(((TraversableSource)src).getName());
              } else if (src instanceof PartSource){
                  dest = trDest.getChild(((PartSource)src).getPart().getFileName());
              }
          }
      }
      // And transfer all content.
      try {
          SourceUtil.copy(src, dest);
      } finally {
          resolver.release(src);
          resolver.release(dest);
      }
      // Success !
      return EMPTY_MAP;
  }

}
