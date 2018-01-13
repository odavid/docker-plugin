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
    private final DockerContainerExecuter dockerContainerExecuter;
    private final DockerAPI api;

    DockerContainerComputerLauncher(ComputerLauncher launcher, DockerContainerExecuter dockerContainerExecuter, DockerAPI api){
        super(launcher);
        this.dockerContainerExecuter = dockerContainerExecuter;
        this.api = api;
    }

    public String getContainerUniqueName(){
        return dockerContainerExecuter.getContainerUniqueName();
    }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
        try {
            super.launch(computer, listener);
            final String containerUniqueName = getContainerUniqueName();
            InspectContainerResponse response = api.getClient().inspectContainerCmd(containerUniqueName).exec();
            if(computer instanceof DockerComputer){
                ((DockerComputer)computer).getNode().setRealContainerId(response.getId());
            }
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
