/*
 *      Copyright (c) 2004-2015 YAMJ Members
 *      https://github.com/organizations/YAMJ/teams
 *
 *      This file is part of the Yet Another Media Jukebox (YAMJ).
 *
 *      YAMJ is free software: you can redistribute it and/or modify
 *      it under the terms of the GNU General Public License as published by
 *      the Free Software Foundation, either version 3 of the License, or
 *      any later version.
 *
 *      YAMJ is distributed in the hope that it will be useful,
 *      but WITHOUT ANY WARRANTY; without even the implied warranty of
 *      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *      GNU General Public License for more details.
 *
 *      You should have received a copy of the GNU General Public License
 *      along with YAMJ.  If not, see <http://www.gnu.org/licenses/>.
 *
 *      Web: https://github.com/YAMJ/yamj-v3
 *
 */
package org.yamj.core.database.dao;

import java.util.List;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.yamj.core.hibernate.HibernateDao;

@Transactional
@Repository("upgradeDatabaseDao")
public class UpgradeDatabaseDao extends HibernateDao {

    // MYSQL CHECKS
    
    protected boolean mysqlExistsColumn(String table, String column) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM information_schema.COLUMNS ");
        sb.append("WHERE TABLE_SCHEMA = 'yamj3' ");
        sb.append("AND TABLE_NAME = '").append(table).append("' ");
        sb.append("AND COLUMN_NAME = '").append(column).append("'");
        List<Object> objects = currentSession().createSQLQuery(sb.toString()).list();
        return CollectionUtils.isNotEmpty(objects);
    }

    protected boolean mysqlExistsForeignKey(String table, String foreignKey) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM information_schema.TABLE_CONSTRAINTS ");
        sb.append("WHERE TABLE_SCHEMA = 'yamj3' ");
        sb.append("AND TABLE_NAME = '").append(table).append("' ");
        sb.append("AND CONSTRAINT_TYPE = 'FOREIGN KEY' ");
        sb.append("AND CONSTRAINT_NAME = '").append(foreignKey).append("'");
        List<Object> objects = currentSession().createSQLQuery(sb.toString()).list();
        return CollectionUtils.isNotEmpty(objects);
    }

    protected void mysqldropForeignKey(String table, String foreignKey) {
        if (mysqlExistsForeignKey(table, foreignKey)) {
            StringBuilder sb = new StringBuilder();
            sb.append("ALTER TABLE ").append(table);
            sb.append(" DROP FOREIGN KEY ").append(foreignKey);
            currentSession().createSQLQuery(sb.toString()).executeUpdate();
        }
    }
    
    @SuppressWarnings("cast")
    protected List<String> mysqlListForeignKeys(String table) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS ");
        sb.append("WHERE TABLE_SCHEMA = 'yamj3' ");
        sb.append("AND TABLE_NAME = '").append(table).append("' ");
        sb.append("AND CONSTRAINT_TYPE = 'FOREIGN KEY' ");
        return (List<String>) currentSession().createSQLQuery(sb.toString()).list();
    }

    protected boolean mysqlExistsIndex(String table, String indexName) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM information_schema.STATISTICS ");
        sb.append("WHERE TABLE_SCHEMA = 'yamj3' ");
        sb.append("AND TABLE_NAME = '").append(table).append("' ");
        sb.append("AND INDEX_NAME = '").append(indexName).append("'");
        List<Object> objects = currentSession().createSQLQuery(sb.toString()).list();
        return CollectionUtils.isNotEmpty(objects);
    }

    protected void mysqlDropIndex(String table, String indexName) {
        if (mysqlExistsIndex(table, indexName)) {
            StringBuilder sb = new StringBuilder();
            sb.append("ALTER TABLE ").append(table);
            sb.append(" DROP INDEX ").append(indexName);
            currentSession().createSQLQuery(sb.toString()).executeUpdate();
        }
    }

    protected boolean mysqlExistsUniqueIndex(String table, String indexName) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM information_schema.TABLE_CONSTRAINTS ");
        sb.append("WHERE TABLE_SCHEMA = 'yamj3' ");
        sb.append("AND TABLE_NAME = '").append(table).append("' ");
        sb.append("AND CONSTRAINT_TYPE = 'UNIQUE' ");
        sb.append("AND CONSTRAINT_NAME = '").append(indexName).append("'");
        List<Object> objects = currentSession().createSQLQuery(sb.toString()).list();
        return CollectionUtils.isNotEmpty(objects);
    }

    protected void mysqlDropUniqueIndex(String table, String indexName) {
        if (mysqlExistsUniqueIndex(table, indexName)) {
            StringBuilder sb = new StringBuilder();
            sb.append("ALTER TABLE ").append(table);
            sb.append(" DROP INDEX ").append(indexName);
            currentSession().createSQLQuery(sb.toString()).executeUpdate();
        }
    }  

    // HSQL CHECKS
    
    protected boolean hsqlExistsColumn(String table, String column) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM information_schema.COLUMNS ");
        sb.append("WHERE UPPER(TABLE_NAME) = '").append(table.toUpperCase()).append("' ");
        sb.append("AND UPPER(COLUMN_NAME) = '").append(column.toUpperCase()).append("'");
        List<Object> objects = currentSession().createSQLQuery(sb.toString()).list();
        return CollectionUtils.isNotEmpty(objects);
    }

    // PATCHES
    
}
