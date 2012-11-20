package leon
package synthesis
package rules

import purescala.Trees._
import purescala.Common._
import purescala.Definitions._
import purescala.TypeTrees._
import purescala.TreeOps._
import purescala.Extractors._

class CEGIS(synth: Synthesizer) extends Rule("CEGIS", synth, 150) {
  def attemptToApplyOn(p: Problem): RuleResult = {
    case class Generator(tpe: TypeTree, altBuilder: () => List[(Expr, Set[Identifier])]);

    var generators = Map[TypeTree, Generator]()
    def getGenerator(t: TypeTree): Generator = generators.get(t) match {
      case Some(g) => g
      case None =>
        val alternatives: () => List[(Expr, Set[Identifier])] = t match {
          case BooleanType =>
            { () => List((BooleanLiteral(true), Set()), (BooleanLiteral(false), Set())) }

          case Int32Type =>
            { () => List((IntLiteral(0), Set()), (IntLiteral(1), Set())) }

          case TupleType(tps) =>
            { () =>
              val ids = tps.map(t => FreshIdentifier("t", true).setType(t))
              List((Tuple(ids.map(Variable(_))), ids.toSet))
            }

          case CaseClassType(cd) =>
            { () =>
              val ids = cd.fieldsIds.map(i => FreshIdentifier("c", true).setType(i.getType))
              List((CaseClass(cd, ids.map(Variable(_))), ids.toSet))
            }

          case AbstractClassType(cd) =>
            { () =>
              val alts: Seq[(Expr, Set[Identifier])] = cd.knownDescendents.flatMap(i => i match {
                  case acd: AbstractClassDef =>
                    synth.reporter.error("Unnexpected abstract class in descendants!")
                    None
                  case cd: CaseClassDef =>
                    val ids = cd.fieldsIds.map(i => FreshIdentifier("c", true).setType(i.getType))
                    Some((CaseClass(cd, ids.map(Variable(_))), ids.toSet))
              })
              alts.toList
            }

          case _ =>
            synth.reporter.error("Can't construct generator. Unsupported type: "+t+"["+t.getClass+"]");
            { () => Nil }
        }
        val g = Generator(t, alternatives)
        generators += t -> g
        g
    }

    def inputAlternatives(t: TypeTree): List[(Expr, Set[Identifier])] = {
      p.as.filter(a => isSubtypeOf(a.getType, t)).map(id => (Variable(id) : Expr, Set[Identifier]()))
    }

    case class TentativeFormula(pathcond: Expr,
                                phi: Expr,
                                program: Expr,
                                mappings: Map[Identifier, (Identifier, Expr)],
                                recTerms: Map[Identifier, Set[Identifier]]) {
      def unroll: TentativeFormula = {
        var newProgram  = List[Expr]()
        var newRecTerms = Map[Identifier, Set[Identifier]]()
        var newMappings = Map[Identifier, (Identifier, Expr)]()

        for ((_, recIds) <- recTerms; recId <- recIds) {
          val gen  = getGenerator(recId.getType)
          val alts = gen.altBuilder() ::: inputAlternatives(recId.getType)

          val altsWithBranches = alts.map(alt => FreshIdentifier("b", true).setType(BooleanType) -> alt)

          val bvs = altsWithBranches.map(alt => Variable(alt._1))
          val distinct = if (bvs.size > 1) {
            (for (i <- (1 to bvs.size-1); j <- 0 to i-1) yield {
              Or(Not(bvs(i)), Not(bvs(j)))
            }).toList
          } else {
            List(BooleanLiteral(true))
          }
          val pre = And(Or(bvs) :: distinct) // (b1 OR b2) AND (Not(b1) OR Not(b2))
          val cases = for((bid, (ex, rec)) <- altsWithBranches.toList) yield { // b1 => E(gen1, gen2)     [b1 -> {gen1, gen2}]
            if (!rec.isEmpty) {
              newRecTerms += bid -> rec
            }
            newMappings += bid -> (recId -> ex)

            Implies(Variable(bid), Equals(Variable(recId), ex))
          }

          newProgram = newProgram ::: pre :: cases
        }

        TentativeFormula(pathcond, phi, And(program :: newProgram), mappings ++ newMappings, newRecTerms)
      }

      def bounds = recTerms.keySet.map(id => Not(Variable(id))).toList
      def bss = mappings.keySet

      def entireFormula = And(pathcond :: phi :: program :: bounds)
    }

    val res = new RuleImmediateApplication {
      def apply() = {
        var result: Option[RuleApplicationResult]   = None

        var ass = p.as.toSet
        var xss = p.xs.toSet

        var lastF     = TentativeFormula(p.c, p.phi, BooleanLiteral(true), Map(), Map() ++ p.xs.map(x => x -> Set(x)))
        var currentF  = lastF.unroll
        var unrolings = 0
        val maxUnrolings = 3
        var predicates: Seq[Expr]        = Seq()
        try {
          do {
            //println("="*80)
            //println("Was: "+lastF.entireFormula)
            //println("Now Trying : "+currentF.entireFormula)

            val tpe = TupleType(p.xs.map(_.getType))
            val bss = currentF.bss

            var continue = true

            while (result.isEmpty && continue && synth.continue) {
              val basePhi = currentF.entireFormula
              val constrainedPhi = And(basePhi +: predicates)
              //println("-"*80)
              //println("To satisfy: "+constrainedPhi)
              synth.solver.solveSAT(constrainedPhi) match {
                case (Some(true), satModel) =>
                  //println("Found candidate!: "+satModel.filterKeys(bss))

                  //println("Corresponding program: "+simplifyTautologies(synth.solver)(valuateWithModelIn(currentF.program, bss, satModel)))
                  val fixedBss = And(bss.map(b => Equals(Variable(b), satModel(b))).toSeq)
                  //println("Phi with fixed sat bss: "+fixedBss)

                  val counterPhi = And(Seq(currentF.pathcond, fixedBss, currentF.program, Not(currentF.phi)))
                  //println("Formula to validate: "+counterPhi)

                  synth.solver.solveSAT(counterPhi) match {
                    case (Some(true), invalidModel) =>
                      val fixedAss = And(ass.map(a => Equals(Variable(a), invalidModel(a))).toSeq)

                      val mustBeUnsat = And(currentF.pathcond :: currentF.program :: fixedAss :: currentF.phi :: Nil)

                      val bssAssumptions: Set[Expr] = bss.toSet.map { b: Identifier => satModel(b) match {
                        case BooleanLiteral(true) => Variable(b)
                        case BooleanLiteral(false) => Not(Variable(b))
                      }}

                      val unsatCore = synth.solver.solveSATWithCores(mustBeUnsat, bssAssumptions) match {
                        case ((Some(false), _, core)) =>
                          //println("Formula: "+mustBeUnsat)
                          //println("Core:    "+core)
                          //println(synth.solver.solveSAT(And(mustBeUnsat +: bssAssumptions.toSeq)))
                          //println("maxcore: "+bssAssumptions)
                          if (core.isEmpty) {
                            synth.reporter.warning("Got empty core, must be unsat without assumptions!")
                            Set()
                          } else {
                            core
                          }
                        case _ =>
                          bssAssumptions
                      }

                      // Found as such as the xs break, refine predicates
                      //println("Found counter EX: "+invalidModel)
                      if (unsatCore.isEmpty) {
                        continue = false
                      } else {
                        predicates = Not(And(unsatCore.toSeq)) +: predicates
                      }

                    case (Some(false), _) =>
                      //println("Sat model: "+satModel.toSeq.sortBy(_._1.toString).map{ case (id, v) => id+" -> "+v }.mkString(", "))
                      var mapping = currentF.mappings.filterKeys(satModel.mapValues(_ == BooleanLiteral(true))).values.toMap

                      //println("Mapping: "+mapping)

                      // Resolve mapping
                      for ((c, e) <- mapping) {
                        mapping += c -> substAll(mapping, e)
                      }

                      result = Some(RuleSuccess(Solution(BooleanLiteral(true), Set(), Tuple(p.xs.map(valuateWithModel(mapping))).setType(tpe))))

                    case _ =>
                      synth.reporter.warning("Solver returned 'UNKNOWN' in a CEGIS iteration.")
                      continue = false
                  }

                case (Some(false), _) =>
                  //println("%%%% UNSAT")
                  continue = false
                case _ =>
                  //println("%%%% WOOPS")
                  continue = false
              }
            }

            lastF = currentF
            currentF = currentF.unroll
            unrolings += 1
          } while(unrolings < maxUnrolings && lastF != currentF && result.isEmpty && synth.continue)

          result.getOrElse(RuleApplicationImpossible)

        } catch {
          case e: Throwable =>
            synth.reporter.warning("CEGIS crashed: "+e.getMessage)
            RuleApplicationImpossible
        }

      }
    }

    RuleResult(List(res))
  }
}