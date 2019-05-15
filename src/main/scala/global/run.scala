package global

import org.apache.spark.{SparkConf, SparkContext}
import global.algorithm.{MAD, adsorption, labelPropagation}
import global.graph.{knnGraph, knnVertex}
import global.util.dataProcessor
import org.apache.spark.graphx.Graph
import org.apache.spark.sql.SparkSession

object run {
  def main(args: Array[String]): Unit = {

    // Basic Setup
    val jobName = "Graph-based SSL"
    val conf = new SparkConf().setAppName(jobName).setMaster("local[*]")
    val sc = new SparkContext(conf)
    sc.setLogLevel("ERROR")

    val spark = SparkSession
      .builder()
      .appName("Spark SQL")
      .master("local[*]")
      .getOrCreate()

    val headerPath = args(0)
    val rawPath = args(1)
    val traPath = args(2)
    val writePath = args(3)
    val graphAlgorithm = args(4)
    val inferringAlgorithm = args(5)
    val k = args(6).toInt
    val maxIter = args(7).toInt
    val numPartitions = args(8).toInt


    var mu1: Double = 1.0
    var mu2: Double = 0.5
    var mu3: Double = 1.0
    if (inferringAlgorithm == "mad") {
      mu1 = args(8).toInt
      mu2 = args(9).toInt
      mu3 = args(10).toInt
    }

    // Data Pipeline
    val dataProcessor = new dataProcessor(sc, spark)
    dataProcessor.readHeader(headerPath)
    val trainingDF = dataProcessor.generateNormalizedDataFrame(traPath,numPartitions)

    // Graph Construction
    var g: Option[Graph[knnVertex, Double]] = None
//    val g = knnGraph(trainingDF).bruteForce(k).persist()

    if (graphAlgorithm == "approximate") {
      g = Some(knnGraph(trainingDF).approximate(k).persist())
    } else {
      g = Some(knnGraph(trainingDF).bruteForce(k).persist())
    }


    // Learning and Inference
    var gs: Option[Graph[Option[Int], Double]] = None
    val dummyLabels = dataProcessor.dummyLabels

    if (inferringAlgorithm == "adsorption") {
      gs = Some(adsorption(g.get, dummyLabels).run(maxIter).persist())
    } else if (inferringAlgorithm == "mad") {
      gs = Some(MAD(g.get, dummyLabels, mu1, mu2, mu3).run(maxIter).persist())
    } else {
      gs = Some(labelPropagation(g.get).run(maxIter).persist())
    }

    // Get result of labelled data
    val result = gs.get.vertices.collect().sortWith((a,b) => a._1 < b._1).map(_._2)

    val rawDF = dataProcessor.readCSV(rawPath)
    val rawLabels = rawDF.select("class").collect().map(_.toSeq.toArray).flatten.map(_.toString.toInt).map(x => Option(x))

    dataProcessor.writeCSV(spark.createDataFrame(rawLabels.zip(result).toSeq),writePath)

    spark.stop()
    sc.stop()

  }
}


