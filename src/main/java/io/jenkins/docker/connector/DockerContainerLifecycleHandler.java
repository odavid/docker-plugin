package io.jenkins.docker.connector;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import io.jenkins.docker.client.DockerAPI;

import java.io.IOException;
import java.io.Serializable;

public class DockerContainerLifecycleHandler implements Serializable{
    private static final long serialVersionUID = 1L;

    /**
     * Can be overridden by concrete implementations to provide some customization to the container creation command
     */
    public void beforeContainerCreated(DockerAPI api, String workdir, CreateContainerCmd cmd) throws IOException, InterruptedException {}

    /**
     * Container has been created but not started yet, that's a good opportunity to inject <code>remoting.jar</code>
     * using {@link DockerComputerConnector#injectRemotingJar(String, String, DockerClient)}
     */
    public void beforeContainerStarted(DockerAPI api, String workdir, String containerId) throws IOException, InterruptedException {}

    /**
     * Container has started. Good place to check it's healthy before considering agent is ready to accept connexions
     */
    public void afterContainerStarted(DockerAPI api, String workdir, String containerId) throws IOException, InterruptedException {}
}
