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

package ca.sqlpower.wabit.dao;

import java.awt.Font;
import java.awt.Image;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.olap4j.Axis;

import ca.sqlpower.dao.PersisterUtils;
import ca.sqlpower.dao.session.DateConverter;
import ca.sqlpower.graph.DepthFirstSearch;
import ca.sqlpower.object.SPObject;
import ca.sqlpower.object.WorkspaceGraphModel;
import ca.sqlpower.object.WorkspaceGraphModelEdge;
import ca.sqlpower.query.Container;
import ca.sqlpower.query.Item;
import ca.sqlpower.query.Query;
import ca.sqlpower.query.SQLJoin;
import ca.sqlpower.query.TableContainer;
import ca.sqlpower.util.SQLPowerUtils;
import ca.sqlpower.util.Version;
import ca.sqlpower.wabit.WabitDataSource;
import ca.sqlpower.wabit.WabitObject;
import ca.sqlpower.wabit.WabitSessionContext;
import ca.sqlpower.wabit.WabitVersion;
import ca.sqlpower.wabit.WabitWorkspace;
import ca.sqlpower.wabit.image.WabitImage;
import ca.sqlpower.wabit.report.CellSetRenderer;
import ca.sqlpower.wabit.report.ChartRenderer;
import ca.sqlpower.wabit.report.ColumnInfo;
import ca.sqlpower.wabit.report.ContentBox;
import ca.sqlpower.wabit.report.Guide;
import ca.sqlpower.wabit.report.ImageRenderer;
import ca.sqlpower.wabit.report.WabitLabel;
import ca.sqlpower.wabit.report.Layout;
import ca.sqlpower.wabit.report.Page;
import ca.sqlpower.wabit.report.Report;
import ca.sqlpower.wabit.report.ResultSetRenderer;
import ca.sqlpower.wabit.report.Template;
import ca.sqlpower.wabit.report.chart.Chart;
import ca.sqlpower.wabit.report.chart.ChartColumn;
import ca.sqlpower.wabit.report.selectors.ComboBoxSelector;
import ca.sqlpower.wabit.report.selectors.DateSelector;
import ca.sqlpower.wabit.report.selectors.Selector;
import ca.sqlpower.wabit.report.selectors.TextBoxSelector;
import ca.sqlpower.wabit.rs.olap.OlapQuery;
import ca.sqlpower.wabit.rs.olap.QueryInitializationException;
import ca.sqlpower.wabit.rs.olap.WabitOlapAxis;
import ca.sqlpower.wabit.rs.olap.WabitOlapDimension;
import ca.sqlpower.wabit.rs.olap.WabitOlapExclusion;
import ca.sqlpower.wabit.rs.olap.WabitOlapInclusion;
import ca.sqlpower.wabit.rs.query.QueryCache;
import ca.sqlpower.xml.XMLHelper;

public class WorkspaceXMLDAO {
	
	private static final Logger logger = Logger.getLogger(WorkspaceXMLDAO.class);

