//object test2 {
//
//  def main(args: Array[String]): Unit = {
//
//    val headerPath = "src/main/resource/iris-header.dat"
//    val dataPath = "src/main/resource/iris-tra.dat"
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
//    val dataProcessor = new dataProcessor(sc, spark)
//    dataProcessor.readHeader(headerPath)
//    val originalDF = dataProcessor.readCSV(dataPath)
//    val traDF = dataProcessor.generateTrainingData(originalDF, 0.05)
//    val vertexRDD = dataProcessor.generateVertex1(traDF)
//
//    originalDF.show(150)
//    traDF.show(150)
//
//    val g = knnGraph(sc).bruteForce(vertexRDD, 5)
//    val gs = labelPropagation(g).run(15)
//
//    gs.triplets.foreach(println(_))
//
//    val a = gs.vertices.collect().sortWith((a,b) => a._1 < b._1).map(_._2)
//
//    val map = dataProcessor.class2IntMap
//    val b = originalDF.select("class").collect().map(_.toSeq.toArray).flatten.map(_.toString).map(x=>map.get(x))
//
//    var i = 0
//    var result = 0
//    for (r <- a) {
//      if (r == b(i))
//        result += 1
//      i += 1
//    }
//
//    println(result)
//
//    spark.stop()
//    sc.stop()
//
//  }
//}
