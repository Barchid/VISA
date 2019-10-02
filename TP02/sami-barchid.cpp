/* --------------------------------------------------------------------------
Mise en correspondance de points d'interet detectes dans deux images
Copyright (C) 2010, 2018  Univ. Lille

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
-------------------------------------------------------------------------- */

/* --------------------------------------------------------------------------
Inclure les fichiers d'entete
-------------------------------------------------------------------------- */
using namespace std;
#include <stdio.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/highgui.hpp>
using namespace cv;
using namespace std;
#include "glue.hpp"
#include "sami-barchid.hpp"

// -----------------------------------------------------------------------
/// \brief Detecte les coins.
///
/// @param mImage: pointeur vers la structure image openCV
/// @param iMaxCorners: nombre maximum de coins detectes
/// @return matrice des coins
// -----------------------------------------------------------------------
Mat iviDetectCorners(const Mat& mImage,
                     int iMaxCorners) {
    // A modifier !
    double tx = mImage.cols, ty = mImage.rows;
    Mat mCorners = (Mat_<double>(3, iMaxCorners));

  /// Parameters for Shi-Tomasi algorithm
  vector<Point2f> corners;
  double qualityLevel = 0.01;
  double minDistance = 10;
  int blockSize = 3;
  bool useHarrisDetector = false;
  double k = 0.04;
  int maxCorners = iMaxCorners;

   /// Apply corner detection
    goodFeaturesToTrack( mImage,
        corners,
        maxCorners,
        qualityLevel,
        minDistance,
        Mat(),
        blockSize,
        useHarrisDetector,
        k
    );

    // POUR CHAQUE [coin] de [coins détectés par Shi & Tomasi]
    int i = 0;
    for(Point2f corner : corners) {
        // DEFINIR les coordonnées
        int x = corner.x;
        int y = corner.y;
        int w = 1; // coordonnée homogène

        // AJOUTER dans MATRICE à la ième colonne
        mCorners.at<double>(0,i) = x; // pour X
        mCorners.at<double>(1,i) = y; // pour Y
        mCorners.at<double>(2,i) = w; // pour coordonnée homogène w

        // INCR. compteur
        i++;
    }


    // Retour de la matrice
    return mCorners;
}

// -----------------------------------------------------------------------
/// \brief Initialise une matrice de produit vectoriel.
///
/// @param v: vecteur colonne (3 coordonnees)
/// @return matrice de produit vectoriel
// -----------------------------------------------------------------------
Mat iviVectorProductMatrix(const Mat& v) {
    double px = v.at<double>(0);
    double py = v.at<double>(1);
    double pz = v.at<double>(2);
    Mat mVectorProduct = (Mat_<double>(3,3)<<
        0, -pz, py,
        pz, 0, -px,
        -py, px, 0
    );
    // Retour de la matrice
    return mVectorProduct;
}

// -----------------------------------------------------------------------
/// \brief Initialise et calcule la matrice fondamentale.
///
/// @param mLeftIntrinsic: matrice intrinseque de la camera gauche
/// @param mLeftExtrinsic: matrice extrinseque de la camera gauche
/// @param mRightIntrinsic: matrice intrinseque de la camera droite
/// @param mRightExtrinsic: matrice extrinseque de la camera droite
/// @return matrice fondamentale
// -----------------------------------------------------------------------
Mat iviFundamentalMatrix(const Mat& mLeftIntrinsic,
                         const Mat& mLeftExtrinsic,
                         const Mat& mRightIntrinsic,
                         const Mat& mRightExtrinsic) {
    // A modifier !
    // Doit utiliser la fonction iviVectorProductMatrix
    Mat mFundamental = Mat::eye(3, 3, CV_64F);

    // Calculer P2
    Mat cheh = (Mat_<double>(3,4) <<
        1, 0, 0, 0,
        0, 1, 0, 0,
        0, 0, 1, 0
    );
    Mat P2 = mRightIntrinsic * cheh * mRightExtrinsic;

    // Calculer P1
    Mat P1 = mLeftIntrinsic * cheh * mLeftExtrinsic;
    Mat P1p = P1.inv(DECOMP_SVD); // La pseudo inverse de P1

    // Calculer homographie Hpi
    Mat Hpi = P2 * P1p;

    // Calculer o1
    // O1 est la dernière colonne de l'inverse de E1
    Mat Ei = mLeftExtrinsic.inv();
    Mat O1 = Ei.col(3);

    // Calculer P2O1
    Mat P2O1x = iviVectorProductMatrix(P2*O1);

    // On applique la formule des thugs (slide n°17)
    mFundamental = P2O1x * Hpi;

    // Retour de la matrice fondamentale
    return mFundamental;
}

