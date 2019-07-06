package com.github.pawelkrol

import org.apache.commons.io.FilenameUtils.{getBaseName, getExtension, getFullPath, getFullPathNoEndSeparator, getName, normalizeNoEndSeparator}
import org.apache.commons.io.FileUtils.{convertFileCollectionToFileArray, forceMkdir, listFiles}
import org.apache.commons.io.filefilter.{SuffixFileFilter, TrueFileFilter}

import java.io.File
import java.lang.Character.isWhitespace

import sbt.{AutoPlugin, Keys, TaskKey, filesToFinder, fileToRichFile, stringToOrganization, toRepositoryName}
import sbt.Configurations.{Compile, Test}
import sbt.Def.{Setting, settingKey, task}
import sbt.Keys.{TaskStreams, baseDirectory, compile, libraryDependencies, resolvers, run, scalaSource, sourceDirectory, streams, target, unmanagedSourceDirectories}
import sbt.plugins.JvmPlugin

import scala.sys.process.Process

object SbtCommTest extends AutoPlugin {

  object autoImport {
    lazy val addFiles = settingKey[Seq[String]]("Additional files to be written into target disk image.")
    lazy val assemblySource = settingKey[File]("Default assembly source directory.")
    lazy val compiler = settingKey[String]("Defines the compiler to use for compilation.")
    lazy val compilerOptions = settingKey[String]("Options for the compiler.")
    lazy val emulator = settingKey[String]("Defines the emulator to use for running.")
    lazy val emulatorOptions = settingKey[String]("Options for running.")
    lazy val fuseCFS = settingKey[String]("Defines the driver to mount CFS 0.11 filesystem using fuse.")
    lazy val imageBuilder = settingKey[String]("Defines the D64 disk image builder.")
    lazy val imageBuilderOptions = settingKey[String]("Options for the image creator.")
    lazy val mainProgram = settingKey[String]("Main executable program file.")
    lazy val packager = settingKey[String]("Defines the packager to use for packaging.")
    lazy val packagerOptions = settingKey[String]("Options for packaging.")
    lazy val startAddress = settingKey[Int]("Main executable start address.")

    lazy val compileAssembly = TaskKey[Unit]("compile-assembly", "Compiles assembly sources.")
    lazy val packageAssembly = TaskKey[Unit]("package-assembly", "Produces a main artifact, such as an executable program.")
    lazy val runEmulator = TaskKey[Unit]("run-emulator", "Runs an emulator.")
    lazy val runPlus60k = TaskKey[Unit]("run-plus60k", "Runs an emulator with a simulated +60k memory expansion.")
    lazy val sourceFiles = TaskKey[Seq[File]]("source-files", "Collects all assembly source files.")
  }

  import autoImport._

  override def requires = JvmPlugin

  override def trigger = allRequirements

  lazy val baseSettings: Seq[Setting[_]] = Seq(
    addFiles := Seq(),
    assemblySource := (sourceDirectory in Compile).value,
    compile in Compile := { (compile in Compile) dependsOn compileAssembly }.value,
    compileAssembly := compileAssemblyTask.value,
    compiler := "dreamass",
    compilerOptions := "--max-errors 10 --max-warnings 10 --verbose -Wall",
    emulator := "x64sc",
    emulatorOptions := "-model c64c -truedrive",
    fuseCFS := "cfs011mount",
    imageBuilder := "cc1541",
    imageBuilderOptions := "-n \"- SBT-COMMTEST -\" -i \"2019 \"",
    Keys.`package` := (packageAssemblyTask dependsOn compileAssemblyTask).value,
    packager := "exomizer",
    packageAssembly := (packageAssemblyTask dependsOn compileAssemblyTask).value,
    packagerOptions := "-p 1 -m 1 -t64 -n",
    run := (runEmulatorTask dependsOn packageAssemblyTask).value,
    runEmulator := (runEmulatorTask dependsOn packageAssemblyTask).value,
    runPlus60k := (runPlus60kTask dependsOn packageAssemblyTask).value,
    scalaSource in Test := baseDirectory.value / "src" / "test",
    sourceFiles := sourceFilesTask.value,
    unmanagedSourceDirectories in Compile += baseDirectory.value / "src" / "main"
  )

  private def dependencies: Seq[Setting[_]] = Seq(
    resolvers += "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
    libraryDependencies += "com.github.pawelkrol" % "commtest" % "0.05"
  )

  override lazy val projectSettings = baseSettings ++ dependencies

