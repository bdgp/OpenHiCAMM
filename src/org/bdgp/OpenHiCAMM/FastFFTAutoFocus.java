package org.bdgp.OpenHiCAMM;


import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;

import java.awt.Color;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.lang.System;

import org.micromanager.MMStudio;
import org.micromanager.api.Autofocus;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.AutofocusBase;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MMException;

import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import edu.mines.jtk.dsp.FftComplex;
import edu.mines.jtk.dsp.FftReal;

/**
 * ImageJ plugin wrapper for uManager.
 * This plugin take a stack of snapshots and computes their sharpness using an FFT Bandpass
 * 
 * Created on June 2nd 2007
 * @author: Pakpoom Subsoontorn & Hernan Garcia, changed to FFT Bandpass Analysis by Stephan Preib
 * 
 */
public class FastFFTAutoFocus extends AutofocusBase implements PlugIn, Autofocus {
    // NOTES:
    // gui = MMStudio, mmc = MMCore, acq = AcquisitionWrapperEngine
    // gui.getAutofocus()
    // mmc.getPosition(mmc.getFocusDevice());
    // mmc.setPosition(mmc.getFocusDevice(), 200);
    // gui.getAutofocus().fullFocus(); gui.doSnap(); 
    // mmc.setExposure(mmc.getExposure()*Math.pow(Integer.parseInt(mmc.getProperty(mmc.getCameraDevice(),"Binning"))/4.0,2))
    // mmc.setProperty(mmc.getCameraDevice(),"Binning","4")
    // mmc.setProperty(mmc.getCameraDevice(),"PixelType","Grayscale")

   public static Boolean verbose_ = true; // displaying debug info or not

   public static final String KEY_SIZE_FIRST = "1st step size";
   public static final String KEY_NUM_FIRST = "1st step number";
   public static final String KEY_SIZE_SECOND = "2nd step size";
   public static final String KEY_NUM_SECOND = "2nd step number";
   public static final String KEY_THRES    = "Threshold";
   public static final String KEY_CROP_SIZE = "Crop ratio";
   public static final String KEY_CHANNEL = "Channel";
   //private static final String AF_SETTINGS_NODE = "micro-manager/extensions/autofocus";
   
   public static final String AF_DEVICE_NAME = "FastFFT";

   public static final String KEY_MIN_AUTOFOCUS = "minAutoFocus";
   public static final String KEY_MAX_AUTOFOCUS = "maxAutoFocus";

    /*private ImagePlus outputRough = null;
    private ImageStack outputStackRough = null;
    private ImagePlus outputFine = null;
    private ImageStack outputStackFine = null;*/

    public CMMCore core_;
    public ImageProcessor ipCurrent_ = null;

    public double ADAPTIVE_DIST_DIFF = 20.0;
    public double SIZE_FIRST = 10.0;//
    public int NUM_FIRST = 20; // +/- #of snapshot
    public double SIZE_SECOND = 1.0;
    public int NUM_SECOND = 10;
    public double THRES = 0.02;
    public double CROP_SIZE = 0.2; 
    public String CHANNEL="";
    public int WAIT_FOR_DEVICE_SLEEP = 100;

    private double indx = 0; //snapshot show new window iff indx = 1

    private double curDist;
    private double baseDist;
    private Double prevBestDist;
    private Double bestDist;
    private double curSh;
    //private double curShScale; //sharpness rescaling factor
    private double bestSh;
    private boolean liveMode;
    
    // variables for skipping autofocus every acquisition
    public int skipCounter = -1;
    public static final int MAX_SKIP = 25;
    
    Double minAutoFocus;
    Double maxAutoFocus;

    public FastFFTAutoFocus() {
    	super();
    	
    	this.skipCounter = -1;

      // set-up properties
      createProperty(KEY_SIZE_FIRST, Double.toString(SIZE_FIRST));
      createProperty(KEY_NUM_FIRST, Integer.toString(NUM_FIRST));
      createProperty(KEY_SIZE_SECOND, Double.toString(SIZE_SECOND));
      createProperty(KEY_NUM_SECOND, Integer.toString(NUM_SECOND));
      createProperty(KEY_THRES, Double.toString(THRES));
      createProperty(KEY_CROP_SIZE, Double.toString(CROP_SIZE));
      createProperty(KEY_CHANNEL, CHANNEL);
      
      createProperty(KEY_MIN_AUTOFOCUS, "");
      createProperty(KEY_MAX_AUTOFOCUS, "");
      
      createProperty("autofocusDuration", new Long(0).toString());
      createProperty("liveMode", "yes");
      
      loadSettings();
    }

