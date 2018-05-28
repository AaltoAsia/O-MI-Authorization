package org.aalto.asia

import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.{ DefaultFormats, Formats }
import org.json4s._
import org.json4s.native
import org.aalto.asia.database.PermissionResult
import org.aalto.asia._
import types.Path

trait JsonSupport extends Json4sSupport {
  class PathSerializer extends CustomSerializer[Path](format =>
    ({ case JString(s) => Path(s) }, { case p => JString(p.toString) }))
  implicit val serialization = native.Serialization
  implicit val json4sFormats: Formats = DefaultFormats + new PathSerializer
}
//#json-support
