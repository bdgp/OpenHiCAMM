package org.bdgp.OpenHiCAMM;

import javax.swing.JDialog;
import net.miginfocom.swing.MigLayout;
import javax.swing.JLabel;
import javax.swing.Timer;
import javax.swing.JButton;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

@SuppressWarnings("serial")
public class CountdownDialog extends JDialog {
	int countdownTimer;

	CountdownDialog(String labelText, int countdownTimer, Runnable onComplete) {
		this.countdownTimer = countdownTimer;
		setType(Type.POPUP);
		setUndecorated(true);
		setModalityType(ModalityType.APPLICATION_MODAL);
		setModalExclusionType(ModalExclusionType.APPLICATION_EXCLUDE);
		setModal(true);
		setTitle("Re-starting process");
		getContentPane().setLayout(new MigLayout("", "[]", "[][]"));
		
		JLabel lblNewLabel = new JLabel(String.format(labelText, this.countdownTimer));
		getContentPane().add(lblNewLabel, "cell 0 0");
		
        Timer timer = new Timer(1000, new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				CountdownDialog.this.countdownTimer--;
				if (countdownTimer < 0) {
                    CountdownDialog.this.setVisible(false);
                    CountdownDialog.this.dispose();
                    onComplete.run();
				}
				else {
					lblNewLabel.setText(String.format(labelText, CountdownDialog.this.countdownTimer));
				}
			}
        });
        timer.setRepeats(true);
        timer.start();

		JButton btnNewButton = new JButton("Cancel");
		btnNewButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				timer.stop();
				CountdownDialog.this.setVisible(false);
				CountdownDialog.this.dispose();
			}
		});
		getContentPane().add(btnNewButton, "cell 0 1");

        this.setVisible(true); // if modal, application will pause here
	}

}
