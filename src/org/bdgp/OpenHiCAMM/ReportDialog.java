package org.bdgp.OpenHiCAMM;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import javax.swing.JFrame;

import org.bdgp.OpenHiCAMM.DB.ModuleConfig;
import org.bdgp.OpenHiCAMM.DB.WorkflowModule;
import org.bdgp.OpenHiCAMM.Modules.Interfaces.Report;

import ij.IJ;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.event.ActionEvent;
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
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

import static org.bdgp.OpenHiCAMM.Util.where;

@SuppressWarnings("serial")
public class ReportDialog {
    private WorkflowRunner workflowRunner;

    @FXML private WebView webView;
    @FXML private ChoiceBox<String> selectReport;
    @FXML private Button selectButton;
    @FXML private TextField evaluateJs;
    
    public static final boolean DEBUG=true;

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
            try {
                Report report = (Report)Class.forName(reportName).newInstance();
                report.initialize(this.workflowRunner);
                runReport(report);
            } 
            catch (InstantiationException|IllegalAccessException|ClassNotFoundException e) {throw new RuntimeException(e);}
        }
    }


    @FXML void evaluateJs(ActionEvent event) {
        String text = evaluateJs.getText();

        try {
            log("JS result: %s", webView.getEngine().executeScript(text).toString());
        } 
        catch (Throwable e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            log("Caught exception while running workflow: %s", sw.toString());
        }
    }

    public void runReport(Report report) {
        WebEngine webEngine = webView.getEngine();
        JSObject jsobj = (JSObject) webEngine.executeScript("window");
        jsobj.setMember("report", report);
        jsobj.setMember("workflowReport", report);
        
        try{
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
            File reportDir = new File(new File(new File(
                    this.workflowRunner.getWorkflowDir(), 
                    this.workflowRunner.getWorkflowInstance().getStorageLocation()),
                    "reports"),
                    reportName);
            reportDir.mkdirs();
            File reportIndex = new File(reportDir, "index.html");

            if (reportIndex.exists()) {
                String html = new String(Files.readAllBytes(Paths.get(reportIndex.getPath())));
                Platform.runLater(()->{
                    webEngine.loadContent(html);
                });
            }

            // if the report file's timestamp is older than the workflow run time,
            // it needs to be regenerated
            if (workflowRunTime == null || !reportIndex.exists() || reportIndex.lastModified() <= workflowRunTime) {
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
                        report.runReport(reportDir.getPath(), reportIndex.getName());
                        if (reportIndex.exists()) {
                            try {
                                String html = new String(Files.readAllBytes(Paths.get(reportIndex.getPath())));
                                Platform.runLater(()->{
                                    webEngine.loadContent(html);
                                });
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
                // log JavaFX exceptions
                Thread.currentThread().setUncaughtExceptionHandler((thread, throwable) -> {
                    StringWriter sw = new StringWriter();
                    throwable.printStackTrace(new PrintWriter(sw));
                    log("Caught exception while running workflow: %s", sw.toString());
                });

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
