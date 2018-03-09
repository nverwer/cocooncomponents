/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.cocoon.transformation;

import java.io.IOException;
import java.text.ParseException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.avalon.framework.CascadingException;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.avalon.framework.service.ServiceException;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.xml.AttributesImpl;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.eclipse.jgit.JGit;

/**
 * This transformer can perform actions on a local git repository.
 * These are supported actions: Init, Checkout, Add, Commit, Push and Pull.
 * <p>
 * This transformer triggers for elements in the namespace "http://apache.org/cocoon/git/1.0".
 * The elements cannot be nested.
 * <p>
 * Example XML input:
 * <p>
 * <pre>
 * {@code
 *   <git:push/>
 * }
 * </pre>
 * <pre>
 * {@code
 *   <git:checkout repository="repository id"/>
 *   <git:checkout repository="repository id" file="..."/>
 *   <git:commit repository="repository id"><gti:commit-message>...</gti:commit-message></git:commit>
 *   <git:pull repository="repository id"/>
 *   <git:push repository="repository id"/>
 * }
 * </pre>
 * The @repository attribute specifies the repository that is to be used. Repositories are specified when the
 * transformer is defined in the sitemap.
 *
 *
 *       <map:transformer logger="sitemap.transformer.git" name="git"
 *           pool-grow="2" pool-max="32" pool-min="8"
 *           src="org.apache.cocoon.transformation.GitTransformer">
 *         <repository id="repo-1" path=".../my-first-repository" account="..." password="..." author-name="..." author-email="..." url="https://..."? />
 *         <repository id="repo-2" path=".../my-second-repository" account="..." password="..." author-name="..." author-email="..." url="https://..."? />
 *       </map:transformer>
 *
 *       For a repository element @id, @path, @author-name and @author-email are mandatory,
 *       @account, @password and @url are only used when accessing a remote repository.
 * <p>
 * @author Huib Verweij (hhv@x-scale.nl)
 * </p>
 *
 */
public class GitTransformer extends AbstractSAXTransformer {

    public static final String GIT_NAMESPACE_URI = "http://apache.org/cocoon/git/1.0";
    private static final String GIT_PREFIX = "git";
    private static final String ADD_ELEMENT = "add";
    private static final String COMMIT_ELEMENT = "commit";
    private static final String CHECKOUT_ELEMENT = "checkout";
    private static final String COMMIT_MESSAGE_ELEMENT = "commit-message";
    private static final String PUSH_ELEMENT = "push";
    private static final String PULL_ELEMENT = "pull";
    private static final String REPOSITORY_ELEMENT = "repository";
    private static final String REPOSITORY_ATTR = "repository";
    private static final String ID_ATTR = "id";
    private static final String PATH_ATTR = "path";
    private static final String URL_ATTR = "url";
    private static final String USER-NAME_ATTR = "user-name";
    private static final String USER-EMAIL_ATTR = "user-email";
    private static final String PASSWORD_ATTR = "password";
    private static final String ACCOUNT_ATTR = "account";

    private static final String RESULT_ELEMENT = "result";

    private HashMap<String, Repository> repos;
    private String repo-id;
    private String commit-message;

    public GitTransformer() {
        this.defaultNamespaceURI = GIT_NAMESPACE_URI;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.apache.avalon.framework.configuration.Configurable#configure(org.
     * apache.avalon.framework.configuration.Configuration)
     */
    public void configure(Configuration configuration)
            throws ConfigurationException {
        super.configure(configuration);
        this.repos = getRepositories(configuration.getChildren(REPOSITORY_ELEMENT));
    }

    public void setup(SourceResolver resolver, Map objectModel, String src,
            Parameters params) throws ProcessingException, SAXException, IOException {
        super.setup(resolver, objectModel, src, params);
        reset();
    }

    private String getAttribute(Attributes attr, String name, String defaultValue) {
        return (attr.getIndex(name) >= 0) ? attr.getValue(name) : defaultValue;
    }

