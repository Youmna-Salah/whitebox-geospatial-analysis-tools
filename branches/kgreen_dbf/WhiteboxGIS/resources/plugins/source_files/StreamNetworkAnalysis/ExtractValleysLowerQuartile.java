/*
 * Copyright (C) 2011-2012 Dr. John Lindsay <jlindsay@uoguelph.ca>
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
package plugins;

import java.util.Arrays;
import java.util.Date;
import whitebox.geospatialfiles.WhiteboxRaster;
import whitebox.interfaces.WhiteboxPlugin;
import whitebox.interfaces.WhiteboxPluginHost;

/**
 * WhiteboxPlugin is used to define a plugin tool for Whitebox GIS.
 * @author Dr. John Lindsay <jlindsay@uoguelph.ca>
 */
public class ExtractValleysLowerQuartile implements WhiteboxPlugin {
    
    private WhiteboxPluginHost myHost = null;
    private String[] args;
    /**
     * Used to retrieve the plugin tool's name. This is a short, unique name containing no spaces.
     * @return String containing plugin name.
     */
    @Override
    public String getName() {
        return "ExtractValleysLowerQuartile";
    }
    /**
     * Used to retrieve the plugin tool's descriptive name. This can be a longer name (containing spaces) and is used in the interface to list the tool.
     * @return String containing the plugin descriptive name.
     */
    @Override
    public String getDescriptiveName() {
    	return "Extract Valleys (Lower Quartile)";
    }
    /**
     * Used to retrieve a short description of what the plugin tool does.
     * @return String containing the plugin's description.
     */
    @Override
    public String getToolDescription() {
    	return "Identifies potential valley bottom grid cells.";
    }
    /**
     * Used to identify which toolboxes this plugin tool should be listed in.
     * @return Array of Strings.
     */
    @Override
    public String[] getToolbox() {
    	String[] ret = { "StreamAnalysis" };
    	return ret;
    }
    /**
     * Sets the WhiteboxPluginHost to which the plugin tool is tied. This is the class
     * that the plugin will send all feedback messages, progress updates, and return objects.
     * @param host The WhiteboxPluginHost that called the plugin tool.
     */
    @Override
    public void setPluginHost(WhiteboxPluginHost host) {
        myHost = host;
    }
    /**
     * Used to communicate feedback pop-up messages between a plugin tool and the main Whitebox user-interface.
     * @param feedback String containing the text to display.
     */
    private void showFeedback(String message) {
        if (myHost != null) {
            myHost.showFeedback(message);
        } else {
            System.out.println(message);
        }
    }
    /**
     * Used to communicate a return object from a plugin tool to the main Whitebox user-interface.
     * @return Object, such as an output WhiteboxRaster.
     */
    private void returnData(Object ret) {
        if (myHost != null) {
            myHost.returnData(ret);
        }
    }

    private int previousProgress = 0;
    private String previousProgressLabel = "";
    /**
     * Used to communicate a progress update between a plugin tool and the main Whitebox user interface.
     * @param progressLabel A String to use for the progress label.
     * @param progress Float containing the progress value (between 0 and 100).
     */
    private void updateProgress(String progressLabel, int progress) {
        if (myHost != null && ((progress != previousProgress) || 
                (!progressLabel.equals(previousProgressLabel)))) {
            myHost.updateProgress(progressLabel, progress);
        }
        previousProgress = progress;
        previousProgressLabel = progressLabel;
    }
    /**
     * Used to communicate a progress update between a plugin tool and the main Whitebox user interface.
     * @param progress Float containing the progress value (between 0 and 100).
     */
    private void updateProgress(int progress) {
        if (myHost != null && progress != previousProgress) {
            myHost.updateProgress(progress);
        }
        previousProgress = progress;
    }
    /**
     * Sets the arguments (parameters) used by the plugin.
     * @param args 
     */
    @Override
    public void setArgs(String[] args) {
        this.args = args.clone();
    }
    
    private boolean cancelOp = false;
    /**
     * Used to communicate a cancel operation from the Whitebox GUI.
     * @param cancel Set to true if the plugin should be canceled.
     */
    @Override
    public void setCancelOp(boolean cancel) {
        cancelOp = cancel;
    }
    
    private void cancelOperation() {
        showFeedback("Operation cancelled.");
        updateProgress("Progress: ", 0);
    }
    
    private boolean amIActive = false;
    /**
     * Used by the Whitebox GUI to tell if this plugin is still running.
     * @return a boolean describing whether or not the plugin is actively being used.
     */
    @Override
    public boolean isActive() {
        return amIActive;
    }

