package cp

import purescala.Trees._
import purescala.TypeTrees.classDefToClassType
import purescala.Definitions._
import purescala.Common.Identifier

trait CodeGeneration {
  self: CPComponent =>
  import global._
  import CODE._

  private lazy val scalaPackage                   = definitions.ScalaPackage

  private lazy val exceptionClass                 = definitions.getClass("java.lang.Exception")

  private lazy val collectionModule               = definitions.getModule("scala.collection")
  private lazy val immutableModule                = definitions.getModule("scala.collection.immutable")
  private lazy val listMapFunction                = definitions.getMember(definitions.ListClass, "map")
  private lazy val listClassApplyFunction         = definitions.getMember(definitions.ListClass, "apply")
  private lazy val listModuleApplyFunction        = definitions.getMember(definitions.ListModule, "apply")
  private lazy val canBuildFromFunction           = definitions.getMember(definitions.ListModule, "canBuildFrom")

  private lazy val iteratorClass                  = definitions.getClass("scala.collection.Iterator")
  private lazy val iteratorMapFunction            = definitions.getMember(iteratorClass, "map")

  private lazy val optionClass                    = definitions.getClass("scala.Option")
  private lazy val optionMapFunction              = definitions.getMember(optionClass, "map")

  private lazy val cpPackage                      = definitions.getModule("cp")

  private lazy val callTransformationModule       = definitions.getModule("cp.CallTransformation")
  private lazy val chooseExecFunction             = definitions.getMember(callTransformationModule, "chooseExec")
  private lazy val chooseMinimizingExecFunction   = definitions.getMember(callTransformationModule, "chooseMinimizingExec")
  private lazy val chooseMaximizingExecFunction   = definitions.getMember(callTransformationModule, "chooseMaximizingExec")
  private lazy val findMinimizingExecFunction     = definitions.getMember(callTransformationModule, "findMinimizingExec")
  private lazy val findMaximizingExecFunction     = definitions.getMember(callTransformationModule, "findMaximizingExec")
  private lazy val findExecFunction               = definitions.getMember(callTransformationModule, "findExec")
  private lazy val findAllExecFunction            = definitions.getMember(callTransformationModule, "findAllExec")
  private lazy val findAllMinimizingExecFunction  = definitions.getMember(callTransformationModule, "findAllMinimizingExec")
  private lazy val inputVarFunction               = definitions.getMember(callTransformationModule, "inputVar")
  private lazy val skipCounterFunction            = definitions.getMember(callTransformationModule, "skipCounter")

  private lazy val serializationModule            = definitions.getModule("cp.Serialization")
  private lazy val getProgramFunction             = definitions.getMember(serializationModule, "getProgram")
  private lazy val getInputVarListFunction        = definitions.getMember(serializationModule, "getInputVarList")

  private lazy val purescalaPackage               = definitions.getModule("purescala")

  private lazy val definitionsModule              = definitions.getModule("purescala.Definitions")
  private lazy val programClass                   = definitions.getClass("purescala.Definitions.Program")
  private lazy val caseClassDefFunction           = definitions.getMember(programClass, "caseClassDef")
  private lazy val caseClassDefClass              = definitions.getClass("purescala.Definitions.CaseClassDef")
  private lazy val idField                        = definitions.getMember(caseClassDefClass, "id")

  private lazy val commonModule                   = definitions.getModule("purescala.Common")
  private lazy val identifierClass                = definitions.getClass("purescala.Common.Identifier")
  private lazy val nameField                      = definitions.getMember(identifierClass, "name")

  private lazy val treesModule                    = definitions.getModule("purescala.Trees")
  private lazy val exprClass                      = definitions.getClass("purescala.Trees.Expr")
  private lazy val intLiteralModule               = definitions.getModule("purescala.Trees.IntLiteral")
  private lazy val intLiteralClass                = definitions.getClass("purescala.Trees.IntLiteral")
  private lazy val booleanLiteralModule           = definitions.getModule("purescala.Trees.BooleanLiteral")
  private lazy val booleanLiteralClass            = definitions.getClass("purescala.Trees.BooleanLiteral")
  private lazy val caseClassModule                = definitions.getModule("purescala.Trees.CaseClass")
  private lazy val caseClassClass                 = definitions.getClass("purescala.Trees.CaseClass")
  private lazy val andClass                       = definitions.getClass("purescala.Trees.And")
  private lazy val equalsClass                    = definitions.getClass("purescala.Trees.Equals")

