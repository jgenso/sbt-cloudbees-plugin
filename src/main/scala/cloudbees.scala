package cloudbees

import sbt._, Keys._, Project.Initialize
import com.cloudbees.api.{BeesClient,HashWriteProgress}

object Plugin extends Plugin {
  import CloudBees._

  case class Client(host: String, key: Option[String], secret: Option[String]){
    def apply() = {
      val k = require(key, apiKey)
      val s = require(secret, apiSecret)
      new BeesClient("https://%s/api".format(host), k, s, "xml", "1.0")
    }
  }

  object CloudBees {
    // settings
    val host          = SettingKey[String]("cloudbees-host", "Host URL of the CloudBees API")
    val useDeltaWar   = SettingKey[Boolean]("cloudbees-use-delta-war", "Deploy only a delta-WAR to CloudBees (default: true)")
    val username      = SettingKey[Option[String]]("cloudbees-username", "Your CloudBees username")
    val apiKey        = SettingKey[Option[String]]("cloudbees-api-key", "Your CloudBees API key")
    val apiSecret     = SettingKey[Option[String]]("cloudbees-api-secrect", "Your CloudBees API secret")
    val applicationId = SettingKey[Option[String]]("cloudbees-application-id", "The application identifier of the deploying project")
    val client        = SettingKey[Client]("cloudbees-client")
    // tasks
    val applications  = TaskKey[Unit]("cloudbees-applications")
    val deploy        = TaskKey[Unit]("cloudbees-deploy")
  }

  import com.github.siasia.WarPlugin._
  import com.github.siasia.PluginKeys.packageWar

  val cloudBeesSettings: Seq[Setting[_]] = Seq(
    host := "api.cloudbees.com",
    useDeltaWar := true,
    username := None,
    apiKey := None,
    apiSecret := None,
    applicationId := None,
    client <<= (host, apiKey, apiSecret)(Client),
    applications <<= applicationsTask,
    deploy <<= deployTask
  ) ++ warSettings
  
  import scala.collection.JavaConverters._
  
  /***** tasks ******/
  def applicationsTask = (client, streams) map { (client,s) =>
    client().applicationList.getApplications.asScala.foreach(
      a => s.log.info("+ %s - %s".format(a.getTitle, a.getUrls.head)))
  }
  def deployTask = ((packageWar in Compile), client, username, applicationId, useDeltaWar, streams) map {
    (war, client, user, app, delta, s) =>
      if (war.exists) {
        val to = targetAppId(require(user, username), require(app, applicationId))
        s.log.info("Deploying application '%s' to Run@Cloud".format(to))
        val result = client().applicationDeployWar(to, null, null, war.asFile.getAbsolutePath, null, true, new HashWriteProgress)
        s.log.info("Application avalible at %s".format(result.getUrl))
      } else sys.error("There was a problem locating the WAR file for this project")
  }

  /***** internal *****/
  private def targetAppId(username: String, appId: String) = appId.split("/").toList match {
    case a :: Nil => username+"/"+a
    case _ => appId
  }
  private def require[T](value: Option[String], setting: SettingKey[Option[String]]) =
    value.getOrElse {
      sys.error("%s setting is required".format(setting.key.label))
    }
}