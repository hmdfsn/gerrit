// Copyright (C) 2012 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.git.strategy;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.IntegrationException;
import com.google.gerrit.server.git.MergeSorter;
import com.google.gerrit.server.git.MergeTip;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.project.ChangeControl;

import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Base class that submit strategies must extend.
 * <p>
 * A submit strategy for a certain {@link SubmitType} defines how the submitted
 * commits should be merged.
 */
public abstract class SubmitStrategy {
  static class Arguments {
    protected final PersonIdent serverIdent;
    protected final ReviewDb db;
    protected final BatchUpdate.Factory batchUpdateFactory;
    protected final ChangeControl.GenericFactory changeControlFactory;

    protected final Repository repo;
    protected final CodeReviewRevWalk rw;
    protected final ObjectInserter inserter;
    protected final RevFlag canMergeFlag;
    protected final Set<RevCommit> alreadyAccepted;
    protected final Branch.NameKey destBranch;
    protected final ApprovalsUtil approvalsUtil;
    protected final MergeUtil mergeUtil;
    protected final MergeSorter mergeSorter;
    protected final IdentifiedUser caller;

    Arguments(PersonIdent serverIdent, ReviewDb db,
        BatchUpdate.Factory batchUpdateFactory,
        ChangeControl.GenericFactory changeControlFactory, Repository repo,
        CodeReviewRevWalk rw, ObjectInserter inserter, RevFlag canMergeFlag,
        Set<RevCommit> alreadyAccepted, Branch.NameKey destBranch,
        ApprovalsUtil approvalsUtil, MergeUtil mergeUtil,
        IdentifiedUser caller) {
      this.serverIdent = checkNotNull(serverIdent);
      this.db = checkNotNull(db);
      this.batchUpdateFactory = checkNotNull(batchUpdateFactory);
      this.changeControlFactory = checkNotNull(changeControlFactory);

      this.repo = checkNotNull(repo);
      this.rw = checkNotNull(rw);
      this.inserter = checkNotNull(inserter);
      this.canMergeFlag = checkNotNull(canMergeFlag);
      this.alreadyAccepted = checkNotNull(alreadyAccepted);
      this.destBranch = checkNotNull(destBranch);
      this.approvalsUtil = checkNotNull(approvalsUtil);
      this.mergeUtil = checkNotNull(mergeUtil);
      this.caller = checkNotNull(caller);

      this.mergeSorter = new MergeSorter(rw, alreadyAccepted, canMergeFlag);
    }

    BatchUpdate newBatchUpdate(Timestamp when) {
      return batchUpdateFactory
          .create(db, destBranch.getParentKey(), caller, when)
          .setRepository(repo, rw, inserter);
    }
  }

  protected final Arguments args;

  SubmitStrategy(Arguments args) {
    this.args = checkNotNull(args);
  }

  /**
   * Runs this submit strategy.
   * <p>
   * If possible, the provided commits will be merged with this submit strategy.
   *
   * @param currentTip the mergeTip
   * @param toMerge the list of submitted commits that should be merged using
   *        this submit strategy. Implementations are responsible for ordering
   *        of commits, and should not modify the input in place.
   * @return the new merge tip.
   * @throws IntegrationException
   */
  public abstract MergeTip run(CodeReviewCommit currentTip,
      Collection<CodeReviewCommit> toMerge) throws IntegrationException;

  /**
   * Returns all commits that have been newly created for the changes that are
   * getting merged.
   * <p>
   * By default this method returns an empty map, but subclasses may override
   * this method to provide any newly created commits.
   * <p>
   * This method may only be called after {@link #run(CodeReviewCommit,
   * Collection)}.
   *
   * @return new commits created for changes that were merged.
   */
  public Map<Change.Id, CodeReviewCommit> getNewCommits() {
    return Collections.emptyMap();
  }
}
