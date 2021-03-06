/* Copyright 2009-2015 EPFL, Lausanne */

package leon
package real

import purescala.TransformerWithPC
import purescala.Common._
import purescala.Definitions._
import purescala.Trees._
import purescala.TreeOps._
import purescala.TypeTrees._

import xlang.Trees._
import real.Trees._
import real.RationalAffineUtils._
import VCKind._
import VariableShop._
import real.{FixedPointFormat => FPFormat}
import Rational._

object TreeOps {

  def getAdditionalRealConstraints(e: Expr) = {
    getClauses(e).filter(cl => !belongsToActual(cl) && !isRangeConstraint(cl))
  }

  def isRangeConstraint(e: Expr): Boolean = e match {
    case GreaterThan(RealLiteral(_),Variable(_)) |
        GreaterEquals(RealLiteral(_), Variable(_)) |
        LessThan(RealLiteral(_),Variable(_)) |
        LessEquals(RealLiteral(_),Variable(_)) => true

    case GreaterThan(Variable(_), RealLiteral(_)) |
        GreaterEquals(Variable(_), RealLiteral(_)) |
        LessThan(Variable(_), RealLiteral(_)) |
        LessEquals(Variable(_), RealLiteral(_)) => true

    case _ => false
  }


  /*def deduplicateClauses(expr: Expr): Expr = expr match {
    case And(args) => And(args.toSet.toSeq)
    case _ => expr
  }*/

  def rangeConstraint(v: Expr, i: RationalInterval): Expr = {
    And(LessEquals(RealLiteral(i.xlo), v), LessEquals(v, RealLiteral(i.xhi)))
  }

  def rangeConstraint(vars: Map[Expr, XReal]): Expr = {
    val clauses: Seq[Expr] = vars.flatMap({
      case (v, xreal) => Seq(LessEquals(RealLiteral(xreal.interval.xlo), v),
                              LessEquals(v, RealLiteral(xreal.interval.xhi)))
      }).toSeq
    And(clauses)
  }



  def rangeConstraintFromIntervals(vars: Map[Expr, RationalInterval]): Expr = {
    val clauses: Seq[Expr] = vars.flatMap({
      case (v, i) => Seq(LessEquals(RealLiteral(i.xlo), v),
                              LessEquals(v, RealLiteral(i.xhi)))
      }).toSeq
    And(clauses)
  }

  def getValMapForInlining(body: Expr): Map[Expr, Expr] = {
    var valMap: Map[Expr, Expr] = Map.empty
    preTraversal { expr => expr match {
        case Equals(v @ Variable(id), rhs) =>
          valMap = valMap + (v -> replace(valMap,rhs))
        case _ => ;
      }
    }(body)
    valMap
  }

  def inlineBody(body: Expr): Expr = {
    var valMap: Map[Expr, Expr] = Map.empty
    val lastInstruction = preMap { expr => expr match {

        case Equals(v @ Variable(id), rhs) =>
          valMap = valMap + (v -> replace(valMap,rhs))
          Some(True)

        case x => Some(x)  //last instruction
      }
    }(body)
    val res = replace(valMap, lastInstruction)
    res
  }

  /*
    Performs some pre-processing of the constraint in order to increase solving success.
    - equality propagation
    - removing of redundant bounds constraints (included in another)
  */
  def simplifyConstraint(e: Expr, removeBounds: Boolean = false,
    equalityPropagation: Boolean = true): Expr = {
    // for bounds, first collect all bounds, then re-generate constraints
    
    val expr = if (equalityPropagation) {
      // for equality propagation, replace variables by their definition
      propagateEquals(e)
    } else {
      e
    }


    if (removeBounds) {
      val boundsCollector = new TightBoundsCollector
      val rem = boundsCollector.transform(expr)
      And(And(boundsCollector.getConstraints), rem)
    } else {
      expr
    }

  }

  def propagateEquals(body: Expr): Expr = {
    var valMap: Map[Expr, Expr] = Map.empty
    preMap { expr => expr match {
        case Equals(v @ Variable(id), rhs) =>
          valMap = valMap + (v -> replace(valMap,rhs))
          Some(Equals(v, replace(valMap, rhs)))
        case _ => None
      }
    }(body)
  }

  /*def getClausesSet(e: Expr): Set[Expr] = e match {
    case And(args) => args.toSet
    case _ => Set(e)
  }*/

  def getClauses(e: Expr): Seq[Expr] = e match {
    case And(args) => args
    case _ => Seq(e)
  }

  def removeRedundantConstraints(body: Expr, post: Expr): Set[Expr] = {
    //println("Computing needed for " + post)
    // we can try a fixed-point computation, starting with the constraint from the postcondition,
    // and the set of constraints from the body, iteratively adding constraints which contain
    // variables needed to prove the postcondition

    var neededVars: Set[Identifier] = variablesOf(post)
    var currentNeeded: Set[Expr] = Set.empty
    var currentWaiting: Set[Expr] = getClauses(body).toSet

    var continue = true
    while( continue ) {
      //println("\nNew round")
      //println(s"neededVars: $neededVars")
      //println(s"currentNeeded: $currentNeeded")
      //println(s"currentWaiting: $currentWaiting")

      val newNeeded = currentWaiting.filter(e => {
        variablesOf(e).intersect(neededVars).size > 0
        })
      //println("newNeeded: " + newNeeded)
      val newVars = variablesOf(And(newNeeded.toSeq)).diff(neededVars)
      //println("newVars: " + newVars)

      currentNeeded = currentNeeded ++ newNeeded
      
      if (newVars.size == 0) {
        continue = false
      } else {
        continue = true
        neededVars = neededVars ++ newVars
      }
    }
    //println("\nfinal result: " + currentNeeded)
    currentNeeded
  } 

