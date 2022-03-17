package bluewave.neo4j.plugins;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.event.PropertyEntry;
import org.neo4j.graphdb.event.LabelEntry;
import org.neo4j.graphdb.event.TransactionData;
import org.neo4j.graphdb.event.TransactionEventListener;
import org.neo4j.logging.internal.LogService;

import java.util.*;
import javaxt.json.*;
import static javaxt.utils.Console.console;

public class Neo4JTransactionEventListener implements TransactionEventListener<Object> {

    private Logger logger;
    private Metadata metadata;

    // Lookup table for deleted nodes. Currently when a deleted node comes through,
    // there is no way to get its labels.
    // However, the deleted labels event comes through after the deleted node event, so 
    // we can do a lookup by node id and grab the deleted nodes' labels.
    private JSONObject deletedNodes = new JSONObject();

  //**************************************************************************
  //** Constructor
  //**************************************************************************
    protected Neo4JTransactionEventListener(final GraphDatabaseService graphDatabaseService,
        final LogService logsvc, Logger logger, Metadata metadata) {
        this.logger = logger;
        this.metadata = metadata;
    }


  //**************************************************************************
  //** beforeCommit
  //**************************************************************************
    public Object beforeCommit(final TransactionData data, final Transaction transaction,
            final GraphDatabaseService databaseService) throws Exception {

//        if (meta!=null) meta.handleEventBeforeCommit(data);
        if (logger==null && metadata==null) return null;

        String user = data.username();

        Iterable<Node> createdNodes = data.createdNodes();
        if (createdNodes != null) {
            Iterator<Node> it = createdNodes.iterator();
            if (it.hasNext())
                log("create", "nodes", getNodeInfo(it, "create"), user);
        }

        Iterable<Node> deletedNodes = data.deletedNodes();
        if (deletedNodes != null) {
            Iterator<Node> it = deletedNodes.iterator();
            if (it.hasNext())
                log("delete", "nodes", getNodeInfo(it, "delete"), user);
        }

        Iterable<Relationship> createdRelationships = data.createdRelationships();
        if (createdRelationships != null) {
            Iterator<Relationship> it = createdRelationships.iterator();
            if (it.hasNext())
                log("create", "relationships", getRelationshipInfo(it), user);
        }

        Iterable<Relationship> deletedRelationships = data.deletedRelationships();
        if (data.deletedRelationships() != null) {
            Iterator<Relationship> it = deletedRelationships.iterator();
            if (it.hasNext())
                log("delete", "relationships", getRelationshipInfo(it), user);
        }

        Iterable<LabelEntry> assignedLabels = data.assignedLabels();
        if (assignedLabels != null) {
            Iterator<LabelEntry> it = assignedLabels.iterator();
            if (it.hasNext())
                log("create", "labels", getLabelInfo(it), user);
        }

        Iterable<LabelEntry> removedLabels = data.removedLabels();
        if (removedLabels != null) {
            Iterator<LabelEntry> it = removedLabels.iterator();
            if (it.hasNext())
                log("delete", "labels", getDeletedLabelInfo(it, user), user);

        }

        Iterable<PropertyEntry<Node>> assignedNodeProperties = data.assignedNodeProperties();
        if (assignedNodeProperties != null) {
            Iterator<PropertyEntry<Node>> it = assignedNodeProperties.iterator();
            if (it.hasNext())
                log("create", "properties", getPropertyInfo(it, databaseService, transaction, "create"), user);
        }

        Iterable<PropertyEntry<Node>> removedNodeProperties = data.removedNodeProperties();
        if (removedNodeProperties != null) {
            Iterator<PropertyEntry<Node>> it = removedNodeProperties.iterator();
            if (it.hasNext())
                log("delete", "properties", getPropertyInfo(it, databaseService, transaction, "delete"), user);
        }

        Iterable<PropertyEntry<Relationship>> assignedRelationshipProperties = data.assignedRelationshipProperties();
        if (assignedRelationshipProperties != null) {
            Iterator<PropertyEntry<Relationship>> it = assignedRelationshipProperties.iterator();
            if (it.hasNext())
                log("create", "relationship_property", getRelationshipPropertyInfo(it), user);
        }

        Iterable<PropertyEntry<Relationship>> removedRelationshipProperties = data.removedRelationshipProperties();
        if (removedRelationshipProperties != null) {
            Iterator<PropertyEntry<Relationship>> it = removedRelationshipProperties.iterator();
            if (it.hasNext())
                log("delete", "relationship_property", getRelationshipPropertyInfo(it), user);
        }

        return null;
    }


  //**************************************************************************
  //** log
  //**************************************************************************
    public void log(String action, String type, JSONArray data, String user){
        if (metadata!=null) metadata.log(action, type, data, user);
        if (logger!=null) logger.log(action, type, data, user);
    }


