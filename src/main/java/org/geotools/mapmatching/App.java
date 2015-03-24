/**
 * An application that will match GPS points from a shape(.shp) file
 * to the nearest road, also represented by a shape(.shp) file. It will then attempt
 * to save these new layers as new shape(.shp) file.
 * 
 * There is a bug present in the shape file writer from Geotools which causes the .prj file for the
 * correctionPointCollection to be empty. This however does not seem to stop the .shp file from
 * being opened correctly by software like ArcGIS.
 * 
 * @author Stefan Larsson <Stefan.per.larsson@gmail.com>
 * @version 1.0
 * @since 1.0
 */

package org.geotools.mapmatching;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.index.SpatialIndex;
import com.vividsolutions.jts.index.strtree.STRtree;
import com.vividsolutions.jts.linearref.LinearLocation;
import com.vividsolutions.jts.linearref.LocationIndexedLine;

import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureSource;
import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.Transaction;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.map.FeatureLayer;
import org.geotools.map.Layer;
import org.geotools.map.MapContent;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.styling.SLD;
import org.geotools.styling.Style;
import org.geotools.swing.JMapFrame;
import org.geotools.swing.data.JFileDataStoreChooser;
import org.geotools.util.NullProgressListener;
import org.opengis.feature.Feature;
import org.opengis.feature.FeatureVisitor;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

public class App {

	/**
	 * Requests a file location from the user via a dialog box, it will then return the file object. 
	 *
	 * @return File A File object of the file chosen by the user.
	 */
	private static File getNewShapeFile() {

		//Prompt user for a location and filename to save the shape file(s) at.
        JFileDataStoreChooser chooser = new JFileDataStoreChooser("shp");
        chooser.setDialogTitle("Save shapefile");

        //Store the user choice.
        int returnVal = chooser.showSaveDialog(null);

        //If user cancelled the dialog, exit application entirely.
        if(returnVal != JFileDataStoreChooser.APPROVE_OPTION){
            System.exit(0);
        }

        //Set new file.
        File file = chooser.getSelectedFile();

        //Return the new file.
        return file;
    }

	/**
	 * Write a feature collection to a shape(.shp) file using the
	 * standard GeoTools way of creating shape files. This function will
	 * write the shape file using the Coordinate system WGS84.
	 *
	 * @param SimpleFeatureCollection collection The collection of features you wish to write to the shape file.
	 * @param SimpleFeatureType TYPE The type object of the feature you wish to use.
	 */
	private static void writeShapeFile(SimpleFeatureCollection collection, SimpleFeatureType TYPE) throws IOException{

		//Prompt the user for a save location and filename.
		File newFile = getNewShapeFile();

		//Set up data store factory.
        ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();

        //Set up maps and parameters.
        Map<String, Serializable> params = new HashMap<String, Serializable>();
        params.put("url", newFile.toURI().toURL());
        params.put("create spatial index", Boolean.TRUE);

        //Set up data store.
        ShapefileDataStore dataStore = (ShapefileDataStore) dataStoreFactory.createNewDataStore(params);
        dataStore.createSchema(TYPE);

        //Set CRS.
        dataStore.forceSchemaCRS(DefaultGeographicCRS.WGS84);
		
        //Create transaction.
        Transaction transaction = new DefaultTransaction("create");

        //Set up type name and feature source.
        String typeName = dataStore.getTypeNames()[0];
        SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);

