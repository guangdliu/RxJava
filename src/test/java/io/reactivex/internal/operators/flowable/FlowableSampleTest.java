/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */

package io.reactivex.internal.operators.flowable;

import static org.mockito.Mockito.*;

import java.util.concurrent.TimeUnit;

import org.junit.*;
import org.mockito.InOrder;
import org.reactivestreams.*;

import io.reactivex.*;
import io.reactivex.internal.subscriptions.BooleanSubscription;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.schedulers.TestScheduler;

public class FlowableSampleTest {
    private TestScheduler scheduler;
    private Scheduler.Worker innerScheduler;
    private Subscriber<Long> observer;
    private Subscriber<Object> observer2;

    @Before
    // due to mocking
    public void before() {
        scheduler = new TestScheduler();
        innerScheduler = scheduler.createWorker();
        observer = TestHelper.mockSubscriber();
        observer2 = TestHelper.mockSubscriber();
    }

    @Test
    public void testSample() {
        Flowable<Long> source = Flowable.unsafeCreate(new Publisher<Long>() {
            @Override
            public void subscribe(final Subscriber<? super Long> observer1) {
                observer1.onSubscribe(new BooleanSubscription());
                innerScheduler.schedule(new Runnable() {
                    @Override
                    public void run() {
                        observer1.onNext(1L);
                    }
                }, 1, TimeUnit.SECONDS);
                innerScheduler.schedule(new Runnable() {
                    @Override
                    public void run() {
                        observer1.onNext(2L);
                    }
                }, 2, TimeUnit.SECONDS);
                innerScheduler.schedule(new Runnable() {
                    @Override
                    public void run() {
                        observer1.onComplete();
                    }
                }, 3, TimeUnit.SECONDS);
            }
        });

        Flowable<Long> sampled = source.sample(400L, TimeUnit.MILLISECONDS, scheduler);
        sampled.subscribe(observer);

        InOrder inOrder = inOrder(observer);

        scheduler.advanceTimeTo(800L, TimeUnit.MILLISECONDS);
        verify(observer, never()).onNext(any(Long.class));
        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any(Throwable.class));

