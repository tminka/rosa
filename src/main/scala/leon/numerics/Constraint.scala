package leon
package numerics


import purescala.Common._
import purescala.Definitions._
import purescala.Trees._
import purescala.TreeOps._
import Valid._



case class Constraint(pre: Expr, body: Expr, post: Expr) {
  var status: Option[Valid] = None
  var model: Option[Map[Identifier, Expr]] = None

  def numVariables: Int = variablesOf(pre).size + variablesOf(body).size
  def size: Int = formulaSize(pre) + formulaSize(body)

  var paths: Set[Path] = Set.empty

}