package org.bdgp.MMSlide;

import java.util.Arrays;
import java.util.List;

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
    }
    // static methods for creating various HashMaps
    public static <K,V> HashMap<K,V> map(K k, V v) {
        return new HashMap<K,V>().with(k, v);
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
        
    /**
     * Simple static function for list literals.
     * @param values The list of things to turn into a list literal.
     * @return
     */
    public static <V> List<V> list(V ... values) {
        return Arrays.asList(values);
    }
    
}
