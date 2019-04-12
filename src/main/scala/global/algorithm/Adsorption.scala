package global.algorithm

import org.apache.spark.graphx.Graph
import global.graph.knnVertex

import scala.collection.mutable.{HashMap, Set}

case class adsorption (g:Graph[knnVertex,Double], dummyLabel: HashMap[Int, Double]) {

  def run(maxIterations:Int = 0) = {

    val maxIter = if (maxIterations == 0) g.vertices.count() / 2
    else maxIterations

    val g2 = g.mapVertices((_,vd) => if (vd.label.isDefined) {
      vd.isSeedNode = true
      (HashMap[Int, Double](vd.label.get -> 1.0), vd, (1.0, 0.0, 0.0))
    } else (HashMap[Int, Double](), vd, (0.0, 1.0, 0.0)))

    val tmp =
      g2.aggregateMessages[Set[(Long, Double)]](  //(vid, weight)
        triplet => {
          triplet.sendToDst(Set((triplet.srcId , triplet.attr)))
          triplet.sendToSrc(Set((triplet.dstId , triplet.attr)))
        },
        (a, b) => a ++ b
      )

    var g3 = g2.joinVertices(tmp)((_, vd, u) => (vd._1, vd._2, probabilities(entropy(u.toArray.map(_._2)), vd._2.isSeedNode)))

    var i = 0

    while (i < maxIter) {

      val newV =
        g3.aggregateMessages[Set[(Long, Double, HashMap[Int, Double])]](  //(vid, weight, probDistribution)
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

      g3 = g3.joinVertices(newV)((_, vd, u) => if (vd._2.isSeedNode == true) {
                                                 (weightedAverage(Array(
                                                   (vd._3._1, HashMap[Int, Double](vd._2.label.get -> 1.0)),
                                                   (vd._3._2, weightedAverage(u.toArray.map( a => (a._2,a._3)))),
                                                   (vd._3._3, dummyLabel)
                                                 )), vd._2, vd._3)
                                               }
                                               else {
                                                (weightedAverage(Array(
                                                  (vd._3._2, weightedAverage(u.toArray.map( a => (a._2,a._3)))),
                                                  (vd._3._3, dummyLabel)
                                                )), vd._2, vd._3)
                                               })

      i += 1
    }

    g3.mapVertices((_, vd) => estimatedLabel(vd._1))
  }

  def weightedAverage(a:Array[(Double, HashMap[Int, Double])]) = {

    var result = HashMap[Int, Double]()

    for (l <- a) {
      result = result ++ l._2.map { case (k, v) => k -> (l._1 * v + result.getOrElse(k, 0.0)) }
    }

    result.map{ case (k,v) => k -> (v / a.map(_._1).sum) }
  }

  def probabilities(entropy:Double, labeled:Boolean, beta:Double = 2) = {

    val cv = math.log(beta) / math.log(beta + Math.exp(entropy))

    var dv = 0.0
    if (labeled) {
      dv = (1 - cv) * Math.sqrt(entropy)
    }

    val zv = Math.max(cv + dv, 1.0)

    val pContinue = cv / zv
    val pInject = dv / zv
    val pAbandon = 1 - pContinue - pInject

    (pInject, pContinue, pAbandon)
  }

  def entropy(a:Array[Double]) = {

    var entropy = 0.0
    val sum = a.sum

    for (w <- a) {
      val p = w/sum
      entropy += -1 * p * Math.log(p) / Math.log(2)
    }

    entropy
  }

  def estimatedLabel(labelScores: HashMap[Int, Double]): Option[Int] = {
    if (labelScores.size != 0)
      Some(labelScores.toArray.sortWith((a,b) => a._2 > b._2) (0)._1)
    else
      None
  }
}

