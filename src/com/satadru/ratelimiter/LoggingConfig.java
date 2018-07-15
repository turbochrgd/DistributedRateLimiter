package com.satadru.ratelimiter;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.LogManager;

public class LoggingConfig {
    public static void configureLogging( Level level, final String logConfigurationFile ) {
        try {
            final LogManager logManager = LogManager.getLogManager();
            try ( final InputStream is = new FileInputStream( logConfigurationFile ) ) {
                logManager.readConfiguration( is );
            }
        }
        catch ( Exception e ) {
            // The runtime won't show stack traces if the exception is thrown
            e.printStackTrace();
        }
    }
}
