package de.micromata.borgbutler.jobs;

import de.micromata.borgbutler.config.Definitions;
import org.apache.commons.exec.*;
import org.apache.commons.exec.environment.EnvironmentUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * A queue is important because Borg doesn't support parallel calls for one repository.
 * For each repository one single queue is allocated.
 */
public abstract class AbstractCommandLineJob extends AbstractJob<String> {
    private Logger log = LoggerFactory.getLogger(AbstractCommandLineJob.class);
    private ExecuteWatchdog watchdog;
    private boolean executeStarted;
    private CommandLine commandLine;
    /**
     * The command line as string. This property is also used as ID for detecting multiple borg calls.
     */
    private String commandLineAsString;
    private File workingDirectory;
    private String description;
    protected ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    protected ByteArrayOutputStream errorOutputStream = new ByteArrayOutputStream();
    protected boolean logError = true;

    protected abstract CommandLine buildCommandLine();

    @Override
    public Object getId() {
        return getCommandLineAsString();
    }

    public String getCommandLineAsString() {
        if (commandLine == null) {
            commandLine = buildCommandLine();
        }
        if (commandLine == null) {
            return null;
        }
        if (commandLineAsString == null) {
            commandLineAsString = commandLine.getExecutable() + " " + StringUtils.join(commandLine.getArguments(), " ");
        }
        return commandLineAsString;
    }

    @Override
    public JobResult<String> execute() {
        getCommandLineAsString();
        if (commandLine == null) {
            return null;
        }
        DefaultExecutor executor = new DefaultExecutor();
        if (workingDirectory != null) {
            executor.setWorkingDirectory(workingDirectory);
        }
        //executor.setExitValue(2);
        this.watchdog = new ExecuteWatchdog(ExecuteWatchdog.INFINITE_TIMEOUT);
        executor.setWatchdog(watchdog);
        //  ExecuteResultHandler handler = new DefaultExecuteResultHandler();
        PumpStreamHandler streamHandler = new PumpStreamHandler(new LogOutputStream() {
            @Override
            protected void processLine(String line, int level) {
                processStdOutLine(line, level);
            }
        }, new LogOutputStream() {
            @Override
            protected void processLine(String line, int level) {
                processStdErrLine(line, level);
            }
        });
        executor.setStreamHandler(streamHandler);
        String msg = StringUtils.isNotBlank(this.description) ? description + " ('" + commandLineAsString + "')..."
                : "Executing '" + commandLineAsString + "'...";
        log.info(msg);
        this.executeStarted = true;
        JobResult<String> result = new JobResult<>();
        try {
            executor.execute(commandLine, getEnvironment());
            result.setStatus(JobResult.Status.OK);
            log.info(msg + " Done.");
        } catch (Exception ex) {
            result.setStatus(JobResult.Status.ERROR);
            if (logError && !isCancellationRequested() && getStatus() != Status.CANCELLED) {
                log.error("Execution failed for job: '" + commandLineAsString + "': " + ex.getMessage());
                log.error("Error output of job '" + commandLineAsString + "': "
                        + getErrorString(2000));
            }
            failed();
        }
        result.setResultObject(outputStream.toString(Definitions.STD_CHARSET));
        return result;
    }

    /**
     * @param maxlength The result string will be abbreviated (in the middle).
     * @return
     * @see StringUtils#abbreviateMiddle(String, String, int)
     */
    public String getErrorString(int maxlength) {
        return StringUtils.abbreviateMiddle(errorOutputStream.toString(Definitions.STD_CHARSET),
                "\n    [... ***** error log abbreviated ***** ...]\n", maxlength);
    }

    public void processStdOutLine(String line, int level) {
        //log.info(line);
        try {
            outputStream.write(line.getBytes());
            outputStream.write("\n".getBytes());
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
        }
    }

    public void processStdErrLine(String line, int level) {
        //log.info(line);
        try {
            errorOutputStream.write(line.getBytes());
            errorOutputStream.write("\n".getBytes());
        } catch (IOException ex) {
            log.error(ex.getMessage(), ex);
        }
    }

    @Override
    protected void cancelRunningProcess() {
        if (watchdog != null) {
            log.info("Cancelling job #" + getUniqueJobNumber() + ": " + getId());
            watchdog.destroyProcess();
            watchdog = null;
        }
    }

    protected Map<String, String> getEnvironment() throws IOException {
        return null;
    }

    protected void addEnvironmentVariable(Map<String, String> env, String name, String value) {
        if (StringUtils.isNotBlank(value)) {
            EnvironmentUtils.addVariableToEnvironment(env, name + "=" + value);
        }
    }

    /**
     * @param env
     * @param variable Variable in format "variable=value".
     */
    protected void addEnvironmentVariable(Map<String, String> env, String variable) {
        if (StringUtils.isNotBlank(variable)) {
            EnvironmentUtils.addVariableToEnvironment(env, variable);
        }
    }

    /**
     * Frees the output streams.
     * Should be called after a job was done, failed or cancelled while running.
     */
    public void cleanUp() {
        log.debug("Freeing resources of job: " + commandLineAsString);
        outputStream = null;
        errorOutputStream = null;
    }

    public boolean isExecuteStarted() {
        return this.executeStarted;
    }

    public File getWorkingDirectory() {
        return this.workingDirectory;
    }

    public String getDescription() {
        return this.description;
    }

    protected AbstractCommandLineJob setExecuteStarted(boolean executeStarted) {
        this.executeStarted = executeStarted;
        return this;
    }

    protected AbstractCommandLineJob setCommandLineAsString(String commandLineAsString) {
        this.commandLineAsString = commandLineAsString;
        return this;
    }

    public AbstractCommandLineJob setWorkingDirectory(File workingDirectory) {
        this.workingDirectory = workingDirectory;
        return this;
    }

    public AbstractCommandLineJob setDescription(String description) {
        this.description = description;
        return this;
    }
}
