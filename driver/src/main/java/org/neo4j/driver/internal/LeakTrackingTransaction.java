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

import java.util.Map;
import java.util.Set;

import org.neo4j.driver.internal.spi.Connection;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Statement;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Value;

import static java.lang.System.lineSeparator;

public class LeakTrackingTransaction extends ExplicitTransaction
{
    private final Set<LeakTrackingTransaction> allTransactions;
    private final long startTimeMs;
    private final String stackTrace;

    private String lastQuery;
    private long lastQueryStartTimeMs;

    public LeakTrackingTransaction( Connection conn, SessionResourcesHandler resourcesHandler, String bookmark,
            Set<LeakTrackingTransaction> allTransactions )
    {
        super( conn, resourcesHandler, bookmark );
        this.allTransactions = allTransactions;
        this.startTimeMs = System.currentTimeMillis();
        this.stackTrace = captureStackTrace();
    }

    @Override
    public void close()
    {
        try
        {
            super.close();
        }
        finally
        {
            allTransactions.remove( this );
        }
    }

    @Override
    public StatementResult run( String statementTemplate, Value parameters )
    {
        recordQueryRun( statementTemplate );
        return super.run( statementTemplate, parameters );
    }

    @Override
    public StatementResult run( String statementTemplate, Map<String,Object> statementParameters )
    {
        recordQueryRun( statementTemplate );
        return super.run( statementTemplate, statementParameters );
    }

    @Override
    public StatementResult run( String statementTemplate, Record statementParameters )
    {
        recordQueryRun( statementTemplate );
        return super.run( statementTemplate, statementParameters );
    }

    @Override
    public StatementResult run( String statementTemplate )
    {
        recordQueryRun( statementTemplate );
        return super.run( statementTemplate );
    }

    @Override
    public StatementResult run( Statement statement )
    {
        recordQueryRun( statement.text() );
        return super.run( statement );
    }

    public long getStartTimeMs()
    {
        return startTimeMs;
    }

    @Override
    public String toString()
    {
        return "LeakTrackingTransaction{" +
               "startTimeMs=" + startTimeMs +
               ", lastQuery='" + lastQuery + '\'' +
               ", lastQueryStartTimeMs=" + lastQueryStartTimeMs +
               ", stackTrace:\n" + stackTrace + '\n' +
               '}';
    }

    private void recordQueryRun( String query )
    {
        lastQuery = query;
        lastQueryStartTimeMs = System.currentTimeMillis();
    }

    private static String captureStackTrace()
    {
        StringBuilder result = new StringBuilder();
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        for ( StackTraceElement element : elements )
        {
            result.append( "\t" ).append( element ).append( lineSeparator() );
        }
        return result.toString();
    }
}
