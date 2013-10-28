/* Copyright 2013 EPFL, Lausanne */

package leon
package real

import java.io.{PrintWriter, File}

import purescala.Definitions._
import purescala.Trees._
import purescala.TreeOps._
import purescala.TypeTrees._
import purescala.Common._
import purescala.ScalaPrinter

import xlang.Trees._

import real.Trees._
import real.TreeOps._

import Precision._
import VCKind._


object CompilationPhase extends LeonPhase[Program,CompilationReport] {
  val name = "Real compilation"
  val description = "compilation of real programs"

  var verbose = false
  var reporter: Reporter = null

  override val definedOptions: Set[LeonOptionDef] = Set(
    LeonValueOptionDef("functions", "--functions=f1:f2", "Limit verification to f1, f2,..."),
    LeonFlagOptionDef("simulation", "--simulation", "Run a simulation instead of verification"),
    LeonFlagOptionDef("z3Only", "--z3Only", "Let Z3 loose on the full constraint - at your own risk."),
    LeonValueOptionDef("z3Timeout", "--z3Timeout=1000", "Timeout for Z3 in milliseconds."),
    LeonValueOptionDef("precision", "--precision=single", "Which precision to assume of the underlying"+
      "floating-point arithmetic: single, double, doubledouble, quaddouble or all (finds the best one)."),
    LeonFlagOptionDef("pathError", "--pathError", "Check also the path error (default is to not check)"),
    LeonFlagOptionDef("specGen", "--specGen", "Generate specs also for functions without postconditions")
  )

  // TODO check code generation
  def run(ctx: LeonContext)(program: Program): CompilationReport = { 
    reporter = ctx.reporter
    reporter.info("Running Compilation phase")

    var fncNamesToAnalyse = Set[String]()
    var options = RealOptions()

    for (opt <- ctx.options) opt match {
      case LeonValueOption("functions", ListValue(fs)) => fncNamesToAnalyse = Set() ++ fs
      case LeonFlagOption("simulation") => options = options.copy(simulation = true)
      case LeonFlagOption("z3Only") => options = options.copy(z3Only = true)
      case LeonFlagOption("pathError") => options = options.copy(pathError = true)
      case LeonFlagOption("specGen") => options = options.copy(specGen = true)
      case LeonValueOption("z3Timeout", ListValue(tm)) => options = options.copy(z3Timeout = tm.head.toLong)
      case LeonValueOption("precision", ListValue(ps)) => options = options.copy(precision = ps.head match {
        case "single" => List(Float32)
        case "double" => List(Float64)
        case "doubledouble" => List(DoubleDouble)
        case "quaddouble" => List(QuadDouble)
        case "all" => List(Float32, Float64, DoubleDouble, QuadDouble)
      })
      case _ =>
    }
    
    val fncsToAnalyse  = 
      if(fncNamesToAnalyse.isEmpty) program.definedFunctions
      else {
        val toAnalyze = program.definedFunctions.filter(f => fncNamesToAnalyse.contains(f.id.name))
        val notFound = fncNamesToAnalyse -- toAnalyze.map(fncDef => fncDef.id.name).toSet
        notFound.foreach(fn => reporter.error("Did not find function \"" + fn + "\" though it was marked for analysis."))
        toAnalyze
      }
        
    val (vcs, fncs) = analyzeThis(fncsToAnalyse)
    if (reporter.errorCount > 0) throw LeonFatalError()
    
    reporter.info("--- Analysis complete ---")
    reporter.info("")
    if (options.simulation) {
      val simulator = new Simulator(reporter)
      val prec = if (options.precision.size == 1) options.precision.head else Float64
      for(vc <- vcs) simulator.simulateThis(vc, prec)
      new CompilationReport(List(), prec)
    } else {
      val prover = new Prover(ctx, options, program, fncs, verbose)
      val finalPrecision = prover.check(vcs)

      val newProgram = specToCode(program.id, program.mainObject.id, vcs, finalPrecision) 
      val newProgramAsString = ScalaPrinter(newProgram)
      reporter.info("Generated program with %d lines.".format(newProgramAsString.lines.length))
      //reporter.info(newProgramAsString)

      val writer = new PrintWriter(new File("generated/" + newProgram.mainObject.id +".scala"))
      writer.write(newProgramAsString)
      writer.close()
    
      new CompilationReport(vcs.sortWith((vc1, vc2) => vc1.fncId < vc2.fncId), finalPrecision)
    }
    
  }

  

