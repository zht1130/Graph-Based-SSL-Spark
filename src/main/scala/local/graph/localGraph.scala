package local.graph

import org.jgrapht.graph._;
import org.apache.spark.ml.linalg.Vectors
import org.apache.spark.ml.linalg.Vector

case class localGraph() {

  def bruteForce(a: Seq[vertex], k: Int) = {

    val g = new WeightedPseudograph[vertex, DefaultWeightedEdge](classOf[DefaultWeightedEdge])

    val a2 = a.zipWithIndex.map(x => (x._2.toLong, x._1)).toArray
    val a3 = a.zipWithIndex.map(x => (x._2.toLong, x._1)).toArray.map(v => (v._1, v._2.features))
    val v3 = a3.clone()

//    val e = v3.map(x => (x._1,neighbors(x, a3, k)))
//              .flatMap(x => x._2.map(y => (x._1, y._1, y._2)))

    val e = a2.map(v1 => (v1._1, a2.map(v2 => (v2._1, euclideanDist(v1._2.features, v2._2.features)))
      .filter(v2 => v1._1 != v2._1)
      .sortWith((e, f) => e._2 < f._2)
      .slice(0, k)))
      .flatMap(x => x._2.map(v2 =>
        (x._1, v2._1, 1 / (1 + v2._2))))

    for (i <- a) {
      g.addVertex(i)
    }

    for (i <- e) {
      val e1 = g.addEdge(a2(i._1.toInt)._2, a2(i._2.toInt)._2)
      g.setEdgeWeight(e1, i._3);
    }

    g
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
