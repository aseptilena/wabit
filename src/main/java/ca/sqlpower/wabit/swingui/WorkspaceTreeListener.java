/*
 * Copyright (c) 2008, SQL Power Group Inc.
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

package ca.sqlpower.wabit.swingui;

import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EnumSet;

import javax.annotation.Nullable;
import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.tree.TreePath;

import org.apache.log4j.Logger;

import ca.sqlpower.enterprise.client.Grant;
import ca.sqlpower.enterprise.client.Group;
import ca.sqlpower.enterprise.client.User;
import ca.sqlpower.object.SPObject;
import ca.sqlpower.sql.JDBCDataSource;
import ca.sqlpower.sql.Olap4jDataSource;
import ca.sqlpower.sql.SPDataSource;
import ca.sqlpower.sqlobject.SQLObject;
import ca.sqlpower.swingui.SPSUtils;
import ca.sqlpower.wabit.WabitDataSource;
import ca.sqlpower.wabit.WabitObject;
import ca.sqlpower.wabit.WabitWorkspace;
import ca.sqlpower.wabit.enterprise.client.ReportTask;
import ca.sqlpower.wabit.enterprise.client.security.CachingWabitAccessManager;
import ca.sqlpower.wabit.enterprise.client.security.WabitAccessManager;
import ca.sqlpower.wabit.image.WabitImage;
import ca.sqlpower.wabit.report.ContentBox;
import ca.sqlpower.wabit.report.Report;
import ca.sqlpower.wabit.report.Template;
import ca.sqlpower.wabit.report.chart.Chart;
import ca.sqlpower.wabit.report.selectors.Selector;
import ca.sqlpower.wabit.rs.olap.OlapQuery;
import ca.sqlpower.wabit.rs.query.QueryCache;
import ca.sqlpower.wabit.swingui.action.AddDataSourceAction;
import ca.sqlpower.wabit.swingui.action.CopyChartAction;
import ca.sqlpower.wabit.swingui.action.CopyImageAction;
import ca.sqlpower.wabit.swingui.action.CopyQueryAction;
import ca.sqlpower.wabit.swingui.action.CopyReportAction;
import ca.sqlpower.wabit.swingui.action.CopyReportTaskAction;
import ca.sqlpower.wabit.swingui.action.CopyTemplateAction;
import ca.sqlpower.wabit.swingui.action.CopyUuidToClipboardAction;
import ca.sqlpower.wabit.swingui.action.DeleteFromTreeAction;
import ca.sqlpower.wabit.swingui.action.EditCellAction;
import ca.sqlpower.wabit.swingui.action.NewChartAction;
import ca.sqlpower.wabit.swingui.action.NewGroupAction;
import ca.sqlpower.wabit.swingui.action.NewImageAction;
import ca.sqlpower.wabit.swingui.action.NewOLAPQueryAction;
import ca.sqlpower.wabit.swingui.action.NewQueryAction;
import ca.sqlpower.wabit.swingui.action.NewReportAction;
import ca.sqlpower.wabit.swingui.action.NewReportTaskAction;
import ca.sqlpower.wabit.swingui.action.NewTemplateAction;
import ca.sqlpower.wabit.swingui.action.NewUserAction;
import ca.sqlpower.wabit.swingui.action.ReportFromTemplateAction;
import ca.sqlpower.wabit.swingui.action.ScheduleReportAction;
import ca.sqlpower.wabit.swingui.action.SecurityAction;
import ca.sqlpower.wabit.swingui.action.ShowEditorAction;
import ca.sqlpower.wabit.swingui.tree.FolderNode;
import ca.sqlpower.wabit.swingui.tree.WorkspaceTreeCellRenderer;
import ca.sqlpower.wabit.swingui.tree.FolderNode.FolderType;
import ca.sqlpower.wabit.swingui.tree.WorkspaceTreeModel.Olap4jTreeObject;

/**
 * This listener is the main listener on the workspace tree in Wabit.
 * It will listen and handle all tree events from creating new elements
 * to changing the view based on selected nodes.
 */
public class WorkspaceTreeListener extends MouseAdapter {
	
	private static final Logger logger = Logger.getLogger(WorkspaceTreeListener.class);
	
