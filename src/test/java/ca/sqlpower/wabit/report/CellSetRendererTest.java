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

package ca.sqlpower.wabit.report;

import java.util.Set;

import ca.sqlpower.wabit.AbstractWabitObjectTest;
import ca.sqlpower.wabit.WabitObject;
import ca.sqlpower.wabit.rs.olap.OlapQuery;
import ca.sqlpower.wabit.util.StubOlapConnectionMapping;

public class CellSetRendererTest extends AbstractWabitObjectTest {
    
    private OlapQuery query;
    private CellSetRenderer renderer;
    
    public Set<String> getPropertiesToIgnoreForEvents() {
    	Set<String> ignored = super.getPropertiesToIgnoreForEvents();
    	ignored.add("modifiedOlapQuery");
    	ignored.add("cellSet");
    	ignored.add("selectedMember");
    	return ignored;
    };
    
    @Override
    public Class<? extends WabitObject> getParentClass() {
    	return ContentBox.class;
    }
    
    @Override
    public Set<String> getPropertiesToNotPersistOnObjectPersist() {
    	Set<String> ignorable = super.getPropertiesToNotPersistOnObjectPersist();
    	ignorable.add("cellSet");
    	ignorable.add("errorMessage");
    	
    	//Child of this thing.
    	ignorable.add("modifiedOlapQuery");
    	
    	//These properties are only defined when a user hovers over a member with their mouse.
    	ignorable.add("memberSelectedAtPoint");
    	ignorable.add("selectedMember");
    	
    	//This is always null, there is no actual field for this.
    	ignorable.add("backgroundColour");
    	
    	return ignorable;
    }
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        query = new OlapQuery(new StubOlapConnectionMapping());
        query.setName("New query");
        getWorkspace().addOlapQuery(query);
        renderer = new CellSetRenderer(query);
        renderer.setName("New renderer");
        
        ContentBox contentBox = new ContentBox();
        contentBox.setName("contentbox");
        contentBox.setContentRenderer(renderer);
        Report report = new Report("report");
        report.getPage().addContentBox(contentBox);
        
        getWorkspace().addReport(report);
        
    }

    @Override
    public WabitObject getObjectUnderTest() {
        return renderer;
    }

}
