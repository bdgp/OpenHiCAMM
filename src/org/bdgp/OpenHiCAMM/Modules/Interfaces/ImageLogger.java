package org.bdgp.OpenHiCAMM.Modules.Interfaces;

import java.util.List;
import java.util.concurrent.FutureTask;

import org.bdgp.OpenHiCAMM.ImageLog.ImageLogRecord;

public interface ImageLogger {
    /**
     * Add log images to the ImageLog object
     */
    public List<FutureTask<ImageLogRecord>> logImages();
}
