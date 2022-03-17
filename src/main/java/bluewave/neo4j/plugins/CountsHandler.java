package bluewave.neo4j.plugins;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotInTransactionException;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.event.LabelEntry;
import org.neo4j.graphdb.event.TransactionData;

import javaxt.json.JSONArray;
import javaxt.json.JSONObject;
import javaxt.json.JSONValue;

import static javaxt.utils.Console.console;

public class CountsHandler {

    private GraphDatabaseService db;

    public static final String KEY_COUNTS = "counts";

    public static final int INDEX_LABELS = 0;
    private static final int INDEX_COUNT = 1;
    public static final int INDEX_RELATIONS = 2;
    private static final int INDEX_TRANSACTION_ID = 3;
    private static final int INDEX_NODE_ID = 4;

    public CountsHandler(GraphDatabaseService db) {
        this.db = db;
    }



    public void handleEvent(TransactionData data) throws Exception {

      //***********************************************
      //** Deleted Nodes
      //***********************************************
        try {
            if (data.deletedNodes() != null) {
                data.deletedNodes().forEach(n -> {
                    deletedNodesEventNew(n);
                });
            }
        }
        catch (Exception e) {
            e("deletedNodesEvent: calling forEach(): " + e);
        }


      //***********************************************
      //** Deleted Relationships
      //***********************************************
        try {
            if (data.deletedRelationships() != null) {
                data.deletedRelationships().forEach(n -> deletedRelationshipsEvent(n));
            }
        } catch (Exception e) {
            e("deletedRelationshipsEvent: " + e);
        }

      //***********************************************
      //** Created Nodes
      //***********************************************
        try {
            Map<Long, Set<String>> nodeLabels = new HashMap<Long, Set<String>>();

            if (data.createdNodes() != null) {
                /**
                 * Process each new node
                 */
                data.createdNodes().forEach(n -> {
                    Set<String> newNodesLabels = new HashSet<String>();
                    Long nodeId = null;
                    try (Transaction tx = db.beginTx()) {
                        nodeId = n.getId();
                        Node thisNode = tx.getNodeById(nodeId);
                        thisNode.getLabels().forEach(l -> newNodesLabels.add(String.valueOf(l)));
                        nodeLabels.put(nodeId, newNodesLabels);

                    } catch (Exception te) {
                        e("createNodesEventNew: calling getNodeById(): " + te);
                    }
                });
                nodeLabels.entrySet().forEach(e -> createdNodesEventNew(e.getKey(), e.getValue(),
                        data.getTransactionId()));
            }
        } catch (Exception e) {
            e("createNodesEventNew: calling forEach(): " + e);
        }

        //***********************************************
        //** Assigned Labels
        //***********************************************
        try {
            if (data.assignedLabels() != null) {
                data.assignedLabels()
                        .forEach(n -> assignedLabelEventNew(n, data.getTransactionId()));
            }
        } catch (Exception e) {
            e("assignedLabelsEvent: " + e);
        }

        //***********************************************
        //** Removed Labels
        //***********************************************
        try {
            if (data.removedLabels() != null) {
                data.removedLabels().forEach(n -> removedLabelEventNew(n, data.getTransactionId()));
            }
        } catch (Exception e) {
            e("removedLabelEvent: " + e);
        }

        //***********************************************
        //** Created Relationships
        //***********************************************
        try {
            if (data.createdRelationships() != null) {
                data.createdRelationships()
                        .forEach(n -> createdRelationshipsEvent(n, data.getTransactionId()));
            }
        } catch (Exception e) {
            e("createdRelationshipsEvent: " + e);
        }
    }


 /**
     * Holds the nodes and counts data
     * @param labels
     * @param count
     * @param relations
     * @param txId
     * @param nodeId
     * @return
     */
    private JSONArray newNodesAndCountsObject(JSONArray labels, long count, long relations,
            long txId, long nodeId) {
        JSONArray data = new JSONArray();
        data.add(labels);
        data.add(count);
        data.add(relations);
        data.add(txId);
        data.add(nodeId);
        return data;
    }

    private void e(Object message) {
        console.log("*** ------- ERROR ------- *** " + message.toString());
    }

