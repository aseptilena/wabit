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

package ca.sqlpower.wabit.report;

import java.awt.Font;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import ca.sqlpower.object.CleanupExceptions;
import ca.sqlpower.object.SPObject;
import ca.sqlpower.util.SQLPowerUtils;
import ca.sqlpower.wabit.AbstractWabitObject;
import ca.sqlpower.wabit.WabitObject;
import ca.sqlpower.wabit.WabitUtils;
import ca.sqlpower.wabit.report.Guide.Axis;

/**
 * A page is an arrangement of boxes and guides (usually page margins) on a
 * container of a particular size. Boxes have data provided to them by content
 * renderers, which can be fed by database queries, labels with variable
 * substitution, or anything else. The actual report will be rendered to one or
 * more pages--the content renderers specify whether or not they need another
 * page to finish rendering their data.
 */
public class Page extends AbstractWabitObject {

	private static final Logger logger = Logger.getLogger(Page.class);
	
    /**
     * This is the Graphics2D standard for pixels-to-inches conversions.
     */
    public static final int DPI = 72;

    /**
     * The default FontMetrics for this Page. All ContentBoxes in this
     * Page that use a FontMetrics will by default inherit this one.
     */
    private Font defaultFont;
    
    /**
     * Indicates the transformation that should be applied when this page is rendered.
     * Portrait is normal (the identity transform), landscape is rotated 90 degrees
     * clockwise, and reverse landscape is rotated 90 degrees counterclockwise.
     * <p>
     * The orientation doesn't affect what width and height the page reports having;
     * it simply prescribes which direction should be "up" for rendered content.
     */
    public static enum PageOrientation {
        PORTRAIT(PageFormat.PORTRAIT),
        LANDSCAPE(PageFormat.LANDSCAPE),
        REVERSE_LANDSCAPE(PageFormat.REVERSE_LANDSCAPE);
        
        private final int printApiCode;

        PageOrientation(int printApiCode) {
            this.printApiCode = printApiCode;
        }

        /**
         * Returns the correct integer code for this orientation in the Java
         * Print API.
         */
        public int getPrintApiCode() {
            return printApiCode;
        }

        public static PageOrientation forPrintApiCode(int apiCode) {
            for (PageOrientation po : values()) {
                if (po.printApiCode == apiCode) {
                    return po;
                }
            }
            throw new IllegalArgumentException("Unknown Java Print orientation code: " + apiCode);
        }
    }
    
    /**
     * Page width, inclusive of margins and non-printable area.
     */
    private int width;
    
    /**
     * Page height, inclusive of margins and non-printable area.
     */
    private int height;
    
    /**
     * Indicates the orientation for this page's contents.
     */
    private PageOrientation orientation;
    
    /**
     * The content boxes that provide the page's content and define its layout.
     */
    private final List<ContentBox> contentBoxes = new ArrayList<ContentBox>();

    /**
     * The guides of this page. This list includes but is not limited to the
     * default page margins. They don't print visibly, but they give reference
     * points for the layout.
     */
    private final List<Guide> guides = new ArrayList<Guide>();

	/**
	 * FIXME This enum defines the {@link SPObject} child classes a {@link Page}
	 * takes as well as the ordinal order of these child classes such that the
	 * class going before does not depend on the class that goes after. This is
	 * here temporarily, see bug 2327 for future enhancements.
	 * http://trillian.sqlpower.ca/bugzilla/show_bug.cgi?id=2327
	 */
	public enum SPObjectOrder {
		CONTENT_BOX(ContentBox.class),
		GUIDE(Guide.class);
		
		/**
		 * @see #getSuperChildClass()
		 */
		private final Class<? extends SPObject> superChildClass;
		
		/**
		 * @see #getChildClasses()
		 */
		private final Set<Class<? extends SPObject>> classes;

		/**
		 * Creates a new {@link SPObjectOrder},
		 * 
		 * @param superChildClass
		 *            The highest {@link SPObject} class that the
		 *            {@link SPObject#childPositionOffset(Class)} method looks
		 *            at to determine the index.
		 * @param classes
		 *            The list of child {@link SPObject} class varargs which
		 *            share the same ordering in the list of children. These
		 *            classes must be extending/implementing
		 *            {@link #superChildClass}.
		 */
		private SPObjectOrder(Class<? extends SPObject> superChildClass, Class<? extends SPObject>... classes) {
			this.superChildClass = superChildClass;
			this.classes = new HashSet<Class<? extends SPObject>>(Arrays.asList(classes));
		}

