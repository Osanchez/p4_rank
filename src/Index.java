// Resource below was used to create method for sorting hashmaps by value
// http://www.java67.com/2015/01/how-to-sort-hashmap-in-java-based-on.html

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;

public class Index {
    //Map<Term, HashMap<DocId, ArrayList<Positions>>>
    private HashMap<String, HashMap<String, ArrayList<Integer>>> dataStorage;
    private HashMap<String, ArrayList<Integer>> allScenes;
    private HashMap<String, String[]> sceneText;
    private double k1_value;
    private double k2_value;
    private double b_value;
    private double mu;

    private Index() {
        dataStorage = new HashMap<>();
        sceneText = new HashMap<>();
        allScenes = new HashMap<>();
        k1_value = 1.2;
        k2_value = 100.0;
        b_value = 0.75;
        mu = 1500;
    }

    public static void main(String[] args) throws IOException, ParseException {
        Index index = new Index();
        HashMap dataStorage = index.readJSON("shakespeare-scenes.json");
        //index.calculateBM25("Q1","the king queen royalty");
        index.calculateBM25("Q2","servant guard soldier");
        //index.calculateBM25("Q3","hope dream sleep");
        //index.calculateBM25("Q4","ghost spirit");
        //index.calculateBM25("Q5","fool jester player");
        //index.calculateBM25("Q6","to be or not to be");

        //index.sceneTextToString();
    }

