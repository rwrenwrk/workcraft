package org.workcraft.plugins.son.tools;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;

import org.workcraft.dom.Node;
import org.workcraft.dom.visual.HitMan;
import org.workcraft.gui.events.GraphEditorMouseEvent;
import org.workcraft.gui.graph.tools.AbstractTool;
import org.workcraft.gui.graph.tools.Decoration;
import org.workcraft.gui.graph.tools.Decorator;
import org.workcraft.gui.graph.tools.GraphEditor;
import org.workcraft.gui.layouts.WrapLayout;
import org.workcraft.plugins.son.Interval;
import org.workcraft.plugins.son.SON;
import org.workcraft.plugins.son.SONSettings;
import org.workcraft.plugins.son.VisualSON;
import org.workcraft.plugins.son.algorithm.SimulationAlg;
import org.workcraft.plugins.son.connections.VisualSONConnection;
import org.workcraft.plugins.son.connections.SONConnection.Semantics;
import org.workcraft.plugins.son.elements.Condition;
import org.workcraft.plugins.son.elements.PlaceNode;
import org.workcraft.plugins.son.elements.VisualBlock;
import org.workcraft.plugins.son.elements.VisualCondition;
import org.workcraft.plugins.son.elements.VisualPlaceNode;
import org.workcraft.util.Func;
import org.workcraft.util.GUI;
import org.workcraft.workspace.WorkspaceEntry;

public class TimeValueSetterTool extends AbstractTool{

	protected SON net;
	protected VisualSON visualNet;

	protected Map<PlaceNode, Boolean>initialMarking;;
	protected SimulationAlg simuAlg;
	private JPanel interfacePanel, timePropertyPanel, timeInputPanel;

	private int labelheight = 20;
	private int labelwidth = 35;

	private Color selectedColor = Color.ORANGE;
	private Font font = new Font("Arial", Font.PLAIN, 12);
	private String startLabel = "Start time interval: ";
	private String endLabel = "End time interval: ";
	private String durationLabel = "Duration interval: ";
	private String timeLabel = "Time interval: ";

	protected Map<PlaceNode, Boolean> finalMarking;;

	//Set limit integers to JTextField
	class InputFilter extends DocumentFilter {

        private int maxLength;

        public InputFilter() {
            maxLength = 4; // The number of characters allowed
        }

        private boolean isInteger(String text) {
            try {
               Integer.parseInt(text);
               return true;
            } catch (NumberFormatException e) {
               return false;
            }
         }

        @Override
        public void insertString(FilterBypass fb, int offset, String string,
                AttributeSet attr) throws BadLocationException {

            Document doc = fb.getDocument();
            StringBuilder sb = new StringBuilder();
            sb.append(doc.getText(0, doc.getLength()));
            sb.insert(offset, string);

            if (doc.getLength() + string.length() <= maxLength
                    	&& isInteger(string)) {
                fb.insertString(offset, string, attr);
            }
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length,
                String text, AttributeSet attrs) throws BadLocationException {

            Document doc = fb.getDocument();
            StringBuilder sb = new StringBuilder();
            sb.append(doc.getText(0, doc.getLength()));
            sb.replace(offset, offset + length, text);

            if (isInteger(sb.toString())
            		&& (doc.getLength() + text.length() - length) <= maxLength) {
                super.replace(fb, offset, length, text, attrs);
             }
        }
    }

	@Override
	public void createInterfacePanel(final GraphEditor editor) {
		super.createInterfacePanel(editor);

		timePropertyPanel = new JPanel();
		timePropertyPanel.setBorder(BorderFactory.createTitledBorder("Time value"));
		timePropertyPanel.setLayout(new WrapLayout());
		timePropertyPanel.setPreferredSize(new Dimension(0, 250));

		interfacePanel = new JPanel();
		interfacePanel.setLayout(new BorderLayout());
		interfacePanel.add(timePropertyPanel, BorderLayout.PAGE_START);
	}

