/*
 * Copyright (C) 2009-2014 Typesafe Inc. <http://www.typesafe.com>
 */
package com.typesafe.play.docs.sbtplugin

import java.io.{ Closeable, BufferedReader, InputStreamReader, InputStream }
import java.net.HttpURLConnection
import java.util.concurrent.Executors
import java.util.jar.JarFile
import org.pegdown.ast._
import org.pegdown.ast.Node
import org.pegdown.plugins.{ ToHtmlSerializerPlugin, PegDownPlugins }
import org.pegdown._
import play.sbtplugin.Colors
import play.doc.{ FileHandle, JarRepository, CodeReferenceNode, CodeReferenceParser }
import sbt._
import sbt.Keys._
import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, Future, ExecutionContext }
import scala.util.control.NonFatal

import Imports.PlayDocsKeys._

// Test that all the docs are renderable and valid
object PlayDocsValidation {

  case class MarkdownReport(markdownFiles: Seq[File],
    wikiLinks: Seq[LinkRef],
    resourceLinks: Seq[LinkRef],
    codeSamples: Seq[CodeSample],
    relativeLinks: Seq[LinkRef],
    externalLinks: Seq[LinkRef])

  case class LinkRef(link: String, file: File, position: Int)
  case class CodeSample(source: String, segment: String, file: File, sourcePosition: Int, segmentPosition: Int)

  val generateMarkdownReportTask = Def.task {

    val base = manualPath.value

    val markdownFiles = (base / "manual" ** "*.md").get

    val wikiLinks = mutable.ListBuffer[LinkRef]()
    val resourceLinks = mutable.ListBuffer[LinkRef]()
    val codeSamples = mutable.ListBuffer[CodeSample]()
    val relativeLinks = mutable.ListBuffer[LinkRef]()
    val externalLinks = mutable.ListBuffer[LinkRef]()

    def parseMarkdownFile(markdownFile: File): String = {

      val processor = new PegDownProcessor(Extensions.ALL, PegDownPlugins.builder()
        .withPlugin(classOf[CodeReferenceParser]).build)

      // Link renderer will also verify that all wiki links exist
      val linkRenderer = new LinkRenderer {
        override def render(node: WikiLinkNode) = {
          node.getText match {
            case link if link.contains("|") => {
              val parts = link.split('|')
              val desc = parts.head
              val page = parts.tail.head.trim
              wikiLinks += LinkRef(page, markdownFile, node.getStartIndex + desc.length + 3)
            }
            case image if image.endsWith(".png") => {
              image match {
                case full if full.startsWith("http://") =>
                  externalLinks += LinkRef(full, markdownFile, node.getStartIndex + 2)
                case absolute if absolute.startsWith("/") =>
                  resourceLinks += LinkRef("manual" + absolute, markdownFile, node.getStartIndex + 2)
                case relative =>
                  val link = markdownFile.getParentFile.getCanonicalPath.stripPrefix(base.getCanonicalPath).stripPrefix("/") + "/" + relative
                  resourceLinks += LinkRef(link, markdownFile, node.getStartIndex + 2)
              }
            }
            case link => {
              wikiLinks += LinkRef(link.trim, markdownFile, node.getStartIndex + 2)
            }
          }
          new LinkRenderer.Rendering("foo", "bar")
        }

        override def render(node: AutoLinkNode) = addLink(node.getText, node, 1)
        override def render(node: ExpLinkNode, text: String) = addLink(node.url, node, text.length + 3)

        private def addLink(url: String, node: Node, offset: Int) = {
          url match {
            case full if full.startsWith("http://") || full.startsWith("https://") =>
              externalLinks += LinkRef(full, markdownFile, node.getStartIndex + offset)
            case relative => relativeLinks += LinkRef(relative, markdownFile, node.getStartIndex + offset)
          }
          new LinkRenderer.Rendering("foo", "bar")
        }
      }

      val codeReferenceSerializer = new ToHtmlSerializerPlugin() {
        def visit(node: Node, visitor: Visitor, printer: Printer) = node match {
          case code: CodeReferenceNode => {

            // Label is after the #, or if no #, then is the link label
            val (source, label) = code.getSource.split("#", 2) match {
              case Array(source, label) => (source, label)
              case Array(source) => (source, code.getLabel)
            }

            // The file is either relative to current page page or absolute, under the root
            val sourceFile = if (source.startsWith("/")) {
              source.drop(1)
            } else {
              markdownFile.getParentFile.getCanonicalPath.stripPrefix(base.getCanonicalPath).stripPrefix("/") + "/" + source
            }

            val sourcePos = code.getStartIndex + code.getLabel.length + 4
            val labelPos = if (code.getSource.contains("#")) {
              sourcePos + source.length + 1
            } else {
              code.getStartIndex + 2
            }

            codeSamples += CodeSample(sourceFile, label, markdownFile, sourcePos, labelPos)
            true
          }
          case _ => false
        }
      }

      val astRoot = processor.parseMarkdown(IO.read(markdownFile).toCharArray)
      new ToHtmlSerializer(linkRenderer, java.util.Arrays.asList[ToHtmlSerializerPlugin](codeReferenceSerializer))
        .toHtml(astRoot)
    }

    markdownFiles.foreach(parseMarkdownFile)

    MarkdownReport(markdownFiles, wikiLinks.toSeq, resourceLinks.toSeq, codeSamples.toSeq, relativeLinks.toSeq, externalLinks.toSeq)
  }

