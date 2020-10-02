package org.bdgp.OpenHiCAMM.Modules;

import org.bdgp.OpenHiCAMM.DB.Task;
import org.bdgp.OpenHiCAMM.DB.Task.Status;
import org.bdgp.OpenHiCAMM.DB.TaskDispatch;
import org.micromanager.MMPlugin;
import org.micromanager.Studio;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

import static org.bdgp.OpenHiCAMM.Util.where;

import java.util.ArrayList;
import java.util.List;

@Plugin(type=MMPlugin.class)
public class CompareImager extends SlideImager implements SciJavaPlugin, MMPlugin {
    public CompareImager() { }

    public Status setTaskStatusOnResume(Task task) {
        if (task.getStatus() != Status.SUCCESS) {
            return Status.NEW;
        }
        // try to find the parent slide loader task
        // if slide loader parent task needs to be re-run, then re-run this as well.
        List<TaskDispatch> tds = this.workflowRunner.getTaskDispatch().select(where("taskId", task.getId()));
        while (!tds.isEmpty()) {
            List<TaskDispatch> parentTds = new ArrayList<>();
            for (TaskDispatch td : tds) {
                Task parentTask = this.workflowRunner.getTaskStatus().selectOneOrDie(where("id", td.getParentTaskId()));
                if (this.workflowRunner.getModuleConfig().selectOne(
                        where("id", parentTask.getModuleId()).
                        and("key", "canLoadSlides").
                        and("value", "yes")) != null) 
                {
                    if (parentTask.getStatus() == Status.NEW) {
                        return Status.NEW;
                    }
                    else {
                        return null;
                    }
                }
                parentTds.addAll(this.workflowRunner.getTaskDispatch().select(where("taskId", parentTask.getId())));
            }
            tds = parentTds;
        }
        return null;
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
