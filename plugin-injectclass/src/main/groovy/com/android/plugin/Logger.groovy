package com.android.plugin

import org.gradle.api.Project

/**
 * Created by liuk on 2019/3/27
 */
class Logger {

    static Project project

    static void info(String msg) {
        logger(msg, "info")
    }

    static void error(String msg) {
        logger(msg, "error")
    }

    static void warn(String msg) {
        logger(msg, "warn")
    }

    static void debug(String msg) {
        logger(msg, "debug")
    }

    static void trace(String msg) {
        logger(msg, "trace")
    }

    static void logger(String msg, String type) {
        if (project == null)
            println(type + " : " + msg)
        else {
            switch (type) {
                case "info":
                    project.logger.info(msg)
                    break
                case "error":
                    project.logger.error(msg)
                    break
                case "warn":
                    project.logger.warn(msg)
                    break
                case "debug":
                    project.logger.debug(msg)
                    break
                case "trace":
                    project.logger.trace(msg)
                    break
            }
        }
    }

}

