package zht.algorithm

import org.apache.spark.graphx.Graph
import zht.graph.knnVertex

import scala.collection.mutable.{HashMap, Set}


case class labelPropagation (g:Graph[knnVertex,Double]) {

  def run(maxIterations:Int = 0) = {

    val maxIter = if (maxIterations == 0) g.vertices.count() / 2
    else maxIterations

    var g2 = g.mapVertices((_,vd) => if (vd.label.isDefined) {
                                       vd.isSeedNode = true
                                       (HashMap[Int, Double](vd.label.get -> 1.0), vd)
                                     } else (HashMap[Int, Double](), vd))

    var isChanged = true
    var i = 0

    while (i < maxIter  && isChanged) {

      val newV =
        g2.aggregateMessages[Set[(Long, Double, HashMap[Int, Double])]](  //(vid, weight, probDistribution)
          triplet => {
            if (triplet.srcAttr._2.estimatedLabel(triplet.srcAttr._1).isDefined) {
              triplet.sendToDst(Set((triplet.srcId , triplet.attr, triplet.srcAttr._1)))
            }
            if (triplet.dstAttr._2.estimatedLabel(triplet.dstAttr._1).isDefined) {
              triplet.sendToSrc(Set((triplet.dstId , triplet.attr, triplet.dstAttr._1)))
            }
          },
      (a, b) => a ++ b
      )

      isChanged = g2.vertices.join(newV)
        .map(x => estimatedLabel(x._2._1._1) != estimatedLabel(x._2._2.toList (0)._3))
        .reduce(_ || _)


      g2 = g2.joinVertices(newV)((_, vd, u) => if (vd._2.isSeedNode == false)
                                                 (weightedAverage(u.toArray.map( a => (a._2,a._3))), vd._2)
                                               else vd)

      i += 1

    }

    g2.mapVertices((_, vd) => estimatedLabel(vd._1))

  }


  def weightedAverage(a:Array[(Double, HashMap[Int, Double])]) = {

    var result = HashMap[Int, Double]()

    for (l <- a) {
      result = result ++ l._2.map { case (k, v) => k -> (l._1 * v + result.getOrElse(k, 0.0)) }
    }

    result.map{ case (k,v) => k -> (v / a.map(_._1).sum) }
  }

  def estimatedLabel(labelScores:HashMap[Int, Double]): Option[Int] = {
    if (labelScores.size != 0)
      Some(labelScores.toArray.sortWith((a,b) => a._2 > b._2) (0)._1)
    else
      None
  }
}

