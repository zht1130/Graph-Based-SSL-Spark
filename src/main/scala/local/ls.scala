package local

import org.apache.spark.ml.linalg.Vector
import org.apache.spark.{SparkConf, SparkContext}
import global.util.dataProcessor
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.sql.SparkSession
import local.graph.{localGraph, vertex}

object ls {

  def main(args: Array[String]): Unit = {

    val conf = new SparkConf().setAppName("Graph-Based SSL").setMaster("spark://grond:7077")
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

//
//    val headerPath = "src/main/resource/skin.header"
//    //    val dataPath =  "src/main/resource/skin-10000/skin-20-1ori"
//    //    val traPath = "src/main/resource/skin-10000/skin-20-1tra"
//    val dataPath =  "src/main/resource/skin1.data"
//    val traPath = "src/main/resource/skin2.data"
//    //val traPath = "src/main/resource/skin2.data"
//    val k = 5
//    val maxIter = 50
//val numPartitions = 8

    // Data Pipeline
    val dataProcessor = new dataProcessor(sc, spark)
    dataProcessor.readHeader(headerPath)

    for (r <- 1 to 4) {
      for (n <- 1 to 5) {

        println("r: " + r + "n: " + n)

        val InitialTime = System.nanoTime
        val ratio = 0.05 * r
        val ratioName = (ratio*100).toInt

        val tp = traPath + "/skin-" + ratioName + "-" + n + "tra" + "/data"
        val dp = dataPath + "/skin-" + ratioName + "-" + n + "ori" + "/data"
//        val trainingDF = dataProcessor.readCSV(traPath + "/skin-" + ratioName + "-" + n + "tra" + "/data")
//        val originalDF = dataProcessor.readCSV(dataPath + "/skin-" + ratioName + "-" + n + "ori" + "/data")

        val normalizedData = dataProcessor.generateNormalizedData(tp,numPartitions).persist()
        val labeledData = normalizedData.filter(_._3.isDefined).persist()
        val numLabeled = labeledData.count().toInt
        val unlabeledData = normalizedData.filter(!_._3.isDefined).persist()
        val numUnlabeled = unlabeledData.count().toInt

        val labeledData_broadcast = sc.broadcast(labeledData.collect())
        val unlabeledData2 = unlabeledData.repartition(numPartitions)

        //    unlabeledData2.mapPartitions{x => Iterator(x.length)}.collect.foreach(println(_))

        val result = unlabeledData2.mapPartitions(data => training(data, labeledData_broadcast, k, maxIter))

        val testLabels = result.sortBy(_._1, ascending = true).collect()
        val rawLabels = dataProcessor.getRawLabels(dp, numLabeled)

        var i = 0
        var numCorrect = 0
        for (r <- testLabels) {
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
      }
    }

    spark.stop()
    sc.stop()

  }

  def training(unlabeled: Iterator[(Long,Vector,Option[Int])], labeled: Broadcast[Array[(Long,Vector,Option[Int])]], k: Int, maxIter: Int) : Iterator[(Long, Option[Int])] = {

    val v = (unlabeled.toSeq ++ labeled.value.toSeq).map{case(id, feature, label) => new vertex(id, feature, label)}

    // Graph Construction
    val g = localGraph().bruteForce(v, k)

    // Learning and Inference
    val l = labelPropagation(g).run(maxIter)
    //    adsorption(g, 2).run(maxIter)
    l
  }
}



