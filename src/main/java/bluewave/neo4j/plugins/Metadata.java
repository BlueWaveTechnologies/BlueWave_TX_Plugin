package bluewave.neo4j.plugins;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;


import org.neo4j.graphdb.*;

import javaxt.json.*;
import static javaxt.utils.Console.console;


//******************************************************************************
//**  Metadata Class
//******************************************************************************
/**
 *   Used to create and update metadata for the graph. Metadata is stored in a
 *   metadata node.
 *
 ******************************************************************************/

public class Metadata {


    private ConcurrentHashMap<String, NodeMetadata> nodes;
    public static String META_NODE_LABEL = "bluewave_metadata";
    private GraphDatabaseService db;
    private AtomicLong lastUpdate;

    private static final String nodesQuery =
    "MATCH (n) RETURN\n" +
    "distinct labels(n) as labels,\n" +
    "count(labels(n)) as count,\n" +
    "sum(size((n) <--())) as relations;";

    private String propertiesQuery =
    "MATCH(n)\n" +
    "WITH LABELS(n) AS labels , KEYS(n) AS keys\n" +
    "UNWIND labels AS label\n" +
    "UNWIND keys AS key\n" +
    "RETURN DISTINCT label as node, COLLECT(DISTINCT key) AS properties\n" +
    "ORDER BY label";



  //**************************************************************************
  //** Constructor
  //**************************************************************************
    public Metadata() {

        nodes = new ConcurrentHashMap<>();
        lastUpdate = new AtomicLong(0L);
        AtomicBoolean isRunning = new AtomicBoolean(false);


      //Sync recent updates made to the nodes map every 30 seconds
        {
            long interval = 30*1000; //30 seconds
            java.util.Timer timer = new java.util.Timer();
            timer.scheduleAtFixedRate( new java.util.TimerTask(){
                public void run(){
                    if (isRunning.get()==true) return;
                    isRunning.set(true);
                    synchronized (lastUpdate){
                        if (lastUpdate.get()>0){
                            try{
                                if ((System.currentTimeMillis()-lastUpdate.get())>interval){
                                    saveNodes();
                                    lastUpdate.set(0);
                                }
                            }
                            catch(Exception e){}
                        }
                    }
                    isRunning.set(false);
                }
            }, 30*1000, interval);
        }



      //Sync all nodes every 24 hours
        {
            javaxt.utils.Date startDate = new javaxt.utils.Date();
            startDate.setTimeZone("America/New York");
            startDate.removeTimeStamp(); startDate.add(26, "hours"); //2AM
            long interval = 24*60*60*1000; //24 hours
            java.util.Timer timer = new java.util.Timer();
            timer.scheduleAtFixedRate( new java.util.TimerTask(){
                public void run(){
                    if (isRunning.get()==true) return;
                    isRunning.set(true);
                    synchronized (lastUpdate){
                        if (lastUpdate.get()==0){
                            try{
                                init();
                            }
                            catch(Exception e){

                            }
                        }
                    }
                    isRunning.set(false);
                }
            }, startDate.getDate(), interval);
        }
    }


  //**************************************************************************
  //** setDatabaseService
  //**************************************************************************
    public void setDatabaseService(GraphDatabaseService databaseService){
        db = databaseService;
    }


  //**************************************************************************
  //** init
  //**************************************************************************
  /** Used to initialize the Metadata class and populate the metadata node
   */
    public void init() throws Exception {

        long startTime = System.currentTimeMillis();

        updateNodes();
        saveNodes();

        long ellapsedTime = System.currentTimeMillis()-startTime;
        console.log("Found " + nodes.size() + " distinct nodes in " + ellapsedTime + "ms");
        lastUpdate.set(0);

    }


  //**************************************************************************
  //** setNodeName
  //**************************************************************************
    public void setNodeName(String nodeName){
        if (nodeName==null || nodeName.isBlank()) return;
        META_NODE_LABEL = nodeName;
    }


  //**************************************************************************
  //** stop
  //**************************************************************************
    public void stop(){
        //cancelTimers();
    }


