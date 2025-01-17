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
package net.sourceforge.ganttproject.task.algorithm;

import biz.ganttproject.core.calendar.walker.WorkingUnitCounter;
import biz.ganttproject.core.time.GanttCalendar;
import biz.ganttproject.core.time.TimeDuration;
import net.sourceforge.ganttproject.task.Task;
import net.sourceforge.ganttproject.task.TaskContainmentHierarchyFacade;
import net.sourceforge.ganttproject.task.TaskMutator;
import net.sourceforge.ganttproject.task.dependency.TaskDependency;
import net.sourceforge.ganttproject.task.dependency.TaskDependencyConstraint;
import net.sourceforge.ganttproject.task.dependency.TaskDependencyException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * @author bard
 */
public abstract class RecalculateTaskScheduleAlgorithm extends AlgorithmBase {

  private SortedMap<Integer, List<TaskDependency>> myDistance2dependencyList = new TreeMap<Integer, List<TaskDependency>>();

  private Set<Task> myModifiedTasks = new HashSet<Task>();

  private final AdjustTaskBoundsAlgorithm myAdjuster;

  private int myEntranceCounter;

  private boolean isRunning;

  public RecalculateTaskScheduleAlgorithm(AdjustTaskBoundsAlgorithm adjuster) {
    myAdjuster = adjuster;
  }


  @Override
  public boolean isEnabled() {
    return false;
  }


  public void run(Task changedTask) throws TaskDependencyException {
    if (!isEnabled()) {
      return;
    }
    isRunning = true;
    myEntranceCounter++;
    buildDistanceGraph(changedTask);
    fulfilDependencies();
    myDistance2dependencyList.clear();
    myModifiedTasks.add(changedTask);
    myAdjuster.run(myModifiedTasks.toArray(new Task[0]));
    myDistance2dependencyList.clear();
    myModifiedTasks.clear();
    myEntranceCounter--;

    isRunning = false;
  }

  public void run(Collection<Task> taskSet) throws TaskDependencyException {
    if (!isEnabled()) {
      return;
    }
    isRunning = true;
    myEntranceCounter++;
    for (Iterator<Task> tasks = taskSet.iterator(); tasks.hasNext();) {
      Task nextTask = tasks.next();
      buildDistanceGraph(nextTask);
      fulfilDependencies();
      myDistance2dependencyList.clear();
      myModifiedTasks.add(nextTask);
    }
    myAdjuster.run(myModifiedTasks.toArray(new Task[0]));
    myDistance2dependencyList.clear();
    myModifiedTasks.clear();
    myEntranceCounter--;

    isRunning = false;
  }

  @Override
  public void run() throws TaskDependencyException {
    if (!isEnabled()) {
      return;
    }
    myDistance2dependencyList.clear();
    isRunning = true;
    TaskContainmentHierarchyFacade facade = createContainmentFacade();
    Set<Task> independentTasks = new HashSet<Task>();
    traverse(facade, facade.getRootTask(), independentTasks);
    for (Iterator<Task> it = independentTasks.iterator(); it.hasNext();) {
      Task next = it.next();
      buildDistanceGraph(next);
    }
    fulfilDependencies();
    myDistance2dependencyList.clear();
    isRunning = false;
  }

  public boolean isRunning() {
    return isRunning;
  }

  private void traverse(TaskContainmentHierarchyFacade facade, Task root, Set<Task> independentTasks) {
    TaskDependency[] asDependant = root.getDependenciesAsDependant().toArray();
    if (asDependant.length == 0) {
      independentTasks.add(root);
    }
    for (Task nestedTask : facade.getNestedTasks(root)) {
      traverse(facade, nestedTask, independentTasks);
    }
  }

  private void fulfilDependencies() throws TaskDependencyException {
    for (Map.Entry<Integer, List<TaskDependency>> distance : myDistance2dependencyList.entrySet()) {
      List<TaskDependency> dependenciesList = distance.getValue();
      for (TaskDependency dependency : dependenciesList) {
        TaskDependencyConstraint constraint = dependency.getConstraint();
        TaskDependencyConstraint.Collision collision = constraint.getCollision();
        if (collision.isActive()) {
          fulfilConstraints(dependency);
        }
      }
    }
  }