    public static final Icon DB_ICON = new ImageIcon(WorkspaceTreeCellRenderer.class.getClassLoader().getResource("icons/connection-db-16.png"));
    public static final Icon OLAP_DB_ICON = new ImageIcon(WorkspaceTreeCellRenderer.class.getClassLoader().getResource("icons/connection-olap-16.png"));
	
	private final WabitSwingSession session;
	private final WabitSwingSessionContextImpl context;
	
	private WabitAccessManager accessManager = null;

	private User currentUser = null;
	
	public WorkspaceTreeListener(WabitSwingSession session) {
		this.session = session;
		context = (WabitSwingSessionContextImpl) session.getContext();
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		maybeShowPopup(e);
	}

	@Override
	public void mousePressed(MouseEvent e) {
		maybeShowPopup(e);
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		Object lastPathComponent = getLastPathComponent(e);
		if (e.isPopupTrigger()) {
			maybeShowPopup(e);
		}
		if (lastPathComponent != null) {
			if (e.getButton() == MouseEvent.BUTTON1  && e.getClickCount() == 2) {
				if (lastPathComponent instanceof SPObject) {
					session.getWorkspace().setEditorPanelModel((SPObject) lastPathComponent);
				} else if (lastPathComponent instanceof FolderNode) {
					JTree tree = session.getTree();
					if (tree.isExpanded(tree.getSelectionRows()[0])) {
						tree.collapsePath(tree.getSelectionPath());
					} else {
						tree.expandPath(tree.getSelectionPath());
					}
				}
			}
		}
	}

	private Object getLastPathComponent(MouseEvent e) {
		JTree t = (JTree) e.getSource();
		int row = t.getRowForLocation(e.getX(), e.getY());
		TreePath tp = t.getPathForRow(row);
		Object lastPathComponent = null;
		if (tp != null) {
			lastPathComponent = tp.getLastPathComponent();
			logger.debug("Clicked on " + lastPathComponent.getClass());
		}
		return lastPathComponent;
	}
	
