package cm.nexus.cleanup

import spock.lang.Specification
import org.slf4j.Logger

class ConfigTest extends Specification {

    public static final String REPO_NAME = "testrepo"
    public static final String COMPONENT_NAME = "testcomponent"
    public static final String ASSET_NAME = "testasset"
    private Logger log
    def setup(){
        log=Mock(Logger)
    }

    def "an exception is thrown when the config contains an unknown unit"() {
        given:
            def configItems = [default: [max_versions: 7, last_downloaded: [amount: 30, unit: 'week']]]

        when:
            new Config(log,configItems)

        then:
            def e = thrown(IllegalArgumentException)
            e.message == "Unknown unit week (allowed are 'day', 'month' and 'year')"
    }

    def "a newly created config object contains the correct maximum number of days for unit day"() {
        given:
        def configItems = [default: [max_versions: 7, last_downloaded: [amount: 25, unit: 'day']]]

        when:
            def config = new Config(log,configItems)

        then:
            config.getShortestRetainPeriodForRepo(REPO_NAME) == 25
    }

    def "a newly created config object contains the correct maximum number of days for unit month"() {
        given:
            def configItems = [default: [max_versions: 7, last_downloaded: [amount: 2, unit: 'month']]]

        when:
            def config = new Config(log,configItems)

        then:
            config.getShortestRetainPeriodForRepo(REPO_NAME) == 62
    }

    def "a newly created config object contains the correct maximum number of days for unit year"() {
        given:
            def configItems = [default: [max_versions: 7, last_downloaded: [amount: 3, unit: 'year']]]

        when:
            def config = new Config(log,configItems)

        then:
            config.getShortestRetainPeriodForRepo(REPO_NAME) == 1095
    }

