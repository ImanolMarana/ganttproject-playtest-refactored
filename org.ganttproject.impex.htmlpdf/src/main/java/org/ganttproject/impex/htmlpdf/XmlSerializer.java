/*
GanttProject is an opensource project management tool.
Copyright (C) 2005-2011 GanttProject team

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
package org.ganttproject.impex.htmlpdf;

import biz.ganttproject.app.InternationalizationKt;
import biz.ganttproject.core.model.task.TaskDefaultColumn;
import biz.ganttproject.core.table.ColumnList;
import biz.ganttproject.customproperty.CustomProperty;
import biz.ganttproject.customproperty.CustomPropertyDefinition;
import com.google.common.base.Joiner;
import net.sourceforge.ganttproject.document.Document;
import net.sourceforge.ganttproject.export.ExportException;
import net.sourceforge.ganttproject.export.TaskVisitor;
import net.sourceforge.ganttproject.gui.UIFacade;
import net.sourceforge.ganttproject.io.SaverBase;
import net.sourceforge.ganttproject.language.GanttLanguage;
import net.sourceforge.ganttproject.resource.HumanResource;
import net.sourceforge.ganttproject.resource.HumanResourceManager;
import biz.ganttproject.customproperty.CustomColumnsValues;
import net.sourceforge.ganttproject.task.ResourceAssignment;
import net.sourceforge.ganttproject.task.Task;
import net.sourceforge.ganttproject.task.TaskManager;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamSource;
import java.net.URI;
import java.net.URLDecoder;
import java.util.List;

/**
 * Serializes project data into XML for GanttProject's HTML/FOP stylesheets.
 *
 * @author dbarashev (Dmitry Barashev)
 */
public class XmlSerializer extends SaverBase {
  private final SAXTransformerFactory myFactory = (SAXTransformerFactory) SAXTransformerFactory.newInstance();

  protected void startPrefixedElement(String name, AttributesImpl attrs, TransformerHandler handler)
      throws SAXException {
    handler.startElement("http://ganttproject.sf.net/", name, "ganttproject:" + name, attrs);
    attrs.clear();
  }

  protected void endPrefixedElement(String name, TransformerHandler handler) throws SAXException {
    handler.endElement("http://ganttproject.sf.net/", name, "ganttproject:" + name);
  }

  protected void textElement(String name, AttributesImpl attrs, String text, TransformerHandler handler)
      throws SAXException {
    if (text != null) {
      startElement(name, attrs, handler);
      handler.startCDATA();
      handler.characters(text.toCharArray(), 0, text.length());
      handler.endCDATA();
      endElement(name, handler);
      attrs.clear();
    }
  }

  protected SAXTransformerFactory getTransformerFactory() {
    return myFactory;
  }

  protected TransformerHandler createHandler(String xsltPath) throws TransformerConfigurationException {
    TransformerHandler result = getTransformerFactory().newTransformerHandler(new StreamSource(xsltPath));
    Transformer transformer = result.getTransformer();
    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
    return result;
  }

  protected String i18n(String key) {
    String text = GanttLanguage.getInstance().getText(key);
    return GanttLanguage.getInstance().correctLabel(text);
  }

  protected void writeColumns(ColumnList visibleFields, TransformerHandler handler) throws SAXException {
    AttributesImpl attrs = new AttributesImpl();
    int totalWidth = 0;
    for (int i = 0; i < visibleFields.getSize(); i++) {
      if (visibleFields.getField(i).isVisible()) {
        totalWidth += visibleFields.getField(i).getWidth();
      }
    }
    for (int i = 0; i < visibleFields.getSize(); i++) {
      ColumnList.Column field = visibleFields.getField(i);
      if (field.isVisible()) {
        addAttribute("id", field.getID(), attrs);
        addAttribute("name", field.getName(), attrs);
        addAttribute("width", field.getWidth() * 100 / totalWidth, attrs);
        emptyElement("field", attrs, handler);
      }
    }
  }

