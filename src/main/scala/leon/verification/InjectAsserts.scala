/* Copyright 2009-2015 EPFL, Lausanne */

package leon
package utils

import purescala.Common._
import purescala.Trees._
import xlang.Trees._
import purescala.TreeOps._
import purescala.Definitions._

object InjectAsserts extends LeonPhase[Program, Program] {

  val name = "Asserts"
  val description = "Inject asserts for various corrected conditions (map accesses, array accesses, ..)"

  def run(ctx: LeonContext)(pgm: Program): Program = {
    def indexUpTo(i: Expr, e: Expr) = {
      And(GreaterEquals(i, IntLiteral(0)), LessThan(i, e))
    }

    pgm.definedFunctions.foreach(fd => {
      fd.body = fd.body.map(postMap { 
        case e @ ArraySelect(a, i)        =>
          Some(Assert(indexUpTo(i, ArrayLength(a)), Some("Array index out of range"), e).setPos(e))
        case e @ ArrayUpdated(a, i, _)    =>
          Some(Assert(indexUpTo(i, ArrayLength(a)), Some("Array index out of range"), e).setPos(e))
        case e @ ArrayUpdate(a, i, _)  =>
          Some(Assert(indexUpTo(i, ArrayLength(a)), Some("Array index out of range"), e).setPos(e))
        case e @ MapGet(m,k) =>
          Some(Assert(MapIsDefinedAt(m, k), Some("Map undefined at this index"), e).setPos(e))
        case _ =>
          None
      }) 
    })

    pgm
  }
}
