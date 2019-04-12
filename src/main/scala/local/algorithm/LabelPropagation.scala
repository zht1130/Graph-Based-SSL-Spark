package local

import org.jgrapht.Graphs
import org.jgrapht.graph._
import local.graph.vertex

import scala.collection.JavaConversions._
import scala.collection.mutable.HashMap

case class labelPropagation (g:WeightedPseudograph[vertex, DefaultWeightedEdge]) {

  def run(maxIterations:Int = 0) = {

    val maxIter = if (maxIterations == 0) g.vertexSet().size() / 500 else maxIterations

    var i = 0

    while (i < maxIter) {

      for (v <- g.vertexSet()) {

        // Keep labels on seed nodes invariant
        if (!v.isSeedNode) {
          val neighbors = Graphs.neighborListOf(g, v).toList
          v.labelScores = weightedAverage(neighbors.map(x => (g.getEdgeWeight(g.getEdge(x, v)), x.labelScores)))
        }
      }

      i += 1
    }

    g.vertexSet().filter(v => !v.isSeedNode).map(v => (v.id, v.estimatedLabel(v.labelScores))).toArray.iterator
  }

  def weightedAverage(a:List[(Double, HashMap[Int, Double])]) = {

    var result = HashMap[Int, Double]()

    for (l <- a) {
      result = result ++ l._2.map { case (k, v) => k -> (l._1 * v + result.getOrElse(k, 0.0)) }
    }

    result.map{ case (k,v) => k -> (v / a.map(_._1).sum) }
  }

}