  class CodeGenerator(unit : CompilationUnit, owner : Symbol, defaultPos : Position) {

    private def execCode(function : Symbol, progString : String, progId : Int, exprString : String, exprId : Int, 
        outputVarsString : String, outputVarsId : Int, inputConstraints : Tree) : Tree = {
      (cpPackage DOT callTransformationModule DOT function) APPLY
        (LIT(progString), LIT(progId), LIT(exprString), LIT(exprId), LIT(outputVarsString), LIT(outputVarsId), inputConstraints)
    }

    private def execOptimizingCode(function : Symbol, progString : String, progId : Int, exprString : String, exprId : Int, 
        outputVarsString : String, outputVarsId : Int, optExprString : String, optExprId : Int, inputConstraints : Tree) : Tree = {
      (cpPackage DOT callTransformationModule DOT function) APPLY
        (LIT(progString), LIT(progId), LIT(exprString), LIT(exprId), LIT(outputVarsString), LIT(outputVarsId), LIT(optExprString), LIT(optExprId), inputConstraints)
    }

    def chooseExecCode(progString : String, progId : Int, exprString : String, exprId : Int, outputVarsString : String, outputVarsId : Int, inputConstraints : Tree) : Tree = {
      execCode(chooseExecFunction, progString, progId, exprString, exprId, outputVarsString, outputVarsId, inputConstraints)
    }

    def chooseMinimizingExecCode(progString : String, progId : Int, exprString : String, exprId : Int, outputVarsString : String, outputVarsId : Int, minExprString : String, minExprId : Int, inputConstraints : Tree) : Tree = {
      execOptimizingCode(chooseMinimizingExecFunction, progString, progId, exprString, exprId, outputVarsString, outputVarsId, minExprString, minExprId, inputConstraints)
    }

    def chooseMaximizingExecCode(progString : String, progId : Int, exprString : String, exprId : Int, outputVarsString : String, outputVarsId : Int, maxExprString : String, maxExprId : Int, inputConstraints : Tree) : Tree = {
      execOptimizingCode(chooseMaximizingExecFunction, progString, progId, exprString, exprId, outputVarsString, outputVarsId, maxExprString, maxExprId, inputConstraints)
    }

    def findMinimizingExecCode(progString : String, progId : Int, exprString : String, exprId : Int, outputVarsString : String, outputVarsId : Int, minExprString : String, minExprId : Int, inputConstraints : Tree) : Tree = {
      execOptimizingCode(findMinimizingExecFunction, progString, progId, exprString, exprId, outputVarsString, outputVarsId, minExprString, minExprId, inputConstraints)
    }

    def findMaximizingExecCode(progString : String, progId : Int, exprString : String, exprId : Int, outputVarsString : String, outputVarsId : Int, maxExprString : String, maxExprId : Int, inputConstraints : Tree) : Tree = {
      execOptimizingCode(findMaximizingExecFunction, progString, progId, exprString, exprId, outputVarsString, outputVarsId, maxExprString, maxExprId, inputConstraints)
    }

    def findExecCode(progString : String, progId : Int, exprString : String, exprId : Int, outputVarsString : String, outputVarsId : Int, inputConstraints : Tree) : Tree = {
      execCode(findExecFunction, progString, progId, exprString, exprId, outputVarsString, outputVarsId, inputConstraints)
    }
      
    def findAllExecCode(progString : String, progId : Int, exprString : String, exprId : Int, outputVarsString : String, outputVarsId : Int, inputConstraints : Tree) : Tree = {
      execCode(findAllExecFunction, progString, progId, exprString, exprId, outputVarsString, outputVarsId, inputConstraints)
    }
    def findAllMinimizingExecCode(progString : String, progId : Int, exprString : String, exprId : Int, outputVarsString : String, outputVarsId : Int, minExprString : String, minExprId : Int, inputConstraints : Tree) : Tree = {
      execOptimizingCode(findAllMinimizingExecFunction, progString, progId, exprString, exprId, outputVarsString, outputVarsId, minExprString, minExprId, inputConstraints)
    }

