package net.verdagon.vale.scout

import net.verdagon.vale.parser._
import net.verdagon.vale.scout.patterns.{AtomSP, CaptureS}
import net.verdagon.vale.scout.rules._
import net.verdagon.vale.{Err, Ok, vassert, vfail}
import org.scalatest.{FunSuite, Matchers}

class ScoutTests extends FunSuite with Matchers {
  private def compile(code: String): ProgramS = {
    Parser.runParser(code) match {
      case ParseFailure(err) => fail(err.toString)
      case ParseSuccess(program0) => {
        Scout.scoutProgram(List(program0)) match {
          case Err(e) => vfail(e.toString)
          case Ok(t) => t
        }
      }
    }
  }

  private def compileForError(code: String): ICompileErrorS = {
    Parser.runParser(code) match {
      case ParseFailure(err) => fail(err.toString)
      case ParseSuccess(program0) => {
        Scout.scoutProgram(List(program0)) match {
          case Err(e) => e
          case Ok(t) => vfail("Successfully compiled!\n" + t.toString)
        }
      }
    }
  }

  test("Lookup +") {
    val program1 = compile("fn main() int { +(3, 4) }")
    val main = program1.lookupFunction("main")
    
    val CodeBody1(BodySE(_, block)) = main.body
    block match {
      case BlockSE(_, List(FunctionCallSE(_, OutsideLoadSE(_, "+"), _))) =>
    }
  }

  test("Struct") {
    val program1 = compile("struct Moo { x int; }")
    val imoo = program1.lookupStruct("Moo")

    val memberRune = MemberRuneS(0)
    imoo.rules match {
      case List(
        EqualsSR(_, TypedSR(_, memberRune, CoordTypeSR), TemplexSR(NameST(_, CodeTypeNameS("int")))),
        EqualsSR(_, TemplexSR(RuneST(_, ImplicitRuneS(_, _))), TemplexSR(MutabilityST(_, MutableP)))) =>
    }
    imoo.members match { case List(StructMemberS(_, "x",FinalP,memberRune)) => }
  }

  test("Lambda") {
    val program1 = compile("fn main() int { {_ + _}(4, 6) }")

    val CodeBody1(BodySE(_, BlockSE(_, List(expr)))) = program1.lookupFunction("main").body
    val FunctionCallSE(_, FunctionSE(lambda @ FunctionS(_, _, _, _, _, _, _, _,_, _, _, _)), _) = expr
    lambda.identifyingRunes match {
      case List(MagicParamRuneS(mp1), MagicParamRuneS(mp2)) => {
        vassert(mp1 != mp2)
      }
    }
  }

  test("Interface") {
    val program1 = compile("interface IMoo { fn blork(a bool)void; }")
    val imoo = program1.lookupInterface("IMoo")

    imoo.rules match {
      case List(EqualsSR(_, TemplexSR(RuneST(_, ImplicitRuneS(_, _))), TemplexSR(MutabilityST(_, MutableP)))) =>
    }

    val blork = imoo.internalMethods.head
    blork.name match { case FunctionNameS("blork", _) => }

    val (paramRune, retRune) =
      blork.templateRules match {
        case List(
          EqualsSR(_,
            TypedSR(_, actualParamRune, CoordTypeSR),
            TemplexSR(NameST(_, CodeTypeNameS("bool")))),
          EqualsSR(_,
            TypedSR(_, actualRetRune, CoordTypeSR),
            TemplexSR(NameST(_, CodeTypeNameS("void"))))) => {
          actualParamRune match {
            case ImplicitRuneS(_, 0) =>
          }
          actualRetRune match {
            case ImplicitRuneS(_, 1) =>
          }
          (actualParamRune, actualRetRune)
        }
      }

    RuleSUtils.getDistinctOrderedRunesForRulexes(blork.templateRules) shouldEqual
      List(paramRune, retRune)

    blork.params match {
      case List(
        ParameterS(
          AtomSP(_,
            CaptureS(CodeVarNameS("a"),FinalP),
            None,
            ImplicitRuneS(_, 0),
            None))) =>
    }

    // Yes, even though the user didnt specify any. See CCAUIR.
    blork.identifyingRunes shouldEqual List()
  }

