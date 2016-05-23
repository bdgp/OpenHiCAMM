package org.bdgp.OpenHiCAMM;


import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.bdgp.OpenHiCAMM.ImageLog.ImageLogRecord;
import org.bdgp.OpenHiCAMM.DB.Config;
import org.bdgp.OpenHiCAMM.DB.ModuleConfig;
import org.bdgp.OpenHiCAMM.DB.Task;
import org.bdgp.OpenHiCAMM.DB.TaskConfig;
import org.bdgp.OpenHiCAMM.DB.TaskDispatch;
import org.bdgp.OpenHiCAMM.DB.WorkflowInstance;
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
    private Connection instanceDb;
    
    private File workflowDirectory;
    private WorkflowInstance instance;
    
    private Dao<WorkflowModule> workflow;
    private Dao<ModuleConfig> moduleConfig;
    private Dao<ModuleConfig> defaultModuleConfig;
    private Dao<WorkflowInstance> workflowInstance;
    private Dao<TaskDispatch> taskDispatch;
    private Dao<TaskConfig> taskConfig;
    private Dao<Task> taskStatus;
    
    private Map<String,Module> moduleInstances;
    
    private List<Handler> logHandlers;
    private Level logLevel;
    
    private ExecutorService pool;
    private int maxThreads;
    
    private List<TaskListener> taskListeners;
    private OpenHiCAMM mmslide;
    
    private boolean isStopped;
    private Logger logger;
    private int logLabelLength = 14;
    
    private String instancePath;
    private String instanceDbName;
    
    private Set<Task> notifiedTasks;
    private Logger.LogFileHandler logFileHandler;
    private boolean resume;
    private Long startTime;
    private WorkflowModule startModule;
    
    public static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    
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
            Integer instanceId, 
            Level loglevel,
            OpenHiCAMM mmslide) 
    {
        // Load the workflow database and workflow table
        if (workflowDirectory == null || !workflowDirectory.exists() || !workflowDirectory.isDirectory()) {
            throw new RuntimeException("Directory "+workflowDirectory+" is not a valid directory.");
        }
        this.workflowDb = Connection.get(new File(workflowDirectory, WORKFLOW_DB).getPath());
        Dao<WorkflowModule> workflow = this.workflowDb.file(WorkflowModule.class, new File(WORKFLOW_FILE).getPath());
        
        // set the workflow directory
        this.workflowInstance = this.workflowDb.table(WorkflowInstance.class);
        this.workflowDirectory = workflowDirectory;
        this.workflow = workflow;

        // set the instance DB
        this.instance = instanceId == null? newWorkflowInstance() :
                        workflowInstance.selectOneOrDie(where("id",instanceId));
        this.instancePath = new File(this.workflowDirectory, this.instance.getStorageLocation()).getPath();
        this.instanceDbName = String.format("%s.db", this.instance.getName());
        this.instanceDb = Connection.get(new File(workflowDirectory, WORKFLOW_DB).getPath(), this.instance.getName());
        
        // init the notified tasks set
        this.notifiedTasks = new HashSet<Task>();
        // init the logger
        this.logHandlers = new ArrayList<Handler>();
        this.logLevel = Level.CONFIG;
        this.initLogger();
        
        // set the number of cores to use in the thread pool
        this.maxThreads = Runtime.getRuntime().availableProcessors();
        this.pool = Executors.newFixedThreadPool(this.maxThreads+1);

		// initialize various Dao's for convenience
        this.moduleConfig = this.instanceDb.file(ModuleConfig.class, new File(this.instance.getStorageLocation(), MODULECONFIG_FILE).getPath());
        this.defaultModuleConfig = this.workflowDb.file(ModuleConfig.class, new File(DEFAULT_MODULECONFIG_FILE).getPath());
        this.taskConfig = this.instanceDb.table(TaskConfig.class);
        this.taskStatus = this.instanceDb.table(Task.class);
        this.taskDispatch = this.instanceDb.table(TaskDispatch.class);
        
        this.taskListeners = new ArrayList<TaskListener>();
        this.mmslide = mmslide;
        this.isStopped = true;
        this.startTime = null;
        this.startModule = null;
        
        // instantiate the workflow module object instances
        this.moduleInstances = new HashMap<String,Module>();
        this.loadModuleInstances();
        
        // set the logLabelLength
        this.logLabelLength = 14;
        for (WorkflowModule w : workflow.select()) {
            if (this.logLabelLength < w.getName().length()+7) {
                this.logLabelLength = w.getName().length()+7;
            }
        }

        // move default moduleconfig into the instance moduleconfig
        for (WorkflowModule w : workflow.select()) {
            List<ModuleConfig> configs = this.moduleConfig.select(where("id", w.getId()));
            Set<String> keySet = new HashSet<String>();
            for (ModuleConfig mc : configs) {
                keySet.add(mc.getKey());
            }
            List<ModuleConfig> defaultConfigs = this.defaultModuleConfig.select(where("id", w.getId()));
            for (ModuleConfig dc : defaultConfigs) {
                if (!keySet.contains(dc.getKey())) this.moduleConfig.insertOrUpdate(dc,"id","key");
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
                Module m = w.getModule().newInstance();
                m.initialize(this, w);
                moduleInstances.put(w.getName(), m); 
            } 
            catch (InstantiationException e) {throw new RuntimeException(e);} 
            catch (IllegalAccessException e) {throw new RuntimeException(e);}
        }
    }
    
    /**
     * Initialize the workflow instance's subdirectories and tasks.txt files.
     * @return the assigned instance_id for the new workflow instance.
     */
    private WorkflowInstance newWorkflowInstance() {
        WorkflowInstance instance = new WorkflowInstance();
        workflowInstance.insert(instance);
        // Create a new directory for the workflow instance.
        instance.createStorageLocation(this.workflowDirectory.getPath());
        workflowInstance.update(instance,"id");
        
        return instance;
    }
    
    public void deleteTaskRecords() {
        List<WorkflowModule> modules = this.workflow.select(where("parentId", null));
        Collections.sort(modules, (a,b)->a.getPriority().compareTo(b.getPriority()));
        for (WorkflowModule m : modules) {
            this.deleteTaskRecords(m);
        }
    }
    public void deleteTaskRecords(String moduleName) {
        WorkflowModule module = this.workflow.selectOneOrDie(where("name",moduleName));
        this.deleteTaskRecords(module);
    }
    public void deleteTaskRecords(WorkflowModule module) {
        // Delete any child task/dispatch records
        List<WorkflowModule> childModules = this.workflow.select(where("parentId",module.getId()));
        Collections.sort(childModules, (a,b)->a.getPriority().compareTo(b.getPriority()));
        for (WorkflowModule child : childModules) {
            deleteTaskRecords(child);
        }
        // Delete task dispatch and config records
        List<Task> tasks = taskStatus.select(where("moduleId",module.getId()));
        for (Task task : tasks) {
            taskConfig.delete(where("id",task.getId()));
            taskDispatch.delete(where("taskId",task.getId()));
        }
        // Then delete task records
        taskStatus.delete(where("moduleId",module.getId()));
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
        Module m = this.moduleInstances.get(module.getName());
        childTasks = m.createTaskRecords(tasks != null? tasks : new ArrayList<Task>(), moduleConfig, logger);
        List<WorkflowModule> modules = workflow.select(
        		where("parentId",module != null? module.getId() : null));
        Collections.sort(modules, (a,b)->a.getPriority().compareTo(b.getPriority()));
        for (WorkflowModule mod : modules) {
        	this.createTaskRecords(mod, childTasks, moduleConfig, logger);
        }
    }
    
    public void runInitialize(String moduleName) {
        WorkflowModule module = this.workflow.selectOneOrDie(where("name",moduleName));
        runInitialize(module);
    }
    public void runInitialize(WorkflowModule module) {
        Module m = this.moduleInstances.get(module.getName());
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
                    new File(this.workflowDirectory,
                            new File(this.instance.getStorageLocation(), LOG_FILE).getPath()).getPath());
        } 
        catch (SecurityException e) {throw new RuntimeException(e);} 
        catch (IOException e) {throw new RuntimeException(e);}
        
        this.logger = this.makeLogger("WorkflowRunner");
        return this.logger;
    }
    
    public void logWorkflowInfo(String startModuleName) {
        // log some info on this workflow
        this.logger.info(
                String.format("Running workflow instance: %s",
                WorkflowRunner.this.instance.getName()));

        this.logger.info(String.format("Using workflow DB directory: %s", this.workflowDirectory));
        this.logger.info(String.format("Using workflow DB: %s", WORKFLOW_DB));
        this.logger.info(String.format("Using instance directory: %s", this.instancePath));
        this.logger.info(String.format("Using instance DB: %s", this.instanceDbName));
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
                Module m = moduleInstances.get(module.getName());
                if (m == null) {
                    throw new RuntimeException("No instantiated module found with ID: "+module.getName());
                }
                // print workflow module info
                this.logger.info(String.format("    %s", module.toString(true)));
                this.logger.info(String.format(
                        "    %s(title=%s, description=%s, type=%s)", 
                        m.getClass().getSimpleName(),
                        Util.escape(m.getTitle()),
                        Util.escape(m.getDescription()),
                        m.getTaskType()));
                // print workflow module config
                List<ModuleConfig> configs = this.moduleConfig.select(where("id", module.getId()));
                Collections.sort(configs, new Comparator<ModuleConfig>() {
                    @Override public int compare(ModuleConfig a, ModuleConfig b) {
                        return a.getId()-b.getId();
                    }});
                for (ModuleConfig config : configs) {
                    this.logger.info(String.format("        %s", config));
                }
                // Print the tasks associated with this module
                List<Task> tasks = this.taskStatus.select(where("moduleId",module.getId()));
                Collections.sort(tasks, new Comparator<Task>() {
                    @Override public int compare(Task a, Task b) {
                        return a.getId()-b.getId();
                    }});
                for (Task task : tasks) {
                    this.logger.info(String.format("    %s", task));
                    // Print the task configs for the task
                    List<TaskConfig> taskConfigs = this.taskConfig.select(where("id", task.getId()));
                    Collections.sort(taskConfigs, new Comparator<TaskConfig>() {
                        @Override public int compare(TaskConfig a, TaskConfig b) {
                            return new Integer(a.getId()).intValue()-new Integer(b.getId()).intValue();
                        }});
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
                		parentTask.getName(), parentTask.getStatus(), parentModule.getTaskType());
                // Get the child task label
        		Task task = this.taskStatus.selectOneOrDie(where("id", td.getTaskId()));
                Module module = this.moduleInstances.get(task.getModuleId());
                String label = String.format("%s:%s:%s", task.getName(), task.getStatus(), module.getTaskType());
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
                String label = String.format("%s:%s:%s", task.getName(), task.getStatus(), module.getTaskType());
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
            Collections.sort(modules, new Comparator<WorkflowModule>() {
                @Override public int compare(WorkflowModule a, WorkflowModule b) {
                    return a.getClassName().compareTo(b.getClassName());
                }});

            List<WorkflowModule> childModules = new ArrayList<WorkflowModule>();
            for (WorkflowModule module : modules) {
                List<Task> tasks = this.taskStatus.select(where("moduleId",module.getId()));
                Collections.sort(tasks, new Comparator<Task>() {
                    @Override public int compare(Task a, Task b) {
                        return a.getId()-b.getId();
                    }});
                final Map<Status,Integer> stats = new HashMap<Status,Integer>();
                for (Task task : tasks) {
                    stats.put(task.getStatus(), 
                            stats.containsKey(task.getStatus())? stats.get(task.getStatus())+1 : 1);
                }
                List<Status> sortedStats = new ArrayList<Status>(stats.keySet());
                Collections.sort(sortedStats, new Comparator<Status>() {
                    @Override public int compare(Status a, Status b) {
                        return stats.get(b).compareTo(stats.get(a));
                    }});
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
                    Integer slideId = slideIdConf != null? new Integer(slideIdConf.getValue()) : null;
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
            updatedTasks += this.updateTaskRecordsOnResume(t);
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
            final String startModuleName, 
            final Map<String,Config> inheritedTaskConfig,
            final boolean resume) 
    {
        this.resume = resume;
        this.startModule = this.workflow.selectOneOrDie(where("name", startModuleName));
        this.pool = Executors.newFixedThreadPool(this.maxThreads+1);
        this.notifiedTasks.clear();
        Future<Status> future = pool.submit(new Callable<Status>() {
            @Override
            public Status call() {
            	try {
            		// set ImageJ batch mode
                    ij.macro.Interpreter.batchMode = true;

                    WorkflowRunner.this.isStopped = false;
                    WorkflowRunner.this.startTime = System.currentTimeMillis();

                    int taskCount = 0;
                    if (WorkflowRunner.this.resume) {
                        logger.info("Updating task records...");
                        List<Task> tasks = WorkflowRunner.this.taskStatus.select(where("moduleId",startModule.getId()));
                        tasks.sort((a,b)->a.getId()-b.getId());
                        for (Task t : tasks) {
                            taskCount += updateTaskRecordsOnResume(t);
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
                        taskCount = getTaskCount(startModuleName);
                    }

                    // call the runInitialize module method
                    WorkflowRunner.this.logger.info("Calling runInitialize on all modules...");
                    WorkflowRunner.this.runInitialize(startModuleName);

                    // Notify the task listeners of the maximum task count
                    for (TaskListener listener : taskListeners) {
                        listener.taskCount(taskCount);
                        listener.startTime(startTime);
                        //listener.debug(String.format("Set task count: %d", taskCount));
                    }

                    // Log some information on this workflow
                    //WorkflowRunner.this.logWorkflowInfo(startModuleName);
                    if (resume) logger.info("Scanning for previous tasks...");

                    // start the first task(s)
                    List<Task> start = taskStatus.select(where("moduleId",startModule.getId()));
                    Collections.sort(start, (a,b)->a.getId()-b.getId());
                    List<Future<Status>> futures = new ArrayList<Future<Status>>();
                    for (Task t : start) {
                        Future<Status> future = run(t, inheritedTaskConfig);
                        futures.add(future);
                        // If this is a serial task and it failed, don't run the successive sibling tasks
                        if (WorkflowRunner.this.moduleInstances.get(t.getModuleId()).getTaskType() == Module.TaskType.SERIAL) {
                        	Status status;
                            try { status = future.get(); }
                            catch (InterruptedException e) {
                                WorkflowRunner.this.logger.severe(String.format(
                                        "Top-level task %s was interrupted, setting status to DEFER", t.getName()));
                                status = Status.DEFER;
                            } 
                            catch (ExecutionException e) {throw new RuntimeException(e);} 
                            if (status != Status.SUCCESS && status != Status.DEFER) {
                                WorkflowRunner.this.logger.severe(String.format(
                                        "Top-level task %s returned status %s, not running successive sibling tasks",
                                        t.getName(), status));
                                break;
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
                    while (WorkflowRunner.this.notifiedTasks.size() < taskCount) {
                        Thread.sleep(1000);
                    }

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
                            new ModuleConfig(startModule.getId(), "duration", new Long(endTime - startTime).toString()), "id","key");

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
            final Task task, 
            final Map<String,Config> inheritedTaskConfig) 
    {
    	this.logger.fine(String.format("%s: running task %s", task.getName(), task));

        final WorkflowModule module = this.workflow.selectOneOrDie(
                where("id",task.getModuleId()));
        
        // get an instance of the module
        final Module taskModule = moduleInstances.get(module.getName());
        if (taskModule == null) {
            throw new RuntimeException("No instantiated module found with ID: "+module.getName());
        }
                
        // merge the task and module configuration
        List<Config> configs = new ArrayList<Config>();
        List<ModuleConfig> moduleConfigs = moduleConfig.select(where("id",task.getModuleId()));
        for (ModuleConfig moduleConfig : moduleConfigs) {
            configs.add(moduleConfig);
            this.logger.fine(String.format("%s: using module config: %s", task.getName(), moduleConfig));
        }
        if (inheritedTaskConfig != null) {
            for (Map.Entry<String,Config> entry : inheritedTaskConfig.entrySet()) {
                configs.add(entry.getValue());
                this.logger.fine(String.format("%s: using inherited task config: %s", task.getName(), entry.getValue()));
            }
        }
        List<TaskConfig> taskConfigs = taskConfig.select(where("id",task.getId()));
        for (TaskConfig tc : taskConfigs) {
        	configs.add(tc);
            this.logger.fine(String.format("%s: using task config: %s", task.getName(), tc));
        }
        final Map<String,Config> config = Config.merge(configs);
        
        Callable<Status> callable = new Callable<Status>() {
            @Override
            public Status call() {
            	WorkflowRunner.this.taskStatus.reload(task, "id");
                Status status = task.getStatus();
            	
            	if (WorkflowRunner.this.isStopped == true) return status;
                WorkflowRunner.this.logger.fine(String.format(
                        "%s: Previous status was: %s", task.getName(), status));

            	if (status == Status.NEW || status == Status.IN_PROGRESS) {
                    // run the task
                    WorkflowRunner.this.logger.info(String.format("%s: Running task", task.getName()));
                    Logger taskLogger = WorkflowRunner.this.makeLogger(task.getName());
                    try {
                        status = taskModule.run(task, config, taskLogger);
                    } 
                    // Uncaught exceptions set the status to ERROR
                    catch (Throwable e) {
                        StringWriter sw = new StringWriter();
                        e.printStackTrace(new PrintWriter(sw));
                        WorkflowRunner.this.logger.severe(String.format("%s: Error reported during task:%n%s", 
                                task.getName(), sw.toString()));
                        status = Status.ERROR;
                    }
                    finally {
                        WorkflowRunner.this.logger.fine(String.format("%s: Calling cleanup", task.getName()));
                        taskModule.cleanup(task, config, taskLogger); 
                    }
                    WorkflowRunner.this.logger.fine(String.format("%s: Finished running task", task.getName()));
                    WorkflowRunner.this.logger.info(String.format("%s: Setting task status to %s", task.getName(), status));
            	}
                task.setStatus(status);
                taskStatus.update(task, "id");

                // notify any task listeners
                notifyTaskListeners(task);
                
                String dispatchUUID = UUID.randomUUID().toString();
                if (status == Status.SUCCESS) {
                    try {
                        DatabaseConnection db = taskStatus.getConnectionSource().getReadWriteConnection();
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
                                    StatementType.UPDATE, new FieldType[0], DatabaseConnection.DEFAULT_RESULT_FLAGS);
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

                if (status == Status.SUCCESS && !pool.isShutdown()) {
                    // get the child tasks to be dispatched by this task using the dispatch UUID
                    WorkflowRunner.this.logger.fine(String.format("dispatchUUID=%s", dispatchUUID));
                    List<Task> childTasks = taskStatus.select(where("dispatchUUID", dispatchUUID));
                    // Sort tasks by task ID
                    Collections.sort(childTasks, new Comparator<Task>() {
                        @Override public int compare(Task a, Task b) {
                            return a.getId()-b.getId();
                        }});
                    WorkflowRunner.this.logger.fine(String.format("Found %d tasks with dispatchUUID=%s", 
                            childTasks.size(), dispatchUUID));

                    // enqueue the child tasks
                    for (Task childTask : childTasks) {
                        WorkflowRunner.this.logger.fine(String.format("%s: Dispatching child task: %s", 
                                task.getName(), childTask.toString()));
                        Future<Status> future = run(childTask, config);
                        WorkflowRunner.this.logger.fine(String.format("%s: Returned from dispatch of child task: %s", 
                                task.getName(), childTask.toString()));

                        // If a serial task fails, don't run the successive sibling tasks
                        Module.TaskType childTaskType = WorkflowRunner.this.moduleInstances.get(childTask.getModuleId()).getTaskType();
                        if (childTaskType == Module.TaskType.SERIAL && future.isCancelled()) {
                            WorkflowRunner.this.logger.severe(String.format(
                                    "Child task %s was cancelled, not running successive sibling tasks",
                                    childTask.getName()));
                            // update the status of the cancelled task to ERROR
                            childTask.setStatus(Status.ERROR);
                            WorkflowRunner.this.getTaskStatus().update(childTask,"id");
                            // notify task listeners
                            notifyTaskListeners(childTask);
                            break;
                        }
                        if (childTaskType == Module.TaskType.SERIAL && future.isDone()) {
                            Status s = null;
                            try { s = future.get(); } 
                            catch (InterruptedException e) {
                                WorkflowRunner.this.logger.severe(String.format(
                                        "Child task %s was interrupted",
                                        childTask.getName()));
                            } 
                            catch (ExecutionException e) {throw new RuntimeException(e);}
                            if (s != null && s != Task.Status.SUCCESS && s != Task.Status.DEFER) {
                                WorkflowRunner.this.logger.severe(String.format(
                                        "Child task %s returned status %s, not running successive sibling tasks",
                                        childTask.getName(), s));
                                // notify the task listeners
                                notifyTaskListeners(childTask);
                                break;
                            }
                        }
                    }
                }

                WorkflowRunner.this.logger.fine(String.format("%s: Returning status: %s", task.getName(), status));
                return status;
            }
        };
                
        Future<Status> future;
        // Serial tasks get run immediately
        if (taskModule.getTaskType() == Module.TaskType.SERIAL) {
            FutureTask<Status> futureTask = new FutureTask<Status>(callable);
            this.logger.fine(String.format("%s: Starting serial task", task.getName()));
            futureTask.run();
            future = futureTask;
        }
        // Parallel tasks get put in the task pool
        else if (taskModule.getTaskType() == Module.TaskType.PARALLEL) {
            this.logger.fine(String.format("%s: Submitting parallel task", task.getName()));
            future = pool.submit(callable);
        }
        else {
            throw new RuntimeException("Unknown task type: "+taskModule.getTaskType());
        }
        return future;
    }
    
    public int getTaskCount(String startModuleName) {
        // Get the set of tasks that will be run using this the start module ID
        final Set<Task> tasks = new HashSet<Task>();
        List<WorkflowModule> modules = this.getWorkflow().select(where("name",startModuleName));
        while (modules.size() > 0) {
            Collections.sort(modules, (a,b)->a.getPriority().compareTo(b.getPriority()));
            List<WorkflowModule> childModules = new ArrayList<WorkflowModule>();
            for (WorkflowModule module : modules) {
                tasks.addAll(this.getTaskStatus().select(where("moduleId",module.getId())));
                childModules.addAll(this.getWorkflow().select(where("parentId",module.getId())));
            }
            modules = childModules;
        }
        return tasks.size();
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
        List<Runnable> runnables = pool.shutdownNow();

        long endTime = System.currentTimeMillis();
        String endTimestamp = dateFormat.format(new Date(endTime)); 
        WorkflowRunner.this.getModuleConfig().insertOrUpdate(
                new ModuleConfig(startModule.getId(), "endTime", endTimestamp), "id","key");
        WorkflowRunner.this.getModuleConfig().insertOrUpdate(
                new ModuleConfig(startModule.getId(), "duration", new Long(endTime - startTime).toString()), "id","key");


        // Log a summary
        logTaskSummary(this.startModule.getName());

        logElapsedTime(this.startTime, endTime);

        return runnables;
    }
    
    /**
     * Get a list of the instance ids from the workflow instance file inside
     * a workflow directory.
     * @param workflowDirectory
     * @return
     */
    public static List<Integer> getInstanceIds(String workflowDirectory) {
        List<Integer> instanceIds = new ArrayList<Integer>();
        Connection workflowDb = Connection.get(new File(workflowDirectory, WORKFLOW_DB).getPath());
        if (workflowDb != null) {
            Dao<WorkflowInstance> workflowInstance = workflowDb.table(WorkflowInstance.class);
            for (WorkflowInstance instance : workflowInstance.select()) {
                instanceIds.add(instance.getId());
            }
            Collections.sort(instanceIds);
        }
        return instanceIds;
    }
    
    // Various getters/setters
    public Dao<WorkflowModule> getWorkflow() { return workflow; }
    public Dao<ModuleConfig> getModuleConfig() { return moduleConfig; }
    public Dao<ModuleConfig> getDefaultModuleConfig() { return defaultModuleConfig; }
    public Dao<WorkflowInstance> getWorkflowInstanceDao() { return workflowInstance; }
    public Level getLogLevel() { return logLevel; }
    public void setLogLevel(Level logLevel) { this.logLevel = logLevel; }
    public int getMaxThreads() { return maxThreads; }
    public WorkflowInstance getWorkflowInstance() { return instance; }
    public Dao<Task> getTaskStatus() { return taskStatus; }
    public Dao<TaskDispatch> getTaskDispatch() { return taskDispatch; }
    public Dao<TaskConfig> getTaskConfig() { return taskConfig; }
    public Connection getWorkflowDb() { return workflowDb; }
    public File getWorkflowDir() { return workflowDirectory; }
    public Connection getInstanceDb() { return instanceDb; }
    public OpenHiCAMM getOpenHiCAMM() { return mmslide; }
    public Map<String,Module> getModuleInstances() { return moduleInstances; }
    
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
                Module module = this.moduleInstances.get(m.getName());
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
            this.workflowDb.getReadWriteConnection().executeStatement("shutdown compact", DatabaseConnection.DEFAULT_RESULT_FLAGS);
        } 
        catch (SQLException e) {throw new RuntimeException(e);}
    }
}
