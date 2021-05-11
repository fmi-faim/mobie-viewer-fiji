package de.embl.cba.mobie2.view;

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;
import de.embl.cba.mobie.bookmark.Bookmark;
import de.embl.cba.mobie.bookmark.BookmarkReader;
import de.embl.cba.mobie.bookmark.write.BookmarkGithubWriter;
import de.embl.cba.mobie.ui.MoBIEOptions;
import de.embl.cba.mobie2.MoBIE2;
import de.embl.cba.tables.FileUtils;
import de.embl.cba.tables.github.GitHubUtils;
import de.embl.cba.tables.github.GitLocation;
import ij.IJ;
import ij.gui.GenericDialog;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static de.embl.cba.mobie2.PathHelpers.getPath;
import static de.embl.cba.tables.FileUtils.*;

public class ViewsSaver {

    private MoBIE2 moBIE2;
    private MoBIEOptions options;

    enum ProjectSaveLocation {
        datasetJson,
        viewsJson
    }

    enum ViewsJsonOptions {
        appendToExisting,
        createNew
    }

    public ViewsSaver(MoBIE2 moBIE2) {
        this.moBIE2 = moBIE2;
        this.options = moBIE2.getOptions();
    }

    private void saveToFileSystem() {
        String jsonPath = null;
        final JFileChooser jFileChooser = new JFileChooser();
        jFileChooser.setFileFilter(new FileNameExtensionFilter("json", "json"));
        if (jFileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            jsonPath = jFileChooser.getSelectedFile().getAbsolutePath();
        }

        if (jsonPath != null) {
            if (!jsonPath.endsWith(".json")) {
                jsonPath += ".json";
            }

            File jsonFile = new File(jsonPath);
            if (jsonFile.exists()) {
                // check if want to append to existing file, otherwise abort
                if (!appendToFileDialog()) {
                    jsonPath = null;
                }
            }

            if (jsonPath != null) {
                // TODO - write
            }
        }
    }

    private void saveToProject() {
        ProjectSaveLocation projectSaveLocation = chooseSaveLocationDialog();
        if (projectSaveLocation != null) {
            if (projectSaveLocation == ProjectSaveLocation.datasetJson) {
                // TODO - save it
            } else {
                // TODO - choose existing view file, or say make new one
                // if make new one, choose a name for it

                if ( isS3(options.values.getProjectLocation()) ) {
                    // TODO - support saving views to s3?
                    IJ.log("View saving aborted - saving directly to s3 is not yet supported!");
                } else {
                    String additionalViewsDirectory = getPath(options.values.getProjectLocation(), options.values.getProjectBranch(), moBIE2.getDatasetName(), "misc", "views");
                    String[] existingViewFiles = getFileNamesFromProject(additionalViewsDirectory);

                    String jsonFileName;
                    if (existingViewFiles != null && existingViewFiles.length > 0) {
                        // TODO - give option to choose existing or make new
                        jsonFileName = chooseViewsJsonDialog(existingViewFiles);
                    } else {
                        jsonFileName = chooseViewsFileNameDialog();
                    }

                    View currentView = moBIE2.getViewerManager().getCurrentView( "test", true);
                    System.out.println("yo");
                    // TODO - write
                }
            }
        }
    }

    public void saveCurrentSettingsAsViewDialog() {
        final GenericDialog gd = new GenericDialog("Save current view");
        gd.addStringField("View name", "name");

        String[] currentUiSelectionGroups = moBIE2.getUserInterface().getUISelectionGroupNames();
        String[] choices = new String[currentUiSelectionGroups.length + 1];
        choices[0] = "Make New Ui Selection Group";
        for (int i = 0; i < currentUiSelectionGroups.length; i++) {
            choices[i + 1] = currentUiSelectionGroups[i];
        }
        gd.addChoice("Ui Selection Group", choices, choices[0]);

        gd.addChoice("Save to", new String[]{FileUtils.FileLocation.Project.toString(),
                FileUtils.FileLocation.FileSystem.toString()}, FileUtils.FileLocation.Project.toString());
        gd.addCheckbox("exclusive", true);
        gd.showDialog();

        if (!gd.wasCanceled()) {
            String viewName = gd.getNextString();
            String uiSelectionGroup = gd.getNextChoice();
            FileUtils.FileLocation fileLocation = FileUtils.FileLocation.valueOf(gd.getNextChoice());
            boolean exclusive = gd.getNextBoolean();

            if (fileLocation == FileUtils.FileLocation.Project) {
                saveToProject();
            } else {
                saveToFileSystem();
            }
        }
    }

