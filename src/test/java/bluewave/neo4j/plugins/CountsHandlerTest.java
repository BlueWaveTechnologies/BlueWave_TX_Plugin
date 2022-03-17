package bluewave.neo4j.plugins;
import static bluewave.neo4j.plugins.CountsHandler.KEY_COUNTS;

import static javaxt.utils.Console.console;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;

import javaxt.json.JSONArray;
import javaxt.json.JSONObject;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CountsHandlerTest {
    public static final String GET_BLUEWAVE_METADATA_NODE_SQL = "MATCH (n:bluewave_metadata) return n";
    private static final Config driverConfig = Config.builder().withoutEncryption().build();
    private static Driver driver;
    private Neo4j embeddedDatabaseServer;

    @Test
    public void test_Delete_Relationship_To_Existing_Node() {
        console.log("********************************************************************************");
        console.log("***************************** NEW TEST *****************************************");
        console.log("**                                                                            **");
        console.log("** test_Delete_Relationship_To_Existing_Node()                                **");
        console.log("**                                                                            **");
        console.log("**                                                                            **");

        GraphDatabaseService dbService = embeddedDatabaseServer.defaultDatabaseService();
        /**
         * Confirm test data was added
         */
        console.log("** Test data loaded.");
        printResultsToConsole(dbService);

        /**
         * Need to create the relationships first
         */
        final String updateSql = "MATCH (s:TESTNODE), (e:TESTLABEL ) CREATE (s)-[r:FRIENDS]->(e) return r";

        console.log("** Create the relationships");
        console.log("** " + updateSql);

        /**
         * Update
         */
        try (Transaction tx = dbService.beginTx()) {
            tx.execute(updateSql);
            tx.commit();
        } catch (Exception e) {
            fail("ERROR: Failed creating relationships: " + e);
        }

        printResultsToConsole(dbService);

        /**
         * Confirm additional label 'NEWLABEL' on nodes with :TESTNODE.
         */
        try (Transaction tx = dbService.beginTx()) {
            Result result = tx.execute(GET_BLUEWAVE_METADATA_NODE_SQL);

            assertTrue(result.hasNext());
            Map<String, Object> record = result.next();
            Node metaNode = (Node) record.get("n");
            Predicate<Object> hasTESTLABEL = l -> String.valueOf(l).equals("TESTLABEL");
            JSONObject countsJSONObject = new JSONObject(metaNode.getProperty(KEY_COUNTS).toString());
            countsJSONObject.keySet().forEach(key -> {
                JSONArray labels = countsJSONObject.get(key).get(CountsHandler.INDEX_LABELS).toJSONArray();
                boolean isTESTLABELPresent = StreamSupport
                        .stream(labels.spliterator(), false)
                        .filter(hasTESTLABEL)
                        .collect(Collectors.toList())
                        .size() > 0;

                long relations = countsJSONObject.get(key).get(CountsHandler.INDEX_RELATIONS).toLong();
                if (isTESTLABELPresent) {
                    assertTrue(relations == 2);
                } else {
                    assertTrue(relations == 0);
                }
            });

        } catch (Exception e) {
            fail("ERROR -> " + e.toString());
        }

        /**
         * Now that we created and confirmed the relationships, let's delete them
         */

        final String deleteSql = "MATCH (s:TESTNODE)-[r:FRIENDS]->() DELETE r";

        console.log("** Delete the relationships. (This should remove all of them)");
        console.log("** " + deleteSql);

        /**
         * Delete
         */
        try (Transaction tx = dbService.beginTx()) {
            tx.execute(deleteSql);
            tx.commit();
        } catch (Exception e) {
            fail("ERROR: Failed deleting relationships: " + e);
        }

        /**
         * Confirm relationships have all been zero'd out
         */
        try (Transaction tx = dbService.beginTx()) {
            Result result = tx.execute(GET_BLUEWAVE_METADATA_NODE_SQL);

            assertTrue(result.hasNext());
            Map<String, Object> record = result.next();
            Node metaNode = (Node) record.get("n");
            JSONObject countsJSONObject = new JSONObject(metaNode.getProperty(KEY_COUNTS).toString());
            countsJSONObject.keySet().forEach(key -> {
                assertTrue(countsJSONObject.get(key).get(CountsHandler.INDEX_RELATIONS).toLong() == 0);
            });
            tx.close();
        } catch (Exception e) {
            fail("ERROR -> " + e.toString());
        }
    }

    @Test
    public void test_Add_Relationship_To_Existing_Node() {
        console.log("********************************************************************************");
        console.log("***************************** NEW TEST *****************************************");
        console.log("**                                                                            **");
        console.log("** test_Add_Relationship_To_Existing_Node()                                   **");
        console.log("**                                                                            **");
        console.log("**                                                                            **");

        GraphDatabaseService dbService = embeddedDatabaseServer.defaultDatabaseService();
        /**
         * Confirm test data was added
         */
        console.log("** Test data loaded.");
        printResultsToConsole(dbService);

        final String updateSql = "MATCH (s:TESTNODE), (e:TESTLABEL ) CREATE (s)-[r:FRIENDS]->(e) return r";

        console.log("** Add Label To Existing Nodes With LABEL 'TESTNODE' (No change in meta node should occur)");
        console.log("** " + updateSql);

        /**
         * Update
         */
        try (Transaction tx = dbService.beginTx()) {
            tx.execute(updateSql);
            tx.commit();
        } catch (Exception e) {

        }

        /**
         * Confirm additional label 'NEWLABEL' on nodes with :TESTNODE.
         */
        try (Transaction tx = dbService.beginTx()) {
            Result result = tx.execute(GET_BLUEWAVE_METADATA_NODE_SQL);

            assertTrue(result.hasNext());
            Map<String, Object> record = result.next();
            Node metaNode = (Node) record.get("n");
            Predicate<Object> hasTESTLABEL = l -> String.valueOf(l).equals("TESTLABEL");
            JSONObject countsJSONObject = new JSONObject(metaNode.getProperty(KEY_COUNTS).toString());
            countsJSONObject.keySet().forEach(key -> {
                JSONArray labels = countsJSONObject.get(key).get(CountsHandler.INDEX_LABELS).toJSONArray();
                boolean isTESTLABELPresent = StreamSupport
                        .stream(labels.spliterator(), false)
                        .filter(hasTESTLABEL)
                        .collect(Collectors.toList())
                        .size() > 0;

                long relations = countsJSONObject.get(key).get(CountsHandler.INDEX_RELATIONS).toLong();
                if (isTESTLABELPresent) {
                    assertTrue(relations == 2);
                } else {
                    assertTrue(relations == 0);
                }
            });

        } catch (Exception e) {
            fail("ERROR -> " + e.toString());
        }
    }

    @Test
    public void test_Delete_Existing_Label() {
        console.log("********************************************************************************");
        console.log("***************************** NEW TEST *****************************************");
        console.log("**                                                                            **");
        console.log("** test_Delete_Existing_Label()                                               **");
        console.log("**                                                                            **");
        console.log("**                                                                            **");
        GraphDatabaseService dbService = embeddedDatabaseServer.defaultDatabaseService();
        /**
         * Confirm test data was added
         */
        console.log("** Test data loaded.");
        printResultsToConsole(dbService);

        final String deleteSql = "MATCH (n:TESTLABEL:TESTLABEL2) REMOVE n :TESTLABEL return n";

        console.log("** Delete Label 'TESTLABEL' From Node");
        console.log("** " + deleteSql);

        /**
         * Delete
         */
        try (Transaction tx = dbService.beginTx()) {
            tx.execute(deleteSql);
            tx.commit();
        } catch (Exception e) {
            fail("Error: " + e);
        }

        /**
         * Confirm nodes have changed.
         */
        try (Transaction tx = dbService.beginTx()) {
            Result result = tx.execute(GET_BLUEWAVE_METADATA_NODE_SQL);

            assertTrue(result.hasNext());
            Map<String, Object> record = result.next();
            Node metaNode = (Node) record.get("n");
            // JSONObject jsonObjectForDataProperty = new
            // JSONObject(metaNode.getProperty("data").toString());

            /**
             * Let's verify the label has been removed.
             */

            Predicate<Object> isTESTLABELPresent = l -> String.valueOf(l).equals("TESTLABEL");
            Predicate<Object> isTESTLABEL2Present = l -> String.valueOf(l).equals("TESTLABEL2");

            JSONObject countsJSONObject = new JSONObject(metaNode.getProperty(KEY_COUNTS).toString());
            countsJSONObject.keySet().forEach(key -> {

                JSONArray labels = countsJSONObject.get(key).get(CountsHandler.INDEX_LABELS).toJSONArray();
                boolean hasTestLabel2 = StreamSupport
                        .stream(labels.spliterator(), false)
                        .filter(isTESTLABEL2Present)
                        .collect(Collectors.toList())
                        .size() > 0;
                if (hasTestLabel2) {
                    boolean hasTestLabel = StreamSupport
                            .stream(labels.spliterator(), false)
                            .filter(isTESTLABELPresent)
                            .collect(Collectors.toList())
                            .size() > 0;

                    assertFalse(hasTestLabel);
                }
            });

        } catch (Exception e) {
            fail("ERROR -> " + e.toString());
        }
    }

    @Test
    public void test_Delete_All_Nodes_With_Specific_Label() {
        console.log("********************************************************************************");
        console.log("***************************** NEW TEST *****************************************");
        console.log("**                                                                            **");
        console.log("** test_Delete_All_Nodes_With_Specific_Label()                                **");
        console.log("**                                                                            **");
        console.log("**                                                                            **");
        GraphDatabaseService dbService = embeddedDatabaseServer.defaultDatabaseService();
        /**
         * Confirm test data was added
         */
        console.log("** Test data loaded.");
        printResultsToConsole(dbService);

        final String deleteSql = "MATCH (n:TESTLABEL) DETACH DELETE n";

        console.log("** Delete All Nodes With LABEL 'TESTLABEL' ");
        console.log("** " + deleteSql);

        /**
         * Delete
         */
        try (Transaction tx = dbService.beginTx()) {
            tx.execute(deleteSql);
            tx.commit();
        } catch (Exception e) {
            fail("ERROR: " + e);
        }

        /**
         * Confirm nothing has changed.
         */
        try (Transaction tx = dbService.beginTx()) {
            Result result = tx.execute(GET_BLUEWAVE_METADATA_NODE_SQL);

            assertTrue(result.hasNext());
            Map<String, Object> record = result.next();
            Node metaNode = (Node) record.get("n");
            JSONObject jsonObjectForDataProperty = new JSONObject(metaNode.getProperty("data").toString());

            /**
             * Let's verify the label 'TESTLABEL' has been removed from all nodes.
             * This is testing for adverse effects. There should be no changes in the
             * metadata node.
             */

            Predicate<Object> isTESTLABELPresent = l -> String.valueOf(l).equals("TESTLABEL");
            JSONObject nodesJSONObject = jsonObjectForDataProperty.get("nodes").toJSONObject();
            jsonObjectForDataProperty.get("nodes").toJSONObject().keys().forEachRemaining(key -> {
                assertTrue(
                        StreamSupport.stream(nodesJSONObject.get(key).get("labels").toJSONArray().spliterator(),
                                false)
                                .filter(isTESTLABELPresent)
                                .findFirst()
                                .isEmpty());
            });
        } catch (Exception e) {
            fail("ERROR -> " + e.toString());
        }
    }

    @Test
    public void test_Add_Label_To_Existing_Node() {
        console.log("********************************************************************************");
        console.log("***************************** NEW TEST *****************************************");
        console.log("**                                                                            **");
        console.log("** test_Add_Label_To_Existing_Node()                                          **");
        console.log("**                                                                            **");
        console.log("**                                                                            **");

        GraphDatabaseService dbService = embeddedDatabaseServer.defaultDatabaseService();
        /**
         * Confirm test data was added
         */
        console.log("** Test data loaded.");
        printResultsToConsole(dbService);

        final String updateSql = "MATCH (n:TESTNODE) SET n :NEWLABEL return n";

        console.log("** Add Label To Existing Nodes With LABEL 'TESTNODE' ");
        console.log("** " + updateSql);

        /**
         * Update
         */
        try (Transaction tx = dbService.beginTx()) {
            tx.execute(updateSql);
            tx.commit();
        } catch (Exception e) {
            fail("ERROR: " + e);
        }

        /**
         * Confirm additional label 'NEWLABEL' on nodes with :TESTNODE.
         */
        try (Transaction tx = dbService.beginTx()) {
            Result result = tx.execute(GET_BLUEWAVE_METADATA_NODE_SQL);

            assertTrue(result.hasNext());
            Map<String, Object> record = result.next();
            Node metaNode = (Node) record.get("n");

            /**
             * Let's verify the new label 'NEWLABEL' is found on the node with label
             * 'TESTNODE'.
             */

            Predicate<Object> isTESTLABELPresent = l -> String.valueOf(l).equals("TESTNODE");
            Predicate<Object> isNEWLABELPresent = l -> String.valueOf(l).equals("NEWLABEL");

            /**
             * Example structure of counts in metadata
             * (n:bluewave_metadata{"counts":{"534657143":[["label1","labeln"], 1, 15, 4,
             * 2],"7989553324":[["label1","labeln"], 1, 13, 2, 3]}})
             */
            JSONObject countsJSONObject = new JSONObject(metaNode.getProperty(KEY_COUNTS).toString());
            countsJSONObject.keySet().forEach(key -> {

                JSONArray labels = countsJSONObject.get(key).toJSONArray().get(CountsHandler.INDEX_LABELS).toJSONArray();
                boolean hasLabelTESTNODE = StreamSupport
                        .stream(labels.spliterator(), false)
                        .filter(isTESTLABELPresent)
                        .collect(Collectors.toList())
                        .size() > 0;

                boolean hasLabelNEWLABEL = StreamSupport
                        .stream(labels.spliterator(), false)
                        .filter(isNEWLABELPresent)
                        .collect(Collectors.toList())
                        .size() > 0;

                /**
                 * Confirm is TESTNODE is present so it NEWLABEL
                 * or
                 * Confirm both TESTNODE and NEWLABEL are not present
                 */
                if (hasLabelTESTNODE) {
                    assertTrue(hasLabelNEWLABEL);
                } else {
                    assertFalse(hasLabelTESTNODE);
                    assertFalse(hasLabelNEWLABEL);
                }
            });
        } catch (Exception e) {
            fail("ERROR -> " + e.toString());
        }
    }

    /**
     * Prints out the contents of bluewave_metadata node
     *
     * @param dbService
     */
    private void printResultsToConsole(GraphDatabaseService dbService) {

        /**
         * Eyeball the results in the console and verify the bluewave_metadata node has
         * one node with one label.
         */
        try (Transaction tx = dbService.beginTx()) {
            Result result = tx.execute(GET_BLUEWAVE_METADATA_NODE_SQL);

            assertTrue(result.hasNext());
            Map<String, Object> record = result.next();
            Node metaNode = (Node) record.get("n");

            console.log("** bluewave_metadata node contents: "
                    // + " --properties--:> " +
                    // metaNode.getProperties(Metadata.KEY_PROPERTIES).toString()
                    + "\n --counts--:> " + metaNode.getProperties(KEY_COUNTS).toString() + "\n");
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
            final String createSql = "CREATE (n1{prop1:'val1'})," // One node with one property
                    + " (n2:TESTNODE{prop1:'val1', prop2:'val2'})," // One node with a label and 2 properties
                    + " (n3:TESTLABEL{prop1:'val1', prop2:'val2', prop3:'val3'}), " // One node with a label and 3
                                                                                    // properties
                    + " (n4:TESTLABEL:TESTLABEL3:TESTNODE{prop1:'val33', prop2:'val2', prop3:'val3'}), "
                    + "(n5:TESTLABEL:TESTLABEL2{prop1:'val1', prop2:'val2', prop3:'val3', prop4:'val4'}) "; // One node
                                                                                                            // with
                                                                                                            // 2 labels
                                                                                                            // and
                                                                                                            // 4
                                                                                                            // properties

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
        console.log("** Final result:");
        printResultsToConsole(db);
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
            CountsHandlerTest.driver.close();
            this.embeddedDatabaseServer.close();
        }
    }
}
