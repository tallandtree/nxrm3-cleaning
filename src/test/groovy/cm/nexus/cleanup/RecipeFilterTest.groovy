package cm.nexus.cleanup

import org.sonatype.nexus.repository.Repository
import org.sonatype.nexus.repository.config.Configuration
import spock.lang.Specification

class RecipeFilterTest extends Specification {

    def "only supported recipes are validated succesfully"() {
        expect:
            RecipeFilter.SUPPORTED_RECIPES.each {r -> !RecipeFilter.isSupported(r)}
    }

    def "returns repositories with supported recipes only"() {
        given:
            def repoConfig = Mock(Configuration)
            repoConfig.getRecipeName() >>> ['docker-hosted', 'unsupported', 'maven2-hosted']
            def repo1 = Mock(Repository)
            repo1.configuration >> repoConfig
            def repo2 = Mock(Repository)
            repo2.configuration >> repoConfig
            def repo3 = Mock(Repository)
            repo3.configuration >> repoConfig

        when:
            def filteredRepos = RecipeFilter.supported([repo1, repo2, repo3])

        then:
            filteredRepos == [repo1, repo3]
    }
}