	/**
	 * The version number to put in exported files. This is not the Wabit version
	 * number; it is the version of the file format itself. It is common for the
	 * version number to change independent of Wabit releases, and this is especially
	 * important for those using the continuous integration builds to do real work.
	 * <p>
     * This is also the version we support reading.
     * <p>
     * Forward compatibility policy: It will be allowable to read files with a
     * newer version, as long as the major and minor version numbers are the
     * same as the supported version. For example, if the supported version is
     * 2.3.4, we can read 2.3.5 and 2.3.455, but not 2.4.0.
     * <p>
     * Backward compatibility policy: we can read older files that have the same
     * major version number and the supported minor version or less. There is no
     * compatibility between major versions.
	 * 
     * <h2>VERSION CHANGE HISTORY</h2>
     * 
     * <dl>
     *  <dt>1.0.0 <dd>initial version. lots of changes. (too many!)
     *  <dt>1.0.1 <dd>adds page orientation attribute
     *  <dt>1.0.2 <dd>update to the chart column identifier, they are now objects instead of just column names
     *  <dt>1.0.3 <dd>Added more info to saved queries inside a report definition.
     *  <dt>1.1.0 <dd>OLAP query syntax has changed, both inside the datasources definition and the report.
     *  <dt>1.1.1 <dd>OLAP query syntax has changed for reports, -report tag was removed.
     *  <dt>1.1.2 <dd>Added exclusions when saving an OLAP query
     *  <dt>1.1.3 <dd>Changed how images are being saved. There is now a wabit-image section for each {@link WabitImage}.
     *  <dt>1.1.4 <dd>Added two flags to the query cache to save if a user should be prompted each time the query
     *  is executed with a missing join, and if the user is not being prompted, if the query should be automatically
     *  executed.
     *  <dt>1.1.5 <dd>Merged the data type and the x axis identifier into the column identifier itself.
     *  This removes the graph-name-to-data-type and graph-series-col-to-x-axis-col tags.
     *  <dt>1.2.0 <dd>Pulled up charts to top level workspace objects; took the opportunity
     *                to rename all the tags that had "graph" in their name to use "chart"
     *                instead. This change is not backward compatible; all charts in reports will
     *                be lost.
     *  <dt>1.2.1 <dd>Moved the text in user-defined queries and the text in report labels
     *                into their own text tag to preserve newline characters.
     *  <dt>1.2.2 <dd>Added the horizontal and vertical alignment properties to the image renderer.
     *  <dt>1.2.3 <dd>Added gratuitous animation flag to charts
     *  <dt>1.2.4 <dd>Put UUID attribute on every WabitObject element (especially workspace)
     *  <dt>1.2.5 <dd>Put UUID and name (mostly unused) attribute on OlapQuery child elements
     *  <dt>1.2.6 <dd>Saves the grand-totals attribute of RS renderers
     *  <dt>1.2.7 <dd>Saves the chart's auto axis values and report selectors.
     *  <dt>1.2.8 <dd>Saves the date report selectors.
     *  <dt>1.2.9 <dd>Adds page-breaking sections and colors for rs headers and data.
     *  
     * </dl> 
     * <!--Please update version number (below) if you updated the version documentation.-->
	 */
	//                                         UPDATE HISTORY!!!!!
    static final Version FILE_VERSION = new Version("1.2.9"); // please update version history (above) when you change this
    //                                         UPDATE HISTORY!!??!

    /**
	 * This output stream will be used to  write the workspace to a file.
	 */
	private final PrintWriter out;
	
	/**
	 * This XML helper will do the formatting and outputting of the XML that
	 * creates our save file.
	 */
	private final XMLHelper xml;

	/**
	 * This is the context that contains objects that require saving.
	 */
	private final WabitSessionContext context;

    private final Comparator<WabitObject> wabitObjectComparator = new WabitObjectComparator();
	
