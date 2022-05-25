/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.engine.test.api.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Attachment;
import org.camunda.bpm.engine.task.IdentityLinkType;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@Deployment(resources = "org/camunda/bpm/engine/test/api/oneTaskProcess.bpmn20.xml")
public class TaskQueryLastUpdatedTest {

  @Rule
  public ProcessEngineRule engineRule = new ProvidedProcessEngineRule();

  TaskService taskService;
  RuntimeService runtimeService;

  @Before
  public void setUp() {
    taskService = engineRule.getTaskService();
    runtimeService = engineRule.getRuntimeService();
  }

  @After
  public void tearDown() {
    List<Task> tasks = taskService.createTaskQuery().list();
    for (Task task : tasks) {
      // standalone tasks (deployed process are cleaned up by the engine rule)
      if(task.getProcessDefinitionId() == null) {
        taskService.deleteTask(task.getId(), true);
      }
    }
  }

  // make sure that time passes between two fast operations
  public Date getAfterCurrentTime() {
    return new Date(ClockUtil.getCurrentTime().getTime() + 1L);
  }

  //make sure that time passes between two fast operations
  public Date getBeforeCurrentTime() {
    return new Date(ClockUtil.getCurrentTime().getTime() - 1L);
  }

  @Test
  public void shouldFindTaskLastUpdatedNullUseCreateDate() {
    // given
    Date beforeStart = getBeforeCurrentTime();
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");

    // when
    // no update to task, lastUpdated = null

    // then
    Task taskResult = taskService.createTaskQuery().processInstanceId(processInstance.getId()).taskUpdatedAfter(beforeStart).singleResult();
    assertThat(taskResult).isNotNull();
    assertThat(taskResult.getLastUpdated()).isNull();
    assertThat(taskResult.getCreateTime()).isAfter(beforeStart);
  }

  @Test
  public void shouldNotFindTaskLastUpdatedNullCreateDateBeforeQueryDate() {
    // given
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    Date afterStart = getAfterCurrentTime();

    // when
    // no update to task, lastUpdated = null

    // then
    Task taskResult = taskService.createTaskQuery().processInstanceId(processInstance.getId()).taskUpdatedAfter(afterStart).singleResult();
    assertThat(taskResult).isNull();
  }

  @Test
  public void shouldNotFindTaskLastUpdatedBeforeQueryDate() {
    // given
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    Date lastUpdatedBeforeUpdate = task.getLastUpdated();
    task.setAssignee("myself");

    // when
    taskService.saveTask(task);

    // then
    assertThat(lastUpdatedBeforeUpdate).isNull();
    Date afterUpdate = getAfterCurrentTime();
    Task taskResult = taskService.createTaskQuery().processInstanceId(processInstance.getId()).taskUpdatedAfter(afterUpdate).singleResult();
    assertThat(taskResult).isNull();
    taskResult = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    assertThat(taskResult).isNotNull();
    assertThat(taskResult.getLastUpdated()).isBefore(afterUpdate);
  }

  @Test
  public void shouldNotFindTaskLastUpdatedEqualsQueryDate() {
    // given
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    task.setAssignee("myself");

    // when
    taskService.saveTask(task);

    // then
    Task taskResult = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    assertThat(taskResult).isNotNull();
    assertThat(taskResult.getLastUpdated()).isNotNull();
    taskResult = taskService.createTaskQuery().processInstanceId(processInstance.getId()).taskUpdatedAfter(taskResult.getLastUpdated()).singleResult();
    assertThat(taskResult).isNull();
  }

  @Test
  public void shouldNotSetLastUpdatedWithoutTaskUpdate() {
    // given
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");

    // when
    // no update to task, lastUpdated = null

    // then
    Task taskResult = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    assertThat(taskResult.getLastUpdated()).isNull();
  }

  @Test
  public void shouldSetLastUpdatedToExactlyNow() {
    // given
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    Date beforeUpdate = getBeforeCurrentTime();
    // fix time to one ms after
    Date now = new Date(beforeUpdate.getTime() + 1L);
    ClockUtil.setCurrentTime(now);

    // when
    taskService.setAssignee(task.getId(), "myself");

    // then
    assertThat(task.getLastUpdated()).isNull();
    Task taskResult = taskService.createTaskQuery().processInstanceId(processInstance.getId()).taskUpdatedAfter(beforeUpdate).singleResult();
    assertThat(taskResult).isNotNull();
    assertThat(taskResult.getLastUpdated()).isEqualTo(now);
  }

