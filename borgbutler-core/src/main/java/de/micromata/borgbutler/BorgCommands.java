package de.micromata.borgbutler;

import de.micromata.borgbutler.config.BorgRepoConfig;
import de.micromata.borgbutler.data.Archive;
import de.micromata.borgbutler.data.Repository;
import de.micromata.borgbutler.demo.DemoRepos;
import de.micromata.borgbutler.jobs.JobResult;
import de.micromata.borgbutler.json.JsonUtils;
import de.micromata.borgbutler.json.borg.*;
import de.micromata.borgbutler.utils.DateUtils;
import de.micromata.borgbutler.utils.ReplaceUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates and executes  borg commands by calling system's borg application (Borg must be installed).
 */
public class BorgCommands {
    private static Logger log = LoggerFactory.getLogger(BorgCommands.class);

    /**
     * Executes borg --version
     *
     * @return version string.
     */
    public static String version() {
        BorgCommand command = new BorgCommand()
                .setParams("--version")
                .setDescription("Getting borg version.");

        BorgJob<String> job = new BorgJob<>(command);
        JobResult<String> jobResult = job.execute();
        if (jobResult == null || jobResult.getStatus() != JobResult.Status.OK) {
            return null;
        }
        String origVersion = jobResult.getResultObject();
        String version = origVersion;
        String[] strs = StringUtils.split(origVersion);
        if (strs != null) {
            if (!StringUtils.containsIgnoreCase(origVersion, "borg")) {
                version = "";
            } else {
                version = strs[strs.length - 1]; // Work arround: borg returns "borg-macosx64 1.1.8" as version string (command is included).
            }
        }
        if (version.length() == 0 || !Character.isDigit(version.charAt(0))) {
            log.error("Version string returned by '" + job.getCommandLineAsString() + "' not as expected (not borg?): " + origVersion);
            return null;
        }
        log.info("Borg version: " + version);
        return version;
    }

    /**
     * Executes borg init repository.
     *
     * @param repoConfig The configuration of the repo config (only repo is required).
     * @param encryption The encryption value (repokey, repokey-blake2, none, ...).
     * @return true, if no errors occured, otherwise false.
     */
    public static boolean init(BorgRepoConfig repoConfig, String encryption) {
        BorgCommand command = new BorgCommand()
                .setRepoConfig(repoConfig)
                .setCommand("init")
                //.setParams("--json") // --progress has no effect.
                .setDescription("Init new repo '" + repoConfig.getDisplayName() + "'.");
        JobResult<String> jobResult = getResult(command);
        String result = jobResult != null ? jobResult.getResultObject() : null;
        // If everything is ok, now String will be returned, result must be blank:
        if (jobResult == null || jobResult.getStatus() != JobResult.Status.OK || StringUtils.isNotBlank(result)) {
            log.error("Error while trying to intialize repo '" + repoConfig.getRepo() + "': " + result);
            return false;
        }
        log.error("Error while trying to intialize repo '" + repoConfig.getRepo() + "': " + result);
        return false;
    }


    /**
     * Executes borg info repository.
     *
     * @param repoConfig
     * @return Parsed repo config returned by Borg command (without archives).
     */
    public static BorgCommandResult<Repository> info(BorgRepoConfig repoConfig) {
        BorgCommand command = new BorgCommand()
                .setRepoConfig(repoConfig)
                .setCommand("info")
                .setParams("--json") // --progress has no effect.
                .setDescription("Loading info of repo '" + repoConfig.getDisplayName() + "'.");
        BorgCommandResult<Repository> result = new BorgCommandResult<>();
        getResult(result, command);
        if (result.getStatus() != JobResult.Status.OK) {
            return result;
        }
        String resultJson = result.getJobResult().getResultObject();
        BorgRepoInfo repoInfo = JsonUtils.fromJson(BorgRepoInfo.class, resultJson);
        BorgRepository borgRepository = repoInfo.getRepository();
        Repository repository = new Repository();
        repository.setId(borgRepository.getId());
        repository.setName(repoConfig.getRepo());
        repository.setDisplayName(repoConfig.getDisplayName());
        repository.setLastModified(DateUtils.format(borgRepository.getLastModified()));
        repository.setLocation(borgRepository.getLocation());
        repository.setCache(repoInfo.getCache());
        repository.setEncryption(repoInfo.getEncryption());
        repository.setSecurityDir(repoInfo.getSecurityDir());
        repository.setLastCacheRefresh(DateUtils.format(LocalDateTime.now()));
        DemoRepos.repoWasRead(repoConfig, repository);
        return result.setObject(repository);
    }