		/**
		 * Returns the highest {@link SPObject} class that the
		 * {@link SPObject#childPositionOffset(Class)} method looks at to
		 * determine the index.
		 */
		public Class<? extends SPObject> getSuperChildClass() {
			return superChildClass;
		}

		/**
		 * Returns the {@link Set} of {@link SPObject} classes that share the
		 * same ordering in the list of children. These classes must either
		 * extend/implement from the same class type given by
		 * {@link SPObjectOrder#getSuperChildClass()}.
		 */
		public Set<Class<? extends SPObject>> getChildClasses() {
			return Collections.unmodifiableSet(classes);
		}
		
		public static SPObjectOrder getOrderBySimpleClassName(String name) {
			for (SPObjectOrder order : values()) {
				if (order.getSuperChildClass().getSimpleName().equals(name)) {
					return order;
				} else {
					for (Class<? extends SPObject> childClass : order.getChildClasses()) {
						if (childClass.getSimpleName().equals(name)) {
							return order;
						}
					}
				}
			}
			throw new IllegalArgumentException("The " + SPObject.class.getSimpleName() + 
					" class \"" + name + "\" does not exist or is not a child type " +
							"of " + Page.class.getSimpleName() + ".");
		}
		
	}

	/**
	 * Creates a page with the given custom width and height, and 1-inch
	 * margins. The units for width and height are 1/72 of an inch, which
	 * correspond well with screen pixels in Java 2D (72 pixels = 1 inch).
	 * 
	 * @param width
	 *            The page width in units of 1/72 inch. The is the real apparent
	 *            width of the page. If you are creating a landscape page, this
	 *            value should be larger than the one you specify for height.
	 * @param height
	 *            The page height in units of 1/72 inch. The is the real
	 *            apparent height of the page. If you are creating a landscape
	 *            page, this value should be smaller than the one you specify
	 *            for width.
	 * @param orientation
	 *            The page orientation. This value has a fun interplay with
	 *            width and height, so be sure to specify this correctly based
	 *            on the type of page you are creating.
	 * @param startWithGuides
	 *            If true guides will be added to the page defining a 1 inch
	 *            margin. If false the page will start guideless.
	 */
    public Page(String name, int width, int height, PageOrientation orientation, boolean startWithGuides) {
        setName(name);
        this.orientation = orientation;
        this.width = width;
        this.height = height;
       
        if (startWithGuides) {
        	// Default margins of 1 inch
        	addGuide(new Guide(Axis.VERTICAL, DPI));
        	addGuide(new Guide(Axis.VERTICAL, width - DPI));
        	addGuide(new Guide(Axis.HORIZONTAL, DPI));
        	addGuide(new Guide(Axis.HORIZONTAL, height - DPI));
        }
        
        setDefaultFont(Font.decode("Arial-10"));
    }
    
    /**
     * Copy constructor
     */
    public Page(Page page) {
        setName(page.getName());
    	this.orientation = page.orientation;
    	this.width = page.width;
    	this.height = page.height;
    	setDefaultFont(page.getDefaultFont());
    	
    	for (Guide guide : page.guides) {
    		Guide newGuide = new Guide(guide.getAxis(), guide.getOffset());
			addGuide(newGuide);
    		
    	}
    	
    	for (ContentBox contentBox : page.contentBoxes) {
    		ContentBox newContentBox = new ContentBox(contentBox);
			addContentBox(newContentBox);
    	}
    	
    	
    }

    /**
     * Creates a new page whose dimensions and orientation are equivalent to the
     * values in the pageFormat. The width of the page will be the apparent
     * width of the visual page and the same will be true of the height.
     * 
     * @param name
     *            The name for this WabitObject.
     * @param pageFormat
     *            The Java printing API page format from which to read the
     *            dimensions and orientation. This object is not retained by
     *            this class, so subsequent changes you make to the pageFormat
     *            object will not affect this page.
     */
    public Page(String name, PageFormat pageFormat) {
        this(name, (int) pageFormat.getWidth(), (int) pageFormat.getHeight(),
                PageOrientation.forPrintApiCode(pageFormat.getOrientation()), true);
    }

    /**
     * Returns the apparent width of the page. If this is a portrait page, this will
     * usually be the smaller dimension.
     */
    public int getWidth() {
        return width;
    }

