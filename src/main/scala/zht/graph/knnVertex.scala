package zht.graph

import scala.collection.mutable.HashMap

case class knnVertex(features:Array[Double], label:Option[Int]) extends Serializable {

  var isSeedNode = if (label.isDefined) true else false

//  var labelScores = HashMap[Int, Double]()

  def estimatedLabel(labelScores:HashMap[Int, Double]): Option[Int] = {
    if (labelScores.size != 0)
      Some(labelScores.toArray.sortWith((a,b) => a._2 > b._2) (0)._1)
    else
      None
  }

  def euclideanDist (that:knnVertex): Double =
    math.sqrt((features zip that.features).map{case (p,q) => math.pow(q - p, 2)}.sum)

}