  //**************************************************************************
  //** log
  //**************************************************************************
  /** Used to add an event to the queue
   */
    public void log(String action, String type, JSONArray data, String username){

        if ((action.equals("create") || action.equals("delete")) && (type.equals("nodes") || type.equals("relationships") || type.equals("properties")))
        synchronized(nodes){
            try{
                if (type.equals("nodes")){
                    for (int i=0; i<data.length(); i++){
                        JSONArray entry = data.get(i).toJSONArray();
                        if (entry.isEmpty()) continue;


                        HashSet<String> labels = new HashSet<>();
                        for (int j=1; j<entry.length(); j++){
                            String label = entry.get(j).toString();
                            if (label!=null) label = label.toLowerCase();
                            labels.add(label);
                        }

                        if (labels.contains(META_NODE_LABEL) || labels.isEmpty()) continue;

                        NodeMetadata nodeMetadata = null;
                        String labelKey = null;
                        for (String label : labels){
                            nodeMetadata = nodes.get(label);
                            labelKey = label;
                            if (nodeMetadata!=null) break;
                        }


                        if (nodeMetadata==null){
                            nodeMetadata = new NodeMetadata();
                            nodeMetadata.count.incrementAndGet();
                            nodes.put(labels.iterator().next(), nodeMetadata);
                        }
                        else{
                            if (action.equals("create")){
                                nodeMetadata.count.incrementAndGet();
                                nodes.put(labels.iterator().next(), nodeMetadata);
                            }
                            else{
                                Long n = nodeMetadata.count.decrementAndGet();
                                if (n==0){
                                    //TODO: Remove node
                                    if(labelKey != null) nodes.remove(labelKey);
                                } else {
                                    nodes.put(labels.iterator().next(), nodeMetadata);
                                }
                            }
                        }


                        lastUpdate.set(System.currentTimeMillis());
                    }
                }
                else if (type.equals("relationships")){
                    //AtomicLong relations = (AtomicLong) node.get("relations").toObject();

                    for (int i=0; i<data.length(); i++){
                        JSONObject entry = data.get(i).toJSONObject();
                        if (entry.isEmpty()) continue;

                        JSONArray startNodeLabelsArray = entry.get("startNodeLabels").toJSONArray();
                        HashSet<String> startNodelabels = new HashSet<>();
                        for (int j=0; j<startNodeLabelsArray.length();j++) {
                            String label = startNodeLabelsArray.get(j).toString();
                            if (label!=null) label = label.toLowerCase();
                            startNodelabels.add(label);
                        }
                        NodeMetadata startNodeMetadata = null;
                        for (String label : startNodelabels){
                            startNodeMetadata = nodes.get(label);
                            if (startNodeMetadata!=null) break;
                        }

                        JSONArray endNodeLabelsArray = entry.get("endNodeLabels").toJSONArray();
                        HashSet<String> endNodeLabels = new HashSet<>();
                        for (int j=0; j<endNodeLabelsArray.length();j++) {
                            String label = endNodeLabelsArray.get(j).toString();
                            if (label!=null) label = label.toLowerCase();
                            endNodeLabels.add(label);
                        }

                        NodeMetadata endNodeMetadata = null;
                        for (String label : endNodeLabels){
                            endNodeMetadata = nodes.get(label);
                            if (endNodeMetadata!=null) break;
                        }

                        if (!startNodelabels.contains(META_NODE_LABEL) && !startNodelabels.isEmpty()){
                            if (startNodeMetadata==null){
                                startNodeMetadata = new NodeMetadata();
                                startNodeMetadata.relations.incrementAndGet();
                                nodes.put(startNodelabels.iterator().next(), startNodeMetadata);
                            }
                            else{
                                if (action.equals("create")){
                                    startNodeMetadata.relations.incrementAndGet();
                                }
                                else{
                                    startNodeMetadata.relations.decrementAndGet();
                                }
                            }
                        }

                        if (!endNodeLabels.contains(META_NODE_LABEL) && !endNodeLabels.isEmpty()){
                            if (endNodeMetadata==null){
                                endNodeMetadata = new NodeMetadata();
                                endNodeMetadata.relations.incrementAndGet();
                                nodes.put(endNodeLabels.iterator().next(), endNodeMetadata);
                            }
                            else{
                                if (action.equals("create")){
                                    endNodeMetadata.relations.incrementAndGet();
                                }
                                else{
                                    endNodeMetadata.relations.decrementAndGet();
                                }
                            }
                        }
                        lastUpdate.set(System.currentTimeMillis());
                    }
                }
                else if(type.equals("properties")) {
                    for (int i=0; i<data.length(); i++){
                        JSONObject entry = data.get(i).toJSONObject();
                        if (entry.isEmpty()) continue;


                        HashSet<String> labels = new HashSet<>();
                        JSONArray jsonLabels = entry.get("labels").toJSONArray();
                        for (int j=0; j<jsonLabels.length();j++) {
                            String label = jsonLabels.get(j).toString();
                            if (label!=null) label = label.toLowerCase();
                            labels.add(label);
                        }

                        if (labels.contains(META_NODE_LABEL) || labels.isEmpty()) continue;

                        NodeMetadata nodeMetadata = null;
                        for (String label : labels){
                            nodeMetadata = nodes.get(label);
                            if (nodeMetadata!=null) break;
                        }


                        if (nodeMetadata==null){
                            nodeMetadata = new NodeMetadata();
                            nodeMetadata.properties.put(entry.get("property").toString(), new PropertyMetadata());
                            nodes.put(labels.iterator().next(), nodeMetadata);
                        }
                        else{
                            if (action.equals("create")){
                                nodeMetadata.properties.put(entry.get("property").toString(), new PropertyMetadata());
                            }
                            else{
                                // Not deleting properties at this time
                                //nodeMetadata.properties.remove(entry.get("property").toString());
                            }
                        }


                        lastUpdate.set(System.currentTimeMillis());
                    }
                }

            }
            catch(Exception e){
                e.printStackTrace();
            }


            nodes.notifyAll();

        }
    }