    /**
     * Sets the apparent width of the page. If this is a portrait page, this should
     * usually be the smaller dimension.
     */
    public void setWidth(int width) {
        int oldWidth = this.width;
        this.width = width;
        firePropertyChange("width", oldWidth, width);
        if (WabitUtils.getWorkspace(this).isMagicEnabled()) {
        	adjustMarginForPageWidth(oldWidth, width);
        }
    }
    
    private void adjustMarginForPageWidth(int oldWidth, int newWidth) {
        Guide rightMargin = getGuideWithLargestOffset(Axis.VERTICAL);
        if (rightMargin != null) {
            int oldMarginWidth = (int) (oldWidth - rightMargin.getOffset());
            rightMargin.setOffset(newWidth - oldMarginWidth);
        }
    }

    /**
     * Returns the apparent height of the page. If this is a portrait page, this will
     * usually be the larger dimension.
     */
    public int getHeight() {
        return height;
    }

    /**
     * Sets the apparent height of the page. If this is a portrait page, this should
     * usually be the larger dimension.
     */
    public void setHeight(int height) {
        int oldHeight = this.height;
        this.height = height;
        firePropertyChange("height", oldHeight, height);
        if (WabitUtils.getWorkspace(this).isMagicEnabled()) {
        	adjustMarginForPageHeight(oldHeight, height);
        }
    }

    private void adjustMarginForPageHeight(int oldHeight, int newHeight) {
        Guide bottomMargin = getGuideWithLargestOffset(Axis.HORIZONTAL);
        if (bottomMargin != null) {
            int oldMarginHeight = (int) (oldHeight - bottomMargin.getOffset());
            bottomMargin.setOffset(newHeight - oldMarginHeight);
        }
    }
    
    public Font getDefaultFont() {
        return defaultFont;
    }
    
    public void setDefaultFont(Font defaultFont) {
        Font oldFont = this.defaultFont;
        this.defaultFont = defaultFont;
        firePropertyChange("defaultFont", oldFont, defaultFont);
    }
    
    public PageOrientation getOrientation() {
        return orientation;
    }
    
    public void setOrientation(PageOrientation orientation) {
        PageOrientation oldOrientation = this.orientation;
        this.orientation = orientation;
        firePropertyChange("orientation", oldOrientation, orientation);
        
        // the following expression is meant to evaluate to false under the following conditions:
        // 1. The page orientation hasn't actually changed
        // 2. The page orientation has changed between LANDSCAPE and REVERSE_LANDSCAPE
        if (WabitUtils.getWorkspace(this).isMagicEnabled()) {
        	if (   (oldOrientation == PageOrientation.PORTRAIT && orientation != PageOrientation.PORTRAIT)
        			|| (oldOrientation != PageOrientation.PORTRAIT && orientation == PageOrientation.PORTRAIT)) {
        		
        		int oldWidth = getWidth();
        		int oldHeight = getHeight();
        		
        		setWidth(oldHeight);
        		setHeight(oldWidth);
        	}
        }
    }

    /**
     * Adds a content box to this page at the end of the list of content boxes.
     * 
     * @param addme
     *            The content box to add.
     */
    public void addContentBox(ContentBox addme) {
        addContentBox(addme, contentBoxes.size());
    }

    /**
     * Adds a content box to this page at the given index in the list of content
     * boxes.
     * 
     * @param addme
     *            The content box to add.
     * @param index
     *            The index to add the content box at. This cannot be greater
     *            than the number of content boxes currently in the page.
     */
    public void addContentBox(ContentBox addme, int index) {
    	//Setting the parent of the content box to this page. If the addChild method
    	//is used the parent will be set to this page twice but only fire one event which
    	//is fine. TODO See if we can remove the addContentBox methods to not have to set
    	//the parent twice.
    	addme.setParent(this);
    	
        setUniqueName(addme, addme.getName());
        contentBoxes.add(addme);
        fireChildAdded(ContentBox.class, addme, index);
    }
    