	/**
	 * This will Display a List of options once you right click on the WorkspaceTree.
	 */
	private void maybeShowPopup(MouseEvent e) {
		if (!e.isPopupTrigger()) {
			return;
		}
		
		JPopupMenu menu = new JPopupMenu();
		final Object lastPathComponent = getLastPathComponent(e);

		JMenuItem newQuery = new JMenuItem(new NewQueryAction(session));
		newQuery.setIcon(WorkspaceTreeCellRenderer.QUERY_ICON);
		
		JMenuItem newOlapQuery = new JMenuItem(new NewOLAPQueryAction(session));
		newOlapQuery.setIcon(WorkspaceTreeCellRenderer.OLAP_QUERY_ICON);
		
		JMenuItem newChart = new JMenuItem(new NewChartAction(session));
		newChart.setIcon(WorkspaceTreeCellRenderer.CHART_ICON);
		
		JMenuItem newImage = new JMenuItem(new NewImageAction(session));
		
		JMenuItem newReport = new JMenuItem(new NewReportAction(session));
		
		JMenuItem newTemplate = new JMenuItem(new NewTemplateAction(session));
		
		JMenuItem newReportTask = new JMenuItem(new NewReportTaskAction(session));
		
		JMenuItem newUser = new JMenuItem(new NewUserAction(session));
		
		JMenuItem newGroup = new JMenuItem(new NewGroupAction(session));
		
		
		if (lastPathComponent != null) {
			JTree tree = (JTree) e.getSource();
			if (lastPathComponent instanceof FolderNode) {
				
				FolderNode lastFolderNode = (FolderNode) lastPathComponent;
				
				if (lastFolderNode.getFolderType().equals(FolderType.CONNECTIONS)) {
					
					menu.add(new AbstractAction("Database Connection Manager...", WabitIcons.CONNECTION_16) {
						public void actionPerformed(ActionEvent e) {
							session.getDbConnectionManager().showDialog(context.getFrame());
						}
					});
					
					objectsMenu(
							menu, 
							WabitDataSource.class.getSimpleName(), 
							null,
							createDataSourcesMenu(), 
							WabitAccessManager.Permission.CREATE);
					
					menu.addSeparator();
					
					boolean sep = securityMenu(menu,WabitDataSource.class.getSimpleName(),null);
					
					if (sep) menu.addSeparator();
					
				} else if (lastFolderNode.getFolderType().equals(FolderType.QUERIES)) {
					
					boolean sep = objectsMenu(
							menu, 
							QueryCache.class.getSimpleName(), 
							null, 
							newQuery,
							WabitAccessManager.Permission.CREATE);
					sep |= objectsMenu(
							menu, 
							OlapQuery.class.getSimpleName(),  
							null,
							newOlapQuery,
							WabitAccessManager.Permission.CREATE);
					
					if (sep) menu.addSeparator();
					
					sep = securityMenu(menu,QueryCache.class.getSimpleName(),null);
					
					sep |= securityMenu(menu,OlapQuery.class.getSimpleName(),null);
					
					if (sep) menu.addSeparator();
				
				} else if (lastFolderNode.getFolderType().equals(FolderType.IMAGES)) {
					
					boolean sep = objectsMenu(
							menu, 
							WabitImage.class.getSimpleName(),  
							null,
							newImage,
							WabitAccessManager.Permission.CREATE);
					
					if (sep) menu.addSeparator();
					
					sep = securityMenu(menu,WabitImage.class.getSimpleName(),null);
				
					if (sep) menu.addSeparator();
					
				} else if (lastFolderNode.getFolderType().equals(FolderType.CHARTS)) {
					
					boolean sep = objectsMenu(
							menu,
							Chart.class.getSimpleName(),  
							null,
							newChart,
							WabitAccessManager.Permission.CREATE);
					
					if (sep) menu.addSeparator();
					
					sep = securityMenu(menu,Chart.class.getSimpleName(),null);
					
					if (sep) menu.addSeparator();
					
				} else if (lastFolderNode.getFolderType().equals(FolderType.REPORTS)) {
					
					boolean sep = objectsMenu(
							menu,
							Report.class.getSimpleName(),  
							null,
							newReport,
							WabitAccessManager.Permission.CREATE);
					
					if (sep) menu.addSeparator();
					
					sep = securityMenu(menu,Report.class.getSimpleName(),null);
				
					if (sep) menu.addSeparator();
					
				} else if (lastFolderNode.getFolderType().equals(FolderType.TEMPLATES)) {
					
					boolean sep = objectsMenu(
							menu,
							Template.class.getSimpleName(),  
							null,
							newTemplate,
							WabitAccessManager.Permission.CREATE);
				
					if (sep) menu.addSeparator();
					
					sep = securityMenu(menu,Template.class.getSimpleName(),null);
				
					if (sep) menu.addSeparator();
					
				} else if (lastFolderNode.getFolderType().equals(FolderType.REPORTTASK)) {
					
					boolean sep = objectsMenu(
							menu,
							ReportTask.class.getSimpleName(),  
							null,
							newReportTask,
							WabitAccessManager.Permission.CREATE);
				
					if (sep) menu.addSeparator();
					
					sep = securityMenu(menu,ReportTask.class.getSimpleName(),null);
				
					if (sep) menu.addSeparator();
					
				} else if (lastFolderNode.getFolderType().equals(FolderType.USERS)) {
					
					boolean sep =objectsMenu(
							menu,
							User.class.getSimpleName(),  
							null,
							newUser,
							WabitAccessManager.Permission.CREATE);
					
					if (sep) menu.addSeparator();
					
					sep = securityMenu(menu,User.class.getSimpleName(),null);
					
					if (sep) menu.addSeparator();
					
				} else if (lastFolderNode.getFolderType().equals(FolderType.GROUPS)) {
					
					boolean sep = objectsMenu(
							menu,
							Group.class.getSimpleName(),  
							null,
							newGroup,
							WabitAccessManager.Permission.CREATE);
					
					if (sep) menu.addSeparator();
					
					sep = securityMenu(menu,Group.class.getSimpleName(),null);
					
					if (sep) menu.addSeparator();
				}
			} else {
			    if (lastPathComponent instanceof SPObject) {
			    	
			    	String uuid = ((SPObject)lastPathComponent).getUUID();
					if (!(lastPathComponent instanceof WabitDataSource)) {
			    		JMenuItem editItem = new JMenuItem(new ShowEditorAction(session.getWorkspace(),
			    				(SPObject) lastPathComponent));
			    		objectsMenu(
			    				menu,
			    				lastPathComponent.getClass().getSimpleName(),  
			    				uuid,
			    				editItem,
			    				WabitAccessManager.Permission.MODIFY);
			    	}
			        if (!(lastPathComponent instanceof ContentBox)
			        		&& !(lastPathComponent instanceof Selector)) {
			        	
			        	if (!(lastPathComponent instanceof WabitDataSource)) {
				        	objectsMenu(
				        			menu,
				        			lastPathComponent.getClass().getSimpleName(),  
				        			uuid,
				        			new JMenuItem(new EditCellAction(tree)),
				        			WabitAccessManager.Permission.MODIFY);
			        	}
			        	
			        	objectsMenu(
			        			menu,
			        			lastPathComponent.getClass().getSimpleName(),  
			        			uuid,
			        			new JMenuItem(new DeleteFromTreeAction(session.getWorkspace(), 
			        					(SPObject) lastPathComponent, context.getFrame(), context)),
			        					WabitAccessManager.Permission.DELETE);
			        	
			        }
			        
			        if (!(lastPathComponent instanceof WabitDataSource)) {
			        	objectsMenu(
			        			menu,
			        			lastPathComponent.getClass().getSimpleName(),  
			        			uuid,
			        			new JMenuItem(new CopyUuidToClipboardAction(uuid)),
			        			WabitAccessManager.Permission.EXECUTE);
			        }
			        
			        menu.addSeparator();
			        
			        boolean sep = securityMenu(menu,lastPathComponent.getClass().getSimpleName(),(SPObject) lastPathComponent);
			        sep = securityMenu(menu,lastPathComponent.getClass().getSimpleName(), null);
			        if (sep) menu.addSeparator();
			        
			        sep = false;
			        if (lastPathComponent instanceof QueryCache) {
						
						sep = objectsMenu(
								menu,
								QueryCache.class.getSimpleName(),  
								uuid,
								new JMenuItem(new AbstractAction("Stop Running", WabitIcons.STOP_16) {
									public void actionPerformed(ActionEvent e) {
										((QueryCache) lastPathComponent).cancel();
									}
								}),
								WabitAccessManager.Permission.EXECUTE);
						
					}
					
					if (lastPathComponent instanceof OlapQuery) {
						
						sep = objectsMenu(
								menu,
								OlapQuery.class.getSimpleName(),  
								uuid,
								new JMenuItem(new AbstractAction("Stop Running", WabitIcons.STOP_16) {
									public void actionPerformed(ActionEvent e) {
										((OlapQuery) lastPathComponent).cancel();
									}
								}),
								WabitAccessManager.Permission.EXECUTE);
						
					}
					
					if (sep) {
						menu.addSeparator();
					}
			    }
			    
				
				
				if (lastPathComponent instanceof WabitDataSource) {
					SPDataSource ds = ((WabitDataSource) lastPathComponent).getSPDataSource();
					if (ds instanceof JDBCDataSource) {
						NewQueryAction newQueryOnDS = new NewQueryAction(session, (JDBCDataSource) ds);
						JMenuItem newQueryItem = new JMenuItem(newQueryOnDS);
						newQueryItem.setIcon(WorkspaceTreeCellRenderer.QUERY_ICON);
						if (objectsMenu(
								menu,
								QueryCache.class.getSimpleName(),  
								null,
								newQueryItem,
								WabitAccessManager.Permission.CREATE)) {
							menu.addSeparator();
						}
					}
					if (ds instanceof Olap4jDataSource) {
						NewOLAPQueryAction newOlapQueryOnDS = new NewOLAPQueryAction(session, (Olap4jDataSource) ds);
						JMenuItem newOlapQueryItem = new JMenuItem(newOlapQueryOnDS);
						newOlapQueryItem.setIcon(WorkspaceTreeCellRenderer.OLAP_QUERY_ICON);
						if (objectsMenu(
								menu,
								OlapQuery.class.getSimpleName(),  
								null,
								newOlapQueryItem,
								WabitAccessManager.Permission.CREATE)) {
							menu.addSeparator();
						}
					}
					menu.add(new AbstractAction("Database Connection Manager...", WabitIcons.CONNECTION_16) {
						public void actionPerformed(ActionEvent e) {
							session.getDbConnectionManager().showDialog(context.getFrame());
						}
					});
					
					objectsMenu(
							menu,
							WabitDataSource.class.getSimpleName(),  
							null,
							createDataSourcesMenu(),
							WabitAccessManager.Permission.CREATE);
					
					menu.addSeparator();
					
				} else if (lastPathComponent instanceof QueryCache || lastPathComponent instanceof OlapQuery) {
					
					objectsMenu(
							menu,
							QueryCache.class.getSimpleName(),  
							null,
							newQuery,
							WabitAccessManager.Permission.CREATE);
					
					objectsMenu(
							menu,
							OlapQuery.class.getSimpleName(),  
							null,
							newOlapQuery,
							WabitAccessManager.Permission.CREATE);
					
					if (lastPathComponent instanceof QueryCache) {
						
						objectsMenu(
								menu,
								QueryCache.class.getSimpleName(),  
								null,
								new JMenuItem(new CopyQueryAction((WabitObject) lastPathComponent, session, session.getContext().getFrame())),
								WabitAccessManager.Permission.CREATE);
					
					} else if (lastPathComponent instanceof OlapQuery) {
						
						objectsMenu(
								menu,
								OlapQuery.class.getSimpleName(),  
								null,
								new JMenuItem(new CopyQueryAction((WabitObject) lastPathComponent, session, session.getContext().getFrame())),
								WabitAccessManager.Permission.CREATE);
					
					}
				
				} else if (lastPathComponent instanceof Report) {
					
					objectsMenu(
							menu,
							Report.class.getSimpleName(),  
							null,
							newReport,
							WabitAccessManager.Permission.CREATE);
					
					objectsMenu(
							menu,
							Report.class.getSimpleName(),  
							null,
							new JMenuItem(new CopyReportAction((Report) lastPathComponent, session, session.getContext().getFrame())),
							WabitAccessManager.Permission.CREATE);
					
					JMenuItem action = new JMenuItem(new ScheduleReportAction((Report) lastPathComponent, session));
					
					if (!this.session.isEnterpriseServerSession() ||
							this.session.getSystemWorkspace() == null) 
					{
						action.setEnabled(false);
					}
					objectsMenu(
							menu,
							ReportTask.class.getSimpleName(),  
							null,
							action,
							WabitAccessManager.Permission.CREATE);
					
				} else if (lastPathComponent instanceof Template) {
					JMenuItem item = new JMenuItem(new ReportFromTemplateAction(session, (Template) lastPathComponent));
					item.setIcon(WabitIcons.REPORT_ICON_16);
					
					objectsMenu(
							menu,
							Report.class.getSimpleName(),  
							null,
							item,
							WabitAccessManager.Permission.CREATE);
					
					objectsMenu(
							menu,
							Template.class.getSimpleName(),  
							null,
							newTemplate,
							WabitAccessManager.Permission.CREATE);
					
					objectsMenu(
							menu,
							Template.class.getSimpleName(),  
							null,
							new JMenuItem(new CopyTemplateAction((Template) lastPathComponent, session, session.getContext().getFrame())),
							WabitAccessManager.Permission.CREATE);
					
				} else if (lastPathComponent instanceof WabitImage) {
					
					objectsMenu(
							menu,
							WabitImage.class.getSimpleName(),  
							null,
							newImage,
							WabitAccessManager.Permission.CREATE);
					
					objectsMenu(
							menu,
							WabitImage.class.getSimpleName(),  
							null,
							new JMenuItem(new CopyImageAction((WabitImage) lastPathComponent, session, session.getContext().getFrame())),
							WabitAccessManager.Permission.CREATE);
					
				} else if (lastPathComponent instanceof ReportTask) {
					
					objectsMenu(
							menu,
							ReportTask.class.getSimpleName(),  
							null,
							newReportTask,
							WabitAccessManager.Permission.CREATE);
					
					objectsMenu(
							menu,
							WabitImage.class.getSimpleName(),  
							null,
							new JMenuItem(new CopyReportTaskAction((ReportTask) lastPathComponent, session, session.getContext().getFrame())),
							WabitAccessManager.Permission.CREATE);
				
				} else if (lastPathComponent instanceof User) {
					
					objectsMenu(
							menu,
							User.class.getSimpleName(),  
							null,
							newUser,
							WabitAccessManager.Permission.CREATE);
					
				} else if (lastPathComponent instanceof Group) {
					
					objectsMenu(
							menu,
							Group.class.getSimpleName(),  
							null,
							newGroup,
							WabitAccessManager.Permission.CREATE);
					
				} else if (lastPathComponent instanceof Chart) {
					
					objectsMenu(
							menu,
							Chart.class.getSimpleName(),  
							null,
							newChart,
							WabitAccessManager.Permission.CREATE);
					
					objectsMenu(
							menu,
							Chart.class.getSimpleName(),  
							null,
							new JMenuItem(new CopyChartAction((Chart)lastPathComponent, session, session.getContext().getFrame())),
							WabitAccessManager.Permission.CREATE);
					
				}
			}
			
			//For some bizarre reason, you cannot select a node
			//in the JTree on right-click. So the coordinates for e.getSource()
			//are different from e.getPoint().setSelectionRow(tree.getRowForLocation(e.getX(), e.getY()));
			tree.setSelectionRow(tree.getRowForLocation(e.getX(), e.getY()));
		
		} else {

			menu.add(new AbstractAction("Database Connection Manager...", WabitIcons.CONNECTION_16) {

				public void actionPerformed(ActionEvent e) {
					session.getDbConnectionManager().showDialog(context.getFrame());
				}
			});

			boolean sep = objectsMenu(
					menu,
					WabitDataSource.class.getSimpleName(),  
					null,
					createDataSourcesMenu(),
					WabitAccessManager.Permission.CREATE);
			
			if (sep) menu.addSeparator();
			
			sep = securityMenu(menu, WabitWorkspace.class.getSimpleName(), this.session.getWorkspace());
			
			sep |= securityMenu(menu, WabitWorkspace.class.getSimpleName(), null);
			
			if (sep) menu.addSeparator();
			
			if (this.session.getWorkspace().isSystemWorkspace()) {
				
				objectsMenu(
						menu,
						User.class.getSimpleName(),  
						null,
						newUser,
						WabitAccessManager.Permission.CREATE);
				
				objectsMenu(
						menu,
						Group.class.getSimpleName(),  
						null,
						newGroup,
						WabitAccessManager.Permission.CREATE);
				
			} else {
				
				objectsMenu(
						menu,
						QueryCache.class.getSimpleName(),  
						null,
						newQuery,
						WabitAccessManager.Permission.CREATE);
				
				objectsMenu(
						menu,
						OlapQuery.class.getSimpleName(),  
						null,
						newOlapQuery,
						WabitAccessManager.Permission.CREATE);
				
				objectsMenu(
						menu,
						Chart.class.getSimpleName(),  
						null,
						newChart,
						WabitAccessManager.Permission.CREATE);
				
				objectsMenu(
						menu,
						WabitImage.class.getSimpleName(),  
						null,
						newImage,
						WabitAccessManager.Permission.CREATE);
				
				objectsMenu(
						menu,
						Template.class.getSimpleName(),  
						null,
						newTemplate,
						WabitAccessManager.Permission.CREATE);
				
				objectsMenu(
						menu,
						Report.class.getSimpleName(),  
						null,
						newReport,
						WabitAccessManager.Permission.CREATE);
				
				if (this.session.isEnterpriseServerSession()) {
					objectsMenu(
							menu,
							ReportTask.class.getSimpleName(),  
							null,
							newReportTask,
							WabitAccessManager.Permission.CREATE);
				}
			}
		}
		if (!(lastPathComponent instanceof ContentBox) && 
				!(lastPathComponent instanceof Selector) &&
				!(lastPathComponent instanceof SQLObject) &&
				!(lastPathComponent instanceof Olap4jTreeObject) &&
				menu.getComponentCount() > 0) {
			// Prevent orphan separator at the end of the menu.
			if (menu.getComponent(menu.getComponentCount()-1) instanceof JPopupMenu.Separator) {
				menu.remove(menu.getComponentCount()-1);
				menu.pack();
			}
			menu.show(e.getComponent(), e.getX(), e.getY());
		}
		
	}

	
	/**
	 * Will decide if we must insert or not in the menu a given menu item
	 * based on access rights and object simple class name. 
	 * 
	 * If we are not in a server workspace, we insert all options.
	 * 
	 * @param menu The menu into which we insert if needed
	 * @param simpleName The simple class name of the WabitObject concerned 
	 * by the menu option
	 * @param subjectUuid the particular object's UUID we want to check permissions for.
	 * @param menuItem The menu item we might add to the menu.
	 * @param permissions Permissions to lookup
	 */
	private boolean objectsMenu(
			JPopupMenu menu, 
			String simpleName,
			@Nullable String subjectUuid,
			JMenuItem menuItem,
			WabitAccessManager.Permission permission) {
		
		// If we are not in a server session, we display everything.
		if (!session.isEnterpriseServerSession()) {
			menu.add(menuItem);
			return true;
		}
		
		if (this.currentUser==null) {
			this.currentUser = this.getCurrentUser();
			if (this.currentUser==null) {
				// No way to resolve this. We add all menus
				menu.add(menuItem);
				return true;
			}
		}
		
		// Init the access manager since we will need it
		if (accessManager == null) {
			accessManager = new CachingWabitAccessManager();
			accessManager.init(
					this.currentUser,
					session,
					session.getSystemWorkspace().getSession());
		}
		
		boolean add = false;
		if (subjectUuid==null) {
			if (WabitAccessManager.Permission.GRANT.equals(permission) &&
					accessManager.isGrantGranted(
						new Grant(
							subjectUuid, 
							simpleName, 
							false, 
							false, 
							false, 
							false, 
							true))) 
			{
				add = true;
			} else if (accessManager.isGranted(simpleName,EnumSet.of(permission))) {
				add = true;
			}
		} else {
			if (WabitAccessManager.Permission.GRANT.equals(permission) &&
					accessManager.isGrantGranted(
						new Grant(
							subjectUuid, 
							simpleName, 
							false, 
							false, 
							false, 
							false, 
							true))) {
				add = true;
			} else if (accessManager.isGranted(subjectUuid,simpleName,EnumSet.of(permission))) {
				add = true;
			}
		}
		
		if (!add) {
			menuItem.setEnabled(false);
		}
		
		menu.add(menuItem);
		
		return add;
	}
	