  test("Impl") {
    val program1 = compile("impl IMoo for Moo;")
    val impl = program1.impls.head
    val structRune =
      impl.structKindRune match {
        case ir0 @ ImplicitRuneS(_, 0) => ir0
      }
    val interfaceRune =
      impl.interfaceKindRune match {
        case ir0 @ ImplicitRuneS(_, 1) => ir0
      }
    impl.rulesFromStructDirection match {
      case List(
          EqualsSR(_, TypedSR(_, a,KindTypeSR), TemplexSR(NameST(_, CodeTypeNameS("Moo")))),
          EqualsSR(_, TypedSR(_, b,KindTypeSR), TemplexSR(NameST(_, CodeTypeNameS("IMoo"))))) => {
        vassert(a == structRune)
        vassert(b == interfaceRune)
      }
    }
    impl.rulesFromInterfaceDirection match {
      case List(
      EqualsSR(_, TypedSR(_, b,KindTypeSR), TemplexSR(NameST(_, CodeTypeNameS("IMoo")))),
      EqualsSR(_, TypedSR(_, a,KindTypeSR), TemplexSR(NameST(_, CodeTypeNameS("Moo"))))) => {
        vassert(a == structRune)
        vassert(b == interfaceRune)
      }
    }
  }

  test("Method call") {
    val program1 = compile("fn main() int { x = 4; = x.shout(); }")
    val main = program1.lookupFunction("main")

    val CodeBody1(BodySE(_, block)) = main.body
    block match {
      case BlockSE(_, List(_, FunctionCallSE(_, OutsideLoadSE(_, "shout"), List(LendSE(LocalLoadSE(_,name, BorrowP), BorrowP))))) => {
        name match {
          case CodeVarNameS("x") =>
        }
      }
    }
  }

  test("Moving method call") {
    val program1 = compile("fn main() int { x = 4; = x^.shout(); }")
    val main = program1.lookupFunction("main")

    val CodeBody1(BodySE(_, block)) = main.body
    block match {
      case BlockSE(_, List(_, FunctionCallSE(_, OutsideLoadSE(_, "shout"), List(LocalLoadSE(_,_, OwnP))))) =>
    }
  }

  test("Function with magic lambda and regular lambda") {
    // There was a bug that confused the two, and an underscore would add a magic param to every lambda after it

    val program1 =
      compile(
        """fn main() int {
          |  {_};
          |  (a){a};
          |}
        """.stripMargin)
    val main = program1.lookupFunction("main")

    val CodeBody1(BodySE(_, block)) = main.body
    val BlockSE(_, FunctionSE(lambda1) :: FunctionSE(lambda2) :: _) = block
    lambda1.params match {
      case List(_, ParameterS(AtomSP(_,CaptureS(MagicParamNameS(_),FinalP),None,MagicParamRuneS(_),None))) =>
    }
    lambda2.params match {
      case List(_, ParameterS(AtomSP(_,CaptureS(CodeVarNameS("a"),FinalP),None,ImplicitRuneS(_, _),None))) =>
    }
  }