  private class TightBoundsCollector extends TransformerWithPC {
    type C = Seq[Expr]
    val initC = Nil

    case class Bound(loEq: Option[Rational], lo: Option[Rational], up: Option[Rational], upEq: Option[Rational], absUncert: Option[Rational], relUncert: Option[Rational]) {
      // update to tightest bounds
      def updateLoEqTight(n: Rational): Bound = {
        if (loEq.nonEmpty) Bound(Some(max(n, loEq.get)), lo, up, upEq, absUncert, relUncert)
        else Bound(Some(n), lo, up, upEq, absUncert, relUncert)
      }
      def updateLoTight(n: Rational): Bound = {
        if (lo.nonEmpty) Bound(loEq, Some(max(n, lo.get)), up, upEq, absUncert, relUncert)
        else Bound(loEq, Some(n), up, upEq, absUncert, relUncert)
      }
      def updateUpTight(n: Rational): Bound = {
        if (up.nonEmpty) Bound(loEq, lo, Some(min(n, up.get)), upEq, absUncert, relUncert)
        else Bound(loEq, lo, Some(n), upEq, absUncert, relUncert)
      }
      def updateUpEqTight(n: Rational): Bound = {
        if (upEq.nonEmpty) Bound(loEq, lo, up, Some(min(n, upEq.get)), absUncert, relUncert)
        else Bound(loEq, lo, up, Some(n), absUncert, relUncert)
      }
      def updateAbsUncertTight(n: Rational): Bound = {
        if (absUncert.nonEmpty) Bound(loEq, lo, up, upEq, Some(min(n, absUncert.get)), relUncert)
        else Bound(loEq, lo, up, upEq, Some(n), relUncert)
      }
      def updateRelUncertTight(n: Rational): Bound = {
        if (relUncert.nonEmpty) Bound(loEq, lo, up, upEq, absUncert, Some(min(n, relUncert.get)))
        else Bound(loEq, lo, up, upEq, absUncert, Some(n))
      }
    }
    val emptyBound = Bound(None, None, None, None, None, None)

    // indexed by variable
    var boundMap = Map[Expr, Bound]()

    def register(e: Expr, path: C): C = path :+ e

    // (Sound) Overapproximation in the case of strict inequalities
    // Removes the recorded constraints
    override def rec(e: Expr, path: C): Expr = e match {
      case LessEquals(RealLiteral(lwrBnd), x @ Variable(_)) => // a <= x
        boundMap += (x -> boundMap.getOrElse(x, emptyBound).updateLoEqTight(lwrBnd)); True

      case LessEquals(x @ Variable(_), RealLiteral(uprBnd)) => // x <= b
        boundMap += (x -> boundMap.getOrElse(x, emptyBound).updateUpEqTight(uprBnd)); True

      case LessThan(RealLiteral(lwrBnd), x @ Variable(_)) => // a < x
        boundMap += (x -> boundMap.getOrElse(x, emptyBound).updateLoTight(lwrBnd)); True

      case LessThan(x @ Variable(_), RealLiteral(uprBnd)) => // x < b
        boundMap += (x -> boundMap.getOrElse(x, emptyBound).updateUpTight(uprBnd)); True

      case GreaterEquals(RealLiteral(uprBnd), x @ Variable(_)) => // b >= x
        boundMap += (x -> boundMap.getOrElse(x, emptyBound).updateUpEqTight(uprBnd)); True

      case GreaterEquals(x @ Variable(_), RealLiteral(lwrBnd)) => // x >= a
        boundMap += (x -> boundMap.getOrElse(x, emptyBound).updateLoEqTight(lwrBnd)); True

      case GreaterThan(RealLiteral(uprBnd), x @ Variable(_)) => // b > x
        boundMap += (x -> boundMap.getOrElse(x, emptyBound).updateUpTight(uprBnd)); True

      case GreaterThan(x @ Variable(_), RealLiteral(lwrBnd)) => // x > a
        boundMap += (x -> boundMap.getOrElse(x, emptyBound).updateLoTight(lwrBnd)); True

      case Noise(x @ Variable(_), RealLiteral(value)) =>
        boundMap += (x -> boundMap.getOrElse(x, emptyBound).updateAbsUncertTight(value)); True

      case Noise(_, _) =>
        throw UnsupportedRealFragmentException(e.toString); True

      case RelError(x @ Variable(id), RealLiteral(value)) =>
        boundMap += (x -> boundMap.getOrElse(x, emptyBound).updateRelUncertTight(value)); True

      case WithIn(x @ Variable(_), lwrBnd, upBnd) =>
        boundMap += (x -> boundMap.getOrElse(x, emptyBound).updateLoTight(lwrBnd).updateUpTight(upBnd)); True

      case WithIn(e, lwrBnd, upBnd) =>
        throw UnsupportedRealFragmentException(e.toString); True

      case Or(_) | Not(_) =>
        throw new Exception("OR and NOT are not allowed when computing bounds"); e

      case i: IfExpr => i
      case _ =>
        super.rec(e, path)

    }

