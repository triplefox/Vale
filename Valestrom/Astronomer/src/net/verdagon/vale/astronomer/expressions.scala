package net.verdagon.vale.astronomer

import net.verdagon.vale.parser.{BorrowP, MutabilityP, OwnershipP, VariabilityP, WeakP}
import net.verdagon.vale.scout.{CodeLocationS, ITemplexS, IVariableUseCertainty, LocalVariable1, RangeS, RefCountCategory}
import net.verdagon.vale.scout.patterns.AtomSP
import net.verdagon.vale.scout.rules.IRulexSR
import net.verdagon.vale.vassert

// patternId is a unique number, can be used to make temporary variables that wont
// collide with other things
case class LetAE(
    range: RangeS,
    rules: List[IRulexAR],
    typeByRune: Map[IRuneA, ITemplataType],
    localRunes: Set[IRuneA],
    pattern: AtomAP,
    expr: IExpressionAE) extends IExpressionAE

case class DestructAE(range: RangeS, inner: IExpressionAE) extends IExpressionAE

case class IfAE(range: RangeS, condition: BlockAE, thenBody: BlockAE, elseBody: BlockAE) extends IExpressionAE

case class WhileAE(range: RangeS, condition: BlockAE, body: BlockAE) extends IExpressionAE

case class ExprMutateAE(range: RangeS, mutatee: IExpressionAE, expr: IExpressionAE) extends IExpressionAE
case class GlobalMutateAE(range: RangeS, name: IImpreciseNameStepA, expr: IExpressionAE) extends IExpressionAE
case class LocalMutateAE(range: RangeS, name: IVarNameA, expr: IExpressionAE) extends IExpressionAE

case class LendAE(range: RangeS, innerExpr1: IExpressionAE, targetOwnership: OwnershipP) extends IExpressionAE {
  targetOwnership match {
    case WeakP =>
    case BorrowP =>
  }
}
case class LockWeakAE(range: RangeS, innerExpr1: IExpressionAE) extends IExpressionAE

case class ReturnAE(range: RangeS, innerExpr1: IExpressionAE) extends IExpressionAE


//case class CurriedFuncH(closureExpr: ExpressionH, funcName: String) extends ExpressionH

// when we make a closure, we make a struct full of pointers to all our variables
// and the first element is our parent closure
// this can live on the stack, since blocks are limited to this expression
// later we can optimize it to only have the things we use

//
//sealed trait IVariableUseCertainty
//case object Used extends IVariableUseCertainty
//case object NotUsed extends IVariableUseCertainty
//case object MaybeUsed extends IVariableUseCertainty

case class BodyAE(
    range: RangeS,
    // These are all the variables we use from parent environments.
    // We have these so templar doesn't have to dive through all the functions
    // that it calls (impossible) to figure out what's needed in a closure struct.
    closuredNames: List[IVarNameA],

    block: BlockAE
) extends IExpressionAE

case class BlockAE(
  range: RangeS,
  // This shouldn't be ordered yet because we introduce new locals all the
  // time in templar, easier to just order them in hammer.
  locals: List[LocalVariableA],

  exprs: List[IExpressionAE],
) extends IExpressionAE {
  // Every element should have at least one expression, because a block will
  // return the last expression's result as its result.
  // Even empty blocks aren't empty, they have a void() at the end.
  vassert(exprs.nonEmpty)

  vassert(locals == locals.distinct)
}

//case class ConstructAE(
//  tyype: ITemplexA,
//  args: List[IExpressionAE]) extends IExpressionAE

case class ConstructArrayAE(
    range: RangeS,
    typeTemplex: ITemplexA,
    sizeExpr: IExpressionAE,
    generatorExpr: IExpressionAE,
    mutability: MutabilityP) extends IExpressionAE

case class ArgLookupAE(range: RangeS, index: Int) extends IExpressionAE

case class CheckRefCountAE(
  range: RangeS,
  refExpr: IExpressionAE,
  category: RefCountCategory,
  numExpr: IExpressionAE) extends IExpressionAE

 // These things will be separated by semicolons, and all be joined in a block
case class RepeaterBlockAE(range: RangeS, expression: IExpressionAE) extends IExpressionAE

// Results in a pack, represents the differences between the expressions
case class RepeaterBlockIteratorAE(range: RangeS, expression: IExpressionAE) extends IExpressionAE

case class VoidAE(range: RangeS) extends IExpressionAE {}

case class SequenceEAE(range: RangeS, elements: List[IExpressionAE]) extends IExpressionAE

// This thing will be repeated, separated by commas, and all be joined in a pack
case class RepeaterPackAE(range: RangeS, expression: IExpressionAE) extends IExpressionAE

// Results in a pack, represents the differences between the elements
case class RepeaterPackIteratorAE(range: RangeS, expression: IExpressionAE) extends IExpressionAE

case class IntLiteralAE(range: RangeS, value: Int) extends IExpressionAE

case class BoolLiteralAE(range: RangeS, value: Boolean) extends IExpressionAE

case class StrLiteralAE(range: RangeS, value: String) extends IExpressionAE

case class FloatLiteralAE(range: RangeS, value: Float) extends IExpressionAE

case class FunctionAE(name: LambdaNameA, function: FunctionA) extends IExpressionAE {
  override def range: RangeS = function.range
}

case class DotAE(range: RangeS, left: IExpressionAE, member: String, borrowContainer: Boolean) extends IExpressionAE

case class DotCallAE(range: RangeS, left: IExpressionAE, indexExpr: IExpressionAE) extends IExpressionAE

case class FunctionCallAE(range: RangeS, callableExpr: IExpressionAE, argsExprs1: List[IExpressionAE]) extends IExpressionAE

//case class MethodCall0(callableExpr: Expression0, objectExpr: Expression0, argsExpr: Pack0) extends Expression0

case class TemplateSpecifiedLookupAE(range: RangeS, name: String, templateArgs: List[ITemplexS]) extends IExpressionAE
case class RuneLookupAE(range: RangeS, rune: IRuneA, tyype: ITemplataType) extends IExpressionAE

case class LocalLoadAE(range: RangeS, name: IVarNameA, targetOwnership: OwnershipP) extends IExpressionAE
case class OutsideLoadAE(range: RangeS, name: String) extends IExpressionAE

case class UnletAE(range: RangeS, name: String) extends IExpressionAE

case class ArrayLengthAE(range: RangeS, arrayExpr: IExpressionAE) extends IExpressionAE


case class LocalVariableA(
  varName: IVarNameA,
  variability: VariabilityP,
  selfBorrowed: IVariableUseCertainty, // unused
  selfMoved: IVariableUseCertainty, // used to know whether to box, if we have branching.
  selfMutated: IVariableUseCertainty, // unused
  childBorrowed: IVariableUseCertainty, // unused
  childMoved: IVariableUseCertainty, // used to know whether to box
  childMutated: IVariableUseCertainty) // used to know whether to box
