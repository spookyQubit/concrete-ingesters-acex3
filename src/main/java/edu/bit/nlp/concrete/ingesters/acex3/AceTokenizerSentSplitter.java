package edu.bit.nlp.concrete.ingesters.acex3;


import edu.jhu.hlt.concrete.Communication;
import edu.jhu.hlt.concrete.Section;
import edu.jhu.hlt.concrete.TextSpan;
import edu.stanford.nlp.ie.machinereading.domains.ace.reader.*;
import edu.stanford.nlp.ie.machinereading.domains.ace.reader.RobustTokenizer.WordToken;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.PTBTokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.TreebankLanguagePack;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Collection;
import edu.stanford.nlp.pipeline.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations.CorefChainAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.util.Filters;

/**
 * The methods in this class were copied with modifications from
 * edu.stanford.nlp.ie.machinereading.domains.ace.reader.AceDocument.
 */
public class AceTokenizerSentSplitter {

    private static final Logger log = LoggerFactory.getLogger(AceTokenizerSentSplitter.class);

    // list of tokens which mark sentence boundaries
    private final static String[] sentenceFinalPunc = new String[]{".", "!", "?"};
    private static final Set<String> sentenceFinalPuncSet = new HashSet<String>();

    static {
        // set up sentenceFinalPuncSet
        for (int i = 0; i < sentenceFinalPunc.length; i++)
            sentenceFinalPuncSet.add(sentenceFinalPunc[i]);
    }

    public static List<List<WordTokenPOSDep>> tokenizeAndSentenceSegment(LexicalizedParser lp, AceDocument apfDoc, Communication comm) {
        List<List<WordTokenPOSDep>> sentences = new ArrayList<List<WordTokenPOSDep>>();

        // Create the initial tokenization and sentence segmentation using Stanford
        // tools.
        for (Section section : comm.getSectionList()) {
            TextSpan secSpan = section.getTextSpan();
            String input = comm.getText().substring(secSpan.getStart(), secSpan.getEnding());
            tokenizeAndSentenceSegment(lp, input, secSpan.getStart(), sentences);
        }

        // NOTE: We used to call this when our tokenization was non-destructive.
        // Now, we expand out the entity mentions to encapsulate the tokens instead.
        //
        // Split any token which has an internal entity boundary.
        // sentences = splitTokensForEntities(apfDoc, sentences);

        // Merge sentences such that each event spans only one sentence.
        sentences = mergeSentencesForEvents(apfDoc, sentences);

        // Log sentences with offsets.
        for (int i = 0; i < sentences.size(); i++) {
            List<WordTokenPOSDep> sent = sentences.get(i);
            log.trace("Sentence i=" + i + ": " + sent);
        }
        // Log sentences as tokens only.
        for (int i = 0; i < sentences.size(); i++) {
            List<WordTokenPOSDep> sent = sentences.get(i);
            StringBuilder sb = new StringBuilder();
            for (WordTokenPOSDep tok : sent) {
                sb.append(tok.getWord());
                sb.append(" ");
            }
            log.trace("Sentence i=" + i + ": " + sb.toString());
        }

        return sentences;
    }

    private static List<List<WordTokenPOSDep>> mergeSentencesForEvents(AceDocument apfDoc, List<List<WordTokenPOSDep>> sentences) {
        // If any event span contains a sentence boundary, merge the two
        // sentences on either side of that sentence boundary.
        int[] sentBreaks = getSentBoundaries(sentences);
        for (AceEventMention aEm : apfDoc.getEventMentions().values()) {
            int start = Integer.MAX_VALUE;
            int end = Integer.MIN_VALUE;
            for (AceEventMentionArgument aEmArg : aEm.getArgs()) {
                AceCharSeq ext = aEmArg.getContent().getExtent();
                start = Math.min(ext.getByteStart(), start);
                // Note: the entity mention extents in ACE are inclusive, so add one.
                end = Math.max(ext.getByteEnd() + 1, end);
            }
            sentBreaks = mergeIfSpanCrossesSents(sentences, sentBreaks, start, end, aEm.toString());
        }
        // If an entity cross a sentence boundary, merge the two sentences.
        for (AceEntityMention aEm : apfDoc.getEntityMentions().values()) {
            int start = aEm.getExtent().getByteStart();
            // Note: the entity mention extents in ACE are inclusive, so add one.
            int end = aEm.getExtent().getByteEnd() + 1;
            sentBreaks = mergeIfSpanCrossesSents(sentences, sentBreaks, start, end, aEm.toString());
        }
        return sentences;
    }