  //**************************************************************************
  //** deletedNodesEvent New
  //**************************************************************************
  private void deletedNodesEventNew(Node node) {

    Long metaNodeId = null;
    Long nodeId = node.getId();
    JSONObject metaCountsNode = null;
    try (Transaction tx = db.beginTx()) {
        Node metaNode = getMetadataNodeData(tx);
        metaNodeId = metaNode.getId();
        metaCountsNode = new JSONObject(metaNode.getProperty(KEY_COUNTS).toString());
    } catch (Exception e) {
        e("deletedNodesEventNew getMetadataNodeData: " + e);
        return;
    }

    boolean somethingToSave = false;

    /**
     * Check for bluewave_metadata node
     */
    if (metaNodeId.longValue() != nodeId.longValue()) {
        try {
            /**
             * Gather all labels from this node
             */
            Set<String> newNodesLabels = new HashSet<String>();
            try (Transaction tx = db.beginTx()) {
                tx.getNodeById(nodeId).getLabels()
                        .forEach(l -> newNodesLabels.add(String.valueOf(l)));
            } catch (Exception e) {
                e("deletedNodesEventNew get all labels: " + e);
                return;
            }
            JSONValue jsonArrayNodeCountsValue = null;
            if ((jsonArrayNodeCountsValue =
                    metaCountsNode.get(String.valueOf(newNodesLabels.hashCode()))) != null) {
                /**
                 * Found node in metadata, decrement count
                 */
                JSONArray jsonArrayNodeCounts = jsonArrayNodeCountsValue.toJSONArray();
                Long counts = jsonArrayNodeCounts.get(INDEX_COUNT).toLong();
                counts--;
                jsonArrayNodeCounts.set(INDEX_COUNT, counts);
                jsonArrayNodeCounts.set(INDEX_NODE_ID, node.getId());
                jsonArrayNodeCounts.set(INDEX_TRANSACTION_ID, -1);
                metaCountsNode.set(String.valueOf(newNodesLabels.hashCode()),
                        jsonArrayNodeCounts);
                if (counts == 0) {
                    metaCountsNode.remove(String.valueOf(newNodesLabels.hashCode()));
                }
                somethingToSave = true;
            }
        } catch (Exception e) {
            e("deletedNodesEventNew: " + e);
        }
    }

    if (somethingToSave) {
          //saveBluewaveMeta_NodesAndCounts(metaCountsNode);
    }
}


  //**************************************************************************
  //** createdNodesEvent NEW
  //**************************************************************************
  private void createdNodesEventNew(Long nodeId, Set<String> newNodesLabels, Long txId) {

    Long metaNodeId = null;
    JSONObject metaCountsNode = null;
    Node metaNode = null;
    try (Transaction tx = db.beginTx()) {
        metaNode = getMetadataNodeData(tx);
        metaNodeId = metaNode.getId();
        metaCountsNode = new JSONObject(metaNode.getProperty(KEY_COUNTS).toString());
    } catch (Exception e) {
        e("createdNodesEventNew:  foreach2: " + e);
    }

    /**
     * Check for bluewave_metadata node
     */
    if (metaNodeId.longValue() == nodeId.longValue()) {
        return;
    }

    /**
     * Find entry in meta for this label set
     */
    JSONValue entry = null;
    if (!(entry = metaCountsNode.get(String.valueOf(newNodesLabels.hashCode()))).isNull()) {
        /**
         * Found entry in metadata, increment count
         */
        JSONArray entryValueArray = entry.toJSONArray();
        Long counts = entryValueArray.get(INDEX_COUNT).toLong();
        counts++;
        entryValueArray.set(INDEX_TRANSACTION_ID, txId);
        entryValueArray.set(INDEX_NODE_ID, nodeId);
        metaCountsNode.set(String.valueOf(newNodesLabels.hashCode()), entryValueArray);
    } else {
        /**
         * Create new node for counts
         */
        JSONArray labelsValue = new JSONArray();
        newNodesLabels.iterator().forEachRemaining(l -> labelsValue.add(l));
        metaCountsNode.set(String.valueOf(newNodesLabels.hashCode()),
                newNodesAndCountsObject(labelsValue, 1, 0, txId, nodeId));
    }

    //saveBluewaveMeta_NodesAndCounts(metaCountsNode);

  }

