/*
 * Copyright Red Hat, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Red Hat trademarks are not licensed under GPLv3. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.subscriptions.metering.service.prometheus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.candlepin.subscriptions.files.TagMetric;
import org.candlepin.subscriptions.files.TagProfile;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.candlepin.subscriptions.metering.service.prometheus.promql.QueryBuilder;
import org.candlepin.subscriptions.metering.service.prometheus.promql.QueryDescriptor;
import org.candlepin.subscriptions.prometheus.model.QueryResult;
import org.candlepin.subscriptions.prometheus.model.QueryResultData;
import org.candlepin.subscriptions.prometheus.model.QueryResultDataResult;
import org.candlepin.subscriptions.prometheus.model.ResultType;
import org.candlepin.subscriptions.prometheus.model.StatusType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrometheusAccountSourceTest {

  final String TEST_ACCT_QUERY_KEY = "TEST_QUERY";
  final String TEST_ACCOUNT_QUERY = "ACCOUNT QUERY";
  final String TEST_PROD_TAG = "OpenShift-metrics";

  @Mock PrometheusService service;
  @Mock TagProfile tagProfile;

  PrometheusAccountSource accountSource;
  PrometheusMetricsProperties promProps;
  QueryBuilder queryBuilder;
  TagMetric tag1;

  @BeforeEach
  void setupTest() {
    MetricProperties osProps = new MetricProperties();

    promProps = new PrometheusMetricsProperties(tagProfile);
    promProps.setOpenshift(osProps);
    promProps.setAccountQueryTemplates(Map.of(TEST_ACCT_QUERY_KEY, TEST_ACCOUNT_QUERY));

    queryBuilder = new QueryBuilder(promProps);
    accountSource = new PrometheusAccountSource(service, promProps, queryBuilder);

    tag1 =
        TagMetric.builder()
            .tag(TEST_PROD_TAG)
            .uom(Uom.CORES)
            .accountQueryKey(TEST_ACCT_QUERY_KEY)
            .build();

    when(tagProfile.tagIsPrometheusEnabled(TEST_PROD_TAG)).thenReturn(true);
    when(tagProfile.getTagMetrics()).thenReturn(List.of(tag1));
    when(tagProfile.measurementsByTag(TEST_PROD_TAG)).thenReturn(Set.of(Uom.CORES));
  }

  @Test
  void buildsPromQLByAccountLookupTemplateKey() {
    OffsetDateTime expectedDate = OffsetDateTime.now();
    when(service.runQuery(
            queryBuilder.buildAccountLookupQuery(new QueryDescriptor(tag1)),
            expectedDate,
            promProps.getOpenshift().getQueryTimeout()))
        .thenReturn(buildAccountQueryResult(List.of("A1")));

    accountSource.getMarketplaceAccounts(TEST_PROD_TAG, Uom.CORES, expectedDate);
    verify(service)
        .runQuery(
            queryBuilder.buildAccountLookupQuery(new QueryDescriptor(tag1)),
            expectedDate,
            promProps.getOpenshift().getQueryTimeout());
  }

  @Test
  void filtersOutNullAndEmptyAccounts() {
    String expectedAccount = "ACCOUNT_1";
    OffsetDateTime expectedDate = OffsetDateTime.now();

    // Note List.of(...) does not accept null
    List<String> accountList = new LinkedList<>();
    accountList.add(null);
    accountList.add("");
    accountList.add(expectedAccount);

    when(service.runQuery(
            queryBuilder.buildAccountLookupQuery(new QueryDescriptor(tag1)),
            expectedDate,
            promProps.getOpenshift().getQueryTimeout()))
        .thenReturn(buildAccountQueryResult(accountList));

    Set<String> accounts =
        accountSource.getMarketplaceAccounts(TEST_PROD_TAG, Uom.CORES, expectedDate);
    assertEquals(1, accounts.size());
    assertTrue(accounts.contains(expectedAccount));
  }

  @Test
  void getAllAccounts() {
    List<String> expectedAccounts = List.of("A1", "A2");
    OffsetDateTime expectedDate = OffsetDateTime.now();

    when(service.runQuery(
            queryBuilder.buildAccountLookupQuery(new QueryDescriptor(tag1)),
            expectedDate,
            promProps.getOpenshift().getQueryTimeout()))
        .thenReturn(buildAccountQueryResult(expectedAccounts));

    Set<String> accounts =
        accountSource.getMarketplaceAccounts(TEST_PROD_TAG, Uom.CORES, expectedDate);
    assertEquals(2, accounts.size());
    assertTrue(accounts.containsAll(expectedAccounts));
  }

  private QueryResult buildAccountQueryResult(List<String> accounts) {
    QueryResultData resultData = new QueryResultData().resultType(ResultType.MATRIX);
    accounts.forEach(
        account -> {
          resultData.addResultItem(
              new QueryResultDataResult().putMetricItem("ebs_account", account));
        });
    return new QueryResult().status(StatusType.SUCCESS).data(resultData);
  }
}
