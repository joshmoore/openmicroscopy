/*
 * org.openmicroscopy.shoola.util.ui.login.ServerDialog 
 *
 *------------------------------------------------------------------------------
 *  Copyright (C) 2006 University of Dundee. All rights reserved.
 *
 *
 * 	This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License along
 *  with this program; if not, write to the Free Software Foundation, Inc.,
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *------------------------------------------------------------------------------
 */
package org.openmicroscopy.shoola.util.ui.login;



//Java imports
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Enumeration;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.WindowConstants;

//Third-party libraries

//Application-internal dependencies
import org.openmicroscopy.shoola.util.ui.IconManager;
import org.openmicroscopy.shoola.util.ui.TitlePanel;
import org.openmicroscopy.shoola.util.ui.UIUtilities;

/** 
 * Modal dialog used to manage servers.
 *
 * @author  Jean-Marie Burel &nbsp;&nbsp;&nbsp;&nbsp;
 * <a href="mailto:j.burel@dundee.ac.uk">j.burel@dundee.ac.uk</a>
 * @author Donald MacDonald &nbsp;&nbsp;&nbsp;&nbsp;
 * <a href="mailto:donald@lifesci.dundee.ac.uk">donald@lifesci.dundee.ac.uk</a>
 * @version 3.0
 * <small>
 * (<b>Internal version:</b> $Revision: $Date: $)
 * </small>
 * @since OME3.0
 */
