
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.Enumeration;
import java.util.Hashtable;

import ij.*;
import ij.gui.*;
import ij.plugin.*;
import ij.plugin.frame.Recorder;
import ij.process.*;

/**
 * Expression is a plugin filter for ImageJ that applies
 * a user-specified mathematical expression to each pixel.
 *
 * Available variables are:
 * i (value/intensity of this pixel), w (image width in pixels), h (image height in pixels),
 * wr (actual image width adjusted for scaling), hr (actual image height adjusted for scaling),
 * xi (pixel postion in row; 0 &le; xi &le; w), yi (pixel postion in column; 0 &le; yi &le; h)
 * x (position in row adjusted for scaling), y (position in column adjusted for scaling),
 * rw (roi width), rh (roi height),
 * pi, e, d (distance from center of image),
 * a (angle, measured from center of image; between 0 and 2*pi)
 * rnd (random number 0 &lt;= r &lt; 1)
 * n - for working with stacks, the number of slices
 * t - for working with stacks, varies from 1 (first slice) to n (last slice)
 * r, g, b - red, green and blue values/intensities of the current pixel
 * i(m), r(m), g(m), b(m) - m must be between 1 and the number of slices in the stack
 *		These will then return the value of the pixel at location (x,y) in the
 *		image slice 'm'. E.g. m=1 means the pixel in the first slice, m=n means
 *		the pixel in the last slice, and m=t means the current slice (for which the
 *		values would be identical to i, r, g and b, respectively.
 * i(x,y), r(x,y), g(x,y), b(x,y)
 *		These functions return the value of the pixel at location (x,y) in the current image slice. 
 * i(x,y,m), r(x,y,m), g(x,y,m), b(x,y,m)
 *		Same as i(x,y) etc., but for slice 'm' instead of the current slice.
 * maxval - maximum pixel value for this kind of image
 *         (255 for 8bit, 65536 for 16bit, 1 for 32bit)
 * mand() - Mandelbrot function at point (x,y), using the standard iteration z^2+c
 * mand(n) - Mandelbrot function at point (x,y), using the iteration z^n+c
 *
 * Available operators and functions are:
 *		+  -  *  /  ^  %  sin()  cos()  tan()  asin()  acos()  atan()
 *		exp()  ln()  log()  min()  max()  sqrt()  ceil()  floor()  abs()  round()
 *		MIN and MAX accept an arbitrary number of arguments.
 *
 * Expression is inspired by Jim Bumgardners Expression plugin for NIH Image,
 * but doesn't have as much functionality.
 * The image processing framework was ripped off ImageJ's IP_Demo example.
 *
 * This code was written by Ulf Dittmer (udittmer (at) yahoo.com),
 * and is hereby released under the same terms as ImageJ.
 *
 * TODO: consider how to re-enable output scaling for HLS and HSV color models,
 * 			and what to do to force h, l, s and v to be in range
 */

