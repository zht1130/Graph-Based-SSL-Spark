package zht.util

import zht.graph.knnVertex
import org.apache.spark.SparkContext
import org.apache.spark.ml.linalg.{Vector, Vectors}
import org.apache.spark.sql.functions.{lit, monotonically_increasing_id, when}
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions._

import scala.collection.mutable.HashMap
import scala.io.Source

class dataProcessor (sc: SparkContext, spark: SparkSession) extends Serializable{

  val class2IntMap = HashMap[String, Int]()
  val int2ClassMap = HashMap[Int, String]()

  var colNames = Seq[String]()

  def readHeader (path: String) = {

    val bufferedSource = Source.fromFile(path)

    val lines = Source.fromFile(path).getLines.toSeq

    val classLine = lines.find(_.contains("@attribute Class")).get

    val classes = "\\{(.*?)\\}".r.findFirstIn(classLine)
                               .get.replace("{", "").replace("}", "")
                               .split(",").map(_.replace(" ", "")).zipWithIndex

    classes.foreach(x => if (x._1 != "unlabeled") class2IntMap += x._1 -> x._2)
    classes.foreach(x => if (x._1 != "unlabeled") int2ClassMap += x._2 -> x._1)

    val inputLine = lines.find(_.contains("@inputs")).get
    val inputs = inputLine.replace("@inputs ","").split(",").map(_.replace(" ", ""))

    colNames = inputs.toSeq
    colNames = colNames :+ "class"

    bufferedSource.close
  }

  def readCSV (path: String) = {

    val csv = sc.textFile(path);

    val parsedData = csv.map { line =>
      val parts = line.split(',')
      (Vectors.dense(parts.dropRight(1).map(_.toDouble)), parts.last)
    }.cache()

    spark.createDataFrame(parsedData).toDF(Seq("features","label"):_*).show()

//    spark.read.csv(path).toDF(colNames:_*)
  }

  def writeCSV (df: DataFrame, path: String) = {

    df.coalesce(1).write.format("com.databricks.spark.csv").save(path)
  }

  def generateVertex (path: String): Seq[knnVertex]  = {

    val bufferedSource = Source.fromFile(path)

    val lines = Source.fromFile(path).getLines.toSeq

    val vertexSeq = lines.filter(line => !(line contains ("@")))
                         .map(_.split(","))
                         .map(ss => knnVertex(ss.dropRight(1).map(_.toDouble), class2IntMap.get(ss.last)))

    bufferedSource.close

    vertexSeq
  }

  def generateVertex1 (df: DataFrame)  = {

     df.collect.map(_.toSeq.toArray).map(_.map(_.toString)).map(ss => knnVertex(ss.dropRight(1).map(_.toDouble), class2IntMap.get(ss.last)))
  }

  def generateTrainingData (df: DataFrame, labelledRatio: Double) = {

    import spark.implicits._

    val numRows = df.count()

    df.withColumn("rn", monotonically_increasing_id()).show()

    df.withColumn("rn", monotonically_increasing_id())
      .withColumn("label", when($"rn" > labelledRatio * numRows, lit("unlabeled"))
        .otherwise($"class"))
      .drop("rn")
      .drop($"class")
  }

  def calculateAccuracy (traDF: DataFrame,resultDF: DataFrame, ratio: Double) = {

    import spark.implicits._

    val limit = (traDF.count() * ratio).toInt
    val unlabeled = (traDF.count() - limit).toInt

    val df1 = traDF.select(col("class")).withColumn("rn", monotonically_increasing_id())
    val df2 = resultDF.select(col("class")).withColumn("rn", monotonically_increasing_id()).withColumnRenamed("class", "result")

    val comparison = df1.join(df2, df1("rn") === df2("rn")).drop("rn").limit(limit)

    (unlabeled - comparison.filter($"class" =!= $"result").count()) / unlabeled
  }
}
