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

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.files.TagMetric;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.candlepin.subscriptions.metering.service.prometheus.promql.QueryBuilder;
import org.candlepin.subscriptions.metering.service.prometheus.promql.QueryDescriptor;
import org.candlepin.subscriptions.prometheus.model.QueryResult;
import org.springframework.util.StringUtils;

/** Provides account lists from Prometheus metrics. */
public class PrometheusAccountSource {

  private PrometheusService service;
  private PrometheusMetricsProperties prometheusProps;
  private QueryBuilder queryBuilder;

  public PrometheusAccountSource(
      PrometheusService service,
      PrometheusMetricsProperties prometheusProps,
      QueryBuilder queryBuilder) {
    this.service = service;
    this.prometheusProps = prometheusProps;
    this.queryBuilder = queryBuilder;
  }

  public Set<String> getMarketplaceAccounts(
      String productProfileId, Uom metric, OffsetDateTime time) {
    QueryResult result =
        service.runQuery(
            buildQuery(productProfileId, metric),
            time,
            prometheusProps.getMetricsTimeoutForProductTag(productProfileId));

    return result.getData().getResult().stream()
        .map(r -> r.getMetric().getOrDefault("ebs_account", ""))
        .filter(StringUtils::hasText)
        .collect(Collectors.toSet());
  }

  private String buildQuery(String productTag, Uom metric) {
    Optional<TagMetric> tag = prometheusProps.getTagMetric(productTag, metric);
    if (tag.isEmpty()) {
      throw new IllegalArgumentException(
          String.format("Could not find TagMetric for %s %s", productTag, metric));
    }
    return queryBuilder.buildAccountLookupQuery(new QueryDescriptor(tag.get()));
  }
}
