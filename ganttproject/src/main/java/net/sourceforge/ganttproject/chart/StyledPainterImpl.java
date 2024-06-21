/*
Copyright 2003-2012 Dmitry Barashev, GanttProject Team

This file is part of GanttProject, an opensource project management tool.

GanttProject is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

GanttProject is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with GanttProject.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.sourceforge.ganttproject.chart;

import biz.ganttproject.core.chart.canvas.Canvas;
import biz.ganttproject.core.chart.canvas.Canvas.Line;
import biz.ganttproject.core.chart.canvas.Canvas.Rectangle;
import biz.ganttproject.core.chart.canvas.Canvas.Text;
import biz.ganttproject.core.chart.canvas.Canvas.TextGroup;
import biz.ganttproject.core.chart.canvas.Painter;
import biz.ganttproject.core.chart.render.*;
import net.sourceforge.ganttproject.util.PropertiesUtil;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

/**
 * Implements styled painters for the available primitives (see
 * {@link Canvas})
 *
 * @author bard
 */
public class StyledPainterImpl implements Painter {
  private Graphics2D myGraphics;

  private final Map<String, RectanglePainter> myStyle2painter = new HashMap<>();

  private final ChartUIConfiguration myConfig;

  private final int margin;

  /** List X coordinates used to draw polygons */
  private final int[] myXPoints = new int[4];

  /** List Y coordinates used to draw polygons */
  private final int[] myYPoints = new int[4];

  private final Properties myProperties;

  private final TextPainter myTextPainter;

  private final LineRenderer myLineRenderer;

  private final RectangleRenderer myRectangleRenderer;

  private final SummaryTaskRenderer mySummaryTaskRenderer;

  private final PolygonRenderer myPolygonRenderer;

  /** Default stroke used for the primitives */
  private final static BasicStroke defaultStroke = new BasicStroke();

