package edu.bit.nlp.concrete.ingesters.acex3;

import edu.jhu.hlt.concrete.*;
import edu.jhu.hlt.concrete.UUID;
import edu.jhu.hlt.concrete.communications.WritableCommunication;
import edu.jhu.hlt.concrete.miscommunication.MiscommunicationException;
import edu.jhu.hlt.concrete.miscommunication.tokenized.CachedTokenizationCommunication;
import edu.jhu.hlt.concrete.uuid.AnalyticUUIDGeneratorFactory;
import edu.jhu.prim.set.IntHashSet;
import edu.jhu.prim.tuple.ComparableTriple;
import edu.stanford.nlp.ie.machinereading.domains.ace.reader.*;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.io.*;
import java.io.FileWriter;
import java.io.IOException;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * Reads an ACE apf.xml file and its corresponding .sgm file to create a Concrete Communication. See
 * Sgml2Concrete for details about the handling of the .sgm file.
 * <p>
 * This code was designed for and tested on the .apf.xml and .sgm files from ACE 2005.
 * <p>
 * Note: The AceDomReader discards any
 * <code>relation_mention_argument/</code> with a ROLE that is not an
 * entity mention (e.g. "Time-Within"). So this code does the same.
 */

public class AceApf2Concrete {

    private static final Logger log = LoggerFactory.getLogger(AceApf2Concrete.class);

    private static final String toolname = "Pacaya ACE 2005 Event Extractor";

    private int numEnts = 0;
    private int numEntMentions = 0;
    private int numEves = 0;
    private int numEveMentions = 0;
    private int numEveMentionRoles = 0;

    /**
     * Reads an ACE .apf.xml file and its corresponding .sgm file and writes out a
     * Concrete communication file.
     */
    public Communication aceApfFile2CommFile(LexicalizedParser lp, Path apfFile, Path sgmFile, Path commFile)
            throws Exception {
        Communication comm = aceApfFile2Comm(lp, apfFile, sgmFile);

        // Write the communication to disk.
        WritableCommunication wc = new WritableCommunication(comm);
        wc.writeToFile(commFile, true);

        return comm;
    }

