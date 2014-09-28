/*
 *   $Id$
 *
 *   Copyright 2008 Glencoe Software, Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */

package ome.services.scheduler;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.quartz.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ForwardingBlockingQueue;

/**
 * Produces a <a href="http://www.opensymphony.com/quartz/Quartz</a>
 * {@link Scheduler} which automatically loads all the triggers it can find.
 *
 * @author Josh Moore, josh at glencoesoftware.com
 * @since Beta4.2.1
 */
public class ThreadPool {

    private final static Logger log = LoggerFactory.getLogger(ThreadPool.class);

    private final BlockingQueue<Runnable> queue;

    private final ExecutorService executor;

    public ThreadPool(int minThreads, int maxThreads, long msTimeout) {
        queue = newQueue();
        executor = new ThreadPoolExecutor(minThreads, maxThreads, msTimeout,
                TimeUnit.MILLISECONDS, queue); // factory
    }

    protected BlockingQueue<Runnable> newQueue() {
        final BlockingQueue<Runnable> impl = new LinkedBlockingQueue<Runnable>();
        final BlockingQueue<Runnable> wrapper = new ForwardingBlockingQueue<Runnable>() {

            @Override
            protected BlockingQueue<Runnable> delegate() {
                return impl;
            }

            @Override
            public boolean offer(Runnable e, long timeout, TimeUnit unit)
                    throws InterruptedException {

                boolean b = delegate().offer(e, timeout, unit);
                if (b == false) {
                    log.warn("ThreadPool rejected " + e);
                }
                return b;
            }

        };
        return wrapper;
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public int size() {
        return queue.size();
    }

}
