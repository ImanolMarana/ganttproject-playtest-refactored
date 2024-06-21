/*
GanttProject is an opensource project management tool.
Copyright (C) 2003-2011 Alexandre Thomas, GanttProject team

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 3
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package net.sourceforge.ganttproject;

import biz.ganttproject.core.model.task.TaskDefaultColumn;
import biz.ganttproject.core.option.BooleanOption;
import biz.ganttproject.core.option.ChangeValueListener;
import biz.ganttproject.core.option.GPOption;
import biz.ganttproject.core.option.GPOptionGroup;
import biz.ganttproject.core.option.ListOption;
import com.google.common.base.Objects;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.common.xml.XmlEscapers;
import net.sourceforge.ganttproject.document.DocumentManager;
import net.sourceforge.ganttproject.gui.UIConfiguration;
import net.sourceforge.ganttproject.gui.options.model.GP1XOptionConverter;
import net.sourceforge.ganttproject.io.CSVOptions;
import net.sourceforge.ganttproject.io.GanttXMLOpen;
import net.sourceforge.ganttproject.io.OptionSaver;
import net.sourceforge.ganttproject.io.SaverBase;
import net.sourceforge.ganttproject.parser.RoleTagHandler;
import net.sourceforge.ganttproject.roles.Role;
import net.sourceforge.ganttproject.roles.RoleManager;
import net.sourceforge.ganttproject.roles.RoleSet;
import net.sourceforge.ganttproject.util.ColorConvertion;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

import javax.swing.*;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.awt.*;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class is able to load and save options on the file
 */
public class GanttOptions extends SaverBase {

  private int x = 0, y = 0, width = 800, height = 600;
  private boolean isMaximized;

  private boolean isloaded;

  private boolean automatic;

  private boolean redline;

  private int lockDAVMinutes;

  private String xslDir;

  private String xslFo;

  private String workingDir = "";

  private final RoleManager myRoleManager;

  private UIConfiguration myUIConfig;

  private int toolBarPosition;

  private boolean bShowStatusBar;

  private String iconSize;

  public static final int ICONS = 0;

  public static final int TEXT = 2;

  private int buttonsshow;

  /**
   * Export options.
   */
  private boolean bExportName;

  private boolean bExportComplete;

  private boolean bExportRelations;

  private boolean bExport3DBorders;

  /**
   * CSV export options.
   */
  private CSVOptions csvOptions;

  private final Map<String, GPOption<?>> myGPOptions = Maps.newLinkedHashMap();
  private final Map<String, GP1XOptionConverter> myTagDotAttribute_Converter = new HashMap<String, GP1XOptionConverter>();

  private final DocumentManager myDocumentManager;

  private final PluginPreferencesImpl myPluginPreferencesRootNode;

  private String myVersion;

  private final ExecutorService mySaveExecutor = Executors.newSingleThreadExecutor();
  private final AtomicBoolean mySaveScheduled = new AtomicBoolean(true);

  private final ChangeValueListener myOnOptionValueChange = event -> {
    if (!Objects.equal(event.getOldValue(), event.getNewValue())) {
      if (!mySaveScheduled.get()) {
        mySaveScheduled.set(true);
        mySaveExecutor.submit(() -> {
          GanttOptions.this.save();
          mySaveScheduled.set(false);
        });
      }
    }
  };

  public GanttOptions(RoleManager roleManager, DocumentManager documentManager, boolean isOnlyViewer) {
    myDocumentManager = documentManager;
    myRoleManager = roleManager;
    myPluginPreferencesRootNode = new PluginPreferencesImpl(null, "");
    initDefault();
    this.workingDir = System.getProperty("user.home");
  }

  public Preferences getPluginPreferences() {
    return myPluginPreferencesRootNode;
  }

