package cuvee

import cuvee.State.default
import cuvee.test.TestSuite
import cuvee.testutils.Implicits._
import minitest.api.{AssertionException, SourceLocation}

import scala.collection.immutable.Map.empty

object CheckTest extends TestSuite {
  import Sort.bool
  import Sort.int

  // local copies for convenience
  def infer(expr: Expr, ty: Map[Id, Type] = Map.empty, st: State = State.default): Type = Check(st).infer(expr, ty)
  def checkProg(prog: Prog, ty: Map[Id, Type] = Map.empty, st: State = State.default): Unit = Check(st).checkProg(prog, ty, false)

    test("Standard constants") {
    assertEquals(infer(e"true"), bool)
    assertEquals(infer(e"false"), bool)
    assertEquals(infer(e"0"), int)

    assertError(infer(e"whatever"), "unknown identifier")
  }

  test("Function applications") {
    assertEquals(infer(e"(+ 1 1)"), int)
    assertEquals(infer(e"(and true false)"), bool)
    assertEquals(infer(e"(or (and true false) true)"), bool)

    assertError(infer(e"(whatever 1 true)"), "unknown function")
    assertError(infer(e"(and true 1)"), "signature")
    assertError(infer(e"(or (and true 1) true)"), "signature")
    assertError(infer(e"(or (and true false) 1)"), "signature")
  }

  test("Weakest precondition in bind with procedure call") {
    val st = default.define("int-bool-proc", Proc(List(Formal("in", int)), List(Formal("out", bool)), True, True, "out" := True))

    assertEquals(infer(e"(forall ((x Int) (y Bool)) (wp (call int-bool-proc (x) (y)) (= y true)))", st = st), Sort.bool)

    assertError(infer(e"(forall ((x Int) (y Bool)) (wp (call int-bool-proc (x) (z)) (= y true)))", st = st), "unknown identifier")

    assertError(infer(e"(forall ((x Int) (y Bool)) (wp (call int-bool-proc (x) (x)) (= y true)))", st = st), "signature")

    assertError(infer(e"(forall ((x Int) (y Bool)) (wp (call int-bool-proc (y) (y)) (= y true)))", st = st), "signature")

    assertError(infer(e"(forall ((x Int) (y Bool)) (wp (call int-bool-proc (x) (y)) x))", st = st), "post-condition")
  }

  test("Break") {
    assertError(checkProg(p"(block (break))"), "break");

    checkProg(p"(while true (break))");
  }

  test("Let") {
    assertEquals(infer(e"(let ((x true)) (x))"), bool)

    assertEquals(infer(e"(let ((x y)) (x))", Map(Id("y") -> bool), default), bool)

    assertError(infer(e"(let ((x y)) (x))"), "unknown identifier")
  }

  test("Spec") {
    val xDef = Map(Id("x") -> int)

    checkProg(p"(spec (x) (> x 0) (<= x 0))", xDef)

    assertError(checkProg(p"(spec (x) (> x 0) (<= x 0))"), "unknown")
    assertError(checkProg(p"(spec (x) (> x 0) (<= x true))", xDef), "signature")
    assertError(checkProg(p"(spec (x) (> x false) (<= x 0))", xDef), "signature")
  }

  test("Infer errors are presented as stack") {
    assertError(infer(e"(ite (> 0 (and x y)) 1 0)", List("x", "y").map(s => Formal(id(s), Sort.int))),
      "arguments for and do not match function signature. Expected (Bool, Bool) but was (Int, Int)\n" +
        "(and x y)\n" +
        "(> 0 (and x y))\n" +
        "(ite (> 0 (and x y)) 1 0)")
  }

  test("Check prog errors are presented as stack") {
    assertError(checkProg(p"(if (> 0 x) (assign (y true)))", List("x", "y").map(s => Formal(id(s), Sort.int))),
      "value of type Bool cannot be assigned to variable y of type Int\n" +
        "(assign ((y true)))\n" +
        "(if (> 0 x) (assign ((y true))) (block))")
  }

  private def assertError[T](fn: => T, message: String)(implicit pos: SourceLocation): Unit = {
    try {
      fn
      throw new AssertionException("Expected an error", pos)
    } catch {
      case e: AssertionException => throw e
      case e: Exception => if (e.getMessage == null || !e.getMessage.contains(message))
        throw new AssertionException(s"expected message containing '$message' but was '${e.getMessage}'", pos)
    }
  }
}
