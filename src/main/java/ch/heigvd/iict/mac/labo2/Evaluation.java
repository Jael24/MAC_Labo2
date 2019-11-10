package ch.heigvd.iict.mac.labo2;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.StopwordAnalyzerBase;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class Evaluation {

    private static Analyzer analyzer = null;

    private static void readFile(String filename, Function<String, Void> parseLine)
            throws IOException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(
                        new FileInputStream(filename),
                        StandardCharsets.UTF_8)
        )) {
            String line = br.readLine();
            while (line != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    parseLine.apply(line);
                }
                line = br.readLine();
            }
        }
    }

    /*
     * Reading CACM queries and creating a list of queries.
     */
    private static List<String> readingQueries() throws IOException {
        final String QUERY_SEPARATOR = "\t";

        List<String> queries = new ArrayList<>();

        readFile("evaluation/query.txt", line -> {
            String[] query = line.split(QUERY_SEPARATOR);
            queries.add(query[1]);
            return null;
        });
        return queries;
    }

    /*
     * Reading stopwords
     */
    private static List<String> readingCommonWords() throws IOException {
        List<String> commonWords = new ArrayList<>();

        readFile("common_words.txt", line -> {
            commonWords.add(line);
            return null;
        });
        return commonWords;
    }


    /*
     * Reading CACM qrels and creating a map that contains list of relevant
     * documents per query.
     */
    private static Map<Integer, List<Integer>> readingQrels() throws IOException {
        final String QREL_SEPARATOR = ";";
        final String DOC_SEPARATOR = ",";

        Map<Integer, List<Integer>> qrels = new HashMap<>();

        readFile("evaluation/qrels.txt", line -> {
            String[] qrel = line.split(QREL_SEPARATOR);
            int query = Integer.parseInt(qrel[0]);

            List<Integer> docs = qrels.get(query);
            if (docs == null) {
                docs = new ArrayList<>();
            }

            String[] docsArray = qrel[1].split(DOC_SEPARATOR);
            for (String doc : docsArray) {
                docs.add(Integer.parseInt(doc));
            }

            qrels.put(query, docs);
            return null;
        });
        return qrels;
    }

    public static void main(String[] args) throws IOException {
        ///
        /// Reading queries and queries relations files
        ///
        List<String> queries = readingQueries();
        System.out.println("Number of queries: " + queries.size());

        Map<Integer, List<Integer>> qrels = readingQrels();
        System.out.println("Number of qrels: " + qrels.size());

        double avgQrels = 0.0;
        for (int q : qrels.keySet()) {
            avgQrels += qrels.get(q).size();
        }
        avgQrels /= qrels.size();
        System.out.println("Average number of relevant docs per query: " + avgQrels);

        //TODO student: use this when doing the english analyzer + common words
        List<String> commonWords = readingCommonWords();

        ///
        ///  Part I - Select an analyzer
        ///
        analyzer = new StandardAnalyzer();
        //analyzer = new WhitespaceAnalyzer();


        ///
        ///  Part I - Create the index
        ///
        Lab2Index lab2Index = new Lab2Index(analyzer);
        lab2Index.index("documents/cacm.txt");

        ///
        ///  Part II and III:
        ///  Execute the queries and assess the performance of the
        ///  selected analyzer using performance metrics like F-measure,
        ///  precision, recall,...
        ///


        int queryNumber = 1;
        int totalRelevantDocs = 0;
        int totalRetrievedDocs = 0;
        int totalRetrievedRelevantDocs = 0;
        double avgPrecision = 0.0;
        double avgRPrecision = 0.0;
        double avgRecall = 0.0;
        double meanAveragePrecision = 0.0;
        double fMeasure = 0.0;
        // average precision at the 11 recall levels (0,0.1,0.2,...,1) over all queries
        double[] avgPrecisionAtRecallLevels = createZeroedRecalls();

        // For avgPrecision
        double addPrecision = 0.0;

        // For avgRecall
        double addRecall = 0.0;

        // For AP
        double addAP = 0.0;


        // For MAP
        double sumOfAP = 0.0;

        // For R-precision
        double R_Precision = 0.0;

        for(String query:queries) {
            // queryResults contient les documents remontés pour chaque query
            List<Integer> queryResults = lab2Index.search(query);

            // nombre de retrieved documents
            totalRetrievedDocs += queryResults.size();

            double precision = 0.0;
            double recall = 0.0;

            int truePositive = 0;
            int falsePositive = 0;
            int tpPlusFn = 0;

            double AP = 0.0;

            if(qrels.get(queryNumber) != null) {
                List<Integer> qrelResults = qrels.get(queryNumber);
                // nombre de relevant documents
                totalRelevantDocs += qrelResults.size();

                tpPlusFn = qrelResults.size();

                // nombre de retrieved relevant document ( intersection des retrieved et des relevant)
                truePositive = queryResults.stream()
                        .distinct()
                        .filter(qrelResults::contains)
                        .collect(Collectors.toList())
                        .size();


                totalRetrievedRelevantDocs += truePositive;

                //Calcul de AP (Average Precision)
                addAP = 0.0;
                int cntRetrieved = 0;


                for (int i = 0; i < queryResults.size(); ++i) {
                    if (qrelResults.contains(queryResults.get(i))) {
                        addAP += ((double)++cntRetrieved/(double)(i+1));
                    }

                    if (i+1 == qrelResults.size()) {
                        R_Precision += (double)cntRetrieved / (double) qrelResults.size();
                    }
                }
                
                // On divise la somme des précision par ne nombre de document pertinent dans la collection
                AP = addAP / qrelResults.size();
                //AP = addAP / queryResults.size();

            }
            falsePositive = queryResults.size() - truePositive;

            precision = (double)truePositive / (double)(truePositive + falsePositive);
            addPrecision += precision;

            if (tpPlusFn != 0) {
                recall = (double) truePositive / (double) tpPlusFn;
            } else {
                recall = 0;
            }
            addRecall += recall;

            sumOfAP += AP;

            ++queryNumber;
        }

        meanAveragePrecision = sumOfAP / queries.size();
        avgRPrecision = R_Precision / queries.size();

        avgPrecision = addPrecision / queries.size();
        avgRecall = addRecall / queries.size();
        fMeasure = (2*avgPrecision*avgRecall) / (avgPrecision+avgRecall);

        displayMetrics(totalRetrievedDocs,
                totalRelevantDocs,
                totalRetrievedRelevantDocs,
                avgPrecision,
                avgRecall,
                fMeasure,
                meanAveragePrecision,
                avgRPrecision,
                avgPrecisionAtRecallLevels);
    }

    private static void displayMetrics(
            int totalRetrievedDocs,
            int totalRelevantDocs,
            int totalRetrievedRelevantDocs,
            double avgPrecision,
            double avgRecall,
            double fMeasure,
            double meanAveragePrecision,
            double avgRPrecision,
            double[] avgPrecisionAtRecallLevels
    ) {
        String analyzerName = analyzer.getClass().getSimpleName();
        if (analyzer instanceof StopwordAnalyzerBase) {
            analyzerName += " with set size " + ((StopwordAnalyzerBase) analyzer).getStopwordSet().size();
        }
        System.out.println(analyzerName);

        System.out.println("Number of retrieved documents: " + totalRetrievedDocs);
        System.out.println("Number of relevant documents: " + totalRelevantDocs);
        System.out.println("Number of relevant documents retrieved: " + totalRetrievedRelevantDocs);

        System.out.println("Average precision: " + avgPrecision);
        System.out.println("Average recall: " + avgRecall);

        System.out.println("F-measure: " + fMeasure);

        System.out.println("MAP: " + meanAveragePrecision);

        System.out.println("Average R-Precision: " + avgRPrecision);

        System.out.println("Average precision at recall levels: ");
        for (int i = 0; i < avgPrecisionAtRecallLevels.length; i++) {
            System.out.println(String.format("\t%s: %s", i, avgPrecisionAtRecallLevels[i]));
        }
    }

    private static double[] createZeroedRecalls() {
        double[] recalls = new double[11];
        Arrays.fill(recalls, 0.0);
        return recalls;
    }
}