/** Threshold_Median_Filter - 1.6 - Talon Chandler - Takes a 2D, 3D, or
hyperstack image and replaces pixels that have a neighborhood average less than
the slider threshold with the neighborhood median. 

The neighborhood average for each pixel is found by averaging each
adjacent pixel with equal weight. 

By default the center pixel is ignored during neighborhood averaging. 

A pixel's neighborhood median is found by taking the median of each adjacent 
pixel excluding the center pixel. 
 */

import ij.*;
import ij.process.*;
import ij.measure.CurveFitter;
import ij.plugin.frame.Fitter;
import ij.plugin.Duplicator;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.ImageWindow;
import java.awt.*;
import java.util.*;

public class Threshold_Median_Filter implements ExtendedPlugInFilter, DialogListener {

    long startTime;
    private static int FLAGS =  DOES_8G | DOES_16 | DOES_32 | KEEP_PREVIEW;           

    private double threshold;
    private boolean preview;
    private boolean preview_z_avg;    
    private boolean att_correct;
    private boolean[] checkValues;
    
    private ImagePlus imp;
    private ImagePlus impOriginal;
    private ImagePlus impzOriginal;
    private ImageStack is;
    private ImageStack isOriginal;
    private ImageProcessor ip;
    private ImageProcessor[] ipOriginal;
    private ImageWindow win_z;
    
    public int setup (String arg, ImagePlus imp) {
	try{impOriginal = new Duplicator().run(imp);}
	catch(Exception e){IJ.error("Please open an image."); return DONE;}
       	this.imp = imp;
        return FLAGS;
    }

    public int showDialog (ImagePlus imp, String command, PlugInFilterRunner pfr) {

	// Build UI
        GenericDialog gd = new GenericDialog(command+"...");
        gd.addSlider("Threshold", 0.0, max(imp), 0.0);
	gd.setInsets(5,0,0);	
        gd.addPreviewCheckbox(pfr);
	gd.setInsets(0,0,0);
	gd.addCheckbox(" Preview Z-Average", true);
	gd.setInsets(0,0,0);	
	gd.addCheckbox(" Attenuation Correction", false);
	// Add checkbox for each channel
	checkValues = new boolean[imp.getNChannels()];	
	String[] names = new String[imp.getNChannels()];
	boolean[] defaultValues = new boolean[imp.getNChannels()];
        for(int i = 0; i < imp.getNChannels(); i++) {
	    names[i] = " Channel " + Integer.toString(i+1);
	    defaultValues[i] = false;
	}
	defaultValues[imp.getC()-1] = true; // Check current channel by default
	gd.setInsets(0,0,0);	
	gd.addCheckboxGroup(imp.getNChannels(), 1, names, defaultValues);
	// Make UI responsive
	gd.addDialogListener(this);
        gd.showDialog();           // User input happens here
        if (gd.wasCanceled()) {    // Dialog cancelled?
	    this.imp = thresholdMedian(imp, impOriginal, 0, false); // Reset image
            return DONE;
	}
	if (gd.wasOKed()) {
	    this.imp = thresholdMedian(imp, impOriginal, threshold, true);
	}
        return FLAGS;
    }

    public boolean dialogItemChanged (GenericDialog gd, AWTEvent e) {
	// Store user input every time UI changes
        threshold = gd.getNextNumber();
	preview = gd.getNextBoolean();
	preview_z_avg = gd.getNextBoolean();	
	att_correct = gd.getNextBoolean();
	for(int i = 0; i < imp.getNChannels(); i++)
	    checkValues[i] = gd.getNextBoolean();

        return !gd.invalidNumber() && threshold>=0 && threshold <= max(imp);
    }
    
    public void run (ImageProcessor ip) {
	// Main function
	imp = thresholdMedian(imp, impOriginal, threshold, false);	
	if(preview_z_avg) {
	    imp.unlock();

	    String[] names = WindowManager.getImageTitles();
	    for(int i=0; i<names.length; i++) {
		if(names[i].substring(0,3).equals("AVG")) {
		    IJ.selectWindow(names[i]);
		    IJ.run("Close");
		}
	    }

	    IJ.run(imp, "Z Project...", "projection=[Average Intensity]");
	    IJ.run("In [+]", "");
	    IJ.run("In [+]", "");
	    
	    ImagePlus imp_z = WindowManager.getCurrentImage();
	    impzOriginal = new Duplicator().run(imp_z);
	    imp_z = thresholdMedian(imp_z, impzOriginal, threshold, false);
	    IJ.run(imp_z, "Enhance Contrast", "saturated=0");
	}
    }

