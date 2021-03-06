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

package ca.sqlpower.wabit;

import java.util.HashSet;
import java.util.Set;

import ca.sqlpower.object.ObjectDependentException;
import ca.sqlpower.object.SPObject;
import ca.sqlpower.object.WorkspaceGraphModel;
import ca.sqlpower.sql.JDBCDataSource;
import ca.sqlpower.sql.SPDataSource;
import ca.sqlpower.sqlobject.StubSQLDatabaseMapping;
import ca.sqlpower.testutil.StubDataSourceCollection;
import ca.sqlpower.wabit.report.ChartRenderer;
import ca.sqlpower.wabit.report.ContentBox;
import ca.sqlpower.wabit.report.Report;
import ca.sqlpower.wabit.report.ResultSetRenderer;
import ca.sqlpower.wabit.report.chart.Chart;
import ca.sqlpower.wabit.rs.olap.OlapQuery;
import ca.sqlpower.wabit.rs.query.QueryCache;
import ca.sqlpower.wabit.swingui.StubWabitSwingSession;
import ca.sqlpower.wabit.swingui.WabitSwingSession;

public class WabitWorkspaceTest extends AbstractWabitObjectTest {

    
    private WabitWorkspace workspace;

	/**
     * We are not persisting the WabitWorkspace object because it is created by
     * the session.
     */
    @Override
    public void testPersisterAddsNewObject() throws Exception {
    	//no-op
    }

	/**
	 * We are not persisting the WabitWorkspace because it is created by the
	 * session. This test therefore cannot pass.
	 */
    @Override
    public void testPersistsObjectAsChild() throws Exception {
    	//no-op
    }
    
    /**
     * The workspace cannot be persisted as a new child of a WabitObject as it is
     * the root object.
     */
    @Override
    public void testPersisterCommitCanRollbackNewChild() throws Exception {
    	//no-op
    }
    
    /**
     * The workspace cannot be removed as a new child of a WabitObject as it is
     * the root object.
     */
    @Override
    public void testPersisterCommitCanRollbackRemovedChild() throws Exception {
    	//no-op
    }
    
    @Override
    public Set<String> getPropertiesToIgnoreForPersisting() {
    	Set<String> ignored = super.getPropertiesToIgnoreForPersisting();
    	
    	//This property defines the editor and changing this would force all users of each
    	//workspace to view the same editor all the time.
    	ignored.add("editorPanelModel");
    	
    	/*
		 * XXX Because the abstractsplistener bases his transaction lookups
		 * on the workspace UUID, it will throw an exception here if we try to change
		 * the UUID of a workspace since he won't find the transaction records 
		 * in his internal maps. This is an exception we can ignore only for 
		 * WabitWorkspace and UUID property.
		 */
    	ignored.add("UUID");
    	
    	return ignored;
    }
    
