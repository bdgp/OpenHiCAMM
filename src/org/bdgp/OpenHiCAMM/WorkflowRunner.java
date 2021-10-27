package org.bdgp.OpenHiCAMM;


import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.Timer;

import org.bdgp.OpenHiCAMM.ImageLog.ImageLogRecord;
import org.bdgp.OpenHiCAMM.DB.Config;
import org.bdgp.OpenHiCAMM.DB.ModuleConfig;
import org.bdgp.OpenHiCAMM.DB.Pool;
import org.bdgp.OpenHiCAMM.DB.PoolSlide;
import org.bdgp.OpenHiCAMM.DB.Slide;
import org.bdgp.OpenHiCAMM.DB.Task;
import org.bdgp.OpenHiCAMM.DB.TaskConfig;
import org.bdgp.OpenHiCAMM.DB.TaskDispatch;
import org.bdgp.OpenHiCAMM.DB.WorkflowModule;
import org.bdgp.OpenHiCAMM.DB.Task.Status;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Configuration;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.ImageLogger;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Module;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.TaskListener;

import com.j256.ormlite.field.FieldType;
import com.j256.ormlite.field.SqlType;
import com.j256.ormlite.stmt.StatementBuilder.StatementType;
import com.j256.ormlite.support.CompiledStatement;
import com.j256.ormlite.support.DatabaseConnection;

import mmcorej.StrVector;

import static org.bdgp.OpenHiCAMM.Util.set;
import static org.bdgp.OpenHiCAMM.Util.where;

public class WorkflowRunner {
    /**
     * Default file names for the metadata files.
     */

    public static final String WORKFLOW_DB = "workflow.db";
    public static final String LOG_FILE = "workflow.log";
    
    public static final String MODULECONFIG_FILE = "ModuleConfig.txt";
    public static final String DEFAULT_MODULECONFIG_FILE = "DefaultModuleConfig.txt";
    public static final String WORKFLOW_FILE = "Workflow.txt";
    
    private Connection workflowDb;
    
    private File workflowDirectory;
    
    private Dao<WorkflowModule> workflow;
    private Dao<ModuleConfig> moduleConfig;
    private Dao<TaskDispatch> taskDispatch;
    private Dao<TaskConfig> taskConfig;
    private Dao<Task> taskStatus;
    
    private Map<Integer,Module> moduleInstances;
    
    private List<Handler> logHandlers;
    private Level logLevel;
    
    private ThreadPoolExecutor executor;
    private int maxThreads;
    
    private List<TaskListener> taskListeners;
    private OpenHiCAMM mmslide;
    
    private boolean isStopped;
    private Logger logger;
    private int logLabelLength = 14;
    
    private Set<Task> notifiedTasks;
    private Logger.LogFileHandler logFileHandler;
    private boolean resume;
    private Long startTime;
    private WorkflowModule startModule;
    
    public static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    
    private static WorkflowRunner workflowRunnerInstance;

    public boolean isResume() {
        return resume;
    }

    /**
     * Constructor for WorkflowRunner. This loads the workflow.txt file 
     * in the workflow directory and performs some consistency checks 
     * on the workflow described in the file.
     * @param workflowDirectory
     */
    public WorkflowRunner(
            File workflowDirectory, 
            Level loglevel,
            OpenHiCAMM mmslide)
    {
        // Load the workflow database and workflow table
        if (workflowDirectory == null || !workflowDirectory.exists() || !workflowDirectory.isDirectory()) {
            throw new RuntimeException("Directory "+workflowDirectory+" is not a valid directory.");
        }
        this.workflowDb = Connection.get(new File(workflowDirectory, WORKFLOW_DB).getPath());
        Dao<WorkflowModule> workflow = this.workflowDb.file(WorkflowModule.class, WORKFLOW_FILE);
        
        // set the workflow directory
        this.workflowDirectory = workflowDirectory;
        this.workflow = workflow;

        // init the notified tasks set
        this.notifiedTasks = new HashSet<Task>();
        // init the logger
        this.logHandlers = new ArrayList<Handler>();
        this.logLevel = loglevel;
        this.initLogger();
        
        // set the number of cores to use in the thread pool
        this.maxThreads = Runtime.getRuntime().availableProcessors();
        this.executor = new ThreadPoolExecutor(this.maxThreads+1, this.maxThreads+1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());

		// initialize various Dao's for convenience
        this.moduleConfig = this.workflowDb.file(ModuleConfig.class, DEFAULT_MODULECONFIG_FILE);
        this.taskConfig = this.workflowDb.table(TaskConfig.class);
        this.taskStatus = this.workflowDb.table(Task.class);
        this.taskDispatch = this.workflowDb.table(TaskDispatch.class);
        
        this.taskListeners = new ArrayList<TaskListener>();
        this.mmslide = mmslide;
        this.isStopped = true;
        this.startTime = null;
        this.startModule = null;
        
        // instantiate the workflow module object instances
        this.moduleInstances = new HashMap<Integer,Module>();
        this.loadModuleInstances();
        
        // set the logLabelLength
        this.logLabelLength = 14;
        for (WorkflowModule w : workflow.select()) {
            if (this.logLabelLength < w.getName().length()+7) {
                this.logLabelLength = w.getName().length()+7;
            }
        }

        workflowRunnerInstance = this;
    }

    // instantiate the workflow module object instances
    public void loadModuleInstances() {
        this.moduleInstances.clear();
        
        // Re-load the module instances
        List<WorkflowModule> modules = workflow.select();
        Collections.sort(modules, (a,b)->a.getPriority().compareTo(b.getPriority()));
        for (WorkflowModule w : modules) {
            // Make sure parent IDs are defined
            if (w.getParentId() != null) {
                List<WorkflowModule> parent = workflow.select(where("id", w.getParentId()));
                if (parent.size() == 0) {
                    throw new RuntimeException("Workflow references unknown parent ID "+w.getParentId());
                }
            }
            // Make sure all modules implement Module
            if (!Module.class.isAssignableFrom(w.getModule())) {
                throw new RuntimeException("First module "+w.getClassName()
                        +" in Workflow does not inherit the Module interface.");
            }
            // Instantiate the module instances and put them in a hash
            try { 
                Module m = w.getModule().getDeclaredConstructor().newInstance();
                m.initialize(this, w);
                moduleInstances.put(w.getId(), m); 
            } 
            catch (InstantiationException|IllegalArgumentException|InvocationTargetException|NoSuchMethodException|IllegalAccessException e) 
            {
            	throw new RuntimeException(e);
            }
        }
    }
    