    def getConstraints: Seq[Expr] = {
      boundMap.map({
        case (v, Bound(loEq, lo, up, upEq, absUncert, relUncert)) =>
          var cnstrs = Seq[Expr]()
          if (loEq.nonEmpty) cnstrs :+= LessEquals(RealLiteral(loEq.get), v)
          if (lo.nonEmpty) cnstrs :+= LessThan(RealLiteral(lo.get), v)

          if (up.nonEmpty) cnstrs :+= LessThan(v, RealLiteral(up.get))
          if (upEq.nonEmpty) cnstrs :+= LessEquals(v, RealLiteral(upEq.get))

          if (absUncert.nonEmpty) cnstrs :+= Noise(v, RealLiteral(absUncert.get))
          if (relUncert.nonEmpty) cnstrs :+= RelError(v, RealLiteral(relUncert.get))
          (v -> And(cnstrs))
        }).values.toSeq
    }

  }

  def letsToEquals(expr: Expr): Expr = expr match {
    case Equals(l, r) => Equals(l, letsToEquals(r))
    case IfExpr(cond, thenn, elze) =>
      IfExpr(cond, letsToEquals(thenn), letsToEquals(elze))

    case Let(binder, value, body) =>
      And(Equals(Variable(binder), letsToEquals(value)), letsToEquals(body))

    case Block(exprs, last) =>
      And(exprs.map(e => letsToEquals(e)) :+ letsToEquals(last))

    case _ => expr
  }

  def containsIteration(expr: Expr): Boolean = {
    exists {
      case _: Iteration => true
      case _ => false
    }(expr)
  }

  def containsIfExpr(expr: Expr): Boolean = {
    exists{
      case _: IfExpr => true
      case _ => false
    }(expr)
  }

  def containsSqrt(expr: Expr): Boolean = {
    exists{
      case _: SqrtR => true
      case _ => false
    }(expr)
  }

  /* ----------------------
         Analysis phase
   ------------------------ */
  def addResults(expr: Expr, variables: Seq[Expr]): Expr = expr match {
    case Equals(v, IfExpr(c, t, e)) =>
      IfExpr(c, addResults(t, Seq(v)), addResults(e, Seq(v)))

    case Equals(_,_) => expr

    case IfExpr(c, t, e) =>
      IfExpr(c, addResults(t, variables), addResults(e, variables))

    case LessEquals(_, _) | LessThan(_,_) | GreaterThan(_,_) | GreaterEquals(_,_) => expr

    case And(ands) => And(ands.map( addResults(_, variables)))
    case Or(ors) => Or(ors.map(addResults(_, variables)))

    case UMinusR(_) | PlusR(_, _) | MinusR(_, _) | TimesR(_, _) | DivisionR(_, _) | SqrtR(_) | Variable(_) =>
      Equals(variables.head, expr)

    case BooleanLiteral(_) => expr

    case Noise(_,_) => expr

    case Tuple(bases) =>
      assert(bases.length == variables.length)
      And(variables.zip(bases).map({
        case (resVar, tplPart) => Equals(resVar, tplPart)
        }))

    case Not(t) => Not(addResults(t, variables))

    case FncValue(specs, specExpr, _, _, _) =>
      //println("specs: " + specs + ", vars.len: " + variables.length)
      assert(specs.length == variables.length)
      And(variables.zip(specs).map({
        case (resVar, spec) =>
          //And(specToExpr(spec), Equals(resVar, Variable(spec.id)))
          And(specExpr, Equals(resVar, Variable(spec.id)))
        }))
    
    // TODO: this won't work with tuples
    //case FunctionInvocation(_, _) => Equals(variables.head, expr)
  }

  def addResultsF(expr: Expr, variables: Seq[Expr]): Expr = expr match {
    case EqualsF(v, FloatIfExpr(c, t, e)) =>
      FloatIfExpr(c, addResultsF(t, Seq(v)), addResultsF(e, Seq(v)))

    case EqualsF(_,_) => expr

    case FloatIfExpr(c, t, e) =>
      FloatIfExpr(c, addResultsF(t, variables), addResultsF(e, variables))

    case LessEquals(_, _) | LessThan(_,_) | GreaterThan(_,_) | GreaterEquals(_,_) => expr

    case And(ands) => And(ands.map( addResultsF(_, variables)))
    case Or(ors) => Or(ors.map(addResultsF(_, variables)))

    case UMinusF(_) | PlusF(_, _) | MinusF(_, _) | TimesF(_, _) | DivisionF(_, _) | SqrtF(_) | Variable(_) | FloatLiteral(_) =>
      EqualsF(variables.head, expr)

    case BooleanLiteral(_) => expr

    case Noise(_,_) => expr

    case Tuple(bases) =>
      assert(bases.length == variables.length)
      And(variables.zip(bases).map({
        case (resVar, tplPart) => EqualsF(resVar, tplPart)
        }))

    case Not(t) => Not(addResultsF(t, variables))

    case FncValueF(specs, specExpr, _, _) =>
      assert(specs.length == variables.length)
      And(variables.zip(specs).map({
        case (resVar, spec) =>
          //And(specToExpr(spec), EqualsF(resVar, Actual(Variable(spec.id))))
          And(specExpr, EqualsF(resVar, Actual(Variable(spec.id))))
        }))
    
    // TODO: this won't work with tuples
    //case FunctionInvocationF(_, _) => EqualsF(variable.get, expr)
  }