  /**
   * Initialize the options with default values.
   */
  public void initDefault() {
    automatic = false;
    redline = false;
    lockDAVMinutes = 240;
    xslDir = String.valueOf(GanttOptions.class.getResource("/xslt"));
    xslFo = String.valueOf(GanttOptions.class.getResource("/xslfo/ganttproject.xsl"));
    toolBarPosition = JToolBar.HORIZONTAL;
    bShowStatusBar = true;
    // must be 16 small, 24 for big (32 for extra big not directly include on
    // UI)
    iconSize = "16";
    buttonsshow = GanttOptions.ICONS;

    // Export options
    bExportName = true;
    bExportComplete = true;
    bExportRelations = true;
    bExport3DBorders = false;

    // CSV export options
    csvOptions = new CSVOptions();
  }

  public static File getOptionsFile() {
    return new File(System.getProperty("user.home") + System.getProperty("file.separator") + ".ganttproject");
  }


  /**
   * Save the options file
   */
  public void save() {
    try {
      File file = getOptionsFile();
      ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
      doSave(outBuffer);
      BufferedOutputStream outFile = new BufferedOutputStream(new FileOutputStream(file));
      ByteStreams.copy(new ByteArrayInputStream(outBuffer.toByteArray()), outFile);
      outFile.flush();
      outFile.close();
    } catch (Throwable e) {
      GPLogger.log(e);
    }
  }

