package gov.nist.hit.hl7.igamt.bootstrap.configuration;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.config.AbstractMongoConfiguration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import com.mongodb.MongoClient;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;

@Configuration
@EnableMongoRepositories(basePackages = {"gov.nist.hit.hl7.igamt"})
@ComponentScan("gov.nist.hit.hl7.igamt")

public class DataMongoConfig extends AbstractMongoConfiguration {


  @Autowired
  Environment env;


  private static final String DB_NAME = "db.name";
  private static final String DB_HOST = "db.host";
  private static final String DB_PORT = "db.port";
  private static final String DB_USERNAME = "db.username";
  private static final String DB_PASSWORD = "db.password";
  private static final String DB_AUTH_SOURCE = "db.authsource";
	@Bean
	public GridFsTemplate gridFsTemplate() throws Exception {
	    return new GridFsTemplate(mongoDbFactory(), mappingMongoConverter());
	}

  @Override
  protected String getDatabaseName() {
    return env.getProperty(DB_NAME);
  }

  @Override
  public MongoClient mongoClient() {
    ServerAddress address =
        new ServerAddress(env.getProperty(DB_HOST), Integer.parseInt(env.getProperty(DB_PORT)));
    String username = env.getProperty(DB_USERNAME);
    String password = env.getProperty(DB_PASSWORD);
    // Build an authenticated client when credentials are supplied, which is what a
    // deployed Mongo running with authorization enabled needs. With none supplied,
    // connect unauthenticated so a local or compose Mongo still works unchanged.
    if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
      String authSource = env.getProperty(DB_AUTH_SOURCE);
      if (authSource == null || authSource.isEmpty()) {
        authSource = "admin";
      }
      MongoCredential credential =
          MongoCredential.createCredential(username, authSource, password.toCharArray());
      return new MongoClient(address, java.util.Collections.singletonList(credential));
    }
    return new MongoClient(address);
  }

  @Override
  protected String getMappingBasePackage() {
    return "gov.nist.hit.hl7.igamt";
  }



}
