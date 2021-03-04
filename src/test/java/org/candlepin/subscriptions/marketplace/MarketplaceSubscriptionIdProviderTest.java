/*
 * Copyright (c) 2021 Red Hat, Inc.
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
package org.candlepin.subscriptions.marketplace;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.tally.UsageCalculation;
import org.candlepin.subscriptions.tally.UsageCalculation.Key;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

@SpringBootTest
@ActiveProfiles({"marketplace", "test"})
class MarketplaceSubscriptionIdProviderTest {

    @MockBean
    private SubscriptionRepository repo;

    @MockBean
    private MarketplaceSubscriptionIdCollector collector;

    @Autowired
    private MarketplaceSubscriptionIdProvider idProvider;

    @Test
    void doesNotAllowReservedValuesInKey() {
        UsageCalculation.Key key1 = new Key(String.valueOf(1), ServiceLevel._ANY, Usage.PRODUCTION);
        UsageCalculation.Key key2 = new Key(String.valueOf(1), ServiceLevel.STANDARD, Usage._ANY);

        assertThrows(IllegalArgumentException.class, () -> idProvider.findSubscriptionId("1000", key1));
        assertThrows(IllegalArgumentException.class, () -> idProvider.findSubscriptionId("1000", key2));
    }

    @Test
    void findsSubscriptionId() {
        UsageCalculation.Key key = new Key(String.valueOf(1), ServiceLevel.STANDARD, Usage.PRODUCTION);
        Subscription s = new Subscription();
        s.setMarketplaceSubscriptionId("xyz");
        Optional<Subscription> result = Optional.of(s);

        when(repo.findSubscriptionByAccountAndUsageKey("1000", key)).thenReturn(result);

        assertEquals("xyz", idProvider.findSubscriptionId("1000", key).get());
    }

    @Test
    void memoizesSubscriptionId() {
        UsageCalculation.Key key = new Key(String.valueOf(1), ServiceLevel.STANDARD, Usage.PRODUCTION);
        Subscription s = new Subscription();
        Optional<Subscription> result = Optional.of(s);

        when(repo.findSubscriptionByAccountAndUsageKey("1000", key)).thenReturn(result);
        when(collector.fetchSubscriptionId("1000", key)).thenReturn("abc");

        assertEquals("abc", idProvider.findSubscriptionId("1000", key).get());
        verify(repo).save(s);
    }
}
