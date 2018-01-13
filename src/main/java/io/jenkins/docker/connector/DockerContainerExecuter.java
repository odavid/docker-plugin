package io.jenkins.docker.connector;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.DockerException;
import hudson.model.TaskListener;
import io.jenkins.docker.client.DockerAPI;

import java.io.IOException;
import java.io.Serializable;

public class DockerContainerExecuter implements Serializable{

    private static final long serialVersionUID = 1L;

    public InspectContainerResponse executeContainer(DockerAPI dockerAPI, TaskListener listener, CreateContainerCmd cmd, String workdir, DockerComputerConnector connector) throws IOException, InterruptedException{
        final DockerClient client = dockerAPI.getClient();

        connector.beforeContainerCreated(dockerAPI, workdir, cmd);
        final String containerId = cmd.exec().getId();
        try {
            connector.beforeContainerStarted(dockerAPI, workdir, containerId);
            client.startContainerCmd(containerId).exec();
            connector.afterContainerStarted(dockerAPI, workdir, containerId);
        } catch (DockerException e) {
            // if something went wrong, cleanup aborted container
            client.removeContainerCmd(containerId).withForce(true).exec();
            throw e;
        }
        final InspectContainerResponse inspect = dockerAPI.getClient().inspectContainerCmd(containerId).exec();
        final Boolean running = inspect.getState().getRunning();
        if (Boolean.FALSE.equals(running)) {
            listener.error("Container {} is not running. {}", containerId, inspect.getState().getStatus());
            throw new IOException("Container is not running.");
        }

        return inspect;
    }
}
