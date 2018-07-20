package io.sentry.marshaller.json.connector.classloading;

/**
 * A simple Classloader helper.
 * <p>
 * Based on:
 * [https://github.com/jwtk/jjwt/
 * blob/8afca0d0df6248a017f6004dfc6f692f0463545c/src/main/java/io/jsonwebtoken/lang/Classes.java]
 */
public final class Classes {
    private static final Classes.ClassLoaderAccessor THREAD_CL_ACCESSOR = new Classes.ExceptionIgnoringAccessor() {
        @Override
        protected ClassLoader doGetClassLoader() throws Throwable {
            return Thread.currentThread().getContextClassLoader();
        }
    };

    private static final Classes.ClassLoaderAccessor CLASS_CL_ACCESSOR = new Classes.ExceptionIgnoringAccessor() {
        @Override
        protected ClassLoader doGetClassLoader() throws Throwable {
            return Classes.class.getClassLoader();
        }
    };

    private static final Classes.ClassLoaderAccessor SYSTEM_CL_ACCESSOR = new Classes.ExceptionIgnoringAccessor() {
        @Override
        protected ClassLoader doGetClassLoader() throws Throwable {
            return ClassLoader.getSystemClassLoader();
        }
    };

    private Classes() {
    }

    /**
     * Attempts to load the specified class name from the current thread's
     * {@link Thread#getContextClassLoader() context class loader}, then the
     * current ClassLoader (<code>Classes.class.getClassLoader()</code>), then the system/application
     * ClassLoader (<code>ClassLoader.getSystemClassLoader()</code>, in that order.  If any of them cannot locate
     * the specified class, an <code>UnknownClassException</code> is thrown (our RuntimeException equivalent of
     * the JRE's <code>ClassNotFoundException</code>.
     *
     * @param fullyQualifiedClassName the fully qualified class name to load
     * @param <T> Class type found.
     * @return the located class
     * @throws UnknownClassException if the class cannot be found.
     */
    @SuppressWarnings("unchecked")
    public static <T> Class<T> forName(String fullyQualifiedClassName) throws UnknownClassException {

        Class clazz = THREAD_CL_ACCESSOR.loadClass(fullyQualifiedClassName);

        if (clazz == null) {
            clazz = CLASS_CL_ACCESSOR.loadClass(fullyQualifiedClassName);
        }

        if (clazz == null) {
            clazz = SYSTEM_CL_ACCESSOR.loadClass(fullyQualifiedClassName);
        }

        if (clazz == null) {
            String msg = "Unable to load class named [" + fullyQualifiedClassName + "] from the thread context, "
                    + "current, or system/application ClassLoaders.  "
                    + "All heuristics have been exhausted.  Class could not be found.";

            throw new UnknownClassException(msg);
        }

        return clazz;
    }

    /**
     * Checks if classname is available in classpath.
     *
     * @param fullyQualifiedClassName classname to search for.
     * @return if class is available in runtime.
     */
    public static boolean isAvailable(String fullyQualifiedClassName) {
        try {
            forName(fullyQualifiedClassName);
            return true;
        } catch (UnknownClassException e) {
            return false;
        }
    }

    /**
     * Instantiate class from given name using no-arguments constructor.
     * @param fullyQualifiedClassName Classname to instantiate.
     * @param <T> Class type.
     * @return New T class instance.
     */
    @SuppressWarnings("unchecked")
    public static <T> T newInstance(String fullyQualifiedClassName) {
        return (T) newInstance(forName(fullyQualifiedClassName));
    }

    /**
     * Instantiate given Class using no-arguments constructor.
     * @param clazz Class to instantiate.
     * @param <T> Class type.
     * @return New T class instance.
     */
    public static <T> T newInstance(Class<T> clazz) {
        if (clazz == null) {
            String msg = "Class method parameter cannot be null.";
            throw new IllegalArgumentException(msg);
        }
        try {
            return clazz.newInstance();
        } catch (Exception e) {
            throw new InstantiationException("Unable to instantiate class [" + clazz.getName() + "]", e);
        }
    }

    private interface ClassLoaderAccessor {
        Class loadClass(String fullyQualifiedClassName);
    }

    private abstract static class ExceptionIgnoringAccessor implements Classes.ClassLoaderAccessor {

        public Class loadClass(String fqcn) {
            Class clazz = null;
            ClassLoader cl = getClassLoader();
            if (cl != null) {
                try {
                    clazz = cl.loadClass(fqcn);
                } catch (ClassNotFoundException e) {
                    //Class couldn't be found by loader
                }
            }
            return clazz;
        }

        protected final ClassLoader getClassLoader() {
            try {
                return doGetClassLoader();
            } catch (Throwable t) {
                //Unable to get ClassLoader
            }
            return null;
        }

        protected abstract ClassLoader doGetClassLoader() throws Throwable;
    }
}