  /* -----------------------
             Paths
   ------------------------- */
  def getPaths(expr: Expr): Set[(Expr, Expr)] = {
    collectPaths(expr).map(p => (p.condition, And(p.expression)))
  }

  case class PartialPath(condition: Expr, expression: Seq[Expr]) {
    def addCondition(c: Expr): PartialPath =
      PartialPath(And(condition, c), expression)

    def addPath(p: PartialPath): PartialPath = {
      PartialPath(And(this.condition, p.condition), this.expression ++ p.expression)
    }

    def addEqualsToLast(e: Expr): PartialPath = {
      PartialPath(condition, expression.init ++ List(Equals(e, expression.last)))
    }
  }

  def collectPaths(expr: Expr): Set[PartialPath] = expr match {
    case IfExpr(cond, thenn, elze) =>
      val thenPaths = collectPaths(thenn).map(p => p.addCondition(cond))
      val elzePaths = collectPaths(elze).map(p => p.addCondition(negate(cond)))

      thenPaths ++ elzePaths

    case And(args) =>
      var currentPaths: Set[PartialPath] = collectPaths(args.head)

      for (a <- args.tail) {
        var newPaths: Set[PartialPath] = Set.empty
        val nextPaths = collectPaths(a)

        for (np <- nextPaths; cp <- currentPaths)
          newPaths = newPaths + cp.addPath(np)

        currentPaths = newPaths
      }
      currentPaths

    case Equals(e, f) =>
      collectPaths(f).map(p => p.addEqualsToLast(e))

    case _ =>
      Set(PartialPath(True, List(expr)))
  }


  /* -----------------------
        Function calls
   ------------------------- */
  
  def extractPostCondition(resId: Identifier, postExpr: Expr, resFresh: Seq[Identifier]): Expr = postExpr match {
    case MatchExpr(Variable(scrutinee),
      Seq(SimpleCase(TuplePattern(None, wildcards), caseExpr))) if (scrutinee == resId) =>
      val replaceMap: Map[Expr, Expr] = wildcards.map({
        case WildcardPattern(Some(id)) => Variable(id)
        case _ => throw new Exception("Unsupported match expression"); null
        }).zip(resFresh.map(Variable(_))).toMap
      
      replace(replaceMap, caseExpr)
     
    case m: MatchExpr =>
      throw new Exception("! Wrong use of match expression for postcondition !")
      null

    case _ => // simple case (no tuples)
      replace(Map(Variable(resId) -> Variable(resFresh.head)), postExpr)
  }
  

  /**
    Removes all error terms.
  */
  def removeErrors(e: Expr): Expr = {
    preMap {
      case Noise(_,_) | Roundoff(_) | RelError(_,_) => Some(True)
      case _ => None
    }(e)
  }

  /**
    Removes only Roundoff() terms.
  */
  def removeRoundoff(e: Expr): Expr = {
    preMap {
      case Roundoff(_) => Some(True)
      case _ => None
    }(e) 
  }

  /**
    Removes all errors and replaces ~x with x.
  */
  def removeErrorsAndActual(e: Expr): Expr = {
    preMap {
      case Noise(_,_) | Roundoff(_) | RelError(_,_) => Some(True)
      case LessThan(Actual(e), _ ) => Some(True)
      case LessThan(_, Actual(e)) => Some(True)

      case LessEquals(Actual(e), _ ) => Some(True)
      case LessEquals(_, Actual(e)) => Some(True)
      
      case GreaterThan(Actual(e), _ ) => Some(True)
      case GreaterThan(_, Actual(e)) => Some(True)
      
      case GreaterEquals(Actual(e), _ ) => Some(True)
      case GreaterEquals(_, Actual(e)) => Some(True)
      
      case _ => None
    }(e)
  }

  def belongsToActual(e: Expr): Boolean = {
    var contains = false
    preTraversal {
      case Actual(_) | Noise(_,_) | RelError(_,_) | Roundoff(_) => contains = true
      case UMinusF(_) | PlusF(_,_) | MinusF(_,_) | TimesF(_,_) | DivisionF(_,_) | SqrtF(_) =>
        contains = true
      case EqualsF(_,_) => contains = true
      case _ => ;
    }(e)
    contains
  }

