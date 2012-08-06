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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bdgp.MMSlide.Dao.Config;
import org.bdgp.MMSlide.Dao.Dao;
import org.bdgp.MMSlide.Dao.Task;
import org.bdgp.MMSlide.Dao.WorkflowModule;
import org.bdgp.MMSlide.Dao.Task.Status;
import org.bdgp.MMSlide.Logger.Level;
import org.bdgp.MMSlide.Modules.Start;
import org.bdgp.MMSlide.Modules.Interfaces.Module;
import org.bdgp.MMSlide.Modules.Interfaces.Root;

import static org.bdgp.MMSlide.ChainMap.where;

public class WorkflowRunner {
    /**
     * Default file names for the metadata files.
     */
    public static final String WORKFLOW_FILE = "workflow.txt";
    public static final String MODULE_CONFIG_FILE = "moduleconfig.txt";
    public static final String TASK_CONFIG_FILE = "taskconfig.txt";
    public static final String TASK_STATUS_FILE = "tasks.txt";
    public static final String LOG_FILE = "log.txt";
    public static final String MODULE_LIST = "META-INF/modules.txt";
    
    private File workflowDirectory;
    private Dao<WorkflowModule> workflow;
    private Dao<Config> moduleConfig;
    
    @SuppressWarnings("unused")
    private Logger logger;
    private Level loglevel;
    
    // The list of currently running threads
    private ExecutorService pool;
    
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
    