    private ImagePlus thresholdMedian (ImagePlus imp_loc, ImagePlus impOriginal_loc, double threshold, Boolean filterAllSlices) {
	is = impOriginal_loc.getStack();
	isOriginal = imp_loc.getStack();
	int thresholdFrame = imp_loc.getT();

	// Fit exponential to average intensity
	int NFrames = imp_loc.getNFrames();
	double[] xData = new double[NFrames];
	double[] yData = new double[NFrames];

	for (int frame=0; frame<NFrames; frame++) {
	    xData[frame] = frame; 
	    yData[frame] = averageFrameIntensity(imp_loc.getC(), frame, is, impOriginal_loc);
	}
	CurveFitter cf = new CurveFitter(xData, yData);
	if (filterAllSlices  && att_correct) {
	    cf.doFit(11); // Exponential fit
	    Fitter.plot(cf);
	}
	
	int c_min = 0;
	int z_min = 0;
	int t_min = 0;
	int c_max = 0;
	int z_max = 0;
	int t_max = 0;

	// Filter all slices
	if (filterAllSlices) {
	    c_min = 1;
	    z_min = 1;
	    t_min = 1;		    
	    c_max = imp_loc.getNChannels();
	    z_max = imp_loc.getNSlices();
	    t_max = imp_loc.getNFrames();
	}
	// Only filter the current slice (preview mode)
	else { 
	    z_min = imp_loc.getZ();
	    c_min = imp_loc.getC();
	    t_min = imp_loc.getT();
	    z_max = z_min;
	    c_max = c_min;
	    t_max = t_min;	    
	}
	// Find fraction of channels to filter for progress bar
	int trueCount = 0;
	for (int i = 0; i < imp_loc.getNChannels(); i++) {
	    if(checkValues[i])
		trueCount++;
	}
	double channelFrac = trueCount/ (double)checkValues.length;

	// Find averaging kernel
	double[][][] orig_kernel;
	orig_kernel = new double[][][] {{{1,1,1},{1,1,1},{1,1,1}},{{1,1,1},{1,0,1},{1,1,1}},{{1,1,1},{1,1,1},{1,1,1}}};
	
	double[][][] kernel = normalize(orig_kernel);
	int kernel_size = orig_kernel.length;
	int kernel_middle_index = (int) (kernel_size-1)/2;

	Integer[] m_neighbors = new Integer[26]; // Median neighbors
	Double[] a_neighbors = new Double[27]; // Average neighbors				
	Float[] f_m_neighbors = new Float[26];
	ipOriginal = new ImageProcessor[kernel_size];

	// For each channel, slice, and time
	int slice_index = 0;
	for (int c=c_min; c<=c_max; c++) {
	    if (checkValues[c-1]) { // Only filter selected channels
		for (int z=z_min; z<=z_max; z++) {
		    for (int t=t_min; t<=t_max; t++) {

			// Update progress bar
			IJ.showProgress((double)slice_index/(channelFrac*imp_loc.getImageStackSize()));
			slice_index += 1;

			// Find neighborhood slices
			int z_dim = imp_loc.getNSlices();
			for (int kernel_z=0; kernel_z<kernel_size; kernel_z++) {
			    int z_index = reflectedIndex(kernel_z, kernel_size, z, z_dim);
			    ipOriginal[kernel_z] = is.getProcessor(imp_loc.getStackIndex(c, z_index, t));
			}
			// Go through each pixel in the 2D image
			ip = isOriginal.getProcessor(imp_loc.getStackIndex(c, z, t));
			int xdim = ip.getWidth();
			int ydim = ip.getHeight();
			for (int x=0; x<xdim; x++) {
			    for (int y=0; y<ydim; y++) {
				// Previous arraylist alloc here
				//m_neighbors.clear();
				//a_neighbors.clear();
				// Find kernel weighted neighborhood pixels
				int ind = 0;
				for (int kernel_x=0; kernel_x<kernel_size; kernel_x++) {
				    for (int kernel_y=0; kernel_y<kernel_size; kernel_y++) {
					for (int kernel_z=0; kernel_z<kernel_size; kernel_z++) {
					    int x_index = reflectedIndex(kernel_x, kernel_size, x, xdim);
					    int y_index = reflectedIndex(kernel_y, kernel_size, y, ydim);
					    a_neighbors[ind] = kernel[kernel_z][kernel_x][kernel_y]*ipOriginal[kernel_z].getPixel(x_index, y_index);
					    ind = ind + 1;
					}
				    }
				}

				// Find median neighborhood pixels
				int ind2 = 0;
				int median_size = 3;
				int median_middle_index = (int) (median_size-1)/2;
				for (int kernel_x=0; kernel_x<median_size; kernel_x++) {
				    for (int kernel_y=0; kernel_y<median_size; kernel_y++) {
					for (int kernel_z=0; kernel_z<median_size; kernel_z++) {
					    int x_index = reflectedIndex(kernel_x, median_size, x, xdim);
					    int y_index = reflectedIndex(kernel_y, median_size, y, ydim);
					    if (!(x_index == x && y_index == y && kernel_z == (int) median_middle_index)) { // Exclude center pixel
						m_neighbors[ind2] = ipOriginal[((kernel_size - median_size)/2) + kernel_z].getPixel(x_index, y_index);
					        ind2 = ind2 + 1;
					    }
					}
				    }
				}

				// Convert median neighbors to floats
				for(int i = 0; i < m_neighbors.length; i++) {
				    int old = m_neighbors[i];
				    if (ipOriginal[1] instanceof FloatProcessor)
					f_m_neighbors[i] = Float.intBitsToFloat(old);
				    else
					f_m_neighbors[i] = (float) old;
				}

				// Correct threshold for attenuation
				double att_threshold;
				if (filterAllSlices && att_correct)
				    att_threshold = threshold*(cf.f(t)/cf.f(thresholdFrame));
				else
				    att_threshold = threshold;

				// Main filter. If neighborhood mean is less than threshold,
				// replace with the neighborhood median. Otherwise, leave 
				// the pixel alone.
				if(sum(a_neighbors) < att_threshold) { // The sum is actually a mean of pixels because the kernel is normalized
				    int i = (int) median(f_m_neighbors);
				    ip.putPixel(x,y,i);
				}
				else
				    ip.putPixel(x,y,ipOriginal[kernel_middle_index].getPixel(x,y));
			    }
			}
		    }
		}
	    }
	}
	return imp_loc;
    }

