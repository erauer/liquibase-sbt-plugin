The liquibase-sbt-plugin is a plugin for the [Simple Build Tool](https://github.com/harrah/xsbt/wiki) (SBT) for running LiquiBase commands.

[Liquibase](http://www.liquibase.org/) is a database-independent library for tracking, managing and applying database changes.

#Install#

The liquibase-sbt-plugin is not (yet) available in a public repository, so you have to build and install it yourself.

    git clone git://github.com/sdb/liquibase-sbt-plugin.git
    cd liquibase-sbt-plugin
    sbt update
    sbt publish-local
    

#Setup#

1. Define a dependency on the liquibase-sbt-plugin in your plugin definition file, `project/plugins.sbt`

		addSbtPlugin("com.github.sdb" %% "liquibase-sbt-plugin" % "0.0.6")


2. Configure the LiquibasePlugin in your project file, e.g. build.sbt




	liquibaseOptions := Map("dev" -> Seq(
		com.github.sdb.sbt.liquibase.LiquibaseConfiguration(changeLogFile = new java.io.File("config/db/changelog/db.changelog-master.xml"),
		 url = "jdbc:mysql://localhost/database", 
		driver ="com.mysql.jdbc.Driver",
		username = Some("sa"),
	    password = Some(""),
	    contexts = None,
	   defaultSchemaName = None),
  		com.github.sdb.sbt.liquibase.LiquibaseConfiguration(changeLogFile = new java.io.File("config/db/changelog/db2.changelog-master.xml"),
		 url = "jdbc:mysql://localhost/database2", 
		driver ="com.mysql.jdbc.Driver",
		username = Some("sa"),
	    password = Some(""),
	    contexts = None,
	   defaultSchemaName = None))
,
	test -> ....
	)
	
	//Optional - to run before tests
	liquibaseTestConfig := Some("test")



#Usage#

The following actions are available:

* `liquibase update [config name]`

  Applies un-run changes to the database.

* `liquibase update-count [config name] COUNT`

  Applies the next number of change sets.

* `liquibase drop [config name] [SCHEMA]...`

  Drops database objects owned by the current user.

* `liquibase tag [config name] TAG`

  Tags the current database state for future rollback.

* `liquibase rollback [config name] TAG`

  Rolls back the database to the state it was in when the tag was applied.

* `liquibase rollback-count [config name] COUNT`

  Rolls back the last number of change sets.

* `liquibase rollback-date [config name] DATE`

  Rolls back the database to the state it was in at the given date/time. The format of the date must match that of 'liquibaseDateFormat'.

* `liquibase validate [config name]`

  Checks the changelog for errors.

* `liquibase clear-checksums [config name]`

  Removes current checksums from database.

