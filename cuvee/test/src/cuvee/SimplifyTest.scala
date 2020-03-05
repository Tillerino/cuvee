package cuvee

import cuvee.test.TestSuite
import cuvee.testutils.Implicits._

object SimplifyTest extends TestSuite {
  test("remove from plus") {
    assertEquals(simplifyInt(e"(= (+ a b) (+ a c))"), e"(= b c)")
  }

  test("remove from minus") {
    assertEquals(simplifyInt(e"(= (- a b) (- a c))"), e"(= b c)")
  }

  test("remove from gt") {
    assertEquals(simplifyInt(e"(> (- a b) (- a c))"), e"(< b c)")
  }

  test("double minus") {
    assertEquals(simplifyInt(e"(= 0 (- (- x)))"), e"(= 0 x)")
  }

  test("flatten addition") {
    assertEquals(Simplify.norm(e"(= (+ balance (- 0 amount)) (+ credit (- 0 (+ debit amount))))"), e"(= balance (+ credit (- 0 debit)))")
  }

  private def simplifyInt(phi: Expr): Expr = {
    val formals = phi.free.map(Formal(_, Sort.int)).toList
    simplify(phi, formals)
  }

  private def simplify(phi: Expr, formals: List[Formal]) = {
    val solver = Solver.z3()
    solver.bind(formals)
    val simplifier = Simplify(solver)
    val simplified = simplifier(List(phi))
    assertEquals(simplified.size, 1)
    simplified.head
  }
}