    private static List<List<WordTokenPOSDep>> mergeSentencesForRelations(AceDocument apfDoc, List<List<WordTokenPOSDep>> sentences) {
        // If any relation span contains a sentence boundary, merge the two
        // sentences on either side of that sentence boundary.
        int[] sentBreaks = getSentBoundaries(sentences);
        for (AceRelationMention aRm : apfDoc.getRelationMentions().values()) {
            int start = Integer.MAX_VALUE;
            int end = Integer.MIN_VALUE;
            for (AceRelationMentionArgument aRmArg : aRm.getArgs()) {
                AceCharSeq ext = aRmArg.getContent().getExtent();
                start = Math.min(ext.getByteStart(), start);
                // Note: the entity mention extents in ACE are inclusive, so add one.
                end = Math.max(ext.getByteEnd() + 1, end);
            }
            sentBreaks = mergeIfSpanCrossesSents(sentences, sentBreaks, start, end, aRm.toString());
        }
        // If an entity cross a sentence boundary, merge the two sentences.
        for (AceEntityMention aEm : apfDoc.getEntityMentions().values()) {
            int start = aEm.getExtent().getByteStart();
            // Note: the entity mention extents in ACE are inclusive, so add one.
            int end = aEm.getExtent().getByteEnd() + 1;
            sentBreaks = mergeIfSpanCrossesSents(sentences, sentBreaks, start, end, aEm.toString());
        }
        return sentences;
    }

    /**
     * @param start Beginning of the span (inclusive).
     * @param end   End of the span (exclusive).
     */
    private static int[] mergeIfSpanCrossesSents(List<List<WordTokenPOSDep>> sentences, int[] sentBreaks, int start, int end,
                                                 String descr) {
        boolean fixing = false;
        do {
            fixing = false;
            for (int i = 0; i < sentBreaks.length; i++) {
                if (start < sentBreaks[i] && sentBreaks[i] < end) {
                    // Merge the two sentences.
                    List<WordTokenPOSDep> sent1 = sentences.get(i - 1);
                    List<WordTokenPOSDep> sent2 = sentences.get(i);

                    // Union operation.
                    List<WordTokenPOSDep> merged = Stream.concat(sent1.stream(), sent2.stream()).distinct()
                            .collect(Collectors.toList());
                    sentences.set(i - 1, merged);
                    sentences.remove(i);
                    log.warn(
                            String.format("Merged sents: reason=%s\n\tsent1=%s\n\tsent2=%s", descr, sentStr(sent1), sentStr(sent2)));
                    // Reset the sentence boundaries.
                    sentBreaks = getSentBoundaries(sentences);
                    // Try again.
                    fixing = true;
                    break; // TODO: could be i--.
                }
            }
        } while (fixing);
        return sentBreaks;
    }

