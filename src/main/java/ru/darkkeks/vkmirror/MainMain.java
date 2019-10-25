package ru.darkkeks.vkmirror;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainMain {

    private static final Logger logger = LoggerFactory.getLogger(MainMain.class);

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            logger.error("Thread {} didn't handle exception", thread, throwable);
        });

        Injector injector = Guice.createInjector(new VkMirrorModule());
        VkMirror instance = injector.getInstance(VkMirror.class);
        instance.start();
    }
}
