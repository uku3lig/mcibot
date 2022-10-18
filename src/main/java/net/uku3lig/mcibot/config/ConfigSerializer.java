package net.uku3lig.mcibot.config;

import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.function.Supplier;

/**
 * A default config serializer, which saves the config in a TOML file.
 * @param <T> The type of the config
 */
@Slf4j
public class ConfigSerializer<T extends IConfig<T>> {
    private final Class<T> configClass;
    private final File file;
    private final Supplier<T> defaultConfig;

    /**
     * Creates a serializer.
     *
     * @param configClass The class of the config
     * @param file The file to save the config into
     * @param defaultConfig The default config
     */
    public ConfigSerializer(Class<T> configClass, File file, Supplier<T> defaultConfig) {
        this.configClass = configClass;
        this.file = file;
        this.defaultConfig = defaultConfig;
    }

    /**
     * Reads the config from the file.
     * If the file isn't found or is corrupted, the file is overwritten by the default config.
     * @return The deserialized config
     */
    public T deserialize() {
        if (!Files.exists(file.toPath())) {
            return defaultConfig.get();
        }

        try {
            return new Toml().read(file).to(configClass);
        } catch (Exception e) {
            log.warn("A corrupted configuration file was found, overwriting it with the default config");
            serialize(defaultConfig.get());
            return defaultConfig.get();
        }
    }

    public void serialize(T config) {
        try {
            new TomlWriter().write(config, file);
        } catch (IOException e) {
            log.warn("Could not write config", e);
        }
    }
}
