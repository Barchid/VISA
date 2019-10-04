/* --------------------------------------------------------------------------
Stereovision dense par calcul de correlation
Copyright (C) 2010, 2019 Univ. Lille

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
#include <stdio.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/highgui.hpp>
#include <math.h>
using namespace cv;
using namespace std;
#include "functions.hpp"

// -----------------------------------------------------------------------
/// \brief Estime la disparite par minimisation du SSD, image gauchee
/// prise comme reference.
///
/// @param mLeftGray: image gauche
/// @param mRightGray: image droite
/// @param iMaxDisparity: disparite maximale recherchee
/// @param iWindowHalfSize: demi-taille de la fenetre de correlation
/// @return image des disparites
// -----------------------------------------------------------------------
Mat iviLeftDisparityMap(const Mat& mLeftGray,
                        const Mat& mRightGray,
                        int iMaxDisparity,
                        int iWindowHalfSize) {
// Image resultat
Mat mLeftDisparityMap(mLeftGray.size(), CV_8U);

    // Initialisation de l'image du minimum de SSD a la valeur maximale
    // pouvant etre obtenue sur une fenetre carree
    Mat mMinSSD = Mat::ones(mLeftGray.size(), CV_64F) *
        pow((double)(2 * iWindowHalfSize + 1), 2.0) * pow(255.0, 2.0);
    // Boucler pour tous les decalages possibles
    for (int iShift = 0; iShift < iMaxDisparity; iShift++) {
        // Calculer le cout SSD pour ce decalage
        Mat mSSD = iviComputeLeftSSDCost(mLeftGray, mRightGray,
                                         iShift, iWindowHalfSize);
        // Conserver le decalage sur les pixels ou le SSD est minimum
        mLeftDisparityMap.setTo((unsigned char)iShift, mSSD < mMinSSD);
        // Et mettre a jour l'image du SSD minimum
        mSSD.copyTo(mMinSSD, mSSD < mMinSSD);
    }
    return mLeftDisparityMap;
}

// -----------------------------------------------------------------------
/// \brief Calcule la somme des differences au carre, image gauche
/// prise comme reference.
///
/// @param mLeftImage: image gauche
/// @param mRightImage: image droite
/// @param iShift: decalage teste
/// @param iWindowHalfSize: demi-taille de la fenetre de correlation
/// @return image des sommes de differences au carre
// -----------------------------------------------------------------------
Mat iviComputeLeftSSDCost(const Mat& mLeftGray,
                          const Mat& mRightGray,
                          int iShift,
                          int iWindowHalfSize) {
// Image resultat
Mat mLeftSSDCost(mLeftGray.size(), CV_64F);
    // A completer!
    return mLeftSSDCost;
}

// -----------------------------------------------------------------------
/// \brief Estime la disparite par minimisation du SSD, image droite
/// prise comme reference.
///
/// @param mLeftGray: image gauche
/// @param mRightGray: image droite
/// @param iMaxDisparity: disparite maximale recherchee
/// @param iWindowHalfSize: demi-taille de la fenetre de correlation
/// @return image des disparites
// -----------------------------------------------------------------------
Mat iviRightDisparityMap(const Mat& mLeftGray,
                         const Mat& mRightGray,
                         int iMaxDisparity,
                         int iWindowHalfSize) {
// Image resultat
Mat mRightDisparityMap(mLeftGray.size(), CV_8U);
    // A completer!
    return mRightDisparityMap;
}

// -----------------------------------------------------------------------
/// \brief Calcule la somme des differences aux carre, image droite
/// prise comme reference.
///
/// @param mLeftImage: image gauche
/// @param mRightImage: image droite
/// @param iShift: decalage teste
/// @param iWindowHalfSize: demi-taille de la fenetre de correlation
/// @return image des sommes de differences au carre
// -----------------------------------------------------------------------
Mat iviComputeRightSSDCost(const Mat& mLeftGray,
                           const Mat& mRightGray,
                           int iShift,
                           int iWindowHalfSize) {
// Image resultat
Mat mRightSSDCost(mLeftGray.size(), CV_64F);
    // A completer!
    return mRightSSDCost;
}

// -----------------------------------------------------------------------
/// \brief Verifie la coherence des cartes gauche et froite.
///
/// @param mLeftDisparity: image des disparites, reference a gauche
/// @param mRightDisparity: image des disparites, reference a droite
/// @param *mValidityMask: carte des disparites valides (si param non nul)
/// @return image des disparites validees
// -----------------------------------------------------------------------
Mat iviLeftRightConsistency(const Mat& mLeftDisparity,
                            const Mat& mRightDisparity,
                            Mat *pmValidityMask) {
// Images resultat
Mat mDisparity(mLeftDisparity.size(), CV_8U);
Mat mValidityMask(mLeftDisparity.size(), CV_8U);
    // A completer!
    if (pmValidityMask) *pmValidityMask = mValidityMask;
    return mDisparity;
}
