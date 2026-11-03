package io.sentry.gradle

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Returns `name + descriptor` of every method of the class that has a MethodParameters entry
 * without a name. Only those entries make the D8 of AGP 8.0 and older fail, see
 * [StripMethodParameters].
 */
internal fun ClassReader.methodsWithUnnamedParameters(): Set<String> {
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
  accept(scanner, ClassReader.SKIP_CODE)
  return methods
}