  //**************************************************************************
  //** afterCommit
  //**************************************************************************
    public void afterCommit(final TransactionData data, final Object state,
            final GraphDatabaseService databaseService) {

//        if (metadata!=null){
//            try{ metadata.handleEventAfterCommit(data); }
//            catch(Exception e){}
//        }
    }

  //**************************************************************************
  //** afterRollback
  //**************************************************************************
    public void afterRollback(final TransactionData data, final Object state,
            final GraphDatabaseService databaseService) {
    }

  //**************************************************************************
  //** getNodeInfo
  //**************************************************************************
    private JSONArray getNodeInfo(Iterator<Node> it, String action) {
        JSONArray arr = new JSONArray();
        
        try {
            while (it.hasNext()) {
                JSONArray entry = new JSONArray();
                Node node = it.next();
                Long nodeID = node.getId();
                entry.add(nodeID);
                Iterable<Label> labels = null;
                try {
                    labels = node.getLabels();
                }catch(Exception e) {
                    console.log("Could not get labels: "+e.getMessage());
                    if(action.equals("delete") ){
                        deletedNodes.set(nodeID+"", nodeID);
                    }
                    return arr;
                }   
                if (labels != null) {
                    Iterator<Label> i2 = labels.iterator();
                    while (i2.hasNext()) {
                        String label = i2.next().name();
                        if (label != null) {
                            entry.add(label);
                        }
                    }
                }

                arr.add(entry);
            }
        } catch (Exception e) {
            console.log(e.getMessage());
        }

        return arr;
    }

  //**************************************************************************
  //** getRelationshipInfo
  //**************************************************************************
    private JSONArray getRelationshipInfo(Iterator<Relationship> it) {
        JSONArray arr = new JSONArray();
        JSONObject jsonObject = new JSONObject();
        while(it.hasNext()) {
            jsonObject = new JSONObject();
            Relationship relationship = it.next();
            Long startNodeId = relationship.getStartNodeId();
            Long endNodeId = relationship.getEndNodeId();
            jsonObject.set("startNodeId", startNodeId);
            jsonObject.set("endNodeId", endNodeId);
            JSONArray startNodeLabels = new JSONArray();
            for(Label label: relationship.getStartNode().getLabels()) {
                startNodeLabels.add(label);
            }
            jsonObject.set("startNodeLabels", startNodeLabels);
            JSONArray endNodeLabels = new JSONArray();
            for(Label label: relationship.getEndNode().getLabels()) {
                endNodeLabels.add(label);
            }
            jsonObject.set("endNodeLabels", endNodeLabels);
            arr.add(jsonObject);
        }
        return arr;
    }

  //**************************************************************************
  //** getLabelInfo
  //**************************************************************************
    private JSONArray getLabelInfo(Iterator<LabelEntry> it) {
        JSONArray arr = new JSONArray();
        return arr;
    }

  //**************************************************************************
  //** getLabelInfo / Delete
  //**************************************************************************
  private JSONArray getDeletedLabelInfo(Iterator<LabelEntry> it, String username) {
    JSONArray arr = new JSONArray();
    Long nodeId = null;
    while(it.hasNext()) {
        JSONArray entry = new JSONArray();
        LabelEntry labelEntry = it.next();
        nodeId=labelEntry.node().getId();
        entry.add(nodeId);
        String label = labelEntry.label().name();
        entry.add(label);
        if(deletedNodes.has(nodeId+"")) {
            JSONArray nodeArray = new JSONArray();
            deletedNodes.remove(nodeId+"");
            nodeArray.add(entry);
            log("delete", "nodes", nodeArray, username);
        }
    }
    return arr;
}

  //**************************************************************************
  //** getPropertyInfo
  //**************************************************************************
    private JSONArray getPropertyInfo(Iterator<PropertyEntry<Node>> it, GraphDatabaseService db, final Transaction transaction, String action) {
        JSONArray arr = new JSONArray();
        
        try {
            
            JSONObject jsonObject = new JSONObject();
            while (it.hasNext()) {
                jsonObject = new JSONObject();
                
                PropertyEntry<Node> node = it.next();
                Long nodeId = node.entity().getId();
                jsonObject.set("nodeId", nodeId);
                JSONArray nodeLabels = new JSONArray();
                try  {
                    transaction.getNodeById(nodeId).getLabels().forEach(l -> nodeLabels.add(String.valueOf(l)));
                }catch(Exception e) {
                    e.printStackTrace();
                }
                jsonObject.set("labels", nodeLabels);
                jsonObject.set("property", node.key());
                arr.add(jsonObject);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return arr;
    }

  //**************************************************************************
  //** getRelationshipPropertyInfo
  //**************************************************************************
    private JSONArray getRelationshipPropertyInfo(Iterator<PropertyEntry<Relationship>> it) {
        JSONArray arr = new JSONArray();
        int count = 0;
        try {
            while (it.hasNext()) {
                it.next();
                count++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        arr.add(count);
        return arr;
    }
}