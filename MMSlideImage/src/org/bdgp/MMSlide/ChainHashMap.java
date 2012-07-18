package org.bdgp.MMSlide;

import java.util.HashMap;

/**
 * Class and static function for declaring map literals.
 * @param <K> The key type
 * @param <V>
 */
@SuppressWarnings("serial")
public class ChainHashMap<K,V> extends HashMap<K,V> {
    public ChainHashMap() {
        super();
    }
    public ChainHashMap(K k, V v) {
        super();
        this.put(k, v);
    }
    public ChainHashMap<K,V> with(K k, V v) { this.put(k, v); return this; }
    public ChainHashMap<K,V> and(K k, V v) { this.put(k, v); return this; }
    
    public static <K,V> ChainHashMap<K,V> map(K k, V v) {
        return new ChainHashMap<K,V>(k, v);
    }
    public static ChainHashMap<String,Object> set(String k, Object v) {
        return new ChainHashMap<String,Object>(k, v);
    }
    public static ChainHashMap<String,Object> where(String k, Object v) {
        return new ChainHashMap<String,Object>(k, v);
    }
    public static ChainHashMap<String,String> attr(String k, String v) {
        return new ChainHashMap<String,String>(k, v);
    }
}
