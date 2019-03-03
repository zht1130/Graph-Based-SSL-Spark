//object test3 {
//
//  def main(args: Array[String]): Unit = {
//
////    val path = "src/main/resource/test.dat"
////    val conf = new SparkConf().setAppName("app").setMaster("local[*]")
////    val sc = new SparkContext(conf)
////    sc.setLogLevel("ERROR")
//
////    val sqlContext = new SQLContext(sc)
//
////    val spark = SparkSession
////      .builder()
////      .appName("Spark SQL basic example")
////      .config("spark.some.config.option", "some-value")
////      .getOrCreate()
////
////    val df = spark.read.csv("src/main/resource/test.dat")
//
//    val conf = new SparkConf().setAppName("app").setMaster("local[*]")
//    val sc = new SparkContext(conf)
//    sc.setLogLevel("ERROR")
//
//    val spark = SparkSession
//      .builder()
//      .appName("Spark SQL basic example")
//      .master("local[*]")
//      .getOrCreate()
//
//    import spark.implicits._
////
////    val df = spark.read.csv("src/main/resource/test.dat")
////
////    df.show()
//
////    val df1 = spark.read.csv("src/main/resource/test2.dat")
////    val df2 = spark.read.csv("src/main/resource/test2.dat")
////
////    val df3 = df1.select(col("_c3")).withColumn("rn", monotonically_increasing_id())
////    val df4 = df2.select(col("_c3")).withColumn("rn", monotonically_increasing_id())
//
//    val headPath = "src/main/resource/iris-header.dat"
//    val dataPath = "src/main/resource/iris-data.dat"
//
//    val dataProcessor = new dataProcessor(sc,spark)
//    dataProcessor.readHeader(headPath)
//
//    val df = dataProcessor.readCSV(dataPath).orderBy(rand())
//
//
////    val a = df.limit(1).select("class")
//
//
//
//
////    val df_asPerson = df1.as("dfperson")
////    df3.alias("a").join(df4.alias("b"), df3("rn") === df4("rn")).drop("rn").show()
//
////    df1.select(col("_c4")).show()
////    val dataProcessor = new dataProcessor(sc)
////
////    dataProcessor.generateTrainingData(spark, df, 0.3).show()
//
//
////    runBasicDataFrameExample(spark)
//    spark.stop()
//
////    val fs = FileSystem.get(sc.hadoopConfiguration);
////    val file = fs.globStatus(new Path("src/main/resource/output/part*"))(0).getPath().getName();
////    println(file)
//
//  }
//
//  private def runBasicDataFrameExample(spark: SparkSession): Unit = {
//
//    import spark.implicits._
//
//    // $example on:create_df$
//    val df1 = spark.read.csv("src/main/resource/test2.dat")
//    val df2 = spark.read.csv("src/main/resource/test3.dat")
//
////    val df3 = df1.union(df2)
////    val df4 = df1.limit(2)
////    val df5 = df1.drop()
////
////    // Displays the content of the DataFrame to stdout
////    df1.show()
////    df4.show()
////    df5.show()
////    df4.coalesce(1).write.format("com.databricks.spark.csv").save("src/main/resource/output")
//
////    df1.show()
////    val n = df1.count()
////    df1.withColumn("rn", monotonically_increasing_id())
////      .withColumn("label", when($"rn"+1 >= 0.3*n, lit("unlabelled")).otherwise($"_c4"))
////      .select($"_c0",$"_c1",$"_c2",$"_c3",$"label").show()
////
////    df1.withColumn("rn", monotonically_increasing_id())
////      .withColumn("label", when($"rn" >= 0.3*n, lit("unlabelled")).otherwise($"_c4"))
////      .show()
//
//
//
//
//  }
//}
