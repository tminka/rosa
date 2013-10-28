/* Copyright 2013 EPFL, Lausanne */

package leon
package real

import purescala.Trees.{Variable, Expr}
import purescala.TypeTrees._
import purescala.Common._

import real.Trees.{RealLiteral, TimesR, PlusR}

object VariableShop {

  private var errCounter = 0
  private var deltaCounter = 0
  private var sqrtCounter = 0
  private var fncCounter = 0
  private var resErrorCounter = 0
  private var xfloatCounter = 0
  private var freshVarCounter = 0

  def getNewXFloatVar: Variable = { // used for compacting xfloats
    xfloatCounter = xfloatCounter + 1
    Variable(FreshIdentifier("#xvar_" + xfloatCounter)).setType(RealType)
  }
  def getNewErrorVar: Variable = {
    errCounter = errCounter + 1
    Variable(FreshIdentifier("#err_" + errCounter)).setType(RealType)
  }
  def getNewDelta: Variable = {
    deltaCounter = deltaCounter + 1
    Variable(FreshIdentifier("#delta_" + deltaCounter)).setType(RealType)
  }
  def getNewSqrtVariablePair: (Variable, Variable) = {
    sqrtCounter = sqrtCounter + 1
    (Variable(FreshIdentifier("#sqrt" + sqrtCounter)).setType(RealType),
      Variable(FreshIdentifier("#sqrt" + sqrtCounter + "_0")).setType(RealType))
  }
  def getNewSqrtVariable: Variable = {
    sqrtCounter = sqrtCounter + 1
    Variable(FreshIdentifier("#sqrt" + sqrtCounter)).setType(RealType)
  }

  def getNewFncVariable(id: String): Variable = {
    fncCounter = fncCounter + 1
    Variable(FreshIdentifier("#" + id + "_call_" + fncCounter)).setType(RealType)
  }

  def getNewResErrorVariable: Variable = {
    resErrorCounter = resErrorCounter + 1
    Variable(FreshIdentifier("#resErr" + resErrorCounter)).setType(RealType)
  }

  def getNamedError(v: Expr): Variable = {
    Variable(FreshIdentifier("#err_(" + v.toString + ")")).setType(RealType)
  }

  def getRndoff(expr: Expr): (Expr, Variable) = {
    val delta = getNewDelta
    (TimesR(expr, delta), delta)
  }

  def getFreshRndoffMultiplier: (Expr, Variable) = {
    val delta = getNewDelta
    (PlusR(new RealLiteral(1), delta) , delta)
  }

  def getFreshVarOf(name: String): Variable = {
    freshVarCounter = freshVarCounter + 1
    Variable(FreshIdentifier("#" + name + freshVarCounter)).setType(RealType)
  }
}