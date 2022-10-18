package net.uku3lig.mcibot.config;

public class Config implements IConfig<Config> {
    @Override
    public Config defaultConfig() {
        return new Config();
    }
}