    public void run(String arg)
    {
        // make sure minAutoFocus and maxAutoFocus is set
        if (minAutoFocus == null || maxAutoFocus == null) applySettings();
        if (minAutoFocus == null) throw new RuntimeException("minAutofocus is null!");
        if (maxAutoFocus == null) throw new RuntimeException("maxAutofocus is null!");
        
        // only run autofocus every MAX_SKIP acquisitions
        if (bestDist != null && prevBestDist != null && Math.abs(bestDist - prevBestDist) <= ADAPTIVE_DIST_DIFF) {
            skipCounter++;
            if (skipCounter > MAX_SKIP) skipCounter = 0;
            if (skipCounter != 0) {
                if (verbose_) IJ.log(String.format("Skipping autofocus %d/%d", skipCounter, MAX_SKIP));
                return;
            }
        }
        else {
            skipCounter = -1;
        }
        
        bestSh = 0;
        
        //############# CHECK INPUT ARG AND CORE ########
        if (verbose_ == null) {
            if (arg.compareTo("silent") == 0)
                verbose_ = false;
            else
                verbose_ = true;
        }

        if (core_ == null)
        {
            // if core object is not set attempt to get its global handle
            core_ = MMStudio.getInstance().getMMCore();
        }

        if (core_ == null)
        {
            IJ.error("Unable to get Micro-Manager Core API handle.\n" +
                     "If this module is used as ImageJ plugin, Micro-Manager Studio must be running first!");
            return;
        }

        //######################## START THE ROUTINE ###########

        try
        {
//            core_.setShutterOpen(true);
//            core_.setAutoShutter(false);

            //########System setup##########
            //core_.setConfig("Channel", CHANNEL);
            core_.waitForSystem();
            //core_.waitForDevice(core_.getShutterDevice());
            
            IJ.log("Autofocus Properties:");
            IJ.log(String.format("%s = %s", KEY_SIZE_FIRST, SIZE_FIRST));
            IJ.log(String.format("%s = %s", KEY_NUM_FIRST, NUM_FIRST));
            IJ.log(String.format("%s = %s", KEY_SIZE_SECOND, SIZE_SECOND));
            IJ.log(String.format("%s = %s", KEY_NUM_SECOND, NUM_SECOND));
            IJ.log(String.format("%s = %s", KEY_THRES, THRES));
            IJ.log(String.format("%s = %s", KEY_CROP_SIZE, CROP_SIZE));
            IJ.log(String.format("%s = %s", KEY_CHANNEL, CHANNEL));
            IJ.log(String.format("%s = %s", KEY_MIN_AUTOFOCUS, minAutoFocus));
            IJ.log(String.format("%s = %s", KEY_MAX_AUTOFOCUS, maxAutoFocus));
            IJ.log(String.format("%s = %s", "liveMode", liveMode));
            IJ.log("");

            //
            // Inserted by Stephan Preibisch
            //

            // get the set exposure time and current binning
            double exposure = core_.getExposure();
            String binning = core_.getProperty(core_.getCameraDevice(), "Binning");
            int bin = Integer.parseInt(binning);
//            // get pixel depth
            String bits = core_.getProperty(core_.getCameraDevice(), "PixelType");
//            // enable 4x4 binning and adjust the exposure
            double focusBin = 4.0;
            double exposureFactor = (bin/focusBin) * (bin/focusBin);

            if (!liveMode) {
                if (verbose_) IJ.log("Setting Exposure to "+ exposure * exposureFactor +"....");
                core_.setExposure(exposure * exposureFactor);
    //
                if (verbose_) IJ.log("Setting Binning to 4....");
                core_.setProperty(core_.getCameraDevice(), "Binning", "4");
                
                if (verbose_) IJ.log("Setting Camera to 8bit ...");
                core_.setProperty(core_.getCameraDevice(), "PixelType", "Grayscale");
            }
            else {
                if (core_.isSequenceRunning()) {
                    core_.stopSequenceAcquisition();
                    Thread.sleep(WAIT_FOR_DEVICE_SLEEP);
                }
                core_.clearCircularBuffer();
                core_.startContinuousSequenceAcquisition(0);
            }

            //set z-distance to the lowest z-distance of the stack
            curDist = getPosition();
            baseDist = curDist - SIZE_FIRST * NUM_FIRST; //-30
            curDist = setPosition(Math.min(Math.max(minAutoFocus, baseDist), maxAutoFocus));

            //
            // End of insertion
            //
            // Rough search
            prevBestDist = bestDist;
            for (int i = 0; i < 2 * NUM_FIRST + 1; i++)
            {
                if (minAutoFocus == null || maxAutoFocus == null || 
                    (minAutoFocus <= baseDist + i * SIZE_FIRST && baseDist + i * SIZE_FIRST <= maxAutoFocus)) 
                {
                    curDist = setPosition(baseDist + i * SIZE_FIRST);

                    // indx =1;
                    snapSingleImage();
                    // indx =0;

                    ////curSh = sharpNess(ipCurrent_);
                    //curSh = computeFFT(ipCurrent_, 10, 15, 0.75);
                    //curShScale = computeFFT(ipCurrent_, 9, 10, 0.75); //local rescaling
                    //curSh = curSh / curShScale;
                    curSh = computeFFT(ipCurrent_, 15, 80, 1);
                    
                    
                    if (verbose_) IJ.log(String.format("setPosition: %.5f, real: %.5f, curSh: %.5f", baseDist + i * SIZE_FIRST, curDist, curSh));

                    if (curSh > bestSh || bestDist == null)
                    {
                        bestSh = curSh;
                        bestDist = curDist;
                        if (verbose_) IJ.log(String.format("Found new bestDist: %.5f, bestSh: %.5f", bestDist, bestSh));
                    }
                    /*else if (bestSh - curSh > THRES * bestSh && bestDist < 5000)
                    {
                        break;
                    }*/
                }
            }
            if (bestDist == null) throw new RuntimeException("baseDist is outside of min/max range!");

            baseDist = bestDist - SIZE_SECOND * NUM_SECOND;

            curDist = setPosition(Math.min(Math.max(minAutoFocus, baseDist), maxAutoFocus));

            //bestSh = 0;

            //Fine search
            for (int i = 0; i < 2 * NUM_SECOND + 1; i++)
            {
                if (minAutoFocus == null || maxAutoFocus == null || 
                    (minAutoFocus <= baseDist + i * SIZE_SECOND && baseDist + i * SIZE_SECOND <= maxAutoFocus)) 
                {
                    curDist = setPosition(baseDist + i * SIZE_SECOND);

                    // indx =1;
                    snapSingleImage();
                    // indx =0;

                    //curSh = sharpNess(ipCurrent_);
                    //curSh = computeFFT(ipCurrent_, 10, 15, 0.75);
                    //curShScale = computeFFT(ipCurrent_, 9, 10, 0.75); //local rescaling
                    //curSh = curSh / curShScale;
                    curSh = computeFFT(ipCurrent_, 15, 80, 1);
                    
                    if (verbose_) IJ.log(String.format("setPosition: %.5f, real: %.5f, curSh: %.5f", baseDist + i * SIZE_SECOND, curDist, curSh));

                    if (curSh > bestSh || bestDist == null)
                    {
                        bestSh = curSh;
                        bestDist = curDist;
                        if (verbose_) IJ.log(String.format("Found new bestDist: %.5f, bestSh: %.5f", bestDist, bestSh));
                    }
                    /*else if (bestSh - curSh > THRES * bestSh && bestDist < 5000)
                    {
                       break;
                    }*/
                }
            }

            //
            // Inserted by Stephan Preibisch
            //


            if (!liveMode) {
                // reset binning and re-adjust the exposure
                if (verbose_) IJ.log("ReSet Exposure....");
                core_.setExposure(exposure);
                if (verbose_) IJ.log("ReSet Binning....");
                core_.setProperty(core_.getCameraDevice(), "Binning", binning);
                if (verbose_) IJ.log("ReSet Camera Bits....");
                core_.setProperty(core_.getCameraDevice(), "PixelType", bits);
            }
            else {
                core_.stopSequenceAcquisition();
            }


            //
            // End of insertion
            //

            if (bestDist != null) {
                if (verbose_) IJ.log(String.format("*** FOUND BEST bestDist: %.5f, bestSh: %.5f", bestDist, bestSh));
                curDist = setPosition(Math.min(Math.max(minAutoFocus, bestDist), maxAutoFocus));
            }

            // indx =1;
            //if (verbose_) snapSingleImage();
            // indx =0;
            
//            core_.setShutterOpen(false);
//            core_.setAutoShutter(true);
            
            //Thread.sleep(10000);
        } 
        catch (Exception e)
        {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            IJ.error(sw.toString());
            e.printStackTrace();
        }
    }
    
