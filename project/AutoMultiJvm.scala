import sbt.Keys._
import sbt._

object AutoMultiJvm extends AutoPlugin {
  import com.typesafe.sbt.SbtMultiJvm
  import SbtMultiJvm.MultiJvmKeys._
  import sbtassembly.AssemblyKeys._
  import sbtassembly.MergeStrategy

  override def projectSettings: Seq[Setting[_]] = SbtMultiJvm.multiJvmSettings ++ Seq(
    test := {
      (Test / test).value
      (MultiJvm / test).value
    },
    MultiJvm / multiNodeHosts := multiNodeHostNames,
    MultiJvm / assembly / assemblyMergeStrategy := {
      case "application.conf"                     => MergeStrategy.concat
      case x if x.contains("versions.properties") => MergeStrategy.discard
      case x =>
        val oldStrategy = (MultiJvm / assembly / assemblyMergeStrategy).value
        oldStrategy(x)
    }
  )

  def multiNodeHostNames: Seq[String] = sys.env.get("multiNodeHosts") match {
    case Some(str) => str.split(",").toSeq
    case None      => Seq.empty
  }

  override def projectConfigurations: Seq[Configuration] = List(MultiJvm)
}
