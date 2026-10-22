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
 * Removes the
 * [MethodParameters](https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7.24)
 * attribute from the classes a JavaCompile task produced.
 *
 * Since JDK 21 (JDK-8292275) javac writes MethodParameters for every method with synthetic or
 * mandated parameters, e.g. bridge methods, even without `-parameters`. Those entries carry flags
 * but no name, and the D8 of AGP 8.0 and older fails the build while dexing. See the
 * [R8 fix](https://r8.googlesource.com/r8/+/9da58a26e22f195d8187492fac350a90e3315e92) for more
 * details. An alternative would be adding the `-parameters` compile flag, but this could lead to
 * other binary incompatibility issues along the road.
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
