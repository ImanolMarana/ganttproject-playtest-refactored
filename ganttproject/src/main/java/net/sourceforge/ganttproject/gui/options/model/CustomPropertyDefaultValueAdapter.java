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
package net.sourceforge.ganttproject.gui.options.model;

import java.util.Date;

import biz.ganttproject.customproperty.CustomPropertyClass;
import biz.ganttproject.customproperty.CustomPropertyDefinition;
import org.w3c.util.DateParser;

import biz.ganttproject.core.option.DefaultBooleanOption;
import biz.ganttproject.core.option.DefaultDateOption;
import biz.ganttproject.core.option.DefaultDoubleOption;
import biz.ganttproject.core.option.DefaultIntegerOption;
import biz.ganttproject.core.option.DefaultStringOption;
import biz.ganttproject.core.option.GPOption;

public abstract class CustomPropertyDefaultValueAdapter {
  public static GPOption createDefaultValueOption(final CustomPropertyClass propertyClass,
      final CustomPropertyDefinition def) {
    switch (propertyClass) {
      case TEXT:
        return new TextDefaultValue(def);
      case BOOLEAN:
        return new BooleanDefaultValue(def);
      case INTEGER:
        return new IntegerDefaultValue(def);
      case DOUBLE:
        return new DoubleDefaultValue(def);
      case DATE:
        return new DateDefaultValue(def);
      default:
        return null;
    }
  }

  private static class TextDefaultValue extends DefaultStringOption {
    TextDefaultValue(CustomPropertyDefinition def) {
      super("customPropertyDialog.defaultValue.text", def.getDefaultValueAsString());
    }

    @Override
    public void commit() {
      if (isChanged()) {
        super.commit();
        def.setDefaultValueAsString(getValue());
      }
    }

    @Override
    public void setValue(String value) {
      super.setValue(value);
      commit();
    }
  }

  private static class BooleanDefaultValue extends DefaultBooleanOption {
    BooleanDefaultValue(CustomPropertyDefinition def) {
      super("customPropertyDialog.defaultValue.boolean", def.getDefaultValue() == null ? Boolean.FALSE
          : (Boolean) def.getDefaultValue());
    }

    @Override
    public void commit() {
      if (isChanged()) {
        super.commit();
        def.setDefaultValueAsString(String.valueOf(getValue()));
      }
    }

    @Override
    public void setValue(Boolean value) {
      super.setValue(value);
      commit();
    }
  }

  private static class IntegerDefaultValue extends DefaultIntegerOption {
    IntegerDefaultValue(CustomPropertyDefinition def) {
      super("customPropertyDialog.defaultValue.integer", (Integer) def.getDefaultValue());
    }

    @Override
    public void commit() {
      if (isChanged()) {
        super.commit();
        def.setDefaultValueAsString(String.valueOf(getValue()));
      }
    }

    @Override
    public void setValue(Integer value) {
      super.setValue(value);
      commit();
    }
  }

  private static class DoubleDefaultValue extends DefaultDoubleOption {
    DoubleDefaultValue(CustomPropertyDefinition def) {
      super("customPropertyDialog.defaultValue.double", (Double) def.getDefaultValue());
    }

    @Override
    public void commit() {
      if (isChanged()) {
        super.commit();
        def.setDefaultValueAsString(String.valueOf(getValue()));
      }
    }

    @Override
    public void setValue(Double value) {
      super.setValue(value);
      commit();
    }
  }

  private static class DateDefaultValue extends DefaultDateOption {
    DateDefaultValue(CustomPropertyDefinition def) {
      super("customPropertyDialog.defaultValue.date", (Date) def.getDefaultValue());
    }

    @Override
    public void commit() {
      if (isChanged()) {
        super.commit();
        def.setDefaultValueAsString(DateParser.getIsoDate(getValue()));
      }
    }

    @Override
    public void setValue(Date value) {
      super.setValue(value);
      commit();
    }
  }
//Refactoring end
  }
}
