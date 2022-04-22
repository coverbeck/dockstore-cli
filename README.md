[![Codacy Badge](https://app.codacy.com/project/badge/Grade/c583635906d84de5a1b5f62068fc26be)](https://www.codacy.com/gh/dockstore/dockstore-cli/dashboard?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=dockstore/dockstore-cli&amp;utm_campaign=Badge_Grade)
[![codecov](https://codecov.io/gh/dockstore/dockstore-cli/branch/master/graph/badge.svg)](https://codecov.io/gh/dockstore/dockstore-cli)
[![Website](https://img.shields.io/website/https/dockstore.org.svg)](https://dockstore.org)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/ga4gh/dockstore?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)  
[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.2630727.svg)](https://doi.org/10.5281/zenodo.2630727)
[![license](https://img.shields.io/hexpm/l/plug.svg?maxAge=2592000)](LICENSE)
[![CircleCI](https://circleci.com/gh/dockstore/dockstore-cli/tree/develop.svg?style=svg)](https://circleci.com/gh/dockstore/dockstore-cli/tree/develop)
[![Documentation Status](https://readthedocs.org/projects/dockstore/badge/?version=develop)](https://dockstore.readthedocs.io/en/develop/?badge=develop)


# Dockstore

Dockstore provides a place for users to share tools encapsulated in Docker and described with the Common 
Workflow Language (CWL), WDL (Workflow Description Language), or Nextflow. This enables scientists to share analytical 
workflows so that they are  machine readable as well as runnable in a variety of environments. While the 
Dockstore is focused on serving researchers in the biosciences, the combination of Docker + CWL/WDL can be used by 
anyone to describe the tools and services in their Docker images in a standardized, machine-readable way.  
We hope to use this project as motivation to create a GA4GH API standard for container registries.

For the live site see [dockstore.org](https://dockstore.org)

This repo contains the CLI components for Dockstore  

For the main repo see [dockstore](https://github.com/dockstore/dockstore).
For the related web UI see [dockstore-ui](https://github.com/dockstore/dockstore-ui2).

## For Dockstore Users

The following section is useful for users of Dockstore (e.g. those that want to browse, register, and 
launch tools). 

After registering at [dockstore.org](https://dockstore.org), you will be able to download the Dockstore 
CLI at https://dockstore.org/onboarding

### Configuration File

A basic Dockstore configuration file is available/should be created in `~/.dockstore/config` and contains the following
at minimum:
```
token = <your generated by the dockstore site>
server-url = https://www.dockstore.org/api
```

### File Provisioning

By default, cwltool reads input files from the local filesystem. Dockstore also adds support for additional file systems
such as http, https, and ftp. Through a plug-in system, Dockstore also supports 
the Amazon S3, [Synapse](http://docs.synapse.org/articles/downloading_data.html), and 
[ICGC Score Client](https://docs.icgc.org/download/guide/#score-client-usage) via [plugins](https://github.com/dockstore).

Download the above set of default plugins via: 
```
dockstore plugin download
```

Configuration for plugins can be placed inside the Dockstore configuration file in the following format

```
token = <your generated by the dockstore site>
server-url = https://www.dockstore.org/api

# options below this are optional

use-cache = false                           #set this to true to cache input files for rapid development
cache-dir = /home/<user>/.dockstore/cache   #set this to determine where input files are cached (should be the same filesystem as your tool working directories)

[dockstore-file-synapse-plugin]

[dockstore-file-s3-plugin]
endpoint = #set this to point at a non AWS S3 endpoint

[dockstore-file-icgc-storage-client-plugin]
client = /media/large_volume/icgc-storage-client-1.0.23/bin/icgc-storage-client
```

Additional plugins can be created by taking one of the repos in [plugins](https://github.com/dockstore) as a model and 
using [pf4j](https://github.com/decebals/pf4j) as a reference. See [additional documentation](dockstore-file-plugin-parent) for more details. 

## Development

### Coding Standards

[codestyle.xml](codestyle.xml) defines the coding style for Dockstore as an IntelliJ Code Style XML file that should be imported into IntelliJ IDE. 
We also have a matching [checkstyle.xml](checkstyle.xml) that can be imported into other IDEs and is run during the build.  

For users of Intellij or comparable IDEs, we also suggest loading the checkstyle.xml with a plugin in order to display warnings and errors while coding live rather than encountering them later when running a build. 

### Dockstore Command Line

The dockstore command line should be installed in a location in your path.

  /dockstore-client/bin/dockstore

You then need to setup a `~/.dockstore/config` file with the following contents:

```
token: <dockstore_token_from_web_app>
server-url: http://www.dockstore.org:8080
```

If you are working with a custom-built or updated dockstore client you will need to update the jar in: `~/.dockstore/config/self-installs`.

### Encrypted Documents for Travis-CI

Encrypted documents necessary for confidential testing are handled as indicated in the documents at Travis-CI for  
[files](https://docs.travis-ci.com/user/encrypting-files/#Encrypting-multiple-files) and [environment variables](https://docs.travis-ci.com/user/encryption-keys).

A convenience script is provided as encrypt.sh which will compress confidential files, encrypt them, and then update an encrypted archive on GitHub. Confidential files should also be added to .gitignore to prevent accidental check-in. The unencrypted secrets.tar should be privately distributed among members of the team that need to work with confidential data. When using this script you will likely want to alter the [CUSTOM\_DIR\_NAME](https://github.com/dockstore/dockstore/blob/0b59791440af6e3d383d1aede1774c0675b50404/encrypt.sh#L13). This is necessary since running the script will overwrite the existing encryption keys, instantly breaking existing builds using that key. Our current workaround is to use a new directory when providing a new bundle. 

### Adding Copyright header to all files with IntelliJ

To add copyright headers to all files with IntelliJ

1. Ensure the Copyright plugin is installed (Settings -> Plugins)
2. Create a new copyright profile matching existing copyright header found on all files, name it Dockstore (Settings -> Copyright -> Copyright Profiles -> Add New)
3. Set the default project copyright to Dockstore (Settings -> Copyright)