  // TODO: duplicate?
  def isRangeClause(e: Expr): Boolean = e match {
    case LessThan(Variable(_), RealLiteral(_)) | LessThan(RealLiteral(_), Variable(_)) => true
    case LessEquals(Variable(_), RealLiteral(_)) | LessEquals(RealLiteral(_), Variable(_)) => true
    case GreaterThan(Variable(_), RealLiteral(_)) | GreaterThan(RealLiteral(_), Variable(_)) => true
    case GreaterEquals(Variable(_), RealLiteral(_)) | GreaterEquals(RealLiteral(_), Variable(_)) => true
    case _ => false
  }

  def filterOutIdeal(expr: Expr): Expr = expr match {
    case And(args) =>
      And(args.map(a => filterOutIdeal(a)))

    case LessThan(l, r) if (!belongsToActual(l) && !belongsToActual(r)) => True
    case LessEquals(l, r) if (!belongsToActual(l) && !belongsToActual(r)) => True
    case GreaterThan(l, r) if (!belongsToActual(l) && !belongsToActual(r)) => True
    case GreaterEquals(l, r) if (!belongsToActual(l) && !belongsToActual(r)) => True
    case _ => expr
  }

  def filterOutActual(expr: Expr): Expr = expr match {
      case And(args) =>
        And(args.map(a => filterOutActual(a)))

      case LessThan(l, r) if (belongsToActual(l) || belongsToActual(r)) => True
      case LessEquals(l, r) if (belongsToActual(l) || belongsToActual(r)) => True
      case GreaterThan(l, r) if (belongsToActual(l) || belongsToActual(r)) => True
      case GreaterEquals(l, r) if (belongsToActual(l) || belongsToActual(r)) => True
      case _ => expr
    }

  def filterOutActualInFncVal(e: Expr): Expr = {
    preMap {
      case FncValue(s, sexpr, m, f, a) => Some(FncValue(s, filterOutActual(sexpr), m, f, a))
      case _ => None
    }(e)
  }

  def idealToActual(expr: Expr, vars: VariablePool): Expr = {
    val transformer = new RealToFloatTransformer(vars)
    transformer.transform(expr)
  }

  private class RealToFloatTransformer(variables: VariablePool) extends TransformerWithPC {
    type C = Seq[Expr]
    val initC = Nil

    def register(e: Expr, path: C) = path :+ e

    // (Sound) Overapproximation in the case of strict inequalities
    override def rec(e: Expr, path: C) = e match {
      case UMinusR(t) => UMinusF(rec(t, path))
      case PlusR(lhs, rhs) => PlusF(rec(lhs, path), rec(rhs, path))
      case MinusR(lhs, rhs) => MinusF(rec(lhs, path), rec(rhs, path))
      case TimesR(lhs, rhs) => TimesF(rec(lhs, path), rec(rhs, path))
      case DivisionR(lhs, rhs) => DivisionF(rec(lhs, path), rec(rhs, path))
      case SqrtR(t) => SqrtF(rec(t, path))
      case v: Variable => variables.buddy(v)
      //case ResultVariable() => FResVariable()
      case RealLiteral(r) => new FloatLiteral(r)
      case IfExpr(cond, thenn, elze) => FloatIfExpr(rec(cond, path), rec(thenn, path), rec(elze, path))
      case Equals(l, r) => EqualsF(rec(l, path), rec(r, path))
      // leave conditions on if-then-else in reals, as they will be passed as conditions to Z3
      case LessEquals(_,_) | LessThan(_,_) | GreaterEquals(_,_) | GreaterThan(_,_) => e

      case FncValue(s, sexpr, _, f, a) => FncValueF(s, filterOutIdeal(sexpr), f, a)

      case FncBody(n, b, f, a) => FncBodyF(n, rec(b, path), f, a)
      case FunctionInvocation(fundef, args) =>
        FncInvocationF(fundef, args.map(a => rec(a, path)))

      case Times(_, _) | Plus(_, _) | Division(_, _) | Minus(_, _) | UMinus(_) =>
        throw new Exception("found integer arithmetic in RealToFloatTransformer")
        null
      case _ =>
        super.rec(e, path)
    }
  }

  /* --------------------
        Arithmetic ops
   ---------------------- */
  
  def massageArithmetic(expr: Expr): Expr = {
    //TODO: remove redundant definitions of errors? stuff like And(Or(idealPart), Or(actualPart))
    val t1 = distributeMinus(expr)
    //println("t1: " + t1.getClass)
    val t2 = factorize(factorize(t1))
    //println("t2: " + t2)
    val t3 = collectProducts(t2)
    //println("t3: " + t3)
    val t4 = transformPowers(t3)
    //println("\nt4: " + t4)
    val res = simplifyArithmetic(t4)
    //println("res: " + res)
    res
  }


  def collectPowers(expr: Expr): Expr = {
    val t2 = collectProducts(expr)
    val t3 = transformPowers(t2)
    t3
  }

  def collectProducts(e: Expr): Expr = {
    postMap{
      case TimesR(l, r) => Some(Product(l, r))
      case _ => None
    }(e)
  }

  def transformPowers(e: Expr): Expr = {
    preMap {
      case Product(exprs) =>
        val groups: Map[String, Seq[Expr]] = exprs.groupBy[String]( expr => expr.toString )
        val groupsRec = groups.map( x =>
          if (x._2.size == 1) {
            x._2.head
          } else {
            PowerR(x._2.head, IntLiteral(x._2.size))
          }
        )

        Some(groupsRec.tail.foldLeft[Expr](groupsRec.head)((x, y) => TimesR(x, y)))

      case _ => None
    }(e)
  }

