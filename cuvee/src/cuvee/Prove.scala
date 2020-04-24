package cuvee

case class Prove(backend: Solver) {
  import Simplify._

  def apply(phi: Expr, st: State): Expr = {
    prove(phi, st)
  }

  def prove(todo: List[Expr], neg: Boolean, st: State): List[Expr] = todo match {
    case Nil =>
      Nil
    case phi :: rest =>
      val _phi = prove(phi, st)
      val __phi = if (neg) !_phi else _phi
      val _rest = backend.asserting(__phi) { prove(rest, neg, st) }
      _phi :: _rest
  }

  def prove(phi: Expr, st: State): Expr = phi match {
    case t@(True | False) => t
    case Imp(phi, psi) =>
      val _psi = backend.asserting(phi) {
        prove(psi, st)
      }
      imp(phi, _psi)
    case And(args) =>
      val _args = prove(args, neg = false, st)
      and(_args)
    case f@Forall(bound, _) =>
      // we only want to get fresh names for unused variable names or this turns unreadable
      val fresh = Expr.fresh(bound.ids.toSet & st.constants.ids.toSet)
      val Forall(bound_, body_) = f.rename(fresh, fresh)
      val _body = backend.binding(bound_) {
        prove(body_, st.declareConstants(bound_))
      }
      forall(bound_, _body)

    case Eq(left, right)
      // since we never have local variables, we do a type check based on globals
      if Check.infer(left, Map.empty, st, None) == Sort.bool
        && Check.infer(right, Map.empty, st, None) == Sort.bool =>
      prove(And(left ==> right, right ==> left), st)
    case App(Id("iff", None), List(left, right)) =>
      prove(And(left ==> right, right ==> left), st)

    case App(r, args) if (st.fundefs contains r)
      // do not substitute recursive functions
      && !((st.fundefs(r)._2.free) contains r) =>
      val (formals, body) = st.fundefs(r)
      prove(body.subst(Expr.subst(formals, args)), st)

    case _ if backend isTrue phi =>
      True
    case _ =>
      phi
  }
}