    public int deleteTaskRecords() {
        List<WorkflowModule> modules = this.workflow.select(where("parentId", null));
        Collections.sort(modules, (a,b)->a.getPriority().compareTo(b.getPriority()));
        int deleted=0;
        for (WorkflowModule m : modules) {
            deleted += this.deleteTaskRecords(m);
        }
        return deleted;
    }
    public int deleteTaskRecords(String moduleName) {
        WorkflowModule module = this.workflow.selectOneOrDie(where("name",moduleName));
        return this.deleteTaskRecords(module);
    }
    public int deleteTaskRecords(WorkflowModule module) {
        // Delete any child task/dispatch records
        List<WorkflowModule> childModules = this.workflow.select(where("parentId",module.getId()));
        Collections.sort(childModules, (a,b)->a.getPriority().compareTo(b.getPriority()));
        int deleted=0;
        for (WorkflowModule child : childModules) {
            deleted += deleteTaskRecords(child);
        }
        // Delete task dispatch and config records
        List<Task> tasks = taskStatus.select(where("moduleId",module.getId()));
        for (Task task : tasks) {
            taskConfig.delete(where("id",task.getId()));
            taskDispatch.delete(where("taskId",task.getId()));
        }
        // Then delete task records
        deleted += taskStatus.delete(where("moduleId",module.getId()));
        return deleted;
    }
    public int deleteTaskRecords(Task task) {
        List<TaskDispatch> tds = this.taskDispatch.select(where("parentTaskId",task.getId()));
        int deleted=0;
        if (tds.isEmpty()) {
            this.taskConfig.delete(where("id",task.getId()));
            deleted += this.taskStatus.delete(task, "id");
        }
        else {
            for (TaskDispatch td : tds) {
                this.taskDispatch.delete(td);
                Task t = this.taskStatus.selectOne(where("id", td.getTaskId()));
                if (t != null) {
                    deleted += this.deleteTaskRecords(t);
                }
            }
        }
        return deleted;
    }
    
    public void createTaskRecords(String moduleName) {
        WorkflowModule module = this.workflow.selectOneOrDie(where("name",moduleName));
        createTaskRecords(module, new ArrayList<Task>(), new HashMap<String,Config>(), this.logger);
    }
    public void createTaskRecords(WorkflowModule module, List<Task> tasks, Map<String,Config> configs, Logger logger) {
        // merge the task and module configuration

        Map<String,Config> moduleConfig = new HashMap<>(configs);
        List<ModuleConfig> moduleConfigs = this.getModuleConfig().select(where("id",module.getId()));
        for (ModuleConfig mc : moduleConfigs) {
            moduleConfig.put(mc.getKey(), mc);
        }
        
    	List<Task> childTasks = new ArrayList<Task>();
        Module m = this.moduleInstances.get(module.getId());
        childTasks = m.createTaskRecords(tasks != null? tasks : new ArrayList<Task>(), moduleConfig, logger);
        List<WorkflowModule> modules = workflow.select(
        		where("parentId",module != null? module.getId() : null));
        Collections.sort(modules, (a,b)->a.getPriority().compareTo(b.getPriority()));
        for (WorkflowModule mod : modules) {
        	this.createTaskRecords(mod, childTasks, moduleConfig, logger);
        }
        // update the task count and notify listeners
        if (this.startModule != null) {
            getTaskCount(this.startModule.getName(), true);
        }
    }
    
    public void runInitialize(String moduleName) {
        WorkflowModule module = this.workflow.selectOneOrDie(where("name",moduleName));
        runInitialize(module);
    }
    public void runInitialize(WorkflowModule module) {
        Module m = this.moduleInstances.get(module.getId());
        // If there are any new/in-progress tasks, then call runInitialize()
        List<Task> newTasks = new ArrayList<Task>();
        newTasks.addAll(this.getTaskStatus().select(where("moduleId", module.getId()).and("status", Status.NEW)));
        newTasks.addAll(this.getTaskStatus().select(where("moduleId", module.getId()).and("status", Status.IN_PROGRESS)));
        if (newTasks.size() > 0) {
            m.runInitialize();
        }

        List<WorkflowModule> modules = workflow.select(where("parentId", module.getId()));
        Collections.sort(modules, (a,b)->a.getPriority().compareTo(b.getPriority()));
        for (WorkflowModule mod : modules) {
        	this.runInitialize(mod);
        }
    }

    public Logger makeLogger(String label) {
        // Right-justify the label
        String justifiedLabel = String.format(String.format("%%%ds", this.logLabelLength), label);
    	// initialize the workflow logger
        Logger logger = Logger.create(null, justifiedLabel, logLevel);
        for (Handler handler : this.logHandlers) {
            logger.addHandler(handler);
        }
        logger.addHandler(this.logFileHandler);
        return logger;
    }

    public Logger initLogger() {
        // init the log file handler
        try {
            this.logFileHandler =  new Logger.LogFileHandler(
                    new File(this.workflowDirectory, LOG_FILE).getPath());
        } 
        catch (SecurityException e) {throw new RuntimeException(e);} 
        catch (IOException e) {throw new RuntimeException(e);}
        
        this.logger = this.makeLogger("WorkflowRunner");
        return this.logger;
    }
    
    public void logWorkflowInfo(String startModuleName) {
        this.logger.info(String.format("Using workflow DB directory: %s", this.workflowDirectory));
        this.logger.info(String.format("Using workflow DB: %s", WORKFLOW_DB));
        this.logger.info(String.format("Using thread pool with %d max threads", this.maxThreads));
        
        // Log the workflow module info
        this.logger.info("Workflow Modules:");
        List<WorkflowModule> modules = this.workflow.select(where("name",startModuleName));
        Map<Integer,String> labels = new HashMap<Integer,String>();
        GraphEasy graph = new GraphEasy();
        while (modules.size() > 0) {
            Collections.sort(modules, new Comparator<WorkflowModule>() {
                @Override public int compare(WorkflowModule a, WorkflowModule b) {
                    return a.getClassName().compareTo(b.getClassName());
                }});
            List<WorkflowModule> childModules = new ArrayList<WorkflowModule>();
            for (WorkflowModule module : modules) {
                Module m = moduleInstances.get(module.getId());
                if (m == null) {
                    throw new RuntimeException("No instantiated module found with ID: "+module.getName());
                }
                // print workflow module info
                this.logger.info(String.format("    %s", module.toString()));
                this.logger.info(String.format(
                        "    %s(title=%s, description=%s, type=%s)", 
                        m.getClass().getSimpleName(),
                        Util.escape(m.getTitle()),
                        Util.escape(m.getDescription()),
                        m.getTaskType()));
                // print workflow module config
                List<ModuleConfig> configs = this.moduleConfig.select(where("id", module.getId()));
                Collections.sort(configs, (a,b)->a.getId()-b.getId());
                for (ModuleConfig config : configs) {
                    this.logger.info(String.format("        %s", config));
                }
                // Print the tasks associated with this module
                List<Task> tasks = this.taskStatus.select(where("moduleId",module.getId()));
                Collections.sort(tasks, (a,b)->a.getId()-b.getId());
                for (Task task : tasks) {
                    this.logger.info(String.format("    %s", task));
                    // Print the task configs for the task
                    List<TaskConfig> taskConfigs = this.taskConfig.select(where("id", task.getId()));
                    Collections.sort(taskConfigs, (a,b)->a.getId()-b.getId());
                    for (TaskConfig taskConfig : taskConfigs) {
                        this.logger.info(String.format("        %s", taskConfig));
                    }
                }
                // build a workflow graph
                String label = String.format("%s:%s", module.getName(), m.getTaskType());
                labels.put(module.getId(), label);
                if (module.getParentId() != null) {
                    graph.addEdge(labels.get(module.getParentId()), label);
                }
                // now evaluate any child nodes
                childModules.addAll(this.workflow.select(where("parentId",module.getId())));
            }
            modules = childModules;
        }
        // draw the workflow module graph
        this.logger.fine(String.format("PATH=%s", System.getenv("PATH")));
        try { this.logger.info(String.format("Workflow Graph:%n%s", graph.graph())); }
        catch (IOException e) {
            this.logger.warning(String.format("Could not draw workflow graph: %s", e));
        }
        
        // draw the task dispatch graph
        // drawTaskDispatchGraph(startModuleName);
    }
    
