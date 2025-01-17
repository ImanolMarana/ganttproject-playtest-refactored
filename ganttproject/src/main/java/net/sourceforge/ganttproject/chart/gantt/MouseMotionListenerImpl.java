/*
GanttProject is an opensource project management tool.
Copyright (C) 2011 GanttProject Team

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
package net.sourceforge.ganttproject.chart.gantt;

import biz.ganttproject.core.calendar.CalendarEvent;
import biz.ganttproject.core.time.CalendarFactory;
import com.google.common.base.Strings;
import net.sourceforge.ganttproject.ChartComponentBase;
import net.sourceforge.ganttproject.GanttGraphicArea;
import net.sourceforge.ganttproject.chart.item.*;
import net.sourceforge.ganttproject.chart.mouse.MouseMotionListenerBase;
import net.sourceforge.ganttproject.gui.UIFacade;
import net.sourceforge.ganttproject.language.GanttLanguage;
import net.sourceforge.ganttproject.task.Task;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Date;

class MouseMotionListenerImpl extends MouseMotionListenerBase {
  private final ChartComponentBase myChartComponent;
  private final GanttChartController myChartController;

  public MouseMotionListenerImpl(GanttChartController chartImplementation,
                                 UIFacade uiFacade, ChartComponentBase chartComponent) {
    super(uiFacade, chartImplementation);
    myChartController = chartImplementation;
    myChartComponent = chartComponent;
  }

  // Move the move on the area
  @Override
  public void mouseMoved(MouseEvent e) {
    ChartItem itemUnderPoint = myChartController.getChartItemUnderMousePoint(e.getX(), e.getY());
    Task taskUnderPoint = itemUnderPoint == null ? null : itemUnderPoint.getTask();
    // System.err.println("[OldMouseMotionListenerImpl] mouseMoved:
    // taskUnderPoint="+taskUnderPoint);
    myChartController.hideTooltip();
    if (taskUnderPoint == null) {
      handleNoTaskUnderPoint(e, itemUnderPoint);
    } else if (itemUnderPoint instanceof TaskBoundaryChartItem && !taskUnderPoint.isMilestone()) {
      handleTaskBoundaryItem(itemUnderPoint);
    }
    // special cursor
    else if (itemUnderPoint instanceof TaskProgressChartItem) {
      myChartComponent.setCursor(GanttGraphicArea.CHANGE_PROGRESS_CURSOR);
    } else if (itemUnderPoint instanceof TaskNotesChartItem && taskUnderPoint.getNotes() != null) {
      handleTaskNotesItem(e, taskUnderPoint);
    } else {
      myChartComponent.setCursor(ChartComponentBase.HAND_CURSOR);
    }

  }

  private void handleTaskNotesItem(MouseEvent e, Task taskUnderPoint) {
    myChartComponent.setCursor(ChartComponentBase.HAND_CURSOR);
    myChartController.showTooltip(e.getX(), e.getY(),
        GanttLanguage.getInstance().formatText("task.notesTooltip.pattern",
            taskUnderPoint.getNotes().replace("\n", "<br>")));
  }

  private void handleTaskBoundaryItem(ChartItem itemUnderPoint) {
    Cursor cursor = ((TaskBoundaryChartItem) itemUnderPoint).isStartBoundary() ? GanttGraphicArea.W_RESIZE_CURSOR
        : GanttGraphicArea.E_RESIZE_CURSOR;
    myChartComponent.setCursor(cursor);
  }

  private void handleNoTaskUnderPoint(MouseEvent e, ChartItem itemUnderPoint) {
    myChartComponent.setDefaultCursor();
    if (itemUnderPoint instanceof CalendarChartItem) {
      CalendarEvent event = findCalendarEvent(((CalendarChartItem) itemUnderPoint).getDate());
      if (event != null) {
        String tooltipText = getCalendarEventTooltipText(event);
        myChartController.showTooltip(e.getX(), e.getY(), tooltipText);
      }
    }
  }
  
  private String getCalendarEventTooltipText(CalendarEvent event) {
    String tooltipText;
    if (event.isRecurring) {
      tooltipText = GanttLanguage.getInstance().formatText("timeline.holidayTooltipRecurring.pattern",
          GanttLanguage.getInstance().getRecurringDateFormat().format(event.myDate),
          Strings.nullToEmpty(event.getTitle()));
    } else {
      tooltipText = GanttLanguage.getInstance().formatText("timeline.holidayTooltip.pattern",
          GanttLanguage.getInstance().formatDate(CalendarFactory.createGanttCalendar(event.myDate)),
          Strings.nullToEmpty(event.getTitle()));
    }
    return tooltipText;
  }

  private CalendarEvent findCalendarEvent(Date date) {
    return myChartComponent.getProject().getActiveCalendar().getEvent(date);
  }
//Refactoring end
                "timeline.holidayTooltip.pattern",
                GanttLanguage.getInstance().formatDate(CalendarFactory.createGanttCalendar(event.myDate)),
                Strings.nullToEmpty(event.getTitle()));
          }
          myChartController.showTooltip(e.getX(), e.getY(), tooltipText);
        }
      }
    }
    else if (itemUnderPoint instanceof TaskBoundaryChartItem && !taskUnderPoint.isMilestone()) {
      Cursor cursor = ((TaskBoundaryChartItem) itemUnderPoint).isStartBoundary() ? GanttGraphicArea.W_RESIZE_CURSOR
          : GanttGraphicArea.E_RESIZE_CURSOR;
      myChartComponent.setCursor(cursor);
    }
    // special cursor
    else if (itemUnderPoint instanceof TaskProgressChartItem) {
      myChartComponent.setCursor(GanttGraphicArea.CHANGE_PROGRESS_CURSOR);
    }
    else if (itemUnderPoint instanceof TaskNotesChartItem && taskUnderPoint.getNotes() != null) {
      myChartComponent.setCursor(ChartComponentBase.HAND_CURSOR);
      myChartController.showTooltip(e.getX(), e.getY(),
          GanttLanguage.getInstance().formatText(
              "task.notesTooltip.pattern", taskUnderPoint.getNotes().replace("\n", "<br>")));
    }
    else {
      myChartComponent.setCursor(ChartComponentBase.HAND_CURSOR);
    }

  }

  private CalendarEvent findCalendarEvent(Date date) {
    return myChartComponent.getProject().getActiveCalendar().getEvent(date);
  }
}