    public void removeContentBox(ContentBox removeme) {
        if (removeme.getParent() != this) {
            throw new IllegalStateException("That's not my content box!");
        }
        CleanupExceptions cleanupObject = SQLPowerUtils.cleanupSPObject(removeme);
        SQLPowerUtils.displayCleanupErrors(cleanupObject, getSession().getContext());
        int index = contentBoxes.indexOf(removeme);
        if (index != -1) {
        	contentBoxes.remove(removeme);
        	fireChildRemoved(ContentBox.class, removeme, index);
        	removeme.setParent(null);
    	}
    }
    /**
	 * If the name passed is already taken by children of this page (which at
	 * present, are Guides and ContentBoxes)then this method will generate a unique
	 * name for the WabitObject
	 * @param addme
	 * @param maybeUniqueName
	 */
	public void setUniqueName(WabitObject addme, String maybeUniqueName) {
		int newSuffix = 0;
		logger.debug("maybe Unique name: " + maybeUniqueName
				+ " getChildByName: " + getChildByName(maybeUniqueName));
		//If addme is already a child of the page and if its name is the same 
		//as maybeUniqueName, then we do nothing
		if (!(getChildren().contains(addme) && addme.getName().compareTo(
				maybeUniqueName) == 0)) {
			if (getChildByName(maybeUniqueName) != null) {
				logger.debug("We are checking if the name " + maybeUniqueName
						+ " has already been taken");
				WabitObject done;
				do {
					newSuffix++;
					done = getChildByName(maybeUniqueName + "_" + newSuffix); //$NON-NLS-1$
				} while (done != null && done.getName().compareTo(addme.getName()) != 0);
			}
			addme.setName(maybeUniqueName
					+ (newSuffix == 0 ? "" : "_" + newSuffix));
		}
	}
	
    public WabitObject getChildByName(String getMe) {
		for (WabitObject child : getChildren()) {
			if (child.getName().compareTo(getMe) == 0) {
				return child;
			}
		}
		return null;
	}
    
    /**
     * Returns an unmodifiable view of this page's content boxes.
     */
    public List<ContentBox> getContentBoxes() {
        return Collections.unmodifiableList(contentBoxes);
    }
    
    /**
     * Returns an unmodifiable view of the page's guides. Currently only used
     * in testing.
     */
    List<Guide> getGuides() {
    	return Collections.unmodifiableList(guides);
    }

    /**
     * Adds a guide to the end of the list of guides.
     * 
     * @param addme
     *            The guide to add as a child of this page.
     */
    public void addGuide(Guide addme) {
        addGuide(addme, guides.size());
    }

    /**
     * Adds a guide to the list of guides at the given index.
     * 
     * @param addme
     *            The guide to add to this page.
     * @param index
     *            The index in the list of guides to add this guide to. This
     *            cannot be greater than the number of guides currently in the
     *            page.
     */
    public void addGuide(Guide addme, int index) {
    	//Setting the parent of the guide to this page. If the addChild method
    	//is used the parent will be set to this page twice but only fire one event which
    	//is fine. TODO See if we can remove the addGuide methods to not have to set
    	//the parent twice.
    	addme.setParent(this);
    	
        setUniqueName(addme, addme.getName());
        guides.add(index, addme);
        fireChildAdded(Guide.class, addme, index);
    }

    public double getLeftMarginOffset() {
        Guide leftMargin = getGuideWithSmallestOffset(Axis.VERTICAL);
        if (leftMargin != null) {
            return leftMargin.getOffset();
        } else {
            return 0;
        }
    }
    
    public double getRightMarginOffset() {
        Guide rightMargin = getGuideWithLargestOffset(Axis.VERTICAL);
        if (rightMargin != null) {
            return new Double(rightMargin.getOffset());
        } else {
            return new Double(width);
        }
    }

    public double getUpperMarginOffset() {
        Guide upperMargin = getGuideWithSmallestOffset(Axis.HORIZONTAL);
        if (upperMargin != null) {
            return upperMargin.getOffset();
        } else {
            return 0;
        }
    }
    
    public double getLowerMarginOffset() {
        Guide lowerMargin = getGuideWithLargestOffset(Axis.HORIZONTAL);
        if (lowerMargin != null) {
            return lowerMargin.getOffset();
        } else {
            return height;
        }
    }

    private Guide getGuideWithLargestOffset(Axis axis) {
        Guide largest = null;
        for (Guide guide: guides) {
            if (guide.getAxis().equals(axis)) {
                if (largest == null || largest.getOffset() < guide.getOffset()) {
                    largest = guide;
                }
            }
        }
        return largest;
    }
    
    private Guide getGuideWithSmallestOffset(Axis axis) {
        Guide smallest = null;
        for (Guide guide: guides) {
            if (guide.getAxis().equals(axis)) {
                if (smallest == null || smallest.getOffset() > guide.getOffset()) {
                    smallest = guide;
                }
            }
        }
        return smallest;
    }
    
    
    public boolean allowsChildren() {
        return true;
    }

