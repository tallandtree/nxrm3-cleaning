package cm.nexus

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.sonatype.nexus.repository.storage.Asset
import org.sonatype.nexus.repository.storage.Component
import org.sonatype.nexus.repository.storage.Query

class QueryBuilder {
    public static final DateTimeFormatter fmt = DateTimeFormat.forPattern('yyyy-MM-dd HH:mm:ss')

    static Query buildNewerComponentCountQuery(Component component, Asset asset, String matchingKey) {
        Query.Builder builder = Query.builder()
                .where('name = ')
                .param(component.name())
                .and('version !=')
                .param(component.version())
                .and('last_updated >= ')
                // normally component.lastUpdated() should be used, but this timestamp is not updated correctly by Nexus for Docker components
                // asset.lastUpdated() does not give the expected results either, but asset.blobUpdated() does
                .param(asset.blobUpdated().toString(fmt))
                .and('version MATCHES ')
                .param(matchingKey)

        if (component.group()) {
            builder.and('group = ').param(component.group())
        }

        return builder.build()
    }

    static Query findAssetsQuery(int maxDays, String nameFilter1, String nameFilter2) {
        return Query.builder()
		.where('(last_downloaded <')
                .param(DateTime.now().minusDays(maxDays).toString(fmt))
                .or('last_downloaded IS NULL)')
                .and('name MATCHES ')
                .param(nameFilter1)
                .and('name MATCHES ')
                .param(nameFilter2)
                .build()
    }
}
