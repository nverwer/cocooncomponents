package org.apache.cocoon.components.cron;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.XStreamException;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.extended.ISO8601DateConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.xml.DomDriver;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.avalon.framework.CascadingRuntimeException;
import org.apache.avalon.framework.configuration.Configurable;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.avalon.framework.service.ServiceException;
import org.apache.cocoon.components.cron.ConfigurableCronJob;
import org.apache.cocoon.components.cron.ServiceableCronJob;
import org.apache.cocoon.environment.CocoonRunnable;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.excalibur.source.Source;
import org.apache.excalibur.source.SourceResolver;
import org.joda.time.DateTime;

/**
 *
 * @author <a href="mailto:huib.verweij@koop.overheid.nl">Huib Verweij</a>
 *
 * Execute jobs that are submitted to a queue.
 *
 * A queue is a directory on disk that contains jobs-to-be-executed in the "in"
 * subdirectory.
 *
 * A job is a XML file containing meta-data describing the job and one or more
 * smaller tasks that are part of the job. A task contains a URL that is
 * 'resolved' when the task is executed. The output of the URL ends up in a
 * task-{id}.xml file. Note that tasks are executed *** in random order ***.
 *
 * A queue has four directories: "in", "in-progress", "out" and "error". 1 New
 * jobs that are waiting to be processed are in "in". 2 The one job that is
 * being executed is in "in-progress". 3 Finished jobs are zipped. Job-file and
 * task-output files end up in "out". 4 Jobs that could not be executed at all
 * (or generated an error in this class)end up in "error".
 *
 * Execute this object (a "Processor") every n (micro)seconds using a Cocoon
 * Quartz job and it will process one job in a queue. Processing a job means
 * moveing the job-*.xml file to "in-progress" and starting a separate thread
 * for each task in the job, then waiting till all tasks have finished.
 *
 * While executing, a Processor updates the file Queue/processor-status.xml
 * every 10 seconds or so with the following info:
 *
 * &lt;processor id="thread-id" started="dateTime"
 * tasks="nr of tasks" tasks-completed="nr of completed tasks" />

 This file can be read in order to get an idea of the progress of
 the current job. It also indicates whether the current job is being processed
 at all - if the modified timestamp of the file is older than, say, a minute,
 it is assumed the Processor/job has failed. However, when more than one
 QueueProcessorCronJob is allowed to run at the same time, this can go wrong if a
 task takes longer to complete than said minute. This is because the
 processor-status.xml file is only updated after a task has completed
 (successfully or not). It is therefore recommended to have the
 concurrent-runs="false" attribute on a Trigger.

 When a Processor starts there are a few possible scenarios: 1 another job is
 already being processed and that Processor is still alive -> quit. 2 there is
 * no job to be processed -> quit. 3 there is no other Processor running but
 * there's a job already being processed -> move job to "error"-directory, quit.
 * 4 there is a job to be processed and no Processor active -> start processing
 * a new job.
 *
 * To submit a job, place a XML file called "job-{id}.xml" in the
 * "in"-directory, containing the following structure:
 *
 * &lt;job id="..." name="test-job" description="..."
 * created="20140613T11:45:00" max-concurrent="3">
 *    &lt;tasks>
 *        &lt;task id="task-1">
 *           &lt;uri>http://localhost:8888/koop/front/queue-test?id=1&lt;/uri>
 *        &lt;/task>
 *        ...
 *    &lt;/tasks>
 * &lt;/job>
 *
 * The max-concurrent attribute can be positive or negative. When it is 
 * positive it is the maximum number of concurrent threads but the actual 
 * number of concurrent threads will never be higher than the number of
 * available cores. When it is negative the number of threads used is the 
 * number of available cores plus the max-concurrent attribute (resulting in a
 * smaller number); but it is always at least one.
 *
 * To add this cronjob to Cocoon add a trigger to the Quartzcomponent
 * configuration and declare this component in the same sitemap.
 *
 * &lt;component
 *   class="org.apache.cocoon.components.cron.CocoonQuartzJobScheduler"
 *   logger="cron" role="org.apache.cocoon.components.cron.JobScheduler">
 *   .....
 *   &lt;trigger name="queueprocessor-job"
 *       target="org.apache.cocoon.components.cron.CronJob/queueprocessor"
 *       concurrent-runs="false">
 *       &lt;cron>0/10 * * * * ?&lt;/cron>
 *   &lt;/trigger>
 * &lt;/component>
 *
 * and
 *
 * &lt;component class="org.apache.cocoon.components.cron.QueueProcessorCronJob"
 logger="cron.publish"
 role="org.apache.cocoon.components.cron.CronJob/queueprocessor">
 *    &lt;queue-path>path-to-queue-directory-on-disk&lt;/queue-path>
 * &lt;/component>
 *
 */