    /**
     * Constructor for WorkflowRunner. This loads the workflow.txt file 
     * in the workflow directory and performs some consistency checks 
     * on the workflow described in the file.
     * @param workflowDirectory
     */
    public WorkflowRunner(String workflowDirectory, int max_threads, Level loglevel) {
        try {
            // Get the workflow directory
            File dir = new File(workflowDirectory); 
            if (!dir.exists() || !dir.isDirectory()) {
                throw new IOException("Directory "+workflowDirectory+" is not a valid directory.");
            }
            // Get the workflow file
            File workflowFile = new File(dir, WORKFLOW_FILE);
            if (!workflowFile.exists()) {
                throw new IOException("Workflow file "+workflowFile.getPath()
                        +" was not found in "+workflowDirectory);
            }
            
            // Load the workflow file
            Dao<WorkflowModule> workflow = Dao.get(WorkflowModule.class, workflowFile.getPath());
            
            // Check for consistency
            for (WorkflowModule w : workflow.queryForAll()) {
                // Make sure parent IDs are defined and that all successors are compatible.
                if (w.getParentId() != null) {
                    List<WorkflowModule> parent = workflow.queryForEq("id", w.getParentId());
                    if (parent.size() == 0) {
                        throw new SQLException("Workflow "+workflowFile.getPath()
                                +" references unknown parent ID "+w.getParentId());
                    }
                    for (WorkflowModule p : parent) {
                        if (!p.getModule().newInstance().getSuccessorInterface().isAssignableFrom(w.getModule())) 
                        {
                            throw new SQLException("Module "+w.getModuleName()
                                    +" is an incompatible successor to "+p.getModuleName());
                        }
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
                if (!Root.class.isAssignableFrom(f.getModule())) {
                    throw new SQLException("First module "+f.getModuleName()
                            +" in Workflow "+workflowFile.getPath()
                            +" does not inherit the Root interface.");
                }
            }
            
            this.pool = Executors.newFixedThreadPool(max_threads);
            this.moduleConfig = Dao.get(Config.class, 
                new File(workflowDirectory, MODULE_CONFIG_FILE).getPath());
            this.workflowDirectory = dir;
            this.workflow = workflow;
            this.logger = new Logger(new File(workflowDirectory, LOG_FILE).getPath(), 
                    "workflow", loglevel);
        }
        catch (IOException e) {throw new RuntimeException(e);}
        catch (SQLException e) {throw new RuntimeException(e);}
        catch (InstantiationException e) {throw new RuntimeException(e);}
        catch (IllegalAccessException e) {throw new RuntimeException(e);} 
    }
    
    /**
     * Initialize the workflow instance's subdirectories and tasks.txt files.
     * @return the assigned instance_id for the new workflow instance.
     */
    public int newWorkflowInstance() {
        Dao<Task> tasks = Dao.get(Task.class,
                new File(workflowDirectory, TASK_STATUS_FILE).getPath());
        // assign a new instance_id
        int instance_id = 1;
        for (Task task : tasks.select()) {
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
        tasks.insert(new Task(instance_id, null, instanceDir.getName(), Status.NEW));
        return instance_id;
    }
    
    public void run(
            int instance_id, 
            boolean resume,
            boolean dataAcquisitionMode) 
    {
        Task start = new Task(instance_id, null, 
                this.workflowDirectory.getPath(), Status.NEW);
        runSuccessors(start, resume, dataAcquisitionMode, instance_id);
    }
    public void runSuccessors(
            Task task, 
            boolean resume, 
            boolean dataAcquisitionMode) 
    {
        runSuccessors(task, resume, dataAcquisitionMode, null);
    }
    
    /**
     * Initialize the successors' module directories and populate their 
     * task statuses if necessary, and return the task status Dao object.
     * @param directory The parent workflow directory
     * @param successors The list of successor workflow modules.
     * @return The dao object for the task statuses
     */
    synchronized private Dao<Task> getTasks(File directory, List<WorkflowModule> successors) 
    {
        if (!directory.exists()) {
            directory.mkdirs();
            if (!directory.exists()) {
                throw new RuntimeException(
                        "Could not create directory "+directory.getPath());
            }
        }
        File taskFile = new File(directory, TASK_STATUS_FILE);
        boolean taskFileExists = taskFile.exists();
        Dao<Task> tasks = Dao.get(Task.class, taskFile.getPath());
        
        // If the task file didn't exist, we need to populate it with the 
        // default task status information.
        if (!taskFileExists) {
            for (WorkflowModule module : successors) {
                for (int i=1; i<=module.getInstanceCount(); ++i) {
                    File subdir = new File(directory, 
                            module.getId()+"."+String.format("%03i",i));
                    subdir.mkdirs();
                    if (!subdir.exists()) {
                        throw new RuntimeException("Could not create directory "
                                +subdir.getPath());
                    }
                    tasks.insert(new Task(i, module.getId(), subdir.getName(), Status.NEW));       
                }
            }
        }
        return tasks;
    }
    
    /**
     * Given a task, call all of its successors.
     * @param task
     * @param resume
     */
    @SuppressWarnings("unchecked")
    public void runSuccessors(
            Task task, 
            final boolean resume, 
            final boolean dataAcquisitionMode,
            Integer instance_id) 
    {
        try {
            @SuppressWarnings("rawtypes")
            final Module module = task.getModuleId() == null
                ? new Start()
                : this.workflow.selectOne(
                    where("id",task.getModuleId())).getModule().newInstance();
            
            List<WorkflowModule> successors = this.workflow.select(
                    where("parentId", task.getModuleId()));
            
            // Init the workflow directory and the status file, then 
            // return the dao of the status file.
            final Dao<Task> tasks = getTasks(
                    new File(task.getStorageLocation()),
                    successors);
                
            for (WorkflowModule wf : successors) {
                // if instance_id was specified, use it to filter out the tasks.
                List<Task> successorTasks = instance_id == null
                    ? tasks.select(
                        where("moduleId",wf.getId()))
                    : tasks.select(
                            where("moduleId",wf.getId()).
                            and("id",instance_id));
                
                for (final Task successorTask : successorTasks) {
                    // get the task configuration
                    Dao<Config> taskConfig = Dao.get(Config.class, 
                            new File(successorTask.getStorageLocation(),
                                    TASK_CONFIG_FILE).getPath());
                        
                    // merge the task and module configuration
                    final Map<String,Config> config = Config.getMap(
                            taskConfig.select(), 
                            moduleConfig.select(
                                    where("moduleId",successorTask.getModuleId())));
                    // make sure all required fields are filled in
                    for (Map.Entry<String,Config> c : config.entrySet()) {
                        if (c.getValue().isRequired() && c.getValue().getValue() == null) {
                            throw new RuntimeException("Required value "+
                                    c.getKey()+" missing for task "+successorTask.getModuleId());
                        }
                    }
                        
                    @SuppressWarnings("rawtypes")
                    final Module successor = wf.getModule().newInstance();
                    
                    this.pool.submit(new Callable<Status>() {
                        @Override
                        public Status call() throws Exception {
                            Status status = successorTask.getStatus();
                            if ((!resume 
                                    || status == Status.NEW 
                                    || status == Status.DEFER)
                                  && !(!dataAcquisitionMode 
                                          && successor.requiresDataAcquisitionMode())) 
                            {
                                // set the task status to IN_PROGRESS
                                successorTask.setStatus(Status.IN_PROGRESS);
                                tasks.update(successorTask, "id","moduleId");
                                
                                Logger successorLogger = new Logger(
                                        new File(successorTask.getStorageLocation(), LOG_FILE).getPath(),
                                        successorTask.getModuleId(),
                                        loglevel);
                                // run the successor task
                                status = module.callSuccessor(
                                        successor, successorTask.getId(), 
                                        config, successorLogger);
                                
                                // update the task status
                                successorTask.setStatus(status);
                                tasks.update(successorTask, "id","moduleId");
                            }
                            // enqueue the successors of the successor
                            if (status == Status.SUCCESS) {
                                runSuccessors(successorTask, resume, dataAcquisitionMode);
                            }
                            return status;
                        }});
                }
            }
        } 
        catch (InstantiationException e) {throw new RuntimeException(e);}
        catch (IllegalAccessException e) {throw new RuntimeException(e);}
    }

    /**
     * Get a list of the instance ids from the task status file inside
     * a workflow directory.
     * @param workflowDirectory
     * @return
     */
    public static List<Integer> getInstanceIds(String workflowDirectory) {
        List<Integer> instanceIds = new ArrayList<Integer>();
        File workflowPath = new File(workflowDirectory);
        if (workflowPath.exists()) {
            File statusFile = new File(workflowDirectory, TASK_STATUS_FILE);
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
}
