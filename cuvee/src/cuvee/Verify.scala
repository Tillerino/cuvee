package cuvee

object Verify {
  var debug = false

  def refinement(A: Obj, C: Obj, sim: Sim, st: State, solver: Solver): (List[Expr], Expr) = {
    val (as, cs, defs, phi) = R(A, C, sim, st, solver)
    val cond = refinementCondition(A, C, as, cs, phi)
    (defs, cond)
  }

  def R(A: Obj, C: Obj, sim: Sim, st: State, solver: Solver) = sim match {
    case Sim.byFun(fun, recipe) =>
      val as = A.state
      val cs = C.state
      val phi = App(fun, as ++ cs)
      recipe match {
        case Nil =>
          (as, cs, Nil, phi)
        case recipes =>
          val synth = Refine(A, C, fun, st, solver)
          val defs = synth(recipes)
          (as, cs, defs, phi)
      }
    case Sim.byExpr(as, cs, phi) =>
      (as, cs, Nil, phi)
  }

  def refinementCondition(A: Obj, C: Obj, R: Id): Expr = {
    refinementCondition(A, C, A.state, C.state, App(R, A.state ++ C.state))
  }

  private def refinementCondition(A: Obj, C: Obj, as: List[Formal], cs: List[Formal], phi: Expr): Expr = {
    val (init, ops) = refine(A, as, C, cs, phi, phi)
    val diag = init :: ops
    val (_, conds) = diag.unzip
    And(conds)
  }

  def contract(proc: Proc, obj: Option[Obj]): Expr = {
    val Proc(in, out, pre, post, body) = proc

    body match {
      case None =>
        True
      case Some(Body(locals, progs)) =>
        Forall(
          obj.map(_.state).getOrElse(Nil) ++ in ++ out ++ locals,
          pre ==> WP(Block(progs, true, Some(obj)), post))
    }
  }

  def contract(obj: Obj): Expr = {
    val Obj(_, init, ops) = obj

    And(init :: ops.map(_._2) map (contract(_, Some(obj))))
  }

  def refine(A: Obj, as: List[Formal], C: Obj, cs: List[Formal], R0: Expr, R1: Expr) = {
    val ax: List[Id] = as
    val cx: List[Id] = cs
    val common = ax.toSet intersect cx.toSet
    ensure(common.isEmpty, s"state variable names must be disjoint but share: ${common.mkString(", ")}")

    val init = diagram(
      A, as, Id.init -> A.init,
      C, cs, Id.init -> C.init,
      True, R1)

    val ops = for ((aproc, cproc) <- (A.ops zip C.ops)) yield {
      diagram(
        A, as, aproc,
        C, cs, cproc,
        R0, R1)
    }

    (init, ops)
  }

  def diagram(
    A: Obj, as: List[Formal], aproc: (Id, Proc),
    C: Obj, cs: List[Formal], cproc: (Id, Proc),
    R0: Expr, R1: Expr): (Id, Expr) = {

    val (aop, ap) = aproc
    val (cop, cp) = cproc

    ensure(aop == cop, "mismatching operation", aop, cop)

    val Proc(ai, ao, _, _, _) = ap
    val Proc(ci, co, _, _, _) = cp

    val ci_ = ci map (_.prime)
    val co_ = co map (_.prime)

    val (apre, _, Body(alocals, abody)) = ap.call(A.state, as, ai, ao)
    val (cpre, _, Body(clocals, cbody)) = cp.call(C.state, cs, ci_, co_)

    // declare as primed := unprimed so that simplification will removed primed variables
    val in = Eq(ci_, ai)
    val out = Eq(co_, ao)

    val phi =
      ((in && apre && R0) ==>
        (cpre && WP(Block(cbody, false, Some(Some(C))), Dia(Block(abody, false, Some(Some(A))), out && R1))))

    (aop, Forall(
      as ++ ai ++ alocals ++ ao ++ cs ++ ci_ ++ clocals ++ co_,
      phi))
  }
}