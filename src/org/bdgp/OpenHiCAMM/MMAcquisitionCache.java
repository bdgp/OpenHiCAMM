package org.bdgp.OpenHiCAMM;

import java.util.List;
import java.util.ArrayList;

import org.micromanager.acquisition.MMAcquisition;
import org.micromanager.utils.MMScriptException;

public class MMAcquisitionCache {
    private static List<MMCache> cache = new ArrayList<MMCache>();
    private static final int MAX_CACHE = 10;

    private MMAcquisitionCache() { }
    
    public static synchronized MMAcquisition getAcquisition(
            String name, 
            String dir, 
            boolean show, 
            boolean diskCached, 
            boolean existing) 
    {
        MMCache mmCache = new MMCache(name, dir, show, diskCached, existing);
        if (!cache.contains(mmCache)) {
            if (cache.size() >= MAX_CACHE) cache.remove(0);
            cache.add(mmCache);
        }
        return cache.get(cache.indexOf(mmCache)).getAcquisition();
    }
    
    public static class MMCache {
        private String name;
        private String dir;
        private boolean show;
        private boolean diskCached;
        private boolean existing;
        private MMAcquisition acquisition;

        public MMCache(String name, String dir, boolean show, boolean diskCached, boolean existing) {
            this.name = name;
            this.dir = dir;
            this.show = show;
            this.diskCached = diskCached;
            this.existing = existing;
        }

        public String getName() { return name; }
        public String getDir() { return dir; }
        public boolean isShow() { return show; }
        public boolean isDiskCached() { return diskCached; }
        public boolean isExisting() { return existing; }
        public MMAcquisition getAcquisition() { 
            if (this.acquisition == null) {
                try { 
                    this.acquisition = new MMAcquisition(name, dir, show, diskCached, existing); 
                    this.acquisition.initialize(); 
                } 
                catch (MMScriptException e) {throw new RuntimeException(e);}
            }
            return this.acquisition;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((dir == null) ? 0 : dir.hashCode());
            result = prime * result + (diskCached ? 1231 : 1237);
            result = prime * result + (existing ? 1231 : 1237);
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            result = prime * result + (show ? 1231 : 1237);
            return result;
        }
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            MMCache other = (MMCache) obj;
            if (dir == null) {
                if (other.dir != null)
                    return false;
            } else if (!dir.equals(other.dir))
                return false;
            if (diskCached != other.diskCached)
                return false;
            if (existing != other.existing)
                return false;
            if (name == null) {
                if (other.name != null)
                    return false;
            } else if (!name.equals(other.name))
                return false;
            if (show != other.show)
                return false;
            return true;
        }
    }
}