    public double getPosition() {
        int tries = 0;
        final int MAX_TRIES = 15;
        Double lastPosition = null;
        final double EPSILON = 0.0001;
        while (tries++ < MAX_TRIES) {
            try {
                core_.waitForDevice(core_.getFocusDevice());
                Thread.sleep(WAIT_FOR_DEVICE_SLEEP);
                double currentPosition = core_.getPosition(core_.getFocusDevice());
                if (lastPosition != null && Math.abs(currentPosition-lastPosition) < EPSILON) {
                    return currentPosition;
                }
                lastPosition = currentPosition;
            }
            catch (Throwable e) { 
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                IJ.log(sw.toString());
            }
        }
        if (lastPosition == null) {
            throw new RuntimeException(String.format("Could not get Z-axis position!"));
        }
        IJ.log(String.format("Could not get reliable Z-axis position!: %.02f", lastPosition));
        return lastPosition;
    }
    
    public double setPosition(double position) {
        final double MAX_DIFFERENCE = 2.0;
        Double currentPosition = null;
        int tries = 0;
        final int MAX_TRIES = 15;
        while (tries++ < MAX_TRIES) {
            try {
                core_.setPosition(core_.getFocusDevice(), position);
                core_.waitForDevice(core_.getFocusDevice());
                Thread.sleep(WAIT_FOR_DEVICE_SLEEP);
                currentPosition = core_.getPosition(core_.getFocusDevice());
                if (Math.abs(currentPosition - position) <= MAX_DIFFERENCE) {
                    return currentPosition;
                }
            }
            catch (Throwable e) { 
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                IJ.log(sw.toString());
            }
        } 
        if (currentPosition == null) {
            throw new RuntimeException(String.format("Could not get Z-axis position!"));
        }
        if (Math.abs(currentPosition - position) > MAX_DIFFERENCE) {
            IJ.log(String.format("Could not set Z-axis position to %.02f, current position is %.02f!", position, currentPosition));
        }
        return currentPosition.doubleValue();
    }