  private void doSave(OutputStream out) throws Exception {
    final TransformerHandler handler = ((SAXTransformerFactory) SAXTransformerFactory.newInstance()).newTransformerHandler();
    Transformer serializer = handler.getTransformer();
    serializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    serializer.setOutputProperty(OutputKeys.INDENT, "yes");
    serializer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
    handler.setResult(new StreamResult(out));
    handler.startDocument();
    // handler.startDTD("ganttproject.sourceforge.net",
    // "-//GanttProject.org//DTD GanttProject-1.x//EN",
    // "http://ganttproject.sourceforge.net/dtd/ganttproject.dtd");
    // handler.endDTD();

    final AttributesImpl attrs = new AttributesImpl();
    addAttribute("version", GPVersion.getCurrentVersionNumber(), attrs);
    handler.startElement("", "ganttproject-options", "ganttproject-options", attrs);

    attrs.clear();
    // write the task Color

    // Color color = getUIConfiguration().getTaskColor();
    // attrs.addAttribute("", "red", "red", "CDATA", "" + color.getRed());
    // attrs.addAttribute("", "green", "green", "CDATA", ""
    // + color.getGreen());
    // attrs.addAttribute("", "blue", "blue", "CDATA", ""
    // + color.getBlue());
    // handler.startElement("", "task-color", "task-color", attrs);
    // handler.endElement("", "task-color", "task-color"); attrs.clear();

    Color resourceColor = myUIConfig.getResourceColor();
    if (resourceColor != null) {
      attrs.addAttribute("", "resources", "resources", "CDATA", ColorConvertion.getColor(resourceColor));
    }
    Color resourceOverloadColor = myUIConfig.getResourceOverloadColor();
    if (resourceOverloadColor != null) {
      attrs.addAttribute("", "resourcesOverload", "resourcesOverload", "CDATA",
          ColorConvertion.getColor(resourceOverloadColor));
    }
    Color resourceUnderloadColor = myUIConfig.getResourceUnderloadColor();
    if (resourceUnderloadColor != null) {
      attrs.addAttribute("", "resourcesUnderload", "resourcesUnderload", "CDATA",
          ColorConvertion.getColor(resourceUnderloadColor));
    }
    Color weekEndColor = myUIConfig.getWeekEndColor();
    if (weekEndColor != null) {
      attrs.addAttribute("", "weekEnd", "weekEnd", "CDATA", ColorConvertion.getColor(weekEndColor));
    }
    Color daysOffColor = myUIConfig.getDayOffColor();
    if (daysOffColor != null) {
      attrs.addAttribute("", "daysOff", "daysOff", "CDATA", ColorConvertion.getColor(daysOffColor));
    }
    handler.startElement("", "colors", "colors", attrs);
    handler.endElement("", "colors", "colors");
    attrs.clear();

    // Geometry of the window
    addAttribute("x", "" + x, attrs);
    addAttribute("y", "" + y, attrs);
    addAttribute("width", "" + width, attrs);
    addAttribute("height", "" + height, attrs);
    addAttribute("maximized", String.valueOf(this.isMaximized), attrs);
    emptyElement("geometry", attrs, handler);

    // ToolBar position
    addAttribute("position", "" + toolBarPosition, attrs);
    addAttribute("icon-size", "" + iconSize, attrs);
    addAttribute("show", "" + buttonsshow, attrs);
    emptyElement("toolBar", attrs, handler);
    addAttribute("show", "" + bShowStatusBar, attrs);
    emptyElement("statusBar", attrs, handler);

    // Export options
    addAttribute("name", "" + bExportName, attrs);
    addAttribute("complete", "" + bExportComplete, attrs);
    addAttribute("border3d", "" + bExport3DBorders, attrs);
    addAttribute("relations", "" + bExportRelations, attrs);
    emptyElement("export", attrs, handler);

    // csv export options
    startElement("csv-export", attrs, handler);
    addAttribute("fixed", "" + csvOptions.bFixedSize, attrs);
    addAttribute("separatedChar", "" + csvOptions.sSeparatedChar, attrs);
    addAttribute("separatedTextChar", "" + csvOptions.sSeparatedTextChar, attrs);
    emptyElement("csv-general", attrs, handler);

    endElement("csv-export", handler);

    addAttribute("id", "csv-export", attrs);
    startElement("view", attrs, handler);
    for (Map.Entry<String, BooleanOption> entry : csvOptions.getTaskOptions().entrySet()) {
      if (entry.getValue().isChecked()) {
        addAttribute("id", XmlEscapers.xmlAttributeEscaper().escape(entry.getKey()), attrs);
        emptyElement("field", attrs, handler);
      }
    }
    startElement("resources", attrs, handler);
    for (Map.Entry<String, BooleanOption> entry : csvOptions.getResourceOptions().entrySet()) {
      if (entry.getValue().isChecked()) {
        addAttribute("id", XmlEscapers.xmlAttributeEscaper().escape(entry.getKey()), attrs);
        emptyElement("field", attrs, handler);
      }
    }
    endElement("resources", handler);
    if (csvOptions.getBomOption().getValue()) {
      new OptionSaver().saveOption(
          "write-bom", csvOptions.getBomOption(), handler);
    }
    endElement("view", handler);

    // automatic popup launch
    addAttribute("value", "" + automatic, attrs);
    emptyElement("automatic-launch", attrs, handler);
    // automatic tips of the day launch
    // Should WebDAV resources be locked, when opening them?
    addAttribute("value", "" + lockDAVMinutes, attrs);
    emptyElement("lockdavminutes", attrs, handler);
    // write the xsl directory
    addAttribute("dir", xslDir, attrs);
    emptyElement("xsl-dir", attrs, handler);
    // write the xslfo directory
    addAttribute("file", xslFo, attrs);
    emptyElement("xsl-fo", attrs, handler);
    // write the working directory directory
    addAttribute("dir", workingDir, attrs);
    emptyElement("working-dir", attrs, handler);
    // The last opened files
    {
      startElement("files", attrs, handler);

      for (String recent : myDocumentManager.getRecentDocuments()) {
        addAttribute("path", recent, attrs);
        emptyElement("file", attrs, handler);
      }
      endElement("files", handler);
    }

    //addAttribute("category", "chart-main", attrs);
    //addAttribute("spec", getFontSpec(getUIConfiguration().getChartMainFont()), attrs);
    //emptyElement("font", attrs, handler);

    saveRoleSets(handler);
    new OptionSaver().saveOptionMap(myGPOptions.entrySet(), handler);
    savePreferences(myPluginPreferencesRootNode.node("/configuration"), handler);
    savePreferences(myPluginPreferencesRootNode.node("/instance"), handler);
    endElement("ganttproject-options", handler);

    GPLogger.log("[GanttOptions] save(): finished!!");
    handler.endDocument();
  }

