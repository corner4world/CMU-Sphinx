/*
 * Copyright 1999-2002 Carnegie Mellon University.  
 * Portions Copyright 2002 Sun Microsystems, Inc.  
 * Portions Copyright 2002 Mitsubishi Electric Research Laboratories.
 * All Rights Reserved.  Use is subject to license terms.
 * 
 * See the file "license.terms" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL 
 * WARRANTIES.
 *
 */

package tests.linguist.language.ngram.large;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

import edu.cmu.sphinx.linguist.WordSequence;
import edu.cmu.sphinx.linguist.dictionary.Dictionary;
import edu.cmu.sphinx.linguist.dictionary.FastDictionary;
import edu.cmu.sphinx.linguist.dictionary.Word;
import edu.cmu.sphinx.linguist.language.ngram.large.LargeTrigramModel;
import edu.cmu.sphinx.util.LogMath;
import edu.cmu.sphinx.util.Timer;
import edu.cmu.sphinx.util.Utilities;


/**
 * Reads a binary language model file generated by the 
 * CMU-Cambridge Statistical Language Modelling Toolkit.
 * 
 * Note that all probabilites in the grammar are stored in LogMath log
 * base format. Language Probabilties in the language model file are
 * stored in log 10  base. They are converted to the LogMath logbase.
 */
public class LargeTrigramModelTest {

    public static void main(String[] args) throws Exception {

        String propsPath;
        String testFile = null;
        PrintStream outStream = System.out;

        if (args.length == 0) {
            System.out.println
                ("Usage: java LargeTrigramModelTest <props_file> " +
                 "[<testFile>] " +
                 "<output_file>");
        }
        
        propsPath = args[0];
        if (args.length >= 2) {
            testFile = args[1];
        }
        if (args.length >= 3) {
            outStream = new PrintStream(new FileOutputStream(args[2]));
        }

        String context = "test";
        SphinxProperties.initContext(context, new URL(propsPath));
        SphinxProperties props = SphinxProperties.getSphinxProperties(context);

        Dictionary dictionary = new FastDictionary(context);
        LargeTrigramModel lm = new LargeTrigramModel(props, dictionary);

        LogMath logMath = LogMath.getLogMath();

        InputStream stream = new FileInputStream(testFile);

        BufferedReader reader = new BufferedReader
            (new InputStreamReader(stream));

        Timer timer = Timer.getTimer("lmLookup");
        
        String input;
        
        List wordSequences = new LinkedList();
        
        while ((input = reader.readLine()) != null) {

	    if (!input.equals("<START_UTT>") && !input.equals("<END_UTT>")) {
		StringTokenizer st = new StringTokenizer(input);
		List<Word> list = new ArrayList<Word>();
		while (st.hasMoreTokens()) {
		    String tok = st.nextToken().toLowerCase().trim();
		    list.add(dictionary.getWord(tok));
		}
		WordSequence wordSequence = new WordSequence(list);
		wordSequences.add(wordSequence);
	    }	    
        }

        int[] logScores = new int[wordSequences.size()];
        int s = 0;

        timer.start();

        for (Iterator i = wordSequences.iterator(); i.hasNext(); ) {
            lm.start();
            WordSequence ws = (WordSequence) i.next();
            logScores[s++] = (int)lm.getProbability(ws);
            lm.stop();
        }

        timer.stop();
        
        s = 0;
        for (Iterator i = wordSequences.iterator(); i.hasNext(); ) {
            WordSequence ws = (WordSequence) i.next();
            outStream.println(Utilities.pad(logScores[s++], 10) + " "+
                              getString(ws));
        }
        
        if (true) {
            long usedMemory = Runtime.getRuntime().totalMemory() - 
                Runtime.getRuntime().freeMemory();                
            System.out.println("Used memory: " + usedMemory + " bytes");
            System.out.println("Bigram misses: " + lm.getBigramMisses());
            System.out.println("Trigram misses: " + lm.getTrigramMisses());
        }
        
        
        Timer.dumpAll();
    }

    public static String getString(WordSequence ws) {
        String line = ws.getWord(0).getSpelling();
        for (int i = 1; i < ws.size(); i++) {
            line += (" " + ws.getWord(i).getSpelling());
        }
        return line.toUpperCase();
    }
}