class ServerDialog 
	extends JDialog
	implements ActionListener, ComponentListener, PropertyChangeListener
{

	/** Bound property indicating the selected connection speed is selected. */
	static final String 				CONNECTION_SPEED_PROPERTY = 
											"connectionSpeed";
	
	/** Bound property indicating that a new server is selected. */
	static final String 				SERVER_PROPERTY = "server";

	/** Bound property indicating that the window is closed. */
	static final String 				CLOSE_PROPERTY = "close";

	/** Bound property indicating that the window is closed. */
	static final String 				REMOVE_PROPERTY = "remove";
    
	/** ID identifying the close action. */
	private static final int			CLOSE = 0;
	
	/** ID identifying the apply action. */
	private static final int			APPLY = 1;
	
	/** ID identifying the selection of a high speed connection. */
	private static final int			HIGH_SPEED = 2;
	
	/** ID identifying the selection of a medium speed connection. */
	private static final int			MEDIUM_SPEED = 3;
	
	/** ID identifying the selection of a low speed connection. */
	private static final int			LOW_SPEED = 4;
	
	/** The default size of the window. */
	private static final Dimension		WINDOW_DIM = new Dimension(400, 450);
	
	/** The window's title. */
	private static final String			TITLE = "Servers";
	
	/** The textual decription of the window. */
	private static final String 		TEXT = "Enter a new server or \n" +
										"select an existing one.";
	
    /** 
     * The size of the invisible components used to separate buttons
     * horizontally.
     */
    private static final Dimension  	H_SPACER_SIZE = new Dimension(5, 10);
    
	/** 
	 * The size of the invisible components used to separate widgets
	 * vertically.
	 */
	protected static final Dimension	V_SPACER_SIZE = new Dimension(1, 20);

	/** Button to close and dispose of the window. */
	private JButton			cancelButton;
	
	/** Button to select a new server. */
	private JButton			finishButton;
	
	/** Reference to the editor hosting the table. */
	private ServerEditor	editor;
    
    /** The component hosting the title and the warning messages if required. */
    private JLayeredPane    titleLayer;
    
    /** The UI component hosting the title. */
    private TitlePanel      titlePanel;
    
    /** Group hosting the connection speed level. */
    private ButtonGroup 	buttonsGroup;
    
	/** Closes and disposes. */
	private void close()
	{
		editor.stopEdition();
		setVisible(false);
		dispose();
	}
	
	/** Fires a property indicating that a new server is selected. */
	void apply()
	{
		editor.stopEdition();
		String server = editor.getSelectedServer();
		if (server != null) {
			editor.handleServers(server);
			firePropertyChange(SERVER_PROPERTY, null, server);
		}
		if (buttonsGroup != null) {
			Enumeration en = buttonsGroup.getElements();
			JRadioButton button;
			int index;
			while (en.hasMoreElements()) {
				button = (JRadioButton) en.nextElement();
				if (button.isSelected()) {
					index = Integer.parseInt(button.getActionCommand());
					switch (index) {
						case HIGH_SPEED:
							firePropertyChange(CONNECTION_SPEED_PROPERTY, null, 
										new Integer(LoginCredentials.HIGH));
							break;
						case MEDIUM_SPEED:
							firePropertyChange(CONNECTION_SPEED_PROPERTY, null, 
										new Integer(LoginCredentials.MEDIUM));
							break;
						case LOW_SPEED:
							firePropertyChange(CONNECTION_SPEED_PROPERTY, null, 
											new Integer(LoginCredentials.LOW));
					}
				}
			}
		}
		close();
	}
	
	/** Sets the window's properties. */
	private void setProperties()
	{
		setTitle(TITLE);
		setModal(true);
		setAlwaysOnTop(true);
	}
	
	/** Attaches the various listeners. */
	private void initListeners()
	{
		cancelButton.addActionListener(this);
		cancelButton.setActionCommand(""+CLOSE);
		finishButton.addActionListener(this);
		finishButton.setActionCommand(""+APPLY);
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter()
        {
        	public void windowClosing(WindowEvent e) { close(); }
        	public void windowOpened(WindowEvent e) { editor.initFocus(); } 
        });
		addComponentListener(this);
	}
	
	/** Initializes the UI components. */
	private void initComponents()
	{
		editor.addPropertyChangeListener(this);
		cancelButton = new JButton("Cancel");
		cancelButton.setToolTipText("Close the window.");
		cancelButton.setBackground(UIUtilities.WINDOW_BACKGROUND_COLOR);
		finishButton =  new JButton("Apply");
		finishButton.setEnabled(false);
		finishButton.setBackground(UIUtilities.WINDOW_BACKGROUND_COLOR);
		getRootPane().setDefaultButton(finishButton);
		//layer hosting title and empty message
		IconManager icons = IconManager.getInstance();
		titleLayer = new JLayeredPane();
		titlePanel = new TitlePanel(TITLE, TEXT, 
									icons.getIcon(IconManager.CONFIG_48));
		//titleLayer.add(titlePanel, new Integer(0));
	}
	
	/**
	 * Builds and lays out the tool bar.
	 * 
	 * @return See above.
	 */
	private JPanel buildToolBar()
	{
		JPanel bar = new JPanel();
		bar.setBackground(UIUtilities.WINDOW_BACKGROUND_COLOR);
		bar.setOpaque(true);
        bar.setBorder(null);
        bar.add(finishButton);
        bar.add(Box.createRigidArea(H_SPACER_SIZE));
        bar.add(cancelButton);
        JPanel p = UIUtilities.buildComponentPanelRight(bar);
        p.setOpaque(true);
        p.setBackground(UIUtilities.WINDOW_BACKGROUND_COLOR);
        return p;
	}
	
	/** Builds and lays out the UI.
	 * 
	 *  @param index The connection speed index.
	 */
	private void buildGUI(int index)
	{
		JPanel mainPanel;
		if (index == -1) 
			mainPanel = editor;
		else mainPanel = buildConnectionSpeed(index);
		//mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        //mainPanel.add(editor);
        Container c = getContentPane();
        //setLayout(new BorderLayout(0, 0));
        c.add(titlePanel, BorderLayout.NORTH);
        c.add(mainPanel, BorderLayout.CENTER);
        c.add(buildToolBar(), BorderLayout.SOUTH);
	}
	
	/**
	 * Shows the warning message if the passed value is <code>true</code>,
	 * hides it otherwise.
	 * 
	 * @param warning 	Pass <code>true</code> to show the message, 
	 * 					<code>false</code> otherwise.	
	 * @param p			The component to add or remove.		
	 */
	private void showMessagePanel(boolean warning, JComponent p)
	{
		if (warning) {
            titleLayer.add(p, new Integer(1));
            titleLayer.validate();
            titleLayer.repaint();
        } else {
        	if (p == null) return;
        	titleLayer.remove(p);
            titleLayer.repaint();
        }
	}

	/** 
	 * Adds the connection speed options to the display. 
	 * 
	 * @param index The default index.
	 * @return See above.
	 */
	private JPanel buildConnectionSpeed(int index)
	{
		JPanel p = new JPanel();
		p.setBackground(UIUtilities.WINDOW_BACKGROUND_COLOR);
		p.setBorder(BorderFactory.createTitledBorder("Connection Speed"));
		buttonsGroup = new ButtonGroup();
		JRadioButton button = new JRadioButton();
		button.setBackground(UIUtilities.WINDOW_BACKGROUND_COLOR);
		button.setText("LAN");
		button.setActionCommand(""+HIGH_SPEED);
		button.addActionListener(this);
		button.setSelected(index == LoginCredentials.HIGH);
		buttonsGroup.add(button);
		p.add(button);
		button = new JRadioButton();
		button.setBackground(UIUtilities.WINDOW_BACKGROUND_COLOR);
		button.setText("High (Broadband)");
		button.setActionCommand(""+MEDIUM_SPEED);
		button.setSelected(index == LoginCredentials.MEDIUM);
		button.addActionListener(this);
		buttonsGroup.add(button);
		p.add(button);
		button = new JRadioButton();
		button.setBackground(UIUtilities.WINDOW_BACKGROUND_COLOR);
		button.setText("Low (Dial-up)");
		button.setActionCommand(""+LOW_SPEED);
		button.setSelected(index == LoginCredentials.LOW);
		button.addActionListener(this);
		buttonsGroup.add(button);
		p.add(button);
		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.add(editor);
		p = UIUtilities.buildComponentPanel(p);
		p.setBackground(UIUtilities.WINDOW_BACKGROUND_COLOR);
		content.add(p);
		content.setBackground(UIUtilities.WINDOW_BACKGROUND_COLOR);
		return content;
	}
	
	/** 
	 * Creates a new instance. 
	 * 
	 * @param frame		The parent frame. 
	 * @param editor 	The server editor. Mustn't be <code>null</code>.
	 * @param index		The speed of the connection.
	 */
	ServerDialog(JFrame frame, ServerEditor editor, int index)
	{ 
		super(frame);
		this.editor = editor;
		setProperties();
		initComponents();
		initListeners();
		buildGUI(index);
		setSize(WINDOW_DIM);
	}
	
	/** 
	 * Creates a new instance. 
	 * 
	 * @param frame		The parent frame. 
	 * @param editor 	The server editor. Mustn't be <code>null</code>.
	 */
	ServerDialog(JFrame frame, ServerEditor editor)
	{ 
		this(frame, editor, -1);
	}

	
	
	/** 
     * Resizes the layered pane hosting the title when the window is resized.
     * @see ComponentListener#componentResized(ComponentEvent)
     */
	public void componentResized(ComponentEvent e) 
	{
		/*
		Rectangle r = getBounds();
		if (titleLayer == null) return;
		Dimension d  = new Dimension(r.width, ServerEditor.TITLE_HEIGHT);
	    titlePanel.setSize(d);
	    titlePanel.setPreferredSize(d);
	    titleLayer.setSize(d);
	    titleLayer.setPreferredSize(d);
	    titleLayer.validate();
	    titleLayer.repaint();
	    */
	}

	/**
	 * Reacts to property changes fired by the{@link ServerEditor}.
	 * @see PropertyChangeListener#propertyChange(PropertyChangeEvent)
	 */
	public void propertyChange(PropertyChangeEvent evt) 
	{
		String name = evt.getPropertyName();
		if (ServerEditor.EDIT_PROPERTY.equals(name)) {
			Boolean value = (Boolean) evt.getNewValue();
			finishButton.setEnabled(value.booleanValue());
		} else if (ServerEditor.ADD_MESSAGE_PROPERTY.equals(name)) {
			showMessagePanel(true, (JComponent) evt.getNewValue());
		}  else if (ServerEditor.REMOVE_MESSAGE_PROPERTY.equals(name)) {
			showMessagePanel(false, (JComponent) evt.getNewValue());
		} else if (ServerEditor.APPLY_SERVER_PROPERTY.equals(name)) {
			apply();
		}
	}
	
	/** 
	 * Reacts to the selection of the button.
	 * @see ActionListener#actionPerformed(ActionEvent)
	 */
	public void actionPerformed(ActionEvent e)
	{
		int index = Integer.parseInt(e.getActionCommand());
		switch (index) {
			case CLOSE:
				close();
				break;
			case APPLY:
				apply();
				break;
			case HIGH_SPEED:
			case MEDIUM_SPEED:
			case LOW_SPEED:
				finishButton.setEnabled(true);
		}
	}
	
	/** 
     * Required by {@link ComponentListener} interface but no-op implementation 
     * in our case. 
     * @see ComponentListener#componentShown(ComponentEvent)
     */
	public void componentShown(ComponentEvent e) {}
	
	/** 
     * Required by {@link ComponentListener} interface but no-op implementation 
     * in our case. 
     * @see ComponentListener#componentHidden(ComponentEvent)
     */
	public void componentHidden(ComponentEvent e) {}

	/** 
     * Required by {@link ComponentListener} interface but no-op implementation 
     * in our case. 
     * @see ComponentListener#componentMoved(ComponentEvent)
     */
	public void componentMoved(ComponentEvent e) {}

	
    
}
