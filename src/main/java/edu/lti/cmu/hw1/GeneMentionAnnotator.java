package edu.lti.cmu.hw1;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import edu.lti.cmu.hw1.typesys.GeneMention;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

public class GeneMentionAnnotator extends JCasAnnotator_ImplBase {

  private StanfordCoreNLP pipeline;

  public GeneMentionAnnotator() throws ResourceInitializationException {
    Properties props = new Properties();
    props.put("annotators", "tokenize, ssplit, pos");
    pipeline = new StanfordCoreNLP(props);
  }

  public Map<Integer, Integer> getGeneSpans(String text) {
    Map<Integer, Integer> begin2end = new HashMap<Integer, Integer>();
    Annotation document = new Annotation(text);
    pipeline.annotate(document);
    List<CoreMap> sentences = document.get(SentencesAnnotation.class);
    for (CoreMap sentence : sentences) {
      List<CoreLabel> candidate = new ArrayList<CoreLabel>();
      for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
        String pos = token.get(PartOfSpeechAnnotation.class);
        if (pos.startsWith("NN")) {
          candidate.add(token);
        } else if (candidate.size() > 0) {
          int begin = candidate.get(0).beginPosition();
          int end = candidate.get(candidate.size() - 1).endPosition();
          begin2end.put(begin, end);
          candidate.clear();
        }
      }
      if (candidate.size() > 0) {
        int begin = candidate.get(0).beginPosition();
        int end = candidate.get(candidate.size() - 1).endPosition();
        begin2end.put(begin, end);
        candidate.clear();
      }
    }
    return begin2end;
  }

  /**
   * @see JCasAnnotator_ImplBase#process(JCas)
   */
  public void process(JCas aJCas) throws AnalysisEngineProcessException {
    String text = aJCas.getDocumentText();
    String[] textLine = text.split("\n");

    for (int i = 0; i < textLine.length; i++) {
      String line = textLine[i];
      String sentid = line.substring(0, line.indexOf(' '));
      String sent = line.substring(line.indexOf(' ') + 1);
      Map<Integer, Integer> gslineMap = getGeneSpans(sent);
      Set<Map.Entry<Integer, Integer>> entries = gslineMap.entrySet();
      for (Map.Entry<Integer, Integer> entry : entries) {
        GeneMention gmAnnot = new GeneMention(aJCas);
        gmAnnot.setBegin(getIdx(sent, entry.getKey()));
        gmAnnot.setEnd(getIdx(sent, entry.getValue() - 1));
        gmAnnot.setId(sentid);
        gmAnnot.setText(sent);
        gmAnnot.setTag(sent.substring(entry.getKey(), entry.getValue()));
        gmAnnot.addToIndexes();
      }
    }
  }
  
  int getIdx(String sent, int end) {
    int idx = 0;
    for(int i = 0; i < end; i++)
      if(sent.charAt(i) != ' ')
        idx++;
    return idx;
  }
}
