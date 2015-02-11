package org.bdgp.OpenHiCAMM;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.logging.Handler;
import java.util.logging.Level;

import org.bdgp.OpenHiCAMM.DB.Config;
import org.bdgp.OpenHiCAMM.DB.ModuleConfig;
import org.bdgp.OpenHiCAMM.DB.Task;
import org.bdgp.OpenHiCAMM.DB.TaskConfig;
import org.bdgp.OpenHiCAMM.DB.TaskDispatch;
import org.bdgp.OpenHiCAMM.DB.WorkflowInstance;
import org.bdgp.OpenHiCAMM.DB.WorkflowModule;
import org.bdgp.OpenHiCAMM.DB.Task.Status;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Module;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.TaskListener;

import com.github.mdr.ascii.java.GraphBuilder;
import com.github.mdr.ascii.java.GraphLayouter;
import com.j256.ormlite.field.FieldType;
import com.j256.ormlite.field.SqlType;
import com.j256.ormlite.stmt.StatementBuilder.StatementType;
import com.j256.ormlite.support.CompiledStatement;

import static org.bdgp.OpenHiCAMM.Util.where;

public class WorkflowRunner {
    /**
     * Default file names for the metadata files.
     */
    public static final String WORKFLOW_DB = "workflow.db";
    public static final String LOG_FILE = "log.txt";
    
    private Connection workflowDb;
    private Connection instanceDb;
    
    private File workflowDirectory;
    private WorkflowInstance instance;
    
    private Dao<WorkflowModule> workflow;
    private Dao<ModuleConfig> moduleConfig;
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
    
    private String instancePath;
    private String instanceDbName;
    
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
        Dao<WorkflowModule> workflow = this.workflowDb.table(WorkflowModule.class);
        
        // set the workflow directory
        this.workflowInstance = this.workflowDb.table(WorkflowInstance.class);
        this.workflowDirectory = workflowDirectory;
        this.workflow = workflow;

        // set the instance DB
        this.instance = instanceId == null? newWorkflowInstance() :
                        workflowInstance.selectOneOrDie(where("id",instanceId));
        this.instancePath = new File(this.workflowDirectory, this.instance.getStorageLocation()).getPath();
        this.instanceDbName = String.format("%s.db", this.instance.getName());
        this.instanceDb = Connection.get(new File(instancePath, instanceDbName).getPath());

        // set the number of cores to use in the thread pool
        this.maxThreads = Runtime.getRuntime().availableProcessors();
        this.pool = Executors.newFixedThreadPool(this.maxThreads);

		// initialize various Dao's for convenience
        this.moduleConfig = this.instanceDb.table(ModuleConfig.class);
        this.taskConfig = this.instanceDb.table(TaskConfig.class);
        this.taskStatus = this.instanceDb.table(Task.class);
        this.taskDispatch = this.instanceDb.table(TaskDispatch.class);
        
        this.taskListeners = new ArrayList<TaskListener>();
        this.mmslide = mmslide;
        this.moduleInstances = new HashMap<String,Module>();
        this.isStopped = true;

        // instantiate the workflow module object instances
        for (WorkflowModule w : workflow.select()) {
            // Make sure parent IDs are defined
            if (w.getParentId() != null) {
                List<WorkflowModule> parent = workflow.select(where("id", w.getParentId()));
                if (parent.size() == 0) {
                    throw new RuntimeException("Workflow references unknown parent ID "+w.getParentId());
                }
            }
            // Make sure all modules implement Module
            if (!Module.class.isAssignableFrom(w.getModule())) {
                throw new RuntimeException("First module "+w.getModuleName()
                        +" in Workflow does not inherit the Module interface.");
            }
            // Instantiate the module instances and put them in a hash
            try { 
                Module m = w.getModule().newInstance();
                m.initialize(this, w.getId());
                moduleInstances.put(w.getId(), m); 
            } 
            catch (InstantiationException e) {throw new RuntimeException(e);} 
            catch (IllegalAccessException e) {throw new RuntimeException(e);}
        }
        
