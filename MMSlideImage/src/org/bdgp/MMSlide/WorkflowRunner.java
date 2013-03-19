package org.bdgp.MMSlide;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bdgp.MMSlide.DB.Config;
import org.bdgp.MMSlide.DB.Task;
import org.bdgp.MMSlide.DB.TaskDispatch;
import org.bdgp.MMSlide.DB.WorkflowInstance;
import org.bdgp.MMSlide.DB.WorkflowModule;
import org.bdgp.MMSlide.DB.Task.Status;
import org.bdgp.MMSlide.DB.WorkflowModule.TaskType;
import org.bdgp.MMSlide.Logger.Level;
import org.bdgp.MMSlide.Modules.Interfaces.Module;

import static org.bdgp.MMSlide.Util.where;

public class WorkflowRunner {
    /**
     * Default file names for the metadata files.
     */
    public static final String WORKFLOW_DB = "workflow.db";
    public static final String WORKFLOW = "workflow";
    public static final String WORKFLOW_INSTANCE = "workflow_instance";
    public static final String MODULE_CONFIG = "module_config";
    public static final String TASK_CONFIG = "task_config";
    public static final String TASK_DISPATCH = "task_dispatch";
    public static final String TASK_STATUS = "tasks";
    public static final String LOG_FILE = "log.txt";
    public static final String MODULE_LIST = "META-INF/modules.txt";
    
    private Connection workflowDb;
    private Connection instanceDb;
    
    private File workflowDirectory;
    private File instanceDir;
    private int instanceId;
    
    private Dao<WorkflowModule> workflow;
    private Dao<Config> moduleConfig;
    private Dao<WorkflowInstance> workflowInstance;
    private Dao<TaskDispatch> taskDispatch;
    private Dao<Config> taskConfig;
    private Dao<Task> taskStatus;
    
    private Logger logger;
    private Level logLevel;
    
    private ExecutorService pool;
    private int maxThreads;
    private Map<String,Semaphore> resources;
    
    /**
     * Constructor for WorkflowRunner. This loads the workflow.txt file 
     * in the workflow directory and performs some consistency checks 
     * on the workflow described in the file.
     * @param workflowDirectory
     */
    public WorkflowRunner(
            File workflowDirectory, 
            Integer instanceId, 
            File workflowFile, 
            Map<String,Integer> resources,
            Level loglevel) 
    {
        try {
            // Get the workflow directory
            if (workflowDirectory == null || !workflowDirectory.exists() || !workflowDirectory.isDirectory()) {
                throw new IOException("Directory "+workflowDirectory+" is not a valid directory.");
            }
            // Get the workflow file
            if (workflowFile == null || !workflowFile.exists()) {
                throw new IOException("Workflow file "+workflowFile.getPath()
                        +" was not found.");
            }
            
            this.workflowDb = Connection.get(new File(workflowDirectory, WORKFLOW_DB).getPath());
            
            // Load the workflow file
            Dao<WorkflowModule> workflow = this.workflowDb.file(WorkflowModule.class, workflowFile.getPath());
            
            // Make sure parent IDs are defined and that all successors are compatible.
            for (WorkflowModule w : workflow.queryForAll()) {
                if (w.getParentId() != null) {
                    List<WorkflowModule> parent = workflow.queryForEq("id", w.getParentId());
                    if (parent.size() == 0) {
                        throw new SQLException("Workflow "+workflowFile.getPath()
                                +" references unknown parent ID "+w.getParentId());
                    }
                }
            }
            // Find the first workflow modules
            List<WorkflowModule> first = workflow.queryForEq("parent",null);
            if (first.size() == 0) {
                throw new SQLException("Workflow "+workflowFile.getPath()
                        +" is an empty workflow.");
            }
            
            // Make sure the first modules all inherit WorkerRoot
            for (WorkflowModule f : first) {
                if (!Module.class.isAssignableFrom(f.getModule())) {
                    throw new SQLException("First module "+f.getModuleName()
                            +" in Workflow "+workflowFile.getPath()
                            +" does not inherit the Module interface.");
                }
            }
            
            this.pool = Executors.newCachedThreadPool();
            
            this.resources = new HashMap<String,Semaphore>();
            for (Map.Entry<String,Integer> entry : resources.entrySet()) {
                this.resources.put(entry.getKey(), new Semaphore(entry.getValue()));
            }
            if (!this.resources.containsKey("CPU")) {
                this.resources.put("CPU",new Semaphore(Runtime.getRuntime().availableProcessors()));
            }
            if (!this.resources.containsKey("microscope")) {
                this.resources.put("microscope",new Semaphore(1));
            }
            
            this.moduleConfig = this.workflowDb.table(Config.class, MODULE_CONFIG);
            this.workflowInstance = this.workflowDb.table(WorkflowInstance.class, WORKFLOW_INSTANCE);
            this.workflowDirectory = workflowDirectory;
            this.workflow = workflow;
            this.logger = new Logger(new File(workflowDirectory, LOG_FILE).getPath(), 
                    "workflow", loglevel);
            
            if (instanceId == null) {
                instanceId = newWorkflowInstance();
            }
            this.instanceId = instanceId;
            this.instanceDb = Connection.get(new File(this.instanceDir, "instance.db").getPath());
            this.taskConfig = this.instanceDb.table(Config.class, TASK_CONFIG);
            this.taskStatus = this.instanceDb.table(Task.class, TASK_STATUS);
            this.taskDispatch = this.instanceDb.table(TaskDispatch.class, TASK_DISPATCH);
        }
        catch (IOException e) {throw new RuntimeException(e);}
        catch (SQLException e) {throw new RuntimeException(e);}
    }
    