    public void startTransformingElement(String uri, String name, String raw, Attributes attr)
            throws ProcessingException, IOException, SAXException {
        if (name.equals(CHECKOUT_ELEMENT)) {
            this.repo-id = attr.getAttribute(REPOSITORY_ATTR, null);
            if (null == this.repo-id) {
                throw new Exception(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
            }
        }
        if (name.equals(COMMIT_ELEMENT)) {
            this.repo-id = attr.getAttribute(REPOSITORY_ATTR, null);
            if (null == this.repo-id) {
                throw new Exception(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
            }
        }
        if (name.equals(COMMIT_MESSAGE_ELEMENT)) {
            this.commit-message = attr.getAttribute(REPOSITORY_ATTR, null);
            startTextRecording();
        }
        else if (name.equals(PUSH_ELEMENT)) {

        }
        else if (name.equals(PULL_ELEMENT)) {

        }
        else super.startTransformingElement(uri, name, raw, attr);
    }

    public void endTransformingElement(String uri, String name, String raw)
            throws ProcessingException, IOException, SAXException {
        if (name.equals(COMMIT_ELEMENT)) {
            try {
                Repository repository = this.repos.get(this.repo-id);
                if (null != repository) {
                    // Create a Git object for the selected repository
                    Git git = Git.open(repository.path);
                    // Add all files
                    AddCommand addCommand = git.add();
                    addCommand.addFilepattern("/").call();
                    // Commit everything
                    PersonIdent personIdent = new PersonIdent(this.author-name, this.author.email);
                    git.commit().setMessage(this.commit-message).setAuthor(personIdent).call();
                }
            } catch (Exception ex) {
                Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                reset();
            }
        }
        else if (name.equals(COMMIT_MESSAGE_ELEMENT)) {
            this.commit-message = endTextRecording();
        }
        else if (name.equals(PUSH_ELEMENT)) {

        }
        else if (name.equals(PULL_ELEMENT)) {

        }
        else super.endTransformingElement(uri, name, raw);
    }


    private void reset() {
        this.repo-id = null;
        this.commit-message = null;
    }



    private void listCronJobs() throws ServiceException, SAXException {

        CocoonQuartzJobScheduler cqjs = (CocoonQuartzJobScheduler) this.manager.
                lookup(CocoonQuartzJobScheduler.ROLE);
        String[] jobs = cqjs.getJobNames();
        xmlConsumer.startElement(QUARTZ_NAMESPACE_URI, JOBS_ELEMENT,
                String.format("%s:%s", QUARTZ_PREFIX, JOBS_ELEMENT),
                EMPTY_ATTRIBUTES);
        for (String job : jobs) {

            if (this.getLogger().isInfoEnabled()) {
                this.getLogger().info("List cronjobs: job = " + job);
            }

            JobSchedulerEntry entry = cqjs.getJobSchedulerEntry(job);

            AttributesImpl attr = new AttributesImpl();
            attr.addAttribute("", NAME_ATTR, NAME_ATTR, "CDATA", job);

            attr.addAttribute("", SCHEDULE_ATTR, SCHEDULE_ATTR, "CDATA", entry.getSchedule());
            attr.addAttribute("", NEXTTIME_ATTR, NEXTTIME_ATTR, "CDATA", entry.getNextTime().toString());
            attr.addAttribute("", ISRUNNING_ATTR, ISRUNNING_ATTR, "CDATA", entry.isRunning() ? "true" : "false");

            xmlConsumer.startElement(QUARTZ_NAMESPACE_URI, JOB_ELEMENT,
                    String.format("%s:%s", QUARTZ_PREFIX, JOB_ELEMENT),
                    attr);
            xmlConsumer.endElement(QUARTZ_NAMESPACE_URI, JOB_ELEMENT,
                    String.format("%s:%s", QUARTZ_PREFIX, JOB_ELEMENT));
        }
        xmlConsumer.endElement(QUARTZ_NAMESPACE_URI, JOBS_ELEMENT,
                String.format("%s:%s", QUARTZ_PREFIX, JOBS_ELEMENT));
    }


    private void result(String result) throws SAXException {
        xmlConsumer.startElement(QUARTZ_NAMESPACE_URI, RESULT_ELEMENT,
                String.format("%s:%s", QUARTZ_PREFIX, RESULT_ELEMENT),
                EMPTY_ATTRIBUTES);
        char[] output = result.toCharArray();
        xmlConsumer.characters(output, 0, output.length);
        xmlConsumer.endElement(QUARTZ_NAMESPACE_URI, RESULT_ELEMENT,
                String.format("%s:%s", QUARTZ_PREFIX, RESULT_ELEMENT));
    }



    /**
     * Classes used for loading the repository info.
     */
    private static class Repository {

        protected String id;
        protected String path;
        protected String url;
        protected String account;
        protected String password;
        protected String author-name;
        protected String author-email;

        public Repository() {
        }

        public isValid() {
            return  null != id &&
                    null != path;
        }
    }

    private HashMap<String, Repository> getRepositories(Configuration[] configurations) {

        HashMap<String, Repository> repos = new HashMap<String, Repository>();

        for (int i = 0; i < array.length; i++)
        {
            Configuration repo_conf = array[i];
            Repository repo = new Repository();
            repo.id = repo_conf.getAttribute(ID_ATTR, null);
            repo.path = repo_conf.getAttribute(PATH_ATTR, null);
            repo.url = repo_conf.getAttribute(URL_ATTR, null);
            repo.username= repo_conf.getAttribute(USERNAME_ATTR, null);
            repo.password= repo_conf.getAttribute(PASSWORD_ATTR, null);

            if (repo.isValid()) {
                repos.put(repo.id, repo);
            }
        }
        return repos;
    }

}
