package de.micromata.borgbutler.server.rest;

import de.micromata.borgbutler.cache.ButlerCache;
import de.micromata.borgbutler.data.Archive;
import de.micromata.borgbutler.data.Repository;
import de.micromata.borgbutler.json.JsonUtils;
import de.micromata.borgbutler.json.borg.BorgRepository;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/repos")
public class ReposRest {
    private static Logger log = LoggerFactory.getLogger(ReposRest.class);

    @GET
    @Path("repo")
    @Produces(MediaType.APPLICATION_JSON)
    /**
     *
     * @param id id or name of repo.
     * @param force If true, a reload of all repositories is forced.
     * @param prettyPrinter If true then the json output will be in pretty format.
     * @return Repository (without list of archives) as json string.
     * @see JsonUtils#toJson(Object, boolean)
     */
    public String getRepo(@QueryParam("id") String id, @QueryParam("prettyPrinter") boolean prettyPrinter) {
        Repository repository = ButlerCache.getInstance().getRepository(id);
        return JsonUtils.toJson(repository, prettyPrinter);
    }

    @GET
    @Path("repoArchiveList")
    @Produces(MediaType.APPLICATION_JSON)
    /**
     *
     * @param id id or name of repo.
     * @param prettyPrinter If true then the json output will be in pretty format.
     * @return Repository (including list of archives) as json string.
     * @see JsonUtils#toJson(Object, boolean)
     */
    public String getRepoArchiveList(@QueryParam("id") String id, @QueryParam("force") boolean force,
                                     @QueryParam("prettyPrinter") boolean prettyPrinter) {
        if (force) {
            ButlerCache.getInstance().clearRepoArchicesCacheAccess();
        }
        Repository repository = ButlerCache.getInstance().getRepositoryArchives(id);
        return JsonUtils.toJson(repository, prettyPrinter);
    }

    @GET
    @Path("archive")
    @Produces(MediaType.APPLICATION_JSON)
    /**
     *
     * @param repo Name of repository ({@link Repository#getName()}.
     * @param archive Id or name of archive.
     * @param prettyPrinter If true then the json output will be in pretty format.
     * @return Repository (including list of archives) as json string.
     * @see JsonUtils#toJson(Object, boolean)
     */
    public String getArchive(@QueryParam("repo") String repoName,
                             @QueryParam("archive") String archiveIdOrName, @QueryParam("force") boolean force,
                             @QueryParam("prettyPrinter") boolean prettyPrinter) {
        Archive archive = ButlerCache.getInstance().getArchive(repoName, archiveIdOrName, force);
        return JsonUtils.toJson(archive, prettyPrinter);
    }

    @GET
    @Path("list")
    @Produces(MediaType.APPLICATION_JSON)
    /**
     *
     * @param force If true, a reload of all repositories is forced.
     * @param prettyPrinter If true then the json output will be in pretty format.
     * @return A list of repositories of type {@link BorgRepository}.
     * @see JsonUtils#toJson(Object, boolean)
     */
    public String getList(@QueryParam("force") boolean force, @QueryParam("prettyPrinter") boolean prettyPrinter) {
        if (force) {
            ButlerCache.getInstance().clearRepoCacheAccess();
            ButlerCache.getInstance().clearRepoArchicesCacheAccess();
        }
        List<Repository> repositories = ButlerCache.getInstance().getAllRepositories();
        if (CollectionUtils.isEmpty(repositories)) {
            return "";
        }
        return JsonUtils.toJson(repositories, prettyPrinter);
    }
}
