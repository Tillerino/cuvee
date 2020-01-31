package cuvee

import java.io.File
import cuvee.test.TestSuite
import cuvee.testutils.Implicits._

object VerifyTest extends TestSuite {
  test("cannot declare duplicate procedure parameters") {
    try {
      Verify.checkProc("my-proc", Proc(List(Formal(Id("x"), Sort("Int")), Formal(Id("x"), Sort("Int"))), List(), True, Block(List())))
      fail("should have thrown an error")
    } catch {
      case e: Error => assertEquals(e.getMessage, "The method my-proc declares duplicate input parameters x")
    }
  }

  test("simple refinement verification condition") {
    val aState: List[Formal] = List(("counter", "Int"))
    var st = State.default.define("abstract-counter", aState, List(
      (id("init"), Proc(List(), List(), True, "counter" := 0)),
      (id("increment"), Proc(List(), List(), True, "counter" := (Id("counter") + 1))),
      (id("get-count"), Proc(List(), List(("count", "Int")), True, "count" := "counter"))
    ))

    val cState: List[Formal] = List(("counter", "Int"))
    st = st.define("concrete-counter", cState, List(
      (id("init"), Proc( List(), List(), True, "counter" := 0)),
      (id("increment"), Proc(List(), List(), True, "counter" := ("counter" - 1))),
      (id("get-count"), Proc(List(), List(("count", "Int")), True, "count" := (Num(0) - "counter")))
    ))

    val rel = DefineRefinement(("as", "abstract-counter"), ("cs", "concrete-counter"), "as_counter" === ((Num(0) - "cs_counter")))
    val vcs = Verify.verificationConditions(rel, st)

    // init
    assertEquals(vcs head, (And(List()) && And(List())) ==> (Num(0) === Num(0) - 0))

    // increment
    assertEquals(vcs(1), Forall(List(("as_counter", "Int"), ("cs_counter", "Int")),
      ("as_counter" === Num(0) - "cs_counter") // rel
        ==> (True // aPre
        ==> (True // cPre
        && (True // insEq
        ==> (True // outsEq
        && (Id("as_counter") + 1 === Num(0) - ("cs_counter" - 1)))))))) // rel

    // get-count
    assertEquals(vcs(2), Forall(List(("as_counter", "Int"), ("cs_counter", "Int")),
      ("as_counter" === Num(0) - "cs_counter") // rel
        ==> (True // aPre
        ==> (True // cPre
        && (True // insEq
        ==> (("as_counter" === Num(0) - "cs_counter") // outsEq
        && (Id("as_counter") === Num(0) - "cs_counter"))))))) // rel
  }

  test("refinement where inlining of post-relation class members does not work") {
    Expr._index = 0 // reset since we're testing against a specific index

    val aState: List[Formal] = List(("counter", "Int"))
    var st = State.default.define("abstract-counter", aState, List(
      (id("init"), Proc(List(), List(), True, "counter" := 0)),
      (id("increment"), Proc(List(), List(), True, "counter" := (Id("counter") + 1)))
    ))

    val cState: List[Formal] = List(("counter", "Int"))
    st = st.define("concrete-counter", cState, List(
      (id("init"), Proc(List(), List(), True, "counter" := 0)),
      (id("increment"), Proc(List(), List(), True, Spec(List("counter"), True, "counter" === (Old("counter") + 1))))
    ))

    val rel = DefineRefinement(("as", "abstract-counter"), ("cs", "concrete-counter"), "as_counter" === ((Num(0) - "cs_counter")))
    val vcs = Verify.verificationConditions(rel, st)

    assertEquals(vcs(1), Forall(List(("as_counter", "Int"), ("cs_counter", "Int")),
      ("as_counter" === Num(0) - "cs_counter") // rel
        ==> (True // aPre
        ==> (True // cPre
        && (True // insEq
        ==> (True // outsEq
        && Forall(List(("cs_counter1", "Int")), ("cs_counter1" === Id("cs_counter") + 1) ==> (True && Id("as_counter") + 1 === Num(0) - "cs_counter1")))))))) // rel
  }

  test("refinement for account") {
    val aState: List[Formal] = List(("balance", "Int"))
    var st = State.default.define("simple-account", aState, List(
      (id("init"), Proc(List(), List(), True, "balance" := 0)),
      (id("deposit"), Proc(List(("amount", "Int")), List(("new-balance", "Int")), True, Block(List("balance" := (Id("balance") + "amount"), "new-balance" := "balance")), Id("amount") > 0)),
      (id("withdraw"), Proc(List(("amount", "Int")), List(("new-balance", "Int")), True, Block(List("balance" := (Id("balance") - "amount"), "new-balance" := "balance")), Id("amount") > 0 && Id("amount") <= "balance")))
    )

    val cState: List[Formal] = List(("debit", "Int"), ("credit", "Int"), ("max-overdraft", "Int"))
    st = st.define("double-account", cState: List[Formal], List(
      (id("init"), Proc(List(("maximum-overdraft", "Int")), List(), True, Block(List("debit" := 0, "credit" := 0, "max-overdraft" := "maximum-overdraft")), Id("maximum-overdraft") >= 0)),
      (id("deposit"), Proc(List(("amount", "Int")), List(("new-balance", "Int")), True, Block(List("credit" := (Id("credit") + "amount"), "new-balance" := Id("credit") - "debit")), Id("amount") > 0)),
      (id("withdraw"), Proc(List(("amount", "Int")), List(("new-balance", "Int")), True, Block(List("debit" := (Id("debit") + "amount"), "new-balance" := Id("credit") - "debit")), Id("amount") > 0 && Id("amount") <= Id("credit") - "debit" + "max-overdraft")))
    )

    val rel = DefineRefinement(("as", "simple-account"), ("cs", "double-account"), "as_balance" === Id("cs_credit") - "cs_debit")
    val vcs = Verify.verificationConditions(rel, st)

    vcs.foreach(vc => println(PrettyPrinter.printExpr(vc)))
  }

  for (file <- List(
    "examples/verifications/abs-proc.smt2",
    "examples/verifications/gcd-proc.smt2",
    "examples/verifications/zero-proc.smt2",
    "examples/refinements/accounts.smt2")) {
    test("verify " + file) {
      Expr._index = 0
      val in = new File(file)
      val source: Source = runUnwrappingErrors(Verify(Source.file(in).cmds))

      println(s"Verification condition for $file")
      println()
      Cuvee.run(source, Solver.stdout, (_: Res) => { })
      println()

      val report = prove_!
      val solver = Solver.z3()
      Cuvee.run(source, solver, report)
    }
  }

  test("absolute function verification condition") {
    val verificationCondition = Verify.verificationCondition(ParserTest.abs, State.default, None)
    assertEquals(verificationCondition, Forall(List(Formal(Id("x"), Sort("Int"))), True ==> ((Id("x") < 0 ==> 0 - Id("x") >= 0) && !(Id("x") < 0) ==> Id("x") >= 0)))
  }

  def runUnwrappingErrors[A](fun: => A): A = {
    try {
      fun
    } catch {
      case any: Throwable =>
        printToStringStackTrace(any)
        throw any
    }
  }

  @scala.annotation.tailrec
  def printToStringStackTrace(t: Throwable): Unit = {
    println(t)
    if (t.getCause != null && t.getCause != t) {
      printToStringStackTrace(t.getCause)
    }
  }
}