  //**************************************************************************
  //** updateNodes
  //**************************************************************************
    private void updateNodes() throws Exception {

        HashMap<String, NodeMetadata> nodes = new HashMap<>();

        try (Transaction tx = db.beginTx()) {


          //Get nodes and properties from the database
            Result rs = tx.execute(propertiesQuery);
            while (rs.hasNext()){
                Map<String, Object> r = rs.next();
                Object node = r.get("node");
                Object props = r.get("properties");
                if (node==null) continue;

                String nodeName = node.toString().toLowerCase();
                if (nodeName.equals(META_NODE_LABEL)) continue;

                NodeMetadata nodeMetadata = new NodeMetadata();


                if (props!=null){
                    for (Object p : (List) props){
                        String propertyName = p.toString();
                        nodeMetadata.properties.put(propertyName, new PropertyMetadata());
                    }
                }

                nodes.put(nodeName, nodeMetadata);
            }


          //Update nodeMetadata.properties with attributes stored in the bluewave_metadata node
            String metadata = null;
            rs = tx.execute("match (n:" + META_NODE_LABEL + ") return n.nodes");
            if (rs.hasNext()) metadata = rs.next().get("n.nodes").toString();
            if (metadata!=null){
                JSONObject json = new JSONObject(metadata);
                Iterator<String> it = json.keySet().iterator();
                while (it.hasNext()){
                    String nodeName = it.next();
                    JSONObject nodeMetadata = json.get(nodeName).toJSONObject();
                    JSONObject properties = nodeMetadata.get("properties").toJSONObject();
                    Iterator<String> i2 = properties.keySet().iterator();
                    while (i2.hasNext()){
                        String propertyName = i2.next();
                        JSONObject propertyMetadata = properties.get(propertyName).toJSONObject();
                        String type = propertyMetadata.get("type").toString();
                        PropertyMetadata pm = nodes.get(nodeName).properties.get(propertyName);
                        if (type!=null) pm.type = type;
                    }
                }
            }


          //Update counts
            rs = tx.execute(nodesQuery);
            while (rs.hasNext()){
                Map<String, Object> r = rs.next();
                JSONArray labels = new JSONArray(r.get("labels").toString());
                String nodeName = labels.isEmpty()? "" : labels.get(0).toString();
                if (nodeName.equals(META_NODE_LABEL)) continue;

                Long count = Long.parseLong(r.get("count").toString());
                Long relations = Long.parseLong(r.get("relations").toString());

                NodeMetadata nodeMetadata = nodes.get(nodeName.toLowerCase());
                if (nodeMetadata!=null){
                    nodeMetadata.count.set(count);
                    nodeMetadata.relations.set(relations);
                }
            }
        }
        catch (Exception e) {
            throw e;
        }



        synchronized(this.nodes){
            this.nodes.clear();
            Iterator<String> it = nodes.keySet().iterator();
            while (it.hasNext()){
                String nodeName = it.next();
                NodeMetadata nodeMetadata = nodes.get(nodeName);
                this.nodes.put(nodeName, nodeMetadata);
            }

            this.nodes.notify();
        }
    }


