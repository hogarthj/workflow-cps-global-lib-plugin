/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.libs;

import hudson.FilePath;
import hudson.slaves.WorkspaceList;
import java.util.Collections;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class SCMSourceRetrieverTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Issue("JENKINS-40408")
    @Test public void lease() throws Exception {
        sampleRepo.init();
        sampleRepo.write("vars/myecho.groovy", "def call() {echo 'something special'}");
        sampleRepo.git("add", "vars");
        sampleRepo.git("commit", "--message=init");
        GlobalLibraries.get().setLibraries(Collections.singletonList(
            new LibraryConfiguration("echoing",
                new SCMSourceRetriever(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true)))));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('echoing@master') import myecho; myecho()", true));
        FilePath base = r.jenkins.getWorkspaceFor(p).withSuffix("@libs").child("echoing");
        try (WorkspaceList.Lease lease = r.jenkins.toComputer().getWorkspaceList().acquire(base)) {
            WorkflowRun b = r.buildAndAssertSuccess(p);
            r.assertLogContains("something special", b);
            assertFalse(base.child("vars").exists());
            assertTrue(base.withSuffix("@2").child("vars").exists());
        }
    }

}