  private void savePreferences(Preferences node, TransformerHandler handler) throws BackingStoreException, SAXException {
    AttributesImpl attrs = new AttributesImpl();
    startElement(node.name(), attrs, handler);
    String[] keys = node.keys();
    for (String key : keys) {
      addAttribute("name", key, attrs);
      addAttribute("value", node.get(key, ""), attrs);
      emptyElement("option", attrs, handler);
    }
    String[] children = node.childrenNames();
    for (String child : children) {
      savePreferences(node.node(child), handler);
    }
    endElement(node.name(), handler);
  }

  private String getFontSpec(Font font) {
    return font.getFamily() + "-" + getFontStyle(font) + "-" + font.getSize();
  }

  private String getFontStyle(Font font) {
    String result;
    final int BOLDITALIC = Font.BOLD + Font.ITALIC;
    switch (font.getStyle()) {
      case Font.PLAIN: {
        result = "plain";
        break;
      }
      case Font.BOLD: {
        result = "bold";
        break;
      }
      case Font.ITALIC: {
        result = "italic";
        break;
      }
      case BOLDITALIC: {
        result = "bolditalic";
        break;
      }
      default: {
        throw new RuntimeException("Illegal value of font style. style=" + font.getStyle() + " font=" + font);
      }
    }
    return result;
  }

  public String correct(String s) {
    String res;
    res = s.replaceAll("&", "&#38;");
    res = res.replaceAll("<", "&#60;");
    res = res.replaceAll(">", "&#62;");
    res = res.replaceAll("/", "&#47;");
    res = res.replaceAll("\"", "&#34;");
    return res;
  }

  /**
   * Load the options file
   */
  public boolean load() {
    // Use an instance of ourselves as the SAX event handler
    DefaultHandler handler = new GanttXMLOptionsParser();

    // Use the default (non-validating) parser
    SAXParserFactory factory = SAXParserFactory.newInstance();
    try {
      String sFileName = ".ganttproject";
      /*
       * if(System.getProperty("os.name").startsWith("Windows") ||
       * System.getProperty("os.name").startsWith("Mac")) sFileName =
       * "ganttproject.ini";
       */

      File file = new File(System.getProperty("user.home") + System.getProperty("file.separator") + sFileName);
      if (!file.exists()) {
        return false;
      }

      myDocumentManager.clearRecentDocuments();

      // Parse the input
      SAXParser saxParser = factory.newSAXParser();
      saxParser.parse(file, handler);

      loadRoleSets(file);

    } catch (Exception e) {
      if (!GPLogger.log(e)) {
        e.printStackTrace(System.err);
      }
      return false;
    }

    isloaded = true;
    mySaveScheduled.set(false);
    return true;
  }

  private void loadRoleSets(File optionsFile) {
    GanttXMLOpen loader = new GanttXMLOpen(null);

    loader.addTagHandler(new RoleTagHandler(getRoleManager()));
    loader.load(optionsFile);
  }

  private void saveRoleSets(TransformerHandler handler) throws TransformerFactoryConfigurationError, SAXException {
    RoleSet[] roleSets = getRoleManager().getRoleSets();
    for (RoleSet roleSet : roleSets) {
      AttributesImpl attrs = new AttributesImpl();
      attrs.addAttribute("", "roleset-name", "roleset-name", "CDATA", roleSet.getName());
      handler.startElement("", "roles", "roles", attrs);
      saveRoles(roleSet, handler);
      handler.endElement("", "roles", "roles");
    }
  }

  private void saveRoles(RoleSet roleSet, TransformerHandler handler) throws SAXException {
    Role[] roles = roleSet.getRoles();
    AttributesImpl attrs = new AttributesImpl();
    for (Role next : roles) {
      addAttribute("id", "" + next.getID(), attrs);
      addAttribute("name", next.getName(), attrs);
      emptyElement("role", attrs, handler);
    }

  }

