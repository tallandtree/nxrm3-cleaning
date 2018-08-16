import org.yaml.snakeyaml.Yaml

def appConf = new Yaml().load(new FileReader('../resources/nexus-cleanup.yaml'))
println appConf