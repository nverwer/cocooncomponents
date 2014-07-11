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
public class QueueAddJob extends ServiceableCronJob implements Configurable, ConfigurableCronJob {

    public static final String ROLE = "org.apache.cocoon.components.cron.CronJob/queueadd";
    private static final String PARAMETER_QUEUE_PATH = "queue-path";
    private static final String JOBURI_PARAMETER = "job-uri";
    private static final String JOBNAME_PARAMETER = "job-name";
    private static final String JOBDESCRIPTION_PARAMETER = "job-description";

    private static final String inDirName = "in";

    private File queuePath;

    private String uri;
    private String jobName;
    private String jobDescription;

    @SuppressWarnings("rawtypes")
    @Override
    public void setup(Parameters params, Map objects) {
        try {
            if (this.getLogger().isInfoEnabled()) {
                this.getLogger().info(String.format("params = %s", params));
            }
            this.uri = params.getParameter(JOBURI_PARAMETER);
            this.jobName = params.getParameter(JOBNAME_PARAMETER, "Auto-generated job");
            this.jobDescription = params.getParameter(JOBDESCRIPTION_PARAMETER,
                    "Automatically added by QueueAddJob.");

            if (this.getLogger().isInfoEnabled()) {
                this.getLogger().info(String.format("job-uri = %s", uri));
                this.getLogger().info(String.format("job-name = %s", jobName));
                this.getLogger().info(String.format("job-description = %s", jobDescription));
            }

        } catch (ParameterException ex) {
            Logger.getLogger(QueueAddJob.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Return File object for parent/path, creating it if necessary.
     *
     * @param parent
     * @param path
     * @return Resulting File object.
     */
    private File getOrCreateDirectory(File parent, String path) {
        File dir = new File(parent, path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * Get path to queue folder.
     *
     * @param config
     * @throws ConfigurationException
     */
    @Override
    public void configure(final Configuration config) throws ConfigurationException {
        String actualQueuesDirName = config.getChild(PARAMETER_QUEUE_PATH).getValue();
        queuePath = new File(actualQueuesDirName);
    }

    /**
     * Create a new job in the inDir.
     */
    private void createJob(String name) throws IOException {

        /*
         Create subdirs if necessary.
         */
        File queueDir = getOrCreateDirectory(this.queuePath, "");
        File inDir = getOrCreateDirectory(queueDir, inDirName);

        UUID jobID = UUID.randomUUID();

        Task[] tasks = new Task[1];
        Task task = new Task();
        task.id = UUID.randomUUID().toString();
        task.uri = this.uri;
        tasks[0] = task;

        JobConfig job = new JobConfig();
        job.id = jobID.toString();
        job.description = this.jobDescription;
        job.maxConcurrent = 1;
        job.name = this.jobName;
        job.created = new Date();
        job.tasks = tasks;

        File currentJobFile = new File(inDir, String.format("job-%s.xml", jobID));

        if (this.getLogger().isInfoEnabled()) {
            this.getLogger().info(String.format("New job: %s", currentJobFile.getAbsolutePath()));
        }

        XStream xstream = getXStreamJobConfig();

        xstream.toXML(job, new FileOutputStream(currentJobFile));

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