  def factorize(e: Expr): Expr = {
    preMap {
      case TimesR(f, PlusR(a, b)) => Some(PlusR(TimesR(f, a), TimesR(f, b)))
      case TimesR(PlusR(a, b), f) => Some(PlusR(TimesR(a, f), TimesR(b, f)))
      case TimesR(f, MinusR(a, b)) => Some(MinusR(TimesR(f, a), TimesR(f, b)))
      case TimesR(MinusR(a, b), f) => Some(MinusR(TimesR(a, f), TimesR(b, f)))
      case Times(_, _) | Plus(_, _) | Division(_, _) | Minus(_, _) | UMinus(_) =>
        throw new Exception("found integer arithmetic in Factorizer")
        null
      case _ => None
    }(e)
  }

  def distributeMinus(e: Expr): Expr = {
    preMap {
      case UMinusR(PlusR(x, y)) => Some(MinusR(UMinusR(x), y))
      case UMinusR(MinusR(x, y)) => Some(PlusR(UMinusR(x), y))
      case UMinusR(TimesR(x, y)) => Some(TimesR(UMinusR(x), y))
      case UMinusR(DivisionR(x, y)) => Some(DivisionR(UMinusR(x), y))
      case UMinusR(UMinusR(x)) => Some(x)
      case Times(_, _) | Plus(_, _) | Division(_, _) | Minus(_, _) | UMinus(_) =>
        throw new Exception("found integer arithmetic in MinusDistributor " + e)
        null
      case _ => None
    }(e)
  }

   // Copied from purescala.TreeOps, added RealLiteral
  def simplifyArithmetic(expr: Expr): Expr = {
    def simplify0(expr: Expr): Expr = expr match {
      case PlusR(RealLiteral(i1), RealLiteral(i2)) => RealLiteral(i1 + i2)
      case PlusR(RealLiteral(z), e) if (z == Rational.zero) => e
      case PlusR(e, RealLiteral(z)) if (z == Rational.zero) => e
      case PlusR(PlusR(e, RealLiteral(i1)), RealLiteral(i2)) => PlusR(e, RealLiteral(i1+i2))
      case PlusR(PlusR(RealLiteral(i1), e), RealLiteral(i2)) => PlusR(RealLiteral(i1+i2), e)

      case MinusR(e, RealLiteral(z)) if (z == Rational.zero) => e
      case MinusR(RealLiteral(z), e) if (z == Rational.zero) => UMinusR(e)
      case MinusR(RealLiteral(i1), RealLiteral(i2)) => RealLiteral(i1 - i2)
      case UMinusR(RealLiteral(x)) => RealLiteral(-x)

      case MinusR(e1, UMinusR(e2)) => PlusR(e1, e2)
      case MinusR(e1, MinusR(UMinusR(e2), e3)) => PlusR(e1, PlusR(e2, e3))
      case UMinusR(UMinusR(x)) => x
      case UMinusR(PlusR(UMinusR(e1), e2)) => PlusR(e1, UMinusR(e2))
      case UMinusR(MinusR(e1, e2)) => MinusR(e2, e1)

      case TimesR(RealLiteral(i1), RealLiteral(i2)) => RealLiteral(i1 * i2)
      case TimesR(RealLiteral(o), e) if (o == Rational.one) => e
      case TimesR(RealLiteral(no), e) if (no == -Rational.one) => UMinusR(e)
      case TimesR(e, RealLiteral(o)) if (o == Rational.one) => e
      case TimesR(RealLiteral(z), _) if (z == Rational.zero) => RealLiteral(Rational.zero)
      case TimesR(_, RealLiteral(z)) if (z == Rational.zero) => RealLiteral(Rational.zero)
      case TimesR(RealLiteral(i1), TimesR(RealLiteral(i2), t)) => TimesR(RealLiteral(i1*i2), t)
      case TimesR(RealLiteral(i1), TimesR(t, RealLiteral(i2))) => TimesR(RealLiteral(i1*i2), t)
      case TimesR(RealLiteral(i), UMinusR(e)) => TimesR(RealLiteral(-i), e)
      case TimesR(UMinusR(e), RealLiteral(i)) => TimesR(e, RealLiteral(-i))

      case DivisionR(RealLiteral(i1), RealLiteral(i2)) if i2 != 0 => RealLiteral(i1 / i2)
      case DivisionR(e, RealLiteral(o)) if (o == Rational.one) => e

      case PowerR(RealLiteral(o), e) if (o == Rational.one) => RealLiteral(Rational.one)

      //here we put more expensive rules
      //btw, I know those are not the most general rules, but they lead to good optimizations :)
      case PlusR(UMinusR(PlusR(e1, e2)), e3) if e1 == e3 => UMinusR(e2)
      case PlusR(UMinusR(PlusR(e1, e2)), e3) if e2 == e3 => UMinusR(e1)
      case MinusR(e1, e2) if e1 == e2 => RealLiteral(Rational.zero)
      case MinusR(PlusR(e1, e2), PlusR(e3, e4)) if e1 == e4 && e2 == e3 => RealLiteral(Rational.zero)
      case MinusR(PlusR(e1, e2), PlusR(PlusR(e3, e4), e5)) if e1 == e4 && e2 == e3 => UMinusR(e5)
      case Times(_, _) | Plus(_, _) | Division(_, _) | Minus(_, _) | UMinus(_) =>
        throw new Exception("found integer arithmetic in simplifyArithmetic")
        null
      //default
      case e => e
    }
    def fix[A](f: (A) => A)(a: A): A = {
      val na = f(a)
      if(a == na) a else fix(f)(na)
    }


    val res = fix(simplePostTransform(simplify0))(expr)
    res
  } // end simplify arithmetic

