package io.jenkins.docker.connector;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import hudson.model.TaskListener;
import io.jenkins.docker.client.DockerAPI;

import java.io.IOException;

public interface DockerContainerExecuter {
    String getContainerId() throws IllegalStateException;

    InspectContainerResponse executeContainer(DockerAPI dockerAPI,
                                              TaskListener listener,
                                              CreateContainerCmd cmd,
                                              String workdir,
                                              DockerContainerLifecycleHandler handler)
            throws IOException, InterruptedException;
}
