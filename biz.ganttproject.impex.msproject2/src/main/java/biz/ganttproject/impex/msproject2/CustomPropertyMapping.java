/*
Copyright 2017 Dmitry Barashev, BarD Software s.r.o

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
package biz.ganttproject.impex.msproject2;

import biz.ganttproject.customproperty.CustomPropertyDefinition;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import net.sf.mpxj.FieldType;
import net.sf.mpxj.MPXJException;
import net.sf.mpxj.ResourceField;
import net.sf.mpxj.TaskField;
import biz.ganttproject.customproperty.CustomPropertyManager;
import net.sourceforge.ganttproject.resource.HumanResourceManager;
import net.sourceforge.ganttproject.task.TaskManager;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

/**
 * @author dbarashev@bardsoftware.com
 */
class CustomPropertyMapping {
  static final String MSPROJECT_TYPE = "MSPROJECT_TYPE";

  static Map<CustomPropertyDefinition, FieldType> buildMapping(TaskManager taskManager) throws MPXJException {
    final SortedSet<TaskField> taskFields = Sets.newTreeSet(new Comparator<TaskField>() {
      @Override
      public int compare(TaskField o1, TaskField o2) {
        return o1.ordinal() - o2.ordinal();
      }
    });
    taskFields.addAll(Arrays.asList(TaskField.values()));
    return buildMapping(taskManager.getCustomPropertyManager(), taskFields, TaskField.class);
  }

  static Map<CustomPropertyDefinition, FieldType> buildMapping(HumanResourceManager resourceManager) throws MPXJException {
    final SortedSet<ResourceField> taskFields = Sets.newTreeSet(new Comparator<ResourceField>() {
      @Override
      public int compare(ResourceField o1, ResourceField o2) {
        return o1.ordinal() - o2.ordinal();
      }
    });
    taskFields.addAll(Arrays.asList(ResourceField.values()));
    return buildMapping(resourceManager.getCustomPropertyManager(), taskFields, ResourceField.class);
  }

  private static <T extends Enum<T>, S extends FieldType> Map<CustomPropertyDefinition, FieldType> buildMapping(
    final CustomPropertyManager customPropertyManager,
    final SortedSet<S> mpxjFields,
    final Class<T> enumClass) throws MPXJException {

    final Map<CustomPropertyDefinition, FieldType> result = new HashMap<>();
    LinkedHashSet<CustomPropertyDefinition> allDefs = Sets.newLinkedHashSet(customPropertyManager.getDefinitions());

    // First pass: search for saved MSPROJECT_TYPE attributes.
    processDefinitions(allDefs, mpxjFields, enumClass, result, new Function<CustomPropertyDefinition, String>() {
        @Override
        public String apply(@Nullable CustomPropertyDefinition def) {
            return def.getAttributes().get(MSPROJECT_TYPE);
        }
    });

    // Second pass: find properties with predefined names
    processDefinitions(allDefs, mpxjFields, enumClass, result, new Function<CustomPropertyDefinition, String>() {
        @Override
        public String apply(@Nullable CustomPropertyDefinition def) {
            return def.getName().toUpperCase();
        }
    });

    // Third pass: provide appropriate types for the remaining properties
    processDefinitions(allDefs, mpxjFields, enumClass, result, new Function<CustomPropertyDefinition, S>() {
        @Override
        public S apply(@Nullable CustomPropertyDefinition def) {
            return findMatchingFieldType(enumClass, mpxjFields, def);
        }
    });

    if (!allDefs.isEmpty()) {
        List<String> remainingColumns = Lists.newArrayList(Iterables.transform(allDefs, new Function<CustomPropertyDefinition, String>() {
            @Override
            public String apply(@Nullable CustomPropertyDefinition def) {
                return def.getName();
            }
        }));
        throw new MPXJException(String.format("Some of the custom columns failed to export: %s", remainingColumns));
    }
    return result;
}

private static <T extends Enum<T>, S extends FieldType> void processDefinitions(
        LinkedHashSet<CustomPropertyDefinition> allDefs,
        SortedSet<S> mpxjFields,
        Class<T> enumClass,
        Map<CustomPropertyDefinition, FieldType> result,
        Function<CustomPropertyDefinition, S> fieldExtractor) {

    for (Iterator<CustomPropertyDefinition> it = allDefs.iterator(); it.hasNext(); ) {
        CustomPropertyDefinition def = it.next();
        try {
            S tf = fieldExtractor.apply(def);
            if (tf != null) {
                result.put(def, tf);
                mpxjFields.remove(tf);
                it.remove();
            }
        } catch (IllegalArgumentException e) {
            // That's somewhat okay. We have not found such value in the enum, but it might come from the future
            // versions of MPXJ, so it is not the reason to fail
        }
    }
}

private static <T extends Enum<T>, S extends FieldType> S findMatchingFieldType(Class<T> enumClass, SortedSet<S> mpxjFields, CustomPropertyDefinition def) {
    String name;
    switch (def.getPropertyClass()) {
        case BOOLEAN:
            name = "FLAG";
            break;
        case INTEGER:
        case DOUBLE:
            name = "NUMBER";
            break;
        case DATE:
            name = "DATE";
            break;
        default:
            assert false : "Should not be here";
            name = "TEXT";
    }
    for (int i = 1; i <= 30; i++) {
        S tf1 = (S) Enum.valueOf(enumClass, name + String.valueOf(i));
        if (mpxjFields.contains(tf1)) {
            return tf1;
        }
    }
    return null;
}
//Refactoring end
            name = "FLAG";
            break;
          case INTEGER:
          case DOUBLE:
            name = "NUMBER";
            break;
          case TEXT:
            name = "TEXT";
            break;
          case DATE:
            name = "DATE";
            break;
          default:
            assert false : "Should not be here";
            name = "TEXT";
        }
        for (int i = 1; i <= 30; i++) {
          S tf1 = (S)Enum.valueOf(enumClass, name + String.valueOf(i));
          if (mpxjFields.contains(tf1)) {
            return tf1;
          }
        }
        return null;
      }
    });
    if (!f.allDefs.isEmpty()) {
      List<String> remainingColumns = Lists.newArrayList(Iterables.transform(f.allDefs, new Function<CustomPropertyDefinition, String>() {
        @Override
        public String apply(@Nullable CustomPropertyDefinition def) {
          return def.getName();
        }
      }));
      throw new MPXJException(String.format("Some of the custom columns failed to export: %s", remainingColumns));
    }
    return result;
  }
}