        scheduler.advanceTimeTo(1200L, TimeUnit.MILLISECONDS);
        inOrder.verify(observer, times(1)).onNext(1L);
        verify(observer, never()).onNext(2L);
        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any(Throwable.class));

        scheduler.advanceTimeTo(1600L, TimeUnit.MILLISECONDS);
        inOrder.verify(observer, never()).onNext(1L);
        verify(observer, never()).onNext(2L);
        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any(Throwable.class));

        scheduler.advanceTimeTo(2000L, TimeUnit.MILLISECONDS);
        inOrder.verify(observer, never()).onNext(1L);
        inOrder.verify(observer, times(1)).onNext(2L);
        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any(Throwable.class));

        scheduler.advanceTimeTo(3000L, TimeUnit.MILLISECONDS);
        inOrder.verify(observer, never()).onNext(1L);
        inOrder.verify(observer, never()).onNext(2L);
        verify(observer, times(1)).onComplete();
        verify(observer, never()).onError(any(Throwable.class));
    }

    @Test
    public void sampleWithSamplerNormal() {
        PublishProcessor<Integer> source = PublishProcessor.create();
        PublishProcessor<Integer> sampler = PublishProcessor.create();

        Flowable<Integer> m = source.sample(sampler);
        m.subscribe(observer2);

        source.onNext(1);
        source.onNext(2);
        sampler.onNext(1);
        source.onNext(3);
        source.onNext(4);
        sampler.onNext(2);
        source.onComplete();
        sampler.onNext(3);

        InOrder inOrder = inOrder(observer2);
        inOrder.verify(observer2, never()).onNext(1);
        inOrder.verify(observer2, times(1)).onNext(2);
        inOrder.verify(observer2, never()).onNext(3);
        inOrder.verify(observer2, times(1)).onNext(4);
        inOrder.verify(observer2, times(1)).onComplete();
        verify(observer, never()).onError(any(Throwable.class));
    }

    @Test
    public void sampleWithSamplerNoDuplicates() {
        PublishProcessor<Integer> source = PublishProcessor.create();
        PublishProcessor<Integer> sampler = PublishProcessor.create();

        Flowable<Integer> m = source.sample(sampler);
        m.subscribe(observer2);

        source.onNext(1);
        source.onNext(2);
        sampler.onNext(1);
        sampler.onNext(1);

        source.onNext(3);
        source.onNext(4);
        sampler.onNext(2);
        sampler.onNext(2);

        source.onComplete();
        sampler.onNext(3);

        InOrder inOrder = inOrder(observer2);
        inOrder.verify(observer2, never()).onNext(1);
        inOrder.verify(observer2, times(1)).onNext(2);
        inOrder.verify(observer2, never()).onNext(3);
        inOrder.verify(observer2, times(1)).onNext(4);
        inOrder.verify(observer2, times(1)).onComplete();
        verify(observer, never()).onError(any(Throwable.class));
    }

    @Test
    public void sampleWithSamplerTerminatingEarly() {
        PublishProcessor<Integer> source = PublishProcessor.create();
        PublishProcessor<Integer> sampler = PublishProcessor.create();

        Flowable<Integer> m = source.sample(sampler);
        m.subscribe(observer2);

        source.onNext(1);
        source.onNext(2);
        sampler.onNext(1);
        sampler.onComplete();

        source.onNext(3);
        source.onNext(4);

        InOrder inOrder = inOrder(observer2);
        inOrder.verify(observer2, never()).onNext(1);
        inOrder.verify(observer2, times(1)).onNext(2);
        inOrder.verify(observer2, times(1)).onComplete();
        inOrder.verify(observer2, never()).onNext(any());
        verify(observer, never()).onError(any(Throwable.class));
    }

    @Test
    public void sampleWithSamplerEmitAndTerminate() {
        PublishProcessor<Integer> source = PublishProcessor.create();
        PublishProcessor<Integer> sampler = PublishProcessor.create();

        Flowable<Integer> m = source.sample(sampler);
        m.subscribe(observer2);

        source.onNext(1);
        source.onNext(2);
        sampler.onNext(1);
        source.onNext(3);
        source.onComplete();
        sampler.onNext(2);
        sampler.onComplete();

        InOrder inOrder = inOrder(observer2);
        inOrder.verify(observer2, never()).onNext(1);
        inOrder.verify(observer2, times(1)).onNext(2);
        inOrder.verify(observer2, never()).onNext(3);
        inOrder.verify(observer2, times(1)).onComplete();
        inOrder.verify(observer2, never()).onNext(any());
        verify(observer, never()).onError(any(Throwable.class));
    }

    @Test
    public void sampleWithSamplerEmptySource() {
        PublishProcessor<Integer> source = PublishProcessor.create();
        PublishProcessor<Integer> sampler = PublishProcessor.create();

        Flowable<Integer> m = source.sample(sampler);
        m.subscribe(observer2);

        source.onComplete();
        sampler.onNext(1);

        InOrder inOrder = inOrder(observer2);
        inOrder.verify(observer2, times(1)).onComplete();
        verify(observer2, never()).onNext(any());
        verify(observer, never()).onError(any(Throwable.class));
    }

    @Test
    public void sampleWithSamplerSourceThrows() {
        PublishProcessor<Integer> source = PublishProcessor.create();
        PublishProcessor<Integer> sampler = PublishProcessor.create();

        Flowable<Integer> m = source.sample(sampler);
        m.subscribe(observer2);

        source.onNext(1);
        source.onError(new RuntimeException("Forced failure!"));
        sampler.onNext(1);

        InOrder inOrder = inOrder(observer2);
        inOrder.verify(observer2, times(1)).onError(any(Throwable.class));
        verify(observer2, never()).onNext(any());
        verify(observer, never()).onComplete();
    }

    @Test
    public void sampleWithSamplerThrows() {
        PublishProcessor<Integer> source = PublishProcessor.create();
        PublishProcessor<Integer> sampler = PublishProcessor.create();

        Flowable<Integer> m = source.sample(sampler);
        m.subscribe(observer2);

        source.onNext(1);
        sampler.onNext(1);
        sampler.onError(new RuntimeException("Forced failure!"));

        InOrder inOrder = inOrder(observer2);
        inOrder.verify(observer2, times(1)).onNext(1);
        inOrder.verify(observer2, times(1)).onError(any(RuntimeException.class));
        verify(observer, never()).onComplete();
    }

    @Test
    public void testSampleUnsubscribe() {
        final Subscription s = mock(Subscription.class);
        Flowable<Integer> o = Flowable.unsafeCreate(
                new Publisher<Integer>() {
                    @Override
                    public void subscribe(Subscriber<? super Integer> subscriber) {
                        subscriber.onSubscribe(s);
                    }
                }
        );
        o.throttleLast(1, TimeUnit.MILLISECONDS).subscribe().dispose();
        verify(s).cancel();
    }
}