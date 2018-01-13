package io.jenkins.docker.connector;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import hudson.model.Queue;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;
import hudson.slaves.SlaveComputer;
import io.jenkins.docker.DockerComputer;
import io.jenkins.docker.DockerTransientNode;
import io.jenkins.docker.client.DockerAPI;

import java.io.IOException;

public final class DockerContainerComputerLauncher extends DelegatingComputerLauncher {
    private final DockerAPI api;

    public DockerContainerComputerLauncher(ComputerLauncher launcher, DockerAPI api){
        super(launcher);
        this.api = api;
    }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
        try {
            super.launch(computer, listener);
            final String containerUniqueName = ((DockerComputer)computer).getNode().getContainerId();
            InspectContainerResponse response = api.getClient().inspectContainerCmd(containerUniqueName).exec();
            ((DockerComputer)computer).getNode().setRealContainerId(response.getId());
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
