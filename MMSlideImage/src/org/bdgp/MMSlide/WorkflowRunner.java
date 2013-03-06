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

import org.bdgp.MMSlide.Dao.Config;
import org.bdgp.MMSlide.Dao.Dao;
import org.bdgp.MMSlide.Dao.Task;
import org.bdgp.MMSlide.Dao.TaskDispatch;
import org.bdgp.MMSlide.Dao.WorkflowModule;
import org.bdgp.MMSlide.Dao.Task.Status;
import org.bdgp.MMSlide.Dao.WorkflowModule.TaskType;
import org.bdgp.MMSlide.Logger.Level;
import org.bdgp.MMSlide.Modules.Interfaces.Module;

import static org.bdgp.MMSlide.ChainMap.where;

public class WorkflowRunner {
    /**
     * Default file names for the metadata files.
     */
    public static final String WORKFLOW_STATUS_FILE = "workflow_status.txt";
    public static final String MODULE_CONFIG_FILE = "module_config.txt";
    public static final String TASK_CONFIG_FILE = "task_config.txt";
    public static final String TASK_DISPATCH_FILE = "task_dispatch.txt";
    public static final String TASK_STATUS_FILE = "tasks.txt";
    public static final String LOG_FILE = "log.txt";
    public static final String MODULE_LIST = "META-INF/modules.txt";
    
    private File workflowDirectory;
    private File instanceDir;
    private int instanceId;
    
    private Dao<WorkflowModule> workflow;
    private Dao<Config> moduleConfig;
    private Dao<Task> workflowStatus;
    private Dao<TaskDispatch> taskDispatch;
    private Dao<Config> taskConfig;
    private Dao<Task> taskStatus;
    
    private Logger logger;
    private Level logLevel;
    
    private ExecutorService pool;
    private Semaphore sem;
    private int maxThreads;
    
    
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
            Integer maxThreads, 
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
            
            // Load the workflow file
            Dao<WorkflowModule> workflow = Dao.get(WorkflowModule.class, workflowFile.getPath());
            
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
            this.maxThreads = maxThreads;
            if (maxThreads != null) {
                this.sem = new Semaphore(maxThreads);
            }
                    
            this.moduleConfig = Dao.get(Config.class, 
                new File(workflowDirectory, MODULE_CONFIG_FILE).getPath());
            this.workflowStatus = Dao.get(Task.class, 
                new File(workflowDirectory, WORKFLOW_STATUS_FILE).getPath());
            this.workflowDirectory = workflowDirectory;
            this.workflow = workflow;
            this.logger = new Logger(new File(workflowDirectory, LOG_FILE).getPath(), 
                    "workflow", loglevel);
            
            if (instanceId == null) {
                instanceId = newWorkflowInstance();
            }
            this.instanceId = instanceId;
            