  protected void writeViews(UIFacade facade, TransformerHandler handler) throws SAXException {
    AttributesImpl attrs = new AttributesImpl();
    addAttribute("id", "task-table", attrs);
    startElement("view", attrs, handler);
    writeColumns(facade.getTaskColumnList(), handler);
    endElement("view", handler);

    addAttribute("id", "resource-table", attrs);
    startElement("view", attrs, handler);
    writeColumns(facade.getResourceTree().getVisibleFields(), handler);

    endElement("view", handler);
  }

  protected void writeTasks(final TaskManager taskManager, final TransformerHandler handler) throws ExportException,
      SAXException {
    AttributesImpl attrs = new AttributesImpl();
    addAttribute("xslfo-path", "", attrs);
    addAttribute("title", i18n("tasksList"), attrs);
    addAttribute("name", i18n("name"), attrs);
    addAttribute("begin", i18n("start"), attrs);
    addAttribute("end", i18n("end"), attrs);
    addAttribute("milestone", i18n("meetingPoint"), attrs);
    addAttribute("progress", "%", attrs);
    addAttribute("assigned-to", i18n("human"), attrs);
    addAttribute("notes", i18n("notes"), attrs);
    addAttribute("duration", i18n("duration"), attrs);
    startPrefixedElement("tasks", attrs, handler);
    TaskVisitor visitor = new TaskVisitor() {
      AttributesImpl myAttrs = new AttributesImpl();

      @Override
      protected String serializeTask(Task t, int depth) throws Exception {
        writeTaskAttributes(t, depth, myAttrs, handler);
        return "";
      }
    };
    try {
      visitor.visit(taskManager);
    } catch (Exception e) {
      throw new ExportException("Failed to write tasks", e);
    }
    endPrefixedElement("tasks", handler);
  }

  private void writeTaskAttributes(Task t, int depth, AttributesImpl myAttrs, TransformerHandler handler)
      throws SAXException {
    addAttribute("depth", depth, myAttrs);
    startPrefixedElement("task", myAttrs, handler);
    writeTaskPriority(t, myAttrs, handler);
    writeTaskName(t, myAttrs, handler);
    writeTaskDates(t, myAttrs, handler);
    writeTaskProgress(t, myAttrs, handler);
    writeTaskDuration(t, myAttrs, handler);
    writeTaskCost(t, myAttrs, handler);
    writeTaskId(t, myAttrs, handler);
    writeTaskOutlineNumber(t, myAttrs, handler);
    writeTaskAttachments(t, myAttrs, handler);
    writeTaskCoordinator(t, myAttrs, handler);
    writeTaskAssignedResources(t, myAttrs, handler);
    writeTaskNotes(t, myAttrs, handler);
    writeTaskColor(t, myAttrs, handler);
    writeTaskCustomFields(t, taskManager, myAttrs, handler);
    endPrefixedElement("task", handler);
  }

  private void writeTaskPriority(Task t, AttributesImpl myAttrs, TransformerHandler handler) throws SAXException {
    addAttribute("id", "tpd1", myAttrs);
    textElement("priority", myAttrs, i18n(t.getPriority().getI18nKey()), handler);
  }

  private void writeTaskName(Task t, AttributesImpl myAttrs, TransformerHandler handler) throws SAXException {
    addAttribute("id", "tpd3", myAttrs);
    textElement("name", myAttrs, t.getName(), handler);
  }

  private void writeTaskDates(Task t, AttributesImpl myAttrs, TransformerHandler handler) throws SAXException {
    addAttribute("id", "tpd4", myAttrs);
    textElement("begin", myAttrs, t.getStart().toString(), handler);

    addAttribute("id", "tpd5", myAttrs);
    textElement("end", myAttrs, t.getDisplayEnd().toString(), handler);
    textElement("milestone", myAttrs, Boolean.valueOf(t.isMilestone()).toString(), handler);
  }

  private void writeTaskProgress(Task t, AttributesImpl myAttrs, TransformerHandler handler) throws SAXException {
    addAttribute("id", "tpd7", myAttrs);
    textElement("progress", myAttrs, String.valueOf(t.getCompletionPercentage()), handler);
  }

