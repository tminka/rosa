/* Copyright 2009-2015 EPFL, Lausanne */

package leon
package purescala

import Trees._

object Extractors {
  import Common._
  import TypeTrees._
  import Definitions._
  import Extractors._
  import TreeOps._

  object UnaryOperator {
    def unapply(expr: Expr) : Option[(Expr,(Expr)=>Expr)] = expr match {
      case Not(t) => Some((t,Not(_)))
      case UMinus(t) => Some((t,UMinus))
      case SetCardinality(t) => Some((t,SetCardinality))
      case MultisetCardinality(t) => Some((t,MultisetCardinality))
      case MultisetToSet(t) => Some((t,MultisetToSet))
      case SetMin(s) => Some((s,SetMin))
      case SetMax(s) => Some((s,SetMax))
      case CaseClassSelector(cd, e, sel) => Some((e, CaseClassSelector(cd, _, sel)))
      case CaseClassInstanceOf(cd, e) => Some((e, CaseClassInstanceOf(cd, _)))
      case TupleSelect(t, i) => Some((t, TupleSelect(_, i)))
      case ArrayLength(a) => Some((a, ArrayLength))
      case ArrayClone(a) => Some((a, ArrayClone))
      case ArrayMake(t) => Some((t, ArrayMake))
      case (ue: UnaryExtractable) => ue.extract
      case _ => None
    }
  }

  trait UnaryExtractable {
    def extract: Option[(Expr, (Expr)=>Expr)];
  }

  object BinaryOperator {
    def unapply(expr: Expr) : Option[(Expr,Expr,(Expr,Expr)=>Expr)] = expr match {
      case Equals(t1,t2) => Some((t1,t2,Equals.apply))
      case Iff(t1,t2) => Some((t1,t2,Iff(_,_)))
      case Implies(t1,t2) => Some((t1,t2,Implies.apply))
      case Plus(t1,t2) => Some((t1,t2,Plus))
      case Minus(t1,t2) => Some((t1,t2,Minus))
      case Times(t1,t2) => Some((t1,t2,Times))
      case Division(t1,t2) => Some((t1,t2,Division))
      case Modulo(t1,t2) => Some((t1,t2,Modulo))
      case LessThan(t1,t2) => Some((t1,t2,LessThan))
      case GreaterThan(t1,t2) => Some((t1,t2,GreaterThan))
      case LessEquals(t1,t2) => Some((t1,t2,LessEquals))
      case GreaterEquals(t1,t2) => Some((t1,t2,GreaterEquals))
      case ElementOfSet(t1,t2) => Some((t1,t2,ElementOfSet))
      case SubsetOf(t1,t2) => Some((t1,t2,SubsetOf))
      case SetIntersection(t1,t2) => Some((t1,t2,SetIntersection))
      case SetUnion(t1,t2) => Some((t1,t2,SetUnion))
      case SetDifference(t1,t2) => Some((t1,t2,SetDifference))
      case Multiplicity(t1,t2) => Some((t1,t2,Multiplicity))
      case SubmultisetOf(t1,t2) => Some((t1,t2,SubmultisetOf))
      case MultisetIntersection(t1,t2) => Some((t1,t2,MultisetIntersection))
      case MultisetUnion(t1,t2) => Some((t1,t2,MultisetUnion))
      case MultisetPlus(t1,t2) => Some((t1,t2,MultisetPlus))
      case MultisetDifference(t1,t2) => Some((t1,t2,MultisetDifference))
      case mg@MapGet(t1,t2) => Some((t1,t2, (t1, t2) => MapGet(t1, t2).setPos(mg)))
      case MapUnion(t1,t2) => Some((t1,t2,MapUnion))
      case MapDifference(t1,t2) => Some((t1,t2,MapDifference))
      case MapIsDefinedAt(t1,t2) => Some((t1,t2, MapIsDefinedAt))
      case ArrayFill(t1, t2) => Some((t1, t2, ArrayFill))
      case ArraySelect(t1, t2) => Some((t1, t2, ArraySelect))
      case Let(binders, e, body) => Some((e, body, (e: Expr, b: Expr) => Let(binders, e, b)))
      case LetTuple(binders, e, body) => Some((e, body, (e: Expr, b: Expr) => LetTuple(binders, e, b)))
      case Require(pre, body) => Some((pre, body, Require))
      case Ensuring(body, id, post) => Some((body, post, (b: Expr, p: Expr) => Ensuring(b, id, p)))
      case Assert(const, oerr, body) => Some((const, body, (c: Expr, b: Expr) => Assert(c, oerr, b)))
      case (ex: BinaryExtractable) => ex.extract
      case _ => None
    }
  }

  trait BinaryExtractable {
    def extract: Option[(Expr, Expr, (Expr, Expr)=>Expr)];
  }

