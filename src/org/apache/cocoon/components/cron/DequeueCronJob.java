package org.apache.cocoon.components.cron;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.apache.avalon.framework.CascadingRuntimeException;
import org.apache.avalon.framework.configuration.Configurable;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.components.cron.ConfigurableCronJob;
import org.apache.cocoon.components.cron.ServiceableCronJob;
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
 *
 * @author <a href="mailto:maarten.kroon@koop.overheid.nl">Maarten Kroon</a>
 * @author <a href="mailto:huib.verweij@koop.overheid.nl">Huib Verweij</a>
 *
 * Execute jobs that are submitted to a queue.
 *
 * A queue is a directory on disk that contains jobs.
 *
 * A job is a zipped directory containing meta-data describing the job and one
 * or more smaller tasks that are part of the job.
 *
 * A queue has four directories, in, in-progress, out and error. 1 New jobs that
 * are waiting to be processed are in "in". 2 The one job that is being executed
 * is in "in-progress". 3 Finished jobs are moved to "out". 4 Jobs that could
 * not be executed at all end up in "error".
 *
 * Execute this object (a "Processor") every n (micro)seconds using a Cocoon
 * Quartz job and it will process one job in a queue. Processing a job means
 * unzipping the job.zip and starting a separate thread for each task in the
 * job. Every thread is 'join'ed so there are no runaway processes.
 *
 * While executing, a Processor updates the file processor-status.xml every 10
 * seconds or so with the following info:
 * <processor id="(OS?) process-id" started="dateTime" tasks="nr of tasks"
 * tasks-completed="nr of completed tasks" task-started-at="time current task
 * was started"/>
 * This file can be read in order to get an idea of the progress of the current
 * job. It also indicates whether the current job is being processed at all - if
 * the modified timestamp of the file is older than, say, a minute, it is
 * assumed the Processor/job has failed.
 *
 * When a Processor starts there are a few possible scenarios: 1 another job is
 * already being processed and that Processor is still alive -> quit. 2 there is
 * no job to be processed -> quit. 3 there is no other Processor running but
 * there's a job already being processed -> move job to "error"-directory, quit.
 * 4 there is a job to be processed and no Processor active -> start processing
 * new job.
 *
 * To submit a job, upload a XML called "job-*.xml", containing the following
 * structure:
 * <job name="test-job" created="20140613T11:45:00" max-concurrent="3">
 * <tasks>
 * <task id="task-1">
 * <uri>http://localhost:8888/koop/front/queue-test?id=1</uri>
 * </task>
 * ...
 * </tasks>
 * </job>
 *
 */
public class DequeueCronJob extends ServiceableCronJob implements Configurable, ConfigurableCronJob {

    private static final String PARAMETER_QUEUE_PATH = "queue-path";
    private static final String PROCESSOR_STATUS_FILE = "processor-status.xml";
    private static final long PROCESSOR_STATUS_FILE_STALE = 60000;

    private final String inDirName = "in";
    private final String processingDirName = "in-progress";
    private final String outDirName = "out";
    private final String errorDirName = "error";

    private File queuePath;
    private final long DONT_WAIT_TOO_LONG = 8000;

