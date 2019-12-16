import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.frame.*;
import ij.process.ImageProcessor.*;
import ij.plugin.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import java.lang.Math.*;
import java.lang.Object.*;
import java.lang.String.*;
import java.awt.TextArea;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.Window.*;

public class FCM_Visa_ implements PlugIn {

	class Vec {
		int[] data = new int[3]; // *pointeur sur les composantes*/
	}

	////////////////////////////////////////////////////
	Random r = new Random();

	public int rand(int min, int max) {
		return min + (int) (r.nextDouble() * (max - min));
	}

////////////////////////////////////////////////////////////////////////////////////////////
	public void run(String arg) {
		// LES PARAMETRES

		ImageProcessor ip;
		ImageProcessor ipseg;
		ImageProcessor ipJ;
		ImagePlus imp;
		ImagePlus impseg;
		ImagePlus impJ;
		IJ.showMessage("Algorithme FCM", "If ready, Press OK");
		ImagePlus cw;

		imp = WindowManager.getCurrentImage();
		ip = imp.getProcessor();
		
		int width = ip.getWidth();
		int height = ip.getHeight();

		impseg = NewImage.createImage("Image segmentée par FCM", width, height, 1, 24, 0);
		ipseg = impseg.getProcessor();
		impseg.show();

		int nbclasses, nbpixels, iter;
		double stab, seuil, valeur_seuil;
		int i, j, k, l, imax, jmax, kmax;

//		String demande = JOptionPane.showInputDialog("Nombre de classes : ");
//		nbclasses = Integer.parseInt(demande);
		nbclasses = 6;
		nbpixels = width * height; // taille de l'image en pixels

		String demande = JOptionPane.showInputDialog("Valeur de m : ");
		double m = Double.parseDouble(demande);

//		demande = JOptionPane.showInputDialog("Nombre itération max : ");
		int itermax = 10000;

		demande = JOptionPane.showInputDialog("Valeur du seuil de stabilité : ");
		valeur_seuil = Double.parseDouble(demande);

		demande = JOptionPane.showInputDialog("Randomisation améliorée ? ");
		int improveRandomization = Integer.parseInt(demande);

		// tableau des centroids courant
		double centroids[][] = new double[nbclasses][3];

		// Tableau des centroids précédents
		double oldCentroids[][] = new double[nbclasses][3];

		int cidx[] = new int[nbclasses];
		// double m;

		// Matrice des distances courante (distance entre chaque pixel ( = data) et
		// chaque centroid)
		double distances[][] = new double[nbclasses][nbpixels];

		// Matrice des distances précédente
		double oldDistances[][] = new double[nbclasses][nbpixels];

		// Matrice des degrés d'appartenance de chaque pixel à chaque classe (matrice
		// courante)
		double U[][] = new double[nbclasses][nbpixels];

		// Matrice des degrés d'appartenance précédente
		double oldU[][] = new double[nbclasses][nbpixels];

		// Tableaux représentant les valeurs RGB pour chaque pixel
		double red[] = new double[nbpixels];
		double green[] = new double[nbpixels];
		double blue[] = new double[nbpixels];

		// Tableau des pixels x_j de l'image
		double[][] X = new double[nbpixels][3];

		double figJ[] = new double[itermax];
		for (i = 0; i < itermax; i++) {
			figJ[i] = 0;
		}

		// Récupération des données images
		// Tableau des couleurs (
		int[] colorarray = new int[3];
		l = 0;
		for (i = 0; i < width; i++) {
			for (j = 0; j < height; j++) {
				ip.getPixel(i, j, colorarray);
				red[l] = (double) colorarray[0];
				green[l] = (double) colorarray[1];
				blue[l] = (double) colorarray[2];
				X[l][0] = red[l];
				X[l][1] = green[l];
				X[l][2] = blue[l];
				l++;
			}
		}
		////////////////////////////////
		// FCM
		///////////////////////////////

		imax = nbpixels; // nombre de pixels dans l'image
		jmax = 3; // nombre de composantes couleur
		kmax = nbclasses;
		double data[][] = new double[nbclasses][3];
		int[] fixe = new int[3];

		// Initialisation des centroïdes (aléatoirement)
		int[] init = new int[3];
		for (i = 0; i < nbclasses; i++) {
			// Calculer un epsilon pour placer les centroids plus au centre si jamais on a
			// choisi d'améliorer le randomize (ça fait converger plus vite)
			boolean isRandomImproved = improveRandomization == 1;
			int epsilonx = isRandomImproved ? rand((int) (width / (i + 2)), (int) (width / 2)) : 0;
			int epsilony = isRandomImproved ? rand((int) (height / (4)), (int) (height / 2)) : 0;

			int rx = rand(epsilonx, width - epsilonx);
			int ry = rand(epsilony, height - epsilony);

			// Récupérer la valeur des pixels du centroid trouvé
			ip.getPixel(rx, ry, init);
			centroids[i][0] = init[0];
			centroids[i][1] = init[1];
			centroids[i][2] = init[2];
		}

		// Calcul de distance entre data et centroides
		for (l = 0; l < nbpixels; l++) {
			for (k = 0; k < kmax; k++) {
				double r2 = Math.pow(red[l] - centroids[k][0], 2);
				double g2 = Math.pow(green[l] - centroids[k][1], 2);
				double b2 = Math.pow(blue[l] - centroids[k][2], 2);
				distances[k][l] = r2 + g2 + b2;
			}
		}

		// Initialisation des degrés d'appartenance
		// POUR CHAQUE [pixel x_j]
		for (j = 0; j < nbpixels; j++) {
			// POUR CHAQUE [centroid v_i]
			for (i = 0; i < nbclasses; i++) {
				// Calculer le degré d'appartenance du pixel x_j pour la classe du centroid v_i
				double u_ij = this.u_ij(i, j, m, centroids, distances);
				U[i][j] = u_ij;
			}
		}

		////////////////////////////////////////////////////////////
		// FIN INITIALISATION FCM
		///////////////////////////////////////////////////////////

		/////////////////////////////////////////////////////////////
		// BOUCLE PRINCIPALE
		////////////////////////////////////////////////////////////
		iter = 0;
		stab = 2;
		seuil = valeur_seuil;

		/////////////////// A COMPLETER ///////////////////////////////
		while ((iter < itermax) && (stab > seuil)) {

			// ######################################################################
			// Update the matrix of centroids
			oldCentroids = centroids; // on garde les vieux centroids si jamais l'itération courante produit un moins
										// bon résultat
			// POUR CHAQUE [centroids]
			centroids = new double[centroids.length][3];
			for (i = 0; i < nbclasses; i++) {
				centroids[i] = this.v_i(i, X, U, m);
			}

			// ######################################################################
			// Compute Dmat, the matrix of distances (euclidian) with the centroïds
			// POUR CHAQUE [pixel x_j]
			for (j = 0; j < nbpixels; j++) {
				double[] x_j = X[j];

				// POUR CHAQUE [centroid v_i]
				for (i = 0; i < nbclasses; i++) {
					double[] v_i = centroids[i];
					double d_ij = this.d2(v_i, x_j);

					distances[i][j] = d_ij;
				}
			}

			// ######################################################################
			// Calcul de la matrice des degrés d'appartenance
			for (j = 0; j < nbpixels; j++) {
				// POUR CHAQUE [centroid v_i]
				for (i = 0; i < nbclasses; i++) {
					// Calculer le degré d'appartenance du pixel x_j pour la classe du centroid v_i
					double u_ij = this.u_ij(i, j, m, centroids, distances);
					U[i][j] = u_ij;
				}
			}

			// Calculate difference between the previous partition and the new partition
			// (performance index)
			figJ[iter] = this.performanceIndex(U, distances, m);

			// SI [les performances d'avant sont meilleures que les performances courantes]
			if (iter > 0 && Math.abs(figJ[iter - 1]) <= Math.abs(figJ[iter])) {
				// JE ME CASSE
				break;
			}

			stab = iter == 0 ? Math.abs(figJ[iter] - stab) : Math.abs(figJ[iter] - figJ[iter-1]);

			iter++;

			////////////////////////////////////////////////////////

			// Affichage de l'image segmentée
			double[] mat_array = new double[nbclasses];
			l = 0;
			for (i = 0; i < width; i++) {
				for (j = 0; j < height; j++) {
					for (k = 0; k < nbclasses; k++) {
						mat_array[k] = U[k][l];
					}
					int indice = IndiceMaxOfArray(mat_array, nbclasses);
					int array[] = new int[3];
					array[0] = (int) centroids[indice][0];
					array[1] = (int) centroids[indice][1];
					array[2] = (int) centroids[indice][2];
					ipseg.putPixel(i, j, array);
					l++;
				}
			}
			impseg.updateAndDraw();
			//////////////////////////////////
		} // Fin boucle

		double[] xplot = new double[iter];
		double[] yplot = new double[iter];
		for (int w = 0; w < iter; w++) {
			xplot[w] = (double) w;
			yplot[w] = (double) figJ[w];
		}
		Plot plot = new Plot("Performance Index (FCM)", "iterations", "J(P) value", xplot, yplot);
		plot.setLineWidth(2);
		plot.setColor(Color.blue);
		plot.show();
	} // Fin FCM

