package cm.nexus.cleanup


import com.google.common.base.Supplier
import org.joda.time.DateTime
import org.slf4j.Logger
import org.sonatype.nexus.common.entity.EntityId
import org.sonatype.nexus.repository.Repository
import org.sonatype.nexus.repository.config.Configuration
import org.sonatype.nexus.repository.manager.RepositoryManager
import org.sonatype.nexus.repository.storage.*
import spock.lang.Specification

class DeleteAssetsTest extends Specification {

    private static final String REPO_NAME = "repoName"
    private static final String REPO_TWO_NAME = "repoTwoName"
    private static final String REPO_THREE_NAME = "repoThreeName"
    private static final String COMPONENT_NAME = "componentName"
    private static final String COMPONENT_VERSION = "componentVersion"

    private RepositoryManager repoManager
    private Repository repository
    private Configuration repoConfig
    private TestRepositoryApi repo
    private Config cleanupConfig
    private StorageFacet storageFacet
    private StorageTx transaction
    private Asset asset
    private Logger log

    private boolean isValidRecipe = true

    private DeleteAssets sut

    def setup() {
        def componentId = Mock(EntityId)
        def component = Mock(Component)
        component.name() >> COMPONENT_NAME
        component.group() >> true
        component.lastUpdated() >> DateTime.now()
        component.version() >> COMPONENT_VERSION

        asset = Mock(Asset)
        asset.lastDownloaded() >> DateTime.now().minusDays(50)
        asset.blobUpdated() >> DateTime.now()
        asset.componentId() >> componentId

        transaction = Mock(StorageTx)
        transaction.findAssets(_ as Query, _ as List<Repository>) >> Arrays.asList(asset)
        transaction.findComponent(componentId) >> component

        def txSupplier = Mock(Supplier)
        txSupplier.get() >> transaction
        storageFacet = Mock(StorageFacet)
        storageFacet.txSupplier() >> txSupplier
        repoConfig = Mock(Configuration)
        repoConfig.getRecipeName() >> {isValidRecipe ? "docker-hosted" : "unsupported"}
        repository = Mock(Repository)
        repository.facet(StorageFacet) >> storageFacet
        repository.configuration >> repoConfig
        repoManager = Mock(RepositoryManager)
        repoManager.get(REPO_NAME) >> repository
        repo = Mock(TestRepositoryApi)
        repo.repositoryManager >> repoManager
        log = Mock(Logger)
        cleanupConfig =
                new Config(log,[default: [max_versions: 10, last_downloaded: [amount: 30, unit: 'day']], (REPO_NAME): [last_downloaded: [amount: 40, unit: 'day']]])
        sut = new DeleteAssets(log, repo, null, null, REPO_NAME.split(), cleanupConfig)
    }

    def "omitting repositoryNames means that all repo names are retrieved from the manager"() {
        given:
            repository.name >> REPO_NAME
            def repo2 = Mock(Repository)
            repo2.name >> REPO_TWO_NAME
            repo2.configuration >> repoConfig
            repo2.facet(StorageFacet) >> storageFacet
            def repo3 = Mock(Repository)
            repo3.name >> REPO_THREE_NAME
            repo3.configuration >> repoConfig
            repo3.facet(StorageFacet) >> storageFacet
            repoManager.browse() >> [repository, repo2, repo3]
            sut = new DeleteAssets(log, repo, null, null, null, cleanupConfig)

        when:
            sut.clean()

        then:
            1 * repoManager.get(REPO_NAME) >> repository
            1 * repoManager.get(REPO_TWO_NAME) >> repo2
            1 * repoManager.get(REPO_THREE_NAME) >> repo3
        true
    }

    def "only specific repository formats and types combinations can be cleaned"() {
        given:
            isValidRecipe = false

        when:
            sut.clean()

        then:
            0 * repository.facet(StorageFacet)
    }

