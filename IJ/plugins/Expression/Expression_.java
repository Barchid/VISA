
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.Enumeration;
import java.util.Hashtable;

import ij.*;
import ij.plugin.*;
import ij.plugin.frame.Recorder;
import ij.process.*;
import ij.gui.*;

/**
 * Expression is a plugin filter for ImageJ that applies
 * a user-specified mathematical expression to each pixel.
 *
 * Available variables are:
 * rw (roi width), rh (roi height),
 * x (position in row adjusted for scaling), y (position in column adjusted for scaling),
 * xi (position in row in pixels), yi (position in column in pixels),
 * i (value of this pixel), w (image width in pixels), h (image height in pixels),
 * wr (image width adjusted for scaling), hr (image height adjusted for scaling),
 * pi, e, d (distance from center of image),
 * a (angle, measured from center of image; between 0 and 2*pi)
 * rnd (random number 0 &lt;= r &lt; 1)
 * n - for working with stacks, the number of slices
 * t - for working with stacks, varies from 0 (first slice) to n-1 (last slice)
 * r, g, b - red, green and blue values of the current pixel
 * maxval - maximum pixel value for this kind of image
 *         (255 for 8bit, 65536 for 16bit, 1 for 32bit)
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
 */

public class Expression_ extends ij.plugin.frame.PlugInFrame
	implements PlugIn, ActionListener, ItemListener {

	final static int NUM_PRESET_ITEMS = 12;
	final static double MORE_LESS_FACTOR = 1.5;

	String fileName="presets.txt", dirName=null, subDirName="Expression";

	TextField red, blue, green, name, min, max, tf_x, tf_y, tf_w, tf_h;
	Checkbox wrap, center, newWindow;
	Choice presetsMenu;
	int previousId;
	Hashtable presets = new Hashtable();

	Font plainFont = new Font("Helvetica", Font.PLAIN, 12);
	Font boldFont = new Font("Helvetica", Font.BOLD, 12);

	public Expression_() {
		super("Expression");
		setLayout(new BorderLayout(10, 10));
		Panel centerPanel = new Panel();
		centerPanel.setLayout(new GridLayout(7, 1, 0, 10));

		Panel row1 = new Panel();
		red = new TextField(40);
		red.setFont(plainFont);
		Label l1 = new Label(" R ");
		l1.setFont(boldFont);
		row1.add(l1);
		row1.add(red);
		centerPanel.add(row1);

		Panel row2 = new Panel();
		green = new TextField(40);
		green.setFont(plainFont);
		Label l2 = new Label(" G ");
		l2.setFont(boldFont);
		row2.add(l2);
		row2.add(green);
		centerPanel.add(row2);

		Panel row3 = new Panel();
		blue = new TextField(40);
		blue.setFont(plainFont);
		Label l3 = new Label(" B ");
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
		row6.add(l62);
		row6.add(tf_y);
		center = new Checkbox("Center");
		center.setFont(boldFont);
		row6.add(new Label("     "));
		row6.add(center);
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
		newWindow = new Checkbox("New window");
		newWindow.setFont(boldFont);
		row7.add(newWindow);

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
		row4.add(new Label("     "));
		row4.add(wrap);
		centerPanel.add(row4);

		Panel row5 = new Panel();
		Label l7 = new Label("Presets");
		l7.setFont(boldFont);
		row5.add(l7);
		name = new TextField(20);
		name.setFont(plainFont);
		row5.add(name);
		presetsMenu = new Choice();
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
	}

	// for custom parameters
	double[] params = new double[10];

	public void run (String arg) {
		String options = Macro.getOptions();
		// if there are no there, then the plugin has been selected from the menu,
		// an dthe dialog should be shown
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
				drawImage();
				dispose();
			}
		}
	}

		// an item from the "Presets" menu has been selected
	public void itemStateChanged (ItemEvent e) {
			// the presets menu was targetted
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
							(center.getState() ? "1" : "0"), (newWindow.getState() ? "1" : "0") } );
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
				Recorder.setCommand("Expression ");
				Recorder.recordOption("preset", preset);
			}

			name.setText(presetsMenu.getItem(sel));
			String[] tmp = (String[]) presets.get(name.getText());
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
			newWindow.setState("1".equals(tmp[11]));
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
		newWindow.setState("1".equals(tmp[11]));
	}

		// the "Apply" button has been clicked
	public void actionPerformed (ActionEvent e) {
		String cmd = e.getActionCommand();
		if ("apply".equals(cmd)) {
			drawImage();
			if (Recorder.record)
				Recorder.saveCommand();
		} else if ("close".equals(cmd)) {
			if (Recorder.record) {
				Recorder.setCommand(null);
				Recorder.saveCommand();
			}
			dispose();
		}
	}

	void drawImage() {
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
		if (! newWindow.getState())
			Undo.setup(Undo.FILTER, imp);

		try {
			new Executer(red.getText(), green.getText(), blue.getText(), min.getText(),
						max.getText(), wrap.getState(), tf_x.getText(), tf_y.getText(),
						tf_w.getText(), tf_h.getText(), center.getState(), newWindow.getState(),
						imp, ip, params, this);

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
			System.err.println(ex.getMessage());
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
			System.err.println(ex.getMessage());
			return false;
		}
		return true;
	}

	/** Runs expression evaluation in a separate thread. */
	class Executer extends Thread {

		private String exp1, exp2, exp3;
		private String xS, yS, wS, hS;
		private double min, max, xD, yD, wD, hD;
		private boolean wrap, center, newWindow;
		private ImagePlus imp;
		private ImageProcessor ip;
		private Expression_ parent;
		Expr expr;

		Executer (String cmd1, String cmd2, String cmd3, String min, String max,
				  boolean wrap, String x, String y, String w, String h, boolean center,
				  boolean newWindow, ImagePlus imp, ImageProcessor ip, double[] params,
				  Expression_ parent) throws Exception {
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
			this.parent = parent;

			expr = new Expr();

			// set 'w' and 'h' variables before any expression compilation, so they can be used
			expr.setW(ip.getWidth());
			expr.setH(ip.getHeight());
			expr.setParams(params);

			switch (imp.getType()) {
				case ImagePlus.COLOR_256:
				if (! ip.isPseudoColorLut()) {
					IJ.showStatus("This plugin does not work with 8-bit color images.");
					throw new Exception();
				}
				// fall through
				case ImagePlus.GRAY8:
					expr.setMAX(255);
					break;
				case ImagePlus.GRAY16:
					expr.setMAX(65535);
					break;
				case ImagePlus.GRAY32:
					// for GRAY32 images, maxval is just about meaningless, and should not be used
					expr.setMAX(Float.MAX_VALUE);
					break;
				case ImagePlus.COLOR_RGB:
					expr.setMAX(255);
					break;
			}

			// evaluate X, Y, W, H, Min and Max formulas
			try {
				this.min = expr.eval(min, 0, 0, 0, 0, 0);
				this.max = expr.eval(max, 0, 0, 0, 0, 0);
			} catch (Exception ex) {
				IJ.showMessage(" Error evaluating formula :" + ex.getMessage());
				throw ex;
			}
//			setPriority(Math.max(getPriority()-2, MIN_PRIORITY));
			start();
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
			expr.setRW(rWidth);
			int rHeight = rect.height;
			expr.setRH(rHeight);
			int type = imp.getType();

			ImageProcessor destIP = null;
			ImageStack destStack = null;
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

				Roi roi = imp.getRoi();

				long start = System.currentTimeMillis();
				int val, r, g, b;
				double newR=0.0, newG=0.0, newB=0.0, result=0.0, xR, yR;
				expr.setN(slices);
				for (int i=0; i<slices; i++) {
					expr.setT(i);
					// evaluate W, H, X, Y for this slice
					xD = expr.eval(xS, 0, 0, 0, 0, 0);
					yD = expr.eval(yS, 0, 0, 0, 0, 0);
					wD = expr.eval(wS, 0, 0, 0, 0, 0);
					hD = expr.eval(hS, 0, 0, 0, 0, 0);
					expr.setActualW(wD);
					expr.setActualH(hD);					
					if (center) {
						xD -= wD / 2.0;
						yD -= hD / 2.0;
					}

					ImageProcessor localIP = stack.getProcessor(i+1);
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
						IJ.showProgress((i * rWidth * rHeight + rWidth * (y-rect.y)) / (float) (slices * rWidth * rHeight));
						for (int x=rect.x; x<(rect.x+rWidth); x++) {
							if (roi!=null && !roi.contains(x, y))
								continue;

							xR = xD + x * wD / width;
							switch (type) {
								case ImagePlus.GRAY8:
								case ImagePlus.COLOR_256:
									val = byteSrcArr[y*width+x];
									result = expr.eval(exp1, xR, yR, x, y, val<0 ? 256+val : val);
									if (!wrap) result = result <= max ? result >= min ? result : min : max;
									result = (result - min) * 255.0 / (max - min);
									byteDestArr[y*width+x] = (byte) Math.floor(result + 0.5);
									break;
								case ImagePlus.GRAY16:
									val = shortSrcArr[y*width+x];
									result = expr.eval(exp1, xR, yR, x, y, val<0 ? 65536+val : val);
									if (!wrap) result = result <= max ? result >= min ? result : min : max;
									result = (result - min) * 65535.0 / (max - min);
									shortDestArr[y*width+x] = (short) Math.floor(result + 0.5);
									break;
								case ImagePlus.GRAY32:
									result = expr.eval(exp1, xR, yR, x, y, floatSrcArr[y*width+x]);
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

									newR = expr.eval(exp1, xR, yR, x, y, r, r, g, b);
									if (expr2empty)
										newG = expr.eval(exp1, xR, yR, x, y, g, r, g, b);
									else
										newG = expr.eval(exp2, xR, yR, x, y, g, r, g, b);
									if (expr3empty)
										newB = expr.eval(exp1, xR, yR, x, y, b, r, g, b);
									else
										newB = expr.eval(exp3, xR, yR, x, y, b, r, g, b);
									if (!wrap) {
										newR = newR <= max ? newR >= min ? newR : min : max;
										newG = newG <= max ? newG >= min ? newG : min : max;
										newB = newB <= max ? newB >= min ? newB : min : max;
									}
									intDestArr[y*width+x] = ((((int) Math.floor((newR - min) * 255.0 / (max - min) + 0.5)) & 0xff) << 16)
															| ((((int) Math.floor((newG - min) * 255.0 / (max - min) + 0.5)) & 0xff) << 8)
															| (((int) Math.floor((newB - min) * 255.0 / (max - min) + 0.5)) & 0xff);
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
						ImagePlus destIMP = new ImagePlus(imp.getTitle(), destStack);
						destIMP.setSlice(i+1);
						destIMP.show();
						destIMP.updateAndRepaintWindow();
						destIMP.changes = true;
					} else {
						imp.changes = true;
					}
					imp.setSlice(i+1);
					imp.updateAndRepaintWindow();
				}
				float secs = (System.currentTimeMillis() - start) / 1000.0f;
				IJ.showStatus("Expression: " + secs + " seconds, " + (int) (slices*rWidth*rHeight/secs) + " pixels/second");
			} catch (Throwable e) {
				e.printStackTrace();
				System.err.println(e.getMessage());
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
	}

	class Expr {

	/*  Simple recursive descent expression evaluator.
			written in C by Guy Laden

			Changes by Ulf Dittmer:
			May 1997: ported to Oberon and small enhancements
			November 1997: ported to Java
			May 1999: enhanced to accept an arbitrary number of variables
			May 1999: added if(,,) and % functions
			June 2001: added min(,), max(,), tan(), asin(), acos() and ? (random number)
			July 2001: added "n" and "t" variables for working with stacks
			May 2003: added abs(), ceil(), floor() and round() functions,
						and the extended options from ExpressionJEL; added AND, OR and NOT,
						and allowed AND/OR/MIN/MAX to accept an arbitrary number of parameters

			This is a special version for ImageJ.

		Functions:
			+  -  *  /  ^  %  sqrt()  sin()  cos()  arctan()  exp()  ln()  if(,,)
			min(,)  max(,)  abs()  floor()  ceil()  round()

			if works somewhat like an if/then/else, or rather like the "a ? b : c"
			construct in C and Java. The 1st argument should evaluate to true or
			false (the numerical comparison operators can be used); if it's true,
			if evaluates to the 2nd argument, otherwise to the 3rd.

		Variables can be upper- or lowercase; they override functions of the same name.
		"PI" and "E" are predefined to be the corresponding mathematical constants.

		Here's an EBNF of the expressions this class understands:

	  EBNF:
		expr:	 ['-' | '+' ] term {'+' term | '-' term}.
		term:	 factor {'*' factor | '/' factor | '%' factor}.
		factor:	 primary {'^' factor}.
		compExpr:	 ['-' | '+' ] term  compOper term
		compOper:	'=' | '<' | '>' | '<>' | '<=' | '>='
		primary: number | '(' expr ')' | 'sin' '(' expr ')' | 'cos' '(' expr ')' | 'tan' '(' expr ')' |
				'exp' '(' expr ')' | 'ln' '(' expr ')' | 'atan' '(' expr ')'| 'acos' '(' expr ')' |
				'sqrt' '(' expr ')' | 'if' '(' compExpr ',' expr ',' expr ')' | 'asin' '(' expr ')'
				'min' '(' expr ',' expr ')' | 'max' '(' expr ',' expr ')' | variable | '?'
		variable:	[a-zA-Z]+
		number:	intnumber | realnumber.
		realnumber:	[ intnumber ] . [ intnumber ].
		intnumber:	digit {digit}.
		digit:	0 | 1 | 2 | 3 | ... | 9
	*/

		final double ln10 = Math.log(10);

		final int
			MULT = 1,		DIVIDED = 2,	PLUS = 3,		MINUS = 4,
			LBRAK = 5,		RBRAK = 6,		POW = 7,		NUMBER = 8,
			SIN = 9,		COS = 10,		EXP = 11,		LN = 12,
			ATAN = 13,		SQRT = 14,		LAST = 15,		COND = 16,
			COMMA = 17,		LT = 18,		GT = 19,		EQ = 20,
			NE = 21,		LE = 22,		GE = 23,		MOD = 24,
			MIN = 25,		MAX = 26,		ASIN = 27,		ACOS = 28,
			TAN = 29,		CEIL = 30,		FLOOR = 31,		ABS = 32,
			ROUND = 33,		LOG = 34,		AND = 35,		OR = 36,		NOT = 37;

		char c;			/* last character read from input */
		String str,
				ident;	/* scanned identifiers are stored here */
		double num,		/* scanned numbers are stored here */
				value, r, g, b, x, y, wr, hr, time, params[], max;
		int w, h, rw, rh, n=1, t=1, xi, yi;
		int i,
			token;		/* invariant throughout: token is the last token scanned */

		java.util.Random rnd = new java.util.Random(System.currentTimeMillis());

		// these are constant for all pixels in all images
		public void setActualH (double hr) { this.hr = hr; }
		public void setActualW (double wr) { this.wr = wr; }
		public void setW (int w) { this.w = w; }
		public void setH (int h) { this.h = h; }
		public void setRW (int rw) { this.rw = rw; }
		public void setRH (int rh) { this.rh = rh; }
		public void setN (int n) { this.n = n; }
		public void setMAX (double max) { this.max = max; }
		public void setParams (double[] val) { this.params = val; }

		// these are constant for all pixels in one image
		public void setT (int t) { this.t = t; }

	/* Scanner */
		void getIdent() {
			ident = "";
			do {
				ident += String.valueOf(c);
				if (++i < str.length()) c = str.charAt(i);
			} while ((i < str.length()) && (Character.isLetter(c) || Character.isDigit(c)));
		}

		int getCompOp() {
			char c1 = c, c2 = ' ';
			if (++i < str.length()) c = str.charAt(i);
			c2 = c;
			if ((c=='=') || (c=='<') || (c=='>'))
				if (++i < str.length()) c = str.charAt(i);

			if ((c1 == '<') && (c2 == '=')) return LE;
			else if ((c1 == '>') && (c2 == '=')) return GE;
			else if (c1 == '=') return EQ;
			else if ((c1 == '<') && (c2 == '>')) return NE;
			else if (c1 == '<') return LT;
			else if (c1 == '>') return GT;
			else throw new RuntimeException("unknown operator: " + c1 + c2);
		}

		double getNum() throws NumberFormatException {
			StringBuffer s = new StringBuffer("");

			do {
				s.append(String.valueOf(c));
				if (++i < str.length()) c = str.charAt(i);
			} while ((i< str.length()) && (Character.isDigit(c) || (c == '.')));
			return Double.valueOf(s.toString()).doubleValue();
		}

		int getTok() {
			/* pre: c is the last character read */
			int t;

			while ((i < str.length()) && (c == ' ')) {
				if (++i < str.length()) c = str.charAt(i);
			}
			if (i == str.length())
				t = LAST;
			else if (Character.isDigit(c) || (c == '.')) {
				num = getNum();	t = NUMBER;
			} else if ((c == '<') || (c == '=') || (c == '>')) {
				t = getCompOp();
			} else if (Character.isLetter(Character.toUpperCase(c))) {
				getIdent();
				if (ident.equalsIgnoreCase("SIN"))
					t = SIN;
				else if (ident.equalsIgnoreCase("COS"))
					t = COS;
				else if (ident.equalsIgnoreCase("TAN"))
					t = TAN;
				else if (ident.equalsIgnoreCase("EXP"))
					t = EXP;
				else if (ident.equalsIgnoreCase("LN"))
					t = LN;
				else if (ident.equalsIgnoreCase("LOG"))
					t = LOG;
				else if (ident.equalsIgnoreCase("ASIN"))
					t = ASIN;
				else if (ident.equalsIgnoreCase("ACOS"))
					t = ACOS;
				else if (ident.equalsIgnoreCase("ATAN"))
					t = ATAN;
				else if (ident.equalsIgnoreCase("SQRT"))
					t = SQRT;
				else if (ident.equalsIgnoreCase("IF"))
					t = COND;
				else if (ident.equalsIgnoreCase("MIN"))
					t = MIN;
				else if (ident.equalsIgnoreCase("MAX"))
					t = MAX;
				else if (ident.equalsIgnoreCase("CEIL"))
					t = CEIL;
				else if (ident.equalsIgnoreCase("FLOOR"))
					t = FLOOR;
				else if (ident.equalsIgnoreCase("ROUND"))
					t = ROUND;
				else if (ident.equalsIgnoreCase("ABS"))
					t = ABS;
				else if (ident.equalsIgnoreCase("AND"))
					t = AND;
				else if (ident.equalsIgnoreCase("OR"))
					t = OR;
				else if (ident.equalsIgnoreCase("NOT"))
					t = NOT;
				else if (ident.equalsIgnoreCase("X")) {
					num = x;	t = NUMBER;
				} else if (ident.equalsIgnoreCase("Y")) {
					num = y;	t = NUMBER;
				} else if (ident.equalsIgnoreCase("XI")) {
					num = xi;	t = NUMBER;
				} else if (ident.equalsIgnoreCase("YI")) {
					num = yi;	t = NUMBER;
				} else if (ident.equalsIgnoreCase("I")) {
					num = value;	t = NUMBER;
				} else if (ident.equalsIgnoreCase("R")) {
					num = r;	t = NUMBER;
				} else if (ident.equalsIgnoreCase("G")) {
					num = g;	t = NUMBER;
				} else if (ident.equalsIgnoreCase("B")) {
					num = b;	t = NUMBER;
				} else if (ident.equalsIgnoreCase("N")) {
					num = n;	t = NUMBER;
				} else if (ident.equalsIgnoreCase("T")) {
					num = this.t;		t = NUMBER;
				} else if (ident.equalsIgnoreCase("W")) {
					num = w;	t = NUMBER;
				} else if (ident.equalsIgnoreCase("H")) {
					num = h;	t = NUMBER;
				} else if (ident.equalsIgnoreCase("WR")) {
					num = wr;	t = NUMBER;
				} else if (ident.equalsIgnoreCase("HR")) {
					num = hr;	t = NUMBER;
				} else if (ident.equalsIgnoreCase("RW")) {
					num = rw;	t = NUMBER;
				} else if (ident.equalsIgnoreCase("RH")) {
					num = rh;	t = NUMBER;
				} else if (ident.equalsIgnoreCase("MAXVAL")) {
					num = max;	t = NUMBER;
				} else if (ident.equalsIgnoreCase("PI")) {
					num = Math.PI;	t = NUMBER;
				} else if (ident.equalsIgnoreCase("E")) {
					num = Math.E;	t = NUMBER;
				// all identifiers starting with P need to be dealt with before this line
				} else if ((ident.charAt(0) == 'P') || (ident.charAt(0) == 'p')) {
					num = params[((int) ident.charAt(1)) - 48];
					t = NUMBER;
				} else if (ident.equalsIgnoreCase("D")) {
					double dx = x - Math.floor(wr/2);
					double dy = y - Math.floor(hr/2);
					num = Math.sqrt(dx*dx + dy*dy);
					t = NUMBER;
				} else if (ident.equalsIgnoreCase("A")) {
					num = Math.atan2(y - Math.floor(hr/2), x - Math.floor(wr/2));
					if (num < 0)
						num += 2*Math.PI;
					t = NUMBER;
				} else {
					throw new RuntimeException("unknown ident: " + ident);
					/* this exception can be ignored; simply replace it by the following lines
						if (++i < str.length()) c = str.charAt(i);
						t = getTok();
					*/
				}
			} else
				switch (c) {
					case '*': t = MULT;		if (++i < str.length()) c = str.charAt(i); break;
					case '/': t = DIVIDED;	if (++i < str.length()) c = str.charAt(i); break;
					case '%': t = MOD;		if (++i < str.length()) c = str.charAt(i); break;
					case '+': t = PLUS;		if (++i < str.length()) c = str.charAt(i); break;
					case '-': t = MINUS;	if (++i < str.length()) c = str.charAt(i); break;
					case '(': t = LBRAK;	if (++i < str.length()) c = str.charAt(i); break;
					case ')': t = RBRAK;	if (++i < str.length()) c = str.charAt(i); break;
					case ',': t = COMMA;	if (++i < str.length()) c = str.charAt(i); break;
					case '^': t = POW;		if (++i < str.length()) c = str.charAt(i); break;
					case '?': t = NUMBER;	num = rnd.nextDouble();		if (++i < str.length()) c = str.charAt(i); break;
					default:
						throw new RuntimeException("unknown char: " + c);
				/* this exception can be ignored; simply replace it by the following line
						if (++i < str.length()) c = str.charAt(i); t = getTok(); break;
				*/
				}
			return t;
		}

		/* Parser */

		void eat (int expectedToken) {
			if (token == expectedToken)
				token = getTok();
			else
				throw new RuntimeException("expected: " + expectedToken + " but got: " + token);
		}

		double primary() {
			double p=0.0;
			double first=0.0, second=0.0, cond=0.0;

			switch (token) {
				case NUMBER:	p = num; token = getTok(); break;
				case SIN:		token = getTok();	eat(LBRAK);		p = Math.sin(expr());	eat(RBRAK); break;
				case COS:		token = getTok();	eat(LBRAK);		p = Math.cos(expr());	eat(RBRAK); break;
				case TAN:		token = getTok();	eat(LBRAK);		p = Math.tan(expr());	eat(RBRAK); break;
				case EXP:		token = getTok();	eat(LBRAK);		p = Math.exp(expr());	eat(RBRAK); break;
				case LN:		token = getTok();	eat(LBRAK);		p = Math.log(expr());	eat(RBRAK); break;
				case LOG:		token = getTok();	eat(LBRAK);		p = Math.log(expr()) / ln10;	eat(RBRAK); break;
				case ASIN:		token = getTok();	eat(LBRAK);		p = Math.asin(expr());	eat(RBRAK); break;
				case ACOS:		token = getTok();	eat(LBRAK);		p = Math.acos(expr());	eat(RBRAK); break;
				case ATAN:		token = getTok();	eat(LBRAK);		p = Math.atan(expr());	eat(RBRAK); break;
				case SQRT:		token = getTok();	eat(LBRAK);		p = Math.sqrt(expr());	eat(RBRAK); break;
				case CEIL:		token = getTok();	eat(LBRAK);		p = Math.ceil(expr());	eat(RBRAK); break;
				case FLOOR:		token = getTok();	eat(LBRAK);		p = Math.floor(expr());	eat(RBRAK); break;
				case ROUND:		token = getTok();	eat(LBRAK);		p = Math.round(expr());	eat(RBRAK); break;
				case ABS:		token = getTok();	eat(LBRAK);		p = Math.abs(expr());	eat(RBRAK); break;
				case COND:		token = getTok();	eat(LBRAK);		cond = compExpr();		eat(COMMA);
								first = expr();		eat(COMMA);		second = expr();		eat(RBRAK);
								p = (notFalse(cond) ? first : second);						break;
				case NOT:		token = getTok();	eat(LBRAK);		p = (notFalse(compExpr()) ? 0.0 : 1.0);
								eat(RBRAK);			break;
				case AND:		token = getTok();	eat(LBRAK);		first = (notFalse(compExpr()) ? 1.0 : 0.0);
								while (token == COMMA) {
									eat(COMMA);		if (! notFalse(compExpr())) first = 0.0;
								}
								eat(RBRAK);			p = (notFalse(first) ? 1.0 : 0.0);		break;
				case OR:		token = getTok();	eat(LBRAK);		first = (notFalse(compExpr()) ? 1.0 : 0.0);
								while (token == COMMA) {
									eat(COMMA);		if (notFalse(compExpr())) first = 1.0;
								}
								eat(RBRAK);			p = (notFalse(first) ? 1.0 : 0.0);		break;
				case MIN:		token = getTok();	eat(LBRAK);		first = second = expr();
								while (token == COMMA) {
									eat(COMMA); second = expr();	if (second < first) first = second;
								}
								eat(RBRAK);			p = (first < second ? first : second);	break;
				case MAX:		token = getTok();	eat(LBRAK);		first = second = expr();
								while (token == COMMA) {
									eat(COMMA); second = expr();	if (second > first) first = second;
								}
								eat(RBRAK);			p = (first > second ? first : second);	break;
				case LBRAK:		token = getTok();	p = expr();		eat(RBRAK); break;
			default:
				throw new RuntimeException("unexpected token: " + token);
			}
			return p;
		}

		boolean notFalse (double arg) {
			return Math.abs(arg) >= 1.0e-6;
		}

		double factor() {
			double f = primary();
			while (token == POW) {
				token = getTok();
				f = Math.exp(factor() * Math.log(f));
			}
			return f;
		}

		double term() {
			double f = factor();
			while ((token == MULT) || (token == DIVIDED) || (token == MOD)) {
				if (token == MULT) {
					token = getTok();
					f *= factor();
				} else if (token == DIVIDED) {
					token = getTok();
					f /= factor();
				} else {
					token = getTok();
					f %= factor();
				}
			}
			return f;
		}

		double expr() {
			double t;

			if (token == PLUS) {
				token = getTok();
				t = term();
			} else if (token == MINUS) {
				token = getTok();
				t = -term();
			} else
				t = term();

			while ((token == PLUS) || (token == MINUS))
				if (token == PLUS) {
					token = getTok();
					t += term();
				} else {
					token = getTok();
					t -= term();
				}
			return t;
		}

		double compExpr () {
			double t1 = expr();
			int compToken = token;	// remember comparison operator

			switch (compToken) {
				case LE: case LT: case EQ: case NE: case GE: case GT:
					break;
				default:
					return t1;
			}

			token = getTok();
			double t2 = expr();
			switch (compToken) {
				case LE: return ((t1 <= t2) ? 1.0 : 0.0);
				case LT: return ((t1 < t2) ? 1.0 : 0.0);
				case EQ: return ((t1 == t2) ? 1.0 : 0.0);
				case NE: return ((t1 != t2) ? 1.0 : 0.0);
				case GE: return ((t1 >= t2) ? 1.0 : 0.0);
				case GT: return ((t1 > t2) ? 1.0 : 0.0);
				default: return t1;
					// default should not happen
			}
		}

		public Expr() { }

		public double eval (String exp, double xR, double yR, int xi, int yi, double val) {
			this.str = exp;
			this.ident = "";
			this.x = xR;
			this.y = yR;
			this.xi = xi;
			this.yi = yi;
			this.value = val;
			this.i = -1;
			this.c = ' ';
			this.token = getTok();
			if (this.token != LAST)
				try {
					return this.expr();
				} catch (Exception ex) {
					IJ.write("Expression could not be evaluated: " + ex.getMessage());
				}
			return 0.0;
		}

		public double eval (String exp, double xR, double yR, int xi, int yi, double val, double r, double g, double b) {
			this.r = r;
			this.g = g;
			this.b = b;
			return eval(exp, xR, yR, xi, yi, val);
		}
	}
}