  val validateDocsTask = Def.task {
    val report = generateMarkdownReport.value
    val log = streams.value.log
    val base = manualPath.value

    val docsJarRepo: play.doc.FileRepository with Closeable = if (fallbackToJar.value) {
      val classpath: Seq[Attributed[File]] = (dependencyClasspath in Test).value
      val docsJarFile = {
        val f = classpath.map(_.data).filter(_.getName.startsWith("play-docs")).head
        new JarFile(f)
      }
      new JarRepository(docsJarFile, Some("play/docs/content")) with Closeable
    } else {
      new play.doc.FileRepository with Closeable {
        def loadFile[A](path: String)(loader: (InputStream) => A) = None
        def handleFile[A](path: String)(handler: (FileHandle) => A) = None
        def findFileWithName(name: String) = None
        def close(): Unit = ()
      }
    }

    val pages = report.markdownFiles.map(f => f.getName.dropRight(3) -> f).toMap

    var failed = false

    def doAssertion(desc: String, errors: Seq[_])(onFail: => Unit): Unit = {
      if (errors.isEmpty) {
        log.info("[" + Colors.green("pass") + "] " + desc)
      } else {
        failed = true
        onFail
        log.info("[" + Colors.red("fail") + "] " + desc + " (" + errors.size + " errors)")
      }
    }

    def fileExists(path: String): Boolean = {
      new File(base, path).isFile || docsJarRepo.loadFile(path)(_ => ()).nonEmpty
    }

    def assertLinksNotMissing(desc: String, links: Seq[LinkRef], errorMessage: String): Unit = {
      doAssertion(desc, links) {
        links.foreach { link =>
          logErrorAtLocation(log, link.file, link.position, errorMessage + " " + link.link)
        }
      }
    }

    val duplicates = report.markdownFiles
      .filterNot(_.getName.startsWith("_"))
      .groupBy(s => s.getName)
      .filter(v => v._2.size > 1)

    doAssertion("Duplicate markdown file name test", duplicates.toSeq) {
      duplicates.foreach { d =>
        log.error(d._1 + ":\n" + d._2.mkString("\n    "))
      }
    }

    assertLinksNotMissing("Missing wiki links test", report.wikiLinks.filterNot { link =>
      pages.contains(link.link) || docsJarRepo.findFileWithName(link.link + ".md").nonEmpty
    }, "Could not find link")

    def relativeLinkOk(link: LinkRef) = {
      link match {
        case scalaApi if scalaApi.link.startsWith("api/scala/index.html#") => true
        case javaApi if javaApi.link.startsWith("api/java/") => true
        case resource if resource.link.startsWith("resources/") =>
          fileExists(resource.link.stripPrefix("resources/"))
        case bad => false
      }
    }

    assertLinksNotMissing("Relative link test", report.relativeLinks.collect {
      case link if !relativeLinkOk(link) => link
    }, "Bad relative link")

    assertLinksNotMissing("Missing wiki resources test",
      report.resourceLinks.collect {
        case link if !fileExists(link.link) => link
      }, "Could not find resource")

    val (existing, nonExisting) = report.codeSamples.partition(sample => fileExists(sample.source))

    assertLinksNotMissing("Missing source files test",
      nonExisting.map(sample => LinkRef(sample.source, sample.file, sample.sourcePosition)),
      "Could not find source file")

    def segmentExists(sample: CodeSample) = {
      // Find the code segment
      val sourceCode = {
        val file = new File(base, sample.source)
        if (file.exists()) {
          IO.readLines(new File(base, sample.source))
        } else {
          docsJarRepo.loadFile(sample.source)(is => IO.readLines(new BufferedReader(new InputStreamReader(is)))).get
        }
      }
      val notLabel = (s: String) => !s.contains("#" + sample.segment)
      val segment = sourceCode dropWhile (notLabel) drop (1) takeWhile (notLabel)
      !segment.isEmpty
    }

    assertLinksNotMissing("Missing source segments test", existing.collect {
      case sample if !segmentExists(sample) => LinkRef(sample.segment, sample.file, sample.segmentPosition)
    }, "Could not find source segment")

    val allLinks = report.wikiLinks.map(_.link).toSet

    if (!fallbackToJar.value) {
      // A bit hard to do this without parsing all files, so only do it if we're not falling back to the jar file
      val orphanPages = pages.filterNot(page => allLinks.contains(page._1)).filterNot { page =>
        page._1.startsWith("_") || page._1 == "Home" || page._1.startsWith("Book")
      }
      doAssertion("Orphan pages test", orphanPages.toSeq) {
        orphanPages.foreach { page =>
          log.error("Page " + page._2 + " is not referenced by any links")
        }
      }
    }

    docsJarRepo.close()

    if (failed) {
      throw new RuntimeException("Documentation validation failed")
    }
  }

