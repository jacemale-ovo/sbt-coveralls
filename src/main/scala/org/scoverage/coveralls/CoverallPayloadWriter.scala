package org.scoverage.coveralls

import java.io.File
import scala.io.{ Codec, Source }

import sbt.Logger
import annotation.tailrec
import com.fasterxml.jackson.core.{ JsonFactory, JsonEncoding }

class CoverallPayloadWriter(
    repoRootDir: File,
    coverallsFile: File,
    repoToken: Option[String],
    travisJobId: Option[String],
    serviceName: Option[String],
    gitClient: GitClient,
    sourcesEnc: Codec,
    jsonEnc: JsonEncoding) {

  val repoRootDirStr = repoRootDir.getCanonicalPath.replace(File.separator, "/") + "/"
  import gitClient._

  val gen = generator(coverallsFile)

  def generator(file: File) = {
    if (!file.getParentFile.exists) file.getParentFile.mkdirs
    val factory = new JsonFactory
    factory.createGenerator(file, jsonEnc)
  }

  def start(implicit log: Logger) {
    gen.writeStartObject()

    def writeOpt(fieldName: String, holder: Option[String]) =
      holder foreach { gen.writeStringField(fieldName, _) }

    writeOpt("repo_token", repoToken)
    writeOpt("service_name", serviceName)
    writeOpt("service_job_id", travisJobId)
    writeOpt("service_pull_request", sys.env.get("CI_PULL_REQUEST"))

    addGitInfo

    gen.writeFieldName("source_files")
    gen.writeStartArray()
  }

  private def addGitInfo(implicit log: Logger) {
    gen.writeFieldName("git")
    gen.writeStartObject()

    gen.writeFieldName("head")
    gen.writeStartObject()

    val commitInfo = lastCommit()

    gen.writeStringField("id", commitInfo.id)
    gen.writeStringField("author_name", commitInfo.authorName)
    gen.writeStringField("author_email", commitInfo.authorEmail)
    gen.writeStringField("committer_name", commitInfo.committerName)
    gen.writeStringField("committer_email", commitInfo.committerEmail)
    gen.writeStringField("message", commitInfo.shortMessage)

    gen.writeEndObject()

    gen.writeStringField("branch", currentBranch)

    gen.writeFieldName("remotes")
    gen.writeStartArray()

    addGitRemotes(remotes)

    gen.writeEndArray()

    gen.writeEndObject()
  }

  @tailrec
  private def addGitRemotes(remotes: Seq[String])(implicit log: Logger) {
    if (remotes.isEmpty) return

    gen.writeStartObject()
    gen.writeStringField("name", remotes.head)
    gen.writeStringField("url", remoteUrl(remotes.head))
    gen.writeEndObject()

    addGitRemotes(remotes.tail)
  }

  def addSourceFile(report: SourceFileReport) {

    // create a name relative to the project root (rather than the module root)
    // this is needed so that coveralls can find the file in git.
    val fileName = report.file.replace(repoRootDirStr, "")

    gen.writeStartObject()
    gen.writeStringField("name", fileName)

    val source = Source.fromFile(report.file)(sourcesEnc)
    val sourceCode = source.getLines().mkString("\n")
    source.close()

    gen.writeStringField("source", sourceCode)

    gen.writeFieldName("coverage")
    gen.writeStartArray()
    report.lineCoverage.foreach {
      case Some(x) => gen.writeNumber(x)
      case _ => gen.writeNull()
    }
    gen.writeEndArray()
    gen.writeEndObject()
  }

  def end(): Unit = {
    gen.writeEndArray()
    gen.writeEndObject()
    gen.flush()
    gen.close()
  }

  def flush(): Unit = {
    gen.flush()
  }
}
