package org.bdgp.OpenHiCAMM;

import org.bdgp.OpenHiCAMM.Modules.Interfaces.Report;
import org.micromanager.MMPlugin;
import org.micromanager.Studio;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

import ij.IJ;

import org.bdgp.OpenHiCAMM.DB.Acquisition;
import org.bdgp.OpenHiCAMM.DB.Image;
import org.bdgp.OpenHiCAMM.DB.ModuleConfig;
import org.bdgp.OpenHiCAMM.DB.Task;
import org.bdgp.OpenHiCAMM.DB.TaskConfig;

import static org.bdgp.OpenHiCAMM.Util.where;

import java.io.File;
import java.nio.file.Paths;

import static org.bdgp.OpenHiCAMM.Tag.T.*;

@Plugin(type=Report.class)
public class ImageFileReport implements Report, SciJavaPlugin, MMPlugin {
    WorkflowRunner workflowRunner;
    String reportDir;
    String reportIndex;

    public ImageFileReport() { }

    @Override public void initialize(WorkflowRunner workflowRunner, String reportDir, String reportIndex) {
        this.workflowRunner = workflowRunner;
        this.reportIndex = reportIndex;
        this.reportDir = reportDir;
    }

    @Override
    public void runReport() {
        Dao<Image> imageDao = workflowRunner.getWorkflowDb().table(Image.class);
        Dao<Acquisition> acqDao = workflowRunner.getWorkflowDb().table(Acquisition.class);

        Html().with(()->{
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
                                    org.micromanager.data.Image mmimage = image.getImage(workflowRunner);
                                    try {
                                        String dir = acquisition.getDirectory();
                                        String prefix = acquisition.getPrefix();
                                        String posName = mmimage.getMetadata().getPositionName(Integer.toString(mmimage.getCoords().getStagePosition()));
                                        String fileName = mmimage.getMetadata().getFileName();
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
        }).write(reportIndex);
    }

	@Override
	public void setContext(Studio studio) { }

	@Override
	public String getName() {
		return this.getClass().getSimpleName();
	}

	@Override
	public String getHelpText() {
		return "";
	}

	@Override
	public String getVersion() {
		return "1.0";
	}

	@Override
	public String getCopyright() {
		return "";
	}
}
