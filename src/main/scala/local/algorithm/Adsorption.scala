package local.algorithm

import org.jgrapht.Graphs
import org.jgrapht.graph._
import local.graph.vertex

import scala.collection.JavaConversions._
import scala.collection.mutable.HashMap

case class adsorption (g: WeightedPseudograph[vertex, DefaultWeightedEdge], dummyLabel: HashMap[Int, Double]) {

  def run(maxIterations:Int = 0) = {

    val maxIter = if (maxIterations == 0) g.vertexSet().size() / 500 else maxIterations

    for (v <- g.vertexSet()) {

      val neighbors = Graphs.neighborListOf(g, v).toList
      val entropy = getEntropy(neighbors.map(x => g.getEdgeWeight(g.getEdge(x, v))).toArray)
      v.probabilities = probabilities(entropy, v.isSeedNode)
    }

    var i = 0

    while (i < maxIter) {

      for (v <- g.vertexSet()) {
        val neighbors = Graphs.neighborListOf(g, v).toList
        if (v.isSeedNode) {
          v.labelScores = weightedAverage(List(
            (v.probabilities._1, HashMap[Int, Double](v.label.get -> 1.0)),
            (v.probabilities._2, weightedAverage(neighbors.map(x => (g.getEdgeWeight(g.getEdge(x, v)), x.labelScores)))),
            (v.probabilities._3, dummyLabel)))
        }
        else {
          v.labelScores = weightedAverage(List(
            (v.probabilities._2, weightedAverage(neighbors.map(x => (g.getEdgeWeight(g.getEdge(x, v)), x.labelScores)))),
            (v.probabilities._3, dummyLabel)))
        }
      }

      i += 1
    }

    for (v <- g.vertexSet())
      println(v.labelScores)

    g.vertexSet().filter(v => !v.isSeedNode).map(v => (v.id, v.estimatedLabel(v.labelScores))).toArray.iterator
  }

  def weightedAverage(a:List[(Double, HashMap[Int, Double])]) = {

    var result = HashMap[Int, Double]()

    for (l <- a) {
      result = result ++ l._2.map { case (k, v) => k -> (l._1 * v + result.getOrElse(k, 0.0)) }
    }

    result.map{ case (k,v) => k -> (v / a.map(_._1).sum) }
  }

  def getEntropy(a:Array[Double]) = {

    var entropy = 0.0
    val sum = a.sum

    for (w <- a) {
      val p = w/sum
      entropy += -1 * p * Math.log(p) / Math.log(2)
    }

    entropy
  }

  def probabilities(entropy: Double, labeled: Boolean, beta: Double = 2) = {

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

//  def dummyLabels(c: Int) = {
//
//    var m = HashMap[Int, Double](1 -> 0.5, 2->0.5)
//
////    for (i <- 0 to 2-1)
////      m += (i -> 1.0/2)
//
//    m
//  }

}

