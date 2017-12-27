package io.jenkins.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.nirima.jenkins.plugins.docker.DockerCloud;
import com.nirima.jenkins.plugins.docker.DockerOfflineCause;
import com.nirima.jenkins.plugins.docker.strategy.DockerOnceRetentionStrategy;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.slaves.Cloud;
import hudson.slaves.ComputerLauncher;
import io.jenkins.docker.client.DockerAPI;
import io.jenkins.docker.connector.DockerContainerComputerLauncher;
import io.jenkins.docker.connector.DockerContainerExecuter;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

import javax.annotation.Nonnull;
import java.io.IOException;

/**
 * A {@link Slave} node designed to be used only once for a build.
 * 
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerTransientNode extends Slave {
    
    private boolean removeVolumes;

    private String cloudId;

    private final DockerAPI api;

    public DockerTransientNode(@Nonnull String slaveUniqueName, String workdir, DockerAPI api, ComputerLauncher launcher) throws Descriptor.FormException, IOException {
        super("docker-" + slaveUniqueName, workdir, launcher);
        this.api = api;
        setNumExecutors(1);
        setMode(Mode.EXCLUSIVE);
        setRetentionStrategy(new DockerOnceRetentionStrategy(10));
    }

    public String getDisplayName() {
        if (cloudId != null) {
            return getNodeName() + " on " + cloudId;
        }
        return getNodeName();
    }

    public boolean isRemoveVolumes() {
        return removeVolumes;
    }

    public void setRemoveVolumes(boolean removeVolumes) {
        this.removeVolumes = removeVolumes;
    }

    public String getCloudId() {
        return cloudId;
    }

    public void setCloudId(String cloudId) {
        this.cloudId = cloudId;
    }

    public DockerAPI getDockerAPI(){ return api;}
    public String getContainerId(){
        if(getLauncher() instanceof DockerContainerComputerLauncher) {
            return ((DockerContainerComputerLauncher) getLauncher()).getContainerId();
        }else{
            //Old node names were docker-${containerId}. This is for the sake of resolving a DockerSlave
            String nodeName = getNodeName();
            if(StringUtils.isNotEmpty(nodeName) && nodeName.startsWith("docker-")){
                return nodeName.replaceAll("docker-", "");
            }
            throw new IllegalStateException("Cannot get containerName, launcher is not instanceof DockerContainerComputerLauncher");
        }
    }

    @Override
    public DockerComputer createComputer() {
        return new DockerComputer(this);
    }

    public void terminate(TaskListener listener) {
        try {
            toComputer().disconnect(new DockerOfflineCause());
            listener.getLogger().println("Disconnected computer");
        } catch (Exception e) {
            listener.error("Can't disconnect", e);
        }

        Computer.threadPoolForRemoting.submit(() -> {
            final DockerAPI dockerAPI = getDockerAPI();
            final String containerId = getContainerId();
            DockerClient client = dockerAPI.getClient();
            try {
                client.stopContainerCmd(containerId)
                        .withTimeout(10)
                        .exec();
                listener.getLogger().println("Stopped container "+ containerId);
            } catch(NotFoundException e) {
                listener.getLogger().println("Container already removed " + containerId);
            } catch (Exception ex) {
                listener.error("Failed to stop instance " + containerId + " for slave " + name + " due to exception", ex.getMessage());
                listener.error("Causing exception for failure on stopping the instance was", ex);
            }

            try {
                client.removeContainerCmd(containerId)
                        .withRemoveVolumes(removeVolumes)
                        .exec();

                listener.getLogger().println("Removed container " + containerId);
            } catch (NotFoundException e) {
                listener.getLogger().println("Container already gone.");
            } catch (Exception ex) {
                listener.error("Failed to remove instance " + containerId  + " for slave " + name + " due to exception: " + ex.getMessage());
                listener.error("Causing exception for failre on removing instance was", ex);
            }
        });

        try {
            Jenkins.getInstance().removeNode(this);
        } catch (IOException e) {
            listener.error("Failed to remove Docker Node", e);
        }
    }

    public DockerCloud getCloud() {
        if (cloudId == null) return null;
        final Cloud cloud = Jenkins.getInstance().getCloud(cloudId);

        if (cloud == null) {
            throw new RuntimeException("Failed to retrieve Cloud " + cloudId);
        }

        if (cloud.getClass() != DockerCloud.class) {
            throw new RuntimeException(cloudId + " is not DockerCloud");
        }

        return (DockerCloud) cloud;
    }

}
