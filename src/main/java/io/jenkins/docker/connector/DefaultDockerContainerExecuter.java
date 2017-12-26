package io.jenkins.docker.connector;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.DockerException;
import hudson.model.TaskListener;
import io.jenkins.docker.client.DockerAPI;

import java.io.IOException;

public class DefaultDockerContainerExecuter implements DockerContainerExecuter{
    private final DockerAPI dockerAPI;
    private final TaskListener listener;
    private final String workdir;
    private final CreateContainerCmd cmd;
    private final DockerComputerConnector connector;
    // This value is initialized when container is being started
    private String containerId;

    public DefaultDockerContainerExecuter(DockerComputerConnector connector,
                                          DockerAPI dockerAPI, TaskListener listener, String workdir, CreateContainerCmd cmd){
        this.connector = connector;
        this.dockerAPI = dockerAPI;
        this.listener = listener;
        this.workdir = workdir;
        this.cmd = cmd;
    }

    @Override
    public DockerAPI getDockerAPI() {
        return dockerAPI;
    }

    @Override
    public TaskListener getTaskListener() {
        return listener;
    }

    @Override
    public String getWorkdir() {
        return workdir;
    }

    @Override
    public CreateContainerCmd getCreateContainerCmd() {
        return cmd;
    }

    @Override
    public String getContainerId() throws IllegalStateException{
        if(containerId == null){
            throw new IllegalStateException("Container was started yet");
        }
        return containerId;
    }

    @Override
    public InspectContainerResponse executeContainer() throws IOException, InterruptedException{
        final DockerClient client = dockerAPI.getClient();
        connector.beforeContainerCreated(dockerAPI, workdir, cmd);
        containerId = cmd.exec().getId();
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