    // Helper functions
    
    // Returns the sum of an ArrayList    
    private double sum(Double[] array){
	double sum = 0;
	for(int i = 0; i < array.length; i++) {
	    sum += array[i];
	}
	return sum;
    }

    // Returns the median of an ArrayList
    private float median(Float[] array){
	Arrays.sort(array);
	int len = array.length;
	if(len%2==0)
	    return((array[(len/2)-1]+array[len/2])/2);
	else
	    return array[((len-1)/2)];
    }
    
    // Returns the maximum pixel value in an image stack. 
    private float max(ImagePlus imp) {
	ImageStack is = imp.getStack();
	float maxPix = 0;
	float pix = 0;
	int nslices = is.getSize();
	for (int z=1; z<=is.getSize(); z++) {
	    ImageProcessor ipMax = is.getProcessor(z);	    
	    for (int x=0; x<ipMax.getWidth(); x++) {
		for (int y=0; y<ipMax.getHeight(); y++) {
		    if (ip instanceof FloatProcessor)
			pix = Float.intBitsToFloat(ipMax.getPixel(x,y));
		    else
			pix = ipMax.getPixel(x,y);
		    if (pix > maxPix)
			maxPix = pix;
		}
	    }
	}
	return maxPix;
    }

    // Takes:
    // kernel_i - The current index of the kernel
    // kernel_i_size - The size of the kernel
    // i - The current index in the entire signal 
    // i_size - The size of the entire signal
    //
    // Returns the index of the entry that is at position kernel_i when the
    // kernel is centered on i. If the index is outside the bounds of the
    // signal, the reflected index is returned. 
    private int reflectedIndex(int kernel_i, int kernel_i_size, int i, int i_size) {
	int index = kernel_i + i - ((kernel_i_size - 1)/2);
	if (!((index > 0) && (index < i_size))) {
	    if (!((-index > 0) && (-index < i_size))) {
		return -index; // Lower bound reflection
	    }else if (!(((i_size - (index - i_size)) > 0) && ((i_size - (index - i_size)) < i_size))) {
		return i_size - (index - i_size); // Upper bound reflection
	    }else {
		IJ.log("Error: should not reach here");
		return 0;
	    }
	}
	return index;
    }

    // Normalizes a 3D array
    private double[][][] normalize(double[][][] input) {
	double sum = 0;
	double[][][] output = new double[input.length][input.length][input.length];

	// Calculate the sum of the array
	for (int i=0; i<input.length; i++) {
	    for (int j=0; j<input.length; j++) {
		for (int k=0; k<input.length; k++) {
		    sum = sum + input[i][j][k];
		}
	    }
	}
	// Divide each entry by the array sum
	for (int i=0; i<input.length; i++) {
	    for (int j=0; j<input.length; j++) {
		for (int k=0; k<input.length; k++) {
		    output[i][j][k] = (double) input[i][j][k]/sum;
		}
	    }
	}
	return output;
    }

    private double averageFrameIntensity(int channel, int frame, ImageStack is_loc, ImagePlus imp_loc) {
	ImageProcessor ipIntensity;
	double sum = 0.0;
	int count = 0;

	for (int z=0; z<imp_loc.getNSlices(); z++) {
	    ipIntensity = is_loc.getProcessor(imp_loc.getStackIndex(channel, z, frame));
	    for (int x=0; x<ipIntensity.getWidth(); x++) {
		for (int y=0; y<ipIntensity.getHeight(); y++) {
		    count = count + 1;
		    sum = sum + ipIntensity.getPixel(x,y);
		}
	    }
	}
	return sum/count;
    }
	
    public void setNPasses (int nPasses) {}
}
