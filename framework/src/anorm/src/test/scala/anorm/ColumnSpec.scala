package anorm

import java.sql.{ Array => SqlArray }
import javax.sql.rowset.serial.SerialClob
import java.math.BigInteger

import acolyte.{ QueryResult, ImmutableArray }
import acolyte.RowLists.{
  bigDecimalList,
  byteList,
  dateList,
  doubleList,
  floatList,
  intList,
  stringList,
  longList,
  rowList1,
  shortList,
  timeList,
  timestampList
}
import acolyte.Acolyte.{ connection, handleQuery }
import acolyte.Implicits._

import SqlParser.{ byte, double, float, scalar, short }

object ColumnSpec extends org.specs2.mutable.Specification {
  "Column" title

  val bd = new java.math.BigDecimal("34.5679")
  val bi = new java.math.BigInteger("1234")
  val clob = new SerialClob(Array[Char]('a', 'b', 'c', 'd', 'e', 'f'))

  "Column mapped as Char" should {
    "be parsed from string" in withQueryResult(stringList :+ "abc") {
      implicit con =>
        SQL("SELECT c").as(scalar[Char].single) aka "parsed char" must_== 'a'
    }

    "be parsed from clob" in withQueryResult(
      rowList1(classOf[SerialClob]) :+ clob) { implicit con =>
        SQL("SELECT c").as(scalar[Char].single) aka "parsed char" must_== 'a'
      }
  }

  "Column mapped as double" should {
    "be parsed from big decimal" in withQueryResult(bigDecimalList :+ bd) {
      implicit con =>
        SQL("SELECT bg").as(scalar[Double].single).
          aka("parsed double") must_== 34.5679d
    }

    "be parsed from double" in withQueryResult(doubleList :+ 1.2d) {
      implicit con =>
        SQL("SELECT d").as(scalar[Double].single).
          aka("parsed double") must_== 1.2d
    }

    "be parsed from float" in withQueryResult(floatList :+ 2.3f) {
      implicit con =>
        SQL("SELECT f").as(scalar[Double].single).
          aka("parsed double") must_== 2.3d
    }

    "be parsed from big integer" in withQueryResult(
      rowList1(classOf[java.math.BigInteger]) :+ bi) { implicit con =>
        SQL("SELECT bi").as(scalar[Double].single).
          aka("parsed double") must_== 1234d
      }

    "be parsed from integer" in withQueryResult(intList :+ 2) {
      implicit con =>
        SQL("SELECT i").as(scalar[Double].single) aka "parsed double" must_== 2d
    }

    "be parsed from short" in withQueryResult(shortList :+ 3.toShort) {
      implicit con =>
        SQL("SELECT s").as(scalar[Double].single).
          aka("parsed double") must_== 3.toShort
    }

    "be parsed from byte" in withQueryResult(byteList :+ 4.toByte) {
      implicit con =>
        SQL("SELECT s").as(scalar[Double].single).
          aka("parsed double") must_== 4.toByte
    }

    "have convinence mapping function" in withQueryResult(
      rowList1(classOf[Double] -> "d") :+ 1.2d) { implicit con =>

        SQL("SELECT d").as(double("d").single) aka "parsed double" must_== 1.2d
      }
  }

  "Column mapped as short" should {
    "be parsed from short" in withQueryResult(shortList :+ 3.toShort) {
      implicit con =>
        SQL("SELECT s").as(scalar[Short].single).
          aka("parsed short") must_== 3.toShort
    }

    "be parsed from byte" in withQueryResult(byteList :+ 4.toByte) {
      implicit con =>
        SQL("SELECT b").as(scalar[Short].single).
          aka("parsed short") must_== 4.toShort
    }

    "have convinence mapping function" in withQueryResult(
      rowList1(classOf[Short] -> "s") :+ 6.toShort) { implicit con =>

        SQL("SELECT s").as(short("s").single).
          aka("parsed short") must_== 6.toShort
      }
  }

  "Column mapped byte" should {
    "be parsed from short" in withQueryResult(shortList :+ 3.toShort) {
      implicit con =>
        SQL("SELECT s").as(scalar[Byte].single).
          aka("parsed byte") must_== 3.toByte
    }

    "be parsed from byte" in withQueryResult(byteList :+ 4.toByte) {
      implicit con =>
        SQL("SELECT b").as(scalar[Byte].single).
          aka("parsed byte") must_== 4.toByte
    }

    "have convinence mapping function" in withQueryResult(
      rowList1(classOf[Byte] -> "b") :+ 6.toByte) { implicit con =>

        SQL("SELECT b").as(byte("b").single).
          aka("parsed byte") must_== 6.toByte
      }
  }