	/**
	 * Calcule "d²_ij" des formules dans les slides, càd la distance au carré entre
	 * le centroid v_i et le pixel x_j
	 * 
	 * @param v_i le centroid
	 * @param x_j le pixel
	 * @return la distance au carré
	 */
	private double d2(double[] v_i, double[] x_j) {
		double r2 = Math.pow(x_j[0] - v_i[0], 2);
		double g2 = Math.pow(x_j[1] - v_i[1], 2);
		double b2 = Math.pow(x_j[2] - v_i[2], 2);
		return r2 + g2 + b2;
	}

	/**
	 * Effectue la formule du degré d'appartenance à un des centroids v_i pour un
	 * pixel x_j
	 * 
	 * @param i         indice du centroid v_i
	 * @param j         indice du pixel x_j
	 * @param m
	 * @param centroids
	 * @param D         matrice des distances
	 * @return
	 */
	private double u_ij(int i, int j, double m, double[][] centroids, double[][] D) {
		double u_ij = 0;

		// SI [la distance de x_j à v_i est égale à 0]
		if (D[i][j] == 0) {
			// ALORS le seul degré d'appartenance non nul est u_ij ==> = 1
			return 1;
		}

		// POUR CHAQUE [centroid v_k]
		for (int k = 0; k < centroids.length; k++) {

			// SI [la distance de x_j à v_k est égale à 0]
			if (D[k][j] == 0) {
				// ALORS [le seul degré d'appartenance non-nul de x_j est u_kj = 1
				return 0; // donc U_ij = 0
			}

			u_ij += Math.pow(
					// rapport des distances avec le centroid et avec chaque centroid
					D[i][j] / (D[k][j]),

					// Exposant 2/m-1
					(2 / (m - 1)));
		}

		u_ij = Math.pow(u_ij, -1); // Faire l'inverse de ce qui a été calculé pour compléter la formule
		return u_ij;
	}