    private HashMap readJSON(String fileName) throws ParseException, IOException {
        JSONParser parser = new JSONParser();

        try {
            Object obj = parser.parse(new FileReader(fileName));
            JSONObject jsonObject = (JSONObject) obj;

            //loop
            JSONArray corpus = (JSONArray) jsonObject.get("corpus");

            Iterator<JSONObject> iterator = corpus.iterator();
            while (iterator.hasNext()) {
                JSONObject object = iterator.next();
                String sceneID = (String) object.get("sceneId");
                String text = (String) object.get("text");
                String[] text_array = text.split("\\s+");
                sceneText.put(sceneID, text_array); //helper collection for phrases
                allScenes.put(sceneID, new ArrayList<>());

                for (int x = 0; x < text_array.length; x++) {
                    if (dataStorage.containsKey(text_array[x])) {
                        HashMap<String, ArrayList<Integer>> valueMap = dataStorage.get(text_array[x]);
                        if (valueMap.containsKey(sceneID)) {
                            valueMap.get(sceneID).add(x);
                            dataStorage.replace(text_array[x], valueMap);
                            allScenes.get(sceneID).add(x);
                        } else {
                            ArrayList<Integer> newEntryList = new ArrayList<>();
                            newEntryList.add(x);
                            valueMap.put(sceneID, newEntryList);
                            allScenes.get(sceneID).add(x);
                        }
                    } else {
                        //create the value hash map for the new term
                        HashMap<String, ArrayList<Integer>> newEntryMap = new HashMap<>();
                        ArrayList<Integer> newEntryList = new ArrayList<>();
                        newEntryList.add(x);
                        newEntryMap.put(sceneID, newEntryList);

                        //add key and hash map to top level hash map
                        dataStorage.put(text_array[x], newEntryMap);
                        allScenes.get(sceneID).add(x);
                    }

                }

            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return dataStorage;
    }

    private void calculateBM25(String queryLabel, String query) {
        HashMap<String, Double> calculatedResults = new HashMap<>();

        String[] split_query = query.split(" ");

        //no relevance information, we can set r and R to 0, which would give a pi value of 0.5
        double R = 0.00; //number of relevant documents for the query
        double r_i = 0.00; //number of relevant documents containing term i

        double k_1 = k1_value; //set empirically
        double k_2 = k2_value; //set empirically


        //gets the frequency of each word in the query and stores it to avoid having to loop every word in the query each time
        HashMap<String, Integer> frequencyOfTerm_Q = new HashMap<>();
        for(String word: split_query) {
            if(frequencyOfTerm_Q.containsKey(word)) {
                continue; //skip word if its already been counted.
            }
            int count = 0;
            for(int x = 0 ; x < split_query.length; x++) {
                if(split_query[x].equals(word)) {
                    count++;
                }
            }
            if(!frequencyOfTerm_Q.containsKey(word)) {
                frequencyOfTerm_Q.put(word, count);
            }
        }

        //gets the average document length
        double avdl = 0.00; //average length of all documents
        int numberDocuments = allScenes.size();

        for(String key : allScenes.keySet()) {
            avdl += allScenes.get(key).size();
            numberDocuments++;
        }
        avdl = avdl/numberDocuments;

        //BM25 ranking
        int N = allScenes.size(); //number of documents in the collection (Scene is a document)
        int n_i; //number of documents containing term i
        double qf_i; //frequency of term i in query
        int f_i = 0; //frequency of term i in current document
        int dl;

        for(String key : allScenes.keySet()) { //for every scene
            dl = allScenes.get(key).size(); //size of current document (Scene)
            double result = 0.00;
            for (String queryWord : split_query) { //for all words in the query
                qf_i = frequencyOfTerm_Q.get(queryWord); //frequency of term i in the document
                double K = k_1 * ((1 - b_value) + b_value * (dl / avdl));
                HashMap<String, ArrayList<Integer>> queryScenes = dataStorage.get(queryWord);
                n_i = queryScenes.size(); //size;

                //frequency of term in current document
                String[] currentDocumentText = sceneText.get(key);
                for (String word : currentDocumentText) {
                    if (word.equals(queryWord)) {
                        f_i++;
                    }
                }
                //calculate BM25 Score
                double var1 = ((r_i + 0.5)/(R - r_i + 0.5))/((n_i - r_i + 0.5)/(N - n_i - R + r_i + 0.5));
                var1 = Math.log(var1);
                double var2 = ((k_1 + 1) * f_i)/(K + f_i);
                double var3 = ((k_2 + 1) * qf_i)/(k_2 + qf_i);

                double resultingVar;


                if (var2 == 0)
                    resultingVar = var1 * var3;
                else if (var3 == 0)
                    resultingVar = var1 * var2;
                else
                    resultingVar = var1 * var2 * var3;

                result += resultingVar;

            }
            calculatedResults.put(key, result);
        }

        Comparator<Map.Entry<String, Double>> valueComparator = (o1, o2) -> {
            if(o1.getValue() > o2.getValue()) {
                return -1;
            }
            if(o1.getValue() < o2.getValue()) {
                return 1;
            } else {
                return 0;
            }
        };

        Set<Map.Entry<String, Double>> entries = calculatedResults.entrySet();

        List<Map.Entry<String, Double>> listOfEntries = new ArrayList<>(entries);

        Collections.sort(listOfEntries, valueComparator);

        //new hash map of sorted values
        LinkedHashMap<String, Double> sortedByValue = new LinkedHashMap<>(listOfEntries.size());

        //add values back to hash map
        for(Map.Entry<String, Double> entry : listOfEntries){
            sortedByValue.put(entry.getKey(), entry.getValue());
        }

        int sceneRank = 1;

        //TODO: optional column formatting
        DecimalFormat dec = new DecimalFormat("#0.000");
        for(Map.Entry entry : sortedByValue.entrySet()) {
            String scene = entry.getKey().toString();
            Double rankValue = (Double) entry.getValue();
            System.out.println(queryLabel + " " + scene + "\t\t\t\t\t" + sceneRank + " " + dec.format(rankValue) + " Osanchez-bm25");
            sceneRank++;
        }

    }

    public void documentToString() {
        for(Map.Entry entry : dataStorage.entrySet()) {
            System.out.println(entry);
        }
    }

    public void allScenesToString() {
        for (Map.Entry entry : allScenes.entrySet()) {
            System.out.println(entry);
        }
    }

    public void sceneTextToString() {
        for(Map.Entry entry : sceneText.entrySet()) {
            System.out.print(entry.getKey() + ": ");
            System.out.println(Arrays.toString((String[]) entry.getValue()));
        }
    }
}
