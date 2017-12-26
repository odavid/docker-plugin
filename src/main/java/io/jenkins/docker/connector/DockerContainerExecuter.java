package io.jenkins.docker.connector;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import hudson.model.TaskListener;
import io.jenkins.docker.client.DockerAPI;

import java.io.IOException;

public interface DockerContainerExecuter {
    DockerAPI getDockerAPI();

    TaskListener getTaskListener();

    String getWorkdir();

    CreateContainerCmd getCreateContainerCmd();

    String getContainerId() throws IllegalStateException;

    InspectContainerResponse executeContainer() throws IOException, InterruptedException;
}