  private void writeTaskDuration(Task t, AttributesImpl myAttrs, TransformerHandler handler) throws SAXException {
    addAttribute("id", "tpd6", myAttrs);
    textElement("duration", myAttrs, String.valueOf(t.getDuration().getLength()), handler);
  }

  private void writeTaskCost(Task t, AttributesImpl myAttrs, TransformerHandler handler) throws SAXException {
    addAttribute("id", TaskDefaultColumn.COST.getStub().getID(), myAttrs);
    addAttribute("alignment", "right", myAttrs);
    textElement("cost", myAttrs, InternationalizationKt.getNumberFormat().format(t.getCost().getValue()),
        handler);
  }

  private void writeTaskId(Task t, AttributesImpl myAttrs, TransformerHandler handler) throws SAXException {
    addAttribute("id", TaskDefaultColumn.ID.getStub().getID(), myAttrs);
    textElement("task-id", myAttrs, String.valueOf(t.getTaskID()), handler);
  }

  private void writeTaskOutlineNumber(Task t, AttributesImpl myAttrs, TransformerHandler handler)
      throws SAXException {
    addAttribute("id", TaskDefaultColumn.OUTLINE_NUMBER.getStub().getID(), myAttrs);
    textElement("outline-number", myAttrs,
        Joiner.on('.').join(t.getManager().getTaskHierarchy().getOutlinePath(t)), handler);
  }

  private void writeTaskAttachments(Task t, AttributesImpl myAttrs, TransformerHandler handler)
      throws SAXException {
    final List<Document> attachments = t.getAttachments();
    for (int i = 0; i < attachments.size(); i++) {
      Document nextAttachment = attachments.get(i);
      URI nextUri = nextAttachment.getURI();
      if (nextUri != null) {
        String strUri = URLDecoder.decode(nextUri.toString(), "utf-8");
        if (strUri.startsWith("file:")) {
          if (strUri.endsWith("/")) {
            strUri = strUri.replaceAll("/+$", "");
          }
          int lastSlash = strUri.lastIndexOf('/');
          if (lastSlash >= 0) {
            addAttribute("display-name", strUri.substring(lastSlash + 1), myAttrs);
          }
        }
        textElement("attachment", myAttrs, strUri, handler);
      } else {
        textElement("attachment", myAttrs, nextAttachment.getPath(), handler);
      }
    }
  }

  private void writeTaskCoordinator(Task t, AttributesImpl myAttrs, TransformerHandler handler)
      throws SAXException {
    HumanResource coordinator = t.getAssignmentCollection().getCoordinator();
    if (coordinator != null) {
      addAttribute("id", "tpd8", myAttrs);
      textElement("coordinator", myAttrs, coordinator.getName(), handler);
    }
  }

  private void writeTaskAssignedResources(Task t, AttributesImpl myAttrs, TransformerHandler handler)
      throws SAXException {
    StringBuffer usersS = new StringBuffer();
    ResourceAssignment[] assignments = t.getAssignments();
    if (assignments.length > 0) {
      for (int j = 0; j < assignments.length; j++) {
        addAttribute("resource-id", assignments[j].getResource().getId(), myAttrs);
        emptyElement("assigned-resource", myAttrs, handler);
        usersS.append(assignments[j].getResource().getName());
        if (j < assignments.length - 1) {
          usersS.append(getAssignedResourcesDelimiter());
        }
      }
    }
    addAttribute("id", "tpdResources", myAttrs);
    textElement("assigned-to", myAttrs, usersS.toString(), handler);
  }

  private void writeTaskNotes(Task t, AttributesImpl myAttrs, TransformerHandler handler) throws SAXException {
    if (t.getNotes() != null && t.getNotes().length() > 0) {
      textElement("notes", myAttrs, t.getNotes(), handler);
    }
  }

  private void writeTaskColor(Task t, AttributesImpl myAttrs, TransformerHandler handler) throws SAXException {
    if (t.getColor() != null) {
      textElement("color", myAttrs, getHexaColor(t.getColor()), handler);
    }
  }