public class QueueProcessorCronJob extends ServiceableCronJob implements Configurable, ConfigurableCronJob {

    private static final String PARAMETER_QUEUE_PATH = "queue-path";
    private static final String PROCESSOR_STATUS_FILE = "processor-status.xml";
    private static final long PROCESSOR_STATUS_FILE_STALE = 60000;
    private static final long TASK_TIMEOUT = 500000; // 50 min.

    private static final String inDirName = "in";
    private static final String processingDirName = "in-progress";
    private static final String outDirName = "out";
    private static final String errorDirName = "error";

    private File queuePath;

    /**
     * An enum denoting the status of a Processor this class).
     */
    public enum ProcessorStatus {

        ALIVE, DEAD, NONE

    }

    /**
     * Copy job file to outDir, also, zip contents of processingDir into
     * "{currentJob-name}.zip" into outDir.
     * The zip contains a directory {currentJob-name}, all other files are in
     * that directory.
     *
     * @param processingDir
     * @param outDir
     * @param currentJob
     */
    private void finishUpJob(File processingDir, File outDir, File currentJob) throws IOException {
        final String basename = FilenameUtils.getBaseName(currentJob.getName());
        final String zipFileName = String.format("%s.zip", basename);
        File zipFile = new File(outDir, zipFileName);
        final String zipFolder = basename + "/";

        if (this.getLogger().isDebugEnabled()) {
            this.getLogger().debug("Finishing up job, creating Zip file.");
        }

        FileUtils.copyFileToDirectory(currentJob, outDir);

        try {

            // create byte buffer
            byte[] buffer = new byte[1024];

            FileOutputStream fos = new FileOutputStream(zipFile);

            ZipOutputStream zos = new ZipOutputStream(fos);

            // add a directory to the new ZIP first
            zos.putNextEntry(new ZipEntry(zipFolder));

            File[] files = processingDir.listFiles();

            for (File file : files) {
                FileInputStream fis = new FileInputStream(file);
                // begin writing a new ZIP entry, positions the stream to the start of the entry data
                zos.putNextEntry(new ZipEntry(zipFolder + file.getName()));
                int length;
                while ((length = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, length);
                }
                zos.closeEntry();
                // close the InputStream
                fis.close();
            }

            // close the ZipOutputStream
            zos.close();

        } catch (IOException ioe) {
            this.getLogger().error("Error creating zip file" + ioe);
        }

        if (this.getLogger().isDebugEnabled()) {
            this.getLogger().debug("Created Zip file.");
        }
    }

    /**
     * The object that runs a task.
     */
    private static class CocoonTaskRunner extends CocoonRunnable {

        private final Task task;
        private final SourceResolver resolver;
        private final org.apache.avalon.framework.logger.Logger logger;
        private final OutputStream os;
        private final int sequenceNumber;
        String result;

