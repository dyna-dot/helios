/**
 * Copyright (C) 2014 Spotify AB
 */

package com.spotify.helios.system;

import com.spotify.helios.client.HeliosClient;
import com.spotify.helios.common.descriptors.Deployment;
import com.spotify.helios.common.descriptors.Job;
import com.spotify.helios.common.descriptors.JobId;
import com.spotify.helios.common.protocol.CreateJobResponse;
import com.spotify.helios.common.protocol.JobDeleteResponse;
import com.spotify.helios.common.protocol.JobDeployResponse;
import com.spotify.helios.common.protocol.JobUndeployResponse;

import org.junit.Test;

import static com.spotify.helios.common.descriptors.Goal.START;
import static com.spotify.helios.common.descriptors.HostStatus.Status.UP;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.junit.Assert.assertEquals;

public class UndeployRaceTest extends SystemTestBase {

  @Test
  public void test() throws Exception {
    startDefaultMaster();

    final String agentId = "test-agent-id";

    final HeliosClient client = defaultClient();

    // Register a host without the agent running
    client.registerHost(getTestHost(), agentId);

    // Create, deploy and undeploy a job on the host without the agent running
    final Job job = Job.newBuilder()
        .setName(JOB_NAME)
        .setVersion(JOB_VERSION)
        .setImage("ubuntu:12.04")
        .setCommand(DO_NOTHING_COMMAND)
        .build();
    final JobId jobId = job.getId();
    final CreateJobResponse created = client.createJob(job).get();
    assertEquals(CreateJobResponse.Status.OK, created.getStatus());

    final Deployment deployment = Deployment.of(jobId, START);
    final JobDeployResponse deployed = client.deploy(deployment, getTestHost()).get();
    assertEquals(JobDeployResponse.Status.OK, deployed.getStatus());

    final JobUndeployResponse undeployed = client.undeploy(jobId, getTestHost()).get();
    assertEquals(JobUndeployResponse.Status.OK, undeployed.getStatus());

    // Start agent
    startDefaultAgent(getTestHost(), "--id", agentId);

    awaitHostRegistered(client, getTestHost(), LONG_WAIT_MINUTES, MINUTES);
    awaitHostStatus(client, getTestHost(), UP, LONG_WAIT_MINUTES, MINUTES);

    // Wait for the task to disappear
    awaitTaskGone(client, getTestHost(), jobId, LONG_WAIT_MINUTES, MINUTES);

    // Verify that the job can be deleted
    assertEquals(JobDeleteResponse.Status.OK, client.deleteJob(jobId).get().getStatus());
  }
}