    public int childPositionOffset(Class<? extends SPObject> childType) {
        if (childType == ContentBox.class) {
            return 0;
        } else if (childType == Guide.class) {
            return contentBoxes.size();
        } else {
            throw new IllegalArgumentException("Pages don't have children of type " + childType);
        }
    }

    /**
     * Returns an unmodifiable view of this page's boxes.
     */
    public List<WabitObject> getChildren() {
        List<WabitObject> children = new ArrayList<WabitObject>();
        children.addAll(contentBoxes);
        children.addAll(guides);
        return Collections.unmodifiableList(children);
    }
    
    /**
     * Removes the first instance of this guide from the Page. Returns
     * true if the guide was successfully removed. Returns false otherwise.
     * @param guide
     * @return
     */
    public boolean removeGuide(Guide removeme) {
    	int index = guides.indexOf(removeme);
    	if(guides.remove(removeme)) {
    		fireChildRemoved(Guide.class, removeme, index);
    		return true;
    	}
    	return false;
    	
    }

    /**
     * Gets a page format instance that describes this page's geometry in terms
     * of the Java print API.
     * 
     * @return A throwaway PageFormat object that describes this page's current
     *         geometry. Changes to the returned PageFormat object will not
     *         affect this page in any way.
     */
    public PageFormat getPageFormat() {
        PageFormat pageFormat = new PageFormat();
        pageFormat.setOrientation(getOrientation().getPrintApiCode());
        Paper paper = new Paper();
        
        if (getOrientation() == PageOrientation.PORTRAIT) {
            paper.setSize(getWidth(), getHeight());
        } else {
            paper.setSize(getHeight(), getWidth());
        }

        // the imageable area on the page format we return determines the clipping
        // region for the print API, so we always want to set it as big as possible
        // regardless of our own margin guides
        paper.setImageableArea(0, 0, paper.getWidth(), paper.getHeight());

        pageFormat.setPaper(paper);
        
        return pageFormat;
    }
    
    /**
     * Applies the given Java print API page format to this page object. The aspects that
     * are affected are:
     * <ul>
     *  <li>width (paper width)
     *  <li>height (paper height)
     *  <li>orientation
     *  <li>TODO: margins (determined by imageable area)
     * </ul>
     * If any changes are made to the page as a result of this operation, property change
     * events for the individually affected properties will be fired as if those methods
     * had been called directly.
     * 
     * @param pageFormat The page format to apply to this page.
     */
    public void applyPageFormat(PageFormat pageFormat) {
        Paper paper = pageFormat.getPaper();
        
        setOrientation(PageOrientation.forPrintApiCode(pageFormat.getOrientation()));
        
        if (WabitUtils.getWorkspace(this).isMagicEnabled()) {
        	// important to set the page dimensions after orientation so
        	// setOrientation() doesn't swap them inappropriately
        	if (getOrientation() == PageOrientation.PORTRAIT) {
        		setWidth((int) paper.getWidth());
        		setHeight((int) paper.getHeight());
        	} else {
        		setWidth((int) paper.getHeight());
        		setHeight((int) paper.getWidth());
        	}
        }        
        // TODO update margins from page format?
    }

    public List<WabitObject> getDependencies() {
        return Collections.emptyList();
    }
    
    public void removeDependency(SPObject dependency) {
        for (int i = getChildren().size() - 1; i >= 0; i--) {
            getChildren().get(i).removeDependency(dependency);
        }
    }

    @Override
    protected boolean removeChildImpl(SPObject child) {
        if (child instanceof Guide) {
            return removeGuide((Guide) child);
        } else if (child instanceof ContentBox) {
            removeContentBox((ContentBox) child);
            return true;
        } else {
            throw new IllegalArgumentException("Cannot remove children of type " 
                    + child.getClass() + " from " + getName());
        }
    }
    
    @Override
    protected void addChildImpl(SPObject child, int index) {
        if (child instanceof Guide) {
            addGuide((Guide) child, index);
        } else if (child instanceof ContentBox) {
            addContentBox((ContentBox) child, index);
        } else {
            throw new AssertionError("The child " + child.getName() + " of type " + 
                    child.getClass() + " should be a valid type for " + getName() + 
                    " of type " + getClass());
        }
    }
    
    public List<Class<? extends SPObject>> getAllowedChildTypes() {
    	List<Class<? extends SPObject>> types = new ArrayList<Class<? extends SPObject>>();
    	types.add(ContentBox.class);
    	types.add(Guide.class);
    	return types;
    }

}
