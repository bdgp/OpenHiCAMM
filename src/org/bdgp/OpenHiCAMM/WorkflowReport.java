package org.bdgp.OpenHiCAMM;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.PrintWriter;

import javax.swing.JFrame;

import j2html.tags.Tag;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import static j2html.TagCreator.*;

@SuppressWarnings("serial")
public class WorkflowReport extends JFrame {
	WorkflowRunner workflowRunner;
	public static final boolean DEBUG_MODE = true;

	public WorkflowReport(WorkflowRunner workflowRunner) {
		this.workflowRunner = workflowRunner;

		final JFXPanel fxPanel = new JFXPanel();
        this.add(fxPanel);
        this.setSize(1024, 768);
        this.setVisible(true);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        Platform.runLater(new Runnable() {
            @Override public void run() {
                initFX(fxPanel);
            }
       });
	}
	
	public void initFX(JFXPanel fxPanel) {
        Scene scene = new Scene(new Group());
        VBox root = new VBox();     
        final WebView browser = new WebView();
        final WebEngine webEngine = browser.getEngine();

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setContent(browser);
        String html = runReport();
        webEngine.loadContent(html);

        root.getChildren().addAll(scrollPane);
        scene.setRoot(root);
        fxPanel.setScene(scene);
	}
	
	public String runReport() {
		Tag html = html().with(
			body().with(
					
            )
        );
		
		
		if (DEBUG_MODE) {
			try {
				PrintWriter pw = new PrintWriter("workflow_report.html");
				pw.println(html.render());
				pw.close();
			} 
			catch (FileNotFoundException e) {throw new RuntimeException(e);}
		}
		return html.render();
	}
}
