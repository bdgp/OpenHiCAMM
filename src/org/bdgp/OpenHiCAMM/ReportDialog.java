package org.bdgp.OpenHiCAMM;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.swing.JFrame;

import org.bdgp.OpenHiCAMM.DB.ModuleConfig;
import org.bdgp.OpenHiCAMM.DB.WorkflowModule;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Report;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;

import ij.IJ;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextField;

import static org.bdgp.OpenHiCAMM.Util.where;

@SuppressWarnings("serial")
public class ReportDialog {
    private WorkflowRunner workflowRunner;

    @FXML private ChoiceBox<String> selectReport;
    @FXML private Button selectButton;
    @FXML private Button debugButton;
    @FXML private TextField evaluateJs;
    
    public static final boolean DEBUG=true;
    public final int REPORT_PORT=8080;
    public static HttpServer server = null;

    public ReportDialog() { }

    public void initialize(WorkflowRunner workflowRunner) {
        this.workflowRunner = workflowRunner;

        selectReport.getItems().clear();
        selectReport.getItems().add("- Select a report -");
        List<String> reportNames = OpenHiCAMM.getReportNames();
        for (String reportName : reportNames) {
            selectReport.getItems().add(reportName);
        }
        selectReport.getSelectionModel().select(0);
    }

    public static void log(String message, Object... args) {
        if (DEBUG) {
            IJ.log(String.format("[ReportDialog] %s", String.format(message, args)));
        }
    }
    