    public FloatArray2D ImageToFloatArray(ImageProcessor ip)
    {
            if (ip == null)
            {
                    System.out.println("Image is empty.");
                    return null;
            }

            int width = ip.getWidth();
            int height = ip.getHeight();

            if (width * height == 0)
            {
                    System.out.println("Image is empty.");
                    return null;
            }

            if (ip.getPixels() instanceof int[])
            {
                    System.out.println("RGB images supported at the moment.");
                    return null;
            }

            FloatArray2D pixels = new FloatArray2D(width, height);
            int count;

            if (ip.getPixels() instanceof byte[])
            {
                    byte[] pixelTmp = (byte[]) ip.getPixels();
                    count = 0;

                    for (int y = 0; y < height; y++)
                            for (int x = 0; x < width; x++)
                                    pixels.data[pixels.getPos(x, y)] = (float) (pixelTmp[count++] & 0xff);
            }
            else if (ip.getPixels() instanceof short[])
            {
                    short[] pixelTmp = (short[]) ip.getPixels();
                    count = 0;

                    for (int y = 0; y < height; y++)
                            for (int x = 0; x < width; x++)
                                    pixels.data[pixels.getPos(x, y)] = (float) (pixelTmp[count++] & 0xffff);
            }
            else // instance of float[]
            {
                    float[] pixelTmp = (float[]) ip.getPixels();
                    count = 0;

                    for (int y = 0; y < height; y++)
                            for (int x = 0; x < width; x++)
                                    pixels.data[pixels.getPos(x, y)] = pixelTmp[count++];
            }

            return pixels;
    }

