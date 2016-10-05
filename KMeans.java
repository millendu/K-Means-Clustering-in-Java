package edu.asu.irs13;

import org.apache.lucene.index.*;
import org.apache.lucene.queryParser.QueryParserTokenManager;
import org.apache.lucene.store.*;
import org.apache.lucene.document.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.Map.Entry;

public class KMeans {

	public static HashMap<Integer, HashMap<String, Integer>> hMap;

	public static HashMap<Integer, HashMap<String, Integer>> tfIDFdocs = new HashMap<Integer, HashMap<String, Integer>>();
	public static Set<String> wordsSet = new HashSet<String>();
	public static HashMap<Integer, HashMap<String, Double>> tfIDFdocwordMap = new HashMap<Integer, HashMap<String, Double>>();

	public static void main(String[] args) throws Exception {
		// the IndexReader object is the main handle that will give you
		// all the documents, terms and inverted index
		IndexReader r = IndexReader.open(FSDirectory.open(new File("index")));

		int i = 0;

		System.out.println("Please wait for initialization");
		double stamp1, stamp2;
		stamp1 = System.currentTimeMillis();

		// You can find out all the terms that have been indexed using the
		// terms() function
		// Creating an HashMap of HashMap with document id,term and term freq in
		// the respective document
		hMap = new HashMap<Integer, HashMap<String, Integer>>();
		TermEnum t = r.terms();
		while (t.next()) {
			Term te = new Term("contents", t.term().text());
			TermDocs td = r.termDocs(te);

			while (td.next()) {
				int val = td.freq();
				String term = t.term().text();
				HashMap<String, Integer> tMap = hMap.get(td.doc());
				if (tMap == null) {
					tMap = new HashMap<String, Integer>();
					tMap.put(term, val);
					hMap.put(td.doc(), tMap);
				} else {
					tMap.put(term, val);
				}
			}
		}

		HashMap<Integer, Double> docNormMap = new HashMap<Integer, Double>();
		Iterator it = hMap.entrySet().iterator();

		while (it.hasNext()) {
			HashMap.Entry presentIter = (HashMap.Entry) it.next();
			Integer presentKey = (Integer) presentIter.getKey();
			HashMap<String, Integer> termDocFreq = hMap.get(presentIter.getKey());

			Iterator innerIterator = termDocFreq.entrySet().iterator();
			Double docNorm = 0.0;

			while (innerIterator.hasNext()) {

				HashMap.Entry presentInnerIter = (HashMap.Entry) innerIterator.next();

				Integer presentFrequencyValue = (Integer) presentInnerIter.getValue();
				docNorm += presentFrequencyValue * presentFrequencyValue;
			}

			docNorm = Math.sqrt(docNorm);

			docNormMap.put(presentKey, docNorm);
		}

		Scanner sc = new Scanner(System.in);
		String str = "";
		System.out.print("query> ");

		while (!(str = sc.nextLine()).equals("quit")) {
			double stamp5, stamp6;
			stamp5 = System.currentTimeMillis();

			// Creating an HashMap with the terms in query and termFreq of each
			// term in query
			HashMap<String, Integer> query = new HashMap<String, Integer>();
			Double queryNorm = 0.0;
			String[] terms = str.split("\\s+");
			for (String word : terms) {
				int num = 0;
				if (query.get(word) == null) {
					num = 1;
				} else {
					num = query.get(word);
					num++;
				}
				query.put(word, num);
			}

			// Calculating the queryNorm
			Iterator queryIterator = query.entrySet().iterator();
			while (queryIterator.hasNext()) {

				HashMap.Entry presentQueryIter = (HashMap.Entry) queryIterator.next();

				Integer presentQueryFreqValue = (Integer) presentQueryIter.getValue();
				queryNorm += presentQueryFreqValue * presentQueryFreqValue;
			}

			queryNorm = Math.sqrt(queryNorm);

			// System.out.println(queryNorm);
			// Making a hashMap with documentId and similarity based on TFIDF
			HashMap<Integer, Double> TFidf = null;
			for (String word : terms) {
				double dotProduct;
				double denominator;
				Term term = new Term("contents", word);
				TermDocs tdocs = r.termDocs(term);
				TFidf = new HashMap<Integer, Double>();
				while (tdocs.next()) {
					// if(tdocs.doc() < 25000){
					if (TFidf.get(tdocs.doc()) == null) {
						dotProduct = 0;
					} else {
						dotProduct = TFidf.get(tdocs.doc());
					}
					HashMap<String, Integer> dotDoc = hMap.get(tdocs.doc());
					dotProduct += query.get(word) * dotDoc.get(word) * Math.log(r.maxDoc() / r.docFreq(term));
					denominator = queryNorm * docNormMap.get(tdocs.doc());

					dotProduct = dotProduct / denominator;

					TFidf.put(tdocs.doc(), dotProduct);
				}
				// }
			}

			// Printing out the 10 results
			int res = 0;
			for (Integer k : TFidf.keySet()) {
				if (res++ == 10)
					break;
				// System.out.println(k + "=" + TFidf.get(k));
			}

			Set<Entry<Integer, Double>> set = TFidf.entrySet();
			List<Entry<Integer, Double>> list = new ArrayList<Entry<Integer, Double>>(set);
			Collections.sort(list, new Comparator<Map.Entry<Integer, Double>>() {
				public int compare(Map.Entry<Integer, Double> o1, Map.Entry<Integer, Double> o2) {
					return (o2.getValue()).compareTo(o1.getValue());
				}
			});
			int count = 0;
			for (Map.Entry<Integer, Double> entry : list) {
				if (count++ == 50)
					break;

				tfIDFdocs.put(entry.getKey(), hMap.get(entry.getKey()));

			}

			//for(int k = 3; k<=10;k++) {
			kMeans(tfIDFdocs, 3);
			//}
			System.out.print("query> ");

			TFidf.clear();
			tfIDFdocs.clear();
			wordsSet.clear();
			tfIDFdocwordMap.clear();
		}
	}

