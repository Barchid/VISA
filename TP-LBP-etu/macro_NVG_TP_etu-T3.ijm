/* 	=================================================================================
=========================================================================================

AUTEUR: BARCHID Sami
------

Cette macro permet de classifier les textures du dataset en utilisant comme feature le LBP_S en couleur marginale.

=========================================================================================	
    =================================================================================  */ 

//========================================================================================
// General definitions ===================================================================

// execution directory of this macro
execution_dir = File.directory;	 
 
// datasets
datasets = newArray(		"Outex-TC-00013-4x3",	"Outex-TC-00014-4x3");	//  datasets
 
// Texture classes
nb_classes = newArray(4, 4 ); 		// number of classes in each dataset
nb_learning_textures = newArray( 3, 3);		// number of learning images in each class of each dataset
nb_test_textures = newArray( 3, 3 );		// number of test images in each class of each dataset

//Color space 
selected_colormethod = "NVG";
plans = 1; 

// option 
options = newArray (" notused"); 

// End of General definitions ============================================================
//========================================================================================
//========================================================================================
// === Macro parameters (to be filled) ===================================================

selected_dataset = 0;

// distance set to 1 by default, distance between the central pixel and the neighbours
distance_voisins = newArray ("1","2"); 
indice_max_distance=1;
// number of considered neighbours 
nb_voisins = newArray ("8", "16", "24"); 
indice_max_voisin = 0;
 
 // Thresholds
thresholds_M = -1; // Local average in the neighborhood (default value: -1)
thresholds_C = -1; // Average over the whole image (default value: -1)

//option_riu = "riu2";
// A ne pas utiliser pour le TP
option_riu = ""; 
		  
// Batch mode?
//batch_mode = true;
batch_mode = true;

// === End of Macro parameters (to be filled) ============================================
//========================================================================================


// TYPE pour le colour LBP marginal :
LBP_type = "Marginal-Color-LBP";
hist_size_x = 2 * 256 * plans; //Concaténation des trois histogrammes de LBP_S pour les trois canaux (RGB)
hist_size_y = 1;

// Image directories
execution_dir = File.directory;	// execution directory of this macro
images_dir 		=	execution_dir+File.separator+datasets[selected_dataset]+File.separator+"images"+File.separator;// 8-bits images
learning_dir 		= 	execution_dir+File.separator+datasets[selected_dataset]+File.separator+"000"+File.separator;
test_dir 		=	execution_dir+File.separator+datasets[selected_dataset]+File.separator+"000"+File.separator;

// Description files of learning and test images
learning_file 		= learning_dir	+"train.txt";
test_file 		= test_dir	+"test.txt";

 
// Result directory
dossier_res		= selected_colormethod + "_" +datasets[selected_dataset];
result_dir_root = execution_dir+File.separator+"Resultats-etu";


//========================================================================================
setBatchMode(batch_mode);

if (!File.exists(result_dir_root))
		File.makeDirectory(result_dir_root);
result_dir_root = result_dir_root+File.separator;

// Read the description file of learning images, then save the list of learning images to a file
lines_lear = split(File.openAsString(learning_file), "\n");
list_lear = newArray(nb_classes[selected_dataset] * nb_learning_textures[selected_dataset]);	// Array of learning image names
for (i = 0 ; i < list_lear.length; i++)
{
	// Split each file line
	line_lear = split(lines_lear[i+1]);
	list_lear[i] = line_lear[0];
	classe_lear = parseInt (line_lear[1]);
	print ("Learning image:",list_lear[i]," class:",classe_lear);
}
str_all_lear_images="";
for (i = 0 ; i < list_lear.length; i++)
	str_all_lear_images += images_dir + list_lear[i] + "\n";
File.saveString(str_all_lear_images, learning_dir+"files_lear.txt");

