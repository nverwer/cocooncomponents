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
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

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
 *   <git:init repository="path to local repository"/>
 *   <git:clone repository="path to local repository" account="..." password="..." url="https://..." />
 *   // <git:checkout repository="path to local repository" branch=".."/>
 *   <git:add repository="path to local repository" file="..."/>
 *   <git:commit repository="path to local repository" author_name="..." author_email="..."><git:commit_message>...</git:commit_message></git:commit>
 *   <git:pull repository="path to local repository"/>
 *   <git:push repository="path to local repository"/>
 * }
 * </pre>
 * The @repository attribute specifies the path to the local repository that
 * is to be used. 
 *
 *       <map:transformer logger="sitemap.transformer.git" name="git"
 *           pool-grow="2" pool-max="32" pool-min="8"
 *           src="org.apache.cocoon.transformation.GitTransformer">
 *       </map:transformer>
 *
 * <p>
 * @author Huib Verweij (hhv@x-scale.nl)
 * </p>
 *
 */
public class GitTransformer extends AbstractSAXPipelineTransformer {

    public static final String GIT_NAMESPACE_URI = "http://apache.org/cocoon/git/1.0";
    private static final String MASTER_BRANCH = "master";
    private static final String GIT_PREFIX = "git";

    private static final String INIT_ELEMENT = "init";
    private static final String CLONE_ELEMENT = "clone";
    private static final String ADD_ELEMENT = "add";
    private static final String COMMIT_ELEMENT = "commit";
    private static final String CHECKOUT_ELEMENT = "checkout";
    private static final String COMMIT_MESSAGE_ELEMENT = "commit-message";
    private static final String PUSH_ELEMENT = "push";
    private static final String PULL_ELEMENT = "pull";

    private static final String REPOSITORY_ATTR = "repository";
    private static final String URL_ATTR = "url";
    private static final String AUTHORNAME_ATTR = "author-name";
    private static final String AUTHOREMAIL_ATTR = "author-email";
    private static final String PASSWORD_ATTR = "password";
    private static final String ACCOUNT_ATTR = "account";
    private static final String FILE_ATTR = "file";

    private static final String RESULT_ELEMENT = "result";

