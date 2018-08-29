package ccv.cm.nexus.cleanup

class Pair {
    String key
    Map config

    Pair (String key, Map config) {
        this.key = key ?: '.*'
        this.config = config
    }
}
