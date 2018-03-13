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
import java.util.Map.Entry;
import java.util.Set;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.xml.AttributesImpl;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.errors.EmtpyCommitException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

/**
 * This transformer can perform actions on a local git repository.
 * It has basic functionality, enough to init or clone a repo, add files,
 * get the status, commit and push and pull.
 * <p>
 * This transformer triggers for elements in the namespace "http://apache.org/cocoon/git/1.0".
 * The elements cannot be nested.
 * <p>
 * Example XML input:
 * <p>
 * <pre>
 * {@code
 *   <git:push repository="/data/git/repo-1"/>
 * }
 * </pre>
 * <pre>
 * All supported actions:
 * {@code
 *   <git:init repository="..."/>
 *   <git:status repository="..."/>
 *   <git:clone repository="..." account="..." password="..." url="https://..."/>
 *   <git:add repository="..." file="..."/>
 *   <git:commit repository="..." author-name="..." author-email="...">
 *     <git:commit_message>...</git:commit_message>
 *   </git:commit>
 *   <git:pull repository="..." account="..." password="..."/>
 *   <git:push repository="..." account="..." password="..."/>
 * }
 * </pre>
 * The @repository attribute specifies the path to the local repository that
 * is to be used. 
 * When cloning the @url is mandatory.
 * When adding, @file defaults to "." (= all files).
 * When committing @author-name and @author-email are mandatory.
 * @account and @password can be used when authentication is necessary.
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
    private static final String STATUS_ELEMENT = "status";
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
                final String repository = getAttribute(attr, REPOSITORY_ATTR, null);
                final String account = getAttribute(attr, ACCOUNT_ATTR, null);
                final String password = getAttribute(attr, PASSWORD_ATTR, null);
                final String url = getAttribute(attr, URL_ATTR, null);

                if (null == repository) {
                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
                }

                File directory = new File(repository);
                
                CloneCommand cloneCommand = Git.cloneRepository();
                     
                if (null != account) {
                    cloneCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(account, password));
                }
                
                try (Git git = cloneCommand.setURI(url).
                        setCloneAllBranches(true).
                        setBranch(MASTER_BRANCH).
                        setDirectory(directory).
                        call()) {
                       
                    sEr("clone-result", repository);
                    chars("Git repository " + git.getRepository().getDirectory().toString() + " cloned from " + url);
                    eE("clone-result");

                } catch (Exception ex) {
                    Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
                    throw new SAXException(ex);
                } finally {
                    reset();
                }
            } else if (name.equals(INIT_ELEMENT)) {
                final String repository = getAttribute(attr, REPOSITORY_ATTR, null);

                if (null == repository) {
                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
                }

                File directory = new File(repository);

                try (Git git = Git.init().setDirectory(directory).call()) {
                    sEr("init-result", repository);
                    chars("Git repository " + git.getRepository().getDirectory().toString() + " initialised.");
                    eE("init-result");

                } catch (Exception ex) {
                    Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
                    throw new SAXException(ex);
                } finally {
                    reset();
                }
            } else if (name.equals(STATUS_ELEMENT)) {
                final String repository = getAttribute(attr, REPOSITORY_ATTR, null);

                if (null == repository) {
                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
                }

                try (Git git = Git.open(new File(repository))) {
                    Status status = git.status().call();
                    sEr("status-result", repository);
                    fileList("added", status.getAdded());
                    fileList("changed", status.getChanged());
                    fileList("conflicting", status.getConflicting());
                    // fileList("conflictingstagestate", status.getConflictingStageState());
                    fileList("ignorednotinindex", status.getIgnoredNotInIndex());
                    fileList("missing", status.getMissing());
                    fileList("modified", status.getModified());
                    fileList("removed", status.getRemoved());
                    fileList("untracked", status.getUntracked());
                    fileList("untrackedfolders", status.getUntrackedFolders());
                    eE("status-result");
                } catch (Exception ex) {
                    Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
                    throw new SAXException(ex);
                } finally {
                    reset();
                }
            }
            else if (name.equals(ADD_ELEMENT)) {
                final String repository = getAttribute(attr, REPOSITORY_ATTR, null);
                String file = getAttribute(attr, FILE_ATTR, null);

                if (null == repository) {
                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
                }
                
                if (null == file) {
                    file = ".";
                }
                
                try (Git git = Git.open(new File(repository))) {

                    DirCache dirCache = git.add().addFilepattern(file).call();
                    sEr("add-result", repository);
                    chars("repository now has " + dirCache.getEntryCount() + " entries.");
                    eE("add-result");
                    

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
                final String repository = getAttribute(attr, REPOSITORY_ATTR, null);
                final String account = getAttribute(attr, ACCOUNT_ATTR, null);
                final String password = getAttribute(attr, PASSWORD_ATTR, null);

                if (null == repository) {
                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
                }

                try (Git git = Git.open(new File(repository))) {

                    PushCommand pushCommand = git.push();
                    if (null != account) {
                        pushCommand.setCredentialsProvider( new UsernamePasswordCredentialsProvider( account, password ) );
                    }
                    Iterable<PushResult> pushResults = pushCommand.call();
                    sEr("push-result", repository);
                    chars("Git push: " + pushResults.toString());
                    eE("push-result");

                } catch (Exception ex) {
                    Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
                    throw new SAXException(ex);
                } finally {
                    reset();
                }
            } else if (name.equals(PULL_ELEMENT)) {
                final String repository = getAttribute(attr, REPOSITORY_ATTR, null);
                final String account = getAttribute(attr, ACCOUNT_ATTR, null);
                final String password = getAttribute(attr, PASSWORD_ATTR, null);

                if (null == repository) {
                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
                }

                try (Git git = Git.open(new File(repository))) {

                    PullCommand pullCommand = git.pull();
                    if (null != account) {
                        pullCommand.setCredentialsProvider( new UsernamePasswordCredentialsProvider( account, password ) );
                    }
                    PullResult pullResult = pullCommand.call();
                    sEr("pull-result", repository);
                    chars("Git pull: " + (pullResult.isSuccessful() ? "succeeded" : "failed") );
                    eE("pull-result");
                } catch (Exception ex) {
                    Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
                    throw new SAXException(ex);
                } finally {
                    reset();
                }
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
                    try {
                        RevCommit revCommit = git.commit().setAllowEmpty(false).setAll(true).setMessage(this.commit_message).setAuthor(personIdent).setCommitter("GitTransformer", "no-email").call();
                        
                        sE("commit-result");
                        chars(revCommit.toString());
                        eE("commit-result");
                    } catch(org.eclipse.jgit.api.errors.EmtpyCommitException ex) {
                        sE("commit-result");
                        chars("Empty commit");
                        eE("commit-result");
                    }
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

    private void sE(String elementName) throws SAXException {
        sEa(elementName, null);
    }
    private void sEr(String elementName, final String repository) throws SAXException {
        sEa(elementName, new HashMap<String , String>() {{
                        put("repository", repository);
                    }});
    }
    private void sEa(String elementName, Map<String, String> attributes) throws SAXException {
        AttributesImpl attrs = new AttributesImpl();        
        if (null != attributes) {
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                attrs.addCDATAAttribute(key, value);
            }
        }          
        xmlConsumer.startElement(GIT_NAMESPACE_URI, elementName,
                String.format("%s:%s", GIT_PREFIX, elementName),
                attrs);
    }
    private void chars(String characters) throws SAXException {
        char[] output = characters.toCharArray();
        xmlConsumer.characters(output, 0, output.length);
    }
    private void eE(String elementName) throws SAXException {
        xmlConsumer.endElement(GIT_NAMESPACE_URI, elementName,
                String.format("%s:%s", GIT_PREFIX, elementName));
    }
    private void fileList(String element, Set<String> files) throws SAXException {
        sE(element);
        for (String s : files) {
            sE("file");
            chars(s);
            eE("file");
        }
        eE(element);
    }

}