    private FloatArray2D zeroPad(FloatArray2D ip, int width, int height)
    {
            FloatArray2D image = new FloatArray2D(width, height);

            int offsetX = (width - ip.width) / 2;
            int offsetY = (height - ip.height) / 2;

            if (offsetX < 0)
            {
                    IJ.error("Stitching_3D.ZeroPad(): Zero-Padding size in X smaller than image! " + width + " < " + ip.width);
                    return null;
            }

            if (offsetY < 0)
            {
                    IJ.error("Stitching_3D.ZeroPad(): Zero-Padding size in Y smaller than image! " + height + " < " + ip.height);
                    return null;
            }

            for (int y = 0; y < ip.height; y++)
                    for (int x = 0; x < ip.width; x++)
                            image.set(ip.get(x, y), x + offsetX, y + offsetY);

            return image;
    }

    private FloatArray2D pffft2D(FloatArray2D values, boolean scale)
    {
            int height = values.height;
            int width = values.width;
            int complexWidth = (width / 2 + 1) * 2;

            FloatArray2D result = new FloatArray2D(complexWidth, height);

            //do fft's in x direction
            float[] tempIn = new float[width];
            float[] tempOut;

            FftReal fft = new FftReal(width);

            for (int y = 0; y < height; y++)
            {
                    tempOut = new float[complexWidth];

                    for (int x = 0; x < width; x++)
                            tempIn[x] = values.get(x, y);

                    fft.realToComplex( -1, tempIn, tempOut);

                    if (scale)
                            fft.scale(width, tempOut);

                    for (int x = 0; x < complexWidth; x++)
                            result.set(tempOut[x], x, y);
            }

            // do fft's in y-direction on the complex numbers
            tempIn = new float[height * 2];

            FftComplex fftc = new FftComplex(height);

            for (int x = 0; x < complexWidth / 2; x++)
            {
                    tempOut = new float[height * 2];

                    for (int y = 0; y < height; y++)
                    {
                            tempIn[y * 2] = result.get(x * 2, y);
                            tempIn[y * 2 + 1] = result.get(x * 2 + 1, y);
                    }

                    fftc.complexToComplex( -1, tempIn, tempOut);

                    for (int y = 0; y < height; y++)
                    {
                            result.set(tempOut[y * 2], x * 2, y);
                            result.set(tempOut[y * 2 + 1], x * 2 + 1, y);
                    }
            }

            return result;
    }

    public static void rearrangeFFT(FloatArray2D values)
    {
            float[] fft = values.data;
            int w = values.width;
            int h = values.height;

            int halfDimYRounded = ( int )( h / 2 );

            float buffer[] = new float[w];
            int pos1, pos2;

            for (int y = 0; y < halfDimYRounded; y++)
            {
                    // copy upper line
                    pos1 = y * w;
                    for (int x = 0; x < w; x++)
                            buffer[x] = fft[pos1++];

                    // copy lower line to upper line
                    pos1 = y * w;
                    pos2 = (y+halfDimYRounded) * w;
                    for (int x = 0; x < w; x++)
                            fft[pos1++] = fft[pos2++];

                    // copy buffer to lower line
                    pos1 = (y+halfDimYRounded) * w;
                    for (int x = 0; x < w; x++)
                            fft[pos1++] = buffer[x];
            }
    }

    public static float[] computePowerSpectrum(float[] complex)
    {
           int wComplex = complex.length / 2;

            float[] powerSpectrum = new float[wComplex];

            for (int pos = 0; pos < wComplex; pos++)
                    powerSpectrum[pos] = (float)Math.sqrt(Math.pow(complex[pos*2],2) + Math.pow(complex[pos*2 + 1],2));
                     
         
            return powerSpectrum;
    }

    public static double gLog(double z, double c)
    {
            if (c == 0)
                    return z;
            else
                    return Math.log10((z + Math.sqrt(z * z + c * c)) / 2.0);
    }

