# Sonatype Nexus 3 hosted repository cleaning

Nexus 3 repository manager (NXRM3) does not yet offer automatic cleaning of hosted
repositories. A set of Groovy scripts is developed to clean NXRM3 hosted repositories.
As repositories, components and versions may require specific cleaning rules, a YAML
file has been introduced, which is parsed by the cleaning script. This allows you to
specify cleaning settings at the desired level.

The cleaning supports and is tested with the following repository types:

- docker
- maven2
- nuget
- npm

## The nexus-cleanup.yaml file format

```
default:
  max_versions: <number of versions to retain>
  last_downloaded:
    amount: <number of units>
    unit: <day|month|year>
 
[<repository name>:]
  [max_versions: <number of versions to retain>]
  [last_downloaded:
    amount: <number of units>
    unit: <day|month|year>
  ]
  [keep: forever]
  [<component>:
    [max_versions: <number of versions to retain>]
    [last_downloaded:
      amount: <number of units>
      unit: <day|month|year>
    ]
    [keep: forever]
    [<version>
      [last_downloaded:
        amount: <number of units>
        unit: <day|month|year>
      ]
      [keep: forever]
    ]...
  ]
```

Important notes about the format:

> Pattern matching is performed for repositories, components and versions, allowing them 
> to contain wildcards.

And:

> If a definition only includes numbers and underscores, it is parsed by YAML as 
> an integer and all underscores are stripped. E.g. 1.7\_4 will be parsed as 1.74. 
> To ensure the object becomes a string, it should be surrounded by quotes.
>
> See https://docs.saltstack.com/en/develop/topics/troubleshooting/yaml_idiosyncrasies.html

## Examples

### nexus-cleanup.yaml - default version limit only

```
default:
  max_versions: 5
  last_downloaded:
    amount: -1
    unit: day
```
Only the 5 latest component versions in all repositories are retained.

### nexus-cleanup.yaml - default time limit only

```
default:
  max_versions: 0
  last_downloaded:
    amount: 30
    unit: day
```
All component versions in all repositories that have not been downloaded in the last 30 days are removed.

### nexus-cleanup.yaml - override for repository

```
default:
  max_versions: 5
  last_downloaded:
    amount: 30
    unit: day
 
team-x-docker-internal:
  last_downloaded:
    amount: 40
    unit: day
```
Only 5 tags of all component versions in all repositories are retained, unless 
they have been downloaded in the last 30 days. Cleaning is overridden for the 
component versions in the "team-x-docker-internal" repository, which are retained 
for at least 40 days.

```
default:
  max_versions: 5
  last_downloaded:
    amount: 30
    unit: day
 
team-x-docker-internal:
  keep: forever
```
Only 5 tags of all component versions in all repositories are retained, unless 
they have been downloaded in the last 30 days. Cleaning is overridden for the 
component versions in the "team-x-docker-internal" repository, which are retained forever.

### nexus-cleanup.yaml - full example

```
default:
  max_versions: 5
  last_downloaded:
    amount: 30
    unit: day
 
team-x-docker-internal:
  last_downloaded:
    amount: 40
    unit: day
  mygrp/build-template-backend:
    last_downloaded:
      amount: 50
      unit: day
    1.3.*:
      keep: forever
    1.2.*:
      last_downloaded:
        amount: 60
        unit: day
  mygrpv/build-.*:
    last_downloaded:
      amount: 50
      unit: day
    1.3.*:
      keep: forever
    1.2.*:
      last_downloaded:
        amount: 50
        unit: day
```

### Regular expressions to be used for versions

To match a certain pattern in a version, you can use a regular expression.

The regular expression `"^(.*)(BUILD|SNAPSHOT)(.*)$"` matches all versions that contain either the word
"BUILD" or "SNAPSHOT". 
Or `"^((?!SNAPSHOT|BUILD).)*$"` matches all versions that do not contain the words "BUILD" or "SNAPSHOT".
By using the following nexus-clean.yaml, it is possible to set a retention period of 1 day and 1 version for
all versions, but keep the versions that do not contain "BUILD" or "SNAPSHOT" for at least one year.

```
default:
  max_versions: 5
  last_downloaded:
    amount: 30
    unit: day

my-repo:
  mygrp/myapp-.*:
    max_versions: 1
    last_downloaded:
      amount: 1
      unit: day
    "^((?!SNAPSHOT|BUILD).)*$":
      last_downloaded:
        amount: 1
        unit: year
```

See also: https://www.regextester.com/

## How to retrieve config file values

### Docker, Npm and NuGet repositories

Retrieving the values for Docker, Npm and NuGet repositories is straight forward: the values 
you are looking for are labeled `Repository`, `Component Name` and `Component version` 
in the Nexus Repository Manager.

```
default:
...

 
team-y-docker-internal:
  cm/haproxy:
    "1.7_4":
      keep: forever
```

### Maven2 repositories

The configuration file values for Maven2 repositories can be found in the 
Nexus Repository Manager with the following labels: `Repository`, `Name` (corresponds 
to component in the config file) and `Version`. Name and Version correspond 
with the `<artifactId/>` and `<version/>` values in the Maven POM file.

```
default:
...
  
team-z-mvn-release:
  application.assembly.install.complete:
    "7.110.0.4":
      keep: forever
```

## Maintaining the configuration file (nexus-cleaning.yaml)

The [nexus-cleanup.yaml](src/main/resources/nexus-cleanup.yaml) file is stored 
in this Git repository.  In order to make changes to the file, you will have to 
create a feature branch in this repository, which is derived from the master 
branch. When finished, you can create a merge request for the Configuration 
Management team. After testing, your branch will be merged to the master branch 
and will then be discarded.

## How to deploy and run the cleaning script

### Deploy

The scripts need to be installed on the nexus server in a folder that can be accessed by NXRM3
for example in /opt/sonatype/sonatype-work/nexus3/cm/scripts folder.

### Run

Configure a task in Nexus with the following content:

```
import groovy.lang.GroovyClassLoader
def gcl = this.class.classLoader
gcl.clearCache()
gcl.addClasspath("/opt/sonatype/sonatype-work/nexus3/cm/scripts/groovy")                                         
def configReaderClass = gcl.loadClass("cm.nexus.cleanup.ConfigReader")
def deleteAssetsClass = gcl.loadClass("cm.nexus.cleanup.DeleteAssets")
def config = configReaderClass.readConfig("/opt/sonatype/sonatype-work/nexus3/cm/scripts/resources")

def cleaner =  deleteAssetsClass.newInstance(log, repository, "com/.*", ".*", (String[]) ["team1-mvn-release"], config)
cleaner.dryRun()
```
Above example logs which assets will be deleted if the cleaning is run.
To actually clean, change the last statement to:
```
cleaner.clean()

```

## License

This project is licensed under the terms of [the MIT License](LICENSE.md).