  def actualToIdealVars(e: Expr, variables: VariablePool) = {
    preMap {
      case v @ Variable(_) =>
        variables.getIdealOrNone(v) match {
          case Some(ideal) => Some(ideal)
          case None => Some(v)
        }
      case _ => None
    }(e)
  }

  // Accepts SSA format only and transforms actual to ideal
  def translateToFP(expr: Expr, formats: Map[Expr, FPFormat], bitlength: Int, getConstant: (Rational, Int) => Expr): Expr = expr match {
    case And(exprs) =>
      And(exprs.map(e => translateToFP(e, formats, bitlength, getConstant)))

    case FloatIfExpr(c, t, e) =>
      IfExpr(translateToFP(c, formats, bitlength, getConstant), translateToFP(t, formats, bitlength, getConstant),
                  translateToFP(e, formats, bitlength, getConstant))

    case GreaterEquals(_, _) | GreaterThan(_, _) | LessEquals(_, _) | LessThan(_, _) => expr

    case FncBodyF(name, body, funDef, args) =>
      FunctionInvocation(TypedFunDef(funDef, Seq.empty), args)

    case FncValueF(_, _, funDef, args) =>
      FunctionInvocation(TypedFunDef(funDef, Seq.empty), args)    

    case EqualsF(vr, PlusF(lhs, rhs)) =>
      val resultFormat = formats(vr)
      val mx = resultFormat.f
      val (ll, rr, mr) = alignOperators(lhs, rhs, formats, bitlength, getConstant)
      val assignment =
        if (mx == mr) Plus(ll, rr)
        else if (mx <= mr) RightShift(Plus(ll, rr), (mr - mx))
        else LeftShift(Plus(ll, rr), (mx - mr))  // Fixme: really?
      Equals(vr, assignment)

    case EqualsF(vr, MinusF(lhs, rhs)) =>
      val resultFormat = formats(vr)
      val mx = resultFormat.f
      val (ll, rr, mr) = alignOperators(lhs, rhs, formats, bitlength, getConstant)
      val assignment =
        if (mx == mr) Minus(ll, rr)
        else if (mx <= mr) RightShift(Minus(ll, rr), (mr - mx))
        else LeftShift(Minus(ll, rr), (mx - mr))  // Fixme: really?
      Equals(vr, assignment)

    case EqualsF(vr, TimesF(lhs, rhs)) =>
      val resultFormat = formats(vr)
      val mx = resultFormat.f
      val (mult, mr) = multiplyOperators(lhs, rhs, formats, bitlength, getConstant)
      val assignment =
        if (mx == mr) mult
        else if (mr - mx >= 0) RightShift(mult, (mr - mx))
        else LeftShift(mult, mx - mr)
      Equals(vr, assignment)

    case EqualsF(vr, DivisionF(lhs, rhs)) =>
      val resultFormat = formats(vr)
      val mx = resultFormat.f
      Equals(vr, divideOperators(lhs, rhs, mx, formats, bitlength, getConstant))

    case EqualsF(vr, rhs) => Equals(vr, translateToFP(rhs, formats, bitlength, getConstant))

    case v @ Variable(id) => v
    case FloatLiteral(r) =>
      val bits = FPFormat.getFormat(r, bitlength).f
      getConstant(r, bits)
    case UMinusF( t ) => UMinus(translateToFP(t, formats, bitlength, getConstant))
  }


  private def alignOperators(x: Expr, y: Expr, formats: Map[Expr, FPFormat], bitlength: Int,
    getConstant: (Rational, Int) => Expr): (Expr, Expr, Int) = (x, y) match {
    case (v1 @ Variable(_), v2 @ Variable(_)) =>
      val my = formats(v1).f
      val mz = formats(v2).f

      if (mz == my) (v1, v2, my)
      else if (my <= mz) (LeftShift(v1, (mz - my)), v2, mz)
      else (v1, LeftShift(v2, (my - mz)), my)

    case (v @ Variable(_), FloatLiteral(r)) =>
      val my = formats(v).f
      val mz = FPFormat.getFormat(r, bitlength).f

      val const = getConstant(r, mz)
      if (my == mz) (v, const, mz)
      else if (my <= mz) (LeftShift(v, (mz - my)), const, mz)
      else (v, LeftShift(const, (my - mz)), my)

    case (FloatLiteral(r), v @ Variable(_)) =>
      val mz = formats(v).f
      val my = FPFormat.getFormat(r, bitlength).f
      val const = getConstant(r, my)
      if (my == mz) (const, v, mz)
      else if (my <= mz) (LeftShift(const, (mz - my)), v, mz)
      else (const, LeftShift(v, (my - mz)), my)

    case (FloatLiteral(r1), FloatLiteral(r2)) =>
      val my = FPFormat.getFormat(r1, bitlength).f
      val mz = FPFormat.getFormat(r2, bitlength).f
      val i1 = getConstant(r1, my)
      val i2 = getConstant(r2, mz)
      if (my == mz) (i1, i2, mz)
      else if (my <= mz) (LeftShift(i1, (mz - my)), i2, mz)
      else (i1, LeftShift(i2, (my - mz)), my)
  }