  private def analyzeThis(sortedFncs: Seq[FunDef]): (Seq[VerificationCondition], Map[FunDef, Fnc]) = {
    var vcs = Seq[VerificationCondition]()
    var fncs = Map[FunDef, Fnc]()
    
    for (funDef <- sortedFncs if (funDef.body.isDefined)) {
      reporter.info("Analysing fnc:  %s".format(funDef.id.name))
      if (verbose) reporter.debug("fnc body: " + funDef.body.get)
      
      funDef.precondition match {
        case Some(pre) =>
          val variables = VariablePool(pre)
          if (verbose) reporter.debug("parameter: " + variables)
          if (variables.hasValidInput(funDef.args)) {
            if (verbose) reporter.debug("precondition is acceptable")
            val allFncCalls = functionCallsOf(funDef.body.get).map(invc => invc.funDef.id.toString)
              //functionCallsOf(pre).map(invc => invc.funDef.id.toString) ++

            // Add default roundoff on inputs
            val precondition = And(pre, And(variables.inputsWithoutNoise.map(i => Roundoff(i))))
            if (verbose) reporter.debug("precondition: " + precondition)
            
            val (body, bodyWORes, postcondition) = funDef.postcondition match {
              case Some(ResultVariable()) =>
                val posts = getInvariantCondition(funDef.body.get)
                val bodyWOLets = convertLetsToEquals(funDef.body.get)
                val body = replace(posts.map(p => (p, True)).toMap, bodyWOLets)
                (body, body, Or(posts))
              case Some(p) =>
                (convertLetsToEquals(addResult(funDef.body.get)), convertLetsToEquals(funDef.body.get), p)

              case None => // only want to generate specs
                (convertLetsToEquals(addResult(funDef.body.get)), convertLetsToEquals(funDef.body.get), True)
            }
            if (postcondition == True)
              vcs :+= new VerificationCondition(funDef, SpecGen, precondition, body, postcondition, allFncCalls, variables)
            else  
              vcs :+= new VerificationCondition(funDef, Postcondition, precondition, body, postcondition, allFncCalls, variables)

            // VCs for preconditions of fnc calls and assertions
            val assertionCollector = new AssertionCollector(funDef, precondition, variables)
            assertionCollector.transform(body)
            vcs ++= assertionCollector.vcs

            // for function inlining
            fncs += (funDef -> Fnc(precondition, bodyWORes, postcondition))
          } else {
            reporter.warning("Incomplete precondition! Skipping...")
          }
        case None =>
      }
    }
    (vcs.sortWith((vc1, vc2) => lt(vc1, vc2)), fncs)
  }

  private def lt(vc1: VerificationCondition, vc2: VerificationCondition): Boolean = {
    if (vc1.allFncCalls.isEmpty) true
    else if (vc2.allFncCalls.isEmpty) false
    else if (vc2.allFncCalls.contains(vc1.fncId)) true
    else if (vc1.allFncCalls.contains(vc2.fncId)) false
    else true
  }


  private def specToCode(programId: Identifier, objectId: Identifier, vcs: Seq[VerificationCondition], precision: Precision): Program = {
    var defs: Seq[Definition] = Seq.empty

    for (vc <- vcs) {
      val f = vc.funDef
      val id = f.id
      val floatType = getNonRealType(precision)
      val returnType = floatType // FIXME: check that this is actually RealType
      val args: Seq[VarDecl] = f.args.map(decl => VarDecl(decl.id, floatType))

      val funDef = new FunDef(id, returnType, args)
      funDef.body = f.body

      funDef.precondition = f.precondition

      vc.spec(precision) match {
        case Some(spec) => funDef.postcondition = Some(specToExpr(spec))
        case _ =>
      }

      defs = defs :+ funDef
    }
    val invariants: Seq[Expr] = Seq.empty

    val newProgram = Program(programId, ObjectDef(objectId, defs, invariants))
    newProgram
  }

  private def getNonRealType(precision: Precision): TypeTree = precision match {
    case Float64 => Float64Type
    case Float32 => Float32Type
    case DoubleDouble => FloatDDType
    case QuadDouble => FloatQDType
  }

  /*
  class AssertionRemover extends TransformerWithPC {
    type C = Seq[Expr]
    val initC = Nil

    def register(e: Expr, path: C) = path :+ e

    override def rec(e: Expr, path: C) = e match {
      case Assertion(expr) => True
      case _ =>
        super.rec(e, path)
    }
  }*/
}