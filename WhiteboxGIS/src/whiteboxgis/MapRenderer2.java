/*
 * Copyright (C) 2011-2013 Dr. John Lindsay <jlindsay@uoguelph.ca>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package whiteboxgis;

import whitebox.cartographic.MapInfo;
import java.awt.Point;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.MemoryImageSource;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.io.File;
import java.text.AttributedString;
import java.text.DecimalFormat;
import java.awt.font.TextAttribute;
import java.awt.font.LineBreakMeasurer;
import java.text.AttributedCharacterIterator;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.util.ArrayList;
import java.util.Collections;
import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.font.GlyphVector;
import whitebox.cartographic.MapArea;
import whitebox.cartographic.*;
import whitebox.geospatialfiles.RasterLayerInfo;
import whitebox.geospatialfiles.VectorLayerInfo;
import whitebox.geospatialfiles.WhiteboxRasterBase;
import whitebox.geospatialfiles.shapefile.*;
import whitebox.geospatialfiles.WhiteboxRasterInfo.*;
import whitebox.interfaces.CartographicElement;
import whitebox.interfaces.MapLayer;
import whitebox.interfaces.WhiteboxPluginHost;
import whitebox.structures.BoundingBox;
import whitebox.structures.GridCell;
import whitebox.structures.XYPoint;
import whitebox.utilities.OSFinder;
import whiteboxgis.user_interfaces.ModifyPixel;

/**
 *
 * @author johnlindsay
 */