    public void drawTaskDispatchGraph(String startModuleName) {
        // Draw the task dispatch graph
        // Start with all tasks associated with the start module ID
        WorkflowModule startModule = this.workflow.selectOneOrDie(where("name", startModuleName));
        List<Task> tasks = this.taskStatus.select(where("moduleId", startModule.getId()));
        List<TaskDispatch> dispatches = new ArrayList<TaskDispatch>();
        GraphEasy taskGraph = new GraphEasy();
        // Add all task dispatches associated with the first set of tasks
        for (Task task : tasks) {
        	dispatches.addAll(this.taskDispatch.select(where("parentTaskId", task.getId())));
        }
        // Iterate through the task dispatch tree and load all edges into the graph
        Set<Task> seen = new HashSet<Task>();
        while (dispatches.size() > 0) {
        	List<TaskDispatch> childDispatches = new ArrayList<TaskDispatch>();
        	for (TaskDispatch td : dispatches) {
        		// Get the parent task label
        		Task parentTask = this.taskStatus.selectOneOrDie(where("id", td.getParentTaskId()));
                Module parentModule = this.moduleInstances.get(parentTask.getModuleId());
                String parentLabel = String.format("%s:%s:%s", 
                		parentTask.getName(workflow), parentTask.getStatus(), parentModule.getTaskType());
                // Get the child task label
        		Task task = this.taskStatus.selectOneOrDie(where("id", td.getTaskId()));
                Module module = this.moduleInstances.get(task.getModuleId());
                String label = String.format("%s:%s:%s", task.getName(workflow), task.getStatus(), module.getTaskType());
                // Add the edge and record the tasks as being visited
        		taskGraph.addEdge(parentLabel, label);
        		seen.add(parentTask);
        		seen.add(task);
        		// Get the next set of child dispatches
        		childDispatches.addAll(this.taskDispatch.select(where("parentTaskId", task.getId())));
        	}
        	dispatches = childDispatches;
        }
        // Now find all the singleton tasks with no dispatch records
        for (Task task : tasks) {
        	// If the task was never included in an edge, then display it as a singleton task
        	if (!seen.contains(task)) {
                Module module = this.moduleInstances.get(task.getModuleId());
                String label = String.format("%s:%s:%s", task.getName(workflow), task.getStatus(), module.getTaskType());
        		taskGraph.addEdge(label);
        		seen.add(task);
        	}
        }
        // If there are too many tasks, skip drawing the graph
        final int MAX_TASKS_IN_GRAPH = 25;
        if (tasks.size() < MAX_TASKS_IN_GRAPH) {
            try { this.logger.info(String.format("Task Dispatch Graph:%n%s", taskGraph.graph())); }
            catch (IOException e) {
                this.logger.warning(String.format("Could not draw task graph: %s", e));
            }
        }
        else {
            this.logger.info(String.format("Too many tasks (%d), not drawing the task graph", tasks.size()));
        }
    }
    
    /**
     * Display a summary of all the task statuses
     */
    private void logTaskSummary(String startModuleName) {
        List<WorkflowModule> modules = this.workflow.select(where("name",startModuleName));
        this.logger.info("");
        this.logger.info("Task Status Summary:");
        this.logger.info("====================");
        while (modules.size() > 0) {
            Collections.sort(modules, (a,b)->a.getClassName().compareTo(b.getClassName()));

            List<WorkflowModule> childModules = new ArrayList<WorkflowModule>();
            for (WorkflowModule module : modules) {
                List<Task> tasks = this.taskStatus.select(where("moduleId",module.getId()));
                Collections.sort(tasks, (a,b)->a.getId()-b.getId());
                Map<Status,Integer> stats = new HashMap<Status,Integer>();
                for (Task task : tasks) {
                    stats.put(task.getStatus(), 
                            stats.containsKey(task.getStatus())? stats.get(task.getStatus())+1 : 1);
                }
                List<Status> sortedStats = new ArrayList<Status>(stats.keySet());
                Collections.sort(sortedStats, (a,b)->stats.get(b).compareTo(stats.get(a)));
                for (Status status : sortedStats) {
                    this.logger.info(String.format("Module %s: Status %s: %d / %d tasks (%.02f%%)",
                            Util.escape(module.getName()), 
                            status, 
                            stats.get(status), 
                            tasks.size(),
                            ((double)stats.get(status) / (double)tasks.size())*100.0));
                }
                // now evaluate any child nodes
                List<WorkflowModule> ms = this.workflow.select(where("parentId",module.getId()));
                Collections.sort(ms, (a,b)->a.getPriority().compareTo(b.getPriority()));
                childModules.addAll(ms);
            }
            modules = childModules;
        }
        this.logger.info("");
    }
    
    public void logTaskStatusSummary(String message) {
        this.logger.info(message);
        List<WorkflowModule> modules = new ArrayList<>();
        WorkflowModule startModule = this.workflow.selectOneOrDie(where("id", this.startModule.getId()));
        modules.add(startModule);
        while (!modules.isEmpty()) {
            List<WorkflowModule> newModules = new ArrayList<>();
            for (WorkflowModule module : modules) {
                Integer oldSlideId = null;
                List<Task> tasks = this.taskStatus.select(where("moduleId", module.getId()));
                Collections.sort(tasks, (a,b)->a.getId()-b.getId());
                StringBuilder sb = new StringBuilder();
                for (Task task : tasks) {
                    TaskConfig slideIdConf = this.taskConfig.selectOne(where("id",task.getId()).and("key", "slideId"));
                    Integer slideId = slideIdConf != null? Integer.parseInt(slideIdConf.getValue()) : null;
                    if (!Objects.equals(oldSlideId, slideId)) sb.append(slideId != null? String.format(" S%s:", slideId) : " ");
                    sb.append(task.getStatus().toString().charAt(0));
                    oldSlideId = slideId;
                }
                logger.info(String.format("    Module %s: %s", module.getName(), sb.toString()));
                newModules.addAll(this.workflow.select(where("parentId", module.getId())));
            }
            modules.clear();
            modules.addAll(newModules);
        }
    }
    
