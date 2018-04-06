import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.util.*;

public class Index {
    //Map<Term, HashMap<DocId, ArrayList<Positions>>>
    private HashMap<String, HashMap<String, ArrayList<Integer>>> dataStorage;
    private HashMap<String, ArrayList<Integer>> allScenes;
    private HashMap<String, String[]> sceneText;
    private double k1_value;
    private double k2_value;
    private double b_value;

    private Index() {
        dataStorage = new HashMap<>();
        sceneText = new HashMap<>();
        allScenes = new HashMap<>();
        k1_value = 1.2;
        k2_value = 100.0;
        b_value = 0.75;
    }

    public static void main(String[] args) throws IOException, ParseException {
        Index index = new Index();
        HashMap dataStorage = index.readJSON("shakespeare-scenes.json");
        //index.documentToString();
        index.allScenesToString();
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

    private double calculateBM25(String query) {

        double result = 0.00;
        String[] split_query = query.split("");

        //no relevance information, we can set r and R to 0, which would give a pi value of 0.5
        double R = 0.00; //number of relevant documents for the query
        double r_i = 0.00; //number of relevant documents containing term i

        double k_1 = k1_value; //set empirically
        double k_2 = k2_value; //set empirically


        //TODO: calculate these values
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

        //gets the average number of documents
        //average document length
        double avdl = 0.00; //average length of all documents
        double numberDocuments = allScenes.size();

        for(String key : allScenes.keySet()) {
            avdl += allScenes.get(key).size();
            numberDocuments++;
        }
        avdl = avdl/numberDocuments;

        //BM25 ranking //TODO: frequency of term in current document.
        double N = allScenes.size(); //number of documents in the collection (Scene is a document)
        double n_i; //number of documents containing term i
        double dl; //current document size (Words in the document maybe?)
        double f_i = 0; //frequency of term in current document

        for(String key : allScenes.keySet()) {
            dl = allScenes.get(key).size(); //size of current document
            for (int i = 0; i < split_query.length; i++) {
                double qf_i = frequencyOfTerm_Q.get(split_query[i]); //frequency of term i in the document
                double K = k_1 * ((1 - b_value) + b_value * (dl / avdl));
                HashMap<String, ArrayList<Integer>> possibleScenes = dataStorage.get(split_query[i]);
                n_i = possibleScenes.size();
                System.out.println(Math.log(((r_i + 0.5) / (R - r_i + 0.5) / ((n_i - r_i + 0.5) / (N - n_i - R + r_i + 0.5))) * (((k_1 + 1) * f_i) / (K + f_i)) * (((k_2 + 1) * qf_i) / (k_2 + qf_i))));
            }
        }

        return result;

    }

    private void writeTermFiles(String fileName, HashMap data)
            throws IOException {
        HashMap<String, ArrayList<String>> copyData = data;
        BufferedWriter writer = new BufferedWriter(new FileWriter(fileName));
        ArrayList<String> alphabeticallySorted = new ArrayList<>();
        for (String entry : copyData.keySet()) {
            ArrayList<String> dataEntries = copyData.get(entry);
            for (String dataentry : dataEntries) {
                if (!alphabeticallySorted.contains(dataentry)) {
                    alphabeticallySorted.add(dataentry);
                }
            }
        }
        Collections.sort(alphabeticallySorted);
        for (String entry : alphabeticallySorted) {
            writer.write(entry);
            writer.newLine();
        }
        writer.close();
    }

    private HashMap<String, ArrayList<String>> usedMoreThan(String[] words, String moreThanWord) { //scenes
        HashMap<String, ArrayList<String>> result = new HashMap<>();

        HashMap<String, HashMap<String, Integer>> wordCounts = new HashMap<>(); //words
        HashMap<String, Integer> morethanWordCounts = new HashMap<>();

        //word count for each scene moreThanWord appears in
        HashMap<String, ArrayList<Integer>> moreThanWordScenes = dataStorage.get(moreThanWord);
        for (String scene : moreThanWordScenes.keySet()) {
            morethanWordCounts.put(scene, moreThanWordScenes.get(scene).size());
        }

        //word count for each scene each word appears in
        for (String word : words) {
            HashMap<String, ArrayList<Integer>> wordScenes = dataStorage.get(word);
            HashMap<String, Integer> sceneWordCount = new HashMap<>();
            for (String scene : wordScenes.keySet()) {
                int wordCountScene = wordScenes.get(scene).size();
                sceneWordCount.put(scene, wordCountScene);
            }
            wordCounts.put(word, sceneWordCount);
        }

        //get union of words for each scene
        HashMap<String, Integer> unionScenes = new HashMap<>();
        for (String word : wordCounts.keySet()) {
            HashMap<String, Integer> scenes = wordCounts.get(word);
            for (String scene : scenes.keySet()) {
                if (unionScenes.containsKey(scene)) {
                    int current = unionScenes.get(scene);
                    int add = scenes.get(scene);
                    unionScenes.replace(scene, current + add);
                } else {
                    unionScenes.put(scene, scenes.get(scene));
                }
            }
        }

        //comparison for all scenes of other word
        ArrayList<String> finalScenes = new ArrayList<>();
        for (String scene : morethanWordCounts.keySet()) {
            try {
                int wordCountWords = unionScenes.get(scene);
                int wordCountMoreThanWord = morethanWordCounts.get(scene);
                if (wordCountWords > wordCountMoreThanWord) {
                    finalScenes.add(scene);
                }
            } catch (NullPointerException e) { //if the moreThanWord does not appear at all
                int wordCountWords = 0;
                int wordCountMoreThanWord = morethanWordCounts.get(scene);
                if (wordCountWords > wordCountMoreThanWord) {
                    finalScenes.add(scene);
                }
            }
        }

        //comparison for all scenes of words
        for (String scene : unionScenes.keySet()) {
            try {
                int wordCountWords = unionScenes.get(scene);
                int wordCountMoreThanWord = morethanWordCounts.get(scene);
                if (wordCountWords > wordCountMoreThanWord) {
                    finalScenes.add(scene);
                }
            } catch (NullPointerException e) { //if the moreThanWord does not appear at all
                int wordCountWords = unionScenes.get(scene);
                int wordCountMoreThanWord = 0;
                if (wordCountWords > wordCountMoreThanWord) {
                    finalScenes.add(scene);
                }
            }
        }

        result.put("Result", finalScenes);
        return result;
    }

    private HashMap isMentioned(String[] mentionedWord, String scene_play) { //scenes or play
        HashMap<String, ArrayList<String>> result = new HashMap<>();

        for (String word : mentionedWord) {
            ArrayList<String> scenes = new ArrayList<>();
            ArrayList<String> plays = new ArrayList<>();
            if (dataStorage.containsKey(word.toLowerCase())) {
                HashMap<String, ArrayList<Integer>> termValues = dataStorage.get(word.toLowerCase());
                for (String scene : termValues.keySet()) {
                    String play = scene.split(":")[0];
                    if (!plays.contains(play)) {
                        plays.add(play);
                    }
                    if (!scenes.contains(scene)) {
                        scenes.add(scene);
                    }
                }
                if (scene_play.equals("s")) {
                    result.put(word, scenes);
                } else if (scene_play.equals("p")) {
                    result.put(word, plays);
                }
            }
        }
        return result;
    }

    private HashMap phraseMentioned(String phrase) { //scenes
        HashMap<String, ArrayList<String>> result = new HashMap<>();
        ArrayList<String> scenes = new ArrayList<>();
        String[] splitPhrase = phrase.split(" ");
        int wordsInPhrase = splitPhrase.length;
        boolean phraseInScene;

        //scenes that contain the first word
        HashMap<String, ArrayList<Integer>> possibleScenes = dataStorage.get(splitPhrase[0]); //all scenes with first word
        for (String scene : possibleScenes.keySet()) { //for all scenes that have the first word
            ArrayList<Integer> wordIndex = possibleScenes.get(scene); //all indexes of first word in scene
            for (Integer index : wordIndex) { //for each index of first word
                phraseInScene = true; //by default start with true
                String[] text = sceneText.get(scene); //get the text for comparison
                for (int x = 0; x < wordsInPhrase; x++) { //iterate every word in phrase
                    if (!splitPhrase[x].equals(text[index + x])) { //check if next text word matches next phrase word
                        phraseInScene = false;
                        break;
                    }
                }
                if (phraseInScene) {
                    scenes.add(scene);
                    break;
                }
            }
        }
        result.put(phrase, scenes);
        return result;
    }

    public void documentToString() {
        for(Map.Entry entry : dataStorage.entrySet()) {
            System.out.println(entry);
        }
    }

    public void allScenesToString() {
        for(Map.Entry entry : allScenes.entrySet()) {
            System.out.println(entry);
        }
    }
}
