package org.aalto.asia.requests

import java.sql.Timestamp

import org.aalto.asia.types.Path
object RequestType extends Enumeration {

  type RequestType = Value
  val Read, Write, Call, Delete = Value
}

sealed trait Request {
}

case class Read() extends Request {
  override def toString = "r"
}

case class Write() extends Request {
  override def toString = "w"
}

case class Call() extends Request {
  override def toString = "c"
}
case class Delete() extends Request {
  override def toString = "d"
}
case class ReadWrite() extends Request {
  override def toString = "rw"
}
case class ReadCall() extends Request {
  override def toString = "rc"
}
case class ReadDelete() extends Request {
  override def toString = "rd"
}
case class WriteDelete() extends Request {
  override def toString = "wd"
}
case class WriteCall() extends Request {
  override def toString = "wc"
}
case class CallDelete() extends Request {
  override def toString = "cd"
}
case class ReadWriteCall() extends Request {
  override def toString = "rwc"
}
case class ReadWriteDelete() extends Request {
  override def toString = "rwd"
}
case class ReadCallDelete() extends Request {
  override def toString = "rcd"
}
case class WriteCallDelete() extends Request {
  override def toString = "wcd"
}
case class ReadWriteCallDelete() extends Request {
  override def toString = "rwcd"
}

import RequestType._
case class GetPermissions(
  val username: String,
  val requestType: RequestType //Read,Call,Write,Delete
)

case class PermissionResult(
  allowed: Set[Path],
  denied: Set[Path])

case class Rule(path: Path, request: Request, allow: Boolean)
case class SetRules(
  val group: String,
  val rules: Seq[Rule])
case class RemoveRule(
  val group: String,
  val path: Path)

case class AddUser(val username: String)
case class RemoveUser(val username: String)

case class AddGroup(val groupname: String)
case class RemoveGroup(val groupname: String)

case class JoinGroups(val username: String, val groups: Set[String])
case class LeaveGroups(val username: String, val groups: Set[String])

object Request {
  def apply(requestType: RequestType): Request = {
    requestType match {
      case RequestType.Read => Read()
      case RequestType.Write => Write()
      case RequestType.Call => Call()
      case RequestType.Delete => Delete()
    }
  }
  def apply(str: String): Request = {
    str match {
      case "r" => Read()
      case "w" => Write()
      case "rw" => ReadWrite()
      case "c" => Call()
      case "rc" => ReadCall()
      case "wc" => WriteCall()
      case "rwc" => ReadWriteCall()
      case "d" => Delete()
      case "rd" => ReadDelete()
      case "wd" => WriteDelete()
      case "rwd" => ReadWriteDelete()
      case "cd" => CallDelete()
      case "rcd" => ReadCallDelete()
      case "wcd" => WriteCallDelete()
      case "rwcd" => ReadWriteCallDelete()
    }
  }
}
