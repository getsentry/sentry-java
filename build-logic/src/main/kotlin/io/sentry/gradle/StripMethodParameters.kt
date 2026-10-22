package io.sentry.gradle

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.tasks.compile.JavaCompile
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Removes the MethodParameters attribute from the classes a JavaCompile task produced.
 *
 * Since JDK 21 (JDK-8292275) javac writes MethodParameters for every method with synthetic or
 * mandated parameters, e.g. bridge methods, even without `-parameters`. Those entries carry flags
 * but no name, and the D8 of AGP 7.x (used e.g. by React Native 0.71 apps) crashes on them with
 * `NullPointerException: Cannot invoke "String.length()"` while dexing. Without `-parameters` this
 * attribute holds nothing the SDK relies on, so dropping it restores the class files javac 17
 * produced.
 */
internal class StripMethodParameters : Action<Task> {
  override fun execute(task: Task) {
    val classesDir = (task as JavaCompile).destinationDirectory.get().asFile
    classesDir
      .walkTopDown()
      .filter { it.isFile && it.extension == "class" }
      .forEach { file ->
        val reader = ClassReader(file.readBytes())
        val writer = ClassWriter(reader, 0)
        val stripper = MethodParametersStripper(writer)
        reader.accept(stripper, 0)
        if (stripper.stripped) {
          file.writeBytes(writer.toByteArray())
        }
      }
  }

  private class MethodParametersStripper(next: ClassVisitor) : ClassVisitor(Opcodes.ASM9, next) {
    var stripped = false

    override fun visitMethod(
      access: Int,
      name: String?,
      descriptor: String?,
      signature: String?,
      exceptions: Array<out String>?,
    ): MethodVisitor? {
      val next = super.visitMethod(access, name, descriptor, signature, exceptions)
      return object : MethodVisitor(Opcodes.ASM9, next) {
        override fun visitParameter(name: String?, access: Int) {
          stripped = true
        }
      }
    }
  }
}
