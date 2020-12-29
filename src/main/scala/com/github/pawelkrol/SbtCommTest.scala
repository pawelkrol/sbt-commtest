package com.github.pawelkrol

import org.apache.commons.io.FilenameUtils.{getBaseName, getExtension, getFullPath, getFullPathNoEndSeparator, getName, normalizeNoEndSeparator}
import org.apache.commons.io.FileUtils.{convertFileCollectionToFileArray, deleteDirectory, forceMkdir, listFiles}
import org.apache.commons.io.filefilter.{SuffixFileFilter, TrueFileFilter}

import java.io.{File, FileInputStream, FileOutputStream}
import java.lang.Character.isWhitespace
import java.nio.file.Files.{copy, createTempDirectory}
import java.nio.file.Paths.{get => getPath}
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.zip.{ZipFile, ZipInputStream}

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
    lazy val emulatorOptions = settingKey[Seq[String]]("Options for running.")
    lazy val fuseCFS = settingKey[String]("Defines the driver to mount CFS 0.11 filesystem using fuse.")
    lazy val imageBuilder = settingKey[String]("Defines the D64 disk image builder.")
    lazy val imageBuilderOptions = settingKey[String]("Options for the image creator.")
    lazy val mainProgram = settingKey[String]("Main executable program file.")
    lazy val packager = settingKey[String]("Defines the packager to use for packaging.")
    lazy val packagerOptions = settingKey[String]("Options for packaging.")
    lazy val startAddress = settingKey[Int]("Main executable start address.")

    lazy val compileAssembly = TaskKey[Unit]("compile-assembly", "Compiles assembly sources.")
    lazy val packageAssembly = TaskKey[Unit]("package-assembly", "Produces a main artifact, such as an executable program.")
    lazy val packageD64 = TaskKey[Unit]("package-d64", "Produces a floppy disk image with an executable program.")
    lazy val packageIDE64 = TaskKey[Unit]("package-ide64", "Produces a hard disk image with an executable program.")
    lazy val runEmulator = TaskKey[Unit]("run-emulator", "Runs an emulator.")
    lazy val runPlus60k = TaskKey[Unit]("run-plus60k", "Runs an emulator with a simulated +60k memory expansion.")
    lazy val runIDE64 = TaskKey[Unit]("run-ide64", "Runs an emulator with a simulated IDE64 device.")
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
    emulatorOptions := Seq("+confirmonexit", "+nativemonitor", "-speed 100", "-sound", "-soundsync 1", "-keymap 1", "+keepaspect", "-pal", "-ciamodel 1", "-model c64c", "-VICIIvcache", "-VICIIdsize", "-VICIIfilter 1", "-VICIIborders 1", "-sidenginemodel 258", "-residsamp 1", "-sidfilters", "-joydev1 0", "-joydev2 0", "+autostart-handle-tde", "+mouse", "-drive8type 1542", "-drive8extend 2", "-drive9type 0", "-truedrive", "-drivesound", "-drivesoundvolume 25"),
    fuseCFS := "cfs011mount",
    imageBuilder := "cc1541",
    imageBuilderOptions := "-n \"- sbt-commtest -\" -i \"2020 \"",
    Keys.`package` := (packageAssemblyTask dependsOn compileAssemblyTask).value,
    packager := "exomizer",
    packageAssembly := (packageAssemblyTask dependsOn compileAssemblyTask).value,
    packageD64 := (packageD64Task dependsOn packageAssemblyTask).value,
    packageIDE64 := (packageIDE64Task dependsOn packageAssemblyTask).value,
    packagerOptions := "-p 1 -m 1 -t64 -n",
    run := (runEmulatorTask dependsOn packageD64Task).value,
    runEmulator := (runEmulatorTask dependsOn packageD64Task).value,
    runPlus60k := (runPlus60kTask dependsOn packageD64Task).value,
    runIDE64 := (runIDE64Task dependsOn packageIDE64Task).value,
    scalaSource in Test := baseDirectory.value / "src" / "test",
    sourceFiles := sourceFilesTask.value,
    unmanagedSourceDirectories in Compile += baseDirectory.value / "src" / "main"
  )

  private def dependencies: Seq[Setting[_]] = Seq(
    resolvers += "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
    libraryDependencies += "com.github.pawelkrol" % "commtest" % "0.06-SNAPSHOT"
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
      val assemblySourcePath = assemblySource.value.getAbsolutePath
      val basePath = baseDirectory.value.getAbsolutePath
      val targetPath = normalizeNoEndSeparator(target.value.getAbsolutePath)
      val relativeSource = mainProgram.value
      val sourcePath = new File(assemblySourcePath, relativeSource).getAbsolutePath
      compileSrc(sourcePath, streams.value, assemblySourcePath, basePath, targetPath, compiler.value, compilerOptions.value)
      packagePrg(relativeSource, streams.value, targetPath, packager.value, packagerOptions.value, startAddress.value)
    }

  lazy val packageD64Task =
    task {
      val additionalFiles = addFiles.value.map(new File(_).getAbsolutePath)
      val targetPath = normalizeNoEndSeparator(target.value.getAbsolutePath)
      val relativeSource = mainProgram.value
      packageD64Files(relativeSource, streams.value, targetPath, additionalFiles, imageBuilder.value, imageBuilderOptions.value)
    }

  lazy val packageIDE64Task =
    task {
      val additionalFiles = addFiles.value.map(new File(_).getAbsolutePath)
      val relativeSource = mainProgram.value
      packageIDE64Files(relativeSource, streams.value, target.value, additionalFiles, fuseCFS.value)
    }

  lazy val runEmulatorTask =
    task {
      val options = emulatorOptions.value ++ Seq("-memoryexphack 0")
      startEmulator(streams.value, options, target.value, mainProgram.value, emulator.value)
    }

  lazy val runPlus60kTask =
    task {
      val options = emulatorOptions.value ++ Seq("-memoryexphack 2", "-plus60kbase 0xD100")
      startEmulator(streams.value, options, target.value, mainProgram.value, emulator.value)
    }

  private def extractResourceFile(
    resource: String,
    s: TaskStreams,
    target: File
  ) = {
    val stream = getClass.getResourceAsStream(resource)
    val (resourceDirectory, resourceName, _, _) = relativeSourceComponents(resource)
    val targetPath = normalizeNoEndSeparator(target.getAbsolutePath)
    val targetDirectory = setupTargetDirectory(resourceDirectory, targetPath, s)
    val filePath = new File(targetDirectory, resourceName).getAbsolutePath
    copy(stream, getPath(filePath), REPLACE_EXISTING)
    filePath
  }

  lazy val runIDE64Task =
    task {
      val relativeSource = mainProgram.value
      val targetPath = normalizeNoEndSeparator(target.value.getAbsolutePath)
      val targetOutputIDE64 = targetHardDiskImageFullPath(relativeSource, streams.value, targetPath)
      val idedosPath = extractResourceFile("/idedos20151012-c64.rom", streams.value, target.value)
      val options = emulatorOptions.value ++ Seq("-cartide " + idedosPath, "-IDE64version 1", "-IDE64image1 " + targetOutputIDE64, "-IDE64autosize1")
      startEmulator(streams.value, options, target.value, mainProgram.value, emulator.value)
    }

  private def unpackHardDiskImage(
    idezipResource: String,
    relativeSource: String,
    s: TaskStreams,
    target: File
  ) = {
    s.log.info("Archive:  ide.zip")
    val targetPath = normalizeNoEndSeparator(target.getAbsolutePath)
    val targetOutputIDE64 = targetHardDiskImageFullPath(relativeSource, s, targetPath)
    val idezipPath = extractResourceFile(idezipResource, s, target)
    s.log.info("  inflating: ide.cfa")
    val istream = new ZipInputStream(new FileInputStream(idezipPath))
    while (istream.getNextEntry.getName != "ide.cfa") {}
    val hddFile = new File(targetOutputIDE64)
    val ostream = new FileOutputStream(hddFile)
    val buffer = new Array[Byte](1024)
    var length = istream.read(buffer)
    while (length > 0) {
      ostream.write(buffer, 0, length)
      length = istream.read(buffer)
    }
    ostream.close
    istream.closeEntry
    istream.close
    targetOutputIDE64
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
    val command = emulator + " " + emulatorOptions.mkString(" ") + " -attach8rw " + targetOutputD64 + ":" + fileNameOnDisk
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

  private def targetHardDiskImageFullPath(
    relativeSource: String,
    s: TaskStreams,
    targetPath: String
  ) = {
    val (sourceDirectory, _, _, _) = relativeSourceComponents(relativeSource)
    val targetDirectory = setupTargetDirectory(sourceDirectory, targetPath, s)
    new File(targetDirectory, "ide.cfa").getAbsolutePath
  }

  private def fileToDisk(
    fullPath: String
  ) = "-f \"" + getBaseName(fullPath).toLowerCase + "\" -w " + fullPath

  private def targetExecutablePrg(
    relativeSource: String,
    s: TaskStreams,
    targetPath: String,
  ) = {
    val (sourceDirectory, _, sourceBaseName, _) = relativeSourceComponents(relativeSource)
    val targetDirectory = setupTargetDirectory(sourceDirectory, targetPath, s)
    new File(targetDirectory, sourceBaseName).getAbsolutePath
  }

  private def packageD64Files(
    relativeSource: String,
    s: TaskStreams,
    targetPath: String,
    additionalFiles: Seq[String],
    imageBuilder: String,
    imageBuilderOptions: String
  ) = {
    val allFiles = targetExecutablePrg(relativeSource, s, targetPath) +: additionalFiles
    val targetOutputD64 = targetDiskImageFullPath(relativeSource, s, targetPath)
    val command = imageBuilder + " " + allFiles.map(fileToDisk(_)).mkString(" ") + " " + imageBuilderOptions + " " + targetOutputD64
    executeCommand(command, s)
    targetOutputD64
  }

  private def packageIDE64Files(
    relativeSource: String,
    s: TaskStreams,
    target: File,
    additionalFiles: Seq[String],
    cfsMount: String
  ) = {
    val targetPath = normalizeNoEndSeparator(target.getAbsolutePath)
    val allFiles = targetExecutablePrg(relativeSource, s, targetPath) +: additionalFiles
    val targetOutputIDE64 = unpackHardDiskImage("/ide.zip", relativeSource, s, target)
    val ide64MountPoint = createTempDirectory("ide64-").toAbsolutePath.toString
    try {
      val command = cfsMount + " " + targetOutputIDE64 + " " + ide64MountPoint
      executeCommand(command, s)
      val cfsPartitionName = "01 sbt-commtest"
      val cfsPartitionRoot = getPath(ide64MountPoint, cfsPartitionName).toString
      allFiles.foreach(file => {
        val (_, fileName, baseName, extension) = relativeSourceComponents(file)
        val targetName = if (extension == "" || extension == "prg") baseName else fileName
        val targetPath = getPath(cfsPartitionRoot, targetName)
        copy(getPath(file), targetPath, REPLACE_EXISTING)
        s.log.info("'" + file + "' -> '" + targetPath.toString + "'")
        val targetFile = targetPath.toFile
        targetFile.setWritable(true)
        targetFile.setReadable(true)
        targetFile.setExecutable(true)
        s.log.info("mode of '" + targetPath.toString + "' changed to 0755 (rwxr-xr-x)")
      })
    }
    finally {
      val command = "fusermount -u " + ide64MountPoint
      executeCommand(command, s)
      deleteDirectory(new File(ide64MountPoint))
    }
    targetOutputIDE64
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