    /**
     * Initialize the workflow instance's subdirectories and tasks.txt files.
     * @return the assigned instance_id for the new workflow instance.
     */
    private int newWorkflowInstance() {
        WorkflowInstance instance = new WorkflowInstance(this.workflowDirectory.getPath());
        workflowInstance.insert(instance);
        // create a new directory for the workflow instance
        if (!instanceDir.mkdirs()) {
            throw new RuntimeException("Could not create directory "
                    +instanceDir.getPath());
        }
        // make a task record that points to the workflow instance.
        this.instanceDir = new File(instance.getStorageLocation());
        return instance.getId();
    }
    
    public Future<Status> run(
            final Task start,
            final boolean resume) 
    {
        Future<Status> future = pool.submit(new Callable<Status>() {
            @Override
            public Status call() {
                Future<Status> future = run(start, resume, instanceDir, null);
                Status status;
                try { status = future.get(); } 
                catch (ExecutionException e) {throw new RuntimeException(e);}
                catch (InterruptedException e) {throw new RuntimeException(e);}
                return status;
            }
        });
        return future;
    }
    
    /**
     * Given a task, call all of its successors.
     * @param task
     * @param resume
     */
    private Future<Status> run(
            final Task task, 
            final boolean resume,
            final File taskDir,
            final Map<String,Integer> inheritedResources) 
    {
        final WorkflowModule module = this.workflow.selectOne(
                where("id",task.getModuleId()));
        
        // create the task storage location
        if (!taskDir.exists()) {
            taskDir.mkdirs();
            if (!taskDir.exists()) {
                throw new RuntimeException(
                        "Could not create directory "+taskDir.getPath());
            }
        }
            
        // merge the task and module configuration
        List<Config> configs = new ArrayList<Config>();
        configs.addAll(taskConfig.select(where("id",task.getId())));
        configs.addAll(moduleConfig.select(where("id",task.getModuleId())));
        final Map<String,Config> config = Config.merge(configs);
        
        // make sure all required fields are filled in
        for (Map.Entry<String,Config> c : config.entrySet()) {
            if (c.getValue().isRequired() && c.getValue().getValue() == null) {
                throw new RuntimeException("Required value "+
                        c.getKey()+" missing for task "+task.getModuleId());
            }
        }
            
        final WorkflowRunner workflowRunner = this;
                
        Callable<Status> callable = new Callable<Status>() {
            @Override
            public Status call() {
                Status status = task.getStatus();
                
                // get an instance of the module
                Module taskModule = null;
                try { taskModule = module.getModule().newInstance(); } 
                catch (InstantiationException e) {throw new RuntimeException(e);} 
                catch (IllegalAccessException e) {throw new RuntimeException(e);}
                
                Map<String,Integer> acquiredResources = new HashMap<String,Integer>();
                try {
                    if (!resume || status == Status.NEW || status == Status.DEFER) {
                        // set the task status to IN_PROGRESS
                        task.setStatus(Status.IN_PROGRESS);
                        taskStatus.update(task, "id","moduleId");
                        
                        // instantiate a logger for the task
                        Logger taskLogger = new Logger(
                                new File(instanceDir, LOG_FILE).getPath(),
                                task.getModuleId(),
                                logLevel);
                        
                        // figure out the required resources for this task
                        Map<String,Integer> requiredResources = new HashMap<String,Integer>();
                        requiredResources.putAll(taskModule.getResources());
                        if (!requiredResources.containsKey("CPU")) {
                            requiredResources.put("CPU", 1);
                        }
                        // acquire the required resources for this task
                        for (Map.Entry<String,Integer> resource : requiredResources.entrySet()) {
                            int requiredResource = resource.getValue();
                            if (inheritedResources != null && inheritedResources.containsKey(resource.getKey())) {
                                requiredResource -= Math.min(requiredResource, inheritedResources.get(resource.getKey()));
                            }
                            if (requiredResource > 0) {
                                taskLogger.info("Acquiring resource "+resource.getKey()+" ("+resource.getValue()+")");
                                resources.get(resource.getKey()).acquireUninterruptibly(requiredResource);
                                acquiredResources.put(resource.getKey(), requiredResource);
                                taskLogger.info("Acquired resource "+resource.getKey()+" ("+resource.getValue()+")");
                            }
                        }
                        
                        // run the successor task
                        try {
                            status = taskModule.run(workflowRunner, task, config, taskLogger);
                        } 
                        // Uncaught exceptions set the status to ERROR
                        catch (Exception e) {
                            taskLogger.severe("Error reported during task "
                                    +task.toString()+": \n"+e.toString());
                            status = Status.ERROR;
                        }
                    }
                    
                    // update the task status
                    task.setStatus(status);
                    taskStatus.update(task, "id","moduleId");
                    
                    // enqueue the child tasks
                    if (status == Status.SUCCESS) {
                        List<TaskDispatch> childTaskIds = taskDispatch.select(
                                where("parentTaskId",task.getId()));
                                    
                        List<Future<Status>> childFutures = new ArrayList<Future<Status>>();
                        for (TaskDispatch childTaskId : childTaskIds) {
                            Task childTask = taskStatus.selectOne(
                                    where("id",childTaskId.getTaskId()));
                            
                            WorkflowModule childModule = workflow.selectOne(
                                    where("id",childTask.getModuleId()));
                            File childDir = new File(taskDir, childModule.getId());
                            
                            if (module.getTaskType() == TaskType.SERIAL) {
                                // combine any inherited and acquired resources and pass them to the child task.
                                Map<String,Integer> childResources = new HashMap<String,Integer>();
                                if (inheritedResources != null) {
                                    childResources.putAll(inheritedResources);
                                }
                                for (Map.Entry<String,Integer> resource : acquiredResources.entrySet()) {
                                    Integer childResource = childResources.get(resource.getKey());
                                    childResources.put(resource.getKey(), 
                                            resource.getValue() + (childResource != null? childResource : 0));
                                }
                                childFutures.add(run(childTask, resume, childDir, childResources));
                            }
                            else if (module.getTaskType() == TaskType.PARALLEL) {
                                childFutures.add(run(childTask, resume, childDir, null));
                            }
                            else {
                                throw new RuntimeException("Unknown task type: "+module.getTaskType());
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
                        status = coalesceStatuses(statuses);
                    }
                    return status;
                } 
                finally {
                    // relinquish any acquired resources
                    for (Map.Entry<String,Integer> resource : acquiredResources.entrySet()) {
                        resources.get(resource.getKey()).release(resource.getValue());
                    }
                }
            }
        };
                
        Future<Status> future;
        // Serial tasks get run immediately
        if (module.getTaskType() == TaskType.SERIAL) {
            FutureTask<Status> futureTask = new FutureTask<Status>(callable);
            futureTask.run();
            future = futureTask;
        }
        // Parallel tasks get put in the task pool
        else if (module.getTaskType() == TaskType.PARALLEL) {
            future = pool.submit(callable);
        }
        else {
            throw new RuntimeException("Unknown task type: "+module.getTaskType());
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
     * Get a list of the instance ids from the workflow instance file inside
     * a workflow directory.
     * @param workflowDirectory
     * @return
     */
    public static List<Integer> getInstanceIds(String workflowDirectory) {
        List<Integer> instanceIds = new ArrayList<Integer>();
        Connection workflowDb = Connection.get(new File(workflowDirectory, "workflow.db").getPath(), false);
        if (workflowDb != null) {
            Dao<WorkflowInstance> workflowInstance = workflowDb.table(WorkflowInstance.class, WORKFLOW_INSTANCE);
            for (WorkflowInstance instance : workflowInstance.select()) {
                instanceIds.add(instance.getId());
            }
            Collections.sort(instanceIds);
        }
        return instanceIds;
    }
    
    /**
     * @return The list of registered modules from the META-INF/modules.txt files.
     */
    public static List<String> getModuleNames() {
        try {
            Enumeration<URL> configs = ClassLoader.getSystemResources(MODULE_LIST);
            Set<String> modules = new HashSet<String>();
            for (URL url : Collections.list(configs)) {
                BufferedReader r = new BufferedReader(
                        new InputStreamReader(url.openStream(), "utf-8"));
                String line;
                while ((line = r.readLine()) != null) {
                    Matcher m = Pattern.compile("^\\s*([^#\\s]+)").matcher(line);
                    if (m.find() && m.groupCount() > 0) {
                        Class<?> c = Class.forName(m.group(1));
                        if (!Module.class.isAssignableFrom(c)) {
                            throw new RuntimeException(
                                    "Class "+c.getName()+
                                    " does not implement the Module interface");
                        }
                        modules.add(m.group(1));
                    }
                }
            }
            return new ArrayList<String>(modules);
        } 
        catch (IOException e) {throw new RuntimeException(e);}
        catch (ClassNotFoundException e) {throw new RuntimeException(e);}
    }
    
    public File getWorkflowDirectory() { return workflowDirectory; }
    public Dao<WorkflowModule> getWorkflow() { return workflow; }
    public Dao<Config> getModuleConfig() { return moduleConfig; }
    public Dao<WorkflowInstance> getWorkflowInstance() { return workflowInstance; }
    public Logger getLogger() { return logger; }
    public Level getLogLevel() { return logLevel; }
    public void setLogLevel(Level logLevel) { this.logLevel = logLevel; }
    public int getMaxThreads() { return maxThreads; }
    public int getInstanceId() { return instanceId; }
    public Dao<Task> getTaskStatus() { return taskStatus; }
    public Dao<TaskDispatch> getTaskDispatch() { return taskDispatch; }
    public Dao<Config> getTaskConfig() { return taskConfig; }
}
