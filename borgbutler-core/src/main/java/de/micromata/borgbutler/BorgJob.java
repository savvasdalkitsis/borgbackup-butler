package de.micromata.borgbutler;

import de.micromata.borgbutler.config.BorgRepoConfig;
import de.micromata.borgbutler.config.ConfigurationHandler;
import de.micromata.borgbutler.data.Archive;
import de.micromata.borgbutler.demo.DemoRepos;
import de.micromata.borgbutler.jobs.AbstractCommandLineJob;
import de.micromata.borgbutler.jobs.JobResult;
import de.micromata.borgbutler.json.JsonUtils;
import de.micromata.borgbutler.json.borg.ProgressInfo;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.environment.EnvironmentUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * A queue is important because Borg doesn't support parallel calls for one repository.
 * For each repository one single queue is allocated.
 */
public class BorgJob<T> extends AbstractCommandLineJob implements Cloneable {
    private Logger log = LoggerFactory.getLogger(BorgJob.class);
    private BorgCommand command;
    /**
     * Some jobs may store here the result of the command (e. g. {@link BorgCommands#listArchiveContent(BorgRepoConfig, Archive)}).
     */
    protected T payload;

    private ProgressInfo progressInfo;

    public BorgJob(BorgCommand command) {
        this.command = command;
        setWorkingDirectory(command.getWorkingDir());
        setDescription(command.getDescription());
    }

    private BorgJob() {
    }

    @Override
    protected CommandLine buildCommandLine() {
        if (command == null) {
            return null;
        }
        String borgCommand = ConfigurationHandler.getConfiguration().getBorgCommand();
        if (StringUtils.isBlank(borgCommand)) {
            log.error("Can't run empty borg command.");
            return null;
        }
        CommandLine commandLine = new CommandLine(borgCommand);
        commandLine.addArgument(command.getCommand());
        if (command.getParams() != null) {
            for (String param : command.getParams()) {
                if (param != null)
                    commandLine.addArgument(param);
            }
        }
        if (command.getRepoArchive() != null) {
            commandLine.addArgument(command.getRepoArchive());
        }
        if (command.getArgs() != null) {
            for (String arg : command.getArgs()) {
                if (arg != null)
                    commandLine.addArgument(arg);
            }
        }
        return commandLine;
    }

    public void processStdErrLine(String line, int level) {
        if (StringUtils.startsWith(line, "{\"message")) {
            ProgressInfo message = JsonUtils.fromJson(ProgressInfo.class, line);
            if (message != null) {
                progressInfo = message;
                return;
            }
        }
        super.processStdErrLine(line, level);
    }

    @Override
    protected Map<String, String> getEnvironment() throws IOException {
        BorgRepoConfig repoConfig = command.getRepoConfig();
        if (repoConfig == null) {
            return null;
        }
        Map<String, String> env = EnvironmentUtils.getProcEnvironment();
        String[] variables = repoConfig.getEnvironmentVariables(true);
        for (String variable : variables) {
            // For MacOS BORG_PASSCOMMAND="security find-generic-password -a $USER -s borg-passphrase -w"
            String environmentVariable = variable.replace("$USER", System.getProperty("user.name"));
            addEnvironmentVariable(env, environmentVariable);
        }
        return env;
    }

    @Override
    public JobResult<String> execute() {
        if (command.getRepoConfig() != null && DemoRepos.isDemo(command.getRepoConfig().getRepo())) {
            return DemoRepos.execute(this);
        }
        return super.execute();
    }

    @Override
    public BorgJob<?> clone() {
        BorgJob<?> clone = new BorgJob<>();
        if (command != null) {
            // Needed for getting environment variables: JsonJob of borgbutler-server.
            clone.command = new BorgCommand().setRepoConfig(command.getRepoConfig());
        }
        clone.setUniqueJobNumber(getUniqueJobNumber());
        clone.setTitle(getTitle());
        clone.setExecuteStarted(isExecuteStarted());
        clone.setCommandLineAsString(getCommandLineAsString());
        clone.setCancellationRequested(isCancellationRequested());
        clone.setStatus(getStatus());
        clone.setWorkingDirectory(getWorkingDirectory());
        clone.setDescription(getDescription());
        if (progressInfo != null) {
            clone.setProgressInfo(progressInfo.clone());
        }
        clone.setCreateTime(getCreateTime());
        clone.setStartTime(getStartTime());
        clone.setStopTime(getStopTime());
        return clone;
    }

    @Override
    public void cleanUp() {
        super.cleanUp();
        payload = null;
    }

    public BorgCommand getCommand() {
        return this.command;
    }

    public T getPayload() {
        return this.payload;
    }

    public ProgressInfo getProgressInfo() {
        return this.progressInfo;
    }

    protected BorgJob<T> setProgressInfo(ProgressInfo progressInfo) {
        this.progressInfo = progressInfo;
        return this;
    }
}
