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

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.xml.AttributesImpl;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.revwalk.DepthWalk.RevWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This transformer can perform actions on a local git repository.
 * It has basic functionality, enough to init or clone a repo, fetch, diff,
 * checkout a branch, add files, ignore files,
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
 *   <git:fetch repository="..."/>
 *   <!-- <git:list repository="..."/> -->
 *   <git:diff repository="..." old-tree="..." new-tree="..."/>
 *   <git:checkout repository="..." branch="..."/>
 *   <git:clone repository="..." account="..." password="..." url="https://..."/>
 *   <git:add repository="..." file="..."/>
 *   <!-- <git:ignore repository="..." file="..."/> -->
 *   <git:commit repository="..." author-name="..." author-email="...">
 *     <git:commit_message>...</git:commit_message>
 *   </git:commit>
 *   <git:pull repository="..." account="..." password="..."/>
 *   <git:push repository="..." account="..." password="..."/>
 *   <git:merge repository="..." from-branch="..." account="..." password="..."/>
 * }
 * </pre>
 * The @repository attribute specifies the path to the local repository that
 * is to be used. 
 * When cloning the @url is mandatory.
 * When adding, @file defaults to "." (= all files).
 * With checkout, @file defaults to "master".
 * When committing @author-name and @author-email are mandatory.
 * @account and @password can be used when authentication is necessary.
 * All output is in elements &lt;git:<action>-result>, e.g. &lt;git:init-result>.
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
    private static final String INITRESULT_ELEMENT = "init-result";
    private static final String CLONE_ELEMENT = "clone";
    private static final String CLONERESULT_ELEMENT = "clone-result";
    private static final String STATUS_ELEMENT = "status";
    private static final String STATUSRESULT_ELEMENT = "status-result";
    private static final String ADD_ELEMENT = "add";
    private static final String ADDRESULT_ELEMENT = "add-result";
    private static final String LIST_ELEMENT = "list";
    private static final String LISTRESULT_ELEMENT = "list-result";
    private static final String FETCH_ELEMENT = "fetch";
    private static final String FETCHRESULT_ELEMENT = "fetch-result";
    private static final String DIFF_ELEMENT = "diff";
    private static final String DIFFRESULT_ELEMENT = "diff-result";
    private static final String COMMIT_ELEMENT = "commit";
    private static final String COMMITRESULT_ELEMENT = "commit-result";
    private static final String CHECKOUT_ELEMENT = "checkout";
    private static final String CHECKOUTRESULT_ELEMENT = "checkout-result";
    private static final String COMMIT_MESSAGE_ELEMENT = "commit-message";
    private static final String PUSH_ELEMENT = "push";
    private static final String PUSHRESULT_ELEMENT = "push-result";
    private static final String PULL_ELEMENT = "pull";
    private static final String PULLRESULT_ELEMENT = "pull-result";
    private static final String MERGE_ELEMENT = "merge";
    private static final String MERGERESULT_ELEMENT = "merge-result";

    private static final String REPOSITORY_ATTR = "repository";
    private static final String URL_ATTR = "url";
    private static final String AUTHORNAME_ATTR = "author-name";
    private static final String AUTHOREMAIL_ATTR = "author-email";
    private static final String PASSWORD_ATTR = "password";
    private static final String ACCOUNT_ATTR = "account";
    private static final String FILE_ATTR = "file";
    private static final String BRANCH_ATTR = "branch";
    private static final String NR_OF_FILES_ATTR = "nr-of-files";
    private static final String FROMBRANCH_ATTR = "from-branch";
    private static final String OLDTREE_ATTR = "old-tree";
    private static final String NEWTREE_ATTR = "new-tree";
    private static final String DIR_ELEMENT = "dir";
    private static final String FILE_ELEMENT = "file";
    private static final String OBJECT_ID_ATTR = "object-id";
    private static final String NAME_ATTR = "name";
    private static final String REMOTE_ATTR = "remote";
    private static final String REMOTE_BRANCH_ATTR = "remote-branch";
    private static final String FETCHED_FROM_ATTR = "fetched-from";
    private static final String FETCH_MESSAGES_ATTR = "fetch-messages";
    private static final String MERGE_STATUS_ATTR = "merge-status";
    private static final String CHANGE_TYPE_ATTR = "change-type";
    private static final String SCORE_ATTR = "score";
    private static final String NEW_PATH_ATTR = "new-path";
    private static final String OLD_PATH_ATTR = "old-path";
    private static final String NEW_ID_ATTR = "new-id";
    private static final String OLD_ID_ATTR = "old-id";
    private static final String NEW_MODE_ATTR = "new-mode";
    private static final String OLD_MODE_ATTR = "old-mode";
    private static final String ADDED_ELEMENT = "added";
    private static final String CHANGED_ELEMENT = "changed";
    private static final String CONFLICTING_ELEMENT = "conflicting";
    private static final String IGNOREDNOTININDEX_ELEMENT = "ignorednotinindex";
    private static final String MISSING_ELEMENT = "missing";
    private static final String MODIFIED_ELEMENT = "modified";
    private static final String REMOVED_ELEMENT = "removed";
    private static final String UNTRACKED_ELEMENT = "untracked";
    private static final String UNTRACKEDFOLDERS_ELEMENT = "untrackedfolders";

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
    @Override
    public void configure(Configuration configuration)
            throws ConfigurationException {
        super.configure(configuration);
    }

    @Override
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
                final String branch = getAttribute(attr, BRANCH_ATTR, MASTER_BRANCH);

                if (null == repository) {
                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
                }
                doClone(repository, account, password, url, branch);
            }
            else if (name.equals(INIT_ELEMENT)) {
                final String repository = getAttribute(attr, REPOSITORY_ATTR, null);

                if (null == repository) {
                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
                }
                doInit(repository);
            }
            else if (name.equals(STATUS_ELEMENT)) {
                final String repository = getAttribute(attr, REPOSITORY_ATTR, null);

                if (null == repository) {
                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
                }
                doStatus(repository);
            }
            else if (name.equals(LIST_ELEMENT)) {

                final String repository = getAttribute(attr, REPOSITORY_ATTR, null);

                if (null == repository) {
                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
                }
                doList(repository);
            }
            else if (name.equals(CHECKOUT_ELEMENT)) {
                final String repository = getAttribute(attr, REPOSITORY_ATTR, null);
                final String branch = getAttribute(attr, BRANCH_ATTR, "master");

                if (null == repository) {
                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
                }
                doCheckout(repository, branch);
            } else if (name.equals(DIFF_ELEMENT)) {
                final String repository = getAttribute(attr, REPOSITORY_ATTR, null);
                final String oldTree = getAttribute(attr, OLDTREE_ATTR, "HEAD^{tree}");
                final String newTree = getAttribute(attr, NEWTREE_ATTR, "FETCH_HEAD^{tree}");

                if (null == repository) {
                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
                }
                doDiff(repository, oldTree, newTree);

            } else if (name.equals(FETCH_ELEMENT)) {
                final String repository = getAttribute(attr, REPOSITORY_ATTR, null);
                final String account = getAttribute(attr, ACCOUNT_ATTR, null);
                final String password = getAttribute(attr, PASSWORD_ATTR, null);

                if (null == repository) {
                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
                }
                doFetch(repository, account, password);
            } else if (name.equals(ADD_ELEMENT)) {
                final String repository = getAttribute(attr, REPOSITORY_ATTR, null);
                String file = getAttribute(attr, FILE_ATTR, ".");

                if (null == repository) {
                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
                }

                doAdd(repository, file);
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
                doPush(repository, account, password);
            } else if (name.equals(PULL_ELEMENT)) {
                final String repository = getAttribute(attr, REPOSITORY_ATTR, null);
                final String account = getAttribute(attr, ACCOUNT_ATTR, null);
                final String password = getAttribute(attr, PASSWORD_ATTR, null);
                final String branch = getAttribute(attr, BRANCH_ATTR, MASTER_BRANCH);

                if (null == repository) {
                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
                }

                doFetch(repository, account, password);
                doPull(repository, account, password, branch);
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
                        if (null == revCommit) {
                            startElement(COMMITRESULT_ELEMENT);
                            chars("revCommit is NULL (commit.message="+this.commit_message+", author_name="+this.author_name+", author_email="+this.author_email+", repository="+this.repository+")");
                            endElement(COMMITRESULT_ELEMENT);
                        }
                        else {
                            startElement(COMMITRESULT_ELEMENT);
                            chars(revCommit.toString());
                            endElement(COMMITRESULT_ELEMENT);
                        }
                    } catch(org.eclipse.jgit.api.errors.EmtpyCommitException ex) {
                        startElement(COMMITRESULT_ELEMENT);
                        chars("Empty commit");
                        endElement(COMMITRESULT_ELEMENT);
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

    private void doClone(String repository, String account, String password, String url, String branch) throws SAXException {

        File directory = new File(repository);

        CloneCommand cloneCommand = Git.cloneRepository();

        if (null != account) {
            cloneCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(account, password));
        }

        try (Git git = cloneCommand.setURI(url).
                setCloneAllBranches(true).
                setBranch(branch).
                setDirectory(directory).
                call()) {
            startElement(CLONERESULT_ELEMENT,
                    new EnhancedAttributesImpl().
                            addAttribute(REPOSITORY_ATTR, repository));
            chars("Git repository " + git.getRepository().getDirectory().toString() + " cloned from " + url);
            endElement(CLONERESULT_ELEMENT);

        } catch (Exception ex) {
            Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
            throw new SAXException(ex);
        } finally {
            reset();
        }
    }
    private void doInit(String repository) throws SAXException {

        File directory = new File(repository);

        try (Git git = Git.init().setDirectory(directory).call()) {
            startElement(INITRESULT_ELEMENT,
                    new EnhancedAttributesImpl().
                            addAttribute(REPOSITORY_ATTR, repository));
            chars("Git repository " + git.getRepository().getDirectory().toString() + " initialised.");
            endElement(INITRESULT_ELEMENT);

        } catch (Exception ex) {
            Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
            throw new SAXException(ex);
        } finally {
            reset();
        }
    }

    private void doStatus(String repository) throws SAXException {

        try (Git git = Git.open(new File(repository))) {
            Repository repo = git.getRepository();
            final DirCache dirCache = repo.readDirCache();
            final Status status = git.status().call();
            startElement(STATUSRESULT_ELEMENT,
                    new EnhancedAttributesImpl().
                            addAttribute(REPOSITORY_ATTR, repository).
                            addAttribute(BRANCH_ATTR, repo.getFullBranch()).
                            addAttribute(NR_OF_FILES_ATTR, String.valueOf(dirCache.getEntryCount())));
            fileList(ADDED_ELEMENT, status.getAdded());
            fileList(CHANGED_ELEMENT, status.getChanged());
            fileList(CONFLICTING_ELEMENT, status.getConflicting());
            // fileList("conflictingstagestate", status.getConflictingStageState());
            fileList(IGNOREDNOTININDEX_ELEMENT, status.getIgnoredNotInIndex());
            fileList(MISSING_ELEMENT, status.getMissing());
            fileList(MODIFIED_ELEMENT, status.getModified());
            fileList(REMOVED_ELEMENT, status.getRemoved());
            fileList(UNTRACKED_ELEMENT, status.getUntracked());
            fileList(UNTRACKEDFOLDERS_ELEMENT, status.getUntrackedFolders());
            endElement(STATUSRESULT_ELEMENT);
        } catch (Exception ex) {
            Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
            throw new SAXException(ex);
        } finally {
            reset();
        }
    }

    private void doList(String repository) throws SAXException {

        RevWalk walk = null;
        try (Git git = Git.open(new File(repository))) {
            Repository repo = git.getRepository();
            Ref head = repo.exactRef("HEAD");
            // a RevWalk allows to walk over commits based on some filtering that is defined
            walk = new RevWalk(repo, 100);

            RevCommit commit = walk.parseCommit(head.getObjectId());
            RevTree tree = commit.getTree();
            TreeWalk treeWalk = new TreeWalk(repo);
            treeWalk.addTree(tree);
            treeWalk.setRecursive(false);
            startElement(LISTRESULT_ELEMENT,
                    new EnhancedAttributesImpl().
                            addAttribute(REPOSITORY_ATTR, repository));

            while (treeWalk.next()) {
                if (treeWalk.isSubtree()) {
                    startElement(DIR_ELEMENT);
                    chars(treeWalk.getPathString());
                    endElement(DIR_ELEMENT);
                    treeWalk.enterSubtree();
                } else {

                    startElement(FILE_ELEMENT);
                    chars(treeWalk.getPathString());
                    xmlConsumer.endElement(GIT_NAMESPACE_URI, FILE_ELEMENT,
                            String.format("%s:%s", GIT_PREFIX, FILE_ELEMENT));
                }
            }
            endElement(LISTRESULT_ELEMENT);
        } catch (Exception ex) {
            Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
            throw new SAXException(ex);
        } finally {
            if (null != walk) {
                walk.dispose();
            }
            reset();
        }
    }


    private void doCheckout(String repository, String branch) throws SAXException {
        try (Git git = Git.open(new File(repository))) {
            final Ref ref = git.checkout().setName(branch).call();
            startElement(CHECKOUTRESULT_ELEMENT,
                    new EnhancedAttributesImpl().
                            addAttribute(REPOSITORY_ATTR, repository).
                            addAttribute(OBJECT_ID_ATTR, ref.getObjectId().toString()).
                            addAttribute(NAME_ATTR, ref.getName()));
            endElement(CHECKOUTRESULT_ELEMENT);
        } catch (Exception ex) {
            Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
            throw new SAXException(ex);
        } finally {
            reset();
        }
    }

    private void doDiff(String repository, String oldTree, String newTree) throws SAXException  {

        try (Git git = Git.open(new File(repository))) {
            DiffCommand diffCommand = git.diff();
            Repository repo = git.getRepository();
            ObjectId head = repo.resolve(newTree);
            ObjectId previousHead = repo.resolve(oldTree);
            // Instanciate a reader to read the data from the Git database
            ObjectReader reader = repo.newObjectReader();
            // Create the tree iterator for each commit
            CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
            oldTreeIter.reset(reader, previousHead);
            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
            newTreeIter.reset(reader, head);

            final List<DiffEntry> diffEntries = diffCommand.setOldTree(oldTreeIter).setNewTree(newTreeIter).call();
            startElement(DIFFRESULT_ELEMENT,
                    new EnhancedAttributesImpl().
                            addAttribute(REPOSITORY_ATTR, repository).
                            addAttribute(OLDTREE_ATTR, oldTree).
                            addAttribute(NEWTREE_ATTR, newTree));
            for (final DiffEntry diffEntry : diffEntries) {
                startElement(FILE_ELEMENT,
                        new EnhancedAttributesImpl().
                                addAttribute(CHANGE_TYPE_ATTR, diffEntry.getChangeType().toString()).
                                addAttribute(SCORE_ATTR, String.valueOf(diffEntry.getScore())).
                                addAttribute(NEW_PATH_ATTR, diffEntry.getNewPath()).
                                addAttribute(OLD_PATH_ATTR, diffEntry.getOldPath()).
                                addAttribute(NEW_ID_ATTR, diffEntry.getNewId().name()).
                                addAttribute(OLD_ID_ATTR, diffEntry.getOldId().name()).
                                addAttribute(NEW_MODE_ATTR, diffEntry.getNewMode().toString()).
                                addAttribute(OLD_MODE_ATTR, diffEntry.getOldMode().toString()));

                ByteArrayOutputStream os = new ByteArrayOutputStream();
                try (DiffFormatter formatter = new DiffFormatter(os)) {
                    formatter.setRepository(repo);
                    formatter.format(diffEntry);
                }
                String aString = new String(os.toByteArray(), "UTF-8");
                chars(aString);
                endElement(FILE_ELEMENT);
            }
            endElement(DIFFRESULT_ELEMENT);
        } catch (Exception ex) {
            Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
            throw new SAXException(ex);
        } finally {
            reset();
        }
    }

    private void doPush(String repository, String account, String password) throws SAXException  {

        try (Git git = Git.open(new File(repository))) {

            PushCommand pushCommand = git.push();
            if (null != account) {
                pushCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(account, password));
            }
            Iterable<PushResult> pushResults = pushCommand.call();
            startElement(PUSHRESULT_ELEMENT,
                    new EnhancedAttributesImpl().
                            addAttribute(REPOSITORY_ATTR, repository));
            for (PushResult pr : pushResults) {
                Collection<RemoteRefUpdate> upds = pr.getRemoteUpdates();
                for (RemoteRefUpdate rro : upds) {
                    String msg = rro.getStatus().toString();
                    if (null != msg) {
                        chars(msg);
                    }
                }
                chars("\n");
            }
            endElement(PUSHRESULT_ELEMENT);

        } catch (Exception ex) {
            Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
            throw new SAXException(ex);
        } finally {
            reset();
        }
    }

    private void doAdd(String repository, String file) throws SAXException {
        try (Git git = Git.open(new File(repository))) {
            DirCache dirCache = git.add().addFilepattern(file).call();
            startElement(ADDRESULT_ELEMENT,
                    new EnhancedAttributesImpl().
                            addAttribute(REPOSITORY_ATTR, repository));
            chars("repository now has " + dirCache.getEntryCount() + " entries.");
            endElement(ADDRESULT_ELEMENT);
        } catch (Exception ex) {
            Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
            throw new SAXException(ex);
        } finally {
            reset();
        }
    }


    private void doFetch(String repository, String account, String password) throws SAXException {
        try (Git git = Git.open(new File(repository))) {
            final FetchCommand fetchCommand = git.fetch();
            if (null != account) {
                fetchCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(account, password));
            }
            RefSpec spec = new RefSpec("refs/heads/*:refs/remotes/origin/*");
            final FetchResult fetchResult = fetchCommand.setRefSpecs(spec).setCheckFetchedObjects(true).call();

            xmlConsumer.startElement(GIT_NAMESPACE_URI,
                    FETCHRESULT_ELEMENT,
                    String.format("%s:%s", GIT_PREFIX, FETCHRESULT_ELEMENT),
                    new EnhancedAttributesImpl().
                            addAttribute(REPOSITORY_ATTR, repository).
                            addAttribute(REMOTE_ATTR, fetchCommand.getRemote()));
            Collection<TrackingRefUpdate> refUpdates = fetchResult.getTrackingRefUpdates();
            for (TrackingRefUpdate refUpdate : refUpdates) {
                Result result = refUpdate.getResult();
                chars(result.toString());
                chars(", ");
            }
            endElement(FETCHRESULT_ELEMENT);
        } catch (Exception ex) {
            Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
            throw new SAXException(ex);
        } finally {
            reset();
        }
    }


    private void doPull(String repository, String account, String password, String branch) throws SAXException {
        try (Git git = Git.open(new File(repository))) {
            // To simplify things and not end up with a Git-mess, do e hard rest to the remote branch first.
            git.reset().setMode(ResetType.HARD).setRef("refs/remotes/origin/" + branch).call();

            final PullCommand pullCommand = git.pull().setStrategy(MergeStrategy.THEIRS);
            if (null != account) {
                pullCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(account, password));
            }
            final PullResult pullResult = pullCommand.call();

            final FetchResult fetchResult = pullResult.getFetchResult();
            final MergeResult mergeResult = pullResult.getMergeResult();


            startElement(PULLRESULT_ELEMENT,
                    new EnhancedAttributesImpl().
                            addAttribute(REPOSITORY_ATTR, repository).
                            addAttribute(REMOTE_ATTR, pullCommand.getRemote()).
                            addAttribute(REMOTE_BRANCH_ATTR, pullCommand.getRemoteBranchName()).
                            addAttribute(FETCHED_FROM_ATTR, pullResult.getFetchedFrom()).
                            addAttribute(FETCH_MESSAGES_ATTR, fetchResult.getMessages()).
                            addAttribute(MERGE_STATUS_ATTR, mergeResult.getMergeStatus().toString()));
            endElement(PULLRESULT_ELEMENT);
        } catch (Exception ex) {
            Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
            throw new SAXException(ex);
        } finally {
            reset();
        }
    }

    private void reset() {
        this.repository = null;
        this.author_name = null;
        this.author_email = null;
        this.commit_message = null;
    }

    /*
     *  Generate a XML opening tag without attributes in the output stream.
     */
    private void startElement(String elementName) throws SAXException {
        startElement(elementName, null);
    }

    /*
     *  Generate a XML opening tag with attributes in the output stream.
     */
    private void startElement(String elementName, AttributesImpl attributes) throws SAXException {
        xmlConsumer.startElement(GIT_NAMESPACE_URI, elementName,
                String.format("%s:%s", GIT_PREFIX, elementName),
                attributes);
    }

    /*
     *  Generate text in the output stream.
     */
    private void chars(String characters) throws SAXException {
        char[] output = characters.toCharArray();
        xmlConsumer.characters(output, 0, output.length);
    }

    /*
     *  Generate a XML closing tag in the output stream.
     */
    private void endElement(String elementName) throws SAXException {
        xmlConsumer.endElement(GIT_NAMESPACE_URI, elementName,
                String.format("%s:%s", GIT_PREFIX, elementName));
    }


    /*
     *  Generate a list of XML FILE_ELEMENTs enclosed by another element in the output stream.
     */
    private void fileList(String element, Set<String> files) throws SAXException {
        startElement(element);
        for (String s : files) {
            startElement(FILE_ELEMENT);
            chars(s);
            endElement(FILE_ELEMENT);
        }
        endElement(element);
    }

    /*
     * Auxiliary class to easily create an AttributesImpl object.
     */
    private class EnhancedAttributesImpl extends AttributesImpl {
        public EnhancedAttributesImpl() {
            //return this;
        }
        public EnhancedAttributesImpl addAttribute(String key, String value) {
            this.addCDATAAttribute(key, value);
            return this;
        }
    }

}
