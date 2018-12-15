package de.micromata.borgbutler.cache;

import de.micromata.borgbutler.BorgCommands;
import de.micromata.borgbutler.config.BorgRepoConfig;
import de.micromata.borgbutler.config.Configuration;
import de.micromata.borgbutler.config.ConfigurationHandler;
import de.micromata.borgbutler.data.Archive;
import de.micromata.borgbutler.data.Repository;
import de.micromata.borgbutler.json.borg.BorgFilesystemItem;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.jcs.JCS;
import org.apache.commons.jcs.access.CacheAccess;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ButlerCache {
    private static Logger log = LoggerFactory.getLogger(ButlerCache.class);
    public static final String CACHE_DIR_NAME = "cache";
    private static ButlerCache instance = new ButlerCache();

    private JCSCache jcsCache;
    private CacheAccess<String, Repository> repoCacheAccess;
    private ArchiveFilelistCache archiveFilelistCache;

    public static ButlerCache getInstance() {
        return instance;
    }

    /**
     * @param idOrName
     * @return Repository.
     */
    public Repository getRepository(String idOrName) {
        BorgRepoConfig repoConfig = ConfigurationHandler.getConfiguration().getRepoConfig(idOrName);
        if (repoConfig != null) {
            return getRepository(repoConfig);
        }
        List<Repository> repositories = getAllRepositories();
        if (CollectionUtils.isNotEmpty(repositories)) {
            for (Repository repository : repositories) {
                if (StringUtils.equals(idOrName, repository.getName()) || StringUtils.equals(idOrName, repository.getId())) {
                    return repository;
                }
            }
        }
        log.warn("Repo with id or name '" + idOrName + "' not found.");
        return null;
    }

    /**
     * @param repoConfig
     * @return Repository.
     */
    private Repository getRepository(BorgRepoConfig repoConfig) {
        Repository repository = null;
        try {
            repository = repoCacheAccess.get(repoConfig.getRepo());
        } catch (Exception ex) {
            log.warn("Error while deserializing repository (maybe data format was changed). Reloading...");
        }
        if (repository == null || repository.getLocation() == null) {
            repository = BorgCommands.info(repoConfig);
            repoCacheAccess.put(repoConfig.getRepo(), repository);
        }
        if (repository == null) {
            log.warn("Repo with name '" + repoConfig.getRepo() + "' not found.");
        } else {
            repoConfig.setId(repository.getId());
        }
        return repository;
    }

    /**
     * @return the list of all repositories.
     */
    public List<Repository> getAllRepositories() {
        List<Repository> repositories = new ArrayList<>();
        for (BorgRepoConfig repoConfig : ConfigurationHandler.getConfiguration().getRepoConfigs()) {
            Repository repository = getRepository(repoConfig);
            if (repository == null) {
                continue;
            }
            repositories.add(repository);
        }
        return repositories;
    }

    public void clearAllCaches() {
        clearRepoCacheAccess();
        log.info("Clearing cache with file lists of archives...");
        this.archiveFilelistCache.removeAllCacheFiles();
    }

    public void clearRepoCacheAccess() {
        log.info("Clearing repositories cache...");
        this.repoCacheAccess.clear();
    }

    /**
     * @param idOrName
     * @return The repository (ensures that the list of archives is loaded).
     */
    public Repository getRepositoryArchives(String idOrName) {
        Repository repository = getRepository(idOrName);
        if (repository == null) {
            return null;
        }
        if (repository.isArchivesLoaded()) {
            return repository;
        }
        BorgRepoConfig repoConfig = ConfigurationHandler.getConfiguration().getRepoConfig(repository.getName());
        BorgCommands.list(repoConfig, repository);
        return repository;
    }

    public Archive getArchive(String repoName, String archiveIdOrName) {
        return getArchive(repoName, archiveIdOrName, false);
    }

    /**
     * @param repoIdOrName
     * @param archiveIdOrName
     * @param forceReload     If true, any cache value will be ignored. Default is false.
     * @return
     */
    public Archive getArchive(String repoIdOrName, String archiveIdOrName, boolean forceReload) {
        BorgRepoConfig repoConfig = ConfigurationHandler.getConfiguration().getRepoConfig(repoIdOrName);
        if (repoConfig == null) {
            log.error("Can't find configured repo '" + repoIdOrName + "'.");
            return null;
        }
        return getArchive(repoConfig, archiveIdOrName, forceReload);
    }

    public Archive getArchive(BorgRepoConfig repoConfig, String archiveIdOrName, boolean forceReload) {
        Repository repository = getRepositoryArchives(repoConfig.getRepo());
        if (repository == null) {
            log.error("Repository '" + repoConfig.getRepo() + "' not found.");
            return null;
        }
        Archive archive = null;
        if (CollectionUtils.isEmpty(repository.getArchives())) {
            log.warn("Repository '" + repoConfig.getRepo() + "' doesn't contain archives.");
        } else {
            for (Archive arch : repository.getArchives()) {
                if (StringUtils.equals(arch.getName(), archiveIdOrName)
                        || StringUtils.equals(arch.getId(), archiveIdOrName)) {
                    archive = arch;
                    break;
                }
            }
        }
        if (archive == null) {
            log.error("Archive with id or name '" + archiveIdOrName + "' not found for repo '" + repoConfig.getRepo()
                    + "'.");
            return null;
        }
        if (!forceReload && archive.hasInfoData()) {
            // borg info archive was already called.
            return archive;
        }
        BorgCommands.info(repoConfig, archive, repository);
        return archive;
    }

    public BorgFilesystemItem[] getArchiveContent(BorgRepoConfig repoConfig, Archive archive) {
        if (archive == null || StringUtils.isBlank(archive.getName())) {
            return null;
        }
        BorgFilesystemItem[] items = archiveFilelistCache.load(repoConfig, archive);
        if (items == null) {
            List<BorgFilesystemItem> list = BorgCommands.listArchiveContent(repoConfig, archive.getName());
            if (CollectionUtils.isNotEmpty(list)) {
                archiveFilelistCache.save(repoConfig, archive, list);
                items = list.toArray(new BorgFilesystemItem[0]);
            }
        }
        if (items == null) {
            log.warn("Repo::archiv with name '" + archive.getBorgIdentifier() + "' not found.");
        }
        return items;
    }

    public BorgFilesystemItem[] getArchiveContent(File file) {
        return archiveFilelistCache.load(file);
    }

    public void shutdown() {
        JCS.shutdown();
    }

    public File getCacheDir() {
        return jcsCache.getCacheDir();
    }

    private ButlerCache() {
        Configuration configuration = ConfigurationHandler.getConfiguration();
        this.jcsCache = JCSCache.getInstance();
        this.repoCacheAccess = jcsCache.getJCSCache("repositories");
        this.archiveFilelistCache = new ArchiveFilelistCache(getCacheDir(), configuration.getCacheArchiveContentMaxDiscSizeMB());
    }
}
