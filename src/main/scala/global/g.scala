package global

import org.apache.spark.{SparkConf, SparkContext}
import global.algorithm.labelPropagation
import global.algorithm.adsorption
import global.graph.knnGraph
import global.util.dataProcessor
import org.apache.spark.graphx.Graph
import org.apache.spark.sql.SparkSession



object g {
  def main(args: Array[String]): Unit = {

    // Basic Setup
    val InitialTime = System.nanoTime
    val jobName = "Graph-based SSL"
    val conf = new SparkConf().setAppName(jobName).setMaster("spark://grond:7077")
    val sc = new SparkContext(conf)
    sc.setLogLevel("ERROR")

    val spark = SparkSession
      .builder()
      .appName("Spark SQL")
      .master("spark://grond:7077")
      .getOrCreate()

    val headerPath = "src/main/resource/skin.header"
    val dataPath = "src/main/resource/skin1.data"
    val traPath = "src/main/resource/skin2.data"
    val algorithm = args(6)
    val k = args(3).toInt
    val maxIter = args(4).toInt
    val numPartitions = args(5).toInt

    // Data Pipeline
    val dataProcessor = new dataProcessor(sc, spark)
    dataProcessor.readHeader(headerPath)
    val trainingDF = dataProcessor.generateNormalizedDataFrame(traPath,numPartitions)
    val originalDF = dataProcessor.readCSV(dataPath)

    val Stage1time = (System.nanoTime - InitialTime)/ 1e9
    println("Data Pipeline Time: " + Stage1time)

    // Graph Construction
    val g = knnGraph(trainingDF).bruteForce(k).persist()

    // Learning and Inference
    val gs = labelPropagation(g).run(maxIter).persist()

    // Get result of labelled data
    val result = gs.vertices.collect().sortWith((a,b) => a._1 < b._1).map(_._2)

    spark.stop()
    sc.stop()
  }
}