	private JPanel createTimeInputPanel(final String title, final Interval value, final Node node){

		timeInputPanel = new JPanel();
		timeInputPanel.setLayout(new FlowLayout());

		JLabel label = new JLabel();
		label.setText(title);
		label.setFont(font);
		label.setPreferredSize(new Dimension(labelwidth * 3, labelheight));

		final JTextField min = new JTextField();
		min.setPreferredSize(new Dimension(labelwidth, labelheight));
		min.setText(value.minToString());
		((AbstractDocument) min.getDocument()).setDocumentFilter(new InputFilter());

		JLabel dash = new JLabel();
		dash.setText("-");

		final JTextField max = new JTextField();
		max.setText(value.maxToString());
		max.setPreferredSize(new Dimension(labelwidth, labelheight));
		((AbstractDocument) max.getDocument()).setDocumentFilter(new InputFilter());


		timeInputPanel.add(label);
		timeInputPanel.add(min);
		timeInputPanel.add(dash);
		timeInputPanel.add(max);

		min.addFocusListener(new FocusListener() {
			@Override
	        public void focusLost(FocusEvent e) {
				setValue(node, title, min, true);
	        }

			@Override
			public void focusGained(FocusEvent e) {
			}
	      });

		min.addKeyListener(new KeyListener(){

			@Override
			public void keyTyped(KeyEvent e) {
			}

			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode()==KeyEvent.VK_ENTER){
					timeInputPanel.requestFocus();
			    }
			}

			@Override
			public void keyReleased(KeyEvent e) {
			}

		});

		max.addFocusListener(new FocusListener() {
			@Override
	        public void focusLost(FocusEvent e) {
				setValue(node, title, max, false);
	        }

			@Override
			public void focusGained(FocusEvent e) {
			}
	      });

		max.addKeyListener(new KeyListener(){

			@Override
			public void keyTyped(KeyEvent e) {
			}

			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode()==KeyEvent.VK_ENTER){
					timeInputPanel.requestFocus();
			    }
			}

			@Override
			public void keyReleased(KeyEvent e) {
			}

		});
		return timeInputPanel;
	}

	private void setValue(Node node, String title, JTextField field, boolean isMin){

		autoComplete(field);

		if(title.equals(timeLabel)){
			VisualSONConnection con = (VisualSONConnection)node;
			Interval value = con.getTime();
			if(isMin){
				Interval input = new Interval(Interval.getInteger(field.getText()), value.getMax());
				if(compare(input)){
					con.setTime(input);
				}else{
					con.setTime(value);
					field.setText(value.minToString());
				}
			}else{
				Interval input = new Interval(value.getMin(), Interval.getInteger(field.getText()));
				if(compare(input)){
					con.setTime(input);
				}else{
					con.setTime(value);
					field.setText(value.maxToString());
				}
			}
		}
		else if(title.equals(startLabel)){
			VisualCondition c = (VisualCondition)node;
			Interval value = c.getStartTime();
			if(isMin){
				Interval input = new Interval(Interval.getInteger(field.getText()), value.getMax());
				if(compare(input)){
					c.setStartTime(input);
				}else{
					c.setStartTime(value);
					field.setText(value.minToString());
				}
			}else{
				Interval input = new Interval(value.getMin(), Interval.getInteger(field.getText()));
				if(compare(input)){
					c.setStartTime(input);
				}else{
					c.setStartTime(value);
					field.setText(value.maxToString());
				}
			}
		}
		else if(title.equals(durationLabel)){
			Interval value;
			if(node instanceof VisualPlaceNode){
				VisualPlaceNode c = (VisualPlaceNode)node;
				value = c.getDuration();
				if(isMin){
					Interval input = new Interval(Interval.getInteger(field.getText()), value.getMax());
					if(compare(input)){
						c.setDuration(input);
					}else{
						c.setDuration(value);
						field.setText(value.minToString());
					}
				}else{
					Interval input = new Interval(value.getMin(), Interval.getInteger(field.getText()));
					if(compare(input)){
						c.setDuration(input);
					}else{
						c.setDuration(value);
						field.setText(value.maxToString());
					}
				}
			}
			else if(node instanceof VisualBlock){
				VisualBlock b = (VisualBlock)node;
				value = b.getDuration();

				if(isMin){
					Interval input = new Interval(Interval.getInteger(field.getText()), value.getMax());
					if(compare(input)){
						b.setDuration(input);
					}else{
						b.setDuration(value);
						field.setText(value.minToString());
					}
				}else{
					Interval input = new Interval(value.getMin(), Interval.getInteger(field.getText()));
					if(compare(input)){
						b.setDuration(input);
					}else{
						b.setDuration(value);
						field.setText(value.maxToString());
					}
				}
			}
		}

		else if(title.equals(endLabel)){
			VisualCondition c = (VisualCondition)node;
			Interval value = c.getEndTime();
			if(isMin){
				Interval input = new Interval(Interval.getInteger(field.getText()), value.getMax());
				if(compare(input)){
					c.setEndTime(input);
				}else{
					c.setEndTime(value);
					field.setText(value.minToString());
				}
			}else{
				Interval input = new Interval(value.getMin(), Interval.getInteger(field.getText()));
				if(compare(input)){
					c.setEndTime(input);
				}else{
					c.setEndTime(value);
					field.setText(value.maxToString());
				}
			}
		}
	}

	private void autoComplete(JTextField field){
		String text = field.getText();
		int length = text.length();

		if(length < 4){
		   while (length < 4) {
		    StringBuffer sb = new StringBuffer();
		    sb.append("0").append(text);
		    text = sb.toString();
		    field.setText(text);
		    length = text.length();
		   }
		}
	}

	private boolean compare(Interval value){
		int start = value.getMin();
		int end = value.getMax();

		if(start <= end){
			return true;
		}
		return false;
	}

	private void updateTimePanel(final GraphEditor editor, Node node){
		timePropertyPanel.removeAll();
		timePropertyPanel.revalidate();
		timePropertyPanel.repaint();

		Interval value;
		if(node instanceof VisualSONConnection){
			VisualSONConnection con = (VisualSONConnection)node;
			if(con.getSemantics()==Semantics.PNLINE || con.getSemantics() == Semantics.ASYNLINE){
				value = ((VisualSONConnection)node).getTime();
				timePropertyPanel.add(createTimeInputPanel(timeLabel, value, node));
			}
		}
		else if(node instanceof VisualPlaceNode){

			if(node instanceof VisualCondition){
				VisualCondition c2 = (VisualCondition)node;

				if(c2.isInitial()){
					value = c2.getStartTime();
					timePropertyPanel.add(createTimeInputPanel(startLabel, value, node));
				}
				if(c2.isFinal()){
					value = c2.getEndTime();
					timePropertyPanel.add(createTimeInputPanel(endLabel, value, node));
				}
			}

			VisualPlaceNode c = (VisualPlaceNode)node;
			value =c.getDuration();
			timePropertyPanel.add(createTimeInputPanel(durationLabel, value, node));
		}
		else if(node instanceof VisualBlock){
			VisualBlock b = (VisualBlock)node;
			value =b.getDuration();
			timePropertyPanel.add(createTimeInputPanel(durationLabel, value, node));
		}

		timePropertyPanel.revalidate();
		editor.requestFocus();
		editor.repaint();
	}


	@Override
	public void activated(final GraphEditor editor) {
		visualNet = (VisualSON)editor.getModel();
		net = (SON)visualNet.getMathModel();
		WorkspaceEntry we = editor.getWorkspaceEntry();
		//BlockConnector.blockBoundingConnector(visualNet);
		we.setCanSelect(false);

		net.refreshColor();
		net.clearMarking();
		initialise();
		SONSettings.setTimeVisibility(true);

		//set property states for initial and final states
		removeProperties();
		setProperties();

		editor.forceRedraw();
		editor.getModel().setTemplateNode(null);
	}

	protected void initialise(){
		simuAlg = new SimulationAlg(net);
		initialMarking=simuAlg.getInitialMarking();
		finalMarking = simuAlg.getFinalMarking();
	}


	@Override
	public void deactivated(final GraphEditor editor) {
		removeProperties();
		SONSettings.setTimeVisibility(false);
		//BlockConnector.blockInternalConnector(visualNet);
		net.refreshColor();
		net.clearMarking();
	}

	private void setProperties(){
		for(PlaceNode c : initialMarking.keySet()){
			if((c instanceof Condition) && initialMarking.get(c))
				((Condition)c).setInitial(true);
		}
		for(PlaceNode c : finalMarking.keySet()){
			if((c instanceof Condition) && finalMarking.get(c))
				((Condition)c).setFinal(true);
		}
	}

	private void removeProperties(){
		for(PlaceNode c : net.getPlaceNodes()){
			if(c instanceof Condition){
				((Condition)c).setInitial(false);
				((Condition)c).setFinal(false);
			}
		}
	}

	@Override
	public void mousePressed(GraphEditorMouseEvent e){
		net.refreshColor();

		Node node = HitMan.hitTestForConnection(e.getPosition(), e.getModel().getRoot());
		if( node instanceof VisualSONConnection){
			VisualSONConnection con = (VisualSONConnection)node;
			if(con.getSemantics()==Semantics.PNLINE || con.getSemantics() == Semantics.ASYNLINE){
				((VisualSONConnection) node).setColor(selectedColor);
				updateTimePanel(e.getEditor(), node);
				return;
			}
		}

		Node node2 = HitMan.hitFirstNodeOfType(e.getPosition(), e.getModel().getRoot(), VisualBlock.class);
		if(node2 != null){
			if(((VisualBlock)node2).getIsCollapsed()){
				((VisualBlock) node2).setForegroundColor(selectedColor);
				updateTimePanel(e.getEditor(), node2);
				return;
			}
		}

		Node node3 = HitMan.hitDeepest(e.getPosition(), e.getModel().getRoot(),
				new Func<Node, Boolean>() {
					@Override
					public Boolean eval(Node node) {
						return node instanceof VisualPlaceNode;
					}
				});
			if (node3 instanceof VisualPlaceNode) {
				((VisualPlaceNode) node).setForegroundColor(selectedColor);
				updateTimePanel(e.getEditor(), node3);
			}
	}

	@Override
	public JPanel getInterfacePanel() {
		return interfacePanel;
	}

	@Override
	public String getLabel() {
		return "Set time value";
	}

	@Override
	public void drawInScreenSpace(GraphEditor editor, Graphics2D g) {
		GUI.drawEditorMessage(editor, g, Color.BLACK, "Click on the condition or connection to set time value.");
	}

	@Override
	public int getHotKeyCode() {
		return KeyEvent.VK_T;
	}

	@Override
	public Icon getIcon() {
		return GUI.createIconFromSVG("images/icons/svg/son-time.svg");
	}

	@Override
	public Decorator getDecorator(GraphEditor editor) {
		return new Decorator(){
			@Override
			public Decoration getDecoration(Node node) {
				return null;

			}
		};
	}
}
