package global.algorithm

import org.apache.spark.graphx.Graph
import global.graph.knnVertex

import scala.collection.mutable.{HashMap, Set}

case class MAD (g:Graph[knnVertex,Double], dummyLabel: HashMap[Int, Double], mu1: Double, mu2: Double, mu3: Double) {

  def run(maxIterations:Int = 0) = {

    val maxIter = if (maxIterations == 0) g.vertices.count() / 2
    else maxIterations

    val g2 = g.mapVertices((_,vd) => if (vd.label.isDefined) {
      vd.isSeedNode = true
      (HashMap[Int, Double](vd.label.get -> 1.0), vd, (1.0, 0.0, 0.0), 1.0)
    } else (HashMap[Int, Double](), vd, (0.0, 1.0, 0.0), 1.0))

    val tmp =
      g2.aggregateMessages[Set[(Long, Double)]](  //(vid, weight)
        triplet => {

          triplet.sendToDst(Set((triplet.srcId , triplet.attr)))
          triplet.sendToSrc(Set((triplet.dstId , triplet.attr)))
        },
        (a, b) => a ++ b
      )

    var g3 = g2.joinVertices(tmp)((_, vd, u) => (vd._1, vd._2, probabilities(entropy(u.toArray.map(_._2)), vd._2.isSeedNode), calculateM(probabilities(entropy(u.toArray.map(_._2)), vd._2.isSeedNode), sumOfWeights(u), mu1,mu2,mu3)))

//    val a = g3.vertices.collect().map(x => x._2._3).foreach(println)

    var i = 0

    while (i < maxIter) {

      val newV =
        g3.aggregateMessages[Set[(Long, Double, HashMap[Int, Double], Double)]](  //(vid, weight, probDistribution, pcontNeighbor)
          triplet => {
            if (triplet.srcAttr._2.estimatedLabel(triplet.srcAttr._1).isDefined) {
              triplet.sendToDst(Set((triplet.srcId , triplet.attr, triplet.srcAttr._1, triplet.srcAttr._3._2)))
            }
            if (triplet.dstAttr._2.estimatedLabel(triplet.dstAttr._1).isDefined) {
              triplet.sendToSrc(Set((triplet.dstId , triplet.attr, triplet.dstAttr._1, triplet.dstAttr._3._2)))
            }
          },
          (a, b) => a ++ b
        )

      g3 = g3.joinVertices(newV)((_, vd, u) => if (vd._2.isSeedNode) {
        (multiHashMap(1/vd._4,
          weightedSum(Array(
            (mu1 * vd._3._1, HashMap[Int, Double](vd._2.label.get -> 1.0)),
            (mu2 * vd._3._2, calculateD(u.toArray.map(a => (a._2, a._3, vd._3._2, a._4)))),
            (mu3 * vd._3._3, dummyLabel)))),
          vd._2,vd._3,vd._4)
      }
      else {
        (multiHashMap(1/vd._4,
          weightedSum(Array(
            (mu2 * vd._3._2, calculateD(u.toArray.map(a => (a._2, a._3, vd._3._2, a._4)))),
            (mu3 * vd._3._3, dummyLabel)))),
          vd._2,vd._3,vd._4)
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

  def weightedSum(a:Array[(Double, HashMap[Int, Double])]) = {

    var result = HashMap[Int, Double]()

    for (l <- a) {
      result = result ++ l._2.map { case (k, v) => k -> (l._1 * v + result.getOrElse(k, 0.0)) }
    }

    result
  }

  def sumOfWeights(s:Set[(Long, Double)]) = {

    s.toSeq.map(_._2).sum
  }

  def multiHashMap(d: Double, h: HashMap[Int, Double]) = {

    h.map{ case (k, v) => k -> d * v}
  }

  def dummyLabels(c: Int) = {

    var m = HashMap[Int, Double](1 -> 0.5, 2->0.5)

    m
  }

  // (weight, labelScore, pcontv, pcontu)
  def calculateD(a: Array[(Double, HashMap[Int, Double], Double, Double)]) = {

    weightedSum(a.map(x => multiHashMap(x._3 * x._1 + x._4 * x._1, x._2)).map(x => (1.0,x)))
  }

  def calculateM(probabilities: (Double, Double, Double), sumOfWeight: Double, mu1: Double, mu2: Double, mu3: Double) = {

    mu1 * probabilities._1 + mu2 * probabilities._2 * sumOfWeight + mu3
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

  def estimatedLabel(labelScores:HashMap[Int, Double]): Option[Int] = {
    if (labelScores.size != 0)
      Some(labelScores.toArray.sortWith((a,b) => a._2 > b._2) (0)._1)
    else
      None
  }
}