// -----------------------------------------------------------------------
/// \brief Initialise et calcule la matrice des distances entres les
/// points de paires candidates a la correspondance.
///
/// @param mLeftCorners: liste des points 2D image gauche
/// @param mRightCorners: liste des points 2D image droite
/// @param mFundamental: matrice fondamentale
/// @return matrice des distances entre points des paires
// -----------------------------------------------------------------------
Mat iviDistancesMatrix(const Mat& m2DLeftCorners,
                       const Mat& m2DRightCorners,
                       const Mat& mFundamental) {

    // Calculer la transposée de la matrice fondamentale (utile pour la suite)
    Mat Ft = mFundamental.t();
    Mat F = mFundamental; // petit alias pour la suite

    // Matrice 2D des distances selon chaque paire de point possible
    Mat mDistances = Mat(m2DLeftCorners.cols, m2DRightCorners.cols, CV_64F);

    // POUR CHAQUE [paire de point], on calcule la distance entre les deux points suivant les droites epipolaires etc blablabla voir la slide
    for(int i = 0; i < m2DLeftCorners.cols; i++) {
        for(int j = 0; j < m2DRightCorners.cols; j++) {
            // Point de l'image gauche --> m1
            Mat m1 = m2DLeftCorners.col(i);
            double xM1 = m1.at<double>(0,0);
            double yM1 = m1.at<double>(1,0);

            // Point de l'image droite --> m2
            Mat m2 = m2DRightCorners.col(j);
            double xM2 = m2.at<double>(0,0);
            double yM2 = m2.at<double>(1,0);

            // La droite épipolaire de l'image gauche associée au point de l'image droite ==> d1 dans les slides
            Mat d1 = Ft * m2;
            double xD1 = d1.at<double>(0,0);
            double yD1 = d1.at<double>(1,0);

            // La droite épipolaire de l'image droite associée au point de l'image gauche ==> d2 dans les slides
            Mat d2 = F  * m1;
            double xD2 = d2.at<double>(0,0);
            double yD2 = d2.at<double>(1,0);

            // Distance euclidienne entre m1 et d1
            double distM1D1 = sqrt(
                pow((xD1 - xM1), 2)
                +
                pow((yD1 - yM1), 2)
            );

            // Distance euclidienne entre m2 et d2
            double distM2D2 = sqrt(
                pow((xD2 - xM2), 2)
                +
                pow((yD2 - yM2), 2)
            );
            // Distance est la somme des deux distances euclidiennes calculées avant
            double distance = distM1D1 + distM2D2;
            mDistances.at<double>(i,j) = distance;
        }
    }
    // Retour de la matrice fondamentale
    //print(mDistances);
    return mDistances;
}

// -----------------------------------------------------------------------
/// \brief Initialise et calcule les indices des points homologues.
///
/// @param mDistances: matrice des distances
/// @param fMaxDistance: distance maximale autorisant une association
/// @param mRightHomologous: liste des correspondants des points gauche
/// @param mLeftHomologous: liste des correspondants des points droite
/// @return rien
// -----------------------------------------------------------------------
void iviMarkAssociations(const Mat& mDistances,
                         double dMaxDistance,
                         Mat& mRightHomologous,
                         Mat& mLeftHomologous) {
    // A modifier !
    // Parcourir les distances et checker la distance ??? Trop simple non ?

}
