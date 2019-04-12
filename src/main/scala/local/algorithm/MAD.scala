package local.algorithm

import org.jgrapht.Graphs
import org.jgrapht.graph._
import local.graph.vertex

import scala.collection.JavaConversions._
import scala.collection.mutable.HashMap

case class MAD (g: WeightedPseudograph[vertex, DefaultWeightedEdge], dummyLabel: HashMap[Int, Double], mu1: Double, mu2: Double, mu3: Double) {

  def run(maxIterations:Int = 0) = {

    val maxIter = if (maxIterations == 0) g.vertexSet().size() / 500 else maxIterations

    for (v <- g.vertexSet()) {

      val neighbors = Graphs.neighborListOf(g, v).toList
      val entropy = getEntropy(neighbors.map(x => g.getEdgeWeight(g.getEdge(x, v))).toArray)
      v.probabilities = probabilities(entropy, v.isSeedNode)
      v.M = mu1 * v.probabilities._1 + mu2 * v.probabilities._2 * neighbors.map(x => getNeighborWeight(x, v)).sum + mu3
    }


    var i = 0

    while (i < maxIter) {

      for (v <- g.vertexSet()) {

        val neighbors = Graphs.neighborListOf(g, v).toList

        val D = weightedSum(
          neighbors.map(x => multiHashMap(x.probabilities._2 * getNeighborWeight(x, v) + v.probabilities._2 * getNeighborWeight(v, x), x.labelScores))
        .map(x => (1.0,x)))


        if (v.isSeedNode) {
          v.labelScores = multiHashMap(1/v.M,
            weightedSum(List(
              (mu1 * v.probabilities._1, HashMap[Int, Double](v.label.get -> 1.0)),
              (mu2 * v.probabilities._2, D),
              (mu3 * v.probabilities._3, dummyLabel))))
        }
        else {
          v.labelScores = multiHashMap(1/v.M,
            weightedSum(List(
              (mu2 * v.probabilities._2, D),
              (mu3 * v.probabilities._3, dummyLabel))))
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

  def weightedSum(a:List[(Double, HashMap[Int, Double])]) = {

    var result = HashMap[Int, Double]()

    for (l <- a) {
      result = result ++ l._2.map { case (k, v) => k -> (l._1 * v + result.getOrElse(k, 0.0)) }
    }

    result
  }

  def multiHashMap(d: Double, h: HashMap[Int, Double]) = {

    h.map{ case (k, v) => k -> d * v}

  }

  def getNeighborWeight(v1: vertex, v2: vertex) = {

    g.getEdgeWeight(g.getEdge(v1,v2))
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

}

