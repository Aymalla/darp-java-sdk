/*
 * Copyright 2023 The Dapr Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
limitations under the License.
*/

package io.dapr.examples.unittesting;

import com.microsoft.durabletask.TaskCanceledException;
import io.dapr.workflows.runtime.Workflow;
import io.dapr.workflows.runtime.WorkflowContext;
import io.dapr.workflows.runtime.WorkflowStub;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;

/**
 * 1. Build and install jars:
 * mvn clean install
 * 2. cd [repo root]/examples
 * 3. Run the test code:
 * java -jar target/dapr-java-sdk-examples-exec.jar \
 *     org.junit.platform.console.ConsoleLauncher --select-class=io.dapr.examples.unittesting.DaprWorkflowExampleTest
 */
public class DaprWorkflowExampleTest {
  private static final String timeoutWorkflow = "DemoWorkflowTimeout";
  private static final String noTimeoutWorkflow = "DemoWorkflowNoTimeout";
  private static final String workflowDefaultId = "demo-workflow-123";

  private class DemoWorkflow extends Workflow {

    @Override
    public WorkflowStub create() {
      return ctx -> {
        String name = ctx.getName().block();
        String id = ctx.getInstanceId().block();
        try {
          ctx.waitForExternalEvent(name, Duration.ofMillis(100)).block();
        } catch (TaskCanceledException e) {
          ctx.getLogger().warn("Timed out");
        }
        String output = name + ":" + id;
        ctx.complete(output).block();
      };
    }
  }

  @Test
  public void testWorkflow() {
    String name = noTimeoutWorkflow;
    String id = workflowDefaultId;
    WorkflowContext mockContext = createMockContext(name, id);

    new DemoWorkflow().runAsync(mockContext).block();

    String expectedOutput = name + ":" + id;
    Mockito.verify(mockContext, Mockito.times(1)).complete(expectedOutput);
  }

  @Test
  public void testWorkflowWaitForEventTimeout() {
    WorkflowContext mockContext = createMockContext(timeoutWorkflow, workflowDefaultId);
    Logger mockLogger = Mockito.mock(Logger.class);
    Mockito.doReturn(mockLogger).when(mockContext).getLogger();

    new DemoWorkflow().runAsync(mockContext).block();

    Mockito.verify(mockLogger, Mockito.times(1)).warn("Timed out");
  }

  @Test
  public void testWorkflowWaitForEventNoTimeout() {
    WorkflowContext mockContext = createMockContext(noTimeoutWorkflow, workflowDefaultId);
    Logger mockLogger = Mockito.mock(Logger.class);
    Mockito.doReturn(mockLogger).when(mockContext).getLogger();

    new DemoWorkflow().runAsync(mockContext).block();

    Mockito.verify(mockLogger, Mockito.times(0)).warn(anyString());
  }

  private WorkflowContext createMockContext(String name, String id) {
    WorkflowContext mockContext = Mockito.mock(WorkflowContext.class);

    Mockito.doReturn(Mono.just(name)).when(mockContext).getName();
    Mockito.doReturn(Mono.just(id)).when(mockContext).getInstanceId();
    Mockito.doReturn(Mono.empty().then())
        .when(mockContext).waitForExternalEvent(startsWith(noTimeoutWorkflow), any(Duration.class));
    Mockito.doThrow(TaskCanceledException.class)
        .when(mockContext).waitForExternalEvent(startsWith(timeoutWorkflow), any(Duration.class));
    Mockito.doReturn(Mono.empty().then())
        .when(mockContext).complete(any(Object.class));

    return mockContext;
  }
}