  private void fulfilConstraints(TaskDependency dependency) throws TaskDependencyException {
        Task dependant = dependency.getDependant();
        TaskDependency[] depsAsDependant = dependant.getDependenciesAsDependant().toArray();
        if (depsAsDependant.length > 0) {
            List<GanttCalendar> startLaterVariations = new ArrayList<>();
            List<GanttCalendar> startEarlierVariations = new ArrayList<>();
            List<GanttCalendar> noVariations = new ArrayList<>();

            categorizeDependencyVariations(depsAsDependant, startEarlierVariations, startLaterVariations, noVariations);

            validateNoVariations(dependant, noVariations);

            Collections.sort(startEarlierVariations, GanttCalendar.COMPARATOR);
            Collections.sort(startLaterVariations, GanttCalendar.COMPARATOR);

            GanttCalendar solution = determineSolution(dependant, startEarlierVariations, startLaterVariations, noVariations);

            modifyTaskStart(dependant, solution);
        }
    }

    private void categorizeDependencyVariations(TaskDependency[] depsAsDependant, List<GanttCalendar> startEarlierVariations,
                                                 List<GanttCalendar> startLaterVariations, List<GanttCalendar> noVariations) {
        for (TaskDependency depAsDependant : depsAsDependant) {
            TaskDependencyConstraint.Collision nextCollision = depAsDependant.getConstraint().getCollision();
            GanttCalendar acceptableStart = nextCollision.getAcceptableStart();
            switch (nextCollision.getVariation()) {
                case TaskDependencyConstraint.Collision.START_EARLIER_VARIATION:
                    startEarlierVariations.add(acceptableStart);
                    break;
                case TaskDependencyConstraint.Collision.START_LATER_VARIATION:
                    startLaterVariations.add(acceptableStart);
                    break;
                case TaskDependencyConstraint.Collision.NO_VARIATION:
                    noVariations.add(acceptableStart);
                    break;
            }
        }
    }

    private void validateNoVariations(Task dependant, List<GanttCalendar> noVariations) throws TaskDependencyException {
        if (noVariations.size() > 1) {
            throw new TaskDependencyException("Failed to fulfill constraints of task=" + dependant + ". There are "
                    + noVariations.size() + " constraints which don't allow for task start variation");
        }
    }

    private GanttCalendar determineSolution(Task dependant, List<GanttCalendar> startEarlierVariations,
                                           List<GanttCalendar> startLaterVariations, List<GanttCalendar> noVariations)
            throws TaskDependencyException {
        GanttCalendar earliestStart = startEarlierVariations.isEmpty() ? null : startEarlierVariations.get(0);
        GanttCalendar latestStart = !startLaterVariations.isEmpty() ? startLaterVariations.get(startLaterVariations.size() - 1)
                : null;
        if (earliestStart == null && latestStart == null) {
            return dependant.getStart();
        } else {
            if (earliestStart == null) {
                earliestStart = latestStart;
            } else if (latestStart == null) {
                latestStart = earliestStart;
            }
            if (earliestStart.compareTo(latestStart) < 0) {
                throw new TaskDependencyException("Failed to fulfill constraints of task=" + dependant);
            }
        }
        if (!noVariations.isEmpty()) {
            GanttCalendar notVariableStart = noVariations.get(0);
            if (notVariableStart.compareTo(earliestStart) < 0 || notVariableStart.compareTo(latestStart) > 0) {
                throw new TaskDependencyException("Failed to fulfill constraints of task=" + dependant);
            }
            return notVariableStart;
        } else {
            return latestStart;
        }
    }


//Refactoring end
    }
    mutator.shift(shift);
    mutator.commit();
    myModifiedTasks.add(task);
  }

  private void buildDistanceGraph(Task changedTask) {
    TaskDependency[] depsAsDependee = changedTask.getDependenciesAsDependee().toArray();
    buildDistanceGraph(depsAsDependee, 1);
  }

  private void buildDistanceGraph(TaskDependency[] deps, int distance) {
    if (deps.length == 0) {
      return;
    }
    var key = distance;
    List<TaskDependency> depsList = myDistance2dependencyList.get(key);
    if (depsList == null) {
      depsList = new ArrayList<TaskDependency>();
      myDistance2dependencyList.put(key, depsList);
    }
    depsList.addAll(Arrays.asList(deps));
    for (TaskDependency dep : deps) {
      Task dependant = dep.getDependant();
      TaskDependency[] nextStepDeps = dependant.getDependenciesAsDependee().toArray();
      buildDistanceGraph(nextStepDeps, ++distance);
    }
  }

  protected abstract TaskContainmentHierarchyFacade createContainmentFacade();
}