  lazy val compileAssemblyTask =
    task {
      val assemblySourcePath = assemblySource.value.getAbsolutePath
      val basePath = baseDirectory.value.getAbsolutePath
      val targetPath = normalizeNoEndSeparator(target.value.getAbsolutePath)
      val sourcePaths = sourceFiles.value.map(_.getAbsolutePath)
      val s = streams.value
      sourcePaths.foreach(sourcePath =>
        compileSrc(sourcePath, s, assemblySourcePath, basePath, targetPath, compiler.value, compilerOptions.value)
      )
    }

  lazy val packageAssemblyTask =
    task {
      val additionalFiles = addFiles.value.map(new File(_).getAbsolutePath)
      val assemblySourcePath = assemblySource.value.getAbsolutePath
      val basePath = baseDirectory.value.getAbsolutePath
      val targetPath = normalizeNoEndSeparator(target.value.getAbsolutePath)
      val relativeSource = mainProgram.value
      val sourcePath = new File(assemblySourcePath, relativeSource).getAbsolutePath
      compileSrc(sourcePath, streams.value, assemblySourcePath, basePath, targetPath, compiler.value, compilerOptions.value)
      val packagedFile = packagePrg(relativeSource, streams.value, targetPath, packager.value, packagerOptions.value, startAddress.value)
      packageD64(relativeSource, streams.value, targetPath, additionalFiles, imageBuilder.value, imageBuilderOptions.value)
      packagedFile
    }

  lazy val runEmulatorTask =
    task {
      val options = Seq(emulatorOptions.value, "-memoryexphack 0")
      startEmulator(streams.value, options, target.value, mainProgram.value, emulator.value)
    }

  lazy val runPlus60kTask =
    task {
      val options = Seq(emulatorOptions.value, "-memoryexphack 2 -plus60kbase 0xD100")
      startEmulator(streams.value, options, target.value, mainProgram.value, emulator.value)
    }

  private def startEmulator(
    s: TaskStreams,
    emulatorOptions: Seq[String],
    target: File,
    relativeSource: String,
    emulator: String
  ): Unit = {
    val targetPath = normalizeNoEndSeparator(target.getAbsolutePath)
    val targetOutputD64 = targetDiskImageFullPath(relativeSource, s, targetPath)
    val fileNameOnDisk = executableFileNameOnDisk(relativeSource)
    val command = emulator + " " + emulatorOptions.mkString(" ") + " " + targetOutputD64 + ":" + fileNameOnDisk
    executeCommand(command, s)
  }

  lazy val sourceFilesTask =
    task {
      val directory = assemblySource.value
      val fileFilter = new SuffixFileFilter(".src")
      val dirFilter = TrueFileFilter.TRUE
      convertFileCollectionToFileArray(listFiles(directory, fileFilter, dirFilter))
    }

  private def compileSrc(
    sourcePath: String,
    s: TaskStreams,
    assemblySourcePath: String,
    basePath: String,
    targetPath: String,
    compiler: String,
    compilerOptions: String
  ): Unit = {
    val compileDirectory = getFullPath(sourcePath)
    val relativeSource = sourcePath.replace(assemblySourcePath, "")
    val (sourceDirectory, sourceName, sourceBaseName, sourceExtension) = relativeSourceComponents(relativeSource)
    val targetDirectory = setupTargetDirectory(sourceDirectory, targetPath, s)
    val targetLabelLog = new File(targetDirectory, sourceBaseName + ".log").getAbsolutePath
    val targetOutputPrg = new File(targetDirectory, sourceBaseName + ".prg").getAbsolutePath
    val command = compiler + " " + compilerOptions + " --label-log " + targetLabelLog + " --output " + targetOutputPrg + " " + sourcePath
    executeCommand(command, s, new File(compileDirectory))
  }

  private def executableFileNameOnDisk(
    relativeSource: String
  ) = {
    val (_, _, sourceBaseName, _) = relativeSourceComponents(relativeSource)
    sourceBaseName.toLowerCase
  }

  private def targetDiskImageFullPath(
    relativeSource: String,
    s: TaskStreams,
    targetPath: String
  ) = {
    val (sourceDirectory, _, sourceBaseName, _) = relativeSourceComponents(relativeSource)
    val targetDirectory = setupTargetDirectory(sourceDirectory, targetPath, s)
    new File(targetDirectory, sourceBaseName + ".d64").getAbsolutePath
  }

