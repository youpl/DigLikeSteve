package io.github.youpl.onedonjon.util;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Petits utilitaires de reflexion tolerants (les API tierces changent souvent de signature). */
public final class Reflect {

    private Reflect() {}

    public static Class<?> cls(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    /** Appelle la premiere methode publique {@code name} dont les parametres acceptent {@code args}. */
    public static Object call(Object target, String name, Object... args) throws ReflectiveOperationException {
        Class<?> c = target instanceof Class<?> k ? k : target.getClass();
        boolean isStatic = target instanceof Class<?>;
        for (Method m : c.getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            if (isStatic && !Modifier.isStatic(m.getModifiers())) continue;
            if (!accepts(m.getParameterTypes(), args)) continue;
            return m.invoke(isStatic ? null : target, args);
        }
        throw new NoSuchMethodException(c.getName() + "#" + name + "/" + args.length);
    }

    public static Object staticField(Class<?> c, String field) throws ReflectiveOperationException {
        return c.getField(field).get(null);
    }

    private static boolean accepts(Class<?>[] types, Object[] args) {
        for (int i = 0; i < types.length; i++) {
            Class<?> t = wrap(types[i]);
            if (args[i] != null && !t.isInstance(args[i])) return false;
        }
        return true;
    }

    private static Class<?> wrap(Class<?> t) {
        if (!t.isPrimitive()) return t;
        return switch (t.getName()) {
            case "int" -> Integer.class;
            case "double" -> Double.class;
            case "boolean" -> Boolean.class;
            case "float" -> Float.class;
            case "long" -> Long.class;
            default -> t;
        };
    }
}
