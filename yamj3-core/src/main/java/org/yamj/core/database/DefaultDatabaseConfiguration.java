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
package org.yamj.core.database;

import static java.sql.Connection.TRANSACTION_READ_COMMITTED;

import java.util.Properties;
import javax.sql.DataSource;
import org.apache.commons.dbcp.BasicDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@Profile("default")
public class DefaultDatabaseConfiguration extends AbstractDatabaseConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultDatabaseConfiguration.class);

    @Value("${yamj3.database.driver}")
    private String driverClassName;

    @Value("${yamj3.database.dialect}")
    private String dialect;

    @Value("${yamj3.database.url}")
    private String url;

    @Value("${yamj3.database.username}")
    private String username;

    @Value("${yamj3.database.password}")
    private String password;

    @Value("${yamj3.database.auto:update}")
    private String hbm2ddlAuto;

    @Value("${yamj3.database.useSqlComments:false}")
    private boolean useSqlComments;
    
    @Value("${yamj3.database.validationQuery:null}")
    private String validationQuery;

    @Override
    @Bean(destroyMethod="close")
    public DataSource dataSource() {
        LOG.trace("Create new data source");
        
        BasicDataSource basicDataSource = new BasicDataSource();
        basicDataSource.setDriverClassName(driverClassName);
        basicDataSource.setUrl(url);
        basicDataSource.setUsername(username);
        basicDataSource.setPassword(password);
        basicDataSource.setValidationQuery(validationQuery);
        basicDataSource.setPoolPreparedStatements(poolPreparedStatements);
        
        basicDataSource.setInitialSize(initialSize);
        basicDataSource.setMaxActive(maxActive);
        basicDataSource.setMinIdle(minIdle);
        basicDataSource.setMaxIdle(maxIdle);
        basicDataSource.setMaxWait(maxWait);
        
        basicDataSource.setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);
        basicDataSource.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);
        basicDataSource.setNumTestsPerEvictionRun(numTestsPerEvictionRun);
        
        basicDataSource.setTestOnBorrow(testOnBorrow);
        basicDataSource.setTestWhileIdle(testWhileIdle);
        basicDataSource.setTestOnReturn(testOnReturn);
        
        basicDataSource.setDefaultTransactionIsolation(TRANSACTION_READ_COMMITTED);
        
        return basicDataSource;
    }
    
    @Override
    protected Properties hibernateProperties() {
        Properties props = new Properties();
        props.put("hibernate.dialect", dialect);
        props.put("hibernate.show_sql", showSql);
        props.put("hibernate.generate_statistics", generateStatistics);
        props.put("hibernate.hbm2ddl.auto", hbm2ddlAuto);
        props.put("hibernate.connection.isolation", TRANSACTION_READ_COMMITTED);
        props.put("hibernate.use_sql_comments", useSqlComments);
        props.put("hibernate.cache.use_query_cache", false);
        props.put("hibernate.cache.use_second_level_cache", false);
        props.put("hibernate.connection.CharSet", "utf8");
        props.put("hibernate.connection.characterEncoding", "utf8");
        props.put("hibernate.connection.useUnicode", false);
        return props;
    }    
}