    def mapIterator(mapFunction : Symbol, iterTree : Tree) : Tree = {
      val returnType = mapFunction.tpe match {
        case MethodType(_,rt) => rt
        case unhandled => scala.Predef.error("Unexpected method type : " + unhandled)
      }

      val seqExprType = typeRef(NoPrefix, definitions.SeqClass, List(exprClass.tpe))

      val anonFunSym = owner.newValue(NoPosition, nme.ANON_FUN_NAME) setInfo (mapFunction.tpe)
      val argValue = anonFunSym.newValue(NoPosition, unit.fresh.newName(NoPosition, "x")) setInfo seqExprType

      val anonFun = Function(
        List(ValDef(argValue, EmptyTree)),
        mapFunction APPLY ID(argValue)
      ) setSymbol anonFunSym 

      Apply(
        TypeApply(iterTree DOT iteratorMapFunction, List(TypeTree(returnType))),
        List(BLOCK(anonFun))
      )
    }

    def mapOption(mapFunction : Symbol, optionTree : Tree) : Tree = {
      val returnType = mapFunction.tpe match {
        case MethodType(_,rt) => rt
        case unhandled        => scala.Predef.error("Unexpected method type : " + unhandled)
      }

      val seqExprType = typeRef(NoPrefix, definitions.SeqClass, List(exprClass.tpe))

      val anonFunSym = owner.newValue(NoPosition, nme.ANON_FUN_NAME) setInfo (mapFunction.tpe)
      val argValue = anonFunSym.newValue(NoPosition, unit.fresh.newName(NoPosition, "x")) setInfo seqExprType

      val anonFun = Function(
        List(ValDef(argValue, EmptyTree)),
        mapFunction APPLY ID(argValue)
      ) setSymbol anonFunSym 

      Apply(
        TypeApply(optionTree DOT optionMapFunction, List(TypeTree(returnType))),
        List(BLOCK(anonFun))
      )
    }

    def exprSeqToScalaMethod(owner : Symbol, exprToScalaCastSym : Symbol, signature : List[Tree]) : (Tree, Symbol) = {
      val returnType = if (signature.size == 1) signature.head.tpe else definitions.tupleType(signature.map(_.tpe))

      val methodSym = owner.newMethod(NoPosition, unit.fresh.newName(NoPosition, "exprSeqToScala"))
      methodSym setInfo (MethodType(methodSym newSyntheticValueParams (List(typeRef(NoPrefix, definitions.SeqClass, List(exprClass.tpe)))), returnType))

      owner.info.decls.enter(methodSym)
      val arg = methodSym ARG 0
      val returnExpressions = (for ((typeTree, idx) <- signature.zipWithIndex) yield {
        val argExpr = ((arg DOT listClassApplyFunction) APPLY LIT(idx)) AS (exprClass.tpe)
        Apply(TypeApply(Ident(exprToScalaCastSym), List(typeTree)), List(argExpr))
      })

      val returnExpr : Tree = if (signature.size == 1) returnExpressions.head else {
        val tupleTypeTree = TypeTree(returnType)
        New(tupleTypeTree,List(returnExpressions))
      }

      (DEF(methodSym) === returnExpr, methodSym)
    }

