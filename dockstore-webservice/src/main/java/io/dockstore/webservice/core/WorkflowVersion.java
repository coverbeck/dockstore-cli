/*
 *    Copyright 2016 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.dockstore.webservice.core;

import javax.persistence.Column;
import javax.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * This implements version for a Workflow.
 * 
 * @author dyuen
 */
@ApiModel(value = "Workflow", description = "This describes one tag associated with a container.")
@Entity
public class WorkflowVersion extends Version<WorkflowVersion> {

    @Column(columnDefinition = "text")
    @JsonProperty("workflow_path")
    @ApiModelProperty("Path for the workflow")
    private String workflowPath;


    public WorkflowVersion() {
        super();
    }

    public void updateByUser(final WorkflowVersion workflowVersion) {
        super.updateByUser(workflowVersion);
        workflowPath = workflowVersion.workflowPath;
    }

    public void update(WorkflowVersion workflowVersion) {
        super.update(workflowVersion);
        super.setReference(workflowVersion.getReference());
    }

    public void clone(WorkflowVersion tag) {
        super.clone(tag);
        super.setReference(tag.getReference());
    }


    @JsonProperty
    public String getWorkflowPath() {
        return workflowPath;
    }

    public void setWorkflowPath(String workflowPath) {
        this.workflowPath = workflowPath;
    }

    @Override
    public int compareTo(WorkflowVersion o) {
        return Long.compare(super.getId(), o.getId());
    }
}