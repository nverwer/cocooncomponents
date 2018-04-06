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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.HashMap;
import java.util.List;
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
import org.eclipse.jgit.api.DiffCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.EmtpyCommitException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.dircache.DirCache;
import static org.eclipse.jgit.lib.ObjectChecker.tree;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.DepthWalk.RevWalk;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.TrackingRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;

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
    private static final String CLONE_ELEMENT = "clone";
    private static final String STATUS_ELEMENT = "status";
    private static final String ADD_ELEMENT = "add";
//    private static final String IGNORE_ELEMENT = "ignore";
//    private static final String LIST_ELEMENT = "list";
    private static final String FETCH_ELEMENT = "fetch";
    private static final String DIFF_ELEMENT = "diff";
    private static final String COMMIT_ELEMENT = "commit";
    private static final String CHECKOUT_ELEMENT = "checkout";
    private static final String COMMIT_MESSAGE_ELEMENT = "commit-message";
    private static final String PUSH_ELEMENT = "push";
    private static final String PULL_ELEMENT = "pull";
    
    private static final String PULLRESULT_ELEMENT = "pull-result";
    private static final String PUSHRESULT_ELEMENT = "push-result";
    private static final String ADDRESULT_ELEMENT = "add-result";
//    private static final String IGNORERESULT_ELEMENT = "ignore-result";
//    private static final String LISTRESULT_ELEMENT = "list-result";
    private static final String FETCHRESULT_ELEMENT = "fetch-result";
    private static final String DIFFRESULT_ELEMENT = "diff-result";
    private static final String CHECKOUTRESULT_ELEMENT = "checkout-result";
    private static final String STATUSRESULT_ELEMENT = "status-result";
    private static final String INITRESULT_ELEMENT = "init-result";
    private static final String CLONERESULT_ELEMENT = "clone-result";

    private static final String REPOSITORY_ATTR = "repository";
    private static final String URL_ATTR = "url";
    private static final String AUTHORNAME_ATTR = "author-name";
    private static final String AUTHOREMAIL_ATTR = "author-email";
    private static final String PASSWORD_ATTR = "password";
    private static final String ACCOUNT_ATTR = "account";
    private static final String FILE_ATTR = "file";
    private static final String BRANCH_ATTR = "branch";
    private static final String OLDTREE_ATTR = "old-tree";
    private static final String NEWTREE_ATTR = "new-tree";

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
                       
                    sEr(CLONERESULT_ELEMENT, repository);
                    chars("Git repository " + git.getRepository().getDirectory().toString() + " cloned from " + url);
                    eE(CLONERESULT_ELEMENT);

                } catch (Exception ex) {
                    Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
                    throw new SAXException(ex);
                } finally {
                    reset();
                }
            } 
            
            
            else if (name.equals(INIT_ELEMENT)) {
                final String repository = getAttribute(attr, REPOSITORY_ATTR, null);

                if (null == repository) {
                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
                }

                File directory = new File(repository);

                try (Git git = Git.init().setDirectory(directory).call()) {
                    sEr(INITRESULT_ELEMENT, repository);
                    chars("Git repository " + git.getRepository().getDirectory().toString() + " initialised.");
                    eE(INITRESULT_ELEMENT);

                } catch (Exception ex) {
                    Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
                    throw new SAXException(ex);
                } finally {
                    reset();
                }
            } 
            
            
            else if (name.equals(STATUS_ELEMENT)) {
                final String repository = getAttribute(attr, REPOSITORY_ATTR, null);

                if (null == repository) {
                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
                }

                try (Git git = Git.open(new File(repository))) {
                    final DirCache dirCache = git.getRepository().readDirCache();
                    final Status status = git.status().call();
                    sEa(STATUSRESULT_ELEMENT, new HashMap<String , String>() {{
                        put("repository", repository);
                        put("nr-of-files", String.valueOf(dirCache.getEntryCount()));
                    }});
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
                    eE(STATUSRESULT_ELEMENT);
                } catch (Exception ex) {
                    Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
                    throw new SAXException(ex);
                } finally {
                    reset();
                }
            }
            
            