	/**
	 * Calcule l'index de performance de la partition floue P caractérisées par les
	 * paramètres.
	 * 
	 * @param U matrice des degrés d'appartenance
	 * @param D matrice des distances
	 * @param m paramètre m
	 * @return l'index de performance pour la partition floue caractérisée par les
	 *         paramètres
	 */
	private double performanceIndex(double[][] U, double[][] D, double m) {
		double J_fcm = 0;

		// POUR CHAQUE [classe]
		for (int i = 0; i < U.length; i++) {
			// POUR CHAQUE [pixel]
			for (int j = 0; j < U[0].length; j++) {
				// Appliquer la formule
				J_fcm += Math.pow(U[i][j], m) * D[i][j];
			}
		}

		return J_fcm;
	}

	/**
	 * Calcule la réattribution du centroid v_i grâce à la formule du cours
	 * 
	 * @param i indice du centroid à calculer
	 * @param X vecteur des pixels
	 * @param U matrices des degrés d'appartenance
	 * @param m
	 * @return le nouveau centroid v_i
	 */
	private double[] v_i(int i, double[][] X, double[][] U, double m) {
		double[] v_i = new double[] { 0, 0, 0 };

		// coordonnées du numérateur dans le quotient à faire dans la formule
		double sumUX_r = 0;
		double sumUX_g = 0;
		double sumUX_b = 0;

		// dénominateur dans le quotient à faire dans la formule
		double sumU = 0;

		// POUR CHAQUE pixel
		for (int j = 0; j < X.length; j++) {
			// MàJ de la somme du numérateur de la formule
			sumUX_r += Math.pow(U[i][j], m) * X[j][0];
			sumUX_g += Math.pow(U[i][j], m) * X[j][1];
			sumUX_b += Math.pow(U[i][j], m) * X[j][2];

			// MàJ de la somme du dénominateur de la formule
			sumU += Math.pow(U[i][j], m);
		}

		// coordonnées du centroid v_i
		v_i[0] = sumUX_r / sumU;
		v_i[1] = sumUX_g / sumU;
		v_i[2] = sumUX_b / sumU;

		return v_i;
	}

	int indice;
	double min, max;

//Returns the maximum of the array

	public int IndiceMaxOfArray(double[] array, int val) {
		max = 0;
		for (int i = 0; i < val; i++) {
			if (array[i] > max) {
				max = array[i];
				indice = i;
			}
		}
		return indice;
	}

}