  public UIConfiguration getUIConfiguration() {
    if (myUIConfig == null) {
      myUIConfig = new UIConfiguration(new Color(140, 182, 206), redline);
    }
    return myUIConfig;
  }

  private RoleManager getRoleManager() {
    return myRoleManager;
  }

  /**
   * Class to parse the xml option file
   */
  class GanttXMLOptionsParser extends DefaultHandler {

    private PluginOptionsHandler myPluginOptionsHandler;
    private ListOption myContextOption;
    private StringBuilder myCdataBuffer = new StringBuilder();
    private Map<String, BooleanOption> myContextViewOptions = null;

    @Override
    public void startElement(String namespaceURI, String sName, // simple name
                             String qName, // qualified name
                             Attributes attrs) throws SAXException {

        if (handleGanttProjectOptions(qName, attrs)) {
            return;
        }
        if (handlePluginOptions(qName, namespaceURI, sName, attrs)) {
            return;
        }

        handleOptionElement(qName, attrs);
        handleViewOptions(qName, attrs);
        handleResourceOptions(qName);
        handleFieldOptions(qName, attrs);
        handleAttributes(qName, attrs);
    }
    
    private boolean handleGanttProjectOptions(String qName, Attributes attrs) {
        if ("ganttproject-options".equals(qName)) {
            myVersion = attrs.getValue("version");
            return true;
        }
        return false;
    }

    private boolean handlePluginOptions(String qName, String namespaceURI, String sName, Attributes attrs) {
        if ("configuration".equals(qName) || "instance".equals(qName)) {
            myPluginOptionsHandler = new PluginOptionsHandler(myPluginPreferencesRootNode);
        }
        if (myPluginOptionsHandler != null) {
            myPluginOptionsHandler.startElement(namespaceURI, sName, qName, attrs);
            return true;
        }
        return false;
    }

    private void handleOptionElement(String qName, Attributes attrs) {
        if ("option".equals(qName)) {
            String id = attrs.getValue("id");
            GPOption option;
            if (id.equals(csvOptions.getBomOption().getID())) {
                option = csvOptions.getBomOption();
            } else {
                option = myGPOptions.get(id);
            }
            if (option instanceof ListOption) {
                myContextOption = (ListOption) option;
            } else {
                if (option != null) {
                    option.loadPersistentValue(attrs.getValue("value"));
                }
            }
        }
    }

    private void handleViewOptions(String qName, Attributes attrs) {
        if ("view".equals(qName)) {
            String viewId = attrs.getValue("id");
            if ("csv-export".equals(viewId)) {
                myContextViewOptions = csvOptions.getTaskOptions();
                resetContextViewOptions();
            }
        }
    }

    private void handleResourceOptions(String qName) {
        if ("resources".equals(qName) && myContextViewOptions != null) {
            myContextViewOptions = csvOptions.getResourceOptions();
            resetContextViewOptions();
        }
    }

    private void handleFieldOptions(String qName, Attributes attrs) {
        if ("field".equals(qName) && myContextViewOptions != null) {
            BooleanOption option = myContextViewOptions.get(attrs.getValue("id"));
            if (option != null) {
                option.setValue(true);
            }
        }
    }

    private void handleAttributes(String qName, Attributes attrs) {
        if (attrs != null) {
            for (int i = 0; i < attrs.getLength(); i++) {
                handleAttribute(qName, attrs, i);
            }
        }
    }
    
    private void handleAttribute(String qName, Attributes attrs, int index) {
        String aName = attrs.getQName(index);
        String value = attrs.getValue(index);

        String tagDotAttribute = qName + "." + aName;
        GP1XOptionConverter converter = myTagDotAttribute_Converter.get(tagDotAttribute);
        if (converter != null) {
            converter.loadValue(value);
            return;
        }

        handleSpecificAttributes(qName, aName, value);
    }

