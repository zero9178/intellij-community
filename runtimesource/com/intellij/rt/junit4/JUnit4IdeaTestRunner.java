package com.intellij.rt.junit4;

import com.intellij.rt.execution.junit.DeafStream;
import com.intellij.rt.execution.junit.IdeaTestRunner;
import com.intellij.rt.execution.junit.segments.OutputObjectRegistryEx;
import com.intellij.rt.execution.junit.segments.Packet;
import com.intellij.rt.execution.junit.segments.PoolOfDelimiters;
import com.intellij.rt.execution.junit.segments.SegmentedOutputStream;
import junit.textui.ResultPrinter;
import org.junit.internal.requests.ClassRequest;
import org.junit.internal.requests.FilterRequest;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.notification.RunListener;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;

/** @noinspection UnusedDeclaration*/
public class JUnit4IdeaTestRunner implements IdeaTestRunner {
  private RunListener myTestsListener;
  private OutputObjectRegistryEx myRegistry;

  private static void sendNode(Description test, Packet packet) {
    final ArrayList children = test.getChildren();
    packet.addObject(test).addLong(children.size());
    for (int i = 0; i < children.size(); i++) {
      sendNode((Description)children.get(i), packet);
    }
  }

  public static void sendTree(OutputObjectRegistryEx registry, Description suite) {
    Packet packet = registry.createPacket();
    packet.addString(PoolOfDelimiters.TREE_PREFIX);
    sendNode(suite, packet);
    packet.addString("\n");
    packet.send();
  }

  public int startRunnerWithArgs(String[] args) {
    try {
      final JUnitCore runner = new JUnitCore();

      final Request request = JUnit4TestRunnerUtil.buildRequest(args);

      try {
        Description description = request.getRunner().getDescription();
        if (request instanceof ClassRequest) {
          description = getSuiteMethodDescription(request, description);
        }
        else if (request instanceof FilterRequest) {
          description = getFilteredDescription(request, description);
        }
        sendTree(myRegistry, description);
      }
      catch (Exception e) {
        //noinspection HardCodedStringLiteral
        System.err.println("Internal Error occured.");
        e.printStackTrace(System.err);
      }

      runner.addListener(myTestsListener);
      try {
        final Object data =
          Class.forName("com.intellij.rt.coverage.data.ProjectData").getMethod("getProjectData", new Class[0]).invoke(null, new Object[0]);
        if (data != null) {
          runner.addListener(new RunListener() {
            public void testStarted(Description description) {
              ((com.intellij.rt.coverage.data.ProjectData)data).testStarted(description.getClassName() + "." + description.getMethodName());
            }

            public void testFinished(Description description) {
              ((com.intellij.rt.coverage.data.ProjectData)data).testEnded(description.getClassName() + "." + description.getMethodName());
            }

          });
        }
      }
      catch (Throwable e) {
        //coverage was not enabled
      }

      long startTime = System.currentTimeMillis();
      Result result = runner.run(request/*.sortWith(new Comparator() {
        public int compare(Object d1, Object d2) {
          return ((Description)d1).getDisplayName().compareTo(((Description)d2).getDisplayName());
        }
      })*/);
      long endTime = System.currentTimeMillis();
      long runTime = endTime - startTime;
      new TimeSender().printHeader(runTime);

      if (!result.wasSuccessful()) {
        return -1;
      }
      return 0;
    }
    catch (Exception e) {
      e.printStackTrace(System.err);
      return -2;
    }
  }

  private static Description getFilteredDescription(Request request, Description description) throws NoSuchFieldException, IllegalAccessException {
    final Field field = FilterRequest.class.getDeclaredField("fFilter");
    field.setAccessible(true);
    final Filter filter = (Filter)field.get(request);
    final String filterDescription = filter.describe();
    try {
      final Description failedTestsDescription = Description.createSuiteDescription(filterDescription, null);
      for (Iterator iterator = description.getChildren().iterator(); iterator.hasNext();) {
        final Description childDescription = (Description)iterator.next();
        if (filter.shouldRun(childDescription)) {
          failedTestsDescription.addChild(childDescription);
        }
      }
      description = failedTestsDescription;
      if (failedTestsDescription.testCount() == 1 && filterDescription.startsWith("Method")) {
        description = (Description)failedTestsDescription.getChildren().get(0);
      }
    }
    catch (NoSuchMethodError e) {
      //junit 4.0 doesn't have method createSuite(String, Annotation...) : skip it
    }
    return description;
  }

  private static Description getSuiteMethodDescription(Request request, Description description) throws NoSuchFieldException, IllegalAccessException {
    final Field field = ClassRequest.class.getDeclaredField("fTestClass");
    field.setAccessible(true);
    final Description methodDescription = Description.createSuiteDescription((Class)field.get(request));
    for (Iterator iterator = description.getChildren().iterator(); iterator.hasNext();) {
      methodDescription.addChild((Description)iterator.next());
    }
    description = methodDescription;
    return description;
  }


  public void setStreams(SegmentedOutputStream segmentedOut, SegmentedOutputStream segmentedErr) {
    myRegistry = new JUnit4OutputObjectRegistry(segmentedOut, segmentedErr);
    myTestsListener = new JUnit4TestResultsSender(myRegistry, segmentedErr);
  }

  private class TimeSender extends ResultPrinter {
    public TimeSender() {
      super(DeafStream.DEAF_PRINT_STREAM);
    }

    protected void printHeader(long runTime) {
      myRegistry.createPacket().addString(PoolOfDelimiters.TESTS_DONE).addLong(runTime).send();
    }
  }
}