  //**************************************************************************
  //** assignedLabelEvent NEW
  //**************************************************************************
  private void assignedLabelEventNew(LabelEntry labelEntry, Long txId) {
    String labelName = labelEntry.label().name();
    if (labelName.equals(Metadata.META_NODE_LABEL)) {
        return;
    }

    Long metaNodeId = null;
    Long nodeId = labelEntry.node().getId();
    JSONObject metaCountsNode = null;

    try (Transaction tx = db.beginTx()) {
        Node metaNode = getMetadataNodeData(tx);
        metaNodeId = metaNode.getId();
        metaCountsNode = new JSONObject(metaNode.getProperty(KEY_COUNTS).toString());
    } catch (Exception e) {
        e("assignedLabelEventNew getMetadataNodeData: " + e);
        return;
    }

    /**
     * Discard bluewave_metadata transactions
     */
    if (metaNodeId == nodeId) {
        return;
    }

    /**
     * Gather all labels from this node
     */
    Set<String> newNodesLabels = new HashSet<String>();
    try (Transaction tx = db.beginTx()) {
        tx.getNodeById(nodeId).getLabels().forEach(l -> newNodesLabels.add(String.valueOf(l)));
    } catch (Exception e) {
        e("assignedLabelEventNew get all labels: " + e);
        return;
    }

    /**
     * Check for existence of the entry using the label set
     */
    JSONValue entry = null;
    entry = metaCountsNode.get(String.valueOf(newNodesLabels.hashCode()));
    if (!entry.isNull()) {
        /**
         ** Found entry in metadata
         */
        JSONArray entryValue = entry.toJSONArray();
        long lastTxId = entryValue.get(INDEX_TRANSACTION_ID).toLong();
        long lastNodeId = entryValue.get(INDEX_NODE_ID).toLong();
        /**
         * Check if we updated this node count already
         */
        if (lastTxId == txId && lastNodeId == nodeId) {
            return;
        }
        /**
         * Finally, increment count
         */
        Long counts = entryValue.get(INDEX_COUNT).toLong();
        counts++;
        entryValue.set(INDEX_COUNT, counts);
        entryValue.set(INDEX_TRANSACTION_ID, txId);
        entryValue.set(INDEX_NODE_ID, nodeId);

    } else {
        /**
         * Create new entry
         */
        JSONArray labelsArray = new JSONArray();
        for (String string : newNodesLabels) {
            labelsArray.add(string);
        }
        JSONArray newEntryValue = newNodesAndCountsObject(labelsArray, 1L, 0L, txId, nodeId);
        metaCountsNode.set(String.valueOf(newNodesLabels.hashCode()), newEntryValue);

        /**
         * Find entry that contained this node prior to adding this label
         */
        if (newNodesLabels.size() > 1) {
            if (newNodesLabels.remove(labelName)) {
                JSONValue originalEntryValue =
                        metaCountsNode.get(String.valueOf(newNodesLabels.hashCode()));
                if (originalEntryValue != null && !originalEntryValue.isNull()) {
                    JSONArray originalEntryValueArray = originalEntryValue.toJSONArray();
                    long originalNodeId = originalEntryValueArray.get(INDEX_NODE_ID).toLong();
                    if (originalNodeId == nodeId) {
                        /**
                         * Decrement count in original entry Also found PRIOR entry in metadata
                         * w/same nodeId for labels, decrement count
                         */
                        long originalNodeCount =
                                originalEntryValueArray.get(INDEX_COUNT).toLong();
                        if (originalNodeCount > 0) {
                            originalNodeCount--;
                            if (originalNodeCount == 0) {
                                /**
                                 * No more nodes containing this label set exist so remove from
                                 * index Remove PRIOR entry w/same nodeId for labels
                                 */
                                metaCountsNode
                                        .remove(String.valueOf(newNodesLabels.hashCode()));
                            } else {
                                originalEntryValueArray.set(INDEX_COUNT, originalNodeCount);
                                metaCountsNode.set(String.valueOf(newNodesLabels.hashCode()),
                                        originalEntryValueArray);
                            }
                        }
                    }
                }
            }
        }
    }
    //saveBluewaveMeta_NodesAndCounts(metaCountsNode);
}