//            
//            else if (name.equals(LIST_ELEMENT)) {
//                final String repository = getAttribute(attr, REPOSITORY_ATTR, null);
//                final String branch = getAttribute(attr, BRANCH_ATTR, "master");
//
//                if (null == repository) {
//                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
//                }
//
//                try (Git git = Git.open(new File(repository))) {
//                    Repository repo = git.getRepository();
//                                      
//                    
//                    
//                    Ref head = repo.exactRef(branch);
//
//        // a RevWalk allows to walk over commits based on some filtering that is defined
//        RevWalk walk = new RevWalk(repo, 100);
//
//        RevCommit commit = walk.parseCommit(head.getObjectId());
//        RevTree tree = commit.getTree();
//                    TreeWalk treeWalk = new TreeWalk(repo);
//treeWalk.addTree(tree);
//treeWalk.setRecursive(false);
//                    sEa(LISTRESULT_ELEMENT, new HashMap<String , String>() {{
//                        put("repository", repository);
//                    }});
//                    
//while (treeWalk.next()) {
//    if (treeWalk.isSubtree()) {
//        sE("dir"); chars(treeWalk.getPathString());eE("dir"); 
//        treeWalk.enterSubtree();
//    } else {
//        
//        sE("file"); chars(treeWalk.getPathString());eE("file"); 
//    }
//}
//                    eE(LISTRESULT_ELEMENT);
//                } catch (Exception ex) {
//                    Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
//                    throw new SAXException(ex);
//                } finally {
//                    reset();
//                }
//            } 
//            
            
            
            else if (name.equals(CHECKOUT_ELEMENT)) {
                final String repository = getAttribute(attr, REPOSITORY_ATTR, null);
                final String branch = getAttribute(attr, BRANCH_ATTR, "master");

                if (null == repository) {
                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
                }

                try (Git git = Git.open(new File(repository))) {
                    final Ref ref = git.checkout().setName(branch).call();
                    sEa(CHECKOUTRESULT_ELEMENT, new HashMap<String , String>() {{
                        put("repository", repository);
                        put("object-id", ref.getObjectId().toString());
                        put("name", ref.getName());
                    }});
                    eE(CHECKOUTRESULT_ELEMENT);
                } catch (Exception ex) {
                    Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
                    throw new SAXException(ex);
                } finally {
                    reset();
                }
            
            } 
            
            
            else if (name.equals(DIFF_ELEMENT)) {
                final String repository = getAttribute(attr, REPOSITORY_ATTR, null);
                final String oldTree = getAttribute(attr, OLDTREE_ATTR, "HEAD^{tree}");
                final String newTree = getAttribute(attr, NEWTREE_ATTR, "FETCH_HEAD^{tree}");

                if (null == repository) {
                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
                }

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
                    sEa(DIFFRESULT_ELEMENT, new HashMap<String , String>() {{
                        put("repository", repository);
                        put("old-tree", oldTree);
                        put("new-tree", newTree);
                    }});
                    for (final DiffEntry diffEntry : diffEntries) {
                        sEa("file", new HashMap<String , String>() {{
                            put("change-type", diffEntry.getChangeType().toString());
                            put("score", String.valueOf(diffEntry.getScore()));
                            put("new-path", diffEntry.getNewPath());
                            put("old-path", diffEntry.getOldPath());
                            put("new-id", diffEntry.getNewId().name());
                            put("old-id", diffEntry.getOldId().name());
                            put("new-mode", diffEntry.getNewMode().toString());
                            put("old-mode", diffEntry.getOldMode().toString());
                        }});
                        
                        ByteArrayOutputStream os = new ByteArrayOutputStream();
                        try (DiffFormatter formatter = new DiffFormatter(os)) {
                            formatter.setRepository(repo);
                            formatter.format(diffEntry);
                        }
                        String aString = new String(os.toByteArray(),"UTF-8");
                        chars(aString);
                        eE("file");
                    }
                    eE(DIFFRESULT_ELEMENT);
                } catch (Exception ex) {
                    Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
                    throw new SAXException(ex);
                } finally {
                    reset();
                }
            
            } 
            
            
            else if (name.equals(FETCH_ELEMENT)) {
                final String repository = getAttribute(attr, REPOSITORY_ATTR, null);
                final String account = getAttribute(attr, ACCOUNT_ATTR, null);
                final String password = getAttribute(attr, PASSWORD_ATTR, null);

                if (null == repository) {
                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
                }    
                
                try (Git git = Git.open(new File(repository))) {
                    final FetchCommand fetchCommand = git.fetch();
                    if (null != account) {
                        fetchCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(account, password));
                    }
                    RefSpec spec = new RefSpec("refs/heads/*:refs/remotes/origin/*");
                    final FetchResult fetchResult = fetchCommand.setRefSpecs(spec).setCheckFetchedObjects(true).call();
                    
                    sEa(FETCHRESULT_ELEMENT, new HashMap<String , String>() {{
                        put("repository", repository);
                        put("remote", fetchCommand.getRemote());
                    }});
                    Collection<TrackingRefUpdate> refUpdates = fetchResult.getTrackingRefUpdates();
                    for (TrackingRefUpdate refUpdate : refUpdates ) {
                        Result result = refUpdate.getResult();
                        chars(result.toString());
                        chars(", ");
                    }
                    eE(FETCHRESULT_ELEMENT);
                } catch (Exception ex) {
                    Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
                    throw new SAXException(ex);
                } finally {
                    reset();
                }
            }

            
            
            else if (name.equals(ADD_ELEMENT)) {
                final String repository = getAttribute(attr, REPOSITORY_ATTR, null);
                String file = getAttribute(attr, FILE_ATTR, ".");

                if (null == repository) {
                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
                }
                
                try (Git git = Git.open(new File(repository))) {

                    DirCache dirCache = git.add().addFilepattern(file).call();
                    sEr(ADDRESULT_ELEMENT, repository);
                    chars("repository now has " + dirCache.getEntryCount() + " entries.");
                    eE(ADDRESULT_ELEMENT);
                    

                } catch (Exception ex) {
                    Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
                    throw new SAXException(ex);
                } finally {
                    reset();
                }
            }
            
            
            else if (name.equals(COMMIT_ELEMENT)) {
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
            } 
            
            
            else if (name.equals(COMMIT_MESSAGE_ELEMENT)) {
                startTextRecording();
            } 
            
            
            else if (name.equals(PUSH_ELEMENT)) {
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
                    sEr(PUSHRESULT_ELEMENT, repository);
                    chars("Git push: " + pushResults.toString());
                    eE(PUSHRESULT_ELEMENT);

                } catch (Exception ex) {
                    Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
                    throw new SAXException(ex);
                } finally {
                    reset();
                }
            } 
            
            
            else if (name.equals(PULL_ELEMENT)) {
                final String repository = getAttribute(attr, REPOSITORY_ATTR, null);
                final String account = getAttribute(attr, ACCOUNT_ATTR, null);
                final String password = getAttribute(attr, PASSWORD_ATTR, null);

                if (null == repository) {
                    throw new SAXException(java.lang.String.format("Missing @%s attribute.", REPOSITORY_ATTR));
                }

                try (Git git = Git.open(new File(repository))) {

                    final PullCommand pullCommand = git.pull();
                    if (null != account) {
                        pullCommand.setCredentialsProvider( new UsernamePasswordCredentialsProvider( account, password ) );
                    }
                    final PullResult pullResult = pullCommand.call();

                    final FetchResult fetchResult = pullResult.getFetchResult();
                    final MergeResult mergeResult = pullResult.getMergeResult();

                    
                    sEa(PULLRESULT_ELEMENT, new HashMap<String , String>() {{
                        put("repository", repository);
                        put("remote", pullCommand.getRemote());
                        put("remote-branch", pullCommand.getRemoteBranchName());
                        put("fetched-from", pullResult.getFetchedFrom());
                        put("fetch-messages", fetchResult.getMessages());
                        put("merge-status", mergeResult.getMergeStatus().toString());
                    }});
                    eE(PULLRESULT_ELEMENT);
                } catch (Exception ex) {
                    Logger.getLogger(GitTransformer.class.getName()).log(Level.SEVERE, null, ex);
                    throw new SAXException(ex);
                } finally {
                    reset();
                }
            }
            
            
            else {
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
                        
                        sE(COMMITRESULT_ELEMENT);
                        chars(revCommit.toString());
                        eE(COMMITRESULT_ELEMENT);
                    } catch(org.eclipse.jgit.api.errors.EmtpyCommitException ex) {
                        sE(COMMITRESULT_ELEMENT);
                        chars("Empty commit");
                        eE(COMMITRESULT_ELEMENT);
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
    private static final String COMMITRESULT_ELEMENT = "commit-result";

    
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
