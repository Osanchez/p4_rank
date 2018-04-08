downloading dependecies for code: 
(Instructions for InteliJ)
Simple JSON needed for json file parsing
Simply download jar file and place in a new folder called "lib"
add library to project under project structure 

Building the code:
Code can be build by running the main class in the Index.java file

Running the code:
Make sure that the shakespeare json file is located in the main project folder
If you uncoment the function calls in the main method it will read the json
file and create an inverted index. After this the BM25 and QL variables are calculated
and the final score of each scene is made. These are stored in a hashmap with
key scene and value rank. The hashmap result for each query is passed to a file write 
method which prints these values to a file. If the rank is = 0 this scene is not added to 
the file.