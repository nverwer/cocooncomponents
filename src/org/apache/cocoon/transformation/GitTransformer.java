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

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;

/**
 * This transformer can perform actions on a local git repository.
 * These are supported actions: Init, Clone, Checkout, Add, Commit, Push and Pull.
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
 *   <git:init repository="repository id"/>
 *   <git:clone repository="repository id" account="..." password="..." url="https://..." />
 *   // <git:checkout repository="repository id" branch=".."/>
 *   <git:add repository="repository id" file="..."/>
 *   <git:commit repository="repository id" author_name="..." author_email="..."><gti:commit_message>...</gti:commit_message></git:commit>
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
 *         <repository id="repo-1" path=".../my-first-repository" />
 *         <repository id="repo-2" path=".../my-second-repository" />
 *       </map:transformer>
 *
 * <p>
 * @author Huib Verweij (hhv@x-scale.nl)
 * </p>
 *
 */
public class GitTransformer extends AbstractSAXTransformer {

    public static final String GIT_NAMESPACE_URI = "http://apache.org/cocoon/git/1.0";
    private static final String MASTER_BRANCH = "master";
    private static final String GIT_PREFIX = "git";

    private static final String INIT_ELEMENT = "init";
    private static final String CLONE_ELEMENT = "clone";
    private static final String ADD_ELEMENT = "add";
    private static final String COMMIT_ELEMENT = "commit";
    private static final String CHECKOUT_ELEMENT = "checkout";
    private static final String COMMIT_MESSAGE_ELEMENT = "commit_message";
    private static final String PUSH_ELEMENT = "push";
    private static final String PULL_ELEMENT = "pull";
    private static final String REPOSITORY_ELEMENT = "repository";

    private static final String REPOSITORY_ATTR = "repository";
    private static final String ID_ATTR = "id";
    private static final String PATH_ATTR = "path";
    private static final String URL_ATTR = "url";
    private static final String AUTHORNAME_ATTR = "author_name";
    private static final String AUTHOREMAIL_ATTR = "author_email";
    private static final String PASSWORD_ATTR = "password";
    private static final String ACCOUNT_ATTR = "account";
    private static final String FILE_ATTR = "file";

    private static final String RESULT_ELEMENT = "result";

    private HashMap<String, Repository> repos;
    private String repo_id;
    private String commit_message;
    private String author_name;
    private String author_email;
    private String account;
    private String password;
    private String url;
    private String file;


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
        Git git = null;

