/* Copyright 2009-2015 EPFL, Lausanne */

/*package leon

import scala.language.implicitConversions

import scala.math.{ScalaNumericConversions, ScalaNumber}

  object Real {
    class NotExecutableException(msg: String) extends Exception
    private val exMsg = "don't know how to implement reals"

    implicit def double2real(d: Double): Real = new Real(d)
    implicit def int2real(i: Int): Real = new Real(i.toDouble)

    def sqrt(x: Real): Real = { throw new NotExecutableException(exMsg); null }
  }

  class Real private(v: Double) extends ScalaNumber with ScalaNumericConversions with Ordered[Real] {
    import Real._

    def unary_-(): Real = { throw new NotExecutableException(exMsg); null }
    def +(other: Real): Real = { throw new NotExecutableException(exMsg); null }
    def -(other: Real): Real = { throw new NotExecutableException(exMsg); null }
    def *(other: Real): Real = { throw new NotExecutableException(exMsg); null }
    def /(other: Real): Real = { throw new NotExecutableException(exMsg); null }

    def unary_~(): Real = { throw new NotExecutableException(exMsg); null }

    // Convenience method to specify intervals
    //def in(a: Real, b: Real): Boolean = { throw new NotExecutableException(exMsg); false }
    def ><(a: (Real, Real)): Boolean = { throw new NotExecutableException(exMsg); false }

    // Uncertainty on this value
    def +/-(x: Real): Boolean = { throw new NotExecutableException(exMsg); false }
    // Error of this value
    def unary_!(): Real = { throw new NotExecutableException(exMsg); null }

      
    def compare(other: Real): Int = {
      throw new NotExecutableException(exMsg)
      0
    }

    def underlying(): AnyRef = this
    def isWhole(): Boolean = { throw new NotExecutableException(exMsg); false }
    def doubleValue(): Double = { throw new NotExecutableException(exMsg); 0.0 }
    def floatValue(): Float = { throw new NotExecutableException(exMsg); 0.0f }
    def longValue(): Long = { throw new NotExecutableException(exMsg); 0l }
    def intValue(): Int = { throw new NotExecutableException(exMsg); 0 }
  }*/