    private String commit_message;
    private String repository;
    private String author_name;
    private String author_email;

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
        if (uri.equals(GIT_NAMESPACE_URI)) {
            if (name.equals(CLONE_ELEMENT)) {
                String repository = getAttribute(attr, REPOSITORY_ATTR, null);
                String account = getAttribute(attr, ACCOUNT_ATTR, null);
                String password = getAttribute(attr, PASSWORD_ATTR, null);
                String url = getAttribute(attr, URL_ATTR, null);

                if (null == repository) {
                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
                }

                File directory = new File(repository);

                try (Git git = Git.cloneRepository().setURI(url).
                        setCloneAllBranches(true).
                        setBranch(MASTER_BRANCH).
                        setDirectory(directory).
                        call()) {

                    result("Git repository " + git.getRepository().getDirectory().toString() + " cloned from " + url);

                } catch (Exception ex) {
                    Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
                    throw new SAXException(ex);
                } finally {
                    reset();
                }
            } else if (name.equals(INIT_ELEMENT)) {
                String repository = getAttribute(attr, REPOSITORY_ATTR, null);

                if (null == repository) {
                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
                }

                File directory = new File(repository);

                try (Git git = Git.init().setDirectory(directory).call()) {

                    result("Git repository " + git.getRepository().getDirectory().toString() + " initialised.");

                } catch (Exception ex) {
                    Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
                    throw new SAXException(ex);
                } finally {
                    reset();
                }
            } //        else if (name.equals(CHECKOUT_ELEMENT)) {
            //            this.repository = getAttribute(attr, REPOSITORY_ATTR, null);
            //            if (null == this.repository) {
            //                throw new Exception(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
            //            }
            //        }
            else if (name.equals(ADD_ELEMENT)) {
                String repository = getAttribute(attr, REPOSITORY_ATTR, null);
                String file = getAttribute(attr, FILE_ATTR, null);

                if (null == repository) {
                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
                }
                
                if (null == file) {
                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", FILE_ATTR));
                }
                
                try (Git git = Git.open(new File(repository))) {

                    DirCache dirCache = git.add().addFilepattern(file).call();
                    result("Git: repository now has " + dirCache.getEntryCount() + " entries.");

                } catch (Exception ex) {
                    Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
                    throw new SAXException(ex);
                } finally {
                    reset();
                }
            } else if (name.equals(COMMIT_ELEMENT)) {
                this.repository = getAttribute(attr, REPOSITORY_ATTR, null);
                this.author_name = getAttribute(attr, AUTHORNAME_ATTR, null);
                this.author_email = getAttribute(attr, AUTHOREMAIL_ATTR, null);
                if (null == this.repository) {
                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
                }
                if (null == this.author_name) {
                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", AUTHORNAME_ATTR));
                }
                if (null == this.author_email) {
                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", AUTHOREMAIL_ATTR));
                }
            } else if (name.equals(COMMIT_MESSAGE_ELEMENT)) {
                startTextRecording();
            } else if (name.equals(PUSH_ELEMENT)) {
                String repository = getAttribute(attr, REPOSITORY_ATTR, null);
                String account = getAttribute(attr, ACCOUNT_ATTR, null);
                String password = getAttribute(attr, PASSWORD_ATTR, null);

                if (null == repository) {
                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
                }
                
                if (null == account) {
                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", ACCOUNT_ATTR));
                }
                
                if (null == password) {
                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", PASSWORD_ATTR));
                }

                try (Git git = Git.open(new File(repository))) {

                    PushCommand pushCommand = git.push();
                    pushCommand.setCredentialsProvider( new UsernamePasswordCredentialsProvider( account, password ) );
                    Iterable<PushResult> pushResults = pushCommand.call();
                    result("Git push: " + pushResults.toString());

                } catch (Exception ex) {
                    Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
                    throw new SAXException(ex);
                } finally {
                    reset();
                }
            } else if (name.equals(PULL_ELEMENT)) {

            } else {
                super.startTransformingElement(uri, name, raw, attr);
            }
        }
    }

    public void endTransformingElement(String uri, String name, String raw)
            throws ProcessingException, IOException, SAXException {
        if (uri.equals(GIT_NAMESPACE_URI)) {

            if (name.equals(COMMIT_ELEMENT)) {
                if (null == this.commit_message) {
                    throw new SAXException("Missing <git:commit-message/>.");
                }
                try (Git git = Git.open(new File(this.repository))) {
                    // Commit everything
                    PersonIdent personIdent = new PersonIdent(this.author_name, this.author_email);
                    git.commit().setMessage(this.commit_message).setAuthor(personIdent).setCommitter("GitTransformer", "no-email").call();
                } catch (Exception ex) {
                    Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
                    throw new SAXException(ex);
                } finally {
                    reset();
                }
            } else if (name.equals(COMMIT_MESSAGE_ELEMENT)) {
                this.commit_message = endTextRecording();
            }
        } else {
            super.endTransformingElement(uri, name, raw);
        }
    }

    private void reset() {
        this.repository = null;
        this.author_name = null;
        this.author_email = null;
        this.commit_message = null;
    }

//
//    private void listCronJobs() throws ServiceException, SAXException {
//
//        CocoonQuartzJobScheduler cqjs = (CocoonQuartzJobScheduler) this.manager.
//                lookup(CocoonQuartzJobScheduler.ROLE);
//        String[] jobs = cqjs.getJobNames();
//        xmlConsumer.startElement(GIT_NAMESPACE_URI, JOBS_ELEMENT,
//                String.format("%s:%s", GIT_PREFIX, JOBS_ELEMENT),
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
//            xmlConsumer.startElement(GIT_NAMESPACE_URI, JOB_ELEMENT,
//                    String.format("%s:%s", GIT_PREFIX, JOB_ELEMENT),
//                    attr);
//            xmlConsumer.endElement(GIT_NAMESPACE_URI, JOB_ELEMENT,
//                    String.format("%s:%s", GIT_PREFIX, JOB_ELEMENT));
//        }
//        xmlConsumer.endElement(GIT_NAMESPACE_URI, JOBS_ELEMENT,
//                String.format("%s:%s", GIT_PREFIX, JOBS_ELEMENT));
//    }
//
//
    private void result(String result) throws SAXException {
        xmlConsumer.startElement(GIT_NAMESPACE_URI, RESULT_ELEMENT,
                String.format("%s:%s", GIT_PREFIX, RESULT_ELEMENT),
                EMPTY_ATTRIBUTES);
        char[] output = result.toCharArray();
        xmlConsumer.characters(output, 0, output.length);
        xmlConsumer.endElement(GIT_NAMESPACE_URI, RESULT_ELEMENT,
                String.format("%s:%s", GIT_PREFIX, RESULT_ELEMENT));
    }

}