    /**
     * Save dataset into Json format by merging its different attributes
     * @author Meryem M'hamdi (meryem@isi.edu)
     * @param sentencesList
     * @param wordsList
     * @param posList
     * @param depList
     * @param goldenEntities
     * @param goldenEvents
     * @param outfile
     */
    public void writeToJSON(Map<String, String> sentencesList, Map<String, ArrayList<String>> wordsList, Map<String,
            ArrayList<String>> posList, Map<String, ArrayList<String>> depList, Map<String, HashMap<String,
            EntityJson>>goldenEntities, Map<String, ArrayList<EventJson>> goldenEvents, String outfile){

        JSONArray sentencesListJSON = new JSONArray();
        for (String uuid : sentencesList.keySet()) {

            JSONObject sentenceJSON = new JSONObject();
            sentenceJSON.put("sentence", sentencesList.get(uuid));
            JSONArray wordsListJSON = new JSONArray();
            for (String word: wordsList.get(uuid)){
                wordsListJSON.add(word);
            }

            JSONArray posListJSON = new JSONArray();
            for (String pos: posList.get(uuid)){
                posListJSON.add(pos);
            }

            JSONArray depListJSON = new JSONArray();
            for (String dep: depList.get(uuid)){
                depListJSON.add(dep);
            }

            sentenceJSON.put("words", wordsListJSON);
            sentenceJSON.put("pos-tags", posListJSON);
            sentenceJSON.put("stanford-colcc", depListJSON);


            JSONArray entityListJSON = new JSONArray();
            if (goldenEntities.containsKey(uuid)) {
                for (String entityUUiD : goldenEntities.get(uuid).keySet()) {
                    JSONObject entityJSON = new JSONObject();
                    entityJSON.put("start", goldenEntities.get(uuid).get(entityUUiD).getStartIndex());
                    entityJSON.put("end", goldenEntities.get(uuid).get(entityUUiD).getEndIndex());
                    entityJSON.put("entity-type", goldenEntities.get(uuid).get(entityUUiD).getEntityType());
                    entityJSON.put("text", goldenEntities.get(uuid).get(entityUUiD).getEntityText());
                    entityListJSON.add(entityJSON);
                }
            }

            sentenceJSON.put("golden-entity-mentions", entityListJSON);

            JSONArray eventsListJSON = new JSONArray();
            if (goldenEvents.containsKey(uuid)) {
                for (EventJson event : goldenEvents.get(uuid)) {
                    JSONObject eventJSON = new JSONObject();
                    JSONObject triggerJSON = new JSONObject();

                    triggerJSON.put("start", event.getTrigger().getStartIndex());
                    triggerJSON.put("end", event.getTrigger().getEndIndex());
                    triggerJSON.put("text", event.getTrigger().getText());

                    eventJSON.put("trigger", triggerJSON);

                    JSONArray argumentsListJSON = new JSONArray();
                    for (ArgumentJson argument : event.getArguments()) {
                        JSONObject argumentJSON = new JSONObject();
                        argumentJSON.put("start", argument.getStartIndex());
                        argumentJSON.put("end", argument.getEndIndex());
                        argumentJSON.put("role", argument.getRole());
                        argumentJSON.put("text", argument.getText());

                        argumentsListJSON.add(argumentJSON);
                    }

                    eventJSON.put("arguments", argumentsListJSON);
                    eventJSON.put("event_type", event.getEventType());

                    eventsListJSON.add(eventJSON);
                }
            }

            sentenceJSON.put("golden-event-mentions", eventsListJSON);


            sentencesListJSON.add(sentenceJSON);

        }
        //Write JSON file
        try (FileWriter file = new FileWriter(outfile)) {

            file.write(sentencesListJSON.toJSONString());
            file.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Reads an ACE .apf.xml file and its corresponding .sgm file and gets a
     * Concrete communication.
     *
     * @throws Exception
     */
    public Communication aceApfFile2Comm(LexicalizedParser lp, Path apfFile, Path sgmFile) throws Exception {
        // Get the .sgm file as a Communication.
        Sgml2Concrete s2c = new Sgml2Concrete();
        Communication comm = s2c.sgmlFile2Comm(sgmFile);
        // Annoying hack. Have to pass this in to subsequent methods.
        // TODO: opportunity to refactor this, return a generator-comm tuple
        // or something, but for now, whatever.
        AnalyticUUIDGeneratorFactory f = new AnalyticUUIDGeneratorFactory(comm);
        AnalyticUUIDGeneratorFactory.AnalyticUUIDGenerator g = f.create();

        // Read the apf file as XML.
        // The AceDomReader reads only the apf.xml file, and does not do the
        // additional processing in AceDocument.
        log.info("Reading apf file: " + apfFile);
        AceDocument apfDoc = AceDomReader.parseDocument(apfFile.toFile());
        checkEntityMentions(apfDoc, comm);

        // Tokenize while respecting gold entity boundaries. Split sentences making
        // sure relations don't straddle two sentences.
        log.info("Tokenizing and sentence splitting");
        List<List<WordTokenPOSDep>> tokens = AceTokenizerSentSplitter.tokenizeAndSentenceSegment(lp, apfDoc, comm);

        log.info("Adding tokenization and sentence splits to Concrete communication");
        addTokenizationAndSentences(comm, tokens, g);

        // Convert the XML annotations to Concrete annotations on the comm.
        log.info("Adding ACE annotations to Concrete communication");
        addApfAnnotations(apfDoc, comm, g);

        return comm;
    }

    /**
     * Adds the sentence splits and tokenization to the communication.
     */
    private void addTokenizationAndSentences(Communication comm, List<List<WordTokenPOSDep>> aSents,
                                             AnalyticUUIDGeneratorFactory.AnalyticUUIDGenerator g) {
        IntHashSet taken = new IntHashSet();
        List<Integer> list = new ArrayList<Integer>();
        int s = 0;
        for (Section cSec : comm.getSectionList()) {
            TextSpan cSecExt = cSec.getTextSpan();
            for (int i = 0; i < aSents.size(); i++) {
                List<WordTokenPOSDep> aSent = aSents.get(i);
                if (!textSpanContainsSentence(cSecExt, aSent)) {
                    continue;
                }
                taken.add(i);
                list.add(i);
                Sentence cSent = new Sentence();
                cSent.setUuid(g.next());
                Tokenization cTokenization = new Tokenization();
                cTokenization.setUuid(g.next());
                cTokenization.setKind(TokenizationKind.TOKEN_LIST);
                cTokenization.setMetadata(ConcreteUtils.metadata(toolname));
                TokenList cTokenList = new TokenList();

                for (int j = 0; j < aSent.size(); j++) {
                    WordTokenPOSDep aTok = aSent.get(j);
                    Token cTok = new Token();
                    cTok.setTokenIndex(j);
                    cTok.setText(aTok.getWord()+" "+aTok.getPOS()+ " "+String.valueOf(aTok.getGov())+" "+aTok.getType());
                    cTok.setTextSpan(new TextSpan(aTok.getStart(), aTok.getEnd()));
                    cTokenList.addToTokenList(cTok);
                }
                cTokenization.setTokenList(cTokenList);
                cSent.setTextSpan(new TextSpan(aSent.get(0).getStart(), aSent.get(aSent.size() - 1).getEnd()));
                cSent.setTokenization(cTokenization);
                cSec.addToSentenceList(cSent);
            }
            s++;
        }

        if (taken.size() != aSents.size()) {
            throw new IllegalStateException("Sentence skipped: " + taken);
        }
    }

    private boolean textSpanContainsSentence(TextSpan cSecExt, List<WordTokenPOSDep> aSent) {
        int startOfFirstToken = aSent.get(0).getStart();
        int endOfLastToken = aSent.get(aSent.size() - 1).getEnd();

        return cSecExt.getStart() <= startOfFirstToken && endOfLastToken <= cSecExt.getEnding();
    }

    /**
     * Adds the entity, entity mention, event, and event mention annotations
     * from the {@link AceDocument} to a Concrete {@link Communication}.
     */
    private void addApfAnnotations(AceDocument apfDoc, Communication comm, AnalyticUUIDGeneratorFactory.AnalyticUUIDGenerator g) throws Exception {
        Map<AceEntityMention, EntityMention> a2cEntityMentions = new HashMap<>();

        // Add the Entity annotations.
        EntitySet cEs = new EntitySet();
        cEs.setUuid(g.next());
        cEs.setEntityList(new ArrayList<Entity>());
        cEs.setMetadata(ConcreteUtils.metadata(toolname));
        EntityMentionSet cEms = new EntityMentionSet();
        cEms.setUuid(g.next());
        cEms.setMentionList(new ArrayList<EntityMention>());
        cEms.setMetadata(ConcreteUtils.metadata(toolname));

        for (String aEntityId : apfDoc.getKeySetEntities()) {
            AceEntity aEntity = apfDoc.getEntity(aEntityId);

            Entity cEntity = new Entity();
            cEntity.setUuid(g.next());
            // TODO: Nowhere to store entity ID? aEntity.getId()
            // TODO: There is nowhere to store the entity subtype in Concrete.
            // Instead, we just concatenate the type and subtype.
            cEntity.setType(getTypeSubtype(aEntity));

            // Add EntityMention IDs.
            for (AceEntityMention aEm : aEntity.getMentions()) {
                // TODO: Nowhere to store entity mention ID? aEm.getId()

                // Add the EntityMention annotations.
                AceCharSeq aEmExt = aEm.getExtent();
                AceCharSeq aEmHead = aEm.getHead();

                TokenRefSequence cEmExt = matchToTokens(aEmExt, comm, true);
                TokenRefSequence cEmHead = matchToTokens(aEmHead, comm, true); // temporary
                // Mark the head as the last token in its span.
                cEmExt.setAnchorTokenIndex(cEmHead.getTokenIndexList().get(cEmHead.getTokenIndexListSize() - 1));

                EntityMention cEm = new EntityMention();
                cEm.setUuid(g.next());
                cEm.setPhraseType(aEm.getType());
                cEm.setEntityType(getTypeSubtype(aEm.getParent()));
                cEm.setTokens(cEmExt);

                a2cEntityMentions.put(aEm, cEm);
                // Add cEm to both the EntityMentionSet and the Entity.
                cEms.addToMentionList(cEm);
                cEntity.addToMentionIdList(cEm.getUuid());
                numEntMentions++;
            }

            cEs.addToEntityList(cEntity);
            numEnts++;
        }
        comm.addToEntitySetList(cEs);
        comm.addToEntityMentionSetList(cEms);

        // Add the Event annotations.
        SituationSet cSs = new SituationSet();
        cSs.setUuid(g.next());
        cSs.setSituationList(new ArrayList<Situation>());
        cSs.setMetadata(ConcreteUtils.metadata(toolname));
        SituationMentionSet cSms = new SituationMentionSet();
        cSms.setUuid(g.next());
        cSms.setMentionList(new ArrayList<SituationMention>());
        cSms.setMetadata(ConcreteUtils.metadata(toolname));

        for (AceEvent aEve : getAllEvents(apfDoc)) {
            Situation cEve = new Situation();
            cEve.setUuid(g.next());
            cEve.setSituationType("EVENT");
            cEve.setSituationKind(getTypeSubtype(aEve));
            // TODO: The Stanford objects have parsed the <event_mention_argument/> tags
            cEve.setMentionIdList(new ArrayList<UUID>());

            for (int m = 0; m < aEve.getMentionCount(); m++) {
                AceEventMention aEm = aEve.getMention(m);
                List<MentionArgument> cEmArgList = new ArrayList<MentionArgument>();
                SituationMention cEm = new SituationMention(g.next(), cEmArgList);
                cEm.setSituationType("EVENT");
                cEm.setSituationKind(getTypeSubtype(aEm.getParent()));
                try {
                    TokenRefSequence cEmExt = matchToTokens(aEm.getExtent(), comm, false);
                    cEm.setTokens(cEmExt);
                } catch (IllegalStateException e) {
                    // Some of these extents will cross multiple sentences, due to slight
                    // inconsistencies in the annotation. If this occurs, we don't add the
                    // extent.
                    log.warn("Skipping event mention token span: " + e.getMessage());
                }

                for (AceEventMentionArgument aEmArg : aEm.getArgs()) {
                    MentionArgument cEmArg = new MentionArgument();
                    assert aEmArg.getRole() != null;
                    cEmArg.setRole(aEmArg.getRole());
                    EntityMention aEnm = a2cEntityMentions.get(aEmArg.getContent());
                    cEmArg.setEntityMentionId(aEnm.getUuid());
                    cEm.addToArgumentList(cEmArg);
                    numEveMentionRoles++;
                }

                cSms.addToMentionList(cEm);
                cEve.addToMentionIdList(cEm.getUuid());
                // TODO: The justification and mention ID list seem to be redundant.
                // Here we only add to the mention ID list.
                // cRel.addToJustificationList(cJustification);
                numEveMentions++;
            }
            cSs.addToSituationList(cEve);
            numEves++;
        }

        comm.addToSituationSetList(cSs);
        comm.addToSituationMentionSetList(cSms);
    }

    private static Set<AceEvent> getAllEvents(AceDocument apfDoc) {
        Set<AceEvent> eves = new HashSet<>();
        for (AceEventMention aEm : apfDoc.getEventMentions().values()) {
            eves.add(aEm.getParent());
        }
        return eves;
    }

    private TokenRefSequence matchToTokens(AceCharSeq aSpan, Communication comm, boolean logMismatch) {
        UUID tokenizationId = null; // UUID of tokenization (i.e. sentence)
        int start = -1; // inclusive token index
        int end = -1; // exclusive token index
        int startChar = -1;
        int endChar = -1;

        for (Section cSec : comm.getSectionList()) {
            for (Sentence cSent : cSec.getSentenceList()) {
                Tokenization cTokenization = cSent.getTokenization();
                int j = 0;
                for (Token cTok : cTokenization.getTokenList().getTokenList()) {
                    TextSpan cSpan = cTok.getTextSpan();

                    // ---------------------------
                    // For each Concrete Token...

                    if (cSpan.getStart() <= aSpan.getByteStart() && aSpan.getByteStart() < cSpan.getEnding() && start == -1) {
                        // Found the FIRST token which contains the starting character of
                        // aSpan.
                        start = j;
                        startChar = cSpan.getStart();
                        tokenizationId = cTokenization.getUuid();
                    }
                    if (cSpan.getStart() <= aSpan.getByteEnd() && aSpan.getByteEnd() < cSpan.getEnding()) {
                        // Found a token (not necessarily the last) which contains the
                        // ending character of aSpan.
                        // We do not break out of the loop in case the next token also
                        // contains the span's end.
                        end = j + 1;
                        endChar = cSpan.getEnding();
                        if (start == -1) {
                            throw new IllegalStateException("end was found before start end=" + end + " aSpan=" + aSpan);
                        }
                        if (!tokenizationId.equals(cTokenization.getUuid())) {
                            throw new IllegalStateException("Span crosses multiple sentences: " + aSpan);
                        }
                    }

                    // ---------------------------

                    j++;
                }
            }
        }

        if (tokenizationId == null) {
            throw new IllegalStateException("Unable to match span to tokens: " + aSpan);
        }

        if (log.isDebugEnabled()) {
            // Log if the concrete text doesn't match
            String cText = comm.getText().substring(startChar, endChar);
            if (logMismatch && !cText.equals(aSpan.getText())) {
                String aText = aSpan.getText().replaceAll("\n", "\\\\n");
                cText = cText.replaceAll("\n", "\\\\n");
                log.debug(String.format("Mismatch between ACE span and Concrete span (ace / concrete): %s\t%s", aText, cText));
            }
        }
        if (log.isTraceEnabled()) {
            Tokenization cTokenization = getTokenization(comm, tokenizationId);
            List<Token> cToks = cTokenization.getTokenList().getTokenList();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < cToks.size(); i++) {
                if (i == start) {
                    sb.append("(");
                }
                sb.append(cToks.get(i).getText());
                sb.append(" ");
                if (i + 1 == end) {
                    sb.append(")");
                }
            }
            log.trace(String.format("Extent (%s) in context: %s", aSpan.getText(), sb.toString()));
        }

        TokenRefSequence cTokens = new TokenRefSequence();
        List<Integer> tokenIndexList = new ArrayList<>();
        for (int t = start; t < end; t++) {
            tokenIndexList.add(t);
        }
        cTokens.setTokenizationId(tokenizationId);
        cTokens.setTokenIndexList(tokenIndexList);
        return cTokens;
    }

    // Super slow, but useful for trace logging.
    private Tokenization getTokenization(Communication comm, UUID tokenizationId) {
        try {
            CachedTokenizationCommunication tc = new CachedTokenizationCommunication(comm);
            return tc.getUuidToTokenizationMap().get(tokenizationId);
        } catch (MiscommunicationException e) {
            // Could throw if there are no tokenizations, but
            // there will definitely be tokenizations.
            // Throw an RTE if something goes amiss.
            throw new RuntimeException("No tokenizations in communication: " + comm.getUuid());
        }
    }

    private String getTypeSubtype(AceEntity aEntity) {
        return aEntity.getType() + ":" + aEntity.getSubtype();
    }

    private String getTypeSubtype(AceRelation aRel) {
        return aRel.getType() + ":" + aRel.getSubtype();
    }

    private String getTypeSubtype(AceEvent aEve) {
        return aEve.getType() + ":" + aEve.getSubtype();
    }

    public static Path toSgmFile(Path aceApfFile) {
        Path sgmFile = Paths.get(aceApfFile.toString().replace(".apf.xml", ".sgm"));
        if (!Files.exists(sgmFile)) {
            throw new IllegalStateException(".sgm file can not be found in expected location: " + sgmFile);
        }
        return sgmFile;
    }

    /**
     * Logs the entity mentions.
     */
    private void checkEntityMentions(AceDocument apfDoc, Communication comm) {
        // Sort the entities by their extents.
        List<ComparableTriple<Integer, Integer, String>> order = new ArrayList<>();
        for (String aEmId : apfDoc.getEntityMentions().keySet()) {
            AceEntityMention aEm = apfDoc.getEntityMention(aEmId);
            order.add(new ComparableTriple<Integer, Integer, String>(aEm.getExtent().getByteStart(),
                    aEm.getExtent().getByteEnd(), aEm.getId()));
        }
        Collections.sort(order);

        // Pattern AMP_RE = Pattern.compile("&amp;");
        for (ComparableTriple<Integer, Integer, String> tr : order) {
            String aEmId = tr.get3();
            AceEntityMention aEm = apfDoc.getEntityMention(aEmId);
            AceCharSeq aEmExt = aEm.getExtent();
            String cEmText = comm.getText().substring(aEmExt.getByteStart(), aEmExt.getByteEnd() + 1);
            String aEmText = aEm.getExtent().getText();
            // First convert "&amp;" to "&", since these aren't errors.
            cEmText = cEmText.replace("&amp;", "&");
            boolean eq = cEmText.equals(aEmText);
            if (eq) {
                log.trace(String.format("ACE Entity Mention: eq=%b aExt=%s cText=%s", eq, aEm.getExtent().toString(),
                        cEmText));
            } else {
                log.warn(String.format("Mismatched ACE Entity Mention: eq=%b aExt=%s cText=%s", eq, aEm.getExtent().toString(),
                        cEmText));
            }
        }
    }

    /**
     * Example usage:
     * <br>
     * <br>
     * <pre>java edu.jhu.hlt.concrete.ingesters.acere.AceApf2Concrete
     *     CNN_CF_20030303.1900.00.apf.xml
     *     CNN_CF_20030303.1900.00.comm
     * </pre>
     * apf.v5.1.1.dtd should be in the same directory
     */

    public static void main(String[] args) throws Exception {
        String rootDir = args[0]; //"/Users/d22admin/USCGDrive/ISI/EventExtraction/"
        String outfile = rootDir+ args[1]; // "3Datasets/EventsExtraction/ACE/Preprocessed/JMEE_Dataset/English_new/" "3Datasets/EventsExtraction/ACE/LDC2006T06/data/English/" "5Algorithms/EventDetection/JMEE/qi_filelist/new_filelist_ACE_"
        String rootDataDir = rootDir + args[2]; // "3Datasets/EventsExtraction/ACE/LDC2006T06/data/English/";
        String splitPath = rootDir + args[3]; // "5Algorithms/EventDetection/JMEE/qi_filelist/new_filelist_ACE_";

        String[] filesplits = new String[]{"training", "dev", "test"};

        for (int j=0; j<filesplits.length; j++){
            outfile += filesplits[j] +".json";
            List<String> files = new ArrayList<>();
            File file = new File(splitPath+filesplits[j]);
            BufferedReader br = new BufferedReader(new FileReader(file));
            String st;
            while((st = br.readLine()) != null){
                files.add(st);
            }
            Map<String, String> sentencesList = new HashMap<>();
            Map<String, String> entityIdMap = new HashMap<>();
            Map<String, ArrayList<String>> wordsList = new HashMap<String, ArrayList<String>>();
            Map<String, ArrayList<String>> posList = new HashMap<String, ArrayList<String>>();
            Map<String, ArrayList<String>> depList = new HashMap<String, ArrayList<String>>();
            Map<String, HashMap<String, EntityJson>> entityMap = new HashMap<String, HashMap<String, EntityJson>>();
            Map<String, ArrayList<EventJson>> eventMap = new HashMap<String, ArrayList<EventJson>>();

            AceApf2Concrete a2c = new AceApf2Concrete();
            int num_events = 0;
            LexicalizedParser lp = LexicalizedParser.loadModel();
            for (int i=0; i<files.size(); i++) {
                log.info(String.format("Processing file=%s", files.get(i)));
                String apfFilePath = rootDataDir + files.get(i) + ".apf.xml";
                String commFilePath = rootDataDir + files.get(i) + ".comm";
                Path outDir = Paths.get(commFilePath);
                List<Path> apfXmlFilesList = new ArrayList<Path>();
                if (!Files.isDirectory(Paths.get(apfFilePath))) {
                    String apfXmlFile = apfFilePath; // Input .apf.xml file.
                    Path aceApfPath = Paths.get(apfXmlFile);
                    apfXmlFilesList.add(aceApfPath);

                    log.info(String.format("#entities=%d #e-mentions=%d #events=%d #eve-mentions=%d " +
                                    "#eve-mention-roles=%d", a2c.numEnts, a2c.numEntMentions, a2c.numEves,
                            a2c.numEveMentions, a2c.numEveMentionRoles - a2c.numEveMentions));
                }
                else {
                    // Process matching files in a directory.
                    String apfPrefix = apfFilePath;
                    if (!Files.exists(outDir)) {
                        Files.createDirectory(outDir);
                    }
                    Path inPath = Paths.get(apfPrefix);
                    apfXmlFilesList = Files.list(inPath).filter(p -> p.toString().endsWith(".apf.xml"))
                            .collect(Collectors.toList());
                    log.info(String.format("Found %d apf.xml files", apfXmlFilesList.size()));
                }
                for (Path aceApfFile : apfXmlFilesList) {
                    Path sgmFile = toSgmFile(aceApfFile);
                    Communication comm = a2c.aceApfFile2Comm(lp, aceApfFile, sgmFile);

                    /**
                     *
                     * @author Meryem M'hamdi (meryem@isi.edu)
                     * Extracting Sentence Tokenization from Communication Object
                     *
                     */
                    for (int k=0; k<comm.getSectionListSize();k++) {
                        Section sec = comm.getSectionList().get(k);
                        for (int m = 0; m < sec.getSentenceListSize(); m++) {
                            Sentence sent = sec.getSentenceList().get(m);
                            Tokenization tok = sent.getTokenization();
                            String uuid = tok.getUuid().getUuidString();

                            sentencesList.put(uuid, comm.getText().substring(sent.getTextSpan().getStart(),
                                    sent.getTextSpan().getEnding()));
                            TokenList tokenList = tok.getTokenList();
                            ArrayList<String> tokens = new ArrayList<>();
                            ArrayList<String> pos = new ArrayList<>();
                            ArrayList<String> deps = new ArrayList<>();
                            for (int o=0; o<tokenList.getTokenListSize();o++){
                                String [] tokPosGovType = tokenList.getTokenList().get(o).getText().split(" ");
                                tokens.add(tokPosGovType[0]);
                                pos.add(tokPosGovType[1]);
                                deps.add(tokPosGovType[3]+"/dep="+o+"/gov="+tokPosGovType[2]);
                            }
                            wordsList.put(uuid, tokens);
                            posList.put(uuid, pos);
                            depList.put(uuid, deps);
                        }
                    }


                    /**
                     *
                     * @author Meryem M'hamdi (meryem@isi.edu)
                     * Extracting Entity Mentions from Communication Object
                     *
                     */
                    for (int k=0; k<comm.getEntityMentionSetListSize();k++) {
                        EntityMentionSet entitySet = comm.getEntityMentionSetList().get(k);
                        for (int m = 0; m < comm.getEntityMentionSetList().get(0).getMentionListSize(); m++) {
                            EntityMention entityMention = entitySet.getMentionList().get(m);
                            String uuid = entityMention.getTokens().getTokenizationId().getUuidString();
                            String entityUUID = entityMention.getUuid().getUuidString();
                            List<Integer> tokensList = entityMention.getTokens().getTokenIndexList();
                            String tokensText = "";
                            for (int o=0; o<entityMention.getTokens().getTokenIndexListSize(); o++){
                                tokensText += wordsList.get(uuid).get(tokensList.get(o)) + " ";
                            }
                            EntityJson entityJson = new EntityJson(tokensList.get(0),tokensList.get(tokensList.size()-1),
                                    entityMention.getEntityType(), tokensText);

                            if (!entityMap.containsKey(uuid)){
                                entityMap.put(uuid, new HashMap<String, EntityJson>());
                            }
                            entityMap.get(uuid).put(entityUUID, entityJson);
                            entityIdMap.put(entityUUID, uuid);

                        }
                    }

                    /**
                     *
                     * @author Meryem M'hamdi (meryem@isi.edu)
                     * Extracting Event Mentions from Communication Object
                     *
                     */
                    for (int k=0;k<comm.getSituationMentionSetListSize(); k++){
                        SituationMentionSet situationSet = comm.getSituationMentionSetList().get(k);
                        num_events += situationSet.getMentionListSize();
                        for (int m=0; m<situationSet.getMentionListSize();m++) {
                            SituationMention situation = situationSet.getMentionList().get(m);
                            String eventType = situation.getSituationKind();
                            List<ArgumentJson> arguments = new ArrayList<>();
                            TriggerJson trigger = new TriggerJson(0, 1 , "N/A");
                            for (int o=0;o<situation.getArgumentListSize(); o++){
                                MentionArgument argument = situation.getArgumentList().get(o);
                                String argumentUUid = argument.getEntityMentionId().getUuidString();
                                String uuid = entityIdMap.get(argumentUUid);
                                for (int p=0;p<entityMap.get(uuid).size();p++){
                                    EntityJson entityJson = entityMap.get(uuid).get(argumentUUid);

                                    if (argument.getRole().equals("TRIGGER")){
                                        trigger = new TriggerJson(entityJson.getStartIndex(),
                                                entityJson.getEndIndex(), entityJson.getEntityText());
                                    } else{
                                        arguments.add(new ArgumentJson(entityJson.getStartIndex(), entityJson.
                                                getEndIndex(), argument.getRole(), entityJson.getEntityText()));

                                    }

                                    EventJson eventJson = new EventJson(trigger, arguments, eventType);
                                    if (!eventMap.containsKey(uuid)){
                                        eventMap.put(uuid, new ArrayList<EventJson>());
                                    }
                                    if (!eventMap.get(uuid).contains(eventJson)) {
                                        eventMap.get(uuid).add(eventJson);
                                    }
                                }

                            }
                        }
                    }

                }
                log.info(String.format("#entities=%d #e-mentions=%d #events=%d #eve-mentions=%d #eve-mention-roles=%d",
                        a2c.numEnts, a2c.numEntMentions, a2c.numEves, a2c.numEveMentions,
                        a2c.numEveMentionRoles - a2c.numEveMentions));

            }
            System.out.println(" Number of sentences in = "+ sentencesList.size());
            System.out.println(" Number of events in split: "+ filesplits[j]+ " is = "+ num_events);
            a2c.writeToJSON(sentencesList, wordsList, posList, depList, entityMap, eventMap, outfile);
        }


    }

}
