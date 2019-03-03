package zht

import org.apache.spark.ml.linalg.Vectors
import org.apache.spark.sql.SparkSession
import org.apache.spark.{SparkConf, SparkContext}
import zht.algorithm.labelPropagation
import zht.algorithm.adsorption
import zht.graph.knnGraph
import zht.util.dataProcessor


object text {
  def main(args: Array[String]): Unit = {

    val headerPath = "src/main/resource/iris-header.dat"
    val dataPath = "src/main/resource/iris-tra.dat"

    val conf = new SparkConf().setAppName("app").setMaster("local[*]")
    val sc = new SparkContext(conf)
    sc.setLogLevel("ERROR")

    val spark = SparkSession
      .builder()
      .appName("Spark SQL basic example")
      .master("local[*]")
      .getOrCreate()

    val dataProcessor = new dataProcessor(sc, spark)
    dataProcessor.readHeader(headerPath)
    dataProcessor.readCSV(dataPath)
//val dataFrame = spark.createDataFrame(Seq(
//  (0, Vectors.dense(1.0, 0.5, -1.0)),
//  (1, Vectors.dense(2.0, 1.0, 1.0)),
//  (2, Vectors.dense(4.0, 10.0, 2.0))
//)).toDF("id", "features")
//
//    dataFrame.show

    spark.stop()
    sc.stop()

  }
}