	/**
	 * Returns the current user object, null if it cannot be found
	 * or we are not on a server workspace.
	 */
    private User getCurrentUser() 
    {
    	if (!session.isEnterpriseServerSession()) {
    		return null;
    	}
    	
    	WabitWorkspace systemWorkspace = session.getSystemWorkspace();
    	if (systemWorkspace == null) {
    		return null;
    	}
    	
    	String username = ((WabitSwingSessionImpl)session).getEnterpriseServerInfos().getUsername();
    	User currentUser = null;
    	for (User user : systemWorkspace.getUsers()) {
    		if (user.getUsername().equals(username)) {
    			currentUser = user;
    			break;
    		}
    	}
    	return currentUser;
	}

	private boolean securityMenu(JPopupMenu menu, String simpleName, SPObject object) {
    	
    	WabitWorkspace systemWorkspace = this.session.getSystemWorkspace();

    	if (!this.session.isEnterpriseServerSession() ||
    			systemWorkspace == null) {
    		
    		String label = getLabel(simpleName);
    		
    		if (label == null) {
				return false;
			}
    		
    		JMenuItem action = new JMenuItem(
									new SecurityAction(
											this.session.getWorkspace(), 
											systemWorkspace, 
											object == null ? null : object.getUUID(),
											simpleName,
											object == null ? label : object.getName()));
    		action.setEnabled(false);
    		menu.add(action);

    	} else {
    	
    		if (simpleName != null && object == null) {
    			
    			String label = getLabel(simpleName);
    			if (label == null) {
    				return false;
    			}
    			
    			objectsMenu(
    					menu, 
    					simpleName,
    					null,
    					new JMenuItem(
    							new SecurityAction(
    									this.session.getWorkspace(), 
    									systemWorkspace, 
    									null,
    									simpleName,
    									label)),
    									WabitAccessManager.Permission.GRANT);
    			
    		} else if (simpleName != null && object != null) {
    			
    			if (object instanceof WabitDataSource ||
    					object instanceof QueryCache ||
    					object instanceof OlapQuery ||
    					object instanceof WabitImage ||
    					object instanceof Chart ||
    					object instanceof Report ||
    					object instanceof Template ||
    					object instanceof ReportTask) 
    			{
    				objectsMenu(
    						menu, 
    						simpleName,
    						object.getUUID(),
    						new JMenuItem(
    								new SecurityAction(
    										this.session.getWorkspace(), 
    										systemWorkspace, 
    										object.getUUID(),
    										object.getClass().getSimpleName(),
    										object.getName())),
    										WabitAccessManager.Permission.GRANT);
    				
    			} else if (object instanceof WabitWorkspace) 
    			{
    				objectsMenu(
    						menu, 
    						simpleName,
    						object.getUUID(),
    						new JMenuItem(
    								new SecurityAction(
    										this.session.getWorkspace(), 
    										systemWorkspace, 
    										object.getUUID(),
    										object.getClass().getSimpleName(),
    										object.getName().concat(" workspace"))),
    										WabitAccessManager.Permission.GRANT);
    				
    			}
    		} else {
    			throw new IllegalStateException();
    		}
    	}
		return true;
	}
	