    /**
     * Executes borg list repository.
     * The given repository will be used and archives will be added.
     *
     * @param repoConfig The repo config associated to the masterRepository. Needed for the borg call.
     * @param repository Repository without archives, archives will be loaded.
     */
    public static void list(BorgRepoConfig repoConfig, Repository repository) {
        BorgCommand command = new BorgCommand()
                .setRepoConfig(repoConfig)
                .setCommand("list")
                .setParams("--json") // --progress has no effect.
                .setDescription("Loading list of archives of repo '" + repoConfig.getDisplayName() + "'.");
        JobResult<String> jobResult = getResult(command);
        if (jobResult == null || jobResult.getStatus() != JobResult.Status.OK) {
            log.error("Can't load archives from repo '" + repository.getName() + "'.");
            return;
        }
        String result = jobResult.getResultObject();
        BorgRepoList repoList = JsonUtils.fromJson(BorgRepoList.class, result);
        if (repoList == null || CollectionUtils.isEmpty(repoList.getArchives())) {
            log.error("Can't load archives from repo '" + repository.getName() + "'.");
            return;
        }
        repository.setLastModified(DateUtils.format(repoList.getRepository().getLastModified()));
        repository.setLastCacheRefresh(DateUtils.format(LocalDateTime.now()));
        for (BorgArchive borgArchive : repoList.getArchives()) {
            Archive archive = new Archive()
                    .setName(borgArchive.getArchive())
                    .setId(borgArchive.getId())
                    .setStart(DateUtils.format(borgArchive.getStart()))
                    .setTime(DateUtils.format(borgArchive.getTime()))
                    .setRepoId(repository.getId())
                    .setRepoName(repository.getName())
                    .setRepoDisplayName(repoConfig.getDisplayName());
            repository.add(archive);
        }
    }

    /**
     * Executes borg info repository::archive.
     * The given repository will be modified.
     * The field {@link Repository#getLastModified()} of masterRepository will be updated.
     *
     * @param repoConfig The repo config associated to the repository. Needed for the borg call.
     * @param archive    The archive to update.
     * @param repository Repository without archives.
     */
    public static void info(BorgRepoConfig repoConfig, Archive archive, Repository repository) {
        BorgCommand command = new BorgCommand()
                .setRepoConfig(repoConfig)
                .setCommand("info")
                .setArchive(archive.getName())
                .setParams("--json", "--log-json", "--progress")
                .setDescription("Loading info of archive '" + archive.getName() + "' of repo '" + repoConfig.getDisplayName() + "'.");
        JobResult<String> jobResult = getResult(command);
        if (jobResult == null || jobResult.getStatus() != JobResult.Status.OK) {
            return;
        }
        String result = jobResult.getResultObject();
        BorgArchiveInfo archiveInfo = JsonUtils.fromJson(BorgArchiveInfo.class, result);
        if (archiveInfo == null) {
            log.error("Archive '" + command.getRepoArchive() + "' not found.");
            return;
        }
        repository.setLastModified(DateUtils.format(archiveInfo.getRepository().getLastModified()));
        repository.setLastCacheRefresh(DateUtils.format(LocalDateTime.now()));
        archive.setCache(archiveInfo.getCache())
                .setEncryption(archiveInfo.getEncryption());
        if (CollectionUtils.isEmpty(archiveInfo.getArchives())) {
            log.error("The returned borg archive contains no archive infos: " + command.getAbbreviatedResponse());
            return;
        }
        if (archiveInfo.getArchives().size() > 1) {
            log.warn("Archive '" + command.getRepoArchive() + "' contains more than one archives!? (Using only first.)");
        }
        BorgArchive2 borgArchive = archiveInfo.getArchives().get(0);
        archive.setStart(DateUtils.format(borgArchive.getStart()))
                .setChunkerParams(borgArchive.getChunkerParams())
                .setCommandLine(borgArchive.getCommandLine())
                .setComment(borgArchive.getComment())
                .setStats(borgArchive.getStats())
                .setLimits(borgArchive.getLimits())
                .setHostname(borgArchive.getHostname())
                .setUsername(borgArchive.getUsername())
                .setEnd(DateUtils.format(borgArchive.getEnd()))
                .setDuration(borgArchive.getDuration());
    }