public class MapRenderer2 extends JPanel implements Printable, MouseMotionListener,
        MouseListener {

    public static final int MOUSE_MODE_ZOOM = 0;
    public static final int MOUSE_MODE_PAN = 1;
    public static final int MOUSE_MODE_GETINFO = 2;
    public static final int MOUSE_MODE_SELECT = 3;
    public static final int MOUSE_MODE_CARTO_ELEMENT = 4;
    public static final int MOUSE_MODE_MAPAREA = 5;
    public static final int MOUSE_MODE_RESIZE = 6;
    private int myMode = 0;
    public static final int RESIZE_MODE_N = 0;
    public static final int RESIZE_MODE_S = 1;
    public static final int RESIZE_MODE_E = 2;
    public static final int RESIZE_MODE_W = 3;
    public static final int RESIZE_MODE_NE = 4;
    public static final int RESIZE_MODE_NW = 5;
    public static final int RESIZE_MODE_SE = 6;
    public static final int RESIZE_MODE_SW = 7;
    private int myResizeMode = -1;
    //private int mapElementX, mapElementY;
    public MapInfo map = null;
    private StatusBar status = null;
    private JTextField scaleText = null;
    private WhiteboxPluginHost host = null;
    private Cursor zoomCursor = null;
    private Cursor panCursor = null;
    private Cursor panClosedHandCursor = null;
    private Cursor selectCursor = null;
    private String graphicsDirectory;
    private boolean modifyingPixels = false;
    private double modifyPixelsX = -1;
    private double modifyPixelsY = -1;
    private boolean usingDistanceTool = false;
    private int whichCartoElement = -1;
    public static final int CARTO_ELEMENT_SCALE = 0;
    public static final int CARTO_ELEMENT_NORTH_ARROW = 1;
    public static final int CARTO_ELEMENT_LEGEND = 2;
    public static final int CARTO_ELEMENT_TITLE = 3;
    private boolean printingMap = false;
    private BoundingBox mapExtent = new BoundingBox();
    private Color selectedFeatureColour = Color.CYAN;
    private Color selectionBoxColour = Color.GRAY;

    public MapRenderer2() {
        init();
    }

    private void init() {
        try {

            String applicationDirectory = java.net.URLDecoder.decode(getClass().getProtectionDomain().getCodeSource().getLocation().getPath(), "UTF-8");
            if (applicationDirectory.endsWith(".exe") || applicationDirectory.endsWith(".jar")) {
                applicationDirectory = new File(applicationDirectory).getParent();
            } else {
                // Add the path to the class files
                applicationDirectory += getClass().getName().replace('.', File.separatorChar);

                // Step one level up as we are only interested in the
                // directory containing the class files
                applicationDirectory = new File(applicationDirectory).getParent();
            }
            graphicsDirectory = applicationDirectory + File.separator
                    + "resources" + File.separator + "Images" + File.separator;

            Toolkit toolkit = Toolkit.getDefaultToolkit();
            Point cursorHotSpot = new Point(0, 0);
            if (!OSFinder.isWindows()) {
                zoomCursor = toolkit.createCustomCursor(toolkit.getImage(graphicsDirectory + "ZoomToBoxCursor.png"), cursorHotSpot, "zoomCursor");
                panCursor = toolkit.createCustomCursor(toolkit.getImage(graphicsDirectory + "Pan3.png"), cursorHotSpot, "panCursor");
                panClosedHandCursor = toolkit.createCustomCursor(toolkit.getImage(graphicsDirectory + "Pan4.png"), cursorHotSpot, "panCursor");
                selectCursor = toolkit.createCustomCursor(toolkit.getImage(graphicsDirectory + "select.png"), cursorHotSpot, "selectCursor");
            } else {
                // windows requires 32 x 32 cursors. Otherwise they look terrible.
                zoomCursor = toolkit.createCustomCursor(toolkit.getImage(graphicsDirectory + "ZoomToBoxCursorWin.png"), cursorHotSpot, "zoomCursor");
                panCursor = toolkit.createCustomCursor(toolkit.getImage(graphicsDirectory + "Pan3Win.png"), cursorHotSpot, "panCursor");
                panClosedHandCursor = toolkit.createCustomCursor(toolkit.getImage(graphicsDirectory + "Pan4Win.png"), cursorHotSpot, "panCursor");
                selectCursor = toolkit.createCustomCursor(toolkit.getImage(graphicsDirectory + "selectWin.png"), cursorHotSpot, "selectCursor");
            }
            this.setCursor(zoomCursor);
            this.addMouseMotionListener(this);
            this.addMouseListener(this);
            this.addKeyListener(new KeyListener() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                        removeSelectedMapElements();
                        host.refreshMap(true);
                    } else if (e.getKeyCode() == KeyEvent.VK_DOWN
                            || e.getKeyCode() == KeyEvent.VK_KP_DOWN) {
                        map.getActiveMapArea().panDown();
                        host.refreshMap(true);
                    } else if (e.getKeyCode() == KeyEvent.VK_UP
                            || e.getKeyCode() == KeyEvent.VK_KP_UP) {
                        map.getActiveMapArea().panUp();
                        host.refreshMap(true);
                    } else if (e.getKeyCode() == KeyEvent.VK_LEFT
                            || e.getKeyCode() == KeyEvent.VK_KP_LEFT) {
                        map.getActiveMapArea().panLeft();
                        host.refreshMap(true);
                    } else if (e.getKeyCode() == KeyEvent.VK_RIGHT
                            || e.getKeyCode() == KeyEvent.VK_KP_RIGHT) {
                        map.getActiveMapArea().panRight();
                        host.refreshMap(true);
                    } else if (e.getKeyCode() == KeyEvent.VK_PLUS
                            || e.getKeyCode() == KeyEvent.VK_EQUALS) {
                        map.getActiveMapArea().zoomIn();
                        host.refreshMap(true);
                    } else if (e.getKeyCode() == KeyEvent.VK_MINUS
                            || e.getKeyCode() == KeyEvent.VK_UNDERSCORE) {
                        map.getActiveMapArea().zoomOut();
                        host.refreshMap(true);
                    }
                }

                @Override
                public void keyReleased(KeyEvent e) {
                }

                @Override
                public void keyTyped(KeyEvent e) {
                }
            });

        } catch (Exception e) {
            System.err.println(e);
        }
    }

    public void setHost(WhiteboxPluginHost host) {
        this.host = host;
    }

    public MapInfo getMapInfo() {
        return map;
    }

    public void setMapInfo(MapInfo mapinfo) {
        this.map = mapinfo;
    }

    public int getMouseMode() {
        return backgroundMouseMode; //myMode;
    }
    private int backgroundMouseMode = MOUSE_MODE_ZOOM;

    public void setMouseMode(int mouseMode) {
        myMode = mouseMode;
        backgroundMouseMode = mouseMode;
        switch (myMode) {
            case MOUSE_MODE_ZOOM:
                this.setCursor(zoomCursor);
                break;
            case MOUSE_MODE_PAN:
                this.setCursor(panCursor);
                break;
            case MOUSE_MODE_SELECT:
                this.setCursor(selectCursor);
                break;
        }

    }

    public void setUsingDistanceTool(boolean val) {
        usingDistanceTool = val;
        if (!usingDistanceTool) {
            distPoints.clear();
        }
    }

    public boolean isUsingDistanceTool() {
        return usingDistanceTool;
    }

    public void setModifyingPixels(boolean val) {
        modifyingPixels = val;
    }

    public boolean isModifyingPixels() {
        return modifyingPixels;
    }

    public void setStatusBar(StatusBar status) {
        this.status = status;
    }

    public void setScaleText(JTextField scaleText) {
        this.scaleText = scaleText;
    }

    public void removeSelectedMapElements() {
        try {
            // remove any selected map elements
            ArrayList<Integer> listOfSelectedElements = new ArrayList<Integer>();
            for (CartographicElement ce : map.getCartographicElementList()) {
                if (ce.isSelected()) {
                    listOfSelectedElements.add(ce.getElementNumber());
                }
            }
            Collections.sort(listOfSelectedElements);
            for (int i = listOfSelectedElements.size() - 1; i >= 0; i--) {
                map.removeCartographicElement(listOfSelectedElements.get(i));
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    @Override
    public int print(Graphics g, PageFormat pf, int page) throws PrinterException {
//        host.showFeedback("Map printing has been temporarily disabled.");
//        return -1;
        try {

            if (page > 0) {
                return NO_SUCH_PAGE;
            }

//        int i = pf.getOrientation();

            // get the size of the page
        double pageWidth = pf.getImageableWidth();
        double pageHeight = pf.getImageableHeight();
            w = (int) (printResolution * pageWidth / 72);
            h = (int) (printResolution * pageHeight / 72);
//        double myWidth = this.getWidth();// - borderWidth * 2;
//        double myHeight = this.getHeight();// - borderWidth * 2;
//        double scaleX = pageWidth / w; //myWidth;
//        double scaleY = pageHeight / h; //myHeight;
        double minScale = 72d / printResolution; //Math.min(scaleX, scaleY);
            
//            w = (int)
            printingMap = true;
            map.deslectAllCartographicElements();

            Graphics2D g2d = (Graphics2D) g;
            g2d.translate(pf.getImageableX(), pf.getImageableY());
            g2d.scale(minScale, minScale);
            
            drawMap(g);

            printingMap = false;
            
            return PAGE_EXISTS;
        } catch (Exception e) {
            throw new PrinterException("Something went wrong during printing.");
        }
    }
    
    private int printResolution = 600;

    public int getPrintResolution() {
        return printResolution;
    }

    public void setPrintResolution(int resolution) {
        this.printResolution = resolution;
    }
    
    public boolean saveToImage(String fileName) {
        try {
            // get the page size information
            PageFormat pageFormat = map.getPageFormat();
            double pageWidthInInches = pageFormat.getWidth() / 72;
            double pageHeightInInches = pageFormat.getHeight() / 72;
            
            w = (int)(printResolution * pageWidthInInches); 
            h = (int)(printResolution * pageHeightInInches); 

            // TYPE_INT_ARGB specifies the image format: 8-bit RGBA packed
            // into integer pixels
            BufferedImage bi = new BufferedImage((int)w, (int)h, BufferedImage.TYPE_INT_ARGB);

            Graphics ig = bi.createGraphics();
            printingMap = true;
            map.deslectAllCartographicElements();
             
            drawMap(ig);
            String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toUpperCase();
            if (!ImageIO.write(bi, extension, new File(fileName))) {
                return false;
            }
            printingMap = false;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void paint(Graphics g) {
        w = this.getWidth();
        h = this.getHeight();
        drawMap(g);
    }
    
    private double scale = 0;
    private double pageTop = 0;
    private double pageLeft = 0;
    private double w = 0;
    private double h = 0;

    private void drawMap(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
       
        try {

            AffineTransform old = g2.getTransform();

            // get the drawing area's width and height
            
            Stroke oldStroke;

            RenderingHints rh = new RenderingHints(
                    RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHints(rh);
            rh = new RenderingHints(
                    RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHints(rh);

            g2.setColor(Color.white);
            g2.fillRect(0, 0, (int) w, (int) h);
            
            if (map != null && map.getNumberOfCartographicElements() > 0) {

                DecimalFormat df = new DecimalFormat("###,###,###.#");

                Font font = new Font("SanSerif", Font.PLAIN, 11);
                Font oldFont;
                FontMetrics metrics = g2.getFontMetrics(font);
                int adv;

                String label;
                int x, y;

                // get the page size information
                PageFormat pageFormat = map.getPageFormat();
                double pageWidth = pageFormat.getWidth();
                double pageHeight = pageFormat.getHeight();

                // set the page scale
                BoundingBox pageExtent = map.getPageExtent();
                int pageShadowSize;
                if (!printingMap) {
                    pageShadowSize = 6;
                    if (pageExtent.getMaxY() == Float.NEGATIVE_INFINITY) {
                        // it hasn't yet been initialized
                        pageExtent.setMinX(-pageShadowSize);
                        pageExtent.setMinY(-pageShadowSize);
                        pageExtent.setMaxX(pageWidth + 1.25 * pageShadowSize);
                        pageExtent.setMaxY(pageHeight + 1.25 * pageShadowSize);

                        map.setPageExtent(pageExtent);
                    }
                } else {
                    pageShadowSize = 0;
                    if (pageExtent.getMaxY() == Float.NEGATIVE_INFINITY) {
                        // it hasn't yet been initialized
                        pageExtent.setMinX(0);
                        pageExtent.setMinY(0);
                        pageExtent.setMaxX(pageWidth - 1);
                        pageExtent.setMaxY(pageHeight - 1);

                        map.setPageExtent(pageExtent);
                    }
                }


                scale = Math.min((w / pageExtent.getWidth()),
                        (h / pageExtent.getHeight()));

                // what are the margins of the page on the drawing area?
                //double pageWidthOnScreen = pageWidth * scale;
                //double pageHeightOnScreen = pageHeight * scale;
                pageTop = (h - pageExtent.getHeight() * scale) / 2.0 - pageExtent.getMinY() * scale; //(h - pageHeightOnScreen) / 2.0;
                pageLeft = (w - pageExtent.getWidth() * scale) / 2.0 - pageExtent.getMinX() * scale; //(w - pageWidthOnScreen) / 2.0;
                //double pageBottom = pageTop + pageHeightOnScreen;
                //double pageRight = pageLeft + pageWidthOnScreen;
                int margin = (int) (map.getMargin() * 72);

                g2.translate(pageLeft, pageTop);
                g2.scale(scale, scale);

                float miter = (float) (2.0 / scale);
                miter = (miter < 1) ? 1 : miter;
                float constantLineWidth = (float) (1 / scale);
                if (constantLineWidth == 0) {
                    constantLineWidth = 0.5f;
                }
                float dash1[] = {constantLineWidth * 4};
                BasicStroke dashed =
                        new BasicStroke(constantLineWidth,
                        BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_MITER,
                        miter, dash1, 0.0f);
                BasicStroke dashed2 =
                        new BasicStroke(constantLineWidth,
                        BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_MITER,
                        miter, dash1, constantLineWidth * 4);

                BoundingBox currentExtent; // = map.getCurrentExtent();
                double xRange; //= Math.abs(currentExtent.getMaxX() - currentExtent.getMinX());
                double yRange; //= Math.abs(currentExtent.getMaxY() - currentExtent.getMinY());

                // draw the page on the drawing area if it is visible
                if (map.isPageVisible() && !printingMap) {

                    g2.setColor(Color.GRAY);
                    g2.fillRect((int) (pageShadowSize),
                            (int) (pageShadowSize), (int) pageWidth,
                            (int) pageHeight);
                    g2.setColor(Color.WHITE);
                    g2.fillRect(0, 0, (int) pageWidth,
                            (int) pageHeight);

                    g2.setColor(Color.DARK_GRAY);
                    g2.drawRect(0, 0, (int) pageWidth,
                            (int) pageHeight);

                    if (margin > 0) {
                        // draw page margin marks
                        g2.setColor(Color.LIGHT_GRAY);
                        int tickSize = 7; // in points
                        g2.drawLine(margin, margin, margin, margin - tickSize);
                        g2.drawLine(margin, margin, margin - tickSize, margin);

                        g2.drawLine((int) (pageWidth - margin), margin, (int) (pageWidth - margin), margin - tickSize);
                        g2.drawLine((int) (pageWidth - margin), margin, (int) (pageWidth - margin + tickSize), margin);

                        g2.drawLine(margin, (int) (pageHeight - margin), margin, (int) (pageHeight - margin + tickSize));
                        g2.drawLine(margin, (int) (pageHeight - margin), margin - tickSize, (int) (pageHeight - margin));

                        g2.drawLine((int) (pageWidth - margin), (int) (pageHeight - margin), (int) (pageWidth - margin), (int) (pageHeight - margin + tickSize));
                        g2.drawLine((int) (pageWidth - margin), (int) (pageHeight - margin), (int) (pageWidth - margin + tickSize), (int) (pageHeight - margin));
                    }

                }
                
                //***************************
                // Draw cartographic elements
                //***************************

                Color selectedColour = Color.BLACK;

                double ppm = java.awt.Toolkit.getDefaultToolkit().getScreenResolution() * 39.3701;

                for (CartographicElement ce : map.getCartographicElementList()) {

                    if (ce instanceof MapScale) {
                        // Map scale
                        MapScale mapScale = (MapScale) ce;
                        if (mapScale.isVisible()) {
                            if (mapScale.getMapArea() != null) {
                                mapScale.setScale();
                            }
                            double scale = mapScale.getScale();
                            if (scale > 0 && scale != Double.NaN && scale < Double.POSITIVE_INFINITY) {
                                if (mapScale.getUpperLeftY() == -32768) {
                                    mapScale.setUpperLeftY((int) (pageHeight - margin - mapScale.getHeight()));
                                    mapScale.setUpperLeftX((int) (margin));
                                }

                                // set the stroke
                                oldStroke = g2.getStroke();
                                g2.setStroke(new BasicStroke(mapScale.getLineWidth()));

                                // draw the scale's box
                                if (mapScale.isBackgroundVisible()) {
                                    g2.setColor(mapScale.getBackColour());

                                    g2.fillRect(mapScale.getUpperLeftX(),
                                            mapScale.getUpperLeftY(),
                                            mapScale.getWidth(),
                                            mapScale.getHeight());
                                }

                                g2.setColor(mapScale.getLegendColour());

                                if (mapScale.isBorderVisible() || mapScale.isSelected()) {
                                    Stroke oldStroke2 = g2.getStroke();
                                    if (mapScale.isSelected() && !printingMap) {
                                        g2.setStroke(dashed);
                                        g2.setColor(selectedColour);
                                    } else {
                                        g2.setColor(mapScale.getBorderColour());
                                    }
                                    g2.drawRect(mapScale.getUpperLeftX(),
                                            mapScale.getUpperLeftY(),
                                            mapScale.getWidth(),
                                            mapScale.getHeight());
                                    g2.setStroke(oldStroke2);
                                }

                                g2.setColor(mapScale.getOutlineColour());

                                // set up the font
                                Font newfont = new Font("SanSerif", Font.PLAIN, 10);
                                g2.setFont(newfont);
                                metrics = g2.getFontMetrics(newfont);

                                // what is the content height?
                                int contentHeight = 0;
                                int spacingBetweenElements = 4;
                                int barHeight = 6;
                                int spacingBetweenBarAndLabels = 3;
                                int fontHeight = metrics.getHeight() - metrics.getDescent();
                                if (mapScale.isRepresentativeFractionVisible()) {
                                    contentHeight = fontHeight * 3
                                            + spacingBetweenElements * 2 + barHeight
                                            + spacingBetweenBarAndLabels;
                                } else {
                                    contentHeight = fontHeight * 2
                                            + spacingBetweenElements + barHeight
                                            + spacingBetweenBarAndLabels;
                                }

                                // make sure that the scale box's height is large enough for the content
                                if (contentHeight > (mapScale.getHeight() + 2 * mapScale.getMargin())) {
                                    mapScale.setHeight(contentHeight + 2 * mapScale.getMargin());
                                }

                                // calculate the bottom for all the content
                                int contentBottomY = (int) (mapScale.getUpperLeftY()
                                        + (mapScale.getHeight() - contentHeight) / 2.0
                                        + contentHeight);

                                // draw the units label to the scale
                                label = mapScale.getUnits();
                                adv = metrics.stringWidth(label);
                                x = mapScale.getUpperLeftX() + ((mapScale.getWidth() - adv) / 2);
                                y = contentBottomY;
                                g2.drawString(label, x, y);

                                // draw the scale bar to the scale
                                double barLengthInMapUnits = mapScale.getBarLength() * mapScale.getConversionToMetres()
                                        / scale * ppm / mapScale.getNumberDivisions();
                                double barStartingX = mapScale.getUpperLeftX() + ((mapScale.getWidth() - (mapScale.getBarLength() * mapScale.getConversionToMetres()
                                        / scale * ppm)) / 2);

                                y = contentBottomY - fontHeight - spacingBetweenElements - barHeight;
                                for (int k = 0; k < mapScale.getNumberDivisions(); k++) {
                                    x = (int) (k * barLengthInMapUnits + barStartingX);
                                    if (k % 2.0 == 0.0) {
                                        g2.drawRect(x, y, (int) barLengthInMapUnits, barHeight);
                                    } else {
                                        g2.drawRect(x, y, (int) barLengthInMapUnits, barHeight);
                                        g2.fillRect(x, y, (int) barLengthInMapUnits, barHeight);
                                    }
                                }

                                // label the scale bar
                                adv = metrics.stringWidth(mapScale.getLowerLabel());
                                x = (int) (barStartingX - adv / 2.0);
                                y = contentBottomY - fontHeight - spacingBetweenElements
                                        - barHeight - spacingBetweenBarAndLabels;
                                g2.drawString(mapScale.getLowerLabel(), x, y);

                                adv = metrics.stringWidth(mapScale.getUpperLabel());
                                x = (int) (barStartingX + barLengthInMapUnits * mapScale.getNumberDivisions() - adv / 2.0);
                                g2.drawString(mapScale.getUpperLabel(), x, y);

                                // label the representative fraction
                                if (mapScale.isRepresentativeFractionVisible()) {
                                    label = mapScale.getRepresentativeFraction();
                                    adv = metrics.stringWidth(label);
                                    x = mapScale.getUpperLeftX() + ((mapScale.getWidth() - adv) / 2);
                                    y = contentBottomY - fontHeight - spacingBetweenElements
                                            - barHeight - spacingBetweenBarAndLabels - fontHeight
                                            - spacingBetweenElements;
                                    g2.drawString(label, x, y);

                                }

                                g2.setStroke(oldStroke);
                                g2.setFont(font);
                            }
                        }

                    } else if (ce instanceof NorthArrow) {
                        NorthArrow northArrow = (NorthArrow) ce;
                        // north arrow
                        if (northArrow.isVisible()) {
                            if (northArrow.getX() == -32768) {
                                northArrow.setX((int) (pageWidth - margin - northArrow.getMarkerSize() / 2));
                                northArrow.setY((int) (pageHeight - margin - northArrow.getMarkerSize() / 2));
                            }

                            if (northArrow.isBackgroundVisible()) {
                                g2.setColor(northArrow.getBackColour());

                                g2.fillRect(northArrow.getUpperLeftX(),
                                        northArrow.getUpperLeftY(),
                                        northArrow.getWidth(),
                                        northArrow.getHeight());
                            }

                            if (northArrow.isBorderVisible() || northArrow.isSelected()) {
                                oldStroke = g2.getStroke();
                                if (northArrow.isSelected() && !printingMap) {
                                    g2.setColor(selectedColour);
                                    g2.setStroke(dashed);
                                } else {
                                    g2.setColor(northArrow.getBorderColour());
                                    g2.setStroke(new BasicStroke(northArrow.getLineWidth()));
                                }
                                g2.drawRect(northArrow.getUpperLeftX(),
                                        northArrow.getUpperLeftY(),
                                        northArrow.getWidth(),
                                        northArrow.getHeight());
                                g2.setStroke(oldStroke);
                            }

                            g2.setColor(northArrow.getOutlineColour());
                            oldStroke = g2.getStroke();
                            g2.setStroke(new BasicStroke(northArrow.getLineWidth()));
                            ArrayList<GeneralPath> gpList = northArrow.getMarkerData();
                            ArrayList<Integer> gpInstructions = northArrow.getMarkerDrawingInstructions();
                            for (int a = 0; a < gpList.size(); a++) {
                                GeneralPath gp = gpList.get(a);
                                Integer instruction = gpInstructions.get(a);
                                if (instruction.equals(0)) {
                                    g2.draw(gp);
                                } else {
                                    g2.fill(gp);
                                    g2.draw(gp);
                                }
                            }
                            g2.setStroke(oldStroke);
                        }

                    } else if (ce instanceof MapTitle) {
                        MapTitle mapTitle = (MapTitle) ce;
                        // Map title
                        if (mapTitle.isVisible()) {
                            if (mapTitle.getWidth() < 0) {

                                Font newFont = mapTitle.getLabelFont();
                                g2.setFont(newFont);
                                metrics = g2.getFontMetrics(newFont);
                                adv = metrics.stringWidth(mapTitle.getLabel());
                                mapTitle.setWidth(adv + 2 * mapTitle.getMargin());
                                mapTitle.setHeight(metrics.getHeight() + 2 * mapTitle.getMargin());

                                if (mapTitle.getUpperLeftX() == -32768) {
                                    mapTitle.setUpperLeftX((int) ((pageWidth - mapTitle.getWidth()) / 2.0));
                                    mapTitle.setUpperLeftY(margin);
                                }
                            }
                            if (mapTitle.isBackgroundVisible()) {
                                g2.setColor(mapTitle.getBackColour());

                                g2.fillRect(mapTitle.getUpperLeftX(),
                                        mapTitle.getUpperLeftY(),
                                        mapTitle.getWidth(),
                                        mapTitle.getHeight());
                            }

                            if (mapTitle.isBorderVisible() || mapTitle.isSelected()) {
                                oldStroke = g2.getStroke();
                                if (mapTitle.isSelected() && !printingMap) {
                                    g2.setColor(selectedColour);
                                    g2.setStroke(dashed);
                                } else {
                                    g2.setColor(mapTitle.getBorderColour());
                                    g2.setStroke(new BasicStroke(mapTitle.getLineWidth()));
                                }

                                g2.drawRect(mapTitle.getUpperLeftX(),
                                        mapTitle.getUpperLeftY(),
                                        mapTitle.getWidth(),
                                        mapTitle.getHeight());
                                g2.setStroke(oldStroke);
                                g2.setFont(font);
                            }
                            Font newFont = mapTitle.getLabelFont();
                            g2.setColor(mapTitle.getFontColour());
                            oldFont = g2.getFont();
                            g2.setFont(newFont);
                            metrics = g2.getFontMetrics(newFont);
                            int fontHeight = metrics.getHeight() - metrics.getDescent();
                            x = mapTitle.getUpperLeftX() + mapTitle.getMargin();
                            y = (int) (mapTitle.getUpperLeftY() + mapTitle.getMargin() + fontHeight);
                            //g2.drawString(mapTitle.getLabel(), x, y);

                            GlyphVector gv = newFont.createGlyphVector(g2.getFontRenderContext(), mapTitle.getLabel());
                            Shape sp = gv.getOutline();
                            //g2.setColor(mapTitle.getOutlineColour());
                            g2.translate(x, y);
                            g2.fill(sp);
                            g2.translate(-x, -y);

                            if (mapTitle.isOutlineVisible()) {
                                //GlyphVector gv = newFont.createGlyphVector(g2.getFontRenderContext(), mapTitle.getLabel());
                                //Shape sp = gv.getOutline();
                                g2.setColor(mapTitle.getOutlineColour());
                                g2.translate(x, y);
                                g2.draw(sp);
                                g2.translate(-x, -y);
                            }

                            g2.setFont(oldFont);
                        }

                    } else if (ce instanceof MapTextArea) {
                        MapTextArea mapTextArea = (MapTextArea) ce;
                        // Map title
                        if (mapTextArea.isVisible()) {
                            int x1, y1, x2, y2;
                            int labelMargin = mapTextArea.getMargin();
                            float interlineSpace = mapTextArea.getInterlineSpace();
                            x1 = mapTextArea.getUpperLeftX() + labelMargin;
                            y1 = mapTextArea.getUpperLeftY() + labelMargin;
                            x2 = mapTextArea.getLowerRightX() - labelMargin;
                            y2 = mapTextArea.getLowerRightY() - labelMargin;
                            
                            if (mapTextArea.isBackgroundVisible()) {
                                g2.setColor(mapTextArea.getBackColour());

                                g2.fillRect(mapTextArea.getUpperLeftX(), 
                                        mapTextArea.getUpperLeftY(),
                                        mapTextArea.getWidth(),
                                        mapTextArea.getHeight());
                            }

                            if (mapTextArea.isBorderVisible() || mapTextArea.isSelected()) {
                                oldStroke = g2.getStroke();
                                if (mapTextArea.isSelected() && !printingMap) {
                                    g2.setColor(selectedColour);
                                    g2.setStroke(dashed);
                                } else {
                                    g2.setColor(mapTextArea.getBorderColour());
                                    g2.setStroke(new BasicStroke(mapTextArea.getLineWidth()));
                                }

                                g2.drawRect(mapTextArea.getUpperLeftX(), 
                                        mapTextArea.getUpperLeftY(),
                                        mapTextArea.getWidth(),
                                        mapTextArea.getHeight());
                                g2.setStroke(oldStroke);
                            }
                            Font newFont = mapTextArea.getLabelFont();
                            g2.setColor(mapTextArea.getFontColour());
                            oldFont = g2.getFont();
                            g2.setFont(newFont);
                            
                            drawStringRect(g2, x1, y1, x2, y2, interlineSpace, 
                                    mapTextArea.getLabel());
                            g2.setFont(oldFont);

                            g2.setFont(oldFont);
                        }
                    } else if (ce instanceof NeatLine) {
                        NeatLine neatLine = (NeatLine) ce;
                        if (neatLine.isVisible()) {
                            if (neatLine.getUpperLeftY() == -32768) {
                                neatLine.setWidth((int) (pageWidth - 2 * margin));
                                neatLine.setHeight((int) (pageHeight - 2 * margin));
                                neatLine.setUpperLeftX(margin);
                                neatLine.setUpperLeftY(margin);

                            }

                            int neatLineULX = neatLine.getUpperLeftX();
                            int neatLineULY = neatLine.getUpperLeftY();
                            int neatLineLRX = neatLine.getLowerRightX();
                            int neatLineLRY = neatLine.getLowerRightY();
                            int neatLineWidth = neatLineLRX - neatLineULX;
                            int neatLineHeight = neatLineLRY - neatLineULY;

                            int gap = neatLine.getDoubleLineGap();
                            boolean isDoubleLine = neatLine.isDoubleLine();
                            float innerLineWidth = neatLine.getInnerLineWidth();
                            float outerLineWidth = neatLine.getOuterLineWidth();

                            if (neatLine.isBackgroundVisible()) {
                                g2.setColor(neatLine.getBackgroundColour());
                                if (isDoubleLine) {
                                    g2.fillRect(neatLineULX + gap,
                                            neatLineULY + gap,
                                            neatLineWidth - 2 * gap,
                                            neatLineHeight - 2 * gap);
                                } else {
                                    g2.fillRect(neatLineULX,
                                            neatLineULY,
                                            neatLineWidth,
                                            neatLineHeight);
                                }

                            }
                            if (neatLine.isVisible() || neatLine.isSelected()) {
                                oldStroke = g2.getStroke();
                                if (neatLine.isSelected() && !printingMap) {
                                    g2.setColor(selectedColour);
                                    g2.setStroke(dashed);
                                } else {
                                    g2.setColor(neatLine.getBorderColour());
                                    g2.setStroke(new BasicStroke(outerLineWidth));
                                }

                                if (isDoubleLine) {
                                    // outer line
                                    g2.drawRect(neatLineULX,
                                            neatLineULY,
                                            neatLineWidth,
                                            neatLineHeight);

                                    // inner line
                                    g2.setStroke(new BasicStroke(innerLineWidth));
                                    g2.drawRect(neatLineULX + gap,
                                            neatLineULY + gap,
                                            neatLineWidth - 2 * gap,
                                            neatLineHeight - 2 * gap);
                                } else {
                                    g2.drawRect(neatLineULX,
                                            neatLineULY,
                                            neatLineWidth,
                                            neatLineHeight);
                                }

                                g2.setStroke(oldStroke);
                            }


                        }
                    } else if (ce instanceof Legend) {
                        Legend legend = (Legend) ce;
                        if (legend.isVisible()) {
                            int legendMargin = legend.getMargin();
                            if (legend.getUpperLeftY() == -32768) {
                                // initialize its size and location
                                legend.setWidth((int) (2 * legendMargin + 120));
                                legend.setHeight((int) (2 * legendMargin + legend.getNumberOfLegendEntries() * 60));
                                legend.setUpperLeftX(margin);
                                legend.setUpperLeftY(margin);

                            }

                            int legendULX = legend.getUpperLeftX();
                            int legendULY = legend.getUpperLeftY();
                            int legendLRX = legend.getLowerRightX();
                            int legendLRY = legend.getLowerRightY();
                            int legendWidth = legendLRX - legendULX;
                            int legendHeight = legendLRY - legendULY;

                            Rectangle2D rect = new Rectangle2D.Float();
                            rect.setRect(legendULX, legendULY, legendWidth, legendHeight);
                            Shape oldClip = g2.getClip();
                            Rectangle2D clipRect = new Rectangle2D.Float();
                            clipRect.setRect(legendULX - 1, legendULY - 1, legendWidth + 2, legendHeight + 2);
                            g2.setClip(clipRect);

                            if (legend.isBackgroundVisible()) {
                                g2.setColor(legend.getBackgroundColour());
                                g2.fill(rect);
                            }

                            g2.setColor(Color.BLACK);
                            int top = legendULY + legendMargin;
                            int left = legendULX + legendMargin;
                            Font newFont = legend.getLabelFont();
                            g2.setFont(newFont);
                            metrics = g2.getFontMetrics(newFont);
                            int fontHeight = metrics.getHeight() - metrics.getDescent();
                            int gap = 10;
                            int imageHeight = 35;
                            int imageWidth = 12;
                            top += fontHeight;
                            label = "Legend";
                            adv = metrics.stringWidth(label);
                            g2.drawString(label, legendULX + (legendWidth - adv) / 2, top);
                            //top += 4;

                            for (MapArea ma : legend.getMapAreasList()) {
                                for (int k = ma.getNumLayers() - 1; k >= 0; k--) {
                                    MapLayer mapLayer = ma.getLayer(k);
                                    //for (MapLayer mapLayer : ma.getLayersList()) {
                                    if (mapLayer.isVisible()) {
                                        top += gap;
//                                        g2.setColor(ma.getFontColour());
//                                        g2.drawString(mapLayer.getLayerTitle(), left, top);
//                                        top += 4;
                                        if (mapLayer.getLayerType() == MapLayer.MapLayerType.RASTER) {
                                            g2.setColor(ma.getFontColour());
                                            g2.drawString(mapLayer.getLayerTitle(), left, top);
                                            top += 4;

                                            RasterLayerInfo rli = (RasterLayerInfo) mapLayer;
                                            PaletteImage paletteImage = new PaletteImage(18, 45, rli.getPaletteFile(), rli.isPaletteReversed(), PaletteImage.VERTICAL_ORIENTATION);
                                            if (rli.getDataScale() == WhiteboxRasterBase.DataScale.CATEGORICAL) {
                                                paletteImage.isCategorical(true, rli.getMinVal(), rli.getMaxVal());
                                            }
                                            if (rli.getNonlinearity() != 1) {
                                                paletteImage.setNonlinearity(rli.getNonlinearity());
                                            }
                                            Image img = paletteImage.getPaletteImage();

                                            if (!g2.drawImage(img, left, top, imageWidth, imageHeight, this)) {
                                                // do nothing
                                            }

                                            g2.drawRect(left, top, imageWidth, imageHeight);

                                            String maxVal = df.format(rli.getDisplayMaxVal());
                                            String minVal = df.format(rli.getDisplayMinVal());
                                            g2.drawString(maxVal, left + imageWidth + 4, top + fontHeight);
                                            g2.drawString(minVal, left + imageWidth + 4, top + imageHeight);

                                            top += 45;

                                        } else if (mapLayer.getLayerType() == MapLayer.MapLayerType.VECTOR) {
                                            VectorLayerInfo vli = (VectorLayerInfo) mapLayer;
                                            ShapeType st = vli.getShapeType();

                                            VectorLayerInfo.LegendEntry[] le = vli.getLegendEntries();
                                            if (le != null && le[0].getLegendLabel().equals("continuous numerical variable") && le[0].getLegendColour().equals(Color.black)) {
                                                // it's a continuous, scaled, numerical variable
                                                g2.setColor(ma.getFontColour());
                                                g2.drawString(mapLayer.getLayerTitle(), left, top);
                                                top += 4;

                                                PaletteImage paletteImage = new PaletteImage(18, 50, vli.getPaletteFile(), false, PaletteImage.VERTICAL_ORIENTATION);
                                                Image img = paletteImage.getPaletteImage();

                                                if (!g2.drawImage(img, left, top, imageWidth, imageHeight, this)) {
                                                    // do nothing
                                                }

                                                g2.drawRect(left, top, imageWidth, imageHeight);

                                                String maxVal = df.format(vli.getMaximumValue());
                                                String minVal = df.format(vli.getMinimumValue());
                                                g2.drawString(maxVal, left + imageWidth + 4, top + fontHeight);
                                                g2.drawString(minVal, left + imageWidth + 4, top + imageHeight);

                                                top += 45;

                                            } else {
                                                Color fillColour = vli.getFillColour();
                                                Color lineColour = vli.getLineColour();
                                                float lineThickness = vli.getLineThickness();
                                                //float markerSize = vli.getMarkerSize();
                                                boolean isFilled = vli.isFilled();
                                                boolean isOutlined = vli.isOutlined();
                                                boolean bentLine = true;
                                                double sqrSize = 16.0;
                                                double spacing = 3.0;
                                                /*
                                                 * figure out the width and
                                                 * height. This will depend on
                                                 * the fill method. If it is
                                                 * being filled using an
                                                 * attribute field then it may
                                                 * have a greater than normal
                                                 * height.
                                                 */
                                                boolean isFilledWithOneColour = vli.isFilledWithOneColour();
                                                boolean isOutlinedWithOneColour = vli.isOutlinedWithOneColour();

                                                int numEntries = 1;

                                                if (!isFilledWithOneColour || !isOutlinedWithOneColour) {
                                                    g2.setColor(ma.getFontColour());
                                                    g2.drawString(mapLayer.getLayerTitle(), left, top);
                                                    top += 4;

                                                    // how many legend entries are there?
                                                    le = vli.getLegendEntries();
                                                    numEntries = le.length;
                                                }
                                                int entryHeight = (int) sqrSize;
                                                int entryWidth = (int) sqrSize;
                                                top += spacing;

                                                double x1, y1;
                                                //g2.setColor(legend.getBackgroundColour());
                                                //g2.fillRect(0, 0, width, height);

                                                if (st == ShapeType.POLYGON || st == ShapeType.POLYGONM
                                                        || st == ShapeType.POLYGONZ || st == ShapeType.MULTIPATCH) {
                                                    //double top = 7.0;
                                                    double vecSampleBottom = top + entryHeight; //- 7.0;
                                                    //double left = 15.0;
                                                    double right = left + entryWidth; // - 15.0;
                                                    GeneralPath polyline;
                                                    if (isFilled) {
                                                        if (isFilledWithOneColour) {
                                                            g2.setColor(fillColour);
                                                            polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 4);
                                                            polyline.moveTo(left, vecSampleBottom);
                                                            polyline.lineTo(left, top);
                                                            polyline.lineTo(right, top);
                                                            polyline.lineTo(right, vecSampleBottom);
                                                            polyline.closePath();
                                                            g2.fill(polyline);
                                                        } else {
                                                            double t, b;
                                                            double r = left + sqrSize;
                                                            for (int j = 0; j < numEntries; j++) {
                                                                t = top + j * (sqrSize + spacing);
                                                                b = t + sqrSize;
                                                                g2.setColor(le[j].legendColour);
                                                                polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 4);
                                                                polyline.moveTo(left, b);
                                                                polyline.lineTo(left, t);
                                                                polyline.lineTo(r, t);
                                                                polyline.lineTo(r, b);
                                                                polyline.closePath();
                                                                g2.fill(polyline);
                                                            }
                                                        }

                                                    }

                                                    if (isOutlined) {
                                                        BasicStroke myStroke = new BasicStroke(lineThickness);
                                                        if (vli.isDashed()) {
                                                            myStroke =
                                                                    new BasicStroke(lineThickness,
                                                                    BasicStroke.CAP_BUTT,
                                                                    BasicStroke.JOIN_MITER,
                                                                    10.0f, vli.getDashArray(), 0.0f);
                                                        }
                                                        Stroke oldStroke2 = g2.getStroke();
                                                        g2.setStroke(myStroke);

                                                        g2.setColor(lineColour);
                                                        if (isFilledWithOneColour) {
                                                            polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 4);
                                                            polyline.moveTo(left, vecSampleBottom);
                                                            polyline.lineTo(left, top);
                                                            polyline.lineTo(right, top);
                                                            polyline.lineTo(right, vecSampleBottom);
                                                            polyline.closePath();
                                                            g2.draw(polyline);
                                                        } else {
                                                            double t, b;
                                                            double r = left + sqrSize;
                                                            for (int j = 0; j < numEntries; j++) {
                                                                t = top + j * (sqrSize + spacing);
                                                                b = t + sqrSize;
                                                                polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 4);
                                                                polyline.moveTo(left, b);
                                                                polyline.lineTo(left, t);
                                                                polyline.lineTo(r, t);
                                                                polyline.lineTo(r, b);
                                                                polyline.closePath();
                                                                g2.draw(polyline);
                                                            }
                                                        }
                                                        g2.setStroke(oldStroke2);
                                                    }

                                                    if (!isFilledWithOneColour) {
                                                        //Font font = new Font("SanSerif", Font.PLAIN, 11);
                                                        //g2d.setFont(font);
                                                        //FontMetrics metrics = g.getFontMetrics(font);
                                                        double hgt = metrics.getHeight() / 4.0;
                                                        g2.setColor(legend.getFontColour());
                                                        double vOffset = (sqrSize / 2.0) - hgt;
                                                        double t, b;
                                                        double r = left + sqrSize + 6;
                                                        for (int j = 0; j < numEntries; j++) {
                                                            t = top + j * (sqrSize + spacing);
                                                            b = t + sqrSize;
                                                            label = le[j].getLegendLabel().trim();
                                                            g2.drawString(label, (float) (r), (float) (b - vOffset));
                                                        }

                                                    } else {
                                                        double hgt = metrics.getHeight() / 4.0;
                                                        g2.setColor(legend.getFontColour());
                                                        double vOffset = (sqrSize / 2.0) - hgt;
                                                        double r = left + sqrSize + 6;
                                                        g2.drawString(vli.getLayerTitle(), (float) (r), (float) (top + sqrSize - vOffset));
                                                    }
                                                } else if (st == ShapeType.POINT
                                                        || st == ShapeType.POINTM || st == ShapeType.POINTZ
                                                        || st == ShapeType.MULTIPOINT || st == ShapeType.MULTIPOINTM
                                                        || st == ShapeType.MULTIPOINTZ) {

                                                    double[][] xyData = PointMarkers.getMarkerData(vli.getMarkerStyle(), vli.getMarkerSize());

                                                    if (isFilledWithOneColour) {
                                                        x1 = left + entryWidth / 2.0;
                                                        y1 = top + entryHeight / 2.0;
                                                        GeneralPath gp = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 1);
                                                        for (int a = 0; a < xyData.length; a++) {
                                                            if (xyData[a][0] == 0) { // moveTo
                                                                gp.moveTo(x1 + xyData[a][1], y1 + xyData[a][2]);
                                                            } else if (xyData[a][0] == 1) { // lineTo
                                                                gp.lineTo(x1 + xyData[a][1], y1 + xyData[a][2]);
                                                            } else if (xyData[a][0] == 2) { // elipse2D
                                                                Ellipse2D circle = new Ellipse2D.Double((x1 - xyData[a][1]), (y1 - xyData[a][1]), xyData[a][2], xyData[a][2]);
                                                                gp.append(circle, true);
                                                            }
                                                        }
                                                        if (isFilled) {
                                                            g2.setColor(fillColour);
                                                            g2.fill(gp);
                                                        }
                                                        if (isOutlined) {
                                                            BasicStroke myStroke = new BasicStroke(lineThickness);
                                                            Stroke oldStroke2 = g2.getStroke();
                                                            g2.setStroke(myStroke);

                                                            g2.setColor(lineColour);
                                                            g2.draw(gp);

                                                            g2.setStroke(oldStroke2);
                                                        }
                                                        double hgt = metrics.getHeight() / 4.0;
                                                        g2.setColor(legend.getFontColour());
                                                        double vOffset = (sqrSize / 2.0) - hgt;
                                                        double r = left + sqrSize + 6;
                                                        g2.drawString(vli.getLayerTitle(), (float) (r), (float) (top + sqrSize - vOffset));
                                                    } else {
                                                        double t;
                                                        for (int j = 0; j < numEntries; j++) {
                                                            t = legendMargin + j * (sqrSize + spacing);
                                                            x1 = legendMargin + sqrSize / 2.0;
                                                            y1 = t + sqrSize / 2.0;
                                                            GeneralPath gp = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 1);
                                                            for (int a = 0; a < xyData.length; a++) {
                                                                if (xyData[a][0] == 0) { // moveTo
                                                                    gp.moveTo(x1 + xyData[a][1], y1 + xyData[a][2]);
                                                                } else if (xyData[a][0] == 1) { // lineTo
                                                                    gp.lineTo(x1 + xyData[a][1], y1 + xyData[a][2]);
                                                                } else if (xyData[a][0] == 2) { // elipse2D
                                                                    Ellipse2D circle = new Ellipse2D.Double((x1 - xyData[a][1]), (y1 - xyData[a][1]), xyData[a][2], xyData[a][2]);
                                                                    gp.append(circle, true);
                                                                }
                                                            }
                                                            if (isFilled) {
                                                                g2.setColor(le[j].legendColour);
                                                                g2.fill(gp);
                                                            }
                                                            if (isOutlined) {
                                                                BasicStroke myStroke = new BasicStroke(lineThickness);
                                                                Stroke oldStroke2 = g2.getStroke();
                                                                g2.setStroke(myStroke);

                                                                g2.setColor(lineColour);
                                                                g2.draw(gp);

                                                                g2.setStroke(oldStroke2);
                                                            }
                                                        }
                                                        //Font font = new Font("SanSerif", Font.PLAIN, 11);
                                                        //g2d.setFont(font);
                                                        //FontMetrics metrics = g.getFontMetrics(font);
                                                        double hgt = metrics.getHeight() / 4.0; // why a quarter rather than a half? I really can't figure it out either. But it works.
                                                        g2.setColor(Color.BLACK);
                                                        double vOffset = (sqrSize / 2.0) - hgt;
                                                        double b;
                                                        double r = legendMargin + sqrSize + 6;
                                                        for (int j = 0; j < numEntries; j++) {
                                                            t = margin + j * (sqrSize + spacing);
                                                            b = t + sqrSize;
                                                            label = le[j].getLegendLabel().trim();
                                                            g2.drawString(label, (float) (r), (float) (b - vOffset));
                                                        }
                                                    }
                                                } else if (st == ShapeType.POLYLINE || st == ShapeType.POLYLINEM
                                                        | st == ShapeType.POLYLINEZ) {

                                                    double vecSampleBottom = top + entryHeight;
                                                    double right = left + entryWidth;
                                                    double oneThirdWidth = (right - left) / 3.0;

                                                    if (isOutlinedWithOneColour) {
                                                        if (lineColour.equals(Color.white)) {
                                                            g2.setColor(Color.LIGHT_GRAY);
                                                            g2.fillRect(left, top, entryWidth, entryHeight);

                                                        }
                                                        g2.setColor(lineColour);
                                                        BasicStroke myStroke = new BasicStroke(lineThickness);
                                                        if (vli.isDashed()) {
                                                            myStroke =
                                                                    new BasicStroke(lineThickness,
                                                                    BasicStroke.CAP_BUTT,
                                                                    BasicStroke.JOIN_MITER,
                                                                    10.0f, vli.getDashArray(), 0.0f);
                                                        }

                                                        Stroke oldStroke2 = g2.getStroke();
                                                        g2.setStroke(myStroke);

                                                        GeneralPath polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 4);
                                                        if (bentLine) {
                                                            polyline.moveTo(left, vecSampleBottom);
                                                            polyline.lineTo(left + oneThirdWidth, top);
                                                            polyline.lineTo(left + oneThirdWidth * 2, vecSampleBottom);
                                                            polyline.lineTo(left + oneThirdWidth * 3, top);
                                                        } else {
                                                            double middle = entryHeight / 2.0;
                                                            polyline.moveTo(left, middle);
                                                            polyline.lineTo(right, middle);
                                                        }
                                                        g2.draw(polyline);

                                                        g2.setStroke(oldStroke2);

                                                        double hgt = metrics.getHeight() / 4.0;
                                                        g2.setColor(legend.getFontColour());
                                                        double vOffset = (sqrSize / 2.0) - hgt;
                                                        double r = left + sqrSize + 6;
                                                        g2.drawString(vli.getLayerTitle(), (float) (r), (float) (top + sqrSize - vOffset));

                                                    } else {
                                                        BasicStroke myStroke = new BasicStroke(lineThickness);
                                                        Stroke oldStroke2 = g2.getStroke();
                                                        g2.setStroke(myStroke);

                                                        double t, b;
                                                        double r = margin + 2 * sqrSize;
                                                        oneThirdWidth = (r - margin) / 3.0;
                                                        for (int j = 0; j < numEntries; j++) {
                                                            t = margin + j * (sqrSize + spacing);
                                                            b = t + sqrSize;
                                                            x1 = margin + sqrSize / 2.0;
                                                            y1 = t + sqrSize / 2.0;
                                                            GeneralPath polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 1);
                                                            if (bentLine) {
                                                                polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 4);
                                                                polyline.moveTo(margin, b);
                                                                polyline.lineTo(margin + oneThirdWidth, t);
                                                                polyline.lineTo(margin + oneThirdWidth * 2, b);
                                                                polyline.lineTo(margin + oneThirdWidth * 3, t);
                                                            } else {
                                                                double middle = entryHeight / 2.0;
                                                                polyline.moveTo(left, middle);
                                                                polyline.lineTo(right, middle);
                                                            }
                                                            g2.setColor(le[j].legendColour);
                                                            g2.draw(polyline);

                                                        }
                                                        g2.setStroke(oldStroke2);

                                                        //Font font = new Font("SanSerif", Font.PLAIN, 11);
                                                        //g2d.setFont(font);
                                                        //FontMetrics metrics = g.getFontMetrics(font);
                                                        double hgt = metrics.getHeight() / 4.0; // why a quarter rather than a half? I really can't figure it out either. But it works.
                                                        g2.setColor(Color.BLACK);
                                                        double vOffset = (sqrSize / 2.0) - hgt;
                                                        r = margin + 2 * sqrSize + 6;
                                                        for (int j = 0; j < numEntries; j++) {
                                                            t = margin + j * (sqrSize + spacing);
                                                            b = t + sqrSize;
                                                            label = le[j].getLegendLabel().trim();
                                                            g2.drawString(label, (float) (r), (float) (b - vOffset));
                                                        }
                                                    }

                                                }

                                                top += entryHeight;
                                            }
                                        }
                                    }
                                }
                            }

                            g2.setClip(oldClip);

                            if (legend.isBorderVisible() || legend.isSelected()) {
                                oldStroke = g2.getStroke();
                                if (legend.isSelected() && !printingMap) {
                                    g2.setColor(selectedColour);
                                    g2.setStroke(dashed);
                                } else {
                                    g2.setColor(legend.getBorderColour());
                                    g2.setStroke(new BasicStroke(legend.getBorderWidth()));
                                }

                                g2.draw(rect);
                                g2.setStroke(oldStroke);
                                g2.setFont(font);
                            }


                        }
                    } else if (ce instanceof MapArea) {
                        MapArea mapArea = (MapArea) ce;
                        if (mapArea.isVisible()) {
//                            if (mapArea.getRotation() > 0) {
//                                g2.rotate(mapArea.getRotation());
//                            }
                            Font newFont = mapArea.getLabelFont();
                            g2.setColor(mapArea.getFontColour());
                            oldFont = g2.getFont();
                            g2.setFont(newFont);
                            metrics = g2.getFontMetrics(newFont);
                            int fontHeight = metrics.getHeight() - metrics.getDescent();

                            if (mapArea.getUpperLeftY() == -32768) {
                                // first set the reference marks size;
                                mapArea.setReferenceMarksSize(fontHeight + 2);

                                // now set the initial size
//                                int mapAreaSize = (int) (Math.min((pageHeight - 2 * margin - 4),
//                                        (pageWidth - 2 * margin - 4)));
//                                mapArea.setWidth(mapAreaSize);
//                                mapArea.setHeight(mapAreaSize);
                                int mapAreaWidth = (int) (pageWidth - 2 * margin - 4);
                                int mapAreaHeight = (int) (pageHeight - 2 * margin - 4);
                                mapArea.setWidth(mapAreaWidth);
                                mapArea.setHeight(mapAreaHeight);
//                                mapArea.setUpperLeftX((int) (margin + (pageWidth - 2 * margin - mapAreaSize) / 2));
//                                mapArea.setUpperLeftY((int) (margin + (pageHeight - 2 * margin - mapAreaSize) / 2));
                                mapArea.setUpperLeftX((int) (margin + (pageWidth - 2 * margin - mapAreaWidth) / 2));
                                mapArea.setUpperLeftY((int) (margin + (pageHeight - 2 * margin - mapAreaHeight) / 2));

                            }
                            int referenceMarkSize = mapArea.getReferenceMarksSize();

                            int mapAreaULX = mapArea.getUpperLeftX();
                            int mapAreaULY = mapArea.getUpperLeftY();
                            int mapAreaLRX = mapArea.getLowerRightX();
                            int mapAreaLRY = mapArea.getLowerRightY();
                            int mapAreaWidth = mapAreaLRX - mapAreaULX;
                            int mapAreaHeight = mapAreaLRY - mapAreaULY;
                            int viewAreaULX = mapArea.getUpperLeftX() + referenceMarkSize;
                            int viewAreaULY = mapArea.getUpperLeftY() + referenceMarkSize;
                            int viewAreaLRX = mapArea.getLowerRightX() - referenceMarkSize;
                            int viewAreaLRY = mapArea.getLowerRightY() - referenceMarkSize;
                            int viewAreaWidth = viewAreaLRX - viewAreaULX;
                            int viewAreaHeight = viewAreaLRY - viewAreaULY;

                            if (mapArea.isSizeMaximizedToScreenSize() && !printingMap) {
                                mapAreaULX = (int) (-pageLeft / scale);
                                mapAreaULY = (int) (-pageTop / scale);
                                mapAreaLRX = (int) ((-pageLeft + w) / scale);
                                mapAreaLRY = (int) ((-pageTop + h) / scale);
                                mapAreaWidth = mapAreaLRX - mapAreaULX;
                                mapAreaHeight = mapAreaLRY - mapAreaULY;
                                viewAreaULX = mapAreaULX + referenceMarkSize;
                                viewAreaULY = mapAreaULY + referenceMarkSize;
                                viewAreaLRX = viewAreaULX + (int) (w / scale - 2 * referenceMarkSize);
                                viewAreaLRY = viewAreaULY + (int) (h / scale - 2 * referenceMarkSize);
                                viewAreaWidth = viewAreaLRX - viewAreaULX;
                                viewAreaHeight = viewAreaLRY - viewAreaULY;
                            }

                            if (mapArea.isBackgroundVisible()) {
                                g2.setColor(mapArea.getBackgroundColour());

                                g2.fillRect(mapAreaULX, mapAreaULY,
                                        mapAreaWidth, mapAreaHeight);

                            }

                            String XYUnits = "";
                            double mapScale = 1;
                            int numLayers = mapArea.getNumLayers();
                            currentExtent = mapArea.getCurrentExtent();
                            xRange = Math.abs(currentExtent.getMaxX() - currentExtent.getMinX());
                            yRange = Math.abs(currentExtent.getMaxY() - currentExtent.getMinY());

                            mapScale = Math.min((viewAreaWidth / xRange), (viewAreaHeight / yRange));

                            // now draw the data into the MapArea
                            if (numLayers > 0) { // && (!mouseDragged || whichCartoElement != mapArea.getElementNumber())) {

                                if (scaleText != null && map.getActiveMapAreaOverlayNumber() == mapArea.getElementNumber()) {
                                    scaleText.setText(df.format(mapArea.getScale()));
                                }

                                int width, height;

                                // what are the edge coordinates of the actual map area
                                mapExtent.setMinX(currentExtent.getMinX() - (viewAreaWidth / mapScale - xRange) / 2);
                                mapExtent.setMaxX(currentExtent.getMaxX() + (viewAreaWidth / mapScale - xRange) / 2);
                                mapExtent.setMinY(currentExtent.getMinY() - (viewAreaHeight / mapScale - yRange) / 2);
                                mapExtent.setMaxY(currentExtent.getMaxY() + (viewAreaHeight / mapScale - yRange) / 2);

                                mapArea.setCurrentMapExtent(mapExtent);

                                for (int i = 0; i < numLayers; i++) {
                                    if (mapArea.getLayer(i).getLayerType() == MapLayer.MapLayerType.RASTER) {
                                        RasterLayerInfo layer = (RasterLayerInfo) mapArea.getLayer(i);
                                        if (mapArea.getXYUnits().trim().equals("")) {
                                            if (layer.getXYUnits().toLowerCase().contains("met")) {
                                                XYUnits = " m";
                                            } else if (layer.getXYUnits().toLowerCase().contains("deg")) {
                                                XYUnits = "\u00B0";
                                            } else if (!layer.getXYUnits().toLowerCase().contains("not specified")) {
                                                XYUnits = " " + layer.getXYUnits();
                                            }
                                            mapArea.setXYUnits(XYUnits);
                                        }

                                        if (layer.isVisible()) {

                                            BoundingBox fe = layer.getFullExtent();
                                            if (fe.overlaps(mapExtent)) {
                                                BoundingBox layerCE = fe.intersect(mapExtent);
                                                layer.setCurrentExtent(layerCE);
                                                x = (int) (viewAreaULX + (layerCE.getMinX() - mapExtent.getMinX()) * mapScale);
                                                y = (int) (viewAreaULY + (mapExtent.getMaxY() - layerCE.getMaxY()) * mapScale);
                                                int layerWidth = (int) ((Math.abs(layerCE.getMaxX() - layerCE.getMinX())) * mapScale);
                                                int layerHeight = (int) ((Math.abs(layerCE.getMaxY() - layerCE.getMinY())) * mapScale);

                                                int startR = (int) (Math.abs(layer.fullExtent.getMaxY() - layerCE.getMaxY()) / layer.getCellSizeY());
                                                int endR = (int) (layer.getNumberRows() - (Math.abs(layer.fullExtent.getMinY() - layerCE.getMinY()) / layer.getCellSizeY()));
                                                int startC = (int) (Math.abs(layer.fullExtent.getMinX() - layerCE.getMinX()) / layer.getCellSizeX());
                                                int endC = (int) (layer.getNumberColumns() - (Math.abs(layer.fullExtent.getMaxX() - layerCE.getMaxX()) / layer.getCellSizeX()));
                                                int numRows = endR - startR;
                                                int numCols = endC - startC;

                                                //if (!printingMap) {
                                                int res = (int) (Math.min(numRows / (double) layerHeight, numCols / (double) layerWidth));
                                                layer.setResolutionFactor(res);
                                                //} else {
                                                //    layer.setResolutionFactor(1);
                                                //}

                                                if (layer.isDirty()) {
                                                    layer.createPixelData();
                                                }

                                                width = layer.getImageWidth();
                                                height = layer.getImageHeight();
                                                Image image = createImage(new MemoryImageSource(width, height, layer.getPixelData(), 0, width));
                                                if (!g2.drawImage(image, x, y, layerWidth, layerHeight, this)) {
                                                    g2.drawImage(image, x, y, layerWidth, layerHeight, this);
                                                }

                                            }
                                        }
                                    } else if (mapArea.getLayer(i).getLayerType() == MapLayer.MapLayerType.VECTOR) {

                                        Rectangle2D rect = new Rectangle2D.Float();
                                        rect.setRect(viewAreaULX, viewAreaULY, viewAreaWidth, viewAreaHeight);
                                        Shape oldClip = null;

                                        VectorLayerInfo layer = (VectorLayerInfo) mapArea.getLayer(i);
                                        if (mapArea.getXYUnits().trim().equals("")) {
                                            if (layer.getXYUnits().toLowerCase().contains("met")) {
                                                XYUnits = " m";
                                            } else if (layer.getXYUnits().toLowerCase().contains("deg")) {
                                                XYUnits = "\u00B0";
                                            } else if (!layer.getXYUnits().toLowerCase().contains("not specified")) {
                                                XYUnits = " " + layer.getXYUnits();
                                            }
                                            mapArea.setXYUnits(XYUnits);
                                        }
                                        // is it the active layer?
                                        int selectedFeature = -1;
                                        boolean activeLayerBool = false;
                                        if (backgroundMouseMode == MOUSE_MODE_SELECT && mapArea.getActiveLayerOverlayNumber() == layer.getOverlayNumber()) {
                                            selectedFeature = layer.getSelectedFeatureNumber();
                                            activeLayerBool = true;
                                        } else if (layer.getSelectedFeatureNumber() >= 0) {
                                            layer.setSelectedFeatureNumber(-1);
                                        }
                                        float xPoint, yPoint;
                                        /*
                                         * minDistinguishableLength is used to
                                         * speed up the drawing of vectors. Any
                                         * feature that is smaller than this
                                         * value will be excluded from the map.
                                         * This is an example of cartographic
                                         * generalization.
                                         */
                                        double minDistinguishableLength = layer.getCartographicGeneralizationLevel() / mapScale; //scale;

                                        int r;

                                        if (layer.isVisible()) {
                                            BoundingBox fe = layer.getFullExtent();
                                            if (fe.overlaps(mapExtent)) {
                                                // only set the clip region if this layer's bounding box actually intersects the
                                                // boundary of the mapExtent.
                                                boolean isClipped = false;
                                                if (!fe.entirelyContainedWithin(mapExtent)) {
                                                    oldClip = g2.getClip();
                                                    g2.setClip(rect);
                                                    isClipped = true;
                                                }
                                                BoundingBox layerCE = fe.intersect(mapExtent);
                                                layer.setCurrentExtent(layerCE, minDistinguishableLength);
                                                int a1 = layer.getAlpha();
                                                //Color fillColour = new Color(r1, g1, b1, a1);
                                                int r1 = layer.getLineColour().getRed();
                                                int g1 = layer.getLineColour().getGreen();
                                                int b1 = layer.getLineColour().getBlue();
                                                Color lineColour = new Color(r1, g1, b1, a1);

                                                ShapeType shapeType = layer.getShapeType();
                                                //ShapeFileRecord[] records = layer.getGeometry();
                                                ArrayList<ShapeFileRecord> records = layer.getData();
                                                double x1, y1;
                                                //int xInt, yInt, x2Int, y2Int;
                                                double topCoord = mapExtent.getMaxY();
                                                double bottomCoord = mapExtent.getMinY();
                                                double leftCoord = mapExtent.getMinX();
                                                double rightCoord = mapExtent.getMaxX();
                                                double EWRange = rightCoord - leftCoord;
                                                double NSRange = topCoord - bottomCoord;

                                                double[][] xyData;
                                                GeneralPath gp;
                                                BasicStroke myStroke;
                                                Color[] colours = layer.getColourData();
                                                boolean isFilled = layer.isFilled();
                                                boolean isOutlined = layer.isOutlined();
                                                double[][] recPoints;

                                                int[] partStart;
                                                double[][] points;
                                                int pointSt;
                                                int pointEnd;
                                                float xPoints[];
                                                float yPoints[];
                                                GeneralPath polyline;

                                                switch (shapeType) {

                                                    case POINT:
                                                        xyData = PointMarkers.getMarkerData(layer.getMarkerStyle(), layer.getMarkerSize());
                                                        myStroke = new BasicStroke(layer.getLineThickness());
                                                        oldStroke = g2.getStroke();
                                                        g2.setStroke(myStroke);

                                                        for (ShapeFileRecord record : records) {
                                                            r = record.getRecordNumber() - 1;
                                                            if (record.getShapeType() != ShapeType.NULLSHAPE) {
                                                                whitebox.geospatialfiles.shapefile.Point rec = (whitebox.geospatialfiles.shapefile.Point) (record.getGeometry());
                                                                x1 = rec.getX();
                                                                y1 = rec.getY();
                                                                if (y1 < bottomCoord || x1 < leftCoord
                                                                        || y1 > topCoord || x1 > rightCoord) {
                                                                    // It's not within the map area; do nothing.
                                                                } else {
                                                                    x1 = (viewAreaULX + (x1 - leftCoord) / EWRange * viewAreaWidth);
                                                                    y1 = (viewAreaULY + (topCoord - y1) / NSRange * viewAreaHeight);
                                                                    gp = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 1);
                                                                    for (int a = 0; a < xyData.length; a++) {
                                                                        if (xyData[a][0] == 0) { // moveTo
                                                                            gp.moveTo(x1 + xyData[a][1], y1 + xyData[a][2]);
                                                                        } else if (xyData[a][0] == 1) { // lineTo
                                                                            gp.lineTo(x1 + xyData[a][1], y1 + xyData[a][2]);
                                                                        } else if (xyData[a][0] == 2) { // elipse2D
                                                                            Ellipse2D circle = new Ellipse2D.Double((x1 - xyData[a][1]), (y1 - xyData[a][1]), xyData[a][2], xyData[a][2]);

                                                                            gp.append(circle, true);
                                                                        }
                                                                    }
                                                                    //circle = new Ellipse2D.Double((x1 - halfMS), (y1 - halfMS), markerSize, markerSize);
                                                                    if (isFilled) {
                                                                        g2.setColor(colours[r]);
                                                                        g2.fill(gp);
                                                                    }
                                                                    if (isOutlined) {
                                                                        g2.setColor(lineColour);
                                                                        g2.draw(gp);
                                                                    }
                                                                    if (activeLayerBool && record.getRecordNumber() == selectedFeature) {
                                                                        g2.setColor(selectedFeatureColour);
                                                                        g2.draw(gp);
                                                                    }

                                                                }
                                                            }
                                                        }
                                                        g2.setStroke(oldStroke);
                                                        break;
                                                    case POINTZ:
                                                        xyData = PointMarkers.getMarkerData(layer.getMarkerStyle(), layer.getMarkerSize());
                                                        myStroke = new BasicStroke(layer.getLineThickness());
                                                        oldStroke = g2.getStroke();
                                                        g2.setStroke(myStroke);

                                                        for (ShapeFileRecord record : records) {
                                                            r = record.getRecordNumber() - 1;
                                                            if (record.getShapeType() != ShapeType.NULLSHAPE) {
                                                                PointZ rec = (PointZ) (record.getGeometry());
                                                                x1 = rec.getX();
                                                                y1 = rec.getY();
                                                                if (y1 < bottomCoord || x1 < leftCoord
                                                                        || y1 > topCoord || x1 > rightCoord) {
                                                                    // It's not within the map area; do nothing.
                                                                } else {
                                                                    x1 = (viewAreaULX + (x1 - leftCoord) / EWRange * viewAreaWidth);
                                                                    y1 = (viewAreaULY + (topCoord - y1) / NSRange * viewAreaHeight);
                                                                    gp = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 1);
                                                                    for (int a = 0; a < xyData.length; a++) {
                                                                        if (xyData[a][0] == 0) { // moveTo
                                                                            gp.moveTo(x1 + xyData[a][1], y1 + xyData[a][2]);
                                                                        } else if (xyData[a][0] == 1) { // lineTo
                                                                            gp.lineTo(x1 + xyData[a][1], y1 + xyData[a][2]);
                                                                        } else if (xyData[a][0] == 2) { // elipse2D
                                                                            Ellipse2D circle = new Ellipse2D.Double((x1 - xyData[a][1]), (y1 - xyData[a][1]), xyData[a][2], xyData[a][2]);

                                                                            gp.append(circle, true);
                                                                        }
                                                                    }
                                                                    if (isFilled) {
                                                                        g2.setColor(colours[r]);
                                                                        g2.fill(gp);
                                                                    }
                                                                    if (isOutlined) {
                                                                        g2.setColor(lineColour);
                                                                        g2.draw(gp);
                                                                    }
                                                                    if (activeLayerBool && record.getRecordNumber() == selectedFeature) {
                                                                        g2.setColor(selectedFeatureColour);
                                                                        g2.draw(gp);
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        g2.setStroke(oldStroke);
                                                        break;
                                                    case POINTM:
                                                        xyData = PointMarkers.getMarkerData(layer.getMarkerStyle(), layer.getMarkerSize());
                                                        myStroke = new BasicStroke(layer.getLineThickness());
                                                        oldStroke = g2.getStroke();
                                                        g2.setStroke(myStroke);

                                                        for (ShapeFileRecord record : records) {
                                                            r = record.getRecordNumber() - 1;
                                                            if (record.getShapeType() != ShapeType.NULLSHAPE) {
                                                                PointM rec = (PointM) (record.getGeometry());
                                                                x1 = rec.getX();
                                                                y1 = rec.getY();
                                                                if (y1 < bottomCoord || x1 < leftCoord
                                                                        || y1 > topCoord || x1 > rightCoord) {
                                                                    // It's not within the map area; do nothing.
                                                                } else {
                                                                    x1 = (viewAreaULX + (x1 - leftCoord) / EWRange * viewAreaWidth);
                                                                    y1 = (viewAreaULY + (topCoord - y1) / NSRange * viewAreaHeight);
                                                                    gp = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 1);
                                                                    for (int a = 0; a < xyData.length; a++) {
                                                                        if (xyData[a][0] == 0) { // moveTo
                                                                            gp.moveTo(x1 + xyData[a][1], y1 + xyData[a][2]);
                                                                        } else if (xyData[a][0] == 1) { // lineTo
                                                                            gp.lineTo(x1 + xyData[a][1], y1 + xyData[a][2]);
                                                                        } else if (xyData[a][0] == 2) { // elipse2D
                                                                            Ellipse2D circle = new Ellipse2D.Double((x1 - xyData[a][1]), (y1 - xyData[a][1]), xyData[a][2], xyData[a][2]);

                                                                            gp.append(circle, true);
                                                                        }
                                                                    }
                                                                    //circle = new Ellipse2D.Double((x1 - halfMS), (y1 - halfMS), markerSize, markerSize);
                                                                    if (isFilled) {
                                                                        g2.setColor(colours[r]);
                                                                        g2.fill(gp);
                                                                    }
                                                                    if (isOutlined) {
                                                                        g2.setColor(lineColour);
                                                                        g2.draw(gp);
                                                                    }
                                                                    if (activeLayerBool && record.getRecordNumber() == selectedFeature) {
                                                                        g2.setColor(selectedFeatureColour);
                                                                        g2.draw(gp);
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        g2.setStroke(oldStroke);
                                                        break;
                                                    case MULTIPOINT:
                                                        xyData = PointMarkers.getMarkerData(layer.getMarkerStyle(), layer.getMarkerSize());
                                                        myStroke = new BasicStroke(layer.getLineThickness());
                                                        oldStroke = g2.getStroke();
                                                        g2.setStroke(myStroke);

                                                        for (ShapeFileRecord record : records) {
                                                            r = record.getRecordNumber() - 1;
                                                            if (record.getShapeType() != ShapeType.NULLSHAPE) {
                                                                MultiPoint rec = (MultiPoint) (record.getGeometry());
                                                                recPoints = rec.getPoints();
                                                                for (int p = 0; p < recPoints.length; p++) {
                                                                    x1 = recPoints[p][0];
                                                                    y1 = recPoints[p][1];
                                                                    if (y1 < bottomCoord || x1 < leftCoord
                                                                            || y1 > topCoord || x1 > rightCoord) {
                                                                        // It's not within the map area; do nothing.
                                                                    } else {
                                                                        x1 = (viewAreaULX + (x1 - leftCoord) / EWRange * viewAreaWidth);
                                                                        y1 = (viewAreaULY + (topCoord - y1) / NSRange * viewAreaHeight);

                                                                        gp = new GeneralPath(GeneralPath.WIND_EVEN_ODD, xyData.length);
                                                                        for (int a = 0; a < xyData.length; a++) {
                                                                            if (xyData[a][0] == 0) { // moveTo
                                                                                gp.moveTo(x1 + xyData[a][1], y1 + xyData[a][2]);
                                                                            } else if (xyData[a][0] == 1) { // lineTo
                                                                                gp.lineTo(x1 + xyData[a][1], y1 + xyData[a][2]);
                                                                            } else if (xyData[a][0] == 2) { // elipse2D
                                                                                Ellipse2D circle = new Ellipse2D.Double((x1 - xyData[a][1]), (y1 - xyData[a][1]), xyData[a][2], xyData[a][2]);

                                                                                gp.append(circle, true);
                                                                            }
                                                                        }
                                                                        if (isFilled) {
                                                                            g2.setColor(colours[r]);
                                                                            g2.fill(gp);
                                                                        }
                                                                        if (isOutlined) {
                                                                            g2.setColor(lineColour);
                                                                            g2.draw(gp);
                                                                        }
                                                                        if (activeLayerBool && record.getRecordNumber() == selectedFeature) {
                                                                            g2.setColor(selectedFeatureColour);
                                                                            g2.draw(gp);
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        g2.setStroke(oldStroke);
                                                        break;
                                                    case MULTIPOINTZ:
                                                        xyData = PointMarkers.getMarkerData(layer.getMarkerStyle(), layer.getMarkerSize());
                                                        myStroke = new BasicStroke(layer.getLineThickness());
                                                        oldStroke = g2.getStroke();
                                                        g2.setStroke(myStroke);

                                                        for (ShapeFileRecord record : records) {
                                                            r = record.getRecordNumber() - 1;
                                                            if (record.getShapeType() != ShapeType.NULLSHAPE) {
                                                                MultiPointZ rec = (MultiPointZ) (record.getGeometry());
                                                                recPoints = rec.getPoints();
                                                                for (int p = 0; p < recPoints.length; p++) {
                                                                    x1 = recPoints[p][0];
                                                                    y1 = recPoints[p][1];
                                                                    if (y1 < bottomCoord || x1 < leftCoord
                                                                            || y1 > topCoord || x1 > rightCoord) {
                                                                        // It's not within the map area; do nothing.
                                                                    } else {
                                                                        x1 = (viewAreaULX + (x1 - leftCoord) / EWRange * viewAreaWidth);
                                                                        y1 = (viewAreaULY + (topCoord - y1) / NSRange * viewAreaHeight);

                                                                        gp = new GeneralPath(GeneralPath.WIND_EVEN_ODD, xyData.length);
                                                                        for (int a = 0; a < xyData.length; a++) {
                                                                            if (xyData[a][0] == 0) { // moveTo
                                                                                gp.moveTo(x1 + xyData[a][1], y1 + xyData[a][2]);
                                                                            } else if (xyData[a][0] == 1) { // lineTo
                                                                                gp.lineTo(x1 + xyData[a][1], y1 + xyData[a][2]);
                                                                            } else if (xyData[a][0] == 2) { // elipse2D
                                                                                Ellipse2D circle = new Ellipse2D.Double((x1 - xyData[a][1]), (y1 - xyData[a][1]), xyData[a][2], xyData[a][2]);

                                                                                gp.append(circle, true);
                                                                            }
                                                                        }
                                                                        if (isFilled) {
                                                                            g2.setColor(colours[r]);
                                                                            g2.fill(gp);
                                                                        }
                                                                        if (isOutlined) {
                                                                            g2.setColor(lineColour);
                                                                            g2.draw(gp);
                                                                        }
                                                                        if (activeLayerBool && record.getRecordNumber() == selectedFeature) {
                                                                            g2.setColor(selectedFeatureColour);
                                                                            g2.draw(gp);
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        g2.setStroke(oldStroke);
                                                        break;
                                                    case MULTIPOINTM:
                                                        xyData = PointMarkers.getMarkerData(layer.getMarkerStyle(), layer.getMarkerSize());
                                                        myStroke = new BasicStroke(layer.getLineThickness());
                                                        oldStroke = g2.getStroke();
                                                        g2.setStroke(myStroke);

                                                        for (ShapeFileRecord record : records) {
                                                            r = record.getRecordNumber() - 1;
                                                            if (record.getShapeType() != ShapeType.NULLSHAPE) {
                                                                MultiPointM rec = (MultiPointM) (record.getGeometry());
                                                                recPoints = rec.getPoints();
                                                                for (int p = 0; p < recPoints.length; p++) {
                                                                    x1 = recPoints[p][0];
                                                                    y1 = recPoints[p][1];
                                                                    if (y1 < bottomCoord || x1 < leftCoord
                                                                            || y1 > topCoord || x1 > rightCoord) {
                                                                        // It's not within the map area; do nothing.
                                                                    } else {
                                                                        x1 = (viewAreaULX + (x1 - leftCoord) / EWRange * viewAreaWidth);
                                                                        y1 = (viewAreaULY + (topCoord - y1) / NSRange * viewAreaHeight);

                                                                        gp = new GeneralPath(GeneralPath.WIND_EVEN_ODD, xyData.length);
                                                                        for (int a = 0; a < xyData.length; a++) {
                                                                            if (xyData[a][0] == 0) { // moveTo
                                                                                gp.moveTo(x1 + xyData[a][1], y1 + xyData[a][2]);
                                                                            } else if (xyData[a][0] == 1) { // lineTo
                                                                                gp.lineTo(x1 + xyData[a][1], y1 + xyData[a][2]);
                                                                            } else if (xyData[a][0] == 2) { // elipse2D
                                                                                Ellipse2D circle = new Ellipse2D.Double((x1 - xyData[a][1]), (y1 - xyData[a][1]), xyData[a][2], xyData[a][2]);

                                                                                gp.append(circle, true);
                                                                            }
                                                                        }
                                                                        if (isFilled) {
                                                                            g2.setColor(colours[r]);
                                                                            g2.fill(gp);
                                                                        }
                                                                        if (isOutlined) {
                                                                            g2.setColor(lineColour);
                                                                            g2.draw(gp);
                                                                        }
                                                                        if (activeLayerBool && record.getRecordNumber() == selectedFeature) {
                                                                            g2.setColor(selectedFeatureColour);
                                                                            g2.draw(gp);
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        g2.setStroke(oldStroke);
                                                        break;
                                                    case POLYLINE:
                                                        //g2.setColor(lineColour);
                                                        myStroke = new BasicStroke(layer.getLineThickness());
                                                        if (layer.isDashed()) {
                                                            myStroke =
                                                                    new BasicStroke(layer.getLineThickness(),
                                                                    BasicStroke.CAP_BUTT,
                                                                    BasicStroke.JOIN_MITER,
                                                                    10.0f, layer.getDashArray(), 0.0f);
                                                        }
                                                        oldStroke = g2.getStroke();
                                                        g2.setStroke(myStroke);

                                                        for (ShapeFileRecord record : records) {
                                                            r = record.getRecordNumber() - 1;
                                                            if (record.getShapeType() != ShapeType.NULLSHAPE) {
                                                                PolyLine rec = (PolyLine) (record.getGeometry());
                                                                partStart = rec.getParts();
                                                                points = rec.getPoints();
                                                                for (int p = 0; p < rec.getNumParts(); p++) {
                                                                    pointSt = partStart[p];
                                                                    if (p < rec.getNumParts() - 1) {
                                                                        pointEnd = partStart[p + 1];
                                                                    } else {
                                                                        pointEnd = points.length;
                                                                    }
                                                                    xPoints = new float[pointEnd - pointSt];
                                                                    yPoints = new float[pointEnd - pointSt];
                                                                    for (int k = pointSt; k < pointEnd; k++) {
                                                                        xPoint = (float) (viewAreaULX + (points[k][0] - leftCoord) / EWRange * viewAreaWidth);
                                                                        yPoint = (float) (viewAreaULY + (topCoord - points[k][1]) / NSRange * viewAreaHeight);
                                                                        xPoints[k - pointSt] = xPoint;
                                                                        yPoints[k - pointSt] = yPoint;
                                                                    }
                                                                    polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, xPoints.length);

                                                                    polyline.moveTo(xPoints[0], yPoints[0]);

                                                                    for (int index = 1; index < xPoints.length; index++) {
                                                                        polyline.lineTo(xPoints[index], yPoints[index]);
                                                                    }
                                                                    if (!activeLayerBool || record.getRecordNumber() != selectedFeature) {
                                                                        g2.setColor(colours[r]);
                                                                    } else if (activeLayerBool && record.getRecordNumber() == selectedFeature) {
                                                                        g2.setColor(selectedFeatureColour);
                                                                    }
                                                                    g2.draw(polyline);
                                                                }

                                                                if (activeLayerBool && backgroundMouseMode == MOUSE_MODE_SELECT) {
                                                                    BoundingBox bb = rec.getBox();
                                                                    if (bb.isPointInBox(mapX, mapY)) {
                                                                        g2.setColor(selectionBoxColour);
                                                                        polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 5);
                                                                        xPoint = (float) (viewAreaULX + (bb.getMinX() - leftCoord) / EWRange * viewAreaWidth);
                                                                        yPoint = (float) (viewAreaULY + (topCoord - bb.getMinY()) / NSRange * viewAreaHeight);
                                                                        float xPoint2 = (float) (viewAreaULX + (bb.getMaxX() - leftCoord) / EWRange * viewAreaWidth);
                                                                        float yPoint2 = (float) (viewAreaULY + (topCoord - bb.getMaxY()) / NSRange * viewAreaHeight);
                                                                        polyline.moveTo(xPoint, yPoint);
                                                                        polyline.lineTo(xPoint, yPoint2);
                                                                        polyline.lineTo(xPoint2, yPoint2);
                                                                        polyline.lineTo(xPoint2, yPoint);
                                                                        polyline.lineTo(xPoint, yPoint);

                                                                        g2.draw(polyline);

                                                                        Ellipse2D circle = new Ellipse2D.Double(xPoint + (xPoint2 - xPoint) / 2 - 2, yPoint + (yPoint2 - yPoint) / 2 - 2, 4, 4);
                                                                        g2.fill(circle);

                                                                    }
                                                                }
                                                            }
                                                        }
                                                        g2.setStroke(oldStroke);
                                                        break;
                                                    case POLYLINEZ:
                                                        myStroke = new BasicStroke(layer.getLineThickness());
                                                        if (layer.isDashed()) {
                                                            myStroke =
                                                                    new BasicStroke(layer.getLineThickness(),
                                                                    BasicStroke.CAP_BUTT,
                                                                    BasicStroke.JOIN_MITER,
                                                                    10.0f, layer.getDashArray(), 0.0f);
                                                        }
                                                        oldStroke = g2.getStroke();
                                                        g2.setStroke(myStroke);
                                                        for (ShapeFileRecord record : records) {
                                                            r = record.getRecordNumber() - 1;
                                                            if (record.getShapeType() != ShapeType.NULLSHAPE) {
                                                                PolyLineZ rec = (PolyLineZ) (record.getGeometry());
                                                                partStart = rec.getParts();
                                                                points = rec.getPoints();
                                                                for (int p = 0; p < rec.getNumParts(); p++) {
                                                                    pointSt = partStart[p];
                                                                    if (p < rec.getNumParts() - 1) {
                                                                        pointEnd = partStart[p + 1];
                                                                    } else {
                                                                        pointEnd = points.length;
                                                                    }
                                                                    xPoints = new float[pointEnd - pointSt];
                                                                    yPoints = new float[pointEnd - pointSt];
                                                                    for (int k = pointSt; k < pointEnd; k++) {
                                                                        xPoints[k - pointSt] = (float) (viewAreaULX + (points[k][0] - leftCoord) / EWRange * viewAreaWidth);
                                                                        yPoints[k - pointSt] = (float) (viewAreaULY + (topCoord - points[k][1]) / NSRange * viewAreaHeight);
                                                                    }
                                                                    polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, xPoints.length);

                                                                    polyline.moveTo(xPoints[0], yPoints[0]);

                                                                    for (int index = 1; index < xPoints.length; index++) {
                                                                        polyline.lineTo(xPoints[index], yPoints[index]);
                                                                    }
                                                                    if (!activeLayerBool || record.getRecordNumber() != selectedFeature) {
                                                                        g2.setColor(colours[r]);
                                                                    } else if (activeLayerBool && record.getRecordNumber() == selectedFeature) {
                                                                        g2.setColor(selectedFeatureColour);
                                                                    }
                                                                    g2.draw(polyline);
                                                                }

                                                                if (activeLayerBool && backgroundMouseMode == MOUSE_MODE_SELECT) {
                                                                    BoundingBox bb = rec.getBox();
                                                                    if (bb.isPointInBox(mapX, mapY)) {
                                                                        g2.setColor(selectionBoxColour);
                                                                        polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 5);
                                                                        xPoint = (float) (viewAreaULX + (bb.getMinX() - leftCoord) / EWRange * viewAreaWidth);
                                                                        yPoint = (float) (viewAreaULY + (topCoord - bb.getMinY()) / NSRange * viewAreaHeight);
                                                                        float xPoint2 = (float) (viewAreaULX + (bb.getMaxX() - leftCoord) / EWRange * viewAreaWidth);
                                                                        float yPoint2 = (float) (viewAreaULY + (topCoord - bb.getMaxY()) / NSRange * viewAreaHeight);
                                                                        polyline.moveTo(xPoint, yPoint);
                                                                        polyline.lineTo(xPoint, yPoint2);
                                                                        polyline.lineTo(xPoint2, yPoint2);
                                                                        polyline.lineTo(xPoint2, yPoint);
                                                                        polyline.lineTo(xPoint, yPoint);

                                                                        g2.draw(polyline);

                                                                        Ellipse2D circle = new Ellipse2D.Double(xPoint + (xPoint2 - xPoint) / 2 - 2, yPoint + (yPoint2 - yPoint) / 2 - 2, 4, 4);
                                                                        g2.fill(circle);

                                                                    }
                                                                }
                                                            }
                                                        }
                                                        g2.setStroke(oldStroke);
                                                        break;
                                                    case POLYLINEM:
                                                        myStroke = new BasicStroke(layer.getLineThickness());
                                                        if (layer.isDashed()) {
                                                            myStroke =
                                                                    new BasicStroke(layer.getLineThickness(),
                                                                    BasicStroke.CAP_BUTT,
                                                                    BasicStroke.JOIN_MITER,
                                                                    10.0f, layer.getDashArray(), 0.0f);
                                                        }
                                                        oldStroke = g2.getStroke();
                                                        g2.setStroke(myStroke);

                                                        for (ShapeFileRecord record : records) {
                                                            r = record.getRecordNumber() - 1;
                                                            if (record.getShapeType() != ShapeType.NULLSHAPE) {
                                                                PolyLineM rec = (PolyLineM) (record.getGeometry());
                                                                partStart = rec.getParts();
                                                                points = rec.getPoints();
                                                                for (int p = 0; p < rec.getNumParts(); p++) {
                                                                    pointSt = partStart[p];
                                                                    if (p < rec.getNumParts() - 1) {
                                                                        pointEnd = partStart[p + 1];
                                                                    } else {
                                                                        pointEnd = points.length;
                                                                    }
                                                                    xPoints = new float[pointEnd - pointSt];
                                                                    yPoints = new float[pointEnd - pointSt];
                                                                    for (int k = pointSt; k < pointEnd; k++) {
                                                                        xPoints[k - pointSt] = (float) (viewAreaULX + (points[k][0] - leftCoord) / EWRange * viewAreaWidth);
                                                                        yPoints[k - pointSt] = (float) (viewAreaULY + (topCoord - points[k][1]) / NSRange * viewAreaHeight);
                                                                    }
                                                                    polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, xPoints.length);

                                                                    polyline.moveTo(xPoints[0], yPoints[0]);

                                                                    for (int index = 1; index < xPoints.length; index++) {
                                                                        polyline.lineTo(xPoints[index], yPoints[index]);
                                                                    }
                                                                    if (!activeLayerBool || record.getRecordNumber() != selectedFeature) {
                                                                        g2.setColor(colours[r]);
                                                                    } else if (activeLayerBool && record.getRecordNumber() == selectedFeature) {
                                                                        g2.setColor(selectedFeatureColour);
                                                                    }
                                                                    g2.draw(polyline);
                                                                }

                                                                if (activeLayerBool && backgroundMouseMode == MOUSE_MODE_SELECT) {
                                                                    BoundingBox bb = rec.getBox();
                                                                    if (bb.isPointInBox(mapX, mapY)) {
                                                                        g2.setColor(selectionBoxColour);
                                                                        polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 5);
                                                                        xPoint = (float) (viewAreaULX + (bb.getMinX() - leftCoord) / EWRange * viewAreaWidth);
                                                                        yPoint = (float) (viewAreaULY + (topCoord - bb.getMinY()) / NSRange * viewAreaHeight);
                                                                        float xPoint2 = (float) (viewAreaULX + (bb.getMaxX() - leftCoord) / EWRange * viewAreaWidth);
                                                                        float yPoint2 = (float) (viewAreaULY + (topCoord - bb.getMaxY()) / NSRange * viewAreaHeight);
                                                                        polyline.moveTo(xPoint, yPoint);
                                                                        polyline.lineTo(xPoint, yPoint2);
                                                                        polyline.lineTo(xPoint2, yPoint2);
                                                                        polyline.lineTo(xPoint2, yPoint);
                                                                        polyline.lineTo(xPoint, yPoint);

                                                                        g2.draw(polyline);

                                                                        Ellipse2D circle = new Ellipse2D.Double(xPoint + (xPoint2 - xPoint) / 2 - 2, yPoint + (yPoint2 - yPoint) / 2 - 2, 4, 4);
                                                                        g2.fill(circle);

                                                                    }
                                                                }
                                                            }
                                                        }
                                                        g2.setStroke(oldStroke);
                                                        break;
                                                    case POLYGON:

                                                        if (layer.isFilled()) {
                                                            colours = layer.getColourData();
                                                            for (ShapeFileRecord record : records) {
                                                                r = record.getRecordNumber() - 1;
                                                                if (record.getShapeType() != ShapeType.NULLSHAPE) {
                                                                    whitebox.geospatialfiles.shapefile.Polygon rec = (whitebox.geospatialfiles.shapefile.Polygon) (record.getGeometry());
                                                                    partStart = rec.getParts();
                                                                    points = rec.getPoints();
                                                                    polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, points.length);
                                                                    for (int p = 0; p < rec.getNumParts(); p++) {
                                                                        pointSt = partStart[p];
                                                                        if (p < rec.getNumParts() - 1) {
                                                                            pointEnd = partStart[p + 1];
                                                                        } else {
                                                                            pointEnd = points.length;
                                                                        }
                                                                        xPoints = new float[pointEnd - pointSt];
                                                                        yPoints = new float[pointEnd - pointSt];
                                                                        for (int k = pointSt; k < pointEnd; k++) {
                                                                            xPoints[k - pointSt] = (float) (viewAreaULX + (points[k][0] - leftCoord) / EWRange * viewAreaWidth);
                                                                            yPoints[k - pointSt] = (float) (viewAreaULY + (topCoord - points[k][1]) / NSRange * viewAreaHeight);
                                                                        }
                                                                        polyline.moveTo(xPoints[0], yPoints[0]);

                                                                        for (int index = 1; index < xPoints.length; index++) {
                                                                            polyline.lineTo(xPoints[index], yPoints[index]);
                                                                        }
                                                                        polyline.closePath();
                                                                    }
                                                                    g2.setColor(colours[r]);
                                                                    g2.fill(polyline);
                                                                }
                                                            }
                                                        }

                                                        if (layer.isOutlined()) {
                                                            g2.setColor(lineColour);
                                                            myStroke = new BasicStroke(layer.getLineThickness());
                                                            if (layer.isDashed()) {
                                                                myStroke =
                                                                        new BasicStroke(layer.getLineThickness(),
                                                                        BasicStroke.CAP_BUTT,
                                                                        BasicStroke.JOIN_MITER,
                                                                        10.0f, layer.getDashArray(), 0.0f);
                                                            }
                                                            oldStroke = g2.getStroke();
                                                            g2.setStroke(myStroke);
                                                            for (ShapeFileRecord record : records) {
                                                                if (record.getShapeType() != ShapeType.NULLSHAPE) {
                                                                    whitebox.geospatialfiles.shapefile.Polygon rec = (whitebox.geospatialfiles.shapefile.Polygon) (record.getGeometry());
                                                                    partStart = rec.getParts();
                                                                    points = rec.getPoints();
                                                                    for (int p = 0; p < rec.getNumParts(); p++) {
                                                                        pointSt = partStart[p];
                                                                        if (p < rec.getNumParts() - 1) {
                                                                            pointEnd = partStart[p + 1];
                                                                        } else {
                                                                            pointEnd = points.length;
                                                                        }
                                                                        xPoints = new float[pointEnd - pointSt];
                                                                        yPoints = new float[pointEnd - pointSt];
                                                                        for (int k = pointSt; k < pointEnd; k++) {
                                                                            xPoints[k - pointSt] = (float) (viewAreaULX + (points[k][0] - leftCoord) / EWRange * viewAreaWidth);
                                                                            yPoints[k - pointSt] = (float) (viewAreaULY + (topCoord - points[k][1]) / NSRange * viewAreaHeight);
                                                                        }
                                                                        polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, xPoints.length);
                                                                        polyline.moveTo(xPoints[0], yPoints[0]);

                                                                        for (int index = 1; index < xPoints.length; index++) {
                                                                            polyline.lineTo(xPoints[index], yPoints[index]);
                                                                        }
                                                                        g2.draw(polyline);
                                                                    }
                                                                }
                                                            }
                                                            g2.setStroke(oldStroke);
                                                        }
                                                        if (activeLayerBool) {
                                                            g2.setColor(selectedFeatureColour);
                                                            for (ShapeFileRecord record : records) {
                                                                if (record.getRecordNumber() == selectedFeature) {
                                                                    whitebox.geospatialfiles.shapefile.Polygon rec = (whitebox.geospatialfiles.shapefile.Polygon) (record.getGeometry());
                                                                    partStart = rec.getParts();
                                                                    points = rec.getPoints();
                                                                    for (int p = 0; p < rec.getNumParts(); p++) {
                                                                        pointSt = partStart[p];
                                                                        if (p < rec.getNumParts() - 1) {
                                                                            pointEnd = partStart[p + 1];
                                                                        } else {
                                                                            pointEnd = points.length;
                                                                        }
                                                                        xPoints = new float[pointEnd - pointSt];
                                                                        yPoints = new float[pointEnd - pointSt];
                                                                        for (int k = pointSt; k < pointEnd; k++) {
                                                                            xPoints[k - pointSt] = (float) (viewAreaULX + (points[k][0] - leftCoord) / EWRange * viewAreaWidth);
                                                                            yPoints[k - pointSt] = (float) (viewAreaULY + (topCoord - points[k][1]) / NSRange * viewAreaHeight);
                                                                        }
                                                                        polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, xPoints.length);
                                                                        polyline.moveTo(xPoints[0], yPoints[0]);

                                                                        for (int index = 1; index < xPoints.length; index++) {
                                                                            polyline.lineTo(xPoints[index], yPoints[index]);
                                                                        }
                                                                        g2.draw(polyline);
                                                                    }
                                                                }

                                                                whitebox.geospatialfiles.shapefile.Polygon rec = (whitebox.geospatialfiles.shapefile.Polygon) (record.getGeometry());
                                                                BoundingBox bb = rec.getBox();
                                                                if (bb.isPointInBox(mapX, mapY)) {
                                                                    g2.setColor(selectionBoxColour);
                                                                    polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 5);
                                                                    xPoint = (float) (viewAreaULX + (bb.getMinX() - leftCoord) / EWRange * viewAreaWidth);
                                                                    yPoint = (float) (viewAreaULY + (topCoord - bb.getMinY()) / NSRange * viewAreaHeight);
                                                                    float xPoint2 = (float) (viewAreaULX + (bb.getMaxX() - leftCoord) / EWRange * viewAreaWidth);
                                                                    float yPoint2 = (float) (viewAreaULY + (topCoord - bb.getMaxY()) / NSRange * viewAreaHeight);
                                                                    polyline.moveTo(xPoint, yPoint);
                                                                    polyline.lineTo(xPoint, yPoint2);
                                                                    polyline.lineTo(xPoint2, yPoint2);
                                                                    polyline.lineTo(xPoint2, yPoint);
                                                                    polyline.lineTo(xPoint, yPoint);

                                                                    g2.draw(polyline);

                                                                    Ellipse2D circle = new Ellipse2D.Double(xPoint + (xPoint2 - xPoint) / 2 - 2, yPoint + (yPoint2 - yPoint) / 2 - 2, 4, 4);
                                                                    g2.fill(circle);

                                                                    g2.setColor(selectedFeatureColour);
                                                                }
                                                            }
                                                        }
                                                        break;
                                                    case POLYGONZ:
                                                        if (layer.isFilled()) {
                                                            colours = layer.getColourData();
                                                            for (ShapeFileRecord record : records) {
                                                                r = record.getRecordNumber() - 1;
                                                                if (record.getShapeType() != ShapeType.NULLSHAPE) {
                                                                    PolygonZ rec = (PolygonZ) (record.getGeometry());
                                                                    partStart = rec.getParts();
                                                                    points = rec.getPoints();
                                                                    polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, points.length);
                                                                    for (int p = 0; p < rec.getNumParts(); p++) {
                                                                        pointSt = partStart[p];
                                                                        if (p < rec.getNumParts() - 1) {
                                                                            pointEnd = partStart[p + 1];
                                                                        } else {
                                                                            pointEnd = points.length;
                                                                        }
                                                                        xPoints = new float[pointEnd - pointSt];
                                                                        yPoints = new float[pointEnd - pointSt];
                                                                        for (int k = pointSt; k < pointEnd; k++) {
                                                                            xPoints[k - pointSt] = (float) (viewAreaULX + (points[k][0] - leftCoord) / EWRange * viewAreaWidth);
                                                                            yPoints[k - pointSt] = (float) (viewAreaULY + (topCoord - points[k][1]) / NSRange * viewAreaHeight);
                                                                        }
                                                                        polyline.moveTo(xPoints[0], yPoints[0]);

                                                                        for (int index = 1; index < xPoints.length; index++) {
                                                                            polyline.lineTo(xPoints[index], yPoints[index]);
                                                                        }
                                                                        polyline.closePath();
                                                                    }
                                                                    g2.setColor(colours[r]);
                                                                    g2.fill(polyline);
                                                                }
                                                            }
                                                        }

                                                        if (layer.isOutlined()) {
                                                            g2.setColor(lineColour);
                                                            myStroke = new BasicStroke(layer.getLineThickness());
                                                            if (layer.isDashed()) {
                                                                myStroke =
                                                                        new BasicStroke(layer.getLineThickness(),
                                                                        BasicStroke.CAP_BUTT,
                                                                        BasicStroke.JOIN_MITER,
                                                                        10.0f, layer.getDashArray(), 0.0f);
                                                            }
                                                            oldStroke = g2.getStroke();
                                                            g2.setStroke(myStroke);
                                                            for (ShapeFileRecord record : records) {
                                                                if (record.getShapeType() != ShapeType.NULLSHAPE) {
                                                                    PolygonZ rec = (PolygonZ) (record.getGeometry());
                                                                    partStart = rec.getParts();
                                                                    points = rec.getPoints();
                                                                    for (int p = 0; p < rec.getNumParts(); p++) {
                                                                        pointSt = partStart[p];
                                                                        if (p < rec.getNumParts() - 1) {
                                                                            pointEnd = partStart[p + 1];
                                                                        } else {
                                                                            pointEnd = points.length;
                                                                        }
                                                                        xPoints = new float[pointEnd - pointSt];
                                                                        yPoints = new float[pointEnd - pointSt];
                                                                        for (int k = pointSt; k < pointEnd; k++) {
                                                                            xPoints[k - pointSt] = (float) (viewAreaULX + (points[k][0] - leftCoord) / EWRange * viewAreaWidth);
                                                                            yPoints[k - pointSt] = (float) (viewAreaULY + (topCoord - points[k][1]) / NSRange * viewAreaHeight);
                                                                        }
                                                                        polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, xPoints.length);
                                                                        polyline.moveTo(xPoints[0], yPoints[0]);

                                                                        for (int index = 1; index < xPoints.length; index++) {
                                                                            polyline.lineTo(xPoints[index], yPoints[index]);
                                                                        }
                                                                        g2.draw(polyline);
                                                                    }
                                                                }
                                                            }
                                                            g2.setStroke(oldStroke);
                                                        }

                                                        if (activeLayerBool) {
                                                            g2.setColor(selectedFeatureColour);
                                                            for (ShapeFileRecord record : records) {
                                                                if (record.getRecordNumber() == selectedFeature) {
                                                                    PolygonZ rec = (PolygonZ) (record.getGeometry());
                                                                    partStart = rec.getParts();
                                                                    points = rec.getPoints();
                                                                    for (int p = 0; p < rec.getNumParts(); p++) {
                                                                        pointSt = partStart[p];
                                                                        if (p < rec.getNumParts() - 1) {
                                                                            pointEnd = partStart[p + 1];
                                                                        } else {
                                                                            pointEnd = points.length;
                                                                        }
                                                                        xPoints = new float[pointEnd - pointSt];
                                                                        yPoints = new float[pointEnd - pointSt];
                                                                        for (int k = pointSt; k < pointEnd; k++) {
                                                                            xPoints[k - pointSt] = (float) (viewAreaULX + (points[k][0] - leftCoord) / EWRange * viewAreaWidth);
                                                                            yPoints[k - pointSt] = (float) (viewAreaULY + (topCoord - points[k][1]) / NSRange * viewAreaHeight);
                                                                        }
                                                                        polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, xPoints.length);
                                                                        polyline.moveTo(xPoints[0], yPoints[0]);

                                                                        for (int index = 1; index < xPoints.length; index++) {
                                                                            polyline.lineTo(xPoints[index], yPoints[index]);
                                                                        }
                                                                        g2.draw(polyline);
                                                                    }
                                                                }

                                                                PolygonZ rec = (PolygonZ) (record.getGeometry());
                                                                BoundingBox bb = rec.getBox();
                                                                if (bb.isPointInBox(mapX, mapY)) {
                                                                    g2.setColor(selectionBoxColour);
                                                                    polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 5);
                                                                    xPoint = (float) (viewAreaULX + (bb.getMinX() - leftCoord) / EWRange * viewAreaWidth);
                                                                    yPoint = (float) (viewAreaULY + (topCoord - bb.getMinY()) / NSRange * viewAreaHeight);
                                                                    float xPoint2 = (float) (viewAreaULX + (bb.getMaxX() - leftCoord) / EWRange * viewAreaWidth);
                                                                    float yPoint2 = (float) (viewAreaULY + (topCoord - bb.getMaxY()) / NSRange * viewAreaHeight);
                                                                    polyline.moveTo(xPoint, yPoint);
                                                                    polyline.lineTo(xPoint, yPoint2);
                                                                    polyline.lineTo(xPoint2, yPoint2);
                                                                    polyline.lineTo(xPoint2, yPoint);
                                                                    polyline.lineTo(xPoint, yPoint);

                                                                    g2.draw(polyline);

                                                                    Ellipse2D circle = new Ellipse2D.Double(xPoint + (xPoint2 - xPoint) / 2 - 2, yPoint + (yPoint2 - yPoint) / 2 - 2, 4, 4);
                                                                    g2.fill(circle);

                                                                    g2.setColor(selectedFeatureColour);
                                                                }
                                                            }
                                                        }
                                                        break;
                                                    case POLYGONM:
                                                        if (layer.isFilled()) {
                                                            colours = layer.getColourData();
                                                            for (ShapeFileRecord record : records) {
                                                                r = record.getRecordNumber() - 1;
                                                                if (record.getShapeType() != ShapeType.NULLSHAPE) {
                                                                    PolygonM rec = (PolygonM) (record.getGeometry());
                                                                    partStart = rec.getParts();
                                                                    points = rec.getPoints();
                                                                    polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, points.length);
                                                                    for (int p = 0; p < rec.getNumParts(); p++) {
                                                                        pointSt = partStart[p];
                                                                        if (p < rec.getNumParts() - 1) {
                                                                            pointEnd = partStart[p + 1];
                                                                        } else {
                                                                            pointEnd = points.length;
                                                                        }
                                                                        xPoints = new float[pointEnd - pointSt];
                                                                        yPoints = new float[pointEnd - pointSt];
                                                                        for (int k = pointSt; k < pointEnd; k++) {
                                                                            xPoints[k - pointSt] = (float) (viewAreaULX + (points[k][0] - leftCoord) / EWRange * viewAreaWidth);
                                                                            yPoints[k - pointSt] = (float) (viewAreaULY + (topCoord - points[k][1]) / NSRange * viewAreaHeight);
                                                                        }
                                                                        polyline.moveTo(xPoints[0], yPoints[0]);

                                                                        for (int index = 1; index < xPoints.length; index++) {
                                                                            polyline.lineTo(xPoints[index], yPoints[index]);
                                                                        }
                                                                        polyline.closePath();
                                                                    }
                                                                    g2.setColor(colours[r]);
                                                                    g2.fill(polyline);
                                                                }
                                                            }
                                                        }

                                                        if (layer.isOutlined()) {
                                                            g2.setColor(lineColour);
                                                            myStroke = new BasicStroke(layer.getLineThickness());
                                                            if (layer.isDashed()) {
                                                                myStroke =
                                                                        new BasicStroke(layer.getLineThickness(),
                                                                        BasicStroke.CAP_BUTT,
                                                                        BasicStroke.JOIN_MITER,
                                                                        10.0f, layer.getDashArray(), 0.0f);
                                                            }
                                                            oldStroke = g2.getStroke();
                                                            g2.setStroke(myStroke);
                                                            for (ShapeFileRecord record : records) {
                                                                if (record.getShapeType() != ShapeType.NULLSHAPE) {
                                                                    PolygonM rec = (PolygonM) (record.getGeometry());
                                                                    partStart = rec.getParts();
                                                                    points = rec.getPoints();
                                                                    for (int p = 0; p < rec.getNumParts(); p++) {
                                                                        pointSt = partStart[p];
                                                                        if (p < rec.getNumParts() - 1) {
                                                                            pointEnd = partStart[p + 1];
                                                                        } else {
                                                                            pointEnd = points.length;
                                                                        }
                                                                        xPoints = new float[pointEnd - pointSt];
                                                                        yPoints = new float[pointEnd - pointSt];
                                                                        for (int k = pointSt; k < pointEnd; k++) {
                                                                            xPoints[k - pointSt] = (float) (viewAreaULX + (points[k][0] - leftCoord) / EWRange * viewAreaWidth);
                                                                            yPoints[k - pointSt] = (float) (viewAreaULY + (topCoord - points[k][1]) / NSRange * viewAreaHeight);
                                                                        }
                                                                        polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, xPoints.length);
                                                                        polyline.moveTo(xPoints[0], yPoints[0]);

                                                                        for (int index = 1; index < xPoints.length; index++) {
                                                                            polyline.lineTo(xPoints[index], yPoints[index]);
                                                                        }
                                                                        g2.draw(polyline);
                                                                    }
                                                                }
                                                            }
                                                            g2.setStroke(oldStroke);
                                                        }
                                                        if (activeLayerBool) {
                                                            g2.setColor(selectedFeatureColour);
                                                            for (ShapeFileRecord record : records) {
                                                                if (record.getRecordNumber() == selectedFeature) {
                                                                    PolygonM rec = (PolygonM) (record.getGeometry());
                                                                    partStart = rec.getParts();
                                                                    points = rec.getPoints();
                                                                    for (int p = 0; p < rec.getNumParts(); p++) {
                                                                        pointSt = partStart[p];
                                                                        if (p < rec.getNumParts() - 1) {
                                                                            pointEnd = partStart[p + 1];
                                                                        } else {
                                                                            pointEnd = points.length;
                                                                        }
                                                                        xPoints = new float[pointEnd - pointSt];
                                                                        yPoints = new float[pointEnd - pointSt];
                                                                        for (int k = pointSt; k < pointEnd; k++) {
                                                                            xPoints[k - pointSt] = (float) (viewAreaULX + (points[k][0] - leftCoord) / EWRange * viewAreaWidth);
                                                                            yPoints[k - pointSt] = (float) (viewAreaULY + (topCoord - points[k][1]) / NSRange * viewAreaHeight);
                                                                        }
                                                                        polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, xPoints.length);
                                                                        polyline.moveTo(xPoints[0], yPoints[0]);

                                                                        for (int index = 1; index < xPoints.length; index++) {
                                                                            polyline.lineTo(xPoints[index], yPoints[index]);
                                                                        }
                                                                        g2.draw(polyline);
                                                                    }
                                                                }

                                                                PolygonM rec = (PolygonM) (record.getGeometry());
                                                                BoundingBox bb = rec.getBox();
                                                                if (bb.isPointInBox(mapX, mapY)) {
                                                                    g2.setColor(selectionBoxColour);
                                                                    polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 5);
                                                                    xPoint = (float) (viewAreaULX + (bb.getMinX() - leftCoord) / EWRange * viewAreaWidth);
                                                                    yPoint = (float) (viewAreaULY + (topCoord - bb.getMinY()) / NSRange * viewAreaHeight);
                                                                    float xPoint2 = (float) (viewAreaULX + (bb.getMaxX() - leftCoord) / EWRange * viewAreaWidth);
                                                                    float yPoint2 = (float) (viewAreaULY + (topCoord - bb.getMaxY()) / NSRange * viewAreaHeight);
                                                                    polyline.moveTo(xPoint, yPoint);
                                                                    polyline.lineTo(xPoint, yPoint2);
                                                                    polyline.lineTo(xPoint2, yPoint2);
                                                                    polyline.lineTo(xPoint2, yPoint);
                                                                    polyline.lineTo(xPoint, yPoint);

                                                                    g2.draw(polyline);

                                                                    Ellipse2D circle = new Ellipse2D.Double(xPoint + (xPoint2 - xPoint) / 2 - 2, yPoint + (yPoint2 - yPoint) / 2 - 2, 4, 4);
                                                                    g2.fill(circle);

                                                                    g2.setColor(selectedFeatureColour);
                                                                }
                                                            }
                                                        }
                                                        break;
                                                    case MULTIPATCH:
                                                        // this vector type is unsupported
                                                        break;
                                                }
                                                if (isClipped) {
                                                    g2.setClip(oldClip);
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (mapArea.isBorderVisible()) {
                                oldStroke = g2.getStroke();
                                g2.setStroke(new BasicStroke(mapArea.getLineWidth()));
                                g2.setColor(mapArea.getBorderColour());

                                g2.drawRect(viewAreaULX,
                                        viewAreaULY,
                                        viewAreaWidth,
                                        viewAreaHeight);

                                g2.setStroke(oldStroke);

                            }
                            if (mapArea.isReferenceMarksVisible()) {
                                oldStroke = g2.getStroke();
                                g2.setStroke(new BasicStroke(mapArea.getLineWidth()));
                                g2.setColor(mapArea.getBorderColour());

                                g2.drawLine(viewAreaULX,
                                        viewAreaULY,
                                        viewAreaULX,
                                        viewAreaULY - referenceMarkSize);

                                g2.drawLine(viewAreaULX,
                                        viewAreaULY,
                                        viewAreaULX - referenceMarkSize,
                                        viewAreaULY);

                                g2.drawLine(viewAreaLRX,
                                        viewAreaULY,
                                        viewAreaLRX,
                                        viewAreaULY - referenceMarkSize);

                                g2.drawLine(viewAreaLRX,
                                        viewAreaULY,
                                        viewAreaLRX + referenceMarkSize,
                                        viewAreaULY);

                                g2.drawLine(viewAreaULX,
                                        viewAreaLRY,
                                        viewAreaULX,
                                        viewAreaLRY + referenceMarkSize);

                                g2.drawLine(viewAreaULX,
                                        viewAreaLRY,
                                        viewAreaULX - referenceMarkSize,
                                        viewAreaLRY);

                                g2.drawLine(viewAreaLRX,
                                        viewAreaLRY,
                                        viewAreaLRX,
                                        viewAreaLRY + referenceMarkSize);

                                g2.drawLine(viewAreaLRX,
                                        viewAreaLRY,
                                        viewAreaLRX + referenceMarkSize,
                                        viewAreaLRY);

                                g2.setStroke(oldStroke);

                                if (numLayers > 0) {
                                    Font labelFont = new Font("SanSerif", Font.PLAIN, 10);
                                    XYUnits = mapArea.getXYUnits();
                                    // labels
                                    df = new DecimalFormat("###,###,###.#");
                                    g2.setFont(labelFont);
                                    metrics = g2.getFontMetrics(labelFont);

                                    label = df.format(currentExtent.getMinX() - (viewAreaWidth / mapScale - xRange) / 2) + XYUnits;
                                    g2.drawString(label, viewAreaULX + 4, viewAreaULY - 3);
                                    g2.drawString(label, viewAreaULX + 4, viewAreaLRY + referenceMarkSize - 1);

                                    label = df.format(currentExtent.getMaxX() + (viewAreaWidth / mapScale - xRange) / 2) + XYUnits;
                                    adv = metrics.stringWidth(label) + 6;
                                    g2.drawString(label, viewAreaLRX - adv, viewAreaULY - 3);
                                    g2.drawString(label, viewAreaLRX - adv, viewAreaLRY + referenceMarkSize - 1);

                                    // rotate the font

                                    // Create a rotation transformation for the font.
                                    AffineTransform fontAT = new AffineTransform();

                                    // Derive a new font using a rotatation transform
                                    fontAT.rotate(-Math.PI / 2.0);
                                    Font theDerivedFont = labelFont.deriveFont(fontAT);

                                    // set the derived font in the Graphics2D context
                                    g2.setFont(theDerivedFont);

                                    fontHeight = labelFont.getSize();

                                    metrics = g2.getFontMetrics(labelFont); //theDerivedFont);

                                    //Rectangle2D rect = metrics.getStringBounds(label, g);
                                    label = df.format(currentExtent.getMaxY() + (viewAreaHeight / mapScale - yRange) / 2) + XYUnits;
                                    adv = metrics.stringWidth(label) + 6;
                                    //adv = (int)rect.getWidth();
                                    g2.drawString(label, viewAreaULX - 3, viewAreaULY + adv);
                                    g2.drawString(label, viewAreaLRX + fontHeight, viewAreaULY + adv);

                                    label = df.format(currentExtent.getMinY() - (viewAreaHeight / mapScale - yRange) / 2) + XYUnits;
                                    //adv = metrics.stringWidth(label);
                                    g2.drawString(label, viewAreaULX - 3, viewAreaLRY - 4);
                                    g2.drawString(label, viewAreaLRX + fontHeight, viewAreaLRY - 4);

                                    // put the original font back
                                    g2.setFont(labelFont);

                                }

                            }


                            if (mapArea.isNeatlineVisible() || mapArea.isSelected()) {
                                if (!mapArea.isSizeMaximizedToScreenSize()) {
                                    oldStroke = g2.getStroke();
                                    if (mapArea.isSelected() && !printingMap) {
                                        g2.setColor(selectedColour);
                                        g2.setStroke(dashed);
                                    } else {
                                        g2.setColor(mapArea.getBorderColour());
                                        g2.setStroke(new BasicStroke(mapArea.getLineWidth()));
                                    }

                                    g2.drawRect(mapAreaULX,
                                            mapAreaULY,
                                            mapArea.getWidth(),
                                            mapArea.getHeight());

                                    g2.setStroke(oldStroke);
                                }
                            }

                            // replace the rotated font.
                            g2.setFont(oldFont);

//                            if (mapArea.getRotation() > 0) {
//                                g2.rotate(-mapArea.getRotation());
//                            }


                            if (usingDistanceTool) {
                                double topCoord = mapExtent.getMaxY();
                                double bottomCoord = mapExtent.getMinY();
                                double leftCoord = mapExtent.getMinX();
                                double rightCoord = mapExtent.getMaxX();
                                double EWRange = rightCoord - leftCoord;
                                double NSRange = topCoord - bottomCoord;

                                oldStroke = g2.getStroke();
                                g2.setColor(Color.yellow);
                                double x1, y1;
                                if (distPoints.size() > 1) {
                                    GeneralPath polyline = new GeneralPath(GeneralPath.WIND_EVEN_ODD, 5);
                                    x1 = (viewAreaULX + (distPoints.get(0).x - leftCoord) / EWRange * viewAreaWidth);
                                    y1 = (viewAreaULY + (topCoord - distPoints.get(0).y) / NSRange * viewAreaHeight);
                                    polyline.moveTo(x1, y1);

                                    for (int i = 1; i < distPoints.size(); i++) {
                                        x1 = (viewAreaULX + (distPoints.get(i).x - leftCoord) / EWRange * viewAreaWidth);
                                        y1 = (viewAreaULY + (topCoord - distPoints.get(i).y) / NSRange * viewAreaHeight);
                                        polyline.lineTo(x1, y1);

                                    }
                                    g2.draw(polyline);
                                }
                                for (int i = 0; i < distPoints.size(); i++) {
                                    if (i == distPoints.size() - 1) {
                                        g2.setColor(Color.blue);
                                    }
                                    x1 = (viewAreaULX + (distPoints.get(i).x - leftCoord) / EWRange * viewAreaWidth);
                                    y1 = (viewAreaULY + (topCoord - distPoints.get(i).y) / NSRange * viewAreaHeight);
                                    g2.fillRect((int) x1 - 3, (int) y1 - 3, 6, 6);

                                }

                                g2.setStroke(oldStroke);
                            } else if (modifyingPixels && modifyPixelsX > 0 && modifyPixelsY > 0) {
                                double topCoord = mapExtent.getMaxY();
                                double bottomCoord = mapExtent.getMinY();
                                double leftCoord = mapExtent.getMinX();
                                double rightCoord = mapExtent.getMaxX();
                                double EWRange = rightCoord - leftCoord;
                                double NSRange = topCoord - bottomCoord;

                                int crosshairlength = 13;
                                int radius = 9;
                                int x1 = (int) (viewAreaULX + (modifyPixelsX - leftCoord) / EWRange * viewAreaWidth);
                                int y1 = (int) (viewAreaULY + (topCoord - modifyPixelsY) / NSRange * viewAreaHeight);

                                g2.setColor(Color.white);
                                g2.drawOval(x1 - radius - 1, y1 - radius - 1, 2 * radius + 2, 2 * radius + 2);
                                g2.setColor(Color.black);
                                g2.drawOval(x1 - radius, y1 - radius, 2 * radius, 2 * radius);

                                g2.setColor(Color.white);
                                g2.drawRect(x1 - 1, y1 - crosshairlength - 1, 2, crosshairlength * 2 + 2);
                                g2.drawRect(x1 - crosshairlength - 1, y1 - 1, crosshairlength * 2 + 2, 2);
                                g2.setColor(Color.black);
                                g2.drawLine(x1, y1 - crosshairlength, x1, y1 + crosshairlength);
                                g2.drawLine(x1 - crosshairlength, y1, x1 + crosshairlength, y1);

                            }
                        }

                    }

                }
                
                if (mouseDragged && (myMode == MOUSE_MODE_ZOOM
                        || backgroundMouseMode == MOUSE_MODE_ZOOM)
                        && !(myMode == MOUSE_MODE_RESIZE)) {
                    boolean drawLine = true;
                    if (myMode == MOUSE_MODE_CARTO_ELEMENT || myMode == MOUSE_MODE_MAPAREA) {
                        CartographicElement ce = map.getCartographicElement(whichCartoElement);
                        if (ce.isSelected()) {
                            drawLine = false;
                        }
                    }
                    if (drawLine) {
                        oldStroke = g2.getStroke();
                        g2.setStroke(dashed);
                        g2.setColor(Color.black);
                        int boxWidth = (int) (Math.abs(startCol - endCol));
                        int boxHeight = (int) (Math.abs(startRow - endRow));
                        x = Math.min(startCol, endCol);
                        y = Math.min(startRow, endRow);
                        g2.drawRect(x, y, boxWidth, boxHeight);
                        g2.setColor(Color.white);
                        g2.setStroke(dashed2);
                        g2.drawRect(x, y, boxWidth, boxHeight);
                        g2.setStroke(oldStroke);
                    }
                }


                g2.setTransform(old);

            }

        } catch (Exception e) {
            host.showFeedback(e.getMessage());
            //System.out.println(e.getMessage());
        } finally {
            g.dispose();
            g2.dispose();
        }
    }

    private void drawStringRect(Graphics2D graphics, int x1, int y1, int x2, 
            int y2, float interline, String txt) {
        if (txt.isEmpty()) { return; }
        AttributedString as = new AttributedString(txt);
        as.addAttribute(TextAttribute.FOREGROUND, graphics.getPaint());
        as.addAttribute(TextAttribute.FONT, graphics.getFont());
        AttributedCharacterIterator aci = as.getIterator();
        FontRenderContext frc = new FontRenderContext(null, true, false);
        LineBreakMeasurer lbm = new LineBreakMeasurer(aci, frc);
        float width = x2 - x1;

        while (lbm.getPosition() < txt.length()) {
            TextLayout tl = lbm.nextLayout(width);
            y1 += tl.getAscent();
            tl.draw(graphics, x1, y1);
            y1 += tl.getDescent() + tl.getLeading() + (interline - 1.0f) * tl.getAscent();
            if (y1 > y2) {
                break;
            }
        }
    }
    
    private double calculateArea() {
        int numPoints;
        double x1, y1, x2, y2;
        int n1 = 0, n2 = 0;
        double area = 0;


        numPoints = distPoints.size();
        if (numPoints < 3) {
            return -1;
        } // something's wrong!

        double area2 = 0;
        for (int j = 0; j < numPoints; j++) {
            n1 = j;
            if (j < numPoints - 1) {
                n2 = j + 1;
            } else {
                n2 = 0;
            }
            x1 = distPoints.get(n1).x;
            y1 = distPoints.get(n1).y;
            x2 = distPoints.get(n2).x;
            y2 = distPoints.get(n2).y;

            area2 += (x1 * y2) - (x2 * y1);
        }
        area2 = area2 / 2.0;
        if (area2 < 0) { // a positive area indicates counter-clockwise order
            area += -area2;
        } else {
            area += area2;
        }
        return area;
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        mouseDragged = true;
        int x = (int) ((e.getX() - pageLeft) / scale);
        int y = (int) ((e.getY() - pageTop) / scale);
        endCol = x;
        endRow = y;

        if (myMode == MOUSE_MODE_CARTO_ELEMENT
                || myMode == MOUSE_MODE_MAPAREA) {

            for (CartographicElement ce : map.getCartographicElementList()) {
                if (ce.isSelected()) {
                    ce.setUpperLeftX(x - ce.getSelectedOffsetX());
                    ce.setUpperLeftY(y - ce.getSelectedOffsetY());
                }
            }
            if (map.getCartographicElement(whichCartoElement) instanceof MapArea) {
                MapArea mapArea = (MapArea) map.getCartographicElement(whichCartoElement);
                if (mapArea.isVisible() && mapExtent.getMinY() != mapExtent.getMaxY()
                        && backgroundMouseMode == MOUSE_MODE_PAN && !mapArea.isSelected()) {
                    int x2 = (int) ((e.getX() - pageLeft) / scale);
                    int y2 = (int) ((e.getY() - pageTop) / scale);

                    int referenceMarkSize = mapArea.getReferenceMarksSize();
                    int viewAreaULX = mapArea.getUpperLeftX() + referenceMarkSize;
                    int viewAreaULY = mapArea.getUpperLeftY() + referenceMarkSize;
                    int viewAreaLRX = mapArea.getLowerRightX() - referenceMarkSize;
                    int viewAreaLRY = mapArea.getLowerRightY() - referenceMarkSize;
                    double viewAreaWidth = viewAreaLRX - viewAreaULX;
                    double viewAreaHeight = viewAreaLRY - viewAreaULY;

                    if (mapArea.isSizeMaximizedToScreenSize() && !printingMap) {
                        viewAreaULX = (int) ((-pageLeft / scale) + referenceMarkSize);
                        viewAreaULY = (int) ((-pageTop / scale) + referenceMarkSize);
                        viewAreaLRX = viewAreaULX + (int) (w / scale - 2 * referenceMarkSize);
                        viewAreaLRY = viewAreaULY + (int) (h / scale - 2 * referenceMarkSize);
                        viewAreaWidth = viewAreaLRX - viewAreaULX;
                        viewAreaHeight = viewAreaLRY - viewAreaULY;
                    }

                    double xRange = Math.abs(mapExtent.getMaxX() - mapExtent.getMinX());
                    double yRange = Math.abs(mapExtent.getMaxY() - mapExtent.getMinY());

                    double mapYEnd = mapExtent.getMinY() + (viewAreaLRY - y2) / viewAreaHeight * yRange;
                    double mapXEnd = mapExtent.getMinX() + (x2 - viewAreaULX) / viewAreaWidth * xRange;

                    double deltaX = mapX - mapXEnd;
                    double deltaY = mapY - mapYEnd;
                    if (Math.abs(deltaX / xRange) >= 0.05 || Math.abs(deltaY / yRange) >= 0.05) {
                        BoundingBox bb = new BoundingBox(mapExtent.getMinX() + deltaX,
                                mapExtent.getMinY() + deltaY,
                                mapExtent.getMaxX() + deltaX,
                                mapExtent.getMaxY() + deltaY);
                        mapArea.setCurrentExtent(bb);
                    }

                }
            }
        } else if (myMode == MOUSE_MODE_RESIZE) {
            CartographicElement ce = map.getCartographicElement(whichCartoElement);
            if (ce.isSelected()) {
                ce.resize(x, y, myResizeMode);
            }


        }
        this.repaint();
    }

    //int oldMouseMode = 0;
    @Override
    public void mouseMoved(MouseEvent e) {
        int x = (int) ((e.getX() - pageLeft) / scale);
        int y = (int) ((e.getY() - pageTop) / scale);
        boolean withinElement = false;
        int nearDist = 8;
        for (CartographicElement ce : map.getCartographicElementList()) {
            int ulX = ce.getUpperLeftX();
            int ulY = ce.getUpperLeftY();
            int lrX = ce.getLowerRightX();
            int lrY = ce.getLowerRightY();
            // is the mouse within the element?
            if (isBetween(x, ulX, lrX) && isBetween(y, ulY, lrY)) {
                if (!(ce instanceof MapArea)) {
                    if (ce.isSelected() && myMode != MOUSE_MODE_CARTO_ELEMENT) {
                        this.setCursor(panCursor);
                    } else if (!ce.isSelected() && myMode != MOUSE_MODE_CARTO_ELEMENT) {
                        this.setCursor(selectCursor);
                    }

                    myMode = MOUSE_MODE_CARTO_ELEMENT;

                } else {
                    if (ce.isSelected() && myMode != MOUSE_MODE_MAPAREA) {
                        this.setCursor(panCursor);
                        //oldMouseMode = myMode;
                    }
                    myMode = MOUSE_MODE_MAPAREA;

//                    calculateMapXY(e, (MapArea)ce);

                    updateStatus(e, (MapArea) ce);

                }
                whichCartoElement = ce.getElementNumber();
                ce.setSelectedOffsetX(x - ce.getUpperLeftX());
                ce.setSelectedOffsetY(y - ce.getUpperLeftY());
                withinElement = true;

                // are you near the edge of a selected element?
            } else if (ce.isSelected() && isBetween(x, ulX - nearDist, lrX + nearDist)
                    && isBetween(y, ulY - nearDist, lrY + nearDist)) {
                if (myMode != MOUSE_MODE_RESIZE) {
                    /*  public static final int RESIZE_MODE_N = 0;
                     public static final int RESIZE_MODE_S = 1;
                     public static final int RESIZE_MODE_E = 2;
                     public static final int RESIZE_MODE_W = 3;
                     public static final int RESIZE_MODE_NE = 4;
                     public static final int RESIZE_MODE_NW = 5;
                     public static final int RESIZE_MODE_SE = 6;
                     public static final int RESIZE_MODE_SW = 7;
                     */
                    if (isBetween(x, ulX, lrX) && (y < ulY)) {
                        this.setCursor(new Cursor(java.awt.Cursor.N_RESIZE_CURSOR));
                        myResizeMode = RESIZE_MODE_N;
                    } else if (isBetween(x, ulX, lrX) && (y > lrY)) {
                        this.setCursor(new Cursor(java.awt.Cursor.S_RESIZE_CURSOR));
                        myResizeMode = RESIZE_MODE_S;
                    } else if (isBetween(y, ulY, lrY) && (x > ulX)) {
                        this.setCursor(new Cursor(java.awt.Cursor.E_RESIZE_CURSOR));
                        myResizeMode = RESIZE_MODE_E;
                    } else if (isBetween(y, ulY, lrY) && (x < lrX)) {
                        this.setCursor(new Cursor(java.awt.Cursor.W_RESIZE_CURSOR));
                        myResizeMode = RESIZE_MODE_W;
                    } else if ((y < ulY) && (x < ulX)) {
                        this.setCursor(new Cursor(java.awt.Cursor.NW_RESIZE_CURSOR));
                        myResizeMode = RESIZE_MODE_NW;
                    } else if ((y < ulY) && (x > lrX)) {
                        this.setCursor(new Cursor(java.awt.Cursor.NE_RESIZE_CURSOR));
                        myResizeMode = RESIZE_MODE_NE;
                    } else if ((y > lrY) && (x < ulX)) {
                        this.setCursor(new Cursor(java.awt.Cursor.SW_RESIZE_CURSOR));
                        myResizeMode = RESIZE_MODE_SW;
                    } else if ((y > lrY) && (x > lrX)) {
                        this.setCursor(new Cursor(java.awt.Cursor.SE_RESIZE_CURSOR));
                        myResizeMode = RESIZE_MODE_SE;
                    }
                }

                myMode = MOUSE_MODE_RESIZE;

                whichCartoElement = ce.getElementNumber();
                ce.setSelectedOffsetX(x - ce.getUpperLeftX());
                ce.setSelectedOffsetY(y - ce.getUpperLeftY());
                withinElement = true;
            }
            if (ce.isSelected()) {
                ce.setSelectedOffsetX(x - ce.getUpperLeftX());
                ce.setSelectedOffsetY(y - ce.getUpperLeftY());
            }

            if (myMode == MOUSE_MODE_MAPAREA && ce instanceof MapArea) {
                calculateMapXY(e, (MapArea) ce);
            }

        }
        if (!withinElement) {
            if (myMode == MOUSE_MODE_CARTO_ELEMENT
                    || myMode == MOUSE_MODE_MAPAREA
                    || myMode == MOUSE_MODE_RESIZE) {
                if (backgroundMouseMode == MOUSE_MODE_ZOOM) {
                    myMode = MOUSE_MODE_ZOOM;
                    this.setCursor(zoomCursor);
                } else if (backgroundMouseMode == MOUSE_MODE_PAN) {
                    myMode = MOUSE_MODE_PAN;
                    this.setCursor(panCursor);
                } else if (backgroundMouseMode == MOUSE_MODE_SELECT) {
                    myMode = MOUSE_MODE_SELECT;
                    this.setCursor(selectCursor);
                }
                whichCartoElement = -1;
                this.repaint();
            }
        }
        if (myMode == MOUSE_MODE_MAPAREA && backgroundMouseMode == MOUSE_MODE_SELECT) {
            this.repaint();
        }
    }

    // Return true if val is between theshold1 and theshold2.
    private static boolean isBetween(double val, double threshold1, double threshold2) {
        if (val == threshold1 || val == threshold2) {
            return true;
        }
        return threshold2 > threshold1 ? val > threshold1 && val < threshold2 : val > threshold2 && val < threshold1;
    }
    ArrayList<XYPoint> distPoints = new ArrayList<>();
    double calculatedDistance;

    @Override
    public void mouseClicked(MouseEvent e) {
        int clickCount = e.getClickCount();
        if (clickCount == 2 && (myMode == MOUSE_MODE_CARTO_ELEMENT
                || myMode == MOUSE_MODE_MAPAREA) && !usingDistanceTool) {
            if (host instanceof WhiteboxGui) { //SwingUtilities.getRoot(this) instanceof WhiteboxGui) {
                WhiteboxGui wb = (WhiteboxGui) host;
                wb.showMapProperties(whichCartoElement);
            }

        } else if (clickCount == 1 && modifyingPixels && myMode != MOUSE_MODE_CARTO_ELEMENT) {
            if (map.getCartographicElement(whichCartoElement) instanceof MapArea) {
                MapArea mapArea = (MapArea) map.getCartographicElement(whichCartoElement);
                BoundingBox currentExtent = mapArea.getCurrentMapExtent();
                if (mapArea.isVisible() && currentExtent.getMinY() != currentExtent.getMaxY()) {
                    int x = (int) ((e.getX() - pageLeft) / scale);
                    int y = (int) ((e.getY() - pageTop) / scale);

                    int referenceMarkSize = mapArea.getReferenceMarksSize();
                    int viewAreaULX = mapArea.getUpperLeftX() + referenceMarkSize;
                    int viewAreaULY = mapArea.getUpperLeftY() + referenceMarkSize;
                    int viewAreaLRX = mapArea.getLowerRightX() - referenceMarkSize;
                    int viewAreaLRY = mapArea.getLowerRightY() - referenceMarkSize;
                    double viewAreaWidth = viewAreaLRX - viewAreaULX;
                    double viewAreaHeight = viewAreaLRY - viewAreaULY;

                    if (mapArea.isSizeMaximizedToScreenSize() && !printingMap) {
                        viewAreaULX = (int) ((-pageLeft / scale) + referenceMarkSize);
                        viewAreaULY = (int) ((-pageTop / scale) + referenceMarkSize);
                        viewAreaLRX = viewAreaULX + (int) (w / scale - 2 * referenceMarkSize);
                        viewAreaLRY = viewAreaULY + (int) (h / scale - 2 * referenceMarkSize);
                        viewAreaWidth = viewAreaLRX - viewAreaULX;
                        viewAreaHeight = viewAreaLRY - viewAreaULY;
                    }

                    if (!isBetween(x, viewAreaULX, viewAreaLRX) || !isBetween(y, viewAreaULY, viewAreaLRY)) {
                        return;
                    }

                    double xRange = Math.abs(currentExtent.getMaxX() - currentExtent.getMinX());
                    double yRange = Math.abs(currentExtent.getMaxY() - currentExtent.getMinY());

                    modifyPixelsY = currentExtent.getMinY() + (viewAreaLRY - y) / viewAreaHeight * yRange;
                    modifyPixelsX = currentExtent.getMinX() + (x - viewAreaULX) / viewAreaWidth * xRange;

                    GridCell point = mapArea.getRowAndColumn(modifyPixelsX, modifyPixelsY);
                    if (point.row >= 0) {
                        host.refreshMap(false);
                        RasterLayerInfo rli = (RasterLayerInfo) mapArea.getActiveLayer();
                        String fileName = new File(rli.getHeaderFile()).getName();
                        ModifyPixel mp = new ModifyPixel((Frame) findWindow(this), true, point, fileName);
                        if (mp.wasSuccessful()) {
                            point = mp.getValue();
                            rli.setDataValue(point.row, point.col, point.z);
                            rli.update();
                            host.refreshMap(false);
                            //mapinfo.setRowAndColumn(mp.getValue());
                        }
                    } else {
                        modifyPixelsX = -1;
                        modifyPixelsY = -1;
                        host.refreshMap(false);
                    }
                }

            }
//            modifyPixelsX = (int) ((e.getX() - pageLeft) / scale);
//            modifyPixelsY = (int) ((e.getY() - pageTop) / scale);
//            double myWidth = this.getWidth() - borderWidth * 2;
//            double myHeight = this.getHeight() - borderWidth * 2;
//            double y = mapExtent.getMaxY() - (modifyPixelsY - borderWidth) / myHeight * (mapExtent.getMaxY() - mapExtent.getMinY());
//            double x = mapExtent.getMinX() + (modifyPixelsX - borderWidth) / myWidth * (mapExtent.getMaxX() - mapExtent.getMinX());
//            GridCell point = mapinfo.getRowAndColumn(x, y);
//            if (point.row >= 0) {
//                host.refreshMap(false);
//                RasterLayerInfo rli = (RasterLayerInfo) (mapinfo.getLayer(point.layerNum));
//                String fileName = new File(rli.getHeaderFile()).getName();
//                ModifyPixel mp = new ModifyPixel((Frame) findWindow(this), true, point, fileName);
//                if (mp.wasSuccessful()) {
//                    point = mp.getValue();
//                    rli.setDataValue(point.row, point.col, point.z);
//                    rli.update();
//                    host.refreshMap(false);
//                    //mapinfo.setRowAndColumn(mp.getValue());
//                }
//            } else {
//                modifyPixelsX = -1;
//                modifyPixelsY = -1;
//                host.refreshMap(false);
//            }

        } else if (clickCount == 1 && usingDistanceTool) {
            if (map.getCartographicElement(whichCartoElement) instanceof MapArea) {
                MapArea mapArea = (MapArea) map.getCartographicElement(whichCartoElement);
                BoundingBox currentExtent = mapArea.getCurrentMapExtent();
                if (mapArea.isVisible() && currentExtent.getMinY() != currentExtent.getMaxY()) {
                    int x = (int) ((e.getX() - pageLeft) / scale);
                    int y = (int) ((e.getY() - pageTop) / scale);

                    int referenceMarkSize = mapArea.getReferenceMarksSize();
                    int viewAreaULX = mapArea.getUpperLeftX() + referenceMarkSize;
                    int viewAreaULY = mapArea.getUpperLeftY() + referenceMarkSize;
                    int viewAreaLRX = mapArea.getLowerRightX() - referenceMarkSize;
                    int viewAreaLRY = mapArea.getLowerRightY() - referenceMarkSize;
                    double viewAreaWidth = viewAreaLRX - viewAreaULX;
                    double viewAreaHeight = viewAreaLRY - viewAreaULY;

                    if (mapArea.isSizeMaximizedToScreenSize() && !printingMap) {
                        viewAreaULX = (int) ((-pageLeft / scale) + referenceMarkSize);
                        viewAreaULY = (int) ((-pageTop / scale) + referenceMarkSize);
                        viewAreaLRX = viewAreaULX + (int) (w / scale - 2 * referenceMarkSize);
                        viewAreaLRY = viewAreaULY + (int) (h / scale - 2 * referenceMarkSize);
                        viewAreaWidth = viewAreaLRX - viewAreaULX;
                        viewAreaHeight = viewAreaLRY - viewAreaULY;
                    }

                    if (!isBetween(x, viewAreaULX, viewAreaLRX) || !isBetween(y, viewAreaULY, viewAreaLRY)) {
                        return;
                    }

                    double xRange = Math.abs(currentExtent.getMaxX() - currentExtent.getMinX());
                    double yRange = Math.abs(currentExtent.getMaxY() - currentExtent.getMinY());

                    mapY = currentExtent.getMinY() + (viewAreaLRY - y) / viewAreaHeight * yRange;
                    mapX = currentExtent.getMinX() + (x - viewAreaULX) / viewAreaWidth * xRange;

                }
                distPoints.add(new XYPoint(mapX, mapY));
                updateStatus(e, (MapArea) map.getCartographicElement(whichCartoElement));
                host.refreshMap(true);
            }
        } else if (clickCount == 1 && (myMode == MOUSE_MODE_CARTO_ELEMENT
                || (myMode == MOUSE_MODE_MAPAREA && backgroundMouseMode != MOUSE_MODE_SELECT))) {
            boolean isSelected = map.getCartographicElement(whichCartoElement).isSelected();
            if (!isSelected) {
                if (!e.isShiftDown()) {
                    map.deslectAllCartographicElements();
                }
                map.getCartographicElement(whichCartoElement).setSelected(true);
                this.setCursor(panCursor);
                if (map.getCartographicElement(whichCartoElement) instanceof MapArea) {
                    map.setActiveMapAreaByElementNum(map.getCartographicElement(whichCartoElement).getElementNumber());
                    host.refreshMap(true);

//                    if (usingDistanceTool) {
//                        distPoints.add(new XYPoint(e.getX(), e.getY()));
//                    }
                }
            } else if (myMode == MOUSE_MODE_CARTO_ELEMENT) {
                map.getCartographicElement(whichCartoElement).setSelected(false);
                this.setCursor(selectCursor);
            } else {
                map.getCartographicElement(whichCartoElement).setSelected(false);
                switch (backgroundMouseMode) {
                    case MOUSE_MODE_ZOOM:
                        this.setCursor(zoomCursor);
                        break;
                    case MOUSE_MODE_PAN:
                        this.setCursor(panCursor);
                        break;
                    case MOUSE_MODE_SELECT:
                        this.setCursor(selectCursor);
                        break;
                }
            }
            this.repaint();
        } else if (clickCount == 1 && (myMode == MOUSE_MODE_MAPAREA
                && backgroundMouseMode != MOUSE_MODE_SELECT)) {
            map.deslectAllCartographicElements();
            this.repaint();
        } else if (clickCount == 1 && backgroundMouseMode == MOUSE_MODE_SELECT) {
            if (myMode == MOUSE_MODE_MAPAREA) { //map.getCartographicElement(whichCartoElement) instanceof MapArea) {
                MapArea mapArea = (MapArea) map.getCartographicElement(whichCartoElement);
                if (mapArea != null) {
                    mapArea.selectVectorFeatures(mapX, mapY);
                    updateStatus(e, mapArea);
                }
            }
        } else if (clickCount == 2 && usingDistanceTool) {
            if (usingDistanceTool) {
                distPoints.clear();
            }
        }

        this.requestFocus();
    }
    double startX;
    double startY;
    double endX;
    double endY;
    int startCol;
    int startRow;
    int endCol;
    int endRow;
    boolean mouseDragged = false;
    double mapX, mapY;

    @Override
    public void mousePressed(MouseEvent me) {
        try {
            startCol = (int) ((me.getX() - pageLeft) / scale);
            startRow = (int) ((me.getY() - pageTop) / scale);

            if (myMode == MOUSE_MODE_MAPAREA) {
                MapArea mapArea = (MapArea) map.getCartographicElement(whichCartoElement);
                BoundingBox currentExtent = mapArea.getCurrentMapExtent();
                if (mapArea.isVisible() && currentExtent.getMinY() != currentExtent.getMaxY()) {
                    int x = (int) ((me.getX() - pageLeft) / scale);
                    int y = (int) ((me.getY() - pageTop) / scale);

                    int referenceMarkSize = mapArea.getReferenceMarksSize();
                    int viewAreaULX = mapArea.getUpperLeftX() + referenceMarkSize;
                    int viewAreaULY = mapArea.getUpperLeftY() + referenceMarkSize;
                    int viewAreaLRX = mapArea.getLowerRightX() - referenceMarkSize;
                    int viewAreaLRY = mapArea.getLowerRightY() - referenceMarkSize;
                    double viewAreaWidth = viewAreaLRX - viewAreaULX;
                    double viewAreaHeight = viewAreaLRY - viewAreaULY;

                    if (mapArea.isSizeMaximizedToScreenSize() && !printingMap) {
                        viewAreaULX = (int) ((-pageLeft / scale) + referenceMarkSize);
                        viewAreaULY = (int) ((-pageTop / scale) + referenceMarkSize);
                        viewAreaLRX = viewAreaULX + (int) (w / scale - 2 * referenceMarkSize);
                        viewAreaLRY = viewAreaULY + (int) (h / scale - 2 * referenceMarkSize);
                        viewAreaWidth = viewAreaLRX - viewAreaULX;
                        viewAreaHeight = viewAreaLRY - viewAreaULY;
                    }

                    if (!isBetween(x, viewAreaULX, viewAreaLRX) || !isBetween(y, viewAreaULY, viewAreaLRY)) {
                        return;
                    }

                    double xRange = Math.abs(currentExtent.getMaxX() - currentExtent.getMinX());
                    double yRange = Math.abs(currentExtent.getMaxY() - currentExtent.getMinY());

                    mapY = currentExtent.getMinY() + (viewAreaLRY - y) / viewAreaHeight * yRange;
                    mapX = currentExtent.getMinX() + (x - viewAreaULX) / viewAreaWidth * xRange;

                    if (backgroundMouseMode == MOUSE_MODE_PAN) {
                        this.setCursor(panClosedHandCursor);
                    }
                }
            }

            this.requestFocus();
        } catch (Exception e) {
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (mouseDragged && (myMode == MOUSE_MODE_ZOOM
                || myMode == MOUSE_MODE_CARTO_ELEMENT)
                && !usingDistanceTool
                && (myMode != MOUSE_MODE_RESIZE)) {
            boolean zoomToBox = true;
            if (myMode == MOUSE_MODE_CARTO_ELEMENT) {
                CartographicElement ce = map.getCartographicElement(whichCartoElement);
                if (ce.isSelected()) {
                    zoomToBox = false;
                }
            }
            if (zoomToBox) {
                int x = (int) ((e.getX() - pageLeft) / scale);
                int y = (int) ((e.getY() - pageTop) / scale);

                BoundingBox pageExtent = map.getPageExtent();

                // it hasn't yet been initialized
                pageExtent.setMinX(Math.min(x, startCol)); //pageZoomX));
                pageExtent.setMinY(Math.min(y, startRow)); //pageZoomY));
                pageExtent.setMaxX(Math.max(x, startCol)); //pageZoomX));
                pageExtent.setMaxY(Math.max(y, startRow)); //pageZoomY));

                map.setPageExtent(pageExtent);
            }

        } else if (mouseDragged && (myMode == MOUSE_MODE_RESIZE)) {
            CartographicElement ce = map.getCartographicElement(whichCartoElement);

            int x = (int) ((e.getX() - pageLeft) / scale);
            int y = (int) ((e.getY() - pageTop) / scale);

            ce.resize(x, y, myResizeMode);
        } else if (mouseDragged && myMode == MOUSE_MODE_MAPAREA) {
            MapArea mapArea = (MapArea) map.getCartographicElement(whichCartoElement);
            BoundingBox currentExtent = mapArea.getCurrentMapExtent();
            if (mapArea.isVisible() && currentExtent.getMinY() != currentExtent.getMaxY()
                    && !mapArea.isSelected()) {
                int x = (int) ((e.getX() - pageLeft) / scale);
                int y = (int) ((e.getY() - pageTop) / scale);

                int referenceMarkSize = mapArea.getReferenceMarksSize();
                int viewAreaULX = mapArea.getUpperLeftX() + referenceMarkSize;
                int viewAreaULY = mapArea.getUpperLeftY() + referenceMarkSize;
                int viewAreaLRX = mapArea.getLowerRightX() - referenceMarkSize;
                int viewAreaLRY = mapArea.getLowerRightY() - referenceMarkSize;
                double viewAreaWidth = viewAreaLRX - viewAreaULX;
                double viewAreaHeight = viewAreaLRY - viewAreaULY;

                if (mapArea.isSizeMaximizedToScreenSize() && !printingMap) {
                    viewAreaULX = (int) ((-pageLeft / scale) + referenceMarkSize);
                    viewAreaULY = (int) ((-pageTop / scale) + referenceMarkSize);
                    viewAreaLRX = viewAreaULX + (int) (w / scale - 2 * referenceMarkSize);
                    viewAreaLRY = viewAreaULY + (int) (h / scale - 2 * referenceMarkSize);
                    viewAreaWidth = viewAreaLRX - viewAreaULX;
                    viewAreaHeight = viewAreaLRY - viewAreaULY;
                }

//                if (!isBetween(x, viewAreaULX, viewAreaLRX) || !isBetween(y, viewAreaULY, viewAreaLRY)) {
//                    status.setMessage("");
//                    return;
//                }

                double xRange = Math.abs(currentExtent.getMaxX() - currentExtent.getMinX());
                double yRange = Math.abs(currentExtent.getMaxY() - currentExtent.getMinY());

                double mapYEnd = currentExtent.getMinY() + (viewAreaLRY - y) / viewAreaHeight * yRange;
                double mapXEnd = currentExtent.getMinX() + (x - viewAreaULX) / viewAreaWidth * xRange;

                if (backgroundMouseMode == MOUSE_MODE_ZOOM) {
                    BoundingBox bb = new BoundingBox(Math.min(mapX, mapXEnd),
                            Math.min(mapY, mapYEnd),
                            Math.max(mapX, mapXEnd),
                            Math.max(mapY, mapYEnd));
                    mapArea.setCurrentExtent(bb);
                } else if (backgroundMouseMode == MOUSE_MODE_PAN) {
                    double deltaX = mapX - mapXEnd;
                    double deltaY = mapY - mapYEnd;
                    BoundingBox bb = new BoundingBox(currentExtent.getMinX() + deltaX,
                            currentExtent.getMinY() + deltaY,
                            currentExtent.getMaxX() + deltaX,
                            currentExtent.getMaxY() + deltaY);
                    mapArea.setCurrentExtent(bb);
                    this.setCursor(panCursor);
                }

            }
        }
        mouseDragged = false;
        this.repaint();
    }

    @Override
    public void mouseEntered(MouseEvent me) {
        //throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void mouseExited(MouseEvent me) {
        //throw new UnsupportedOperationException("Not supported yet.");
    }

    private void calculateMapXY(MouseEvent e, MapArea mapArea) {
        BoundingBox currentExtent = mapArea.getCurrentMapExtent();
        if (mapArea.isVisible() && currentExtent.getMinY() != currentExtent.getMaxY()) {
            int x = (int) ((e.getX() - pageLeft) / scale);
            int y = (int) ((e.getY() - pageTop) / scale);

            int referenceMarkSize = mapArea.getReferenceMarksSize();
            int viewAreaULX = mapArea.getUpperLeftX() + referenceMarkSize;
            int viewAreaULY = mapArea.getUpperLeftY() + referenceMarkSize;
            int viewAreaLRX = mapArea.getLowerRightX() - referenceMarkSize;
            int viewAreaLRY = mapArea.getLowerRightY() - referenceMarkSize;
            double viewAreaWidth = viewAreaLRX - viewAreaULX;
            double viewAreaHeight = viewAreaLRY - viewAreaULY;

            if (mapArea.isSizeMaximizedToScreenSize() && !printingMap) {
                viewAreaULX = (int) ((-pageLeft / scale) + referenceMarkSize);
                viewAreaULY = (int) ((-pageTop / scale) + referenceMarkSize);
                viewAreaLRX = viewAreaULX + (int) (w / scale - 2 * referenceMarkSize);
                viewAreaLRY = viewAreaULY + (int) (h / scale - 2 * referenceMarkSize);
                viewAreaWidth = viewAreaLRX - viewAreaULX;
                viewAreaHeight = viewAreaLRY - viewAreaULY;
            }

            double xRange = Math.abs(currentExtent.getMaxX() - currentExtent.getMinX());
            double yRange = Math.abs(currentExtent.getMaxY() - currentExtent.getMinY());

            mapY = currentExtent.getMinY() + (viewAreaLRY - y) / viewAreaHeight * yRange;
            mapX = currentExtent.getMinX() + (x - viewAreaULX) / viewAreaWidth * xRange;
        }
    }

    //private String xyUnits = null;
    private void updateStatus(MouseEvent e, MapArea mapArea) {
        if (status == null) {
            return;
        }
        BoundingBox currentExtent = mapArea.getCurrentMapExtent();
        if (mapArea.isVisible() && currentExtent.getMinY() != currentExtent.getMaxY()) {
            int x = (int) ((e.getX() - pageLeft) / scale);
            int y = (int) ((e.getY() - pageTop) / scale);

            int referenceMarkSize = mapArea.getReferenceMarksSize();
            int viewAreaULX = mapArea.getUpperLeftX() + referenceMarkSize;
            int viewAreaULY = mapArea.getUpperLeftY() + referenceMarkSize;
            int viewAreaLRX = mapArea.getLowerRightX() - referenceMarkSize;
            int viewAreaLRY = mapArea.getLowerRightY() - referenceMarkSize;
            double viewAreaWidth = viewAreaLRX - viewAreaULX;
            double viewAreaHeight = viewAreaLRY - viewAreaULY;

            if (mapArea.isSizeMaximizedToScreenSize() && !printingMap) {
                viewAreaULX = (int) ((-pageLeft / scale) + referenceMarkSize);
                viewAreaULY = (int) ((-pageTop / scale) + referenceMarkSize);
                viewAreaLRX = viewAreaULX + (int) (w / scale - 2 * referenceMarkSize);
                viewAreaLRY = viewAreaULY + (int) (h / scale - 2 * referenceMarkSize);
                viewAreaWidth = viewAreaLRX - viewAreaULX;
                viewAreaHeight = viewAreaLRY - viewAreaULY;
            }

            if (!isBetween(x, viewAreaULX, viewAreaLRX) || !isBetween(y, viewAreaULY, viewAreaLRY)) {
                status.setMessage("");
                return;
            }

            double xRange = Math.abs(currentExtent.getMaxX() - currentExtent.getMinX());
            double yRange = Math.abs(currentExtent.getMaxY() - currentExtent.getMinY());

            double mapY2 = currentExtent.getMinY() + (viewAreaLRY - y) / viewAreaHeight * yRange;
            double mapX2 = currentExtent.getMinX() + (x - viewAreaULX) / viewAreaWidth * xRange;

            DecimalFormat df = new DecimalFormat("###,###,###.0");
            String xStr = df.format(mapX2);
            String yStr = df.format(mapY2);

            if (usingDistanceTool && distPoints.size() > 1) {
                calculatedDistance = 0;
                double mapX3, mapY3;
                for (int i = 1; i < distPoints.size(); i++) {
                    mapX2 = distPoints.get(i).x;
                    mapY2 = distPoints.get(i).y;
                    mapX3 = distPoints.get(i - 1).x;
                    mapY3 = distPoints.get(i - 1).y;
                    calculatedDistance += Math.sqrt((mapX3 - mapX2) * (mapX3 - mapX2)
                            + (mapY3 - mapY2) * (mapY3 - mapY2));
                }
                //calculatedDistance = Math.sqrt(calculatedDistance);
                xStr = "Distance: " + df.format(calculatedDistance);
                if (!mapArea.getXYUnits().toLowerCase().equals("not specified")) {
                    xStr += mapArea.getXYUnits();
                }
                if (distPoints.size() > 2) {
                    xStr += "   Enclosing Area: " + df.format(calculateArea()) + " sqr. units";
                }
                status.setMessage(xStr);

            } else if (!mapArea.isActiveLayerAVector()) {
                GridCell point = mapArea.getRowAndColumn(mapX2, mapY2);
                if (point.row >= 0) {
                    //double noDataValue = point.noDataValue;
                    DecimalFormat dfZ = new DecimalFormat("###,###,###.####");
                    String zStr;
                    if (!point.isValueNoData() && !Double.isNaN(point.z)) {
                        zStr = dfZ.format(point.z);
                    } else if (Double.isNaN(point.z)) {
                        zStr = "Not Available";
                    } else {
                        zStr = "NoData";
                    }
                    if (!point.isRGB || point.isValueNoData()) {
                        status.setMessage("E: " + xStr + "  N: " + yStr
                                + "  Row: " + (int) (point.row) + "  Col: "
                                + (int) (point.col) + "  Z: " + zStr);
                    } else {
                        String r = String.valueOf((int) point.z & 0xFF);
                        String g = String.valueOf(((int) point.z >> 8) & 0xFF);
                        String b = String.valueOf(((int) point.z >> 16) & 0xFF);
                        String a = String.valueOf(((int) point.z >> 24) & 0xFF);
                        if (a.equals("255")) {
                            status.setMessage("E: " + xStr + "  N: " + yStr
                                    + "  Row: " + (int) (point.row) + "  Col: "
                                    + (int) (point.col) + "  R: " + r + "  G: " + g
                                    + "  B: " + b);
                        } else {
                            status.setMessage("E: " + xStr + "  N: " + yStr
                                    + "  Row: " + (int) (point.row) + "  Col: "
                                    + (int) (point.col) + "  R: " + r + "  G: " + g
                                    + "  B: " + b + "  A: " + a);
                        }
                    }
                } else if (!Double.isNaN(x) && !Double.isNaN(y)) {
                    status.setMessage("E: " + xStr + "  N: " + yStr);
                }
            } else {
                int selectedFeature = mapArea.getSelectedFeatureFromActiveVector();
                if (selectedFeature >= 0) {
                    status.setMessage("E: " + xStr + "  N: " + yStr + "  Selected Feature: " + selectedFeature);
                } else {
                    status.setMessage("E: " + xStr + "  N: " + yStr);
                }
            }
        }
    }
    
    private static Window findWindow(Component c) {
        if (c instanceof Window) {
            return (Window) c;
        } else {
            return findWindow(c.getParent());
        }
    }
}