    public static boolean appendToFileDialog() {
        int result = JOptionPane.showConfirmDialog(null,
                "This Json file already exists - append view to this file?", "Append to file?",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (result != JOptionPane.YES_OPTION) {
            return false;
        } else {
            return true;
        }
    }

    private ProjectSaveLocation chooseSaveLocationDialog() {
        final GenericDialog gd = new GenericDialog("Save location");
        String[] choices = new String[]{"dataset.json", "views.json"};
        gd.addChoice("Save location:", choices, choices[0]);
        gd.showDialog();

        if (!gd.wasCanceled()) {
            String projectSaveLocation = gd.getNextChoice();
            if (projectSaveLocation.equals("dataset.json")) {
                return ProjectSaveLocation.datasetJson;
            } else if (projectSaveLocation.equals("views.json")) {
                return ProjectSaveLocation.viewsJson;
            }
        }
        return null;
    }

    private ProjectSaveLocation chooseViewJsonOption() {
        final GenericDialog gd = new GenericDialog("Options for view json:");
        String[] choices = new String[]{"Append to existing views json", "Make new views json"};
        gd.addChoice("View json options:", choices, choices[0]);
        gd.showDialog();

        if (!gd.wasCanceled()) {
            String projectSaveLocation = gd.getNextChoice();
            if (projectSaveLocation.equals("dataset.json")) {
                return ProjectSaveLocation.datasetJson;
            } else if (projectSaveLocation.equals("views.json")) {
                return ProjectSaveLocation.viewsJson;
            }
        }
        return null;
    }

    private String chooseViewsFileNameDialog() {
        final GenericDialog gd = new GenericDialog("Choose views json filename");

        gd.addStringField("New view json filename:", "");
        gd.showDialog();

        if (!gd.wasCanceled()) {
            // TODO - check for invalid names e.g. stuff with spaces, punctuation etc...
            String viewFileName =  gd.getNextString();
            if ( !viewFileName.endsWith(".json") ) {
                viewFileName += ".json";
            }
            return viewFileName;
        } else {
            return null;
        }

    }

    private String chooseViewsJsonDialog(String[] viewFileNames) {
        final GenericDialog gd = new GenericDialog("Choose views json");
        String[] choices = new String[viewFileNames.length + 1];
        choices[0] = "Make new views json file";
        for (int i = 0; i < viewFileNames.length; i++) {
            choices[i + 1] = viewFileNames[i];
        }

        gd.addChoice("Choose Views Json:", choices, choices[0]);
        gd.showDialog();

        if (!gd.wasCanceled()) {
            String choice = gd.getNextChoice();
            if (choice.equals("Make new views json file")) {
                choice = chooseViewsFileNameDialog();
                // TODO - check if that file exists already, if it does - abort and log a warning sayin why
            }
            return choice;
        } else {
            return null;
        }

    }

    public static void writeViewsToFile (Gson gson, Type type, File jsonFile, Map< String, Bookmark> bookmarks) throws IOException
    {
        Map<String, Bookmark> bookmarksInFile = new HashMap<>();
        // If json already exists, read existing bookmarks to append new ones
        if (jsonFile.exists()) {
            bookmarksInFile.putAll( BookmarkReader.readBookmarksFromFile(gson, type, jsonFile.getAbsolutePath()));
        }
        bookmarksInFile.putAll(bookmarks);

        try (OutputStream outputStream = new FileOutputStream( jsonFile );
             final JsonWriter writer = new JsonWriter( new OutputStreamWriter(outputStream, "UTF-8")) ) {
            writer.setIndent("	");
            gson.toJson(bookmarksInFile, type, writer);
        }
    }

    public static void writeViewsToGithub(ArrayList< Bookmark > bookmarks, BookmarkReader bookmarkReader ) {
        final GitLocation gitLocation = GitHubUtils.rawUrlToGitLocation( bookmarkReader.getDatasetLocation() );
        gitLocation.path += "misc/bookmarks";

        // BookmarkGithubWriter bookmarkWriter = new BookmarkGithubWriter(gitLocation, bookmarkReader );
        // bookmarkWriter.writeBookmarksToGithub(bookmarks);
    }

}
