/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.weibangong.spark.ml;

import com.google.common.collect.Lists;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.ml.Pipeline;
import org.apache.spark.ml.PipelineStage;
import org.apache.spark.ml.classification.LogisticRegression;
import org.apache.spark.ml.evaluation.BinaryClassificationEvaluator;
import org.apache.spark.ml.feature.HashingTF;
import org.apache.spark.ml.feature.Tokenizer;
import org.apache.spark.ml.param.ParamMap;
import org.apache.spark.ml.tuning.CrossValidator;
import org.apache.spark.ml.tuning.CrossValidatorModel;
import org.apache.spark.ml.tuning.ParamGridBuilder;
import org.apache.spark.sql.DataFrame;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SQLContext;

import java.util.List;

/**
 * A simple example demonstrating model selection using CrossValidator.
 * This example also demonstrates how Pipelines are Estimators.
 *
 * This example uses the Java bean classes {@link com.weibangong.spark.ml.JavaLabeledDocument} and
 * {@link com.weibangong.spark.ml.JavaDocument} defined in the Scala example
 * {@link com.weibangong.spark.ml.JavaSimpleTextClassificationPipeline}.
 *
 * Run with
 * <pre>
 * bin/run-example ml.JavaCrossValidatorExample
 * </pre>
 */
public class JavaCrossValidatorExample {

  public static void main(String[] args) {
    SparkConf conf = new SparkConf().setAppName("JavaCrossValidatorExample");
    JavaSparkContext jsc = new JavaSparkContext(conf);
    SQLContext jsql = new SQLContext(jsc);

    // Prepare training documents, which are labeled.
    List<JavaLabeledDocument> localTraining = Lists.newArrayList(
      new JavaLabeledDocument(0L, "a b c d e spark", 1.0),
      new JavaLabeledDocument(1L, "b d", 0.0),
      new JavaLabeledDocument(2L, "spark f g h", 1.0),
      new JavaLabeledDocument(3L, "hadoop mapreduce", 0.0),
      new JavaLabeledDocument(4L, "b spark who", 1.0),
      new JavaLabeledDocument(5L, "g d a y", 0.0),
      new JavaLabeledDocument(6L, "spark fly", 1.0),
      new JavaLabeledDocument(7L, "was mapreduce", 0.0),
      new JavaLabeledDocument(8L, "e spark program", 1.0),
      new JavaLabeledDocument(9L, "a e c l", 0.0),
      new JavaLabeledDocument(10L, "spark compile", 1.0),
      new JavaLabeledDocument(11L, "hadoop software", 0.0));
    DataFrame training = jsql.createDataFrame(jsc.parallelize(localTraining), JavaLabeledDocument.class);

    // Configure an ML pipeline, which consists of three stages: tokenizer, hashingTF, and lr.
    Tokenizer tokenizer = new Tokenizer()
      .setInputCol("text")
      .setOutputCol("words");
    HashingTF hashingTF = new HashingTF()
      .setNumFeatures(1000)
      .setInputCol(tokenizer.getOutputCol())
      .setOutputCol("features");
    LogisticRegression lr = new LogisticRegression()
      .setMaxIter(10)
      .setRegParam(0.01);
    Pipeline pipeline = new Pipeline()
      .setStages(new PipelineStage[] {tokenizer, hashingTF, lr});

    // We now treat the Pipeline as an Estimator, wrapping it in a CrossValidator instance.
    // This will allow us to jointly choose parameters for all Pipeline stages.
    // A CrossValidator requires an Estimator, a set of Estimator ParamMaps, and an Evaluator.
    CrossValidator crossval = new CrossValidator()
        .setEstimator(pipeline)
        .setEvaluator(new BinaryClassificationEvaluator());
    // We use a ParamGridBuilder to construct a grid of parameters to search over.
    // With 3 values for hashingTF.numFeatures and 2 values for lr.regParam,
    // this grid will have 3 x 2 = 6 parameter settings for CrossValidator to choose from.
    ParamMap[] paramGrid = new ParamGridBuilder()
        .addGrid(hashingTF.numFeatures(), new int[]{10, 100, 1000})
        .addGrid(lr.regParam(), new double[]{0.1, 0.01})
        .build();
    crossval.setEstimatorParamMaps(paramGrid);
    crossval.setNumFolds(2); // Use 3+ in practice

    // Run cross-validation, and choose the best set of parameters.
    CrossValidatorModel cvModel = crossval.fit(training);

    // Prepare test documents, which are unlabeled.
    List<JavaDocument> localTest = Lists.newArrayList(
      new JavaDocument(4L, "spark i j k"),
      new JavaDocument(5L, "l m n"),
      new JavaDocument(6L, "mapreduce spark"),
      new JavaDocument(7L, "apache hadoop"));
    DataFrame test = jsql.createDataFrame(jsc.parallelize(localTest), JavaDocument.class);

    // Make predictions on test documents. cvModel uses the best model found (lrModel).
    DataFrame predictions = cvModel.transform(test);
    for (Row r: predictions.select("id", "text", "probability", "prediction").collect()) {
      System.out.println("(" + r.get(0) + ", " + r.get(1) + ") --> prob=" + r.get(2)
          + ", prediction=" + r.get(3));
    }

    jsc.stop();
  }
}