  public StyledPainterImpl(final ChartUIConfiguration config) {
    myConfig = config;
    margin = myConfig.getMargin();
    initStylePainters();

    myProperties = new Properties();
    PropertiesUtil.loadProperties(myProperties, "/resources/chart.properties");
    config.getChartStylesOption().addChangeValueListener(event -> {
        for (Entry<String, String> entry : config.getChartStylesOption().getValues()) {
            myProperties.put(entry.getKey(), entry.getValue());
        }
    });
    myTextPainter = new TextPainter(myProperties, config::getChartFont);
    myLineRenderer = new LineRenderer(myProperties);
    myRectangleRenderer = new RectangleRenderer(myProperties);
    mySummaryTaskRenderer = new SummaryTaskRenderer(myProperties);
    myPolygonRenderer = new PolygonRenderer(myProperties);
}

private void initStylePainters() {
    myStyle2painter.put("task.progress", new ColouredRectanglePainter(Color.BLACK));
    myStyle2painter.put("task.progress.end", new ColouredRectanglePainter(Color.BLACK));
    RectanglePainter containerRectanglePainter = next -> mySummaryTaskRenderer.render(next);
    myStyle2painter.put("task.projectTask", containerRectanglePainter);
    myStyle2painter.put("task.supertask", containerRectanglePainter);

    RectanglePainter myResourceLoadPainter = this::paintResourceLoad;
    myStyle2painter.put("load.normal", myResourceLoadPainter);
    myStyle2painter.put("load.normal.first", myResourceLoadPainter);
    myStyle2painter.put("load.normal.last", myResourceLoadPainter);
    myStyle2painter.put("load.normal.first.last", myResourceLoadPainter);
    myStyle2painter.put("load.overload", myResourceLoadPainter);
    myStyle2painter.put("load.overload.first", myResourceLoadPainter);
    myStyle2painter.put("load.overload.last", myResourceLoadPainter);
    myStyle2painter.put("load.overload.first.last", myResourceLoadPainter);
    myStyle2painter.put("load.underload", myResourceLoadPainter);
    myStyle2painter.put("load.underload.first", myResourceLoadPainter);
    myStyle2painter.put("load.underload.last", myResourceLoadPainter);
    myStyle2painter.put("load.underload.first.last", myResourceLoadPainter);

    myStyle2painter.put("dependency.arrow.down", this::paintArrowDown);
    myStyle2painter.put("dependency.arrow.up", this::paintArrowUp);
    myStyle2painter.put("dependency.arrow.left", this::paintArrowLeft);
    myStyle2painter.put("dependency.arrow.right", this::paintArrowRight);
    myStyle2painter.put("dayoff", this::paintDayOff);
    myStyle2painter.put("previousStateTask", this::paintPreviousStateTask);
}

private void paintResourceLoad(Rectangle next) {
    String style = next.getStyle();
    Color c;
    if (style.indexOf("overload") > 0) {
        c = myConfig.getResourceOverloadColor();
    } else if (style.indexOf("underload") > 0) {
        c = myConfig.getResourceUnderLoadColor();
    } else {
        c = myConfig.getResourceNormalLoadColor();
    }
    myGraphics.setColor(c);

    myGraphics.fillRect(next.getLeftX(), next.getTopY() + margin, next.getWidth(), next.getHeight() - 2 * margin);
    if (style.indexOf(".first") > 0) {
        myGraphics.setColor(Color.BLACK);
        myGraphics.drawLine(next.getLeftX(), next.getTopY() + margin, next.getLeftX(), next.getBottomY() - margin);
    }
    if (style.indexOf(".last") > 0) {
        myGraphics.setColor(Color.BLACK);
        myGraphics.drawLine(next.getRightX(), next.getTopY() + margin, next.getRightX(), next.getBottomY() - margin);
    }
    myGraphics.setColor(Color.BLACK);

    // Resource load percentage drawing logic (commented out)
    // ... 

    myGraphics.drawLine(next.getLeftX(), next.getTopY() + margin, next.getRightX(), next.getTopY() + margin);
    myGraphics.drawLine(next.getLeftX(), next.getBottomY() - margin, next.getRightX(), next.getBottomY() - margin);
}

private void paintArrowDown(Rectangle next) {
    myXPoints[0] = next.getLeftX();
    myXPoints[1] = next.getRightX();
    myXPoints[2] = next.getMiddleX();
    myYPoints[0] = next.getTopY();
    myYPoints[1] = next.getTopY();
    myYPoints[2] = next.getBottomY();
    myGraphics.setColor(Color.BLACK);
    myGraphics.fillPolygon(myXPoints, myYPoints, 3);
}

private void paintArrowUp(Rectangle next) {
    myXPoints[0] = next.getLeftX();
    myXPoints[1] = next.getRightX();
    myXPoints[2] = next.getMiddleX();
    myYPoints[0] = next.getBottomY();
    myYPoints[1] = next.getBottomY();
    myYPoints[2] = next.getTopY();
    myGraphics.setColor(Color.BLACK);
    myGraphics.fillPolygon(myXPoints, myYPoints, 3);
}

private void paintArrowLeft(Rectangle next) {
    Graphics g = myGraphics;
    g.setColor(Color.BLACK);
    myXPoints[0] = next.getLeftX();
    myXPoints[1] = next.getRightX();
    myXPoints[2] = next.getRightX();
    myYPoints[0] = next.getMiddleY();
    myYPoints[1] = next.getTopY();
    myYPoints[2] = next.getBottomY();
    g.fillPolygon(myXPoints, myYPoints, 3);
}

private void paintArrowRight(Rectangle next) {
    myXPoints[0] = next.getLeftX();
    myXPoints[1] = next.getRightX();
    myXPoints[2] = next.getLeftX();
    myYPoints[0] = next.getTopY();
    myYPoints[1] = next.getMiddleY();
    myYPoints[2] = next.getBottomY();
    myGraphics.setColor(Color.BLACK);
    myGraphics.fillPolygon(myXPoints, myYPoints, 3);
}

private void paintDayOff(Rectangle next) {
    int margin = StyledPainterImpl.this.margin - 3;
    Color c = myConfig.getDayOffColor();
    myGraphics.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 100));
    myGraphics.fillRect(next.getLeftX(), next.getTopY() + margin, next.getWidth(), next.getHeight() - 2 * margin);
    myGraphics.setColor(Color.BLACK);
    myGraphics.drawLine(next.getLeftX(), next.getTopY() + margin, next.getLeftX(), next.getBottomY() - margin);
    myGraphics.drawLine(next.getLeftX(), next.getTopY() + margin, next.getRightX(), next.getTopY() + margin);
    myGraphics.drawLine(next.getLeftX(), next.getBottomY() - margin, next.getRightX(), next.getBottomY() - margin);
    myGraphics.drawLine(next.getRightX(), next.getTopY() + margin, next.getRightX(), next.getBottomY() - margin);
}