  private def fileToDisk(
    fullPath: String
  ) = "-f \"" + getBaseName(fullPath).toUpperCase + "\" -w " + fullPath

  private def packageD64(
    relativeSource: String,
    s: TaskStreams,
    targetPath: String,
    additionalFiles: Seq[String],
    imageBuilder: String,
    imageBuilderOptions: String
  ) = {
    val (sourceDirectory, _, sourceBaseName, _) = relativeSourceComponents(relativeSource)
    val targetDirectory = setupTargetDirectory(sourceDirectory, targetPath, s)
    val targetExecutablePrg = new File(targetDirectory, sourceBaseName).getAbsolutePath
    val allFiles = targetExecutablePrg +: additionalFiles
    val targetOutputD64 = targetDiskImageFullPath(relativeSource, s, targetPath)
    val command = imageBuilder + " " + allFiles.map(fileToDisk(_)).mkString(" ") + " " + imageBuilderOptions + " " + targetOutputD64
    executeCommand(command, s)
  }

  private def packagePrg(
    relativeSource: String,
    s: TaskStreams,
    targetPath: String,
    packager: String,
    packagerOptions: String,
    startAddress: Int
  ) = {
    val (sourceDirectory, _, sourceBaseName, _) = relativeSourceComponents(relativeSource)
    val targetDirectory = setupTargetDirectory(sourceDirectory, targetPath, s)
    val targetExecutablePrg = new File(targetDirectory, sourceBaseName).getAbsolutePath
    val targetOutputPrg = new File(targetDirectory, sourceBaseName + ".prg").getAbsolutePath
    val command = packager + " sfx 0x%04x".format(startAddress) + " " + packagerOptions + " -o " + targetExecutablePrg + " " + targetOutputPrg
    executeCommand(command, s)
    new File(targetExecutablePrg)
  }

  private def relativeSourceComponents(
    relativeSource: String
  ) = {
    val sourceDirectory = getFullPathNoEndSeparator(relativeSource)
    val sourceName = getName(relativeSource)
    val sourceBaseName = getBaseName(relativeSource)
    val sourceExtension = getExtension(relativeSource)
    (sourceDirectory, sourceName, sourceBaseName, sourceExtension)
  }

  private def setupTargetDirectory(
    sourceDirectory: String,
    targetPath: String,
    s: TaskStreams
  ) = {
    val targetDirectory =
      if (sourceDirectory.equals("/"))
        new File(targetPath)
      else
        new File(targetPath, sourceDirectory)
    if (!targetDirectory.exists) {
      forceMkdir(targetDirectory)
      s.log.info("mkdir: created directory '" + targetDirectory + "'")
    }
    targetDirectory
  }

  private def executeCommand(
    command: String,
    s: TaskStreams,
    executionDirectory: File = new File(".")
  ): Unit = {
    s.log.info(command)

    object QuotientStatus extends Enumeration {
      val None, Opened, Closed = Value
    }

    import QuotientStatus._

    val (splitCommand, lastItem, lastSeparator, lastQuotient) = command.toList.foldLeft[Tuple4[Seq[String], String, Boolean, QuotientStatus.Value]]((Seq(), "", false, None))((result, char) => {
      val (command, item, separator, quotient) = result

      if (separator)
        if (quotient == Opened)
          (command, item :+ char, false, Opened)
        else
          if (isWhitespace(char))
            (command, item, true, None)
          else if (char == '\"')
            (command, item, false, Opened)
          else
            (command, item :+ char, false, None)
      else
        if (quotient == Closed)
          if (isWhitespace(char))
            (command, "", true, None)
          else
            throw new RuntimeException("Unexpected character encountered just after a closing parenthesis: '%c'".format(char))
        else if (quotient == Opened)
          if (char == '\"')
            (command :+ item, "", false, Closed)
          else
            (command, item :+ char, false, Opened)
        else
          if (isWhitespace(char))
            if (item.length > 0)
              (command :+ item, "", true, None)
            else
              (command, "", true, None)
          else
            (command, item :+ char, false, None)
    })

    if (lastQuotient == Opened)
      throw new RuntimeException("Unbalanced quotation mark encountered while parsing command: '%s'".format(command))

    val finalCommand =
      if (lastSeparator)
        splitCommand
      else
        splitCommand :+ lastItem

    val result = Process(finalCommand.filterNot(_.isEmpty).toSeq, executionDirectory).!!
    s.log.info(result)
  }
}
