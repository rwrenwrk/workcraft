package org.workcraft.plugins.verification.gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import info.clearthought.layout.TableLayout;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import org.workcraft.Trace;
import org.workcraft.gui.ToolboxWindow;
import org.workcraft.plugins.petri.SimulationTool;
import org.workcraft.plugins.shared.tasks.MpsatChainTask;


@SuppressWarnings("serial")
public class SolutionPanel extends JPanel {
	private JPanel buttonsPanel;
	private JTextArea traceText;

	public SolutionPanel(final MpsatChainTask task, final Trace t) {
		super (new TableLayout(new double[][]
		        { { TableLayout.FILL, TableLayout.PREFERRED },
				{TableLayout.FILL} }
		));

		traceText = new JTextArea();
		traceText.setText(t.toString());

		final JScrollPane scrollPane = new JScrollPane();
		scrollPane.setViewportView(traceText);

		buttonsPanel = new JPanel();
		buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.Y_AXIS));

		JButton saveButton = new JButton("Save");

		JButton playButton = new JButton("Play trace");
		playButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e) {
				task.getFramework().getMainWindow().createEditorWindow(task.getWorkspaceEntry());
				final ToolboxWindow toolbox = task.getFramework().getMainWindow().getToolboxWindow();
				final SimulationTool tool = toolbox.getToolInstance(SimulationTool.class);
				tool.setTrace(t);
				toolbox.selectTool(tool);
			}
		});

		buttonsPanel.add(saveButton);
		buttonsPanel.add(playButton);


		add(scrollPane, "0 0");
		add(buttonsPanel, "1 0");
	}
}
