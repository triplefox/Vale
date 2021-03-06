#include <iostream>
#include <function/expressions/shared/shared.h>

#include "translatetype.h"

#include "function.h"
#include "expression.h"

LLVMValueRef declareFunction(
    GlobalState* globalState,
    IRegion* region,
    Function* functionM) {

  auto paramTypesL = translateTypes(globalState, region, functionM->prototype->params);
  auto returnTypeL = region->translateType(functionM->prototype->returnType);
  auto nameL = functionM->prototype->name->name;

  LLVMTypeRef functionTypeL =
      LLVMFunctionType(returnTypeL, paramTypesL.data(), paramTypesL.size(), 0);
  LLVMValueRef functionL = LLVMAddFunction(globalState->mod, nameL.c_str(), functionTypeL);

  assert(globalState->functions.count(functionM->prototype->name->name) == 0);
  globalState->functions.emplace(functionM->prototype->name->name, functionL);

  return functionL;
}

//LLVMTypeRef translateExternType(GlobalState* globalState, Reference* reference) {
//  if (reference == globalState->metalCache.intRef) {
//    return LLVMInt64TypeInContext(globalState->context);
//  } else if (reference == globalState->metalCache.boolRef) {
//    return LLVMInt8TypeInContext(globalState->context);
//  } else if (reference == globalState->metalCache.floatRef) {
//    return LLVMDoubleTypeInContext(globalState->context);
//  } else if (reference == globalState->metalCache.strRef) {
//    return LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0);
//  } else if (reference == globalState->metalCache.neverRef) {
//    return LLVMVoidTypeInContext(globalState->context);
//  } else if (reference == globalState->metalCache.emptyTupleStructRef) {
//    return LLVMVoidTypeInContext(globalState->context);
//  } else if (auto structReferend = dynamic_cast<StructReferend*>(reference->referend)) {
//    if (reference->location == Location::INLINE) {
//      return globalState->region->getReferendStructsSource()->getInnerStruct(structReferend);
//    } else {
//      std::cerr << "Can only pass inline imm structs between C and Vale currently." << std::endl;
//      assert(false);
//    }
//  } else {
//    std::cerr << "Invalid type for extern!" << std::endl;
//    assert(false);
//  }
//}

LLVMValueRef declareExternFunction(
    GlobalState* globalState,
    Prototype* prototypeM) {
  std::vector<LLVMTypeRef> paramTypesL;
  for (auto paramTypeM : prototypeM->params) {
    paramTypesL.push_back(globalState->region->translateType(paramTypeM));
  }

  auto returnTypeL = globalState->region->translateType(prototypeM->returnType);
  auto nameL = prototypeM->name->name;

  LLVMTypeRef functionTypeL =
      LLVMFunctionType(returnTypeL, paramTypesL.data(), paramTypesL.size(), 0);
  LLVMValueRef functionL = LLVMAddFunction(globalState->mod, nameL.c_str(), functionTypeL);

  assert(globalState->externFunctions.count(prototypeM->name->name) == 0);
  globalState->externFunctions.emplace(prototypeM->name->name, functionL);

  return functionL;
}

void translateFunction(
    GlobalState* globalState,
    IRegion* region,
    Function* functionM) {

  auto functionL = globalState->getFunction(functionM->prototype->name);
  auto returnTypeL = region->translateType(functionM->prototype->returnType);

  auto localAddrByLocalId = std::unordered_map<int, LLVMValueRef>{};


  auto localsBlockName = std::string("localsBlock");
  auto localsBuilder = LLVMCreateBuilderInContext(globalState->context);
  LLVMBasicBlockRef localsBlockL = LLVMAppendBasicBlockInContext(globalState->context, functionL, localsBlockName.c_str());
  LLVMPositionBuilderAtEnd(localsBuilder, localsBlockL);

  auto firstBlockName = std::string("codeStartBlock");
  LLVMBasicBlockRef firstBlockL = LLVMAppendBasicBlockInContext(globalState->context, functionL, firstBlockName.c_str());
  LLVMBuilderRef bodyTopLevelBuilder = LLVMCreateBuilderInContext(globalState->context);
  LLVMPositionBuilderAtEnd(bodyTopLevelBuilder, firstBlockL);

  FunctionState functionState(functionM->prototype->name->name, region, functionL, returnTypeL, localsBuilder);

  // There are other builders made elsewhere for various blocks in the function,
  // but this is the one for the top level.
  // It's not always pointed at firstBlockL, it can be re-pointed to other
  // blocks at the top level.
  //
  // For example, in:
  //   fn main() {
  //     x! = 5;
  //     if (true && true) {
  //       mut x = 7;
  //     } else {
  //       mut x = 8;
  //     }
  //     println(x);
  //   }
  // There are four blocks:
  // - 1: contains `x! = 5` and `true && true`
  // - 2: contains `mut x = 7;`
  // - 3: contains `mut x = 8;`
  // - 4: contains `println(x)`
  //
  // When it's done making block 1, we'll make block 4 and `bodyTopLevelBuilder`
  // will point at that.
  //
  // The point is, this builder can change to point at other blocks on the same
  // level.
  //
  // All builders work like this, at whatever level theyre on.

  BlockState initialBlockState(nullptr);

  buildFlare(FL(), globalState, &functionState, bodyTopLevelBuilder, "Inside function ", functionM->prototype->name->name);

  // Translate the body of the function. Can ignore the result because it's a
  // Never, because Valestrom guarantees we end function bodies in a ret.
  auto resultLE =
      translateExpression(
          globalState, &functionState, &initialBlockState, bodyTopLevelBuilder, functionM->block);

  initialBlockState.checkAllIntroducedLocalsWereUnstackified();

  // Now that we've added all the locals we need, lets make the locals block jump to the first
  // code block.
  LLVMBuildBr(localsBuilder, firstBlockL);

//  // This is a total hack, to try and appease LLVM to say that yes, we're sure
//  // we'll never reach this statement.
//  // In .ll we can call a noreturn function and then put an unreachable block,
//  // but I can't figure out how to specify noreturn with the LLVM C API.
//  if (dynamic_cast<Never*>(functionM->prototype->returnType->referend)) {
//    LLVMBuildRet(bodyTopLevelBuilder, LLVMGetUndef(region->translateType(functionM->prototype->returnType)));
//  }

  LLVMDisposeBuilder(bodyTopLevelBuilder);
}
