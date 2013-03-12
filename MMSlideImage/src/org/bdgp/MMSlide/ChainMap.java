package org.bdgp.MMSlide;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class and static function for declaring map literals.
 * @param <K> The key type
 * @param <V>
 */
public class ChainMap<K,V> implements Map<K,V> {
    private Map<K,V> map_;
    
    public ChainMap(Map<K,V> map_) {
        this.map_ = map_;
    }
    
    // Various chained putters
    public ChainMap<K,V> with(K k, V v) { this.put(k, v); return this; }
    public ChainMap<K,V> and(K k, V v) { this.put(k, v); return this; }
    public ChainMap<K,V> o(K k, V v) { this.put(k, v); return this; }
    public ChainMap<K,V> _(K k, V v) { this.put(k, v); return this; }
    
    // Map implementation
    @Override
    public int size() { return map_.size(); }
    @Override
    public boolean isEmpty() { return map_.isEmpty(); }
    @Override
    public boolean containsKey(Object key) { return map_.containsKey(key); }
    @Override
    public boolean containsValue(Object value) { return map_.containsValue(value); }
    @Override
    public V get(Object key) { return map_.get(key); }
    @Override
    public V put(K key, V value) { return map_.put(key, value); }
    @Override
    public V remove(Object key) { return map_.remove(key); }
    @Override
    public void putAll(Map<? extends K, ? extends V> m) { map_.putAll(m); }
    @Override
    public void clear() { map_.clear(); }
    @Override
    public Set<K> keySet() { return map_.keySet(); }
    @Override
    public Collection<V> values() { return map_.values(); }
    @Override
    public Set<java.util.Map.Entry<K, V>> entrySet() { return map_.entrySet(); }
    
    // static methods for creating various HashMaps
    public static <K,V> ChainMap<K,V> map(K k, V v) {
        return new ChainMap<K,V>(new HashMap<K,V>()).with(k, v);
    }
    public static ChainMap<String,Object> set(String k, Object v) {
        return new ChainMap<String,Object>(new HashMap<String,Object>()).with(k, v);
    }
    public static ChainMap<String,Object> where(String k, Object v) {
        return new ChainMap<String,Object>(new HashMap<String,Object>()).with(k, v);
    }
    public static ChainMap<String,String> attr(String k, String v) {
        return new ChainMap<String,String>(new HashMap<String,String>()).with(k, v);
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