  test("Constructing members") {
    val program1 = compile(
      """fn MyStruct() {
        |  this.x = 4;
        |  this.y = true;
        |}
        |""".stripMargin)
    val main = program1.lookupFunction("MyStruct")

    val CodeBody1(BodySE(_, block)) = main.body
    block match {
      case BlockSE(
        List(
          LocalVariable1(ConstructingMemberNameS("x"),FinalP,NotUsed,Used,NotUsed,NotUsed,NotUsed,NotUsed),
          LocalVariable1(ConstructingMemberNameS("y"),FinalP,NotUsed,Used,NotUsed,NotUsed,NotUsed,NotUsed)),
        List(
          LetSE(_,
            _,
            _,
            _,
            AtomSP(_,CaptureS(ConstructingMemberNameS("x"),FinalP),None,_,None),
            IntLiteralSE(4)),
          LetSE(_,
            _,
            _,
            _,
            AtomSP(_,CaptureS(ConstructingMemberNameS("y"),FinalP),None,_,None),
            BoolLiteralSE(true)),
          FunctionCallSE(_,
            OutsideLoadSE(_, "MyStruct"),
            List(
              LocalLoadSE(_,ConstructingMemberNameS("x"),OwnP),
              LocalLoadSE(_,ConstructingMemberNameS("y"),OwnP))))) =>
    }
  }

  test("Constructing members, borrowing another member") {
    val program1 = compile(
      """fn MyStruct() {
        |  this.x = 4;
        |  this.y = &this.x;
        |}
        |""".stripMargin)
    val main = program1.lookupFunction("MyStruct")

    val CodeBody1(BodySE(_, block)) = main.body
    block match {
      case BlockSE(
        List(
          LocalVariable1(ConstructingMemberNameS("x"),FinalP,Used,Used,NotUsed,NotUsed,NotUsed,NotUsed),
          LocalVariable1(ConstructingMemberNameS("y"),FinalP,NotUsed,Used,NotUsed,NotUsed,NotUsed,NotUsed)),
        List(
          LetSE(_, _,_,_,
            AtomSP(_,CaptureS(ConstructingMemberNameS("x"),FinalP),None,_,None),
            IntLiteralSE(4)),
          LetSE(_, _,_,_,
            AtomSP(_,CaptureS(ConstructingMemberNameS("y"),FinalP),None,_,None),
            LendSE(LocalLoadSE(_,ConstructingMemberNameS("x"),BorrowP), BorrowP)),
          FunctionCallSE(_,
            OutsideLoadSE(_, "MyStruct"),
            List(
              LocalLoadSE(_,ConstructingMemberNameS("x"),OwnP),
              LocalLoadSE(_,ConstructingMemberNameS("y"),OwnP))))) =>
    }

  }

  test("this isnt special if was explicit param") {
    val program1 = compile(
      """fn moo(this &MyStruct) {
        |  println(this.x);
        |}
        |""".stripMargin)
    val main = program1.lookupFunction("moo")
    main.body match {
      case CodeBody1(
        BodySE(
          List(),
          BlockSE(
            List(LocalVariable1(CodeVarNameS("this"),FinalP,Used,NotUsed,NotUsed,NotUsed,NotUsed,NotUsed)),
            List(
              FunctionCallSE(_,
                OutsideLoadSE(_, "println"),
                List(DotSE(_, LocalLoadSE(_,CodeVarNameS("this"),BorrowP),"x",true))),
              VoidSE())))) =>
    }
  }

  test("Reports when mutating nonexistant local") {
    val err = compileForError(
      """fn main() int {
        |  mut a = a + 1;
        |}
        |""".stripMargin)
    err match {
      case CouldntFindVarToMutateS(_, "a") =>
    }
  }

  test("Reports when overriding non-kind in param") {
    val err = compileForError(
      """
        |struct Moo {}
        |interface IMoo {}
        |fn func(moo &Moo impl &IMoo) int { 73 }
        |""".stripMargin)
    err match {
      case CantOverrideOwnershipped(_) =>
    }
  }

  test("Reports when non-kind interface in impl") {
    val err = compileForError(
      """
        |struct Moo {}
        |interface IMoo {}
        |impl &IMoo for Moo;
        |""".stripMargin)
    err match {
      case CantOwnershipInterfaceInImpl(_) =>
    }
  }

  test("Reports when non-kind struct in impl") {
    val err = compileForError(
      """
        |struct Moo {}
        |interface IMoo {}
        |impl IMoo for &Moo;
        |""".stripMargin)
    err match {
      case CantOwnershipStructInImpl(_) =>
    }
  }
}