    private void resetContextViewOptions() {
        for (BooleanOption o : myContextViewOptions.values()) {
            o.setValue(false);
        }
    }
    
    private void handleSpecificAttributes(String qName, String aName, String value) {
        switch (qName) {
            case "task-color":
                handleTaskColorAttributes(aName, value);
                break;
            case "geometry":
                handleGeometryAttributes(aName, value);
                break;
            case "file":
                handleFileAttributes(aName, value);
                break;
            case "automatic-launch":
                handleAutomaticLaunchAttributes(aName, value);
                break;
            case "lockdavminutes":
                handleLockDavMinutesAttributes(aName, value);
                break;
            case "xsl-dir":
                handleXslDirAttributes(aName, value);
                break;
            case "xsl-fo":
                handleXslFoAttributes(aName, value);
                break;
            case "working-dir":
                handleWorkingDirAttributes(aName, value);
                break;
            case "toolBar":
                handleToolBarAttributes(aName, value);
                break;
            case "statusBar":
                handleStatusBarAttributes(aName, value);
                break;
            case "export":
                handleExportAttributes(aName, value);
                break;
            case "colors":
                handleColorsAttributes(aName, value);
                break;
            case "csv-general":
                handleCsvGeneralAttributes(aName, value);
                break;
            case "csv-tasks":
                handleCsvTasksAttributes(aName, value);
                break;
            case "csv-resources":
                handleCsvResourceAttributes(aName, value);
                break;
        }
    }
    
    private void handleTaskColorAttributes(String aName, String value) {
        int r = 0, g = 0, b = 0;
        if (aName.equals("red")) {
            r = Integer.valueOf(value).hashCode();
        } else if (aName.equals("green")) {
            g = Integer.valueOf(value).hashCode();
        } else if (aName.equals("blue")) {
            b = Integer.valueOf(value).hashCode();
        }
    }
    
    private void handleGeometryAttributes(String aName, String value) {
        switch (aName) {
            case "x":
                x = Integer.valueOf(value).hashCode();
                break;
            case "y":
                y = Integer.valueOf(value).hashCode();
                break;
            case "width":
                width = Integer.valueOf(value).hashCode();
                break;
            case "height":
                height = Integer.valueOf(value).hashCode();
                break;
            case "maximized":
                isMaximized = Boolean.parseBoolean(value);
                break;
        }
    }

    private void handleFileAttributes(String aName, String value) {
        if (aName.equals("path")) {
            myDocumentManager.addToRecentDocuments(value);
        }
    }
    
    private void handleAutomaticLaunchAttributes(String aName, String value) {
        if (aName.equals("value")) {
            automatic = Boolean.parseBoolean(value);
        }
    }
    
    private void handleLockDavMinutesAttributes(String aName, String value) {
        if (aName.equals("value")) {
            lockDAVMinutes = Integer.parseInt(value);
        }
    }

    private void handleXslDirAttributes(String aName, String value) {
        if (aName.equals("dir")) {
            if (new File(value).exists())
                xslDir = value;
        }
    }

    private void handleXslFoAttributes(String aName, String value) {
        if (aName.equals("file")) {
            if (new File(value).exists())
                xslFo = value;
        }
    }

    private void handleWorkingDirAttributes(String aName, String value) {
        if (aName.equals("dir")) {
            if (new File(value).exists())
                workingDir = value;
        }
    }

    private void handleToolBarAttributes(String aName, String value) {
        switch (aName) {
            case "position":
                toolBarPosition = Integer.parseInt(value);
                break;
            case "icon-size":
                iconSize = value;
                break;
            case "show":
                buttonsshow = Integer.parseInt(value);
                break;
        }
    }

    private void handleStatusBarAttributes(String aName, String value) {
        if (aName.equals("show"))
            bShowStatusBar = Boolean.parseBoolean(value);
    }