    def "exact matching of items is favoured over regex matching"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], 'testre.*': [max_versions: 8, last_downloaded: [amount: 40, unit: 'day']], 'testrepo': [max_versions: 10, last_downloaded: [amount: 50, unit: 'day']]]
            def config = new Config(log,configItems)

        when:
            def repoItems = config.getConfigForAsset(REPO_NAME, COMPONENT_NAME, ASSET_NAME)

        then:
            repoItems == [max_versions: 10, last_downloaded: [amount: 50, unit: 'day']]
    }

    def "retrieving the config settings for an asset returns the default settings when there are no settings for repositories"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']]]
            def config = new Config(log,configItems)

        when:
            def repoItems = config.getConfigForAsset(REPO_NAME, COMPONENT_NAME, ASSET_NAME)

        then:
            repoItems == [max_versions:5, last_downloaded:[amount: 30, unit: 'day']]
    }

    def "retrieving the config settings for an asset returns the default settings when there are no settings for the repository"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], testrepo: [last_downloaded: [amount: 40, unit: 'day']]]
            def config = new Config(log,configItems)

        when:
            def repoItems = config.getConfigForAsset("testrepo2", COMPONENT_NAME, ASSET_NAME)

        then:
            repoItems == [max_versions:5, last_downloaded:[amount: 30, unit: 'day']]
    }

    def "retrieving the config settings for an asset returns the default settings overridden with the last_downloaded settings of the repository"() {
        given:
        def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], testrepo: [last_downloaded: [amount: 40, unit: 'day']]]
        def config = new Config(log,configItems)

        when:
        def repoItems = config.getConfigForAsset(REPO_NAME, COMPONENT_NAME, ASSET_NAME)

        then:
        repoItems == [max_versions: 5, last_downloaded: [amount: 40, unit: 'day']]
    }

    def "retrieving the config settings for an asset returns the default settings overridden with the max_versions setting of the repository"() {
        given:
        def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], testrepo: [max_versions: 10]]
        def config = new Config(log,configItems)

        when:
        def repoItems = config.getConfigForAsset(REPO_NAME, COMPONENT_NAME, ASSET_NAME)

        then:
        repoItems == [max_versions: 10, last_downloaded: [amount: 30, unit: 'day']]
    }

    def "retrieving the config settings for an asset returns the settings of the repository when there are no settings for components"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], testrepo: [max_versions: 10, last_downloaded: [amount: 40, unit: 'day']]]
            def config = new Config(log,configItems)

        when:
            def repoItems = config.getConfigForAsset(REPO_NAME, COMPONENT_NAME, ASSET_NAME)

        then:
            repoItems == [max_versions: 10, last_downloaded: [amount: 40, unit: 'day']]
    }

    def "retrieving the config settings for an asset returns the settings of the repository when there are no settings for the component"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], testrepo: [max_versions: 7, last_downloaded: [amount: 40, unit: 'day'], testcomponent: [max_versions: 10, last_downloaded: [amount: 50, unit: 'week']]]]
            def config = new Config(log,configItems)

        when:
            def repoItems = config.getConfigForAsset(REPO_NAME, "testcomponent2", ASSET_NAME)

        then:
            repoItems == [max_versions: 7, last_downloaded: [amount: 40, unit: 'day']]
    }

    def "retrieving the config settings for an asset returns the settings of the component when there are no settings for assets"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], testrepo: [max_versions: 7, last_downloaded: [amount: 40, unit: 'day'], testcomponent: [max_versions: 10, last_downloaded: [amount: 50, unit: 'week']]]]
            def config = new Config(log,configItems)

        when:
            def repoItems = config.getConfigForAsset(REPO_NAME, COMPONENT_NAME, ASSET_NAME)

        then:
            repoItems == [max_versions: 10, last_downloaded: [amount: 50, unit: 'week']]
    }

    def "retrieving the config settings for an asset returns the settings of the component when there are no settings for the asset"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], testrepo: [max_versions: 7, last_downloaded: [amount: 40, unit: 'day'], testcomponent: [max_versions: 10, last_downloaded: [amount: 50, unit: 'week'], testasset: [max_versions: 15, last_downloaded: [amount: 60, unit: 'year']]]]]
            def config = new Config(log,configItems)

        when:
            def repoItems = config.getConfigForAsset(REPO_NAME, COMPONENT_NAME, "testasset2")

        then:
            repoItems == [max_versions: 10, last_downloaded: [amount: 50, unit: 'week']]
    }

    def "retrieving the config settings for an asset returns the settings of the repo overridden with the max_versions setting of the component"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], testrepo: [last_downloaded: [amount: 40, unit: 'day'], testcomponent: [max_versions: 10]]]
            def config = new Config(log,configItems)

        when:
            def repoItems = config.getConfigForAsset(REPO_NAME, COMPONENT_NAME, ASSET_NAME)

        then:
            repoItems == [max_versions: 10, last_downloaded: [amount: 40, unit: 'day']]
    }

    def "retrieving the config settings for an asset returns the settings of the repo overridden with the last_downloaded settings of the component"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], testrepo: [max_versions: 7, last_downloaded: [amount: 40, unit: 'day'], testcomponent: [last_downloaded: [amount: 50, unit: 'week']]]]
            def config = new Config(log,configItems)

        when:
            def repoItems = config.getConfigForAsset(REPO_NAME, COMPONENT_NAME, ASSET_NAME)

        then:
            repoItems == [max_versions: 7, last_downloaded: [amount: 50, unit: 'week']]
    }

    def "retrieving the config settings for an asset returns the default settings overridden with the max_versions setting of the component"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], testrepo: [max_versions: 7, testcomponent: [max_versions: 10]]]
            def config = new Config(log,configItems)

        when:
            def repoItems = config.getConfigForAsset(REPO_NAME, COMPONENT_NAME, ASSET_NAME)

        then:
            repoItems == [max_versions: 10, last_downloaded: [amount: 30, unit: 'day']]
    }

    def "retrieving the config settings for an asset returns the default settings overridden with the last_downloaded settings of the component"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], testrepo: [last_downloaded: [amount: 40, unit: 'day'], testcomponent: [last_downloaded: [amount: 50, unit: 'week']]]]
            def config = new Config(log,configItems)

        when:
            def repoItems = config.getConfigForAsset(REPO_NAME, COMPONENT_NAME, ASSET_NAME)

        then:
            repoItems == [max_versions: 5, last_downloaded: [amount: 50, unit: 'week']]
    }

    def "retrieving the config settings for an asset returns the settings of the asset when present"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], testrepo: [last_downloaded: [amount: 40, unit: 'day'], testcomponent: [last_downloaded: [amount: 50, unit: 'week'], testasset: [max_versions: 10, last_downloaded: [amount: 60, unit: 'year']]]]]
            def config = new Config(log,configItems)

        when:
            def repoItems = config.getConfigForAsset(REPO_NAME, COMPONENT_NAME, ASSET_NAME)

        then:
            repoItems == [max_versions: 10, last_downloaded: [amount: 60, unit: 'year']]
    }

    def "retrieving the config settings for an asset returns the default settings overridden with the max_versions setting of the asset"() {
        given:
        def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], testrepo: [max_versions: 7, testcomponent: [max_versions: 9, testasset: [max_versions: 10]]]]
        def config = new Config(log,configItems)

        when:
        def repoItems = config.getConfigForAsset(REPO_NAME, COMPONENT_NAME, ASSET_NAME)

        then:
        repoItems == [max_versions: 10, last_downloaded: [amount: 30, unit: 'day']]
    }


    def "retrieving the config settings for an asset returns the default settings overridden with the last_downloaded settings of the asset"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], testrepo: [last_downloaded: [amount: 40, unit: 'day'], testcomponent: [last_downloaded: [amount: 50, unit: 'week'], testasset: [last_downloaded: [amount: 60, unit: 'year']]]]]
            def config = new Config(log,configItems)

        when:
            def repoItems = config.getConfigForAsset(REPO_NAME, COMPONENT_NAME, ASSET_NAME)

        then:
            repoItems == [max_versions: 5, last_downloaded: [amount: 60, unit: 'year']]
    }

    def "retrieving the config settings for an asset returns the settings of repo overridden with the max_versions setting of the asset"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], testrepo: [last_downloaded: [amount: 40, unit: 'day'], testcomponent: [max_versions: 8, testasset: [max_versions: 10]]]]
            def config = new Config(log,configItems)

        when:
            def repoItems = config.getConfigForAsset(REPO_NAME, COMPONENT_NAME, ASSET_NAME)

        then:
            repoItems == [max_versions: 10, last_downloaded: [amount: 40, unit: 'day']]
    }

    def "retrieving the config settings for an asset returns the settings of the repo overridden with the last_downloaded settings of the asset"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], testrepo: [max_versions: 5, last_downloaded: [amount: 40, unit: 'day'], testcomponent: [last_downloaded: [amount: 50, unit: 'week'], testasset: [last_downloaded: [amount: 60, unit: 'year']]]]]
            def config = new Config(log,configItems)

        when:
            def repoItems = config.getConfigForAsset(REPO_NAME, COMPONENT_NAME, ASSET_NAME)

        then:
            repoItems == [max_versions: 5, last_downloaded: [amount: 60, unit: 'year']]
    }

    def "retrieving the config settings for an asset returns the settings of the component overridden with the max_versions setting of the asset"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], testrepo: [last_downloaded: [amount: 40, unit: 'day'], testcomponent: [last_downloaded: [amount: 50, unit: 'week'], testasset: [max_versions: 10]]]]
            def config = new Config(log,configItems)

        when:
            def repoItems = config.getConfigForAsset(REPO_NAME, COMPONENT_NAME, ASSET_NAME)

        then:
            repoItems == [max_versions: 10, last_downloaded: [amount: 50, unit: 'week']]
    }

    def "retrieving the config settings for an asset returns the settings of the component overridden with the last_downloaded settings of the asset"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], testrepo: [last_downloaded: [amount: 40, unit: 'day'], testcomponent: [max_versions: 7, last_downloaded: [amount: 50, unit: 'week'], testasset: [last_downloaded: [amount: 60, unit: 'year']]]]]
            def config = new Config(log,configItems)

        when:
            def repoItems = config.getConfigForAsset(REPO_NAME, COMPONENT_NAME, ASSET_NAME)

        then:
            repoItems == [max_versions: 7, last_downloaded: [amount: 60, unit: 'year']]
    }

    def "retrieving the config settings for an asset overrides the keep:forever of the repo with the settings of the asset"() {
        given:
        def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], testrepo: [keep: 'forever', testcomponent: [max_versions: 7, last_downloaded: [amount: 50, unit: 'week'], testasset: [last_downloaded: [amount: 60, unit: 'year']]]]]
        def config = new Config(log,configItems)

        when:
        def repoItems = config.getConfigForAsset(REPO_NAME, COMPONENT_NAME, ASSET_NAME)

        then:
        repoItems == [max_versions: 7, last_downloaded: [amount: 60, unit: 'year']]
    }

    def "retrieving the config settings for an asset overrides the keep:forever of the component with the settings of the asset"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], testrepo: [last_downloaded: [amount: 40, unit: 'day'], testcomponent: [keep: 'forever', testasset: [last_downloaded: [amount: 60, unit: 'year']]]]]
            def config = new Config(log,configItems)

        when:
            def repoItems = config.getConfigForAsset(REPO_NAME, COMPONENT_NAME, ASSET_NAME)

        then:
            repoItems == [max_versions: 5, last_downloaded: [amount: 60, unit: 'year']]
    }

    def "retrieving the config settings for an asset returns the keep:forever setting of the repo"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], testrepo: [keep: 'forever']]
            def config = new Config(log,configItems)

        when:
            def repoItems = config.getConfigForAsset(REPO_NAME, COMPONENT_NAME, ASSET_NAME)

        then:
            repoItems == [keep: 'forever']
    }

    def "retrieving the config settings for an asset returns the keep:forever setting of the component"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], testrepo: [last_downloaded: [amount: 40, unit: 'day'], testcomponent: [keep: 'forever']]]
            def config = new Config(log,configItems)

        when:
            def repoItems = config.getConfigForAsset(REPO_NAME, COMPONENT_NAME, ASSET_NAME)

        then:
            repoItems == [keep: 'forever']
    }

    def "retrieving the config settings for an asset returns the keep:forever setting of the asset"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], testrepo: [last_downloaded: [amount: 40, unit: 'day'], testcomponent: [max_versions: 7, last_downloaded: [amount: 50, unit: 'week'], testasset: [keep: 'forever']]]]
            def config = new Config(log,configItems)

        when:
            def repoItems = config.getConfigForAsset(REPO_NAME, COMPONENT_NAME, ASSET_NAME)

        then:
            repoItems == [keep: 'forever']
    }

    def "retrieving the config settings for an asset returns the settings of the repo when the name of the repo matches a regex in the repo config settings"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], 'testre.*': [max_versions: 8, last_downloaded: [amount: 40, unit: 'day']]]
            def config = new Config(log,configItems)

        when:
            def repoItems = config.getConfigForAsset(REPO_NAME, COMPONENT_NAME, ASSET_NAME)

        then:
            repoItems == [max_versions: 8, last_downloaded: [amount: 40, unit: 'day']]
    }

    def "retrieving the config settings for an asset returns the default settings when the name of the repo does not match a regex in the repo config settings"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], 'testri.*': [last_downloaded: [amount: 40, unit: 'day']]]
            def config = new Config(log,configItems)

        when:
            def repoItems = config.getConfigForAsset(REPO_NAME, COMPONENT_NAME, ASSET_NAME)

        then:
            repoItems == [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']]
    }

    def "retrieving the config settings for an asset returns the settings of the component when the name of the component matches a regex in the component config settings"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], testrepo: [max_versions: 8, last_downloaded: [amount: 40, unit: 'day'], 'testcom.*': [max_versions: 10, last_downloaded: [amount: 50, unit: 'week']]]]
            def config = new Config(log,configItems)

        when:
            def repoItems = config.getConfigForAsset(REPO_NAME, COMPONENT_NAME, ASSET_NAME)

        then:
            repoItems == [max_versions: 10, last_downloaded: [amount: 50, unit: 'week']]
    }

    def "retrieving the config settings for an asset returns the settings of the repo when the name of the component does not match a regex in the component config settings"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], testrepo: [max_versions: 8, last_downloaded: [amount: 40, unit: 'day'], 'testcon.*': [last_downloaded: [amount: 50, unit: 'week']]]]
            def config = new Config(log,configItems)

        when:
            def repoItems = config.getConfigForAsset(REPO_NAME, COMPONENT_NAME, ASSET_NAME)

        then:
            repoItems == [max_versions: 8, last_downloaded: [amount: 40, unit: 'day']]
    }

    def "retrieving the config settings for an asset returns the settings of the asset when the name of the asset matches a regex in the asset config settings"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], testrepo: [last_downloaded: [amount: 40, unit: 'day'], testcomponent: [last_downloaded: [amount: 50, unit: 'week'], 'testas.*': [max_versions: 10, last_downloaded: [amount: 60, unit: 'year']]]]]
            def config = new Config(log,configItems)

        when:
            def repoItems = config.getConfigForAsset(REPO_NAME, COMPONENT_NAME, ASSET_NAME)

        then:
            repoItems == [max_versions: 10, last_downloaded: [amount: 60, unit: 'year']]
    }

    def "retrieving the config settings for an asset returns the settings of the component when the name of the asset does not match a regex in the asset config settings"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], testrepo: [last_downloaded: [amount: 40, unit: 'day'], testcomponent: [last_downloaded: [amount: 50, unit: 'week'], 'testasz.*': [max_versions: 10, last_downloaded: [amount: 60, unit: 'year']]]]]
            def config = new Config(log,configItems)

        when:
            def repoItems = config.getConfigForAsset(REPO_NAME, COMPONENT_NAME, ASSET_NAME)

        then:
            repoItems == [max_versions: 5, last_downloaded: [amount: 50, unit: 'week']]
    }

    def "retrieving the retention period for a repo returns the default setting"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], testrepo: [max_versions: 5, testcomponent: [testasset: [max_versions: 6]]]]
            def config = new Config(log,configItems)

        when:
            def repoMaxDays = config.getShortestRetainPeriodForRepo(REPO_NAME)

        then:
            repoMaxDays == 30
    }

    def "retrieving the retention period for a repo returns the setting of the repo"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 10, unit: 'day']], testrepo: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day'], testcomponent: [last_downloaded: [amount: 50, unit: 'day'], testasset: [last_downloaded: [amount: 40, unit: 'day']]]]]
            def config = new Config(log,configItems)

        when:
            def repoMaxDays = config.getShortestRetainPeriodForRepo(REPO_NAME)

        then:
            repoMaxDays == 30
    }

    def "retrieving the retention period for a repo returns the setting of the component"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 10, unit: 'day']], testrepo: [max_versions: 5, last_downloaded: [amount: 50, unit: 'day'], testcomponent: [last_downloaded: [amount: 30, unit: 'day'], testasset: [last_downloaded: [amount: 40, unit: 'day']]]]]
            def config = new Config(log,configItems)

        when:
            def repoMaxDays = config.getShortestRetainPeriodForRepo(REPO_NAME)

        then:
            repoMaxDays == 30
    }

    def "retrieving the retention period for a repo returns the setting of the asset"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 10, unit: 'day']], testrepo: [max_versions: 5, last_downloaded: [amount: 50, unit: 'day'], testcomponent: [last_downloaded: [amount: 40, unit: 'day'], testasset: [last_downloaded: [amount: 30, unit: 'day']]]]]
            def config = new Config(log,configItems)

        when:
            def repoMaxDays = config.getShortestRetainPeriodForRepo(REPO_NAME)

        then:
            repoMaxDays == 30
    }

    def "retrieving the retention period for a repo returns the setting of the second asset"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 10, unit: 'day']], testrepo: [max_versions: 5, last_downloaded: [amount: 50, unit: 'day'], testcomponent: [last_downloaded: [amount: 40, unit: 'day'], testasset: [last_downloaded: [amount: 35, unit: 'day']], testasset2: [last_downloaded: [amount: 30, unit: 'day']]]]]
            def config = new Config(log,configItems)

        when:
            def repoMaxDays = config.getShortestRetainPeriodForRepo(REPO_NAME)

        then:
            repoMaxDays == 30
    }

    def "retrieving the retention period for a repo returns the setting of the first asset if the second asset is set to be kept forever"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 10, unit: 'day']], testrepo: [max_versions: 5, last_downloaded: [amount: 50, unit: 'day'], testcomponent: [last_downloaded: [amount: 40, unit: 'day'], testasset: [last_downloaded: [amount: 30, unit: 'day']], testasset2: [keep: 'forever']]]]
            def config = new Config(log,configItems)

        when:
            def repoMaxDays = config.getShortestRetainPeriodForRepo(REPO_NAME)

        then:
            repoMaxDays == 30
    }

    def "when retrieving the retention period for a repo the setting of the repo overrides the default setting"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], testrepo: [max_versions: 5, last_downloaded: [amount: 40, unit: 'day'], testcomponent: [last_downloaded: [amount: 50, unit: 'day'], testasset: [last_downloaded: [amount: 60, unit: 'day']]]]]
            def config = new Config(log,configItems)

        when:
            def repoMaxDays = config.getShortestRetainPeriodForRepo(REPO_NAME)

        then:
            repoMaxDays == 40
    }

    def "retrieving the retention period for a repo returns the default setting if the repo does not contain retention period settings"() {
        given:
            def configItems = [default: [max_versions: 5, last_downloaded: [amount: 30, unit: 'day']], testrepo: [max_versions: 5, testcomponent: [last_downloaded: [amount: 50, unit: 'day'], testasset: [last_downloaded: [amount: 60, unit: 'day']]]]]
            def config = new Config(log,configItems)

        when:
            def repoMaxDays = config.getShortestRetainPeriodForRepo(REPO_NAME)

        then:
            repoMaxDays == 30
    }
}