	public static void kMeans(HashMap<Integer, HashMap<String, Integer>> docs, int k) throws Exception {

		double stamp_initial, stamp_final;
		stamp_initial = System.currentTimeMillis();
		tfIDFdocWordMap(docs); //Creating normalized word doc matrix for top 50 docs
		
		//Map for storing the centroids
		HashMap<Integer, HashMap<String, Double>> centroids = new HashMap<Integer, HashMap<String, Double>>();
		
		//Map for storing the clusters
		HashMap<Integer, HashSet<Integer>> clusters = new HashMap<Integer, HashSet<Integer>>();

		Set<Integer> keys = tfIDFdocwordMap.keySet();
		HashSet<Integer> clusterkeys = new HashSet<Integer>();
		
		//Initialiazation of clusters
		for(int cluster = 0; cluster<k; cluster++)
			clusterkeys.add(cluster+1);
		
		int i = 1;
		//Taking the first k docs to e the centroids of k different clusters for kMeans
		for (Integer key : keys) {
			HashMap<String, Double> wordFreq = tfIDFdocwordMap.get(key);
			centroids.put(i, wordFreq);
			clusters.put(i, new HashSet<Integer>());//Initially there wont be any docs in cluster
			clusters.get(i).add(key);
			i++;
			if (i > k)
				break;
		}

		//To store the previous cluster for check in future
		HashMap<Integer, HashSet<Integer>> tempCluster = new HashMap<Integer, HashSet<Integer>>();
		while (true) {
			double max;
			double dist;
			Integer cNo = 0;

			for (Integer key : keys) {
				HashMap<String, Double> wordMap = tfIDFdocwordMap.get(key);
				max = 0;
				cNo = 0;
				for (Integer ckey : clusterkeys) {
					HashMap<String, Double> temp = centroids.get(ckey);
					dist = cosineSimilarity(wordMap, temp);
					if (dist > max) {
						max = dist;
						cNo = ckey;
					}
				}
				if (tempCluster.get(cNo) == null) {
					tempCluster.put(cNo, new HashSet<Integer>());
				}
				tempCluster.get(cNo).add(key);
			}
			
			//Check if the  clusters converged
			boolean flag = true;
			for (Integer ckey : clusterkeys) {
				HashSet<Integer> temp1 = clusters.get(ckey);
				HashSet<Integer> temp2 = tempCluster.get(ckey);

				if (temp1 != null && temp2 != null) {
					if (!temp1.equals(temp2))//check whether the clusters are same after an iteration
						flag = false;
				}
			}

			//If clusters converged
			if (flag == true) {
				for (Integer clusterNo : clusterkeys) {
					System.out.println("Cluster " + clusterNo);
					HashSet<Integer> temp2 = tempCluster.get(clusterNo);
					if (temp2 != null) {
						for (Integer doc : temp2) {
							IndexReader r = IndexReader.open(FSDirectory.open(new File("index")));
							Document docno = r.document(doc);
							String url = docno.getFieldable("path").stringValue();
							System.out.println(doc + "-" + url);
						}
					}
				}
				break;
			} else {//If clusters have not converged repeat the process
				centroids.clear();
				clusters.clear();
				for (Integer ckey : clusterkeys) {
					HashSet<Integer> temp = tempCluster.get(ckey);
					HashMap<String, Double> tempwordFreq = centroidComputation(temp);
					centroids.put(ckey, tempwordFreq);
					clusters.put(ckey, temp);
				}
				tempCluster.clear();
			}
		}
		stamp_final = System.currentTimeMillis();
		System.out.println("Time for kMeans = " + (stamp_final - stamp_initial)/1000 + "seconds" );
	}

