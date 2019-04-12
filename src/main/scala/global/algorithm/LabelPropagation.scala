package global.algorithm

import org.apache.spark.graphx.Graph
import global.graph.knnVertex

import scala.collection.mutable
import scala.collection.mutable.{HashMap, Set}

case class labelPropagation (g:Graph[knnVertex,Double]) {

  def run(maxIterations:Int = 0) = {

    val maxIter = if (maxIterations == 0) g.vertices.count() / 10 else maxIterations

    // Initialise label distribution for seed nodes
    var g2 = g.mapVertices((_,vd) => if (vd.label.isDefined) {
                                       (HashMap[Int, Double](vd.label.get -> 1.0), true)
                                     } else (HashMap[Int, Double](), false))

    var i = 0

    while (i < maxIter) {

      val newV =
        // Message: Set(vid, weight, labelDistribution)
        g2.aggregateMessages[Set[(Long, Double, HashMap[Int, Double])]](

          // Send label information to neighbours
          triplet => {
            if (estimatedLabel(triplet.srcAttr._1).isDefined) {
              triplet.sendToDst(Set((triplet.srcId , triplet.attr, triplet.srcAttr._1)))
            }
            if (estimatedLabel(triplet.dstAttr._1).isDefined) {
              triplet.sendToSrc(Set((triplet.dstId , triplet.attr, triplet.dstAttr._1)))
            }
          },
          // Merge label information from neighbours
            (a, b) => a ++ b
      )

      // Update label information
      g2 = g2.joinVertices(newV)((_, vd, u) => if (vd._2 == false)
                                                 (weightedAverage(u.toArray.map( a => (a._2,a._3))), vd._2)
                                               else vd)

      i += 1
    }

    // Get final graph with estimated labels
    g2.mapVertices((_, vd) => estimatedLabel(vd._1))

  }


  def weightedAverage(a:Array[(Double, HashMap[Int, Double])]) : mutable.HashMap[Int, Double] = {

    var result = HashMap[Int, Double]()

    for (l <- a) {
      result = result ++ l._2.map { case (k, v) => k -> (l._1 * v + result.getOrElse(k, 0.0)) }
    }

    result.map{ case (k,v) => k -> (v / a.map(_._1).sum) }
  }

  def estimatedLabel(labelScores:HashMap[Int, Double]) : Option[Int] = {
    if (labelScores.size != 0)
      Some(labelScores.toArray.sortWith((a,b) => a._2 > b._2) (0)._1)
    else
      None
  }
}

