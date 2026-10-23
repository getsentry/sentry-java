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
 * attribute from the methods of a JavaCompile task that have a parameter without a name.
 *
 * Since JDK 21 (JDK-8292275) javac writes MethodParameters for every method with synthetic or
 * mandated parameters, e.g. bridge methods, even without `-parameters`. Those entries carry flags
 * but no name, and the D8 of AGP 8.0 and older fails the build while dexing. See the
 * [R8 fix](https://r8.googlesource.com/r8/+/9da58a26e22f195d8187492fac350a90e3315e92) for more
 * details. An alternative would be adding the `-parameters` compile flag, but this could lead to
 * other binary incompatibility issues along the road.
 *
 * `parameters_count` must match the parameters of the method descriptor, so a single entry cannot
 * be removed. The attribute goes away for the whole method instead, as soon as one of its entries
 * has no name. Methods whose entries all have a name, e.g. the canonical constructor of a record
 * or anything compiled with `-parameters`, keep the attribute and their names.
 */
internal class StripMethodParameters : Action<Task> {
  override fun execute(task: Task) {
    val classesDir = (task as JavaCompile).destinationDirectory.get().asFile
    classesDir
      .walkTopDown()
      .filter { it.isFile && it.extension == "class" }
      .forEach { file ->
        val reader = ClassReader(file.readBytes())
        val methods = methodsWithUnnamedParameters(reader)
        if (methods.isNotEmpty()) {
          val writer = ClassWriter(reader, 0)
          reader.accept(MethodParametersStripper(writer, methods), 0)
          file.writeBytes(writer.toByteArray())
        }
      }
  }

  /** Returns `name + descriptor` of every method that has a parameter entry without a name. */
  private fun methodsWithUnnamedParameters(reader: ClassReader): Set<String> {
    val methods = mutableSetOf<String>()
    val scanner =
      object : ClassVisitor(Opcodes.ASM9) {
        override fun visitMethod(
          access: Int,
          name: String?,
          descriptor: String?,
          signature: String?,
          exceptions: Array<out String>?,
        ): MethodVisitor =
          object : MethodVisitor(Opcodes.ASM9) {
            override fun visitParameter(parameterName: String?, access: Int) {
              if (parameterName == null) {
                methods.add(name + descriptor)
              }
            }
          }
      }
    // no SKIP_DEBUG here, ASM counts MethodParameters as debug info and then skips it
    reader.accept(scanner, ClassReader.SKIP_CODE)
    return methods
  }

  private class MethodParametersStripper(next: ClassVisitor, private val methods: Set<String>) :
    ClassVisitor(Opcodes.ASM9, next) {

    override fun visitMethod(
      access: Int,
      name: String?,
      descriptor: String?,
      signature: String?,
      exceptions: Array<out String>?,
    ): MethodVisitor? {
      val next = super.visitMethod(access, name, descriptor, signature, exceptions)
      // every other method keeps the byte for byte copy ASM does for an untouched method
      if (name + descriptor !in methods) {
        return next
      }
      return object : MethodVisitor(Opcodes.ASM9, next) {
        override fun visitParameter(name: String?, access: Int) = Unit
      }
    }
  }
}
