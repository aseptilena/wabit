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

package ca.sqlpower.wabit.swingui.olap.action;

import org.olap4j.Axis;
import org.olap4j.metadata.Hierarchy;

import ca.sqlpower.wabit.rs.olap.OlapQuery;
import ca.sqlpower.wabit.rs.olap.QueryInitializationException;
import ca.sqlpower.wabit.swingui.WabitSwingSession;

public class RemoveHierarchyAction extends OlapQueryAction {

    private Hierarchy hierarchy;
    private Axis axis;

    public RemoveHierarchyAction(WabitSwingSession session, OlapQuery query, Hierarchy hierarchy, Axis axis) {
        super(session, query, "Remove Hierarchy '" + hierarchy.getName() + "'");
        this.hierarchy = hierarchy;
        this.axis = axis;
    }

	@Override
	protected void performOlapQueryAction(OlapQuery query)
	    throws QueryInitializationException {
	    query.removeHierarchy(hierarchy, axis);
	}
}
