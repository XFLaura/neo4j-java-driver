/*
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.driver.internal;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.neo4j.driver.internal.spi.Connection;
import org.neo4j.driver.v1.Logger;
import org.neo4j.driver.v1.Logging;

import static java.util.Collections.newSetFromMap;
import static java.util.Objects.requireNonNull;

public class TransactionFactory
{
    private static final boolean transactionTrackingDisabled = Boolean.getBoolean( "transactionTrackingDisabled" );
    private static final int thresholdMinutes = Integer.getInteger( "thresholdMinutes", 5 );

    private static final ScheduledExecutorService executor =
            Executors.newScheduledThreadPool( 1, new DaemonThreadFactory( "transactionTracking" ) );

    private static final Set<LeakTrackingTransaction> allTransactions =
            newSetFromMap( new ConcurrentHashMap<LeakTrackingTransaction,Boolean>() );

    private final Logger logger;

    public TransactionFactory( Logging logging )
    {
        this.logger = logging.getLog( "TransactionTracking" );

        if ( !transactionTrackingDisabled )
        {
            executor.scheduleAtFixedRate( new Runnable()
            {
                @Override
                public void run()
                {
                    printLongRunningTransactions();
                }
            }, 30, 30, TimeUnit.SECONDS );
        }
    }

    public ExplicitTransaction createTransaction( Connection conn, SessionResourcesHandler resourcesHandler,
            String bookmark )
    {
        if ( !transactionTrackingDisabled )
        {
            LeakTrackingTransaction tx =
                    new LeakTrackingTransaction( conn, resourcesHandler, bookmark, allTransactions );
            allTransactions.add( tx );
            return tx;
        }
        return new ExplicitTransaction( conn, resourcesHandler, bookmark );
    }

    private void printLongRunningTransactions()
    {
        long currentTimeMillis = System.currentTimeMillis();

        StringBuilder sb = new StringBuilder();

        for ( LeakTrackingTransaction transaction : allTransactions )
        {
            long runTime = currentTimeMillis - transaction.getStartTimeMs();
            if ( runTime > TimeUnit.MINUTES.toMillis( thresholdMinutes ) )
            {
                sb.append( transaction.toString() ).append( System.lineSeparator() );
            }
        }

        if ( sb.length() == 0 )
        {
            logger.info( "No hanging transactions detected" );
        }
        else
        {
            logger.info( "Current time: " + currentTimeMillis + ". " +
                         "Following transactions are open for more than " + thresholdMinutes + " minutes:\n" + sb );
        }
    }
}

class DaemonThreadFactory implements ThreadFactory
{
    private final String namePrefix;
    private final AtomicInteger threadId;

    public DaemonThreadFactory( String namePrefix )
    {
        this.namePrefix = requireNonNull( namePrefix );
        this.threadId = new AtomicInteger();
    }

    @Override
    public Thread newThread( Runnable runnable )
    {
        Thread thread = new Thread( runnable );
        thread.setName( namePrefix + threadId.incrementAndGet() );
        thread.setDaemon( true );
        return thread;
    }
}

