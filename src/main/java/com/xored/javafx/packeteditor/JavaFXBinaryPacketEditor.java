package com.xored.javafx.packeteditor;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.xored.javafx.packeteditor.guice.GuiceModule;

import java.io.IOException;
import java.io.InputStream;

import com.xored.javafx.packeteditor.remote.ScapyServerClient;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaFXBinaryPacketEditor extends Application {
    static Logger log = LoggerFactory.getLogger(JavaFXBinaryPacketEditor.class);

    public static void main(String[] args) {
        JavaFXBinaryPacketEditor.launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        log.debug("Running app");
        Injector injector = Guice.createInjector(new GuiceModule());
        FXMLLoader fxmlLoader = injector.getInstance(FXMLLoader.class);
        fxmlLoader.setLocation(ClassLoader.getSystemResource("com/xored/javafx/packeteditor/gui/JavaFXBinaryPacketEditor.fxml"));
        Parent parent = fxmlLoader.load();
        Scene scene = new Scene(parent);
        scene.getStylesheets().add(ClassLoader.getSystemResource("styles/main.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.setTitle("JavaFX 8 Binary Packet Editor");
        primaryStage.show();
        primaryStage.setOnCloseRequest(e -> {
            System.exit(0);
        });
    }
}