        // init the logger
        this.initLogger();
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
        this.logger.info(String.format("Created new workflow instance: %s", workflowInstance));
        return instance;
    }
    
    public void deleteTaskRecords() {
        List<WorkflowModule> modules = workflow.select(where("parentId",null));
        for (WorkflowModule module : modules) {
            deleteTaskRecords(module);
        }
        this.logger.info(String.format("Removed old task, taskdispatch and taskconfig records"));
    }
    public void deleteTaskRecords(WorkflowModule module) {
        // Delete any child task/dispatch records
        List<WorkflowModule> childModules = workflow.select(where("parentId",module.getId()));
        for (WorkflowModule child : childModules) {
            deleteTaskRecords(child);
        }
        // Delete task dispatch and config records
        List<Task> tasks = taskStatus.select(where("moduleId",module.getId()));
        for (Task task : tasks) {
            taskConfig.delete(where("id",new Integer(task.getId()).toString()));
            taskDispatch.delete(where("taskId",task.getId()));
        }
        // Then delete task records
        taskStatus.delete(where("moduleId",module.getId()));
    }
    
    public void createTaskRecords() {
    	this.logger.info("Creating new task records");
    	this.createTaskRecords(null, null);
    }
    public void createTaskRecords(WorkflowModule module, List<Task> tasks) {
    	List<Task> childTasks = new ArrayList<Task>();
    	if (module != null) {
            Module m = this.moduleInstances.get(module.getId());
            childTasks = m.createTaskRecords(tasks != null? tasks : new ArrayList<Task>());
    	}
        List<WorkflowModule> modules = workflow.select(
        		where("parentId",module != null? module.getId() : null));
        for (WorkflowModule mod : modules) {
        	this.createTaskRecords(mod, childTasks);
        }
    }
    
    public void initLogger() {
    	// initialize the workflow logger
        this.logger = Logger.create(null, "WorkflowRunner", logLevel);

        // Write to a log file in the instance dir
        this.logHandlers = new ArrayList<Handler>();
		try {
            this.logHandlers.add(new Logger.LogFileHandler(
            		new File(workflowDirectory,
            				new File(instance.getStorageLocation(), LOG_FILE).getPath()).getPath()));
		} 
		catch (SecurityException e) {throw new RuntimeException(e);} 
		catch (IOException e) {throw new RuntimeException(e);}

        for (Handler handler : this.logHandlers) {
            this.logger.addHandler(handler);
        }
    }
    
    public void logWorkflowInfo() {
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
        // TODO: Only include modules and tasks in current phase in the workflow/task graphs
        List<WorkflowModule> modules = this.workflow.select(where("parentId",null));
        Collections.sort(modules, new Comparator<WorkflowModule>() {
            @Override public int compare(WorkflowModule a, WorkflowModule b) {
                return a.getModuleName().compareTo(b.getModuleName());
            }});
        GraphBuilder<String> graph = new GraphBuilder<String>();
        Map<String,String> labels = new HashMap<String,String>();
        while (modules.size() > 0) {
            List<WorkflowModule> childModules = new ArrayList<WorkflowModule>();
            for (WorkflowModule module : modules) {
                Module m = moduleInstances.get(module.getId());
                if (m == null) {
                    throw new RuntimeException("No instantiated module found with ID: "+module.getId());
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
                        return a.getId().compareTo(b.getId());
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
                    List<TaskConfig> taskConfigs = this.taskConfig.select(where("id", new Integer(task.getId()).toString()));
                    Collections.sort(taskConfigs, new Comparator<TaskConfig>() {
                        @Override public int compare(TaskConfig a, TaskConfig b) {
                            return new Integer(a.getId()).intValue()-new Integer(b.getId()).intValue();
                        }});
                    for (TaskConfig taskConfig : taskConfigs) {
                        this.logger.info(String.format("        %s", taskConfig));
                    }
                }
                // build a workflow graph
                String label = String.format("%s:%s", module.getId(), m.getTaskType());
                labels.put(module.getId(), label);
                graph.addVertex(label);
                if (module.getParentId() != null) {
                    graph.addEdge(labels.get(module.getParentId()), label);
                }
                // now evaluate any child nodes
                childModules.addAll(this.workflow.select(where("parentId",module.getId())));
            }
            modules = childModules;
        }
        // draw the workflow module graph
        GraphLayouter<String> layout = new GraphLayouter<String>();
        this.logger.info(String.format("Workflow Graph:%n%s", layout.layout(graph.build())));

        // Draw the task dispatch graph
        GraphBuilder<String> taskGraph = new GraphBuilder<String>();
        Map<Integer,String> taskLabels = new HashMap<Integer,String>();
        List<Task> tasks = this.taskStatus.select();
        Collections.sort(tasks, new Comparator<Task>() {
            @Override public int compare(Task a, Task b) {
                return a.getId()-b.getId();
            }});
        for (int t=0; t<tasks.size(); ++t) {
        	Task task = tasks.get(t);
            Module module = this.moduleInstances.get(task.getModuleId());
            String label = String.format("%s:%s:%s", task.getName(), task.getStatus(), module.getTaskType());
            taskLabels.put(task.getId(), label);
            taskGraph.addVertex(label);
        }
        List<TaskDispatch> dispatches = this.taskDispatch.select();
        for (TaskDispatch dispatch : dispatches) {
            String parent = taskLabels.get(dispatch.getParentTaskId());
            String child = taskLabels.get(dispatch.getTaskId());
            taskGraph.addEdge(parent, child);
        }
        GraphLayouter<String> taskLayout = new GraphLayouter<String>();
        this.logger.info(String.format("Task Dispatch Graph:%n%s", taskLayout.layout(taskGraph.build())));
    }
    
    public Future<Status> run(final String startModuleId, final Map<String,Config> inheritedTaskConfig) {
        int cores = Runtime.getRuntime().availableProcessors();
        this.pool = Executors.newFixedThreadPool(cores);
        Future<Status> future = pool.submit(new Callable<Status>() {
            @Override
            public Status call() {
            	try {
                    WorkflowRunner.this.isStopped = false;
                    // Log some information on this workflow
                    WorkflowRunner.this.logWorkflowInfo();
                    // start the first task(s)
                    List<Task> start = taskStatus.select(where("moduleId",startModuleId));
                    List<Future<Status>> futures = new ArrayList<Future<Status>>();
                    for (Task t : start) {
                        Future<Status> future = run(t, inheritedTaskConfig);
                        futures.add(future);
                        // If this is a serial task and it failed, don't run the successive sibling tasks
                        if (WorkflowRunner.this.moduleInstances.get(t.getModuleId()).getTaskType() == Module.TaskType.SERIAL) {
                        	Status status;
                            try { status = future.get(); }
                            catch (InterruptedException e) {throw new RuntimeException(e);} 
                            catch (ExecutionException e) {throw new RuntimeException(e);} 
                            if (status != Status.SUCCESS) {
                                WorkflowRunner.this.logger.severe(String.format(
                                        "Top-level task %s returned status %s, not running successive sibling tasks",
                                        t.getName(), status));
                                break;
                            }
                        }
                    }
                    List<Status> statuses = new ArrayList<Status>();
                    for (Future<Status> future : futures) {
                        try { statuses.add(future.get()); }
                        catch (InterruptedException e) {throw new RuntimeException(e);} 
                        catch (ExecutionException e) {throw new RuntimeException(e);} 
                    }
                    return coalesceStatuses(statuses);
            	}
            	catch (Throwable e) {
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
            		WorkflowRunner.this.logger.severe(String.format("Caught exception while running workflow: %s", 
            				sw.toString()));
            		throw new RuntimeException(e);
            	}
            }
        });
        return future;
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
    	this.logger.info(String.format("%s: running task %s", task.getName(), task));

        final WorkflowModule module = this.workflow.selectOneOrDie(
                where("id",task.getModuleId()));
        
        // get an instance of the module
        final Module taskModule = moduleInstances.get(module.getId());
        if (taskModule == null) {
            throw new RuntimeException("No instantiated module found with ID: "+module.getId());
        }
                
        // merge the task and module configuration
        List<Config> configs = new ArrayList<Config>();
        List<ModuleConfig> moduleConfigs = moduleConfig.select(where("id",task.getModuleId()));
        for (ModuleConfig moduleConfig : moduleConfigs) {
            configs.add(moduleConfig);
            this.logger.info(String.format("%s: using module config: %s", task.getName(), moduleConfig));
        }
        if (inheritedTaskConfig != null) {
            for (Map.Entry<String,Config> entry : inheritedTaskConfig.entrySet()) {
                configs.add(entry.getValue());
                this.logger.info(String.format("%s: using inherited task config: %s", task.getName(), entry.getValue()));
            }
        }
        List<TaskConfig> taskConfigs = taskConfig.select(where("id",task.getId()));
        for (TaskConfig tc : taskConfigs) {
        	configs.add(tc);
            this.logger.info(String.format("%s: using task config: %s", task.getName(), tc));
        }
        final Map<String,Config> config = Config.merge(configs);
        
        Callable<Status> callable = new Callable<Status>() {
            @Override
            public Status call() {
                Status status = task.getStatus();
                WorkflowRunner.this.logger.info(String.format("%s: Previous status was: %s", task.getName(), status));
            	if (WorkflowRunner.this.isStopped == true) return status;
                
                // instantiate a logger for the task
                Logger taskLogger = Logger.create(
                        new File(WorkflowRunner.this.getWorkflowDir(),
                                new File(WorkflowRunner.this.getInstance().getStorageLocation(),
                                        new File(task.getStorageLocation(), LOG_FILE).getPath()).getPath()).getPath(),
                                        String.format("%s", task.getName()),
                                        logLevel);
                for (Handler handler : WorkflowRunner.this.logHandlers) {
                    taskLogger.addHandler(handler);
                }
                
                // run the task
                WorkflowRunner.this.logger.info(String.format("%s: Running task", task.getName()));
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
                    WorkflowRunner.this.logger.info(String.format("%s: Calling cleanup", task.getName()));
                    taskModule.cleanup(task); 
                }
                WorkflowRunner.this.logger.info(String.format("%s: Finished running task", task.getName()));
                
                // notify any task listeners
                for (TaskListener listener : taskListeners) {
                    listener.notifyTask(task);
                }
                
                WorkflowRunner.this.logger.info(String.format("%s: Setting task status to %s", task.getName(), status));
                task.setStatus(status);
                if (status == Status.SUCCESS) {
                    try {
                    	// We need to update the status of this task AND flag the unprocessed child tasks that 
                    	// will be ready to run. Both of these actions must be done in a single atomic DB
                    	// operation to avoid race conditions. As far as I can tell, the HSQLDB merge statement
                    	// is atomic, so hopefully this works.
                    	CompiledStatement compiledStatement = taskStatus.getConnectionSource().getReadWriteConnection().compileStatement(
                            "merge into TASK using (\n"+
                            "  select c.\"id\", p.\"id\", cast('IN_PROGRESS' as longvarchar)\n"+
                            "  from TASK p\n"+
                            "  join TASKDISPATCH td\n"+
                            "    on p.\"id\"=td.\"parentTaskId\"\n"+
                            "  join TASK c\n"+
                            "    on c.\"id\"=td.\"taskId\"\n"+
                            "  left join (TASKDISPATCH td2\n"+
                            "      join TASK p2\n"+
                            "        on p2.\"id\"=td2.\"parentTaskId\")\n"+
                            "    on c.\"id\"=td2.\"taskId\"\n"+
                            "    and p2.\"id\"<>?\n"+
                            "    and p2.\"status\"<>'SUCCESS'\n"+
                            "  where c.\"status\" in ('NEW','DEFER')\n"+
                            "    and c.\"parentTaskId\" is null\n"+
                            "    and p2.\"id\" is null\n"+
                            "    and p.\"id\"=?\n"+
                            "  union all\n"+
                            "  select p.\"id\", p.\"parentTaskId\", cast('SUCCESS' as longvarchar)\n"+
                            "  from TASK p\n"+
                            "  where p.\"id\"=?) \n"+
                            "  as t(taskId, parentTaskId, status) on TASK.\"id\"=t.taskId\n"+
                            "  when matched then update set TASK.\"parentTaskId\"=t.parentTaskId, TASK.\"status\"=t.status",
                            StatementType.UPDATE, new FieldType[0]);
                    	compiledStatement.setObject(0, task.getId(), SqlType.INTEGER);
                    	compiledStatement.setObject(1, task.getId(), SqlType.INTEGER);
                    	compiledStatement.setObject(2, task.getId(), SqlType.INTEGER);
                    	compiledStatement.runUpdate();
                    }
                    catch (SecurityException e) {throw new RuntimeException(e);}
                    catch (SQLException e) {throw new RuntimeException(e);}
                }
                else {
                	// update the task status in the DB
                	taskStatus.update(task, "id");
                }

                // enqueue the child tasks if all parent tasks completed successfully
                List<TaskDispatch> childTaskIds = taskDispatch.select(
                        where("parentTaskId",task.getId()));
                List<Future<Status>> childFutures = new ArrayList<Future<Status>>();
                if (status == Status.SUCCESS) {
                    // Sort task dispatches by task ID
                    Collections.sort(childTaskIds, new Comparator<TaskDispatch>() {
                        @Override public int compare(TaskDispatch a, TaskDispatch b) {
                            return a.getTaskId()-b.getTaskId();
                        }});
                                
                    for (TaskDispatch childTaskId : childTaskIds) {
                        Task childTask = taskStatus.selectOneOrDie(
                                where("id",childTaskId.getTaskId()));

                        if (childTask.getStatus() == Status.IN_PROGRESS && 
                            childTask.getParentTaskId() != null && 
                            childTask.getParentTaskId().equals(task.getId())) 
                        {
                        	WorkflowRunner.this.logger.info(String.format("%s: Dispatching child task: %s", task.getName(), childTask));
                        	Future<Status> future = run(childTask, config);

                        	// If a serial task fails, don't run the successive sibling tasks
                        	Module.TaskType childTaskType = WorkflowRunner.this.moduleInstances.get(childTask.getModuleId()).getTaskType();
                            if (childTaskType == Module.TaskType.SERIAL && future.isCancelled()) {
                                WorkflowRunner.this.logger.severe(String.format(
                                        "Child task %s was cancelled, not running successive sibling tasks",
                                        childTask.getName()));
                                break;
                            }
                            if (childTaskType == Module.TaskType.SERIAL && future.isDone()) {
                            	Status s;
                                try { s = future.get(); } 
                                catch (InterruptedException e) {throw new RuntimeException(e);} 
                                catch (ExecutionException e) {throw new RuntimeException(e);}
                                if (s != Task.Status.SUCCESS) {
                                	WorkflowRunner.this.logger.severe(String.format(
                                			"Child task %s returned status %s, not running successive sibling tasks",
                                			childTask.getName(), s));
                                	break;
                                }
                            }

                            // Otherwise, add the task to 
                            childFutures.add(future);
                        }
                    }
                }

                // resolve all the futures into statuses
                List<Status> statuses = new ArrayList<Status>();
                statuses.add(status);
                for (Future<Status> f : childFutures) {
                    try {
                        Status s = f.get();
                        statuses.add(s);
                    } 
                    catch (ExecutionException e) {throw new RuntimeException(e);}
                    catch (InterruptedException e) {throw new RuntimeException(e);}
                }
                // return the coalesced status
                Status returnStatus = coalesceStatuses(statuses);
                WorkflowRunner.this.logger.info(String.format("%s: Returning coalesced status: %s", task.getName(), returnStatus));
                return returnStatus;
            }
        };
                
        Future<Status> future;
        // Serial tasks get run immediately
        if (taskModule.getTaskType() == Module.TaskType.SERIAL) {
            FutureTask<Status> futureTask = new FutureTask<Status>(callable);
            this.logger.info(String.format("%s: Starting serial task", task.getName()));
            futureTask.run();
            future = futureTask;
        }
        // Parallel tasks get put in the task pool
        else if (taskModule.getTaskType() == Module.TaskType.PARALLEL) {
            this.logger.info(String.format("%s: Submitting parallel task", task.getName()));
            future = pool.submit(callable);
        }
        else {
            throw new RuntimeException("Unknown task type: "+taskModule.getTaskType());
        }
        return future;
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
     * Stop any new tasks from getting queued up.
     */
    public void stop() {
    	logger.warning("Stopping all jobs");
        isStopped = true;

        // notify any task listeners
        for (TaskListener listener : taskListeners) {
            listener.stopped();
        }
        pool.shutdown();
    }

    /** 
     * Stop all actively executing tasks and stop processing any waiting tasks.
     */
    public List<Runnable> kill() {
    	logger.warning("Killing all jobs");
    	isStopped = true;
        // notify any task listeners
        for (TaskListener listener : taskListeners) {
            listener.killed();
        }
        List<Runnable> runnables = pool.shutdownNow();
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
    public Dao<WorkflowInstance> getWorkflowInstance() { return workflowInstance; }
    public Level getLogLevel() { return logLevel; }
    public void setLogLevel(Level logLevel) { this.logLevel = logLevel; }
    public int getMaxThreads() { return maxThreads; }
    public WorkflowInstance getInstance() { return instance; }
    public Dao<Task> getTaskStatus() { return taskStatus; }
    public Dao<TaskDispatch> getTaskDispatch() { return taskDispatch; }
    public Dao<TaskConfig> getTaskConfig() { return taskConfig; }
    public Connection getWorkflowDb() { return workflowDb; }
    public File getWorkflowDir() { return workflowDirectory; }
    public Connection getInstanceDb() { return instanceDb; }
    public OpenHiCAMM getOpenHiCAMM() { return mmslide; }
    
    public void addTaskListener(TaskListener listener) {
        taskListeners.add(listener);
    }
    public void removeTaskListener(TaskListener listener) {
        taskListeners.remove(listener);
    }
    public void addLogHandler(Handler handler) {
    	logHandlers.add(handler);
    	this.logger.addHandler(handler);
    }
    public boolean removeLogHandler(Handler handler) {
    	this.logger.removeHandler(handler);
    	return logHandlers.remove(handler);
    }
    public Logger getLogger() {
    	return this.logger;
    }
}
