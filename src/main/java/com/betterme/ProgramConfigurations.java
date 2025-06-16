package com.betterme;

import java.io.IOException;
import java.util.Properties;

public class ProgramConfigurations {
    private static Properties properties;

    private ProgramConfigurations() {}

    public static Properties getConfiguration() {
        if (properties == null) {
            properties = new Properties();
            try {
                properties.load(ProgramConfigurations.class.getResourceAsStream("/config.properties"));
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return properties;
    }
}