  "Column mapped as float" should {
    "be parsed from float" in withQueryResult(floatList :+ 2.3f) {
      implicit con =>
        SQL("SELECT f").as(scalar[Float].single).
          aka("parsed float") must_== 2.3f
    }

    "be parsed from big integer" in withQueryResult(
      rowList1(classOf[java.math.BigInteger]) :+ bi) { implicit con =>
        SQL("SELECT bi").as(scalar[Float].single).
          aka("parsed float") must_== 1234f
      }

    "be parsed from integer" in withQueryResult(intList :+ 2) {
      implicit con =>
        SQL("SELECT i").as(scalar[Float].single) aka "parsed float" must_== 2f
    }

    "be parsed from short" in withQueryResult(shortList :+ 3.toShort) {
      implicit con =>
        SQL("SELECT s").as(scalar[Float].single) aka "parsed float" must_== 3f
    }

    "be parsed from byte" in withQueryResult(byteList :+ 4.toByte) {
      implicit con =>
        SQL("SELECT s").as(scalar[Float].single) aka "parsed double" must_== 4f
    }

    "have convinence mapping function" in withQueryResult(
      rowList1(classOf[Float] -> "f") :+ 1.23f) { implicit con =>

        SQL("SELECT f").as(float("f").single) aka "parsed byte" must_== 1.23f
      }
  }

  "Column mapped as Java big decimal" should {
    "be parsed from big decimal" in withQueryResult(
      bigDecimalList :+ bd) { implicit con =>

        SQL("SELECT bd").as(scalar[java.math.BigDecimal].single).
          aka("parsed big decimal") must_== bd
      }

    "be parsed from double" in withQueryResult(doubleList :+ 1.35d) {
      implicit con =>
        SQL("SELECT bd").as(scalar[java.math.BigDecimal].single).
          aka("parsed double") must_== java.math.BigDecimal.valueOf(1.35d)

    }

    "be parsed from long" in withQueryResult(longList :+ 5l) {
      implicit con =>
        SQL("SELECT bd").as(scalar[java.math.BigDecimal].single).
          aka("parsed long") must_== java.math.BigDecimal.valueOf(5l)

    }
  }

  "Column mapped as Scala big decimal" should {
    "be parsed from big decimal" in withQueryResult(
      bigDecimalList :+ bd) { implicit con =>

        SQL("SELECT bd").as(scalar[BigDecimal].single).
          aka("parsed big decimal") must_== BigDecimal(bd)
      }

    "be parsed from double" in withQueryResult(doubleList :+ 1.35d) {
      implicit con =>
        SQL("SELECT bd").as(scalar[BigDecimal].single).
          aka("parsed double") must_== BigDecimal(1.35d)

    }

    "be parsed from long" in withQueryResult(longList :+ 5l) {
      implicit con =>
        SQL("SELECT bd").as(scalar[BigDecimal].single).
          aka("parsed long") must_== BigDecimal(5l)

    }
  }

  "Column mapped as Java big integer" should {
    "be parsed from big integer" in withQueryResult(
      rowList1(classOf[java.math.BigInteger]) :+ bi) { implicit con =>
        // Useless as proper resultset won't return BigInteger

        SQL("SELECT bi").as(scalar[java.math.BigInteger].single).
          aka("parsed big integer") must_== bi
      }

    "be parsed from long" in withQueryResult(longList :+ 5l) {
      implicit con =>
        SQL("SELECT bi").as(scalar[java.math.BigInteger].single).
          aka("parsed long") must_== java.math.BigInteger.valueOf(5l)

    }

    "be parsed from int" in withQueryResult(intList :+ 2) {
      implicit con =>
        SQL("SELECT bi").as(scalar[java.math.BigInteger].single).
          aka("parsed int") must_== java.math.BigInteger.valueOf(2)

    }
  }

  "Column mapped as Scala big integer" should {
    "be parsed from big integer" in withQueryResult(
      rowList1(classOf[java.math.BigInteger]) :+ bi) { implicit con =>
        // Useless as proper resultset won't return BigInteger

        SQL("SELECT bi").as(scalar[BigInt].single).
          aka("parsed big integer") must_== BigInt(bi)
      }

    "be parsed from long" in withQueryResult(longList :+ 5l) {
      implicit con =>
        SQL("SELECT bi").as(scalar[BigInt].single).
          aka("parsed long") must_== BigInt(5l)

    }

    "be parsed from int" in withQueryResult(intList :+ 2) {
      implicit con =>
        SQL("SELECT bi").as(scalar[BigInt].single).
          aka("parsed int") must_== BigInt(2)

    }
  }

