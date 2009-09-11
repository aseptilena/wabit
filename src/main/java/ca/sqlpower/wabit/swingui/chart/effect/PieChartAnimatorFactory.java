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

package ca.sqlpower.wabit.swingui.chart.effect;

import java.io.ObjectInputStream.GetField;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.MultiplePiePlot;
import org.jfree.data.Range;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;

public class PieChartAnimatorFactory extends AbstractChartAnimatorFactory {

    public boolean canAnimate(JFreeChart chart) {
        if (!(chart.getPlot() instanceof MultiplePiePlot)) {
            return false;
        }
        MultiplePiePlot mpplot = (MultiplePiePlot) chart.getPlot();
        CategoryDataset dataset = mpplot.getDataset();
        if (!(dataset instanceof DefaultCategoryDataset)) {
            return false;
        }
        return true;
    }

    public ChartAnimator createAnimator(JFreeChart chart) throws CantAnimateException {
        MultiplePiePlot mpplot;
        if (chart.getPlot() instanceof MultiplePiePlot) {
            mpplot = (MultiplePiePlot) chart.getPlot();
        } else {
            throw new CantAnimateException(
                    "This animator only works with MultiplePiePlot. " +
                    "You gave me " + chart.getPlot());
        }
        
        DefaultCategoryDataset dataset;
        if (mpplot.getDataset() instanceof DefaultCategoryDataset) {
            dataset = (DefaultCategoryDataset) mpplot.getDataset();
        } else {
            throw new CantAnimateException(
                    "This animator only works with DefaultCategoryDataset. " +
                    "You gave me " + mpplot.getDataset());
        }

        return new PieChartAnimator(mpplot, getFrameDelay(), getFrameCount());
    }

}
