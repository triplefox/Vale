#include <iostream>
#include "region/common/controlblock.h"

#include "translatetype.h"

#include "function/expressions/shared/members.h"
#include "function/expression.h"
#include "function/expressions/shared/shared.h"
#include "region/common/heap.h"

Ref translateLocalLoad(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    LocalLoad* localLoad) {
  auto local = localLoad->local;
  auto localId = local->id;
  auto localName = localLoad->localName;
  auto localType = local->type;
  auto targetOwnership = localLoad->targetOwnership;
  auto targetLocation = targetOwnership == Ownership::SHARE ? localType->location : Location::YONDER;
  auto resultType = globalState->metalCache.getReference(targetOwnership, targetLocation, localType->referend);

  buildFlare(FL(), globalState, functionState, builder);

  auto localAddr = blockState->getLocalAddr(localId);

  buildFlare(FL(), globalState, functionState, builder);

  auto sourceLE = LLVMBuildLoad(builder, localAddr, localName.c_str());

  buildFlare(FL(), globalState, functionState, builder);

  auto sourceRef = wrap(functionState->defaultRegion, localType, sourceLE);
  functionState->defaultRegion->checkValidReference(FL(), functionState, builder, localType, sourceRef);

  buildFlare(FL(), globalState, functionState, builder);

  auto resultRefLE =
      functionState->defaultRegion->upgradeLoadResultToRefWithTargetOwnership(
          functionState, builder, localType, resultType, sourceRef);
  buildFlare(FL(), globalState, functionState, builder);

  functionState->defaultRegion->alias(FL(), functionState, builder, resultType, resultRefLE);

  buildFlare(FL(), globalState, functionState, builder);

  return resultRefLE;
}
