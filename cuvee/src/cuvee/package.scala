import java.util.regex.Pattern

package object cuvee {
  import scala.language.implicitConversions
  import scala.io.StdIn
  import java.io.File
  import java.io.FileInputStream

  def error(info: Any*) = {
    throw Error(info)
  }

  def ensure(test: Boolean, info: Any*) = {
    if (!test)
      error(info: _*)
  }

  def ensure(test: Boolean, info: => String): Unit = {
    if (!test)
      error(info)
  }

  def unwrap[A](option: Option[A], info: Any*): A = option match {
    case None => error(info: _*)
    case Some(a) => a
  }

  val True = Id("true")
  val False = Id("false")
  val Skip = Block(Nil)

  implicit def toNum(value: Int) = Num(value)
  implicit def toExprs(pats: List[Pat]) = pats map (_.toExpr)
  implicit def toIds(formals: List[Formal]) = formals map (_.id)
  implicit def toTypes(formals: List[Formal]) = formals map (_.typ)
  implicit def toTyping(formals: List[Formal]): List[(Id, Type)] = formals map (f => (f.id, f.typ))
  implicit def toTypeMap(formals: List[Formal]): Map[Id, Type] = formals map (f => f.id -> f.typ) toMap

  def partition[A, B, C](as: List[A])(f: A => Either[B, C]): (List[B], List[C]) = {
    import scala.collection.mutable
    val left = mutable.ListBuffer[B]()
    val right = mutable.ListBuffer[C]()

    for (a <- as) {
      f(a) match {
        case Left(b) => left append b
        case Right(c) => right append c
      }
    }

    (left.toList, right.toList)
  }

  implicit class IntOps(self: Int) {
    def times(f: => Unit) = {
      for (i <- 0 until self) {
        f
      }
    }
  }

  implicit class StringOps(self: String) {
    def __(index: Option[Int]): String = index match {
      case None => self
      case Some(index) => self.toString + index
    }
  }

  implicit class SetOps[A](self: Set[A]) {
    def disjoint(that: Set[A]) = {
      (self & that).isEmpty
    }
  }

  implicit class IterableOps[A](self: Iterable[A]) {
    def _insert(a: A, as: List[(A, List[A])], eq: (A, A) => Boolean): List[(A, List[A])] = as match {
      case Nil => List((a, List(a)))
      case (b, bs) :: cs if eq(a, b) => (a, b :: bs) :: cs
      case c :: cs => c :: _insert(a, cs, eq)
    }

    def _classes(eq: (A, A) => Boolean): List[(A, List[A])] = {
      self.foldLeft(Nil: List[(A, List[A])]) {
        case (as, a) => _insert(a, as, eq)
      }
    }

    def classes(eq: (A, A) => Boolean) = {
      for ((_, as) <- _classes(eq) if as.length > 1)
        yield as
    }

    def duplicates(eq: (A, A) => Boolean) = {
      classes(eq).flatten
    }

    def hasDuplicates = {
      self.toSeq.distinct != self
    }
  }

  implicit class FileOps(file: File) {
    def text() = {
      val length = file.length
      val buf = new Array[Byte](length.toInt)
      val stream = new FileInputStream(file)
      val read = stream.read(buf)
      ensure(read == length, "short read", file)
      stream.close()
      new String(buf, "UTF-8")
    }
  }

  def ok = Pattern.compile(Parser.simplePattern)

  def mangle(id: Id) = {
    val Id(name, index) = id
    val ni = name __ index
    if (ok.matcher(ni).matches()) ni
    else "|" + ni + "|"
  }

  def sexpr(arg0: Any, args: Any*): String = {
    if (args.isEmpty) s"($arg0)"
    else s"($arg0 ${args.mkString(" ")})"
  }

  def sexpr(args: Iterable[Any]): String = {
    args.mkString("(", " ", ")")
  }

  implicit class FormalList(formals: List[Formal]) {
    def prime = formals map (_.prime)
    def priming = formals map (_.id) map (id => id -> id.prime) toMap
    def ids = toIds(formals)
    def types = toTypes(formals)
    def fresh = formals map (f => Formal(Expr.fresh(f.id), f.typ))
  }

  implicit class ExprList(exprs: List[Expr]) {
    def free: Set[Id] = exprs.flatMap(_.free).toSet
  }

  def time[A](f: => A) = {
    val start = System.currentTimeMillis
    val a = f
    val end = System.currentTimeMillis
    val t = end - start
    (t, a)
  }
}