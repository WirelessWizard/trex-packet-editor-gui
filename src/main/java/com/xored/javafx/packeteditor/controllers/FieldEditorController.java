package com.xored.javafx.packeteditor.controllers;

import com.google.common.eventbus.Subscribe;
import com.google.common.io.Files;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.xored.javafx.packeteditor.data.FieldEditorModel;
import com.xored.javafx.packeteditor.data.combined.CombinedField;
import com.xored.javafx.packeteditor.data.user.DocumentFile;
import com.xored.javafx.packeteditor.events.RebuildViewEvent;
import com.xored.javafx.packeteditor.scapy.PacketData;
import com.xored.javafx.packeteditor.service.ConfigurationService;
import com.xored.javafx.packeteditor.service.IMetadataService;
import com.xored.javafx.packeteditor.service.PacketDataService;
import com.xored.javafx.packeteditor.view.ConnectionErrorDialog;
import com.xored.javafx.packeteditor.view.FieldEditorView;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Alert;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class FieldEditorController implements Initializable {

    static Logger logger = LoggerFactory.getLogger(FieldEditorController.class);

    @FXML private StackPane  fieldEditorTopPane;
    @FXML private StackPane  fieldEditorPane;
    @FXML private ScrollPane fieldEditorScrollPane;

    @Inject
    FieldEditorModel model;
    
    @Inject
    IMetadataService metadataService;

    @Inject
    private PacketDataService packetController;
    
    @Inject
    FieldEditorView view;

    @Inject
    ConfigurationService configurationService;
    
    FileChooser fileChooser = new FileChooser();

    @Inject
    @Named("resources")
    private ResourceBundle resourceBundle;
    
    @FXML
    private Text text;

    public IMetadataService getMetadataService() {
        return metadataService;
    }
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        view.setParentPane(fieldEditorPane);
        if (packetController.isInitialized()) {
            if (configurationService.isStandaloneMode()) {
                // TODO: needs to be moved out from controller to app initialization
                Platform.runLater(this::newPacket);
            }
        } else {
            view.displayConnectionError();
        }
    }

    public void showConnectionErrorDialog() {
        ConnectionErrorDialog dialog = new ConnectionErrorDialog();
        dialog.showAndWait();
    }

    public void refreshTitle() {
        String title = resourceBundle.getString("EDITOR_TITLE");
        if (model.getCurrentFile() != null) {
            title += " - " + model.getCurrentFile().getAbsolutePath();
        }

        if (configurationService.isStandaloneMode()) {
            ((Stage)fieldEditorPane.getScene().getWindow()).setTitle(title);
        }
    }

    @Subscribe
    public void handleRebuildViewEvent(RebuildViewEvent event) {
        double val = fieldEditorScrollPane.getVvalue();

        // Workaround for flickering and saving Vscroll:
        // 1) take snapshot of top stackpane
        // 2) create image view with paddings
        // 3) add this image view to stackpane
        //
        // This image view hides scrollpane which is rebuilt
        // Then call view rebuild
        // And last, we set Vscroll and remove image view
        WritableImage snapImage = fieldEditorTopPane.snapshot(new SnapshotParameters(), null);
        ImageView snapView = new ImageView();
        snapView.setImage(snapImage);
        Insets insets = fieldEditorTopPane.getPadding();
        snapView.setViewport(new javafx.geometry.Rectangle2D(insets.getLeft(),
                insets.getTop(),
                snapImage.getWidth() - insets.getLeft() - insets.getRight(),
                snapImage.getHeight() - insets.getTop() - insets.getBottom()));
        fieldEditorTopPane.getChildren().add(snapView);
        // Rebuild content
        view.rebuild(event.getModel());
        
        Platform.runLater(()-> {
            // Save scroll position workaround: runLater inside runLater :)
            Platform.runLater(() -> {
                Platform.runLater(() -> {
                    fieldEditorScrollPane.setVvalue(val);
                    fieldEditorTopPane.getChildren().remove(snapView);
                });
            });
        });
    }

    public void showLoadDialog() {
        initFileChooser();
        fileChooser.setTitle(resourceBundle.getString("OPEN_DIALOG_TITLE"));
        File openFile = fileChooser.showOpenDialog(fieldEditorPane.getScene().getWindow());

        try {
            if (openFile != null) {
                if (openFile.getName().endsWith(DocumentFile.FILE_EXTENSION)) {
                    model.loadDocumentFromFile(openFile);
                } else {
                    loadPcapFile(openFile);
                }
            }
            refreshTitle();
        } catch (Exception e) {
            showError(resourceBundle.getString("LOAD_ERROR"), e);
        }
    }

    public void loadPcapFile(File pcapfile) throws IOException {
        byte[] bytes = Files.toByteArray(pcapfile);
        model.setCurrentFile(pcapfile);
        refreshTitle();
        model.loadDocumentFromPcapData(packetController.read_pcap_packet(bytes));
        // Set window width to scene width
        fitSizeToScene();
    }

    public void loadPcapBinary(byte[] bytes) throws IOException {
        model.loadDocumentFromPcapData(packetController.reconstructPacketFromBinary(bytes));
    }

    public void writeToPcapFile(File file) {
        try {
            writeToPcapFile(file, model.getPkt(), false);
        } catch (Exception e) {
            // Something is wrong, must not be here
            logger.error(e.getMessage());
        }
    }

    public void writeToPcapFile(File file, boolean wantexception) throws Exception {
        writeToPcapFile(file, model.getPkt(), wantexception);
    }

    public void writeToPcapFile(File file, PacketData pkt, boolean wantexception) throws Exception {
        try {
            byte[] pcap_bin = packetController.write_pcap_packet(pkt.getPacketBytes());
            Files.write(pcap_bin, file);
        } catch (Exception e) {
            if (wantexception) {
                throw e;
            }
            else {
                showError(resourceBundle.getString("SAVE_ERROR"), e);
            }
        }
    }

    void showError(String title, Exception e) {
        logger.error("{}: {}", title, e);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText(title);
        alert.initOwner(fieldEditorPane.getScene().getWindow());
        alert.getDialogPane().setExpandableContent(new ScrollPane(new TextArea(title + ": " + e.getMessage())));
        alert.showAndWait();
    }

    public void initFileChooser() {
        String docExt = "*"+DocumentFile.FILE_EXTENSION;
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("TRex Packet editor Files", docExt, "*.pcap", "*.cap"),
                new FileChooser.ExtensionFilter("Packet Editor Files", docExt),
                new FileChooser.ExtensionFilter("Pcap Files", "*.pcap", "*.cap"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));

        File file = model.getCurrentFile();
        if (file != null) {
            fileChooser.setInitialDirectory(file.getParentFile());
            fileChooser.setInitialFileName(file.getName());
        }
    }

    public void showSaveDialog() {
        initFileChooser();
        fileChooser.setTitle(resourceBundle.getString("SAVE_DIALOG_TITLE"));
        java.io.File outFile = fileChooser.showSaveDialog(fieldEditorPane.getScene().getWindow());
        if (outFile != null) {
            try {
                if (outFile.getName().endsWith(DocumentFile.FILE_EXTENSION)) {
                    model.saveDocumentToFile(outFile);
                } else {
                    writeToPcapFile(outFile);
                }
            } catch (Exception e) {
                showError("Failed to save file", e);
            }
        }
    }

    public void selectField(CombinedField field) {
        model.setSelected(field);
    }

    public void setResourceBundle(ResourceBundle resourceBundle) {
        this.resourceBundle = resourceBundle;
    }

    public void newPacket() {
        model.newPacket();
        // Set window width to scene width
        fitSizeToScene();
    }

    public FieldEditorModel getModel() { return model; }

    private void fitSizeToScene() {
        if (configurationService.isStandaloneMode()) {
            fieldEditorPane.getScene().getWindow().sizeToScene();
        }
    }

    public void loadUserModel(String userModelJSON) {
        model.loadDocumentFromJSON(userModelJSON);
    }
}
