package org.bdgp.OpenHiCAMM;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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
        @SafeVarargs
        final public ArrayList<V> push(V ... values) {
            this.addAll(Arrays.asList(values));
            return this;
        }
    }
        
    /**
     * Simple static function for list literals.
     * @param values The list of things to turn into a list literal.
     * @return
     */
    @SafeVarargs
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
     * @param delim The string to put inbetween the lists
     */
    public static String join(List<Object> list) {
        return join("", list);
    }
    public static String join(Object[] list) {
        return join("", Arrays.asList(list));
    }
    public static String join(String delim, Object elem1, Object elem2, Object ... elems) {
        List<Object> list = new ArrayList<Object>();
        list.add(elem1);
        list.add(elem2);
        list.addAll(Arrays.asList(elems));
        return join(delim, list);
    }
    public static String join(String delim, Object[] list) {
        return join("", Arrays.asList(list));
    }
    public static String join(String delim, List<?> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) != null) {
                if (sb.length() > 0) sb.append(delim);
                sb.append(list.get(i).toString());
            }
        }
        return sb.toString();
    }

    public static void sleep(int minwait, int maxwait) {
        try { Thread.sleep(minwait + (int)(Math.random()*(maxwait-minwait))); } 
        catch (InterruptedException e) { }
    }
    public static void sleep() {
        sleep(1000,3000);
    }
    
    public static String escape(Object in) {
    	if (in == null) return "null";
    	String str = in.toString();
    	if (str.matches("^[0-9]+$")) return str;
        return String.format("\"%s\"", str.replaceAll("(\b|\n|\t|\f|\r|\"|\\\\)", "\\\\$1"));
    }

    public static String macroEscape(Object object) {
        if (object == null) throw new RuntimeException("Cannot macroEscape null reference!");
        String value = object.toString();

        Pattern brackets = Pattern.compile("\\[|\\]");
        Pattern singleQuote = Pattern.compile("'");
        if (!brackets.matcher(value).find()) {
            return String.format("[%s]", value);
        }
        else if (!singleQuote.matcher(value).find()) {
            return String.format("'%s'", value);
        }
        else {
            throw new RuntimeException(String.format("Could not macroEscape value: %s", value));
        }
    }

}
