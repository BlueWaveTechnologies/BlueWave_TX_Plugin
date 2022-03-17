package bluewave.neo4j.plugins;


import static javaxt.utils.Console.console;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;

import javaxt.utils.Date;

public class MetadataTest {
    public static final String GET_BLUEWAVE_METADATA_NODE_SQL = "MATCH (n:%s) return n";
    private static final Config driverConfig = Config.builder().withoutEncryption().build();
    private static Driver driver;
    private Neo4j embeddedDatabaseServer;


    @Test
    public void test_init() {
        console.log("********************************************************************************");
        console.log("***************************** NEW TEST *****************************************");
        console.log("**                                                                            **");
        console.log("** test_init()                                                                **");
        console.log("**                                                                            **");
        console.log("**                                                                            **");

        GraphDatabaseService dbService = embeddedDatabaseServer.defaultDatabaseService();

        try {
            // Initial data has been loaded. Sleep so sync can work.
            console.log("Test sleep: " + new Date().toString("EEE MMM dd HH:mm:ss z yyyy"));
            TimeUnit.SECONDS.sleep(40);
        }catch(Exception e) {
          console.log(e);
        } finally {
            console.log("Test sleep finished. " + new Date().toString("EEE MMM dd HH:mm:ss z yyyy"));
        }

        printMetadataNodeContents(dbService);
    }


    @Test
    public void test_Add_Property() {
        console.log("********************************************************************************");
        console.log("***************************** NEW TEST *****************************************");
        console.log("**                                                                            **");
        console.log("** test_Add_Property()                                                        **");
        console.log("**                                                                            **");
        console.log("**                                                                            **");

        GraphDatabaseService dbService = embeddedDatabaseServer.defaultDatabaseService();

        // Add properties
        try (Transaction tx = dbService.beginTx()) {
            tx.execute("MATCH (n:TESTLABEL1) SET n.color = 'blue' return n");
            tx.execute("MATCH (n:TESTLABEL1) SET n.car = 'toyota' return n");
            tx.execute("MATCH (n:TESTLABEL1) SET n.language = 'java' return n");
            tx.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            // Initial data has been loaded. Sleep so sync can work.
            console.log("Test sleep: " + new Date().toString("EEE MMM dd HH:mm:ss z yyyy"));
            TimeUnit.SECONDS.sleep(80);
        }catch(Exception e) {
            e.printStackTrace();
        } finally {
            console.log("Test sleep finished. " + new Date().toString("EEE MMM dd HH:mm:ss z yyyy"));
        }

         printMetadataNodeContents(dbService);
    }

    @Test
    public void test_Remove_Property() {
        console.log("********************************************************************************");
        console.log("***************************** NEW TEST *****************************************");
        console.log("**                                                                            **");
        console.log("** test_Remove_Property()                                                     **");
        console.log("**                                                                            **");
        console.log("**                                                                            **");

        GraphDatabaseService dbService = embeddedDatabaseServer.defaultDatabaseService();

        // Remove properties
        try (Transaction tx = dbService.beginTx()) {
            tx.execute("MATCH (n:TESTLABEL1) REMOVE n.prop1 return n");
            tx.execute("MATCH (n:TESTLABEL2) REMOVE n.prop1 return n");
            tx.execute("MATCH (n:TESTLABEL3) REMOVE n.prop1 return n");
            tx.execute("MATCH (n:TESTLABEL4) REMOVE n.prop1 return n");
            tx.execute("MATCH (n:TESTLABEL5) REMOVE n.prop1 return n");
            tx.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            // Initial data has been loaded. Sleep so sync can work.
            console.log("Test sleep: " + new Date().toString("EEE MMM dd HH:mm:ss z yyyy"));
            TimeUnit.SECONDS.sleep(80);
        }catch(Exception e) {
            e.printStackTrace();
        } finally {
            console.log("Test sleep finished. " + new Date().toString("EEE MMM dd HH:mm:ss z yyyy"));
        }

         printMetadataNodeContents(dbService);
    }    