    def "no available assets does not throw an exception"() {
        given:

        when:
            sut.clean()

        then:
            0 * asset.componentId()
            1 * transaction.begin()
            1 * transaction.findAssets(_ as Query, _ as List<Repository>) >> Arrays.asList()
            0 * transaction.findComponent(_ as EntityId)
            1 * transaction.commit()
            1 * transaction.close()
            0 * transaction.deleteComponent(_ as Component)
            0 * transaction.rollback()
    }

    def "asset without a component is not deleted"() {
        given:

        when:
            sut.clean()

        then:
            1 * asset.componentId() >> null
            1 * transaction.begin()
            0 * transaction.findComponent(_ as EntityId)
            1 * transaction.commit()
            1 * transaction.close()
            0 * transaction.deleteComponent(_ as Component)
            0 * transaction.rollback()
    }

    def "asset with a component that is not found has already been deleted"() {
        given:

        when:
            sut.clean()

        then:
            1 * transaction.begin()
            1 * transaction.findComponent(_ as EntityId) >> null
            0 * transaction.countComponents(_ as Query, _ as List<Repository>)
            1 * transaction.commit()
            1 * transaction.close()
            0 * transaction.deleteComponent(_ as Component)
            0 * transaction.rollback()
    }

    def "asset with a component that is present is not deleted if the maximum number of versions is not exceeded"() {
        given:

        when:
            sut.clean()

        then:
            1 * transaction.begin()
            0 * transaction.findComponent()
            1 * transaction.countComponents(_ as Query, _ as List<Repository>) >> 9
            1 * transaction.commit()
            1 * transaction.close()
            0 * transaction.deleteComponent(_ as Component)
            0 * transaction.rollback()
    }

    def "asset with a component that is present is deleted if the maximum number of versions is exceeded"() {
        given:

        when:
            sut.clean()

        then:
            1 * transaction.begin()
            0 * transaction.findComponent()
            1 * transaction.countComponents(_ as Query, _ as List<Repository>) >> 10
            1 * transaction.commit()
            1 * transaction.close()
            1 * transaction.deleteComponent(_ as Component)
            0 * transaction.rollback()
    }

    def "asset is not deleted if the maximum number of versions is exceeded but it has been downloaded within the retention period"() {
        given:

        when:
            sut.clean()

        then:
            (1.._) * asset.lastDownloaded() >> DateTime.now().minusDays(40)
            1 * transaction.countComponents(_ as Query, _ as List<Repository>) >> 10
            0 * transaction.deleteComponent(_ as Component)
            0 * transaction.rollback()
    }

    def "asset is not deleted when marked to be kept forever"() {
        given:
            def cleanupConfig =
                    new Config(log,[default: [max_versions: 10, last_downloaded: [amount: 30, unit: 'day']],
                                (REPO_NAME): [last_downloaded: [amount: 40, unit: 'day'],
                                              (COMPONENT_NAME): [max_versions: 7, last_downloaded: [amount: 50, unit: 'day'],
                                                        (COMPONENT_VERSION)  : [keep: 'forever']]]])
            sut = new DeleteAssets(log, repo, null, null, REPO_NAME.split(), cleanupConfig)

        when:
            sut.clean()

        then:
            1 * transaction.countComponents(_ as Query, _ as List<Repository>) >> 11
            0 * transaction.deleteComponent(_ as Component)
            0 * transaction.rollback()
    }

    def "calling the dry run method does not delete asset"() {
        given:

        when:
            sut.dryRun()

        then:
            1 * transaction.countComponents(_ as Query, _ as List<Repository>) >> 11
            0 * transaction.deleteComponent(_ as Component)
            0 * transaction.rollback()
    }

    def "transaction is rolled back and closed when an exception is thrown"() {
        given:
            transaction.begin() >> {
                throw new IllegalStateException("Illegal state when starting the transaction")
            }

        when:
            sut.clean()

        then:
        1 * transaction.rollback()
        1 * transaction.close()
    }

    interface TestRepositoryApi {
        RepositoryManager getRepositoryManager()
    }
}
