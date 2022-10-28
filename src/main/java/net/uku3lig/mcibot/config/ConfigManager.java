package net.uku3lig.mcibot.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

/**
 * Manages a config, by holding it, saving it and loading it.
 * @param <T> The type of the config
 */
@Slf4j
public class ConfigManager<T extends IConfig<T>> {
    /**
     * The config held by the manager.
     */
    @Getter
    private final T config;

    /**
     * Creates a manager.
     * @param config The initial config
     */
    public ConfigManager(T config) {
        this.config = config;
    }

    /**
     * Creates a manager which provides an initial config by deserializing the file.
     * @param serializer The serializer
     */
    public ConfigManager(ConfigSerializer<T> serializer) {
        this(serializer.deserialize());
    }

    /**
     * Creates a default config manager, with a default config serializer.
     * The config will be saved to and read from <code>./config/[name].toml</code> (without the brackets).
     *
     * @param configClass The class of the config
     * @param name The name of the config, used for the filename
     * @return The generated config manager
     * @param <T> The type of the config
     */
    public static <T extends IConfig<T>> ConfigManager<T> create(Class<T> configClass, String name) {
        String filename = "./" + name + ".toml";
        Supplier<T> defaultConfig = () -> newInstance(configClass).defaultConfig();
        return new ConfigManager<>(new ConfigSerializer<>(configClass, new File(filename), defaultConfig));
    }

    /**
     * Creates an instance of a given config class. Only works if the class has a public, no-arg constructor.
     *
     * @param klass The class to be instantiated
     * @return An instance of the class
     * @param <T> The type of the class
     * @see ConfigManager#create(Class, String) ConfigManager#create(Class, String)
     */
    private static <T extends IConfig<T>> T newInstance(Class<T> klass) {
        try {
            Constructor<T> constructor = klass.getConstructor();
            return constructor.newInstance();
        } catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException | InstantiationException e) {
            log.error("{} does not have a public no-arg constructor!", klass.getName());
            throw new NoSuchElementException(e);
        } catch (Exception e) {
            log.error("Could not instantiate class {}", klass.getName());
            throw new IllegalArgumentException(e);
        }
    }
}