            // task configuration
            this.taskConfig = Dao.get(Config.class, 
                    new File(instanceDir, TASK_CONFIG_FILE).getPath());
            // task statuses
            this.taskStatus = Dao.get(Task.class, 
                    new File(instanceDir, TASK_STATUS_FILE).getPath());
            // task statuses
            this.taskDispatch = Dao.get(TaskDispatch.class, 
                    new File(instanceDir, TASK_DISPATCH_FILE).getPath());
            
        }
        catch (IOException e) {throw new RuntimeException(e);}
        catch (SQLException e) {throw new RuntimeException(e);}
    }
    
    /**
     * Initialize the workflow instance's subdirectories and tasks.txt files.
     * @return the assigned instance_id for the new workflow instance.
     */
    private int newWorkflowInstance() {
        // assign a new instance_id
        int instance_id = 1;
        for (Task task : workflowStatus.select()) {
            if (instance_id <= task.getId()) {
                instance_id  = task.getId() + 1;
            }
        }
        // create a new directory for the workflow instance
        File instanceDir = new File(workflowDirectory, 
                "WF"+String.format("%05d", instance_id));
        if (!instanceDir.mkdirs()) {
            throw new RuntimeException("Could not create directory "
                    +instanceDir.getPath());
        }
        // make a task record that points to the workflow instance.
        workflowStatus.insert(new Task(instance_id, null, instanceDir.getName(), Status.NEW));
        this.instanceDir = instanceDir;
        return instance_id;
    }
    
    public Future<Status> run(
            final Task start,
            final boolean resume,
            final boolean dataAcquisitionMode) 
    {
        Future<Status> future = pool.submit(new Callable<Status>() {
            @Override
            public Status call() {
                List<Future<Status>> futures = runSuccessors(
                        start, instanceDir, resume, dataAcquisitionMode);        
                
                // resolve all the futures into statuses
                List<Status> statuses = new ArrayList<Status>();
                for (Future<Status> f : futures) {
                    try {
                        Status s = f.get();
                        statuses.add(s);
                    } 
                    catch (ExecutionException e) {throw new RuntimeException(e);}
                    catch (InterruptedException e) {throw new RuntimeException(e);}
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
    private List<Future<Status>> runSuccessors(
            final Task task, 
            final File taskDir,
            final boolean resume, 
            final boolean dataAcquisitionMode) 
    {
        try {
            // Get the list of successors
            List<WorkflowModule> successors = this.workflow.select(
                    where("parentId", task.getModuleId()));
            
            // create the task storage location
            if (!taskDir.exists()) {
                taskDir.mkdirs();
                if (!taskDir.exists()) {
                    throw new RuntimeException(
                            "Could not create directory "+taskDir.getPath());
                }
            }
            
            // run successors 
            List<Future<Status>> futures = new ArrayList<Future<Status>>();
            for (WorkflowModule m : successors) {
                List<TaskDispatch> successorTaskIds = taskDispatch.select(
                        where("parentTaskId",task.getId()));
                
                for (TaskDispatch successorTaskId : successorTaskIds) {
                    final Task successorTask = taskStatus.selectOne(
                            where("id",successorTaskId.getTaskId()));
                    
                    // merge the task and module configuration
                    List<Config> configs = new ArrayList<Config>();
                    configs.addAll(taskConfig.select(where("id",successorTask.getId())));
                    configs.addAll(moduleConfig.select(where("id",successorTask.getModuleId())));
                    final Map<String,Config> config = Config.merge(configs);
                    
                    // make sure all required fields are filled in
                    for (Map.Entry<String,Config> c : config.entrySet()) {
                        if (c.getValue().isRequired() && c.getValue().getValue() == null) {
                            throw new RuntimeException("Required value "+
                                    c.getKey()+" missing for task "+successorTask.getModuleId());
                        }
                    }
                        
                    final Module successor = m.getModule().newInstance();
                    final WorkflowRunner workflowRunner = this;
                    
                    Callable<Status> callable = new Callable<Status>() {
                        @Override
                        public Status call() {
                            Status status = successorTask.getStatus();
                            File successorTaskDir = new File(
                                    taskDir, 
                                    successorTask.getStorageLocation());
                            
                            if ((!resume 
                                    || status == Status.NEW 
                                    || status == Status.DEFER)
                                  && !(!dataAcquisitionMode 
                                          && successor.requiresDataAcquisitionMode())) 
                            {
                                // set the task status to IN_PROGRESS
                                successorTask.setStatus(Status.IN_PROGRESS);
                                taskStatus.update(successorTask, "id","moduleId");
                                
                                Logger successorLogger = new Logger(
                                        new File(instanceDir, LOG_FILE).getPath(),
                                        successorTask.getModuleId(),
                                        logLevel);
                                
                                // run the successor task
                                if (sem != null) {
                                    sem.acquireUninterruptibly();
                                }
                                try {
                                    status = successor.run(workflowRunner, successorTask, config, successorLogger);
                                } 
                                // Uncaught exceptions set the status to ERROR
                                catch (Exception e) {
                                    successorLogger.severe("Error reported during task "
                                            +successorTask.toString()+": \n"+e.toString());
                                    status = Status.ERROR;
                                }
                                finally {
                                    if (sem != null) {
                                        sem.release();
                                    }
                                }
                            }
                            
                            // enqueue the successors of the successor
                            if (status == Status.SUCCESS) {
                                List<Future<Status>> childFutures = runSuccessors(
                                        successorTask, 
                                        successorTaskDir,
                                        resume, dataAcquisitionMode);
                                
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
                                return coalesceStatuses(statuses);
                            }
                            
                            // update the task status
                            successorTask.setStatus(status);
                            taskStatus.update(successorTask, "id","moduleId");
                            return status;
                        }
                    };
                    // Serial tasks get run immediately
                    if (m.getTaskType() == TaskType.SERIAL) {
                        FutureTask<Status> future = new FutureTask<Status>(callable);
                        future.run();
                        futures.add(future);
                    }
                    // Parallel tasks get put in the task pool
                    else if (m.getTaskType() == TaskType.PARALLEL) {
                        Future<Status> future = pool.submit(callable);
                        futures.add(future);
                    }
                    else {
                        throw new RuntimeException("Unknown task type: "+m.getTaskType());
                    }
                }
            }
            return futures;
        } 
        catch (InstantiationException e) {throw new RuntimeException(e);}
        catch (IllegalAccessException e) {throw new RuntimeException(e);}
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
     * Get a list of the instance ids from the workflow status file inside
     * a workflow directory.
     * @param workflowDirectory
     * @return
     */
    public static List<Integer> getInstanceIds(String workflowDirectory) {
        List<Integer> instanceIds = new ArrayList<Integer>();
        File workflowPath = new File(workflowDirectory);
        if (workflowPath.exists()) {
            File statusFile = new File(workflowDirectory, WORKFLOW_STATUS_FILE);
            if (statusFile.exists()) {
                Dao<Task> status = Dao.get(Task.class, statusFile.getPath());
                for (Task task : status.select()) {
                    instanceIds.add(task.getId());
                }
            }
        }
        Collections.sort(instanceIds);
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
    public Dao<Task> getWorkflowStatus() { return workflowStatus; }
    public Logger getLogger() { return logger; }
    public Level getLogLevel() { return logLevel; }
    public void setLogLevel(Level logLevel) { this.logLevel = logLevel; }
    public int getMaxThreads() { return maxThreads; }
    public int getInstanceId() { return instanceId; }
    public Dao<Task> getTaskStatus() { return taskStatus; }
    public Dao<TaskDispatch> getTaskDispatch() { return taskDispatch; }
    public Dao<Config> getTaskConfig() { return taskConfig; }
}
