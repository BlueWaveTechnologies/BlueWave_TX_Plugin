# Neo4J Plugin for Project BlueWave

Custom plugin for Neo4J used to log transactions and save metadata.


# Installation
Neo4J plugins are simply jar files that are copied into the Neo4J plugins folder.
As of this writing, there is no "hot-deploy" option for Neo4J so you will need
to restart the Neo4J server whenever you deploy a new plugin.


# Config.json
The plugin can be configured with an external config file (config.json) to enable logging and tell 
the plugin where to log transaction information (e.g. log files, database, or a REST endpoint). 
The plugin assumes that the config file is installed in the Neo4J plugins folder,
alongside the jar file.

The following is an example of a config file. Logger, database, and webserver
are all optional.

```javascript
{
    "logging" : {

        "local" : {
            "path" : "/bluewave/logs"
        },

        "database" : {
            "driver" : "H2",
            "path" : "/bluewave/logs"
        },

        "webserver" : {
            "host" : "http://bluewave.tech/graph/update",
            "username" : "neo4j",
            "password" : "password"
        }

    },

    "metadata" : {
        "node": "bluewave_metadata"
    }
}
```


# What Gets Logged

- timestamp: system time in nanoseconds
- action: "create" or "delete"
- type: "node", "relationship", etc.
- data: JSON array with a Neo4J assigned ID and summary metadata
- user: Neo4J user that committed the transaction

