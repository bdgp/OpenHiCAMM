package org.bdgp.MMSlide;

@SuppressWarnings("serial")
public class LockFileCreationException extends RuntimeException {
    public LockFileCreationException(String lockfile) {
        super(lockfile);
    }
}