  //**************************************************************************
  //** saveNodes
  //**************************************************************************
    private void saveNodes() throws Exception {

        JSONObject json = new JSONObject();
        synchronized(nodes){
            Iterator<String> it = nodes.keySet().iterator();
            while (it.hasNext()){
                String nodeName = it.next();
                NodeMetadata nodeMetadata = nodes.get(nodeName);
                json.set(nodeName, nodeMetadata.toJSON());
            }
        }
        //System.out.println(json.toString(4));


        Label label = Label.label(META_NODE_LABEL);
        try (Transaction tx = db.beginTx()) {
            ResourceIterator<Node> nodesIterator = tx.findNodes(label);
            Node metadataNode;
            if (nodesIterator.hasNext()) {
                metadataNode = nodesIterator.next();
            }
            else{
                metadataNode = tx.createNode(label);
            }

            metadataNode.setProperty("nodes", json.toString());
            tx.commit();
        }
        catch (Exception e) {
            throw e;
        }
    }


  //**************************************************************************
  //** NodeMetadata
  //**************************************************************************
    private class NodeMetadata {
        private ConcurrentHashMap<String, PropertyMetadata> properties;
        private AtomicLong count;
        private AtomicLong relations;
        public NodeMetadata(){
            properties = new ConcurrentHashMap<>();
            count = new AtomicLong(0L);
            relations = new AtomicLong(0L);
        }
        public JSONObject toJSON(){
            JSONObject json = new JSONObject();
            json.set("count", count.get());
            json.set("relations", relations.get());

            JSONObject props = new JSONObject();
            Iterator<String> it = properties.keySet().iterator();
            while (it.hasNext()){
                String propertyName = it.next();
                PropertyMetadata propertyMetadata = properties.get(propertyName);
                props.set(propertyName, propertyMetadata.toJSON());
            }
            json.set("properties", props);

            return json;
        }
    }


  //**************************************************************************
  //** PropertyMetadata
  //**************************************************************************
    private class PropertyMetadata {
        private String type = "Object";
        private boolean isIndexed = false;
        public JSONObject toJSON(){
            JSONObject json = new JSONObject();
            json.set("type", type);
            json.set("isIndexed", isIndexed);
            return json;
        }
    }

    public void printMaps() {
        if(nodes == null) return;

        // Print Properties
        console.log("Map contents:->");
        HashMap<String, Object[]> entries = new HashMap<>();
        nodes.entrySet().forEach((t) -> {
            NodeMetadata meta = t.getValue();
            Object [] props = meta.properties.keySet().toArray();
            entries.put(t.getKey(), props);
        });
        entries.keySet().forEach(k -> {
            Object[] props = entries.get(k);
            List<String>propsList = new ArrayList();
            for(Object obj : props)
                propsList.add(obj.toString());
            console.log(k + ": " + propsList.toString());
        });
        console.log("Map contents.");
    }
}