  private void writeTaskCustomFields(Task t, TaskManager taskManager, AttributesImpl myAttrs,
      TransformerHandler handler) throws SAXException {
    AttributesImpl attrs = new AttributesImpl();
    CustomColumnsValues customValues = t.getCustomValues();
    for (CustomPropertyDefinition def : taskManager.getCustomPropertyManager().getDefinitions()) {
      Object value = customValues.getValue(def);
      String valueAsString = value == null ? ""
          : def.getPropertyClass().isNumeric() ? InternationalizationKt.getNumberFormat().format(value)
              : value.toString();
      addAttribute("id", def.getId(), attrs);
      if (def.getPropertyClass().isNumeric()) {
        addAttribute("alignment", "right", attrs);
      } else {
        addAttribute("alignment", "left", attrs);
      }
      textElement("custom-field", attrs, valueAsString, handler);
    }
  }
//Refactoring end
  }

  protected String getAssignedResourcesDelimiter() {
    return " ";
  }

  protected void writeResources(HumanResourceManager resourceManager, TransformerHandler handler) throws SAXException {
    AttributesImpl attrs = new AttributesImpl();
    addAttribute("title", i18n("resourcesList"), attrs);
    addAttribute("name", i18n("colName"), attrs);
    addAttribute("role", i18n("colRole"), attrs);
    addAttribute("mail", i18n("colMail"), attrs);
    addAttribute("phone", i18n("colPhone"), attrs);
    addAttribute("rate", i18n("colStandardRate"), attrs);
    addAttribute("totalCost", i18n("colTotalCost"), attrs);
    addAttribute("totalLoad", i18n("colTotalLoad"), attrs);
    startPrefixedElement("resources", attrs, handler);
    {
      List<HumanResource> resources = resourceManager.getResources();

      // String[] function =
      // RoleManager.Access.getInstance().getRoleNames();
      for (int i = 0; i < resources.size(); i++) {
        HumanResource p = resources.get(i);
        addAttribute("id", p.getId(), attrs);
        startPrefixedElement("resource", attrs, handler);
        addAttribute("id", "0", attrs);
        textElement("name", attrs, p.getName(), handler);
        addAttribute("id", "1", attrs);
        textElement("role", attrs, p.getRole().getName(), handler);
        addAttribute("id", "2", attrs);
        textElement("mail", attrs, p.getMail(), handler);
        addAttribute("id", "3", attrs);
        textElement("phone", attrs, p.getPhone(), handler);
        addAttribute("id", "5", attrs);
        textElement("rate", attrs, InternationalizationKt.getNumberFormat().format(p.getStandardPayRate()), handler);
        addAttribute("id", "6", attrs);
        textElement("totalCost", attrs, InternationalizationKt.getNumberFormat().format(p.getTotalCost()), handler);
        addAttribute("id", "7", attrs);
        textElement("totalLoad", attrs, InternationalizationKt.getNumberFormat().format(p.getTotalLoad()), handler);

        List<CustomProperty> customFields = p.getCustomProperties();
        for (int j = 0; j < customFields.size(); j++) {
          CustomProperty nextProperty = customFields.get(j);
          CustomPropertyDefinition def = nextProperty.getDefinition();
          addAttribute("id", def.getId(), attrs);
          if (def.getPropertyClass().isNumeric()) {
            addAttribute("alignment", "right", attrs);
          } else {
            addAttribute("alignment", "left", attrs);
          }
          String value = def.getPropertyClass().isNumeric() ? InternationalizationKt.getNumberFormat().format(nextProperty.getValue()) : nextProperty.getValueAsString();
          textElement("custom-field", attrs, value, handler);
        }
        endPrefixedElement("resource", handler);
      }
    }
    endPrefixedElement("resources", handler);
  }

  protected static String getHexaColor(java.awt.Color color) {
    StringBuffer out = new StringBuffer();
    out.append("#");
    if (color.getRed() <= 15) {
      out.append("0");
    }
    out.append(Integer.toHexString(color.getRed()));
    if (color.getGreen() <= 15) {
      out.append("0");
    }
    out.append(Integer.toHexString(color.getGreen()));
    if (color.getBlue() <= 15) {
      out.append("0");
    }
    out.append(Integer.toHexString(color.getBlue()));

    return out.toString();
  }

}