        public CocoonTaskRunner(Task t, SourceResolver resolver, org.apache.avalon.framework.logger.Logger logger, OutputStream os, int sequenceNumber) {
            this.task = t;
            this.resolver = resolver;
            this.logger = logger;
            this.os = os;
            this.sequenceNumber = sequenceNumber;
        }

//        @Override
        @Override
        public void doRun() {

            String baseMsg = String.format("\nTASK %s (%s)", this.sequenceNumber, this.task.uri);
            String pipelineResult = null;

            try {
                pipelineResult = processPipeline(task.uri, resolver, logger, os);
            } catch (Exception ex) {
                logger.info("Exception for task " + task.uri + " : " + (ex.getMessage()));
                pipelineResult = String.format("ERROR %s", ex.getMessage());

                result = String.format("%s\n%s\n\n", baseMsg, pipelineResult);
                synchronized (os) {
                    try {
                        os.write(result.getBytes());
                    } catch (IOException ex1) {
                        Logger.getLogger(QueueProcessorCronJob.class.getName()).log(Level.SEVERE, null, ex1);
                    }
                }
            }

            result = String.format("%s\n%s\n\n", baseMsg, pipelineResult);
            synchronized (os) {
                try {
                    os.write(result.getBytes());
                } catch (IOException ex) {
                    Logger.getLogger(QueueProcessorCronJob.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            return;
        }

    }

    /**
     * Process a job: add all tasks to ExecutorService, invokeAll tasks and wait
     * until all tasks have finished. While waiting, update
     * processor-status.xml.
     *
     * @param inDir Where all output files are stored.
     * @param currentJob The current job file.
     */
    private void processCurrentJobConcurrently(File inDir, File currentJob) throws ServiceException, FileNotFoundException, IOException {
        if (this.getLogger().isInfoEnabled()) {
            this.getLogger().info("Processing job " + currentJob.getAbsoluteFile());
        }

        if (this.getLogger().isDebugEnabled()) {
            this.getLogger().debug("Reading job file.");
        }
        JobConfig jobConfig = readJobConfig(currentJob);
        if (this.getLogger().isDebugEnabled()) {
            this.getLogger().debug("Job file read.");
        }

        int totalTasks = jobConfig.tasks.length;
        int completedTasks = 0;
        DateTime jobStartedAt = new DateTime();

        writeProcessorStatus(jobConfig.name, jobStartedAt, totalTasks, completedTasks);

        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int maxConcurrent = jobConfig.maxConcurrent;
        int maxThreads = 1; // default nr of threads
        if (maxConcurrent <= 0) {
            // If negative, add to availableProcessors, but of course,
            // use at least one thread.
            maxThreads = availableProcessors + maxConcurrent;
            if (maxThreads < 1) {
                maxThreads = 1;
            }
        } else {
            // Use specified maximum, but only if it is no more than what's
            // available.
            maxThreads = maxConcurrent;
            if (maxConcurrent > availableProcessors) {
                maxThreads = availableProcessors;
            }
        }
        ExecutorService threadPool = Executors.newFixedThreadPool(maxThreads);
        if (this.getLogger().isInfoEnabled()) {
            this.getLogger().info("Using " + maxThreads + " threads to execute " + totalTasks + " tasks.");
        }

        CompletionService<CocoonTaskRunner> jobExecutor = new ExecutorCompletionService<CocoonTaskRunner>(threadPool);
        SourceResolver resolver = (SourceResolver) this.manager.lookup(SourceResolver.ROLE);

        File outputFile = new File(inDir, "task-results.txt");
        OutputStream os = new FileOutputStream(outputFile);

        int submittedTasks = 0;
        for (Task t : jobConfig.tasks) {
            CocoonTaskRunner taskRunner = new CocoonTaskRunner(t, resolver, this.getLogger(), os, ++submittedTasks);
            jobExecutor.submit(taskRunner, taskRunner);
        }
        if (this.getLogger().isDebugEnabled()) {
            this.getLogger().debug("Submitted " + submittedTasks + " tasks.");
        }
        boolean interrupted = false;

        threadPool.shutdown();
        while (!threadPool.isTerminated() && !interrupted) {
            Future<CocoonTaskRunner> f = null;
            try {
                if (this.getLogger().isDebugEnabled()) {
                    this.getLogger().debug("Retrieving next finished task.");
                }
                f = jobExecutor.poll(TASK_TIMEOUT, TimeUnit.MILLISECONDS);
                if (null == f) {
                    this.getLogger().error("Failed getting next finished task (TASK_TIMEOUT (="+TASK_TIMEOUT+")), quitting.");
                    interrupted = true;
                } else {
                    if (this.getLogger().isDebugEnabled()) {
                        this.getLogger().debug("Got finished task.");
                    }
                    Object o = f.get();
                }
            } catch (ExecutionException eex) {
                this.getLogger().error("Received ExecutionException for task, ignoring, continuing with other tasks: ex = " + eex.getMessage());
            } catch (InterruptedException iex) {
                this.getLogger().error("Received InterruptedException, quitting executing tasks.");
                interrupted = true;
                break;
            } catch (CascadingRuntimeException ex) {
                this.getLogger().error("Received CascadingRuntimeException, ignoring, continuing with other tasks.");
            }
            completedTasks++;
            if (this.getLogger().isInfoEnabled()) {
                this.getLogger().info("Tasks completed: " + completedTasks + "/" + totalTasks);
            }
            writeProcessorStatus(jobConfig.name, jobStartedAt, totalTasks, completedTasks);
        }
        if (interrupted) {
            threadPool.shutdownNow();
        }
        os.close();
        this.manager.release(resolver);
    }

    /**
     * Process the URL in one Task. All errors are caught, if one task goes bad
     * continue processing the others.
     *
     * @param url URL to fetch
     * @param manager Cocoon servicemanager (so cocoon: protocol is allowed.)
     * @param logger For logging stuff
     * @param os Where the output ends up.
     * @return the output as a String object
     */
    private static String processPipeline(String url, SourceResolver resolver, org.apache.avalon.framework.logger.Logger logger, OutputStream os) throws IOException {
        Source src = null;
        InputStream is = null;
        String result = null;
        try {
//            logger.debug("Going to resolve " + url);
            src = resolver.resolveURI(url);
//            logger.debug("Resolved " + url);
            is = src.getInputStream();
            StringWriter writer = new StringWriter();
            IOUtils.copy(is, writer, "UTF-8");
            result = writer.toString();
        } finally {
            try {
                if (null != is) {
                    is.close();
                }
            } finally {
                if (null != src) {
                    resolver.release(src);
                    src = null;
                }
            }
        }
        return result;
    }

    /**
     * Check if there's a job in the processingDir. If yes then abstain if
     * Processor = ALIVE, remove it otherwise. If No then move oldest job to
     * processingDir, process that job.
     */
    private void processQueue() throws IOException {

        /*
         Create subdirs if necessary.
         */
        File queueDir = getOrCreateDirectory(this.queuePath, "");
        File inDir = getOrCreateDirectory(queueDir, inDirName);
        File processingDir = getOrCreateDirectory(queueDir, processingDirName);
        File outDir = getOrCreateDirectory(queueDir, outDirName);
        File errorDir = getOrCreateDirectory(queueDir, errorDirName);

        // Get status of Processor
        QueueProcessorCronJob.ProcessorStatus pStatus = processorStatus();

        File currentJobFile = getOldestJobFile(processingDir);

        if (this.getLogger().isDebugEnabled()) {
            this.getLogger().debug(String.format("Processor: %s, queueDir=%s, current job: %s", pStatus, queueDir, currentJobFile));
        }
        // A job is already being processed
        if (null != currentJobFile) {

            /*
             * A job is processed by a live Processor -> quit now.
             */
            if (pStatus.equals(QueueProcessorCronJob.ProcessorStatus.ALIVE)) {

                if (this.getLogger().isDebugEnabled()) {
                    this.getLogger().debug(String.format("Active job \"%s\" in queue \"%s\", stopping", currentJobFile, queueDir));
                }
            } else {
                /*
                 * A job was processed, but the Processor is dead. 
                 * Move job tot error-folder. Clean processing folder.
                 */
                this.getLogger().warn(String.format("Stale job \"%s\" in queue \"%s\", cancelling job and stopping", currentJobFile, queueDir));
                moveFileTo(currentJobFile, new File(errorDir, currentJobFile.getName()));
                FileUtils.cleanDirectory(processingDir);
            }
        } else {
            // No job being processed.
            File jobFile = getOldestJobFile(inDir);

            if (jobFile != null) {

                String jobFileName = jobFile.getName();
                File currentJob = new File(processingDir, jobFileName);

                try {

                    FileUtils.cleanDirectory(processingDir);

                    writeProcessorStatus(jobFileName, new DateTime(), 0, 0);

                    if (this.getLogger().isDebugEnabled()) {
                        this.getLogger().debug(String.format("Processing job \"%s\" in queue \"%s\"", jobFileName, queueDir));
                    }

                    moveFileTo(jobFile, currentJob);

                    if (this.getLogger().isDebugEnabled()) {
                        this.getLogger().debug(String.format("Moved job \"%s\" to \"%s\"", jobFile, currentJob));
                    }

                    processCurrentJobConcurrently(processingDir, currentJob);

                    finishUpJob(processingDir, outDir, currentJob);

                } catch (Exception e) { // Catch IOException AND catch ClassCast exception etc.
                    this.getLogger().error("Error processing job \"" + jobFileName + "\"", e);
                    moveFileTo(currentJob, new File(errorDir, jobFileName));
                    String stackTrace = ExceptionUtils.getFullStackTrace(e);
                    FileUtils.writeStringToFile(new File(errorDir, FilenameUtils.removeExtension(jobFileName) + ".txt"), stackTrace, "UTF-8");
                } finally {
                    FileUtils.cleanDirectory(processingDir);
                    deleteProcessorStatus();
                }
            } else {
                if (this.getLogger().isDebugEnabled()) {
                    this.getLogger().debug("No job, stopping");
                }
            }
        }
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
            processQueue();
        } catch (IOException e) {
            throw new CascadingRuntimeException("CronJob " + name + " raised an exception.", e);
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void setup(Parameters params, Map objects) {
        return;
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
     * Read job description file into JobConfig object using XStream.
     *
     * @param currentJob
     * @return JobConfig
     */
    private JobConfig readJobConfig(File currentJob) throws XStreamException {

        XStream xstream = getXStreamJobConfig();

        JobConfig jobConfig = (JobConfig) xstream.fromXML(currentJob);

        return jobConfig;
    }

    /**
     * Return NONE (No processor), ALIVE (Processor still active) or DEAD
     * (Processor hasn't updated status file for too long).
     *
     * @return ProcessorStatus: NONE, ALIVE or DEAD.
     */
    private synchronized ProcessorStatus processorStatus() {
        File statusFile = new File(this.queuePath, PROCESSOR_STATUS_FILE);
        if (!statusFile.exists()) {
            return QueueProcessorCronJob.ProcessorStatus.NONE;
        } else {
            long lastModified = statusFile.lastModified();
            if (System.currentTimeMillis() - lastModified > PROCESSOR_STATUS_FILE_STALE) {
                return QueueProcessorCronJob.ProcessorStatus.DEAD;
            } else {
                return QueueProcessorCronJob.ProcessorStatus.ALIVE;
            }
        }
    }

    /**
     * We're done, delete status file.
     */
    private synchronized void deleteProcessorStatus() {
        File pStatusFile = new File(this.queuePath, PROCESSOR_STATUS_FILE);
        pStatusFile.delete();
    }

    /**
     * Update status file.
     *
     * @param jobName
     * @param started
     * @param totalTasks
     * @param completedTasks
     * @param currentTaskStartedAt
     */
    private synchronized void writeProcessorStatus(String jobName, DateTime started, int totalTasks, int completedTasks) {
        File pStatusFile = new File(this.queuePath, PROCESSOR_STATUS_FILE);
        String status = String.format("<processor id=\"%s\" job-name=\"%s\" started=\"%s\" tasks=\"%d\" tasks-completed=\"%d\"/>",
                Thread.currentThread().getId(),
                jobName,
                started.toString(),
                totalTasks,
                completedTasks);
        try {
            FileUtils.writeStringToFile(pStatusFile, status, "UTF-8");
        } catch (IOException ex) {
            Logger.getLogger(QueueProcessorCronJob.class.getName()).log(Level.SEVERE, null, ex);
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
     * Move file from one File object to another File object, deleting an
     * already existing file if necessary.
     *
     * @param fromFile
     * @param toFile
     * @throws IOException
     */
    private void moveFileTo(File fromFile, File toFile) throws IOException {
        if (toFile.isFile()) {
            FileUtils.forceDelete(toFile);
        }
        if (!fromFile.renameTo(toFile)) {
            this.getLogger().error(String.format("Could not move file \"%s\" to \"%s\"", fromFile.getAbsolutePath(), toFile.getAbsoluteFile()));
        }
    }

    /**
     * Get oldest job (file named "job-*.xml") in dir, using lastModified
     * timestamp and picking first File if there is more than one file with the
     * same lastModified.
     *
     * @param dir
     * @return
     */
    protected File getOldestJobFile(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return null;
        }
        File[] files = dir.listFiles((FileFilter) new WildcardFileFilter("job-*.xml"));
        if (files.length == 0) {
            return null;
        }
        Arrays.sort(files, new Comparator<File>() {
            public int compare(File file1, File file2) {
                return Long.valueOf(file1.lastModified()).compareTo(file2.lastModified());
            }
        });
        return files[0];
    }

    /**
     * Classes used by XStream for loading the job-*.xml config files into.
     */
    public static class Task {

        /*
         <task id="task-*">
         <uri>URI of task to be executed, f.i. "cocoon://bron/svb/incremental" or
         "http://localhost:8888/import/bwbng"</uri>
         </task>
         */
        public String id;
        public String uri;

        public Task() {
        }
    }

    public static class JobConfig {

        // <job id="id" name="job name" description="job description"
        //      created="datetime" max-concurrent="n">tasks...</job>
        public String id;
        public String name;
        public String description;
        public Date created;
        public Integer maxConcurrent;
        public Task[] tasks;

        public JobConfig() {
        }
    }

    /**
     * Set some XStream options to configure serialization.
     * @return The configured XStream object.
     */
    public static XStream getXStreamJobConfig() {

        // <job id="..." name="test-job" created="20140613T11:45:00" max-concurrent="3" description="...">
        //   <tasks>
        //      <task id="task-1">
        //            <uri>http://localhost:8888/koop/front/queue-test?id=1</uri>
        //      </task>
        //      ...
        // </job>
        XStream xstream = new XStream(new DomDriver());

//        ISO8601DateConverter
        xstream.registerConverter(new ISO8601DateConverter());

        xstream.alias("job", JobConfig.class);
        xstream.useAttributeFor(JobConfig.class, "id");
        xstream.useAttributeFor(JobConfig.class, "name");
        xstream.useAttributeFor(JobConfig.class, "description");
        xstream.useAttributeFor(JobConfig.class, "created");
        xstream.useAttributeFor(JobConfig.class, "maxConcurrent");
        xstream.aliasField("max-concurrent", JobConfig.class, "maxConcurrent");

        xstream.alias("task", Task.class);
        xstream.useAttributeFor(Task.class, "id");

        return xstream;
    }

    public static class JodaTimeConverter implements Converter {

        @Override
        @SuppressWarnings("unchecked")
        public boolean canConvert(final Class type) {
            return DateTime.class.isAssignableFrom(type);
        }

        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            writer.setValue(source.toString());
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object unmarshal(HierarchicalStreamReader reader,
                UnmarshallingContext context) {
            return new DateTime(reader.getValue());
        }
    }
}