    public static void gLogImage(float[] image, float c)
    {
            for (int i = 0; i < image.length; i++)
                    image[i] = (float)gLog(image[i], c);
    }

    /**
     * 
     * @param ip - ImageProcessor to determine the grade of sharpness from
     * @param minPercent - Where the bandpass starts [in %], a good guess is 15
     * @param maxPercent - Where the bandpass ends [in %], a good guess is 80
     * @param starPercent - How much of the center to cut out [in %], a good guess is 1
     * @return Amount of detail on linear scale, the higher the more content
     */
    public double computeFFT(ImageProcessor ip, double minPercent, double maxPercent, double starPercent)
    {
        //
        // Convert to Float Datastructure
        //

        FloatArray2D img = ImageToFloatArray(ip);

        //
        // Zero Padding
        //

        int widthZP = FftReal.nfftFast(img.width);
        int heightZP = FftComplex.nfftFast(img.height);

        img = zeroPad(img, widthZP, heightZP);

        //
        // Fourier Transform
        //

        FloatArray2D fft = pffft2D(img, false);
        FloatArray2D power = new FloatArray2D( computePowerSpectrum(fft.data), fft.width/2, fft.height);

        //
        // Rearrange
        //

        rearrangeFFT(power);

        //
        // Log of Power
        //

        gLogImage(power.data, 2);

        //
        // Simple statistics
        //

        double sum = 0;
        int count = 0;

        double radius = Math.sqrt((power.height/2D)*(power.height/2D) + power.width*power.width);
        double minRadius = radius * (minPercent / 100.0);
        double maxRadius = radius * (maxPercent / 100.0);
        double minRadiusSquare = minRadius * minRadius;
        double maxRadiusSquare = maxRadius * maxRadius;
        double centerY = power.height/2;

        double yThreshold1 = centerY - (power.height * (starPercent / 200.0));
        double yThreshold2 = centerY + (power.height * (starPercent / 200.0));
        double xThreshold = power.width * (starPercent / 100.0);

        int arrayPos = 0;

        for (int y = 0; y < power.height; y++)
        {
            if (y < yThreshold1 || y > yThreshold2)
            {
                arrayPos = power.getPos(0, y);

                for (int x = 0; x < power.width; x++)
                {
                    // compute distance to center
                    double distY = y - centerY;
                    double distanceSquare = x * x + distY * distY;

                    // check radi and check starPercent x
                    if (distanceSquare > minRadiusSquare && distanceSquare < maxRadiusSquare && x > xThreshold)
                    {
                        sum += power.data[arrayPos];
                        count++;
                    }

                    arrayPos++;
                }
            }
        }

        sum /= (double)count;

        power.data = img.data = null;
        power = img = null;

        System.gc();

        return sum;
    }

    public static boolean compareImages(TaggedImage taggedImage1, TaggedImage taggedImage2) {
        if (taggedImage1 == null || taggedImage2 == null)
            return false;
        if (taggedImage1.pix instanceof byte[] && taggedImage2.pix instanceof byte[]) {
            byte[] pix1 = (byte[])taggedImage1.pix;
            byte[] pix2 = (byte[])taggedImage2.pix;
            if (pix1.length != pix2.length) return false;
            for (int i = 0; i < pix1.length; ++i) {
                if (pix1[i] != pix2[i]) return false;
            }
            return true;
        }
        if (taggedImage1.pix instanceof int[] && taggedImage2.pix instanceof int[]) {
            int[] pix1 = (int[])taggedImage1.pix;
            int[] pix2 = (int[])taggedImage2.pix;
            if (pix1.length != pix2.length) return false;
            for (int i = 0; i < pix1.length; ++i) {
                if (pix1[i] != pix2[i]) return false;
            }
            return true;
        }
        if (taggedImage1.pix instanceof short[] && taggedImage2.pix instanceof short[]) {
            short[] pix1 = (short[])taggedImage1.pix;
            short[] pix2 = (short[])taggedImage2.pix;
            if (pix1.length != pix2.length) return false;
            for (int i = 0; i < pix1.length; ++i) {
                if (pix1[i] != pix2[i]) return false;
            }
            return true;
        }
        if (taggedImage1.pix instanceof float[] && taggedImage2.pix instanceof float[]) {
            float[] pix1 = (float[])taggedImage1.pix;
            float[] pix2 = (float[])taggedImage2.pix;
            if (pix1.length != pix2.length) return false;
            for (int i = 0; i < pix1.length; ++i) {
                if (pix1[i] != pix2[i]) return false;
            }
            return true;
        }
        return false;
    }

