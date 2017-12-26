package io.jenkins.docker.connector;

import com.github.dockerjava.api.exception.NotFoundException;
import hudson.model.Queue;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;
import hudson.slaves.SlaveComputer;
import io.jenkins.docker.DockerTransientNode;

import java.io.IOException;

public final class DockerContainerComputerLauncher extends DelegatingComputerLauncher {
    private final DockerContainerExecuter dockerContainerExecuter;

    DockerContainerComputerLauncher(ComputerLauncher launcher, DockerContainerExecuter dockerContainerExecuter ){
        super(launcher);
        this.dockerContainerExecuter = dockerContainerExecuter;
    }

    public DockerContainerExecuter getDockerContainerExecuter() {
        return dockerContainerExecuter;
    }

    public String getContainerId(){
        return dockerContainerExecuter.getContainerId();
    }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
        try {
            super.launch(computer, listener);
            final String containerId = dockerContainerExecuter.getContainerId();
            dockerContainerExecuter.getDockerAPI().getClient().inspectContainerCmd(containerId).exec();
        } catch (NotFoundException e) {
            // Container has been removed
            Queue.withLock( () -> {
                DockerTransientNode node = (DockerTransientNode) computer.getNode();
                node.terminate(listener);
            });
            return;
        }
    }
}
