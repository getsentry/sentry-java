/*
 * Copyright The async-profiler authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.sentry.asyncprofiler.vendor.asyncprofiler.convert;

import static io.sentry.asyncprofiler.vendor.asyncprofiler.convert.Frame.*;

import io.sentry.asyncprofiler.vendor.asyncprofiler.jfr.StackTrace;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
abstract class Classifier {

  enum Category {
    GC("[gc]", TYPE_CPP),
    JIT("[jit]", TYPE_CPP),
    VM("[vm]", TYPE_CPP),
    VTABLE_STUBS("[vtable_stubs]", TYPE_NATIVE),
    NATIVE("[native]", TYPE_NATIVE),
    INTERPRETER("[Interpreter]", TYPE_NATIVE),
    C1_COMP("[c1_comp]", TYPE_C1_COMPILED),
    C2_COMP("[c2_comp]", TYPE_INLINED),
    ADAPTER("[c2i_adapter]", TYPE_INLINED),
    CLASS_INIT("[class_init]", TYPE_CPP),
    CLASS_LOAD("[class_load]", TYPE_CPP),
    CLASS_RESOLVE("[class_resolve]", TYPE_CPP),
    CLASS_VERIFY("[class_verify]", TYPE_CPP),
    LAMBDA_INIT("[lambda_init]", TYPE_CPP);

    final String title;
    final byte type;

    Category(String title, byte type) {
      this.title = title;
      this.type = type;
    }
  }

  public @Nullable Category getCategory(@NotNull StackTrace stackTrace) {
    long[] methods = stackTrace.methods;
    byte[] types = stackTrace.types;

    Category category;
    if ((category = detectGcJit(methods, types)) == null
        && (category = detectClassLoading(methods, types)) == null) {
      category = detectOther(methods, types);
    }
    return category;
  }

  private @Nullable Category detectGcJit(long[] methods, byte[] types) {
    boolean vmThread = false;
    for (int i = types.length; --i >= 0; ) {
      if (types[i] == TYPE_CPP) {
        switch (getMethodName(methods[i], types[i])) {
          case "CompileBroker::compiler_thread_loop":
            return Category.JIT;
          case "GCTaskThread::run":
          case "WorkerThread::run":
            return Category.GC;
          case "java_start":
          case "thread_native_entry":
            vmThread = true;
            break;
        }
      } else if (types[i] != TYPE_NATIVE) {
        break;
      }
    }
    return vmThread ? Category.VM : null;
  }

  private @Nullable Category detectClassLoading(long[] methods, byte[] types) {
    for (int i = 0; i < methods.length; i++) {
      String methodName = getMethodName(methods[i], types[i]);
      if (methodName.equals("Verifier::verify")) {
        return Category.CLASS_VERIFY;
      } else if (methodName.startsWith("InstanceKlass::initialize")) {
        return Category.CLASS_INIT;
      } else if (methodName.startsWith("LinkResolver::")
          || methodName.startsWith("InterpreterRuntime::resolve")
          || methodName.startsWith("SystemDictionary::resolve")) {
        return Category.CLASS_RESOLVE;
      } else if (methodName.endsWith("ClassLoader.loadClass")) {
        return Category.CLASS_LOAD;
      } else if (methodName.endsWith("LambdaMetafactory.metafactory")
          || methodName.endsWith("LambdaMetafactory.altMetafactory")) {
        return Category.LAMBDA_INIT;
      } else if (methodName.endsWith("table stub")) {
        return Category.VTABLE_STUBS;
      } else if (methodName.equals("Interpreter")) {
        return Category.INTERPRETER;
      } else if (methodName.startsWith("I2C/C2I")) {
        return i + 1 < types.length && types[i + 1] == TYPE_INTERPRETED
            ? Category.INTERPRETER
            : Category.ADAPTER;
      }
    }
    return null;
  }

  private @NotNull Category detectOther(long[] methods, byte[] types) {
    boolean inJava = true;
    for (int i = 0; i < types.length; i++) {
      switch (types[i]) {
        case TYPE_INTERPRETED:
          return inJava ? Category.INTERPRETER : Category.NATIVE;
        case TYPE_JIT_COMPILED:
          return inJava ? Category.C2_COMP : Category.NATIVE;
        case TYPE_INLINED:
          inJava = true;
          break;
        case TYPE_NATIVE:
          {
            String methodName = getMethodName(methods[i], types[i]);
            if (methodName.startsWith("JVM_")
                || methodName.startsWith("Unsafe_")
                || methodName.startsWith("MHN_")
                || methodName.startsWith("jni_")) {
              return Category.VM;
            }
            switch (methodName) {
              case "call_stub":
              case "deoptimization":
              case "unknown_Java":
              case "not_walkable_Java":
              case "InlineCacheBuffer":
                return Category.VM;
            }
            if (methodName.endsWith("_arraycopy") || methodName.contains("pthread_cond")) {
              break;
            }
            inJava = false;
            break;
          }
        case TYPE_CPP:
          {
            String methodName = getMethodName(methods[i], types[i]);
            if (methodName.startsWith("Runtime1::")) {
              return Category.C1_COMP;
            }
            break;
          }
        case TYPE_C1_COMPILED:
          return inJava ? Category.C1_COMP : Category.NATIVE;
      }
    }
    return Category.NATIVE;
  }

  protected abstract @NotNull String getMethodName(long method, byte type);
}