    //take a snapshot and save pixel values in ipCurrent_
    private TaggedImage lastimg;
    public boolean snapSingleImage()
    {
        try
        {
            if (liveMode) {
                TaggedImage img = null;
                ImageProcessor ip = null;
                while (img == null || ip == null || ip.getPixels() == null) {
                    try {
                        img = core_.getLastTaggedImage();
                        if (img != null) {
                            if (lastimg != null && compareImages(img, lastimg)) {
                                IJ.log("Autofous detected same image twice, retrying...");
                                img = null;
                                ip = null;
                                // re-start live mode and try again
                                try { core_.stopSequenceAcquisition(); } 
                                catch (Exception e) { /* do nothing */ }
                                Thread.sleep(5000);
                                core_.clearCircularBuffer();
                                core_.startContinuousSequenceAcquisition(0);
                                Thread.sleep(5000);
                                continue;
                            }
                            ip = ImageUtils.makeProcessor(img);  
                            lastimg = img;
                        }
                    } catch (Throwable e) { /* do nothing */ }
                    if (img == null || ip == null || ip.getPixels() == null) {
                        Thread.sleep(WAIT_FOR_DEVICE_SLEEP);
                    }
                }
                ImagePlus implus = new ImagePlus(String.valueOf(curDist), ip);
                new ImageConverter(implus).convertToGray8();
                ipCurrent_ = implus.getProcessor();
            }
            else {
                core_.snapImage();
                TaggedImage img = core_.getTaggedImage();
                ImagePlus implus = new ImagePlus(String.valueOf(curDist), ImageUtils.makeProcessor(img));
                new ImageConverter(implus).convertToGray8();
                ipCurrent_ = implus.getProcessor();
            }
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            IJ.error(sw.toString());
            return false;
        }
        return true;
    }

    //making a new window for a new snapshot.
    public ImagePlus newWindow()
    {
        ImagePlus implus;
        ImageProcessor ip;

        //long byteDepth = core_.getBytesPerPixel();
        //if (byteDepth == 1) {
        //    ip = new ByteProcessor((int) core_.getImageWidth(), (int) core_.getImageHeight());
        //}
        //else {
        //    ip = new ShortProcessor((int) core_.getImageWidth(), (int) core_.getImageHeight());
        //}
        ip = new ByteProcessor((int) core_.getImageWidth(), (int) core_.getImageHeight());
        ip.setColor(Color.black);
        ip.fill();

        implus = new ImagePlus(String.valueOf(curDist), ip);
        if (indx == 1)
        {
            if (verbose_)
            {
                // create image window if we are in the verbose mode
                new ImageWindow(implus);
            }
        }
        return implus;
    }

    public double fullFocus()
    {
        Date startTime = new Date();
        try {
            run("silent");
        }
        finally {
            try {
                Date endTime = new Date();
                Long autofocusDuration = new Long(this.getPropertyValue("autofocusDuration"));
                this.setPropertyValue("autofocusDuration", new Long(
                        endTime.getTime() - startTime.getTime() + autofocusDuration).toString());
            } 
            catch (MMException e) {throw new RuntimeException(e);}
        }
        //run("");
        return 0;
    }

    public String getVerboseStatus()
    {
        return new String("OK");
    }

    public double incrementalFocus()
    {
        Date startTime = new Date();
        try {
            run("silent");
        }
        finally {
            try {
                Date endTime = new Date();
                Long autofocusDuration = new Long(this.getPropertyValue("autofocusDuration"));
                this.setPropertyValue("autofocusDuration", new Long(
                        endTime.getTime() - startTime.getTime() + autofocusDuration).toString());
            } 
            catch (MMException e) {throw new RuntimeException(e);}
        }
        //run("");
        return 0;
    }

    public void setMMCore(CMMCore core)
    {
        core_ = core;
    }