    private static String sentStr(List<WordTokenPOSDep> sent) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sent.size(); i++) {
            WordTokenPOSDep tok = sent.get(i);
            if (i != 0) {
                sb.append(" ");
            }
            sb.append(tok.getWord());
        }
        return sb.toString();
    }

    private static int[] getSentBoundaries(List<List<WordTokenPOSDep>> sentences) {
        List<Integer> blist = new ArrayList<>();
        for (List<WordTokenPOSDep> sent : sentences) {
            blist.add(sent.get(0).getStart());
        }
        int[] barray = new int[blist.size()];
        for (int i = 0; i < barray.length; i++)
            barray[i] = blist.get(i);
        return barray;
    }

    /**
     * This method is similar to
     * AceSentenceSegmenter.tokenizeAndSegmentSentences(), however, this one uses
     * the PTBTokenizer instead of the RobustTokenizer. In addition, the SGML tags
     * have already divided the text into segments, so this method doesn't need to
     * worry about them when sentence splitting. Lastly, the given offset is added
     * to each of the tokens to account for the section's position in the whole
     * document.
     *
     * @param input
     * @param offset
     * @param outSents
     */
    private static void tokenizeAndSentenceSegment(LexicalizedParser lp, String input, int offset,
                                                   List<List<WordTokenPOSDep>> outSents) {
        // now we can split the text into tokens

        StringReader r = new StringReader(input);
        // We use the invertible option:
        //
        // The keys used in it are: TextAnnotation for the tokenized form,
        // OriginalTextAnnotation
        // for the original string, BeforeAnnotation and AfterAnnotation for the
        // whitespace before
        // and after a token, and perhaps CharacterOffsetBeginAnnotation and
        // CharacterOffsetEndAnnotation to record token begin/after end character
        // offsets, if they
        // were specified to be recorded in TokenFactory construction. (Like the
        // String class, begin
        // and end are done so end - begin gives the token length.) Default is
        // false.
        PTBTokenizer<CoreLabel> tokenizer = new PTBTokenizer<>(r, new CoreLabelTokenFactory(),
                "invertible,ptb3Escaping=true");
        List<CoreLabel> coreLabelList = tokenizer.tokenize();
        r.close();

        // Add the offset and create WordTokens
        List<WordTokenPOSDep> tokenList = new ArrayList<>();
        int j = 0;
        //System.out.println("len(coreLabelList)= "+coreLabelList.size());
        for (CoreLabel cl : coreLabelList) {
            String whitespaceBefore = cl.get(CoreAnnotations.BeforeAnnotation.class);
            int newLineCount = 0;
            for (int i = 0; i < whitespaceBefore.length(); i++) {
                if (whitespaceBefore.charAt(i) == '\n') {
                    newLineCount++;
                }
            }
            /**
             *
             * @author Meryem M'hamdi (meryem@isi.edu)
             *
             * Extending WordToken to WordTokenPOSDep with more linguistic attributes
             */
            WordTokenPOSDep tok = new WordTokenPOSDep(cl.get(CoreAnnotations.TextAnnotation.class),
                    cl.get(CoreAnnotations.PartOfSpeechAnnotation.class), -2, "",
                    cl.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class),
                    cl.get(CoreAnnotations.CharacterOffsetEndAnnotation.class), newLineCount);
            tok.setStart(tok.getStart() + offset);
            tok.setEnd(tok.getEnd() + offset);
            tokenList.add(tok);
            j++;
        }

        // and group the tokens into sentences
        List<List<WordTokenPOSDep>> sentences = new ArrayList<List<WordTokenPOSDep>>();
        ArrayList<WordTokenPOSDep> currentSentence = new ArrayList<WordTokenPOSDep>();
        List<CoreLabel> coreLabelSentence = new ArrayList<>();
        int quoteCount = 0;
        for (int i = 0; i < tokenList.size(); i++) {
            WordTokenPOSDep token = tokenList.get(i);
            String tokenText = token.getWord();

            // start a new sentence if we skipped 2+ lines (after datelines, etc.)
            // or we hit some SGML
            if (token.getNewLineCount() > 1 || AceToken.isSgml(tokenText)) {
                // if (AceToken.isSgml(tokenText)) {
                if (currentSentence.size() > 0){
                    Tree tree = lp.apply(coreLabelSentence);
                    TreebankLanguagePack tlp = new PennTreebankLanguagePack();
                    GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory(Filters.<String>acceptFilter());
                    GrammaticalStructure gs = gsf.newGrammaticalStructure(tree);
                    List<SentDep> deps = new ArrayList<SentDep>();
                    Collection<TypedDependency> tdl = gs.typedDependenciesCCprocessed(true);
                    {
                        StringBuilder sb = new StringBuilder();
                        for (TypedDependency td : tdl) {
                            // TypedDependency td = tdl.(i);
                            String name = td.reln().getShortName();
                            if (td.reln().getSpecific() != null)
                                name += "-" + td.reln().getSpecific();

                            int gov = td.gov().index();
                            int dep = td.dep().index();
                            if (gov == dep) {
                                // System.err.println("same???");
                            }
                            SentDep sd = new SentDep(gov - 1, dep - 1, name);
                            deps.add(sd);
                        }
                    }
                    for (int l=0; l<currentSentence.size();l++){
                        currentSentence.get(l).setGov(deps.get(l).getGov());
                        currentSentence.get(l).setType(deps.get(l).getType());
                        currentSentence.get(l).setPOS(coreLabelSentence.get(l).get(CoreAnnotations.PartOfSpeechAnnotation.class));
                    }
                    sentences.add(currentSentence);
                }


                currentSentence = new ArrayList<>();
                coreLabelSentence = new ArrayList<>();
                quoteCount = 0;
            }

            currentSentence.add(token);
            coreLabelSentence.add(coreLabelList.get(i));

            if (tokenText.equals("\""))
                quoteCount++;

            // start a new sentence whenever we hit sentence-final punctuation
            if (sentenceFinalPuncSet.contains(tokenText)) {
                // include quotes after EOS
                if (i < tokenList.size() - 1 && quoteCount % 2 == 1 && tokenList.get(i + 1).getWord().equals("\"")) {
                    WordTokenPOSDep quoteToken = tokenList.get(i + 1);
                    currentSentence.add(quoteToken);
                    quoteCount++;
                    i++;
                }
                if (currentSentence.size() > 0) {
                    /**
                     *
                     * @author Meryem M'hamdi (meryem@isi.edu)
                     *
                     * Parsing using default dependency parsing model for English in CoreNLP
                     * and disabling punctuation filter
                     *
                     */
                    Tree tree = lp.apply(coreLabelSentence);
                    TreebankLanguagePack tlp = new PennTreebankLanguagePack();
                    GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory(Filters.<String>acceptFilter());
                    GrammaticalStructure gs = gsf.newGrammaticalStructure(tree);
                    List<SentDep> deps = new ArrayList<SentDep>();
                    Collection<TypedDependency> tdl = gs.typedDependenciesCCprocessed(true);
                    {
                        StringBuilder sb = new StringBuilder();
                        for (TypedDependency td : tdl) {
                            // TypedDependency td = tdl.(i);
                            String name = td.reln().getShortName();
                            if (td.reln().getSpecific() != null)
                                name += "-" + td.reln().getSpecific();

                            int gov = td.gov().index();
                            int dep = td.dep().index();
                            if (gov == dep) {
                                // System.err.println("same???");
                            }
                            SentDep sd = new SentDep(gov - 1, dep - 1, name);
                            deps.add(sd);
                        }
                    }
                    for (int l=0; l<currentSentence.size();l++){
                        currentSentence.get(l).setGov(deps.get(l).getGov());
                        currentSentence.get(l).setType(deps.get(l).getType());
                        currentSentence.get(l).setPOS(coreLabelSentence.get(l).get(CoreAnnotations.PartOfSpeechAnnotation.class));
                    }
                    sentences.add(currentSentence);
                }
                currentSentence = new ArrayList<WordTokenPOSDep>();
                coreLabelSentence = new ArrayList<>();
                quoteCount = 0;
            }

            // start a new sentence when we hit an SGML tag
            else if (AceToken.isSgml(tokenText)) {
                if (currentSentence.size() > 0) {
                    Tree tree = lp.apply(coreLabelSentence);
                    TreebankLanguagePack tlp = new PennTreebankLanguagePack();
                    GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory(Filters.<String>acceptFilter());
                    GrammaticalStructure gs = gsf.newGrammaticalStructure(tree);
                    List<SentDep> deps = new ArrayList<SentDep>();
                    Collection<TypedDependency> tdl = gs.typedDependenciesCCprocessed(true);
                    {
                        StringBuilder sb = new StringBuilder();
                        for (TypedDependency td : tdl) {
                            // TypedDependency td = tdl.(i);
                            String name = td.reln().getShortName();
                            if (td.reln().getSpecific() != null)
                                name += "-" + td.reln().getSpecific();

                            int gov = td.gov().index();
                            int dep = td.dep().index();
                            if (gov == dep) {
                                // System.err.println("same???");
                            }
                            SentDep sd = new SentDep(gov - 1, dep - 1, name);
                            deps.add(sd);
                        }
                    }
                    for (int l=0; l<currentSentence.size();l++){
                        currentSentence.get(l).setGov(deps.get(l).getGov());
                        currentSentence.get(l).setType(deps.get(l).getType());
                        currentSentence.get(l).setPOS(coreLabelSentence.get(l).get(CoreAnnotations.PartOfSpeechAnnotation.class));
                    }
                    sentences.add(currentSentence);
                }
                currentSentence = new ArrayList<WordTokenPOSDep>();
                coreLabelSentence = new ArrayList<>();
                quoteCount = 0;
            }
        }
        if (currentSentence.size() > 0) {
            Tree tree = lp.apply(coreLabelSentence);
            TreebankLanguagePack tlp = new PennTreebankLanguagePack();
            GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory(Filters.<String>acceptFilter());
            GrammaticalStructure gs = gsf.newGrammaticalStructure(tree);
            List<SentDep> deps = new ArrayList<SentDep>();
            Collection<TypedDependency> tdl = gs.typedDependenciesCCprocessed(true);
            {
                StringBuilder sb = new StringBuilder();
                for (TypedDependency td : tdl) {
                    // TypedDependency td = tdl.(i);
                    String name = td.reln().getShortName();
                    if (td.reln().getSpecific() != null)
                        name += "-" + td.reln().getSpecific();

                    int gov = td.gov().index();
                    int dep = td.dep().index();
                    if (gov == dep) {
                        // System.err.println("same???");
                    }
                    SentDep sd = new SentDep(gov - 1, dep - 1, name);
                    deps.add(sd);
                }
            }
            for (int l=0; l<currentSentence.size();l++){
                currentSentence.get(l).setGov(deps.get(l).getGov());
                currentSentence.get(l).setType(deps.get(l).getType());
                currentSentence.get(l).setPOS(coreLabelSentence.get(l).get(CoreAnnotations.PartOfSpeechAnnotation.class));
            }
            sentences.add(currentSentence);
        }

        outSents.addAll(sentences);
        int numSentToks = 0;
        for (List<WordTokenPOSDep> sent : sentences) {
            numSentToks += sent.size();
        }
        log.trace(String.format("# orig tokens=%d # sent tokens=%d", tokenList.size(), numSentToks));
    }

}
