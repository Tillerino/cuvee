package cuvee

case class Proc(in: List[Formal], out: List[Formal], pre: Expr, post: Expr, body: Prog) {
  def sig = (in map (_.typ), out map (_.typ))

  def call(ps: List[Formal], xs: List[Id]): (List[Formal], List[Formal], Expr, Expr, Prog) = {
    val (pre, post, body) = call(ps, xs, in, out)
    (in, out, pre, post, body)
  }

  def call(ps: List[Formal], xs: List[Id], xi: List[Id], xo: List[Id]): (Expr, Expr, Prog) = {
    val formals = ps ++ in ++ out
    val args = xs ++ xi ++ xo
    val re = Expr.subst(formals, args)
    (pre rename re, post rename re, body replace re)
  }
}

object Proc extends ((List[Formal], List[Formal], Prog, Option[Expr], Option[Expr]) => Proc) {
  def apply(in: List[Formal], out: List[Formal], body: Prog, pre: Option[Expr], post: Option[Expr]): Proc = {
    val _pre = pre.getOrElse(True)
    val _post = post.getOrElse(True)
    Proc(in, out, _pre, _post, body)
  }
}

case class Obj(state: List[Formal], init: Proc, ops: List[(Id, Proc)]) {
  def refine(that: Obj) = {
    import Obj.diagram

    val as = this.state
    val cs = that.state // map (_.prime)

    val R = Id("R")
    val Rxs = App(R, as ++ cs)

    val init = diagram(
      as, Id("init") -> this.init,
      cs, Id("init") -> that.init,
      True, Rxs)

    val ops = for ((aproc, cproc) <- (this.ops zip that.ops)) yield {
      diagram(
        as, aproc,
        cs, cproc,
        Rxs, Rxs)
    }

    (Rxs, as, cs, init, ops.toList)
  }
}

object Obj extends ((List[Formal], Proc, List[(Id, Proc)]) => Obj) {
  def diagram(
    as: List[Formal], aproc: (Id, Proc),
    cs: List[Formal], cproc: (Id, Proc),
    R0: Expr, R1: Expr): Expr = {

    val (aop, ap) = aproc
    val (cop, cp) = cproc

    val Proc(ai, ao, apre, apost, abody) = ap
    val Proc(ci, co, cpre, cpost, cbody) = cp

    val co_ = co map (_.prime)

    val pre = Eq(ai, ci)
    val post = Eq(ao, co_)

    Forall(
      as ++ ai ++ ao ++ cs ++ ci ++ co_,
      pre ==>
        (apre && R0) ==>
        (cpre && WP(cbody, Dia(abody, post && R1))))
  }
}