	/**
	 * This will construct a XML DAO to save the entire workspace or parts of 
	 * the workspace to be loaded in later.
	 */
	public WorkspaceXMLDAO(OutputStream out, WabitSessionContext context) {
	    this.context = context;
		try {
            this.out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(out, "UTF-8")));
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError("The UTF-8 encoding should always be supported.");
        }
		xml = new XMLHelper();
	}
	
	/**
	 * Constructs an XML DAO to print the XML representation of this Workspace
	 * to the given Writer object.
	 * 
	 * @param writer
	 *            The Writer object to output the Workspace to
	 * @param context
	 *            The context that contains the Workspace to be saved
	 */
	public WorkspaceXMLDAO(Writer writer, WabitSessionContext context) {
		this.context = context;
		this.out = new PrintWriter(new BufferedWriter(writer));
		xml = new XMLHelper();
	}
	
	public void saveActiveWorkspace() {
		save(Collections.singletonList(context.getActiveSession().getWorkspace()));
	}

    /**
     * This method is used to export parts of Wabit as well as save the entire
     * set of workspaces.
     * 
     * @param objectToSave
     *            The object that is to save. If this is an object in a
     *            workspace all of the necessary data sources and other
     *            WabitObjects will be saved with it.
     */
	public void save(List<? extends WabitObject> objectToSave) {
		xml.println(out, "<?xml version='1.0' encoding='UTF-8'?>");
		xml.println(out, "");
		xml.println(out, "<wabit export-format=\"" + FILE_VERSION + "\" wabit-app-version=\"" + WabitVersion.VERSION + "\">");
		xml.indent++;
		
		Map<WabitWorkspace, List<WabitObject>> workspaceToDependencies = new HashMap<WabitWorkspace, List<WabitObject>>();
		
		for (WabitObject savingObject : objectToSave) {
		    WabitObject parentWorkspace = savingObject;
		    while (!(parentWorkspace instanceof WabitWorkspace)) {
		        parentWorkspace = parentWorkspace.getParent();
		    }
		    
		    List<WabitObject> workspaceDependencies = workspaceToDependencies.get(parentWorkspace);
		    if (workspaceDependencies == null) {
		        workspaceDependencies = new ArrayList<WabitObject>();
		        workspaceToDependencies.put((WabitWorkspace) parentWorkspace, workspaceDependencies);
		    }
		    
		    DepthFirstSearch<SPObject, WorkspaceGraphModelEdge> dfs = new DepthFirstSearch<SPObject, WorkspaceGraphModelEdge>();
		    dfs.performSearch(new WorkspaceGraphModel(parentWorkspace, savingObject, false, false));
		    List<SPObject> dependenciesToSave = dfs.getFinishOrder();
		    for (SPObject object : dependenciesToSave) {
		        if (!workspaceDependencies.contains(object)) {
		            workspaceDependencies.add((WabitObject) object);
		        }
		    }
		}
		
		for (List<WabitObject> dependenciesToSave : workspaceToDependencies.values()) {
		    Collections.sort(dependenciesToSave, wabitObjectComparator);
		}

		for (Map.Entry<WabitWorkspace, List<WabitObject>> entry : workspaceToDependencies.entrySet()) {
		    WabitWorkspace workspace = entry.getKey();
		    List<WabitObject> dependenciesToSave = entry.getValue();
		    xml.print(out, "<project");
		    printCommonAttributes(workspace);
		    if (workspace.getEditorPanelModel() != null) {
		        printAttribute("editorPanelModel", workspace.getEditorPanelModel().getUUID());
		    }
		    xml.niprintln(out, ">");
		    xml.indent++;


		    List<WabitDataSource> dataSources = new ArrayList<WabitDataSource>();
		    for (WabitObject wabitObject : dependenciesToSave) {
		        if (wabitObject instanceof WabitDataSource) {
		            dataSources.add((WabitDataSource) wabitObject);
		        }
		    }

		    saveDataSources(dataSources);

		    for (WabitObject wabitObject : dependenciesToSave) {
		        if (wabitObject instanceof QueryCache) {
		            saveQueryCache((QueryCache) wabitObject);
		        } else if (wabitObject instanceof OlapQuery) {
		            saveOlapQuery((OlapQuery) wabitObject);
		        } else if (wabitObject instanceof Layout) {
		        	saveLayout((Layout) wabitObject);
                } else if (wabitObject instanceof WabitImage) {
                    saveWabitImage((WabitImage) wabitObject);
                } else if (wabitObject instanceof Chart) {
                    saveChart((Chart) wabitObject);
		        } else {
		            logger.info("Not saving wabit object " + wabitObject.getName() + " of type " + wabitObject.getClass() + " as it should be saved elsewhere.");
		        }
		    }

		    xml.indent--;
		    xml.println(out, "</project>");
		}
		
		xml.indent--;
		xml.println(out, "</wabit>");
		out.flush();
		out.close();
		logger.debug("Saving complete");
	}

	private void saveDataSources(List<WabitDataSource> dataSources) {
		xml.println(out, "<data-sources>");
		xml.indent++;
		
		for (WabitDataSource ds : dataSources) {
			if (ds == null) {
				continue;
			}
			xml.print(out, "<data-source");
			printCommonAttributes(ds);
			xml.niprintln(out, "/>");
		}
		
		xml.indent--;
		xml.println(out, "</data-sources>");
	}
	
	/**
	 * This saves a layout. This will not close the print writer passed into the constructor.
	 * If this save method is used to export the query cache somewhere then close should be 
	 * called on it to flush the print writer and close it.
	 */
	private void saveLayout(Layout layout) {
		xml.print(out, "<layout");
		printCommonAttributes(layout);
		printAttribute("zoom", layout.getZoomLevel());
		printAttribute("template", (layout instanceof Template));
		xml.niprintln(out, ">");
		xml.indent++;
		
		Page page = layout.getPage();
		xml.print(out, "<layout-page");
		printCommonAttributes(page);
		printAttribute("height", page.getHeight());
		printAttribute("width", page.getWidth());
		printAttribute("orientation", page.getOrientation().name());
		xml.niprintln(out, ">");
		xml.indent++;
		saveFont(page.getDefaultFont());
		
		if (layout instanceof Report) {
			for (Selector selector : ((Report) layout).getSelectors()) {
				saveSelector(selector);
			}
		}
		
		for (WabitObject object : page.getChildren()) {
			if (object instanceof ContentBox) {
				ContentBox box = (ContentBox) object;
				xml.print(out, "<content-box");
				printCommonAttributes(box);
				printAttribute("width", box.getWidth());
				printAttribute("height", box.getHeight());
				printAttribute("xpos", box.getX());
				printAttribute("ypos", box.getY());
				xml.niprintln(out, ">");
				xml.indent++;
				saveFont(box.getFont());
				
				// Save ContentBox selectors.
				for (Selector selector : box.getChildren(Selector.class)) {
					saveSelector(selector);
				}
				
				if (box.getContentRenderer() != null) {
					if (box.getContentRenderer() instanceof WabitLabel) {
						WabitLabel label = (WabitLabel) box.getContentRenderer();
						xml.print(out, "<content-label");
						printCommonAttributes(label);
						printAttribute("horizontal-align", label.getHorizontalAlignment().name());
						printAttribute("vertical-align", label.getVerticalAlignment().name());
						if (label.getBackgroundColour() != null) {
							printAttribute("bg-colour", label.getBackgroundColour().getRGB());
						}
						xml.niprintln(out, ">");
						xml.indent++;
						xml.print(out, "<text>");
						xml.niprint(out, SQLPowerUtils.escapeXML(label.getText()));
						xml.niprintln(out, "</text>");
						saveFont(label.getFont());
						xml.indent--;
						xml.println(out, "</content-label>");
					} else if (box.getContentRenderer() instanceof ResultSetRenderer) {
						ResultSetRenderer rsRenderer = (ResultSetRenderer) box.getContentRenderer();
						xml.print(out, "<content-result-set");
						printCommonAttributes(rsRenderer);
						printAttribute("query-id", rsRenderer.getContent().getUUID());
						printAttribute("null-string", rsRenderer.getNullString());
						printAttribute("border", rsRenderer.getBorderType().name());
						printAttribute("grand-totals", rsRenderer.isPrintingGrandTotals());
						if (rsRenderer.getBackgroundColour() != null) {
							printAttribute("bg-colour", rsRenderer.getBackgroundColour().getRGB());
						}
						printAttribute("header-colour", rsRenderer.getHeaderColour().getRGB());
						printAttribute("data-colour", rsRenderer.getDataColour().getRGB());
						xml.niprintln(out, ">");
						xml.indent++;
						saveFont(rsRenderer.getHeaderFont(), "header-font");
						saveFont(rsRenderer.getBodyFont(), "body-font");
						for (WabitObject rendererChild : rsRenderer.getChildren()) {
							ColumnInfo ci = (ColumnInfo) rendererChild;
							xml.print(out, "<column-info");
							printCommonAttributes(ci);
							printAttribute("width", ci.getWidth());
							if (ci.getColumnInfoItem() != null) {
								printAttribute("column-info-item-id", ci.getColumnInfoItem().getUUID());
							}
							printAttribute("column-alias", ci.getColumnAlias());
							printAttribute("horizontal-align", ci.getHorizontalAlignment().name());
							printAttribute("data-type", ci.getDataType().name());
							printAttribute("group-or-break", ci.getWillGroupOrBreak().name());
							printAttribute("will-subtotal", Boolean.toString(ci.getWillSubtotal()));
							xml.niprintln(out, ">");
							xml.indent++;
							if (ci.getFormat() instanceof SimpleDateFormat) {
								xml.print(out, "<date-format");
								SimpleDateFormat dateFormat = (SimpleDateFormat) ci.getFormat();
								printAttribute("format", dateFormat.toPattern());
								xml.niprintln(out, "/>");
							} else if (ci.getFormat() instanceof DecimalFormat) {
								xml.print(out, "<decimal-format");
								DecimalFormat decimalFormat = (DecimalFormat) ci.getFormat();
								printAttribute("format", decimalFormat.toPattern());
								xml.niprintln(out, "/>");
							} else if (ci.getFormat() == null) {
								// This is a default format
							} else {
								throw new ClassCastException("Cannot cast format of type " + ci.getFormat().getClass() + " to a known format type when saving.");
							}
							xml.indent--;
							xml.println(out, "</column-info>");
						}
						xml.indent--;
						xml.println(out, "</content-result-set>");
					} else if (box.getContentRenderer() instanceof ImageRenderer) {
						ImageRenderer imgRenderer = (ImageRenderer) box.getContentRenderer();
						xml.print(out, "<image-renderer");
						printCommonAttributes(imgRenderer);
						if (imgRenderer.getImage() != null) {
						    printAttribute("wabit-image-uuid", imgRenderer.getImage().getUUID());
						}
						printAttribute("preserving-aspect-ratio", 
								imgRenderer.isPreservingAspectRatio());
						printAttribute("h-align", imgRenderer.getHAlign().name());
						printAttribute("v-align", imgRenderer.getVAlign().name());
						xml.niprint(out, ">");
						out.println("</image-renderer>");
						
					} else if (box.getContentRenderer() instanceof ChartRenderer) {
						ChartRenderer chartRenderer = (ChartRenderer) box.getContentRenderer();
						xml.print(out, "<chart-renderer");
						printCommonAttributes(chartRenderer);
						printAttribute("chart-uuid", chartRenderer.getContent().getUUID());
						xml.println(out, " />");
						
					} else if (box.getContentRenderer() instanceof CellSetRenderer) {
					    CellSetRenderer renderer = (CellSetRenderer) box.getContentRenderer();
					    xml.print(out, "<cell-set-renderer");
					    printCommonAttributes(renderer);
					    printAttribute("olap-query-uuid", renderer.getContent().getUUID());
					    printAttribute("body-alignment", renderer.getBodyAlignment().toString());
					    if (renderer.getBodyFormat() != null) {
					        printAttribute("body-format-pattern", renderer.getBodyFormat().toPattern());
					    }
					    xml.println(out, ">");
					    xml.indent++;
					    
					    saveFont(renderer.getHeaderFont(), "olap-header-font");
					    saveFont(renderer.getBodyFont(), "olap-body-font");
				        
					    this.saveOlapQuery(renderer.getModifiedOlapQuery());
					    
					    xml.indent--;
					    xml.println(out, "</cell-set-renderer>");
					    
					} else {
						throw new ClassCastException("Cannot save a content renderer of class " + box.getContentRenderer().getClass());
					}
				}
				
				xml.indent--;
				xml.println(out, "</content-box>");
			} else if (object instanceof Guide) {
				Guide guide = (Guide) object;
				xml.print(out, "<guide");
				printCommonAttributes(guide);
				printAttribute("axis", guide.getAxis().name());
				printAttribute("offset", guide.getOffset());
				xml.niprintln(out, "/>");
			} else {
				throw new ClassCastException("Cannot save page element of type " + object.getClass());
			}
		}
		
		xml.indent--;
		xml.println(out, "</layout-page>");
		
		xml.indent--;
		xml.println(out, "</layout>");
	}
	
	
	private void saveSelector(Selector selector) {
		
		xml.print(out, "<selector");
        printCommonAttributes(selector);
		
        printAttribute("type", selector.getClass().getSimpleName());
        
        xml.niprintln(out, ">");
        xml.indent++;
        
        xml.print(out, "<selector-config");
        if (selector instanceof ComboBoxSelector) {
        	printAttribute("sourceKey", ((ComboBoxSelector) selector).getSourceKey());
        	printAttribute("staticValues", ((ComboBoxSelector) selector).getStaticValues());
        	printAttribute("defaultValue", ((ComboBoxSelector) selector).getDefaultValue());
        	printAttribute("alwaysIncludeDefaultValue", ((ComboBoxSelector) selector).isAlwaysIncludeDefaultValue());
        } else if (selector instanceof TextBoxSelector) {
        	printAttribute("defaultValue", ((TextBoxSelector) selector).getDefaultValue());
        } else if (selector instanceof DateSelector) {
        	Date defaultValue = (Date)((DateSelector) selector).getDefaultValue();
        	if (defaultValue != null) {
        		printAttribute(
        				"defaultValue", 
        				new DateConverter().convertToSimpleType(
        						(Date)defaultValue));
        	}
        }
        xml.niprintln(out, "/>");
        
        xml.indent--;
        xml.niprintln(out, "</selector>");
	}
	
	
	private void saveChart(Chart chart) {
	    xml.print(out, "<chart");
        printCommonAttributes(chart);
        printAttribute("y-axis-name", chart.getYaxisName());
        printAttribute("x-axis-name", chart.getXaxisName());
        printAttribute("x-axis-label-rotation", chart.getXAxisLabelRotation());
        printAttribute("gratuitous-animation", chart.isGratuitouslyAnimated());
        
        printAttribute("auto-x-axis", chart.isAutoXAxisRange());
        printAttribute("auto-y-axis", chart.isAutoYAxisRange());
        
        printAttribute("x-axis-max", chart.getXAxisMaxRange());
        printAttribute("y-axis-max", chart.getYAxisMaxRange());
        printAttribute("x-axis-min", chart.getXAxisMinRange());
        printAttribute("y-axis-min", chart.getYAxisMinRange());
        
        if (chart.getType() != null) {
            printAttribute("type", chart.getType().name());
        }
        if (chart.getLegendPosition() != null) {
            printAttribute("legend-position", chart.getLegendPosition().name());
        }
        if (chart.getQuery() != null) {
            printAttribute("query-id", chart.getQuery().getUUID());
        }
        xml.niprintln(out, ">");
        xml.indent++;
        for (ChartColumn col : chart.getColumns()) {
            saveChartColumn(col);
        }
        xml.println(out, "<missing-columns>");
        xml.indent++;
        for (ChartColumn missingCol : chart.getMissingIdentifiers()) {
            saveChartColumn(missingCol);
        }
        xml.indent--;
        xml.println(out, "</missing-columns>");

        xml.indent--;
        xml.println(out, "</chart>");
	}

    private void saveChartColumn(ChartColumn col) {
        xml.print(out, "<chart-column");
        printCommonAttributes(col);
        printAttribute("data-type", col.getDataType().name());
        printAttribute("role", col.getRoleInChart().name());
        saveColumnIdentifier(out, col.getXAxisIdentifier(), "x-axis-");
        xml.niprintln(out, "/>");
    }
	
	private void saveWabitImage(WabitImage wabitImage) {
	    xml.print(out, "<wabit-image");
        printCommonAttributes(wabitImage);
        xml.niprint(out, ">");
        xml.indent++;
	    
	    final Image wabitInnerImage = wabitImage.getImage();
	    if (wabitInnerImage != null) {
	    	ByteArrayOutputStream byteStream = PersisterUtils.convertImageToStreamAsPNG(wabitInnerImage);
	    	out.flush();
	    	byte[] byteArray = new Base64().encode(byteStream.toByteArray());
	    	logger.debug("Encoded length is " + byteArray.length);
	    	logger.debug("Stream has byte array " + Arrays.toString(byteStream.toByteArray()));
	    	for (int i = 0; i < byteArray.length; i++) {
	    		out.write((char)byteArray[i]);
	    		if (i % 60 == 59) {
	    			out.write("\n");
	    		}
	    	}
	    }
        
        xml.indent--;
        xml.println(out, "</wabit-image>");
	}

    /**
     * This is a helper method for saving charts, for saving ChartColumns. Only
     * saves the {@link ChartColumn} as an attribute of the current xml element
     * it is in, not as an entirely new element. A prefix to each of the values
     * can be given to distinguish between column identifiers in cases where
     * multiples are saved in one element.
     */
    private void saveColumnIdentifier(PrintWriter out, ChartColumn col, String namePrefix) {
        if (col == null) return;
        printAttribute(namePrefix + "name", col.getColumnName());
        printAttribute(namePrefix + "data-type", col.getDataType().name());
    }
	
    /**
     * This method will save an {@link OlapQuery}
     * 
     * @param query
     *      The {@link OlapQuery} to save
     * @param name
     *      A unique name to append to the start of XML tags
     */
	private void saveOlapQuery(OlapQuery query) {
		query.updateAttributes();
		
	    xml.print(out, "<olap-query");
        printCommonAttributes(query);
        if (query.getOlapDataSource() != null) {
            printAttribute("data-source", query.getOlapDataSource().getName());
        }
        xml.niprintln(out, ">");
        xml.indent++;
        
        if (query.getCurrentCube()==null ||
                query.getCurrentCube().getSchema()==null ||
                query.getCurrentCube().getSchema().getCatalog()==null) {
        	// XXX In order to save OLAP queries properly, we need to init the query
        	try {
				query.init();
			} catch (QueryInitializationException e) {
				throw new RuntimeException(e);
			}
        }
        xml.print(out, "<olap-cube");
        printAttribute("catalog", query.getCatalogName());
        printAttribute("schema", query.getSchemaName());
        printAttribute("cube-name", query.getCubeName()); //XXX This does not use it's unique name to look up the cube but instead just the name, don't use unique name or it won't find the cube.
        xml.niprintln(out, "/>");
        
        xml.print(out, "<olap4j-query");
        printAttribute("name", query.getQueryName());
        xml.niprintln(out, ">");
        xml.indent++;
        
        for (WabitOlapAxis axis : query.getAxes()) {
        	saveOlapAxis(axis);
        }
        
        xml.indent--;
        xml.println(out, "</olap4j-query>");
        
        xml.indent--;
	    xml.println(out, "</olap-query>");
	}
	
	private void saveOlapAxis(WabitOlapAxis axis) {
		xml.print(out, "<olap4j-axis");
		printCommonAttributes(axis);
		printAttribute("ordinal", axis.getOrdinal().axisOrdinal());
		
		if (axis.getOrdinal() == Axis.ROWS) {
            printAttribute("non-empty", axis.isNonEmpty());
        }
        if (axis.getSortOrder() != null) {
        	printAttribute("sort-order", axis.getSortOrder());
        	printAttribute("sort-evaluation-literal", axis.getSortEvaluationLiteral());
        }
        xml.niprintln(out, ">");
        xml.indent++;
        
        for (WabitOlapDimension dimension : axis.getDimensions()) {
        	saveOlapDimension(dimension);
        }
        
        xml.indent--;
        xml.println(out, "</olap4j-axis>");
	}
	
	private void saveOlapDimension(WabitOlapDimension dimension) {
		xml.print(out, "<olap4j-dimension");
        printAttribute("dimension-name", dimension.getName());
        printCommonAttributes(dimension);
        xml.niprintln(out, ">");
        xml.indent++;
        
        for (WabitOlapInclusion inclusion : dimension.getInclusions()) {
        	saveOlapSelection(inclusion);
        }
        
        for (WabitOlapExclusion exclusion : dimension.getExclusions()) {
        	saveOlapExclusion(exclusion);
        }
        
        xml.indent--;
        xml.println(out, "</olap4j-dimension>");
	}
	
	private void saveOlapSelection(WabitOlapInclusion selection) {
		xml.print(out, "<olap4j-selection");
		printCommonAttributes(selection);
    	printAttribute("dimension-name", ((WabitOlapDimension) selection.getParent()).getName());
        printAttribute("unique-member-name", selection.getUniqueMemberName());
        printAttribute("operator", selection.getOperator().name());
        xml.niprintln(out, "/>");
	}
	
	private void saveOlapExclusion(WabitOlapExclusion selection) {
		xml.print(out, "<olap4j-exclusion");
		printCommonAttributes(selection);
    	printAttribute("dimension-name", ((WabitOlapDimension) selection.getParent()).getName());
        printAttribute("unique-member-name", selection.getUniqueMemberName());
        printAttribute("operator", selection.getOperator().name());
        xml.niprintln(out, "/>");
	}

	/**
	 * This will save a font to the print writer. The font tag must be contained within tags of 
	 * the font's parent object. This allows giving a specific font name for the XML tag.
	 */
	private void saveFont(Font font, String fontName) {
		xml.print(out, "<" + fontName);
		printAttribute("name", font.getFamily());
		printAttribute("size", font.getSize());
		printAttribute("style", font.getStyle());
		xml.niprintln(out, "/>");
	}
	
	/**
	 * This will save a font to the print writer. The font tag must be contained within tags of 
	 * the font's parent object.
	 */
	private void saveFont(Font font) {
		saveFont(font, "font");
	}
	
	/**
	 * This saves a query cache. This will not close the print writer passed into the constructor.
	 * If this save method is used to export the query cache somewhere then close should be 
	 * called on it to flush the print writer and close it.
	 */
	private void saveQueryCache(QueryCache cache) {
	    Query data = cache;
		xml.print(out, "<query");
		printCommonAttributes(cache);
		printAttribute("zoom", data.getZoomLevel());
		printAttribute("streaming-row-limit", data.getStreamingRowLimit());
		printAttribute("row-limit", data.getRowLimit());
		printAttribute("grouping-enabled", Boolean.toString(data.isGroupingEnabled()));
		printAttribute("prompt-for-cross-joins", cache.getPromptForCrossJoins());
		printAttribute("automatically-executing", cache.isAutomaticallyExecuting());
		printAttribute("streaming", cache.isStreaming());
		if (!cache.getPromptForCrossJoins()) {
		    printAttribute("execute-queries-with-cross-joins", cache.getExecuteQueriesWithCrossJoins());
		}
		if (data.getDatabase() != null && data.getDatabase().getDataSource() != null) {
			printAttribute("data-source", data.getDatabase().getDataSource().getName());
		}
		xml.niprintln(out, ">");
		xml.indent++;

		Map<Item, String> itemIdMap = new HashMap<Item, String>();

		xml.print(out, "<constants");
		Container constants = data.getConstantsContainer();
		printAttribute("uuid", constants.getUUID());
		printAttribute("xpos", constants.getPosition().getX());
		printAttribute("ypos", constants.getPosition().getY());
		xml.niprintln(out, ">");
		xml.indent++;
		for (Item item : constants.getItems()) {
			xml.print(out, "<column");
			printAttribute("id", item.getUUID());
			itemIdMap.put(item, item.getUUID());
			printAttribute("name", item.getName());
			printAttribute("alias", item.getAlias());
			printAttribute("where-text", item.getWhere());
			printAttribute("group-by", item.getGroupBy().toString());
			printAttribute("having", item.getHaving());
			printAttribute("order-by", item.getOrderBy().toString());
			xml.niprintln(out, "/>");
		}
		xml.indent--;
		xml.println(out, "</constants>");
		
		for (Container table : data.getFromTableList()) {
			xml.print(out, "<table");
			printAttribute("name", table.getName());
			printAttribute("uuid", table.getUUID());
			TableContainer tableContainer = (TableContainer)table;
			if (!tableContainer.getSchema().equals("")) {
				printAttribute("schema", tableContainer.getSchema());
			}
			if (!tableContainer.getCatalog().equals("")) {
				printAttribute("catalog", tableContainer.getCatalog());
			}
			printAttribute("alias", table.getAlias());
			printAttribute("xpos", table.getPosition().getX());
			printAttribute("ypos", table.getPosition().getY());
			xml.niprintln(out, ">");
			xml.indent++;
			for (Item item : table.getItems()) {
				xml.print(out, "<column");
				printAttribute("id", item.getUUID());
				itemIdMap.put(item, item.getUUID());
				printAttribute("name", item.getName());
				printAttribute("alias", item.getAlias());
				printAttribute("where-text", item.getWhere());
				printAttribute("group-by", item.getGroupBy().toString());
	            printAttribute("having", item.getHaving());
	            printAttribute("order-by", item.getOrderBy().toString());
				xml.niprintln(out, "/>");
			}
			xml.indent--;
			xml.println(out, "</table>");
		}	
		
		for (SQLJoin join : data.getJoins()) {
			xml.print(out, "<join");
			printAttribute("left-item-id", itemIdMap.get(join.getLeftColumn()));
			printAttribute("left-is-outer", Boolean.toString(join.isLeftColumnOuterJoin()));
			printAttribute("right-item-id", itemIdMap.get(join.getRightColumn())); 
			printAttribute("right-is-outer", Boolean.toString(join.isRightColumnOuterJoin()));
			printAttribute("comparator", join.getComparator()); 
			xml.niprintln(out, "/>");
		}
				
		xml.println(out, "<select>");
		xml.indent++;
		for (Item col : data.getSelectedColumns()) {
			xml.print(out, "<column");
			printAttribute("id", itemIdMap.get(col));
			xml.niprintln(out, "/>");
		}
		xml.indent--;
		xml.println(out, "</select>");
		
		xml.print(out, "<global-where");
		printAttribute("text", data.getGlobalWhereClause());
		xml.niprintln(out, "/>");
		
		for (Item item : data.getOrderByList()) {
			xml.println(out, "<order-by");
			printAttribute("column-id", itemIdMap.get(item));
			xml.niprintln(out, "/>");
		}
		
		if (data.isScriptModified()) {
			xml.print(out, "<text>");
			xml.niprint(out, SQLPowerUtils.escapeXML(data.generateQuery()));
			xml.niprintln(out, "</text>");		
		}

		xml.indent--;
		xml.println(out, "</query>");
	}
	
	/**
	 * Prints the attributes that every WabitObject has (name and uuid for example).
	 * 
	 * @param o The WabitObject whose attributes to print.
	 */
	private void printCommonAttributes(WabitObject o) {
	    if (o.getName() == null) {
	        printAttribute("name", "unnamed");
	    } else {
	        printAttribute("name", o.getName());
	    }
        printAttribute("uuid", o.getUUID());
	}
	
	/**
	 * Prints an attribute to the file. If the attribute value is null
	 * no attribute will be printed.
	 */
    public void printAttribute(String name, String value) {
        if (value == null) return;
        xml.niprint(out, " " + name + "=\"");
        xml.niprint(out, SQLPowerUtils.escapeXML(value) + "\"");
    }
    
    public void printAttribute(String name, double value) {
    	xml.niprint(out, " " + name + "=\"" + value + "\"");
    }
    
    public void printAttribute(String name, int value) {
        xml.niprint(out, " " + name + "=\"" + value + "\"");
    }

    public void printAttribute(String name, boolean value) {
        xml.niprint(out, " " + name + "=\"" + value + "\"");
    }
    
    public void printAttribute(String name, Object value) {
    	if (value == null) return;
    	if (value instanceof String) printAttribute(name, (String)value);
    	else if (value instanceof Double) printAttribute(name, (Double)value);
    	else if (value instanceof Integer) printAttribute(name, (Integer)value);
    	else if (value instanceof Boolean) printAttribute(name, (Boolean)value);
    	else
    		throw new RuntimeException("Unknown class type for DAO class converter.");
    }

    /**
     * Call this to flush and close the output stream if only part
     * of the file is being saved.
     */
    public void close() {
    	out.flush();
    	out.close();
    }
	
}
