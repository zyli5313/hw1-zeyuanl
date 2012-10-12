package edu.lti.cmu.hw1.crf;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import cc.mallet.fst.*;
import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.*;
import cc.mallet.pipe.tsf.*;
import cc.mallet.types.*;
import cc.mallet.util.*;
import edu.lti.cmu.hw1.typesys.CRFModel;

public class TrainCRF extends JCasAnnotator_ImplBase {

  private String mtrainFile, mtestFile, mmodelPath;
  private int miter;

  // Must have 0-argument constructor for UIMA
  public TrainCRF(){}
  
  public void TrainModelCRF(String trainingFile, String testingFile) throws IOException {

    ArrayList<Pipe> pipes = new ArrayList<Pipe>();

    int[][] conjunctions = new int[2][];
    conjunctions[0] = new int[] { -1 };
    conjunctions[1] = new int[] { 1 };

    pipes.add(new SimpleTaggerSentence2TokenSequence());
    pipes.add(new OffsetConjunctions(conjunctions));
    // pipes.add(new FeaturesInWindow("PREV-", -1, 1));
    pipes.add(new TokenTextCharSuffix("C1=", 1));
    pipes.add(new TokenTextCharSuffix("C2=", 2));
    pipes.add(new TokenTextCharSuffix("C3=", 3));
    pipes.add(new RegexMatches("CAPITALIZED", Pattern.compile("^\\p{Lu}.*")));
    pipes.add(new RegexMatches("STARTSNUMBER", Pattern.compile("^[0-9].*")));
    pipes.add(new RegexMatches("HYPHENATED", Pattern.compile(".*\\-.*")));
    pipes.add(new RegexMatches("DOLLARSIGN", Pattern.compile(".*\\$.*")));
    pipes.add(new TokenFirstPosition("FIRSTTOKEN"));
    pipes.add(new TokenSequence2FeatureVectorSequence());

    Pipe pipe = new SerialPipes(pipes);

    InstanceList trainingInstances = new InstanceList(pipe);
    InstanceList testingInstances = new InstanceList(pipe);

    trainingInstances.addThruPipe(new LineGroupIterator(new BufferedReader(new InputStreamReader(
            new FileInputStream(trainingFile))), Pattern.compile("^\\s*$"),
            true));
    testingInstances.addThruPipe(new LineGroupIterator(new BufferedReader(new InputStreamReader(
            new FileInputStream(testingFile))), Pattern.compile("^\\s*$"),
            true));

    CRF crf = new CRF(pipe, null);
    // crf.addStatesForLabelsConnectedAsIn(trainingInstances);
    crf.addStatesForThreeQuarterLabelsConnectedAsIn(trainingInstances);
    crf.addStartState();

    CRFTrainerByLabelLikelihood trainer = new CRFTrainerByLabelLikelihood(crf);
    trainer.setGaussianPriorVariance(1.0);

    // CRFTrainerByStochasticGradient trainer =
    // new CRFTrainerByStochasticGradient(crf, 1.0);

    // CRFTrainerByL1LabelLikelihood trainer =
    // new CRFTrainerByL1LabelLikelihood(crf, 0.75);

    // trainer.addEvaluator(new PerClassAccuracyEvaluator(trainingInstances, "training"));
    trainer.addEvaluator(new PerClassAccuracyEvaluator(testingInstances, "testing"));
    trainer.addEvaluator(new TokenAccuracyEvaluator(testingInstances, "testing"));
    trainer.train(trainingInstances, miter);

    // saving model
    FileOutputStream fos = new FileOutputStream(mmodelPath);
    ObjectOutputStream oos = new ObjectOutputStream(fos);
    oos.writeObject(crf);
    oos.close();
  }

  /**
   * Initializes this CAS Consumer with the parameters specified in the descriptor.
   * 
   * @throws ResourceInitializationException
   *           if there is error in initializing the resources
   */
  public void initialize(UimaContext aContext) throws ResourceInitializationException {
    mtrainFile = (String) aContext.getConfigParameterValue("trainFile");
    mtestFile = (String) aContext.getConfigParameterValue("testFile");
    mmodelPath = (String) aContext.getConfigParameterValue("modelPath");
    miter = (Integer) aContext.getConfigParameterValue("iterations");
  }

  @Override
  public void process(JCas aJCas) throws AnalysisEngineProcessException {
    CRFModel crfmodel = new CRFModel(aJCas);

    File model = new File(mmodelPath);
    if (!model.exists()) {
      try {
        TrainModelCRF(mtrainFile, mtestFile);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    crfmodel.setPath(mmodelPath);
    crfmodel.addToIndexes();
  }

}