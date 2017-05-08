package org.apache.cocoon.components.cron;

import com.thoughtworks.xstream.XStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.avalon.framework.CascadingRuntimeException;
import org.apache.avalon.framework.configuration.Configurable;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.parameters.ParameterException;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.components.cron.ConfigurableCronJob;
import org.apache.cocoon.components.cron.QueueProcessorCronJob.JobConfig;
import org.apache.cocoon.components.cron.QueueProcessorCronJob.Task;
import static org.apache.cocoon.components.cron.QueueProcessorCronJob.getXStreamJobConfig;
import org.apache.cocoon.components.cron.ServiceableCronJob;

/**
 *
 * Submit a job to a queue.
 *
 * @author <a href="mailto:huib.verweij@koop.overheid.nl">Huib Verweij</a>
 *
 */
public class QuartzInitializer extends ServiceableCronJob implements Configurable, ConfigurableCronJob {

    private String PARAMETER_URL = "url";
    private String url;

    @SuppressWarnings("rawtypes")
    @Override
    public void setup(Parameters params, Map objects) {

    }

    /**
     * Get URL to call.
     *
     * @param config
     * @throws ConfigurationException
     */
    @Override
    public void configure(final Configuration config) throws ConfigurationException {
        url = config.getChild(PARAMETER_URL).getValue();
    }

    /**
     * Main entrypoint for CronJob.
     *
     * @param name
     */
    @Override
    public void execute(String name) {
        if (this.getLogger().isDebugEnabled()) {
            this.getLogger().debug("CronJob " + name + " launched at " + new Date());
        }
        try {
            createJob(name);
        } catch (IOException e) {
            throw new CascadingRuntimeException("CronJob " + name + " raised an exception.", e);
        }
    }

}
