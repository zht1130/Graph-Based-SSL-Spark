package global

import org.apache.spark.{SparkConf, SparkContext}
import global.algorithm.labelPropagation
import global.algorithm.adsorption
import global.graph.knnGraph
import global.util.dataProcessor
import org.apache.spark.graphx.Graph
import org.apache.spark.sql.SparkSession

object gs {
  def main(args: Array[String]): Unit = {

    // Basic Setup
    val jobName = "Graph-based SSL Experiment"
    val conf = new SparkConf().setAppName(jobName).setMaster("spark://grond:7077")
    val sc = new SparkContext(conf)
    sc.setLogLevel("ERROR")

    val spark = SparkSession
      .builder()
      .appName("Spark SQL")
      .master("spark://grond:7077")
      .getOrCreate()

    val headerPath = args(0)
    val dataPath = args(1)
    val traPath = args(2)
    val k = args(3).toInt
    val maxIter = args(4).toInt
    val numPartitions = args(5).toInt

    for (r <- 1 to 4) {

      for (n <- 2 to 3) {

        val InitialTime = System.nanoTime
        val ratio = 0.05 * r
        val ratioName = (ratio*100).toInt

        println("r: " + r + " n: " + n)

        // Data Pipeline
        val dataProcessor = new dataProcessor(sc, spark)
        dataProcessor.readHeader(headerPath)
        val trainingDF = dataProcessor.generateNormalizedDataFrame(traPath + "/skin-" + ratioName + "-" + n + "tra" + "/data", numPartitions)
        val originalDF = dataProcessor.readCSV(dataPath + "/skin-" + ratioName + "-" + n + "ori" + "/data")

        val numTrain = trainingDF.count()
        val num_labeled = numTrain * ratio

        val Stage1time = (System.nanoTime - InitialTime) / 1e9
        println("Data Pipeline Time: " + (Stage1time - InitialTime))

        // Graph Construction
        val g = knnGraph(trainingDF).bruteForce(k).persist()
        g.vertices.collect()
        val Stage2time = (System.nanoTime - InitialTime) / 1e9
        println("Graph Construction Time: " + (Stage2time-Stage1time))

        // Learning and Inference
//        val gs = adsorption(g,2).run(maxIter).persist()
        val gs = labelPropagation(g).run(maxIter).persist()
        gs.vertices.collect()
        val Stage3time = (System.nanoTime - InitialTime) / 1e9
        println("Learning and Inference Time: " + (Stage3time - Stage2time))

        // Get result of labelled data
        val a = gs.vertices.collect().sortWith((a,b) => a._1 < b._1).map(_._2)
        val b = originalDF.select("class").collect().map(_.toSeq.toArray).flatten.map(_.toString.toInt).map(x => Option(x))

        var i = 0
        var result = 0
        for (r <- a) {
          if (i > num_labeled && r == b(i))
            result += 1

          i += 1
        }

        println(result)

        println("correct: " + result)
        println("accuracy: " + result/(numTrain - num_labeled))

      }
    }

    spark.stop()
    sc.stop()
  }
}