    /**
     * Zip contents of processingDir into file, named after currentJob,
     * into outDir.
     *
     * @param processingDir
     * @param outDir
     * @param currentJob
     */
    private void finishUpJob(File processingDir, File outDir, File currentJob) {
        final String basename = FilenameUtils.getBaseName(currentJob.getName());
        final String zipFileName = String.format("%s.zip", basename);
        File zipFile = new File(outDir, zipFileName);
        final String zipFolder = basename + "/";

        try {

            // create byte buffer
            byte[] buffer = new byte[1024];

            FileOutputStream fos = new FileOutputStream(zipFile);

            ZipOutputStream zos = new ZipOutputStream(fos);

            // add a directory to the new ZIP first
            zos.putNextEntry(new ZipEntry(zipFolder));

            File[] files = processingDir.listFiles();

            for (File file : files) {
                System.out.println("Adding file: " + file.getName());
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
            System.out.println("Error creating zip file" + ioe);
        }
    }

    private static class TaskRunner implements Callable {

        private final Task task;
        private final org.apache.avalon.framework.service.ServiceManager manager;
        private final org.apache.avalon.framework.logger.Logger logger;
        private final File outputFile;

        public TaskRunner(Task t, org.apache.avalon.framework.service.ServiceManager manager, org.apache.avalon.framework.logger.Logger logger, File outputFile) {
            this.task = t;
            this.manager = manager;
            this.logger = logger;
            this.outputFile = outputFile;
        }

        @Override
        public Object call() throws Exception {
            this.logger.info("Processing task " + task.id);
//            Thread.sleep(4000);
            OutputStream os = new FileOutputStream(outputFile);
            processPipeline(task.uri, manager, logger, os);
            os.close();
            return null;
        }
    }

    /**
     * An enum denoting the status of a Processor this class).
     */
    public enum ProcessorStatus {

        ALIVE, DEAD, NONE
        
    }

//    private ExecutorService executor = Executors.newFixedThreadPool(1);
    private void processCurrentJob(File inDir, File currentJob) {
        this.getLogger().info("Processing job " + currentJob.getAbsoluteFile());

        JobConfig jobConfig = readJobConfig(currentJob);

        int totalTasks = jobConfig.tasks.length;
        int completedTasks = 0;
        DateTime jobStartedAt = new DateTime();


        int processors = Runtime.getRuntime().availableProcessors();
        ExecutorService jobExecutor = Executors.newFixedThreadPool(processors);
        this.getLogger().info("Max concurrent jobs = " + processors);

        Set<Callable<TaskRunner>> callables = new HashSet<Callable<TaskRunner>>();

        for (Task t : jobConfig.tasks) {
            File outputFile = new File(inDir, String.format("task-%s.output", t.id));
            Callable taskRunner = new TaskRunner(t, this.manager, this.getLogger(), outputFile);
            callables.add(taskRunner);
        }

        try {
            List<Future<TaskRunner>> futures = jobExecutor.invokeAll(callables);

            for (Future<TaskRunner> future : futures) {
                try {
                    this.getLogger().info("future.get = " + future.get());
                    completedTasks--;
                    writeProcessorStatus(jobConfig.name, jobStartedAt, totalTasks, completedTasks);
                } catch (ExecutionException ex) {
                    Logger.getLogger(DequeueCronJob.class.getName()).log(Level.SEVERE, null, ex);
                    completedTasks--;
                }
            }
            jobExecutor.shutdown();
            while (!jobExecutor.awaitTermination(DONT_WAIT_TOO_LONG, TimeUnit.MILLISECONDS)) {
                this.getLogger().info("Waiting for all tasks to finish ");
            }
        } catch (InterruptedException ex) {
            Logger.getLogger(DequeueCronJob.class.getName()).log(Level.SEVERE, null, ex);
            jobExecutor.shutdownNow();
        }
    }

    
    /**
     * Read job description file into JobConfig object using XStream.
     *
     * @param currentJob
     * @return JobConfig
     */
    private JobConfig readJobConfig(File currentJob) {

        // <job name="test-job" created="20140613T11:45:00" max-concurrent="3">
        //   <tasks>
        //      <task id="task-1">
        //            <uri>http://localhost:8888/koop/front/queue-test?id=1</uri>
        //      </task>
        //      ...
        // </job>
        XStream xstream = new XStream(new DomDriver());
        xstream.alias("job", JobConfig.class);
        xstream.useAttributeFor(JobConfig.class, "name");
        xstream.useAttributeFor(JobConfig.class, "created");
        xstream.useAttributeFor(JobConfig.class, "maxConcurrent");
        xstream.aliasField("max-concurrent", JobConfig.class, "maxConcurrent");

        xstream.alias("task", Task.class);
        xstream.useAttributeFor(Task.class, "id");

        JobConfig jobConfig = (JobConfig) xstream.fromXML(currentJob);

        this.getLogger().info("jobConfig.name = " + jobConfig.name);
        this.getLogger().info("jobConfig.created = " + jobConfig.created);
        this.getLogger().info("jobConfig.maxConcurrent = " + jobConfig.maxConcurrent);
        this.getLogger().info("jobConfig.tasks = " + jobConfig.tasks);
        this.getLogger().info("jobConfig.tasks.length = " + jobConfig.tasks.length);

        return jobConfig;
    }

    
    @SuppressWarnings("rawtypes")
    @Override
    public void setup(Parameters params, Map objects) {
        return;
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
            Logger.getLogger(DequeueCronJob.class.getName()).log(Level.INFO, status, status);
        } catch (IOException ex) {
            Logger.getLogger(DequeueCronJob.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    
    /**
     * Return File object for parent/path, creating it if necessary.
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
     * Move file from one File object to another File object, deleting
     * an already existing file if necessary.
     * @param fromFile
     * @param toFile
     * @throws IOException 
     */
    private void moveFileTo(File fromFile, File toFile) throws IOException {
        if (toFile.isFile()) {
            FileUtils.forceDelete(toFile);
        }
        if (!fromFile.renameTo(toFile)) {
            if (this.getLogger().isErrorEnabled()) {
                this.getLogger().error(String.format("Could not move file \"%s\" to \"%s\"", fromFile.getAbsolutePath(), toFile.getAbsoluteFile()));
            }
        }
    }

    
    /**
     * Get oldest job (file named "job-*.xml") in dir, using
     * lastModified timestamp and picking first File if there is more
     * than one file with the same lastModified.
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
     * Get path to queue folder.
     * @param config
     * @throws ConfigurationException 
     */
    @Override
    public void configure(final Configuration config) throws ConfigurationException {
        String actualQueuesDirName = config.getChild(PARAMETER_QUEUE_PATH).getValue();
        queuePath = new File(actualQueuesDirName);
    }

    
    /**
     * Process the URL in one Task. All errors are caught, if one task goes bad
     * continue processing the others.
     * @param url URL to fetch
     * @param manager Cocoon servicemanager (so cocoon: protocol is allowed.)
     * @param logger For logging stuff
     * @param os Where the output ends up.
     */
    private static void processPipeline(String url, org.apache.avalon.framework.service.ServiceManager manager, org.apache.avalon.framework.logger.Logger logger, OutputStream os) {
        SourceResolver resolver = null;
        Source src = null;
        InputStream is = null;
        String cocoonPipeline = url;
        try {

            logger.info(String.format("Processing: %s", url));

            resolver = (SourceResolver) manager.lookup(SourceResolver.ROLE);
            src = resolver.resolveURI(cocoonPipeline);
            is = src.getInputStream();
            IOUtils.copy(is, os);

        } catch (Exception e) {
            try {
                os.write(e.getMessage().getBytes());
                os.write("\n".getBytes());
                e.printStackTrace(new PrintStream(os));
            } catch (IOException ex) {
                Logger.getLogger(DequeueCronJob.class.getName()).log(Level.SEVERE, null, ex);
            }
            throw new CascadingRuntimeException(String.format("Error processing pipeline \"%s\"", cocoonPipeline), e);
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
                throw new CascadingRuntimeException("DequeueCronJob raised an exception.", e);
            }
            if (resolver != null) {
                resolver.release(src);
                manager.release(resolver);
                resolver = null;
                src = null;
            }
        }
    }

    /**
     * Check if there's a job in the processingDir.
     * If yes then abstain if Processor = ALIVE, remove it otherwise.
     * If No then move oldest job to processingDir, process that job.
     */
    private void processQueue() throws IOException {

        /*
            Create subdirs if necessary.
        */
        File queueDir = this.queuePath;
        File inDir = getOrCreateDirectory(queueDir, inDirName);
        File processingDir = getOrCreateDirectory(queueDir, processingDirName);
        File outDir = getOrCreateDirectory(queueDir, outDirName);
        File errorDir = getOrCreateDirectory(queueDir, errorDirName);


        // Get status of Processor
        DequeueCronJob.ProcessorStatus pStatus = processorStatus();

        File currentJobFile = getOldestJobFile(processingDir);

        this.getLogger().info(String.format("Processor: %s, current job: %s", pStatus, currentJobFile));

        // A job is already being processed
        if (null != currentJobFile) {

            /*
             * A job is processed by a live Processor -> quit now.
             */
            if (pStatus.equals(DequeueCronJob.ProcessorStatus.ALIVE)) {

                if (this.getLogger().isDebugEnabled()) {
                    this.getLogger().debug(String.format("Active job \"%s\" in queue \"%s\", stopping", currentJobFile, queueDir));
                }
                return;
            }
            else {            
                /*
                 * A job was processed, but the Processor is dead. 
                 * Move job tot error-folder. Clean processing folder.
                 */
                this.getLogger().warn(String.format("Stale job \"%s\" in queue \"%s\", cancelling job and stopping", currentJobFile, queueDir));
                moveFileTo(currentJobFile, new File(errorDir, currentJobFile.getName()));
                FileUtils.cleanDirectory(processingDir);
                return;
            }
        }
        else {
            // No job being processed.
            File jobFile = getOldestJobFile(inDir);

            if (jobFile != null) {

                String jobFileName = jobFile.getName();
                File currentJob = new File(processingDir, jobFileName);

                try {

                    writeProcessorStatus(jobFileName, new DateTime(), 0, 0);

                    if (this.getLogger().isDebugEnabled()) {
                        this.getLogger().debug(String.format("Processing job \"%s\" in queue \"%s\"", jobFileName, queueDir));
                    }

                    moveFileTo(jobFile, currentJob);

                    this.getLogger().debug(String.format("Moved job \"%s\" to \"%s\"", jobFile, currentJob));

                    processCurrentJob(processingDir, currentJob);

                    finishUpJob(processingDir, outDir, currentJob);

                } catch (IOException e) {
                    if (this.getLogger().isErrorEnabled()) {
                        this.getLogger().error("Error processing job \"" + jobFileName + "\"", e);
                    }
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
     * Return NONE (No processor), ALIVE (Processor still active) or DEAD
     * (Processor hasn't updated status file for too long).
     *
     * @return ProcessorStatus: NONE, ALIVE or DEAD.
     */
    private synchronized ProcessorStatus processorStatus() {
        File statusFile = new File(this.queuePath, PROCESSOR_STATUS_FILE);
        if (!statusFile.exists()) {
            return DequeueCronJob.ProcessorStatus.NONE;
        } else {
            long lastModified = statusFile.lastModified();
            if (System.currentTimeMillis() - lastModified > PROCESSOR_STATUS_FILE_STALE) {
                return DequeueCronJob.ProcessorStatus.DEAD;
            } else {
                return DequeueCronJob.ProcessorStatus.ALIVE;
            }
        }
    }

    
    /**
     * Main entrypoint for CronJob.
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

    
    /**
     * Classes used by XStream for loading the job-*.xml config files into.
     */
    private class Task {

        /*
            <task id="task-*">
                <uri>URI of task to be executed, f.i. "cocoon://bron/svb/incremental" or
                "http://localhost:8888/import/bwbng"</uri>
            </task>
         */
        private String id;
        private String uri;

        public Task() {
        }
    }

    private class JobConfig {

        // <job name="job-name" created="date" max-concurrent="n">tasks...</job>
        private String name;
        private DateTime created;
        private Integer maxConcurrent;
        private Task[] tasks;

        public JobConfig() {
        }
    }
}