	//Function to create the normalized docWord map for top 50 docs
	public static void tfIDFdocWordMap(HashMap<Integer, HashMap<String, Integer>> docs)
			throws CorruptIndexException, IOException {
		Set<Integer> keys = docs.keySet();

		for (Integer key : keys) {
			HashMap<String, Integer> wc = docs.get(key);
			Set<String> keys2 = wc.keySet();

			for (String key2 : keys2) {
				if (!wordsSet.contains(key2)) {
					wordsSet.add(key2);
				}
			}
		}

		HashMap<Integer, Integer> maxterm = new HashMap<Integer, Integer>();
		int max;
		for (Integer key : keys) {
			HashMap<String, Integer> wc = docs.get(key);
			Set<String> keys2 = wc.keySet();
			max = 0;

			for (String key2 : keys2) {
				if (max < wc.get(key2)) {
					max = wc.get(key2);
				}
			}
			maxterm.put(key, max);
		}

		for (Integer key : keys) {
			HashMap<String, Integer> wc = docs.get(key);
			HashMap<String, Double> temp = new HashMap<String, Double>();
			IndexReader r = IndexReader.open(FSDirectory.open(new File("index")));

			for (String key2 : wordsSet) {
				if (wc.containsKey(key2)) {
					Term te = new Term("contents", key2);
					double t = (double) wc.get(key2) / maxterm.get(key);
					t = t * Math.log(r.maxDoc() / r.docFreq(te));
					temp.put(key2, t);
				} else {
					temp.put(key2, 0.0);
				}
			}
			tfIDFdocwordMap.put(key, temp);
		}
	}
	
	//Function for computing the centroid for a cluster
	public static HashMap<String, Double> centroidComputation(Set<Integer> setDocs) {

		HashMap<String, Double> centroid = new HashMap<String, Double>();

		for (String word : wordsSet) {
			if (setDocs != null) {
				double sum = 0.0;
				for (Integer doc : setDocs) {
					sum += tfIDFdocwordMap.get(doc).get(word);
				}
				sum = sum / setDocs.size();
				centroid.put(word, sum);
			} else {
				centroid.put(word, 0.0);
			}
		}
		return centroid;
	}

	//Function for computing cosine similarity
	private static double cosineSimilarity(HashMap<String, Double> centroid, HashMap<String, Double> doc) {
		double numerator = 0.0;
		double centroidNorm = 0.0;
		double docNorm = 0.0;

		for (String word : wordsSet) {
			numerator += centroid.get(word) * doc.get(word);
			docNorm += doc.get(word) * doc.get(word);
			centroidNorm += centroid.get(word) * centroid.get(word);
		}
		if (centroidNorm == 0.0 || docNorm == 0) {
			return 0.0;
		}
		double denominator = Math.sqrt(centroidNorm) * Math.sqrt(docNorm);

		return numerator / denominator;
	}
}
