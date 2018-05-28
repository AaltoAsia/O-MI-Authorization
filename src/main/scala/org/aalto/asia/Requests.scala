
package org.aalto.asia

import java.sql.Timestamp

import types.Path
object RequestType extends Enumeration {

  type RequestType = Value
  val Read, Write, Call, Delete = Value
}
import RequestType._

case class PermissionRequest(
  val requestType: RequestType, //Read,Call,Write,Delete
  val roles: Set[String])

case class AddRule(
  val role: String,
  val allow: Boolean,
  val path: Path,
  val expire: Option[Timestamp])
case class RemoveRule(
  val role: String,
  val allow: Boolean,
  val path: Path,
  val expire: Option[Timestamp])
case class AddRole(
  val rolename: String,
  val expire: Option[Timestamp])
case class RemoveRole(
  val rolename: String,
  val expire: Option[Timestamp])