  @Test
  public void shouldSetLastUpdatedOnDescriptionChange() {
    // given
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    Date lastUpdatedBeforeUpdate = task.getLastUpdated();
    task.setDescription("updated");
    Date beforeUpdate = getBeforeCurrentTime();

    // when
    taskService.saveTask(task);

    // then
    assertThat(lastUpdatedBeforeUpdate).isNull();
    Task taskResult = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    assertThat(taskResult.getLastUpdated()).isAfter(beforeUpdate);
  }

  @Test
  public void shouldSetLastUpdatedOnAssigneeChange() {
    // given
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    Date lastUpdatedBeforeUpdate = task.getLastUpdated();
    task.setAssignee("myself");
    Date beforeUpdate = getBeforeCurrentTime();

    // when
    taskService.saveTask(task);

    // then
    assertThat(lastUpdatedBeforeUpdate).isNull();
    Task taskResult = taskService.createTaskQuery().processInstanceId(processInstance.getId()).taskUpdatedAfter(beforeUpdate).singleResult();
    assertThat(taskResult).isNotNull();
    assertThat(taskResult.getLastUpdated()).isAfter(beforeUpdate);
  }

  @Test
  public void shouldSetLastUpdatedOnVariableChange() {
    // given
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    Date beforeUpdate = getBeforeCurrentTime();

    // when
    taskService.setVariableLocal(task.getId(), "myVariable", "variableValue");

    // then
    assertThat(task.getLastUpdated()).isNull();
    Task taskResult = taskService.createTaskQuery().processInstanceId(processInstance.getId()).taskUpdatedAfter(beforeUpdate).singleResult();
    assertThat(taskResult).isNotNull();
    assertThat(taskResult.getLastUpdated()).isAfter(beforeUpdate);
  }

  @Test
  public void shouldSetLastUpdatedOnCreateAttachment() {
    // given
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    Date beforeUpdate = getBeforeCurrentTime();

    // when
    taskService.createAttachment(null, task.getId(), processInstance.getId(), "myAttachment", "attachmentDescription", "http://camunda.com");

    // then
    assertThat(task.getLastUpdated()).isNull();
    Task taskResult = taskService.createTaskQuery().processInstanceId(processInstance.getId()).taskUpdatedAfter(beforeUpdate).singleResult();
    assertThat(taskResult).isNotNull();
    assertThat(taskResult.getLastUpdated()).isAfter(beforeUpdate);
  }

  @Test
  public void shouldSetLastUpdatedOnChangeAttachment() {
    // given
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    Attachment attachment = taskService.createAttachment(null, task.getId(), processInstance.getId(), "myAttachment", "attachmentDescription", "http://camunda.com");
    attachment.setDescription("updatedDescription");
    Date beforeUpdate = getBeforeCurrentTime();

    // when
    taskService.saveAttachment(attachment);

    // then
    assertThat(task.getLastUpdated()).isNull();
    Task taskResult = taskService.createTaskQuery().processInstanceId(processInstance.getId()).taskUpdatedAfter(beforeUpdate).singleResult();
    assertThat(taskResult).isNotNull();
    assertThat(taskResult.getLastUpdated()).isAfter(beforeUpdate);
  }

  @Test
  public void shouldSetLastUpdatedOnDeleteAttachment() {
    // given
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    Attachment attachment = taskService.createAttachment(null, task.getId(), processInstance.getId(), "myAttachment", "attachmentDescription", "http://camunda.com");
    Date beforeUpdate = getBeforeCurrentTime();

    // when
    taskService.deleteAttachment(attachment.getId());

    // then
    assertThat(task.getLastUpdated()).isNull();
    Task taskResult = taskService.createTaskQuery().processInstanceId(processInstance.getId()).taskUpdatedAfter(beforeUpdate).singleResult();
    assertThat(taskResult).isNotNull();
    assertThat(taskResult.getLastUpdated()).isAfter(beforeUpdate);
  }

  @Test
  public void shouldSetLastUpdatedOnComment() {
    // given
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    Date beforeUpdate = getBeforeCurrentTime();

    // when
    taskService.createComment(task.getId(), processInstance.getId(), "my comment");

    // then
    assertThat(task.getLastUpdated()).isNull();
    Task taskResult = taskService.createTaskQuery().processInstanceId(processInstance.getId()).taskUpdatedAfter(beforeUpdate).singleResult();
    assertThat(taskResult).isNotNull();
    assertThat(taskResult.getLastUpdated()).isAfter(beforeUpdate);
  }