  "Column mapped as date" should {
    val time = System.currentTimeMillis

    "be parsed from date" in withQueryResult(
      dateList :+ new java.sql.Date(time)) { implicit con =>
        SQL("SELECT d").as(scalar[java.util.Date].single).
          aka("parsed date") must_== new java.util.Date(time)
      }

    "be parsed from timestamp" in withQueryResult(
      timestampList :+ new java.sql.Timestamp(time)) { implicit con =>
        SQL("SELECT ts").as(scalar[java.util.Date].single).
          aka("parsed date") must beLike {
            case d => d.getTime aka "time" must_== time
          }
      }

    "be parsed from time" in withQueryResult(longList :+ time) { implicit con =>
      SQL("SELECT time").as(scalar[java.util.Date].single).
        aka("parsed date") must_== new java.util.Date(time)

    }
  }

  "Column mapped as array" should {
    val array = ImmutableArray.
      getInstance(classOf[String], Array("aB", "Cd", "EF"))

    "be parsed from array" in withQueryResult(
      rowList1(classOf[SqlArray]) :+ array) { implicit con =>

      SQL"SELECT a".as(scalar[Array[String]].single).
        aka("parsed array") mustEqual Array("aB", "Cd", "EF")
    }

    "not be parsed from array with invalid component type" in withQueryResult(
      rowList1(classOf[SqlArray]) :+ acolyte.ImmutableArray.getInstance(
        classOf[java.sql.Date], Array(new java.sql.Date(1l), 
          new java.sql.Date(2l)))) { implicit con =>

      SQL"SELECT a".as(scalar[Array[String]].single).
        aka("parsing") must throwA[Exception](message = 
          "TypeDoesNotMatch\\(Cannot convert ImmutableArray")

    }

    "not be parsed from float" in withQueryResult(floatList :+ 2f) {
      implicit con =>
      SQL"SELECT a".as(scalar[Array[String]].single).
        aka("parsing") must throwA[Exception](message =
          "TypeDoesNotMatch\\(Cannot convert.* to array")
    }

    "be parsed from array with integer to big integer convertion" in {
      withQueryResult(rowList1(classOf[SqlArray]) :+ ImmutableArray.getInstance(
        classOf[Integer], Array[Integer](1, 3))) { implicit con =>

        SQL"SELECT a".as(scalar[Array[BigInteger]].single).
          aka("parsed array") mustEqual Array(
            BigInteger.valueOf(1), BigInteger.valueOf(3))
      }
    }
  }

  "Column mapped as list" should {
    val array = ImmutableArray.
      getInstance(classOf[String], Array("aB", "Cd", "EF"))

    "be parsed from array" in withQueryResult(
      rowList1(classOf[SqlArray]) :+ array) { implicit con =>

      SQL"SELECT a".as(scalar[List[String]].single).
        aka("parsed list") mustEqual List("aB", "Cd", "EF")
    }

    "not be parsed from array with invalid component type" in withQueryResult(
      rowList1(classOf[SqlArray]) :+ acolyte.ImmutableArray.getInstance(
        classOf[java.sql.Date], Array(new java.sql.Date(1l), 
          new java.sql.Date(2l)))) { implicit con =>

      SQL"SELECT a".as(scalar[List[String]].single).
        aka("parsing") must throwA[Exception](message = 
          "TypeDoesNotMatch\\(Cannot convert ImmutableArray")

    }

    "not be parsed from float" in withQueryResult(floatList :+ 2f) {
      implicit con =>
      SQL"SELECT a".as(scalar[List[String]].single).
        aka("parsing") must throwA[Exception](message =
          "TypeDoesNotMatch\\(Cannot convert.* to list")
    }

    "be parsed from array with integer to big integer convertion" in {
      withQueryResult(rowList1(classOf[SqlArray]) :+ ImmutableArray.getInstance(
        classOf[Integer], Array[Integer](1, 3))) { implicit con =>

        SQL"SELECT a".as(scalar[List[BigInteger]].single).
          aka("parsed list") mustEqual List(
            BigInteger.valueOf(1), BigInteger.valueOf(3))
      }
    }
  }

  def withQueryResult[A](r: QueryResult)(f: java.sql.Connection => A): A =
    f(connection(handleQuery { _ => r }))

}
