# MapMatching
A java application that takes GPS points and road lines in the form of .shp files and matches the GPS points to the nearest roads.

The intention of this application is to give other developers a change to make their own implementation of a map matching application. There are few map matching applications available in open source format that allows for customization so hopefully this will assist someone.

The application uses standard Shape(.shp) files, the kind you will most likely be using if you have worked with applications like ArcGIS. You can chose to export a file from ArcGIS into a .shp file and use it in this application as well, likewise you may import the files created from this application into ArcGIS.

Layers
roadLayer(Line) - Road data.
GPSLayer(Point) - GPS data.
correctionLineLayer(Line) - Lines showing to where the GPS points has been moved.
correctionPointLayer(Point) - Points showing where the GPS points have been moved.
modificationLayer(Line) - Modified layer which shows the GPS points after they have been moved.
  
Prerequisites
This application was coded using Eclipse, JavaSE-1.7 and Maven. Using the pom.xml file provided in the repository you should be able to get all the prerequisites for this application.

Running
When you start the application you will be prompted for .shp files. The files to open/save in order are as follows:
* Road data(Line) .shp file to load.
* GPS data(Point) .shp file to load.
* Correction data(Point) .shp file to save.
* Correction data(Line) .shp file to save.
* Modification data(Line) .shp file to save.

Feel free to contact me if you have any further questions.

Stefan Larsson
Stefan@gemfind.com