    /* Generate the two methods required for converting funcheck expressions to
     * Scala terms, and return the method symbols and method definitions
     * respectively */
    def exprToScalaMethods(owner : Symbol, prog : Program) : ((Symbol, Tree), (Symbol, Tree)) = {
      // `method` is the one that matches expressions and converts them
      // recursively, `castMethod` invokes `method` and casts the results.
      val methodSym     = owner.newMethod(NoPosition, unit.fresh.newName(NoPosition, "exprToScala"))
      val castMethodSym = owner.newMethod(NoPosition, unit.fresh.newName(NoPosition, "exprToScalaCast"))

      val parametricType = castMethodSym.newTypeParameter(NoPosition, unit.fresh.newName(NoPosition, "A"))
      parametricType setInfo (TypeBounds(definitions.NothingClass.typeConstructor, definitions.AnyClass.typeConstructor))

      methodSym      setInfo (MethodType(methodSym     newSyntheticValueParams (List(exprClass.tpe)), definitions.AnyClass.tpe))
      castMethodSym  setInfo (PolyType(List(parametricType), MethodType(castMethodSym newSyntheticValueParams (List(exprClass.tpe)), parametricType.tpe)))

      owner.info.decls.enter(methodSym)
      owner.info.decls.enter(castMethodSym)

      // the following is for the recursive method
      val intSym        = methodSym.newValue(NoPosition, unit.fresh.newName(NoPosition, "value")).setInfo(definitions.IntClass.tpe)
      val booleanSym    = methodSym.newValue(NoPosition, unit.fresh.newName(NoPosition, "value")).setInfo(definitions.BooleanClass.tpe)

      val ccdBinderSym  = methodSym.newValue(NoPosition, unit.fresh.newName(NoPosition, "ccd")).setInfo(caseClassDefClass.tpe)
      val argsBinderSym = methodSym.newValue(NoPosition, unit.fresh.newName(NoPosition, "args")).setInfo(typeRef(NoPrefix, definitions.SeqClass, List(exprClass.tpe)))

      val definedCaseClasses : Seq[CaseClassDef] = prog.definedClasses.filter(_.isInstanceOf[CaseClassDef]).map(_.asInstanceOf[CaseClassDef])
      val dccSyms = definedCaseClasses map (reverseClassesToClasses(_))

      val caseClassMatchCases : List[CaseDef] = ((definedCaseClasses zip dccSyms) map {
        case (ccd, scalaSym) =>
          (CASE(caseClassModule APPLY ((ccdBinderSym BIND WILD()), (argsBinderSym BIND WILD()))) IF ((ccdBinderSym DOT idField DOT nameField).setPos(defaultPos) ANY_== LIT(ccd.id.name).setPos(defaultPos))) ==>
            New(TypeTree(scalaSym.tpe), List({
              (ccd.fields.zipWithIndex map {
                case (VarDecl(id, tpe), idx) =>
                  val typeArg = tpe match {
                    case purescala.TypeTrees.BooleanType => definitions.BooleanClass
                    case purescala.TypeTrees.Int32Type => definitions.IntClass
                    case c : purescala.TypeTrees.ClassType => reverseClassesToClasses(c.classDef)
                    case _ => scala.Predef.error("Cannot generate method using type : " + tpe)
                  }
                  Apply(
                    TypeApply(
                      Ident(castMethodSym), 
                      List(TypeTree(typeArg.tpe))
                    ), 
                    List(
                       // cast hack to make typer happy :(
                       ((argsBinderSym DOT listClassApplyFunction) APPLY LIT(idx)) AS (exprClass.tpe)
                    )
                  )
              }).toList
            }))
      }).toList

      val matchExpr = (methodSym ARG 0) MATCH ( List(
        CASE((intLiteralModule) APPLY (intSym BIND WILD()))         ==> ID(intSym) ,
        CASE((booleanLiteralModule) APPLY (booleanSym BIND WILD())) ==> ID(booleanSym)) :::
        caseClassMatchCases :::
        List(DEFAULT                                                ==> THROW(exceptionClass, LIT("Cannot convert FunCheck expression to Scala term"))) : _*
      )

      // the following is for the casting method
      val castBody = (methodSym APPLY (castMethodSym ARG 0)) AS (parametricType.tpe)

      ((methodSym, DEF(methodSym) === matchExpr), (castMethodSym, DEF(castMethodSym) === castBody))
    }