    @Override
    public void applySettings() {
      try {
          // reset the skip counter
          skipCounter = -1;
          bestDist = null;
          prevBestDist = null;

          SIZE_FIRST = Double.parseDouble(getPropertyValue(KEY_SIZE_FIRST));
          NUM_FIRST = Integer.parseInt(getPropertyValue(KEY_NUM_FIRST));
          SIZE_SECOND = Double.parseDouble(getPropertyValue(KEY_SIZE_SECOND));
          NUM_SECOND = Integer.parseInt(getPropertyValue(KEY_NUM_SECOND));
          THRES = Double.parseDouble(getPropertyValue(KEY_THRES));
          CROP_SIZE = Double.parseDouble(getPropertyValue(KEY_CROP_SIZE));
          CHANNEL = getPropertyValue(KEY_CHANNEL);

          minAutoFocus = getPropertyValue(KEY_MIN_AUTOFOCUS).equals("")? null : 
              Double.parseDouble(getPropertyValue(KEY_MIN_AUTOFOCUS));
          maxAutoFocus = getPropertyValue(KEY_MAX_AUTOFOCUS).equals("")? null : 
              Double.parseDouble(getPropertyValue(KEY_MAX_AUTOFOCUS));
      
          liveMode = "yes".equals(getPropertyValue("liveMode"));
          setPropertyValue("autofocusDuration", new Long(0).toString());
      } catch (NumberFormatException e) {
         e.printStackTrace();
      } catch (MMException e) {
         e.printStackTrace();
      }
   }


    /**
     * <p>Title: </p>
     *
     * <p>Description: </p>
     *
     * <p>Copyright: Copyright (c) 2006</p>
     *
     * <p>Company: </p>
     *
     * @author not attributable
     * @version 1.0
     */
    private abstract class FloatArray
    {
            public float data[] = null;
            public abstract FloatArray clone();
    }


    /**
     * <p>Title: FloatArray2D</p>
     *
     * <p>Description: </p>
     *
     * <p>Copyright: Copyright (c) 2007</p>
     *
     * <p>Company: </p>
     *
     * <p>License: GPL
     *
     * This program is free software; you can redistribute it and/or
     * modify it under the terms of the GNU General Public License 2
     * as published by the Free Software Foundation.
     *
     * This program is distributed in the hope that it will be useful,
     * but WITHOUT ANY WARRANTY; without even the implied warranty of
     * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
     * GNU General Public License for more details.
     *
     * You should have received a copy of the GNU General Public License
     * along with this program; if not, write to the Free Software
     * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
     *
     * @author Stephan Preibisch
     * @version 1.0
     */
    private class FloatArray2D extends FloatArray
    {
//	public float data[] = null;
            public int width = 0;
            public int height = 0;


            public FloatArray2D(int width, int height)
            {
                    data = new float[width * height];
                    this.width = width;
                    this.height = height;
            }

            public FloatArray2D(float[] data, int width, int height)
            {
                    this.data = data;
                    this.width = width;
                    this.height = height;
            }

            public FloatArray2D clone()
            {
                    FloatArray2D clone = new FloatArray2D(width, height);
                    System.arraycopy(this.data, 0, clone.data, 0, this.data.length);
                    return clone;
            }

            public int getPos(int x, int y)
            {
                    return x + width * y;
            }

            public float get(int x, int y)
            {
                    return data[getPos(x, y)];
            }

            public void set(float value, int x, int y)
            {
                    data[getPos(x, y)] = value;
            }
    }


	@Override
	public double computeScore(ImageProcessor impro) {
        return computeFFT(impro, 15, 80, 1);
	}

	@Override
	public double getCurrentFocusScore() {
        return computeFFT(ipCurrent_, 15, 80, 1);
	}

	@Override
	public void setApp(ScriptInterface app) {
		this.core_ = app.getMMCore();
	}

	@Override
	public void focus(double arg0, int arg1, double arg2, int arg3) throws MMException {
		throw new MMException("OBSOLETE - do not use");
	}

	@Override
	public String getDeviceName() {
        return AF_DEVICE_NAME;
	}

	@Override
	public int getNumberOfImages() {
		return 0;
	}
}


