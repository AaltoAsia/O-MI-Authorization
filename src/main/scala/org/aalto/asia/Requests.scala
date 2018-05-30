package org.aalto.asia.requests

import java.sql.Timestamp

import types.Path
object RequestType extends Enumeration {

  type RequestType = Value
  val Read, Write, Call, Delete = Value
}

sealed trait Request {
}

case class Read() extends Request {
  override def toString = "READ"
}

case class Write() extends Request {
  override def toString = "WRITE"
}

case class Call() extends Request {
  override def toString = "CALL"
}
case class Delete() extends Request {
  override def toString = "DELETE"
}
case class ReadWrite() extends Request {
  override def toString = "READ|WRITE"
}
case class ReadCall() extends Request {
  override def toString = "READ|CALL"
}
case class ReadDelete() extends Request {
  override def toString = "READ|DELETE"
}
case class WriteDelete() extends Request {
  override def toString = "WRITE|DELETE"
}
case class WriteCall() extends Request {
  override def toString = "WRITE|CALL"
}
case class CallDelete() extends Request {
  override def toString = "CALL|DELETE"
}
case class ReadWriteCall() extends Request {
  override def toString = "READ|WRITE|CALL"
}
case class ReadWriteDelete() extends Request {
  override def toString = "READ|WRITE|DELETE"
}
case class ReadCallDelete() extends Request {
  override def toString = "READ|CALL|DELETE"
}
case class WriteCallDelete() extends Request {
  override def toString = "WRITE|CALL|DELETE"
}
case class ReadWriteCallDelete() extends Request {
  override def toString = "READ|WRITE|CALL|DELETE"
}

import RequestType._
case class PermissionRequest(
  val username: String,
  val requestType: RequestType //Read,Call,Write,Delete
)

case class PermissionResult(
  allowed: Seq[Path],
  denied: Seq[Path])

case class AddRules(
  val group: String,
  val request: Request,
  val allow: Boolean,
  val paths: Seq[Path])
case class RemoveRule(
  val group: String,
  val allow: Boolean,
  val path: Path)

case class AddUser(val username: String)
case class RemoveUser(val username: String)

case class AddGroup(val groupname: String)
case class RemoveGroup(val groupname: String)

case class JoinGroup(val username: String, val groupname: String)
case class LeaveGroup(val username: String, val groupname: String)

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
      case "READ" => Read()
      case "WRITE" => Write()
      case "READ|WRITE" => ReadWrite()
      case "CALL" => Call()
      case "READ|CALL" => ReadCall()
      case "WRITE|CALL" => WriteCall()
      case "READ|WRITE|CALL" => ReadWriteCall()
      case "DELETE" => Delete()
      case "READ" => ReadDelete()
      case "WRITE|DELETE" => WriteDelete()
      case "READ|WRITE|DELETE" => ReadWriteDelete()
      case "CALL|DELETE" => CallDelete()
      case "READ|CALL|DELETE" => ReadCallDelete()
      case "WRITE|CALL|DELETE" => WriteCallDelete()
      case "READ|WRITE|CALL|DELETE" => ReadWriteCallDelete()
    }
  }
}
