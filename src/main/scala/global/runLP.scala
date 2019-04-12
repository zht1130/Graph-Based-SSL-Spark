package global

import org.apache.spark.{SparkConf, SparkContext}
import global.algorithm.labelPropagation
import global.algorithm.adsorption
import global.graph.{knnGraph, knnVertex}
import global.util.dataProcessor
import org.apache.spark.graphx.Graph
import org.apache.spark.sql.SparkSession

object runLP {
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
    val k = args(4).toInt
    val maxIter = args(5).toInt
    val numPartitions = args(6).toInt

    // Data Pipeline
    val dataProcessor = new dataProcessor(sc, spark)
    dataProcessor.readHeader(headerPath)
    val trainingDF = dataProcessor.generateNormalizedDataFrame(traPath,numPartitions)

    // Graph Construction
    val g = knnGraph(trainingDF).bruteForce(k).persist()

    // Learning and Inference
    val gs = labelPropagation(g).run(maxIter).persist()

    // Get result of labelled data
    val result = gs.vertices.collect().sortWith((a,b) => a._1 < b._1).map(_._2)

    dataProcessor.writeCSV(spark.createDataFrame(sc.makeRDD(result)),writePath)

    spark.stop()
    sc.stop()

  }
}


