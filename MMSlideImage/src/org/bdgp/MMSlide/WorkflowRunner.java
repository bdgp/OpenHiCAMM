package org.bdgp.MMSlide;

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

import org.bdgp.MMSlide.DB.Config;
import org.bdgp.MMSlide.DB.ModuleConfig;
import org.bdgp.MMSlide.DB.Task;
import org.bdgp.MMSlide.DB.TaskConfig;
import org.bdgp.MMSlide.DB.TaskDispatch;
import org.bdgp.MMSlide.DB.WorkflowInstance;
import org.bdgp.MMSlide.DB.WorkflowModule;
import org.bdgp.MMSlide.DB.Task.Status;
import org.bdgp.MMSlide.Modules.Interfaces.Module;
import org.bdgp.MMSlide.Modules.Interfaces.TaskListener;

import com.j256.ormlite.field.FieldType;
import com.j256.ormlite.field.SqlType;
import com.j256.ormlite.stmt.StatementBuilder.StatementType;
import com.j256.ormlite.support.CompiledStatement;

import static org.bdgp.MMSlide.Util.where;

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
    private MMSlide mmslide;
    
    private boolean isStopped;
    private Logger logger;
    
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
            MMSlide mmslide) 
    {
        // Load the workflow database and workflow table
        if (workflowDirectory == null || !workflowDirectory.exists() || !workflowDirectory.isDirectory()) {
            throw new RuntimeException("Directory "+workflowDirectory+" is not a valid directory.");
        }
        this.workflowDb = Connection.get(new File(workflowDirectory, WORKFLOW_DB).getPath());
        Dao<WorkflowModule> workflow = this.workflowDb.table(WorkflowModule.class);
        
        int cores = Runtime.getRuntime().availableProcessors();
        this.pool = Executors.newFixedThreadPool(cores);
        
        this.workflowInstance = this.workflowDb.table(WorkflowInstance.class);
        this.workflowDirectory = workflowDirectory;
        this.workflow = workflow;

        this.instance = instanceId == null? newWorkflowInstance() :
                        workflowInstance.selectOneOrDie(where("id",instanceId));
        this.instanceDb = Connection.get(
                new File(workflowDirectory, WORKFLOW_DB).getPath(),
                new File(this.workflowDirectory, 
                		new File(this.instance.getStorageLocation(), 
                				this.instance.getName()+".db").getPath()).getPath());
        this.moduleConfig = this.instanceDb.table(ModuleConfig.class);
        this.taskConfig = this.instanceDb.table(TaskConfig.class);
        this.taskStatus = this.instanceDb.table(Task.class);
        this.taskDispatch = this.instanceDb.table(TaskDispatch.class);
        
        this.logHandlers = new ArrayList<Handler>();
		try {
            logHandlers.add(new Logger.LogFileHandler(
            		new File(workflowDirectory,
            				new File(instance.getStorageLocation(), LOG_FILE).getPath()).getPath()));
		} 
		catch (SecurityException e) {throw new RuntimeException(e);} 
		catch (IOException e) {throw new RuntimeException(e);}

        this.logger = Logger.create(null, "WorkflowRunner", logLevel);
        for (Handler handler : logHandlers) {
            this.logger.addHandler(handler);
        }

        this.taskListeners = new ArrayList<TaskListener>();
        this.mmslide = mmslide;
        this.moduleInstances = new HashMap<String,Module>();
        this.isStopped = true;

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
        List<WorkflowModule> modules = workflow.select(where("parentId",null));
        for (WorkflowModule module : modules) {
            deleteTaskRecords(module);
        }
    }
    public void deleteTaskRecords(WorkflowModule module) {
        // Delete any child task/dispatch records
        List<WorkflowModule> childModules = workflow.select(where("parentId",module.getId()));
        for (WorkflowModule child : childModules) {
            deleteTaskRecords(child);
        }
        // Delete task dispatch records
        List<Task> tasks = taskStatus.select(where("moduleId",module.getId()));
        for (Task task : tasks) {
            taskDispatch.delete(where("taskId",task.getId()));
        }
        // Then delete task records
        taskStatus.delete(where("moduleId",module.getId()));
    }
    
    public void createTaskRecords() {
        List<WorkflowModule> modules = workflow.select(where("parentId",null));
        while (modules.size() > 0) {
            List<WorkflowModule> childModules = new ArrayList<WorkflowModule>();
            for (WorkflowModule module : modules) {
                Module m = moduleInstances.get(module.getId());
                if (m == null) {
                    throw new RuntimeException("No instantiated module found with ID: "+module.getId());
                }
                m.createTaskRecords();
                childModules.addAll(workflow.select(where("parentId",module.getId())));
            }
            modules = childModules;
        }
    }
    
    public Future<Status> run(final String startModuleId, final Map<String,Config> inheritedTaskConfig) {
        int cores = Runtime.getRuntime().availableProcessors();
        this.pool = Executors.newFixedThreadPool(cores);
        Future<Status> future = pool.submit(new Callable<Status>() {
            @Override
            public Status call() {
            	WorkflowRunner.this.isStopped = false;
                List<Task> start = taskStatus.select(where("moduleId",startModuleId));
                List<Task.Status> statuses = new ArrayList<Task.Status>();
                for (Task t : start) {
                    Future<Status> future = run(t, inheritedTaskConfig);
                    try { statuses.add(future.get()); }
                    catch (InterruptedException e) {throw new RuntimeException(e);} 
                    catch (ExecutionException e) {throw new RuntimeException(e);} 
                }
                return coalesceStatuses(statuses);
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
        final WorkflowModule module = this.workflow.selectOneOrDie(
                where("id",task.getModuleId()));
        
        // get an instance of the module
        final Module taskModule = moduleInstances.get(module.getId());
        if (taskModule == null) {
            throw new RuntimeException("No instantiated module found with ID: "+module.getId());
        }
                
        // merge the task and module configuration
        List<Config> configs = new ArrayList<Config>();
        configs.addAll(moduleConfig.select(where("id",task.getModuleId())));
        if (inheritedTaskConfig != null) {
            for (Map.Entry<String,Config> entry : inheritedTaskConfig.entrySet()) {
                configs.add(entry.getValue());
            }
        }
        configs.addAll(taskConfig.select(where("id",task.getId())));
        final Map<String,Config> config = Config.merge(configs);
        
        Callable<Status> callable = new Callable<Status>() {
            @Override
            public Status call() {
                Status status = task.getStatus();
            	if (WorkflowRunner.this.isStopped == true) return status;
                
                // instantiate a logger for the task
                Logger taskLogger = Logger.create(
                        new File(WorkflowRunner.this.getWorkflowDir(),
                                new File(WorkflowRunner.this.getInstance().getStorageLocation(),
                                        new File(task.getStorageLocation(), LOG_FILE).getPath()).getPath()).getPath(),
                                        task.getModuleId(),
                                        logLevel);
                for (Handler handler : logHandlers) {
                    taskLogger.addHandler(handler);
                }
                
                // run the task
                taskLogger.info("Running module "+module.getId()+", task ID "+task.getId());
                try {
                    status = taskModule.run(task, config, taskLogger);
                } 
                // Uncaught exceptions set the status to ERROR
                catch (Exception e) {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    e.printStackTrace(pw);
                    taskLogger.severe(String.format("Error reported during task %s:%n%s", 
                            task.toString(), sw.toString()));
                    status = Status.ERROR;
                }
                finally {
                    taskModule.cleanup(task); 
                }
                taskLogger.info("Finished module "+module.getId()+", task ID "+task.getId());
                
                // notify any task listeners
                for (TaskListener listener : taskListeners) {
                    listener.notifyTask(task);
                }
                
                task.setStatus(status);
                if (status == Status.SUCCESS) {
                    try {
                    	// We need to update the status of this task AND flag the unprocessed child tasks that 
                    	// will be ready to run. Both of these actions must be done in a single atomic DB
                    	// operation to avoid race conditions. As far as I can tell, the HSQLDB merge statement
                    	// is atomic, so hopefully this works.
                    	CompiledStatement compiledStatement = taskStatus.getConnectionSource().getReadWriteConnection().compileStatement(
                            "merge into TASK using (\n"+
                            "  select c.id, p.id, 'IN_PROGRESS'\n"+
                            "  from TASK p\n"+
                            "  join TASKDISPATCH td\n"+
                            "    on p.id=td.parentTaskId\n"+
                            "  join TASK c\n"+
                            "    on c.id=td.taskId\n"+
                            "  left join (TASKDISPATCH td2\n"+
                            "      join TASK p2\n"+
                            "        on p2.id=td2.parentTaskId)\n"+
                            "    on c.id=td2.taskId\n"+
                            "    and p2.id<>?\n"+
                            "    and p2.status<>'SUCCESS'\n"+
                            "  where c.status in ('NEW','DEFER')\n"+
                            "    and c.parentTaskId is null\n"+
                            "    and p2.id is null\n"+
                            "    and p.id=?\n"+
                            "  union all\n"+
                            "  select p.id, p.parentTaskId, 'SUCCESS'\n"+
                            "  from TASK p\n"+
                            "  where p.id=?) \n"+
                            "  as t(taskId, parentTaskId, status) on TASK.id=t.taskId\n"+
                            "  when matched then update set TASK.parentTaskId=t.parentTaskId, TASK.status=t.status",
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
                	taskStatus.update(task, "id","moduleId");
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
                            childFutures.add(run(childTask, config));
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
                return coalesceStatuses(statuses);
            }
        };
                
        Future<Status> future;
        // Serial tasks get run immediately
        if (taskModule.getTaskType() == Module.TaskType.SERIAL) {
            FutureTask<Status> futureTask = new FutureTask<Status>(callable);
            futureTask.run();
            future = futureTask;
        }
        // Parallel tasks get put in the task pool
        else if (taskModule.getTaskType() == Module.TaskType.PARALLEL) {
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
    public MMSlide getMMSlide() { return mmslide; }
    
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
}