        if(featureSource instanceof SimpleFeatureStore) {
            
        	//Set up feature store and attempt the transaction.
        	SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
            featureStore.setTransaction(transaction);
            try{
                featureStore.addFeatures(collection);
                transaction.commit();
            }catch (Exception e) {
                e.printStackTrace();
                transaction.rollback();
            }finally{
                transaction.close();
            }
        
        //If the type does not support read/write access.
        }else{
            System.out.println(typeName + " does not support read/write access");
        }
		
	}

	/**
	 * This function is really just a simplification of the process to create a
	 * SimpleFeatureSource object. It will prompt the user for a file via a dialog
	 * and then proceed to process it into a feature source.
	 *
	 * @return SimpleFeatureSource The processed feature source.
	 * @throws IOException 
	 */
	private static SimpleFeatureSource getFeatureSourceFromFile() throws IOException{

		//Open a file and process it into a FeatureSource.
		File file = JFileDataStoreChooser.showOpenFile("shp", null);
        FileDataStore store = FileDataStoreFinder.getDataStore(file);
        
        return store.getFeatureSource();
	}
	
	
	/**
	 * A simple function that will verify if a feature source contains line strings or not.
	 *
	 * @param FeatureSource<?, ?> source The source you wish to check.
	 * @return boolean Returns true if the source contains line strings, otherwise it will return false.
	 */
	private static boolean verifyLine(FeatureSource<?, ?> source){
		
		//Verify that the feature contains lines.
        Class<?> geomBinding = source.getSchema().getGeometryDescriptor().getType().getBinding();
        boolean isLine = geomBinding != null 
                && (LineString.class.isAssignableFrom(geomBinding) ||
                    MultiLineString.class.isAssignableFrom(geomBinding));
        
        //Otherwise, return false.
        if(!isLine){
        	return false;
        }
		
        return true;
	}

	/**
	 * A function that will cast the variable into an ArrayList to comply with the Java warranty terms.
	 *
	 * @param Class<? extends T> classType The Java class you wish to cast/add to the list.
	 * @param Collection<?> c The data you wish to add.
	 * @return <T> List<T> Returns the list of data.
	 */
	private static <T> List<T> castList(Class<? extends T> classType, Collection<?> c){
	    List<T> r = new ArrayList<T>(c.size());
	    for(Object o: c)
	      r.add(classType.cast(o));
	    return r;
	}
	
	public static void main(String[] args) throws Exception {

        // Create a map content and set title.
        MapContent map = new MapContent();
        map.setTitle("Map Matching");
        
        //Request the road file from the user.
        SimpleFeatureSource roadSource = getFeatureSourceFromFile();
        
        //Verify that we have line features in the shape file.
        if(!verifyLine(roadSource))
        	return;
        
        //Set style and make a layer from it.
	    Style roadStyle = SLD.createLineStyle(Color.black, 2.0f);
	    Layer roadLayer = new FeatureLayer(roadSource, roadStyle);
        
	    //Get the GPS file from the user.
        SimpleFeatureSource GPSSource = getFeatureSourceFromFile();
        
        //Set style for GPS points.
	    Style GPSStyle = SLD.createLineStyle(Color.green, 2.0f);
	    
	    //Set style for correction lines.
	    Style correctionLineStyle = SLD.createLineStyle(Color.blue, 2.0f);
	    
	    //Set style for correction points.
	    Style correctionPointStyle = SLD.createPointStyle("Circle", Color.red, Color.red, 0, 2.0f);
	    
	    //Set style for modification lines.
	    Style modificationStyle = SLD.createLineStyle(Color.cyan, 2.0f);
	    
	    //Modification type.
	    SimpleFeatureTypeBuilder modificationType = new SimpleFeatureTypeBuilder();
	    modificationType.setName("Modification");
	    modificationType.setCRS(DefaultGeographicCRS.WGS84); 
	    modificationType.add("the_geom", LineString.class);
        
        //Correction line type.
        SimpleFeatureTypeBuilder correctionLineType = new SimpleFeatureTypeBuilder();
        correctionLineType.setName("Correction lines");
        correctionLineType.setCRS(DefaultGeographicCRS.WGS84); 
        correctionLineType.add("the_geom", LineString.class);

        //Correction point type.
        SimpleFeatureTypeBuilder correctionPointType = new SimpleFeatureTypeBuilder();
        correctionPointType.setName("Correction points");
        correctionPointType.setCRS(DefaultGeographicCRS.WGS84); 
        correctionPointType.add("the_geom", Point.class);

        //GPS type.
        SimpleFeatureTypeBuilder GPSType = new SimpleFeatureTypeBuilder();
        GPSType.setName("GPS");
        GPSType.setCRS(DefaultGeographicCRS.WGS84); 
        GPSType.add("the_geom", LineString.class);

        //GPS builder and collection.
        SimpleFeatureBuilder GPSBuilder = new SimpleFeatureBuilder(GPSType.buildFeatureType());
        DefaultFeatureCollection GPSCollection = new DefaultFeatureCollection();
        
        //Correction Line builder and collection.
        SimpleFeatureBuilder correctionLineBuilder = new SimpleFeatureBuilder(correctionLineType.buildFeatureType());
        DefaultFeatureCollection correctionLineCollection = new DefaultFeatureCollection();
		
        //Correction point builder and collection.
        SimpleFeatureBuilder correctionPointBuilder = new SimpleFeatureBuilder(GPSSource.getSchema());
        DefaultFeatureCollection correctionPointCollection = new DefaultFeatureCollection();
		
        //Line modification builder and collection.
        SimpleFeatureBuilder modificationBuilder = new SimpleFeatureBuilder(modificationType.buildFeatureType());
        DefaultFeatureCollection modificationCollection = new DefaultFeatureCollection();
		
        //Set up GPS iterator and feature.
        SimpleFeatureCollection GPSPointCollection = GPSSource.getFeatures();
        SimpleFeatureIterator GPSIterator = GPSPointCollection.features();
		SimpleFeature GPSFeature = null;

		//List of coordinates for GPS points.
		List<Coordinate> GPSCoordinates = new ArrayList<Coordinate>();
		
		//List of coordinates for modification points.
		List<Coordinate> ModificationCoordinates = new ArrayList<Coordinate>();
				
		//String for last timestamp.
    	String lastTimestamp = new String("default");
		
    	//Coordinate for the modification layer.
    	Coordinate corrected = null;
    	
    	//Delimiter for GPS points.
    	final String delimiter = new String("LINE_MARKE");
    	
		//Set special index and feature collection for road points.
        final SpatialIndex index = new STRtree();
        FeatureCollection<?, ?> roadFeatures = roadSource.getFeatures();
        
        //Max search distance.
        final double MAX_SEARCH_DISTANCE = roadFeatures.getBounds().getSpan(0) / 100.0;

        //Set geometry factory.
        GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory(null);

        //Set map index.
	    roadFeatures.accepts(new FeatureVisitor(){
	        public void visit(Feature feature){
	            SimpleFeature simpleFeature = (SimpleFeature) feature;
	            Geometry geom = (MultiLineString) simpleFeature.getDefaultGeometry();
	            
	            //Check for null or empty geometry.
	            if(geom != null){
	                Envelope env = geom.getEnvelopeInternal();
	                if(!env.isNull()){
	                	index.insert(env, new LocationIndexedLine(geom));
	                }
	            }
	        }
        }, new NullProgressListener());

        //Loop the GPS points.
        try{
        	
        	//While we still have data to loop.
        	while(GPSIterator.hasNext()){
    		    
        		//Move to the next item in the iterator.
        		GPSFeature = GPSIterator.next();
        	
        		//Set corrected boolean.
        		corrected = null;
        		
        		//Load the GPS point and create a coordinate.
        		Point GPSPoint = (Point) GPSFeature.getProperty("the_geom").getValue();
        		Coordinate GPSCoordinate = new Coordinate(GPSPoint.getX(), GPSPoint.getY());
        		
        		// Get point and create search envelope
	            Envelope search = new Envelope(GPSCoordinate);
	            search.expandBy(MAX_SEARCH_DISTANCE);
	
	            //Query the spatial index for the objects within the search envelope.
	            List<LocationIndexedLine> lines = castList(LocationIndexedLine.class, index.query(search));
	            
        		//Initialize the minimum distance.
	            double minDist = MAX_SEARCH_DISTANCE + 1.0e-6;
	            Coordinate minDistPoint = null;
	            
	            //Loop the lines we found in the envelope.
	            for(LocationIndexedLine line : lines){
            		LinearLocation here = line.project(GPSCoordinate);
	                Coordinate point = line.extractPoint(here);
	
	                double dist = point.distance(GPSCoordinate);
	                if (dist < minDist) {
	                    minDist = dist;
	                    minDistPoint = point;
	                }
	            }
	          
	            //If coordinate if too far away from a road.
            	if(minDistPoint == null){
	                
	            	System.out.println(GPSCoordinate + " - Too far away");
	
	            }else{
	            
	            	//Create a line.
	            	Coordinate[] lineCorrectionCoordinates = new Coordinate[] { GPSCoordinate, minDistPoint };
	            	LineString correctionLine = geometryFactory.createLineString(lineCorrectionCoordinates);

	            	//Build the line and add to collection.
	            	correctionLineBuilder.add(correctionLine);
	            	correctionLineCollection.add(correctionLineBuilder.buildFeature(null));
	            
	            	//Set the corrected point.
	            	corrected = minDistPoint;
	            	
	            }
        
            	//Set the timestamp manually if this is the first run.
        		if(lastTimestamp.equals("default"))
        			lastTimestamp = (String) GPSFeature.getProperty(delimiter).getValue();
        		
        		//If we switched line marker or is at the last point.
        		if(!lastTimestamp.equals((String) GPSFeature.getProperty(delimiter).getValue()) || !GPSIterator.hasNext()){
        			
        			//If we have enough points.
        			if(GPSCoordinates.size() > 2){
        			
	        			//Create the array and copy over the coordinates from the list.
	        			Coordinate[] GPSCoordinatesArray = new Coordinate[GPSCoordinates.size()];
	        			GPSCoordinates.toArray(GPSCoordinatesArray);
	        			LineString GPS = geometryFactory.createLineString(GPSCoordinatesArray);
	
	        			//Do the same for the modification coordinates.
	        			Coordinate[] ModificationCoordinatesArray = new Coordinate[ModificationCoordinates.size()];
	        			ModificationCoordinates.toArray(ModificationCoordinatesArray);
	        			LineString modification = geometryFactory.createLineString(ModificationCoordinatesArray);
	        			
		            	//Build the line and add to collection.
		            	GPSBuilder.add(GPS);
		            	GPSCollection.add(GPSBuilder.buildFeature(null));
		            	
		            	//Builder and add to collection for modification coordinates as well.
		            	modificationBuilder.add(modification);
		            	modificationCollection.add(modificationBuilder.buildFeature(null));
	            	
        			}

	            	//Clear array.
	            	GPSCoordinates.clear();
	            	
	            }
        		
        		//Add GPS coordinate to list.
        		GPSCoordinates.add(GPSCoordinate);
        		
        		//If we have a corrected GPS coordinate then we add that, otherwise add the uncorrected coordinate.
        		if(corrected == null){
        			ModificationCoordinates.add(GPSCoordinate);
        			correctionPointBuilder.add(geometryFactory.createPoint(GPSCoordinate));
        		}else{
        			ModificationCoordinates.add(corrected);
        			correctionPointBuilder.add(geometryFactory.createPoint(corrected));
        		}

        		//Build point feature and add to collection.
                correctionPointCollection.add(correctionPointBuilder.buildFeature(null));
                
            	//Set the old timestamp before moving on.
            	lastTimestamp = (String) GPSFeature.getProperty(delimiter).getValue();
        		
        	}
        
        }finally{
        	GPSIterator.close();
        }

        //Create a layer for the GPS line corrections.
        Layer correctionLineLayer = new FeatureLayer(correctionLineCollection, correctionLineStyle);

        //Create a layer for the GPS point corrections.
        Layer correctionPointLayer = new FeatureLayer(correctionPointCollection, correctionPointStyle);

        //Create a layer for modification points.
        Layer modificationLayer = new FeatureLayer(modificationCollection, modificationStyle);
        
        //Create a layer for the GPS.
        Layer GPSLayer = new FeatureLayer(GPSCollection, GPSStyle);
        
        //Write the correction line shape file.
    	writeShapeFile(correctionLineCollection, correctionLineCollection.getSchema());
        
    	//Write the correction point shape file.
        writeShapeFile(correctionPointCollection, correctionPointCollection.getSchema());
        
    	//Write the modification shape file.
        writeShapeFile(modificationCollection, modificationCollection.getSchema());
        
        //Add the road, GPS, correction line, correction point and modification layers to the map.
        map.addLayer(roadLayer);
        map.addLayer(GPSLayer);
        map.addLayer(correctionLineLayer);
        map.addLayer(correctionPointLayer);
        map.addLayer(modificationLayer);
        
        //Show the map.
        JMapFrame.showMap(map);
        
    }

}
