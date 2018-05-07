package org.aalto.asia.database

import java.sql.Timestamp

import scala.util.{ Try, Success, Failure }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.collection.mutable.{ Map => MutableMap, HashMap => MutableHashMap }
import scala.language.postfixOps

import org.slf4j.LoggerFactory
import slick.driver.H2Driver.api._
import slick.jdbc.meta.MTable

import slick.backend.DatabaseConfig
import slick.driver.H2Driver.api._
import slick.driver.JdbcProfile
import slick.lifted.{ Index, ForeignKeyQuery, ProvenShape }
import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.immutable.BitSet.BitSet1

import types.Path
//USER|REQUEST|attributes|"ALLOW"or"DENY"|PATH
object Requests {
  type RequestFlag = BitSet1
}
import Requests._

case class AuthEntry(
  val roleID: Long,
  val request: RequestFlag, //Bit flag
  val allow: Boolean,
  val path: Path)

trait DBBase {
  val dc: DatabaseConfig[JdbcProfile] //= DatabaseConfig.forConfig[JdbcProfile](database.dbConfigName)
  import dc.driver.api._
  val db: Database
  //protected[this] val db: Database
}

trait Tables extends DBBase {
  import dc.driver.api._
  type DBSIOro[Result] = DBIOAction[Seq[Result], Streaming[Result], Effect.Read]
  type DBIOro[Result] = DBIOAction[Result, NoStream, Effect.Read]
  type DBIOwo[Result] = DBIOAction[Result, NoStream, Effect.Write]
  type DBIOsw[Result] = DBIOAction[Result, NoStream, Effect.Schema with Effect.Write]
  type ReadWrite = Effect with Effect.Write with Effect.Read with Effect.Transactional
  type DBIOrw[Result] = DBIOAction[Result, NoStream, ReadWrite]

  implicit lazy val pathColumnType = MappedColumnType.base[Path, String](
    { p: Path => p.toString },
    { str: String => Path(str) } // String to Path
  )
  implicit val requestColumnType = MappedColumnType.base[RequestFlag, Int](
    { p: RequestFlag => p.toVector.headOption.getOrElse(0) },
    { i: Int => new RequestFlag(i) } // String to Path
  )
  class AuthorizationTable(tag: Tag) extends Table[AuthEntry](tag, "AUTHENTRIES") {
    def roleid: Rep[Long] = column[Long]("ROLEID")
    def request: Rep[RequestFlag] = column[RequestFlag]("REQUEST")
    def path: Rep[Path] = column[Path]("PATH")
    def allow: Rep[Boolean] = column[Boolean]("ALLOW_OR_DENY")
    def roleIndex = index("ROLEINDEX", roleid, unique = false)
    def roleRequestIndex = index("ROLEREQUESTINDEX", (roleid, request), unique = false)
    def * = (roleid, request, allow, path) <> (AuthEntry.tupled, AuthEntry.unapply)
  }
}
