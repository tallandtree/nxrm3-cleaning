package cm.nexus.cleanup

import org.yaml.snakeyaml.Yaml

class ConfigReader {
    static Config readConfig(String path) {
        def configItems = new Yaml().load(new File(path, 'nexus-cleanup.yaml').newInputStream()) as Map
        return new Config(configItems)
    }
}
