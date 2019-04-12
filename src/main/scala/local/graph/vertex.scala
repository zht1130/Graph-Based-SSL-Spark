package local.graph

import scala.collection.mutable.HashMap
import org.apache.spark.ml.linalg.Vector

case class vertex(id: Long, features: Vector, label: Option[Int]) extends Serializable {

  var isSeedNode = if (label.isDefined) true else false

  var labelScores = if (label.isDefined)
                      HashMap[Int, Double](label.get -> 1.0)
                    else
                      HashMap[Int, Double]()

  var probabilities = (0.0, 1.0, 0.0)

  var M = -1.0;

  def estimatedLabel(labelScores:HashMap[Int, Double]): Option[Int] = {
    if (labelScores.size != 0)
      Some(labelScores.toArray.sortWith((a,b) => a._2 > b._2) (0)._1)
    else
      None
  }

//  def euclideanDist (that:localVertex): Double =
//    math.sqrt((features zip that.features).map{case (p,q) => math.pow(q - p, 2)}.sum)

}

