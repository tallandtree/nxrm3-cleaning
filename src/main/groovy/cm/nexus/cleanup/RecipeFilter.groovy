package cm.nexus.cleanup

import org.sonatype.nexus.repository.Repository

class RecipeFilter {
    static final SUPPORTED_RECIPES = ['maven2-hosted', 'npm-hosted', 'nuget-hosted', 'docker-hosted']

    static boolean isSupported(String recipeName) {
        return SUPPORTED_RECIPES.contains(recipeName)
    }

    static Collection<Repository> supported(Iterable<Repository> repositories) {
        return repositories.findAll({repo -> SUPPORTED_RECIPES.contains(repo.configuration.recipeName)})
    }
}
