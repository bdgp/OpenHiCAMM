package org.bdgp.MMSlide;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Util {
    /**
     * Custom HashMap with support for pseudo-map literals.
     * @param <K> The key type
     * @param <V>
     */
    @SuppressWarnings("serial")
    public static class HashMap<K,V> extends java.util.HashMap<K,V> {
        public HashMap() {
            super();
        }
        // Various chained putters
        public HashMap<K,V> with(K k, V v) { this.put(k, v); return this; }
        public HashMap<K,V> and(K k, V v) { this.put(k, v); return this; }
        public HashMap<K,V> o(K k, V v) { this.put(k, v); return this; }
        public HashMap<K,V> _(K k, V v) { this.put(k, v); return this; }
        // in-place merge
        public Map<K,V> merge(Map<K,V> map) {
            this.putAll(map);
            return this;
        }
    }
    // static methods for creating various HashMaps
    public static <K,V> HashMap<K,V> map(K k, V v) {
        return new HashMap<K,V>().with(k, v);
    }
    public static <K,V> HashMap<K,V> map(Map<K,V> m) {
        HashMap<K,V> map = new HashMap<K,V>();
        map.putAll(m);
        return map;
    }
    public static HashMap<String,Object> set(String k, Object v) {
        return new HashMap<String,Object>().with(k, v);
    }
    public static HashMap<String,Object> where(String k, Object v) {
        return new HashMap<String,Object>().with(k, v);
    }
    public static HashMap<String,String> attr(String k, String v) {
        return new HashMap<String,String>().with(k, v);
    }
    
    @SuppressWarnings("serial")
    public static class ArrayList<V> extends java.util.ArrayList<V> {
        public ArrayList() {
            super();
        }
        public ArrayList<V> concat(List<V> l) {
            this.addAll(l);
            return this;
        }
        public ArrayList<V> push(V ... values) {
            this.addAll(Arrays.asList(values));
            return this;
        }
    }
        
    /**
     * Simple static function for list literals.
     * @param values The list of things to turn into a list literal.
     * @return
     */
    public static <V> List<V> list(V ... values) {
        return Arrays.asList(values);
    }
    public static <V> List<V> list(List<V> l) {
        List<V> list = new ArrayList<V>();
        list.addAll(l);
        return list;
    }
    
    /**
     * String join helper function
     * @param list The list of strings to join
     * @param joiner The string to put inbetween the lists
     */
    public static String join(List<String> list) {
        return join("", list);
    }
    public static String join(String joiner, List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i=0; i<list.size()-1; ++i) {
            sb.append(list.get(i));
            sb.append(joiner);
        }
        sb.append(list.get(list.size()-1));
        return sb.toString();
    }
}
