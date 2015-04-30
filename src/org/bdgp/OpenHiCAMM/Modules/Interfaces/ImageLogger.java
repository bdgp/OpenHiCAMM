package org.bdgp.OpenHiCAMM.Modules.Interfaces;

import java.util.List;
import java.util.Map;

import org.bdgp.OpenHiCAMM.ImageLog.ImageLogRecord;
import org.bdgp.OpenHiCAMM.Logger;
import org.bdgp.OpenHiCAMM.DB.Config;
import org.bdgp.OpenHiCAMM.DB.Task;

public interface ImageLogger {
    /**
     * Add log images to the ImageLog object
     */
    public List<ImageLogRecord> logImages(Task task, Map<String,Config> config, Logger logger);
}
