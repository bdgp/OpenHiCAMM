package org.bdgp.OpenHiCAMM;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

/** 
 * Java command-line interface to the graph-easy perl script.
 * 
 * To install graph-easy:
 * brew install cpanminus && sudo cpanm Graph::Easy # on Mac OS X
 */
public class GraphEasy {
    public List<Edge> edges;
    
    public GraphEasy() {
        this.edges = new ArrayList<Edge>();
    }
    
    public void addEdge(Edge e) {
        this.edges.add(e);
    }
    
    public void addEdge(String node1, String node2) {
        this.edges.add(new Edge(node1, node2));
    }

    public void addEdge(String node1) {
        this.edges.add(new Edge(node1, null));
    }

    public String graph() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Edge edge : this.edges) {
            sb.append(edge);
            sb.append(" ");
        }
        sb.append(String.format("%n"));
        String graphDesc = sb.toString();
        Process p;
        try { p = Runtime.getRuntime().exec(new String[] {"graph-easy","--as=boxart"}); }
        catch (IOException e) {
            // Try /usr/local/bin explicitly, because Mac OS X doesn't put /usr/local/bin 
            // in the PATH by default.
            try { p = Runtime.getRuntime().exec(new String[] {"/usr/local/bin/graph-easy","--as=boxart"}); }
            catch (IOException e2) { return "graph-easy is not installed, skipping."; }
        }
        BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(p.getOutputStream()));
        out.write(graphDesc);
        out.flush();
        out.close();
        String line;
        StringBuilder graph = new StringBuilder();
        while ((line = in.readLine()) != null) {
            graph.append(String.format("%s%n", line));
        }
        in.close();
        return graph.toString();
    }

    public class Edge {
        public String node1;
        public String node2;
        public Edge(String node1) {
            this.node1 = node1;
            this.node2 = null;
        }
        public Edge(String node1, String node2) {
            this.node1 = node1;
            this.node2 = node2;
        }
        public String toString() {
            if (node1 == null && node2 == null) {
                return "";
            }
            else if (node1 != null && node2 == null) {
                return String.format("[%s]", node1);
            }
            else if (node1 == null && node2 != null) {
                return String.format("[%s]", node2);
            }
            else {
                return String.format("[%s] --> [%s]", node1, node2);
            }
        }
    }
}
