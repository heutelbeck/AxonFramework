/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.eventstore.inmemory;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngineTest;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.jupiter.api.*;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link InMemoryEventStorageEngine}.
 *
 * @author Rene de Waele
 */
class InMemoryEventStorageEngineTest extends EventStorageEngineTest {

    private static final EventMessage<Object> TEST_EVENT = GenericEventMessage.asEventMessage("test");

    private InMemoryEventStorageEngine testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new InMemoryEventStorageEngine();
        setTestSubject(testSubject);
    }

    @Test
    void testPublishedEventsEmittedToExistingStreams() {
        Stream<? extends TrackedEventMessage<?>> stream = testSubject.readEvents(null, true);
        testSubject.appendEvents(TEST_EVENT);

        assertTrue(stream.findFirst().isPresent());
    }

    @Test
    void testPublishedEventsEmittedToExistingStreams_WithOffset() {
        testSubject = new InMemoryEventStorageEngine(1);
        Stream<? extends TrackedEventMessage<?>> stream = testSubject.readEvents(null, true);
        testSubject.appendEvents(TEST_EVENT);

        Optional<? extends TrackedEventMessage<?>> optionalResult = stream.findFirst();
        assertTrue(optionalResult.isPresent());
        OptionalLong optionalResultPosition = optionalResult.get().trackingToken().position();
        assertTrue(optionalResultPosition.isPresent());
        assertEquals(1, optionalResultPosition.getAsLong());
    }

    /**
     * This test issues the publication of an {@link EventMessage} through the {@link EventStore}, as this guarantees
     * the process of attaching the operation to the {@link UnitOfWork} are correctly invoked. This allows for
     * validation of issue #1056 (https://github.com/AxonFramework/AxonFramework/issues/1056), which defines that we
     * should ensure the usage of the {@link InMemoryEventStorageEngine} will rollback stored events correctly.
     */
    @Test
    void testEventsAreNotStoredWhenTheUnitOfWorkIsRolledBack() {
        // Given an EmbeddedEventStore and UnitOfWork...
        EventStore eventStore = EmbeddedEventStore.builder()
                                                  .storageEngine(testSubject)
                                                  .build();
        UnitOfWork<EventMessage<Object>> unitOfWork = DefaultUnitOfWork.startAndGet(TEST_EVENT);

        // when _only_ publishing...
        eventStore.publish(TEST_EVENT);

        // then there are no events in the storage engine, since the UnitOfWork is not committed yet.
        Stream<? extends TrackedEventMessage<?>> eventStream = testSubject.readEvents(null, true);
        assertEquals(0L, eventStream.count());

        // When rolling back the UnitOfWork...
        unitOfWork.rollback();

        // then there are *still* no events in the storage engine.
        eventStream = testSubject.readEvents(null, true);
        assertEquals(0L, eventStream.count());
    }
}
