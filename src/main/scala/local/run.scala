package local

import org.apache.spark.ml.linalg.Vector
import org.apache.spark.{SparkConf, SparkContext}
import global.util.dataProcessor
import local.algorithm.{MAD, adsorption}
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.sql.SparkSession
import local.graph.{localGraph, vertex}
import org.apache.spark.rdd.RDD

import scala.collection.mutable.HashMap

object run {

  def main(args: Array[String]): Unit = {

    val conf = new SparkConf().setAppName("Graph-Based SSL").setMaster("local[*]")
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
    val algorithm = args(4)
    val k = args(5).toInt
    val maxIter = args(6).toInt
    val numPartitions = args(7).toInt

    var mu1: Double = 1.0
    var mu2: Double = 0.5
    var mu3: Double = 1.0
    if (algorithm == "mad") {
      mu1 = args(8).toInt
      mu2 = args(9).toInt
      mu3 = args(10).toInt
    }

    val InitialTime = System.nanoTime

    // Data Pipeline
    val dataProcessor = new dataProcessor(sc, spark)
    dataProcessor.readHeader(headerPath)
    val rawDF = dataProcessor.readCSV(rawPath)

    val normalizedData = dataProcessor.generateNormalizedData(traPath,numPartitions).persist()
    val labeledData = normalizedData.filter(_._3.isDefined).persist()
    val numLabeled = labeledData.count().toInt
    val unlabeledData = normalizedData.filter(_._3.isEmpty).persist()
    val numUnlabeled = unlabeledData.count().toInt

    val labeledData_broadcast = sc.broadcast(labeledData.collect())
    val unlabeledData2 = unlabeledData.repartition(numPartitions)

    val dummyLabels = dataProcessor.dummyLabels

    var result: Option[RDD[(Long, Option[Int])]] = None

    if (algorithm == "adsorption") {
      result = Option(unlabeledData2.mapPartitions(data => trainingAdsorption(data, labeledData_broadcast, k, maxIter, dummyLabels)))
    } else if (algorithm == "mad") {
      result = Option(unlabeledData2.mapPartitions(data => trainingMAD(data, labeledData_broadcast, k, maxIter, dummyLabels, mu1, mu2, mu3)))
    } else {
      result = Option(unlabeledData2.mapPartitions(data => trainingLP(data, labeledData_broadcast, k, maxIter)))
    }


    val predictedLabels = result.get.sortBy(_._1, ascending = true).collect()
    val rawLabels = dataProcessor.getRawLabels(rawPath, numLabeled)

    var i = 0
    var numCorrect = 0
    for (r <- predictedLabels) {
      if (r._2.isDefined) {
        if (r._2.get == rawLabels(i))
          numCorrect += 1
      }
      i += 1
    }

    println(numCorrect)
    println("Accuracy rate: " + numCorrect.toDouble * 100 / numUnlabeled)
    val time = (System.nanoTime - InitialTime) / 1e9
    println("training time: " + time)

    val supervisedLabels = dataProcessor.getSupervisedLabels(rawPath, numLabeled)
    val firstCol = supervisedLabels ++ rawLabels
    val secondCol = supervisedLabels ++ predictedLabels.map(_._2.get)

    dataProcessor.writeCSV(spark.createDataFrame(firstCol.zip(secondCol).toSeq),writePath)

    spark.stop()
    sc.stop()

  }

  def trainingLP(unlabeled: Iterator[(Long,Vector,Option[Int])], labeled: Broadcast[Array[(Long,Vector,Option[Int])]], k: Int, maxIter: Int) :Iterator[(Long, Option[Int])] = {

    val v = (unlabeled.toSeq ++ labeled.value.toSeq).map{case(id, feature, label) => new vertex(id, feature, label)}

    // Graph Construction
    val g = localGraph().bruteForce(v, k)

    // Learning and Inference
    val l = labelPropagation(g).run(maxIter)
//        adsorption(g, 2).run(maxIter)
    l
  }

  def trainingAdsorption(unlabeled: Iterator[(Long,Vector,Option[Int])], labeled: Broadcast[Array[(Long,Vector,Option[Int])]], k: Int, maxIter: Int, dummyLabels: HashMap[Int, Double]) :Iterator[(Long, Option[Int])] = {

    val v = (unlabeled.toSeq ++ labeled.value.toSeq).map{case(id, feature, label) => new vertex(id, feature, label)}

    // Graph Construction
    val g = localGraph().bruteForce(v, k)

    // Learning and Inference
    val l = adsorption(g, dummyLabels).run(maxIter)
    l
  }

  def trainingMAD(unlabeled: Iterator[(Long,Vector,Option[Int])], labeled: Broadcast[Array[(Long,Vector,Option[Int])]], k: Int, maxIter: Int,dummyLabels: HashMap[Int, Double], mu1: Double, mu2: Double, mu3: Double) :Iterator[(Long, Option[Int])] = {

    val v = (unlabeled.toSeq ++ labeled.value.toSeq).map{case(id, feature, label) => new vertex(id, feature, label)}

    // Graph Construction
    val g = localGraph().bruteForce(v, k)

    // Learning and Inference
    val l = MAD(g, dummyLabels, mu1, mu2, mu3).run(maxIter)
    l
  }

}



