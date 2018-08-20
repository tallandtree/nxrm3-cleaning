package cm.nexus.cleanup

import org.yaml.snakeyaml.Yaml
import org.slf4j.Logger

class ConfigReader {
    static Config readConfig(Logger log, String path) {
        def configItems = new Yaml().load(new File(path, 'nexus-cleanup.yaml').newInputStream()) as Map
        return new Config(log, configItems)
    }
}