    @Test
    public void test_Add_Relationships() {
        console.log("********************************************************************************");
        console.log("***************************** NEW TEST *****************************************");
        console.log("**                                                                            **");
        console.log("**     test_Add_Relationships                                                 **");
        console.log("**                                                                            **");
        console.log("**                                                                            **");

        GraphDatabaseService dbService = embeddedDatabaseServer.defaultDatabaseService();

        // Add relationship
        try (Transaction tx = dbService.beginTx()) {
            tx.execute("MATCH (s:TESTLABEL1), (e:TESTLABEL2) CREATE (s)-[r:FRIEND]->(e) return type(r)");
            tx.execute("MATCH (s:TESTLABEL1), (e:TESTLABEL3) CREATE (s)-[r:FRIEND]->(e) return type(r)");
            tx.execute("MATCH (s:TESTLABEL1), (e:TESTLABEL4) CREATE (s)-[r:FRIEND]->(e) return type(r)");
            tx.execute("MATCH (s:TESTLABEL1), (e:TESTLABEL5) CREATE (s)-[r:FRIEND]->(e) return type(r)");

            tx.execute("MATCH (s:TESTLABEL5), (e:TESTLABEL1) CREATE (s)-[r:ENEMY]->(e) return type(r)");
            tx.execute("MATCH (s:TESTLABEL5), (e:TESTLABEL2) CREATE (s)-[r:ENEMY]->(e) return type(r)");
            tx.execute("MATCH (s:TESTLABEL5), (e:TESTLABEL3) CREATE (s)-[r:ENEMY]->(e) return type(r)");
            tx.execute("MATCH (s:TESTLABEL5), (e:TESTLABEL4) CREATE (s)-[r:ENEMY]->(e) return type(r)");
            tx.commit();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        try {
            // Initial data has been loaded. Sleep so sync can work.
            console.log("Test sleep: " + new Date().toString("EEE MMM dd HH:mm:ss z yyyy"));
            TimeUnit.SECONDS.sleep(80);
        }catch(Exception e) {
            e.printStackTrace();
        } finally {
            console.log("Test sleep finished. " + new Date().toString("EEE MMM dd HH:mm:ss z yyyy"));
        }

         printMetadataNodeContents(dbService);
    }

    @Test
    public void test_Remove_Relationships() {
        console.log("********************************************************************************");
        console.log("***************************** NEW TEST *****************************************");
        console.log("**                                                                            **");
        console.log("**     test_Remove_Relationships                                              **");
        console.log("**                                                                            **");
        console.log("**                                                                            **");

        GraphDatabaseService dbService = embeddedDatabaseServer.defaultDatabaseService();

        // Add relationship
        try (Transaction tx = dbService.beginTx()) {
            tx.execute("MATCH (s:TESTLABEL1), (e:TESTLABEL2) CREATE (s)-[r:FRIEND]->(e) return type(r)");
            tx.execute("MATCH (s:TESTLABEL1), (e:TESTLABEL3) CREATE (s)-[r:FRIEND]->(e) return type(r)");
            tx.execute("MATCH (s:TESTLABEL1), (e:TESTLABEL4) CREATE (s)-[r:FRIEND]->(e) return type(r)");
            tx.execute("MATCH (s:TESTLABEL1), (e:TESTLABEL5) CREATE (s)-[r:FRIEND]->(e) return type(r)");

            tx.execute("MATCH (s:TESTLABEL5), (e:TESTLABEL1) CREATE (s)-[r:ENEMY]->(e) return type(r)");
            tx.execute("MATCH (s:TESTLABEL5), (e:TESTLABEL2) CREATE (s)-[r:ENEMY]->(e) return type(r)");
            tx.execute("MATCH (s:TESTLABEL5), (e:TESTLABEL3) CREATE (s)-[r:ENEMY]->(e) return type(r)");
            tx.execute("MATCH (s:TESTLABEL5), (e:TESTLABEL4) CREATE (s)-[r:ENEMY]->(e) return type(r)");
            tx.commit();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        try {
            // Initial data has been loaded. Sleep so sync can work.
            console.log("Test sleep: " + new Date().toString("EEE MMM dd HH:mm:ss z yyyy"));
            TimeUnit.SECONDS.sleep(80);
        }catch(Exception e) {
            e.printStackTrace();
        } finally {
            console.log("Test sleep finished. " + new Date().toString("EEE MMM dd HH:mm:ss z yyyy"));
        }

        printMetadataNodeContents(dbService);

        // Delete relationship
        try (Transaction tx = dbService.beginTx()) {
            tx.execute("MATCH (n:TESTLABEL5)-[r:ENEMY]->() DELETE r");
            tx.commit();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        try {
            // Initial data has been loaded. Sleep so sync can work.
            console.log("Test sleep: " + new Date().toString("EEE MMM dd HH:mm:ss z yyyy"));
            TimeUnit.SECONDS.sleep(80);
        }catch(Exception e) {
            e.printStackTrace();
        } finally {
            console.log("Test sleep finished. " + new Date().toString("EEE MMM dd HH:mm:ss z yyyy"));
        }

        printMetadataNodeContents(dbService);        

    }    


    @Test
    public void test_Add_Node() {
        console.log("********************************************************************************");
        console.log("***************************** NEW TEST *****************************************");
        console.log("**                                                                            **");
        console.log("** test_Add_Node()                                                            **");
        console.log("**                                                                            **");
        console.log("**                                                                            **");

        GraphDatabaseService dbService = embeddedDatabaseServer.defaultDatabaseService();

        // Add node
        try (Transaction tx = dbService.beginTx()) {
            tx.execute("CREATE (n1:TESTLABEL1{prop1:'val2'})");
            tx.execute("CREATE (n1:TESTLABEL1{prop1:'val3'})");
            tx.execute("CREATE (n1:TESTLABEL1{prop1:'val4'})");
            tx.execute("CREATE (n1:TESTLABEL1{prop1:'val5'})");
            tx.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            // Initial data has been loaded. Sleep so sync can work.
            console.log("Test sleep: " + new Date().toString("EEE MMM dd HH:mm:ss z yyyy"));
            TimeUnit.SECONDS.sleep(80);
        }catch(Exception e) {
            e.printStackTrace();
        } finally {
            console.log("Test sleep finished. " + new Date().toString("EEE MMM dd HH:mm:ss z yyyy"));
        }

         printMetadataNodeContents(dbService);
    }    

    /**
     * Run this test:
     * mvn -Dtest=MetadataTest#test_Remove_Node test
     */
    @Test
    public void test_Remove_Node() {
        console.log("********************************************************************************");
        console.log("***************************** NEW TEST *****************************************");
        console.log("**                                                                            **");
        console.log("** test_Remove_Node()                                                         **");
        console.log("**                                                                            **");
        console.log("**                                                                            **");

        GraphDatabaseService dbService = embeddedDatabaseServer.defaultDatabaseService();
        // Create index
        // try (Transaction tx = dbService.beginTx()) {
        //     tx.execute("CREATE INDEX ON :TESTLABEL1(prop1)");
        //     tx.commit();
        // } catch (Exception e) {
        //     e.printStackTrace();
        //     fail();
        // }
        // Add node
        try (Transaction tx = dbService.beginTx()) {
            tx.execute("CREATE (n1:TESTLABEL1{prop1:'val2'})");
            tx.execute("CREATE (n2:TESTLABEL1{prop1:'val3'})");
            tx.execute("CREATE (n3:TESTLABEL1{prop1:'val4'})");
            tx.execute("CREATE (n4:TESTLABEL1{prop1:'val5'})");
            tx.commit();
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }

        try {
            // Initial data has been loaded. Sleep so sync can work.
            console.log("Test sleep: " + new Date().toString("EEE MMM dd HH:mm:ss z yyyy"));
            TimeUnit.SECONDS.sleep(80);
        }catch(Exception e) {
            e.printStackTrace();
        } finally {
            console.log("Test sleep finished. " + new Date().toString("EEE MMM dd HH:mm:ss z yyyy"));
        }

         printMetadataNodeContents(dbService);

        // Remove node
        try (Transaction tx = dbService.beginTx()) {
            console.log("Calling DELETE node");
            tx.execute("MATCH (n:TESTLABEL1) where n.prop1='val1' DELETE n");
            // tx.execute("MATCH (n:TESTLABEL1 { prop1:'val1' }) DELETE n");
            tx.commit();
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }

        try {
            // Initial data has been loaded. Sleep so sync can work.
            console.log("Test sleep: " + new Date().toString("EEE MMM dd HH:mm:ss z yyyy"));
            TimeUnit.SECONDS.sleep(80);
        }catch(Exception e) {
            e.printStackTrace();
        } finally {
            console.log("Test sleep finished. " + new Date().toString("EEE MMM dd HH:mm:ss z yyyy"));
        }

         printMetadataNodeContents(dbService);   
         
        try {
            // Initial data has been loaded. Sleep so sync can work.
            console.log("Test sleep: " + new Date().toString("EEE MMM dd HH:mm:ss z yyyy"));
            TimeUnit.SECONDS.sleep(5);
        }catch(Exception e) {
            e.printStackTrace();
        } finally {
            console.log("Test sleep finished. " + new Date().toString("EEE MMM dd HH:mm:ss z yyyy"));
        }
    }      

    private void printMetadataNodeContents(GraphDatabaseService dbService) {
        String sql = String.format(GET_BLUEWAVE_METADATA_NODE_SQL, Metadata.META_NODE_LABEL);
       console.log(sql);
        try (Transaction tx = embeddedDatabaseServer.defaultDatabaseService().beginTx()) {
            Result result = tx.execute(sql);
            if(result.hasNext()) {
                Map<String, Object> record = result.next();
                Node metaNode = (Node) record.get("n");
                console.log("** node contents: \n\n" + metaNode.getProperties("nodes").toString() + "\n");
            } else {
                fail("###### Error retrieving metadata node. ######");
            }
            tx.close();
        } catch (Exception e) {
            fail("ERROR -> " + e);
        }
    }

    @BeforeEach
    void initializeNeo4j() {
        try {
            /**
             * Load test data.
             *
             */
            final String createSql = "CREATE (n1:TESTLABEL1{prop1:'val1'})," 
                    + " (n2:TESTLABEL2{prop1:'val1', prop2:'val2'}),"
                    + " (n3:TESTLABEL3{prop1:'val1', prop2:'val2', prop3:'val3'}), " 
                    + " (n4:TESTLABEL4{prop1:'val1', prop2:'val2', prop3:'val3', prop4:'val4'}), "
                    + " (n5:TESTLABEL5{prop1:'val1', prop2:'val2', prop3:'val3', prop4:'val4', prop5:'val5'}) "; 
                    
            this.embeddedDatabaseServer = Neo4jBuilders.newInProcessBuilder()
                    .withDisabledServer()
                    .build();

            this.driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI(), driverConfig);
            GraphDatabaseService dbService = embeddedDatabaseServer.defaultDatabaseService();

            while (!dbService.isAvailable(500)) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (Throwable t) {
                }
            }

            try (Transaction tx = dbService.beginTx()) {
                tx.execute(createSql);
                tx.commit();
                console.log("Initial data fill success. ");
            } catch (Exception e) {
                e.printStackTrace();
                fail("Error: " + e);
            }

            try (Transaction tx = dbService.beginTx()) {
                tx.execute("CREATE INDEX ON :TESTLABEL1(prop1)");
                tx.commit();
            } catch (Exception e) {
                e.printStackTrace();
                fail();
            }

        } catch (Throwable e) {
            e.printStackTrace();
            fail("Error: " + e);
        }
    }

    @AfterEach
    void cleanDb() {

        /**
         * Wipe out all db data
         */
        GraphDatabaseService db = this.embeddedDatabaseServer.defaultDatabaseService();
        console.log("** ");
        console.log("** ");
        console.log("**                                                                            **");
        console.log("**                                                                            **");
        console.log("********************************************************************************");
        console.log("********************************************************************************");
        console.log("******** Ignore all errors below this statement, DB is in shutdown mode. *******");
        try (org.neo4j.driver.Transaction tx = driver.session().beginTransaction()) {
            tx.run("MATCH (n) DETACH DELETE n");
            tx.close();
        } catch (Exception e) {
            console.log("ERROR: " + e);
        } finally {
            console.log("*** TESTS COMPLETE ***");
            this.embeddedDatabaseServer.close();
        }
    }

}
