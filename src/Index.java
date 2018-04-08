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
        index.readJSON("shakespeare-scenes.json");

        //BM25 Calculations
        //TODO: For testing I used .txt files, but assignment wants .trecrun files. Simply change extension
        /*
        HashMap bm25_q1 = index.calculateBM25("Q1","the king queen royalty");
        index.writeFiles("bm25.txt", "Q1", bm25_q1);

        HashMap bm25_q2 = index.calculateBM25("Q2","servant guard soldier");
        index.writeFiles("bm25.txt", "Q2", bm25_q2);

        HashMap bm25_q3 = index.calculateBM25("Q3","hope dream sleep");
        index.writeFiles("bm25.txt", "Q3", bm25_q3);

        HashMap bm25_q4 = index.calculateBM25("Q4","ghost spirit");
        index.writeFiles("bm25.txt", "Q4", bm25_q4);

        HashMap bm25_q5 = index.calculateBM25("Q5","fool jester player");
        index.writeFiles("bm25.txt", "Q5", bm25_q5);

        HashMap bm25_q6 = index.calculateBM25("Q6","to be or not to be");
        index.writeFiles("bm25.txt", "Q6", bm25_q6);
        */

        //Query Likelihood
        /*
        HashMap ql_q1 = index.calculateQL("Q1","the king queen royalty");
        index.writeFiles("ql.txt", "Q1", ql_q1);

        HashMap ql_q2 = index.calculateQL("Q2","servant guard soldier");
        index.writeFiles("ql.txt", "Q2", ql_q2);

        HashMap ql_q3 = index.calculateQL("Q3","hope dream sleep");
        index.writeFiles("ql.txt", "Q3", ql_q3);

        HashMap ql_q4 = index.calculateQL("Q4","ghost spirit");
        index.writeFiles("ql.txt", "Q4", ql_q4);

        HashMap ql_q5 = index.calculateQL("Q5","fool jester player");
        index.writeFiles("ql.txt", "Q5", ql_q5);

        HashMap ql_q6 = index.calculateQL("Q6","to be or not to be");
        index.writeFiles("ql.txt", "Q6", ql_q6);
        */

        //index.sceneTextToString();
        //index.documentToString();
    }

    private void writeFiles(String fileName, String queryLabel, HashMap<String, Double> results) {
        BufferedWriter writer = null;
        try {
            //create a temporary file
            File logFile = new File(fileName);

            writer = new BufferedWriter(new FileWriter(logFile, true));

            int sceneRank = 1;
            DecimalFormat dec = new DecimalFormat("#0.000");
            for(Map.Entry entry : results.entrySet()) {
                String scene = (String) entry.getKey();
                double rankValue = (double) entry.getValue();
                if(rankValue == 0) {
                    continue;
                }
                String line = String.format("%s %s %-32s %s %s %s\n", queryLabel, "skip", scene, sceneRank, dec.format(rankValue), "Osanchez-bm25");
                writer.write(line);
                sceneRank++;
            }
            writer.newLine();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                // Close the writer regardless of what happens...
                writer.close();
            } catch (Exception e) {
            }
        }
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

    private HashMap calculateBM25(String queryLabel, String query) {
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

        for(String key : allScenes.keySet()) { //Dictionary -  String SceneID : String[] Text
            avdl += allScenes.get(key).size();
        }
        avdl = avdl/numberDocuments;

        //BM25 ranking
        int N = allScenes.size(); //number of documents in the collection (Scene is a document)
        int n_i; //number of documents containing term i
        double qf_i; //frequency of term i in query
        int f_i; //frequency of term i in current document
        int dl;

        for(String key : allScenes.keySet()) { //for every scene
            dl = allScenes.get(key).size(); //size of current document (Scene)
            double result = 0.00;
            for (String queryWord : split_query) { //for all words in the query
                f_i = 0;
                qf_i = frequencyOfTerm_Q.get(queryWord); //frequency of term i in the document
                double K = k_1 * ((1 - b_value) + (b_value * (dl / avdl)));
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

        DecimalFormat dec = new DecimalFormat("#0.000");
        for(Map.Entry entry : sortedByValue.entrySet()) {
            String scene = entry.getKey().toString();
            Double rankValue = (Double) entry.getValue();
            System.out.format("%s %s %-32s %s %s %s\n", queryLabel, "skip", scene, sceneRank, dec.format(rankValue), "Osanchez-bm25");
            sceneRank++;
        }
        return sortedByValue;
    }

    public double getOccurrencesCollection(HashMap<String, ArrayList<Integer>> documents) {
        int occurrences = 0;
        for(Map.Entry keyValue : documents.entrySet()) {
            ArrayList<Integer> scene = (ArrayList) keyValue.getValue();
            occurrences += scene.size();
        }
        return occurrences;
    }

    public double getOccurrencesDocument(String queryWord, String[] documentText) {
        int occurrences = 0;
        for(String word : documentText) {
            if(word.equals(queryWord)) {
                occurrences ++;
            }
        }
        return occurrences;
    }

    public double getWordOccurrencesCollection(HashMap<String, String[]> collection) {
        int numberWords = 0;
        for(String[] entry : collection.values()) {
            numberWords += entry.length;
        }
        return numberWords;
    }

    private HashMap calculateQL(String queryLabel, String query) {
        String[] splitQuery = query.split(" ");

        HashMap<String, Double> results = new HashMap<>();

        double fqid; //number of times word qi occurs in document D
        double cqi; //number of times a query word occurs in collection of documents
        double totalC = getWordOccurrencesCollection(sceneText); //total number of word occurrences in collection
        double totalD; //number of words in current document


        for(String key : allScenes.keySet()) {
            double total = 0.00;
            double log;
            totalD = sceneText.get(key).length;
            //for every scene
            for (String queryWord : splitQuery) {
                if (dataStorage.get(queryWord) != null) { //collection of documents contains the query word
                    HashMap<String, ArrayList<Integer>> possibleScenes = dataStorage.get(queryWord);
                    cqi = getOccurrencesCollection(possibleScenes);
                    fqid = getOccurrencesDocument(queryWord, sceneText.get(key)); //get occurrences of word in scene
                } else { //collection of documents does not contain the query word
                    fqid = 0.00;
                    cqi = 0.00; //value close to 0
                }
                double numerator = fqid + (mu * (cqi/totalC));
                double denominator = totalD + mu;
                double result = numerator/denominator;
                if(result != 0) {
                    log = Math.log(result);
                } else {
                    log = 0;
                }
                total += log;
            }
            results.put(key, total);
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

        Set<Map.Entry<String, Double>> entries = results.entrySet();

        List<Map.Entry<String, Double>> listOfEntries = new ArrayList<>(entries);

        Collections.sort(listOfEntries, valueComparator);

        //new hash map of sorted values
        LinkedHashMap<String, Double> sortedByValue = new LinkedHashMap<>(listOfEntries.size());

        //add values back to hash map
        for(Map.Entry<String, Double> entry : listOfEntries){
            sortedByValue.put(entry.getKey(), entry.getValue());
        }

        int sceneRank = 1;

        DecimalFormat dec = new DecimalFormat("#0.000");
        for(Map.Entry entry : sortedByValue.entrySet()) {
            String scene = entry.getKey().toString();
            Double rankValue = (Double) entry.getValue();
            System.out.format("%s %s %-32s %s %s %s\n", queryLabel, "skip", scene, sceneRank, dec.format(rankValue), "Osanchez-ql");
            sceneRank++;
        }
        return sortedByValue;
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