  @Test
  public void shouldSetLastUpdatedOnClaimTask() {
    // given
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    Date beforeUpdate = getBeforeCurrentTime();

    // when
    taskService.claim(task.getId(), "myself");

    // then
    assertThat(task.getLastUpdated()).isNull();
    Task taskResult = taskService.createTaskQuery().processInstanceId(processInstance.getId()).taskUpdatedAfter(beforeUpdate).singleResult();
    assertThat(taskResult).isNotNull();
    assertThat(taskResult.getLastUpdated()).isAfter(beforeUpdate);
  }

  @Test
  public void shouldSetLastUpdatedOnEveryPropertyChange() {
    // given
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    Date actualBeforeFirstUpdate = task.getLastUpdated();
    Date beforeUpdates = getBeforeCurrentTime();

    // when
    task.setAssignee("myself");
    taskService.saveTask(task);

    Date expectedBeforeSecondUpdate = getBeforeCurrentTime();

    task.setName("My Task");
    taskService.saveTask(task);

    // then
    assertThat(actualBeforeFirstUpdate).isNull();
    Task taskResult = taskService.createTaskQuery().processInstanceId(processInstance.getId()).taskUpdatedAfter(beforeUpdates).singleResult();
    assertThat(taskResult).isNotNull();
    assertThat(taskResult.getLastUpdated()).isAfter(expectedBeforeSecondUpdate);
  }

  @Test
  public void shouldSetLastUpdatedOnDelegate() {
    // given
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    Date actualBeforeClaim = task.getLastUpdated();
    Date beforeUpdates = getBeforeCurrentTime();

    // when
    taskService.claim(task.getId(), "myself");
    Date beforeDelegate = getBeforeCurrentTime();
    taskService.delegateTask(task.getId(), "someone");

    // then
    assertThat(actualBeforeClaim).isNull();
    Task taskResult = taskService.createTaskQuery().processInstanceId(processInstance.getId()).taskUpdatedAfter(beforeUpdates).singleResult();
    assertThat(taskResult).isNotNull();
    assertThat(taskResult.getLastUpdated()).isAfter(beforeDelegate);
  }

  @Test
  public void shouldSetLastUpdatedOnResolve() {
    // given
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    Date beforeUpdate = getBeforeCurrentTime();

    // when
    taskService.resolveTask(task.getId());

    // then
    assertThat(task.getLastUpdated()).isNull();
    Task taskResult = taskService.createTaskQuery().processInstanceId(processInstance.getId()).taskUpdatedAfter(beforeUpdate).singleResult();
    assertThat(taskResult).isNotNull();
    assertThat(taskResult.getLastUpdated()).isAfter(beforeUpdate);
  }

  @Test
  public void shouldSetLastUpdatedOnAddIdentityLink() {
    // given
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    Date beforeUpdate = getBeforeCurrentTime();

    // when
    taskService.addCandidateUser(task.getId(), "myself");

    // then
    assertThat(task.getLastUpdated()).isNull();
    Task taskResult = taskService.createTaskQuery().processInstanceId(processInstance.getId()).taskUpdatedAfter(beforeUpdate).singleResult();
    assertThat(taskResult).isNotNull();
    assertThat(taskResult.getLastUpdated()).isAfter(beforeUpdate);
  }

  @Test
  public void shouldSetLastUpdatedOnDeleteIdentityLink() {
    // given
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    taskService.addCandidateUser(task.getId(), "myself");
    Date beforeUpdate = getBeforeCurrentTime();

    // when
    taskService.deleteUserIdentityLink(task.getId(), "myself", IdentityLinkType.CANDIDATE);

    // then
    assertThat(task.getLastUpdated()).isNull();
    Task taskResult = taskService.createTaskQuery().processInstanceId(processInstance.getId()).taskUpdatedAfter(beforeUpdate).singleResult();
    assertThat(taskResult).isNotNull();
    assertThat(taskResult.getLastUpdated()).isAfter(beforeUpdate);
  }

  @Test
  public void shouldSetLastUpdatedOnPriorityChange() {
    // given
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    Date beforeUpdate = getBeforeCurrentTime();

    // when
    taskService.setPriority(task.getId(), 1);

    // then
    assertThat(task.getLastUpdated()).isNull();
    Task taskResult = taskService.createTaskQuery().processInstanceId(processInstance.getId()).taskUpdatedAfter(beforeUpdate).singleResult();
    assertThat(taskResult).isNotNull();
    assertThat(taskResult.getLastUpdated()).isAfter(beforeUpdate);
  }

