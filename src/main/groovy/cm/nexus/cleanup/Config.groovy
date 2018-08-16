package cm.nexus.cleanup

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

    Config(Map configItems) {
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
        def optionalRepoConfigItems = findConfigItems(configItems, repoName)
        if (optionalRepoConfigItems.isPresent()) {
            def repoConfigItems = optionalRepoConfigItems.get()
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

    Map getConfigForAsset(String repoName, String componentName, String assetName) {
        def result = new HashMap(defaultItems)
        return getConfigItems(result, configItems, [repoName, componentName, assetName])
    }

    private static Map getConfigItems(Map result, Map configItems, List<String> itemsKeys) {
        def itemsKey = itemsKeys.get(0)
        def optionalSubConfigItems = findConfigItems(configItems, itemsKey)
        if (!optionalSubConfigItems.isPresent()) return clean(result)
        def subConfigItems = optionalSubConfigItems.get()
        saveItemSettings(result, subConfigItems)
        return itemsKeys.size() > 1 ? getConfigItems(result, subConfigItems, itemsKeys[1..-1]) : clean(result)
    }

    private static Optional<Map> findConfigItems(Map configItems, String itemsKey) {
        def matchingKey = configItems.keySet().find {itemsKey == it}
        if (matchingKey == null) {
            matchingKey = configItems.keySet().find {itemsKey ==~ /$it/}
        }
        if (matchingKey != null) {
            return Optional.of(configItems.get(matchingKey) as Map)
        }
        return Optional.empty()
    }

    private static void saveItemSettings(Map result, Map configItems) {
        def maxVersions = configItems.get(MAX_VERSIONS)
        def lastDownloaded = configItems.get(LAST_DOWNLOADED)
        if (maxVersions != null || lastDownloaded != null) {
            result.remove(KEEP)
        }
        storeIfPresent(result, MAX_VERSIONS, maxVersions)
        storeIfPresent(result, LAST_DOWNLOADED, lastDownloaded)
        storeIfPresent(result, KEEP, configItems.get(KEEP))
    }

    private static storeIfPresent(Map result, String itemKey, Object value) {
        if (value != null) {
            result.put(itemKey, value)
        }
    }

    private static final Map clean(Map result) {
        if (result.containsKey(KEEP)) {
            result.remove(MAX_VERSIONS)
            result.remove(LAST_DOWNLOADED)
        }
        return result
    }
}