  //**************************************************************************
  //** removedLabelEvent NEW
  //**************************************************************************
  private void removedLabelEventNew(LabelEntry labelEntry, Long txId) {

    String labelName = labelEntry.label().name();
    if (labelName.equals(Metadata.META_NODE_LABEL)) {
        return;
    }

    Long metaNodeId = null;
    Long nodeId = labelEntry.node().getId();
    JSONObject metaCountsNode = null;
    /**
     * Gather all labels from this node
     */
    Set<String> newNodesLabels = new HashSet<String>();
    try (Transaction tx = db.beginTx()) {
        tx.getNodeById(nodeId).getLabels().forEach(l -> newNodesLabels.add(String.valueOf(l)));
    } catch (org.neo4j.graphdb.NotFoundException e) {
        return;
    }

    try (Transaction tx = db.beginTx()) {
        Node metaNode = getMetadataNodeData(tx);
        metaNodeId = metaNode.getId();
        metaCountsNode = new JSONObject(metaNode.getProperty(KEY_COUNTS).toString());
    } catch (Exception e) {
        e("removedLabelEventNew getMetadataNodeData: " + e);
        return;
    }

    /**
     * Discard bluewave_metadata transactions
     */
    if (metaNodeId == nodeId) {
        return;
    }

    /**
     * Add the newLabel to the labelset
     */
    newNodesLabels.add(labelName);

    /**
     * Check for existence of the entry using the label set
     */
    JSONValue entry = null;
    entry = metaCountsNode.get(String.valueOf(newNodesLabels.hashCode()));
    if (!entry.isNull()) {
        /**
         ** Found entry in metadata
         */
        JSONArray entryValue = entry.toJSONArray();
        long lastTxId = entryValue.get(INDEX_TRANSACTION_ID).toLong();
        long lastNodeId = entryValue.get(INDEX_NODE_ID).toLong();
        /**
         * Check if we updated this node count already
         */
        if (lastTxId == txId && lastNodeId == nodeId) {
            return;
        }
        /**
         * Finally, decrement count
         */
        Long counts = entryValue.get(INDEX_COUNT).toLong();
        counts--;
        if (counts == 0) {
            /**
             * Remove entry
             */
            metaCountsNode.remove(String.valueOf(newNodesLabels.hashCode()));
        } else {
            entryValue.set(INDEX_COUNT, counts);
            entryValue.set(INDEX_TRANSACTION_ID, txId);
            entryValue.set(INDEX_NODE_ID, nodeId);
        }

        if (newNodesLabels.size() > 1) {
            /**
             * Confirm entry that contains the new label set exists
             */
            if (newNodesLabels.remove(labelName)) {
                JSONValue newEntryValue =
                        metaCountsNode.get(String.valueOf(newNodesLabels.hashCode()));
                if (newEntryValue == null || newEntryValue.isNull()) {
                    /**
                     * Create new entry
                     */
                    JSONArray labelsValue = new JSONArray();
                    newNodesLabels.iterator().forEachRemaining(l -> labelsValue.add(l));
                    metaCountsNode.set(String.valueOf(newNodesLabels.hashCode()),
                            newNodesAndCountsObject(labelsValue, 1, 0, txId, nodeId));
                }
            }
        }
    }

    //saveBluewaveMeta_NodesAndCounts(metaCountsNode);
}


