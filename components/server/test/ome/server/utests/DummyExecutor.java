/*
 *   $Id$
 *
 *   Copyright 2007 Glencoe Software, Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */
package ome.server.utests;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import ome.services.util.Executor;
import ome.system.OmeroContext;
import ome.system.Principal;
import ome.system.ServiceFactory;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;

/**
 * Simple Executor implementation which simply delegates to the
 * {@link Executor.Work#doWork(org.hibernate.Session, ServiceFactory)} and
 * similar methods.
 *
 * @author Josh Moore, josh at glencoesoftware.com
 * @since 3.0-Beta2
 */
public class DummyExecutor implements Executor {

    org.hibernate.Session session;
    ServiceFactory sf;

    public DummyExecutor(org.hibernate.Session session, ServiceFactory sf) {
        this.session = session;
        this.sf = sf;
    }

    public Object execute(Principal p, Work work) {
        return work.doWork(session, sf);
    }

    public <T> Future<T> submit(Callable<T> callable) {
        throw new UnsupportedOperationException();
    }

    public <T> T get(Future<T> future) {
        throw new UnsupportedOperationException();
    }

    public Object executeSql(SqlWork work) {
        throw new UnsupportedOperationException();
    }

    public void setApplicationContext(ApplicationContext arg0)
            throws BeansException {
        throw new UnsupportedOperationException();
    }

    public OmeroContext getContext() {
        throw new UnsupportedOperationException();
    }

    public Principal principal() {
        throw new UnsupportedOperationException();
    }

    public void setCallGroup(long gid) {
        throw new UnsupportedOperationException();
    }

    public void resetCallGroup() {
        throw new UnsupportedOperationException();
    }

}
