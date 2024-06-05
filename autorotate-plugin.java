import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.filter.*;

public class AutoRotate_Plugin implements PlugInFilter {
    ImagePlus imp;

    public int setup(String arg, ImagePlus imp) {
        this.imp = imp;
        return DOES_ALL;
    }

    public void run(ImageProcessor ip) {
	log("called autoRotateImage(imp=%s)", imp);
	// create the image we will use to determine the angle of rotation
	ImagePlus imp2 = new ImagePlus();
	imp2.setProcessor(imp.getProcessor().duplicate());

	// convert image to 8-bit
	IJ.run(imp2, "8-bit", "");
	// crop out black rectangles
	int x1 = 0;
	int x2 = imp2.getWidth()-1;
	int y1 = 0;
	int y2 = imp2.getHeight()-1;
	int cropX1 = -1;
	int cropX2 = -1;
	int cropY1 = -1;
	int cropY2 = -1;
	while ((cropX1 < 0 || cropY1 < 0 || cropX2 < 0 || cropY2 < 0) && x1 < x2 && y1 < y2) {
	    if (cropX1 < 0) {
		int black=0;
		for (int y=y1; y<=y2; ++y) {
		    int value = imp2.getPixel(x1, y)[0];
		    if (value == 0) {
			++x1;
			++black;
			break;
		    }
		}
		if (black == 0) {
		    cropX1 = x1;
		}
	    }
	    if (cropX2 < 0) {
		int black=0;
		for (int y=y1; y<=y2; ++y) {
		    int value = imp2.getPixel(x2, y)[0];
		    if (value == 0) {
			--x2;
			++black;
			break;
		    }
		}
		if (black == 0) {
		    cropX2 = x2;
		} 
	    }
	    if (cropY1 < 0) {
		int black=0;
		for (int x=x1; x<=x2; ++x) {
		    int value = imp2.getPixel(x, y1)[0];
		    if (value == 0) {
			++y1;
			++black;
			break;
		    }
		}
		if (black == 0) {
		    cropY1 = y1;
		}
	    }
	    if (cropY2 < 0) {
		int black=0;
		for (int x=x1; x<=x2; ++x) {
		    int value = imp2.getPixel(x, y2)[0];
		    if (value == 0) {
			--y2;
			++black;
			break;
		    }
		}
		if (black == 0) {
		    cropY2 = y2;
		}
	    }
	}
	// cropping failed
	final double MIN_SIZE_RATIO = 0.4;
	final int MIN_WIDTH = (int)Math.floor(imp.getWidth()*MIN_SIZE_RATIO);
	final int MIN_HEIGHT = (int)Math.floor(imp.getHeight()*MIN_SIZE_RATIO);
	if ((cropX1<0 || cropY1<0 || cropX2<0 || cropY2<0) || !(cropX1+MIN_WIDTH < cropX2 && cropY1+MIN_HEIGHT < cropY2)) {
	    return null;
	}
	imp2.setRoi(new Roi(cropX1, cropY1, cropX2-cropX1+1, cropY2-cropY1+1));
	imp2.setProcessor(imp2.getTitle(), imp2.getProcessor().crop());

	// scale to width 200, keep aspect ratio
	final int SCALE_WIDTH = 200;
	double scaleFactor = (double)SCALE_WIDTH / (double)imp2.getWidth();
	imp2.getProcessor().setInterpolationMethod(ImageProcessor.BILINEAR);
	imp2.setProcessor(imp2.getTitle(), imp2.getProcessor().resize(
		(int)Math.floor(imp2.getWidth() * scaleFactor), 
		(int)Math.floor(imp2.getHeight() * scaleFactor)));

	// subtract background
	IJ.run(imp2, "Subtract Background...", "rolling=20 light sliding");
	// run threshold
	IJ.setAutoThreshold(imp2, "Triangle");
	// despeckle 5x
	IJ.run(imp, "Despeckle", "");
	IJ.run(imp, "Despeckle", "");
	IJ.run(imp, "Despeckle", "");
	IJ.run(imp, "Despeckle", "");
	IJ.run(imp, "Despeckle", "");
	// run gray morphology erode
	IJ.run(imp2, "Gray Morphology", "radius=7 type=circle operator=erode");
	// analyze particles to get angle of rotation
	ResultsTable rt = new ResultsTable();
	ParticleAnalyzer particleAnalyzer = new ParticleAnalyzer(
		ParticleAnalyzer.EXCLUDE_EDGE_PARTICLES|
		ParticleAnalyzer.CLEAR_WORKSHEET|
		ParticleAnalyzer.IN_SITU_SHOW,
		Measurements.AREA|
		Measurements.MEAN|
		Measurements.MIN_MAX|
		Measurements.CENTER_OF_MASS|
		Measurements.RECT|
		Measurements.SHAPE_DESCRIPTORS|
		Measurements.ELLIPSE|
		Measurements.PERIMETER|
		Measurements.AREA_FRACTION,
		rt, 20.0, Double.POSITIVE_INFINITY);
	if (!particleAnalyzer.analyze(imp2)) {
	    // particle analyzer failed
	    return null;
	}

	// Get the largest object
	double maxArea = 0.0;
	int largest = -1;
	for (int i=0; i < rt.getCounter(); i++) {
	    double area = rt.getValue("Area", i);
	    if (maxArea < area) {
		maxArea = area;
		largest = i;
	    }
	}
	if (largest < 0) {
	    // particle analyzer returned no results
	    return null;
	}
	double bx = rt.getValue("BX", largest) / scaleFactor;
	//double by = rt.getValue("BY", largest) / scaleFactor;
	double width = rt.getValue("Width", largest) / scaleFactor;
	//double height = rt.getValue("Height", largest) / scaleFactor;
	double major = rt.getValue("Major", largest) / scaleFactor;
	//double minor = rt.getValue("Minor", largest) / scaleFactor;
	double angle = rt.getValue("Angle", largest);
	double centerX = bx+(width/2.0);
	//double centerY = by+(height/2.0);

	// compute histogram of pixel color values
	Map<Integer,Long> histo = new HashMap<>();
	for (int y=0; y<imp.getHeight(); ++y) {
	    for (int x=0; x<imp.getWidth(); ++x) {
		int[] pixel = imp.getPixel(x, y);
		if (pixel.length >= 3) {
		    int value = new Color(pixel[0], pixel[1], pixel[2]).getRGB();
		    histo.put(value, histo.getOrDefault(value, 0L)+1L);
		}
		else if (pixel.length >= 1) {
		    histo.put(pixel[0], histo.getOrDefault(pixel[0], 0L)+1L);
		}
	    }
	}
	// get the modal color value for the image
	int mode = 0;
	long modeCount = 0;
	for (Map.Entry<Integer,Long> entry : histo.entrySet()) {
	    if (modeCount < entry.getValue()) {
		mode = entry.getKey();
		modeCount = entry.getValue();
	    }
	}
	// set the background color to the modal color value
	ip.setBackgroundValue(mode);

	// rotate by that angle
	ip.setInterpolationMethod(ImageProcessor.BILINEAR);
	p.rotate(angle);
	// now crop and return
	final double PADDING=0.20;
	imp.setRoi(new Roi(
		Math.max(0, centerX-(major/2.0)-Math.floor(PADDING*major)), 
		0,
		Math.min(imp.getWidth(), major+(Math.floor(PADDING*major)*2.0)), 
		imp.getHeight()));
	ip.crop();

	// fill any black pixels with the modal background color
	for (int y=0; y<imp.getHeight(); ++y) {
	    PIXEL:
	    for (int x=0; x<imp.getWidth(); ++x) {
		int[] pixel = imp.getPixel(x, y);
		for (int i=0; i<pixel.length; ++i) {
		    if (pixel[i] != 0) continue PIXEL;
		}
		ip.putPixel(x, y, mode);
	    }
	}
    }
}
