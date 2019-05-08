package cm.nexus

import org.joda.time.DateTime
import org.sonatype.nexus.repository.storage.Asset
import org.sonatype.nexus.repository.storage.Component
import spock.lang.Specification

class QueryBuilderTest extends Specification {
    private static final String NAME = "name"
    private static final DateTime LAST_UPDATED = DateTime.now()
    private static final String VERSION = "1.0"
    private static final String GROUP = "group"
    private static final String MATCHINGKEY = "1.0.*"

    private static final int RETAIN_DAYS = 30
    private static final String NAME_FILTER1 = "filter1.*"
    private static final String NAME_FILTER2 = "filter2.*"

    private Component component
    private Asset asset

    def setup() {
        component = Mock(Component)
        component.name() >> NAME
        component.version() >> VERSION
        asset = Mock(Asset)
        asset.blobUpdated() >> LAST_UPDATED
    }

    def "query for counting components does not contain a group filter if the component is not part of a group"() {
        given:

        when:
        def query = QueryBuilder.buildNewerComponentCountQuery(component, asset, MATCHINGKEY)

        then:
        query.where == "name = :p0 AND version !=:p1 AND last_updated >= :p2 AND version MATCHES :p3"
        query.parameters.get("p0") == NAME
        query.parameters.get("p1") == VERSION
        query.parameters.get("p2") == LAST_UPDATED.toString(QueryBuilder.fmt)
        query.parameters.get("p3") == MATCHINGKEY
    }

    def "query for counting components contains a group filter if the component is part of a group"() {
        given:
        component.group() >> GROUP

        when:
        def query = QueryBuilder.buildNewerComponentCountQuery(component, asset, MATCHINGKEY)

        then:
        query.where == "name = :p0 AND version !=:p1 AND last_updated >= :p2 AND version MATCHES :p3 AND group = :p4"
        query.parameters.get("p0") == NAME
        query.parameters.get("p1") == VERSION
        query.parameters.get("p2") == LAST_UPDATED.toString(QueryBuilder.fmt)
        query.parameters.get("p3") == MATCHINGKEY
        query.parameters.get("p4") == GROUP
    }

    def "query for finding assets contains the correct filters and parameters"() {
        given:

        when:
        def query = QueryBuilder.findAssetsQuery(RETAIN_DAYS, NAME_FILTER1, NAME_FILTER2)

        then:
        query.where == "(last_downloaded < :p0 OR last_downloaded IS NULL) AND name MATCHES :p1 AND name MATCHES :p2"
        query.parameters.get("p0") == DateTime.now().minusDays(RETAIN_DAYS).toString(QueryBuilder.fmt)
        query.parameters.get("p1") == NAME_FILTER1
        query.parameters.get("p2") == NAME_FILTER2
    }

}
