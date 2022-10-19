package net.uku3lig.mcibot.util;

import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class ClassScanner {
    private static final String PKG = "net.uku3lig";

    private ClassScanner() {}

    /**
     * Finds the subtypes of the given class, searching only in the parent class' package.
     * @param parent The parent class.
     * @param <T> The type of the class.
     * @return A set of instantiated subtypes.
     */
    public static <T> Set<T> findSubtypes(Class<T> parent) {
        return new Reflections(new ConfigurationBuilder()
                .setUrls(ClasspathHelper.forPackage(PKG))
                .filterInputsBy(new FilterBuilder().includePackage(PKG))
                .setScanners(Scanners.SubTypes))
                .getSubTypesOf(parent).parallelStream()
                .filter(klass -> !(klass.isInterface() || Modifier.isAbstract(klass.getModifiers())))
                .map(klass -> {
                    try {
                        var start = Instant.now();
                        Constructor<? extends T> c = getConstructor(klass);
                        T instance = c.newInstance();
                        log.debug("{} took {} to init", klass.getSimpleName(), Duration.between(start, Instant.now()));
                        return instance;
                    } catch (ExceptionInInitializerError e) {
                        log.error("Class {} threw an exception during instantiation:", klass.getName());
                        e.getCause().printStackTrace();
                        return null;
                    } catch (Exception e) {
                        log.error("Class {} does not seem to have a working constructor", klass.getName());
                        e.printStackTrace();
                        return null;
                    }
                }).filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private static <T> Constructor<? extends T> getConstructor(Class<? extends T> klass) throws NoSuchMethodException {
        try {
            return klass.getConstructor();
        } catch (Exception e) {
            return klass.getDeclaredConstructor();
        }
    }
}
