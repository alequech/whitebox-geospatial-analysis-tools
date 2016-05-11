/*
 * Copyright (C) 2014 Dr. John Lindsay <jlindsay@uoguelph.ca>
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
import java.io.File
import java.util.Date
import java.util.ArrayList
import whitebox.interfaces.WhiteboxPluginHost
import whitebox.geospatialfiles.WhiteboxRasterInfo
import whitebox.geospatialfiles.ShapeFile
import whitebox.geospatialfiles.shapefile.*
import whitebox.geospatialfiles.shapefile.attributes.*
import whitebox.ui.plugin_dialog.ScriptDialog
import whitebox.utilities.FileUtilities
import whitebox.structures.BoundingBox
import groovy.transform.CompileStatic

// The following four variables are required for this 
// script to be integrated into the tool tree panel. 
// Comment them out if you want to remove the script.
def name = "CreateRectangularVectorGrid"
def descriptiveName = "Create Rectangular Vector Grid"
def description = "Creates a rectangular grid (fishnet) of vector polygons"
def toolboxes = ["VectorTools"]

public class CreateRectangularVectorGrid implements ActionListener {
    private WhiteboxPluginHost pluginHost
    private ScriptDialog sd;
    private String descriptiveName
	
    public CreateRectangularVectorGrid(WhiteboxPluginHost pluginHost, 
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
            sd.addDialogFile("Input file to base the output grid's extent on", "Input Base File:", "open", "Whitebox Files (*.dep; *.shp), DEP, SHP", true, false)
            sd.addDialogFile("Output file", "Output Vector File:", "close", "Vector Files (*.shp), SHP", true, false)
            sd.addDialogDataInput("Enter a grid resolution in the x dimension", "x-Dimension Grid Size:", "", true, false)
            sd.addDialogDataInput("Enter a grid resolution in the y dimension", "y-Dimension Grid Size:", "", true, false)
            sd.addDialogDataInput("Enter grid origin x-coordinate", "Origin X-Coordinate:", "0.0", true, false)
            sd.addDialogDataInput("Enter grid origin y-coordinate", "Origin Y-Coordinate", "0.0", true, false)
            
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
        try {

            if (args.length != 6) {
                pluginHost.showFeedback("Incorrect number of arguments given to tool.")
                return
            }
            // read the input parameters
            String baseFile = args[0]
            String outputFile = args[1]
            double widthX = Double.parseDouble(args[2])
            double widthY = Double.parseDouble(args[3])
            double originX = Double.parseDouble(args[4])
            double originY = Double.parseDouble(args[5])
            
			// figure out the extent of the base data set
			BoundingBox extent
			if (baseFile.toLowerCase().contains(".dep")) {
				WhiteboxRasterInfo wri = new WhiteboxRasterInfo(baseFile)
				extent = new BoundingBox(wri.getWest(), wri.getSouth(), wri.getEast(), wri.getNorth())
				wri.close()
				wri = null
			} else if (baseFile.toLowerCase().contains(".shp")) {
				ShapeFile sf = new ShapeFile(baseFile)
				extent = new BoundingBox(sf.getxMin(), sf.getyMin(), sf.getxMax(), sf.getyMax())
				sf = null
			} else {
				pluginHost.showFeedback("The input base file is of an unrecognized type.")
				return
			}
			
            // set up the output files of the shapefile and the dbf
            DBFField[] fields = new DBFField[3]

            fields[0] = new DBFField()
            fields[0].setName("FID")
            fields[0].setDataType(DBFField.DBFDataType.NUMERIC)
            fields[0].setFieldLength(10)
            fields[0].setDecimalCount(0)

            fields[1] = new DBFField()
            fields[1].setName("ROW")
            fields[1].setDataType(DBFField.DBFDataType.NUMERIC)
            fields[1].setFieldLength(10)
            fields[1].setDecimalCount(0)

            fields[2] = new DBFField()
            fields[2].setName("COLUMN")
            fields[2].setDataType(DBFField.DBFDataType.NUMERIC)
            fields[2].setFieldLength(10)
            fields[2].setDecimalCount(0)
            
            ShapeFile output = new ShapeFile(outputFile, ShapeType.POLYGON, fields)

			int startXGrid = (int)(Math.floor((extent.getMinX() - originX) / widthX))
			int endXGrid = (int)(Math.ceil((extent.getMaxX() - originX) / widthX))
			int startYGrid = (int)(Math.floor((extent.getMinY() - originY) / widthY))
			int endYGrid = (int)(Math.ceil((extent.getMaxY() - originY) / widthY))
			int cols = (int)(Math.abs(endXGrid - startXGrid))
			int rows = (int)(Math.abs(endYGrid - startYGrid))
			

            int[] parts = [0]
			double x, y
			int FID = 0
			int progress
			int oldProgress = -1
			int r = 0
			for (int row in startYGrid..<endYGrid) {
				for (int col in startXGrid..<endXGrid) {
					PointsList points = new PointsList();
					
					// Point 1
					x = originX + col * widthX
					y = originY + row * widthY
					if (x < extent.getMinX()) { x = extent.getMinX() }
		    		if (x > extent.getMaxX()) { x = extent.getMaxX() }
		    		if (y < extent.getMinY()) { y = extent.getMinY() }
		    		if (y > extent.getMaxY()) { y = extent.getMaxY() }
		    		points.addPoint(x, y)

		    		// Point 2
					x = originX + col * widthX
		    		y = originY + (row + 1) * widthY
					if (x < extent.getMinX()) { x = extent.getMinX() }
		    		if (x > extent.getMaxX()) { x = extent.getMaxX() }
		    		if (y < extent.getMinY()) { y = extent.getMinY() }
		    		if (y > extent.getMaxY()) { y = extent.getMaxY() }
		    		points.addPoint(x, y)

		    		// Point 2
					x = originX + (col + 1) * widthX
		    		y = originY + (row + 1) * widthY
					if (x < extent.getMinX()) { x = extent.getMinX() }
		    		if (x > extent.getMaxX()) { x = extent.getMaxX() }
		    		if (y < extent.getMinY()) { y = extent.getMinY() }
		    		if (y > extent.getMaxY()) { y = extent.getMaxY() }
		    		points.addPoint(x, y)

					// Point 3
					x = originX + (col + 1) * widthX
		    		y = originY + row * widthY
					if (x < extent.getMinX()) { x = extent.getMinX() }
		    		if (x > extent.getMaxX()) { x = extent.getMaxX() }
		    		if (y < extent.getMinY()) { y = extent.getMinY() }
		    		if (y > extent.getMaxY()) { y = extent.getMaxY() }
		    		points.addPoint(x, y)
					
					points.closePolygon()
		            
		            Polygon poly = new Polygon(parts, points.getPointsArray());
		            Object[] rowData = new Object[3];
		            rowData[0] = new Double(FID);
		            rowData[1] = new Double(row);
		            rowData[2] = new Double(col);
		            output.addRecord(poly, rowData);
		            FID++
				}
				r++
				progress = (int)(100f * r / rows)
    			if (progress != oldProgress) {
					pluginHost.updateProgress(progress)
        			oldProgress = progress
        		}
        		// check to see if the user has requested a cancellation
				if (pluginHost.isRequestForOperationCancelSet()) {
					pluginHost.showFeedback("Operation cancelled")
					return
				}
			}

            output.write();
	           
            // display the output image
            pluginHost.returnData(outputFile)
        } catch (Exception e) {
            pluginHost.showFeedback(e.getMessage())
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
}

if (args == null) {
    pluginHost.showFeedback("Plugin arguments not set.")
} else {
    def f = new CreateRectangularVectorGrid(pluginHost, args, name, descriptiveName)
}
