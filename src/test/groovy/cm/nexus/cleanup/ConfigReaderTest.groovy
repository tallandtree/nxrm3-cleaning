package cm.nexus.cleanup

import spock.lang.Specification

import java.nio.file.Paths
import org.slf4j.Logger

class ConfigReaderTest extends Specification {
    private Logger log
    def setup(){
        log=Mock(Logger)
    }
    def "values are mapped correctly when reading the config file"() {
        given:
            def expectedConfig = new Config(log,['default':['max_versions':5, 'last_downloaded':['amount':30, 'unit':'day']],
                                             'team-z-docker-internal':['last_downloaded':['amount':40, 'unit':'day'],
                                                                      'mygrp/build-template-backend':['last_downloaded':['amount':50, 'unit':'day'],
                                                                                                    '1.3.*': ['keep':'forever'],
                                                                                                    '1.2.*': ['last_downloaded':['amount':60, 'unit':'day']]],
                                                                      'mygrp/build-*':['last_downloaded':['amount':50, 'unit':'day'],
                                                                                                    '1.3.*': ['max_versions': 10, 'last_downloaded':['amount':3, 'unit':'month']],
                                                                                                    '1.2.*': ['last_downloaded':['amount':50, 'unit':'day']]]]])

        when:
            def readConfig =
                    ConfigReader.readConfig(log, Paths.get(ClassLoader.getSystemResource('nexus-cleanup.yaml').toURI()).getParent().normalize().toString())

        then:
            readConfig.getConfigForAsset('team-z-docker-internal', 'mygrp/build-template-backend', '1.2.*') ==
                    expectedConfig.getConfigForAsset('team-z-docker-internal','mygrp/build-template-backend', '1.2.*')
            readConfig.getConfigForAsset('team-z-docker-internal', 'mygrp/build-*', '1.3.*') ==
                    expectedConfig.getConfigForAsset('team-z-docker-internal','mygrp/build-*', '1.3.*')
    }
}