    @FXML void selectButtonPressed(ActionEvent event) {
        String reportName = selectReport.getSelectionModel().getSelectedItem();
        if (reportName != null && !reportName.equals("- Select a report -")) {
            Report report;
            try { report = (Report)Class.forName(reportName).newInstance(); } 
            catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) { 
                throw new RuntimeException(e); 
            }
            runReport(report);
        }
    }

    void openPage(String reportName) {
        ProcessBuilder builder = new ProcessBuilder(
            System.getProperty("os.name").toLowerCase().matches(".*win.*")?
                "start" :
            System.getProperty("os.name").toLowerCase().matches(".*mac.*")?
                "open" : 
                "xdg-open",
            String.format("file://%s", reportName));
        try { 
            Process process = builder.start(); 
            try { int result = process.waitFor(); 
                if (result != 0) throw new RuntimeException(
                        String.format("Got nonzero result from process {}: {}", process, result));
            } 
            catch (InterruptedException e) { throw new RuntimeException(e); }
        } 
        catch (IOException e) {throw new RuntimeException(e);}
    }

    public void runReport(Report report) {
        try {
            // get the time the workflow completed running
            Long workflowRunTime = null;
            for (WorkflowModule startModule : this.workflowRunner.getWorkflow().select(where("parentId", null))) {
                ModuleConfig startTimeConf = this.workflowRunner.getModuleConfig().selectOne(
                        where("id",startModule.getId()).
                        and("key", "startTime"));
                if (startTimeConf != null) {
                        long startTime = WorkflowRunner.dateFormat.parse(startTimeConf.getValue()).getTime();
                        if (workflowRunTime == null || workflowRunTime < startTime) workflowRunTime = startTime;
                }
                ModuleConfig endTimeConf = this.workflowRunner.getModuleConfig().selectOne(
                        where("id",startModule.getId()).
                        and("key", "endTime"));
                if (endTimeConf != null) {
                    long endTime = WorkflowRunner.dateFormat.parse(endTimeConf.getValue()).getTime();
                    if (workflowRunTime == null || workflowRunTime < endTime) workflowRunTime = endTime;
                }
            }

            // get the report file path
            String reportName = report.getClass().getSimpleName();
            File reportDir = new File(new File(
                    this.workflowRunner.getWorkflowDir(), 
                    "reports"),
                    reportName);
            reportDir.mkdirs();
            String reportIndex = "index.html";
            
            report.initialize(this.workflowRunner, reportDir.getPath(), reportIndex);
            
            File reportIndexFile = new File(reportDir, reportIndex);
            if (reportIndexFile.exists()) {
                startWebServer(report);
                openPage(report.getClass().getSimpleName());
            }

            // if the report file's timestamp is older than the workflow run time,
            // it needs to be regenerated
            if (workflowRunTime == null || !reportIndexFile.exists() || reportIndexFile.lastModified() <= workflowRunTime) {
                Alert alert = new Alert(AlertType.CONFIRMATION);
                alert.setTitle(String.format("Report %s", reportName));
                alert.setHeaderText(String.format("Report %s is outdated.", reportName));
                alert.setContentText("Regenerate the report? This could take some time.");
                alert.getButtonTypes().clear();
                alert.getButtonTypes().addAll(ButtonType.YES, ButtonType.NO);
                Optional<ButtonType> result = alert.showAndWait();

                if (result.get() == ButtonType.YES) {
                    new Thread(()->{
                        log("Generating report for %s", reportName);
                        report.runReport();
                        if (reportIndexFile.exists()) {
                            try {
                                startWebServer(report);
                                openPage(report.getClass().getSimpleName());
                            } 
                            catch (Exception e) {throw new RuntimeException(e);}
                        }
                        else {
                            throw new RuntimeException(String.format("Report index file %s was not created!", reportIndex));
                        }
                    }).start();
                }
            }
        } 
        catch (Throwable e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            log("Caught exception while running workflow: %s", sw.toString());
            throw new RuntimeException(e);
        }
    }
    
    synchronized public void startWebServer(Report report) {
        // start HTTP server
        String reportName = report.getClass().getSimpleName();
        if (server != null) {
            server.stop(0);
        }
        try { server = HttpServer.create(new InetSocketAddress(REPORT_PORT), 0); } 
        catch (IOException e) { throw new RuntimeException(e); }
        server.createContext(String.format("/%s/", reportName), (t)->{
            // get command
            String command = new File(t.getRequestURI().getPath()).getName().toString();
            // decode query params
            Map<String,List<String>> query = new LinkedHashMap<>();
            for (String q : t.getRequestURI().getQuery().split("&")) {
                String[] kv = q.split("=", 2);
                String key = URLDecoder.decode(kv[0], "UTF-8");
                String value = kv.length > 1? URLDecoder.decode(kv[1], "UTF-8") : "";
                if (!query.containsKey(key)) query.put(key, new ArrayList<>());
                query.get(key).add(value);
            }
            Gson gson = new Gson();
            if (query.containsKey("args")) {
                try {
                    // decode JSON from args parameter
                    Object[] args = gson.fromJson(query.get("args").get(0), Object[].class);
                    List<Class<?>> classes = new ArrayList<>();
                    for (Object arg : args) {
                        classes.add(arg.getClass());
                    }
                    // invoke the method on the report
                    Method method = report.getClass().getDeclaredMethod(command, classes.toArray(new Class<?>[]{}));
                    Object obj = method.invoke(report, args);
                    String result = gson.toJson(obj);
                    t.sendResponseHeaders(200, result.getBytes().length);
                    OutputStream os = t.getResponseBody();
                    os.write(result.getBytes());
                    os.close();
                }
                catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }
            else {
                // invoke the method on the report
                try {
                    Method method = report.getClass().getDeclaredMethod(command);
                    Object obj = method.invoke(report);
                    String result = gson.toJson(obj);
                    t.sendResponseHeaders(200, result.getBytes().length);
                    OutputStream os = t.getResponseBody();
                    os.write(result.getBytes());
                    os.close();
                } 
                catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) { 
                    throw new RuntimeException(e); 
                }
            }
            
        });
        server.setExecutor(null); // creates a default executor
        server.start();
    }
    
    public static class Frame extends JFrame {
        FXMLLoader loader;
        ReportDialog controller;
        
        public Frame(WorkflowRunner workflowRunner) {
            JFXPanel fxPanel = new JFXPanel();
            this.add(fxPanel);
            this.setSize(1280, 1024);
            this.setVisible(true);
            this.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

            Platform.runLater(()->{
                try {
                    loader = new FXMLLoader(getClass().getResource("/ReportDialog.fxml"));
                    Parent root = loader.load();
                    controller = loader.<ReportDialog>getController();
                    controller.initialize(workflowRunner);
                    Scene scene = new Scene(root);
                    fxPanel.setScene(scene);
                } 
                catch (Throwable e) {
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    log("Caught exception while running workflow: %s", sw.toString());
                    throw new RuntimeException(e);
                }
            });
        }
        
        public void setWorkflowRunner(WorkflowRunner workflowRunner) {
            Platform.runLater(()->{
                controller.initialize(workflowRunner);
            });
        }
    }
}
