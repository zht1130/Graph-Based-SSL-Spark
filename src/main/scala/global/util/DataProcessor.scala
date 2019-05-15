package global.util

import global.graph.knnVertex
import org.apache.spark.SparkContext
import org.apache.spark.ml.feature.{BucketedRandomProjectionLSH, Normalizer, StandardScaler, VectorAssembler}
import org.apache.spark.ml.linalg.{Vector, Vectors}
import org.apache.spark.sql.functions.{lit, monotonically_increasing_id, when}
import org.apache.spark.sql.{DataFrame, SparkSession}

import scala.collection.mutable.HashMap
import scala.io.Source
import org.apache.spark.ml.linalg.Vectors
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.col
import org.apache.spark.ml.knn.KNN


class dataProcessor (sc: SparkContext, spark: SparkSession) extends Serializable{

  val class2IntMap = HashMap[String, Int]()
  val int2ClassMap = HashMap[Int, String]()
  val dummyLabels = HashMap[Int, Double]()

  var colNames = Seq[String]()

  def readHeader (path: String) = {

    val bufferedSource = Source.fromFile(path)

    val lines = Source.fromFile(path).getLines.toSeq

    val classLine = lines.find(_.contains("@attribute class")).get

    val classes = "\\{(.*?)\\}".r.findFirstIn(classLine)
      .get.replace("{", "").replace("}", "")
      .split(",").map(_.replace(" ", "")).zipWithIndex

    classes.foreach(x => if (x._1 != "unlabeled") class2IntMap += x._1 -> x._2)
    classes.foreach(x => if (x._1 != "unlabeled") int2ClassMap += x._2 -> x._1)
    classes.foreach(x => if (x._1 != "unlabeled") dummyLabels += x._1.toInt -> 1/class2IntMap.size)

    val inputLine = lines.find(_.contains("@inputs")).get
    val inputs = inputLine.replace("@inputs ","").split(",").map(_.replace(" ", ""))

    colNames = inputs.toSeq
    colNames = colNames :+ "class"

    bufferedSource.close
  }

  def getRawLabels(path: String, numLabeled: Int) = {

    val trainingData = sc.textFile(path).zipWithIndex().map(a => (a._2,a._1))

    val parsedData = trainingData.map { line =>
      val parts = line._2.split(',')
      (line._1,parts.last)
    }.persist()

    parsedData.filter{case (id, _) =>  id+1 > numLabeled}.map(_._2).map(_.toInt).collect()
  }

  def getSupervisedLabels(path: String, numLabeled: Int) = {

    val trainingData = sc.textFile(path).zipWithIndex().map(a => (a._2,a._1))

    val parsedData = trainingData.map { line =>
      val parts = line._2.split(',')
      (line._1,parts.last)
    }.persist()

    parsedData.filter{case (id, _) =>  id+1 <= numLabeled}.map(_._2).map(_.toInt).collect()

  }

  def generateNormalizedDataFrame(path: String, numPartitions: Int) = {

    import spark.implicits._

    val dataRawIndices = sc.textFile(path, numPartitions).zipWithIndex().map(a => (a._2,a._1))

    val parsedData = dataRawIndices.map { line =>
      val parts = line._2.split(',')
      if (parts.last == "unlabeled")
        (line._1,Vectors.dense(parts.dropRight(1).map(_.toDouble)),parts.last)
      else
        (line._1,Vectors.dense(parts.dropRight(1).map(_.toDouble)),parts.last)
    }.persist()

    val df = spark.createDataFrame(parsedData).toDF("id","features","label")
    val scaler = new StandardScaler()
      .setInputCol("features")
      .setOutputCol("scaledFeatures")
      .setWithStd(true)
      .setWithMean(false)

    val scalerModel = scaler.fit(df)

    val scaledData = scalerModel.transform(df)

    scaledData.drop("features").select($"id", $"scaledFeatures".alias("features"), $"label")
  }

  def generateNormalizedData(path: String, numPartitions: Int) = {

    generateNormalizedDataFrame(path, numPartitions).rdd
             .map(row => (row(0).asInstanceOf[Long],row(1).asInstanceOf[Vector],row(2).asInstanceOf[String]))
             .map(x => (x._1, x._2, if (x._3 == "unlabeled") None else Option(x._3.toInt)))
  }


  def writeCSV (df: DataFrame, path: String) = {

//    df.coalesce(1).write.format("com.databricks.spark.csv").save(path)
    df.coalesce(1).write.format("com.databricks.spark.csv").csv(path)
  }

  def readCSV (path: String) = {

    spark.read.csv(path).toDF(colNames:_*)
  }

  def test (path:String) = {

    val csv = sc.textFile(path);

    val parsedData = csv.map { line =>
      val parts = line.split(',')
      (Vectors.dense(parts.dropRight(1).map(_.toDouble)), parts.last)
    }.cache()

    val df1 = spark.createDataFrame(parsedData).toDF("features","label")

    val normalizer = new Normalizer()
      .setInputCol("features")
      .setOutputCol("normFeatures")
      .setP(1.0)

    val df = normalizer.transform(df1)

//    val key = Vectors.dense(0.2, 0.1,0.6)

    val brp = new BucketedRandomProjectionLSH()
      .setBucketLength(2.0)
      .setNumHashTables(3)
      .setInputCol("normFeatures")
      .setOutputCol("hashes")

    val t1 = System.nanoTime
    val model = brp.fit(df)
    println("modelfit: "+(System.nanoTime - t1) / 1e9d)

    val t2 = System.nanoTime
    val transformed = model.transform(df)
//    transformed.show()
    println("size: " + transformed.count())
    println("modeltransform: "+(System.nanoTime - t2) / 1e9d)

    val r = scala.util.Random

    val t3 = System.nanoTime
    for (i <- 1 to 100) {
      val key = Vectors.dense(r.nextDouble(), r.nextDouble(),r.nextDouble())
      val a = model.approxNearestNeighbors(transformed, key, 5)

      val rdd = a.rdd
    }
    println("find: "+(System.nanoTime - t3) / 1e9d)

  }

  def generateTrainingData (df: DataFrame, labelledRatio: Double) = {

    import spark.implicits._

    val numRows = df.count()

    df.withColumn("rn", monotonically_increasing_id())
      .withColumn("label", when($"rn" > (labelledRatio * numRows - 1), lit("unlabeled"))
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

  def unionDF (path:String, dataPath1:String, dataPath2:String) = {

    val df1 = readCSV(dataPath1)

    val df2 = readCSV(dataPath2)

    writeCSV(df1.union(df2).limit(3000),path)
  }

  def labelToOption (l: String) = {
    if (l == "unlabeled")
      None
    else
      Option(l.toInt)
  }
}