    private void handleExportAttributes(String aName, String value) {
        switch (aName) {
            case "name":
                bExportName = Boolean.parseBoolean(value);
                break;
            case "complete":
                bExportComplete = Boolean.parseBoolean(value);
                break;
            case "relations":
                bExportRelations = Boolean.parseBoolean(value);
                break;
            case "border3d":
                bExport3DBorders = Boolean.parseBoolean(value);
                break;
        }
    }

    private void handleColorsAttributes(String aName, String value) {
        switch (aName) {
            case "resources":
                setResourceColor(ColorConvertion.determineColor(value));
                break;
            case "resourcesOverload":
                setResourceOverloadColor(ColorConvertion.determineColor(value));
                break;
            case "resourcesUnderload":
                setResourceUnderloadColor(ColorConvertion.determineColor(value));
                break;
            case "weekEnd":
                setWeekEndColor(ColorConvertion.determineColor(value));
                break;
            case "daysOff":
                setDaysOffColor(ColorConvertion.determineColor(value));
                break;
        }
    }

    private void handleCsvGeneralAttributes(String aName, String value) {
        switch (aName) {
            case "fixed":
                csvOptions.bFixedSize = Boolean.parseBoolean(value);
                break;
            case "separatedChar":
                csvOptions.sSeparatedChar = value;
                break;
            case "separatedTextChar":
                csvOptions.sSeparatedTextChar = value;
                break;
        }
    }

    private void handleCsvTasksAttributes(String aName, String value) {
        switch (aName) {
            case "id":
                csvOptions.getTaskOptions().get(TaskDefaultColumn.ID.getStub().getID()).setValue(Boolean.valueOf(value));
                break;
            case "name":
                csvOptions.getTaskOptions().get(TaskDefaultColumn.NAME.getStub().getID()).setValue(Boolean.valueOf(value));
                break;
            case "start-date":
                csvOptions.getTaskOptions().get(TaskDefaultColumn.BEGIN_DATE.getStub().getID()).setValue(Boolean.valueOf(value));
                break;
            case "end-date":
                csvOptions.getTaskOptions().get(TaskDefaultColumn.END_DATE.getStub().getID()).setValue(Boolean.valueOf(value));
                break;
            case "percent":
                csvOptions.getTaskOptions().get(TaskDefaultColumn.COMPLETION.getStub().getID()).setValue(Boolean.valueOf(value));
                break;
            case "duration":
                csvOptions.getTaskOptions().get(TaskDefaultColumn.DURATION.getStub().getID()).setValue(Boolean.valueOf(value));
                break;
            case "color":
                csvOptions.getTaskOptions().get(TaskDefaultColumn.COLOR.getStub().getID()).setValue(Boolean.valueOf(value));
                break;
            case "webLink":
                csvOptions.getTaskOptions().get("webLink").setValue(Boolean.valueOf(value));
                break;
            case "resources":
                csvOptions.getTaskOptions().get("resources").setValue(Boolean.valueOf(value));
                break;
            case "notes":
                csvOptions.getTaskOptions().get("notes").setValue(Boolean.valueOf(value));
                break;
        }
    }

    private void handleCsvResourceAttributes(String aName, String value) {
        Boolean bValue = Boolean.valueOf(value);
        switch (aName) {
            case "id":
                csvOptions.getResourceOptions().get("id").setValue(bValue);
                break;
            case "name":
                csvOptions.getResourceOptions().get(ResourceDefaultColumn.NAME.getStub().getID()).setValue(bValue);
                break;
            case "mail":
                csvOptions.getResourceOptions().get(ResourceDefaultColumn.EMAIL.getStub().getID()).setValue(bValue);
                break;
            case "phone":
                csvOptions.getResourceOptions().get(ResourceDefaultColumn.PHONE.getStub().getID()).setValue(bValue);
                break;
            case "role":
                csvOptions.getResourceOptions().get(ResourceDefaultColumn.ROLE.getStub().getID()).setValue(bValue);
                break;
        }
    }

//Refactoring end