    @Override
    public void run() {
        amIActive = true;
        
        String inputHeader = null;
        String outputHeader = null;
        int row, col, rows, cols, x, y;
        int progress = 0;
        double z, zN, noData, outputNoData;
        int i, n;
        int[] dX;
        int[] dY;
        double[] filterShape;
        double[] data;
        double largeValue = Float.POSITIVE_INFINITY;
        int numPixelsInFilter;
        int filterSize, midPoint, lowerQuartile;
        boolean performLineThinning = false;
        
        if (args.length <= 0) {
            showFeedback("Plugin parameters have not been set.");
            return;
        }
        
        inputHeader = args[0];
        outputHeader = args[1];
        filterSize = Integer.parseInt(args[2]);
        performLineThinning = Boolean.parseBoolean(args[3]);

        // check to see that the inputHeader and outputHeader are not null.
        if ((inputHeader == null) || (outputHeader == null)) {
            showFeedback("One or more of the input parameters have not been set properly.");
            return;
        }

        try {
            
            //the filter dimensions must be odd numbers such that there is a middle pixel
            if (Math.floor(filterSize / 2d) == (filterSize / 2d)) {
                showFeedback("Filter dimensions must be odd numbers. The specified filter x-dimension" + 
                        " has been modified.");
                
                filterSize++;
            }
            numPixelsInFilter = filterSize * filterSize;
            dX = new int[numPixelsInFilter];
            dY = new int[numPixelsInFilter];
            filterShape = new double[numPixelsInFilter];
            
            //fill the filter DX and DY values
            midPoint = (int) Math.floor(filterSize / 2);
            //see which pixels in the filter lie within the largest ellipse 
            //that fits in the filter box 
            double aSqr = midPoint * midPoint;
            i = 0;
            for (row = 0; row < filterSize; row++) {
                for (col = 0; col < filterSize; col++) {
                    dX[i] = col - midPoint;
                    dY[i] = row - midPoint;
                    z = (dX[i] * dX[i]) / aSqr + (dY[i] * dY[i]) / aSqr;
                    if (z > 1) {
                        filterShape[i] = 0;
                    } else {
                        filterShape[i] = 1;
                    }
                    i++;
                }
            }

            
            WhiteboxRaster DEM = new WhiteboxRaster(inputHeader, "r");
            
            rows = DEM.getNumberRows();
            cols = DEM.getNumberColumns();
            noData = DEM.getNoDataValue();
            outputNoData = -32768;
                    
            WhiteboxRaster output = new WhiteboxRaster(outputHeader, "rw", 
                    inputHeader, WhiteboxRaster.DataType.INTEGER, 0);
            output.setNoDataValue(outputNoData);
            output.setPreferredPalette("qual.pal");
            output.setDataScale(WhiteboxRaster.DataScale.CATEGORICAL);
            output.setZUnits("dimensionless");
            
            for (row = 0; row < rows; row++) {
                for (col = 0; col < cols; col++) {
                    z = DEM.getValue(row, col);
                    if (z != noData) {
                        data = new double[numPixelsInFilter];
                        n = 0;
                        for (i = 0; i < numPixelsInFilter; i++) {
                            x = col + dX[i];
                            y = row + dY[i];
                            zN = DEM.getValue(y, x);
                            if (zN != noData) {
                                data[i] = zN;
                                n++;
                            } else {
                                data[i] = largeValue;
                            }
                        }
                        if (n > 0) {
                            // sort the array
                            Arrays.sort(data);
                            lowerQuartile = n / 4;
                            if (z <= data[lowerQuartile]) {
                                output.setValue(row, col, 1);
                            }
                        }
                        
                    } else {
                        output.setValue(row, col, outputNoData);
                    }
                }
                if (cancelOp) {
                    cancelOperation();
                    return;
                }
                progress = (int) (100f * row / (rows - 1));
                updateProgress(progress);
            }
            
            if (performLineThinning) {
                long counter = 0;
                int loopNum = 0;
                int a;
                dX = new int[]{1, 1, 1, 0, -1, -1, -1, 0};
                dY = new int[]{-1, 0, 1, 1, 1, 0, -1, -1};
                int[][] elements = {{6, 7, 0, 4, 3, 2}, {7, 0, 1, 3, 5},
                    {0, 1, 2, 4, 5, 6}, {1, 2, 3, 5, 7},
                    {2, 3, 4, 6, 7, 0}, {3, 4, 5, 7, 1},
                    {4, 5, 6, 0, 1, 2}, {5, 6, 7, 1, 3}};
                double[][] vals = {{0, 0, 0, 1, 1, 1}, {0, 0, 0, 1, 1},
                    {0, 0, 0, 1, 1, 1}, {0, 0, 0, 1, 1},
                    {0, 0, 0, 1, 1, 1}, {0, 0, 0, 1, 1},
                    {0, 0, 0, 1, 1, 1}, {0, 0, 0, 1, 1}};

                double[] neighbours = new double[8];
                boolean patternMatch = false;

                do {
                    loopNum++;
                    updateProgress("Loop Number " + loopNum + ":", 0);
                    counter = 0;
                    for (row = 0; row < rows; row++) {
                        for (col = 0; col < cols; col++) {
                            z = output.getValue(row, col);
                            if (z > 0 && z != noData) {
                                // fill the neighbours array
                                for (i = 0; i < 8; i++) {
                                    neighbours[i] = output.getValue(row + dY[i], col + dX[i]);
                                }

                                for (a = 0; a < 8; a++) {
                                    // scan through element
                                    patternMatch = true;
                                    for (i = 0; i < elements[a].length; i++) {
                                        if (neighbours[elements[a][i]] != vals[a][i]) {
                                            patternMatch = false;
                                        }
                                    }
                                    if (patternMatch) {
                                        output.setValue(row, col, 0);
                                        counter++;
                                    }
                                }
                            }

                        }
                        if (cancelOp) {
                            cancelOperation();
                            return;
                        }
                        progress = (int) (100f * row / (rows - 1));
                        updateProgress(progress);
                    }


                } while (counter > 0);

            }

            output.addMetadataEntry("Created by the "
                    + getDescriptiveName() + " tool.");
            output.addMetadataEntry("Created on " + new Date());
            
            DEM.close();
            output.close();

            // returning a header file string displays the image.
            returnData(outputHeader);

        } catch (Exception e) {
            showFeedback(e.getMessage());
        } finally {
            updateProgress("Progress: ", 0);
            // tells the main application that this process is completed.
            amIActive = false;
            myHost.pluginComplete();
        }
    }
}