        if (name.equals(CLONE_ELEMENT)) {
            this.repo_id = getAttribute(attr, REPOSITORY_ATTR, null);
            this.account = getAttribute(attr, ACCOUNT_ATTR, null);
            this.password = getAttribute(attr, PASSWORD_ATTR, null);
            this.url = getAttribute(attr, URL_ATTR, null);
            if (null == this.repo_id) {
                throw new SAXException(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
            }

            try {
                if (null != (git = getGit(this.repo_id))) git.cloneRepository().setURI(this.url).setRemote(this.repo_id).setCloneAllBranches(true).setBranch(MASTER_BRANCH).call();
            } catch (Exception ex) {
                Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
                throw new SAXException(ex);
            } finally {
                reset();
            }
        }
        else if (name.equals(INIT_ELEMENT)) {
            this.repo_id = getAttribute(attr, REPOSITORY_ATTR, null);
            if (null == this.repo_id) {
                throw new SAXException(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
            }

            try {
                if (null != (git = getGit(this.repo_id))) git.init().call();
            } catch (Exception ex) {
                Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
                throw new SAXException(ex);
            } finally {
                reset();
            }
        }
//        else if (name.equals(CHECKOUT_ELEMENT)) {
//            this.repo_id = getAttribute(attr, REPOSITORY_ATTR, null);
//            if (null == this.repo_id) {
//                throw new Exception(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
//            }
//        }
        else if (name.equals(ADD_ELEMENT)) {
            this.repo_id = getAttribute(attr, REPOSITORY_ATTR, null);
            this.file = getAttribute(attr, FILE_ATTR, null);
            if (null == this.repo_id) {
                throw new SAXException(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
            }
            try {
                if (null != (git = getGit(this.repo_id))) {
                    git.add().addFilepattern(this.file).call();
                }
            } catch (Exception ex) {
                Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
                throw new SAXException(ex);
            } finally {
                reset();
            }
        }
        else if (name.equals(COMMIT_ELEMENT)) {
            this.repo_id = getAttribute(attr, REPOSITORY_ATTR, null);
            this.author_name = getAttribute(attr, AUTHORNAME_ATTR, null);
            this.author_email = getAttribute(attr, AUTHORNAME_ATTR, null);
            if (null == this.repo_id) {
                throw new SAXException(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
            }
        }
        else if (name.equals(COMMIT_MESSAGE_ELEMENT)) {
            this.commit_message = getAttribute(attr, REPOSITORY_ATTR, null);
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
        Git git = null;

        if (name.equals(COMMIT_ELEMENT)) {
            try {
                if (null != (git = getGit(this.repo_id))) {
                    // Commit everything
                    PersonIdent personIdent = new PersonIdent(this.author_name, this.author_email);
                    git.commit().setMessage(this.commit_message).setAuthor(personIdent).setCommitter("GitTransformer", "no-email").call();
                }
            } catch (Exception ex) {
                Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
                throw new SAXException(ex);
            } finally {
                reset();
            }
        }
        else if (name.equals(COMMIT_MESSAGE_ELEMENT)) {
            this.commit_message = endTextRecording();
        }
        else super.endTransformingElement(uri, name, raw);
    }


    private void reset() {
        this.repo_id = null;
        this.commit_message = null;
        this.author_name = null;
        this.author_email = null;
        this.account = null;
        this.password = null;
        this.url = null;
        this.file = null;
    }

    private Git getGit(String repo_id) throws IOException {

        Repository repository = this.repos.get(repo_id);
        if (null != repository) {
            // Create a Git object for the selected repository
            return Git.open(new File(repository.path));
        }
        else
            return null;
    }

//
//    private void listCronJobs() throws ServiceException, SAXException {
//
//        CocoonQuartzJobScheduler cqjs = (CocoonQuartzJobScheduler) this.manager.
//                lookup(CocoonQuartzJobScheduler.ROLE);
//        String[] jobs = cqjs.getJobNames();
//        xmlConsumer.startElement(QUARTZ_NAMESPACE_URI, JOBS_ELEMENT,
//                String.format("%s:%s", QUARTZ_PREFIX, JOBS_ELEMENT),
//                EMPTY_ATTRIBUTES);
//        for (String job : jobs) {
//
//            if (this.getLogger().isInfoEnabled()) {
//                this.getLogger().info("List cronjobs: job = " + job);
//            }
//
//            JobSchedulerEntry entry = cqjs.getJobSchedulerEntry(job);
//
//            AttributesImpl attr = new AttributesImpl();
//            attr.addAttribute("", NAME_ATTR, NAME_ATTR, "CDATA", job);
//
//            attr.addAttribute("", SCHEDULE_ATTR, SCHEDULE_ATTR, "CDATA", entry.getSchedule());
//            attr.addAttribute("", NEXTTIME_ATTR, NEXTTIME_ATTR, "CDATA", entry.getNextTime().toString());
//            attr.addAttribute("", ISRUNNING_ATTR, ISRUNNING_ATTR, "CDATA", entry.isRunning() ? "true" : "false");
//
//            xmlConsumer.startElement(QUARTZ_NAMESPACE_URI, JOB_ELEMENT,
//                    String.format("%s:%s", QUARTZ_PREFIX, JOB_ELEMENT),
//                    attr);
//            xmlConsumer.endElement(QUARTZ_NAMESPACE_URI, JOB_ELEMENT,
//                    String.format("%s:%s", QUARTZ_PREFIX, JOB_ELEMENT));
//        }
//        xmlConsumer.endElement(QUARTZ_NAMESPACE_URI, JOBS_ELEMENT,
//                String.format("%s:%s", QUARTZ_PREFIX, JOBS_ELEMENT));
//    }
//
//
//    private void result(String result) throws SAXException {
//        xmlConsumer.startElement(QUARTZ_NAMESPACE_URI, RESULT_ELEMENT,
//                String.format("%s:%s", QUARTZ_PREFIX, RESULT_ELEMENT),
//                EMPTY_ATTRIBUTES);
//        char[] output = result.toCharArray();
//        xmlConsumer.characters(output, 0, output.length);
//        xmlConsumer.endElement(QUARTZ_NAMESPACE_URI, RESULT_ELEMENT,
//                String.format("%s:%s", QUARTZ_PREFIX, RESULT_ELEMENT));
//    }



    /**
     * Classes used for loading the repository info.
     */
    private static class Repository {

        protected String id;
        protected String path;
//        protected String url;
//        protected String account;
//        protected String password;
//        protected String author_name;
//        protected String author_email;

        public Repository() {
        }

        public boolean isValid() {
            return  null != id &&
                    null != path;
        }
    }

    private HashMap<String, Repository> getRepositories(Configuration[] configurations) {

        HashMap<String, Repository> repos = new HashMap<String, Repository>();

        for (int i = 0; i < configurations.length; i++)
        {
            Configuration repo_conf = configurations[i];
            Repository repo = new Repository();
            repo.id = repo_conf.getAttribute(ID_ATTR, null);
            repo.path = repo_conf.getAttribute(PATH_ATTR, null);
            if (repo.isValid()) {
                repos.put(repo.id, repo);
            }
        }
        return repos;
    }

}
