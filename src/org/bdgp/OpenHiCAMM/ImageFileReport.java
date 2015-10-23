package org.bdgp.OpenHiCAMM;

import org.bdgp.OpenHiCAMM.Modules.Interfaces.Report;
import org.micromanager.utils.MDUtils;

import ij.IJ;
import mmcorej.TaggedImage;

import org.bdgp.OpenHiCAMM.DB.Acquisition;
import org.bdgp.OpenHiCAMM.DB.Image;
import org.bdgp.OpenHiCAMM.DB.ModuleConfig;
import org.bdgp.OpenHiCAMM.DB.Task;
import org.bdgp.OpenHiCAMM.DB.TaskConfig;

import static org.bdgp.OpenHiCAMM.Util.where;

import java.io.File;
import java.nio.file.Paths;

import static org.bdgp.OpenHiCAMM.Tag.T.*;

public class ImageFileReport implements Report {
    WorkflowRunner workflowRunner;

    public ImageFileReport() {
        // TODO Auto-generated constructor stub
    }

    @Override public void initialize(WorkflowRunner workflowRunner) {
        this.workflowRunner = workflowRunner;
    }

    @Override
    public String runReport() {
        Dao<Image> imageDao = workflowRunner.getInstanceDb().table(Image.class);
        Dao<Acquisition> acqDao = workflowRunner.getInstanceDb().table(Acquisition.class);

        return Html().with(()->{
            Head();
            Body().with(()->{
                H1("Image File Report");
                Pre().with(()->{
                    for (ModuleConfig canImageSlidesConf : workflowRunner.getModuleConfig().select(
                            where("key","canImageSlides").
                            and("value","yes"))) 
                    {
                        for (Task task : workflowRunner.getTaskStatus().select(
                                where("moduleId", canImageSlidesConf.getId()))) 
                        {
                            TaskConfig imageIdConf = workflowRunner.getTaskConfig().selectOne(
                                    where("id", task.getId()).
                                    and("key", "imageId"));
                            if (imageIdConf != null) {
                                Image image = imageDao.selectOne(where("id", imageIdConf.getValue()));
                                if (image != null) {
                                    Acquisition acquisition = acqDao.selectOneOrDie(where("id", image.getAcquisitionId()));
                                    TaggedImage taggedImage = image.getTaggedImage(acqDao);
                                    try {
                                        String dir = acquisition.getDirectory();
                                        String prefix = acquisition.getPrefix();
                                        String posName = MDUtils.getPositionName(taggedImage.tags);
                                        String fileName = MDUtils.getFileName(taggedImage.tags);
                                        if (fileName != null) {
                                            File path = Paths.get(dir, prefix, fileName).toFile();
                                            if (!path.exists() && posName != null) {
                                                path = Paths.get(dir, prefix, posName, fileName).toFile();
                                            }
                                            if (path.exists()) {
                                                text(String.format("%s", path));
                                                IJ.log(String.format("Found path: %s", path));
                                            }
                                        }
                                    } 
                                    catch (Exception e) {throw new RuntimeException(e);}
                                }
                            }
                        }
                    }
                    
                });
            });
        }).toString();
    }

}
