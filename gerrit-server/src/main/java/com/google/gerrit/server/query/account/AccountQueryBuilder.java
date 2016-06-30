// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.query.account;

import com.google.common.primitives.Ints;
import com.google.gerrit.common.errors.NotSignedInException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.query.LimitPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryBuilder;
import com.google.gerrit.server.query.QueryParseException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

/**
 * Parses a query string meant to be applied to account objects.
 */
public class AccountQueryBuilder extends QueryBuilder<AccountState> {
  public interface ChangeOperatorFactory
      extends OperatorFactory<AccountState, AccountQueryBuilder> {
  }

  public static final String FIELD_ACCOUNT = "account";
  public static final String FIELD_LIMIT = "limit";
  public static final String FIELD_VISIBLETO = "visibleto";

  private static final QueryBuilder.Definition<AccountState, AccountQueryBuilder> mydef =
      new QueryBuilder.Definition<>(AccountQueryBuilder.class);

  public static class Arguments {
    private final Provider<CurrentUser> self;

    @Inject
    public Arguments(Provider<CurrentUser> self) {
      this.self = self;
    }

    IdentifiedUser getIdentifiedUser() throws QueryParseException {
      try {
        CurrentUser u = getUser();
        if (u.isIdentifiedUser()) {
          return u.asIdentifiedUser();
        }
        throw new QueryParseException(NotSignedInException.MESSAGE);
      } catch (ProvisionException e) {
        throw new QueryParseException(NotSignedInException.MESSAGE, e);
      }
    }

    CurrentUser getUser() throws QueryParseException {
      try {
        return self.get();
      } catch (ProvisionException e) {
        throw new QueryParseException(NotSignedInException.MESSAGE, e);
      }
    }
  }

  private final Arguments args;

  @Inject
  AccountQueryBuilder(Arguments args) {
    super(mydef);
    this.args = args;
  }

  @Operator
  public Predicate<AccountState> account(String query)
      throws QueryParseException {
    if ("self".equalsIgnoreCase(query)) {
      return new AccountIdPredicate(self());
    }
    Integer id = Ints.tryParse(query);
    if (id != null) {
      return new AccountIdPredicate(new Account.Id(id));
    }
    throw error("User " + query + " not found");
  }

  @Operator
  public Predicate<AccountState> limit(String query)
      throws QueryParseException {
    Integer limit = Ints.tryParse(query);
    if (limit == null) {
      throw error("Invalid limit: " + query);
    }
    return new LimitPredicate<>(FIELD_LIMIT, limit);
  }

  @Override
  protected Predicate<AccountState> defaultField(String query)
      throws QueryParseException {
    return account(query);
  }

  private Account.Id self() throws QueryParseException {
    return args.getIdentifiedUser().getAccountId();
  }
}
