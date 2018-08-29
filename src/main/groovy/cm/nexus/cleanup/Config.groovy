package cm.nexus.cleanup
import org.slf4j.Logger

class Config {
    public static final String DEFAULT = "default"
    public static final String MAX_VERSIONS = "max_versions"
    public static final String LAST_DOWNLOADED = "last_downloaded"
    public static final String AMOUNT = "amount"
    public static final String UNIT = "unit"
    public static final String KEEP = "keep"

    private Map configItems
    private Map defaultItems
    private int defaultDaysToRetain
    private final Logger log

    Config(Logger log, Map configItems) {
        this.log = log
        this.configItems = configItems
        defaultItems = configItems.get(DEFAULT) as Map
        def downloadItems = defaultItems.get(LAST_DOWNLOADED) as Map
        def downloadUnit = downloadItems.get(UNIT)
        defaultDaysToRetain = downloadItems.get(AMOUNT) * unitToDays(downloadUnit)
    }

    static int getRetainPeriodInDays(Map configItems) {
        def downloadItems = configItems.get(LAST_DOWNLOADED) as Map
        return downloadItems.get(AMOUNT) * unitToDays(downloadItems.get(UNIT))
    }

    private static int unitToDays(def unit) {
        switch (unit) {
            case "day": return 1
            case "month" : return 31
            case "year": return 365
            default:
                throw new IllegalArgumentException("Unknown unit $unit (allowed are 'day', 'month' and 'year')")
        }
    }

    int getShortestRetainPeriodForRepo(String repoName) {
        def minDaysToRetain = defaultDaysToRetain
        def optionalPairRepoConfigItems = findConfigItems(configItems, repoName)
        if (optionalPairRepoConfigItems.isPresent()) {
            def repoConfigItems = optionalPairRepoConfigItems.get().config
            if (repoConfigItems.containsKey(LAST_DOWNLOADED)) {
                minDaysToRetain = Integer.MAX_VALUE
            }
            minDaysToRetain = findShortestRetainPeriod(repoConfigItems, minDaysToRetain)
        }
        return minDaysToRetain
    }

    private static int findShortestRetainPeriod(Map configItems, int daysToRetain) {
        int minDaysToRetain = daysToRetain
        configItems.each {ci ->
            if (ci.key == LAST_DOWNLOADED) {
                def lastDownloaded = ci.value as Map
                def foundDaysToRetain = lastDownloaded.get(AMOUNT) * unitToDays(lastDownloaded.get(UNIT))
                minDaysToRetain = foundDaysToRetain < daysToRetain ? foundDaysToRetain : daysToRetain
            } else if (ci.key == KEEP) {
                minDaysToRetain = daysToRetain
            } else if (ci.value instanceof Map) {
                minDaysToRetain = findShortestRetainPeriod(ci.value as Map, minDaysToRetain)
            }
        }
        return minDaysToRetain
    }

    Pair getConfigForAsset(String repoName, String componentName, String assetName) {
        def result = new Pair("", new HashMap(defaultItems))
        return getConfigItems(result, configItems, [repoName, componentName, assetName])
    }

    private Pair getConfigItems(Pair result, Map configItems, List<String> itemsKeys) {
        def itemsKey = itemsKeys.get(0)
        def optionalSubConfigItems = findConfigItems(configItems, itemsKey)
        if (!optionalSubConfigItems.isPresent()) return clean(result)
        def subConfigItems = optionalSubConfigItems.get()
        saveItemSettings(result, subConfigItems, 1 == itemsKeys.size())
        return itemsKeys.size() > 1 ? getConfigItems(result, subConfigItems.config, itemsKeys[1..-1]) : clean(result)
    }

    private Optional<Pair> findConfigItems(Map configItems, String itemsKey) {
        def matchingKey
        try {
            matchingKey = configItems.keySet().find { itemsKey == it }
            if (matchingKey == null) {
                matchingKey = configItems.keySet().find { itemsKey ==~ /$it/ }
            }
            if (matchingKey != null) {
                def matchingPair = new Pair(matchingKey as String, configItems.get(matchingKey) as Map)
                return Optional.of(matchingPair as Pair)
            }
        } catch (ignore) {
            log.warn("WARNING: version contains no definition in configuration file: {}", matchingKey ?: '')
        }
        return Optional.empty()
    }

    private static void saveItemSettings(Pair result, Pair configItems, Boolean isVersion) {
        def maxVersions = configItems.config.get(MAX_VERSIONS)
        def lastDownloaded = configItems.config.get(LAST_DOWNLOADED)
        if (maxVersions != null || lastDownloaded != null) {
            result.config.remove(KEEP)
        }
        storeIfPresent(result.config, MAX_VERSIONS, maxVersions)
        storeIfPresent(result.config, LAST_DOWNLOADED, lastDownloaded)
        storeIfPresent(result.config, KEEP, configItems.config.get(KEEP))
        if (isVersion) {
            result.key = configItems.key
        }
    }

    private static storeIfPresent(Map result, String itemKey, Object value) {
        if (value != null) {
            result.put(itemKey, value)
        }
    }

    private static final Pair clean(Pair result) {
        if (result.config.containsKey(KEEP)) {
            result.config.remove(MAX_VERSIONS)
            result.config.remove(LAST_DOWNLOADED)
        }
        return result
    }
}