    /* Generate the method for converting ground Scala terms into funcheck
     * expressions */
    def scalaToExprMethod(owner : Symbol, prog : Program, progString : String, progId : Int) : (Tree, Symbol) = {
      val methodSym = owner.newMethod(NoPosition, unit.fresh.newName(NoPosition, "scalaToExpr"))
      methodSym setInfo (MethodType(methodSym newSyntheticValueParams (List(definitions.AnyClass.tpe)), exprClass.tpe))
      owner.info.decls.enter(methodSym)

      val intSym        = methodSym.newValue(NoPosition, unit.fresh.newName(NoPosition, "value")).setInfo(definitions.IntClass.tpe)
      val booleanSym    = methodSym.newValue(NoPosition, unit.fresh.newName(NoPosition, "value")).setInfo(definitions.BooleanClass.tpe)

      val definedCaseClasses : Seq[CaseClassDef] = prog.definedClasses.filter(_.isInstanceOf[CaseClassDef]).map(_.asInstanceOf[CaseClassDef])
      val dccSyms = definedCaseClasses map (reverseClassesToClasses(_))

      val caseClassMatchCases = ((definedCaseClasses zip dccSyms) map {
        case (ccd, scalaSym) =>
          /*
          val binderSyms = (ccd.fields.map {
            case VarDecl(id, tpe) =>
              methodSym.newValue(NoPosition, unit.fresh.newName(NoPosition, id.name)).setInfo(definitions.AnyClass.tpe)
          }).toList
          */

          val scalaBinderSym = methodSym.newValue(NoPosition, unit.fresh.newName(NoPosition, "cc")).setInfo(scalaSym.tpe)

          val memberSyms = (ccd.fields.map {
            case VarDecl(id, tpe) =>
              scalaSym.info.member(id.name)
          }).toList

          // CASE(scalaSym APPLY (binderSyms map (_ BIND WILD()))) ==>
          CASE(scalaBinderSym BIND Typed(WILD(), TypeTree(scalaSym.tpe))) ==>
            New(
              TypeTree(caseClassClass.tpe),
              List(
                List(
                  (((cpPackage DOT serializationModule DOT getProgramFunction) APPLY (LIT(progString), LIT(progId))) DOT caseClassDefFunction) APPLY LIT(scalaSym.nameString),
                  (scalaPackage DOT collectionModule DOT immutableModule DOT definitions.ListModule DOT listModuleApplyFunction) APPLY (memberSyms map {
                    case ms => methodSym APPLY (scalaBinderSym DOT ms)
                  })
                )
              )
            )
      }).toList

      val matchExpr = (methodSym ARG 0) MATCH ( List(
        CASE(intSym     BIND Typed(WILD(), TypeTree(definitions.IntClass.tpe)))     ==> NEW(ID(intLiteralClass), ID(intSym)) ,
        CASE(booleanSym BIND Typed(WILD(), TypeTree(definitions.BooleanClass.tpe))) ==> NEW(ID(booleanLiteralClass), ID(booleanSym))) :::
        caseClassMatchCases :::
        List(DEFAULT                                                                ==> THROW(exceptionClass, LIT("Cannot convert Scala term to FunCheck expression"))) : _*
      )

      (DEF(methodSym) === matchExpr, methodSym)
    }

    def inputEquality(inputVarListString : String, inputVarListId : Int, varId : Identifier, scalaToExprSym : Symbol) : Tree = {
      NEW(
        ID(equalsClass),
          // retrieve input variable list and get corresponding variable
        (cpPackage DOT callTransformationModule DOT inputVarFunction) APPLY
          ((cpPackage DOT serializationModule DOT getInputVarListFunction) APPLY (LIT(inputVarListString), LIT(inputVarListId)), LIT(varId.name)),
        // invoke s2e on corresponding Tree
        scalaToExprSym APPLY variablesToTrees(Variable(varId))
      )
    }

    def andExpr(exprs : Seq[Tree]) : Tree = {
      NEW(ID(andClass), (scalaPackage DOT collectionModule DOT immutableModule DOT definitions.ListModule DOT listModuleApplyFunction) APPLY (exprs.toList))
    }

    def trueLiteral : Tree = {
      NEW(ID(booleanLiteralClass), LIT(true))
    }

    def skipCounter(i : Int) : Tree = {
      (cpPackage DOT callTransformationModule DOT skipCounterFunction) APPLY LIT(i)
    }
  }
}