  def multiplyOperators(x: Expr, y: Expr, formats: Map[Expr, FixedPointFormat], bitlength: Int,
    getConstant: (Rational, Int) => Expr): (Times, Int) = (x, y) match {
    case (v1 @ Variable(_), v2 @ Variable(_)) =>
      val my = formats(v1).f
      val mz = formats(v2).f
      (Times(v1, v2), my + mz)

    case (v @ Variable(_), FloatLiteral(r)) =>
      val my = formats(v).f
      val mz = FPFormat.getFormat(r, bitlength).f
      val i = getConstant(r, mz)
      (Times(v, i), my + mz)

    case (FloatLiteral(r), v @ Variable(_)) =>
      val my = FPFormat.getFormat(r, bitlength).f
      val i = getConstant(r, my)
      val mz = formats(v).f
      (Times(i, v), my + mz)

    case (FloatLiteral(r1), FloatLiteral(r2)) =>
      val my = FPFormat.getFormat(r1, bitlength).f
      val i1 = getConstant(r1, my)
      val mz = FPFormat.getFormat(r2, bitlength).f
      val i2 = getConstant(r2, mz)
      (Times(i1, i2), my + mz)
   }

  def divideOperators(x: Expr, y: Expr, mx: Int, formats: Map[Expr, FixedPointFormat], bitlength: Int,
    getConstant: (Rational, Int) => Expr): Division = (x, y) match {
    case (v1 @ Variable(_), v2 @ Variable(_)) =>
      val my = formats(v1).f
      val mz = formats(v2).f
      val shift = mx + mz - my
      Division(LeftShift(v1, shift), v2)

    case (v @ Variable(_), FloatLiteral(r)) =>
      val my = formats(v).f
      val mz = FPFormat.getFormat(r, bitlength).f
      val i = getConstant(r, mz)
      val shift = mx + mz - my
      Division(LeftShift(v, shift), i)

    case (FloatLiteral(r), v @ Variable(_)) =>
      val my = FPFormat.getFormat(r, bitlength).f
      val i = getConstant(r, my)
      val mz = formats(v).f
      val shift = mx + mz - my
      Division(LeftShift(i, shift), v)

    case (FloatLiteral(r1), FloatLiteral(r2)) =>
      val my = FPFormat.getFormat(r1, bitlength).f
      val i1 = getConstant(r1, my)
      val mz = FPFormat.getFormat(r2, bitlength).f
      val i2 = getConstant(r2, mz)
      val shift = mx + mz - my
      Division(LeftShift(i1, shift), i2)
    }


    /*def rationalToLong(r: Rational, f: Int): Long = {
      (r * Rational(math.pow(2, f))).roundToInt.toLong
    }

    def rationalToInt(r: Rational, f: Int): Int = {
      (r * Rational(math.pow(2, f))).roundToInt
    }*/


  /*
  // Convenience for readability of printouts
  class DeltaRemover extends TransformerWithPC {
    type C = Seq[Expr]
    val initC = Nil

    def register(e: Expr, path: C) = path :+ e

    override def rec(e: Expr, path: C) = e match {
      case LessEquals(Variable(id1), Variable(id2)) if (id1.toString.contains("#delta_") && id2.toString == "#eps") =>
        True
      case LessEquals(UMinus(Variable(id1)), Variable(id2)) if (id1.toString == "#eps" && id2.toString.contains("#delta_")) =>
        True
      case _ =>
        super.rec(e, path)
    }
  }

  class BoundsConverter(eps2: Variable, offset: Variable) extends TransformerWithPC {
    type C = Seq[Expr]
    val initC = Nil

    def register(e: Expr, path: C) = path :+ e

    override def rec(e: Expr, path: C) = e match {
      case LessEquals(UMinus(eps), delta) if (eps.toString == "#eps") => LessThan(UMinus(eps2), delta)
      case LessEquals(delta, eps) if (eps.toString == "#eps") => LessThan(delta, eps2)
      case Equals(eps, machineEps) if (eps.toString == "#eps") => Equals(eps2, machineEps)

      case LessEquals(r @ RationalLiteral(v), x) => LessThan(Minus(r, offset), x)
      case GreaterEquals(x, r @ RationalLiteral(v)) => GreaterThan(x, Minus(r, offset))
      case LessEquals(x, y) => LessThan(x, Plus(y, offset))
      case GreaterEquals(x, y) => GreaterThan(Plus(x, offset), y)
      case _ =>
        super.rec(e, path)
    }
  }*/
}