private void paintPreviousStateTask(Rectangle next) {
    Graphics g = myGraphics;
    final Color c;
    if (next.hasStyle("earlier")) {
        c = myConfig.getEarlierPreviousTaskColor();
    } else if (next.hasStyle("later")) {
        c = myConfig.getLaterPreviousTaskColor();
    } else {
        c = myConfig.getPreviousTaskColor();
    }
    g.setColor(c);

    if (next.hasStyle("milestone")) {
        int middleX = (next.getWidth() <= next.getHeight()) ? next.getRightX() - next.getWidth() / 2 : next.getLeftX()
                + next.getHeight() / 2;
        int middleY = next.getMiddleY();

        myXPoints[0] = next.getLeftX() + 2;
        myYPoints[0] = middleY;
        myXPoints[1] = middleX + 3;
        myYPoints[1] = next.getTopY() - 1;
        myXPoints[2] = (next.getWidth() <= next.getHeight()) ? next.getRightX() + 4 : next.getLeftX() + next.getHeight() + 4;
        myYPoints[2] = middleY;
        myXPoints[3] = middleX + 3;
        myYPoints[3] = next.getBottomY() + 1;

        g.fillPolygon(myXPoints, myYPoints, 4);
    } else if (next.hasStyle("super")) {
        g.fillRect(next.getLeftX(), next.getTopY() + next.getHeight() - 6, next.getWidth(), 3);
        int topy = next.getTopY() + next.getHeight() - 3;
        int rightx = next.getLeftX() + next.getWidth();
        g.fillPolygon(new int[]{rightx - 3, rightx, rightx}, new int[]{topy, topy, topy + 3}, 3);
    } else {
        g.fillRect(next.getLeftX(), next.getTopY(), next.getWidth(), next.getHeight());
        g.setColor(Color.black);
        g.drawLine(next.getLeftX(), next.getTopY(), next.getRightX(), next.getTopY());
        g.drawLine(next.getLeftX(), next.getBottomY(), next.getRightX(), next.getBottomY());
        if (next.hasStyle("start")) {
            g.drawLine(next.getLeftX(), next.getTopY(), next.getLeftX(), next.getBottomY());
        }
        if (next.hasStyle("end")) {
            g.drawLine(next.getRightX(), next.getTopY(), next.getRightX(), next.getBottomY());
        }
    }
}

//Refactoring end

  public void setGraphics(Graphics g) {
    myGraphics = (Graphics2D) g;
    myTextPainter.setGraphics(myGraphics);
    myLineRenderer.setGraphics(myGraphics);
    myRectangleRenderer.setGraphics(myGraphics);
    mySummaryTaskRenderer.setGraphics(myGraphics);
    myPolygonRenderer.setGraphics(myGraphics);
  }

  @Override
  public void prePaint() {
    myGraphics.setStroke(defaultStroke);
    myGraphics.setFont(myConfig.getChartFont());
  }

  @Override
  public void paint(Rectangle next) {
    assert myGraphics != null;
    if (myRectangleRenderer.render(next)) {
      return;
    }
    RectanglePainter painter = myStyle2painter.get(next.getStyle());
    if (painter != null) {
      // Use found painter
      painter.paint(next);
    } else {
      // Use default painter, since no painter was provided
      if (next.getBackgroundColor() == null) {
        Color foreColor = next.getForegroundColor();
        if (foreColor == null) {
          foreColor = Color.BLACK;
        }
        myGraphics.setColor(foreColor);
        myGraphics.drawRect(next.getLeftX(), next.getTopY(), next.getWidth(), next.getHeight());
      } else {
        myGraphics.setColor(next.getBackgroundColor());
        myGraphics.fillRect(next.getLeftX(), next.getTopY(), next.getWidth(), next.getHeight());
      }
    }
  }

  /**
   * Interface providing a method to paint a rectangle (currently, used to draw
   * many more other things...)
   */
  private interface RectanglePainter {
    void paint(Rectangle next);
  }


  private class ColouredRectanglePainter implements RectanglePainter {
    private final Color myColor;

    private ColouredRectanglePainter(Color color) {
      myColor = color;
    }

    @Override
    public void paint(Rectangle next) {
      myGraphics.setColor(myColor);
      myGraphics.fillRect(next.getLeftX(), next.getTopY(), next.getWidth(), next.getHeight());
    }
  }

  @Override
  public void paint(Line line) {
    myLineRenderer.renderLine(line);
  }

  @Override
  public void paint(Text text) {
    myTextPainter.paint(text);
  }

  @Override
  public void paint(TextGroup textGroup) {
    myTextPainter.paint(textGroup);
  }

  @Override
  public void paint(Canvas.Rhombus rhombus) {
    myPolygonRenderer.render(rhombus);
  }
}