// Read the description file of test images, then save the list of learning images to a file
lines_test = split(File.openAsString(test_file), "\n");
list_test = newArray(nb_classes[selected_dataset] * nb_test_textures[selected_dataset]);	// array of test image names
for (i = 0 ; i < list_test.length; i++)
{
	// Split each file line
	line_test = split(lines_test[i+1]);
	list_test[i] = line_test[0];
	classe_test = parseInt (line_test[1]);
	//print ("Test image:",list_test[i]," class:",classe_test);
}
str_all_test_images="";
for (i = 0 ; i < list_test.length; i++)
	str_all_test_images += images_dir+ list_test[i] + "\n";
File.saveString(str_all_test_images, test_dir+"files_test.txt");


// === Create learning images
// Open all learning images in a stack
run("Stack From List...", "open="+learning_dir+"files_lear.txt"); 
lear_image = getImageID();
lear_image_title = getTitle();

// SPLIT DES CANAUX R G ET B !
run("Split Channels");
selectWindow(lear_image_title + " (red)");
lear_image_RED = getImageID();
selectWindow(lear_image_title + " (green)");
lear_image_GREEN = getImageID();
selectWindow(lear_image_title + " (blue)");
lear_image_BLUE = getImageID();

// === Create test images
// Open all test images in a stack
run("Stack From List...", "open="+test_dir+"files_test.txt");
test_image = getImageID();
test_image_title = getTitle();

// SPLIT DES CANAUX R G ET B !
run("Split Channels");
selectWindow(test_image_title + " (red)");
test_image_RED = getImageID();
selectWindow(test_image_title + " (green)");
test_image_GREEN = getImageID();
selectWindow(test_image_title + " (blue)");
test_image_BLUE = getImageID();



// Taille images
taillex = getWidth();
tailley = getHeight();
	 
	
//setBatchMode("exit & display");


// === Start processing images
result_dir= result_dir_root ;

