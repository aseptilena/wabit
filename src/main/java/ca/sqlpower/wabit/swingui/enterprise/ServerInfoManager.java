/*
 * Copyright (c) 2009, SQL Power Group Inc.
 *
 * This file is part of Wabit.
 *
 * Wabit is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * Wabit is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
 */

package ca.sqlpower.wabit.swingui.enterprise;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.Callable;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import ca.sqlpower.enterprise.client.SPServerInfo;
import ca.sqlpower.swingui.DataEntryPanelBuilder;
import ca.sqlpower.wabit.WabitSessionContext;
import ca.sqlpower.wabit.swingui.LogInToServerAction;

import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;

/**
 * A GUI for maintaining the manually-configured server info objects.
 */
public class ServerInfoManager {

    private final WabitSessionContext context;
    private final Component dialogOwner;
    private final JPanel panel;
    private JList serverInfos;
    private final JButton connectButton;
    
    private Action removeAction = new AbstractAction("Remove") {

        public void actionPerformed(ActionEvent e) {
            Object[] selectedValues = serverInfos.getSelectedValues();
            for (Object o : selectedValues) {
                SPServerInfo si = (SPServerInfo) o;
                context.removeServer(si);
            }
            
            refreshInfoList();
        }
        
    };
    
    private Action addAction = new AbstractAction("Add...") {

        public void actionPerformed(ActionEvent e) {
            showAddOrEditDialog(null);
        }
        
    };
    
    private Action editAction = new AbstractAction("Edit...") {
    	public void actionPerformed(ActionEvent e) {
    		editSelectedServer();
    	}
    };

	/**
	 * Creates a panel that displays the currently configured server
	 * connections. New connections can be added from this panel and existing
	 * connections can be modified or removed.
	 * 
	 * @param m_context
	 *            A Wabit context that contains server connection information.
	 * @param m_dialogOwner
	 *            A component that will be used as the dialog owner for other
	 *            panels.
	 * @param closeAction
	 *            An action that will properly close the object displaying the
	 *            panel.
	 */
    public ServerInfoManager(
    		WabitSessionContext m_context,
    		Component m_dialogOwner, 
    		final Runnable closeAction) {
        this.context = m_context;
        this.dialogOwner = m_dialogOwner;
        DefaultFormBuilder builder = new DefaultFormBuilder(new FormLayout("pref:grow, 5dlu, pref", "pref, pref, pref"));
        
        serverInfos = new JList(new DefaultListModel());
        serverInfos.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    editSelectedServer();
                }
            }


        });
        JScrollPane scrollPane = new JScrollPane(serverInfos);
        scrollPane.setPreferredSize(new Dimension(400, 300));

        this.connectButton = new JButton("Connect");
        this.connectButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) 
			{
				SPServerInfo selectedItem = 
					(SPServerInfo) serverInfos.getSelectedValue();
				
				Window dialogParent;
		        if (dialogOwner instanceof Window) {
		            dialogParent = (Window) dialogOwner;
		        } else {
		            dialogParent = SwingUtilities.getWindowAncestor(dialogOwner);
		        }
		        
				if (selectedItem != null) {
				    LogInToServerAction action = 
				    	new LogInToServerAction(
				    		dialogParent, 
				    		selectedItem, 
				    		context);
				    action.actionPerformed(e);
				}
			}
		});
        
        // Build the GUI
        refreshInfoList();
        CellConstraints cc = new CellConstraints();
        builder.add(new JLabel("Available Server Connections:"), cc.xyw(1, 1, 3));
        builder.nextLine();
        builder.add(scrollPane, cc.xywh(1, 2, 1, 2));
        
        DefaultFormBuilder buttonBarBuilder = new DefaultFormBuilder(new FormLayout("pref"));
        buttonBarBuilder.append(new JButton(addAction));
        buttonBarBuilder.append(new JButton(editAction));
        buttonBarBuilder.append(new JButton(removeAction));
        buttonBarBuilder.append(connectButton);
        buttonBarBuilder.append(new JButton(new AbstractAction("Close") {
			public void actionPerformed(ActionEvent arg0) {
				closeAction.run();
			}
        }));
        builder.add(buttonBarBuilder.getPanel(), cc.xy(3, 2));
        builder.setDefaultDialogBorder();
        panel = builder.getPanel();
    }
    
    private void refreshInfoList() {
        DefaultListModel model = (DefaultListModel) serverInfos.getModel();
        model.removeAllElements();
        for (SPServerInfo si : context.getEnterpriseServers(false)) {
            model.addElement(si);
        }
    }
    
    public JPanel getPanel() {
        return panel;
    }

    /**
     * Shows a dialog for adding a new server info, or editing an existing one.
     * 
     * @param serverInfo
     *            The server info to edit. If the intent is to add a new one,
     *            specify null.
     */
    private void showAddOrEditDialog(final SPServerInfo serverInfo) {
        
        final ServerInfoPanel infoPanel;
        if (serverInfo == null) {
            infoPanel = new ServerInfoPanel(panel);
        } else {
            infoPanel = new ServerInfoPanel(panel, serverInfo);
        }

        Callable<Boolean> okCall = new Callable<Boolean>() {
            public Boolean call() throws Exception {
            	if (!infoPanel.applyChanges()) {
            		return false;
            	}
                if (serverInfo != null) {
                    context.removeServer(serverInfo);
                }
                context.addServer(infoPanel.getServerInfo());
                refreshInfoList();
                return true;
            }
        };
        Callable<Boolean> cancelCall = new Callable<Boolean>() {
            public Boolean call() throws Exception {
                return true;
            }
        };

        JDialog dialog = DataEntryPanelBuilder.createDataEntryPanelDialog(
                infoPanel, dialogOwner, "Server Connection Properties", "OK",
                okCall, cancelCall);

        dialog.setVisible(true);
    }
    
	private void editSelectedServer() {
		SPServerInfo selectedItem = (SPServerInfo) serverInfos.getSelectedValue();
		if (selectedItem != null) {
		    showAddOrEditDialog(selectedItem);
		}
	}
}