    private int updateTaskRecordsOnResume(Task task) {
        Module module = moduleInstances.get(task.getModuleId());
        if (module == null) throw new RuntimeException(String.format(
                "Unknown module: %s", task.getModuleId()));
        int updatedTasks = 0;
        // do not update tasks with successful/failed status
        if (task.getStatus().equals(Status.SUCCESS) || task.getStatus().equals(Status.FAIL)) {
            return updatedTasks;
        }
        Status status = module.setTaskStatusOnResume(task);
        if (status != null) {
            task.setStatus(status);
            this.taskStatus.update(task, "id");
            ++updatedTasks;
        }
        List<TaskDispatch> dispatch = taskDispatch.select(where("parentTaskId", task.getId()));
        List<Task> tasks = new ArrayList<>();
        for (TaskDispatch td : dispatch) {
            tasks.addAll(this.taskStatus.select(where("id", td.getTaskId())));
        }
        tasks.sort((a,b)->a.getId()-b.getId());
        for (Task t : tasks) {
            if (this.taskStatus.selectOne(where("id", t.getId())) != null) {
                updatedTasks += this.updateTaskRecordsOnResume(t);
            }
        }
        if (updatedTasks > 0) {
            this.taskStatus.update(
                    set("dispatchUUID", null), 
                    where("id", task.getId()));
        }
        return updatedTasks;
    }