    @Override
    public Set<String> getPropertiesToNotPersistOnObjectPersist() {
    	Set<String> notPersisting = super.getPropertiesToNotPersistOnObjectPersist();
    	
    	/*
		 * XXX Because the abstractsplistener bases his transaction lookups
		 * on the workspace UUID, it will throw an exception here if we try to change
		 * the UUID of a workspace since he won't find the transaction records 
		 * in his internal maps. This is an exception we can ignore only for 
		 * WabitWorkspace and UUID property.
		 */
    	notPersisting.add("UUID");
    	
    	notPersisting.add("charts");
    	notPersisting.add("connections");
    	notPersisting.add("dataSources");
    	notPersisting.add("images");
    	notPersisting.add("olapQueries");
    	notPersisting.add("queries");
    	notPersisting.add("reports");
    	notPersisting.add("session");
    	notPersisting.add("templates");
    	notPersisting.add("reportTasks");
    	
    	// These are system workspace specific children and properties
    	notPersisting.add("systemWorkspace");
    	notPersisting.add("groups");
    	notPersisting.add("users");
    	
    	// These are currently not supported.
    	notPersisting.add("dataSourceTypes");
    	notPersisting.add("serverBaseURI");
    	notPersisting.add("SQLType");
    	notPersisting.add("SQLTypes");
    	
    	return notPersisting;
    }
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.workspace = getWorkspace();
    }
    
    @Override
    public Set<String> getPropertiesToIgnoreForEvents() {
    	Set<String> ignore = super.getPropertiesToIgnoreForEvents();
        ignore.add("dataSourceTypes");
        ignore.add("serverBaseURI");
        ignore.add("mondrianServerBaseURI");
        ignore.add("session");
        ignore.add("SQLTypes");
        
        //workspace parents cannot be set as they are always null.
        ignore.add("parent");
    	return ignore;
    }
    
    @Override
    public WabitObject getObjectUnderTest() {
        return getWorkspace();
    }
    
    /**
     * Regression test for bug 1976. If a query cache is selected and is
     * removed the wabit object being edited should be changed.
     */
    public void testRemovingSelectedQueryCacheChangesSelection() throws Exception {
    	
        QueryCache query = new QueryCache(getContext());
        
        workspace.addQuery(query, getSession());
        workspace.setEditorPanelModel(query);
        assertEquals(query, workspace.getEditorPanelModel());
        
        workspace.removeChild(query);
        assertNotSame(query, workspace.getEditorPanelModel());
    }
    
    /**
     * Regression test for bug 1976. If an OLAP query is selected and is
     * removed the wabit object being edited should be changed.
     */
    public void testRemovingSelectedOlapQueryChangesSelection() throws Exception {
    	
        OlapQuery query = new OlapQuery(getContext());
        
        workspace.addOlapQuery(query);
        workspace.setEditorPanelModel(query);
        assertEquals(query, workspace.getEditorPanelModel());
        
        workspace.removeChild(query);
        assertNotSame(query, workspace.getEditorPanelModel());
    }
    
    /**
     * Regression test for bug 1976. If a layout is selected and is
     * removed the wabit object being edited should be changed.
     */
    public void testRemovingSelectedLayoutChangesSelection() throws Exception {
        Report layout = new Report("Layout");
        workspace.addReport(layout);
        workspace.setEditorPanelModel(layout);
        assertEquals(layout, workspace.getEditorPanelModel());
        
        workspace.removeChild(layout);
        assertNotSame(layout, workspace.getEditorPanelModel());
    }

    /**
     * Removing a child object that is a dependency should throw an exception
     * when the remove operation occurs.
     */
    public void testRemovingQueryWithDependency() throws Exception {
             
        QueryCache query = new QueryCache(getContext());
        
        query.setDataSource((JDBCDataSource)getSession().getDataSources().getDataSource("regression_test"));
        
        workspace.addQuery(query, getSession());
        Chart chart = new Chart();
        chart.setName("chart");
        chart.setQuery(query);
        workspace.addChart(chart);
        
        try {
            workspace.removeChild(query);
            fail("The child was removed while there was a chart dependent on it. " +
            		"Now the chart depends on an unparented object.");
        } catch (ObjectDependentException e) {
            //successfully caught the exception.
        }
    }
    
    /**
     * A simple test of merging some WabitObjects from one workspace into another.
     */
    public void testMergeIntoSession() throws Exception {
        
        QueryCache query = new QueryCache(getContext());
        
        query.setDataSource((JDBCDataSource)getSession().getDataSources().getDataSource("regression_test"));
        getWorkspace().addQuery(query, getSession());
        
        Chart chart = new Chart();
        chart.setName("chart");
        chart.setQuery(query);
        getWorkspace().addChart(chart);
        
        Report report = new Report("Report");
        getWorkspace().addReport(report);
        ContentBox chartContentBox = new ContentBox();
        chartContentBox.setContentRenderer(new ChartRenderer(chart));
        report.getPage().addContentBox(chartContentBox);
        ContentBox queryContentBox = new ContentBox();
        queryContentBox.setContentRenderer(new ResultSetRenderer(query));
        report.getPage().addContentBox(queryContentBox);
        
        WabitSwingSession finishingSession = new StubWabitSwingSession();
        WabitWorkspace finishingWorkspace = new WabitWorkspace();
        finishingWorkspace.setSession(finishingSession);
        
        assertEquals(3, getWorkspace().getChildren().size());
        assertTrue(getWorkspace().getChildren().contains(query));
        assertTrue(getWorkspace().getChildren().contains(chart));
        assertTrue(getWorkspace().getChildren().contains(report));
        assertEquals(0, finishingWorkspace.getChildren().size());
        
        getWorkspace().mergeIntoWorkspace(finishingWorkspace);
        
        assertEquals(0, getWorkspace().getChildren().size());
        assertEquals(3, finishingWorkspace.getChildren().size());
        assertTrue(finishingWorkspace.getChildren().contains(query));
        assertTrue(finishingWorkspace.getChildren().contains(chart));
        assertTrue(finishingWorkspace.getChildren().contains(report));
    }
    
    /**
     * A test of merging some WabitObjects from one workspace into another changes
     * the UUIDs. This prevents the workspace from gaining duplicate UUIDs.
     */
    public void testMergingUpdatesUUIDs() throws Exception {
        
        Set<String> uniqueUUIDs = new HashSet<String>() {
            @Override
            public boolean add(String o) {
                if (contains(o)) {
                    fail("The uuid " + o + " already exists " + "and is therefore not unique.");
                }
                return super.add(o);
            }
        };
        
        QueryCache query = new QueryCache(getContext());
        query.setDataSource((JDBCDataSource)getSession().getDataSources().getDataSource("regression_test"));
        getWorkspace().addQuery(query, getSession());
        
        Chart chart = new Chart();
        chart.setName("chart");
        chart.setQuery(query);
        getWorkspace().addChart(chart);
        Report report = new Report("Report");
        getWorkspace().addReport(report);
        ContentBox chartContentBox = new ContentBox();
        final ChartRenderer chartContentRenderer = new ChartRenderer(chart);
        chartContentBox.setContentRenderer(chartContentRenderer);
        report.getPage().addContentBox(chartContentBox);
        ContentBox queryContentBox = new ContentBox();
        final ResultSetRenderer resultSetContentRenderer = new ResultSetRenderer(query);
        queryContentBox.setContentRenderer(resultSetContentRenderer);
        report.getPage().addContentBox(queryContentBox);
        
        WorkspaceGraphModel graph = new WorkspaceGraphModel(getWorkspace(), 
        		getWorkspace(), false, false);
        for (SPObject o : graph.getNodes()) {
            System.out.println("Adding object of type " + o.getClass() + " with UUID " + o.getUUID());
            uniqueUUIDs.add(o.getUUID());
        }
        
        WabitSwingSession finishingSession = new StubWabitSwingSession();
        WabitWorkspace finishingWorkspace = new WabitWorkspace();
        finishingWorkspace.setSession(finishingSession);
        
        assertEquals(3, getWorkspace().getChildren().size());
        assertTrue(getWorkspace().getChildren().contains(query));
        assertTrue(getWorkspace().getChildren().contains(chart));
        assertTrue(getWorkspace().getChildren().contains(report));
        assertEquals(0, finishingWorkspace.getChildren().size());
        
        getWorkspace().mergeIntoWorkspace(finishingWorkspace);
        
        assertEquals(0, getWorkspace().getChildren().size());
        assertEquals(3, finishingWorkspace.getChildren().size());
        assertTrue(finishingWorkspace.getChildren().contains(query));
        assertTrue(finishingWorkspace.getChildren().contains(chart));
        assertTrue(finishingWorkspace.getChildren().contains(report));
        
        WorkspaceGraphModel endGraph = new WorkspaceGraphModel(finishingWorkspace, 
                finishingWorkspace, false, false);
        for (SPObject o : endGraph.getNodes()) {
        	if (!(o instanceof WabitDataSource) ||
        			((WabitDataSource)o).getName().equals("regression_test") == false) {
        		uniqueUUIDs.add(o.getUUID());
        	}
        }
        
    }
    
    /**
     * Test a query can be added to and removed from a workspace with the
     * addChild method. Also checks that the index is correct.
     * @throws Exception
     */
    public void testAddAndRemoveQuery() throws Exception {
        final JDBCDataSource spds = new JDBCDataSource(new StubDataSourceCollection<SPDataSource>());
        spds.setName("ds");
        WabitDataSource ds = new WabitDataSource(spds);
        workspace.addDataSource(ds);
        
        QueryCache q = new QueryCache(new StubSQLDatabaseMapping());
        q.setName("query");
        
        workspace.addChild(q, 0);
        
        assertTrue(workspace.getChildren().contains(q));
        
        workspace.removeChild(q);
        
        assertFalse(workspace.getChildren().contains(q));
    }

}