    public static List<BorgFilesystemItem> listArchiveContent(BorgRepoConfig repoConfig, Archive archive) {
        BorgCommand command = new BorgCommand()
                .setRepoConfig(repoConfig)
                .setCommand("list")
                .setArchive(archive.getName())
                .setParams("--json-lines")
                .setDescription("Loading list of files of archive '" + archive.getName() + "' of repo '" + repoConfig.getDisplayName() + "'.");
        // The returned job might be an already queued or running one!
        final ProgressInfo progressInfo = new ProgressInfo()
                .setMessage("Getting file list...")
                .setCurrent(0);
        if (archive.getStats() != null) // Occurs only for demo repos.
            progressInfo.setTotal(archive.getStats().getNfiles());
        BorgJob<List<BorgFilesystemItem>> job = BorgQueueExecutor.getInstance().execute(new BorgJob<List<BorgFilesystemItem>>(command) {
            @Override
            public void processStdOutLine(String line, int level) {
                BorgFilesystemItem item = JsonUtils.fromJson(BorgFilesystemItem.class, line);
                item.setMtime(DateUtils.format(item.getMtime()));
                payload.add(item);
                if ("-".equals(item.getType())) {
                    // Only increment for files, because number of files is the total.
                    setProgressInfo(progressInfo.incrementCurrent());
                }
            }
        });
        job.payload = new ArrayList<>();
        JobResult<String> jobResult = job.getResult();
        if (jobResult == null || jobResult.getStatus() != JobResult.Status.OK) {
            return null;
        }
        List<BorgFilesystemItem> items = job.payload;
        job.cleanUp(); // payload will be released.
        return items;
    }

    /**
     * Stores the file in a subdirectory named with the repos display name.
     *
     * @param restoreHomeDir
     * @param repoConfig
     * @param archive
     * @param path
     * @return Used sub directory with the restored content.
     * @throws IOException
     */
    public static File extractFiles(File restoreHomeDir, BorgRepoConfig repoConfig, Archive archive, String path) throws IOException {
        File restoreDir = new File(restoreHomeDir, ReplaceUtils.encodeFilename(repoConfig.getDisplayName(), true));
        if (!restoreDir.exists()) {
            restoreDir.mkdirs();
        }
        BorgCommand command = new BorgCommand()
                .setWorkingDir(restoreDir)
                .setRepoConfig(repoConfig)
                .setCommand("extract")
                .setParams("--log-json", "--progress")
                .setArchive(archive.getName())
                .setArgs(path)
                .setDescription("Extract content of archive '" + archive.getName()
                        + "' of repo '" + repoConfig.getDisplayName() + "': "
                        + path);
        JobResult<String> jobResult = getResult(command);
        return restoreDir;
    }

    private static void getResult(BorgCommandResult<?> result, BorgCommand command) {
        BorgJob<Void> job = execute(command);
        JobResult<String> jobResult = job.getResult();
        result.setJobResult(jobResult);
        if (jobResult == null || jobResult.getStatus() == JobResult.Status.ERROR) {
            jobResult.setErrorString(job.getErrorString(2000));
        }
        job.cleanUp();
    }

    private static JobResult<String> getResult(BorgCommand command) {
        BorgJob<Void> job = execute(command);
        JobResult<String> jobResult = job.getResult();
        job.cleanUp();
        return jobResult;
    }

    private static BorgJob<Void> execute(BorgCommand command) {
        Validate.notNull(command);
        return BorgQueueExecutor.getInstance().execute(command);
    }
}
