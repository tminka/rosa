package leon
package numerics

import ceres.common.{Rational, RationalInterval}

import purescala.Common._
import purescala.Definitions._
import purescala.Trees._
import purescala.TreeOps._
import purescala.TypeTrees._

import affine.{XFloat, XFloatConfig}
import affine.XFloat._

import Utils._

import Sat._
import Valid._
import ApproximationType._
import Precision._
import SpecGenType._


class Prover(reporter: Reporter, ctx: LeonContext, program: Program, vcMap: Map[FunDef, VerificationCondition],
  precision: Precision, specGenType: SpecGenType) {
  val verbose = false
  val solver = new NumericSolver(ctx, program)
  val postInliner = new PostconditionInliner(reporter)
  val fullInliner = new FullInliner(reporter, vcMap)

  val unitRoundoff = getUnitRoundoff(precision)
  val unitRoundoffDefault = getUnitRoundoff(Float64)

  def check(vc: VerificationCondition) = {
    reporter.info("")
    reporter.info("----------> checking VC of " + vc.funDef.id.name)

    val start = System.currentTimeMillis
    for (c <- vc.allConstraints) {
      reporter.info("----------> checking constraint: " + c.description)
      if (verbose) {println("pre: " + c.pre); println("body: " + c.body); println("post: " + c.post)}

      while (c.hasNextApproximation && !c.solved) {
        val next = c.getNextApproxType.get
        reporter.info("Computing approximation: " + next)
        val approx = getNextApproximation(next, c, vc.inputs)
        c.approximations = Seq(approx) ++ c.approximations
        c.overrideStatus(checkWithZ3(approx, vc.allVariables))
        reporter.info("RESULT: " + c.status)
      }
    }

    val mainCnstr = if(vc.allConstraints.size > 0) vc.allConstraints.head
      else Constraint(vc.precondition.get, vc.body.get, BooleanLiteral(true), "wholebody")
    vc.generatedPost = Some(getPost(mainCnstr, vc.inputs))
    reporter.info("Generated post: " + vc.generatedPost)

    val totalTime = (System.currentTimeMillis - start)
    vc.verificationTime = Some(totalTime)
  }



  /* *************************
        Verification
  **************************** */
  private def checkWithZ3(ca: ConstraintApproximation, parameters: Seq[Variable]): (Option[Valid], Option[Map[Identifier, Expr]]) = {
    val (resVar, eps, buddies) = getVariables(parameters ++ ca.vars)
    val trans = new NumericConstraintTransformer(buddies, resVar, eps, RoundoffType.RoundoffMultiplier, reporter)
    val precondition = trans.transformCondition(ca.pre)
    val postcondition = trans.transformCondition(ca.post)

    var (idealPart, actualPart) = (Seq[Expr](), Seq[Expr]())
    for(path <- ca.paths) {
      val (aI, nI) = trans.transformBlock(path.idealBody)
      idealPart = idealPart :+ And(And(path.pathCondition, trans.transformCondition(path.idealCnst)), aI)
      val (aN, nN) = trans.transformBlock(path.actualBody)
      actualPart = actualPart :+ And(And(trans.getNoisyCondition(path.pathCondition), trans.transformCondition(path.actualCnst)), nN)
    }

    val resultError = Equals(getNewResErrorVariable, Minus(resVar, buddies(resVar))) // let z3 give us error explicitly
    val machineEpsilon = Equals(eps, RationalLiteral(unitRoundoff))
    val body = And(And(Or(idealPart), Or(actualPart)), And(resultError, machineEpsilon))


    //val toCheck = Implies(And(precondition, body), postcondition)
    val toCheck = And(And(precondition, body), Not(postcondition)) //has to be unsat
    println("toCheck: " + toCheck)

    // At this point the sanity check has to pass, i.e. all infeasible paths have been ruled out.
    val firstTry = if (reporter.errorCount == 0 && sanityCheck(precondition, false, body))
      solver.checkSat(toCheck)
    else
      (None, None)

    println("first try: " + firstTry._1)

    firstTry match {
      case (UNSAT, _) => (Some(VALID), None)
      case _ => // try again for each part separately
        if (ca.paths.size > 1) {
          val paths = idealPart.zip(actualPart)
          for ((i, a) <- paths) {
            println("checking path: " + And(i, a))
            val (sat, model) = solver.checkSat(And(Seq(precondition, i, a, resultError, machineEpsilon, Not(postcondition))))
            println("with result: " + sat)
            // TODO: print the models that are actually useful, once we figure out which ones those are
            if (sat != UNSAT) {
              reporter.info("path could not be proven: " + And(i, a))
              return (Some(NOT_SURE), None)
            }
          }
        } else {
          return (Some(NOT_SURE), None)
        }
    }
    (Some(VALID), None)
  }

  // if true, we're sane
  private def sanityCheck(pre: Expr, silent: Boolean = true, body: Expr = BooleanLiteral(true)): Boolean = {
    val sanityCondition = And(pre, body)
    solver.checkSat(sanityCondition) match {
      case (SAT, model) =>
        //reporter.info("Sanity check passed! :-)")
        //reporter.info("model: " + model)
        true
      case (UNSAT, model) =>
        if (!silent) reporter.warning("Not sane! " + sanityCondition)
        false
      case _ =>
        reporter.warning("Sanity check failed! ")// + sanityCondition)
        false
    }
  }

  /*private def checkWithVariablePrecision(ca: ConstraintApproximation, parameters: Seq[Variable]): (Option[Valid], Option[Map[Identifier, Expr]]) = {
    val (resVar, eps, buddies) = getVariables(parameters ++ ca.vars)
    val trans = new NumericConstraintTransformer(buddies, resVar, eps, RoundoffType.RoundoffMultiplier, reporter)
    val precondition = trans.transformCondition(ca.pre)
    val postcondition = trans.transformCondition(ca.post)

    var (idealPart, actualPart) = (Seq[Expr](), Seq[Expr]())
    for(path <- ca.paths) {
      val (aI, nI) = trans.transformBlock(path.idealBody)
      idealPart = idealPart :+ And(And(path.pathCondition, trans.transformCondition(path.idealCnst)), aI)
      val (aN, nN) = trans.transformBlock(path.actualBody)
      actualPart = actualPart :+ And(And(trans.getNoisyCondition(path.pathCondition), trans.transformCondition(path.actualCnst)), nN)
    }

    val body = And(Or(idealPart), Or(actualPart))

    val resultError = Equals(getNewResErrorVariable, Minus(resVar, buddies(resVar))) // let z3 give us error explicitly
    val machineEpsilonWanted = Equals(eps, RationalLiteral(unitRoundoff))
    val machineEpsilonDefault = Equals(eps, RationalLiteral(unitRoundoffDefault))

    val toCheck = And(And(precondition, resultError), Not(postcondition))
    //val toCheck = Implies(And(precondition, And(body, And(resultError, machineEpsilon))), postcondition)
    println("toCheck: " + toCheck)

    val firstTry = if (reporter.errorCount == 0 && sanityCheck(precondition, body)) {
      solver.push
      solver.assertCnstr(toCheck)
      val (res, model) = solver.checkSat(machineEpsilonWanted)

      solver.pop
      println("first try: " + res)
      (Some(res), model)
    } else {
      (None, None)
    }

    // So at this point, all paths should be feasible
    firstTry match {
      case (Some(VALID), _) => firstTry
      case _ => // try again
        val paths = idealPart.zip(actualPart)
        for ((i, a) <- paths) {
          val cnstr = Implies(And(precondition, And(And(i, a), And(resultError, machineEpsilon))), postcondition)
          println("checking path: " + And(i, a))
          val (res, model) = solver.checkValid(cnstr)
          println("with result: " + res)
          if (res != VALID) {
            reporter.info("path could not be proven: " + And(i, a))
            return (Some(res), model)
          }
        }
    }
    (Some(VALID), None)
  }*/

  /* *************************
        Approximations
  **************************** */
  val allTrueAPath = APath(True, True, True, True, True)

    // TODO: we can cache some of the body transforms and reuse for AA...
  def getNextApproximation(tpe: ApproximationType, c: Constraint, inputs: Map[Variable, Record]): ConstraintApproximation = tpe match {
    /* ******************
       NO APPROXIMATION
    * ******************* */
    case Uninterpreted_None =>
      val paths = collectPaths(c.body).map(p => getAPath(p))
      ConstraintApproximation(c.pre, paths, c.post, Set.empty, tpe)

    case PostInlining_None =>
      val (newPre, newBody, newPost, vars) = postInliner.inlinePostcondition(c.pre, c.body, c.post)
      val paths = collectPaths(newBody).map(p => getAPath(p))
      ConstraintApproximation(newPre, paths, newPost, vars, tpe)

    case FullInlining_None =>
      val (newPre, newBody, newPost, vars) = fullInliner.inlineFunctions(c.pre, c.body, c.post)
      val paths = collectPaths(newBody).map(p => getAPath(p))
      ConstraintApproximation(newPre, paths, newPost, vars, tpe)

    /* ******************
       Full APPROXIMATION
    * ******************* */
    case NoFncs_AA =>
      val (newConstraint, apaths, values) = computeApproxForRes(c.paths, c.pre, inputs)
      ConstraintApproximation(And(c.pre, newConstraint), apaths, c.post, Set.empty, tpe, values)

    case NoFncs_AAPathSensitive =>
      val paths = c.paths
      if (!c.pathsApproximated) for (p <- paths) computeApproximation(p, c.pre, inputs)
      val apaths = paths.collect {
        case p: Path if (p.feasible) => getAPath(p).updateNoisy(True, constraintFromXFloats(p.values))
      }
      ConstraintApproximation(c.pre, apaths, c.post, Set.empty, tpe)

    case PostInlining_AA =>
      val (newPre, newBody, newPost, vars) = postInliner.inlinePostcondition(c.pre, c.body, c.post)
      val (newConstraint, apaths, values) = computeApproxForRes(collectPaths(newBody), newPre, getVariableRecords(newPre))
      ConstraintApproximation(And(newPre, newConstraint), apaths, newPost, vars, tpe, values)

    case PostInlining_AAPathSensitive =>
      val (newPre, newBody, newPost, vars) = postInliner.inlinePostcondition(c.pre, c.body, c.post)
      val paths = collectPaths(newBody)
      for (p <- paths) computeApproximation(p, newPre, inputs)
      val apaths = paths.collect {
        case p: Path if (p.feasible) => getAPath(p).updateNoisy(True, constraintFromXFloats(p.values))
      }
      ConstraintApproximation(newPre, apaths, newPost, vars, tpe)

    case FullInlining_AA =>
      val (newPre, newBody, newPost, vars) = postInliner.inlinePostcondition(c.pre, c.body, c.post)
      val (newConstraint, apaths, values) = computeApproxForRes(collectPaths(newBody), newPre, getVariableRecords(newPre))
      ConstraintApproximation(And(newPre, newConstraint), apaths, newPost, vars, tpe, values)

    case FullInlining_AAPathSensitive =>
      val (newPre, newBody, newPost, vars) = postInliner.inlinePostcondition(c.pre, c.body, c.post)
      val paths = collectPaths(newBody)
      for (p <- paths) computeApproximation(p, newPre, inputs)
      val apaths = paths.collect {
        case p: Path if (p.feasible) => getAPath(p).updateNoisy(True, constraintFromXFloats(p.values))
      }
      ConstraintApproximation(newPre, apaths, newPost, vars, tpe)

      // TODO: automatic approximation of functions called without postcondition
      // TODO: If neither work, do partial approx.
  }

  private def computeApproxForRes(paths: Set[Path], pre: Expr, inputs: Map[Variable, Record]):
    (Expr, Set[APath], Map[Expr, (RationalInterval, Rational)]) = {
    for (p <- paths) computeApproximation(p, pre, inputs)
    val (interval, error) = mergeRealPathResults(paths)(ResultVariable())
    val newConstraint = Noise(ResultVariable(), RationalLiteral(error))
    val apaths = paths.collect { case p: Path if (p.feasible) => getAPathRealOnly(p) }
    (newConstraint, apaths, Map(ResultVariable() -> (interval, error)))
  }


  // Computes one constraint that overapproximates the paths given.
  private def approximatePaths(paths: Set[Path], pre: Expr, inputs: Map[Variable, Record]): (Expr, Map[Expr, (RationalInterval, Rational)]) = {
    for (p <- paths) computeApproximation(p, pre, inputs)
    //println("approximation: " + paths.head.values)
    val approx = mergeRealPathResults(paths)
    //println("merged: " + approx)
    val newConstraint = constraintFromResults(approx)
    (newConstraint, approx)
  }


  private def computeApproximation(path: Path, precondition: Expr, inputs: Map[Variable, Record]) = {
    println("approximating path : " + path.condition)
    val pathCondition = And(path.condition, filterPreconditionForBoundsIteration(precondition))
    if (sanityCheck(pathCondition)) {
      // The condition given to the solver is the real(ideal)-valued one, since we use Z3 for the real part only.
      val config = XFloatConfig(reporter, solver, pathCondition, precision, unitRoundoff)
      val (variables, indices) = variables2xfloats(inputs, config)
      solver.countTimeouts = 0
      path.values = inXFloats(path.expression, variables, config) -- inputs.keys
      reporter.info("Timeouts: " + solver.countTimeouts)
      //println("path values: " + path.values)
      path.indices= indices

    } else {
      reporter.warning("skipping path " + path.condition)
      path.feasible = false
      // TODO: what to do here? we only checked the ideal part is impossible,
      // but the floating-point part may still be possible
      // although this should be an error
    }
  }


  private def getAPath(path: Path): APath =
    APath(path.condition, And(path.expression), True, And(path.expression), True, path.values)

  private def getAPathRealOnly(path: Path): APath =
    APath(path.condition, And(path.expression), True, True, True, path.values)


  // Returns a map from all variables to their final value, including local vars
  private def inXFloats(exprs: List[Expr], vars: Map[Expr, XFloat], config: XFloatConfig): Map[Expr, XFloat] = {
    var currentVars: Map[Expr, XFloat] = vars

    for (expr <- exprs) expr match {
      case Equals(variable, value) =>
        try {
          val computedValue = eval(value, currentVars, config)
          //println("computedValue: " + computedValue)
          currentVars = currentVars + (variable -> computedValue)
          //println("currentVars: " + currentVars)
        } catch {
          case UnsupportedFragmentException(msg) => reporter.error(msg)
        }

      case BooleanLiteral(true) => ;
      case _ =>
        reporter.error("AA cannot handle: " + expr)
    }

    currentVars
  }

  // Evaluates an arithmetic expression
  private def eval(expr: Expr, vars: Map[Expr, XFloat], config: XFloatConfig): XFloat = expr match {
    case v @ Variable(id) => vars(v)
    case RationalLiteral(v) => XFloat(v, config)
    case IntLiteral(v) => XFloat(v, config)
    case UMinus(rhs) => - eval(rhs, vars, config)
    case Plus(lhs, rhs) => eval(lhs, vars, config) + eval(rhs, vars, config)
    case Minus(lhs, rhs) => eval(lhs, vars, config) - eval(rhs, vars, config)
    case Times(lhs, rhs) => eval(lhs, vars, config) * eval(rhs, vars, config)
    case Division(lhs, rhs) => eval(lhs, vars, config) / eval(rhs, vars, config)
    case Sqrt(t) => eval(t, vars, config).squareRoot
    case _ =>
      throw UnsupportedFragmentException("AA cannot handle: " + expr)
      null
  }


  /* *************************
    Specification Generation.
  **************************** */
  private def getPost(c: Constraint, inputs: Map[Variable, Record]): Expr = (specGenType, c.hasFunctionCalls) match {
    case (Simple, false) =>
      val approx = findApproximation(c, inputs, List(NoFncs_AA))
      constraintFromResults(Map(ResultVariable() -> approx.values(ResultVariable())))
    case (Simple, true) =>
      val approx = findApproximation(c, inputs, List(PostInlining_AA, FullInlining_AA))
      constraintFromResults(Map(ResultVariable() -> approx.values(ResultVariable())))

    case (PathSensitive, false) =>
      val approx = findApproximation(c, inputs, List(NoFncs_AAPathSensitive))
      val newPost: Seq[Expr] = approx.paths.foldLeft(Seq[Expr]())(
            (s, p) => s :+ And(p.pathCondition, constraintFromXFloats(Map(ResultVariable() -> p.values(ResultVariable())))))
      Or(newPost)

    case (PathSensitive, true) =>
      val approx = findApproximation(c, inputs, List(PostInlining_AAPathSensitive, FullInlining_AAPathSensitive))
      val newPost: Seq[Expr] = approx.paths.foldLeft(Seq[Expr]())(
            (s, p) => s :+ And(p.pathCondition, constraintFromXFloats(Map(ResultVariable() -> p.values(ResultVariable())))))
      Or(newPost)

    // TODO: this may have to be different if we have to use the function for verification
    case (NoGen, _) => True
  }

  private def findApproximation(c: Constraint, inputs: Map[Variable, Record], tpes: List[ApproximationType]): ConstraintApproximation = {
    c.approximations.find(a => tpes.contains(a.tpe)) match {
      case Some(app) => app
      case None => getNextApproximation(tpes.head, c, inputs)
    }
  }

  /*def generateSpecMoreInfo(vc: VerificationCondition) = {
    reporter.info("")
    reporter.info("----------> generating postcondition for: " + vc.funDef.id.name)

    // TODO: what do we do with invariants?
    vc.specConstraint match {
      case Some(c) =>
        var args = Seq[Expr]()
        for (path <- c.paths) {
          // TODO: add error on computing the path condition
          val cond = path.condition
          val res = path.values(ResultVariable())

          res.interval

          val errorExpr = getErrorExpression(res.error, path.indices)
          args = args :+ And(Seq(cond, LessEquals(RationalLiteral(res.interval.xlo), ResultVariable()),
            LessEquals(ResultVariable(), RationalLiteral(res.interval.xhi)),
            Noise(ResultVariable(), errorExpr)))
        }


        val newConstraint = Or(args)
        reporter.info("computed spec: " + newConstraint)
        vc.generatedPost = Some(newConstraint)

      case None =>
        reporter.warning("Forgotten spec constraint?")
    }
  }

  def getErrorExpression(a: XRationalForm, indices: Map[Int, Expr]): Expr = {
    val indexSet: Set[Int] = indices.keys.toSet
    val (lin, rest) = a.noise.partition(d => indexSet(d.index))

    val maxError = affine.Utils.sumSeq(rest)
    val restError = RationalInterval(-maxError, maxError) + RationalInterval(a.x0, a.x0)

    val restErrorVar = getNewErrorVar
    var cnstr: Expr = restErrorVar

    for (dev <- lin) {
      // TODO: not quite right! it should be the error on variable, or rather whether it was there or not...
      cnstr = Plus(cnstr, Times(RationalLiteral(dev.value), getNamedError(indices(dev.index))))
    }
    And(ratint2expr(restError, restErrorVar), cnstr)
  }*/

  /* *************************
            Utils
  **************************** */
  private def getVariables(variables: Seq[Variable]): (Variable, Variable, Map[Expr, Expr]) = {
    val resVar = Variable(FreshIdentifier("#ress")).setType(RealType)
    val machineEps = Variable(FreshIdentifier("#eps")).setType(RealType)

    var buddies: Map[Expr, Expr] =
      variables.foldLeft(Map[Expr, Expr](resVar -> Variable(FreshIdentifier("#res_0")).setType(RealType)))(
        (map, nextVar) => map + (nextVar -> Variable(FreshIdentifier("#"+nextVar.id.name+"_0")).setType(RealType))
      )
    (resVar, machineEps, buddies)
  }


  private def filterPreconditionForBoundsIteration(expr: Expr): Expr = expr match {
    case And(args) => And(args.map(a => filterPreconditionForBoundsIteration(a)))
    case Noise(e, f) => BooleanLiteral(true)
    case Roundoff(e) => BooleanLiteral(true)
    case _ => expr
  }

  private def filterDeltas(expr: Expr): Expr = expr match {
    case And(args) => And(args.map(a => filterDeltas(a)))
    case LessEquals(Variable(id1), Variable(id2)) if (id1.toString.contains("#delta_") && id2.toString == "#eps") =>
      //println("filtering out: " + expr)
      True
    case LessEquals(UMinus(Variable(id1)), Variable(id2)) if (id1.toString == "#eps" && id2.toString.contains("#delta_")) =>
      //println("filtering out: " + expr)
      True

    case _ => expr
  }

}
