package leon.numerics.affine

import ceres.common.{Rational, RationalInterval, Interval}
import Rational._
import XRationalForm._
import collection.mutable.Queue

import Utils._

object XRationalForm {
  var maxNoiseCount = 200

  def apply(interval: RationalInterval): XRationalForm = {
    val a = interval.xlo
    val b = interval.xhi
    val un = (b - a)/ Rational(2)
    new XRationalForm(a + un, un)
  }

  // This is the equivalent of adding [-a, a]
  def addNoise(x: XRationalForm, n: Rational): XRationalForm = {
    val newTerms = new Queue[Deviation]()
    var iter = x.noise.iterator
    while(iter.hasNext) {
      newTerms += iter.next
    }
    newTerms += Deviation(newIndex, n)
    new XRationalForm(x.x0, newTerms)
  }

  var currIndex: Int = 0
  def newIndex: Int = {
    currIndex += 1
    assert(currIndex != Int.MaxValue, "Affine indices just ran out...")
    currIndex
  }
}

/*
  Represents a range of rational numbers.
*/
case class XRationalForm(val x0: Rational, var noise: Queue[Deviation]) {

  // Constant value
  def this(r: Rational) = this(r, new Queue[Deviation])

  // Creating a range of values in fixed point format
  def this(r: Rational, un: Rational) =
    this(r, new Queue[Deviation]() += Deviation(XRationalForm.newIndex, un))

  if (noise.size > maxNoiseCount) {
    println("Packing noise terms")
    noise = packRationalNoiseTerms(noise)
  }

  val radius: Rational = sumQueue(noise)

  /** This is the interval of values in R represented by this fixed-point range. */
  val interval: RationalInterval = {
    val rad = radius
    RationalInterval(x0 - rad, x0 + rad)
  }

  def intervalDouble = Interval(interval.xlo.toDouble, interval.xhi.toDouble)

  override def toString: String =
    "[%f,%f]".format(intervalDouble.xlo, intervalDouble.xhi)

  def absValue: XRationalForm = {
    if (Rational(0) <= x0) return this else return -this
  }

  def isNonZero: Boolean = return (x0 != 0 || noise.size > 0)

  /**
    Negates this XRationalForm.
   */
  def unary_-(): XRationalForm = {
    var newTerms = new Queue[Deviation]()
    var iter = noise.iterator
    while(iter.hasNext) {
      newTerms += - iter.next  // just flip the sign
    }
    new XRationalForm(-x0, newTerms)
  }


  def +(y: XRationalForm): XRationalForm =
    return new XRationalForm(this.x0 + y.x0, addQueues(this.noise, y.noise))

  def -(y: XRationalForm): XRationalForm =
    return new XRationalForm(this.x0 - y.x0, subtractQueues(this.noise, y.noise))

  def *(y: XRationalForm): XRationalForm = {
    var z0 = this.x0 * y.x0
    var (z0Addition, delta) = multiplyNonlinearQueues2(this.noise, y.noise)
    z0 += z0Addition
    val newTerms = multiplyQueues(this.x0, this.noise, y.x0, y.noise)
    if(delta != 0)
      newTerms += Deviation(newIndex, delta)
    return new XRationalForm(z0, newTerms)
  }

  /**
    Computes the inverse of this XRationalForm as a linear approximation.
   */
  def inverse: XRationalForm = {
    val (xlo, xhi) = (interval.xlo, interval.xhi)

    if (xlo <= Rational(0.0) && xhi >= Rational(0.0))
      throw DivisionByZeroException("Possible division by zero: " + toString)

    if(noise.size == 0.0) { //exact
      val inv = Rational(1.0)/x0
      return new XRationalForm(inv, new Queue[Deviation]())
    }

    /* Calculate the inverse */
    val a = min(abs(xlo), abs(xhi))
    val b = max(abs(xlo), abs(xhi))

    val alpha = Rational(-1.0) / (b * b)

    val dmax = (Rational(1.0) / a) - (alpha * a)
    val dmin = (Rational(1.0) / b) - (alpha * b)

    var zeta = (dmin / Rational(2.0)) + (dmax / Rational(2.0))
    if (xlo < Rational(0.0)) zeta = -zeta
    val delta = max( zeta - dmin, dmax - zeta )

    val z0 = alpha * this.x0 + zeta

    var newTerms = multiplyQueue(noise, alpha)
    if(delta != 0.0) newTerms += new Deviation(newIndex, delta)
    return new XRationalForm(z0, newTerms)
  }

  /**
   Computes x/y as x * (1/y).
   */
  def /(y: XRationalForm): XRationalForm = {
    return this * y.inverse
  }

  private def multiplyNonlinearQueues(xqueue: Queue[Deviation], yqueue: Queue[Deviation]): Rational = {
    val indices = mergeIndices(getIndices(xqueue), getIndices(yqueue))
    var zqueue = Rational(0.0)

    var i = 0
    while (i < indices.length) {
      val iInd = indices(i)
      // quadratic
      val xi = xqueue.find((d: Deviation) => d.index == iInd) match {
        case Some(d) => d.value; case None => Rational(0) }
      val yi = yqueue.find((d: Deviation) => d.index == iInd) match {
        case Some(d) => d.value; case None => Rational(0) }
      val zii = xi * yi
      if (zii != 0) zqueue += abs(zii)

      var j = i + 1
      while (j < indices.length) {
        val jInd = indices(j)
        val xj = xqueue.find((d: Deviation) => d.index == jInd) match {
        case Some(d) => d.value; case None => Rational(0) }
        val yj = yqueue.find((d: Deviation) => d.index == jInd) match {
        case Some(d) => d.value; case None => Rational(0) }
        val zij = xi * yj + xj * yi
        if (zij != 0) zqueue += abs(zij)
        j += 1
      }
      i += 1
    }
    zqueue
  }

  // Does a smarter computation of the quadratic terms
  private def multiplyNonlinearQueues2(xqueue: Queue[Deviation], yqueue: Queue[Deviation]): (Rational, Rational) = {
    val indices = mergeIndices(getIndices(xqueue), getIndices(yqueue))
    var zqueue = Rational(0.0)
    var z0Addition = Rational(0.0)

    var i = 0
    while (i < indices.length) {
      val iInd = indices(i)
      // quadratic
      val xi = xqueue.find((d: Deviation) => d.index == iInd) match {
        case Some(d) => d.value; case None => Rational(0) }
      val yi = yqueue.find((d: Deviation) => d.index == iInd) match {
        case Some(d) => d.value; case None => Rational(0) }
      val zii = xi * yi
      z0Addition += zii / Rational(2.0)
      if (zii != 0) zqueue += abs(zii / Rational(2.0))

      var j = i + 1
      while (j < indices.length) {
        val jInd = indices(j)
        val xj = xqueue.find((d: Deviation) => d.index == jInd) match {
          case Some(d) => d.value; case None => Rational(0) }
        val yj = yqueue.find((d: Deviation) => d.index == jInd) match {
        case Some(d) => d.value; case None => Rational(0) }
        val zij = xi * yj + xj * yi
        if (zij != 0) zqueue += abs(zij)
        j += 1
      }
      i += 1
    }
    (z0Addition, zqueue)
  }

  private def mergeIndices(x: Set[Int], y: Set[Int]): Array[Int] = {
    val set = x ++ y
    val list = set.toList.sorted
    return list.toArray
  }

  private def packRationalNoiseTerms(queue: Queue[Deviation]): Queue[Deviation] = {
    var sum = sumQueue(queue)
    val avrg: Double = sum.toDouble / queue.size

    //compute st. deviation
    var devSum = 0.0
    val iter = queue.iterator
    while(iter.hasNext) {
      val diff = (abs(iter.next.value).toDouble - avrg).toDouble
      devSum += diff * diff
    }
    val stdDev = math.sqrt(devSum/queue.size)
    val threshold: Double = avrg + stdDev

    //Now compute the new queue
    var newNoise = Rational(0)
    var newDev = new Queue[Deviation]()

    val iter2 = queue.iterator
    while(iter2.hasNext) {
      val xi = iter2.next
      val v = abs(xi.value)
      if(v.toDouble < threshold) newNoise += v
      else newDev += xi
    }
    newDev += new Deviation(newIndex, newNoise)
    return newDev
  }

}