  @Test
  @Deployment(resources = {"org/camunda/bpm/engine/test/api/form/DeployedCamundaFormSingleTaskProcess.bpmn20.xml",
        "org/camunda/bpm/engine/test/api/form/task.html"})
  public void shouldSetLastUpdatedOnSubmitTaskForm() {
    // given
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("FormsProcess");
    Task task = taskService.createTaskQuery().processInstanceId(processInstance.getId()).singleResult();
    // delegate is necessary so that submitting the form does not complete the task
    taskService.delegateTask(task.getId(), "myself");
    Date beforeSubmit = getBeforeCurrentTime();

    // when
    engineRule.getFormService().submitTaskForm(task.getId(), null);

    // then
    Task taskResult = taskService.createTaskQuery().taskUpdatedAfter(beforeSubmit).singleResult();
    assertThat(taskResult).isNotNull();
    assertThat(taskResult.getLastUpdated()).isAfter(beforeSubmit);
  }

  @Test
  public void shouldReturnResultsOrderedAsc() {
    // given
    ProcessInstance processInstance1 = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    ProcessInstance processInstance2 = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    ProcessInstance processInstance3 = runtimeService.startProcessInstanceByKey("oneTaskProcess");

    Date beforeUpdates = getBeforeCurrentTime();

    Task task2 = taskService.createTaskQuery().processInstanceId(processInstance2.getId()).singleResult();
    taskService.setOwner(task2.getId(), "myself");

    Task task1 = taskService.createTaskQuery().processInstanceId(processInstance1.getId()).singleResult();
    taskService.setVariableLocal(task1.getId(), "myVar", "varVal");

    Task task3 = taskService.createTaskQuery().processInstanceId(processInstance3.getId()).singleResult();
    taskService.setPriority(task3.getId(), 3);

    // when
    List<Task> tasks = taskService.createTaskQuery()
        .processInstanceIdIn(processInstance1.getId(), processInstance2.getId(), processInstance3.getId())
        .taskUpdatedAfter(beforeUpdates).orderByTaskUpdatedAfter().asc().list();

    // then
    assertThat(tasks).hasSize(3);
    assertThat(tasks).extracting("id").containsExactly(task2.getId(), task1.getId(), task3.getId());
    assertThat(tasks).extracting("lastUpdated").isSorted();
  }

  @Test
  public void shouldReturnResultsOrderedDesc() {
    // given
    ProcessInstance processInstance1 = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    ProcessInstance processInstance2 = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    ProcessInstance processInstance3 = runtimeService.startProcessInstanceByKey("oneTaskProcess");

    Date beforeUpdates = getBeforeCurrentTime();

    Task task2 = taskService.createTaskQuery().processInstanceId(processInstance2.getId()).singleResult();
    taskService.setOwner(task2.getId(), "myself");

    Task task1 = taskService.createTaskQuery().processInstanceId(processInstance1.getId()).singleResult();
    taskService.setVariableLocal(task1.getId(), "myVar", "varVal");

    Task task3 = taskService.createTaskQuery().processInstanceId(processInstance3.getId()).singleResult();
    taskService.setPriority(task3.getId(), 3);

    // when
    List<Task> tasks = taskService.createTaskQuery()
        .processInstanceIdIn(processInstance1.getId(), processInstance2.getId(), processInstance3.getId())
        .taskUpdatedAfter(beforeUpdates).orderByTaskUpdatedAfter().desc().list();

    // then
    assertThat(tasks).hasSize(3);
    assertThat(tasks).extracting("id").containsExactly(task3.getId(), task1.getId(), task2.getId());
    assertThat(tasks).extracting("lastUpdated").isSortedAccordingTo(Collections.reverseOrder());
  }

  @Test
  public void shouldFindStandaloneTaskWithoutUpdateByLastUpdated() {
    // given
    Date beforeCreateTask = getBeforeCurrentTime();
    Task task = taskService.newTask();
    task.setAssignee("myself");
    Date beforeSave = getAfterCurrentTime();

    // when
    taskService.saveTask(task);

    // then
    Task taskResult = taskService.createTaskQuery().taskUpdatedAfter(beforeCreateTask).singleResult();
    assertThat(taskResult).isNotNull();
    assertThat(taskResult.getLastUpdated()).isNull();
    assertThat(taskResult.getCreateTime()).isBefore(beforeSave);
  }

  @Test
  public void shouldFindStandaloneTaskWithUpdateByLastUpdated() {
    // given
    Task task = taskService.newTask();
    task.setAssignee("myself");
    Date beforeSave = getAfterCurrentTime();
    taskService.saveTask(task);

    // when
    taskService.setPriority(task.getId(), 2);

    // then
    Task taskResult = taskService.createTaskQuery().taskUpdatedAfter(beforeSave).singleResult();
    assertThat(taskResult).isNotNull();
    assertThat(taskResult.getLastUpdated()).isAfter(beforeSave);
    assertThat(taskResult.getCreateTime()).isBefore(beforeSave);
  }

}
