/**
 * AUTEUR : BARCHID Sami
 * 
 * Segmentation d'une image couleur d'un test d'Ishihara (correspondant au "cas 4" dans l'exercice, c'est-à-dire un nombre orange-rose sur un fond composé de points verts-bleus).
 * 
 * En résumé, le but est d'utiliser de convertir l'image RGB vers un color space Y'UV puis de séparer les channels.
 * Ensuite, on utilise le channel V pour appliquer un seuil qui segmentera l'image de telle sorte que le nombre soit en blanc et le fond en noir.
 * 
 * La conversion de RGB vers Y'UV est faite "en dur" dans la macro où, pour chaque pixel RGB, on applique la formule donnée dans l'énoncé pour retrouver les valeurs Y'UV.
 */
 
macro "Segmentation_Cas4" {
title = getTitle();

// Duppliquer l'image sélectionnée pour la convertir en Y'UV
run("Duplicate...", "title=" + title + " (Y'UV)");

// cette dupplication devient notre image de travail
imageID = getImageID();

// CONVERTIR DE [RGB] VERS [YUV]
RGB_to_YUV(imageID);

// SÉPARER LES CHANNELS (note : dans l'affichage des titres, le canal red correspond au canal Y', le canal green correspond à U et le canal blue correspond à V) 
run("Split Channels");

// FERMER [le canal Y'] (facultatif, c'est juste pour virer les infos inutiles)
selectWindow(title + " (red)");
close();

// FERMER [le canal U] (facultatif, c'est juste pour virer les infos inutiles)
selectWindow(title + " (green)");
close();

// SÉLECTIONNER [le canal V]
selectWindow(title + " (blue)");

// SEUILLER L'IMAGE [28, 80]
setThreshold(28, 80);
setOption("BlackBackground", true);
run("Convert to Mask");

/**
 * Prend en paramètre l'ID de l'image RGB qui va être convertie vers l'espace Y'UV (télévision) grâce à l'équation donnée dans l'énoncé de l'exercice.
 * @param imgID le "imageID" de l'image RGB à convertir.
 */
function RGB_to_YUV(imgID) {
	selectImage(imgID);

	// Récupération de la taille W x H de l'image
	W = getWidth();
	H = getHeight();


	setBatchMode(true);
	// POUR CHAQUE [pixel]
	for(y = 0; y < H; y++) {
		for (x = 0; x < W; x++) {
			couleur = getPixel(x, y); // le pixel est un mot 32 bits qui contient les trois couleurs ==> il faut faire des shifts pour ne conserver que les bits des couleurs en séparé

			// RÉCUPERER [la composante R] DE [pixel]
			R = (couleur & 0xff0000) >> 16;

			// RÉCUPERER [la composante G] DE [pixel]
			G = (couleur & 0x00ff00) >> 8;

			// RÉCUPERER [la composante B] DE [pixel]
			B = (couleur & 0x0000ff);

			// Utilisation de la matrice donnée à l'énoncé (on va appliquer à la main le calcul matriciel en gros)
			// CALCULER [le canal Y']
			Y = (0.299 * R) + (0.587 * G) + (0.114 * B);

			// CALCULER [le canal U]
			U = (-0.14713 * R) + (-0.28886 * G) + (0.436 * B);

			// CALCULER [le canal V]
			V = (0.615 * R) + (-0.51499 * G) + (-0.10001 * B);

			// RECOMPOSER [la nouvelle valeur du pixel en Y'UV]
			YUV = ((Y & 0xff ) << 16) + ((U & 0xff) << 8) + V & 0xff;

			// Changement du pixel
			setPixel(x, y, YUV);
		}
	}
	setBatchMode(false);
}