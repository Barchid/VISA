/* 	=================================================================================
=========================================================================================

AUTEUR: BARCHID Sami
------

Cette macro permet de classifier les textures du dataset en utilisant comme feature le LBP_C en luminance.

Très peu de choses ont changé par rapport à la macro originale, en dehors de l'indice de sélection du type de LBP que la macro devait calculer (variable "min_LBP_index" et "max_LBP_index")

On obtient les mêmes performances de classification que la macro originale.

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
 
// Min and max indices of LBPs to be tested (0..LBP_types.length-1)

// indice 2 uniquement car indice 2 = CLBP-C --> ce que l'on cherche
min_LBP_index= 2;
max_LBP_index= 2;

//option_riu = "riu2";
// A ne pas utiliser pour le TP
option_riu = ""; 
		  
// Batch mode?
batch_mode = true;
//batch_mode = false;

// === End of Macro parameters (to be filled) ============================================
//========================================================================================



//========================================================================================
// Arrays of LBP types and histogram sizes (for both standard and rotation invariant LBPs)
// Note: histogram size do not depend on the neighborhood size

LBP_types 		= newArray(9);
hist_sizes_x	= newArray(9);
hist_sizes_y	= newArray(9);

// CLBP-S
LBP_types[0] = "CLBP-S"; 
hist_sizes_x[0]		= plans*256; 
hist_sizes_y[0]		= 1;   

// CLBP-SxC
LBP_types[1] = "CLBP-SxC"; 
hist_sizes_x[1]		= plans*512; 
hist_sizes_y[1]		= 1;   

// CLBP-C
LBP_types[2] = "CLBP-C"; 
hist_sizes_x[2]		= plans*2; 
hist_sizes_y[2]		= 1;   

// CLBP-M
LBP_types[3] = "CLBP-M"; 
hist_sizes_x[3]		= plans*256; 
hist_sizes_y[3]		= 1;   

// CLBP-S-M
LBP_types[4] = "CLBP-S-M"; 
hist_sizes_x[4]		= plans*512; 
hist_sizes_y[4]		= 1;   

// CLBP-S/M
LBP_types[5] = "CLBP-SxM"; 
hist_sizes_x[5]		= plans*256; 
hist_sizes_y[5]		= plans*256;   

// CLBP-S-M/C
LBP_types[6] = "CLBP-S-MxC"; 
hist_sizes_x[6]		= (256*2*plans)+(256*plans); 
hist_sizes_y[5]		= 1;   
   
// CLBP-M-S/C
LBP_types[7] = "CLBP-M-SxC"; 
hist_sizes_x[7]		= (256*2*plans)+(256*plans); 
hist_sizes_y[7]		= 1;   

// CLBP-S/M/C
LBP_types[8] = "CLBP-SxMxC"; 
hist_sizes_x[8]		= (256*plans)*2; 
hist_sizes_y[8]		= 256*plans;   

// End of Arrays of LBP types and histogram
//========================================================================================



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

// Conversion des images couleur en images de luminance
run("8-bit");

// === Create test images
// Open all test images in a stack
run("Stack From List...", "open="+test_dir+"files_test.txt");
test_image = getImageID();

// Conversion des images couleur en images de luminance
run("8-bit");



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

	// Image de LBP_S
	newImage("LBP_S"+"_lear", "8-bit Black",  taillex , tailley, nb_classes[selected_dataset] * nb_learning_textures[selected_dataset]);
	LBP_S_lear=getImageID();
	LBP_S_lear_title=getTitle();

	// Image de LBP_M
	newImage("LBP_M"+"_lear", "8-bit Black",  taillex , tailley, nb_classes[selected_dataset] * nb_learning_textures[selected_dataset]);
	LBP_M_lear=getImageID();
	LBP_M_lear_title=getTitle();

	// Image de LBP_C (celle qui intéresse ici)
	newImage("LBP_C"+"_lear", "8-bit Black",  taillex , tailley, nb_classes[selected_dataset] * nb_learning_textures[selected_dataset]);
	LBP_C_lear=getImageID();
	LBP_C_lear_title=getTitle();
	


	
	// Create LBP of test images
	// -------------------------

	// Image de LBP_S
	newImage("LBP_S"+"_test", "8-bit Black", taillex, tailley, nb_classes[selected_dataset] * nb_test_textures[selected_dataset]);
	LBP_S_test=getImageID();
	LBP_S_test_title=getTitle();

	// Image de LBP_M
	newImage("LBP_M"+"_test", "8-bit Black", taillex, tailley, nb_classes[selected_dataset] * nb_test_textures[selected_dataset]);
	LBP_M_test=getImageID();
	LBP_M_test_title=getTitle();

	// Image de LBP_C
	newImage("LBP_C"+"_test", "8-bit Black", taillex, tailley, nb_classes[selected_dataset] * nb_test_textures[selected_dataset]);
	LBP_C_test=getImageID();
	LBP_C_test_title=getTitle();
		
	// Calcul des LBP images base d'apprentissage			
	for (ind_lear=0; ind_lear < list_lear.length; ind_lear++)
	{
		// Compute LBP of learning images
		slice_lear = ind_lear+1;

		selectImage (LBP_S_lear);
		setSlice(slice_lear);
		selectImage (LBP_M_lear);
		setSlice(slice_lear);
		selectImage (LBP_C_lear);
		setSlice(slice_lear);	
		selectImage (lear_image);
		setSlice(slice_lear);	

		// Lancement du plugin qui calcule le LBP_S et le LBP_C (pour l'image de 
		command = "ip="+lear_image + " nbvoisins=" + nb_voisins[indice_max_voisin] + " r="+ distance_voisins[val_dist]+" seuilm="+thresholds_M+" seuilc="+thresholds_C+ " " + option_riu +" lbp_s=LBP_S_lear lbp_m=LBP_M_lear lbp_c=LBP_C_lear";
		run("byte 2 lbp etu ",command);
		
	} //for indlear
		
	//setBatchMode("exit & display");


	// Calcul des LBP images base test	
	for (ind_test=0; ind_test < list_test.length; ind_test++)
	{
		slice_test = ind_test+1;
										
		selectImage (LBP_S_test);
		setSlice(slice_test);
		selectImage (LBP_M_test);
		setSlice(slice_test);
		selectImage (LBP_C_test);
		setSlice(slice_test);	
		selectImage (test_image);
		setSlice(slice_test);	
		
		command = "ip="+test_image + " nbvoisins=" + nb_voisins[indice_max_voisin] + " r="+ distance_voisins[val_dist]+" seuilm="+thresholds_M+" seuilc="+thresholds_C+ " " + option_riu +" lbp_s=LBP_S_test lbp_m=LBP_M_test lbp_c=LBP_C_test";
		run("byte 2 lbp etu ",command);
			
		//print ("command=",command);		
		
	}//for indtest
	

	// ICI, C'EST LA CLASSIFICATION (et le calcul de l'histogramme pour chaque descripteurs utilisé)		
	// For each kind of descriptor
	for (i_LBP_type = min_LBP_index; i_LBP_type <= max_LBP_index; i_LBP_type++)
	{
		LBP_type = LBP_types[i_LBP_type];
		
		if (option_riu == "" )
		{
			hist_size_x = hist_sizes_x [i_LBP_type];
			hist_size_y = hist_sizes_y [i_LBP_type];
		} 
			
			
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

			selectImage (LBP_S_lear);
			setSlice(slice_lear);
			selectImage (LBP_M_lear);
			setSlice(slice_lear);
			selectImage (LBP_C_lear);
			setSlice(slice_lear);	
			
			selectImage(LBP_hist_lear);
			setSlice(slice_lear);					
			command = "type="+LBP_type+" r="+ distance_voisins[val_dist] + " lbp_s=LBP_S_lear lbp_m=LBP_M_lear lbp_c=LBP_C_lear";
			run("lbp 2 hist ",command);
			
			showProgress(ind_lear/(list_lear.length+list_test.length));
		} //for indlear
			
		for (ind_test=0; ind_test < list_test.length; ind_test++)
		{
			slice_test = ind_test+1;
			
			selectImage (LBP_S_test);
			setSlice(slice_test);
			selectImage (LBP_M_test);
			setSlice(slice_test);
			selectImage (LBP_C_test);
			setSlice(slice_test);
							
			selectImage(LBP_hist_test);
			setSlice(slice_test);				
			command = "type="+LBP_type+" r="+ distance_voisins[val_dist] +" lbp_s=LBP_S_test lbp_m=LBP_M_test lbp_c=LBP_C_test";				
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
			
	} //For each kind of descriptor
			
			
		
		

	selectImage(LBP_S_lear);
	close();
	selectImage(LBP_M_lear);
	close();
	selectImage(LBP_C_lear);
	close();
	selectImage(LBP_S_test);
	close();
	selectImage(LBP_M_test);
	close();
	selectImage(LBP_C_test);
	close();	
		
	

}//For neighbors distance


selectImage(lear_image);
close();
selectImage(test_image);
close();	  
		
//}//For selected_dataset


	
Dialog.create("End");
Dialog.addMessage("Click OK to finish classification");
Dialog.show();
