package org.bdgp.MMSlide;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.bdgp.MMSlide.Dao;
import org.bdgp.MMSlide.Logger.Level;
import org.bdgp.MMSlide.Task.Status;
import org.bdgp.MMSlide.Modules.Start;
import org.bdgp.MMSlide.Modules.Interfaces.Module;
import org.bdgp.MMSlide.Modules.Interfaces.Root;

import static org.bdgp.MMSlide.ChainHashMap.where;
import static org.bdgp.MMSlide.Dao.one;

public class WorkflowRunner {
    /**
     * Default file names for the metadata files.
     */
    public static final String WORKFLOW_FILE = "workflow.txt";
    public static final String MODULE_CONFIG_FILE = "moduleconfig.txt";
    public static final String TASK_CONFIG_FILE = "taskconfig.txt";
    public static final String TASK_STATUS_FILE = "tasks.txt";
    public static final String LOG_FILE = "log.txt";
    
    /**
     * Keep a registry of all known workflow modules. 
     * It is the responsibility of each workflow module to register with the 
     * WorkflowRunner as part of its static initialization.
     */
    private static Set<Class<?>> modules = new HashSet<Class<?>>();
    public static void addModule(Class<?> module) {
       if (!Module.class.isAssignableFrom(module)) {
           throw new RuntimeException("Class "+module.getName()+
                   " does not inherit from module!");
       }
       modules.add(module); 
    }
    public static void removeModule(Class<?> module) {
       modules.remove(module); 
    }
    public static Set<Class<?>> getModules() {
        return modules;
    }
    
    private File workflowDirectory;
    private Dao<WorkflowModule> workflow;
    private Dao<Config> moduleConfig;
    
    @SuppressWarnings("unused")
    private Logger logger;
    private Level loglevel;
    
    // The list of currently running threads
    private ExecutorService pool;
    
    public WorkflowRunner(String workflowDirectory) {
        this(workflowDirectory, Integer.MAX_VALUE, Level.INFO);
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
            Dao<WorkflowModule> workflow;
            workflow = Dao.get(WorkflowModule.class, workflowFile.getPath());
            
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
    
    public void run(int instance_id) {
        run(instance_id, true);
    }
    public void run(int instance_id, boolean resume) {
        Task start = new Task(instance_id, null, 
                this.workflowDirectory.getPath(), Status.NEW);
        runSuccessors(start, resume, instance_id);
    }
    public void runSuccessors(Task task, boolean resume) {
        runSuccessors(task, resume, null);
    }
    
    /**
     * Given a task, call all of its successors.
     * @param task
     * @param resume
     */
    @SuppressWarnings("unchecked")
    public void runSuccessors(Task task, final boolean resume, Integer instance_id) {
        try {
            @SuppressWarnings("rawtypes")
            final Module module = task.getModuleId() == null
                ? new Start()
                : one(this.workflow.queryForFieldValuesArgs(
                    where("id",task.getModuleId()))).getModule().newInstance();
            
            List<WorkflowModule> successors = this.workflow.queryForFieldValuesArgs(
                    where("parentId", task.getModuleId()));
            
            final Dao<Task> tasks = Dao.get(Task.class,
                    new File(task.getStorageLocation(), TASK_STATUS_FILE).getPath());
            
            for (WorkflowModule wf : successors) {
                // if instance_id was specified, use it to filter out the tasks.
                List<Task> successorTasks = instance_id == null
                    ? tasks.queryForFieldValuesArgs(
                        where("moduleId",wf.getId()))
                    : tasks.queryForFieldValuesArgs(
                            where("moduleId",wf.getId()).
                            and("id",instance_id));
                
                for (final Task successorTask : successorTasks) {
                    // get the task configuration
                    Dao<Config> taskConfig = Dao.get(Config.class, 
                            new File(successorTask.getStorageLocation(), TASK_CONFIG_FILE).getPath());
                        
                    // merge the task and module configuration
                    final Map<String,Config> config = Config.getMap(
                            taskConfig.queryForAll(), 
                            moduleConfig.queryForFieldValuesArgs(
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
                                  && !(Main.isCommandLineMode() 
                                          && !successor.canRunInCommandLineMode())) 
                            {
                                // set the task status to IN_PROGRESS
                                successorTask.setStatus(Status.IN_PROGRESS);
                                tasks.update(successorTask, "id","moduleId");
                                
                                Logger successorLogger = new Logger(
                                        new File(successorTask.getStorageLocation(), LOG_FILE).getPath(),
                                        successorTask.getModuleId(),
                                        loglevel);
                                // run the successor task
                                status = module.callSuccessor(successor, config, successorLogger);
                                
                                // update the task status
                                successorTask.setStatus(status);
                                tasks.update(successorTask, "id","moduleId");
                            }
                            // enqueue the successors of the successor
                            if (status == Status.SUCCESS) {
                                runSuccessors(successorTask, resume);
                            }
                            return status;
                        }});
                }
            }
        } 
        catch (SQLException e) {throw new RuntimeException(e);} 
        catch (InstantiationException e) {throw new RuntimeException(e);}
        catch (IllegalAccessException e) {throw new RuntimeException(e);}
    }


}