  val validateExternalLinksTask = Def.task {
    val log = streams.value.log
    val report = generateMarkdownReport.value

    val grouped = report.externalLinks.groupBy(_.link).filterNot(_._1.startsWith("http://localhost:9000")).toSeq.sortBy(_._1)

    implicit val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(50))

    val futures = grouped.map { entry =>
      Future {
        val (url, refs) = entry
        val connection = new URL(url).openConnection().asInstanceOf[HttpURLConnection]
        try {
          connection.connect()
          connection.getResponseCode match {
            // A few people use GitHub.com repositories, which will return 403 errors for directory listings
            case 403 if "GitHub.com".equals(connection.getHeaderField("Server")) => Nil
            case bad if bad >= 300 => {
              refs.foreach { link =>
                logErrorAtLocation(log, link.file, link.position, connection.getResponseCode + " response for external link " + link.link)
              }
              refs
            }
            case ok => Nil
          }
        } catch {
          case NonFatal(e) =>
            refs.foreach { link =>
              logErrorAtLocation(log, link.file, link.position, e.getClass.getName + ": " + e.getMessage + " for external link " + link.link)
            }
            refs
        } finally {
          connection.disconnect()
        }
      }
    }

    val invalidRefs = Await.result(Future.sequence(futures), Duration.Inf).flatten

    ec.shutdownNow()

    if (invalidRefs.isEmpty) {
      log.info("[" + Colors.green("pass") + "] External links test")
    } else {
      log.info("[" + Colors.red("fail") + "] External links test (" + invalidRefs.size + " errors)")
      throw new RuntimeException("External links validation failed")
    }

    grouped.map(_._1)
  }

  private def logErrorAtLocation(log: Logger, file: File, position: Int, errorMessage: String) = synchronized {
    // Load the source
    val lines = IO.readLines(file)
    // Calculate the line and col
    // Tuple is (total chars seen, line no, col no, Option[line])
    val (_, lineNo, colNo, line) = lines.foldLeft((0, 0, 0, None: Option[String])) { (state, line) =>
      state match {
        case (_, _, _, Some(_)) => state
        case (total, l, c, None) => {
          if (total + line.length < position) {
            (total + line.length + 1, l + 1, c, None)
          } else {
            (0, l + 1, position - total + 1, Some(line))
          }
        }
      }
    }
    log.error(errorMessage + " at " + file.getAbsolutePath + ":" + lineNo)
    line.foreach { l =>
      log.error(l)
      log.error(l.take(colNo - 1).map { case '\t' => '\t'; case _ => ' ' } + "^")
    }
  }
}

