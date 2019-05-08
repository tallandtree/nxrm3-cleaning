package cm.nexus.cleanup

import cm.nexus.QueryBuilder
import org.joda.time.DateTime
import org.slf4j.Logger
import org.sonatype.nexus.repository.Repository
import org.sonatype.nexus.repository.storage.Asset
import org.sonatype.nexus.repository.storage.Component
import org.sonatype.nexus.repository.storage.StorageFacet
// cleaning groovy class for the following repository types:
// maven2-proxy, maven2-hosted
// docker-hosted, docker-proxy
// nuget-hosted, nuget-proxy
// npm-hosted, npm-proxy
// Use filter1 and filter2 to include or exclude patterns from the cleaning
//
class DeleteAssets {
    private String filter1
    private String filter2
    private String[] repositoryNames
    private final repoManager
    private final Logger log
    private final Config config

    DeleteAssets(Logger log, repo, String filter1, String filter2, String[] repositoryNames, Config config) {
        this.config = config
        this.filter1 = filter1 ?: ".*"
        this.filter2 = filter2 ?: ".*"
        this.repositoryNames = repositoryNames ?: RecipeFilter.supported(repo.repositoryManager.browse() as Iterable<Repository>).collect({ r -> r.name})
        this.repoManager = repo
        this.log = log
    }

    void dryRun() {
        clean(false)
    }

    void clean(deleteComponents = true) {
        repositoryNames.each { repoName ->
            def retainDays = config.getShortestRetainPeriodForRepo(repoName)
            Repository repo = repoManager.repositoryManager.get(repoName)
            log.info($/Cleaning repository: ${repoName}, recipe: ${repo.configuration.recipeName}, 
                last_downloaded < ${DateTime.now().minusDays(retainDays).toString(QueryBuilder.fmt)}/$)

            if (!RecipeFilter.isSupported(repo.configuration.recipeName)) {
                log.warn($/Cleaning of this kind of recipe is not supported./$)
                return
            }

            // Get a database transaction
            def tx = repo.facet(StorageFacet).txSupplier().get()
            try {
                tx.begin()

                for (Asset asset in tx.findAssets(QueryBuilder.findAssetsQuery(retainDays, filter1, filter2), [repo])) {
                    log.debug("Asset found: ${asset.name()}, last downloaded: ${asset.lastDownloaded()}, last updated: ${asset.blobUpdated()}")

                    if (asset.componentId() != null) {
                        Component component = tx.findComponent(asset.componentId())
                        // if component not found, the asset and component was already deleted when processing a previously found asset
                        if (component != null) {
                            def assetConfigPair = config.getConfigForAsset(repoName, component.name(), component.version())
                            // Check if there are newer components of the same name. Newer means components updated after the current asset was last updated.
                            def newerComponentVersions = tx.countComponents(QueryBuilder.buildNewerComponentCountQuery(component, asset, assetConfigPair.key), [repo])
                            log.debug("\n\tAsset:  ${asset.name()}, lastDownloaded: ${asset.lastDownloaded()}, updated: ${asset.blobUpdated()}\n"+
                                    "\tcomponent: ${component.name()}, ${component.version()}, updated: ${component.lastUpdated()}\n"+
                                    "\tcount: ${newerComponentVersions}, max versions: ${assetConfigPair.config.get(Config.MAX_VERSIONS)} matchingKey: ${assetConfigPair.key}, ")
                            if (isNoLongerNeeded(assetConfigPair.config, asset, newerComponentVersions)) {
                                log.info($/Delete component ${component.name()}, version ${component.version()} as it has not been downloaded since ${Config.getRetainPeriodInDays(assetConfigPair.config)} days and has a newer version/$)
                                if (deleteComponents) {
                                    // this also deletes the other assets within this component
                                    tx.deleteComponent(component)
                                }
                            }
                        }
                    } else {
                        log.info($/Asset ${asset.name()} not deleted although not downloaded for more than ${retainDays} days./$)
                    }

                }

                tx.commit()

            }
            catch (Exception e) {
                log.warn("Cleanup failed!!!")
                log.warn("Exception details: {}", e.toString())
                log.warn("Rolling back storage transaction")
                e.printStackTrace()
                tx.rollback()
            } finally {
                tx.close()
            }
        }
    }

    private boolean isNoLongerNeeded(Map assetConfig, Asset asset, long newerVersions) {
        if (assetConfig.get(Config.KEEP) == "forever") {
            log.info($/Asset is configured not to be cleaned/$)
            return false
        }
        if (newerVersions < (assetConfig.get(Config.MAX_VERSIONS) as int)) {
            return false
        }
        def minimumToRetainDate = DateTime.now().minusDays(Config.getRetainPeriodInDays(assetConfig)).withTimeAtStartOfDay()
        log.debug($/Asset retainDays: ${Config.getRetainPeriodInDays(assetConfig)}/$)
        if (asset.lastDownloaded() == null) {
            return minimumToRetainDate > asset.blobUpdated()
        }
        else {
            return minimumToRetainDate > asset.lastDownloaded().withTimeAtStartOfDay()
        }
    }
}

/* Examples nexus task: */
//import groovy.lang.GroovyClassLoader
//def gcl = this.class.classLoader
//gcl.clearCache()
//gcl.addClasspath("/opt/sonatype/sonatype-work/nexus3/cm/scripts/groovy")
//def configReaderClass = gcl.loadClass("cm.nexus.cleanup.ConfigReader")
//def deleteAssetsClass = gcl.loadClass("cm.nexus.cleanup.DeleteAssets")
//def config = configReaderClass.readConfig(log, "/opt/sonatype/sonatype-work/nexus3/cm/scripts/resources")
//def cleaner =  deleteAssetsClass.newInstance(log, repository, "com/.*", ".*", (String[]) ["my-mvn-repo"], config)
//cleaner.dryRun()

/* Filter examples */
//def filter1 = ".*"
//def filter2 = "^org/myothercompany/app/.*"
//def filter2 = "^com/.*/app/.*"
// Use "(?!filteroutstring)/.*" to filter out assets not to be cleaned.
//def filter2 = "(?!com/myothercompany/app/).*"
//def filter2 = ".*"
//def cleaner =  deleteAssetsClass.newInstance(log, repository, "com/.*", "(?!com/nl/host/myapp).*", (String[]) ["my-mvn-repo"], config)