public class ExpressionNT_ extends ij.plugin.frame.PlugInFrame
	implements PlugIn, ActionListener, ItemListener {

	final static String USER_FUNCTIONS_PREF_KEY = "ExpressionNT.functions";
	boolean canUsePrefs = false;

	final static int NUM_PRESET_ITEMS = 17;			// add one for the name, so it's 18 altogether
	final static double MORE_LESS_FACTOR = 1.5;		// zoom factor for the maximum # of iterations

	// running index of classes created
	static int countClasses = 1;

	Navi navigator = null;
	Func functions = null;

	String fileName="presetsNT.txt", dirName=null, subDirName="Expression";

	TextField red, blue, green, name, min, max, tf_x, tf_y, tf_w, tf_h, iter;
	Button less, more;
	Checkbox wrap, center, maxIs0, newWindow, collapseStack, currentImage;
	Choice presetsMenu, colorModel;
	Label l1, l2, l3;	// for the 3 expression fields
	int previousId;
	Hashtable presets = new Hashtable();
	long id = System.currentTimeMillis();
	String userFunctions = "";

	Font plainFont = new Font("Helvetica", Font.PLAIN, 12);
	Font boldFont = new Font("Helvetica", Font.BOLD, 12);

	public ExpressionNT_() {
		super("ExpressionNT");
		setLayout(new BorderLayout(10, 10));
		Panel centerPanel = new Panel();
		centerPanel.setLayout(new GridLayout(10, 1, 0, 5));

		Panel row0 = new Panel();
		Label cmLabel = new Label("Color model: ");
		cmLabel.setFont(boldFont);
		row0.add(cmLabel);
		colorModel = new Choice();
		colorModel.setName("colorModel");
		colorModel.addItem("Gray");
		colorModel.addItem("HLS");
		colorModel.addItem("HSV");
		colorModel.addItem("RGB");
		colorModel.addItemListener(this);
		row0.add(colorModel);
		centerPanel.add(row0);

		Panel row1 = new Panel();
		red = new TextField(40);
		red.setFont(plainFont);
		l1 = new Label(" R ");
		l1.setFont(boldFont);
		row1.add(l1);
		row1.add(red);
		centerPanel.add(row1);

		Panel row2 = new Panel();
		green = new TextField(40);
		green.setFont(plainFont);
		l2 = new Label(" G ");
		l2.setFont(boldFont);
		row2.add(l2);
		row2.add(green);
		centerPanel.add(row2);

		Panel row3 = new Panel();
		blue = new TextField(40);
		blue.setFont(plainFont);
		l3 = new Label(" B ");
		l3.setFont(boldFont);
		row3.add(l3);
		row3.add(blue);
		centerPanel.add(row3);

		Panel row6 = new Panel();
		tf_x = new TextField(10);
		tf_x.setFont(plainFont);
		tf_y = new TextField(10);
		tf_y.setFont(plainFont);
		Label l61 = new Label(" X ");
		l61.setFont(boldFont);
		row6.add(l61);
		row6.add(tf_x);
		Label l62 = new Label(" Y ");
		l62.setFont(boldFont);
		Button functions = new Button("Functions");
		functions.setFont(boldFont);
		functions.setActionCommand("functions");
		functions.addActionListener(this);
		row6.add(l62);
		row6.add(tf_y);
		row6.add(functions);
		centerPanel.add(row6);

		Panel row7 = new Panel();
		tf_w = new TextField(10);
		tf_w.setFont(plainFont);
		tf_h = new TextField(10);
		tf_h.setFont(plainFont);
		Label l71 = new Label("W");
		l71.setFont(boldFont);
		row7.add(l71);
		row7.add(tf_w);
		Label l72 = new Label("H");
		l72.setFont(boldFont);
		row7.add(l72);
		row7.add(tf_h);
		center = new Checkbox("Center");
		center.setFont(boldFont);
		row7.add(new Label("     "));
		row7.add(center);
		centerPanel.add(row7);

		Panel row4 = new Panel();
		min = new TextField(10);
		min.setFont(plainFont);
		max = new TextField(10);
		max.setFont(plainFont);
		Label l4 = new Label("Min");
		l4.setFont(boldFont);
		row4.add(l4);
		row4.add(min);
		Label l5 = new Label("Max");
		l5.setFont(boldFont);
		row4.add(l5);
		row4.add(max);
		wrap = new Checkbox("Wrap");
		wrap.setFont(boldFont);
		row4.add(wrap);
		centerPanel.add(row4);

		Panel row9 = new Panel();
		newWindow = new Checkbox("New window");
		newWindow.setFont(boldFont);
		newWindow.addItemListener(this);
		row9.add(newWindow);
		collapseStack = new Checkbox("Collapse Stack");
		collapseStack.setFont(boldFont);
		row9.add(collapseStack);
		currentImage = new Checkbox("Current Image Only");
		currentImage.setFont(boldFont);
		row9.add(currentImage);
		centerPanel.add(row9);

		Panel row8 = new Panel();
		Label l8 = new Label("Max Iter");
		l8.setFont(boldFont);
		row8.add(l8);
		more = new Button(" + ");
		more.setFont(boldFont);
		more.setActionCommand("more");
		more.addActionListener(this);
		row8.add(more);
		iter = new TextField("200", 6);
		iter.setFont(plainFont);
		row8.add(iter);
		less = new Button(" - ");
		less.setFont(boldFont);
		less.setActionCommand("less");
		less.addActionListener(this);
		row8.add(less);
		maxIs0 = new Checkbox("Max is 0");
		maxIs0.setFont(boldFont);
		row8.add(new Label("     "));
		row8.add(maxIs0);
		centerPanel.add(row8);

		Panel row5 = new Panel();
		Label l7 = new Label("Presets");
		l7.setFont(boldFont);
		row5.add(l7);
		name = new TextField(20);
		name.setFont(plainFont);
		row5.add(name);
		presetsMenu = new Choice();
		presetsMenu.setName("presetsMenu");
		presetsMenu.setBackground(Color.white);
		presetsMenu.addItem("Add");
		presetsMenu.addItem("Delete");
		presetsMenu.addItem("----------");
		presetsMenu.select(2);
		if (readPresetsFromDisk())
			selectNamedPreset("Default");
		presetsMenu.addItemListener(this);
		row5.add(presetsMenu);
		centerPanel.add(row5);

		add("Center", centerPanel);

		Panel southPanel = new Panel();
		southPanel.setLayout(new GridLayout(1, 3, 10, 10));
		Button navi = new Button("Navigator");
		navi.setFont(boldFont);
		navi.setActionCommand("navigator");
		navi.addActionListener(this);
		southPanel.add(navi);
		Button apply = new Button("Apply Expression");
		apply.setFont(boldFont);
		apply.setActionCommand("apply");
		apply.addActionListener(this);
		southPanel.add(apply);
		Button close = new Button("Close");
		close.setFont(boldFont);
		close.setActionCommand("close");
		close.addActionListener(this);
		southPanel.add(close);

		add("South", southPanel);
		pack();
		GUI.center(this);

		// This following trickery is necessary to outsmart the Java compiler.
		// Since ImageJ.VERSION is final, its value would normally be inserted
		// into the code, so we need to get it dynamically via reflection.
		try {
			Class ImageJClass = Class.forName("ij.ImageJ");
			String vers = (String) ImageJClass.getField("VERSION").get(null);
			canUsePrefs = (vers.compareTo("1.32c") >= 0);
		} catch (Exception ex) { }

		if (canUsePrefs)
			userFunctions = decode(Prefs.get(USER_FUNCTIONS_PREF_KEY, ""));
//		setColorModel("RGB");
	}

	// for custom parameters
	double[] params = new double[10];

	// escape whitespace characters so it can be stored in a single line
	String encode (String value) {
		String valueConv = replace(value, "\n", "\\n");
		valueConv = replace(valueConv, "\t", "\\t");
		return replace(valueConv, "\r", "\\r");
	}

	// un-escape whitespace characters
	String decode (String value) {
		String valueConv = replace(value, "\\n", "\n");
		valueConv = replace(valueConv, "\\t", "\t");
		return replace(valueConv, "\\r", "\r");
	}

	// replaces all instances of oldStr by newStr in return Str
	String replace (String returnStr, String oldStr, String newStr) {		
		int startAt = 0;
		int oldLength = oldStr.length();
		int newLength = newStr.length();
		int pos = returnStr.indexOf(oldStr, startAt);
		while ((pos >= 0) && (returnStr.length() > 0)) {
			returnStr = (pos>0 ? returnStr.substring(0,pos) : "") + newStr + returnStr.substring(pos+oldLength);
			startAt = pos + newLength;
			pos = returnStr.indexOf(oldStr, startAt);
		}
		return returnStr;
	}

	public void run (String arg) {
		String options = Macro.getOptions();
		// if there are no options, then the plugin has been selected from the menu,
		// and the dialog should be shown
		if (options == null) {
			setVisible(true);
		} else {
			// get all the custom parameters, which are named p0 through p9
			for (int i=0; i<=9; i++)
				params[i] = new Double(Macro.getValue(options, "p"+i, "0.0")).doubleValue();

			// the Preset= option can be used to select and run a predefined expression
			String preset = Macro.getValue(options, "preset", null);
			if (preset != null) {
				selectNamedPreset(preset);
				drawImage(true);
				dispose();
			}
		}
	}

		// an item from the presets menu or the color model menu has been selected
	public void itemStateChanged (ItemEvent e) {
		ItemSelectable item = e.getItemSelectable();
		if ("presetsMenu".equals(((Component) e.getSource()).getName())) {
				// the presets menu was targeted
			int sel = ((Choice) e.getItemSelectable()).getSelectedIndex();
			if (sel == 0) {
				String newName = name.getText();
				if ((newName.length() != 0) && (! "Add".equals(newName)) && (! "Delete".equals(newName))) {
						// if item already exists, remove it
					try { presetsMenu.remove(newName); } catch (IllegalArgumentException iaex) { }
						// insert new menu item in alphabetical position
					presets.put(newName, new String[] { red.getText(), green.getText(),
								blue.getText(), min.getText(), max.getText(), (wrap.getState() ? "1" : "0"),
								tf_x.getText(), tf_y.getText(), tf_w.getText(), tf_h.getText(),
								(center.getState() ? "1" : "0"), iter.getText(),
								(maxIs0.getState() ? "1" : "0"), (newWindow.getState() ? "1" : "0"),
								(collapseStack.getState() ? "1" : "0"), (currentImage.getState() ? "1" : "0"),
								colorModel.getSelectedItem()});
					for (int i=3; i<=presetsMenu.getItemCount(); i++) {
						if (i == presetsMenu.getItemCount()) {
							presetsMenu.add(newName);
							presetsMenu.select(i);
							break;
						} else if (newName.compareTo(presetsMenu.getItem(i)) < 0) {
							presetsMenu.insert(newName, i);
							presetsMenu.select(i);
							break;
						}
					}
					writePresetsToDisk();
				}
			} else if (sel == 1) {
				String newName = name.getText();
				try {
					presetsMenu.remove(newName);
					presets.remove(newName);
				} catch (IllegalArgumentException iaex) { }
				presetsMenu.select(2);
				writePresetsToDisk();
			} else {
				String preset = presetsMenu.getItem(sel);
				selectNamedPreset(preset);
				if (Recorder.record) {
					Recorder.setCommand("ExpressionNT ");
					Recorder.recordOption("preset", preset);
		        }
			}
		} else if (item == newWindow) {
			enableCollapseStack(e.getStateChange() == ItemEvent.SELECTED);
		} else if (item == colorModel) {
			setColorModel((String) e.getItem());
		}
	}

	protected void selectNamedPreset (String itemName) {
		name.setText(itemName);
		String[] tmp = (String[]) presets.get(itemName);
		if (tmp == null) {
			IJ.error("Expression Plugin Error", "A preset named '"+itemName
					+"' does not exist in the file '"+fileName+"'.");
			return;
		}
		red.setText(tmp[0]);
		green.setText(tmp[1]);
		blue.setText(tmp[2]);
		min.setText(tmp[3]);
		max.setText(tmp[4]);
		wrap.setState("1".equals(tmp[5]));
		tf_x.setText(tmp[6]);
		tf_y.setText(tmp[7]);
		tf_w.setText(tmp[8]);
		tf_h.setText(tmp[9]);
		center.setState("1".equals(tmp[10]));
		iter.setText(tmp[11]);
		maxIs0.setState("1".equals(tmp[12]));
		newWindow.setState("1".equals(tmp[13]));
		collapseStack.setState("1".equals(tmp[14]));
		currentImage.setState("1".equals(tmp[15]));
		enableCollapseStack(newWindow.getState());
		setColorModel(tmp[16]);
	}

	protected void enableCollapseStack (boolean enable) {
		collapseStack.setEnabled(enable);
		//currentImage.setEnabled(enable);
	}

	protected void setColorModel (String which) {
		if (which.equals("RGB")) {
			l1.setText(" R ");
			l2.setText(" G ");
			green.setEnabled(true);
			l3.setText(" B ");
			blue.setEnabled(true);
			colorModel.select(which);
		} else if (which.equals("HLS")) {
			l1.setText(" H ");
			l2.setText(" L ");
			green.setEnabled(true);
			l3.setText(" S ");
			blue.setEnabled(true);
			colorModel.select(which);
		} else if (which.equals("HSV")) {
			l1.setText(" H ");
			l2.setText(" S ");
			green.setEnabled(true);
			l3.setText(" V ");
			blue.setEnabled(true);
			colorModel.select(which);
		} else if (which.equals("Gray")) {
			l1.setText(" I ");
			l2.setText("");
			green.setEnabled(false);
			l3.setText("");
			blue.setEnabled(false);
			colorModel.select(which);
		} else {
			IJ.showMessage("setColorModel which='" + which + "' ???");
		}
	}

		// the "Apply" button has been clicked
	public void actionPerformed (ActionEvent e) {
		String cmd = e.getActionCommand();
		if ("apply".equals(cmd)) {
			drawImage(true);
			if (Recorder.record)
				Recorder.saveCommand();
		} else if ("navigator".equals(cmd)) {
			if (navigator == null)
				navigator = new Navi("Navigator", this, tf_x.getText(), tf_y.getText(), tf_w.getText(), tf_h.getText());
			navigator.setVisible(true);
		} else if ("functions".equals(cmd)) {
			if (functions == null)
				functions = new Func("Functions", this);
			functions.setVisible(true);
		} else if ("close".equals(cmd)) {
			if (Recorder.record) {
				Recorder.setCommand(null);
				Recorder.saveCommand();
			}
			dispose();
		} else if ("more".equals(cmd)) {
			int maxIter;
			try {
				maxIter = Integer.parseInt(iter.getText());
			} catch (NumberFormatException nfex) {
				IJ.showMessage("maxIter is not a number: " + nfex.getMessage());
				return;
			}
			iter.setText("" + (int) (maxIter * MORE_LESS_FACTOR));
		} else if ("less".equals(cmd)) {
			int maxIter;
			try {
				maxIter = Integer.parseInt(iter.getText());
			} catch (NumberFormatException nfex) {
				IJ.showMessage("maxIter is not a number: " + nfex.getMessage());
				return;
			}
			iter.setText("" + (int) (maxIter / MORE_LESS_FACTOR));
		}
	}

	void drawImage (boolean canUndo) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null) {
			IJ.showStatus("No image");
			previousId = 0;
			return;
		}
		if (!imp.lock()) {
			previousId = 0;
			return;
		}
		ImageProcessor ip = imp.getProcessor();
		if (imp.getID() != previousId)
			ip.snapshot();
		previousId = imp.getID();

		// undo is possible if we don't open a new window;
		// if we do it's pointless, because the window can simply be closed
		if (canUndo && !newWindow.getState())
			Undo.setup(Undo.FILTER, imp);

		try {
			new Executer(red.getText(), green.getText(), blue.getText(), min.getText(),
						max.getText(), wrap.getState(), tf_x.getText(), tf_y.getText(),
						tf_w.getText(), tf_h.getText(), center.getState(), iter.getText(),
						maxIs0.getState(), newWindow.getState(), collapseStack.getState(),
						currentImage.getState(), colorModel.getSelectedItem(),
						imp, ip, params, userFunctions, this);
		
			// wait until image is unlocked before resuming execution; important for macros
			myWait();
		} catch (Exception ex) {
			imp.unlock();
		}
	}

	protected void myWait() {
		synchronized (this) {
			try { this.wait(); } catch (InterruptedException iex) { }
		}
	}

	protected void myNotify() {
		synchronized (this) {
			this.notify();
		}
	}

	boolean readPresetsFromDisk() {
		try {
			// try to find the presets file either in the plugins directory,
			// or in subdirectory called "Expression"
			dirName = Menus.getPlugInsPath();
			File f = new File(dirName, fileName);
			if (! f.exists()) {
				dirName += File.separator + subDirName;
				f = new File(dirName, fileName);
				if (! f.exists()) {
					IJ.showMessage("The file '"+fileName+"' was not found.\n"
								+ "The Presets menu will be empty.");
					return false;
				}
			}
			BufferedReader br = new BufferedReader(new FileReader(f));
			while (br.ready()) {
				String name = br.readLine();
				if ((name == null) || (name.length() == 0)) break;
				presetsMenu.add(name);
				String[] tmp = new String[NUM_PRESET_ITEMS];
				for (int j=0; j<NUM_PRESET_ITEMS; j++)
					tmp[j] = br.readLine();
				presets.put(name, tmp);
			}
			br.close();
		} catch (Exception ex) {
			IJ.showMessage(ex.getMessage());
			return false;
		}
		return true;
	}

	boolean writePresetsToDisk() {
		try {
			// dirName has been set to a suitable value by readPresetsFromDisk
			PrintWriter pw = new PrintWriter(new FileWriter(new File(dirName, fileName)));
			for (int i=3; i<presetsMenu.getItemCount(); i++) {
				String name = presetsMenu.getItem(i);
				String[] values = (String[]) presets.get(name);
				pw.println(name);
				for (int j=0; j<NUM_PRESET_ITEMS; j++)
					pw.println(values[j]);
			}
			pw.close();
		} catch (Exception ex) {
			IJ.showMessage(ex.getMessage());
			return false;
		}
		return true;
	}

	void zoom (char where) {
		double x, y, w, h;
		try {
			x = Double.valueOf(tf_x.getText()).doubleValue();
			y = Double.valueOf(tf_y.getText()).doubleValue();
			w = Double.valueOf(tf_w.getText()).doubleValue();
			h = Double.valueOf(tf_h.getText()).doubleValue();
		} catch (NumberFormatException nfex) {
			IJ.showMessage("Not a number: " + nfex.getMessage());
			return;
		}

		// where='I' is zoom in, 'O' is zoom out
		where = Character.toUpperCase(where);
		if (where == 'I') {
			if (! center.getState()) {
				tf_x.setText("" + (x + w / 4.0));
				tf_y.setText("" + (y + h / 4.0));
			}
			tf_w.setText("" + (w / 2.0));
			tf_h.setText("" + (h / 2.0));
		} else if (where == 'O') {
			if (! center.getState()) {
				tf_x.setText("" + (x - w / 2.0));
				tf_y.setText("" + (y - h / 2.0));
			}
			tf_w.setText("" + (w * 2.0));
			tf_h.setText("" + (h * 2.0));
		}
		drawImage(false);
	}

	void shift (char direction) {
		double x, y, xshift, yshift;
		try {
			x = Double.valueOf(tf_x.getText()).doubleValue();
			y = Double.valueOf(tf_y.getText()).doubleValue();
			xshift = 0.25 * Double.valueOf(tf_w.getText()).doubleValue();
			yshift = 0.25 * Double.valueOf(tf_h.getText()).doubleValue();
		} catch (NumberFormatException nfex) {
			IJ.showMessage("Not a number: " + nfex.getMessage());
			return;
		}

		switch (direction) {
			case 'N': case 'n':
				tf_y.setText("" + (center.getState() ? (y - yshift) : (y - yshift)));
				break;
			case 'S': case 's':
				tf_y.setText("" + (center.getState() ? (y + yshift) : (y + yshift)));
				break;
			case 'E': case 'e':
				tf_x.setText("" + (center.getState() ? (x + xshift) : (x + xshift)));
				break;
			case 'W': case 'w':
				tf_x.setText("" + (center.getState() ? (x - xshift) : (x - xshift)));
				break;
			default:
		}
		drawImage(false);
	}

	/** Runs expression evaluation in a separate thread. */
	class Executer extends Thread {

		private String exp1, exp2, exp3;
		private String xS, yS, wS, hS;
		private double min, max, xD, yD, wD, hD;
		private boolean wrap, center, newWindow, collapseStack, currentImage, isGray, isHLS, isHSV;
		private ImagePlus imp;
		private ImageProcessor ip;
		private ExpressionNT_ parent;
		Expr expr;

		// Yes, I know that it is abominable to pass 22 parameters to a method.

		Executer (String cmd1, String cmd2, String cmd3, String min, String max,
				  boolean wrap, String x, String y, String w, String h, boolean center,
				  String iter, boolean maxIs0, boolean newWindow, boolean collapseStack,
				  boolean currentImage, String colorModel, ImagePlus imp, ImageProcessor ip,
				  double[] params, String userFunctions, ExpressionNT_ parent) throws Exception {
			super(cmd1);
			this.exp1 = cmd1;
			this.exp2 = cmd2;
			this.exp3 = cmd3;
			this.wrap = wrap;
			this.center = center;
			this.xS = x;
			this.yS = y;
			this.wS = w;
			this.hS = h;
			this.imp = imp;
			this.ip = ip;
			this.newWindow = newWindow;
			this.collapseStack = collapseStack;
			this.currentImage = currentImage;
			this.parent = parent;
			this.isGray = ("Gray".equals(colorModel));
			this.isHLS = ("HLS".equals(colorModel));
			this.isHSV = ("HSV".equals(colorModel));

			expr = createClass(min, max, cmd1, cmd2, cmd3, x, y, w, h, params, userFunctions);
			if (expr == null) {
				throw new Exception();
			} else {
				expr.setColorModel(colorModel);
				expr.setMaxIs0(maxIs0);						// if mand exceeds max # of iterations, return 0
				expr.setMaxIter(Integer.parseInt(iter));	// max # of iterations for mand function

				// set 'w' and 'h' variables before any expression compilation, so they can be used
				expr.w = ip.getWidth();
				expr.h = ip.getHeight();
				switch (imp.getType()) {
					case ImagePlus.COLOR_256:
						if (! ip.isPseudoColorLut()) {
							IJ.showMessage("This plugin does not work with 8-bit color images.");
							throw new Exception();
						}
						// fall through
					case ImagePlus.GRAY8:
						expr.maxval = 255;
						break;
					case ImagePlus.GRAY16:
						expr.maxval = 65535;
						break;
					case ImagePlus.GRAY32:
						// for GRAY32 images, maxval is just about meaningless, and should not be used
						expr.maxval = Float.MAX_VALUE;
						break;
					case ImagePlus.COLOR_RGB:
						expr.maxval = 255;
						break;
				}

				// evaluate X, Y, W, H, Min and Max formulas
				try {
					this.min = expr.calcMin();
					this.max = expr.calcMax();
				} catch (Exception ex) {
					IJ.showMessage(" Error evaluating formula :" + ex.getMessage());
					throw ex;
				}
	//			setPriority(Math.max(getPriority()-2, MIN_PRIORITY));
				start();
			}
		}

		public void run() {
			try { runCommand(imp, ip); }
			catch (OutOfMemoryError e) {
				IJ.outOfMemory(exp1);
				if (imp!=null) imp.unlock();
			} catch (Exception ex) {
				CharArrayWriter caw = new CharArrayWriter();
				PrintWriter pw = new PrintWriter(caw);
				ex.printStackTrace(pw);
				IJ.write(caw.toString());
				IJ.showStatus("");
				if (imp!=null) imp.unlock();
			}
			// Sleep briefly so the main thread can enter myWait().
			// Just in case the filtering happens very fast.
			try { sleep(100); } catch (Exception ex) { }
			parent.myNotify();
		}

			// this method is part of the HLS -> RGB conversion
		double result (int degree, double t1, double t2, double h) {
			double hue = h + (double) degree;
			if (hue > 360.0) hue -= 360.0;
			if (hue < 60.0)
				return (t1 + (t2-t1) * hue / 60.0);
			else if (hue < 180.0)
				return t2;
			else if (hue < 240.0)
				return (t1 + (t2-t1) * (240.0-hue) / 60.0);
			else
				return t1;
		}

		public void runCommand (ImagePlus imp, ImageProcessor ip) {
			IJ.showStatus(exp1 + "...");

			ImageStack stack = imp.getStack();
			int slices = stack.getSize();

			int width = ip.getWidth();
			int height = ip.getHeight();
			Rectangle rect = ip.getRoi();
			int rWidth = rect.width;
			expr.rw = rWidth;
			int rHeight = rect.height;
			expr.rh = rHeight;
			int type = imp.getType();
			expr.setType(type);

			ImageProcessor destIP = null;
			ImageStack destStack = null;
			ImagePlus destIMP = null;
			if (newWindow) {
				switch (type) {
					case ImagePlus.GRAY8:
					case ImagePlus.COLOR_256:
						destIP = new ByteProcessor(width, height);
						break;
					case ImagePlus.GRAY16:
						destIP = new ShortProcessor(width, height);
						break;
					case ImagePlus.GRAY32:
						destIP = new FloatProcessor(width, height);
						break;
					case ImagePlus.COLOR_RGB:
						destIP = new ColorProcessor(width, height);
						break;
				}
				destStack = new ImageStack(width, height);
			}

			byte[] byteSrcArr = null, byteDestArr = null;
			short[] shortSrcArr = null, shortDestArr = null;
			int[] intSrcArr = null, intDestArr = null;
			float[] floatSrcArr = null, floatDestArr = null;

			try {
				boolean expr2empty = (exp2.length() == 0);
				boolean expr3empty = (exp3.length() == 0);

				long start = System.currentTimeMillis();
				int val, valS, r, rS, g, gS, b, bS;
				double newR=0.0, newG=0.0, newB=0.0, result=0.0, xR, yR;
				expr.setNumSlices(slices);
				ImageProcessor[] ipArray = new ImageProcessor[slices];
				for (int i=0; i<slices; i++)
					ipArray[i] = stack.getProcessor(i+1);
				expr.setIPArray(ipArray);

				Roi roi = imp.getRoi();

				for (int i=0; i<slices; i++) {
						// if the "current image" checkbox is on, skip all but the current image
					if (currentImage && (slices > 1) && ((i+1) != imp.getCurrentSlice()))
						continue;

					expr.t = i+1;
					// evaluate W, H, X, Y for this slice
					xD = expr.calcX();
					yD = expr.calcY();
					wD = expr.calcW();
					hD = expr.calcH();
					expr.wr = wD;
					expr.hr = hD;					
					if (center) {
						xD -= wD / 2.0;
						yD -= hD / 2.0;
					}

					ImageProcessor localIP = ipArray[i];
					switch (type) {
						case ImagePlus.GRAY8:
						case ImagePlus.COLOR_256:
							byteSrcArr = (byte[]) localIP.getPixels();
							if (newWindow)
								byteDestArr = new byte[localIP.getWidth() * localIP.getHeight()];
							else
								byteDestArr = byteSrcArr;
							break;
						case ImagePlus.GRAY16:
							shortSrcArr = (short[]) localIP.getPixels();
							if (newWindow)
								shortDestArr = new short[localIP.getWidth() * localIP.getHeight()];
							else
								shortDestArr = shortSrcArr;
							break;
						case ImagePlus.GRAY32:
							floatSrcArr = (float[]) localIP.getPixels();
							if (newWindow)
								floatDestArr = new float[localIP.getWidth() * localIP.getHeight()];
							else
								floatDestArr = floatSrcArr;
							break;
						case ImagePlus.COLOR_RGB:
							intSrcArr = (int[]) localIP.getPixels();
							if (newWindow)
								intDestArr = new int[localIP.getWidth() * localIP.getHeight()];
							else
								intDestArr = intSrcArr;
							break;
					}

					for (int y=rect.y; y<(rect.y+rHeight); y++) {
						yR = yD + y * hD / height;
						expr.setY(y, yR);
						IJ.showProgress((i * rWidth * rHeight + rWidth * (y-rect.y)) / (float) (slices * rWidth * rHeight));
						for (int x=rect.x; x<(rect.x+rWidth); x++) {
							if (roi!=null && !roi.contains(x, y))
								continue;

							xR = xD + x * wD / width;
							expr.setX(x, xR);
							switch (type) {
								case ImagePlus.GRAY8:
								case ImagePlus.COLOR_256:
									val = byteSrcArr[y*width+x];
									expr.i = val<0 ? 256+val : val;
									result = expr.calcR();
									if (!wrap) result = result <= max ? result >= min ? result : min : max;
									result = (result - min) * 255.0 / (max - min);
									byteDestArr[y*width+x] = (byte) Math.floor(result + 0.5);
									break;
								case ImagePlus.GRAY16:
									val = shortSrcArr[y*width+x];
									expr.i = val<0 ? 65536+val : val;
									result = expr.calcR();
									if (!wrap) result = result <= max ? result >= min ? result : min : max;
									shortDestArr[y*width+x] = (short) Math.floor(result + 0.5);
									break;
								case ImagePlus.GRAY32:
									expr.i = floatSrcArr[y*width+x];
									result = expr.calcR();
									if (!wrap) result = result <= max ? result >= min ? result : min : max;
									floatDestArr[y*width+x] = (float) result;
									break;
								case ImagePlus.COLOR_RGB:
									val = intSrcArr[y*width+x];
									r = (val & 0xff0000) >> 16;
									r = r<0 ? r+256 : r;
									g = (val & 0x00ff00) >> 8;
									g = g<0 ? g+256 : g;
									b = (val & 0x0000ff);
									b = b<0 ? b+256 : b;

									expr.r = r;
									expr.g = g;
									expr.b = b;
									expr.i = r;
									newR = expr.calcR();
									if (isGray) {
										newG = newR;
										newB = newR;
									} else {
										expr.i = g;
										if (expr2empty)
											newG = expr.calcR();
										else
											newG = expr.calcG();
										expr.i = b;
										if (expr3empty)
											newB = expr.calcR();
										else
											newB = expr.calcB();
									}
									if (isHLS) {
										// convert back to RGB
										double h=newR, l=newG, s=newB;
										double t2 = (l > 0.5) ? (l + s - l*s) : (l + l*s);
										double t1 = 2 * l - t2;
										newR = 255.0 * (s>0.0 ? result(0, t1, t2, h) : l);
										newG = 255.0 * (s>0.0 ? result(240, t1, t2, h) : l);
										newB = 255.0 * (s>0.0 ? result(120, t1, t2, h) : l);
									} else if (isHSV) {
										// convert back to RGB
										double h=newR, s=newG, v=newB;
										if (s > 0.0) {
											h = h / 60.0;
											int sixth = (int) Math.floor(h);
											double fract = h - sixth;
											double t1 = v * (1.0 - s);
											double t2 = v * (1.0 - s*fract);
											double t3 = v * (1.0 - s*(1.0 - fract));
											switch (sixth) {
												case 0: newR = v;	newG = t3;	newB = t1;	break;
												case 1: newR = t2;	newG = v;	newB = t1;	break;
												case 2: newR = t1;	newG = v;	newB = t3;	break;
												case 3: newR = t1;	newG = t2;	newB = v;	break;
												case 4: newR = t3;	newG = t1;	newB = v;	break;
												case 5: newR = v;	newG = t1;	newB = t2;	break;
											}													
										} else {
											newR = v;
											newG = v;
											newB = v;
										}
										newR *= 255.0;
										newG *= 255.0;
										newB *= 255.0;
									}
									if (!wrap) {
										newR = newR <= max ? newR >= min ? newR : min : max;
										newG = newG <= max ? newG >= min ? newG : min : max;
										newB = newB <= max ? newB >= min ? newB : min : max;
									}
									if (isHLS || isHSV) {
										// for the HLS and HSV color models, the scaling doesn't make sense,
										// because H ranges from 0..360 and L, S and V range from 0..1
										intDestArr[y*width+x] = ((((int) Math.floor(newR + 0.5)) & 0xff) << 16)
																| ((((int) Math.floor(newG + 0.5)) & 0xff) << 8)
																| (((int) Math.floor(newB + 0.5)) & 0xff);
									} else {
										intDestArr[y*width+x] = ((((int) Math.floor((newR - min) * 255.0 / (max - min) + 0.5)) & 0xff) << 16)
																| ((((int) Math.floor((newG - min) * 255.0 / (max - min) + 0.5)) & 0xff) << 8)
																| (((int) Math.floor((newB - min) * 255.0 / (max - min) + 0.5)) & 0xff);
									}
									break;
							}
						}
					}
					if (newWindow) {
						switch (type) {
							case ImagePlus.GRAY8:
							case ImagePlus.COLOR_256:
								destIP.setPixels(byteDestArr);
								break;
							case ImagePlus.GRAY16:
								destIP.setPixels(shortDestArr);
								break;
							case ImagePlus.GRAY32:
								destIP.setPixels(floatDestArr);
								break;
							case ImagePlus.COLOR_RGB:
								destIP.setPixels(intDestArr);
								break;
						}
						destStack.addSlice("", destIP);
						if (destIMP == null)
							destIMP = new ImagePlus(imp.getTitle()+" new", destStack);
						destIMP.setSlice(i+1);
						destIMP.show();
						destIMP.updateAndRepaintWindow();
						destIMP.changes = true;
					} else {
						imp.changes = true;
						imp.setSlice(i+1);
						imp.updateAndRepaintWindow();
					}
					if (collapseStack && newWindow)
						break;
				}
				float secs = (System.currentTimeMillis() - start) / 1000.0f;
				IJ.showStatus("Expression: " + secs + " seconds, " + (int) ((collapseStack&&newWindow ? 1 : slices)*rWidth*rHeight/secs) + " pixels/second");
			} catch (Throwable e) {
				e.printStackTrace();
				IJ.showMessage(e.getMessage());
			}
/*
			Roi roi = imp.getRoi();
			if (roi != null) {
				ImageProcessor mask = roi.getMask();
				if (mask != null)
					ip.reset(mask);
			}
*/
			imp.updateAndDraw();
			imp.unlock();
		}

		// returns an instance of a freshly compiled expression-evaluating class
		Expr createClass (String min, String max, String r, String g, String b,
							String x, String y, String w, String h,
							double[] params, String userFunctions) {
			Expr rtn = null;
			String exprFileName = "Expr.java";
			File exprFile = new File(dirName, exprFileName);
			if (! exprFile.exists()) {
				IJ.showMessage("The file 'Expr.java' was not found.\n"
					+ "Please read the documentation to fix the installation.");
				return null;
			}
			String javaFileName = "Expr"+countClasses+""+id+".java";
			File javaFile = new File(dirName, javaFileName);
			String classFileName = "Expr"+countClasses+""+id+".class";
			File classFile = new File(dirName, classFileName);

			if (g.length() == 0) g = "0.0";
			if (b.length() == 0) b = "0.0";

			try {
				String tmpl = "public class Expr" + countClasses + "" + id + " extends Expr {\n"
								+ "public Expr" + countClasses + "" + id + "() { super(); }\n";
				for (int i=0; i<=9; i++)
					tmpl += "double p" + i + " = " + params[i] + ";\n";
				if ((userFunctions != null) && (userFunctions.length() > 0))
					tmpl += userFunctions + "\n";
				tmpl += "public double calcR() { return (" + r + ") ; }\n"
								+ "public double calcG() { return (" + g + ") ; }\n"
								+ "public double calcB() { return (" + b + ") ; }\n"
								+ "public double calcMin() { return (" + min + ") ; }\n"
								+ "public double calcMax() { return (" + max + ") ; }\n"
								+ "public double calcX() { return (" + x + ") ; }\n"
								+ "public double calcY() { return (" + y + ") ; }\n"
								+ "public double calcW() { return (" + w + ") ; }\n"
								+ "public double calcH() { return (" + h + ") ; }\n"
							+ "}\n";

				Writer fw = new FileWriter(javaFile);
				fw.write(tmpl);
				fw.close();

// The next 2 lines can be substituted for the 2 lines following them. 
// They hardcode the Sun compiler (which may be a problem on non-Sun JDKs),
// but do not assume the accessibility of "javac" (which is a problem on MacOS 9 and maybe others).

//				Process compiler = Runtime.getRuntime().exec(new String[] {"javac", javaFile.getAbsolutePath(), exprFile.getAbsolutePath()});
//				if (compiler.waitFor() == 0) {

//				sun.tools.javac.Main main = new sun.tools.javac.Main(System.err, "javac");
//				if (main.compile(new String[] { javaFile.getAbsolutePath(), exprFile.getAbsolutePath() })) {

// The following 2 lines should be used on newer JDK (1.3 and up), which have a newer compiler.
				StringWriter sw = new StringWriter();
				if (0 == com.sun.tools.javac.Main.compile(new String[] { javaFile.getAbsolutePath(), exprFile.getAbsolutePath() },
															new PrintWriter(sw))) {
					Class clss = Class.forName("Expr"+countClasses+""+id);
					rtn = (Expr) clss.newInstance();
					classFile.delete();

					countClasses++;
				} else {
					IJ.showMessage("There was a problem interpreting the expression", sw.toString());
				}
			} catch (Exception ex) {
				IJ.showMessage(ex.getMessage());
			}
			javaFile.delete();
			return rtn;				
		}
	}

	class Navi extends Frame {
		Panel myPanel;
		ExpressionNT_ parent;
		String x, y, w, h;

		Navi (String title, ExpressionNT_ _parent, String _x, String _y, String _w, String _h) {
			super(title);
			parent = _parent;
			x = _x;
			y = _y;
			w = _w;
			h = _h;
			setLayout(new BorderLayout());
			addWindowListener(new WindowAdapter() {
				public void windowClosing (WindowEvent e) {
					restoreFields();
					dispose();
				}
			});

			myPanel = new Panel();
			myPanel.setLayout(new GridLayout(4, 2, 5, 5));
			myPanel.setBackground(Color.lightGray);
			Button north = new Button("Up");
			north.setFont(boldFont);
			north.setBackground(Color.white);
			north.addActionListener(new AL(2));
			myPanel.add(north);
			Button south = new Button("Down");
			south.setFont(boldFont);
			south.setBackground(Color.white);
			south.addActionListener(new AL(3));
			myPanel.add(south);
			Button west = new Button("Left");
			west.setFont(boldFont);
			west.setBackground(Color.white);
			west.addActionListener(new AL(1));
			myPanel.add(west);
			Button east = new Button("Right");
			east.setFont(boldFont);
			east.setBackground(Color.white);
			east.addActionListener(new AL(4));
			myPanel.add(east);
			Button zoom_in = new Button("Zoom in");
			zoom_in.setFont(boldFont);
			zoom_in.setBackground(Color.white);
			zoom_in.addActionListener(new AL(5));
			myPanel.add(zoom_in);
			Button zoom_out = new Button("Zoom out");
			zoom_out.setFont(boldFont);
			zoom_out.setBackground(Color.white);
			zoom_out.addActionListener(new AL(6));
			myPanel.add(zoom_out);
			Button ok = new Button("OK");
			ok.setFont(boldFont);
			ok.setBackground(Color.white);
			ok.addActionListener(new AL(7));
			myPanel.add(ok);
			Button cxl = new Button("Cancel");
			cxl.setFont(boldFont);
			cxl.setBackground(Color.white);
			cxl.addActionListener(new AL(8));
			myPanel.add(cxl);

			add("Center", myPanel);
			pack();
			ij.gui.GUI.center(this);
			setVisible(true);
		}

		void restoreFields() {
			parent.tf_x.setText(x);
			parent.tf_y.setText(y);
			parent.tf_w.setText(w);
			parent.tf_h.setText(h);
		}

			// AL = ActionListener
		private class AL implements ActionListener {
			int which;

			AL (int _which) {
				which = _which;
			}

			public void actionPerformed (ActionEvent e) {
				switch (which) {
					case 1: parent.shift('W'); break;
					case 2: parent.shift('N'); break;
					case 3: parent.shift('S'); break;
					case 4: parent.shift('E'); break;
					case 5: parent.zoom('I'); break;
					case 6: parent.zoom('O'); break;
					case 7: dispose(); break;
					case 8: restoreFields(); dispose(); break;
					default:
				}
			}
		}
	}

	class Func extends Frame {
		ExpressionNT_ parent;
		TextArea ta;

		Func (String title, ExpressionNT_ _parent) {
			super(title);
			parent = _parent;
			ta = new TextArea(parent.userFunctions, 20, 40);
			setLayout(new BorderLayout());
			addWindowListener(new WindowAdapter() {
				public void windowClosing (WindowEvent e) {
					ta.setText(parent.userFunctions);
					if (canUsePrefs)
						Prefs.set(USER_FUNCTIONS_PREF_KEY, encode(parent.userFunctions));
					setVisible(false);
				}
			});

			Panel myPanel = new Panel();
			myPanel.setBackground(Color.lightGray);
			Button ok = new Button("OK");
			ok.setFont(boldFont);
			ok.setBackground(Color.white);
			ok.addActionListener(new ActionListener() {
				public void actionPerformed (ActionEvent e) {
					String functions = ta.getText();
					parent.userFunctions = functions;
					if (canUsePrefs)
						Prefs.set(USER_FUNCTIONS_PREF_KEY, encode(functions));
					setVisible(false);
				}
			});
			myPanel.add(ok);
			Button cxl = new Button("Cancel");
			cxl.setFont(boldFont);
			cxl.setBackground(Color.white);
			cxl.addActionListener(new ActionListener() {
				public void actionPerformed (ActionEvent e) {
					ta.setText(parent.userFunctions);
					if (canUsePrefs)
						Prefs.set(USER_FUNCTIONS_PREF_KEY, encode(parent.userFunctions));
					setVisible(false);
				}
			});
			myPanel.add(cxl);
			add("South", myPanel);

			add("Center", ta);
			pack();
			ij.gui.GUI.center(this);
			setVisible(true);
		}
	}
}