  //**************************************************************************
  //** createdRelationshipsEvent
  //**************************************************************************
  private void createdRelationshipsEvent(Relationship createdRelationship, Long txId) {
    /**
     * Collect r where (n) <--() These are where the nodes are the end nodes of the relationship
     */
    Node metaNode = null;
    Long endNodeId = createdRelationship.getEndNodeId();
    Long metaNodeId = null;
    JSONObject metaCountsNode = null;

    /**
     * Get metadata node
     */
    try (Transaction tx = db.beginTx()) {
        metaNode = getMetadataNodeData(tx);
        metaNodeId = metaNode.getId();
        metaCountsNode = new JSONObject(metaNode.getProperty(KEY_COUNTS).toString());
    } catch (Exception e) {
        e("createdRelationshipsEvent getMetadataNodeData: " + e);
        return;
    }

    /**
     * Check if node is bluewave_metadata
     */
    if (metaNodeId == endNodeId) {
        return;
    }

    /**
     * Get endNode label set
     */
    Set<String> newNodesLabels = new HashSet<String>();
    try (Transaction tx = db.beginTx()) {
        tx.getNodeById(endNodeId).getLabels()
                .forEach(l -> newNodesLabels.add(String.valueOf(l)));
    } catch (Exception e) {
        e("createdRelationshipsEvent getMetadataNodeData: " + e);
        return;
    }

    /**
     * Check for existence of the entry using the label set
     */
    JSONValue entry = null;
    entry = metaCountsNode.get(String.valueOf(newNodesLabels.hashCode()));
    if (!entry.isNull()) {
        /**
         ** Found entry in metadata
         */
        JSONArray entryValue = entry.toJSONArray();

        /**
         * Finally, increment relations
         */
        Long relations = entryValue.get(INDEX_RELATIONS).toLong();
        relations++;
        entryValue.set(INDEX_RELATIONS, relations);
        entryValue.set(INDEX_TRANSACTION_ID, txId);
        entryValue.set(INDEX_NODE_ID, endNodeId);
        metaCountsNode.set(String.valueOf(newNodesLabels.hashCode()), entryValue);
        //saveBluewaveMeta_NodesAndCounts(metaCountsNode);
    } else {
        /**
         * Create new entry
         */
        JSONArray labelsArray = new JSONArray();
        for (String string : newNodesLabels) {
            labelsArray.add(string);
        }
        JSONArray newEntryValue = newNodesAndCountsObject(labelsArray, 1L, 1L, txId, endNodeId);
        metaCountsNode.set(String.valueOf(newNodesLabels.hashCode()), newEntryValue);
        //saveBluewaveMeta_NodesAndCounts(metaCountsNode);
    }

}

//**************************************************************************
//** deletedRelationshipsEvent
//**************************************************************************
private void deletedRelationshipsEvent(Relationship deletedRelationship) {
    Node metaNode = null;
    Long endNodeId = deletedRelationship.getEndNodeId();
    Long metaNodeId = null;
    JSONObject metaCountsNode = null;

    /**
     * Get metadata node
     */
    try (Transaction tx = db.beginTx()) {
        metaNode = getMetadataNodeData(tx);
        metaNodeId = metaNode.getId();
        metaCountsNode = new JSONObject(metaNode.getProperty(KEY_COUNTS).toString());
    } catch (Exception e) {
        e("deletedRelationshipsEvent getMetadataNodeData: " + e);
        return;
    }

    /**
     * Check if node is bluewave_metadata
     */
    if (metaNodeId == endNodeId) {
        return;
    }

    /**
     * Get endNode label set
     */
    Set<String> newNodesLabels = new HashSet<String>();
    try (Transaction tx = db.beginTx()) {
        tx.getNodeById(endNodeId).getLabels()
                .forEach(l -> newNodesLabels.add(String.valueOf(l)));
    } catch (Exception e) {
        e("deletedRelationshipsEvent getMetadataNodeData: " + e);
        return;
    }

    /**
     * Check for existence of the entry using the label set
     */
    JSONValue entry = null;
    entry = metaCountsNode.get(String.valueOf(newNodesLabels.hashCode()));
    if (!entry.isNull()) {
        /**
         ** Found entry in metadata
         */
        JSONArray entryValue = entry.toJSONArray();

        /**
         * Finally, increment relations
         */
        Long relations = entryValue.get(INDEX_RELATIONS).toLong();
        relations--;
        entryValue.set(INDEX_RELATIONS, relations);
        entryValue.set(INDEX_TRANSACTION_ID, -1);
        entryValue.set(INDEX_NODE_ID, endNodeId);
        metaCountsNode.set(String.valueOf(newNodesLabels.hashCode()), entryValue);
        //saveBluewaveMeta_NodesAndCounts(metaCountsNode);
    }
}


//**************************************************************************
  //** getMetadataNodeData
  //**************************************************************************
  /** Returns the metadata node
   */
  private Node getMetadataNodeData(Transaction tx) throws NotInTransactionException {
    try {
        Label metadataNodeLabel = Label.label(Metadata.META_NODE_LABEL);
        ResourceIterator<Node> result = tx.findNodes(metadataNodeLabel);
        if (result.hasNext()) {
            Node metaNode = result.next();
            return metaNode;
        }
    } catch (Exception e) {
        e("getMetadataNodeData: " + e);
    }
    return null;
}

}