    /**
     * Start the workflow runner
     * @param startModuleName The starting module
     * @param resume Should we resume a previous run?
     * @param inheritedTaskConfig Inherited task configuration. Can be null.
     * @return
     */
    public Future<Status> run(
            String startModuleName, 
            Map<String,Config> inheritedTaskConfig,
            boolean resume) 
    {
        this.resume = resume;
        this.startModule = this.workflow.selectOneOrDie(where("name", startModuleName));
        this.executor = new ThreadPoolExecutor(this.maxThreads+1, this.maxThreads+1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
        this.notifiedTasks.clear();
        Future<Status> future = executor.submit(new Callable<Status>() {
            @Override
            public Status call() {
            	try {
            		// set ImageJ batch mode
                    ij.macro.Interpreter.batchMode = true;

                    WorkflowRunner.this.isStopped = false;
                    WorkflowRunner.this.startTime = System.currentTimeMillis();

                    if (WorkflowRunner.this.resume) {
                        logger.info("Updating task records...");
                        List<Task> tasks = WorkflowRunner.this.taskStatus.select(where("moduleId",startModule.getId()));
                        tasks.sort((a,b)->a.getId()-b.getId());
                        for (Task t : tasks) {
                            updateTaskRecordsOnResume(t);
                        }
                    }
                    else {
                        // delete and re-create the task records
                        logger.info("Creating task records...");
                        WorkflowRunner.this.deleteTaskRecords(startModuleName);
                        WorkflowRunner.this.createTaskRecords(startModuleName);
                        
                        String startTimestamp = dateFormat.format(new Date(WorkflowRunner.this.startTime)); 
                        WorkflowRunner.this.getModuleConfig().insertOrUpdate(
                                new ModuleConfig(startModule.getId(), "startTime", startTimestamp), "id","key");
                        WorkflowRunner.this.getModuleConfig().delete(
                                where("id", startModule.getId()).
                                and("key", "endTime"));
                        WorkflowRunner.this.getModuleConfig().delete(
                                where("id", startModule.getId()).
                                and("key", "duration"));
                    }
                    int taskCount = getTaskCount(startModuleName, false);

                    // call the runInitialize module method
                    WorkflowRunner.this.logger.info("Calling runInitialize on all modules...");
                    WorkflowRunner.this.runInitialize(startModuleName);

                    // Notify the task listeners of the maximum task count
                    for (TaskListener listener : taskListeners) {
                        listener.setTaskCount(taskCount);
                        listener.startTime(startTime);
                        //listener.debug(String.format("Set task count: %d", taskCount));
                    }

                    // Log some information on this workflow
                    //WorkflowRunner.this.logWorkflowInfo(startModuleName);
                    if (resume) logger.info("Scanning for previous tasks...");

                    // start the first task(s)
                    Set<Integer> alreadyDone = new HashSet<>();
                    List<Task> start = taskStatus.select(where("moduleId",startModule.getId()));
                    List<Future<Status>> futures = new ArrayList<Future<Status>>();
                    START:
                    while (!start.isEmpty()) {
                        Collections.sort(start, (a,b)->a.getId()-b.getId());
                        for (Task t : start) {
                            if (getTaskStatus().selectOne(where("id", t.getId())) == null) {
                                WorkflowRunner.this.logger.fine(String.format(
                                        "Task %s was invalidated, skipping", t.getName(workflow)));
                                continue;
                            }
                            Future<Status> future = run(t, inheritedTaskConfig);
                            futures.add(future);
                            // If this is a serial task and it failed, don't run the successive sibling tasks
                            if (WorkflowRunner.this.moduleInstances.get(t.getModuleId()).getTaskType() == Module.TaskType.SERIAL) {
                                Status status;
                                try { status = future.get(); }
                                catch (InterruptedException e) {
                                    WorkflowRunner.this.logger.severe(String.format(
                                            "Top-level task %s was interrupted, setting status to DEFER", t.getName(workflow)));
                                    status = Status.DEFER;
                                } 
                                catch (ExecutionException e) {throw new RuntimeException(e);} 
                                alreadyDone.add(t.getId());

                                if (status != Status.SUCCESS && status != Status.DEFER) {
                                    WorkflowRunner.this.logger.severe(String.format(
                                            "Top-level task %s returned status %s, not running successive sibling tasks",
                                            t.getName(workflow), status));
                                    break START;
                                }
                            }
                        }
                        start.clear();
                        List<Task> newStart = taskStatus.select(where("moduleId",startModule.getId()));
                        for (Task s : newStart) {
                            if (!alreadyDone.contains(s.getId())) {
                                start.add(s);
                            }
                        }
                        if (!start.isEmpty()) {
                            for (TaskListener listener : taskListeners) {
                                listener.addToTaskCount(start.size());
                            }
                        }
                    }
                    
                    // Force execute all the futures
                    for (Future<Status> future : futures) {
                        try { future.get(); }
                        catch (InterruptedException e) {throw new RuntimeException(e);} 
                        catch (ExecutionException e) {throw new RuntimeException(e);} 
                    }
                    
                    // Wait until all tasks have completed
                    while (WorkflowRunner.this.executor.getActiveCount() > 1) {
                        Thread.sleep(1000);
                    }
                    // Make sure task listeners have been notified of all tasks
                    getTaskCount(startModuleName, true);

                    // Coalesce all the statuses
                    List<Status> statuses = new ArrayList<Status>();
                    List<Task> tasks = WorkflowRunner.this.taskStatus.select(where("moduleId",startModule.getId()));
                    while (tasks.size() > 0) {
                        List<TaskDispatch> dispatch = new ArrayList<TaskDispatch>();
                        for (Task task : tasks) {
                            statuses.add(task.getStatus());
                            dispatch.addAll(WorkflowRunner.this.taskDispatch.select(where("parentTaskId", task.getId())));
                        }
                        tasks.clear();
                        for (TaskDispatch td : dispatch) {
                            tasks.addAll(WorkflowRunner.this.taskStatus.select(where("id", td.getTaskId())));
                        }
                    }
                    Status status = coalesceStatuses(statuses);

                    long endTime = System.currentTimeMillis();
                    String endTimestamp = dateFormat.format(new Date(endTime)); 
                    WorkflowRunner.this.getModuleConfig().insertOrUpdate(
                            new ModuleConfig(startModule.getId(), "endTime", endTimestamp), "id","key");
                    WorkflowRunner.this.getModuleConfig().insertOrUpdate(
                            new ModuleConfig(startModule.getId(), "duration", Long.toString(endTime - startTime)), "id","key");

                    // Display a summary of all the task statuses
                    logTaskSummary(startModuleName);
                    
                    logElapsedTime(startTime, endTime);

                    return status;
            	}
            	catch (Throwable e) {
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
            		WorkflowRunner.this.logger.severe(String.format("Caught exception while running workflow: %s", 
            				sw.toString()));
            		throw new RuntimeException(e);
            	}
            	finally {
            		// unset batch mode
                    ij.macro.Interpreter.batchMode = false;
            	}
            }
        });
        return future;
    }
    
    // Display the elapsed time
    public void logElapsedTime(long startTime, long endTime) {
        long elapsedTime = endTime - startTime;
        long hours = (long)Math.floor(elapsedTime / (1000 * 60 * 60));
        long minutes = (long)Math.floor(elapsedTime / (1000 * 60)) - (hours * 60);
        double seconds = (elapsedTime / 1000.0) - (hours * 60 * 60) - (minutes * 60);
        String timeElapsed = Util.join(", ", 
                hours > 0? String.format("%d hours", hours) : null,
                minutes > 0? String.format("%d minutes", minutes) : null,
                String.format("%.1f seconds", seconds));
        WorkflowRunner.this.logger.info(String.format(
                "Time elapsed: %s", timeElapsed));
    }
    
    /**
     * Given a task, call all of its successors.
     * @param task
     * @param resume
     */
    public Future<Status> run(
            Task task, 
            Map<String,Config> inheritedTaskConfig) 
    {
    	this.logger.fine(String.format("%s: running task %s", task.getName(workflow), task));

        WorkflowModule module = this.workflow.selectOneOrDie(
                where("id",task.getModuleId()));
        
        // get an instance of the module
        Module taskModule = moduleInstances.get(module.getId());
        if (taskModule == null) {
            throw new RuntimeException("No instantiated module found with ID: "+module.getName());
        }
                
        // merge the task and module configuration
        List<Config> configs = new ArrayList<Config>();
        List<ModuleConfig> moduleConfigs = moduleConfig.select(where("id",task.getModuleId()));
        for (ModuleConfig moduleConfig : moduleConfigs) {
            configs.add(moduleConfig);
            this.logger.fine(String.format("%s: using module config: %s", task.getName(workflow), moduleConfig));
        }
        if (inheritedTaskConfig != null) {
            for (Map.Entry<String,Config> entry : inheritedTaskConfig.entrySet()) {
                configs.add(entry.getValue());
                this.logger.fine(String.format("%s: using inherited task config: %s", task.getName(workflow), entry.getValue()));
            }
        }
        List<TaskConfig> taskConfigs = taskConfig.select(where("id",task.getId()));
        for (TaskConfig tc : taskConfigs) {
        	configs.add(tc);
            this.logger.fine(String.format("%s: using task config: %s", task.getName(workflow), tc));
        }
        Map<String,Config> config = Config.merge(configs);
        
        Callable<Status> callable = new Callable<Status>() {
            @Override
            public Status call() {
            	WorkflowRunner.this.taskStatus.reload(task, "id");
                Status status = task.getStatus();
            	
            	if (WorkflowRunner.this.isStopped == true) return status;
                WorkflowRunner.this.logger.fine(String.format(
                        "%s: Previous status was: %s", task.getName(workflow), status));

            	if (status == Status.NEW || status == Status.IN_PROGRESS) {
                    // run the task up to max_tries attempts in case task times out
            		int retries = 0;
            		int max_retries = taskModule.numTimeoutsBeforeRestart();
            		while (retries < max_retries) {
            			if (retries > 0) {
                            WorkflowRunner.this.logger.warning(String.format("Task %s timed, out, retrying %s times", 
                                    task.getName(workflow), retries));
            			}
                        WorkflowRunner.this.logger.info(String.format("%s: Running task", task.getName(workflow)));
                        Logger taskLogger = WorkflowRunner.this.makeLogger(task.getName(workflow));
                        try {
                            ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
                            Long maxDuration = taskModule.getMaxAllowedDuration(task, config);
                            Future<Status> future = executor.submit(new Callable<Status>() {
                                @Override public Status call() {
                                    return taskModule.run(task, config, taskLogger);
                                }});
                            try {
                                status = maxDuration != null? future.get(maxDuration, TimeUnit.SECONDS) : future.get();
                            }
                            catch (TimeoutException e) {
                                ++retries;
                                continue;
                            }
                            finally {
                                executor.shutdown();
                            }
                        } 
                        // Uncaught exceptions set the status to ERROR
                        catch (Throwable e) {
                            StringWriter sw = new StringWriter();
                            e.printStackTrace(new PrintWriter(sw));
                            WorkflowRunner.this.logger.severe(String.format("%s: Error reported during task:%n%s", 
                                    task.getName(workflow), sw.toString()));
                            status = Status.ERROR;
                        }
                        finally {
                            WorkflowRunner.this.logger.fine(String.format("%s: Calling cleanup", task.getName(workflow)));
                            taskModule.cleanup(task, config, taskLogger); 
                        }
            		}
            		if (retries < max_retries) {
                        WorkflowRunner.this.logger.fine(String.format("%s: Finished running task", task.getName(workflow)));
                        WorkflowRunner.this.logger.info(String.format("%s: Setting task status to %s", task.getName(workflow), status));
                        task.setStatus(status);
                        taskStatus.update(task, "id");
            		}
                    // the task timed out more than max_retries times
            		else if (taskModule.restartProcessIfTimeout()) {
            			// set task status
                        status = Status.ERROR;
                        task.setStatus(status);
                        taskStatus.update(task);

                        // show a 60 second timer to cancel the re-start process
                        WorkflowRunner.this.logger.warning(String.format(
                        		"\n"+
                        		"----------------------------------------------------------------\n"+
                        		"%s: Task timed out %s times, re-starting engine in 60 seconds...\n"+
                        		"----------------------------------------------------------------\n"+
                                "\n", 
                        		task.getName(workflow), retries));
                        
                        new CountdownDialog("Re-starting process in %s seconds...", 60, ()->{
                            // shut down the executor
                            WorkflowRunner.this.stop();
                            WorkflowRunner.this.executor.shutdownNow();
                            
                            // wait and see if the tasks terminate
                            int activeCount = WorkflowRunner.this.executor.getActiveCount();
                            int activeCountRetries = 0;
                            int activeCountMaxRetries = 30;
                            while (activeCount > 0 && activeCountRetries < activeCountMaxRetries) {
                                WorkflowRunner.this.logger.info(String.format("Shutting down executor, %s tasks still running", 
                                        activeCount));
                                try { Thread.sleep(10000); } catch (InterruptedException e) { }
                                activeCount = WorkflowRunner.this.executor.getActiveCount();
                                ++activeCountRetries;
                            }
                            if (WorkflowRunner.this.executor.getActiveCount() > 0) {
                                WorkflowRunner.this.logger.info(String.format("Shutting down executor, but %s tasks still running even after %s seconds. Terminating anyway.", 
                                        activeCount, 10*activeCountRetries));
                            }

                            // shut down the database connection
                            WorkflowRunner.this.logger.info(String.format("Shutting down database connection", 
                                    activeCount, 10*activeCountRetries));
                            WorkflowRunner.this.shutdown();

                            // get executable used to launch this process
                            String exe = System.getProperty("ij.executable");
                            if (exe == null) throw new RuntimeException("System property ij.executable not found!");

                            // get the processes's PID
                            int pid = Processes.getPid();

                            // get list of micro-manager group/configs. These need to be set manually by the new process
                            List<String> configs = new ArrayList<>();
                            StrVector groups = WorkflowRunner.this.getOpenHiCAMM().getApp().getCMMCore().getAvailableConfigGroups();
                            for (String group : groups) {
                                try {
                                    String config = WorkflowRunner.this.getOpenHiCAMM().getApp().getCMMCore().getCurrentConfig(group);
                                    configs.add(String.format("%s:%s", group, config));
                                } 
                                catch (Exception e) {throw new RuntimeException(e);}
                            }

                            try {
                                 Runtime.getRuntime().exec(new String[] {
                                        exe,
                                        "--run","org.bdgp.OpenHiCAMM.ijplugin.IJPlugin",
                                        Integer.toString(pid),
                                        // mm user profile
                                        WorkflowRunner.this.getOpenHiCAMM().getApp().getUserProfile().getProfileName(),
                                        // camera group:preset, ...
                                        String.join(",", configs),
                                        // openhicamm workflow directory
                                        WorkflowRunner.this.getWorkflowDir().toString(),
                                        // openhicamm start task
                                        WorkflowRunner.this.startModule.getName(),
                                        // openhicamm number of threads
                                        Integer.toString(WorkflowRunner.this.maxThreads),
                                });
                            } 
                            catch (IOException e) {throw new RuntimeException(e);}
                        });
            		}
            		else {
                        WorkflowRunner.this.logger.warning(String.format("%s: Task timed out %s times, giving up", 
                        		task.getName(workflow), retries));
                        task.setStatus(status);
                        taskStatus.update(task, "id");
            		}
            	}
            	else {
                    task.setStatus(status);
                    taskStatus.update(task, "id");
            	}

                // notify any task listeners
                notifyTaskListeners(task);
                
                String dispatchUUID = UUID.randomUUID().toString();
                if (status == Status.SUCCESS) {
                    try {
                        DatabaseConnection db = taskStatus.getConnectionSource().getReadWriteConnection("TASK");
                        List<TaskDispatch> childTasks = taskDispatch.select(where("parentTaskId",task.getId()));
                        try {
                            for (TaskDispatch td : childTasks) {
                                CompiledStatement compiledStatement = db.compileStatement(
                                    "update TASK set \n"+
                                    "  dispatchUUID=?, \n"+
                                    "  status=case when status in ('NEW','DEFER') \n"+
                                    "              then cast('IN_PROGRESS' as longvarchar) \n"+
                                    "              else status end \n"+
                                    "where id=? \n"+
                                    "  and dispatchUUID is null \n"+
                                    "  and status in ('NEW','DEFER','SUCCESS') \n"+
                                    "  and (select count(*) \n"+
                                    "       from TASKDISPATCH td, TASK p \n"+
                                    "       where td.parentTaskId=p.id \n"+
                                    "         and td.taskId=? \n"+
                                    "         and p.status<>'SUCCESS')=0",
                                    StatementType.UPDATE, new FieldType[0], DatabaseConnection.DEFAULT_RESULT_FLAGS, false);
                                compiledStatement.setObject(0, dispatchUUID, SqlType.LONG_STRING);
                                compiledStatement.setObject(1, td.getTaskId(), SqlType.INTEGER);
                                compiledStatement.setObject(2, td.getTaskId(), SqlType.INTEGER);
                                compiledStatement.runUpdate();
                            }
                        }
                        finally {
                            taskStatus.getConnectionSource().releaseConnection(db);
                        }
                    }
                    catch (SecurityException e) { throw new RuntimeException(e); }
                    catch (SQLException e) { throw new RuntimeException(e); }
                }

                if (status == Status.SUCCESS && !executor.isShutdown()) {
                    // find all child tasks with the given dispatchUUID
                    // keep trying in case new tasks were added at runtime
                    // if not new tasks were added, stop trying
                    // this will probably only work reliably for serial tasks.
                    Set<Integer> alreadyDone = new HashSet<>();
                    WorkflowRunner.this.logger.fine(String.format("dispatchUUID=%s", dispatchUUID));
                    List<Task> childTasks = taskStatus.select(where("dispatchUUID", dispatchUUID));
                    CHILD_TASKS:
                    while (!childTasks.isEmpty()) {
                        // Sort tasks by task ID
                        Collections.sort(childTasks, (a,b)->a.getId()-b.getId());
                        WorkflowRunner.this.logger.fine(String.format("Found %d tasks with dispatchUUID=%s", 
                                childTasks.size(), dispatchUUID));

                        // enqueue the child tasks
                        for (Task childTask : childTasks) {
                            if (getTaskStatus().selectOne(where("id",childTask.getId())) == null) {
                                WorkflowRunner.this.logger.fine(String.format("%s: Child task was invalidated, skipping: %s", 
                                        task.getName(workflow), childTask.toString()));
                                continue;
                            }
                            WorkflowRunner.this.logger.fine(String.format("%s: Dispatching child task: %s", 
                                    task.getName(workflow), childTask.toString()));
                            Future<Status> future = run(childTask, config);
                            WorkflowRunner.this.logger.fine(String.format("%s: Returned from dispatch of child task: %s", 
                                    task.getName(workflow), childTask.toString()));
                            alreadyDone.add(childTask.getId());

                            // If a serial task fails, don't run the successive sibling tasks
                            Module.TaskType childTaskType = WorkflowRunner.this.moduleInstances.get(childTask.getModuleId()).getTaskType();
                            if (childTaskType == Module.TaskType.SERIAL && future.isCancelled()) {
                                WorkflowRunner.this.logger.severe(String.format(
                                        "Child task %s was cancelled, not running successive sibling tasks",
                                        childTask.getName(workflow)));
                                // update the status of the cancelled task to ERROR
                                childTask.setStatus(Status.ERROR);
                                WorkflowRunner.this.getTaskStatus().update(childTask,"id");
                                // notify task listeners
                                notifyTaskListeners(childTask);
                                break CHILD_TASKS;
                            }
                            if (childTaskType == Module.TaskType.SERIAL && future.isDone()) {
                                Status s = null;
                                try { s = future.get(); } 
                                catch (InterruptedException e) {
                                    WorkflowRunner.this.logger.severe(String.format(
                                            "Child task %s was interrupted",
                                            childTask.getName(workflow)));
                                } 
                                catch (ExecutionException e) {throw new RuntimeException(e);}
                                if (s != null && s != Task.Status.SUCCESS && s != Task.Status.DEFER) {
                                    WorkflowRunner.this.logger.severe(String.format(
                                            "Child task %s returned status %s, not running successive sibling tasks",
                                            childTask.getName(workflow), s));
                                    // notify the task listeners
                                    notifyTaskListeners(childTask);
                                    break CHILD_TASKS;
                                }
                            }
                        }
                        childTasks.clear();
                        List<Task> newChildTasks = taskStatus.select(where("dispatchUUID", dispatchUUID));
                        for (Task childTask : newChildTasks) {
                            if (!alreadyDone.contains(childTask.getId())) {
                                childTasks.add(childTask);
                            }
                        }
                        if (!childTasks.isEmpty()) {
                            for (TaskListener listener : taskListeners) {
                                listener.addToTaskCount(childTasks.size());
                            }
                        }
                    }
                }
                WorkflowRunner.this.logger.fine(String.format("%s: Returning status: %s", task.getName(workflow), status));
                return status;
            }
        };
                
        Future<Status> future;
        // Serial tasks get run immediately
        if (taskModule.getTaskType() == Module.TaskType.SERIAL) {
            FutureTask<Status> futureTask = new FutureTask<Status>(callable);
            this.logger.fine(String.format("%s: Starting serial task", task.getName(workflow)));
            futureTask.run();
            future = futureTask;
        }
        // Parallel tasks get put in the task pool
        else if (taskModule.getTaskType() == Module.TaskType.PARALLEL) {
            this.logger.fine(String.format("%s: Submitting parallel task", task.getName(workflow)));
            future = executor.submit(callable);
        }
        else {
            throw new RuntimeException("Unknown task type: "+taskModule.getTaskType());
        }
        return future;
    }
    
    public int getTaskCount(String startModuleName, boolean notify) {
        // Get the set of tasks that will be run using this the start module ID
        WorkflowModule startModule = this.getWorkflow().selectOneOrDie(
                where("name",startModuleName));
        List<Task> tasks = this.getTaskStatus().select(
                where("moduleId",startModule.getId()));
        Set<Task> totalTasks = new HashSet<>();
        while (tasks.size() > 0) {
            List<Task> childTasks = new ArrayList<>();
            for (Task t : tasks) {
                if (!totalTasks.contains(t)) {
                    totalTasks.add(t);
                    if (t.getStatus() != Status.FAIL) {
                        for (TaskDispatch td : this.getTaskDispatch().select(where("parentTaskId", t.getId()))) {
                            Task ct = this.getTaskStatus().selectOne(where("id", td.getTaskId()));
                            if (ct != null && ct.getDispatchUUID() == null) {
                                childTasks.add(ct);
                            }
                        }
                    }
                }
            }
            tasks = childTasks;
        }
        if (notify) {
            for (Task t : totalTasks) {
                notifyTaskListeners(t);
            }
        }
        return totalTasks.size();
    }

    private void notifyTaskListeners(Task task) {
        if (!WorkflowRunner.this.notifiedTasks.contains(task)) {
            WorkflowRunner.this.notifiedTasks.add(task);
            for (TaskListener listener : taskListeners) {
                listener.notifyTask(task);
                //listener.debug(String.format("Notified task: %s", task));
            }
        }
    }
    
    /**
     * Sort the child statuses and set this status to 
     * the highest priority status.
     */
    private static Status coalesceStatuses(List<Status> statuses) {
        if (statuses.size() > 0) {
            Collections.sort(statuses);
            return statuses.get(0);
        }
        return Status.SUCCESS;
    }
    
    /** 
     * Stop all actively executing tasks and stop processing any waiting tasks.
     */
    public List<Runnable> stop() {
    	this.logger.warning("Stopping all jobs");
    	isStopped = true;
        // notify any task listeners
        for (TaskListener listener : taskListeners) {
            //listener.debug("Called stop()");
            listener.stopped();
        }
        List<Runnable> runnables = executor.shutdownNow();

        long endTime = System.currentTimeMillis();
        String endTimestamp = dateFormat.format(new Date(endTime)); 
        WorkflowRunner.this.getModuleConfig().insertOrUpdate(
                new ModuleConfig(startModule.getId(), "endTime", endTimestamp), "id","key");
        WorkflowRunner.this.getModuleConfig().insertOrUpdate(
                new ModuleConfig(startModule.getId(), "duration", Long.toString(endTime - startTime)), "id","key");


        // Log a summary
        logTaskSummary(this.startModule.getName());

        logElapsedTime(this.startTime, endTime);

        return runnables;
    }
    
    // Various getters/setters
    public Dao<WorkflowModule> getWorkflow() { return workflow; }
    public Dao<ModuleConfig> getModuleConfig() { return moduleConfig; }
    public Level getLogLevel() { return logLevel; }
    public void setLogLevel(Level logLevel) { this.logLevel = logLevel; }
    public int getMaxThreads() { return maxThreads; }
    public Dao<Task> getTaskStatus() { return taskStatus; }
    public Dao<TaskDispatch> getTaskDispatch() { return taskDispatch; }
    public Dao<TaskConfig> getTaskConfig() { return taskConfig; }
    public Connection getWorkflowDb() { return workflowDb; }
    public File getWorkflowDir() { return workflowDirectory; }
    public OpenHiCAMM getOpenHiCAMM() { return mmslide; }
    public Map<Integer,Module> getModuleInstances() { return moduleInstances; }
    
    public void addTaskListener(TaskListener listener) {
        taskListeners.add(listener);
        //listener.debug(String.format("Added to workflowRunner: %s", this));
    }
    public void removeTaskListener(TaskListener listener) {
        taskListeners.remove(listener);
        //listener.debug(String.format("Removed from workflowRunner: %s", this));
    }
    public void addLogHandler(Handler handler) {
    	logHandlers.add(handler);
    	this.logger.addHandler(handler);
    	handler.publish(new LogRecord(Level.INFO, String.format("Add log handler %s to workflowRunner %s%n", handler, this)));
    }
    public boolean removeLogHandler(Handler handler) {
    	this.logger.removeHandler(handler);
    	handler.publish(new LogRecord(Level.INFO, String.format("Remove log handler %s from workflowRunner %s%n", handler, this)));
    	return logHandlers.remove(handler);
    }
    public Logger getLogger() {
    	return this.logger;
    }
    public void setMaxThreads(int threads) {
    	this.maxThreads = threads;
    }

    public List<ImageLogRecord> getImageLogRecords(Task task, Map<String,Config> config, Logger logger) {
        List<ImageLogRecord> imageLogRecords = new ArrayList<ImageLogRecord>();
        // add the configuration
        List<ModuleConfig> moduleConfigs = this.getModuleConfig().select(where("id", task.getModuleId()));
        for (ModuleConfig mc : moduleConfigs) {
            config.put(mc.getKey(), mc);
        }
        List<TaskConfig> taskConfigs = this.getTaskConfig().select(where("id", task.getId()));
        for (TaskConfig tc : taskConfigs) {
            config.put(tc.getKey(), tc);
        }
        // run the image logger on this task
        if (this.moduleInstances.containsKey(task.getModuleId())) {
            Module m = this.moduleInstances.get(task.getModuleId());
            if (ImageLogger.class.isAssignableFrom(m.getClass())) {
                imageLogRecords.addAll(((ImageLogger)m).logImages(task, config, logger));
            }
        }
        // run the image logger on child tasks
        List<TaskDispatch> tds = this.getTaskDispatch().select(where("parentTaskId", task.getId()));
        for (TaskDispatch td : tds) {
            Task childTask = this.getTaskStatus().selectOneOrDie(where("id", td.getTaskId()));
            Map<String,Config> conf = new HashMap<String,Config>();
            conf.putAll(config);
            imageLogRecords.addAll(this.getImageLogRecords(childTask, conf, logger));
        }
            
        return imageLogRecords;
    }
    
    public List<ImageLogRecord> getImageLogRecords() {
        List<ImageLogRecord> imageLogRecords = new ArrayList<ImageLogRecord>();
        List<WorkflowModule> modules = this.workflow.select(where("parentId", null));
        Collections.sort(modules, (a,b)->a.getPriority().compareTo(b.getPriority()));
        for (WorkflowModule module : modules) {
            List<Task> tasks = this.taskStatus.select(where("moduleId", module.getId()));
            for (Task task : tasks) {
                Map<String,Config> config = new HashMap<String,Config>();
                imageLogRecords.addAll(getImageLogRecords(task, config, this.getLogger()));
            }
        }
        return imageLogRecords;
    }

    /**
     * Get the set of Configuration objects to pass to the Workflow Configuration Dialog.
     * @return a map of the configuration name -> configuration
     */
    public Map<String,Configuration> getConfigurations() {
    	// get list of JPanels and load them with the configuration interfaces
    	Map<String,Configuration> configurations = new LinkedHashMap<String,Configuration>();
    	Dao<WorkflowModule> modules = this.getWorkflow();
    	List<WorkflowModule> ms = modules.select(where("parentId", null));

    	while (ms.size() > 0) {
            Collections.sort(ms, (a,b)->a.getPriority().compareTo(b.getPriority()));
    		List<WorkflowModule> newms = new ArrayList<WorkflowModule>();
    		for (WorkflowModule m : ms) {
                Module module = this.moduleInstances.get(m.getId());
                configurations.put(m.getName(), module.configure());
    			newms.addAll(modules.select(where("parentId",m.getId())));
    		}
    		ms = newms;
    	}
    	return configurations;
    }
    
    public static WorkflowRunner getInstance() {
        return workflowRunnerInstance;
    }
    public static void setInstance(WorkflowRunner workflowRunnerInstance) {
        WorkflowRunner.workflowRunnerInstance = workflowRunnerInstance;
    }
    
    public void shutdown() {
        try {
            this.workflowDb.getReadWriteConnection(null).executeStatement("shutdown", DatabaseConnection.DEFAULT_RESULT_FLAGS);
        } 
        catch (SQLException e) {throw new RuntimeException(e);}
    }

    public void copyFromProject(WorkflowRunner oldWorkflowRunner) {
        Dao<Pool> poolDao = this.getWorkflowDb().table(Pool.class);
        Dao<Pool> oldPoolDao = oldWorkflowRunner.getWorkflowDb().table(Pool.class);
        Dao<Slide> slideDao = this.getWorkflowDb().table(Slide.class);
        Dao<Slide> oldSlideDao = oldWorkflowRunner.getWorkflowDb().table(Slide.class);
        Dao<PoolSlide> poolSlideDao = this.getWorkflowDb().table(PoolSlide.class);
        Dao<PoolSlide> oldPoolSlideDao = oldWorkflowRunner.getWorkflowDb().table(PoolSlide.class);

        // delete old records
        poolSlideDao.delete();
        slideDao.delete();
        poolDao.delete();
        this.getModuleConfig().delete();
        this.getWorkflow().delete();

        // copy new records
        for (WorkflowModule wm : oldWorkflowRunner.getWorkflow().select()) {
            this.getWorkflow().insert(wm);
        }
        for (ModuleConfig mc : oldWorkflowRunner.getModuleConfig().select()) {
            this.getModuleConfig().insert(mc);
        }
        for (Pool p : oldPoolDao.select()) {
            poolDao.insert(p);
        }
        for (Slide s : oldSlideDao.select()) {
            slideDao.insert(s);
        }
        for (PoolSlide ps : oldPoolSlideDao.select()) {
            poolSlideDao.insert(ps);
        }
        
        // update sequences
        this.getWorkflow().updateSequence();
        poolDao.updateSequence();
        slideDao.updateSequence();
        poolSlideDao.updateSequence();

        // reload module instances
        this.loadModuleInstances();
        
        // set the logLabelLength
        this.logLabelLength = 14;
        for (WorkflowModule w : workflow.select()) {
            if (this.logLabelLength < w.getName().length()+7) {
                this.logLabelLength = w.getName().length()+7;
            }
        }
    }
}
