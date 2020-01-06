// Titre de l'image de test
img_test = getTitle();

// Path de l'image d'apprentissage qui va servir à la segmentation
img_apprentissage = getString("Choisir le path de l'image d'apprentissage à ouvrir", "D:/M2/VISA/VISA/TP7/images_2018/cas_3_dalton15.bmp");

// Nombre de classes défini pour clustering k-means de l'image d'apprentissage (il en faut 4 pour les images de cas3)
nbClasses = getNumber ("Choisissez le nombre de classes pour le clustering k-means.",4);

// Choisir l'espace de couleur entre RGB ou HSB
Dialog.create("Espace de couleur");
Dialog.addMessage("Choix espace couleur");
Dialog.addChoice("Choix espace couleur", newArray("RGB", "HSB"));
Dialog.show();
espaceCouleur = Dialog.getChoice();

setBatchMode(true);

// Lancer le plugin "k-means" sur l'image d'apprentissage
open(img_apprentissage);

// SI [l'utilisateur a choisi le mode HSB]
if(espaceCouleur == "HSB"){
	run("Color Space Converter", "from=RGB to=HSB white=D65");
}

run("k-means Clustering", "number=" + nbClasses + " cluster=0.000010000 enable randomization=48 show");


// Trouver les valeurs des centroids
centroids = trouverCentroids(nbClasses);

// Duppliquer l'image de test  pour faire l'image de segmentation
selectWindow(img_test);
// SI [l'utilisateur a choisi le mode HSB]
if(espaceCouleur == "HSB"){
	run("Color Space Converter", "from=RGB to=HSB white=D65");
}
else {
	run("Duplicate...", "title=[" + img_test + " SEGMENTEE]");
}
img_segmentee = getTitle();	
selectWindow(img_segmentee);

// Segmenter l'image pour avoir le résultat
segmentationImageTest(centroids);

setBatchMode(false);

/**
 * Fonction qui se charge de trouver les valeurs des pixels des centres de classe trouvés dans une image segmentée par k-means (via le Plugin)
 * param: nbClasses: nombre de classes trouvées dans l'image segmentée d'apprentissage
 */
function trouverCentroids(nbClasses) {
	// Sélectionner l'image des centres des classes de l'image d'apprentissage
	selectWindow("Cluster centroid values");
	
	nbCentroidFound = 0; // centres trouvés actuellement
	centroidsFound = newArray(nbClasses); // tableau des valeurs des pixels des centroids trouvés (à retourner)
	
	// POUR CHAQUE [pixel] DE [image des centres de classes]
	H = getHeight();
	W = getWidth();
	for (x = 0; x < W; x++) {
		for (y = 0; y < H; y++) {

			// Je stoppe quand j'ai tout trouvé
			if(nbCentroidFound >= nbClasses) {
				return centroidsFound;
			}

			// Récupérer la valeur du pixel du centroid
			centroid = getPixel(x, y);

			// Chercher si on n'a pas déjà trouvé le centroid
			isAlreadyFound = false;
			for (i = 0; i < nbCentroidFound; i++) {
				isAlreadyFound = isAlreadyFound || centroidsFound[i] == centroid;
			}

			// SI [le centroid n'a jamais été vu avant]
			if(!isAlreadyFound) {
				// ALORS [ajouter la nouvelle valeur du centroid]
				centroidsFound[nbCentroidFound] = centroid;
				nbCentroidFound++;
			}
		}
	}

	return centroidsFound;
}

/**
 * Fonction qui segmente l'image de test (déjà pré-sélectionnée auparavant) en utilisant les valeurs des pixels des centres des classes fournis en paramètre
 * param: centroids: valeurs de couleur des centres des classes trouvés via clustering
 */
function segmentationImageTest(centroids) {
	H = getHeight();
	W = getWidth();

	// POUR CHAQUE [pixel] DE [image de test]
	for (x = 0; x < W; x++) {
		for (y = 0; y < H; y++) {
			pixel = getPixel(x, y);

			// TROUVER [le centroid qui est le plus proche de la valeur du pixel donnée] (ça devient la classe du pixel courant)
			idxBestCentroid = trouverCentroidPlusProche(pixel, centroids);

			// CHANGER [la valeur du pixel] SUR [l'image segmentée]
			pixel = centroids[idxBestCentroid];
			setPixel(x, y, pixel);
		}
	}
}

/**
 * Fonction qui trouve le centroid (parmis un tableau de centroids) dont la valeur est la plus proche en terme de distance euclidienne
 * par rapport à la valeur de pixel en paramètre
 * param: pixel: pixel courant
 * param: centroids: tableau des centroids dont il faut trouver le centroid le plus proche
 * return: l'indice du tableau des centroids dont la valeur est la plus proche du pixel
 */
function trouverCentroidPlusProche(pixel, centroids) {
	idxBestCentroid = 0;
	minCentroid = distanceEuclidienne(centroids[0], pixel);
	
	// POUR CHAQUE [centroid] (recherche du min en distance euclidienne en gros)
	for (i = 1; i < centroids.length; i++) {
		centroid = centroids[i];

		distEuclidienne = distanceEuclidienne(centroid, pixel);

		// CALCULER [distance euclidienne] ENTRE [centroid] ET [pixel courant] 
		if(minCentroid > distEuclidienne) {
			idxBestCentroid = i;
			minCentroid = distEuclidienne;
		}
	}

	return idxBestCentroid;
}

/**
 * Calcul la distance euclidienne entre deux valeurs (a et b) de pixel
 * param: a: valeur de pixel a
 * param: b: valeur de pixel b
 * return: la distance euclidienne entre a et b
 */
function distanceEuclidienne(a,b) {
	// RÉCUPERER [la composante R] DE [a]
	aR = (a & 0xff0000) >> 16;
	// RÉCUPERER [la composante G] DE [a]
	aG = (a & 0x00ff00) >> 8;
	// RÉCUPERER [la composante B] DE [a]
	aB = (a & 0x0000ff);

	// RÉCUPERER [la composante R] DE [b]
	bR = (b & 0xff0000) >> 16;
	// RÉCUPERER [la composante G] DE [b]
	bG = (b & 0x00ff00) >> 8;
	// RÉCUPERER [la composante B] DE [b]
	bB = (b & 0x0000ff);
	
	return sqrt(
		pow(bR-aR, 2) +
		pow(bG-aG, 2) +
		pow(bB-aB, 2)
	);
}