    @Override
    public void endElement(String uri, String localName, String name) throws SAXException {
      if (myPluginOptionsHandler != null) {
        myPluginOptionsHandler.endElement(uri, localName, name);
      }
      if ("configuration".equals(name) || "instance".equals(name)) {
        myPluginOptionsHandler = null;
      }
      if (myContextOption != null) {
        myContextOption.loadPersistentValue(myCdataBuffer.toString());
        myContextOption = null;
        myCdataBuffer = new StringBuilder();
      }
      if ("view".equals(name)) {
        myContextViewOptions = null;
      }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
      if (myContextOption != null) {
        myCdataBuffer.append(ch, start, length);
      }
    }
  }

  /**
   * @return true is options are loaded from the options file.
   */
  public boolean isLoaded() {
    return isloaded;
  }

  /**
   * @return the top left x position of the window.
   */
  public int getX() {
    return x;
  }

  /**
   * @return the top left y position of the window.
   */
  public int getY() {
    return y;
  }

  /**
   * @return the width of the window.
   */
  public int getWidth() {
    return width;
  }

  /**
   * @return the height of the window.
   */
  public int getHeight() {
    return height;
  }

  public boolean isMaximized() {
    return this.isMaximized;
  }

  /**
   * @return the csvOptions.
   */
  public CSVOptions getCSVOptions() {
    return csvOptions;
  }

  /**
   * @return the size of the icons on the toolbar.
   */
  public String getIconSize() {
    return iconSize;
  }

  /**
   * Set a new icon size. Must be 16, 24 (or 32 exceptionnally)
   */
  public void setIconSize(String sIconSize) {
    iconSize = sIconSize;
  }

  /**
   * Set new window position (top left corner)
   */
  public void setWindowPosition(int x, int y) {
    this.x = x;
    this.y = y;
  }

  /**
   * Set new window position (top left corner)
   */
  public void setWindowSize(int width, int height, boolean isMaximized) {
    this.width = width;
    this.height = height;
    this.isMaximized = isMaximized;
  }

  /**
   * set a new default resources color.
   */
  public void setResourceColor(Color color) {
    getUIConfiguration().setResourceColor(color);
  }

  /**
   * set a new resources overload tasks color.
   */
  public void setResourceOverloadColor(Color color) {
    getUIConfiguration().setResourceOverloadColor(color);
  }

  /**
   * set a new resources underload tasks color.
   */
  public void setResourceUnderloadColor(Color color) {
    getUIConfiguration().setResourceUnderloadColor(color);
  }

  /**
   * Set a new week end color.
   */
  public void setWeekEndColor(Color color) {
    getUIConfiguration().setWeekEndColor(color);
  }

  /**
   * Set a new week end color.
   */
  public void setDaysOffColor(Color color) {
    getUIConfiguration().setDayOffColor(color);
  }

  public void setUIConfiguration(UIConfiguration uiConfiguration) {
    this.myUIConfig = uiConfiguration;
  }

  public void addOptionGroups(GPOptionGroup... optionGroups) {
    for (GPOptionGroup group : optionGroups) {
      addOptions(group);
    }
  }

  public void addOptionGroups(Iterable<GPOptionGroup> optionGroups) {
    for (GPOptionGroup group : optionGroups) {
      addOptions(group);
    }
  }

  public void addOptions(GPOptionGroup optionGroup) {
    GPOption[] options = optionGroup.getOptions();
    for (GPOption nextOption : options) {
      nextOption.addChangeValueListener(myOnOptionValueChange);
      myGPOptions.put(optionGroup.getID() + "." + nextOption.getID(), nextOption);
      if (nextOption instanceof GP1XOptionConverter) {
        GP1XOptionConverter nextConverter = (GP1XOptionConverter) nextOption;
        myTagDotAttribute_Converter.put(nextConverter.getTagName() + "." + nextConverter.getAttributeName(),
            nextConverter);
      }
    }
  }

  public String getVersion() {
    return myVersion;
  }
}