  object NAryOperator {
    def unapply(expr: Expr) : Option[(Seq[Expr],(Seq[Expr])=>Expr)] = expr match {
      case fi @ FunctionInvocation(fd, args) => Some((args, (as => FunctionInvocation(fd, as).setPos(fi))))
      case mi @ MethodInvocation(rec, cd, tfd, args) => Some((rec +: args, (as => MethodInvocation(as.head, cd, tfd, as.tail).setPos(mi))))
      case CaseClass(cd, args) => Some((args, CaseClass(cd, _)))
      case And(args) => Some((args, And.apply))
      case Or(args) => Some((args, Or.apply))
      case FiniteSet(args) =>
        Some((args.toSeq,
              { newargs =>
                if (newargs.isEmpty) {
                  FiniteSet(Set()).setType(expr.getType)
                } else {
                  FiniteSet(newargs.toSet)
                }
              }
            ))
      case FiniteMap(args) => {
        val subArgs = args.flatMap{case (k, v) => Seq(k, v)}
        val builder: (Seq[Expr]) => Expr = (as: Seq[Expr]) => {
          val (keys, values, isKey) = as.foldLeft[(List[Expr], List[Expr], Boolean)]((Nil, Nil, true)){
            case ((keys, values, isKey), rExpr) => if(isKey) (rExpr::keys, values, false) else (keys, rExpr::values, true)
          }
          assert(isKey)
          FiniteMap(keys.zip(values))
        }
        Some((subArgs, builder))
      }
      case FiniteMultiset(args) => Some((args, FiniteMultiset))
      case ArrayUpdated(t1, t2, t3) => Some((Seq(t1,t2,t3), (as: Seq[Expr]) => ArrayUpdated(as(0), as(1), as(2))))
      case FiniteArray(args) => Some((args, FiniteArray))
      case Distinct(args) => Some((args, Distinct))
      case Tuple(args) => Some((args, Tuple))
      case IfExpr(cond, thenn, elze) => Some((Seq(cond, thenn, elze), (as: Seq[Expr]) => IfExpr(as(0), as(1), as(2))))
      case MatchExpr(scrut, cases) =>
        Some((scrut +: cases.flatMap{ case SimpleCase(_, e) => Seq(e)
                                     case GuardedCase(_, e1, e2) => Seq(e1, e2) }
             , { es: Seq[Expr] =>
            var i = 1;
            val newcases = for (caze <- cases) yield caze match {
              case SimpleCase(b, _) => i+=1; SimpleCase(b, es(i-1))
              case GuardedCase(b, _, _) => i+=2; GuardedCase(b, es(i-2), es(i-1))
            }

           MatchExpr(es(0), newcases)
           }))
      case LetDef(fd, body) =>
        fd.body match {
          case Some(b) =>
            (fd.precondition, fd.postcondition) match {
              case (None, None) =>
                  Some((Seq(b, body), (as: Seq[Expr]) => {
                    fd.body = Some(as(0))
                    LetDef(fd, as(1))
                  }))
              case (Some(pre), None) =>
                  Some((Seq(b, body, pre), (as: Seq[Expr]) => {
                    fd.body = Some(as(0))
                    fd.precondition = Some(as(2))
                    LetDef(fd, as(1))
                  }))
              case (None, Some((pid, post))) =>
                  Some((Seq(b, body, post), (as: Seq[Expr]) => {
                    fd.body = Some(as(0))
                    fd.postcondition = Some((pid, as(2)))
                    LetDef(fd, as(1))
                  }))
              case (Some(pre), Some((pid, post))) =>
                  Some((Seq(b, body, pre, post), (as: Seq[Expr]) => {
                    fd.body = Some(as(0))
                    fd.precondition = Some(as(2))
                    fd.postcondition = Some((pid, as(3)))
                    LetDef(fd, as(1))
                  }))
            }

          case None => //case no body, we still need to handle remaining cases
            (fd.precondition, fd.postcondition) match {
              case (None, None) =>
                  Some((Seq(body), (as: Seq[Expr]) => {
                    LetDef(fd, as(0))
                  }))
              case (Some(pre), None) =>
                  Some((Seq(body, pre), (as: Seq[Expr]) => {
                    fd.precondition = Some(as(1))
                    LetDef(fd, as(0))
                  }))
              case (None, Some((pid, post))) =>
                  Some((Seq(body, post), (as: Seq[Expr]) => {
                    fd.postcondition = Some((pid, as(1)))
                    LetDef(fd, as(0))
                  }))
              case (Some(pre), Some((pid, post))) =>
                  Some((Seq(body, pre, post), (as: Seq[Expr]) => {
                    fd.precondition = Some(as(1))
                    fd.postcondition = Some((pid, as(2)))
                    LetDef(fd, as(0))
                  }))
            }
        }
      case (ex: NAryExtractable) => ex.extract
      case _ => None
    }
  }

  trait NAryExtractable {
    def extract: Option[(Seq[Expr], (Seq[Expr])=>Expr)];
  }

  object TopLevelOrs { // expr1 AND (expr2 AND (expr3 AND ..)) => List(expr1, expr2, expr3)
    def unapply(e: Expr): Option[Seq[Expr]] = e match {
      case Or(exprs) =>
        Some(exprs.flatMap(unapply(_)).flatten)
      case e =>
        Some(Seq(e))
    }
  }
  object TopLevelAnds { // expr1 AND (expr2 AND (expr3 AND ..)) => List(expr1, expr2, expr3)
    def unapply(e: Expr): Option[Seq[Expr]] = e match {
      case And(exprs) =>
        Some(exprs.flatMap(unapply(_)).flatten)
      case e =>
        Some(Seq(e))
    }
  }

  object IsTyped {
    def unapply[T <: Typed](e: T): Option[(T, TypeTree)] = Some((e, e.getType))
  }

}