	/**
	 * Finds a suitable label for a given SP object.
	 * Returns null if the object is not one of those that we can 
	 * apply security to.
	 */
	public String getLabel(String simpleName) {
		final String label;
		if (simpleName.equals("WabitDataSource")) {
			label = "datasources";
		} else if (simpleName.equals("QueryCache")) {
			label = "relational queries";
		} else if (simpleName.equals("OlapQuery")) {
			label = "OLAP queries";
		} else if (simpleName.equals("WabitImage")) {
			label = "images";
		} else if (simpleName.equals("Chart")) {
			label = "charts";
		} else if (simpleName.equals("Report")) {
			label = "reports";
		} else if (simpleName.equals("Template")) {
			label = "templates";
		} else if (simpleName.equals("ReportTask")) {
			label = "scheduled reports";
		} else if (simpleName.equals("User")) {
			label = "users";
		} else if (simpleName.equals("Group")) {
			label = "groups";
		} else if (simpleName.equals("WabitWorkspace")) {
			label = "all workspaces";
		} else {
			return null;
		}
		return label;
	}

	/**
     * Creates a JMenu with an item for each data source defined in the context's
     * data source collection. When one of these items is selected, it invokes an
     * action that adds that data source to the workspace. 
     */
	public JMenu createDataSourcesMenu() {
        JMenu dbcsMenu = new JMenu("Add Data Source"); //$NON-NLS-1$

        if (session.isEnterpriseServerSession()) {
        	for (SPDataSource dbcs : session.getDataSources().getConnections()) {
        		JMenuItem newMenuItem = new JMenuItem(new AddDataSourceAction(session.getWorkspace(), dbcs));
        		if (dbcs instanceof Olap4jDataSource) {
        			newMenuItem.setIcon(OLAP_DB_ICON);
        		} else if (dbcs instanceof JDBCDataSource) {
        			newMenuItem.setIcon(DB_ICON);
        		}
        		dbcsMenu.add(newMenuItem);
        	}
        } else {
        	for (SPDataSource dbcs : session.getContext().getDataSources().getConnections()) {
        		JMenuItem newMenuItem = new JMenuItem(new AddDataSourceAction(session.getWorkspace(), dbcs));
        		if (dbcs instanceof Olap4jDataSource) {
        			newMenuItem.setIcon(OLAP_DB_ICON);
        		} else if (dbcs instanceof JDBCDataSource) {
        			if (((JDBCDataSource)dbcs).getParentType().getSupportsStreamQueries()) {
        				newMenuItem.setIcon(WabitIcons.CONNECTION_STREAM_16);
        			} else {
        				newMenuItem.setIcon(DB_ICON);
        			}
        		}
        		dbcsMenu.add(newMenuItem);
        	}
        }
        SPSUtils.breakLongMenu(context.getFrame(), dbcsMenu);
        
        return dbcsMenu;
	}

}
