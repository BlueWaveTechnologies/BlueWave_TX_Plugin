package bluewave.neo4j.plugins;

import org.neo4j.annotations.service.ServiceProvider;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.kernel.availability.AvailabilityGuard;
import org.neo4j.kernel.availability.AvailabilityListener;
import org.neo4j.kernel.extension.ExtensionFactory;
import org.neo4j.kernel.extension.ExtensionType;
import org.neo4j.kernel.extension.context.ExtensionContext;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.kernel.lifecycle.Lifecycle;
import org.neo4j.kernel.lifecycle.LifecycleAdapter;
import org.neo4j.logging.internal.LogService;

import javaxt.json.JSONObject;
import static javaxt.utils.Console.console;


//******************************************************************************
//**  Neo4JTransactionEventListenerExtensionFactory Class
//******************************************************************************
/**
 *   Entry point for the plugin. Used to watch for server events.
 *
 ******************************************************************************/

@ServiceProvider
public class Neo4JTransactionEventListenerExtensionFactory
    extends ExtensionFactory<Neo4JTransactionEventListenerExtensionFactory.Dependencies> {

    private Logger logger;
    private Metadata meta;


  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public Neo4JTransactionEventListenerExtensionFactory() {
        super(ExtensionType.DATABASE, "neo4JTransactionEventHandler");

      //Find and parse config file
        javaxt.io.Jar jar = new javaxt.io.Jar(this);
        java.io.File pluginDir = jar.getFile().getParentFile();
        javaxt.io.File configFile = new javaxt.io.File(pluginDir, "config.json");
        JSONObject config = null;
        try { config = new JSONObject(configFile.getText()); }
        catch (Exception e) { console.log(e.getMessage()); }


      //Instantiate logger and metadata classes
        if (config!=null){

            if (logger==null)
            try { logger = getLogger(config.get("logging").toJSONObject()); new Thread(logger).start(); }
            catch (Exception e) { console.log(e.getMessage()); }

            if (meta==null)
            try { meta = getMetadata(config.get("metadata").toJSONObject()); }
            catch (Exception e) { console.log(e.getMessage()); }
        }
    }


  //**************************************************************************
  //** newInstance
  //**************************************************************************
  /** Used to instantiate a Lifecycle listener
   */
    @Override
    public Lifecycle newInstance(final ExtensionContext extensionContext, final Dependencies dependencies) {


        final GraphDatabaseAPI db = dependencies.graphdatabaseAPI();
        final LogService log = dependencies.log();
        final DatabaseManagementService databaseManagementService = dependencies.databaseManagementService();
        final AvailabilityGuard availabilityGuard = dependencies.availabilityGuard();



        availabilityGuard.addListener(new AvailabilityListener() {

            @Override
            public void available() {
                if (dependencies.graphdatabaseAPI().databaseName().compareTo("system") != 0) {
                    try{
                        meta.setDatabaseService(db);
                        meta.init(); //this will hang the server for a bit
                    }
                    catch(Exception e){
                        if (meta!=null) console.log("Failed to initialize metadata");
                    }
                }
            }

            @Override
            public void unavailable() {
                if (dependencies.graphdatabaseAPI().databaseName().compareTo("system") != 0) {
                    if (meta!=null) meta.stop();
                }
            }

        });
        return new CustomGraphDatabaseLifecycle(log, db, dependencies, databaseManagementService, logger, meta);
    }


  //**************************************************************************
  //** Dependencies
  //**************************************************************************
    interface Dependencies {
        GraphDatabaseAPI graphdatabaseAPI();
        DatabaseManagementService databaseManagementService();
        AvailabilityGuard availabilityGuard();
        LogService log();
    }


  //**************************************************************************
  //** CustomGraphDatabaseLifecycle
  //**************************************************************************
    public static class CustomGraphDatabaseLifecycle extends LifecycleAdapter {
        private final GraphDatabaseAPI db;
        private LogService log;
        private Neo4JTransactionEventListener transactionEventhandler;
        private final DatabaseManagementService databaseManagementService;
        private Logger logger;
        private Metadata metadata;

        public CustomGraphDatabaseLifecycle(
            final LogService log,
            final GraphDatabaseAPI db,
            final Dependencies dependencies,
            final DatabaseManagementService databaseManagementService,
            Logger logger, Metadata metadata){

            this.db = db;
            this.databaseManagementService = databaseManagementService;
            this.log = log;
            this.logger = logger;
            this.metadata = metadata;
        }

        @Override
        public void start() {
            if (db.databaseName().compareTo("system") != 0) {
                transactionEventhandler = new Neo4JTransactionEventListener(db, log, logger, metadata);
                databaseManagementService.registerTransactionEventListener(
                    db.databaseName(), transactionEventhandler
                );
            }
        }

        @Override
        public void shutdown() {
            databaseManagementService.unregisterTransactionEventListener(
                db.databaseName(), transactionEventhandler
            );
        }
    }


  //**************************************************************************
  //** getLogger
  //**************************************************************************
    private Logger getLogger(JSONObject config) throws Exception {


      //Get path to the log file directory
        javaxt.io.Directory logDir;
        try {
            logDir = new javaxt.io.Directory(config.get("local").get("path").toString());
        }
        catch (Exception e) {
            logDir = null;
        }


      //Get database config
        javaxt.sql.Database database;
        try {
            JSONObject json = config.get("database").toJSONObject();
            String path = json.get("path").toString().replace("\\", "/");
            javaxt.io.Directory dbDir = new javaxt.io.Directory(path);
            dbDir.create();
            path = new java.io.File(dbDir.toString() + "database").getCanonicalPath();

            database = new javaxt.sql.Database();
            database.setDriver("H2");
            database.setHost(path);
            database.setConnectionPoolSize(25);
        }
        catch (Exception e) {
            database = null;
        }

      //Get webserver config
        JSONObject webserver;
        try {
            webserver = config.get("webserver").toJSONObject();
        }
        catch (Exception e) {
            webserver = null;
        }


        if (logDir==null && database==null && webserver==null) return null;


      //Instantiate logger
        Logger logger = new Logger();
        if (logDir!=null) logger.setDirectory(logDir);
        if (database!=null) logger.setDatabase(database);
        if (webserver!=null) logger.setWebServer(webserver);
        return logger;
    }


  //**************************************************************************
  //** getMetadata
  //**************************************************************************
    private Metadata getMetadata(JSONObject config){

        Metadata metadata = new Metadata();

        try{ metadata.setNodeName(config.get("node").toString()); }
        catch(Exception e){}

        return metadata;
    }
}