// POUR CHAQUE [distance de voisinage] (d=1 ou d=2)
for(val_dist = 0; val_dist <= indice_max_distance; val_dist++)
{
	
	// Create LBP of learn images
	// --------------------------
	
	// Création du stack pour marge LBP du channel RED
	newImage("LBP_S_RED"+"_lear", "8-bit Black",  taillex , tailley, nb_classes[selected_dataset] * nb_learning_textures[selected_dataset]); // LBP_S
	LBP_S_RED_lear = getImageID();
	LBP_S_RED_lear_title = getTitle();
	newImage("LBP_M_RED"+"_lear", "8-bit Black",  taillex , tailley, nb_classes[selected_dataset] * nb_learning_textures[selected_dataset]); // LBP_M
	LBP_M_RED_lear=getImageID();
	LBP_M_RED_lear_title=getTitle();
	newImage("LBP_C_RED"+"_lear", "8-bit Black",  taillex , tailley, nb_classes[selected_dataset] * nb_learning_textures[selected_dataset]); // LBP_C
	LBP_C_RED_lear=getImageID();
	LBP_C_RED_lear_title=getTitle();
	

	// Création des stacks pour marge LBP du channel GREEN
	newImage("LBP_S_GREEN"+"_lear", "8-bit Black",  taillex , tailley, nb_classes[selected_dataset] * nb_learning_textures[selected_dataset]); // LBP_S
	LBP_S_GREEN_lear = getImageID();
	LBP_S_GREEN_lear_title = getTitle();
	newImage("LBP_M_GREEN"+"_lear", "8-bit Black",  taillex , tailley, nb_classes[selected_dataset] * nb_learning_textures[selected_dataset]); // LBP_M
	LBP_M_GREEN_lear=getImageID();
	LBP_M_GREEN_lear_title=getTitle();
	newImage("LBP_C_GREEN"+"_lear", "8-bit Black",  taillex , tailley, nb_classes[selected_dataset] * nb_learning_textures[selected_dataset]); // LBP_C
	LBP_C_GREEN_lear=getImageID();
	LBP_C_GREEN_lear_title=getTitle();


	// Création du stack pour marge LBP du channel BLUE
	newImage("LBP_S_BLUE"+"_lear", "8-bit Black",  taillex , tailley, nb_classes[selected_dataset] * nb_learning_textures[selected_dataset]); // LBP_S
	LBP_S_BLUE_lear = getImageID();
	LBP_S_BLUE_lear_title = getTitle();
	newImage("LBP_M_BLUE"+"_lear", "8-bit Black",  taillex , tailley, nb_classes[selected_dataset] * nb_learning_textures[selected_dataset]); // LBP_M
	LBP_M_BLUE_lear=getImageID();
	LBP_M_BLUE_lear_title=getTitle();	
	newImage("LBP_C_BLUE"+"_lear", "8-bit Black",  taillex , tailley, nb_classes[selected_dataset] * nb_learning_textures[selected_dataset]); // LBP_C
	LBP_C_BLUE_lear=getImageID();
	LBP_C_BLUE_lear_title=getTitle();
	


	
	// Create LBP of test images
	// -------------------------

	// Création du stack pour marge LBP du channel RED
	newImage("LBP_S_RED"+"_test", "8-bit Black",  taillex , tailley, nb_classes[selected_dataset] * nb_test_textures[selected_dataset]); // LBP_S
	LBP_S_RED_test = getImageID();
	LBP_S_RED_test_title = getTitle();
	newImage("LBP_M_RED"+"_test", "8-bit Black",  taillex , tailley, nb_classes[selected_dataset] * nb_test_textures[selected_dataset]); // LBP_M
	LBP_M_RED_test=getImageID();
	LBP_M_RED_test_title=getTitle();
	newImage("LBP_C_RED"+"_test", "8-bit Black",  taillex , tailley, nb_classes[selected_dataset] * nb_test_textures[selected_dataset]); // LBP_C
	LBP_C_RED_test=getImageID();
	LBP_C_RED_test_title=getTitle();
	

	// Création des stacks pour marge LBP du channel GREEN
	newImage("LBP_S_GREEN"+"_test", "8-bit Black",  taillex , tailley, nb_classes[selected_dataset] * nb_test_textures[selected_dataset]); // LBP_S
	LBP_S_GREEN_test = getImageID();
	LBP_S_GREEN_test_title = getTitle();
	newImage("LBP_M_GREEN"+"_test", "8-bit Black",  taillex , tailley, nb_classes[selected_dataset] * nb_test_textures[selected_dataset]); // LBP_M
	LBP_M_GREEN_test=getImageID();
	LBP_M_GREEN_test_title=getTitle();
	newImage("LBP_C_GREEN"+"_test", "8-bit Black",  taillex , tailley, nb_classes[selected_dataset] * nb_test_textures[selected_dataset]); // LBP_C
	LBP_C_GREEN_test=getImageID();
	LBP_C_GREEN_test_title=getTitle();


	// Création du stack pour marge LBP du channel BLUE
	newImage("LBP_S_BLUE"+"_test", "8-bit Black",  taillex , tailley, nb_classes[selected_dataset] * nb_test_textures[selected_dataset]); // LBP_S
	LBP_S_BLUE_test = getImageID();
	LBP_S_BLUE_test_title = getTitle();
	newImage("LBP_M_BLUE"+"_test", "8-bit Black",  taillex , tailley, nb_classes[selected_dataset] * nb_test_textures[selected_dataset]); // LBP_M
	LBP_M_BLUE_test=getImageID();
	LBP_M_BLUE_test_title=getTitle();	
	newImage("LBP_C_BLUE"+"_test", "8-bit Black",  taillex , tailley, nb_classes[selected_dataset] * nb_test_textures[selected_dataset]); // LBP_C
	LBP_C_BLUE_test=getImageID();
	LBP_C_BLUE_test_title=getTitle();
		
	// Calcul des LBP images base d'apprentissage			
	for (ind_lear=0; ind_lear < list_lear.length; ind_lear++)
	{
		// Compute LBP of learning images
		slice_lear = ind_lear+1;

		// CALCUL LBP POUR CHANNEL RED
		// ---------------------------
		selectImage (LBP_S_RED_lear);
		setSlice(slice_lear);
		selectImage (LBP_M_RED_lear);
		setSlice(slice_lear);
		selectImage (LBP_C_RED_lear);
		setSlice(slice_lear);	
		selectImage (lear_image_RED);
		setSlice(slice_lear);
		// Lancement du plugin qui calcule le LBP_S et le LBP_C pour le channel rouge
		command = "ip="+lear_image_RED + " nbvoisins=" + nb_voisins[indice_max_voisin] + " r="+ distance_voisins[val_dist]+" seuilm="+thresholds_M+" seuilc="+thresholds_C+ " " + option_riu +" lbp_s=LBP_S_RED_lear lbp_m=LBP_M_RED_lear lbp_c=LBP_C_RED_lear";
		run("byte 2 lbp etu ",command);


		// CALCUL LBP POUR CHANNEL GREEN
		// ---------------------------
		selectImage (LBP_S_GREEN_lear);
		setSlice(slice_lear);
		selectImage (LBP_M_GREEN_lear);
		setSlice(slice_lear);
		selectImage (LBP_C_GREEN_lear);
		setSlice(slice_lear);	
		selectImage (lear_image_GREEN);
		setSlice(slice_lear);
		// Lancement du plugin qui calcule le LBP_S et le LBP_C pour le channel GREEN
		command = "ip="+lear_image_GREEN + " nbvoisins=" + nb_voisins[indice_max_voisin] + " r="+ distance_voisins[val_dist]+" seuilm="+thresholds_M+" seuilc="+thresholds_C+ " " + option_riu +" lbp_s=LBP_S_GREEN_lear lbp_m=LBP_M_GREEN_lear lbp_c=LBP_C_GREEN_lear";
		run("byte 2 lbp etu ",command);

		// CALCUL LBP POUR CHANNEL BLUE
		// ----------------------------
		selectImage (LBP_S_BLUE_lear);
		setSlice(slice_lear);
		selectImage (LBP_M_BLUE_lear);
		setSlice(slice_lear);
		selectImage (LBP_C_BLUE_lear);
		setSlice(slice_lear);	
		selectImage (lear_image_BLUE);
		setSlice(slice_lear);
		// Lancement du plugin qui calcule le LBP_S et le LBP_C pour le channel GREEN
		command = "ip="+lear_image_BLUE + " nbvoisins=" + nb_voisins[indice_max_voisin] + " r="+ distance_voisins[val_dist]+" seuilm="+thresholds_M+" seuilc="+thresholds_C+ " " + option_riu +" lbp_s=LBP_S_BLUE_lear lbp_m=LBP_M_BLUE_lear lbp_c=LBP_C_BLUE_lear";
		run("byte 2 lbp etu ",command);
		
		
	} //for indlear
		
	//setBatchMode("exit & display");


	// Calcul des LBP images base test	
	for (ind_test=0; ind_test < list_test.length; ind_test++)
	{
		slice_test = ind_test+1;

		// CALCUL LBP POUR CHANNEL RED
		// ---------------------------
		selectImage (LBP_S_RED_test);
		setSlice(slice_test);
		selectImage (LBP_M_RED_test);
		setSlice(slice_test);
		selectImage (LBP_C_RED_test);
		setSlice(slice_test);	
		selectImage (test_image_RED);
		setSlice(slice_test);
		// Lancement du plugin qui calcule le LBP_S et le LBP_C pour le channel rouge
		command = "ip="+test_image_RED + " nbvoisins=" + nb_voisins[indice_max_voisin] + " r="+ distance_voisins[val_dist]+" seuilm="+thresholds_M+" seuilc="+thresholds_C+ " " + option_riu +" lbp_s=LBP_S_RED_test lbp_m=LBP_M_RED_test lbp_c=LBP_C_RED_test";
		run("byte 2 lbp etu ",command);


		// CALCUL LBP POUR CHANNEL GREEN
		// ---------------------------
		selectImage (LBP_S_GREEN_test);
		setSlice(slice_test);
		selectImage (LBP_M_GREEN_test);
		setSlice(slice_test);
		selectImage (LBP_C_GREEN_test);
		setSlice(slice_test);	
		selectImage (test_image_GREEN);
		setSlice(slice_test);
		// Lancement du plugin qui calcule le LBP_S et le LBP_C pour le channel GREEN
		command = "ip="+test_image_GREEN + " nbvoisins=" + nb_voisins[indice_max_voisin] + " r="+ distance_voisins[val_dist]+" seuilm="+thresholds_M+" seuilc="+thresholds_C+ " " + option_riu +" lbp_s=LBP_S_GREEN_test lbp_m=LBP_M_GREEN_test lbp_c=LBP_C_GREEN_test";
		run("byte 2 lbp etu ",command);

		// CALCUL LBP POUR CHANNEL BLUE
		// ----------------------------
		selectImage (LBP_S_BLUE_test);
		setSlice(slice_test);
		selectImage (LBP_M_BLUE_test);
		setSlice(slice_test);
		selectImage (LBP_C_BLUE_test);
		setSlice(slice_test);	
		selectImage (test_image_BLUE);
		setSlice(slice_test);
		// Lancement du plugin qui calcule le LBP_S et le LBP_C pour le channel GREEN
		command = "ip="+test_image_BLUE + " nbvoisins=" + nb_voisins[indice_max_voisin] + " r="+ distance_voisins[val_dist]+" seuilm="+thresholds_M+" seuilc="+thresholds_C+ " " + option_riu +" lbp_s=LBP_S_BLUE_test lbp_m=LBP_M_BLUE_test lbp_c=LBP_C_BLUE_test";
		run("byte 2 lbp etu ",command);		
		
	}//for indtest
	

	// ICI, C'EST LA CLASSIFICATION (et le calcul de l'histogramme pour chaque descripteurs utilisé)
	// C'est le type "Marginal LBP-S" qu'on fait
	LBP_type = "Marginal-LBP-S";
		
	// === Create LBP histograms (sizes depend on LBP type)
	// Create learning LBP histograms
	newImage("histo_"+LBP_type+option_riu+"_lear", "16-bit Black", hist_size_x,hist_size_y, list_lear.length);
	LBP_hist_lear=getImageID();
	LBP_hist_lear_title=getTitle();

	// Create test LBP histograms
	newImage("histo_"+LBP_type+option_riu+"_test", "16-bit Black", hist_size_x,hist_size_y, list_test.length);
	LBP_hist_test=getImageID();
	LBP_hist_test_title=getTitle();			
	
	
	for (ind_lear=0; ind_lear < list_lear.length; ind_lear++)
	{
		// Compute LBP of learning images
		slice_lear = ind_lear+1;	

		selectImage (LBP_S_RED_lear);
		setSlice(slice_lear);
		selectImage (LBP_S_GREEN_lear);
		setSlice(slice_lear);
		selectImage (LBP_S_BLUE_lear);
		setSlice(slice_lear);	
		
		selectImage(LBP_hist_lear);
		setSlice(slice_lear);
		
		/*calc la concatenation des histogrammes des trois LBP-s R G et B mais en utilisant la concaténation déjà prévue de SMC (petit trick mais c'est censé marcher)*/
		//command = "type=CLBP-S-M-C"+" r="+ distance_voisins[val_dist] + " lbp_s=LBP_S_RED_lear lbp_m=LBP_S_GREEN_lear lbp_c=LBP_S_BLUE_lear";
		command = "type=CLBP-S-M"+" r="+ distance_voisins[val_dist] + " lbp_s=LBP_S_RED_lear lbp_m=LBP_S_GREEN_lear lbp_c=LBP_S_BLUE_lear";

		run("lbp 2 hist ",command);
		
		showProgress(ind_lear/(list_lear.length+list_test.length));
	} //for indlear
		
	for (ind_test=0; ind_test < list_test.length; ind_test++)
	{
		slice_test = ind_test+1;
		
		selectImage (LBP_S_RED_test);
		setSlice(slice_test);
		selectImage (LBP_S_GREEN_test);
		setSlice(slice_test);
		selectImage (LBP_S_BLUE_test);
		setSlice(slice_test);	
		
		selectImage(LBP_hist_test);
		setSlice(slice_test);
		
		/*calc la concatenation des histogrammes des trois LBP-s R G et B mais en utilisant la concaténation déjà prévue de SMC (petit trick mais c'est censé marcher)*/
		command = "type=CLBP-S-M-C"+" r="+ distance_voisins[val_dist] + " lbp_s=LBP_S_RED_test lbp_m=LBP_S_GREEN_test lbp_c=LBP_S_BLUE_test";
		run("lbp 2 hist ",command);
		showProgress(ind_test/(list_lear.length+list_test.length));
	}
		
		
	// Init output result file names
	result_dir = result_dir_root;
	if (!File.exists(result_dir))
					File.makeDirectory(result_dir);

	result_file_Intersection = "CLBP=" +LBP_type+"_comparaison=intersection_nbVoisins=" +nb_voisins[indice_max_voisin] + "_distance="+ distance_voisins[val_dist]+".txt";
				
	print ("================================================================");
	print ("type_LBP=",LBP_type+option_riu, " nbVoisins=",nb_voisins[indice_max_voisin], " distance="+ distance_voisins[val_dist]);
	print ("Result file Intersection=",result_dir+result_file_Intersection);
	
	
	// Classify images from their histo LBPs
	showStatus("Classification...");								
	learning_statistics = ""; //list of (comma-separated) LBP histograms of learning images
	test_statistics = "";
	learning_statistics += ","+LBP_hist_lear_title; 
	test_statistics += ","+LBP_hist_test_title+",";
	learning_statistics = substring(learning_statistics, 1);
	test_statistics = substring(test_statistics, 1);
	command = "learning_statistics="+learning_statistics+" test_statistics="+test_statistics+" nb_classes="+d2s(nb_classes[selected_dataset],0);
	command = command + " directory="+result_dir+" learning_file="+learning_file+" test_file="+test_file;
	print ("command =",command);
	
	run("classif similarity", command+" result_file="+result_file_Intersection+" similarity_measure=Intersection");
	//run("classif similarity", command+" result_file="+result_file_JD+" similarity_measure=Jeffrey-Divergence");
	
	showStatus("Classification ... finished");	
	
	selectImage(LBP_hist_lear);
	close();
	selectImage(LBP_hist_test);
	close();	
			
		
		
	// On ferme tout
	selectImage(LBP_S_RED_lear);
	close();
	selectImage(LBP_M_RED_lear);
	close();
	selectImage(LBP_C_RED_lear);
	close();
	selectImage(LBP_S_GREEN_lear);
	close();
	selectImage(LBP_M_GREEN_lear);
	close();
	selectImage(LBP_C_GREEN_lear);
	close();
	selectImage(LBP_S_BLUE_lear);
	close();
	selectImage(LBP_M_BLUE_lear);
	close();
	selectImage(LBP_C_BLUE_lear);
	close();

	
	selectImage(LBP_S_RED_test);
	close();
	selectImage(LBP_M_RED_test);
	close();
	selectImage(LBP_C_RED_test);
	close();
	selectImage(LBP_S_GREEN_test);
	close();
	selectImage(LBP_M_GREEN_test);
	close();
	selectImage(LBP_C_GREEN_test);
	close();
	selectImage(LBP_S_BLUE_test);
	close();
	selectImage(LBP_M_BLUE_test);
	close();
	selectImage(LBP_C_BLUE_test);
	close();
		
	

}//For neighbors distance


selectImage(lear_image_RED);
close();
selectImage(lear_image_GREEN);
close();
selectImage(lear_image_BLUE);
close();

selectImage(test_image_RED);
close();
selectImage(test_image_GREEN);
close();
selectImage(test_image_BLUE);
close();
		
//}//For selected_dataset


	
Dialog.create("End");
Dialog.addMessage("Click OK to finish classification");
Dialog.show();
