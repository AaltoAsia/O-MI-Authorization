package org.aalto.asia

import akka.actor.ActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.ExtendedActorSystem
import scala.concurrent.duration.Duration
import com.typesafe.config.Config
import java.util.concurrent.TimeUnit

class AuthConfigSettings(config: Config) extends Extension {
  val interface: String = config.getString("o-mi-authorization.bindInterface")
  val port: Int = config.getInt("o-mi-authorization.bindPort")

}
object Settings extends ExtensionId[AuthConfigSettings] with ExtensionIdProvider {

  override def lookup = Settings

  override def createExtension(system: ExtendedActorSystem) =
    new AuthConfigSettings(system.settings.config)

  /**
   * Java API: retrieve the Settings extension for the given system.
   */
  override def get(system: ActorSystem): AuthConfigSettings = super.get(system)

}
