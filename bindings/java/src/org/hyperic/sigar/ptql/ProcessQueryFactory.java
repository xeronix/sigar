/*
 * Copyright (C) [2004, 2005, 2006], Hyperic, Inc.
 * This file is part of SIGAR.
 * 
 * SIGAR is free software; you can redistribute it and/or modify
 * it under the terms version 2 of the GNU General Public License as
 * published by the Free Software Foundation. This program is distributed
 * in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA.
 */

package org.hyperic.sigar.ptql;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.StringTokenizer;

import org.hyperic.sigar.Sigar;
import org.hyperic.sigar.SigarException;
import org.hyperic.sigar.util.ReferenceMap;

public class ProcessQueryFactory implements Comparator {
    
    //for testing until native port is completed
    private static final boolean useNative =
        "true".equals(System.getProperty("sigar.ptql.native"));

    private static Map cache =
        ReferenceMap.synchronizedMap();

    public ProcessQueryFactory() {}

    //sort what will become instruction branches.
    //so the cheapest are executed first.
    private int getOrder(String s) {
        if (s.startsWith("Port.")) {
            return 11;
        }
        else if (s.startsWith("Env.")) {
            return 10;
        }
        else if (s.startsWith("Args.")) {
            return 9;
        }
        else if (s.startsWith("Exe.")) {
            return 8;
        }
        else if (s.startsWith("State.")) {
            return 7;
        }

        return 1;
    }

    public int compare(Object o1, Object o2) {

        return getOrder((String)o1) -
            getOrder((String)o2);
    }

    public ProcessQuery prepare(QueryBranchMap map)
        throws MalformedQueryException,
               QueryLoadException {

        ProcessQueryBuilder builder = new ProcessQueryBuilder();

        String[] keys =
            (String[])map.keySet().toArray(new String[0]);

        Arrays.sort(keys, this);

        for (int i=0; i<keys.length; i++) {
            String key = keys[i];
            String val = (String)map.get(key);

            if (val.charAt(0) == '$') {
                String var = val.substring(1);

                try {
                    int ix = Integer.parseInt(var);
                    //$1..n references
                    ix--;
                    if ((ix < 0) || (ix >= map.branches.size())) {
                        String msg = "Variable out of range " + var;
                        throw new MalformedQueryException(msg);
                    }

                    var = (String)map.branches.get(ix);
                    var = builder.addModifier(var,
                                              ProcessQueryBuilder.MOD_VALUE);
                    key = builder.addModifier(key,
                                              ProcessQueryBuilder.MOD_CLONE);
                } catch (NumberFormatException e) {
                    var = System.getProperty(var, val);
                }

                val = var;
            }

            builder.append(key, val);
        }

        builder.finish();

        return builder.load();
    }

    private static ProcessQuery getPidInstance(String query)
        throws MalformedQueryException {

        if (query.indexOf(",") > 0) {
            throw new MalformedQueryException("Invalid Pid query");
        }

        String[] vals = QueryBranch.split(query);

        QueryBranch branch = new QueryBranch(vals[0]);
        String val = vals[1];

        if (!branch.op.equals("eq")) {
            throw new MalformedQueryException("Invalid Pid operator");
        }

        if (branch.attr.equals("PidFile")) {
            return new PidFileQuery(val);
        }
        else if (branch.attr.equals("Service")) {
            return new WindowsServiceQuery(val);
        }

        throw new MalformedQueryException("Unsupported method: " +
                                          branch.attr);
    }

    public static ProcessQuery getInstance(String query)
        throws MalformedQueryException,
               QueryLoadException {

        if (query == null) {
            throw new MalformedQueryException("null query");
        }

        if (query.length() == 0) {
            throw new MalformedQueryException("empty query");
        }

        ProcessQuery pQuery = (ProcessQuery)cache.get(query);

        if (pQuery != null) {
            return pQuery;
        }

        if (useNative) {
            pQuery = new SigarProcessQuery();
            try {
                ((SigarProcessQuery)pQuery).create(query);
            } catch (SigarException e) {
                throw new MalformedQueryException(e.getMessage());
            }
            cache.put(query, pQuery);
            return pQuery;
        }

        if (query.startsWith("Pid.PidFile.") ||
            query.startsWith("Pid.Service."))
        {
            pQuery = getPidInstance(query);
            cache.put(query, pQuery);
            return pQuery;
        }

        QueryBranchMap queries = new QueryBranchMap();
        StringTokenizer st = new StringTokenizer(query, ",");

        while (st.hasMoreTokens()) {
            String[] vals = QueryBranch.split(st.nextToken());

            queries.put(vals[0], vals[1]);
        }

        ProcessQueryFactory factory = new ProcessQueryFactory();

        pQuery = factory.prepare(queries);

        cache.put(query, pQuery);

        return pQuery;
    }
}
