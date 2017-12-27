package io.jenkins.docker.connector;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;
import io.jenkins.docker.client.DockerAPI;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerComputerJNLPConnector extends DockerComputerConnector {

    private String user;
    private final JNLPLauncher jnlpLauncher;
    private String jenkinsUrl;
    private boolean useStandardJNLPArgs;

    @DataBoundConstructor
    public DockerComputerJNLPConnector(JNLPLauncher jnlpLauncher) {
        this.jnlpLauncher = jnlpLauncher;
    }


    public String getUser() {
        return user;
    }

    @DataBoundSetter
    public void setUser(String user) {
        this.user = user;
    }

    public String getJenkinsUrl(){ return jenkinsUrl; }

    @DataBoundSetter
    public void setJenkinsUrl(String jenkinsUrl){ this.jenkinsUrl = jenkinsUrl; }

    @DataBoundSetter
    public void setUseStandardJNLPArgs(boolean useStandardJNLPArgs){
        this.useStandardJNLPArgs = useStandardJNLPArgs;
    }

    public boolean isUseStandardJNLPArgs() { return useStandardJNLPArgs; }

    public DockerComputerJNLPConnector withUser(String user) {
        this.user = user;
        return this;
    }

    public DockerComputerJNLPConnector withUseStandardJNLPArgs(boolean useStandardJNLPArgs){
        setUseStandardJNLPArgs(useStandardJNLPArgs);
        return this;
    }

    public DockerComputerJNLPConnector withJenkinsUrl(String jenkinsUrl) {
        this.jenkinsUrl = jenkinsUrl;
        return this;
    }

    public JNLPLauncher getJnlpLauncher() {
        return jnlpLauncher;
    }


    @Override
    protected ComputerLauncher createLauncher(final DockerAPI api,
                                              final DockerContainerExecuter containerExecuter,
                                              TaskListener listener,
                                              final String workdir,
                                              final CreateContainerCmd cmd) throws IOException, InterruptedException {
        return new DelegatingComputerLauncher(new JNLPLauncher()) {

            @Override
            public boolean isLaunchSupported() {
                return true;
            }

            @Override
            public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
                final DockerContainerLifecycleHandler handler = new DockerContainerLifecycleHandler(){
                    @Override
                    public void beforeContainerCreated(DockerAPI api, String workdir, CreateContainerCmd cmd) throws IOException, InterruptedException {
                        if(!useStandardJNLPArgs) {
                            ensureWaiting(cmd);
                        }
                    }

                    @Override
                    public void afterContainerStarted(DockerAPI api, String workdir, String containerId) throws IOException, InterruptedException {
                        if(!useStandardJNLPArgs) {
                            final DockerClient client = api.getClient();
                            injectRemotingJar(containerId, workdir, client);
                        }
                    }
                };
                if(!useStandardJNLPArgs){
                    launchAndInjectJar(computer, listener, handler, api, containerExecuter, workdir, cmd);
                }else{
                    launchStandardJNLPImage(computer, listener, handler, api, containerExecuter, workdir, cmd);
                }
            }
        };
    }

    private List<String> buildJNLPArgs(SlaveComputer computer){
        List<String> args = new ArrayList<>();
        if (StringUtils.isNotBlank(jnlpLauncher.tunnel)) {
            args.addAll(Arrays.asList("-tunnel", jnlpLauncher.tunnel));
        }

        args.addAll(Arrays.asList(
                "-url", jenkinsUrl == null ? Jenkins.getInstance().getRootUrl() : jenkinsUrl,
                computer.getJnlpMac(),
                computer.getName()));
        return args;

    }

    private void launchStandardJNLPImage(SlaveComputer computer,
                                         TaskListener listener,
                                         DockerContainerLifecycleHandler handler,
                                         DockerAPI api,
                                         DockerContainerExecuter containerExecuter,
                                         String workdir,
                                         CreateContainerCmd cmd)  throws IOException, InterruptedException {
        String vmargs = jnlpLauncher.vmargs;
        if (StringUtils.isNotBlank(vmargs)) {
            cmd.withEnv("JAVA_OPT=" + vmargs.trim());
        }
        List<String> jnlpArgs = buildJNLPArgs(computer);
        cmd.withCmd(jnlpArgs);
        containerExecuter.executeContainer(api, listener, cmd, workdir, handler);
    }

    private void launchAndInjectJar(SlaveComputer computer,
                                    TaskListener listener,
                                    DockerContainerLifecycleHandler handler,
                                    DockerAPI api,
                                    DockerContainerExecuter containerExecuter,
                                    String workdir,
                                    CreateContainerCmd cmd)  throws IOException, InterruptedException{
        final DockerClient client = api.getClient();
        final InspectContainerResponse inspect = containerExecuter.executeContainer(api, listener, cmd, workdir, handler);
        List<String> args = new ArrayList<>();
        args.add("java");

        String vmargs = jnlpLauncher.vmargs;
        if (StringUtils.isNotBlank(vmargs)) {
            args.addAll(Arrays.asList(vmargs.split(" ")));
        }

        args.addAll(Arrays.asList(
                "-cp", workdir + "/" + remoting.getName(),
                "hudson.remoting.jnlp.Main", "-headless"));
        args.addAll(buildJNLPArgs(computer));

        final String containerId = inspect.getId();
        final ExecCreateCmd cmdAfterInject = client.execCreateCmd(containerId)
                .withAttachStdout(true)
                .withTty(true)
                .withCmd(args.toArray(new String[args.size()]));

        if (StringUtils.isNotBlank(user)) {
            cmdAfterInject.withUser(user);
        }

        final ExecCreateCmdResponse exec = cmdAfterInject.exec();

        final PrintStream logger = computer.getListener().getLogger();
        final ExecStartResultCallback start = client.execStartCmd(exec.getId())
                .withDetach(true)
                .withTty(true)
                .exec(new ExecStartResultCallback(logger, logger));

        start.awaitCompletion();
    }

    @Extension @Symbol("jnlp")
    public static final class DescriptorImpl extends Descriptor<DockerComputerConnector> {

        @Override
        public String getDisplayName() {
            return "Connect with JNLP";
        }

    }
}
