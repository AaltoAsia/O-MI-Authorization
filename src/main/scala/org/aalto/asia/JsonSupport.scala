package org.aalto.asia

import org.aalto.asia.UserRegistryActor.ActionPerformed

//#json-support
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import spray.json._
import org.aalto.asia.database.PermissionResult
import types.Path

trait JsonSupport extends SprayJsonSupport {
  // import the default encoders for primitive types (Int, String, Lists etc)
  import DefaultJsonProtocol._
  implicit object PathJsonFormat extends RootJsonFormat[Path] {
    def write(path: Path) = { JsString(path.toString) }
    def read(value: JsValue) = {
      value match {
        case JsString(strPath) => Path(strPath)
        case _ => deserializationError("Path expected")
      }
    }
  }

  implicit val userJsonFormat = jsonFormat3(User)
  implicit val usersJsonFormat = jsonFormat1(Users)

  implicit val actionPerformedJsonFormat = jsonFormat1(ActionPerformed)
  implicit val permissionResultJsonFormat = jsonFormat3(PermissionResult)
}
//#json-support
