package global.graph

import org.apache.spark.graphx.{Edge, Graph}
import org.apache.spark.ml.linalg.{Vector, Vectors}
import org.apache.spark.ml.knn.KNN
import org.apache.spark.ml.linalg.Vectors

import org.apache.spark.sql.DataFrame

case class knnGraph(df: DataFrame)  {

  def approximate(k: Int) = {

    val v = df.rdd.map(row => (row(0).asInstanceOf[Long],row(1).asInstanceOf[Vector],row(2).asInstanceOf[String]))
      .map(x => (x._1, knnVertex(x._2, if (x._3 == "unlabeled") None else Option(x._3.toInt)))).persist()

    val knn = new KNN()
      .setTopTreeSize(df.count().toInt/500)
      .setK(k+1)
    val knnModel = knn.fit(df)
    val e = knnModel.edges(df)

    Graph(v, e)
  }

  def bruteForce(k: Int) = {

    val v = df.rdd.map(row => (row(0).asInstanceOf[Long],row(1).asInstanceOf[Vector],row(2).asInstanceOf[String]))
      .map(x => (x._1, knnVertex(x._2, if (x._3 == "unlabeled") None else Option(x._3.toInt)))).persist()

    val dataset = v.collect()

    val e = v.mapPartitions(subset => knn(subset, dataset, k))

    val v2 = v.repartition(2)
    val e2 = e.repartition(2)

    Graph(v2, e2)
  }

//  def knn(v: Iterator[(Long,knnVertex)], a:Array[(Long,knnVertex)], k: Int) = {
//
//    v.map(v1 => (v1._1, a.map(v2 => (v2._1, euclideanDist(v1._2.features, v2._2.features)))
//      .filter(v2 => v1._1 != v2._1)
//      .sortWith((e, f) => e._2 < f._2)
//      .slice(0, k)))
//      .flatMap(x => x._2.map(v2 =>
//        (x._1, v2._1, 1 / (1 + v2._2))))
//      .map(x => Edge(x._1, x._2, x._3))
//
//  }

  def knn(v: Iterator[(Long,knnVertex)], a: Array[(Long,knnVertex)], k: Int) = {

    val v2 = v.map(v => (v._1, v._2.features))
    val a2 = a.map(v => (v._1, v._2.features))

    v2.map(x => (x._1,neighbors(x, a2, k)))
              .map(x => x._2.map(y => (x._1, y._1, y._2)))
              .flatMap(x => x)
              .map(x => Edge(x._1, x._2, x._3))

  }

  def neighbors(x: (Long, Vector), a: Array[(Long, Vector)], k: Int) = {

    val a2 = a.filter(_._1!= x._1)
    val nearest = Array.fill(k)((-1L, Vectors.dense(-1)))
    val distA = Array.fill(k)(0.0d)

    for (i <- a2) {
      val dist = euclideanDist(x._2, i._2)
      if (dist > 0d) {
        var stop = false
        var j = 0
        while (j < k && !stop) {
          if (nearest(j) == (-1L, Vectors.dense(-1)) || dist <= distA(j)) {
            for (l <- ((j + 1) until k).reverse) {
              nearest(l) = nearest(l - 1)
              distA(l) = distA(l - 1)
            }
            nearest(j) = i
            distA(j) = dist
            stop = true
          }
          j += 1
        }
      }
    }
    nearest.map(_._1).zip(distA.map(d => 1/(1+d)))
  }

  def euclideanDist (v1 :Vector, v2 :Vector): Double =
    math.sqrt(Vectors.sqdist(v1, v2))

}