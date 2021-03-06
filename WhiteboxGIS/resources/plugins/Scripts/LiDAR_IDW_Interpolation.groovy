/*
 * Copyright (C) 2013 Dr. John Lindsay <jlindsay@uoguelph.ca>
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
 
import java.awt.event.ActionListener
import java.awt.event.ActionEvent
import java.util.concurrent.Future
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.Date
import java.util.ArrayList
import whitebox.interfaces.WhiteboxPluginHost
import whitebox.geospatialfiles.LASReader
import whitebox.geospatialfiles.LASReader.PointRecord
import whitebox.geospatialfiles.LASReader.PointRecColours
import whitebox.geospatialfiles.WhiteboxRaster
import whitebox.geospatialfiles.WhiteboxRasterBase.DataType
import whitebox.structures.BoundingBox
import whitebox.structures.KdTree
import whitebox.ui.plugin_dialog.ScriptDialog
import whitebox.utilities.StringUtilities
import whitebox.utilities.FileUtilities;
import groovy.transform.CompileStatic

// The following four variables are required for this 
// script to be integrated into the tool tree panel. 
// Comment them out if you want to remove the script.
def name = "LiDAR_IDW_Interpolation"
def descriptiveName = "IDW Interpolation (LiDAR)"
def description = "Interpolates LAS files using an inverse-distance to a weight scheme."
def toolboxes = ["LidarTools"]

public class LiDAR_IDW_Interpolation implements ActionListener {
	private WhiteboxPluginHost pluginHost
	private ScriptDialog sd;
	private String descriptiveName
	
	private AtomicInteger numSolvedTiles = new AtomicInteger(0)
	
	public LiDAR_IDW_Interpolation(WhiteboxPluginHost pluginHost, 
		String[] args, def name, def descriptiveName) {
		this.pluginHost = pluginHost
		this.descriptiveName = descriptiveName
			
		if (args.length > 0) {
			execute(args)
		} else {
			// Create a dialog for this tool to collect user-specified
			// tool parameters.
			sd = new ScriptDialog(pluginHost, descriptiveName, this)	
		
			// Specifying the help file will display the html help
			// file in the help pane. This file should be be located 
			// in the help directory and have the same name as the 
			// class, with an html extension.
			sd.setHelpFile(name)
		
			// Specifying the source file allows the 'view code' 
			// button on the tool dialog to be displayed.
			def pathSep = File.separator
			def scriptFile = pluginHost.getResourcesDirectory() + "plugins" + pathSep + "Scripts" + pathSep + name + ".groovy"
			sd.setSourceFile(scriptFile)
			
			// add some components to the dialog
			sd.addDialogMultiFile("Select the input LAS files", "Input LAS Files:", "LAS Files (*.las), LAS")
			sd.addDialogDataInput("Output File Suffix (e.g. _lastReturn)", "Output File Suffix (e.g. _lastReturn)", "", false, false)
			sd.addDialogComboBox("Interpolation Paramter:", "Interpolation Parameter:", ["Z (elevation)", "Intensity", "Scan Angle"], 0)
			sd.addDialogComboBox("Point Return:", "Point Return:", ["All Points", "First Return", "Last Return"], 0)
			sd.addDialogDataInput("IDW Exponent", "IDW Exponent", "2", true, false)
			sd.addDialogDataInput("Max Search Distance (m)", "Max Search Distance (m)", "", true, false)
			sd.addDialogDataInput("Grid Resolution (m)", "Grid Resolution (m)", "", true, false)
			sd.addDialogDataInput("Max Scan Angle Deviation", "Max Scan Angle Deviation", "5.0", true, false)
			sd.addDialogLabel("Exclude points with the following classification")
			sd.addDialogLabel("values from the interpolation:")
			sd.addDialogCheckBox("Never Classified", "Never Classified", false)
			sd.addDialogCheckBox("Unclassified", "Unclassified", false)
			sd.addDialogCheckBox("Bare Ground", "Bare Ground", false)
			sd.addDialogCheckBox("Low Vegetation", "Low Vegetation", false)
			sd.addDialogCheckBox("Medium Vegetation", "Medium Vegetation", false)
			sd.addDialogCheckBox("High Vegetation", "High Vegetation", false)
			sd.addDialogCheckBox("Building", "Building", false)
			sd.addDialogCheckBox("Low Points", "Low Points", false)
			sd.addDialogCheckBox("Model Key Points", "Model Key Points", false)
			sd.addDialogCheckBox("Water", "Water", false)
			
			// resize the dialog to the standard size and display it
			sd.setSize(800, 400)
			sd.visible = true
		}
	}

	// The CompileStatic annotation can be used to significantly
	// improve the performance of a Groovy script to nearly 
	// that of native Java code.
	@CompileStatic
	private void execute(String[] args) {
		long start = System.currentTimeMillis()  
	  try {
	  	if (args.length != 18) {
			pluginHost.showFeedback("Incorrect number of arguments given to tool.")
			return
		}
		
		// read the input parameters
		final String inputFileString = args[0]
		String suffix = ""
		if (args[1] != null) {
			suffix = " " + args[1].trim();
		}
        String whatToInterpolate = args[2].toLowerCase();
        String returnNumberToInterpolate = args[3].toLowerCase();
        double weight = Double.parseDouble(args[4]);
		double maxDist = Double.parseDouble(args[5]);
		double resolution = Double.parseDouble(args[6]);
        double maxScanAngleDeviation = Double.parseDouble(args[7]);
        final boolean excludeNeverClassified = Boolean.parseBoolean(args[8]);
        final boolean excludeUnclassified = Boolean.parseBoolean(args[9]);
        final boolean excludeBareGround = Boolean.parseBoolean(args[10]);
        final boolean excludeLowVegetation = Boolean.parseBoolean(args[11]);
        final boolean excludeMediumVegetation = Boolean.parseBoolean(args[12]);
        final boolean excludeHighVegetation = Boolean.parseBoolean(args[13]);
        final boolean excludeBuilding = Boolean.parseBoolean(args[14]);
        final boolean excludeLowPoint = Boolean.parseBoolean(args[15]);
        final boolean excludeModelKeyPoint = Boolean.parseBoolean(args[16]);
        final boolean excludeWater = Boolean.parseBoolean(args[17]);

        boolean[] classValuesToExclude = new boolean[32]; // there can be up to 32 different classes in future versions

        if (excludeNeverClassified) {
            classValuesToExclude[0] = true;
        }
        if (excludeUnclassified) {
            classValuesToExclude[1] = true;
        }
        if (excludeBareGround) {
            classValuesToExclude[2] = true;
        }
        if (excludeLowVegetation) {
            classValuesToExclude[3] = true;
        }
        if (excludeMediumVegetation) {
            classValuesToExclude[4] = true;
        }
        if (excludeHighVegetation) {
            classValuesToExclude[5] = true;
        }
        if (excludeBuilding) {
            classValuesToExclude[6] = true;
        }
        if (excludeLowPoint) {
            classValuesToExclude[7] = true;
        }
        if (excludeModelKeyPoint) {
            classValuesToExclude[8] = true;
        }
        if (excludeWater) {
            classValuesToExclude[9] = true;
        }

		String[] inputFiles = inputFileString.split(";")

		// check for empty entries in the inputFiles array
		int i = 0;
		for (String inputFile : inputFiles) {
			if (!inputFile.isEmpty()) {
				i++
			}
		}

		if (i != inputFiles.length) {
			// there are empty entries in the inputFiles array
			// remove them.
			ArrayList<String> temp = new ArrayList<>();
			for (String inputFile : inputFiles) {
				if (!inputFile.isEmpty()) {
					temp.add(inputFile)
				}
			}
			inputFiles = new String[i];
    		temp.toArray(inputFiles);
		}
		
		//int rows, cols
		final int numFiles = inputFiles.length
		BoundingBox[] bb = new BoundingBox[numFiles]
		i = 0
		for (String inputFile : inputFiles) {
			LASReader las = new LASReader(inputFile)
			bb[i] = new BoundingBox(las.getMinX(), las.getMinY(), las.getMaxX(), las.getMaxY());
			i++
		}


		pluginHost.updateProgress("Please wait...", 0)
		ArrayList<DoWork> tasks = new ArrayList<>();
		for (i = 0; i < numFiles; i++) {
			tasks.add(new DoWork(i, inputFiles, suffix, 
	      		 bb, whatToInterpolate, returnNumberToInterpolate, 
	      		 weight, maxDist, resolution, maxScanAngleDeviation, classValuesToExclude))
		}

		ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
  	    // the only reason for the getExecutorResults method 
  	    // is that Groovy throws a compilation type mis-match
  	    // error when compiled statically. I think it's a bug.
  	    List<Future<Boolean>> results = getExecutorResults(executor, tasks); //executor.invokeAll(tasks);
        executor.shutdown();

        i = 0
        int progress = 0
        int oldProgress = -1
        int numSuccessfulInterpolations = 0
	    for (Future<Boolean> result : results) {
    		Boolean data = result.get()
    		if (data) { numSuccessfulInterpolations++ }
        	i++
			// update progress bar
			progress = (int)(100f * i / numFiles)
			if (progress > oldProgress) {
				pluginHost.updateProgress("Progress", progress)
				oldProgress = progress
			}
			// check to see if the user has requested a cancellation
			if (pluginHost.isRequestForOperationCancelSet()) {
				pluginHost.showFeedback("Operation cancelled")
				return
			}
	    }

	    String inputFileExtension = FileUtilities.getFileExtension(inputFiles[0])
        String outputHeader = inputFiles[0].replace(".${inputFileExtension}", suffix + ".dep");
        pluginHost.returnData(outputHeader);

		long end = System.currentTimeMillis()  
		double duration = (end - start) / 1000.0
		pluginHost.showFeedback("Interpolation completed in " + duration + " seconds.\n" + 
		  numSuccessfulInterpolations + " tiles were successfully interpolated.\n" + 
		  "One has been displayed on the map.")
		
	  } catch (OutOfMemoryError oe) {
            pluginHost.showFeedback("An out-of-memory error has occurred during operation.")
	    } catch (Exception e) {
	        pluginHost.showFeedback("An error has occurred during operation. See log file for details.")
	        pluginHost.logException("Error in " + descriptiveName, e)
        } finally {
        	// reset the progress bar
        	pluginHost.updateProgress(0)
        }
	}
	
	@Override
    public void actionPerformed(ActionEvent event) {
    	if (event.getActionCommand().equals("ok")) {
    		final def args = sd.collectParameters()
			sd.dispose()
			final Runnable r = new Runnable() {
            	@Override
            	public void run() {
                	execute(args)
            	}
        	}
        	final Thread t = new Thread(r)
        	t.start()
    	}
    }

    public List<Future<Boolean>> getExecutorResults(ExecutorService executor, ArrayList<DoWork> tasks) {
    	List<Future<Boolean>> results = executor.invokeAll(tasks);
		return results
    }

	class DoWork implements Callable<Boolean> {
		private int tileNum
		private boolean[] classValuesToExclude
		private BoundingBox[] bb
		private String[] inputFiles
		private String suffix
	    private String whatToInterpolate
	    private String returnNumberToInterpolate
	    private double weight
		private double maxDist
		private double resolution
		private double maxScanAngleDeviation
		
	    DoWork(int tileNum, String[] inputFiles, String suffix, 
	      BoundingBox[] bb, String whatToInterpolate, 
	      String returnNumberToInterpolate, double weight, 
	      double maxDist, double resolution, double maxScanAngleDeviation,
	      boolean[] classValuesToExclude) {
			this.tileNum = tileNum;
			this.inputFiles = inputFiles.clone();
			this.suffix = suffix;
			this.bb = bb.clone();
			this.whatToInterpolate = whatToInterpolate;
			this.returnNumberToInterpolate = returnNumberToInterpolate;
			this.weight = weight;
			this.maxDist = maxDist;
			this.resolution = resolution;
			this.maxScanAngleDeviation = maxScanAngleDeviation;
			this.classValuesToExclude = classValuesToExclude.clone();
       	}
        	
        @Override
        @CompileStatic
	    public Boolean call() {
	    	// check to see if the user has requested a cancellation
			if (pluginHost.isRequestForOperationCancelSet()) {
				return Boolean.FALSE
			}
			
	    	String inputFile = inputFiles[tileNum]
	    	int numFiles = inputFiles.length
	    	double x, y, z;
			double easting, northing;
			double scanAngle;
			final double noData = -32768;
			double maxDist2 = 2.0 * maxDist;
			int row, col, i;
			List<KdTree.Entry<InterpolationRecord>> results;
		
			LASReader las;
			BoundingBox expandedBB = new BoundingBox(bb[tileNum].getMinX() - maxDist2, bb[tileNum].getMinY() - maxDist2, bb[tileNum].getMaxX() + maxDist2, bb[tileNum].getMaxY() + maxDist2);
			
			// count how many valid points there are
			int numPoints = 0;
			ArrayList<PointRecord> recs = new ArrayList<>();
			for (int a = 0; a < numFiles; a++) {
				if (bb[a].entirelyContainedWithin(expandedBB) || 
				  bb[a].intersectsAnEdgeOf(expandedBB)) {
				 	las = new LASReader(inputFiles[a])
				 	ArrayList<PointRecord> points = las.getPointRecordsInBoundingBox(expandedBB)
				 	if (returnNumberToInterpolate.equals("all points")) {
				 		for (PointRecord point : points) {
					 		if (!point.isPointWithheld() &&
	                          !(classValuesToExclude[point.getClassification()])) {
	                            recs.add(point);
	                        }
				 		}
				 	} else if (returnNumberToInterpolate.equals("first return")) {
				 		for (PointRecord point : points) {
					 		if (!point.isPointWithheld() &&
	                          !(classValuesToExclude[point.getClassification()])
                              && point.getReturnNumber() == 1) {
	                            recs.add(point);
	                        }
				 		}
				 	} else { // if (returnNumberToInterpolate.equals("last return")) {
				 		for (PointRecord point : points) {
					 		if (!point.isPointWithheld() &&
	                          !(classValuesToExclude[point.getClassification()])
                              && point.getReturnNumber() == point.getNumberOfReturns()) {
	                            recs.add(point);
	                        }
				 		}
				 	}
				 	points.clear();
				}
			}
			
			numPoints = recs.size();
			KdTree<InterpolationRecord> pointsTree = new KdTree.SqrEuclid<InterpolationRecord>(2, new Integer(numPoints))

			double[] entry;
			PointRecColours pointColours;
			if (whatToInterpolate.equals("z (elevation)")) {
				for (PointRecord point : recs) {
					entry = [point.getY(), point.getX()]
					pointsTree.addPoint(entry, new InterpolationRecord(point.getZ(), point.getScanAngle()));
				}
            } else if (whatToInterpolate.equals("intensity")) {
                for (PointRecord point : recs) {
					entry = [point.getY(), point.getX()]
					pointsTree.addPoint(entry, new InterpolationRecord(point.getIntensity(), point.getScanAngle()));
				}
            } else if (whatToInterpolate.equals("classification")) {
                for (PointRecord point : recs) {
					entry = [point.getY(), point.getX()]
					pointsTree.addPoint(entry, new InterpolationRecord(point.getClassification(), point.getScanAngle()));
				}
            } else if (whatToInterpolate.equals("scan angle")) {
                for (PointRecord point : recs) {
					entry = [point.getY(), point.getX()]
					pointsTree.addPoint(entry, new InterpolationRecord(point.getScanAngle(), point.getScanAngle()));
				}
            }
			recs.clear();
			
            // create the output grid
            String inputFileExtension = FileUtilities.getFileExtension(inputFile)
            String outputHeader = inputFile.replace(".${inputFileExtension}", suffix + ".dep");
            
	        // see if the output files already exist, and if so, delete them.
	        if ((new File(outputHeader)).exists()) {
	            (new File(outputHeader)).delete();
	            (new File(outputHeader.replace(".dep", ".tas"))).delete();
	        }
	
	        // What are north, south, east, and west and how many rows and 
	        // columns should there be?
	        double west = bb[tileNum].getMinX() - 0.5 * resolution;
	        double north = bb[tileNum].getMaxY() + 0.5 * resolution;
	        int nrows = (int) (Math.ceil((north - bb[tileNum].getMinY()) / resolution));
	        int ncols = (int) (Math.ceil((bb[tileNum].getMaxX() - west) / resolution));
	        double south = north - nrows * resolution;
	        double east = west + ncols * resolution;
	
	        try {
	            // create the whitebox header file.
	            FileWriter fw = new FileWriter(outputHeader, false);
	            BufferedWriter bw = new BufferedWriter(fw);
	            PrintWriter out = new PrintWriter(bw, true);
	
	            String str1 = "Min:\t" + Double.toString(Integer.MAX_VALUE);
	            out.println(str1);
	            str1 = "Max:\t" + Double.toString(Integer.MIN_VALUE);
	            out.println(str1);
	            str1 = "North:\t" + Double.toString(north);
	            out.println(str1);
	            str1 = "South:\t" + Double.toString(south);
	            out.println(str1);
	            str1 = "East:\t" + Double.toString(east);
	            out.println(str1);
	            str1 = "West:\t" + Double.toString(west);
	            out.println(str1);
	            str1 = "Cols:\t" + Integer.toString(ncols);
	            out.println(str1);
	            str1 = "Rows:\t" + Integer.toString(nrows);
	            out.println(str1);
	            str1 = "Data Type:\t" + "float";
	            out.println(str1);
	            str1 = "Z Units:\t" + "not specified";
	            out.println(str1);
	            str1 = "XY Units:\t" + "not specified";
	            out.println(str1);
	            str1 = "Projection:\t" + "not specified";
	            out.println(str1);
	            str1 = "Data Scale:\tcontinuous";
	            out.println(str1);
	            if (whatToInterpolate.equals("intensity")) {
	                str1 = "Preferred Palette:\t" + "grey.pal";
	            } else {
	                str1 = "Preferred Palette:\t" + "spectrum.pal";
	            }
	            out.println(str1);
	            str1 = "NoData:\t" + noData;
	            out.println(str1);
	            if (java.nio.ByteOrder.nativeOrder() == java.nio.ByteOrder.LITTLE_ENDIAN) {
	                str1 = "Byte Order:\t" + "LITTLE_ENDIAN";
	            } else {
	                str1 = "Byte Order:\t" + "BIG_ENDIAN";
	            }
	            out.println(str1);
	
	            out.close();

	            // Create the whitebox raster object.
	        	WhiteboxRaster image = new WhiteboxRaster(outputHeader, "rw");
                InterpolationRecord value;
                double sumWeights, dist;
	            double halfResolution = resolution / 2;
	            int oldProgress = -1;
	            int progress;
                for (row = 0; row < nrows; row++) {
                    for (col = 0; col < ncols; col++) {
                        easting = (col * resolution) + (west + halfResolution);
                        northing = (north - halfResolution) - (row * resolution);
                        entry = [northing, easting];
                        results = pointsTree.neighborsWithinRange(entry, maxDist);
                        double minScanAngle = Double.POSITIVE_INFINITY;
                        double maxScanAngle = Double.NEGATIVE_INFINITY;
                        for (i = 0; i < results.size(); i++) {
                        	value = (InterpolationRecord)results.get(i).value;
                            scanAngle = value.scanAngle;
                            if (scanAngle > maxScanAngle) { maxScanAngle = scanAngle; }
                            if (scanAngle < minScanAngle) { minScanAngle = scanAngle; }
                        }
                        boolean[] scanAngleFilter = new boolean[results.size()];
                        for (i = 0; i < results.size(); i++) {
                        	value = (InterpolationRecord)results.get(i).value;
                            scanAngle = value.scanAngle;
                            if ((scanAngle - minScanAngle) < maxScanAngleDeviation) {
                                scanAngleFilter[i] = true;
                            }
                        }
                        
                        sumWeights = 0;
                        for (i = 0; i < results.size(); i++) {
                            if ((results.get(i).distance > 0) && (results.get(i).distance < maxDist) &&
                                    scanAngleFilter[i]) {
                                dist = Math.pow(Math.sqrt(results.get(i).distance), weight);
                                sumWeights += 1 / dist;
                            } else if (results.get(i).distance == 0) {
                                break;
                            }
                        }
                        if (sumWeights > 0) {
                            z = 0;
                            for (i = 0; i < results.size(); i++) {
                            	value = (InterpolationRecord)results.get(i).value;
                                if ((results.get(i).distance > 0) && (results.get(i).distance < maxDist) &&
                                        scanAngleFilter[i]) {
                                    dist = 1 / Math.pow(Math.sqrt(results.get(i).distance), weight);
                                    z += (dist * value.value) / sumWeights;
                                } else if (results.get(i).distance == 0) {
                                    z = value.value;
                                    break;
                                }
                            }
                            image.setValue(row, col, z);
                        } else {
                            image.setValue(row, col, noData);
                        }
                    }

                    if (numFiles == 1) {
                    	progress = (int)(100f * row / (nrows - 1))
                    	if (progress > oldProgress) {
                    		oldProgress = progress
                    		pluginHost.updateProgress("Interpolation Progress:", progress)
                    	}
                    }
                    
                    // check to see if the user has requested a cancellation
					if (pluginHost.isRequestForOperationCancelSet()) {
						pluginHost.showFeedback("Operation cancelled")
						return Boolean.FALSE
					}
                }

                image.addMetadataEntry("Created by the " + descriptiveName + " tool.");
                image.addMetadataEntry("Created on " + new Date());
                image.close();
	
	        } catch (Exception e) {
	            pluginHost.showFeedback(e.getMessage());
	            return Boolean.FALSE;
	        }

			int solved = numSolvedTiles.incrementAndGet() //++;
			int progress = (int) (100f * solved / numFiles)
			pluginHost.updateProgress("Interpolated ${solved} tiles:", progress)
	        return Boolean.TRUE;
        }                
    }

	class InterpolationRecord {
        
        double value;
        byte scanAngle;
        
        InterpolationRecord(double value, byte scanAngle) {
            this.value = value;
            this.scanAngle = (byte)Math.abs(scanAngle);
        }
        
        double getValue() {
            return value;
        }
        
        byte getScanAngle() {
            return scanAngle;
        }
        
    }
}

if (args == null) {
	pluginHost.showFeedback("Plugin arguments not set.")
} else {
	def myClass = new LiDAR_IDW_Interpolation(pluginHost, args, name, descriptiveName)
}
